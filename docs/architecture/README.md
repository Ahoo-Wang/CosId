# CosId 5.0 架构设计

> 状态:**R0–R4、R5-B 已合入;R5-A 发布门槛在 CI 上运行;RC 前全面审查进行中**(2026-10-27)。进度见 [roadmap.md](roadmap.md) §5。

CosId 5.0 是一次完全重构:**不考虑兼容性,以理想架构为目标,不保留任何兼容代码**。
4.x 存储中的数据由离线迁移工具一次性迁入([09-migration.md](09-migration.md));这是 5.0 与 4.x 之间唯一的连接点。

## 文档索引

| 文档 | 内容 |
|---|---|
| [01-principles.md](01-principles.md) | 第一性原理、4.x 审查结论、设计原则 |
| [02-modules.md](02-modules.md) | 模块与目录布局、依赖规则、包结构、构建约定 |
| [03-core-model.md](03-core-model.md) | `IdGenerator`/`IdCodec`、`Layout`、`TimeBasedGenerator`、序列器、时钟策略 |
| [04-segment.md](04-segment.md) | `RangeStore`、`SegmentGenerator`、补充调度、`PeriodicSegmentGenerator` |
| [05-machine-lease.md](05-machine-lease.md) | `SlotLeaseStore`、租约不变式、`LeaseKeeper`、`StaticSlot` |
| [06-stores.md](06-stores.md) | 存储 SPI、各后端实现与数据结构、TCK |
| [07-runtime-and-spring.md](07-runtime-and-spring.md) | `CosIdRuntime`、装配、可观测性、字段填充、Spring Boot 集成与配置模型 |
| [08-remote.md](08-remote.md) | 远程租约/区间服务、可选发号服务、HTTP API |
| [09-migration.md](09-migration.md) | 4.x → 5.0 离线迁移工具 |
| [10-performance-and-parity.md](10-performance-and-parity.md) | 性能基准、判定规则、不回退门槛 |
| [11-feature-parity.md](11-feature-parity.md) | 4.x → 5.0 特性对照表 |
| [roadmap.md](roadmap.md) | 阶段划分、会话分工、验收标准、里程碑 |

## 文档与代码的关系

R0 冻结接口之后,**公开签名以 `core` 代码为准**,本文档描述语义、不变式与设计理由。
R0 补充的类型(`CodecSpec`、`PartsCodecSpec`、`GroupedCodecSpec`、`RuntimeOptions`、`HostResolver`、`LeaseKeeper` / `StaticSlot` 的构造器、
`NamespaceMismatchException`、各 `DEFAULT` 常量等)见代码及其 Javadoc。

## 决策记录

