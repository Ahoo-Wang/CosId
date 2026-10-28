# GA 前全面审查(D35)

> 状态:**第 1 轮进行中**(2026-10-28,基于 `main` @ `3a1cdaae`,即 `5.0.0-alpha.1` 之后)。
> 本文件只记录审查发现;核实、排期与修复进度由总调度在 [rc-review.md](rc-review.md) §6 与 [roadmap.md](roadmap.md) 维护。
> 中断后从 §0 继续。

## 0. 方法与恢复

- 只读审查,不改产品代码,不跑构建、测试或基准(CPU 规则见 `AGENTS.md`)。
- 逐模块对照设计文档 01–11 审查,维度见 rc-review.md §6:**架构**、**代码清洁**、**高内聚低耦合**、**高性能**、**高扩展性**、**易用性**;另核对三项结果条件:**性能**(D21/D34)、**特性**(11-feature-parity 与 4.x)、**文档**。
- 严重度:**BLOCKER**(重号、数据丢失、安全问题,或 RC 冻结后无法再改的 API/配置/存储格式问题)/ **MAJOR**(明显影响性能、扩展或易用)/ **MINOR**(其余)。
- "冻结面"列:修复是否改变公开 API、配置项(名称或默认值)或存储格式;为"是"的必须在 RC1 前处理。
- 只写经过代码核实的发现;推测的标"存疑"。rc-review.md §3 已修复的问题不重复报告,但在各模块"确认通过"中复核其修复。
- 中断后恢复:看 §1 的进度表,未完成的模块按同样范围重新审查,每审完一个模块提交一次。

## 1. 进度

