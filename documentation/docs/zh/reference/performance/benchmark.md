# 基准测试

**5.0 不得比 4.x 慢**（D21）。这是发布门槛，而不是目标。每个场景都分别在已发布的 4.1.1 jar 和 5.0 上运行，由对比结果决定。

## 测量方式

4.x 与 5.0 共用包名 `me.ahoo.cosid`，因此不能放在同一个 classpath 上。基准是两个 JMH 工程，场景相同，类名与方法名也相同
（`<Scenario>Benchmark.<op>_t<threads>`）：

```
benchmark/
  baseline-4x/   me.ahoo.cosid:*:4.1.1
  v5/            the 5.0 modules
  scenarios.md   the scenarios: parameters, threads, warm-up, iterations
  compare        reads both JMH JSON results and prints PASS / FAIL per row
```

| # | 场景 | 指标 |
|---|---|---|
| P1 | Snowflake 毫秒，1 / 4 / 16 线程 | 吞吐量 |
| P2 | Snowflake 秒，1 / 16 线程 | 吞吐量 |
| P3 | Snowflake + Radix62 文本、+ Friendly 文本 | 吞吐量、内存分配 |
| P4 | 80 位 CosId Radix62 / Friendly | 吞吐量、内存分配 |
| P5 | 基于内存存储的号段，1 / 4 / 16 / 64 线程 | 吞吐量、p99 / p99.9 |
| P5 参照 | 一个共享的 `AtomicLong`，1 / 4 / 16 / 64 线程（只在 5.x 侧运行，不判定）：报告 P5 两侧相对它的比例 | 吞吐量 |
| P6 | 号段，step 1，不预取 | 吞吐量 |
| P7 | 基于 MySQL / Redis / MongoDB / ZooKeeper 的号段 | 吞吐量、p99 / p99.9 |
| P8 | 跨周期切换的周期号段（按天） | 吞吐量、切换时的 p99.9 |
| P9 | 编解码器：Radix62/36 编码与解码、Friendly | 吞吐量、内存分配 |
| P10 | 存储原语：reserve、acquire、renew，按后端 | 平均与 p99 延迟 |
| P11 | Spring 上下文启动到第一个 ID | 时间 |
| P12 | `IdFiller.fill` | 吞吐量、内存分配 |
| P13 | UUIDv7（只在 5.x 侧运行，只报告趋势，不判定），1 / 4 / 16 线程 | 吞吐量 |

每行的界限取 4.x 加减其误差（JMH 的 99.9% 置信区间）与 4.x 乘以容差两者中较宽的一个：吞吐量与平均耗时 5%，p99 / p99.9 取 10% 与 +100 ns 中较大者（D50：亚微秒级的尾延迟随计时分辨率与 runner 噪声的变动远超 10%），内存分配取 5% 与 +64 B 中较大者。首次失败的行会在新的 fork 中重跑，重跑仍失败才算回退。存储场景（P7、P10）的两侧各运行两次，按 ABBA 顺序（4.x、5.x、5.x、4.x），使任一侧都不总是紧跟在另一侧之后访问存储。门槛以端到端发号为准（D44）：组件行（P9 编解码、P10 存储原语）超出上述界限时只给出警告，警告的上限为吞吐与平均耗时 20%、p99 / p99.9 取 30% 与 +100 ns 中较大者、内存分配取 25% 与 +64 B 中较大者，再超出才失败。
门槛运行在 GitHub 的共享 runner 上（D31）：同一场景的两侧在同一台 runner 上交替运行，按上述容差相互比较；与其他 runner 或其他时间的数据比较没有意义，那些都是噪声。

自行运行：

```bash
benchmark/run-smoke P5 P12                          # 每个场景依次运行 4.x 与 5.0，然后 compare --smoke
./gradlew :benchmark:v5:jmh -PjmhProfile=release    # 发布门槛设置；-PjmhIncludes=A,B 缩小范围
benchmark/compare BASELINE.json CANDIDATE.json
```

## 5.0 结果

<!-- R2-perf-01: the summary of the gate on the GA commit, committed to docs/jmh/5.0.0/, goes here. -->

候选发布版通过了门槛：第七次对照 4.1.1 运行（[run 40832851823](https://github.com/Ahoo-Wang/CosId/actions/runs/40832851823)），8 组全部通过，
3 个组件行 WARN（D44）。端到端的号段链在单线程、step 10 下的发号速度，4.x → 5.0：

| 存储 | 4.1.1（ops/µs） | 5.0（ops/µs） |
|---|---|---|
| MongoDB | 1.75 | 392 |
| MySQL | 0.47 | 209 |
| Redis | 0.86 | 211 |
| ZooKeeper | 0.28 | 405 |

::: info GA
门槛会在 GA 提交上再跑一次；其摘要（`report.md`，以及 4.1.1 一侧的 `baseline-4x.md`）会提交到
[`docs/jmh/`](https://github.com/Ahoo-Wang/CosId/tree/main/docs/jmh)（`docs/jmh/5.0.0/`），并在此处汇总。
:::

## 历史结果

`docs/jmh/` 中也保存了 4.x 的结果（基于 MySQL 与 Redis 的 `SegmentChainId`），仅供参考。它们是在其他硬件上测得的，与发布门槛不可比。
