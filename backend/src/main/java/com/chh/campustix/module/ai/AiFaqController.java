package com.chh.campustix.module.ai;

import com.chh.campustix.common.api.Result;
import com.chh.campustix.module.ai.dto.FaqAnswerVO;
import com.chh.campustix.module.ai.dto.FaqAskReq;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/faq")
@RequiredArgsConstructor
public class AiFaqController {

    private final FaqService faqService;

    @PostMapping("/ask")
    public Result<FaqAnswerVO> ask(@Valid @RequestBody FaqAskReq req, HttpServletRequest request) {
        // 登录用户按用户 ID 限流，匿名按 IP 限流（服务端判定，防伪造）
        var login = com.chh.campustix.common.security.UserContext.get();
        String subject = login != null ? "u:" + login.id() : "ip:" + request.getRemoteAddr();
        return Result.ok(faqService.ask(subject, req));
    }
}
