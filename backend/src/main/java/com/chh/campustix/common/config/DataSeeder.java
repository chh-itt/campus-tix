package com.chh.campustix.common.config;

import com.chh.campustix.common.redis.StockRedisService;
import com.chh.campustix.common.security.LoginUser;
import com.chh.campustix.module.activity.Activity;
import com.chh.campustix.module.activity.ActivityMapper;
import com.chh.campustix.module.activity.ActivitySession;
import com.chh.campustix.module.activity.ActivitySessionMapper;
import com.chh.campustix.module.ai.Faq;
import com.chh.campustix.module.ai.FaqMapper;
import com.chh.campustix.module.user.User;
import com.chh.campustix.module.user.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 演示种子数据：仅当 t_user 为空时执行（幂等）。
 * 账号：admin / organizer / club / student 密码统一 123456。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserMapper userMapper;
    private final ActivityMapper activityMapper;
    private final ActivitySessionMapper sessionMapper;
    private final FaqMapper faqMapper;
    private final PasswordEncoder passwordEncoder;
    private final StockRedisService stockRedis;

    @Override
    public void run(String... args) {
        if (userMapper.selectCount(null) > 0) {
            return;
        }
        log.info("首次启动：写入演示种子数据…");
        User admin = saveUser("admin", "平台管理员", LoginUser.ROLE_ADMIN);
        User org = saveUser("organizer", "校学生会文体部", LoginUser.ROLE_ORGANIZER);
        User club = saveUser("club", "计算机协会", LoginUser.ROLE_ORGANIZER);
        saveUser("student", "陈同学", LoginUser.ROLE_STUDENT);

        LocalDateTime now = LocalDateTime.now();
        Activity act1 = saveActivity(org.getId(), "2026 新年音乐会", "MUSIC",
                "大学生活动中心剧场",
                "校交响乐团年度巨献，古典与现代曲目交融，欢迎全体师生凭票入场。");
        saveSession(act1, "晚场（19:00）", now.plusDays(1).withHour(19).withMinute(0),
                now.plusDays(1).withHour(21).withMinute(0), now.minusHours(1),
                now.plusDays(1).withHour(18).withMinute(0), "19.90", 200);
        saveSession(act1, "午场（14:00）", now.plusDays(2).withHour(14).withMinute(0),
                now.plusDays(2).withHour(16).withMinute(0), now.minusHours(1),
                now.plusDays(2).withHour(13).withMinute(0), "19.90", 150);

        Activity act2 = saveActivity(club.getId(), "AI 工作坊：大模型应用开发入门", "TECH",
                "信息楼 A305 机房",
                "从 Prompt 到 RAG，动手搭建你的第一个大模型应用。请自带笔记本电脑。");
        saveSession(act2, "周六场", now.plusDays(3).withHour(14).withMinute(0),
                now.plusDays(3).withHour(17).withMinute(0), now.minusHours(1),
                now.plusDays(3).withHour(13).withMinute(30), "0", 30);

        Activity act3 = saveActivity(org.getId(), "校园歌手大赛决赛", "SHOW",
                "大学生活动中心剧场",
                "十强选手巅峰对决，现场大众评审投票，你的一票决定冠军归属！");
        saveSession(act3, "决赛夜", now.plusDays(5).withHour(18).withMinute(30),
                now.plusDays(5).withHour(21).withMinute(0), now.minusHours(1),
                now.plusDays(5).withHour(17).withMinute(30), "9.90", 500);

        // 小容量场次：便于演示"售罄→候补→释放→自动转正"完整链路
        Activity act4 = saveActivity(club.getId(), "研究生学术论坛 · AI 分论坛", "TECH",
                "图书馆报告厅",
                "小规模闭门研讨（仅 2 席），与嘉宾面对面交流，候补机制演示场次。");
        saveSession(act4, "闭门研讨", now.plusDays(2).withHour(9).withMinute(30),
                now.plusDays(2).withHour(11).withMinute(30), now.minusHours(1),
                now.plusDays(2).withHour(9).withMinute(0), "5.00", 2);

        saveFaq(null, "怎么退票？", "退票 规则 取消",
                "开场前可在【我的订单】自助取消并释放名额（名额将按候补顺序分配给排队用户）；开场后不支持退票。");
        saveFaq(null, "凭什么入场？需要纸质票吗？", "入场 凭证 核销 二维码 纸质",
                "无需纸质票。支付成功后在【我的票】出示核销码，现场工作人员扫码核销，一人一码，仅限本人使用。");
        saveFaq(null, "可以把票转让给别人吗？", "转让 改签 换人",
                "电子票与账号绑定，暂不支持转让。如需让他人参加，请先取消订单，再由对方自行报名。");
        saveFaq(null, "售罄了还能买到票吗？", "售罄 候补 排队 没票",
                "可以加入候补队列：他人取消或超时未支付释放名额后，系统按排队顺序自动为队首用户创建限时支付订单，收到站内通知后尽快支付即可。");
        saveFaq(null, "支持哪些支付方式？", "支付 付款 方式 支付宝",
                "目前演示环境支持平台模拟支付收银台；接入支付宝沙箱后支持支付宝支付。");
        saveFaq(null, "下单后一直不支付会怎么样？", "超时 未支付 过期 关单 释放",
                "下单后需在限定时间内完成支付（演示环境为 2 分钟），超时订单自动关闭并释放名额，释放的名额会优先分配给候补用户。");
        saveFaq(null, "免费活动也需要下单吗？", "免费 0元 报名 流程",
                "需要。0 元活动同样走下单流程（支付金额为 0），支付成功后同样生成电子票用于入场核销。");
        saveFaq(null, "票码丢了怎么办？", "丢失 找不到 补码",
                "电子票永久保存在账号【我的票】中，换设备登录同一账号即可重新出示，无需补票。");

        // Redis 库存初始化（尽力而为；运行期 MISS 自愈也能兜底）
        for (ActivitySession s : sessionMapper.selectList(null)) {
            try {
                stockRedis.initIfAbsent(s.getId(), s.getRemaining());
            } catch (Exception e) {
                log.warn("种子库存写入 Redis 失败 sessionId={}（运行期自愈会兜底）", s.getId());
            }
        }
        log.info("演示数据写入完成：用户 4 个（admin/organizer/club/student，密码 123456），活动 4 个，场次 5 个，FAQ 8 条");
    }

    private User saveUser(String username, String nickname, String role) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode("123456"));
        u.setNickname(nickname);
        u.setRole(role);
        u.setStatus(1);
        userMapper.insert(u);
        return u;
    }

    private Activity saveActivity(Long organizerId, String title, String category,
                                  String venue, String description) {
        Activity a = new Activity();
        a.setOrganizerId(organizerId);
        a.setTitle(title);
        a.setCategory(category);
        a.setVenue(venue);
        a.setDescription(description);
        a.setStatus(Activity.STATUS_PUBLISHED);
        activityMapper.insert(a);
        return a;
    }

    private void saveSession(Activity activity, String title, LocalDateTime start, LocalDateTime end,
                             LocalDateTime saleStart, LocalDateTime saleEnd, String price, int quota) {
        ActivitySession s = new ActivitySession();
        s.setActivityId(activity.getId());
        s.setTitle(title);
        s.setStartTime(start);
        s.setEndTime(end);
        s.setSaleStart(saleStart);
        s.setSaleEnd(saleEnd);
        s.setPrice(new BigDecimal(price));
        s.setQuota(quota);
        s.setRemaining(quota);
        s.setSold(0);
        s.setStatus(ActivitySession.STATUS_ON_SALE);
        sessionMapper.insert(s);
    }

    private void saveFaq(Long activityId, String question, String keywords, String answer) {
        Faq f = new Faq();
        f.setActivityId(activityId);
        f.setQuestion(question);
        f.setKeywords(keywords);
        f.setAnswer(answer);
        faqMapper.insert(f);
    }
}
