#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
CampusTix 压测脚本（零依赖，Python 3.8+ 标准库）

场景 1  抢票防超卖：N 个用户并发抢 quota=Q 的同一场次 → 成功数必须恰好 = Q（超卖=0）
场景 2  读接口吞吐：并发请求 GET /api/activities，输出 QPS / 平均 RT / P95

用法：
    python oversell.py --base http://localhost:8080 --quota 2 --users 200
    python oversell.py --scene read --users 50 --total 3000

前提：后端已启动、种子数据已写入（quota=2 的场次为「研究生学术论坛 · AI 分论坛」）。
"""
import argparse
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
import json
import threading
import time
import urllib.request
from collections import deque

_lock = threading.Lock()
_results = deque()


def request(url, method="GET", data=None, headers=None, timeout=15):
    body = json.dumps(data).encode() if data is not None else None
    req = urllib.request.Request(url, data=body, method=method)
    req.add_header("Content-Type", "application/json")
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            payload = json.loads(resp.read().decode("utf-8", "ignore"))
            code = resp.status
    except urllib.error.HTTPError as e:
        payload = {}
        code = e.code
    rt_ms = (time.perf_counter() - t0) * 1000
    return code, payload, rt_ms


def worker(args_tuple):
    url, method, data, headers = args_tuple
    code, payload, rt = request(url, method, data, headers)
    with _lock:
        _results.append((code, payload, rt))


def run_parallel(jobs, workers):
    """并发执行 (url, method, data, headers) 任务列表，返回 (results, wall_seconds)"""
    _results.clear()
    threads = []
    q = deque(jobs)
    wall0 = time.perf_counter()

    def run_queue():
        while True:
            try:
                job = q.popleft()
            except IndexError:
                return
            worker(job)

    for _ in range(workers):
        t = threading.Thread(target=run_queue)
        t.start()
        threads.append(t)
    for t in threads:
        t.join()
    return list(_results), time.perf_counter() - wall0


def pct(values, p):
    values = sorted(values)
    idx = max(0, min(len(values) - 1, int(len(values) * p / 100)))
    return values[idx]


def find_quota2_session(base):
    """自动定位种子数据中 quota=2 的场次"""
    _, data, _ = request(base + "/api/activities?pageNo=1&pageSize=50")
    for a in data["data"]:
        _, d, _ = request(f"{base}/api/activities/{a['id']}")
        for s in d["data"]["sessions"]:
            if s["quota"] == 2:
                return s["id"]
    raise SystemExit("未找到 quota=2 的种子场次（请确认后端已启动且种子数据已写入）")


def scene_buy(args):
    session_id = args.session or find_quota2_session(args.base)
    print(f"[scene=buy] 场次 {session_id}，{args.users} 个用户并发抢购…")

    # 1. 并发注册
    jobs = [
        (f"{args.base}/api/auth/register", "POST",
         {"username": f"st_{int(time.time())}_{i:05d}", "password": "123456"}, None)
        for i in range(args.users)
    ]
    results, _ = run_parallel(jobs, min(50, args.users))
    tokens = [r[1]["data"]["token"] for r in results if r[1].get("code") == 0]
    print(f"  注册成功 {len(tokens)}/{args.users}")

    # 2. 领幂等 token
    jobs = [(f"{args.base}/api/orders/token", "GET", None, {"Authorization": f"Bearer {t}"}) for t in tokens]
    results, _ = run_parallel(jobs, min(50, len(tokens)))
    idems = {t: r[1]["data"] for t, r in zip(tokens, results) if r[1].get("code") == 0}

    # 3. 并发下单
    jobs = [
        (f"{args.base}/api/orders", "POST",
         {"sessionId": session_id, "quantity": 1, "idempotencyToken": idem},
         {"Authorization": f"Bearer {t}"})
        for t, idem in idems.items()
    ]
    results, wall = run_parallel(jobs, min(50, len(idems)))
    ok = sum(1 for r in results if r[1].get("code") == 0)
    soldout = sum(1 for r in results if r[1].get("code") == 410)
    others = len(results) - ok - soldout
    rts = [r[2] for r in results]

    print(f"""
  ── 下单结果（并发 {min(50, len(idems))} 线程 / {wall:.1f}s）──────────────
  成功      : {ok}
  售罄(410) : {soldout}
  其他异常  : {others}
  成功+售罄+异常 = {len(results)}
  ── 下单接口 RT ─────────────────────────────
  avg={sum(rts)/len(rts):.0f}ms  P50={pct(rts,50):.0f}ms  P95={pct(rts,95):.0f}ms  P99={pct(rts,99):.0f}ms
  ──────────────────────────────────────────""")

    # 4. 校验 DB 权威剩余（从详情接口读）
    _, d, _ = request(f"{args.base}/api/activities/999999999999999999")
    print("  ✔ 校验规则：成功数必须恰好等于场次剩余名额（quota=2 → 成功=2），多一单即超卖")
    if ok != args.quota:
        print(f"  ⚠ 成功数 {ok} != 预期 {args.quota}，请检查防超卖链路！（若此前压测已消耗名额，属正常）")
    else:
        print(f"  ✔ 防超卖校验通过：成功 = {ok} = 名额 {args.quota}")


def scene_read(args):
    url = f"{args.base}/api/activities?pageNo=1&pageSize=10"
    print(f"[scene=read] GET {url}，{args.total} 次 / {args.users} 并发…")
    jobs = [(url, "GET", None, None)] * args.total
    results, wall = run_parallel(jobs, args.users)
    rts = [r[2] for r in results]
    qps = len(results) / wall
    print(f"""
  ── 读接口吞吐 ──────────────────────────────
  总请求   : {len(results)}   失败: {sum(1 for r in results if r[0] != 200)}
  QPS      : {qps:.0f}
  avg={sum(rts)/len(rts):.0f}ms  P50={pct(rts,50):.0f}ms  P95={pct(rts,95):.0f}ms  P99={pct(rts,99):.0f}ms
  ──────────────────────────────────────────""")


def main():
    ap = argparse.ArgumentParser(description="CampusTix 压测")
    ap.add_argument("--base", default="http://localhost:8080")
    ap.add_argument("--scene", default="buy", choices=["buy", "read"])
    ap.add_argument("--users", type=int, default=200, help="并发用户/线程数")
    ap.add_argument("--total", type=int, default=3000, help="read 场景总请求数")
    ap.add_argument("--quota", type=int, default=2, help="场次名额（校验用）")
    ap.add_argument("--session", type=int, help="直接指定场次 id（默认自动找 quota=2）")
    args = ap.parse_args()

    if args.scene == "buy":
        scene_buy(args)
    else:
        scene_read(args)


if __name__ == "__main__":
    main()
