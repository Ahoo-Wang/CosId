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
| D32 | 4.x → 5.0 除迁移工具外,另有可选的**停机手工迁移**:时间型生成器在布局不变时停机升级(停机 ≥ 主机最大时钟偏差,至少 1 分钟,并核对 NTP);普通号段停机后取 4.x `last_max_id`,设 `initial-offset = last_max_id + 1`(建议手工封存 4.x 值);周期号段、自定义布局/秒级 epoch、key 多或需要防护时用工具。只改文档,不新增配置项(租约模式的命名空间级 `not-before` 留待 RC 后评估) | 已定 |
| D33 | 5.0 的第一个公开版本为 `5.0.0-alpha.1`(取代原计划的 RC1),以 GitHub **pre-release** 发布,**不做兼容性承诺**:API、配置、存储格式与 server 接口在 alpha 之间及到 GA 之前都可能变化,不提供 alpha 之间的数据迁移,不建议用于生产。Docker 不打 `latest`。文档站随 alpha.1 部署:release 发布后用 tag `v5.0.0-alpha.1` 手动触发 `document-deploy`(它自动只响应 `released`),站点导航保留 4.x 文档入口,页面显示 alpha 提示。4.x 的 `document-deploy` 去掉 release 触发、只保留手动触发(E-M1)。D21 发布门槛仍是 RC/GA 的前提 | 已定 |
| D34 | D21 发布门槛的判定口径(2026-10-28 用户裁定):5.x 与 4.x 在同一 runner 上交替运行,每行的上限取 `max(4.x ± 其 JMH 误差, 4.x × (1 ± 容差))`。容差:吞吐与平均/单次耗时 5%,p99/p99.9 10%,分配 `max(5%, +64 B)`(另保留 0.01 B/op 的零分配下限)。首次失败的行在新 fork 中重跑,**重跑仍失败才算回退**。原口径只用 4.x 的 JMH 误差(约 0.3%~1.5%),低于共享 runner 的噪声,同一代码两次运行的失败行不同。冒烟口径(0.9×)不变 | 已定 |
| D35 | GA 前做 2 轮全面代码审查(架构、代码清洁、高内聚低耦合、高性能、高扩展性、易用性),第 1 轮在 RC1 之前(API/配置/存储格式类问题必须在 RC 冻结前修),第 2 轮在 RC 观察期、GA 之前;GA 条件:性能无明显回退(D21/D34)、无特性缺失(11-feature-parity)、文档齐备。见 rc-review.md §6 | 已定 |
| D36 | `cosid80` 补齐 4.x CosIdGenerator 的可配置项(GA 审查 R1-x-03/04/05,按总调度推荐,用户可推翻):`GeneratorSpec.CosId80` 增加 `layout`(默认 `COSID_80`,总位数 > 63,epoch 固定 Unix epoch),Spring 复用 snowflake 的布局配置键(位宽与 `seq-reset-threshold`);`PartsCodecSpec.Friendly` 增加 `padStart`;cosid80 上设置不适用的 codec 属性时报配置错误;迁移判定"相同布局"不比较 `seqResetThreshold`,converter 写出 4.x 位宽与阈值。补充(api-freeze.md §1):cosid80 的 epoch/unit 固定(Unix、毫秒),设置即报错;radix 的 width/pad-start 在 cosid80 上报错;迁移写出 `pad-start`。须在 RC 冻结前完成 | 已定(按推荐) |
| D37 | 跨服务全局唯一(4.x CosIdGenerator 的独立 `cosid.generator.namespace` 与独立机器号,GA 审查 R1-x-06):5.0 支持**命名的 machine**:`RuntimeSpec` 可含多个机器规格(默认一个 + 命名的),各自的 `namespace`、`store`、`bits`;时间型生成器用 `machine: <name>` 选择,默认用默认 machine。全局场景:各服务的全局 machine 用同一个 `namespace` 与同一个(或经 cosid-server `remote` 访问的)存储,业务生成器仍在服务命名空间。cosid-server `/ids` 集中发号作为多语言/低频场景的补充,不替代进程内发号。补充(api-freeze.md §2):各 machine 的命名空间两两不同,名 `default` 保留,只启动被引用的 machine,串行启动、逆序释放;`Leased.slotOffset`(节点号 = offset + slot,`offset + 2^bits ≤ 2^nodeBits`,记入命名空间元数据,不一致即拒绝)支持 4.x 与 5.0 共存的**滚动迁移**(总调度改定,适配 K8s 微服务);迁移把 `cosid.generator.namespace` 写成命名 machine `generator`,共享命名空间单独迁移一次;默认 slotOffset = `2^nodeBits − 2^bits`(5.0 slot 置于节点空间顶端,4.x 余量最大;4.x 按 `cosid.generator.machine-bit` 分配,保留位宽时无法构造性保证不相交,plan 报告 headroom,`verify` 事后检查;api-freeze.md 中"M+1 向上取 2 的幂"作废)。已实现:#1374、#1375、#1376 | 已定(按推荐) |
| D38 | Spring 下 `cosid.namespace` 默认取 `spring.application.name`(用户 2026-10-28),不必显式填写;未设置应用名时回退为 `cosid` 并在启动时 WARN(两个未命名应用会共享号段 key 与槽位)。理由:生产清单要求"每个应用一个命名空间",默认取应用名让默认即正确。迁移 `config` 子命令始终显式写出 `cosid.namespace`(4.x 默认 `cosid`),不受此默认值影响。属于冻结面,RC 前完成 | 已定 |
| D39 | 公开值类型的演进(R1-core-01):封闭的值(`Range`、`IdParts`、`GroupedId`、`Owner`、`Layout`、`ClockRegressionPolicy`、codec 规格、`Exhausted`、`Disabled`)保留 record;配置与 SPI 值类型改为 final class + builder,GA 后只往 builder 加有默认值的字段;`CosIdProperties` 不属于 API 承诺。见 api-freeze.md §4 | 已定(按推荐) |
| D40 | sealed 规格接口(`GeneratorSpec`、`MachineSpec`、`CodecSpec`、`PartsCodecSpec`、`GroupedCodecSpec`)次版本可加变体,javadoc 要求 `switch` 保留 `default`;`AcquireResult` 封闭 | 已定(按推荐) |
| D41 | `GeneratorSpec.Custom`(按名,Spring Bean)与 `Instance`(实例)覆盖 4.x `IdGeneratorProvider.set`(R1-core-02);运行时不拥有其生命周期 | 已定(按推荐) |
| D42 | `MachineSpec.Leased.acquireTimeout`(默认 ttl,`cosid.machine.leased.acquire-timeout`)(R1-core-03) | 已定(按推荐) |
| D43 | 其余冻结面 MINOR:R1-core-11/12/17、R1-spring-03/05/06、`cosid.metrics.generated.enabled` 在 RC 前改;R1-x-02(静态入口)只做文档;R1-int-01 随 PR3 | 已定(按推荐) |
| D44 | 性能门槛以**端到端发号**为最终指标(用户 2026-10-28;修订 D21/D34 的判定口径):**端到端行**=应用视角的 ID 生产,即 P1–P8、P11(启动到第一个 ID)、P12(字段填充),按 D34 容差硬判(FAIL 阻塞)。**组件行**=P9 编解码、P10 存储原语(reserve/acquire/renew),只要求"不过度退化":在 D34 内为 PASS;超出 D34 但吞吐/平均延迟 ≤ 20%、p99/p99.9 ≤ 30%、分配 ≤ max(25%, +64 B) 为 WARN(报告列出,不阻塞);超出即 FAIL。端到端场景须覆盖每个存储后端:P7 补 Mongo、ZooKeeper 的号段链基准(4.x 与 5.x 两侧)。阈值按协调会话推荐 | 已定(用户提出原则,阈值按推荐) |
| D45 | **依赖保持最新稳定版,代码保持清洁**(用户 2026-10-29):所有依赖(Spring Boot、驱动/客户端、Curator、Gradle 与插件、Checkstyle 等)升级到最新稳定版。优先通过升级上游消除告警，不要钉住传递依赖;只有上游尚无修复时，才显式声明传递依赖，并在声明处注明原因和移除条件。被升级取代的约束、排除项要删掉。Spring Boot:构建用最新 4.1.x;支持下限取 4.0.x 线的最新补丁(协调会话推荐，保留对 4.0 的支持),CI 矩阵覆盖这两端。4.x 基线 cosid4 也升到最新(4.1.1,覆盖 §4.3 第 12 项的暂缓),之后的门槛以 4.1.1 为基线。**补充(用户 2026-10-29):依赖版本以最新 Spring Boot BOM 为准**:
| D46 | **UUIDv7 生成器**(下游 Wow V10 代用户提出,2026-10-29):新增生成器策略 `uuidv7`,对应规格 `GeneratorSpec.UuidV7`(final class + builder,D39/D40)。<br>格式：遵循 RFC 9562 §5.7,48 位 Unix 毫秒时间戳、version 7、variant 10。<br>**有序性**:采用方法 1,`rand_a` 的 12 位作为计数器，每毫秒以随机值起步并留出余量，因此单个生成器实例内严格单调;计数器用尽时借用下一毫秒;时钟回拨时沿用上次的时间戳继续计数。`rand_b` 的 62 位取自 `ThreadLocalRandom`(只保证唯一，不保证不可预测，文档明确说明 id 不是秘密)。<br>**不需要 machine、命名空间和租约**,配置 `machine` 会报错，文档说明无需协调即可唯一。<br>文本默认为 36 字符的规范形式;radix 等 codec 不在 5.0 范围内，以后可兼容地追加。<br>运行时以 `text(name)` 取文本，以 `get(name, UuidV7Generator.class)` 取 `java.util.UUID`。<br>`IdGenerators.uuid()`(v4)保留。<br>测试覆盖 version/variant 位、跨毫秒与毫秒内的有序性、并发唯一性、时钟回拨和计数器溢出;另有一行 5.x 自身的趋势基准，只报告不判定。<br>**补充(API)**:文本统一用已有的 `runtime.text(name)` 取，它适用于所有策略;`UUID` 用 `get(name, UuidV7Generator.class)` 取。`stringGenerator(name)` 仍只服务 long id 加 codec,对 cosid80 或 uuidv7 调用时，异常信息提示改用 `text(name)`。不改动任何已有 API<br>**时间：进 5.0,在 RC1 之前完成**(协调会话推荐：自包含、无存储，补回 4.x `UuidGenerator` 的现代替代);用户已确认(2026-10-29) | 已定(用户确认) |
| D47 | **不拆分独立的 api 模块**(用户 2026-10-29 提问，协调会话评估):`cosid-core` 本身已是 API 与运行时的最小组合：约 100 个类、jar 约 250 KB,唯一依赖是 slf4j-api;JPMS 只导出 9 个公开包,`internal` 不导出，仓库内的其他模块也都不引用 `internal`;冻结面已由导出包和 `package-info` 界定。拆分不会带来新的能力：运行时只有一种实现，扩展点是 SPI,而 SPI 已在 core 中;两个模块同版本发布，也换不来独立演进。拆分的代价却很大:JPMS 不允许两个模块含同名包，必须给公开包改名，涉及冻结面上的全部类型以及所有模块、文档和下游(Wow),RC1 将因此推迟。只需要"一个 id 来源"的下游库，用 JDK 的 `LongSupplier`/`Supplier<String>` 即可解耦(CoSec 就是这样)。**何时重新评估**:core 引入重量级依赖，或出现第二种运行时实现，或下游需要不带运行时的类型时，在次版本中以新增模块(新包名)的方式追加，不影响现有 API | 已定(协调会话推荐，待用户确认) |
| D48 | **cosid-server 镜像运行在 JDK 25**(用户 2026-10-29):Docker 运行时基础镜像为 `eclipse-temurin:25-jre`,库的字节码目标仍为 Java 21(支持 21+)。理由：从 JDK 24 起(JEP 491),虚拟线程在 `synchronized` 中不再固定在载体线程上，而 cosid-server 默认开启虚拟线程;CI 已在 Java 25 上测试。server 模块的测试也须在 25 上运行，与运行时一致 | 已定(用户) |
  - 所有模块(非 Spring 模块也一样)在构建期导入 `spring-boot-dependencies` 作为不发布的 platform,与 spring 模块现有的 `managedVersions` 做法相同;
  - BOM 管理的依赖(驱动、Lettuce、Mongo、Hikari、Jackson、Netty、SLF4J/Logback、Testcontainers、JUnit 等)不再在 `libs.versions.toml` 中声明版本;
  - BOM 不管理的依赖(Curator、ZooKeeper、Guava、springdoc、spring-cloud-commons、JMH 等)才显式声明，取最新稳定版;
  - 安全修复需要高于 BOM 的版本时才显式覆盖，并注明移除条件;
  - 发布的 POM 写入解析后的具体版本，不带 BOM import,不把 Spring Boot 的版本强加给非 Spring 用户;
  - `benchmark/baseline-4x` 不受影响 | 已定(用户提出原则，下限按推荐) |
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
