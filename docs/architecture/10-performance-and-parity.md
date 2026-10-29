# 10 性能与特性不回退(D21)

> **D21:5.0 的性能与特性均不得低于 4.x。** 这是发布门槛,不是目标。

## 1. 性能门槛

### 1.1 基准工程

4.x 与 5.0 使用相同的包名 `me.ahoo.cosid`,不能放在同一个 classpath 中对比。基准因此分成两个工程,用同一组场景:

```
benchmark/
  baseline-4x/   依赖 me.ahoo.cosid:*:4.1.1(固定版本),实现场景接口
  v5/            依赖 5.0 模块,实现同一组场景
  scenarios.md   场景定义(参数、线程数、预热、迭代)
  compare        对比脚本:读取两边 JMH JSON,按 §1.3 规则输出 PASS/FAIL 报告
```

### 1.2 场景

| # | 场景 | 4.x 对照 | 指标 | 类型(D44) |
|---|---|---|---|---|
| P1 | Snowflake 毫秒 `generate`,1 / 4 / 16 线程 | `MillisecondSnowflakeId` | 吞吐 | 端到端 |
| P2 | Snowflake 秒级 `generate`,1 / 16 线程 | `SecondSnowflakeId` | 吞吐 | 端到端 |
| P3 | Snowflake + Radix62 字符串、+ Friendly 字符串 | `StringSnowflakeId` / `SnowflakeFriendlyId` | 吞吐、每次分配字节 | 端到端 |
| P4 | CosIdGenerator 80 位 Radix62 / Friendly 字符串 | `Radix62CosIdGenerator` / `FriendlyCosIdGenerator` | 吞吐、每次分配字节 | 端到端 |
| P5 | 号段,内存存储,1 / 4 / 16 / 64 线程 | `SegmentChainId` + 内存分发器 | 吞吐、p99 / p999 延迟 | 端到端 |
| P6 | 号段,step = 1(无预取) | `DefaultSegmentId` | 吞吐 | 端到端 |
| P7 | 号段,真实 MySQL / Redis / MongoDB / ZooKeeper;5.x 一律走生产路径(`CosIdRuntime` 发出的生成器,运行时打开并包装的存储) | `MySqlChainIdBenchmark` / `RedisChainIdBenchmark` / `MongoChainIdBenchmark` / `ZookeeperChainIdBenchmark` | 吞吐、p99 / p999 延迟 | 端到端 |
| P8 | 周期号段(按天)含跨期 | grouped `SegmentChainId` | 吞吐、跨期 p999 延迟 | 端到端 |
| P9 | 编解码:Radix62/36 encode/decode、Friendly parse | 各 `IdConverter` | 吞吐、每次分配字节 | 组件 |
| P10 | 存储原语延迟:reserve、acquire、renew(各后端) | 4.x 对应操作 | 平均与 p99 延迟 | 组件 |
| P11 | 启动:Spring 上下文启动到第一个 ID | 4.x starter | 耗时 | 端到端 |
| P12 | 字段填充 `IdFiller.fill` | `CosIdAccessorRegistry.ensureId` | 吞吐、每次分配字节 | 端到端 |

### 1.3 判定规则

对每个场景、每个线程数:

每行的界限取 4.x 加减其 JMH 误差(99.9% 置信区间)与 4.x 乘以容差两者中较宽的一个(D34;只用 JMH 误差时界限低于共享 runner 的噪声):

- **吞吐**:`score_5 ≥ min(score_4 − error_4, 0.95 × score_4)`。
- **延迟**:p99、p999 不高于 `max(4.x + error_4, 1.10 × 4.x)`(JMH 不给分位数误差,`error_4` 取 4.x 均值的误差)。
- **平均与单次耗时**:不高于 `max(score_4 + error_4, 1.05 × score_4)`。
- **分配**:每次操作分配字节数(`gc.alloc.rate.norm`)不高于 `max(4.x + 误差, 1.05 × 4.x, 4.x + 64 B)`;5.x 不超过 0.01 B/op 时视为通过(低于该值只是补充等低频记账的摊销,不是热路径分配)。`long` 型生成热路径为 0(< 1 B/op)。
- **启动**:不慢于 4.x 的 105%。

