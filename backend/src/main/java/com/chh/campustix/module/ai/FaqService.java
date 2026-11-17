package com.chh.campustix.module.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chh.campustix.common.api.ResultCode;
import com.chh.campustix.common.exception.BizException;
import com.chh.campustix.common.props.CampustixProps;
import com.chh.campustix.module.ai.dto.FaqAnswerVO;
import com.chh.campustix.module.ai.dto.FaqAskReq;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI-FAQ：MySQL FULLTEXT(ngram) 检索 top3 作为资料 → DeepSeek 生成回答。
 * 未配置 API Key 时降级为"直接返回检索命中的答案"；每用户/ IP 每分钟限流。
 */
@Slf4j
@Service
public class FaqService {

    private final FaqMapper faqMapper;
    private final CampustixProps props;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient;

    public FaqService(FaqMapper faqMapper, CampustixProps props, StringRedisTemplate stringRedisTemplate) {
        this.faqMapper = faqMapper;
        this.props = props;
        this.stringRedisTemplate = stringRedisTemplate;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(30_000);
        this.restClient = RestClient.builder()
                .baseUrl(props.getAi().getDeepseek().getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    /** 活动详情页 FAQ 列表：本活动 + 全局，最多 20 条 */
    public List<String> listQuestions(Long activityId) {
        return faqMapper.selectList(new LambdaQueryWrapper<Faq>()
                .and(activityId != null,
                        w -> w.eq(Faq::getActivityId, activityId).or().isNull(Faq::getActivityId))
                .orderByDesc(Faq::getId)
                .last("LIMIT 20"))
                .stream().map(Faq::getQuestion).toList();
    }

    public FaqAnswerVO ask(String subjectKey, FaqAskReq req) {
        rateLimit(subjectKey);

        // 1. FULLTEXT 检索（ngram 分词，中文可用）；命中范围 = 指定活动 + 全局 FAQ
        List<Faq> hits = faqMapper.selectList(new LambdaQueryWrapper<Faq>()
                .and(req.getActivityId() != null,
                        w -> w.eq(Faq::getActivityId, req.getActivityId()).or().isNull(Faq::getActivityId))
                .and(w -> w.apply("MATCH(question, keywords) AGAINST({0})", req.getQuestion())
                        .or().like(Faq::getQuestion, req.getQuestion()))
                .last("LIMIT 3"));

        if (hits.isEmpty()) {
            return new FaqAnswerVO("暂没有找到与您问题相关的答案，您可以换个说法，或联系主办方咨询。", List.of());
        }
        List<String> sources = hits.stream().map(Faq::getQuestion).toList();

        // 2. 未配置 Key / 无 Key 时降级：直接返回检索命中的第一条答案
        String apiKey = props.getAi().getDeepseek().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return new FaqAnswerVO(hits.get(0).getAnswer(), sources);
        }

        // 3. DeepSeek 生成
        try {
            String answer = chat(hits, req.getQuestion());
            return new FaqAnswerVO(answer, sources);
        } catch (Exception e) {
            log.warn("AI 生成失败，降级返回检索答案 question={}", req.getQuestion(), e);
            return new FaqAnswerVO(hits.get(0).getAnswer(), sources);
        }
    }

    private void rateLimit(String subjectKey) {
        String key = "rl:faq:" + subjectKey;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, Duration.ofSeconds(60));
        }
        int limit = props.getAi().getRateLimitPerMinute();
        if (count != null && count > limit) {
            throw new BizException(ResultCode.RATE_LIMITED, "提问太频繁，请稍后再试");
        }
    }

    @SuppressWarnings("unchecked")
    private String chat(List<Faq> hits, String question) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            sb.append("[").append(i + 1).append("] 问题：").append(hits.get(i).getQuestion())
                    .append("\n答案：").append(hits.get(i).getAnswer()).append("\n\n");
        }
        Map<String, Object> body = Map.of(
                "model", props.getAi().getDeepseek().getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content",
                                "你是校园活动票务平台 CampusTix 的客服助手。只依据下方参考资料回答用户问题，"
                                        + "资料没有覆盖的内容请如实说不知道并建议联系主办方。回答用中文，简洁分点，100 字以内。"),
                        Map.of("role", "user", "content",
                                "参考资料：\n" + sb + "\n用户问题：" + question)),
                "temperature", 0.3);

        String resp = restClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.getAi().getDeepseek().getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(resp);
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            return choices.get(0).path("message").path("content").asText("").trim();
        }
        throw new IllegalStateException("DeepSeek 响应缺少 choices: " + resp);
    }
}
