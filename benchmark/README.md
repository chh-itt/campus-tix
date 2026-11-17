# CampusTix 压测

两套方案：**Python 脚本（推荐，零依赖）** 与 JMeter（可选）。

## 方案一：Python 脚本

前置：后端已启动（`mvn spring-boot:run`），种子数据已写入。

```bash
# 场景 1：抢票防超卖（200 用户并发抢 2 个名额 → 成功必须恰好 2）
python benchmark/oversell.py --scene buy --users 200

# 指定场次
python benchmark/oversell.py --scene buy --session <sessionId> --users 500

# 场景 2：活动列表读吞吐（3000 请求 / 50 并发）
python benchmark/oversell.py --scene read --users 50 --total 3000
```

输出示例：

```
[scene=buy] 场次 1923... ，200 个用户并发抢购…
  注册成功 200/200
  ── 下单结果（并发 50 线程 / 6.2s）──
  成功      : 2
  售罄(410) : 198
  其他异常  : 0
  ── 下单接口 RT ──
  avg=142ms  P50=120ms  P95=280ms  P99=410ms
  ✔ 防超卖校验通过：成功 = 2 = 名额 2
```

## 方案二：JMeter

1. `bin/jmeter.sh -n -t benchmark/campustix.jmx -l benchmark/results/out.jtl -e -o benchmark/results/report`
2. 计划：CSV 变量 username 循环 200 → 注册/登录取 token → 并发 POST `/api/orders`，聚合报告看 Throughput / P95 / Error%。

## 指标口径

| 指标 | 含义 | 达标线（2C4G 演示机） |
|------|------|----------------------|
| 超卖数 | 成功订单数 − 场次名额 | **必须 = 0** |
| 下单 QPS | 写接口吞吐 | ≥ 200 |
| 列表 QPS | 读接口吞吐（含缓存命中） | ≥ 800 |
| P95 RT | 下单 95 分位延迟 | ≤ 500ms |
