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
| store/*(含 tck)、migration | ✅ 完成(§5) |
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

## 5. store/*(含 tck)与 migration

范围:`store/{jdbc,redis,mongo,zookeeper,remote,tck}`、`migration` 及其 README,对照 05(存储侧)/06/08/09/11 与 `v4.1.0`(baseline-4x 所用版本)。总调度抽查核实:R1-jdbc-01(`schema-mysql.sql` 的 `cosid_slot` 只有主键 `(namespace, slot)`,而 `JdbcSlotLeaseStore.java:176-180` 按 `owner_key = ? or last_owner_key = ? or slot = ?` 查询)、R1-jdbc-02(同处每次拼接 SQL)、R1-jdbc-06(`MySqlDialect.java:77-78` 只要 generated key 非 0 即采信)。

### 5.x 跨存储 / core SPI

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 触及冻结面 |
|---|---|---|---|---|---|---|
| R1-spi-01 | MAJOR | 易用性 / 数据安全 | `store/jdbc/.../JdbcStoreProvider.java:47-64`、`store/redis/.../RedisStoreProvider.java:68-111`、`store/mongo/.../MongoStoreProvider.java`、`store/zookeeper/.../ZookeeperStoreProvider.java`、`store/remote/.../RemoteStoreProvider.java` | 所有 provider 都静默忽略未知的 option。拼错 `rangeTable`(如写成 `rangeTabel`)会悄悄改用默认表 `cosid_range`。如果迁移写入的是自定义表,5.0 会从 `initialOffset` 重新发号,与已迁移的号段重复。`wait`、`writeConcern` 拼错时也会悄悄回到默认值 | 每个 provider 声明自己认识的 option(例如在 `StoreProvider` 上加 default 方法 `Set<String> options()`,由 RuntimePlan 校验),遇到未知 key 直接拒绝。必须在 RC 之前决定:RC 之后再收紧会让已有配置启动失败 | 是(配置) |
| R1-spi-02 | MAJOR | 架构 / 存储格式 | `schema-*.sql`(`range_key varchar(255)`、`namespace varchar(100)`)、`SqlServerDialect.java:103-107`、`ZookeeperRangeStore.java:47-56`、`ZookeeperSlotLeaseStore.java:84-96` | 没有跨后端统一的 key 语法与长度:JDBC 的 range key 最多 255 字符、namespace 最多 100;SQL Server 拒绝尾随空格;ZK 拒绝 namespace 中的 `/` 以及非法路径;Redis/Mongo 什么都接受。同一份配置换后端(或跨后端迁移)后,可能到运行时才失败,JDBC 上还只是一个笼统的 `JdbcStoreException`("Data too long") | 在 core 中定义可移植的 key/namespace/ownerKey 语法与长度上限,在 RuntimePlan 中校验,并写进 06 §1。列宽一旦冻结就无法再放宽 | 是(存储格式 + API 契约) |
| R1-spi-03 | MINOR | 高内聚低耦合 | `MongoRangeStore.java:120-123`(原样抛出驱动异常)、`Zk.java:113,136`(`CosIdException`)、`RedisStoreException.java:43-45` 与 `RemoteClient.java:63-66`(`StoreTimeoutException` 只作为 cause) | 各后端失败类型不统一:JDBC/Mongo/ZK 直接抛 `StoreTimeoutException`,Redis/Remote 把它包成 cause;Mongo 把 `MongoException` 原样泄漏给调用方;core 没有公共的 `StoreException` 基类 | 冻结前选定一种:core 增加 `StoreException`,各后端统一包装;或者在 SPI Javadoc 中明确"任意 RuntimeException,超时用 `StoreTimeoutException.isTimeout` 判断",并让 TCK 验证 | 是(异常契约) |
| R1-spi-04 | MINOR | 代码清洁 / 易用性 | `StoreConfig.java`(不校验 timeout)、`Jdbc.java:97`、`MongoStore.java:117-126`、`Zk.java:46`、`LettuceRedisScripts.java:56`、`RemoteClient.java:~40`、`core/.../internal/RuntimePlan.java:178` | `timeout = 0` 的语义互相矛盾:JDBC 和 Mongo 当作"无超时"(JDBC README 也这样写),ZK、Redis、Remote 直接拒绝,RuntimePlan 要求为正数。于是"0 = 无超时"的分支只有直接 new 才能走到 | 在 `StoreConfig` 的紧凑构造器中要求 timeout 为正数,删掉 0 的分支与相应文档 | 是(StoreConfig) |
| R1-spi-05 | MINOR | 高扩展性 | `core/.../store/StoreConfig.java` | `StoreConfig` 是公开 record,第三方测试会直接调用它的规范构造器。冻结后再加字段(例如 clock、metrics)会破坏所有 `new StoreConfig(...)` 调用 | RC 前确认字段集;或者提供静态工厂 / builder,作为对外推荐的构造方式 | 是 |

### 5.x jdbc

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 触及冻结面 |
|---|---|---|---|---|---|---|
| R1-jdbc-01 | MAJOR | 高性能 / 存储格式 | `JdbcSlotLeaseStore.java:176-180`;各 `schema-*.sql` 的 `cosid_slot` | `cosid_slot` 在 `owner_key` 和 `last_owner_key` 上都没有索引。acquire 候选查询的子查询 `o`(`namespace=? and slot<? and (owner_key=? or last_owner_key=? or slot=?)`)只能沿主键前缀扫描该命名空间的全部 `limit` 行(默认 1024),每行还要算一次 `nowMillis()` 表达式。4.x 有 `idx_instance_id`,而且只按需插入行。这是 acquire 延迟 +15% 的首要嫌疑 | `take` 总是同时写 `owner_key = last_owner_key = key`,`release` 保留 `last_owner_key`,所以 `owner_key=?` 必然蕴含 `last_owner_key=?`:谓词可以简化为 `last_owner_key=? or slot=?`,只需在五份 schema 上加一个索引 `(namespace, last_owner_key)`。06 §2.1 的 DDL 同步更新 | 是(schema DDL) |
| R1-jdbc-02 | MAJOR | 高性能 | `JdbcSlotLeaseStore.java:71,176-180,204,226-228,264-265,292-294,311-312`、`MySqlDialect.java:66-67,82`、`JdbcRangeStore.java:83,99,107` | 每次调用都重新拼 SQL 字符串,而表名和方言对一个 store 实例来说是固定的。acquire 要拼约 600 字符的 union 查询(含 3 份 `nowMillis()` 表达式);renew/release 每次拼两条约 200 字符的语句;reserve 拼约 250 字符。4.x 的 SQL 全是 `static final` 常量 | 在构造器里把全部 SQL 预计算成 final 字段(每个 store 一个 `Sql` record) | 否 |
| R1-jdbc-03 | MINOR | 高性能 | `JdbcRangeStore.java:48`、`JdbcSlotLeaseStore.java:89,253,282` | `"reserve " + key`、`"renew " + ns + "/" + slot` 这类 operation 字符串在成功路径上也会被立刻拼出来,但只有出错时才用到 | 延迟构造(传 key/slot,出错时再拼),或者传 `Supplier<String>` | 否 |
| R1-jdbc-04 | MINOR(存疑) | 高性能 | `MySqlDialect.java:66-79` | MySQL 的 upsert 绑定 5 个参数(4.x 只有 2 个),并且带 `RETURN_GENERATED_KEYS`。走 update 分支时影响行数是 2,Connector/J 的 `getGeneratedKeys()` 会按影响行数构造 2 行结果(4.x 的 update 只有 1 行) | 在客户端算好 `initialOffset+size`,绑定 3 个参数;两行 generated keys 的现象需要在 MySQL 9 驱动上实测确认 | 否 |
| R1-jdbc-05 | MINOR | 高性能 | `JdbcSlotLeaseStore.java:192-196` | 每次 acquire 读回最多 `1+8` 行 × 11 列,按列名取值(Connector/J 每个 ResultSet 都要建一次列名映射),再用 `TreeMap<Integer>`(装箱)和 `ArrayList` 加 `Collections.shuffle` | 改用列序号取值;`FREE_CANDIDATES` 可降到 4;候选集用数组 | 否 |
| R1-jdbc-06 | MAJOR | 数据安全(特性回退) | `MySqlDialect.java:76-79` 对比 4.x `JdbcIdSegmentDistributor.lastInsertId`(v4.1.0) | 4.1 专门为 SQL 代理和读写分离加过防线:generated key 小于 step 时抛错,并提供 `fetch-plan=transaction` 作为退路。5.0 只要 generated key 非 0 就直接采信;11:69 的结论是"fetch-plan 不再需要"。在会改写或丢失 last_insert_id 的代理后面,可能返回错误区间,进而重号 | 至少恢复合理性检查(`next >= size`;新 key 时 `next == initialOffset+size` 不可证,但可检查 `next > 0 && next >= size`),在 README 中声明不支持哪些代理;或者提供 `options.mysqlFetch=transaction`(`select … for update` + update) | 否(新增 option 属于增量) |
| R1-jdbc-07 | MINOR | 扩展性 | `JdbcDialect.java:95-97,28-34` | SPI 的 `firstRows(columns, table, where, orderBy, rows)` 与 Javadoc 中的列清单暴露了 store 拼 SQL 的内部方式:冻结后想换 acquire 的查询形态,就会被第三方方言绑住 | 收窄为 `limitClause(int rows)` / `topClause`,或者把该方法标为 evolving | 是(公开 SPI) |
| R1-jdbc-08 | MINOR | 易用性 | `JdbcStore.java:66` 对比 `JdbcStoreProvider.java:54-55` | `JdbcStore.of` 用 `JdbcStore.class.getClassLoader()` 查找方言,provider 用 TCCL:同一个自定义方言,一条路径能找到,另一条找不到 | 两处统一用 TCCL(TCCL 为空时回退到类加载器) | 否 |
| R1-jdbc-09 | MINOR | 易用性 / 安全 | `DriverManagerDataSource.java:60-62,89-91` | `setLoginTimeout` 改的是 JVM 全局的 `DriverManager`;`toString` 带出完整 url(可能含 `password=`);不带连接池时,登录过程不受 `StoreConfig.timeout` 约束 | `toString` 先脱敏;login timeout 取自 store timeout,只作用于本次连接 | 否 |
| R1-jdbc-10 | MINOR | 高性能 | `Jdbc.java:56-64` | 遇到 40001/40P01 立即重试,最多 5 次,没有退避;4.x `JdbcMachineLeaseStore.pauseAfterDeadlock` 有随机退避 | 加 1–64ms 的随机退避 | 否 |
| R1-jdbc-11 | MINOR | 架构(文档) | `docs/architecture/06-stores.md` §2.1 | 06 已经落后于代码:DDL 没有二进制排序规则(B-M1 的修复只在代码里);MySQL reserve 写的是"然后 `select last_insert_id()`",实际先读 OK 包,且漏写了 `if(sealed or next_value > ?)`;PG 漏了 `r.next_value <= ?`;H2 时钟写"同 PG",代码是 `current_timestamp` | 06 是存储格式的权威文档,RC 前与 schema 文件逐字对齐 | 否(文档) |
| R1-jdbc-12 | MINOR | 代码清洁 | `src/main/resources/.../schema-mariadb.sql:1` | 只有这一份 schema 缺许可证头 | 补上 | 否 |

### 5.x redis

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 触及冻结面 |
|---|---|---|---|---|---|---|
| R1-redis-01 | MINOR | 存储格式 | `slot-row.lua:3-5,23-38` | 槽位行是按位置解析的 `a|b|…` 字符串(带 `\\`、`\p` 转义),但没有写明演进规则 | 在 06 §2.2 / README 写明"新字段只追加在末尾,旧解码器忽略多余字段"(当前的 `decode` 已经这样处理),并加一个测试锁定 | 是(格式规则需在冻结前写明) |
| R1-redis-02 | MINOR | 架构(文档) | `docs/architecture/06-stores.md` §2.2 对比 `RedisStore.java:38-46` | 06 只列了 `slot/free/live/owner`,缺 `:meta`、`:held`,也没写行转义 | 按 `RedisStore` 的 Javadoc 更新 06 | 否(文档) |
| R1-redis-03 | MINOR | 易用性 | `RedisStoreProvider.java:135,147-151` | `wait=auto` 只在 open 时解析一次,而且不记日志。之后加了副本,不会 WAIT,持久性前提被悄悄丢掉;去掉副本,所有写都会失败,直到重启 | open 时用 INFO 级别记录解析出的副本数;README 写明要重启;可选:定期重新解析 | 否 |
| R1-redis-04 | MINOR | 高性能 | `Script.java:66`、`RedisStore.java:158-162,183-186` | 每次调用都用 stream 转换回复;acquire 每次拼 6 个 key 字符串和 7 个参数;`keys.toArray`/`args.toArray` 再各拷一次 | 普通循环;按命名空间缓存 key 列表(`ConcurrentHashMap<String, List<String>>`) | 否 |

### 5.x mongo

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 触及冻结面 |
|---|---|---|---|---|---|---|
| R1-mongo-01 | MINOR | 高性能 / 存储格式 | `MongoSlotLeaseStore.java:167,314-317` | acquire 用 `ownerKey==k OR lastOwnerKey==k` 匹配,并建了 `ns_ownerKey` 和 `ns_lastOwnerKey` 两个索引。`take` 同时写这两个字段,`release` 保留 `lastOwnerKey`,所以前一个条件被后一个蕴含:`ns_ownerKey` 是多余的,每次 acquire/release 都要多维护一个索引 | 只匹配 `lastOwnerKey`,去掉 `ns_ownerKey`。索引由 store 自己创建,属于库上格式,RC 前定 | 是 |
| R1-mongo-02 | MINOR | 高性能 | `MongoSlotLeaseStore.java:94-96,235-245,258-262` | take/renew 的 `findOneAndUpdate` 没有 projection,返回整份文档;pipeline 的 `Document` 每次调用都重建 | 加 projection(只取 lease 需要的字段);不随调用变化的部分预构建为常量 | 否 |
| R1-mongo-03 | MINOR | 架构(文档) | 06 §2.3 | 缺 `cosid_namespace {_id, limit, toleranceMs}`、`writtenAt` 字段和 3 个索引;写的是"E11000 重试一次",代码是 `ATTEMPTS=3` / `NAMESPACE_ATTEMPTS=5` | 更新 06 | 否(文档) |

### 5.x zookeeper

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 触及冻结面 |
|---|---|---|---|---|---|---|
| R1-zk-01 | MAJOR | 高性能 | `ZookeeperRangeStore.java:66-81,169-171`、`Zk.java:71-78,103-105` | reserve 分配 +19% 的来源(见"性能回退候选"):每次调用都 `PathUtils.validatePath`(`toCharArray`)、拼 `"reserve " + path`;每个 Curator 后台操作都有 `CompletableFuture`、两个 lambda、`CuratorEventImpl`、EventThread 队列节点和带时限 `get` 的 `Signaller`;`Ascii.Writer(32)` 加 `toBytes()` 是两次 byte[] 分配(4.x 只有 `ByteBuffer.allocate(8)`) | 缓存已校验的 path(`ConcurrentHashMap`,key 有限);`what` 延迟构造;`Value.bytes()` 先算好位数,一次分配到位;可选:热路径直接用 ZooKeeper 原生异步回调(`DataCallback`/`StatCallback`)加一个轻量 future,绕开 `CuratorEventImpl` | 否 |
| R1-zk-02 | MINOR | 架构(文档) | 06 §2.4 对比 `ZookeeperSlotLeaseStore.java:52-68,73-74`、`README.md:32-38` | 06 写的是"异步批量 getData 读取全部槽位";代码是 owner 索引 + 32→256 递增批次的只读 multi。06 没有记录 `/cosid/owner/{ns}/{key}`(URL 编码,`.` 和 `*` 也转义)、`.clock` 以及 `limit=..\ntolerance=..` 的元数据格式,这些都是存储格式 | 把 README 中的节点表并入 06 | 否(文档,但这是冻结格式的唯一权威描述) |

### 5.x remote

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 触及冻结面 |
|---|---|---|---|---|---|---|
| R1-remote-01 | MINOR | 架构(文档) | `docs/architecture/08-remote.md` §2 对比 `RemoteApi.java`、`RemoteSlotLeaseStore.java:96-108` | 08 是冻结的 HTTP 协议文档,但缺 `504 store_timeout`、`429 too_many_keys/too_many_namespaces`、`503 busy` 的 code;也没写 renew/release 请求体里带着 `expiresAt/floor/ceiling/storeNow` 窗口字段,以及 acquire 的扁平响应字段 | 以 `RemoteApi` 为准补全 08 §2 的表(状态码、code、请求和响应字段) | 是(协议) |
| R1-remote-02 | MINOR | 扩展性 | `RemoteStoreProvider.java:~45-47`、`RemoteClient.java:~40-44` | provider 拒绝任何 client,`HttpClient` 由内部构建。自定义 `SSLContext`(mTLS)、代理、HTTP/2 都只能靠 JVM 全局默认值 | 允许把 `java.net.http.HttpClient` 作为 `client` 传入(增量改动) | 否 |
| R1-remote-03 | MINOR | 安全 | `RemoteClient.java:~33-36` | 有 token 时,在明文 `http` 上发 bearer token 不会有任何提示 | `http` + token + 非回环地址时,在 open 时告警 | 否 |

### 5.x tck

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 触及冻结面 |
|---|---|---|---|---|---|---|
| R1-tck-01 | MINOR | 高内聚 | `store/tck/.../TestRuntimes.java:45-99` | 发布的 TCK 公开 API 里带着 CosId 自己的测试夹具(`ORDER/TOKEN/BILL/OFF` 命名空间、`spec()`、`options()`),冻结后就成了公开 API | 改用 core/spring 的 `java-test-fixtures`,或者移到 `internal` 包并注明不承诺 | 是(TCK API) |
| R1-tck-02 | MINOR | 高扩展性 | `store/tck`(缺失) | 只有 `RangeStoreContract` 和 `SlotLeaseStoreContract`,没有 provider/Store 级别的契约:`type()`、注入的 client 不关闭、自建的 client 随 store 关闭、超时映射为 `StoreTimeoutException.isTimeout`、坏 option 报错。第三方 provider 无法自证这些 | 增加 `StoreProviderContract`(增量) | 否 |
| R1-tck-03 | MINOR | 易用性 | `store/tck/`(没有 README)、`documentation/docs/*/guide/backends/test-suite.md` | `ContractsRan` 对第三方怎么用只有一句话,没有 Gradle/Maven 片段;文档说 `InMemoryStore` 是 "a `memory` provider",实际上 TCK 没有在 ServiceLoader 中注册(只有 `TestRuntimes` 里一个编程式 provider) | 补 README,给出 `JavaExec`/exec-maven 的示例;修正 memory provider 的说法 | 否 |

### 5.x migration

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 触及冻结面 |
|---|---|---|---|---|---|---|
| R1-mig-01 | MINOR | 安全(D-M2 残留) | `target/JdbcTarget.java:56`、`source/JdbcSource.java:124`、`StoreLocation.java:156-158` | 异常消息直接拼 `location.url()`(可能含 `;password=` 或 `?password=`),由 Main 打印到 stderr;`StoreLocation.toString` 也返回原始 url | 统一改用 `StoreLocation.redactUrl` | 否 |
| R1-mig-02 | MINOR(存疑) | 数据安全 | `RedisConnection.java`、`target/RedisTarget.java:93,158`(全文没有 WAIT) | 迁移对 Redis 的写入(4.x 封存、5.0 raise、prepare)不做 WAIT,与运行时的 `wait=auto` 不一致。apply 后若在复制追上之前发生 failover,raise/prepare 可能丢失,5.0 从更低的值开始,与 4.x 重叠;`verify` 只有在 failover 之后运行才能发现 | 有副本时每步写入后 `WAIT n timeout`,不足就中止 | 否 |
| R1-mig-03 | MINOR | 架构(文档) | `docs/architecture/09-migration.md` §2(第 3 条)、§3 | D-M1 的文档部分没有落实:09 §2 仍说遗漏的 4.x 实例"不会重复"(第二次快照之后新建的 key 不受这句话保护)。09 也没有记录 README 里那些 RC 前加的护栏(`--allow-unmigrated`、`--machine-from/--machine-to`、`--allow-new`、verify 报告新出现的 4.x key) | 同步 09 与 README | 否 |
| R1-mig-04 | MINOR | 代码清洁 / 高内聚 | `target/RedisTarget.java:42,65-69,151-153`、`target/ZookeeperTarget.java:232,377`、`target/MongoTarget.java:146-149` | 迁移工具自己重写了一遍各 store 的存储格式(Redis key 与行、ZK 节点文本、Mongo 字段名字面量),冻结后两处容易漂移。已有的缓解:`TargetAdminTest` 在 prepare 之后通过真实 store 做 acquire | 要求每个 target 都必须有"prepare 后经真实 store acquire/reserve"的测试;或者从各 store 暴露格式常量 | 否 |
| R1-mig-05 | MINOR | 易用性 | `target/JdbcTarget.java:47-52` 对比 `JdbcStoreProvider.java:53` | 迁移用 `user`,运行时 option 是 `username`;从 `--to-option` 抄到 5.0 配置时容易写错 | 两边都接受 `user` 和 `username` | 否 |

### 5.x Javadoc 缺口(公开成员)

- `store/jdbc/.../JdbcStoreException.java:25`:构造器。
- `store/redis/.../RedisStoreException.java:27,31`:两个构造器。
- `store/zookeeper/.../ZookeeperStoreProvider.java:53`:`TYPE`(其他 provider 都有)。
- `store/tck/.../FaultyRangeStore.java:30`、`FaultySlotLeaseStore.java:35`:构造器;`TestRuntimes.java:70,84`:`spec()` 和 `options()`。
- 其余公开类型与方法都有 Javadoc(脚本扫描了全部 `store/*/src/main`)。`store/tck` 是唯一没有 README 的 store 模块。

### 5.x 确认通过的部分

- **依赖方向**:每个 store 只依赖 `:core` 与各自的驱动(jdbc 只依赖 core,驱动由应用提供;remote 只用 JDK);store 之间没有互相引用;不暴露 `internal` 类型;store 模块没有 module-info,但发布时带 `Automatic-Module-Name`(`cosid.library.gradle.kts:30`)。
- **rc-review 修复复核**:
  - **B-B1** ✅ `LettuceRedisScripts.java:87-103`:有副本的 Cluster 上,`EVALSHA` 与 `WAIT` 用同一个按 nodeId 取得的连接;`wait=auto` 由 `fewestReplicas`(`:137-156`)同时比较 `INFO replication` 与 `CLUSTER REPLICAS`,部分主节点没有副本时拒绝。`ClusterReplicaWaitTest.writesFailWhileTheReplicasAreFrozen` 证明了确实在同一连接上。
  - **B-M1** ✅ MySQL `utf8mb4_0900_bin`、MariaDB `utf8mb4_nopad_bin`(都是 NO PAD)、SQL Server `Latin1_General_100_BIN2`,另加 `checkKey` 拒绝尾随空格(`SqlServerDialect.java:103-107`)。`*KeyExactnessTest` 覆盖全部 5 种数据库。
  - **B-N1** ✅ `Zk.java:162-165`:CAS 冲突中超时抛 `StoreTimeoutException`。
  - **B-N2** ✅ 连接池取连接超时映射为超时(`Jdbc.java:77-91`,`SQLTransientConnectionException`、08 类、`isTimeout`);acquire/renew/release 的 CAS 循环受整体期限约束(`JdbcSlotLeaseStore.java:90-93,254-257,283-286`)。
  - **B-N3** ✅ `RemoteApi.STORE_TIMEOUT`(504)→ cause 为 `StoreTimeoutException`(`RemoteClient.java` `Response.error`);HTTP 超时同样处理。
  - **B-N4** ✅ `acquire.lua:85-92`:幂等分支原样重写一次行;`ClusterReplicaWaitTest.idempotentAcquireWaitsForItsReplicaToo`。
  - **D-B1** ✅ `LegacySnapshot.java:82-94` 读取额外命名空间,`plan.extraNamespaces` 在 apply/verify 中沿用。
  - **D-B2** ✅ `config` 校验生成器名对应 `apply-result.json` 中的 key,或者有 `--allow-new`(`Migration.java:~757`、README)。
  - **D-B3** ✅ `Migration.java:476-489`:有未映射的 key 时中止,除非用 `--allow-unmigrated` 逐个点名。
  - **D-B4** ✅ `Migration.java:203-226`:比对 distributor 类型与 `--from/--machine-from`,两者分离时要求 `--machine-to`。
  - **D-M1** ✅ 代码:verify 报告结果中没有的 4.x key(`Migration.java:~591`);❌ 09 的文档没有改(R1-mig-03)。
  - **D-M2** ✅ `StoreLocation.redacted/redactUrl`(`:129-152`)去掉 userinfo 密码和 `password=`/`pwd=`;异常消息里还有残留(R1-mig-01)。
  - **D-M3** ✅ `Legacy4x.java:161-164`:秒级单位的自定义 epoch × 1000。
  - **D-N1** ✅ `LegacySnapshot.java:94`。
  - **D-N2** ✅ `ConfigConverter.java:346-379`:bits 与 tolerance 取自 result。
  - **D-N3** ✅ plan(`Migration.java:152,187`)和 apply(`:491`)都取源与目标时钟的较大者。
  - **D-N4** ✅ purge 检查存活租约(dry-run 除外)。
- **其他核实正确的点**:
  - Redis 的 key 构造(`cosid:{k}`、`cosid:{k}:sealed`、`cosid:{ns}:<suffix>`)三类之间不可能互相碰撞,同组 key 共享 hash tag。
  - Redis 的 free/live 不变式与有界清扫;release/renew 的 CAS。
  - Mongo 管道 upsert 带 `next <= MAX-size` 过滤;CSOT 超时映射;写关注拒绝不确认的级别,弱于 majority 时告警。
  - ZK 的 fence 存在数据里;CAS 用版本化 setData;新建 key 时 overflow 先检查。
  - JDBC 的表名经标识符校验,其余全部参数绑定;`take` 的 CAS 包含 `fence` 和 `next_floor_ms`。
  - 注入的 client 一律不关闭。
- **README 快速上手**:与代码中的 option 名和默认值核对一致(jdbc、redis、mongo、zookeeper、remote)。jdbc README 的 "zero: none" 见 R1-spi-04。

### 5.x 性能回退候选

### MySQL(acquire/renew/reserve 分配 +25~+67%,acquire 延迟 +15%)

基准:`benchmark/v5/.../MySqlStoreBenchmark.java` 对比 `benchmark/baseline-4x/.../MySqlStoreBenchmark.java`,两边都是 Hikari 32,driver 默认参数(没有 `cachePrepStmts`/`useServerPrepStmts`,所以每次 `prepareStatement` 都在客户端重新解析 SQL,分配量随 SQL 长度和参数个数增长);两边都对每条语句 `setQueryTimeout`,这部分不是差异来源。

1. **acquire 延迟 +15%:服务端扫描**。见 R1-jdbc-01。`o` 子查询没有可用索引,每次 acquire 都扫描该命名空间全部 1024 行(5.0 预建全部行;4.x 只按需插入,还有 `idx_instance_id`),并且每行计算一次 `timestampdiff(... utc_timestamp(6))`。
2. **acquire 分配**:
   - 候选查询每次调用重新拼约 600 字符的 SQL,带 7 个参数,客户端解析成本更高(`JdbcSlotLeaseStore.java:176-180`);
   - 读回最多 9 行 × 11 列,按列名取值(`:192-195`);
   - take 的 update 有 12 个参数(`:226-240`);
   - `UUID.toString()` 在 take 中再算一次(`:230`);
   - `TreeMap` 装箱、`ArrayList` 和 shuffle;
   - 基准本身也有不对称:
     - v5 的 `@TearDown(Level.Invocation) release` 是 select+update 两条语句(4.x 只有一条 update);GC profiler 的每操作分配量包含 invocation 级 teardown;
     - v5 每次调用 `UUID.randomUUID()`,4.x 是 `InstanceId.of(...)`。
3. **renew 分配**:
   - v5 是同一连接上的 `select current`(含 `nowMillis` 表达式)加 9 参数的 update,两条 SQL 每次都现拼(`:264-265,311-312`);
   - `"renew " + ns + "/" + slot`(`:253`);
   - `incarnation().toString()` 算两次(`:315,326`);
   - `Current`、`Optional`、`SlotLease`、lambda、`JdbcSession`。
   - 4.x 的 renew 是 `serverTime()` 加一条 7 参数的静态 SQL update;round trip 次数相近,差异主要在 SQL 构造和多出的参数。
4. **reserve 分配**:
   - 约 250 字符的 upsert 每次现拼(`MySqlDialect.java:66-67`),5 个参数(4.x 约 110 字符,2 个参数);
   - update 分支 `getGeneratedKeys` 可能有 2 行(存疑,R1-jdbc-04);
   - `"reserve " + key`(`JdbcRangeStore.java:48`)、lambda、`JdbcSession`、`OptionalLong`、`Range`。
   - InnoDB 的 `insert … on duplicate key update` 先尝试插入、遇到重复再更新,服务端比单纯 update 多一步,也会影响延迟。
5. **最可能的修复顺序**:
   1. 加 `(namespace, last_owner_key)` 索引并简化谓词(schema,RC 前);
   2. 预计算全部 SQL;
   3. operation 字符串延迟构造,`UUID` 字符串只算一次;
   4. reserve 参数合并;
   5. 基准的 teardown 与 UUID 对齐 4.x,或在报告中注明。

### ZooKeeper reserve(分配 +19%)

- **调用方式**:4.x(`ZookeeperIdSegmentDistributor.tryCompareAndAdd`,`CallTimeout.NONE`)是前台同步 `getData().storingStatIn(stat)` 和 `setData().withVersion()`,值是 8 字节二进制。v5 每次 reserve 是两次 Curator **后台**操作。
- **v5 每次调用多出的分配**(`ZookeeperRangeStore.java:66-81`、`Zk.java:71-78,103-105`):
  - `path()` 的拼接与 `PathUtils.validatePath`(内部 `toCharArray`);
  - `"reserve " + path`;
  - 每个操作一个 `CompletableFuture`、两个 lambda(Operation 与 BackgroundCallback)、`CuratorEventImpl`、ZooKeeper EventThread 的队列节点,以及 `CompletableFuture.get(timeout)` 的 `Signaller`;
  - 写入值时 `Ascii.Writer(32)` 的缓冲区加 `toBytes()` 的拷贝(两次 byte[] 分配)。
- **其他差异**:
  - 4.x 基线没有调用超时,而 v5 总是有期限(D 系列设计决定:原生超时),这一部分差异是有意为之;
  - 十进制文本编码本身影响很小。
- **修复**:见 R1-zk-01。
  - 缓存已校验的 path;
  - `what` 延迟构造;
  - `Value.bytes()` 一次分配;
  - 可选:热路径直接用 ZooKeeper 原生异步 API 加轻量 future,省掉 `CuratorEventImpl` 和一层回调。

### Mongo / Redis 热路径可疑点

- **Mongo**:
  - renew/take 每次重建 pipeline 文档,并且返回整份文档(R1-mongo-02);
  - `ns_ownerKey` 索引多余,每次 acquire/release 都要维护(R1-mongo-01);
  - reserve 已经有 projection,开销合理。
- **Redis**:
  - `Script.eval` 的 stream 转换,以及每次拼 key 和参数(R1-redis-04);
  - 在有副本的 Cluster 上,每次调用都要 `getPartitionBySlot` + `getConnection(nodeId)`,另加一次 `WAIT` 往返(这是设计决定);
  - `WAIT` 会阻塞该节点连接,同一节点上的 store 调用会串行地排在它后面:吞吐受副本确认延迟限制,README 中应写明。

### 5.x 特性对照

- **11 中的引用**:存储和迁移各行引用的测试类和方法都存在,内容与声明相符(抽查了 `ReplicaWaitTest`、`ClusterReplicaWaitTest`、`KeyExactnessTest`、`JdbcStoreProviderTest`、`MongoStoreProviderTest`、`ZookeeperStoreProviderTest`、`TargetAdminTest`)。有 6 处行号锚点已过期:
  - `ConfigBindingTest`:#L60→68、#L85→93、#L98→106、#L109→117;
  - `RemoteStoreTest`:#L158→167、#L216→225。
- **4.x 的存储能力**:
  - JDBC:表名定制 ✅(`rangeTable/slotTable/namespaceTable`);自定义 SQL → `JdbcDialect` ✅;query-timeout ✅;**fetch-plan=transaction 及代理防线 ❌**(R1-jdbc-06,11:69 的结论"不再需要"过于乐观)。
  - Redis:cluster ✅;wait-replicas/wait-timeout → `options.wait` 加 `timeout` ✅。
  - ZooKeeper:connectString、sessionTimeout、connectionTimeout、blockUntilConnectedWait、retry.{baseSleepTime,maxRetries,maxSleep} 与 4.x `CosIdZookeeperProperties` 一一对应 ✅;4.x 也没有 ACL/auth,需要时注入 `CuratorFramework`。
  - Mongo:database ✅、write-concern ✅。
  - proxy → `remote` ✅;但不能注入自定义 `HttpClient`(R1-remote-02);4.x 的两种客户端实现合并为 JDK HttpClient 一种,符合 11:112 的说法。
- **迁移**:四种后端的 4.x 读取与 5.0 写入齐全,支持跨类型迁移;Proxy 源按其背后的实际存储读取(09 §4.1)✅。