**端到端与组件(D44)**:门槛以端到端发号为最终指标,单个组件只要求不过度退化。场景按类型判定(`benchmark/compare` 的 `SCENARIOS` 表,按基准类名,不在表中的类按端到端判):

- **端到端行**(P1–P8、P11、P12):按上面的 D34 规则判定,超出即 FAIL。
- **组件行**(P9 编解码、P10 存储原语 `*StoreBenchmark`):在 D34 以内为 PASS;超出 D34 但吞吐与平均耗时变差不超过 20%、p99/p99.9 不超过 30%、分配不超过 `max(1.25 × 4.x, 4.x + 64 B)`(各自同样允许 4.x 的误差)为 **WARN**;再超出为 FAIL。
- 只有 FAIL 触发重跑并阻塞门槛;WARN 在报告中单独成节(哪些行、变差多少),不阻塞,组结论显示为 `PASS (n WARN)`。冒烟口径不区分类型,也没有 WARN。

一行首次失败时,两侧在新的 fork 中重跑,**重跑仍失败才算回退**(`benchmark/run-release` 在 CI 上默认如此,`compare --rerun`)。阈值的边界由 `benchmark/compare_test.py` 覆盖。

执行环境:
- **发布门槛**(D31):**只在 CI 上运行,不在本地运行**。`release-gate` workflow(手动触发)按场景组分 job,每个 job 在同一台 GitHub runner 上交替运行 4.1.1 与 5.x;存储场景使用 CI 的 service 容器;固定 JDK 21 小版本,报告记录 runner 的 CPU 型号与核数。
  CI runner 有噪声,因此 FAIL 行在新的 fork 对上重跑一次,重跑通过才算通过,两次结果都保留在报告中。结论表(每组 PASS/FAIL 与逐行对比)写入该次运行的 job summary,完整报告与原始 JMH JSON 作为 artifact 保留;报告是证据,workflow 不提交、不开 PR。需要留档时人工提交 markdown 摘要(`release-gate-summary` artifact,不含原始 JSON)到 `docs/jmh/5.0/`,4.1.1 基线到 `docs/jmh/4.1.1/release-ci.md`。
- **PR 冒烟**:CI 上运行 P1、P3、P5、P9 的短版本,容差放宽到 `score_5 ≥ 0.9 × score_4`,只用来尽早发现量级上的回退。
- **存储场景(P7、P10)**:延迟由外部服务决定。PR 中只要求两侧往返次数相同、冒烟无量级回退,最终判定在 `release-gate` workflow 中进行。

### 1.4 设计上的性能保障

| 4.x 热路径 | 5.0 做法 |
|---|---|
| Snowflake `synchronized generate`,序列耗尽时持锁 park | 短临界区发号(D25),所有等待都在锁外 |
| 每次生成都经过装饰链(Leased → ClockSync → Counted → String) | 单个 final 类;租约检查是一次 volatile 读 |
| 热路径调用全局 `CosIdEvents` | 热路径不调用 listener |
| Friendly 编码每次构造 `LocalDateTime` + `DateTimeFormatter` | 预计算字段宽度,直接写 `char[]` |
| 号段推进 head 的 CAS 加链表遍历 | 一次 `getAndIncrement` 加一次比较 |
| 号段补充时调用方被存储 I/O 的锁阻塞 | I/O 在锁外,单飞补充 |

若无锁序列器在高竞争下不及 `synchronized`(见路线图风险),以基准结果为准选择实现,但等待时仍不持锁。

## 2. 特性不回退

### 2.1 原则

- 以**能力**为单位比对,而不是以类为单位:4.x 的每一项用户可见能力,在 5.0 中都必须有等价或更强的表达方式。
- 4.x 中的缺陷、兼容代码、内部实现细节不属于"特性"(例如双存储格式、v1 API、全局单例)。
- 对照表(§2.2)是发布门槛:每一行都必须是"保留"或"等价替代",并且有测试证明。

### 2.2 特性对照表

见 [11-feature-parity.md](11-feature-parity.md)。
