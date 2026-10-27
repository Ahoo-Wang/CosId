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
| R5-B | 文档站 `documentation/`(中英文)重写、迁移指南、`examples/`、README、`extras/sharding` 迁入、汇总各后端"实测兼容性(不承诺)"表 | 文档站构建通过;示例可运行 |

### R6 发布

- `5.0.0-M1`:R2 与 R3 完成后发布,此时可以编程使用。
- `5.0.0-RC1`:R4 完成后发布。
- `5.0.0`:R5 全部通过、RC 无阻塞问题后发布。GA 时 `5.x` 合回 `main`。

## 4. 波次与并行度

| 波次 | 并行会话 | 前置条件 | 状态 |
|---|---|---|---|
| 1 | R0 | 设计完成 | ✅ |
| 2 | R1-A、R1-B、R1-C | R0 合入 | ✅ |
| 3 | R2-JDBC、R2-Redis、R2-Mongo、R2-ZK、R3 | R1-C 合入(R3 还需要 R1-A、R1-B) | ✅ |
| 4 | R3-S、R4-C | R3 合入(R4-C 还需要 R2) | ✅ |
| 5 | R4-A、R4-B | R3-S 合入 | ✅ |
| 6 | R5-A、R5-B | 功能冻结 | 待启动 |

## 5. 进度

> 更新于 2026-10-27。`5.x` 上已合入 19 个 PR;所有提交作者为 Ahoo Wang,提交说明为英文,不带 `Co-authored-by`。

### 5.1 阶段

