# CosId 5.0 路线图

> 设计见 [README.md](README.md)。4.x 的历史路线图见 [../ROADMAP.md](../ROADMAP.md)。

## 1. 工作方式

- **分支**:从 `main` 切出长期分支 `5.x`(D10)。`main` 继续发布 4.x 修复,直到 5.0 GA 后 `5.x` 合回 `main`,4.x 转为 `4.x` 维护分支。
- **会话**:每个工作包一个子会话、一个 worktree、一个指向 `5.x` 的 PR。总调度会话负责拆分、审查、合入,并同步更新本文档与设计文档。
- **合入门槛**:
  - `./gradlew check` 全绿;相关 TCK 通过;patch 覆盖率 ≥ 80%;
  - 公开 API 的变化必须先更新对应的设计文档;
  - 涉及性能场景的 PR 附上 PR 冒烟基准结果(10 §1.3);
  - 涉及特性对照表(11)的行,PR 中注明对应的测试。
- **设计变更**:实施中发现设计问题时,先在设计文档里提修改,经总调度审查后再改代码。
- **并行规则**:同一时刻,同一组文件只有一个会话在写;R0 冻结的接口在后续阶段只能由总调度修改。

## 2. 阶段总览

```
R0 骨架、接口冻结、4.x 基线基准
 ├─→ R1-A 时间型 + 编解码 ────────┐
 ├─→ R1-B 号段 + 周期号段 ────────┼─→ R3 运行时装配 ─→ R3-S Spring ─┬─→ R4-A remote 服务与客户端
 └─→ R1-C 租约客户端 + TCK + 内存存储 ┤                                 ├─→ R4-B 集成适配器
                                  └─→ R2 四个后端(并行)──────────────┼─→ R4-C 迁移工具
                                                                     └─→ R5 最终基准、特性验收、文档 ─→ R6 发布
```

## 3. 阶段明细

### R0 骨架、接口冻结与基线(1 个会话,串行)

| 交付物 | 验收 |
|---|---|
| 切出 `5.x`;从构建中删除全部 4.x 模块(代码留在 `main` 作参考) | `settings.gradle.kts` 只包含新的目录结构 |
| 新目录结构与构建约定:目录无前缀、artifactId 生成规则、Java 21、无 Lombok、Checkstyle/SpotBugs/JaCoCo、依赖方向校验任务 | 故意违反依赖方向的样例会让构建失败 |
| `core` 的全部公开接口与 record(无实现或最小实现),以及 `module-info` | 与 03–07 文档中的签名一致,由总调度逐项审查 |
| `store/tck` 契约类骨架(用例名与断言意图,可先 `@Disabled`) | 用例清单与 06 §3 一致 |
| `benchmark/baseline-4x`:在 4.1.0 上实现 10 §1.2 的全部场景,产出基线 JSON;`benchmark/v5` 场景骨架;`compare` 脚本 | 基线结果存入 `docs/jmh/4.1.0/` |
| 更新 `AGENTS.md`(5.x 分支) | 新的命令与布局可用 |

**里程碑:接口冻结。** 之后的接口修改必须经总调度审查。

### R1 核心算法(3 个会话并行,依赖 R0)