| 模块 | 状态 |
|---|---|
| core | ✅ 完成(§4) |
| store/*(含 tck)、migration | 进行中 |
| spring/*、server、integrations/*、extras、bom、examples、benchmark/v5 | 进行中 |
| documentation/、模块 README | 进行中 |
| 结果条件(性能、特性、文档)与汇总 | 待做 |

## 2. 专题:4.x 静态入口 `DefaultIdGeneratorProvider.INSTANCE` / `getShare()`(总调度追加)

**现状(已核实)**:
- 迁移指南只有一行映射:`IdGeneratorProvider`、`DefaultIdGeneratorProvider.INSTANCE`、share → `CosIdRuntime`、名为 `default` 的生成器(`documentation/docs/en/guide/migration/from-4x.md:263`、`zh/.../from-4x.md:239`)。没有说明**非 Spring 管理的代码**(实体静态工厂、工具类、`new` 出来的对象)在 5.0 如何取号;`LazyIdGenerator` 的替代写法 `IdGenerators.lazy(() -> runtime.generator("order"))`(`documentation/docs/en/guide/concepts/layout-and-codecs.md:135`)同样假定手里已经有 `runtime`。
- 5.0 唯一的静态状态是 Axon 的 `CosIdIdentifierFactory` 绑定栈(`integrations/axon/src/main/java/me/ahoo/cosid/axon/CosIdIdentifierFactory.java:31-50,80-99`):Spring 装配在上下文启动后 bind、关闭前 unbind,多个运行时同时绑定时告警、取最近一个,未绑定时抛 `IllegalStateException`。这证明"由装配填充、可解绑的只读 holder"与 D17 可以共存,且项目已有先例。
- D17(`docs/architecture/07-runtime-and-spring.md:45`)只要求"没有全局单例、多个运行时互不影响";它约束的是 core 的组装路径,并不禁止 Spring 层提供一个**指向当前上下文运行时的便捷引用**。

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 触及冻结面 |
|---|---|---|---|---|---|---|
| R1-x-01 | MAJOR | 易用性、文档 | `documentation/docs/{en,zh}/guide/migration/from-4x.md:263/239` | 4.x 在静态上下文用 `DefaultIdGeneratorProvider.INSTANCE.getShare().generate()` 的用户,迁移指南没有给出替代写法,只有一行类名映射;这是 4.x 最常见的用法之一 | 迁移指南(中英文)加一小节"静态代码里取号":① 首选注入 `IdGenerator` Bean;② 必须静态时,在应用里放一个由 `@Bean`/`SmartInitializingSingleton` 赋值的 holder(给出 10 行示例,强调上下文关闭后不可再用);③ 非 Spring 时自己持有 `CosIdRuntime`。javadoc 中 `CosIdRuntime` 类注释补一句同样的指引 | 否(纯文档) |
| R1-x-02 | MINOR(待用户决定) | 易用性、高扩展性、架构 | `spring/autoconfigure`(新增);`docs/architecture/07-runtime-and-spring.md:45` | 是否提供官方静态入口。**方案 A(推荐 RC 前只做文档)**:只做 R1-x-01;官方 holder 留到 GA 后按需增加。新增一个类是**向后兼容的增量**,RC 冻结后仍可在次版本加入,所以不必为它阻塞 RC。**方案 B**:在 `spring/autoconfigure`(不放 core,保持 core 无全局状态)提供只读 `CosIdHolder`:装配在上下文启动后 bind、关闭前 unbind(复用 Axon 的绑定语义:多个上下文时告警并取最近一个,未绑定时抛带指引的异常),只暴露 `runtime()` / `generator(name)`。与 D17 的关系:不冲突的前提是它只是"当前 Spring 上下文的引用",core 不感知;代价是多上下文(父子上下文、测试里多个 context、同 JVM 多应用)时"最近一个"语义可能取错运行时,以及上下文关闭后静态引用失效带来的隐蔽错误。**方案 C**:放进 core 作为全局注册表——与 D17 冲突,不建议 | 方案 B 若选,宜在 RC 前定名(一旦发布即属公开 API);同时 07 §… 增加一句说明 holder 不是全局单例 | 是(方案 B/C 新增公开 API;方案 A 否) |

## 3. 专题:4.x `CosIdGenerator`(80 位)与 5.0 `cosid80`(总调度追加,特性维度)

**4.x(已核实,`origin/4.x`)**:`cosid.generator.*`(`CosIdGeneratorProperties`)可配 `type`(radix62/radix36/friendly)、`timestamp-bit`(默认 44)、`machine-bit`(20)、`sequence-bit`(16)、`sequence-reset-threshold`(默认 `~(-1 << 15)` = 32767)、`zone-id`(默认系统时区)、`pad-start`(默认 true,作用于 friendly 的节点与序列);epoch 固定为 Unix epoch(`RadixCosIdGenerator` 直接用 `System.currentTimeMillis()`);`CosIdGeneratorAutoConfiguration.java:153-165` 把全部位宽与阈值传给三种生成器。

**5.0(已核实)**:`GeneratorSpec.CosId80(regression, codec)`(`core/src/main/java/me/ahoo/cosid/runtime/GeneratorSpec.java:51`)没有布局字段,`RuntimeParts.java:181` 固定用 `Layout.COSID_80`(`Layout.java:51`:Unix epoch、44/20/16、`defaultSeqResetThreshold(16)` = 32767,与 4.x 默认相同);`PartsCodecSpec.Friendly(zone)`(`PartsCodecSpec.java:38`)没有 `padStart`;文档写明"`cosid80` 始终使用 `Layout.COSID_80`"(`documentation/docs/en/reference/config/generators.md:26`、zh 同)。

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 触及冻结面 |
|---|---|---|---|---|---|---|
| R1-x-03 | MAJOR | 特性、架构 | `core/.../runtime/GeneratorSpec.java:51`;`core/.../internal/RuntimeParts.java:181`;`docs/architecture/11-feature-parity.md:19` | 4.x CosIdGenerator 的 `timestamp-bit`/`machine-bit`/`sequence-bit`/`sequence-reset-threshold` 在 5.0 都不能配置,11 第 19 行标"等价"不准确(只覆盖了编码类型与 `generateAsState`)。按 D21 这是能力缩减:例如 4.x 用 `machine-bit` 缩小节点位、或调低 `sequence-reset-threshold` 的部署,在 5.0 没有对应写法。core 内部已能支持任意宽布局(`PartsCodec.radix(Layout, radix)`、`Codecs.java:131` 只是传入常量),缺的只是规格与配置 | 二选一,**须在 RC 前决定**(`CosId80` 是公开 record,加字段即改 API):**方案 1(推荐)**:`CosId80` 增加 `Layout layout` 字段(默认 `COSID_80`,要求 `totalBits() > 63`,epoch 固定 Unix epoch 以保持 4.x 语义),Spring 复用 snowflake 的 `layout` 配置键;至少补 `seq-reset-threshold`(只影响行为、不影响数值空间,成本最低)。**方案 2**:不支持,在 11 中把该行改为"等价(位宽固定)"并写明理由(D4:80 位文本格式在各语言间互通,固定布局避免同名格式不兼容),迁移指南列出受影响的 4.x 配置 | 是(方案 1 改 `GeneratorSpec.CosId80` 与新增配置项;方案 2 只改文档) |
| R1-x-04 | MAJOR | 特性、易用性 | `migration/src/main/java/me/ahoo/cosid/migration/LayoutCheck.java:41-47`;`migration/.../config/ConfigConverter.java:615-617` | 迁移对自定义 CosIdGenerator 的处理:位宽不同则 M3 判定失败("an 80-bit layout differs"),`apply` 中止(`Migration.java:430`),这是安全的。但 `LayoutCheck` 用 `Layout.equals` 判断"相同",而 `Layout` 是 record,**包含 `seqResetThreshold`**(`Layout.java:34`):4.x 只改了 `sequence-reset-threshold`、位宽与 epoch 都相同的部署也会被判为 80 位布局不同而无法迁移,尽管阈值不影响数值空间(snowflake 同理,只是 snowflake 在 5.0 能配置阈值,通常两边一致) | 判断"相同布局"只比较 `epoch`、`unit`、三个位宽;阈值不同只写报告。若采纳 R1-x-03 方案 1,converter 应把 4.x 位宽与阈值写进 5.0 配置而不是只报告 | 否 |
| R1-x-05 | MINOR | 易用性、特性 | `spring/autoconfigure/.../CosIdSpecs.java:283-297`;`core/.../runtime/PartsCodecSpec.java:30,38` | cosid80 的 codec 配置**静默忽略**部分属性:`friendly.pad-start`(`CosIdProperties.Friendly` 有该属性,默认 true)和 `radix.width`/`radix.pad-start` 被丢弃,不报错;4.x 的 friendly `pad-start: false` 在 5.0 无对应(迁移 converter 只写报告,`ConfigConverter.java:618-620`)。默认值与 4.x 一致(补零),`zone-id` → `codec[0].friendly.zone`(默认系统时区)对应正确 | cosid80 上设置了不适用的属性时报配置错误(与其他校验一致);是否支持 friendly 不补零:要支持需给 `PartsCodecSpec.Friendly` 加 `padStart`,否则在 11 与配置参考中写明 | 是(若给 `PartsCodecSpec.Friendly` 加字段);只报错则否 |

**已核对一致**:默认 `seq-reset-threshold`(32767)、默认位宽 44/20/16、epoch(Unix)、radix 62/36 与 friendly 三种文本、`zone-id` 的对应项与默认值、默认补零。

### 3.1 跨服务全局唯一:`cosid.generator.namespace`(总调度核实的发现 + 迁移与文档补查)

**4.x(已核实)**:`CosIdGeneratorAutoConfiguration.java:110-111`(`origin/4.x`)用 `firstNotBlank(cosid.generator.namespace, cosid.namespace)` 在**独立命名空间**里按自己的 `machine-bit` 分配机器号;各服务把 `cosid.generator.namespace` 设为同一全局值并共用机器号存储,即得到跨服务唯一的 80 位 ID,而 snowflake 与号段仍留在各自的服务命名空间。
**5.0(已核实)**:`RuntimeSpec(String namespace, Map stores, Optional<MachineSpec> machine, Map generators)`(`core/src/main/java/me/ahoo/cosid/runtime/RuntimeSpec.java:29-32`):一个运行时一个命名空间、至多一个 machine,同时用于号段 key 与槽位租约。

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 触及冻结面 |
|---|---|---|---|---|---|---|
| R1-x-06 | MAJOR(RC 前必须解决) | 特性、高扩展性、架构 | `core/.../runtime/RuntimeSpec.java:29-32`;`GeneratorSpec.java:51`(及 `Snowflake`);Spring `cosid.machine` | Spring 应用无法让业务生成器留在服务命名空间、同时让 cosid80(或某个 snowflake)用一个全局命名空间的机器号;各服务共用一个命名空间则同名号段 key 被共享(耦合而非隔离)。D21 意义上的特性缺失。core 层面可绕过(D17 允许同一 JVM 两个 `CosIdRuntime`,一个用全局命名空间),但 Spring 只装配一个运行时,且两个运行时意味着两套存储连接与生命周期 | **评估协调会话的"命名 machine"方案:认可,建议采用。** `RuntimeSpec` 增加 `Map<String, MachineSpec> machines`(保留现有 `machine` 作为默认,现有配置不变),`MachineSpec` 可带自己的 `namespace`(缺省为运行时命名空间)、`store`、`bits`;`Snowflake`/`CosId80` 增加 `machine` 名(缺省默认 machine)。Spring:`cosid.machines.<name>.{namespace,store,bits,...}`、`cosid.generators.<g>.machine`。实现要点:每个 machine 一个 `LeaseKeeper`/`SlotBinding`(各自的 incarnation、续约与关闭顺序,运行时等全部就绪才开始发号);`RuntimePlan` 按所选 machine 校验 `checkNodeBits`;拒绝两个 machine 指向同一 `(store, namespace)`(否则同一进程重复租约);指标、health、`describe()` 带 machine 名;D17 不受影响(仍无全局状态)。另一做法 cosid-server `/ids/{name}` 集中发号适合多语言或不能直连中心存储的低频场景,**不能替代**进程内发号,只宜作为文档中的补充 | 是(`RuntimeSpec`、`MachineSpec`、`GeneratorSpec` record 与 Spring 配置) |
| R1-x-07 | BLOCKER(按 rc-review §3.D 口径:合理配置下悄悄重号) | 特性、架构 | `migration/src/main/java/me/ahoo/cosid/migration/config/ConfigConverter.java:621`;`Migration.java:139,241-244`;`config/Legacy4x.java:55` | 迁移工具不认识 `cosid.generator.namespace`:① `config` 把它列入 handled 后**静默丢弃**(`for (String part : List.of(..., "namespace"))`),不写报告;转换出的 cosid80 落在服务命名空间,**各服务迁移后从各自的槽位 0 开始发 80 位 ID,跨服务不再唯一**,而 4.x 依赖的正是跨服务唯一。② `plan`/`apply` 只读 `--namespace` 的机器号记录(`Readers.read(namespace, …)`),全局命名空间里 CosIdGenerator 实例的 T4 不进入 W;W 只剩 `T_apply + sourceSkew` 一项保护,时钟超前的 4.x 全局实例可能已发出大于 W 的时间戳(与 D-B4 同类) | 在 R1-x-06 落地前:`config` 遇到与 `cosid.namespace` 不同的 `cosid.generator.namespace` 时报错拒绝转换(说明 5.0 暂不支持并给出 server 集中发号或两个运行时的替代);`plan` 有 `--layout-config` 且其中有独立 `cosid.generator.namespace` 时,把该命名空间的机器号记录并入 T4,或要求单独迁移该命名空间并拒绝继续。R1-x-06 落地后:`config` 生成 `cosid.machines.<global>` 并让 cosid80 引用它,W 按该命名空间计算。加测试 | 否(工具行为);与 R1-x-06 联动 |
| R1-x-08 | MAJOR | 文档 | `documentation/docs/{en,zh}/guide/introduction.md:3`;`guide/concepts/runtime.md`;`guide/migration/from-4x.md`;`guide/server.md` | 文档只承诺"一个服务的各实例间唯一"(introduction 第 3 行),没有任何一页讲跨服务全局唯一怎么做;迁移指南的映射表没有 `cosid.generator.namespace` 一行 | 概念页(runtime 或 slot-lease)加"跨服务全局唯一"一节:共用命名空间的代价、命名 machine(R1-x-06 后)、cosid-server 集中发号的适用场景;迁移指南与配置参考补 `cosid.generator.namespace` 的对应项(中英文) | 否 |

## 4. core

范围:`core/src/main/java`,对照 01/02/03/04/05/07(core 部分)/10/11 与 `origin/4.x` `cosid-core`。总调度抽查核实:R1-core-02(`GeneratorSpec` sealed)、R1-core-03(`RuntimeParts.java:143-144` 把 `leased.ttl()` 当作 acquireTimeout 传入)、R1-core-04(`SegmentGenerator.java:431-433` 每次补充 `new RangeCursor`)、R1-core-05(`TimeSequencer.java:366` `state.set`)、R1-core-11(`IdGenerators.java:71` 抛 `IllegalStateException`)。

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 触及冻结面 |
|---|---|---|---|---|---|---|
| R1-core-01 | MAJOR | 高扩展性 / 易用性 | `core/src/main/java/me/ahoo/cosid/runtime/RuntimeOptions.java:41-81`、`segment/BatchPolicy.java:33-38`、`store/StoreConfig.java`、`runtime/MachineSpec.java:39,57`、`lease/AcquireResult.java:28` | 公开配置类型和 SPI 值类型都是 record:`RuntimeOptions`(9 个分量)、`BatchPolicy`(6 个)、`StoreConfig`、`MachineSpec.*`、`GeneratorSpec.*`、`AcquireResult.Acquired`、`SlotLease`、`GeneratorInfo`。record 加分量就会改掉规范构造器和解构模式,无法兼容演进。已经出过一次:`RuntimeOptions` 为了 `countGenerated` 补了一个 8 参数重载(:63-81)。`BatchPolicy` 没有 `withX`,只改 step 也要重写 6 个位置参数(`benchmark/v5/.../V5.java` 的 `batch(step)` 就是这么写的)。`MAX_READY_RANGES=64`、退避 100ms/5s 这类以后可能要开放的参数,将来也没法加进去 | RC 前逐个定:(a) 本身封闭的值(`Range`、`IdParts`、`GroupedId`、`Owner`、`Layout`)保留 record;(b) 会长出新选项的(`RuntimeOptions`、`BatchPolicy`、`StoreConfig`、`MachineSpec.Leased/Static`)现在就加 builder 或 `withX`,并在 javadoc 写明规范构造器不保证稳定,或者改成带 builder 的 final 类 | 是 |
| R1-core-02 | MAJOR | 高扩展性 / 特性对照 | `core/src/main/java/me/ahoo/cosid/runtime/GeneratorSpec.java:22`(sealed,4 种策略加 `Disabled`) | 4.x 的 `IdGeneratorProvider.set(String, StringIdGenerator)` 可以按名字注册用户自己的生成器,再配合 `@CosId("x")` 和 share 使用。5.0 的 `GeneratorSpec` 是 sealed,没有 `Instance` 或 `Custom` 变体,`RuntimeSpecCustomizer` 只能加内置的 4 种策略,所以这项能力表达不了。11 §1 的 `IdGeneratorProvider … 增删` 行标"等价",说多了。另外 GA 之后再给 sealed 接口加 permitted record,会让用户(以及 `CosIdSpecs`)里的穷举 switch 编译失败 | RC 前二选一:加 `record Instance(IdGenerator ids, List<CodecSpec> codec)`,在 `RuntimeParts.create` / `GeneratorEntry` 里各补一个分支;或者在 11 里明确写成"不提供"。`MachineSpec`、`CodecSpec`、`AcquireResult` 的 sealed 也一并确认是有意为之 | 是 |
| R1-core-03 | MAJOR | 架构(代码应改) / 易用性 | `core/src/main/java/me/ahoo/cosid/internal/RuntimeParts.java:143-144`、`runtime/MachineSpec.java:39` | 05 §4.2 和 §5 把 `acquireTimeout`(默认 `ttl`)列为参数,`LeaseKeeper` 的构造器也收这个参数。但 `MachineSpec.Leased` 没有这个分量,`RuntimeParts` 直接传 `leased.ttl()`,Spring 侧也没有对应属性(grep 无结果)。GA 后再补,既要改 record,又要加配置属性 | RC 前在 `Leased` 加 `Duration acquireTimeout`(配 `cosid.machine.leased.acquire-timeout`,默认 ttl),或者把它从 05 §5 删掉、写明固定等于 ttl | 是 |
| R1-core-04 | MAJOR | 高性能 | `core/src/main/java/me/ahoo/cosid/segment/SegmentGenerator.java:431-433`(`new RangeCursor`)、`:196-235` 与 `:410-449`(每次补充加锁两次)、`:295-297`(`BatchSizer.record`) | P6(step 1、lookahead 0)每个 id 都要走一遍慢路径:失败的 `getAndAdd`、`tryLock`、`currentMillis`、`sizer.record`(double 除法)、单飞 bookkeeping、`reserve`、`complete` 里再加锁、`currentMillis`、**`new RangeCursor`(32 B,会逃逸)**、`advanceLocked`,最后再一次 `getAndAdd`。4.x `DefaultSegmentId.next` 有 `ONE_STEP` 快路:不加锁、不安装 segment,`DefaultIdSegment` 可以被标量替换。分析见下方"性能回退候选"(没跑 `-prof gc`,原因 **存疑**,但置信度高) | 调用方自己做预留的路径(`reserveHereSize > 0`)直接返回 `range.start()`,只有 `[start+1, end)` 非空时才安装 cursor。`step == maxBatch` 时跳过 `BatchSizer.record`。没有 ttl 也不预取时,`complete` 不读 `currentMillis`。`highWater` 校验照留 | 否 |
| R1-core-05 | MINOR(存疑) | 高性能 | `core/src/main/java/me/ahoo/cosid/internal/TimeSequencer.java:366` | 每发一个号,都在监视器里对 `AtomicLong.set` 做一次 volatile 写(x86 上是一条 `lock` 前缀指令或 StoreLoad 屏障)。4.x 的 `AbstractSnowflakeId` 是 `synchronized` 加普通字段(:90,96)。锁外只有 `lastIssuedMillis()` 读这个状态,能容忍读到旧值;`close()` 和 `retire()` 走的是 `getAndSet`。这是 P1/P2 吞吐的候选开销 | 改成 `state.setRelease(next)`(或 `lazySet`),换绑和关闭路径保持 `getAndSet`。用 P1 的 t1/t16 验证 | 否 |
| R1-core-06 | MINOR | 高性能 / 架构 | `core/src/main/java/me/ahoo/cosid/internal/ObservedStores.java:44-46,105-114` | 运行时给每个 `RangeStore` / `SlotLeaseStore` 套了一层 `Observer.call(op, () -> store.reserve(key, size, offset))`:每次 reserve 捕获一个 lambda(约 40 B,不保证能做逃逸分析)。P6 和 P5 的基准(`V5.segment`)绕过了 `CosIdRuntime` 和 `ObservedStores`,所以发布门槛量不到生产路径;step=1 的用户每个 id 都要多付这一份 | `ranges()` / `slots()` 里每个方法直接写 try/catch,再调 `observer.timeout(op, e)`,不要 Supplier。scenarios.md 注明 P6 不覆盖运行时这层包装,或者加一个经由 `CosIdRuntime` 的 P6 变体 | 否 |
| R1-core-07 | MINOR | 高扩展性 | `core/src/main/java/me/ahoo/cosid/internal/ObservedStores.java:40-87` | 包装用的匿名 `RangeStore` / `SlotLeaseStore` 只覆盖了现有方法。GA 之后 SPI 新加的 default 方法(比如批量 reserve 或能力查询)不会被转发,会静默落回默认实现 | 加一个单测,反射检查包装类覆盖了接口的全部方法;以后给 SPI 加 default 方法时,在 javadoc 里提醒同步包装层 | 否 |
| R1-core-08 | MINOR | 高扩展性 / 高内聚低耦合 | `core/src/main/java/me/ahoo/cosid/codec/GroupedCodec.java:69`、`codec/PeriodWithin.java`(`instanceof PatternPeriod`) | 导出的 `GroupedCodec` 靠 `instanceof internal.PatternPeriod` 拿 key 长度。自定义 `Period` 拿不到这个能力,空分隔符的文本无法反解 | 给 `Period` 加 `default OptionalInt keyLength() { return OptionalInt.empty(); }`,`PatternPeriod` 覆盖它,两个 codec 改为调用这个方法 | 否(default 方法是增量) |
| R1-core-09 | MINOR | 易用性 / 文档 | `core/src/main/java/me/ahoo/cosid/observe/GeneratorListener.java:22-24` | javadoc 写着 "Never called on the hot path",实际不是这样:`onClockRegression("borrow")` 在发号线程上调(`TimeSequencer.java:152-154`);调用方自己做预留时,`onBatchResized` / `onRangeReserved` 在调用线程上调(`SegmentGenerator.java:237-238,452`)。14 个回调里有 11 个没有 javadoc,也没写线程约定(在哪个线程调用、能不能阻塞) | 在接口级 javadoc 写清各回调在哪个线程触发,并要求实现不得阻塞;改掉 "never on the hot path" 这句 | 否 |
| R1-core-10 | MINOR | 代码清洁 | `core/src/main/java/me/ahoo/cosid/segment/SegmentGenerator.java:508-545`、`lease/LeaseKeeper.java:581-587`、`internal/RuntimeParts.java:103`、`internal/RangeCursor.java:61` | listener 的防护有三套写法(`SafeListener`、`SegmentGenerator.fire*`、`LeaseKeeper.notify`),runtime 路径上已经包过一层 `SafeListener`,等于 try/catch 套了两层。`saturatedAdd` 重复了 3 份(`SegmentGenerator`、`PeriodicSegmentGenerator`、`ZonedClock`)。`RangeCursor.of` 只有测试在用,主代码在 `SegmentGenerator:429-433` 里把 cap 和构造手写了一遍 | 公开构造器统一 `SafeListener.of(listener)`,删掉 `fire*` 和 `notify` 里的 try/catch;`saturatedAdd` 收进 internal 的一个工具方法;`complete` 改用 `RangeCursor.of`(或者删掉 `of`) | 否 |
| R1-core-11 | MINOR | 易用性 | `core/src/main/java/me/ahoo/cosid/IdGenerators.java:71-73` | `uncertain` 在原 id 太大时抛 `IllegalStateException`;同一个类里的 `toInt` 抛 `IdOverflowException`;4.x 对应的是 `OriginalIdOverflowException`。调用方没法统一捕获 | 改成抛 `IdOverflowException`(必要时加一个带 bits 的构造器) | 是(异常类型) |
| R1-core-12 | MINOR | 特性对照 / 测试 | `core/src/main/java/me/ahoo/cosid/internal/TimeSequencer.java:351-355` | time 位溢出和 epoch 在未来这两种情况抛普通的 `IllegalStateException`;core 测试里 grep 不到覆盖。4.x 的 `TimestampOverflowException` 在 11 里没有对应行 | 补测试(用 `ManualTime` 推到 `maxTime+1`,再测一个 epoch 在未来);11 §1 加一行;要不要专门的异常类型,RC 前定下来 | 是(若改异常类型) |
| R1-core-13 | MINOR | 架构(文档或代码) | `core/src/main/java/me/ahoo/cosid/lease/LeaseKeeper.java:51` | `static final Set<String> INCARNATIONS` 是 JVM 级的可变全局状态,和 01 §3 原则 3 "无全局状态"冲突。它只是一个安全网,作用有限 | 在 01 写明这是例外(只用于检测,不参与发号),或者把检查挪到 `RuntimeParts` / Owner 的生成处 | 否 |
| R1-core-14 | MINOR | 架构(文档应改) | `docs/architecture/03-core-model.md` §4.1 和 §6、`05-machine-lease.md` §4.1 和 §4.3、`01-principles.md` §3.7、`04-segment.md` §2 和 §2.1、`02-modules.md` §4 | 文档和代码对不上:03/05 写的是对 `Bound` / state 做 CAS,代码是 `synchronized` 里 `bound.set`(`TimeSequencer.java:145,274-291`);01 §3.7 写"热路径无锁",和 D25 矛盾;04 写 maxBatch 默认 `step×1000`,代码是 `max(step×1000, 2^20)`(`BatchPolicy.java:46-56`);04 §2.1 写 `ready: ArrayDeque<Range>`、`inflight: CompletableFuture<Range>`、`expiresAtNanos`,代码分别是 `ArrayDeque<RangeCursor>`、`CompletableFuture<Void>`、毫秒;02 §4 的包清单漏了 `PartsIdGenerator`、`GroupedIdGenerator`、`AcquireReason`、`StoreTimeoutException`、`RuntimeOptions`、`StoreSpec`、`PartsCodecSpec`、`GroupedCodecSpec`、`HostResolver`;03 §6 写"共 15 个公开 API",早已不止 | 按代码修文档(代码本身没问题) | 否 |
| R1-core-15 | MINOR | 文档 / 特性对照 | `docs/architecture/11-feature-parity.md`(多处) | 37 个测试链接的 `#L` 行号已经偏了,方法本身都还在。core 部分:`SegmentGeneratorTest` +17、`IdFillerTest` +19、`PeriodicSegmentGeneratorTest` +10、`DatePrefixTest` +5、`LeaseKeeperTest` +4;其余在 server、migration、remote、spring | 去掉行号锚点,或者在 CI 里加锚点校验(只用 grep 方法名就够) | 否 |
| R1-core-16 | MINOR | Javadoc | `IdGenerator.java:21`、`codec/IdCodec.java:25`、`codec/PartsCodec.java:27,29`、`codec/GroupedCodec.java:29,31`、`store/StoreProvider.java:28`、`store/Store.java:41`、`accessor/IdFiller.java:23`、`runtime/HostResolver.java:27`、`runtime/MachineSpec.java:27`、`observe/GeneratorListener.java`(11 个回调) | 导出包里约 160 个公开声明没有 javadoc,大部分是平凡的访问器,但 SPI 的核心契约也缺:`IdCodec.encode` 遇到非法 id 怎么处理;`StoreProvider.open` 能不能返回 null(`RuntimeParts:115` 有防御判断,说明契约不清);`IdFiller.fill(null)`。导出包没有一个 `package-info.java` | 先补 SPI 方法和 listener 的 javadoc,再给 9 个导出包和 `internal` 各写一个 `package-info.java`(`internal` 写明"不是 API") | 否 |
| R1-core-17 | MINOR | 高内聚低耦合 | `core/src/main/java/me/ahoo/cosid/internal/GeneratorEntry.java:326-353`、`observe/GeneratorInfo.java` | `describe()` 的 `details` 是 `Map<String,Object>`,key(`held`、`batch`、`ceiling`、`slotValid`…)只在 internal 里以字面量出现,却直接成了 actuator JSON 的格式,等于一份隐式的冻结契约 | 在 `GeneratorInfo` 的 javadoc 里列出各策略的 key,或者在导出包里定义成常量 | 是(actuator 输出) |
| R1-core-18 | MINOR | 易用性 | `core/src/main/java/me/ahoo/cosid/runtime/CosIdRuntime.java:153-158`、`internal/RuntimeParts.java:143,168` | `get(name, TimeBasedGenerator.class)` 返回的是归运行时管的具体生成器(`AutoCloseable`),javadoc 没提醒不要 `close`。`Store.ranges()` / `slots()` 不支持时抛的 `UnsupportedOperationException` 要到装配阶段才暴露,不在 `RuntimePlan` 的汇总报错里 | javadoc 写明"由运行时拥有,不要关闭";`Store` 加 `default boolean supportsRanges()/supportsSlots()`,或者 `StoreProvider` 声明能力,让 `RuntimePlan` 提前报错 | 否(加 default 方法) |

### 4.1 确认通过的部分(core)

- **rc-review 修复的复核(全部正确)**
  - **A-M1**:`LeaseKeeper`(`start`、`runDue`、`renew`、`reacquire`、`close`)都是先在锁内取快照,在锁外做 I/O,再用 `stillHolds(held, epoch)` 复核后才发布;`close()` 只翻状态、`invalidate()`,`release` 放在锁外(`:230-255`)。`StaticSlot` 的状态锁不包文件 I/O,写文件由单独的 `fileLock` 串行化;顺序是先 fsync、再 `raiseCeiling`;`close()` 的最后一次写在 `fileLock` 下完成,扩展线程看到 `closed` 后不再写(`:295-300`)。
  - **A-N1**:`eventBit`/`fired` 保证每次调用每类事件最多触发一次;借用事件由 `borrowing` 标志合并(`TimeSequencer.java:157-159,369-370`)。
  - **A-N2**:`TimeBasedGenerator` 构造时包了 `SafeListener.of`(`:39`)。
  - **A-N3**:先按 `backwards` 判断是否失败,再按 `ahead` 决定等多久(`:338-347`)。
  - **A-N4**:`PeriodicSegmentGenerator.open` 先算出 `end` 并校验 `end <= now`,之后才改状态(`:222-226`)。
  - **A-N5**:`readyCap` 做了饱和乘法(`SegmentGenerator.java:327`)。
  - **A-N6**:`Friendly` 和 `PartsCodec.friendly` 的 javadoc 已写明夏令时回拨那一小时的限制。
  - **A-N7**:`DatePrefix` 走 ID_TIME 时校验 `id >>> totalBits`(`:113-115`)。
- **唯一性**
  - `TimeSequencer` 在同一个临界区里对本次实际使用的时间戳做 floor/ceiling 检查并发布状态;换绑时序列随新 `Bound` 重置;`close` 之后 `lastIssuedMillis` 是精确值。`RuntimeParts.closeAll` 先关时间型生成器、取 max(lastIssued),再 release 或写窗口文件(`:263-284`)。
  - `SegmentGenerator` 用 `highWater` 拒绝存储返回的重叠区间(`:419-421`);单飞由 `refillSeq/refillClaimed` 加 `claim()` 保证;`RangeCursor.HEADROOM` 防止计数回绕;`CLOSED` 哨兵让热路径不需要额外检查。
  - `SlotBinding.raiseCeiling` 只增不减;续约响应丢失时只会少用窗口,不会越界。
- **分层**:导出包的公开签名里没有出现任何 internal 类型(import 只用在实现内部);`module-info` 只导出 9 个 API 包;core 只依赖 slf4j。
- **热路径与 4.x 对齐**:`SegmentGenerator.generate` 是一次 volatile 读加一次 `getAndAdd` 加一次比较(P5 路径和 4.x 相当);`Radix` 按 radix 分支、成对查表,#1362 之后吞吐回到 4.x 水平;`Friendly` / `ZonedClock` 按两次转换之间的区间缓存偏移量,用整数算法算民用日期,不用 `DateTimeFormatter`;`DatePrefix` 的固定宽度 pattern 逐字节写出。
- **WindowFile**:写临时文件、fsync、原子 rename、再 fsync 目录;读取时忽略不认识的行,将来往文件里加字段可以向前兼容。存储格式 `ceiling=<ms>` 没有问题。
- **RuntimePlan**:所有问题一次汇总成一个 `CosIdException`;检查存储 timeout ≤ reserveTimeout;检查节点位 ≥ machine.bits;只打开实际用到的存储。
- **11 中 core 行的抽查**:列出的测试方法全部存在,实际读过的几个(`withoutLookaheadReservesOnlyWhenUsedUp`、`rangeTtlDropsWhatOutlivesIt`、`rangeTtlAlsoDropsExpiredReadyRanges`、`batchFollowsTheRateWithinStepAndMaxBatch`)确实测了表中声称的行为。`GeneratorListener` 正好 14 个回调,和表中一致。

### 4.2 性能回退候选(core)

#### P6 SegmentNoPrefetch:24 → 32 B/op,吞吐 −6%

两边的基准:4.x 是 `new DefaultSegmentId(new MemoryDistributor("p6", 1))`;5.x 是 `V5.segment("p6", new BatchPolicy(1, 0, ZERO, 1, 3s, empty))`,用的是 `MemoryRangeStore`,没有 listener,也没有 `ObservedStores`。

- **4.x 每次操作(`DefaultSegmentId.java` 的 `next`,`step == ONE_STEP` 分支)**:不加锁;`maxIdDistributor.nextIdSegment(ttl)` 在 `nextMaxId()` 里做一次 `addAndGet`,然后 `new DefaultIdSegment`(约 64 B)。这个 segment 只被读 `isExpired()` 和 `getMaxId()`,不逃逸,可以被标量替换。剩下的 **24 B** 最可能是 `SegmentFetchEvents.timed` 里的 `Duration.ofNanos(...)`(12 B 头 + long + int = 24 B,传给了全局的 `CosIdEvents.listener()`,会逃逸),或者捕获 `this` 和 `ttl` 的 Supplier lambda(也正好 24 B)。总之是一个 24 B 的对象。
- **5.x 每次操作**:
  1. `cursor.take()` 对已经用完的 cursor 做一次 `getAndAdd`,返回 −1;
  2. 进 `slowPath`:`tryLock`,`time.currentMillis()`;
  3. `advanceLocked`:`sizer.record(1, Δ)`(double 运算),然后 `current = empty`;
  4. `maybeRefillLocked`:`refilling = true`,`++refillSeq`;调用方自己认领这次补充,解锁;
  5. `reserve()`:`store.reserve` 里一次 `addAndGet` 加 `new Range`(32 B,但 `:385` 注释说明它不逃逸,单态内联后可以被标量替换);
  6. `complete()`:**第二次 `lock`**,`currentMillis`,**`new RangeCursor(start, end)`**(12 B 头 + 两个 long = 28 B,对齐到 **32 B**;它被放进 `ready` 并发布到 `current`,一定在堆上分配),`advanceLocked`、`maybeRefillLocked`,解锁;
  7. 对新 cursor 做 `getAndAdd`。

**结论**:多出来的 8 B,是 5.x 每次补充都要分配并安装一个 32 B 的 `RangeCursor`(`SegmentGenerator.java:431-433`),而 4.x 是一个 24 B 的对象。吞吐 −6% 来自原子操作和锁的往返数:5.x 每次操作大约有两次 lock/unlock、三次 `getAndAdd`/`addAndGet`、两次 `currentMillis`,再加一次 double 除法;4.x 不加锁,只有一次 `addAndGet` 和两次 `nanoTime`。(没跑 `-prof gc` 验证,存疑,但两边对象的字节数正好对上。)修法见 R1-core-04:让自己做预留的调用方直接拿走 `start`,区间只剩 0 个值时不分配 cursor,并跳过 `BatchSizer` 和多余的 `currentMillis`。这样估计每次操作能省掉 32 B 和一组临界区里的工作,`complete` 的第二次加锁还得保留(用于 `highWater` 和单飞状态)。

#### 其他候选

- **时间型生成**:`TimeSequencer.java:366` 每个 id 一次 volatile 写(R1-core-05)。另外每个 id 还要读 `slot.current()`、`binding.isValid()`、`binding.ceiling()`、`bound.get()` 四个 volatile 字段;在 x86 上读开销很小,ARM 上需要实测。`nextParts()` 每次分配一个 `IdParts`,和 4.x 的 `CosIdState` 持平。`TimeBasedGenerator.generate` 每次调用 `layout.totalBits()`(三次加法),可以忽略,也可以在构造时缓存成一个 boolean。
- **运行时路径**:`ObservedStores` 每次 reserve 捕获一个 lambda(R1-core-06)。只要 listener 不是 `NOOP`(Spring 装了 Micrometer 时就不是),每次补充还会多两次 `nanoTime`,外加 `new Range` 和 `Duration`(`SegmentGenerator.java:380,513`)。这些都是按补充计、不是按 id 计,只在 step 很小时显著。
- **`IdFiller`**:`Long` 和 `Integer` 字段经 `Supplier<Object>` 装箱(`AnnotatedIdFiller.java:189-195`);4.x 也会装箱,不算回退。
- **编解码**:`Radix.encode` 分配 `byte[width]` 再构造 `String`,一共两次分配,已和 4.x 持平;`DatePrefix.encode` 是三段字符串拼接(日期、分隔符、内层 codec),每次三次分配,和 4.x 的 `DatePrefixIdConverter` 相当。没有发现新的回退。

### 4.3 特性对照(core)

1. **按名字注册自定义生成器**(`IdGeneratorProvider.set(name, StringIdGenerator)`,以及 share 的 `setShare`):5.0 做不到,11 §1 却标了"等价"。见 R1-core-02。
2. **时间戳溢出**(`snowflake/exception/TimestampOverflowException`):11 里没有对应行,core 也没有测试。见 R1-core-12。
3. **`UncertaintyIdGenerator` 的 `OriginalIdOverflowException`**:5.0 换成了 `IllegalStateException`,和 `IdOverflowException` 的体系不一致。见 R1-core-11。
4. **`IdSegmentDistributor.allowReset()` / `IdSegmentRollbackException`**(检测存储回退):5.0 用 `highWater` 拒绝重叠区间(`SegmentGenerator.java:419-421`),测试 `SegmentGeneratorTest.overlappingRangeFromStoreIsRefused` 也有,但 11 里没有这一行。建议补成"等价"并引用这个测试。
5. **自定义分组 `GroupBySupplier`**:5.0 对应自定义 `Period`,`PeriodicSegmentGeneratorTest` 和 `GroupedCodecTest` 里都有自定义 Period 的用例,但 11 那一行只提到预设周期。建议补上;另外,空分隔符时自定义 Period 无法反解,见 R1-core-08。
6. **`DefaultSegmentId.getRemaining()` / `getNamespacedName()`(4.1.0 新增)**:对应 `describe()` 里的 `held` / `key`,"Stat 体系"那一行可以覆盖,建议在该行写出来。
7. 已核对、无缺口:`ClockBackwardsSynchronizer`(由窗口 floor 取代,见"本地状态存储"行)、`MachineIdLostException`(对应 `SlotLostException`,见 fail-fast 行)、`CallTimeout`(见 `max-concurrent-calls` 行)、`StringSegmentId` / `StringSnowflakeId`(对应 `StringIdGenerator`)、`Clock.CACHE` 和 `LocalDateTimeConvert`(03 §7 已列为删除项)。
