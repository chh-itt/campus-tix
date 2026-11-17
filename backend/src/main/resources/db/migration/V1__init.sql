-- =====================================================
-- CampusTix 校园活动票务平台 初始表结构 (MySQL 8.0, utf8mb4)
-- =====================================================

CREATE TABLE t_user (
    id          BIGINT       NOT NULL PRIMARY KEY,
    username    VARCHAR(32)  NOT NULL,
    password    VARCHAR(100) NOT NULL COMMENT 'BCrypt',
    nickname    VARCHAR(32)  NULL,
    phone       VARCHAR(20)  NULL,
    role        VARCHAR(16)  NOT NULL DEFAULT 'STUDENT' COMMENT 'STUDENT/ORGANIZER/ADMIN',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='用户';

CREATE TABLE t_activity (
    id           BIGINT       NOT NULL PRIMARY KEY,
    organizer_id BIGINT       NOT NULL COMMENT '主办方用户id',
    title        VARCHAR(128) NOT NULL,
    category     VARCHAR(16)  NOT NULL DEFAULT '讲座' COMMENT '讲座/演出/比赛/社团/其他',
    cover        VARCHAR(255) NULL,
    description  TEXT         NULL,
    venue        VARCHAR(128) NULL,
    status       VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/OFFLINE/FINISHED',
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_status (status),
    KEY idx_organizer (organizer_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='活动';

-- 库存实体：名额挂在场次上，扣减走 DB 条件更新 + Redis 预扣双层
CREATE TABLE t_activity_session (
    id          BIGINT        NOT NULL PRIMARY KEY,
    activity_id BIGINT        NOT NULL,
    title       VARCHAR(64)   NOT NULL COMMENT '如：周六下午场',
    start_time  DATETIME      NOT NULL,
    end_time    DATETIME      NOT NULL,
    sale_start  DATETIME      NOT NULL,
    sale_end    DATETIME      NOT NULL,
    price       DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '单价，0=免费',
    quota       INT           NOT NULL COMMENT '总名额',
    remaining   INT           NOT NULL COMMENT '剩余名额（DB 层权威值）',
    sold        INT           NOT NULL DEFAULT 0,
    status      VARCHAR(16)   NOT NULL DEFAULT 'ON_SALE' COMMENT 'ON_SALE/OFF_SALE/CANCELLED',
    created_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_activity (activity_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='活动场次';

CREATE TABLE t_order (
    id             BIGINT        NOT NULL PRIMARY KEY,
    order_no       VARCHAR(40)   NOT NULL,
    user_id        BIGINT        NOT NULL,
    activity_id    BIGINT        NOT NULL,
    session_id     BIGINT        NOT NULL,
    quantity       INT           NOT NULL DEFAULT 1,
    unit_price     DECIMAL(10,2) NOT NULL DEFAULT 0,
    total_amount   DECIMAL(10,2) NOT NULL DEFAULT 0,
    status         VARCHAR(16)   NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/PAID/CHECKED_IN/CANCELLED/EXPIRED/REFUNDING/REFUNDED',
    close_reason   VARCHAR(32)   NULL COMMENT 'USER_CANCEL/PAY_EXPIRED/...',
    pay_expire_at  DATETIME(3)   NOT NULL COMMENT '支付截止时间',
    paid_at        DATETIME(3)   NULL,
    created_at     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_status (user_id, status),
    KEY idx_session_status (session_id, status),
    KEY idx_status_expire (status, pay_expire_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='订单';

CREATE TABLE t_ticket (
    id            BIGINT      NOT NULL PRIMARY KEY,
    ticket_no     VARCHAR(40) NOT NULL COMMENT '核销码/票号，二维码内容',
    order_id      BIGINT      NOT NULL,
    order_no      VARCHAR(40) NOT NULL,
    activity_id   BIGINT      NOT NULL,
    session_id    BIGINT      NOT NULL,
    user_id       BIGINT      NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'VALID' COMMENT 'VALID/USED/VOID',
    checked_in_at DATETIME(3) NULL,
    checked_by    BIGINT      NULL,
    created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_ticket_no (ticket_no),
    KEY idx_user (user_id),
    KEY idx_order (order_id),
    KEY idx_session_status (session_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='票';

CREATE TABLE t_payment (
    id           BIGINT        NOT NULL PRIMARY KEY,
    out_trade_no VARCHAR(48)   NOT NULL COMMENT '商户单号=订单号',
    order_no     VARCHAR(40)   NOT NULL,
    channel      VARCHAR(16)   NOT NULL COMMENT 'MOCK/ALIPAY',
    amount       DECIMAL(10,2) NOT NULL,
    status       VARCHAR(16)   NOT NULL DEFAULT 'INIT' COMMENT 'INIT/SUCCESS/FAILED/REFUNDED',
    trade_no     VARCHAR(64)   NULL COMMENT '渠道流水号',
    callback_at  DATETIME(3)   NULL,
    refunded_at  DATETIME(3)   NULL,
    created_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_out_trade_no (out_trade_no),
    KEY idx_order_no (order_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='支付单';

-- 业务幂等记录（下单 token 级别防重）
CREATE TABLE t_idempotency_record (
    id         BIGINT      NOT NULL PRIMARY KEY,
    token      VARCHAR(64) NOT NULL,
    scene      VARCHAR(32) NOT NULL DEFAULT 'ORDER_CREATE',
    user_id    BIGINT      NOT NULL,
    biz_no     VARCHAR(48) NULL COMMENT '关联业务单号',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_token (token)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='幂等记录';

CREATE TABLE t_waitlist (
    id               BIGINT      NOT NULL PRIMARY KEY,
    session_id       BIGINT      NOT NULL,
    user_id          BIGINT      NOT NULL,
    status           VARCHAR(16) NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING/NOTIFIED/FULFILLED/EXPIRED/CANCELLED',
    notified_order_no VARCHAR(40) NULL COMMENT '转正后的订单号',
    notified_at      DATETIME(3) NULL,
    created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_session_status (session_id, status),
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='候补队列';

CREATE TABLE t_notice (
    id         BIGINT       NOT NULL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    title      VARCHAR(64)  NOT NULL,
    content    VARCHAR(512) NULL,
    is_read    TINYINT      NOT NULL DEFAULT 0,
    biz_no     VARCHAR(48)  NULL,
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_user_read (user_id, is_read)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='站内通知';

CREATE TABLE t_faq (
    id          BIGINT        NOT NULL PRIMARY KEY,
    activity_id BIGINT        NULL COMMENT 'NULL=平台通用FAQ',
    question    VARCHAR(255)  NOT NULL,
    keywords    VARCHAR(255)  NULL,
    answer      VARCHAR(1024) NOT NULL,
    created_at  DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    FULLTEXT KEY ft_faq (question, keywords) WITH PARSER ngram
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='FAQ（AI 问答检索源）';