| 阶段 | 状态 | PR |
|---|---|---|
| 设计 | ✅ 完成,决策 D1–D28 已定,实施偏差已逐项写回 01–11 | — |
| R0 骨架、接口冻结、基线 | ✅ 已合入 | [#1308](https://github.com/Ahoo-Wang/CosId/pull/1308) |
| R1-A 时间型生成器与编解码 | ✅ 已合入(D25:短临界区) | [#1312](https://github.com/Ahoo-Wang/CosId/pull/1312) |
| R1-B 号段与周期号段 | ✅ 已合入 | [#1310](https://github.com/Ahoo-Wang/CosId/pull/1310) |
| R1-C 租约客户端与 TCK | ✅ 已合入 | [#1309](https://github.com/Ahoo-Wang/CosId/pull/1309) |
| R2-JDBC | ✅ 已合入 | [#1314](https://github.com/Ahoo-Wang/CosId/pull/1314)、[#1321](https://github.com/Ahoo-Wang/CosId/pull/1321) |
| R2-Redis | ✅ 已合入 | [#1313](https://github.com/Ahoo-Wang/CosId/pull/1313)、[#1318](https://github.com/Ahoo-Wang/CosId/pull/1318) |
| R2-Mongo | ✅ 已合入 | [#1311](https://github.com/Ahoo-Wang/CosId/pull/1311)、[#1316](https://github.com/Ahoo-Wang/CosId/pull/1316) |
| R2-ZK | ✅ 已合入 | [#1315](https://github.com/Ahoo-Wang/CosId/pull/1315)、[#1320](https://github.com/Ahoo-Wang/CosId/pull/1320)、[#1322](https://github.com/Ahoo-Wang/CosId/pull/1322) |
| R3 运行时装配 | ✅ 已合入 | [#1319](https://github.com/Ahoo-Wang/CosId/pull/1319) |
| R3-S Spring Boot 集成 | ✅ 已合入 | [#1323](https://github.com/Ahoo-Wang/CosId/pull/1323) |
| R4-A 远程服务与客户端 | ✅ 已合入 | [#1328](https://github.com/Ahoo-Wang/CosId/pull/1328) |
| R4-B 集成适配器 | ✅ 已合入 | [#1327](https://github.com/Ahoo-Wang/CosId/pull/1327) |
| R4-C 离线迁移工具 | ✅ 已合入 | [#1324](https://github.com/Ahoo-Wang/CosId/pull/1324) |
| R5-A 发布门槛基准与特性验收 | ⏳ 待启动 | |
| R5-B 文档站、示例、迁移指南 | ⏳ 待启动 | |
| R6 发布 | ⏳ | |

后续修复(已合入):`maxBatch` 默认值 [#1317](https://github.com/Ahoo-Wang/CosId/pull/1317)、Mongo 并发 upsert 与重复身份误报 [#1316](https://github.com/Ahoo-Wang/CosId/pull/1316)、
ZK 退避中断 [#1320](https://github.com/Ahoo-Wang/CosId/pull/1320)、ZK acquire 分配量 [#1322](https://github.com/Ahoo-Wang/CosId/pull/1322)、Redis 最低 7.0 [#1318](https://github.com/Ahoo-Wang/CosId/pull/1318)。

### 5.2 里程碑

| 里程碑 | 条件 | 状态 |
|---|---|---|
| `5.0.0-M1` | R2 + R3 合入,可编程使用 | 条件已满足,待决定是否发布 |
| `5.0.0-RC1` | R4 全部合入 | 条件已满足,待决定是否发布 |
| `5.0.0` | R5 全部通过,RC 无阻塞问题;`5.x` 合回 `main` | 未开始 |

### 5.3 进入 R5 前的遗留事项

| # | 事项 | 归属 |
|---|---|---|
| L1 | 4.1.0 基线 P5、P12 在空闲机器上干净重跑(R0 时机器负载过高) | R5-A |
| L2 | 存储场景 P7、P10 在安静机器上最终判定;已知待复核:ZK renew p99、ZK reserve sample 分配、Mongo P10 | R5-A |
| L3 | Redis `ReplicaWaitTest` 偶发失败的修复 PR(R2-Redis 正在本地连跑验证) | R2-Redis 后续 PR |
| L4 | 集成测试稳定性:每个集成测试类在 CI 上连跑 N 次(建议 20)无失败,作为 R5-A 验收项;实施期间已出现 4 个依赖时序的偶发失败(Mongo 2、ZK 1、Redis 1) | R5-A |
| L5 | 主 `check` job 与各存储 job 重复跑容器测试,需要一个跳过容器的开关去重 | R5-A |
| L6 | 文档站写明 ZK 槽位节点人工重建的前提、Redis 持久化前提、各后端"实测兼容性(不承诺)"汇总 | R5-B |
| L7 | ~~`config` 子命令对 4.x proxy 配置生成的 `remote` 存储与 R4-A 选项名一致~~ 已在 #1324 中完成 | R4-C ✅ |

## 6. 风险与经验

| 风险 / 经验 | 应对 |
|---|---|
| 无锁序列器在高竞争下不及 4.x 的 `synchronized` | **已发生**:t16 只有 4.x 的 92–97%;按 D25 改为短临界区,与 4.x 持平,等待一律在锁外 |
| 窗口检查与绑定读取给 Snowflake 热路径增加开销 | 绑定是一次 volatile 读,窗口检查是两次比较;P1 已证明与 4.x 持平 |
| ZooKeeper `mtime` 在 leader 切换时跳变 | 安全性不依赖它(D22);failover 测试已覆盖 |
| Redis failover 丢写 | 文档声明前提;默认 `WAIT` auto,专用连接 |
| 迁移遗漏 4.x key 格式或运行中的实例 | 用真实 4.1.0 jar 写入的数据做测试;静止观察 + 先封存再写目标 |
| 共享机器上的基准噪声 | **已发生**:负载常达 20–30;PR 用 `run-smoke` 同次交替运行,存储场景推迟到 R5-A,发布门槛只在固定物理机上判定 |
| 并行会话改同一批共享文件(settings、bom、CI、AGENTS.md)导致反复冲突 | 只追加自己的行;合入一个后,其余 PR 必须同步 `5.x` 并在合并后的结果上重新跑 CI 才合入 |
| 子会话 worktree 从 `main` 切出 | 每份任务说明第一步都要求从 `origin/5.x` 切分支并核对 `settings.gradle.kts`;下发 reset 类指令前先核实对方分支状态 |
| 提交身份与 `Co-authored-by` | 已统一为 Ahoo Wang,`5.x` 历史已改写;squash 合并始终显式传 `--body` |
| 5.x 长期分支与 `main` 上的 4.x 修复分叉 | 4.x 修复只涉及旧代码;涉及算法正确性的修复由总调度评估是否影响新设计 |
