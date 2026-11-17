# CampusTix —— 校园活动票务平台

面向高校场景的一站式活动票务系统：**活动发布 → 在线报名 → 支付 → 电子票核销 → 退款/候补** 交易闭环，覆盖高并发库存、接口幂等、订单状态机、延迟关单、支付回调、缓存一致性等电商级难点，并内置 **AI 智能客服（RAG）**。

## 项目结构

```
campus-tix/
├── backend/                    # Spring Boot 3 后端（Java 21）
│   ├── src/main/java/com/chh/campustix/
│   │   ├── common/             # Result 统一响应 / 全局异常 / JWT 安全 / 幂等切面 / Redis Lua 库存 / 延迟关单抽象
│   │   ├── module/user         # 注册登录（BCrypt + JWT）
│   │   ├── module/activity     # 活动/场次/库存 + 主办方工作台 + 核销
│   │   ├── module/order        # 下单/取消/超时关单/候补转正单
│   │   ├── module/payment      # 模拟收银台 + 支付宝沙箱回调
│   │   ├── module/ticket       # 电子票 + 扫码核销 CAS
│   │   ├── module/waitlist     # 候补队列（库存释放自动唤醒队首）
│   │   ├── module/notice       # 站内通知
│   │   └── module/ai           # AI-FAQ：FULLTEXT 检索 + DeepSeek 生成 + 限流降级
│   ├── src/main/resources/
│   │   ├── db/migration/V1__init.sql   # 9 张表（Flyway 自动执行）
│   │   └── lua/stock_deduct.lua        # Redis 原子扣减库存
│   └── src/test/java/.../OrderFlowIT   # Testcontainers 全链路 + 并发防超卖测试
├── frontend/                   # Vue 3 + Vite + Pinia + Vue Router
├── docker-compose.dev.yml      # 本地开发：MySQL + Redis + RocketMQ
├── docker-compose.prod.yml     # 生产部署：全组件容器化
└── deploy/rocketmq/broker.conf
```

## 快速启动（本地开发）

```bash
# 1. 启动中间件（需 Docker Desktop）
docker compose -f docker-compose.dev.yml up -d

# 2. 启动后端（首次自动建表 + 写入演示数据）
cd backend && mvn spring-boot:run

# 3. 启动前端
cd frontend && pnpm install && pnpm dev    # http://localhost:8088
```

演示账号（密码均为 `123456`）：

| 账号 | 角色 | 用途 |
|------|------|------|
| student | STUDENT | 报名购票 |
| organizer | ORGANIZER | 主办方：建活动/场次/核销 |
| club | ORGANIZER | 第二个主办方 |
| admin | ADMIN | 平台管理 |

> 种子数据特意放了一个 **2 席小场次**（研究生学术论坛），两单即售罄，可完整演示「售罄 → 候补 → 释放 → 自动转正」链路。

## 核心设计

### 1. 三层防超卖（Redis Lua → DB 条件更新 → 唯一约束）
- 前置：`stock_deduct.lua` 原子扣减 Redis 库存，无效流量不落库；
- 权威：`UPDATE t_activity_session SET remaining = remaining - N WHERE remaining >= N` 条件更新，行数=0 即售罄；
- 补偿：Redis key 缺失时从 DB 自愈（`initIfAbsent`），取消/超时/退款 Lua 回补 + 事务提交后触发候补唤醒。

### 2. 接口幂等
- `/api/orders/token` 下单前领号；服务端 `t_idempotency_record` 唯一索引 + Redis SETNX 双保险，重放返回原订单。

### 3. 订单状态机 + 超时关单
- 全部状态流转用 CAS（`WHERE status = 期望值`），杜绝并发错乱；
- 延迟关单抽象 `DelayQueue`：RocketMQ 延迟消息（生产）/ Redis RDelayedQueue（测试/降级）双实现，`@Scheduled` 30s 扫表兜底；
- 事务提交后才投递 MQ（`TransactionSynchronization`），杜绝消息先于提交的脏读。

### 4. 支付
- 模拟收银台：服务端 HMAC-SHA256 签名链接 → 网关回调验签 → 幂等落账；
- 支付宝沙箱：RSA2 验签、`success/failure` 应答协议；
- 迟到支付：订单已关闭时自动进入退款流程（REFUNDING→REFUNDED）。

### 5. 候补队列
- 售罄可排队；库存释放事件（取消/超时）按 FIFO 自动唤醒队首，CAS 抢占候补记录防并发重复唤醒，直接创建限时支付订单并站内通知。

### 6. 缓存一致性（活动详情）
- Cache-Aside + 空值缓存（防穿透）+ SETNX 锁（防击穿）+ 随机 TTL 60~90s（防雪崩），主办方写操作主动 evict。

### 7. AI 智能客服（RAG）
- MySQL FULLTEXT（ngram 中文分词）检索 Top3 → DeepSeek 生成回答，每用户/分钟限流；未配置 Key 自动降级为纯检索答案。

## 压测

`benchmark/oversell.py`（Python 标准库零依赖），本机（Windows 开发机 + Docker 全家桶同机）实测：

| 场景 | 压力 | 结果 |
|---|---|---|
| 抢票（防超卖） | 200 用户 50 线程并发抢 2 票，1.1s 完成 | 成功 **2** / 售罄 410 共 198 / 异常 0，**超卖 0**；下单 RT avg=232ms P50=225ms P95=341ms P99=406ms |
| 读接口吞吐 | 50 并发 3000 请求 GET /api/activities | **QPS 761**，失败 0；RT avg=62ms P50=58ms P95=104ms P99=137ms |

```bash
python benchmark/oversell.py --scene buy  --users 200 --quota 2   # 并发抢票，校验成功数==quota
python benchmark/oversell.py --scene read --users 50 --total 3000 # 列表吞吐
```

## 生产部署

```bash
cp .env.example .env     # 填入域名/密钥/DeepSeek Key
docker compose -f docker-compose.prod.yml up -d --build
```

后端 8080（API），前端 8088（静态站）。CORS 由 `CAMPUSTIX_CORS_ALLOWEDORIGINS_0` 控制。
