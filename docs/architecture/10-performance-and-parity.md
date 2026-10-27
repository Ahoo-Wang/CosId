# 10 性能与特性不回退(D21)

> **D21:5.0 的性能与特性均不得低于 4.x。** 这是发布门槛,不是目标。

## 1. 性能门槛

### 1.1 基准工程

4.x 与 5.0 使用相同的包名 `me.ahoo.cosid`,不能放在同一个 classpath 中对比。基准因此分成两个工程,用同一组场景:

```
benchmark/
  baseline-4x/   依赖 me.ahoo.cosid:*:4.1.0(固定版本),实现场景接口
  v5/            依赖 5.0 模块,实现同一组场景
  scenarios.md   场景定义(参数、线程数、预热、迭代)
  compare        对比脚本:读取两边 JMH JSON,按 §1.3 规则输出 PASS/FAIL 报告
```

### 1.2 场景

| # | 场景 | 4.x 对照 | 指标 |
|---|---|---|---|
| P1 | Snowflake 毫秒 `generate`,1 / 4 / 16 线程 | `MillisecondSnowflakeId` | 吞吐 |
| P2 | Snowflake 秒级 `generate`,1 / 16 线程 | `SecondSnowflakeId` | 吞吐 |
| P3 | Snowflake + Radix62 字符串、+ Friendly 字符串 | `StringSnowflakeId` / `SnowflakeFriendlyId` | 吞吐、每次分配字节 |
| P4 | CosIdGenerator 80 位 Radix62 / Friendly 字符串 | `Radix62CosIdGenerator` / `FriendlyCosIdGenerator` | 吞吐、每次分配字节 |
| P5 | 号段,内存存储,1 / 4 / 16 / 64 线程 | `SegmentChainId` + 内存分发器 | 吞吐、p99 / p999 延迟 |
| P6 | 号段,step = 1(无预取) | `DefaultSegmentId` | 吞吐 |
| P7 | 号段,真实 MySQL / Redis | `MySqlChainIdBenchmark` / `RedisChainIdBenchmark` | 吞吐、p99 / p999 延迟 |
| P8 | 周期号段(按天)含跨期 | grouped `SegmentChainId` | 吞吐、跨期 p999 延迟 |
| P9 | 编解码:Radix62/36 encode/decode、Friendly parse | 各 `IdConverter` | 吞吐、每次分配字节 |
| P10 | 存储原语延迟:reserve、acquire、renew(各后端) | 4.x 对应操作 | 平均与 p99 延迟 |
| P11 | 启动:Spring 上下文启动到第一个 ID | 4.x starter | 耗时 |
| P12 | 字段填充 `IdFiller.fill` | `CosIdAccessorRegistry.ensureId` | 吞吐、每次分配字节 |

### 1.3 判定规则

对每个场景、每个线程数:

- **吞吐**:`score_5 ≥ score_4 − error_4`(JMH 99.9% 置信区间)。
- **延迟**:p99、p999 不高于 4.x 加上 4.x 的误差。
- **分配**:每次操作分配字节数(`gc.alloc.rate.norm`)不高于 4.x 加上误差;5.x 不超过 0.01 B/op 时视为通过(低于该值只是补充等低频记账的摊销,不是热路径分配)。`long` 型生成热路径为 0。
- **启动**:不慢于 4.x 的 105%。

执行环境:
- **发布门槛**(D31):**只在 CI 上运行,不在本地运行**。`release-gate` workflow(手动触发)按场景组分 job,每个 job 在同一台 GitHub runner 上交替运行 4.1.0 与 5.x;存储场景使用 CI 的 service 容器;固定 JDK 21 小版本,报告记录 runner 的 CPU 型号与核数。
  CI runner 有噪声,因此 FAIL 行在新的 fork 对上重跑一次,重跑通过才算通过,两次结果都保留在报告中。汇总报告以 PR 形式存入 `docs/jmh/5.0/`,4.1.0 基线同时刷新到 `docs/jmh/4.1.0/`。
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