| 包 | 内容 | 验收 |
|---|---|---|
| R1-A | `Layout`、`IdParts`、`TimeSource`、序列器(无锁 CAS、绑定内状态、`CLOSED` 哨兵)、`ClockRegressionPolicy`、`TimeBasedGenerator`、`Radix` / `Friendly` / `DatePrefix` / `PartsCodec`、`IdGenerators` | 回拨策略、序列耗尽、窗口上下界、换绑、关闭竞争、秒级布局、JS 安全、`COSID_80`;编解码往返属性测试;多线程唯一性压力测试;**P1–P4、P9 不低于基线** |
| R1-B | `SegmentGenerator`(单次读取游标、就绪队列、单飞补充、自适应规模、`rangeTtl`、`CLOSED`)、`PeriodicSegmentGenerator`、`Period`、`GroupedCodec` | 使用 `InMemoryStore` 与 TCK 的故障注入包装器:锁内无 I/O(测试断言)、补充失败退避、超时后的区间仍然可用、`close` 语义、跨期预热无同步 I/O、封存后拒绝;多线程唯一性;**P5、P6、P8 不低于基线** |
| R1-C | `InMemoryStore`(通过全部 TCK)、TCK 用例实现与故障注入包装器、`LeaseKeeper`、`SlotHandle` / `SlotBinding`、`StaticSlot`(本地窗口预写) | TCK 全绿;`ManualTimeSource` 下验证续约、窗口推进、续约响应丢失重放、丢失后重新获取、`Exhausted`、关闭顺序;StaticSlot 崩溃加回拨不重号 |

### R2 存储后端(4 个会话并行,依赖 R1-C 的 TCK)

| 包 | 内容 | 验收 |
|---|---|---|
| R2-JDBC | MySQL、MariaDB、PostgreSQL、SQL Server、H2 方言;`schema.sql`;`JdbcDialect` SPI | 各数据库在 Testcontainers 下 TCK 全绿;**P7(MySQL)、P10 不低于基线** |
| R2-Redis | `RedisScripts` + Lettuce 实现;两段 Lua;`WAIT` auto(专用连接) | 单机、主从(验证 `WAIT`)、Cluster(hash tag)下 TCK 全绿;**P7(Redis)、P10 不低于基线** |
| R2-Mongo | 两个集合、`$$NOW`、`majority` | 副本集下 TCK 全绿;P10 |
| R2-ZK | 版本化写,fence 存在数据中,以 `mtime` 为时钟,异步批量选槽 | TCK 全绿;P10(含 1024 个槽位的 acquire 耗时) |

每个后端在 CI 中各有一个集成测试 job,并检查 TCK 用例确实执行了(沿用 4.x 的"契约测试已运行"检查)。

### R3 运行时装配(1 个会话,依赖 R1;可与 R2 并行)

| 交付物 | 验收 |
|---|---|
| `RuntimeSpec` 及各子 spec、`CosIdRuntime.start/close`、启动回滚、`StoreProvider` 发现、ownerKey 推导、`IdFiller`、`GeneratorListener` 分发、`describe()` | 基于 `InMemoryStore` 的端到端测试:混合策略、混合存储、同一 JVM 中两个运行时不共享槽位、启动失败回滚、关闭顺序;**P12 不低于基线** |

### R3-S Spring Boot 集成(1 个会话,依赖 R3)

| 交付物 | 验收 |
|---|---|
| 不可变配置绑定、`CosIdProperties → RuntimeSpec` 纯函数、`RuntimeSpecCustomizer`、自动配置(目标 ≤ 15 个类)、Micrometer、端点、健康检查、`SpringDataRedisScripts`、starter、配置元数据 | `ApplicationContextRunner` 测试覆盖 07 §4;Spring Boot 兼容矩阵 job;原生镜像提示测试;**P11 不低于基线** |

### R4 外围(3 个会话并行)

| 包 | 依赖 | 内容 | 验收 |
|---|---|---|---|
| R4-A | R2 任一后端 + R3-S | `server`(`/v5` API、可选发号端点、鉴权默认开启、限流与 503)、`store/remote` 客户端 | `store/remote` 对真实 server 跑 TCK 全绿;鉴权、限制测试;Docker 镜像构建 |
| R4-B | R3-S | `integrations`(Jackson、MyBatis、Spring Data JDBC、Activiti/Flowable、Axon) | 每个集成一个最小端到端测试 |
| R4-C | R2 + R3 | `migration`(plan / apply / verify / config / purge) | 09 §5 的全部用例,包括用 4.1.x jar 写入真实数据、遗漏实例模拟 |

### R5 验收与文档(2 个会话并行)