| # | 决策 | 状态 |
|---|---|---|
| D1 | 不考虑兼容性;不保留任何 4.x 兼容代码(双格式、v1 API、tombstone 配置);提供离线迁移工具 | 已定 |
| D2 | 模块目录名不带 `cosid-` 前缀(如 `core/`、`store/jdbc/`) | 已定 |
| D3 | 版本 5.0;Java 包名保留 `me.ahoo.cosid`;Java 基线 21 | 已定 |
| D4 | 保留 80 位 CosIdGenerator,实现为 `Layout.COSID_80` + `PartsCodec` | 已定 |
| D5 | 按期分组号段保留为一等功能,隔离在 `PeriodicSegmentGenerator` | 已定 |
| D6 | Redis 存储不依赖 Spring:最小执行接口 + Lettuce 原生实现,Spring Data 适配放在 autoconfigure;`WAIT` 默认 auto | 已定 |
| D7 | 分片算法移到 `extras/sharding`,随主版本发布,只依赖 core 的 `Layout` | 已定 |
| D8 | 远程服务以租约/区间服务为主,发号端点可选;旧 v1/v2 API 全部删除 | 已定 |
| D9 | 不保留 `IntegerIdGenerator`、`UncertaintyIdGenerator` 等类,能力由 `IdGenerators` 组合函数提供(按 D21 修订);JS 安全改为 `Layout` 预设 | 已定 |
| D10 | 在长期分支 `5.x` 上清场重建;RC 前(2026-10-27)分支改名:原 `main` → `4.x`(维护线,与 `1.x`/`2.x`/`3.x` 一致),`5.x` → `main`(默认分支) | 已定 |
| D11 | 发布 artifactId 保留 `cosid-` 前缀,由构建约定从目录路径生成(`store/jdbc` → `cosid-store-jdbc`) | 已定 |
| D12 | `core` 提供 JPMS `module-info`,只导出 API 包 | 已定 |
| D13 | 新代码不使用 Lombok(record + 显式代码) | 已定 |
| D14 | 只有一个 Spring Boot starter;用户自行添加 store 依赖;删除 Gradle feature variants | 已定 |
| D15 | ~~集成适配器合并为一个 `integrations` 模块~~,已被 D29 取代 | 已废止 |
| D16 | 默认 Snowflake `Layout` 与 epoch 沿用 4.x(41/10/12 位,epoch `1577203200000`),保证迁移后 ID 数值连续 | 已定 |
| D17 | 运行时不可变:生成器在启动时确定,端点只读 | 已定 |
| D18 | 每个运行时持有一个机器号槽位,所有时间型生成器共用 | 已定 |
| D19 | 号段契约:允许间隙,绝不重复 | 已定 |
| D20 | 命名空间级参数 `tolerance`(默认 1s):租约窗口上界超出存储过期时间的量;只影响可用性,不影响唯一性(按评审修订,原 `maxSkew`) | 已定 |
| D21 | **性能与特性均不得低于 4.x**:以能力为单位逐项对照,基准逐场景对比,作为发布门槛 | 已定 |
| D22 | 租约即时间窗口:唯一性只依赖存储内 `floor_{k+1} ≥ ceiling_k`,不依赖任何时钟(评审后新增,见 05) | 已定 |
| D23 | 号段 key 只封存、不物理删除;物理清理只能离线进行(评审后新增,见 04) | 已定 |
| D25 | Snowflake 发号采用短临界区(锁内不等待),而非无锁 CAS;依据 R1-A 基准(P1 t16) | 已定 |
| D26 | MongoDB 最低版本 6.0(CI 覆盖 6.0 与 8.0) | 已定 |
| D27 | 其余后端最低版本:Redis 7.0、MySQL 8.4 LTS、MariaDB 10.11 LTS、PostgreSQL 15、ZooKeeper 3.8;SQL Server 2019、H2 2.x 不变 | 已定 |
| D28 | 低于最低版本的若干版本各实测一次 TCK(非阻塞),结果写入"实测兼容性(不承诺)"文档,不加兼容代码 | 已定 |
| D29 | 集成适配器按第三方依赖拆分为 `integrations/{jackson,mybatis,spring-data-jdbc,activiti,flowable,axon}`,artifactId 为 `cosid-*`(与 4.x 坐标一致);每个模块只依赖自己的第三方库;Axon 未绑定时报错 | 已定 |
| D30 | `cosid-server` 的 HTTP API 不带版本前缀(`/ranges:reserve`、`/slots:*`、`/ids/*`) | 已定 |
| D31 | 基准测试不在本地运行;发布门槛在 CI(`release-gate` workflow,同一 runner 上交替运行 4.1.0 与 5.x)上判定 | 已定 |
| D24 | 不提供租约丢失后继续发号的开关(P1);运行时删除生成器改为启动期的 `disabled` 配置(P2) | 已定 |

## 术语

| 术语 | 含义 |
|---|---|
| 槽位(slot) | 4.x 的"机器号",`[0, 2^nodeBits)` 中的一个整数 |
| 租约(lease) | 对一个槽位上一段时间窗口 `(floor, ceiling]` 的独占使用权,带过期时间与 fence |
| floor / ceiling | 持有者可发出的时间戳下界(不含)与上界(含);相继持有者的窗口互不重叠 |
| 区间(range) | 从存储原子预留的 `[start, end)` |
| 存储时钟(store clock) | 由存储自身提供的时间,所有过期判断以它为准 |
| incarnation | 每次进程启动生成的 UUID,区分同一逻辑身份的不同进程 |