| 包 | 内容 | 验收 |
|---|---|---|
| R5-A | 在固定物理机上运行全部 12 个场景的发布门槛基准;逐行核对特性对照表 | 10 §1.3 全部 PASS,报告存入 `docs/jmh/5.0/`;11 中每一行都有测试链接,没有「待定」 |
| R5-B | 文档站 `documentation/`(中英文)重写、迁移指南、`examples/`、README、`extras/sharding` 迁入 | 文档站构建通过;示例可运行 |

### R6 发布

- `5.0.0-M1`:R2 与 R3 完成后发布,此时可以编程使用。
- `5.0.0-RC1`:R4 完成后发布。
- `5.0.0`:R5 全部通过、RC 无阻塞问题后发布。GA 时 `5.x` 合回 `main`。

## 4. 波次与并行度

| 波次 | 并行会话 | 前置条件 |
|---|---|---|
| 1 | R0 | 设计完成 |
| 2 | R1-A、R1-B、R1-C | R0 合入 |
| 3 | R2-JDBC、R2-Redis、R2-Mongo、R2-ZK、R3 | R1-C 合入(R3 还需要 R1-A、R1-B) |
| 4 | R3-S、R4-C | R3 合入(R4-C 还需要 R2) |
| 5 | R4-A、R4-B | R3-S 合入 |
| 6 | R5-A、R5-B | 功能冻结 |

## 5. 进度

| 阶段 | 状态 | PR |
|---|---|---|
| 设计 | 完成:D1–D24 已定 | — |
| R0 | 已合入(P5/P12 基线待在空闲机器上干净重跑) | [#1308](https://github.com/Ahoo-Wang/CosId/pull/1308) |
| R1-C | 已合入 | [#1309](https://github.com/Ahoo-Wang/CosId/pull/1309) |
| R1-B | 已合入 | [#1310](https://github.com/Ahoo-Wang/CosId/pull/1310) |
| R1-A | 已合入 | [#1312](https://github.com/Ahoo-Wang/CosId/pull/1312) |
| R2-Mongo | 已合入 | [#1311](https://github.com/Ahoo-Wang/CosId/pull/1311) |
| R2-Redis | 已合入 | [#1313](https://github.com/Ahoo-Wang/CosId/pull/1313) |
| R2-JDBC | 审查中:acquire 改走 CAS 路径 | [#1314](https://github.com/Ahoo-Wang/CosId/pull/1314) |
| R2-ZK | 审查中 | [#1315](https://github.com/Ahoo-Wang/CosId/pull/1315) |
| 后续修复 | R2-Mongo:4.4 上命名空间并发 upsert;R1-B:小 step 下 `maxBatch` 默认值 | |
| R3 | 进行中 | |
| R3-S | 未开始 | |
| R4-A / R4-B / R4-C | 未开始 | |
| R5-A / R5-B | 未开始 | |
| R6 | 未开始 | |

## 6. 风险

| 风险 | 应对 |
|---|---|
| 无锁序列器在高竞争下 CAS 失败率高,吞吐不如 4.x 的 `synchronized` | R1-A 先做基准;以结果选择实现,但等待时一律不持锁 |
| 窗口检查与绑定读取给 Snowflake 热路径增加开销 | 绑定是一次 volatile 读,窗口检查是两次比较;由 P1 门槛把关 |
| ZooKeeper `mtime` 在 leader 切换时跳变 | 安全性不依赖它(D22);只影响过期判断,由 TCK 与故障测试覆盖 |
| Redis failover 丢写 | 文档明确声明前提;默认 `WAIT` auto |
| 迁移遗漏某种 4.x key 格式或遗漏运行中的实例 | 用真实 4.x jar 写入的数据做测试;静止观察加封存源 |
| 5.x 长期分支与 `main` 上的 4.x 修复分叉 | 4.x 修复只涉及旧代码,不需要合入 5.x;涉及算法正确性的修复由总调度评估是否影响新设计 |
