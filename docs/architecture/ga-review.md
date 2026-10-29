# GA 前全面审查(D35)

> 状态:**第 1 轮完成**(2026-10-28,基于 `main` @ `3a1cdaae`,即 `5.0.0-alpha.1` 之后;MINOR 分诊见 §9);**第 2 轮完成,待总调度逐条核实**(2026-10-29,基于 `main` @ `8ebde66d`,即 `v5.0.0-rc.1` 之后,见 §10)。
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
| spring/*、server、integrations/*、extras、bom、examples、benchmark/v5 | ✅ 完成(§6) |
| documentation/、模块 README | ✅ 完成(§6.6、§7.3) |
| 结果条件(性能、特性、文档)与汇总 | ✅ 完成(§7、§8) |

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

### 5.1 跨存储 / core SPI

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 触及冻结面 |
|---|---|---|---|---|---|---|
| R1-spi-01 | MAJOR | 易用性 / 数据安全 | `store/jdbc/.../JdbcStoreProvider.java:47-64`、`store/redis/.../RedisStoreProvider.java:68-111`、`store/mongo/.../MongoStoreProvider.java`、`store/zookeeper/.../ZookeeperStoreProvider.java`、`store/remote/.../RemoteStoreProvider.java` | 所有 provider 都静默忽略未知的 option。拼错 `rangeTable`(如写成 `rangeTabel`)会悄悄改用默认表 `cosid_range`。如果迁移写入的是自定义表,5.0 会从 `initialOffset` 重新发号,与已迁移的号段重复。`wait`、`writeConcern` 拼错时也会悄悄回到默认值 | 每个 provider 声明自己认识的 option(例如在 `StoreProvider` 上加 default 方法 `Set<String> options()`,由 RuntimePlan 校验),遇到未知 key 直接拒绝。必须在 RC 之前决定:RC 之后再收紧会让已有配置启动失败 | 是(配置) |
| R1-spi-02 | MAJOR | 架构 / 存储格式 | `schema-*.sql`(`range_key varchar(255)`、`namespace varchar(100)`)、`SqlServerDialect.java:103-107`、`ZookeeperRangeStore.java:47-56`、`ZookeeperSlotLeaseStore.java:84-96` | 没有跨后端统一的 key 语法与长度:JDBC 的 range key 最多 255 字符、namespace 最多 100;SQL Server 拒绝尾随空格;ZK 拒绝 namespace 中的 `/` 以及非法路径;Redis/Mongo 什么都接受。同一份配置换后端(或跨后端迁移)后,可能到运行时才失败,JDBC 上还只是一个笼统的 `JdbcStoreException`("Data too long") | 在 core 中定义可移植的 key/namespace/ownerKey 语法与长度上限,在 RuntimePlan 中校验,并写进 06 §1。列宽一旦冻结就无法再放宽 | 是(存储格式 + API 契约) |
| R1-spi-03 | MINOR | 高内聚低耦合 | `MongoRangeStore.java:120-123`(原样抛出驱动异常)、`Zk.java:113,136`(`CosIdException`)、`RedisStoreException.java:43-45` 与 `RemoteClient.java:63-66`(`StoreTimeoutException` 只作为 cause) | 各后端失败类型不统一:JDBC/Mongo/ZK 直接抛 `StoreTimeoutException`,Redis/Remote 把它包成 cause;Mongo 把 `MongoException` 原样泄漏给调用方;core 没有公共的 `StoreException` 基类 | 冻结前选定一种:core 增加 `StoreException`,各后端统一包装;或者在 SPI Javadoc 中明确"任意 RuntimeException,超时用 `StoreTimeoutException.isTimeout` 判断",并让 TCK 验证 | 是(异常契约) |
| R1-spi-04 | MINOR | 代码清洁 / 易用性 | `StoreConfig.java`(不校验 timeout)、`Jdbc.java:97`、`MongoStore.java:117-126`、`Zk.java:46`、`LettuceRedisScripts.java:56`、`RemoteClient.java:~40`、`core/.../internal/RuntimePlan.java:178` | `timeout = 0` 的语义互相矛盾:JDBC 和 Mongo 当作"无超时"(JDBC README 也这样写),ZK、Redis、Remote 直接拒绝,RuntimePlan 要求为正数。于是"0 = 无超时"的分支只有直接 new 才能走到 | 在 `StoreConfig` 的紧凑构造器中要求 timeout 为正数,删掉 0 的分支与相应文档 | 是(StoreConfig) |
| R1-spi-05 | MINOR | 高扩展性 | `core/.../store/StoreConfig.java` | `StoreConfig` 是公开 record,第三方测试会直接调用它的规范构造器。冻结后再加字段(例如 clock、metrics)会破坏所有 `new StoreConfig(...)` 调用 | RC 前确认字段集;或者提供静态工厂 / builder,作为对外推荐的构造方式 | 是 |

### 5.2 jdbc

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

### 5.3 redis

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 触及冻结面 |
|---|---|---|---|---|---|---|
| R1-redis-01 | MINOR | 存储格式 | `slot-row.lua:3-5,23-38` | 槽位行是按位置解析的 `a|b|…` 字符串(带 `\\`、`\p` 转义),但没有写明演进规则 | 在 06 §2.2 / README 写明"新字段只追加在末尾,旧解码器忽略多余字段"(当前的 `decode` 已经这样处理),并加一个测试锁定 | 是(格式规则需在冻结前写明) |
| R1-redis-02 | MINOR | 架构(文档) | `docs/architecture/06-stores.md` §2.2 对比 `RedisStore.java:38-46` | 06 只列了 `slot/free/live/owner`,缺 `:meta`、`:held`,也没写行转义 | 按 `RedisStore` 的 Javadoc 更新 06 | 否(文档) |
| R1-redis-03 | MINOR | 易用性 | `RedisStoreProvider.java:135,147-151` | `wait=auto` 只在 open 时解析一次,而且不记日志。之后加了副本,不会 WAIT,持久性前提被悄悄丢掉;去掉副本,所有写都会失败,直到重启 | open 时用 INFO 级别记录解析出的副本数;README 写明要重启;可选:定期重新解析 | 否 |
| R1-redis-04 | MINOR | 高性能 | `Script.java:66`、`RedisStore.java:158-162,183-186` | 每次调用都用 stream 转换回复;acquire 每次拼 6 个 key 字符串和 7 个参数;`keys.toArray`/`args.toArray` 再各拷一次 | 普通循环;按命名空间缓存 key 列表(`ConcurrentHashMap<String, List<String>>`) | 否 |

### 5.4 mongo

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 触及冻结面 |
|---|---|---|---|---|---|---|
| R1-mongo-01 | MINOR | 高性能 / 存储格式 | `MongoSlotLeaseStore.java:167,314-317` | acquire 用 `ownerKey==k OR lastOwnerKey==k` 匹配,并建了 `ns_ownerKey` 和 `ns_lastOwnerKey` 两个索引。`take` 同时写这两个字段,`release` 保留 `lastOwnerKey`,所以前一个条件被后一个蕴含:`ns_ownerKey` 是多余的,每次 acquire/release 都要多维护一个索引 | 只匹配 `lastOwnerKey`,去掉 `ns_ownerKey`。索引由 store 自己创建,属于库上格式,RC 前定 | 是 |
| R1-mongo-02 | MINOR | 高性能 | `MongoSlotLeaseStore.java:94-96,235-245,258-262` | take/renew 的 `findOneAndUpdate` 没有 projection,返回整份文档;pipeline 的 `Document` 每次调用都重建 | 加 projection(只取 lease 需要的字段);不随调用变化的部分预构建为常量 | 否 |
| R1-mongo-03 | MINOR | 架构(文档) | 06 §2.3 | 缺 `cosid_namespace {_id, limit, toleranceMs}`、`writtenAt` 字段和 3 个索引;写的是"E11000 重试一次",代码是 `ATTEMPTS=3` / `NAMESPACE_ATTEMPTS=5` | 更新 06 | 否(文档) |
| R1-mongo-04 | MAJOR | 特性、文档 | `store/mongo/README.md:5`;`documentation/docs/{en,zh}/guide/backends/mongo.md:4`、`backends/index.md:12/11`;`.github/workflows/`(无 mongos) | README 与文档站承诺"副本集**或分片集群**",但 CI 与测试只在单节点副本集上跑 TCK(仓库内无 mongos/shard 配置),属未经验证的承诺。代码层面:集合**不分片**(默认)时经 mongos 路由到 primary shard,语义应与副本集相同;若用户把 `cosid_slot` 分片,`MongoSlotLeaseStore.java:211` 的 `findOneAndUpdate(and(inNamespace, FREE))` 与 `:216` 的排序查询不含完整分片键等值,6.0/7.0 上单文档 `findOneAndUpdate` 会被拒绝(7.1+ 放宽为广播),`$$NOW`(`:86`)在分片间取各自 mongod 时钟;`cosid_range`/`cosid_namespace` 按 `_id` 过滤,以 `_id` 为分片键时可定向。`majority` 读写与 CSOT 经 mongos 透传,未见代码问题。另:未分片集合被 `moveCollection`/`movePrimary` 迁到另一分片后,"存储时钟"换成另一台 mongod 的时钟(存疑,影响取决于分片间时钟偏差与 tolerance) | 二选一:① 加 CI job(mongos + 一个分片副本集 + 配置服务器副本集)跑 TCK,证实不分片集合可用,文档写明"cosid 的三个集合不要分片(数据量极小)"并说明时钟取自 primary shard;② 把 README 与文档站(中英文)的承诺收窄为副本集,分片集群列入"实测兼容性(不承诺)" | 否 |

### 5.5 zookeeper

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 触及冻结面 |
|---|---|---|---|---|---|---|
| R1-zk-01 | MAJOR | 高性能 | `ZookeeperRangeStore.java:66-81,169-171`、`Zk.java:71-78,103-105` | reserve 分配 +19% 的来源(见"性能回退候选"):每次调用都 `PathUtils.validatePath`(`toCharArray`)、拼 `"reserve " + path`;每个 Curator 后台操作都有 `CompletableFuture`、两个 lambda、`CuratorEventImpl`、EventThread 队列节点和带时限 `get` 的 `Signaller`;`Ascii.Writer(32)` 加 `toBytes()` 是两次 byte[] 分配(4.x 只有 `ByteBuffer.allocate(8)`) | 缓存已校验的 path(`ConcurrentHashMap`,key 有限);`what` 延迟构造;`Value.bytes()` 先算好位数,一次分配到位;可选:热路径直接用 ZooKeeper 原生异步回调(`DataCallback`/`StatCallback`)加一个轻量 future,绕开 `CuratorEventImpl` | 否 |
| R1-zk-02 | MINOR | 架构(文档) | 06 §2.4 对比 `ZookeeperSlotLeaseStore.java:52-68,73-74`、`README.md:32-38` | 06 写的是"异步批量 getData 读取全部槽位";代码是 owner 索引 + 32→256 递增批次的只读 multi。06 没有记录 `/cosid/owner/{ns}/{key}`(URL 编码,`.` 和 `*` 也转义)、`.clock` 以及 `limit=..\ntolerance=..` 的元数据格式,这些都是存储格式 | 把 README 中的节点表并入 06 | 否(文档,但这是冻结格式的唯一权威描述) |

### 5.6 remote

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 触及冻结面 |
|---|---|---|---|---|---|---|
| R1-remote-01 | MINOR | 架构(文档) | `docs/architecture/08-remote.md` §2 对比 `RemoteApi.java`、`RemoteSlotLeaseStore.java:96-108` | 08 是冻结的 HTTP 协议文档,但缺 `504 store_timeout`、`429 too_many_keys/too_many_namespaces`、`503 busy` 的 code;也没写 renew/release 请求体里带着 `expiresAt/floor/ceiling/storeNow` 窗口字段,以及 acquire 的扁平响应字段 | 以 `RemoteApi` 为准补全 08 §2 的表(状态码、code、请求和响应字段) | 是(协议) |
| R1-remote-02 | MINOR | 扩展性 | `RemoteStoreProvider.java:~45-47`、`RemoteClient.java:~40-44` | provider 拒绝任何 client,`HttpClient` 由内部构建。自定义 `SSLContext`(mTLS)、代理、HTTP/2 都只能靠 JVM 全局默认值 | 允许把 `java.net.http.HttpClient` 作为 `client` 传入(增量改动) | 否 |
| R1-remote-03 | MINOR | 安全 | `RemoteClient.java:~33-36` | 有 token 时,在明文 `http` 上发 bearer token 不会有任何提示 | `http` + token + 非回环地址时,在 open 时告警 | 否 |

### 5.7 tck

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 触及冻结面 |
|---|---|---|---|---|---|---|
| R1-tck-01 | MINOR | 高内聚 | `store/tck/.../TestRuntimes.java:45-99` | 发布的 TCK 公开 API 里带着 CosId 自己的测试夹具(`ORDER/TOKEN/BILL/OFF` 命名空间、`spec()`、`options()`),冻结后就成了公开 API | 改用 core/spring 的 `java-test-fixtures`,或者移到 `internal` 包并注明不承诺 | 是(TCK API) |
| R1-tck-02 | MINOR | 高扩展性 | `store/tck`(缺失) | 只有 `RangeStoreContract` 和 `SlotLeaseStoreContract`,没有 provider/Store 级别的契约:`type()`、注入的 client 不关闭、自建的 client 随 store 关闭、超时映射为 `StoreTimeoutException.isTimeout`、坏 option 报错。第三方 provider 无法自证这些 | 增加 `StoreProviderContract`(增量) | 否 |
| R1-tck-03 | MINOR | 易用性 | `store/tck/`(没有 README)、`documentation/docs/*/guide/backends/test-suite.md` | `ContractsRan` 对第三方怎么用只有一句话,没有 Gradle/Maven 片段;文档说 `InMemoryStore` 是 "a `memory` provider",实际上 TCK 没有在 ServiceLoader 中注册(只有 `TestRuntimes` 里一个编程式 provider) | 补 README,给出 `JavaExec`/exec-maven 的示例;修正 memory provider 的说法 | 否 |

### 5.8 migration

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 触及冻结面 |
|---|---|---|---|---|---|---|
| R1-mig-01 | MINOR | 安全(D-M2 残留) | `target/JdbcTarget.java:56`、`source/JdbcSource.java:124`、`StoreLocation.java:156-158` | 异常消息直接拼 `location.url()`(可能含 `;password=` 或 `?password=`),由 Main 打印到 stderr;`StoreLocation.toString` 也返回原始 url | 统一改用 `StoreLocation.redactUrl` | 否 |
| R1-mig-02 | MINOR(存疑) | 数据安全 | `RedisConnection.java`、`target/RedisTarget.java:93,158`(全文没有 WAIT) | 迁移对 Redis 的写入(4.x 封存、5.0 raise、prepare)不做 WAIT,与运行时的 `wait=auto` 不一致。apply 后若在复制追上之前发生 failover,raise/prepare 可能丢失,5.0 从更低的值开始,与 4.x 重叠;`verify` 只有在 failover 之后运行才能发现 | 有副本时每步写入后 `WAIT n timeout`,不足就中止 | 否 |
| R1-mig-03 | MINOR | 架构(文档) | `docs/architecture/09-migration.md` §2(第 3 条)、§3 | D-M1 的文档部分没有落实:09 §2 仍说遗漏的 4.x 实例"不会重复"(第二次快照之后新建的 key 不受这句话保护)。09 也没有记录 README 里那些 RC 前加的护栏(`--allow-unmigrated`、`--machine-from/--machine-to`、`--allow-new`、verify 报告新出现的 4.x key) | 同步 09 与 README | 否 |
| R1-mig-04 | MINOR | 代码清洁 / 高内聚 | `target/RedisTarget.java:42,65-69,151-153`、`target/ZookeeperTarget.java:232,377`、`target/MongoTarget.java:146-149` | 迁移工具自己重写了一遍各 store 的存储格式(Redis key 与行、ZK 节点文本、Mongo 字段名字面量),冻结后两处容易漂移。已有的缓解:`TargetAdminTest` 在 prepare 之后通过真实 store 做 acquire | 要求每个 target 都必须有"prepare 后经真实 store acquire/reserve"的测试;或者从各 store 暴露格式常量 | 否 |
| R1-mig-05 | MINOR | 易用性 | `target/JdbcTarget.java:47-52` 对比 `JdbcStoreProvider.java:53` | 迁移用 `user`,运行时 option 是 `username`;从 `--to-option` 抄到 5.0 配置时容易写错 | 两边都接受 `user` 和 `username` | 否 |

### 5.9 Javadoc 缺口(公开成员)

- `store/jdbc/.../JdbcStoreException.java:25`:构造器。
- `store/redis/.../RedisStoreException.java:27,31`:两个构造器。
- `store/zookeeper/.../ZookeeperStoreProvider.java:53`:`TYPE`(其他 provider 都有)。
- `store/tck/.../FaultyRangeStore.java:30`、`FaultySlotLeaseStore.java:35`:构造器;`TestRuntimes.java:70,84`:`spec()` 和 `options()`。
- 其余公开类型与方法都有 Javadoc(脚本扫描了全部 `store/*/src/main`)。`store/tck` 是唯一没有 README 的 store 模块。

### 5.10 确认通过的部分

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

### 5.11 性能回退候选

#### MySQL(acquire/renew/reserve 分配 +25~+67%,acquire 延迟 +15%)

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

#### ZooKeeper reserve(分配 +19%)

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

#### Mongo / Redis 热路径可疑点

- **Mongo**:
  - renew/take 每次重建 pipeline 文档,并且返回整份文档(R1-mongo-02);
  - `ns_ownerKey` 索引多余,每次 acquire/release 都要维护(R1-mongo-01);
  - reserve 已经有 projection,开销合理。
- **Redis**:
  - `Script.eval` 的 stream 转换,以及每次拼 key 和参数(R1-redis-04);
  - 在有副本的 Cluster 上,每次调用都要 `getPartitionBySlot` + `getConnection(nodeId)`,另加一次 `WAIT` 往返(这是设计决定);
  - `WAIT` 会阻塞该节点连接,同一节点上的 store 调用会串行地排在它后面:吞吐受副本确认延迟限制,README 中应写明。

### 5.12 特性对照

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

## 6. spring/*、server、integrations/*、extras、bom、examples、benchmark/v5 与文档

范围见标题,另含 `documentation/`(中英文)、根 README 与各模块 README、02/07/08。总调度抽查核实:R1-server-01(`ServerProperties.java:123` 的 admin-paths 为固定默认列表,`AuthFilter.java:65` 按 `requestURI − contextPath` 匹配)、R1-doc-01(`from-4x.md` Rollback 一节无条件写号段"safe")、R1-spring-01(`ZookeeperStoreProvider.java:74` 精确匹配驼峰键 `connectString`;`CosIdProperties.java:69` 为 `Map<String,String>`)。

### 6.1 spring(spring/autoconfigure、spring/starter)

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 触及冻结面 |
|---|---|---|---|---|---|---|
| R1-spring-01 | MAJOR | 易用性 | `spring/autoconfigure/.../CosIdProperties.java:66-69`(`Map<String,String> options`);各 provider 精确匹配驼峰键:`store/zookeeper/.../ZookeeperStoreProvider.java:71-80`、`store/jdbc/.../JdbcStoreProvider.java:61`、`store/remote/.../RemoteStoreProvider.java:53-59`、`JdbcTables.java:49`;`documentation/docs/{en,zh}/reference/config/server.md:5`("Every property can be set from the environment") | 存储 options 的键大小写敏感且是驼峰(`initSchema`、`connectString`、`baseUrl`、`writeConcern`、`retry.baseSleepTime`…),但 Spring 把环境变量绑定到 Map 时**键一律小写**(`COSID_STORES_MAIN_OPTIONS_CONNECTSTRING` 得到 `connectstring`),kebab 写法 `init-schema` 也匹配不上。未知键被静默忽略:Docker 部署的 cosid-server 用环境变量配 ZooKeeper 时会**悄悄连 `localhost:2181`**,`initSchema` / `writeConcern` 也会悄悄失效。只有 `uri`、`url`、`wait` 这种全小写的键碰巧能用(文档示例只用了它们) | 在 `CosIdSpecs`(或 core 的 `StoreConfig` 取值处)按"小写并去掉 `-`、`_`"规范化选项键;各 provider 声明自己认识的键,遇到未知键时启动失败或打 WARN;在 metadata 里给 `cosid.stores.*.options` 加键提示;修正 server.md 中"每个属性都能来自环境变量"的说法 | 是(选项键的规范与校验规则须在 RC 前定下) |
| R1-spring-02 | MINOR | 高扩展性 | `CosIdAutoConfiguration.java:74-95` | `cosIdRuntime` 和 `cosIdFiller` 都不是 `@ConditionalOnMissingBean`,`RuntimeOptions` 的 executor 写死为 `Optional.empty()`、时间源写死为 `TimeSource.SYSTEM`,Spring 用户没有任何钩子替换预取执行器或时钟。11 §4 却把 4.x `prefetch-worker` 标为"注入的执行器 → 等价",对 Spring 用户并不成立 | 增加 `RuntimeOptionsCustomizer` Bean(或读取一个带名字的 `Executor` Bean);`cosIdRuntime` 加 `@ConditionalOnMissingBean` | 否(新增) |
| R1-spring-03 | MINOR | 易用性 | `CosIdGeneratorRegistrar.java:81`(`bindOrCreate` 用默认处理器) | `cosid.*` 中写错的属性名(如 `stepp`、`seal-afer`)被静默忽略,会以默认值启动 | 绑定 `CosIdProperties` 时用 `NoUnboundElementsBindHandler`(`options` 除外),或至少对未知键打 WARN | 是(收紧校验后,原本能启动的配置会启动失败) |
| R1-spring-04 | MINOR | 易用性 | `CosIdSpecs.java:283-297` | cosid80 的 `radix` 项只取 `radix.radix`,`width` / `pad-start` 以及 `friendly.pad-start` 被静默忽略 | 这些字段被设置时报告问题(与其他校验一样按属性路径报告) | 否 |
| R1-spring-05 | MINOR | 易用性 | `CosIdActuatorConfiguration.java:53-54` | 健康指示器的开关是 `management.health.cosid.enabled`,但 Bean 名 `cosIdHealthIndicator` 让贡献者名变成 `cosId`(`/actuator/health/cosId`,health group 里也要写 `cosId`):同一个名字大小写不一致 | 把 Bean 名改为 `cosidHealthIndicator`,使路径变为 `/health/cosid`,并同步文档 | 是(健康检查路径与 group 名) |
| R1-spring-06 | MINOR | 高性能 | `CosIdMeterListener.java:131-133` | `cosid.segment.reserve` 计时器带 `key` 标签;周期号段每个周期都是一个新 key,每天新增一个永不移除的 Timer,长期运行后基数无界 | 周期号段只打 `name` 标签(或打 `period` 粒度标签);移除已封存周期的 meter | 是(指标标签) |
| R1-spring-07 | MINOR | 高内聚低耦合 | `CosIdIntegrationsConfiguration.java:75` | `CosIdJacksonModule` 直接注入 `CosIdRuntime`,于是创建 `JsonMapper` 时就会先启动运行时(阻塞获取租约、连接存储)。只要某个存储或监听器 Bean 依赖 JsonMapper 就会形成循环依赖(存疑:未见实际案例) | 改用 `new CosIdJacksonModule(name -> runtime.getObject().get(name, IdCodec.class))`,通过 `ObjectProvider` 延迟解析 | 否 |
| R1-spring-08 | MINOR | 架构 | `CosIdGeneratorRegistrar.java:93` | AOT / 原生镜像下生成器 Bean 定义在构建期就已固定,而 `cosIdRuntimeSpec` 在运行期重新绑定环境。部署时通过环境变量增删的生成器会没有 Bean,或者 Bean 失效,且没有任何报错;文档未说明 | 运行期比较 AOT 生成的名字集合与 spec,不一致时启动失败;在 spring-boot-starter 页写明原生镜像下生成器集合在构建期固定 | 否 |
| R1-spring-09 | MINOR | 代码清洁 | `CosIdIntegrationsConfiguration.java:58-66`;`CosIdProperties.java:58` | 集成相关属性用 `Environment.getProperty` 零散读取,不像设计说的那样是 `*Properties` record(只靠 additional metadata 描述);`Store.type` 的 javadoc 漏了 `remote`(IDE 提示里会看到) | 收拢为 `CosIdProperties` 的嵌套 record,或在 07 中写明这是例外;javadoc 补上 `remote` | 否(属性名不变) |
| R1-spring-10 | MINOR(存疑) | 易用性 | `CosIdProperties.java:198-205` | 无字段的 codec 项写成 `- friendly: {}` 或 `- period: {}` 时,大概率绑定为 null,报"0 set",提示令人困惑;想全用默认值时必须随便写一个字段 | 支持简写 `- friendly` / `- period`,或把"0 set"的报错改为提示"至少写一个字段" | 否 |

### 6.2 server

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 触及冻结面 |
|---|---|---|---|---|---|---|
| R1-server-01 | MAJOR | 易用性(安全) | `server/.../ServerProperties.java:122-124`;`AuthFilter.java:65,76` | `admin-paths` 硬编码为 `/actuator/**`、`/v3/api-docs/**`、`/swagger-ui/**`,并用 `requestURI − contextPath` 去匹配。一旦设置了 `management.endpoints.web.base-path`(如 `/manage`)、`spring.mvc.servlet.path` 或 `springdoc.*.path`,受限的 client token 就能重新访问 `cosid` 端点(给任意生成器发号)、prometheus 和 api-docs,C-B1 换个条件又出现。另外,设置 `management.server.port` 后 Actuator 运行在子上下文里,`FilterRegistrationBean` 不会作用到它,即 Actuator **完全不鉴权**(存疑:按 Boot 子上下文的行为推断,未实测) | 改为默认拒绝:受限 token 只放行 `/ranges:*`、`/slots:*`、`/ids/**`(白名单),其他路径一律要求不受限 token;或者从 `WebEndpointProperties` 与 springdoc 属性推出 admin 路径;`management.server.port` 与主端口不同时拒绝启动,或给管理上下文也注册过滤器;补测试 | 是(`admin-paths` 的语义) |
| R1-server-02 | MINOR | 易用性 | `server/build.gradle.kts:27-47`;`documentation/docs/en/guide/server.md:28,138` | server 没有任何检查后端存储的健康指示器(没有 spring-data-redis / DataSource Bean / MongoClient Bean,`cosId` 只看槽位)。文档说"liveness 不访问存储",暗示 readiness 或 health 会访问,实际上存储宕机时 readiness 仍是 UP | 增加 `BackingStore` 健康检查(带超时的轻量调用,如读存储时钟),并纳入 readiness group;或者修正文档 | 否 |
| R1-server-03 | MINOR | 高性能 / 高内聚 | `CosIdServer.java:85-93`、`BackingStore.java:82`、`CosIdStoreClients.java:53-63` | 开启 `/ids` 时,运行时又从同一份 `cosid.stores` 打开第二个 Store:JDBC 走无连接池的 `DriverManagerDataSource`(每次补充都新建连接),Redis、Mongo、ZK 各多一个客户端 | 让运行时复用 `BackingStore`(以 `StoreProvider` Bean 或 client 的形式交给运行时),或把 Hikari 池注册为 `DataSource` Bean | 否 |
| R1-server-04 | MINOR | 高性能 | `server/.../IdController.java:86-88,103` | `count` 最多 1000,每个 id 都装箱为 `List<Long>` | 改用 `long[]` 的 body(JSON 形状不变) | 否 |
| R1-server-05 | MINOR | 架构(文档) | `docs/architecture/08-remote.md:23-41` | 08 没有反映已合入的 C-B1、C-B2、C-M1、B-N3:`admin-paths`、token 最短 16 位与拒绝 `${`、`max-namespaces-per-client`、`key-idle-timeout`、`504 store_timeout`;renew/release 请求体的说明也漏了 `ownerKey`(仅在"R4-A 补充"中提到) | 改文档,以代码为准 | 否 |

### 6.3 integrations

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 触及冻结面 |
|---|---|---|---|---|---|---|
| R1-int-01 | MINOR | 高内聚低耦合 / 代码清洁 | `integrations/{activiti,flowable,axon,spring-data-jdbc}/.../TextIds.java`(4 份相同);`spring/autoconfigure/.../CosIdEndpoint.java:84-88`;core 已有 `internal/GeneratorEntry.java:281 text()` | "任意策略的文本 id"在 core 内部已经存在,但 `CosIdRuntime` 没有公开它,于是 4 个集成各复制一份,并按字符串 `"cosid80"` / `"periodic-segment"` 分支,与策略名耦合 | `CosIdRuntime` 增加 `Supplier<String> text(String name)`(或 `textGenerator`),删除这 4 份复制和端点里的分支 | 是(core 公开 API,新增,宜在冻结前加入) |
| R1-int-02 | MINOR | 易用性 | `CosIdIntegrationsConfiguration.java:89-100`;4.x `cosid-spring-boot-starter/.../jdbc/CosIdJdbcDisabledHintAutoConfiguration.java` | 4.x 在 `cosid.jdbc.enabled` 关闭(默认)时,会在启动日志里列出 CosId 本会填充的 Spring Data JDBC 实体;5.0 去掉了这个提示,引入 `cosid-spring-data-jdbc` 后什么都不做,也没有任何提示 | 恢复这条 INFO 提示(`SmartInitializingSingleton`) | 否 |
| R1-int-03 | MINOR | 文档 / 特性对照 | `integrations/jackson/build.gradle.kts:20`;`documentation/docs/*/guide/integrations/jackson.md`;`guide/migration/from-4x.md:248` | cosid-jackson 从 Jackson 2(`com.fasterxml`)改为只支持 Jackson 3(07 §5 已决定)。但迁移页写的是"the same artifacts",`@AsString` 属性也已改名(`value`→`codec`、`radixCharSize`→`width`、`radixPadStart`→`padStart`、`timestampBit` / `machineBit` / `sequenceBit`→`timeBits` / `nodeBits` / `seqBits`、`FRIENDLY_ID`→`FRIENDLY`),文档里都没有对照表 | 在 jackson 页与 from-4x 加"4.x → 5.0"对照,写明不再支持 Jackson 2;11 §6 的 Jackson 行注明这一点 | 否 |
| R1-int-04 | MINOR | 易用性 | `integrations/mybatis/.../CosIdInterceptor.java:85-93` | 只认一个全局 `listKey`;`insert(@Param("order") Order o)` 这样单个实体放在 ParamMap 里时不会被填充(与 4.x 相同,但文档没有写) | 文档写明;可选:遍历 ParamMap 中所有 `Iterable` 和实体值 | 否 |

### 6.4 extras(sharding)

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 触及冻结面 |
|---|---|---|---|---|---|---|
| R1-sharding-01 | MINOR | 代码清洁 | `extras/sharding/.../ExactCollection.java:39,62,66,70`;`LocalDateTimeConvertor.java:18` | 公开方法和接口缺 javadoc(doclint 允许缺失,但这是公开 API) | 补上 | 否 |
| R1-sharding-02 | MINOR | 易用性 | `CachedSharding.java:77-87` | 缓存直接返回被包装算法的集合实例;如果调用方修改它(`ExactCollection` 支持 `add(index, …)`),会污染缓存(4.x 同样存在) | 缓存不可变视图,或在 javadoc 写明不可修改 | 否 |

### 6.5 bom / examples / benchmark

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 触及冻结面 |
|---|---|---|---|---|---|---|
| R1-build-01 | MINOR | 文档 | `documentation/docs/{en,zh}/guide/getting-started.md`("Next"一节) | 说三个示例都能用 `./gradlew :examples:<name>:bootRun` 运行;`server` 示例需要先跑 server(compose),`plain-stores` 只是测试 | 按 `examples/README.md` 改写 | 否 |
| R1-build-02 | MINOR | 代码清洁 | `benchmark/v5/.../SpringStartBenchmark.java:36` | javadoc 里有转义反引号 `` \` ``(Markdown 残留) | 改为 `{@code …}` | 否 |
| R1-build-03 | MINOR | 架构(文档) | `docs/architecture/02-modules.md:30` | 目录表里的 examples 只有三个,漏了 `plain-stores`(L14) | 补上 | 否 |

bom:16 个发布模块与 `settings.gradle.kts` 一致,`checkBom` 会守住,无发现。starter 只依赖 core 和 autoconfigure,与 D14 一致。

### 6.6 documentation(VitePress、README、设计文档)

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 触及冻结面 |
|---|---|---|---|---|---|---|
| R1-doc-01 | MAJOR | 文档(重号风险) | `documentation/docs/en/guide/migration/from-4x.md:235-237`、`zh/.../from-4x.md:212-214`;`docs/architecture/09-migration.md:137`("封存不影响回滚") | 回滚一节无条件写"号段:安全,封存只会调高 4.x 的值"。但手工迁移的封存是"建议、可选",工具也有 `--no-seal-source`:**没有封存就回滚时,4.x 从 `last_max_id` 继续取段,正好与 5.0 从 `last_max_id+1` 起已发出的号重叠,产生重号** | 回滚一节写明"只有封存过才安全;未封存时,回滚前必须先把 4.x 的值调到不低于 5.0 已用的最大值(或补做封存)";09 §6.3 同步 | 否 |
| R1-doc-02 | MINOR | 文档 | `docs/architecture/07-runtime-and-spring.md:151`、`roadmap.md:78`、`AGENTS.md:9-10` | rc-review 的 C-N4(`codec: { radix: 62 }` 无法绑定)、C-N5(`/v5` 残留)、E-N1("4.x 代码在 `main`")**并未修在 main 上**:修复只在 `origin/wip/rc-review`(#1344 未合入) | 把这三处改动从 `wip/rc-review` 摘到 main | 否 |
| R1-doc-03 | MINOR | 架构(文档) | `07-runtime-and-spring.md:157,159`;`reference/config/basic.md:11`;`guide/integrations/spring-boot-starter.md:15`(中英文) | "不再有各特性的 `enabled` 开关",与实际存在的 `cosid.mybatis.enabled`、`cosid.jdbc.enabled`、`cosid.activiti.enabled`、`cosid.flowable.enabled` 矛盾;07 还写着"周期号段列表第一项必须是 `date-prefix`",代码已支持 `period` 项 | 改文档:生成器、存储没有开关,集成有各自的开关;补上 `period` 项的说明 | 否 |
| R1-doc-04 | MINOR | 文档 | `guide/migration/from-4x.md`(中英文) | 运行手册没有提 `--machine-from` / `--machine-to`(D-B4:4.x 号段与机器号在不同存储)、`--allow-unmigrated`(D-B3)、`--target-config`、`config --allow-new`、`--gap`;这些只在 `migration/README.md` 里。拆分存储的用户照着站点文档做会被 apply 拒绝 | 在 Plan / Apply 两步补上这些选项,并链接到 README | 否 |
| R1-doc-05 | MINOR | 易用性 | `migration/.../target/JdbcTarget.java:48`、`RedisConnection.java:55`;`from-4x.md:40,62` | 迁移工具的 JDBC 账号选项是 `user`,而运行时 JDBC 存储是 `options.username`:同一个概念两个名字,容易写错 | 迁移工具同时接受 `username`(别名) | 否 |
| R1-doc-06 | MINOR | 文档 | `docs/architecture/11-feature-parity.md` §5–§7 | 引用的测试都存在、方法都在,但有 17 处行号锚点失效(偏 7–59 行),例如 `AuthTest.allowAnonymousStartsOpen` 写 #L175,实际在 L234;`IdFillerTest` 整体偏 19 行;`CosIdRuntimeHintsTest` 写 #L36,实际在 L50 | 重新生成锚点(或去掉行号) | 否 |
| R1-doc-07 | MINOR | 文档 | `guide/integrations/spring-boot-starter.md:113` | 只说"注册原生镜像所需反射",没有说 AOT 下生成器集合在构建期固定(见 R1-spring-08) | 补一句 | 否 |

### 6.7 确认通过的部分

- **配置参考与代码一致(a)**:`reference/config/{basic,generators,server}.md` 列出的 `cosid.*`、`cosid.server.*` 属性名和默认值,逐项与 `CosIdProperties`、`ServerProperties` 的 record 和 `@DefaultValue` 对照,全部一致。核对过的项包括:timeout 2s、bits 10、ttl 30s、tolerance 1s、ahead 10s、step 100、initial-offset 1、lookahead 1s、reserve-timeout 3s、prewarm 1m、regression 5ms / 1s、radix 62、friendly pad-start true、date-prefix `yyyyMMdd` / `-` / `ID_TIME`、period `true` / `-`、metrics.generated false,limits 100ms / 1h / 1m / 65536 / 1e12 / 1000 / 10000 / 1000 / 1h,blocking 64 / 1024,public-paths 与 admin-paths 的默认列表。后端选项表也与各 provider 的默认值一致(`cosid_db`、`sweepBatch` 16、ZK 的 60s / 15s / 10s / 100ms / 5 / 500ms、remote connectTimeout 5s,jdbc 表名)。
- **中英文页面对应(b)**:页面集合完全相同;每页的标题数、代码块数、表格行数都相同;去掉注释后代码块内容逐字相同;三张配置表的第一列和默认值列只有翻译差异;两个 sidebar 的链接一致。README 与 README.zh-CN 的代码块一致。
- **快速上手与示例(c)**:坐标(`cosid-bom`、`cosid-spring-boot-starter`、`cosid-store-*`)、`__COSID_VERSION__` 替换、YAML 片段都能绑定到当前 record;`RuntimeOptions` 的 8 参数构造器存在;示例的 yaml、compose 与代码一致(compose 的卷路径相对 compose 文件,正确)。
- **迁移文档(d)**:from-4x、README、09 里出现的 CLI 选项在 `Main.java` 中全部存在;D32 手工迁移一节(时间型、普通号段、何时必须用工具)与 09 §6 一致;回滚问题见 R1-doc-01。
- **运维文档(e)**:monitoring 页的 19 个 meter 名称、类型、标签,与 `CosIdMeterListener` 逐一对应;`cosid.server.call.rejections{reason}` 与 `Rejections` 一致;健康指示器 `cosId` 与开关 `management.health.cosid.enabled` 与代码一致(大小写问题见 R1-spring-05)。
- **模块 README(f)**:server、integrations、sharding、store/remote 的 README 与代码一致。
- **javadoc(g)**:spring 和 integrations 的公开 API 基本都有 javadoc;缺失的只是常量和 `@Bean` 方法。
- **依赖方向**:autoconfigure 对 store 与 integrations 都是 `compileOnly` 加 `@ConditionalOnClass`;每个 integration 只依赖 core 和自己的库;server 依赖 autoconfigure、stores 和 store/remote(用到 RemoteApi),与 `DependencyRules.kt` 一致。
- **性能**:
  - `AnnotatedIdFiller` 按类用 `ClassValue` 缓存 `MethodHandle`(`invokeExact`),MyBatis 每次 insert 无反射;
  - Jackson 的 `createContextual` 每个属性只解析一次;
  - 运行时的视图有缓存;
  - `CompositeListener` 对 0 个和 1 个监听器不包装;
  - server 请求路径:正则是静态编译的,KeyCap 按客户端加锁,存储调用在有界池上执行,没有全局锁。
- **rc-review 修复复核**:
  - C-B1 ✅:`admin-paths` 让受限 token 得到 403;但换路径、换端口后会失效,见 R1-server-01。
  - C-B2 ✅:`requireToken` 拒绝含 `${` 的 token 和少于 16 个字符的 token,并拒绝重复 token。
  - C-M1 ✅:按客户端计数,同时限制命名空间数,reserve 失败时 forget,并按 LRU / idle 淘汰。
  - C-N1 ✅:存储错误只返回通用消息,细节写日志。
  - C-N2 ✅:保留名 `strings`、`periodic`。
  - C-N3 ✅:hints 和 metadata 都包含 remote。
  - E-B2 ✅:`docker-deploy.yml` 推送到 Docker Hub、ghcr、aliyun,多架构,`latest` 只在主版本 ≥5 的 GA 上打。
  - E-B3 ✅:迁移工具 jar 由 `gh release upload` 上传,权限 `contents: write`。
  - E-M2 ✅:`javadocSite` 由 Gradle 按 artifactId 命名目录。
  - E-M3 ✅:发布 job 依赖 `verify-build`。
  - E-N2 ✅:`checkPublishedMetadata` 已在 `cosid.library` 中,并拒绝 enforced-platform。
  - E-N3 ✅:有 `checkBom`。
  - E-N4 ✅:SCM 带 `scm:git:` 前缀,并有 developerConnection。
  - E-N6 ✅:没有 `TODO(R1)`。
  - E-N7 ✅:Lua 脚本有许可证头。
  - E-N8 ✅:codeql 的 paths-ignore 已是 5.0 路径。
  - E-N9 ✅:发布由 `release: published` 触发。
  - **C-N4、C-N5、E-N1 ❌ 未在 main 上**(R1-doc-02)。

### 6.8 性能回退候选

只读审查,没有新的测量。下面是代码层面的候选:

1. R1-server-03:`/ids` 在 JDBC 上补充时无连接池,每次新建连接;会影响 server 发号场景的延迟。现有基准不覆盖 server。
2. R1-spring-06:周期号段的 Timer 随周期累积,长期运行时内存和抓取量增长;不影响基准。
3. R1-server-04:`/ids` 的装箱分配,每次请求最多 1000 个对象。
4. 本轮在 MyBatis / Spring Data JDBC 填充、Jackson 和 Actuator 路径上没有发现新的热路径分配或锁。

### 6.9 特性对照(4.x → 5.0)

- 11 §5–§7 引用的测试全部存在并测试了对应能力,只是锚点失效(R1-doc-06)。
- 已覆盖的 4.x 能力(由 `cosid-migration config` 转换):
  - `cosid.machine.*`(distributor 类型、stable、instance-id、guarder、state-storage)→ `machine.leased` / `static`;
  - `cosid.snowflake.share` 与 `cosid.segment.share` → `default`;
  - `cosid.segment.chain` / `prefetch-worker` → `lookahead`(执行器不可配见 R1-spring-02);
  - `snowflake.provider.*.converter` → codec 列表;
  - `clock-backwards` → `regression`;
  - 3 个 4.x Actuator 端点 → 一个 `cosid` 端点,DELETE → `disabled`;
  - `MachineIdHealthIndicator` → `CosIdHealthIndicator`;
  - proxy server → server 加 store/remote,另增 key 前缀限定与 `max-namespaces-per-client`;
  - ShardingSphere 由 ShardingSphere 自己维护(blog 页已标为 4.x 文章)。
- 缺失或变化,需要在文档中说明:
  - Jackson 2 不再支持,`@AsString` 属性改名(R1-int-03);
  - Spring Data JDBC 关闭时的启动提示没了(R1-int-02);
  - 4.x `cosid.metrics.enabled` 总开关没了,只能用 `management.metrics.enable.cosid=false`,迁移页没有写;
  - 4.x `cosid.metrics.generated.enabled` 在 5.0 是布尔值 `cosid.metrics.generated`,以后无法再加子属性(MINOR,触及冻结面:是);
  - Spring 用户无法注入预取执行器(R1-spring-02)。

## 7. 结果条件

### 7.1 性能(D21,按 D34 判定;只列候选,未跑基准)

| 门槛回退 | 疑似原因 | 修复候选 |
|---|---|---|
| SegmentNoPrefetch 分配 24→32 B、吞吐 −6% | 每次补充分配并发布一个 32 B 的 `RangeCursor`(4.x 是一个 24 B 对象,且无锁快路),每次补充两次加锁、两次 `currentMillis`、一次 `BatchSizer.record` | R1-core-04;另 R1-core-06(运行时路径上 `ObservedStores` 的 lambda,门槛未覆盖) |
| MySQL acquire 耗时 +15%、acquire/renew/reserve 分配 +25~+67% | `cosid_slot` 缺 `(namespace, last_owner_key)` 索引,候选查询扫整个命名空间;每次调用重新拼接约 600 字符的 SQL(驱动按 SQL 文本重新解析);acquire 读回多行多列;只在出错时才用到的字符串被预先拼接;基准本身不对称(v5 每次 `UUID.randomUUID()`、TearDown 释放两条语句) | R1-jdbc-01(须在 RC 前,改 DDL)、R1-jdbc-02;同时修正基准的不对称(§5.11) |
| ZK reserve 分配 +19% | 两次 Curator 后台操作(各有 `CompletableFuture`、lambda、`CuratorEventImpl`、带时限等待)、每次路径校验、预拼错误字符串、两次 byte[] | R1-zk-01;其中"总是带超时"是有意设计,剩余部分可优化 |
| 时间型(P1/P2,非门槛失败行) | 每个 id 在监视器内一次 volatile 写 | R1-core-05(存疑) |
| 服务端 `/ids`(无基准) | 运行时另开一个无连接池的 JDBC Store | R1-server-03 |

### 7.2 特性(11-feature-parity 与 4.x)

不是"等价"、需修代码或改对照表的:
- **跨服务全局唯一**(`cosid.generator.namespace`):R1-x-06(缺失,冻结面)与 R1-x-07(迁移静默丢弃,BLOCKER)。
- **CosIdGenerator 位宽与 `sequence-reset-threshold` 不可配**:R1-x-03(冻结面);迁移误拒绝 R1-x-04;friendly 不补零 R1-x-05 / R1-spring-04。
- **按名注册自定义生成器**(`IdGeneratorProvider.set`):R1-core-02(冻结面)。
- **SQL 代理下 generated key 校验与 `fetch-plan=transaction` 退路**:R1-jdbc-06。
- **Spring 用户注入预取执行器或时钟**:R1-spring-02;`acquireTimeout` 不可配:R1-core-03。
- **Jackson 2 不再支持、`@AsString` 改名**、Spring Data JDBC 关闭提示、`cosid.metrics.enabled` 总开关:R1-int-02、R1-int-03、§6.9,文档补对照即可。
- 11 的对照表需补行:时间戳溢出、存储回退检测(`highWater`)、自定义 Period(§4.3);测试锚点行号失效(R1-core-15、R1-doc-06,共约 60 处)。

### 7.3 文档

- **会导致重号的文档**:R1-doc-01(未封存时回滚)。
- **未经验证的承诺**:R1-mongo-04(Mongo 分片集群)。
- **设计文档落后于代码**:03/04/05/01/02(R1-core-14)、06 的 DDL 与 Redis/ZK 键(§5)、08(R1-server-05)、07(R1-doc-03);rc-review 中 C-N4、C-N5、E-N1 的修复只在 `wip/rc-review`,未上 main(R1-doc-02)。
- **迁移指南缺项**:静态取号写法(R1-x-01)、跨服务唯一(R1-x-08)、拆分存储等 CLI 选项(R1-doc-04)。
- **配置参考**:属性名与默认值逐项与 `*Properties` record 一致(§6.7);但环境变量对存储 options 不可用(R1-spring-01)。
- **中英文**:页面、标题、代码块、表格逐页对应(§6.7)。
- **javadoc**:core 导出包约 160 个公开声明缺 javadoc,SPI 契约(`IdCodec.encode`、`StoreProvider.open`、listener 线程约定)缺失,无 `package-info.java`(R1-core-09、R1-core-16);store 见 §5.9。

## 8. 汇总

共 **95** 条(按编号计;其中 R1-spring-04 与 R1-x-05、R1-doc-06 与 R1-core-15、R1-spring-01 与 R1-spi-01 同源,修复时合并)。

| 严重度 | 触及冻结面 | 不触及 | 合计 |
|---|---|---|---|
| BLOCKER | 0 | 1 | 1 |
| MAJOR | 10 | 9 | 19 |
| MINOR | 17 | 58 | 75 |
| 合计 | 27 | 68 | 95 |

**BLOCKER**:R1-x-07(迁移静默丢弃 `cosid.generator.namespace`,跨服务 80 位 ID 迁移后重复;T4 不含全局命名空间)。

**MAJOR 且触及冻结面(RC 前必须决定)**:R1-x-03(cosid80 布局)、R1-x-06(命名 machine)、R1-core-01(公开 record 的演进)、R1-core-02(自定义生成器)、R1-core-03(`acquireTimeout`)、R1-spi-01 / R1-spring-01(存储 options 的键规范与未知键校验)、R1-spi-02(可移植的 key/namespace 语法)、R1-jdbc-01(`cosid_slot` 索引,DDL)、R1-server-01(admin 路径改为白名单)。

**MAJOR 不触及冻结面**:R1-x-01、R1-x-04、R1-x-08、R1-core-04、R1-jdbc-02、R1-jdbc-06、R1-mongo-04、R1-zk-01、R1-doc-01。

**建议的修复分组**(供总调度排队,一次一个):
1. **WP-API-freeze**(core + spring):R1-core-01、R1-core-02、R1-core-03、R1-x-03、R1-x-06 及其 Spring 配置,R1-core-11/12/17 等冻结面 MINOR——先在设计文档(03/05/07)定稿再改代码。
2. **WP-store-contract**:R1-spi-01、R1-spi-02、R1-spring-01、R1-jdbc-01(+ 五份 schema 与迁移工具的 admin 写路径)。
3. **WP-perf**:R1-core-04、R1-core-05、R1-core-06、R1-jdbc-02、R1-zk-01,修正 MySQL 基准的不对称;之后按 D34 重跑门槛。
4. **WP-server-auth**:R1-server-01(+ R1-server-02/03)。
5. **WP-migration**:R1-x-07、R1-x-04、R1-jdbc-06 的迁移侧、R1-doc-05。
6. **WP-docs**:R1-x-01、R1-x-08、R1-doc-01–07、R1-mongo-04(或加 CI)、R1-core-14/15/16、R1-server-05、11 的补行。

## 9. MINOR 分诊(第 1 轮,2026-10-29)

75 条 MINOR 逐条对照 main(`45f6a82d`,含 #1365–#1393)核实。状态的判断依据 D35:**会影响冻结面**(公开 API、配置键与语义、存储/线上格式)**或用户体验**(错误或误导的行为、报错、用户照做的文档)的,RC 前修;**纯内部清洁**(实现、性能微调、测试卫生),可以兼容地后改的,GA 后。"已修复"按 PR 核实,不只看 PR 描述里有没有提到。

### 9.1 汇总

| 状态 | 条数 |
|---|---|
| 已修复 | 32 |
| 本次修(RC 前) | 25 |
| GA 后 | 16 |
| 不修 | 2 |

### 9.2 逐条

| ID | 问题(一句话) | 状态 | 理由 / 修法 | 主题 |
|---|---|---|---|---|
| R1-x-02 | 是否提供官方静态入口 `CosIdHolder` | 不修(已定案) | #1371 定了方案 A(只做文档);from-4x 的"静态代码中取号"一节和 `CosIdRuntime` 类注释已经写明 holder 的做法(#1382)。官方 holder 是新增类,以后需要时可以在次版本加 | — |
| R1-x-05 | cosid80 的 codec 静默忽略 `friendly.pad-start`、`radix.width/pad-start` | 已修复 #1373、#1375 | `PartsCodecSpec.Friendly.padStart`;radix 的这两项写了就是配置错误 | — |
| R1-core-05 | `TimeSequencer` 每发一个号做一次 volatile 写 | 已修复 #1380 | `setRelease`;close/retire 仍用 `getAndSet` | — |
| R1-core-06 | `ObservedStores` 的 lambda;P5/P6 没有走生产路径 | 已修复 #1380 | 改成直接 try/catch;P5/P6 改为经由 `CosIdRuntime` | — |
| R1-core-07 | 匿名包装 store 以后不会转发 SPI 新增的 default 方法 | GA 后 | 内部包装;目前两个 default 方法都已覆盖。以后补一个反射单测 | — |
| R1-core-08 | 自定义 `Period` 拿不到 key 长度(`instanceof PatternPeriod`) | GA 后 | 给 `Period` 加 `default OptionalInt keyLength()` 是增量改动 | — |
| R1-core-09 | `GeneratorListener` javadoc 写"Never called on the hot path",与实际不符,也没有线程约定 | 本次修 | 这是实现方要遵守的 SPI 契约:在接口级写明各回调所在线程、不得阻塞 | core-javadoc |
| R1-core-10 | listener 防护有三套写法,`saturatedAdd` 有 3 份 | GA 后 | 纯内部清理 | — |
| R1-core-11 | `IdGenerators.uncertain` 抛 `IllegalStateException` | 已修复 #1373 | `IdOverflowException` | — |
| R1-core-12 | time 位溢出、epoch 在未来时的异常类型,且没有测试 | 已修复 #1373 | `TimeOverflowException`;`RuntimePlan` 拒绝未来的 epoch | — |
| R1-core-13 | `LeaseKeeper.INCARNATIONS` 是 JVM 级静态状态 | GA 后 | 只用于检测,不参与发号;以后在 01 §3 写明这一例外 | — |
| R1-core-14 | 01–05 设计文档与代码不符 | 已修复 #1383 | 已按代码同步 | — |
| R1-core-15 | 11 中测试链接的行号锚点漂移 | 已修复 #1383 | 已重新生成;CI 锚点校验 GA 后再加 | — |
| R1-core-16 | 导出包缺 javadoc 和 `package-info`;SPI 契约不清 | 本次修(只做契约部分) | 写明 `IdCodec.encode` 遇到非法 id、`StoreProvider.open` 能否返回 null、`IdFiller.fill(null)` 的契约;给 9 个导出包加 `package-info`,`internal` 写明"不是 API"。平凡访问器的 javadoc GA 后补 | core-javadoc |
| R1-core-17 | `describe()` 的 details key 是隐式冻结 | 已修复 #1373 | `GeneratorInfo` javadoc 列出 key;`DescribeKeysTest` 锁定 | — |
| R1-core-18 | `get(name, TimeBasedGenerator.class)` 拿到的对象没说不要 close | 本次修(只做 javadoc) | 写明"由运行时拥有,不要关闭";`supportsRanges/Slots` GA 后再加 | core-javadoc |
| R1-spi-03 | 各后端失败类型不统一(Mongo 泄漏驱动异常),也没有公共 `StoreException` | 本次修(只做契约) | 在 `RangeStore`/`SlotLeaseStore` javadoc 写明"任意 RuntimeException;超时用 `StoreTimeoutException.isTimeout` 判断",TCK 验证超时可识别。是否加 `StoreException` 基类:以后让各后端异常继承它是兼容的,**请总调度定** | core-javadoc |
| R1-spi-04 | `timeout = 0` 在各后端语义矛盾 | 已修复 #1372、#1378 | timeout 必须为正数 | — |
| R1-spi-05 | `StoreConfig` 是公开 record,冻结后加字段会破坏兼容 | 已修复 #1372 | 改为 final class + builder | — |
| R1-jdbc-03 | 成功路径上也立即拼接 operation 字符串 | 已修复 #1380 | 只在出错时拼接 | — |
| R1-jdbc-04 | MySQL upsert 绑 5 个参数,generated keys 可能有 2 行 | 已修复 #1392 | 已打开的 key 先走 2 个参数的 `update`;upsert 只在新建、已封存或已满时执行 | — |
| R1-jdbc-05 | acquire 读候选时按列名取值,`TreeMap` 装箱,再 shuffle | GA 后 | acquire 是低频路径,纯内部性能 | — |
| R1-jdbc-07 | 公开 SPI `JdbcDialect.firstRows(...)` 暴露了 store 拼 SQL 的方式 | 本次修(只做 javadoc) | 写明 `firstRows`/`createIndex`/`addColumn`/`selectNowMillis` 是 store 的 SQL 钩子:store 以后可以不再调用、也可以新增带默认实现的方法。如果要缩窄 SPI,需另做设计 | stores |
| R1-jdbc-08 | `JdbcStore.of` 与 provider 查找方言用的类加载器不同 | 本次修 | 同一个自定义方言,一条路径能找到、另一条找不到。`of` 也用 TCCL,TCCL 为空时回退到类加载器 | stores |
| R1-jdbc-09 | `DriverManagerDataSource.setLoginTimeout` 改的是 JVM 全局设置;`toString` 带出完整 url | 本次修 | url 可能含密码。`toString` 改用脱敏后的 url;login timeout 只记在本实例里 | stores |
| R1-jdbc-10 | 死锁立即重试,没有退避 | GA 后 | 内部重试策略,以后可兼容地加随机退避 | — |
| R1-jdbc-11 | 06 §2.1 落后于代码 | 已修复 #1383、#1392 | — | — |
| R1-jdbc-12 | `schema-mariadb.sql` 缺许可证头 | 本次修 | 零风险,用户会直接执行这份文件 | stores |
| R1-redis-01 | Redis 槽位行按位置解析,没有写明演进规则 | 本次修 | **存储格式**。06 已写"只读前 8 个字段"(#1383),但 encode 写回时只写 8 个字段:新旧版本混跑时,旧版本重写一行就会丢掉新字段。规则要写明"新字段只追加在末尾,并且丢失时必须安全(能从缺省值推导),否则需要版本门槛",再加一个解码时忽略多余字段的测试 | stores |
| R1-redis-02 | 06 §2.2 缺 `:meta`、`:held` 和转义 | 已修复 #1383 | — | — |
| R1-redis-03 | `wait=auto` 只在 open 时解析一次,不记日志 | 本次修 | 影响用户对持久性的判断。open 时以 INFO 记录解析出的副本数;README 写明副本增减后需要重启 | stores |
| R1-redis-04 | `Script.eval` 用 stream 转换回复,每次拼 key 和参数 | GA 后 | 纯内部热路径优化 | — |
| R1-mongo-01 | 索引 `ns_ownerKey` 多余(被 `lastOwnerKey` 蕴含) | 本次修 | 06 记录的索引集合属于库上格式,冻结前收窄。聚合只匹配 `lastOwnerKey`,store 与 `MongoTarget` 都不再建 `ns_ownerKey`,06 同步;已有的库在说明里提示可以手动删除 | stores |
| R1-mongo-02 | take/renew 的 `findOneAndUpdate` 没有 projection,pipeline 每次重建 | GA 后(renew 已修复 #1392) | renew 已加 projection,并改为直接构造 BSON 值;take/acquire 是低频路径,纯性能 | — |
| R1-mongo-03 | 06 §2.3 缺 `cosid_namespace`、`writtenAt`、索引,重试次数写错 | 已修复 #1383 | — | — |
| R1-zk-02 | 06 §2.4 没有 owner 索引、`.clock`、元数据格式和分批读取 | 已修复 #1383 | — | — |
| R1-remote-01 | 08 §2 协议表不全 | 本次修 | 以 `RemoteApi` 常量为准补一张完整的状态码/code 表(`503 busy`、`410 range_sealed`、`401/403/404/405`、`bad_request`、`unsupported`),主表 renew/release 行补上 `ownerKey` 和窗口字段 | docs |
| R1-remote-02 | remote provider 不能注入自定义 `HttpClient`(mTLS、代理) | GA 后 | 现在注入会直接报错,以后放开是兼容的增量 | — |
| R1-remote-03 | 在明文 `http` 上发 bearer token 没有提示 | 本次修 | 安全提示,成本极低:`http`、带 token、非回环地址三者都成立时,open 时打 WARN | stores |
| R1-tck-01 | TCK 公开包里带着 CosId 自己的夹具 `TestRuntimes` | 本次修 | 冻结后就成了公开 API。移到 `me.ahoo.cosid.store.tck.internal`,写明不承诺兼容 | stores |
| R1-tck-02 | 缺 provider/Store 级别的契约 | GA 后 | 新增契约类是增量 | — |
| R1-tck-03 | TCK 没有 README;文档说 `InMemoryStore` 是"memory provider",但并没有注册 | 本次修 | 中英文都改正 memory provider 的说法,补 `ContractsRan` 的 Gradle/Maven 用法 | docs |
| R1-mig-01 | 迁移的异常消息和 `StoreLocation.toString` 带出原始 url | 本次修 | url 可能含密码。`JdbcTarget`、`JdbcSource`、`StoreLocation.toString` 改用 `StoreLocation.redactUrl` | migration |
| R1-mig-02 | 迁移写 Redis 不做 WAIT,与运行时 `wait=auto` 不一致 | 本次修 | failover 时可能丢掉 raise,导致重号。封存和 raise 写入后,有副本就 `WAIT n timeout`,确认数不足就中止;至少要在文档里写明这个前提 | migration |
| R1-mig-03 | 09 没有同步 README 里的护栏 | 本次修 | `--allow-unmigrated`、`--machine-from/--machine-to`、`--allow-new`,以及 verify 报告新出现的 4.x key,并入 09 §2/§3 | docs |
| R1-mig-04 | 迁移工具自己重写了一遍各 store 的格式 | 不修 | `TargetAdminTest` 在四个后端上 prepare 之后都用真实 store 做 acquire/reserve;冻结后格式不会再变 | — |
| R1-mig-05 | 迁移用 `user`,运行时用 `username` | 已修复 #1382 | 两个都接受,值不同时报错 | — |
| R1-spring-02 | Spring 用户无法替换预取执行器或时钟 | GA 后 | 加 customizer 是增量;11 §4"注入的执行器"一行对 Spring 用户要加注(并入 docs) | — |
| R1-spring-03 | `cosid.*` 中写错的键被静默忽略 | 已修复 #1375 | 未知键会导致启动失败 | — |
| R1-spring-04 | cosid80 codec 选项被静默忽略 | 已修复 #1373、#1375 | 与 R1-x-05 同一处修复 | — |
| R1-spring-05 | 健康贡献者名的大小写 | 已修复 #1375 | 改为 `cosid` | — |
| R1-spring-06 | `cosid.segment.reserve` 带 `key` 标签,基数无界 | 已修复 #1375 | 已去掉 `key` 标签 | — |
| R1-spring-07 | `CosIdJacksonModule` 可能形成循环依赖 | GA 后 | 没有实际案例;改为 `ObjectProvider` 延迟解析不影响兼容 | — |
| R1-spring-08 | AOT 下生成器集合在构建期固定 | 已修复 #1382(文档) | 运行期比对名字集合的校验 GA 后再加 | — |
| R1-spring-09 | `CosIdProperties.Store` javadoc 漏了 `remote` | 本次修 | 用户在 IDE 提示里能看到;只补 javadoc,收拢属性读取 GA 后做 | spring-config |
| R1-spring-10 | `- friendly: {}` 的报错是"(0 set)",令人困惑 | 本次修 | 先写测试确认实际怎么绑定,再把 0 个的情况改成"至少写一个字段,例如 …" | spring-config |
| R1-server-02 | server 的 readiness 不检查存储 | 已修复 #1365 | — | — |
| R1-server-03 | 开 `/ids` 时运行时另开第二个 Store | 已修复 #1365 | — | — |
| R1-server-04 | `/ids` 装箱成 `List<Long>` | GA 后 | JSON 形状不变,纯性能 | — |
| R1-server-05 | 08 没有反映已合入的改动 | 已修复 #1383 | — | — |
| R1-int-01 | 4 个集成各复制一份 `TextIds` | 已修复 #1374 | `CosIdRuntime.text(name)` | — |
| R1-int-02 | `cosid.jdbc.enabled` 关闭时没有任何提示 | 本次修 | 引入依赖却什么也不做。classpath 上有 Spring Data JDBC 而开关未开时,打一条 INFO | spring-config |
| R1-int-03 | 迁移页说 cosid-jackson 是"same artifacts",没写只支持 Jackson 3,也没有属性对照 | 本次修 | jackson 页与 from-4x(中英文)加 4.x → 5.0 属性对照,写明不再支持 Jackson 2 | docs |
| R1-int-04 | MyBatis 只认全局 `listKey`,ParamMap 中的单个实体不会被填充 | 本次修(只做文档) | mybatis 页(中英文)写明限制和推荐写法;遍历 ParamMap GA 后做 | docs |
| R1-sharding-01 | `ExactCollection` 等缺 javadoc | GA 后 | 纯 javadoc | — |
| R1-sharding-02 | `CachedSharding` 返回可变集合 | 本次修(只做 javadoc) | 以后改成不可变视图会让修改返回值的调用方抛错,所以 RC 前先在 javadoc 写明"不可修改" | core-javadoc |
| R1-build-01 | getting-started 说三个示例都能 `bootRun` | 本次修 | 按 `examples/README.md` 改写(server 需要先 compose,`plain-stores` 是测试) | docs |
| R1-build-02 | `SpringStartBenchmark` javadoc 中有转义反引号 | GA 后 | 只是基准代码的注释 | — |
| R1-build-03 | 02 目录表的 examples 漏了 `plain-stores` | GA 后 | 内部设计文档;顺手并入 docs PR | — |
| R1-doc-02 | rc-review 的 C-N4/C-N5/E-N1 未合入 main | 已修复 #1344(f93dee38) | #1382 核实 | — |
| R1-doc-03 | "没有各特性的 enabled 开关"与集成开关矛盾 | 已修复 #1382、#1383 | — | — |
| R1-doc-04 | 迁移手册缺拆分存储等选项 | 已修复 #1382 | — | — |
| R1-doc-05 | 迁移工具的 `user` 与 `username` | 已修复 #1382 | — | — |
| R1-doc-06 | 11 §5–§7 锚点失效 | 已修复 #1383 | — | — |
| R1-doc-07 | AOT 下生成器集合在构建期固定 | 已修复 #1382 | — | — |

### 9.3 评审外的新发现

- **server readiness 贡献者名**:`cosIdServerStore` 是驼峰,用户会在 `management.endpoint.health.group.readiness.include` 中写到它,与 R1-spring-05 统一为 `cosid` 的做法不一致。建议 RC 前改为 `cosidServerStore`,并保留旧名作为别名(属于配置面)。**请总调度定**;定了就并入 spring-config PR。

### 9.4 "本次修"的分组(建议 3 个 PR,串行)

1. **stores**(9 条):R1-jdbc-07、R1-jdbc-08、R1-jdbc-09、R1-jdbc-12、R1-redis-01、R1-redis-03、R1-mongo-01、R1-remote-03、R1-tck-01。
2. **core-javadoc + spring-config**(8 条):R1-core-09、R1-core-16(契约 + package-info)、R1-core-18、R1-spi-03(契约)、R1-sharding-02、R1-spring-09、R1-spring-10、R1-int-02,以及 9.3 若定为要改。
3. **docs + migration**(8 条):R1-remote-01、R1-tck-03、R1-mig-03、R1-int-03、R1-int-04、R1-build-01(R1-build-03、11 §4 的执行器行顺手并入);R1-mig-01、R1-mig-02(代码)。

**需要总调度决定的**:R1-spi-03 是否加 `StoreException` 基类;R1-mongo-01 是否在 RC 前改(这一项严格说也可以后改,老库只是多一个用不到的索引);9.3 是否改名。

**决定(总调度,2026-10-29)**:
1. **R1-spi-03**:RC 前只写异常契约,不加 `StoreException` 基类;以后在现有异常层次中插入基类是兼容的。
2. **R1-mongo-01**:RC 前修,因为它属于存储格式,冻结前清掉。`initSchema` 不再创建 `ns_ownerKey`;已有库保留这个无用索引不影响正确性,Mongo 后端文档给出手动删除的命令 `db.cosid_slot.dropIndex("ns_ownerKey")`。
3. **9.3 server readiness 贡献者**:`cosIdServerStore` 改为 `cosidServerStore`,与 `cosid` 一致,**不保留别名**(alpha.1 不承诺兼容,D33);同步 K8s 指南(中英文)、server 文档、rc.1 release notes 的升级说明以及引用它的测试。放在 core-javadoc + spring-config 的 PR 中。

## 10. 第 2 轮(RC 观察期,D35)

> 状态:**第 2 轮完成,待总调度逐条核实**(2026-10-29,基于 `main` @ `8ebde66d`,即 `v5.0.0-rc.1` 之后回到 `5.0.0-SNAPSHOT`)。
> 范围:整个 `main`,重点是第 1 轮之后合入的 #1365–#1412。方法与严重度同 §0;"冻结面"一列按 RC1 之后的口径(api-freeze.md §0):
> **兼容**(可在 5.0.x/5.x 内兼容修复)/ **RC2-BREAKING**(必须破坏冻结面,交用户决定)/ **无**(不触及冻结面)。
> 分 4 路只读审查(core;store 与 migration;spring、server、integrations、build、benchmark;文档与特性对照),各路笔记在会话笔记目录的 `ga-r2-*.md`;本节每条都由总调度会话对照代码复核过位置与结论,重复项已合并,定级有改动的在条目里写明。

### 10.0 进度

| 部分 | 状态 |
|---|---|
| #1365–#1412 逐 PR 审查 | ✅ |
| 各模块六个维度 | ✅(§10.5–§10.8) |
| D35 结论 1:性能 | ✅(§10.2) |
| D35 结论 2:特性 | ✅(§10.3) |
| D35 结论 3:文档 | ✅(§10.4) |
| 第 1 轮"已修复"抽查 | ✅(§10.9) |

### 10.1 摘要

共 **41** 条(合并后计;另有 4 条与他路重复,已并入:R2-parity-02 → R2-mig-04,R2-parity-04 → R2-uuid-01,R2-server-03 → R2-remote-01,R2-core-04 中 README 一项 → R2-arch-01)。

| 严重度 | RC2-BREAKING | 兼容 | 无 | 合计 |
|---|---|---|---|---|
| BLOCKER | 0 | 0 | 2 | 2 |
| MAJOR | 0 | 1 | 3 | 4 |
| MINOR | 1(可选做法) | 9 | 25 | 35 |
| 合计 | 1 | 10 | 30 | 41 |

**BLOCKER(都在迁移工具的 `config`,不触及冻结面,可以在 5.0.x 内修)**:
- **R2-mig-01**:`config` 不读 `apply-result.json` 的 `target`,只按 4.x 的后端类型写 `cosid.stores`。跨类型迁移、或目标用了自定义 Mongo `database` / JDBC 表名时,5.0 会在没有迁移数据的库或表上从 `initialOffset` 发号,与 4.x 重号,全程没有提示。
- **R2-parity-01**:`config` 静默丢弃 4.x snowflake 定义自己的 `namespace`(4.x 按它分配机器号)。多个服务共用这个命名空间时,迁移后跨服务重号。与 R1-x-07 是同一机制。

**MAJOR**:R2-mig-02(迁移工具的 store 选项不校验、不归一化,`range-table` 与运行时读到的表不同)、R2-mig-04(`config` 把 `wait-replicas: N` 降成 `WAIT 1`,丢掉 `fetch-plan`)、R2-doc-03(迁移指南没写 4.x `generateAsString()` 默认 radix62、5.0 默认十进制)、R2-dep-01(main 上唯一未关闭的 Dependabot 告警:Activiti 7.0.0.SR1 带入 MyBatis 3.4.2,high)。

**需要用户决定的(冻结面)**:
- **R2-spring-02**(唯一的 RC2-BREAKING 候选):写在不适用策略上的键(如 `segment` 加 `period`)被静默忽略。改为启动失败就是收紧 `cosid.*` 的校验,属于 RC2-BREAKING;只打 WARN 则兼容。**推荐:WARN,不为它出 RC2。**
- **R2-core-03**:导出包中具体生成器与租约类的公开构造器已积累 5 对位置参数重载。**推荐(兼容)**:保留它们,规定新参数只走 builder 或静态工厂;如果要把这些构造器移出兼容承诺,严格说是收窄 RC1 的冻结面,须用户拍板。

**结论:按本轮发现,不需要 RC2。** 2 条 BLOCKER 与 4 条 MAJOR 都不需要破坏冻结面(R2-dep-01 只改发布的依赖元数据,其余不触及冻结面);GA 前修掉它们,并在 GA 提交上重跑门槛即可。

**D35 三项结论**:
1. **性能无明显回退:PASS**(§10.2)。第七次门槛 8/8 PASS(4.1.1 基线,D34 + D44);门槛之后只有 #1405、#1406 改了发布的代码,都没有碰已测的发号路径。另有 2 条 MINOR:证据留档(R2-perf-01)、覆盖面(R2-perf-02、R2-bench-01/02)。
2. **无特性缺失:PASS(运行时),迁移转换有缺口**(§10.3)。5.0 代码覆盖了 4.x 的全部能力,包括命名 machine、slotOffset、cosid80 布局和自定义生成器;缺口在 `cosid-migration config` 的 4.x → 5.0 转换上(2 条 BLOCKER、2 条 MAJOR、R2-parity-03),以及 11 的几行(R2-parity-05/06/07)。
3. **文档齐备:PASS,有缺口**(§10.4)。中英文 36 页结构一致,94 个 `cosid.*` 键与 27 个存储选项都有文档,默认值也都对;主要缺口是 R2-doc-03(MAJOR),其余是 MINOR(uuidv7 没进各处的策略清单、README 仍是 alpha 警告、Boot 最低版本、skills 过时、SPI 方法级 javadoc)。

**建议的修复分组**(串行,一次一个 PR):
1. **WP-migration-r2**(先做,含全部 BLOCKER):R2-mig-01、R2-parity-01、R2-mig-02、R2-mig-04、R2-mig-03、R2-mig-05、R2-mig-06、R2-parity-03,以及 from-4x 中与之对应的说明。每条都补"经 `config` 输出启动 5.0"的测试(R2-mig-01 / R2-parity-01 现在没有这种测试,所以测试没能发现)。
2. **WP-core-spring-r2**:R2-uuid-01/02/03、R2-core-01/02、R2-core-03(按用户决定只改 javadoc 与约定)、R2-spring-01、R2-spring-02(按用户决定)、R2-spring-04、R2-server-01、R2-dep-01、R2-build-01/02(CI 加"发布 jar + Boot 4.0.9"冒烟)、R2-bench-01(benchmark 改动先推 `release-gate-check/**` 分支 dry run)。
3. **WP-docs-r2**:R2-doc-01–07、R2-parity-05/06/07、R2-spring-03、R2-server-02、R2-remote-01、R2-core-04、R2-arch-01/02(决策表合入 main,协调会话负责)、R2-perf-01(在 GA 提交的门槛跑完后留档)、R2-perf-02 / R2-bench-02(`scenarios.md` 列出不在门槛内的路径;remote 趋势行可选)。

### 10.2 D35 结论 1:性能无明显回退

**结论:通过(PASS),附 2 条文档/留档发现(R2-perf-01、R2-perf-02)。** 证据:

1. **第七次正式门槛**([run 40832851823](https://github.com/Ahoo-Wang/CosId/actions/runs/40832851823),`457c7266`,release 设置 3 fork × 5 × 2 s,D34 容差 + D44 端到端/组件口径,4.x 基线 4.1.1,存储组 ABBA):8 组 0 组失败;112 个基准行(每行按吞吐或延迟、分配分别判定)中 1 行首轮失败、重跑通过,3 个组件行 WARN。逐行读了 `release-gate-summary` 的 `report.md`,5.x 比 4.x 差 5% 以上的行只有下面这些,均已有解释:

| 行 | 4.x | 5.x | 判定 | 解释 |
|---|---|---|---|---|
| `SnowflakeMillisBenchmark.generate_t16` 吞吐 | 4,097 | 3,877(−5.4%)/ 重跑 3,956(−3.4%) | 首轮 FAIL,重跑 PASS | 两边都顶在 12 位序列的 4,096/ms 上限;第六次门槛同一行 4,090 vs 4,098。这台 runner 报 `turbo ON`;判为噪声,不是回退 |
| `Mongo/ZookeeperChainIdBenchmark.generate_t16` step=10 吞吐 | 58.96 ± 22.1 / 58.91 ± 26.9 | 45.16 / 42.97(−23% / −27%) | PASS(在 4.x 误差内) | 4.x 受存储约束且呈双峰(逐迭代 0.5–125),只在预取恰好领先时冲高;5.x 平稳在单计数器的争用上限(同 runner 内存 P5 t16 为 44.8,AtomicLong 参照 69.4)。见 P5 调查(会话笔记 `p5-t16-and-atomiclong.md`)。不是回退 |
| `MongoStoreBenchmark.reserve_t1` avgt / p99 | 867 / 1,451 µs | 918 / 1,757 µs(+5.9% / +21.1%) | WARN(D44 组件行) | 组件行在 D44 软上限内;端到端 `MongoChainIdBenchmark` 全部 PASS |
| `MySqlStoreBenchmark.acquire_t1` avgt | 960 µs | 1,013 µs(+5.5%) | WARN(D44 组件行) | 同上;acquire 是启动期低频路径 |

2. **门槛之后合入的改动**(`457c7266..8ebde66d`):只有两个 PR 改了发布的代码。
   - **#1405**(`40c5992f`):`CosIdSpecs`(Spring 绑定,启动期一次)与 TCK 的 `RangeStoreContract`(测试),都不在发号路径上。
   - **#1406**(`ac3de73f`,UUIDv7):`GeneratorEntry` 只新增 `uuidV7(...)` 工厂和常量,`ids()` 的异常文案变了(只在抛错时拼接);`RuntimeParts`/`RuntimePlan` 只在启动期的 `switch` 里加了分支;`CosIdRuntime` 只改 javadoc;`IdController` 的 `/strings` 改走 `runtime.text(name)`(server 不在门槛内,见 R2-perf-02)。snowflake、segment、periodic、cosid80 的发号路径与 codec 一行未动,门槛测过的路径因此不受影响。
   - 其余是发布 chore、pnpm、`benchmark/` 本身(#1404 的参照行,#1406 的 P13 与 `compare` 的 TREND 类型)。
3. **门槛没有测到的新热路径**:`UuidV7Generator`(P13)按设计只报告不判定(4.x 没有对应实现,10 §1.2)。它还没进过正式门槛,只在 smoke 设置的 dry run([40841106228](https://github.com/Ahoo-Wang/CosId/actions/runs/40841106228),`release-gate-check/uuidv7`)里跑过:t1 28.1、t4 13.0、t16 13.5 ops/µs,`text_t1` 20.6 ops/µs。代码:每个 id 一次 `AtomicLong` CAS,加一次(跨毫秒时两次)`ThreadLocalRandom`,`generateUuid` 每次分配一个 `UUID`,`generate` 再加一个 `String`(`UuidV7Generator.java:86-93,113-133`)。4.x 的 `UuidGenerator`(v4)对应 5.0 的 `IdGenerators.uuid()`,两边都是 `UUID.randomUUID().toString()`(`IdGenerators.java:89-91`),没有差异。
4. **rc.1 发布提交 `c154e69d` 本身没有跑过正式门槛**,两者之间只有上面第 2 条的改动。GA 准入清单本来就要求"门槛在 GA 提交上按 D34 PASS";那一次运行也会产出 P13 的第一组正式趋势数据。

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 冻结面 |
|---|---|---|---|---|---|---|
| R2-perf-01 | MINOR | 文档(性能证据) | `documentation/docs/{en,zh}/reference/performance/benchmark.md:41,54-61` / `:36,48-55`;`docs/jmh/`(无 `5.0*`、无 `4.1.1/`) | 文档站的"5.0 results"一节仍是 R5-A 的占位("Pending",结果"由专用机器产出"),并写"发布数据只来自一台固定、安静的物理机",与 D31/D34(CI 共享 runner 上 ABBA 交替、按容差判定)矛盾。RC1 的性能依据(第七次门槛)只在 Actions artifact 里,`release-gate-summary` 将于 **2027-01-27** 过期;10 §1.3 与 `scenarios.md:110-112` 要求的留档(`report.md` → `docs/jmh/5.0/`,`baseline-4x.md` → `docs/jmh/4.1.1/release-ci.md`)没有做 | GA 前把 GA 提交的门槛摘要提交到 `docs/jmh/5.0.0/`(和 4.1.1 基线),中英文"5.0 results"一节改为摘要表加链接,删掉"专用物理机"的说法,改写成 D31/D34/D44 的口径;rc.1 的第七次门槛摘要也可以先留档 | 无 |
| R2-perf-02 | MINOR | 高性能(门槛覆盖) | `benchmark/scenarios.md`(P1–P13);`server/.../IdController.java` | 下面这些发布的热路径不在任何门槛场景里:cosid-server 的 `/ids/**`(4.x proxy 也没有基准,无法对照);Jackson `@AsString` 序列化、MyBatis/Spring Data JDBC 的填充(P12 只测 `IdFiller.fill` 本身);`cosid.metrics.generated=true` 时每个 id 的 `LongAdder` 计数(`GeneratorEntry.java:109-140`)。P1–P4 直接构造 `TimeBasedGenerator`(`benchmark/v5/.../V5.java:62-64`),不经过 `CosIdRuntime`;不计数时运行时交出的就是同一个对象(`GeneratorEntry.java:109-112,156`),`text(name)` 也只是 `codec.encode(...)` 的一层 lambda,所以测到的仍是生产路径。以上都不是回退证据,只是 D21 的覆盖面 | 不阻塞 GA。在 `scenarios.md` 列出"不在门槛内的路径"及理由;若要补,优先 server `/ids`(5.x 趋势行,与 P13 同样只报告) | 无 |

### 10.3 D35 结论 2:无特性缺失

**结论:运行时 PASS;迁移转换有缺口(R2-mig-01、R2-parity-01 为 BLOCKER,R2-mig-04 为 MAJOR,R2-parity-03 为 MINOR),11 需补 3 行。** 证据:

- **4.x 配置面逐类核对**:`origin/4.x` 的 14 个非测试 `*Properties` 类逐字段对照 5.0 与 `cosid-migration config`(未识别的 `cosid.*` 键统一报 "no 5.0 counterpart, dropped",`ConfigConverter.java:202-205`):
  - `cosid.enabled`、`namespace`(D38)、`proxy.*` → `remote` 存储;`cosid.generator.*` → cosid80 + `cosid.machines.generator`(D36/D37,from-4x 有逐键表);`cosid.machine.*` → `leased`/`static`;`Customize*Properties` → `RuntimeSpecCustomizer`:一致。
  - `cosid.snowflake.provider.<x>.namespace`:静默丢弃(**R2-parity-01**);friendly 默认补零与 4.x 相反(R2-parity-03)。
  - `cosid.segment.distributor.*` 的存储选项:弱化或丢失(**R2-mig-04**);迁移目标的选项与类型不进 `config` 的输出(**R2-mig-01**)。
  - `cosid.metrics.enabled`、`cosid.proxy.server.namespaces`:无对应且没有文档(R2-parity-06、R2-parity-05)。
- **11 中缺行或只部分覆盖**:snowflake 自有 `namespace`/`machine-bit`;proxy server 的全局 `namespaces`;`cosid.metrics.enabled`;编程式 `generateAsString()` 的默认文本(属迁移文档,R2-doc-03)。运行期增删生成器是 D17 的既定取舍,不计为缺口。
- **"等价/更强"不成立或需补注**:11 第 78–80 行(fetch-plan、wait-replicas、write-concern)在运行时成立,经 `config` 不成立;第 17 行(friendly 补零)的默认值与 4.x 相反;第 96/130 行(`disabled`)对 uuidv7 的 Spring Bean 不成立(R2-uuid-01);第 83 行(注入执行器)§9.2 要求的 Spring 补注未加(R2-parity-07)。
- **抽查 16 行并读了测试方法体**,都证明了所称的能力:L10、L13、L20、L21(`NamedMachinesTest.theSlotOffsetMovesTheNodes`、`SlotLeaseStoreContract.rejectsMismatchedSlotOffset`)、L29、L33(uuidv7:`UuidV7GeneratorTest` 8 个方法、`UuidV7RuntimeTest` 4 个)、L35、L69、L78、L79、L82、L95、L96/L130、L107、L117、L119。
- **11 的测试链接**:292 个链接的文件与方法全部存在;259 个 `#L` 锚点正确,33 个在 #1383 之后再次漂移(R2-parity-07)。

### 10.4 D35 结论 3:文档齐备

**结论:PASS,有缺口(R2-doc-03 为 MAJOR,其余为 MINOR)。** 证据:

1. **中英文对称**:`documentation/docs/{en,zh}` 各 36 页,页面集合相同;每页的各级标题数、代码块数、表格行数都相同,代码块去掉注释后逐字相同(只有 `operations/kubernetes.md` 的示意图是翻译);两个侧边栏各 36 个链接,顺序相同、目标都存在;`redirects.ts` 53 条,目标都存在,没有链式重定向;站内链接与锚点 0 处失效。
2. **`cosid.*` 键**:从 `CosIdProperties`、`ServerProperties`、`additional-spring-configuration-metadata.json` 与集成开关枚举出 **94** 个键,`reference/config/{basic,generators,server}.md` 一个不缺,默认值全部一致(含 `cosid.machines.*`、`slot-offset`、`acquire-timeout`、cosid80 位宽键、集成开关)。唯一缺口:`strategy` 的取值列表漏了 `uuidv7`(R2-doc-01)。
3. **策略**:snowflake、segment、periodic-segment、cosid80、custom/Instance、disabled 在参考页与概念页(中英文)都有;uuidv7 在参考页有专节,但各处策略清单、runtime 页的规格表、introduction、starter 的 Bean 表、annotation 页和 README 都没有它(R2-doc-01)。
4. **存储选项**:5 个 provider 在 `options()` 中声明的 **27** 个选项(jdbc 9、redis 4、mongo 3、zookeeper 7、remote 4)都出现在 `guide/backends/*.md`(中英文)与各 store README 中,默认值与代码一致。
5. **迁移指南**:`from-4x`(中英文)与 `migration/README.md` 覆盖了 `cosid.generator.*` 的逐键对照、静态代码取号(R1-x-01)、跨服务唯一(R1-x-08)、拆分存储的 CLI 选项(R1-doc-04)、安全回滚(R1-doc-01)。缺口:`generateAsString()`/`idConverter()` 与默认文本形式的变化,以及 `@CosId` 的包、`CosIdEventListener`、`Stat`(R2-doc-03);proxy server 键的对照(R2-parity-05);`cosid.metrics.enabled` 的替代写法(R2-parity-06)。`config` 自身的转换问题见 §10.3。
6. **javadoc**:core 导出的 9 个包都有 `package-info.java`(`internal` 写明"Not API");导出包 533 个公开声明中 88 个没有 javadoc(第 1 轮约 160),多是异常构造器、访问器、常量(§9.2 定为 GA 后);SPI 契约 `StoreProvider`、`RangeStore`、`SlotLeaseStore`、`IdCodec`、`JdbcDialect` 都已写明,用户实现的 `GeneratorListener` 各回调、`PartsCodec`/`GroupedCodec` 与 `IdGenerator.generate` 缺方法级契约(R2-doc-06)。
7. **发布说明与 README**:`docs/release/5.0.0-rc.1.md` 与代码一致(依赖版本、Boot BOM 4.1.2 / 最低 4.0.9、`StoreKeys` 限制、升级 DDL、`cosidServerStore`、策略清单),只有 SQL Server 的 DDL 写法不对(R2-doc-07)。README 仍是 alpha 警告(R2-doc-02),策略表没有 uuidv7(R2-doc-01),Boot 写的是"4.0+"(R2-doc-04)。另:随仓库发布的 skills 还是 alpha 时的内容(R2-doc-05);文档站的 5.0 性能结果一节仍是占位(R2-perf-01)。

### 10.5 core

范围:`core/src/main/java`,重点是 #1372、#1373、#1374、#1380、#1391、#1397、#1406。总调度(本会话)逐条核实了下表各条的代码位置:R2-uuid-01(`CosIdGeneratorRegistrar.java:143` 的 Bean 类型是 `UuidV7Generator`,`:186-194` 的 `disabled()` 对它返回 `GroupedIdGenerator` lambda,`type.cast` 必然失败)、R2-uuid-02(`GeneratorInfo.java:25-37` 无 uuidv7 行)、R2-uuid-03(`GeneratorEntry.close()` 只关 `AutoCloseable`)、R2-core-01(`SegmentGenerator.java:470-472` 丢弃 `advanceLocked` 的返回值,`:477-479` 让 `currentSize = 1` 而 `current` 仍为 `empty`)、R2-core-02(三个 `toString` 原样输出 `options`;CosId 自己不打印这些对象,泄漏只发生在应用或框架打印它们时)、R2-core-03(5 对公开构造器重载)、R2-core-04。

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 冻结面 |
|---|---|---|---|---|---|---|
| R2-uuid-01 | MINOR | 易用性 / 一致性 | `spring/autoconfigure/.../CosIdGeneratorRegistrar.java:143,152-155,178-194`;`core/.../internal/GeneratorEntry.java:249-257`;`spring/.../CosIdMeterListener.java:152-153`;`docs/architecture/07-runtime-and-spring.md:77` | uuidv7 唯一的带类型句柄是具体的 final 类 `UuidV7Generator`,Spring 也用它作 Bean 类型(`:143`)。这带来两个问题。① **禁用时启动失败**:`getObject()` 捕获 `GeneratorDisabledException` 后调用 `disabled()`,这个方法只认识 `IdGenerator`、`PartsIdGenerator`,其余一律返回 `GroupedIdGenerator` 的 lambda(`:193`),`type.cast` 到 `UuidV7Generator` 时抛 `ClassCastException`。结果是 `cosid.generators.x.disabled=true` 加上注入 `UuidV7Generator`,上下文起不来,违反 D24 的"Bean 保留,调用时抛"。`aDisabledGeneratorKeepsItsBeanAndThrowsOnUse`(`CosIdAutoConfigurationTest.java:250-273`)没有覆盖 uuidv7。② **计数对 Bean 不生效**:`GeneratorEntry.uuidV7` 只给 `text` 视图挂了计数,`get(name, UuidV7Generator.class)` 拿到的是未计数的原对象(`UuidV7RuntimeTest` 注释也承认这一点)。所以 Spring 用户开了 `cosid.metrics.generated.enabled` 后,`cosid.generator.generated` 一直是 0,和 registrar 的 javadoc(`:152-154`"counts the ids")以及 07 §1.0 说的"按接口注册、可代理"都不符 | core 让运行时持有的那**一个** `UuidV7Generator` 自己计数:internal 构造参数传 `LongAdder`,或者把 `TimeSource` 包一层,每次取时间时计数。不要另造一个实例,否则 bean 和 `text()` 之间失去单调性。禁用时的 Bean 可以是 `new UuidV7Generator(name, () -> { throw new GeneratorDisabledException(name); })`。`disabled()` 改成按 `type` 穷举、未知类型直接报错。补 uuidv7 的禁用与计数测试;07 §1.0 写明 uuidv7 的 Bean 是具体类 | 兼容 |
| R2-uuid-02 | MINOR | 文档 / 冻结面(actuator 输出) | `core/.../observe/GeneratorInfo.java:25-37,58,75`;`internal/GeneratorEntry.java:256`;`core/src/test/.../runtime/DescribeKeysTest.java:52-62` | R1-core-17 的键表承诺"列出的键不改名、不删除",但没有列 uuidv7 的 `version`、`text`,`DescribeKeysTest` 也没有锁定它们,等于 RC1 冻结了一个 actuator 形状却没写进承诺。`builder(name, strategy)` 和 `strategy()` 的 javadoc 仍然只列 4 个策略,`custom`(#1373)和 `uuidv7`(#1406)都漏了 | 键表加一行 `uuidv7`:`version text`。两处 javadoc 列出全部 6 个策略。`DescribeKeysTest.spec()` 加一个 uuidv7 生成器 | 兼容 |
| R2-uuid-03 | MINOR | 一致性 / 易用性 | `core/.../time/UuidV7Generator.java:40`;`internal/GeneratorEntry.java:388-397`;`runtime/CosIdRuntime.java:236-238` | 其他策略在 `runtime.close()` 之后都进入终止状态,再调用会抛 `IllegalStateException`。`UuidV7Generator` 没有实现 `AutoCloseable`,`GeneratorEntry.close()` 直接跳过它,所以关闭后 `text(name)`、已经注入的 Bean 照样发号。`CosIdRuntime.close()` 的 javadoc 写的是"generators (terminal state…)",对 uuidv7 不成立。对唯一性没有影响 | GA 前二选一:在 `GeneratorSpec.UuidV7` 和 `CosIdRuntime.close()` 的 javadoc 写明"uuidv7 没有生命周期,关闭后仍可用";或者加一个 closed 标志,与其他策略一致。GA 后再改关闭后的行为,就是行为变更 | 兼容 |
| R2-core-01 | MINOR | 可观测(#1380 引入) | `core/.../segment/SegmentGenerator.java:470-472`、`:473-481`、`:313-315` | `complete()` 在 `current == empty` 时调用 `advanceLocked(current, now)`,但丢掉了它的返回值(变化前的批大小)。#1380 之前这样没问题:此时 `currentSize` 总是 0,不会采样。#1380 之后,调用方自己拿走首值、区间只有一个值时,会设 `currentSize = 1`,而 `current` 仍是 `empty`(`:477-479`)。之后预取补充回来,`complete()` 走到 `advanceLocked` 时会 `sizer.record`,批大小变了,`onBatchResized` 却不会触发,`cosid.segment.batch.resizes` 就少计一次。只在 step=1 且开了 lookahead 时出现,`batch()` 和 `describe()` 的值仍然正确 | 和 `slowPath`(`:247-249`)一样:保存返回值,解锁后再 `fireResized`。补一个 step 1 加 lookahead 的单测 | 无 |
| R2-core-02 | MINOR | 安全 / 易用性 | `core/.../store/StoreConfig.java:118`、`runtime/StoreSpec.java:88`、`runtime/RuntimeSpec.java:130-132`;键见 `store/jdbc/.../JdbcStoreProvider.java:51`(`password`、`url`)、`store/remote/.../RemoteStoreProvider.java:52`(`token`) | 冻结面上的值类型 `toString` 把 `options` 原样打出来,jdbc 的 `password`、带用户信息的 `url`、remote 的 bearer `token` 都会以明文出现。用户在日志里打印 spec 或 config(调试、`RuntimeSpecCustomizer`)就会泄漏。项目对同一类问题已经修过:R1-jdbc-09 让 `DriverManagerDataSource.toString` 脱敏(`DriverManagerDataSource.java:95-96`),但这三个核心类型没有跟进 | `toString` 保持 record 风格,只对值做脱敏:键的规范形(`StoreOptions.canonical`)含 `password`、`pwd`、`token`、`secret`、`credential` 的,打成 `***`;URL 形式的值去掉 user-info 和 `password=` 参数,可以复用 jdbc 的 `redacted`,收进 core 的 internal 工具。补一个测试 | 兼容 |
| R2-core-03 | MINOR | 高扩展性 / 冻结面 | `time/TimeBasedGenerator.java:46,63`、`segment/SegmentGenerator.java:126,144`、`segment/PeriodicSegmentGenerator.java:85,107`、`lease/LeaseKeeper.java:99,121`、`lease/StaticSlot.java:82`、`time/UuidV7Generator.java:67`;`store/StoreOptions.java:73`;同类 `lease/SlotLeaseStore.java:46` | 导出包里有些公开成员,主代码里只有 `internal.RuntimeParts` / `RuntimePlan` 和基准在用,却按 api-freeze §0 一并冻结了,§4 也没有对它们做结论。① 具体生成器和租约类用 7–13 个位置参数的公开构造器,已经出现 D39 否决过的"重载随版本累积":`nodeOffset`、`storeName`、`slotOffset` 各多出一个重载,共 5 对,GA 后每加一个参数就再多一个。② `StoreOptions.resolve(store, type, known, options, List<String> problems)` 是 `RuntimePlan` 的内部辅助方法:传入一个可变的问题列表,消息格式直接写在里面,却成了公开 API。③ SPI `acquire` 已有 7 个位置参数,下一个元数据字段只能再加 default 重载 | GA 前定口径:要么在 api-freeze §0 和类 javadoc 写明"这些类的构造器是装配入口,由 `CosIdRuntime` 使用,不属于兼容承诺",并把 ②移进 internal(这只能在 GA 前做);要么保留它们,规定以后新参数只走 builder 或静态工厂,不再加位置重载。前一种做法严格说要改 RC1 的冻结面,但仓库外不太可能有调用方 | 兼容(收窄承诺只能在 GA 前) |
| R2-core-04 | MINOR | 文档 / 代码清洁 | `docs/architecture/02-modules.md:69`;`07-runtime-and-spring.md:29-39,77`;`03-core-model.md:153-163`;`core/.../time/package-info.java:13-17`;`accessor/IdFiller.java:18`;`runtime/CosIdRuntime.java:139-141`;`docs/architecture/README.md`(决策表止于 D38) | #1373、#1374、#1406 之后有这些地方没跟上:02 §4 的 `time` 行没有 `UuidV7Generator`;07 §1 的 `GeneratorSpec` 清单没有 `UuidV7`,§1.0 的"Bean 按接口注册"对 uuidv7 不成立;03 §6 的 D39 分类既没有 `StringIdGenerator`(公开 record)也没有 `UuidV7Generator`;`time` 包的 package-info 只提到 `TimeBasedGenerator`;`IdFiller` 写 String 字段"through the generator's codec",uuidv7 没有 codec;`CosIdRuntime.generated()` 列出的计数视图漏了 `text(name)`(它确实计数,`GeneratorEntry.java:158,178,195`)。README 决策表只到 D38:api-freeze §11 写着"供协调会话转录进 README"的 D39–D43 没有转录,D44–D46 也不在表里。D46 的内容只出现在 `docs/release/5.0.0-rc.1.md:35,94` 和 PR 描述里 | 按代码补齐以上各处;D39–D46 转录进 README;03 §6 把 `StringIdGenerator` 归入"封闭的值,保留 record" | 无 |

R2-uuid-01 也由 docs+parity 审查独立发现(其笔记中的 R2-parity-04,不另编号)。它是确定的缺陷(禁用的 uuidv7 一旦被注入就让上下文启动失败),虽然只是 MINOR,建议 GA 前修。R2-core-04 中"README 决策表止于 D38"与 R2-arch-01 同源,修复时合并。

**确认通过(core)**:
- **UUIDv7 正确性**(RFC 9562 §5.7、§6.2 方法 1):位布局(48 位毫秒、version 7、16 位计数器 = `rand_a` 12 位 + `rand_b` 高 4 位、variant `10`、58 位随机,`UuidV7Generator.java:53-54,91-92`);单个 `AtomicLong` 状态 `millis << 16 | counter` 的 CAS 保证实例内严格递增,无符号比较(`:124`);新毫秒随机起点 `< 2^15`(`:125`),同毫秒或回拨时 `previous + 1`,计数器满则进位到毫秒(`:128`);48 位与进位两种溢出都抛 `TimeOverflowException`(`:118-120,129-131`)。`ThreadLocalRandom` 无竞争,javadoc 写明 id 可预测、不得作令牌。存疑备注(不算发现):进位后的毫秒内计数器从 0 起,文档的碰撞估计只适用于不进位的毫秒,进位时仍有 58 位随机,可忽略。
- **#1406 没有改动既有策略的共享路径**(见 §10.2)。
- **D39 值类型**:api-freeze §4.2 列为 final class 的类型都没有公开构造器,都有 `equals`/`hashCode`/`toString`,`build()` 做字段校验;导出包里剩下的公开 record 正好是 §4.2 列为封闭的那些(另加 `StringIdGenerator`,见 R2-core-04)。**D40**:5 个 sealed 规格接口的 javadoc 都要求 `switch` 保留 `default`(`GeneratorSpec.java:31` 等),`AcquireResult` 声明封闭。
- **命名 machine 与 slotOffset**:保留名与名字语法、引用不存在的 machine、`slotOffset + 2^bits ≤ 2^nodeBits`(long 运算,逐生成器)、命名空间两两不同、`stateFile` 不重复、`slotOffset ≥ 0`、`acquireTimeout ≥ 0`(`RuntimePlan.java:259-325,396-401`);默认 machine 先启动、命名的按名序串行,失败逆序 release,每个 machine 只取自己生成器的 `max(lastIssued)`(`RuntimeParts.java:126-128,322-346`);节点号 `slot + nodeOffset` 写进 id,换绑时再查越界(`TimeSequencer.java:178,209,306-309`)。
- **#1380 / #1391 号段快路径**:调用方预留时直接返回 `range.start()`,只有 `[start+1, end)` 非空才分配 cursor(`SegmentGenerator.java:415,460-469`);与 `highWater` 重叠、已关闭、补充失败时都不返回首值,不会重号;#1391 之后只有 `current.remaining + readyValues == 0` 时调用方才自己预留(`:226`),首值不会排到就绪区间前面。热路径不变(`:184-191`)。
- **分层**:导出包的公开签名里没有 internal 类型;internal 的 package-info 写明"Not API"。#1397 的 SPI 契约 javadoc(`GeneratorListener` 线程约定、`IdCodec.encode`、`StoreProvider.open` 不返回 null、`IdFiller.fill(null)`、`RangeStore`/`SlotLeaseStore` 的异常契约)均已就位,`SafeListener` 覆盖全部 14 个回调。

**性能备注(core,存疑,不算发现)**:`UuidV7Generator` 用 CAS 重试循环,而 `TimeSequencer` 的 javadoc 记录过 16 线程下短临界区优于 CAS(`TimeSequencer.java:45-46`,D25)。P13 dry run 的 t4/t16(13.0 ± 9.0、13.5 ± 8.8 ops/µs)误差很大,等 GA 门槛的正式趋势数据再决定要不要改成短锁。

### 10.6 store/* 与 migration

范围:`store/{jdbc,redis,mongo,zookeeper,remote,tck}`、`migration/` 及其 README,重点是 #1369、#1370、#1376、#1378、#1379、#1380、#1392、#1393、#1396、#1399、#1405。总调度复核:R2-mig-01(`ConfigConverter.java:155,226-318` 只看 4.x 的 `distributor.type`,全文不读 `result.target()`;`MongoTarget.java:56` 用目标的 `database` 选项,`JdbcTarget.java:66` 用目标的表名选项;普通号段的 `initial-offset` 只来自 4.x 的 `offset`,`:500-505`)、R2-parity-01(`:477,483-487` 的 `ownNamespace` 只记 handled)、R2-mig-02(`StoreLocation.java:88-90` 按字面取选项,运行时经 `StoreOptions.canonical` 归一化)、R2-mig-03(`RedisConnection.java:66-73`:写入走 `connection.sync()`,WAIT 走 `getConnection(nodeId)`;两者是不同连接的判断依据是 Lettuce `PooledClusterConnectionProvider` 的 `ConnectionKey`,来自审查者读的 7.5.2 源码,仓库用 7.9.0,存疑程度低)、R2-mig-04(`:275-307`;`RedisStoreProvider.java:169-179` 的 `wait` 接受副本数)、R2-mig-05、R2-parity-03(`ConfigConverter.java:591-598`,4.x `IdConverterDefinition.Friendly.padStart = false`,5.0 `CosIdProperties.java:266` 默认 true)、R2-remote-01。

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 冻结面 |
|---|---|---|---|---|---|---|
| R2-mig-01 | BLOCKER | 数据安全 / 易用性 | `config/ConfigConverter.java:155`(`store(config, "cosid.segment.distributor", "redis", …)`)、`:226-318`、`:374`、`:409`;`ApplyResult.java:47`(`target`、`machineTarget` 都在结果里) | `config` 只按 4.x 的 `cosid.segment.distributor.type` / `cosid.machine.distributor.type` 生成 `cosid.stores`,完全不读 `apply-result.json` 中的 `target` / `machineTarget`(类型、url、`rangeTable`/`slotTable`/`namespaceTable`、Mongo `database`、Redis `cluster`)。<br>① **跨类型迁移**:09 §3、README、from-4x 都明确支持(Redis → JDBC 等)。这时生成的配置让 5.0 连回 4.x 的那种后端。Spring 应用从上下文里按类型注入客户端(`CosIdStoreClients.java:41-44`,不需要 uri),于是 5.0 在一个没有迁移数据的存储上从 `initialOffset` 发号,槽位的 `next_floor` 也是 0,与 4.x 重号。如果应用已经去掉了源后端的 `cosid-store-*`,则启动报错。<br>② **同类型、自定义库或表名**:例如 `--to-option database=ids` 时,5.0 用的是 `cosid_db`(4.x 的库),Mongo 按需建 `cosid_range`,完全静默。<br>D-B2 的守卫(`Migration.java:971`)只核对 key 名,不核对存储;报告里也没有一行提示。`MigrationScenarioTest` 的跨类型用例(`RedisToH2MigrationTest` 等)自己拼 `RuntimeSpec`,不走 `config` 的输出,所以测试没有覆盖。**定级(总调度)**:按 §0 与 R1-x-07 的口径("合理配置下悄悄重号")定为 BLOCKER:② 用的是 README 写明的选项(`migration/README.md:76,78` 的 `rangeTable`、`database`),整个流程没有任何提示;① 只在应用仍带着源后端的 `cosid-store-*` 与客户端时发生,否则启动报错 | 以结果的 `target` / `machineTarget` 为准写 `cosid.stores`:类型取 `target.type()`;JDBC 写出表名选项,Mongo 写 `database`,Redis 写 `cluster`;口令类选项不写。类型与 4.x 不同时,报告写明并提醒核对客户端 bean。最低限度:写出的存储类型与 `target.type()` 不一致时拒绝(像 D-B2 那样要求显式放行)。另加一道兜底:给每个已迁移的普通号段写 `initial-offset: <targetNext>`。`initial-offset` 只在 key 第一次预留时生效,对正确的存储没有影响;存储指错时也只留下空洞,不会重号(周期号段每期新建 key,不适用)。补一个"跨类型 + 自定义库/表名"经 `config` 输出启动 5.0 的测试 | 无 |
| R2-parity-01 | BLOCKER(多个服务共用该命名空间时) | 特性、迁移 | `migration/src/main/java/me/ahoo/cosid/migration/config/ConfigConverter.java:477,483-487`;`config/Legacy4x.java:285-303`;`migration/README.md:142-146`;`docs/architecture/11-feature-parity.md:21`;4.x `cosid-spring-boot-starter/.../snowflake/SnowflakeIdBeanRegistrar.java:179-181`、4.x `documentation/docs/en/reference/config/snowflake.md:22,26` | 4.x 每个 snowflake 定义可有自己的 `namespace` 与 `machine-bit`(`provider.<x>.namespace`,4.x 文档:"Namespace of the generator's machine id","Give a generator with fewer bits its own namespace"),机器号在该命名空间按该位宽分配。5.0 的等价物是命名 machine(D37),但 ① `config` 的 `ownNamespace()` 只把 `<prefix>.namespace` 记为 handled,**不写命名 machine、不写报告**,转换出的 snowflake 落到默认 machine(服务命名空间);若多个服务共用这个命名空间来让 snowflake 跨服务唯一,迁移后各服务从各自槽位 0 发号,跨服务重复,且无任何提示(cosid80 的同类问题 R1-x-07 已按 D37 修好,snowflake 这条路径没有跟上)。② 自有 `machine-bit` 小于默认 `bits` 时 5.0 启动失败(响亮,但文档没说要改成命名 machine)。③ 11 只有 `cosid.generator.namespace` 一行(L21),没有 snowflake 自有命名空间/位宽这一行。**定级(总调度)**:与 R1-x-07(BLOCKER)同一机制,只是换成 snowflake:4.x 按定义自己的命名空间分配机器号(`SnowflakeIdBeanRegistrar.java:180-181`,`origin/4.x`),共享时迁移后跨服务重号且没有提示;不共享时 plan 已读取该命名空间的时间戳(README:142-146),无害 | `config`:snowflake 的自有 `namespace`(≠ `cosid.namespace`)写成 `cosid.machines.<ns>`(namespace、bits=该定义的 `machine-bit`、存储同默认 machine)并加 `machine: <ns>`,同一命名空间的多个定义共用一个;共享时按 D37 的"共享命名空间单独迁移一次"处理,至少先写报告。README / from-4x(中英文)补一句;11 §1 补一行(测试:ConfigBindingTest 加 snowflake 自有命名空间用例) | 无 |
| R2-mig-02 | MAJOR | 易用性 / 数据安全(R1-spi-01 在迁移工具中仍存在) | `StoreLocation.java:88-90`(`options.get(name)` 按字面查)、`target/JdbcTarget.java:63-67`、`Args.java:120-129`、`source/MongoSource.java:107` | `--to-option` / `--machine-to-option` / `--from-option` 的键不校验,也不归一化。自 #1378 起,运行时把 `range-table`、`RANGE_TABLE` 都对应到 `rangeTable`,并拒绝未知键;迁移工具却按字面读取,`range-table=ids_range` 或拼错的 `rangeTabel` 都被静默忽略,数据写进默认表 `cosid_range`。5.0 按归一化后的 `rangeTable` 读 `ids_range`(带 `initSchema` 时建出空表),从 `initialOffset` 发号。`plan.json` 保存的是同一组选项,`verify` 读的也是同一张默认表,照样通过,发现不了 | target 的选项先过 `StoreOptions.resolve`(对应 provider 的 `options()`,加上迁移自己的 `user`),未知键直接报错;source 的选项也声明白名单(`user`、`password`、`segmentTable`、`machineTable`、`database`、`authSource`、`cluster`) | 无 |
| R2-mig-04 | MAJOR | 易用性 / 持久性 | `config/ConfigConverter.java:275-277`、`:286-287`、`:296-298`、`:303-306` | (docs+parity 审查的 R2-parity-02 是同一问题,合并于此。)`config` 对存储选项的转换落后于 5.0 现有的选项,11 第 78–80 行的"保留/等价"经 `config` 迁移后不成立:<br>① 4.x `wait-replicas: N` 一律写成 `wait: auto`(= `WAIT 1`,`RedisStoreProvider.replicas`),报告却说 "waits for the replicas it finds"。N ≥ 2 时持久性被悄悄降级,与 redis README:12 的"WAIT 覆盖所有副本(`wait` 设为副本数)"正相反。<br>② 4.x `jdbc.fetch-plan: transaction` 被报告成 "custom 4.x SQL and table initialization are gone",没有写成 5.0 已有的 `options.fetchPlan: transaction`,而 06 §2.1 明确写了二者对应(R1-jdbc-06):迁移后在 SQL 代理之后第一次 reserve 就失败(R1-jdbc-06 的守卫让它报错而不是重号,但上线即故障)。<br>③ `mongo.write-concern` 的报告说 "5.0 always uses majority",但 5.0 有 `options.writeConcern`。<br>④ ZooKeeper retry 的提示让用户手写 `retry.base-sleep-time-ms` / `retry.max-sleep-ms`(4.x 的键,`CosIdZookeeperProperties` 的 `baseSleepTimeMs`/`maxSleepMs`)。5.0 声明的是 `retry.baseSleepTime` / `retry.maxSleep`,照做会因未知键启动失败。① 是静默降低持久性(Redis 的持久性是唯一性的前提),所以定为 MAJOR | `wait-replicas: N` 写成 `wait: N`;`fetch-plan` 写成 `fetchPlan`;`write-concern` 原样写成 `writeConcern`;retry 三项直接写成 5.0 的键(数值即毫秒,`ZookeeperStoreProvider.duration` 接受数字) | 无 |
| R2-mig-03 | MINOR | 数据安全(R1-mig-02 的修复在 Cluster 上无效) | `RedisConnection.java:72-73`(nodeOf → `getConnection(nodeId)`)、`:116-124`;写入在 `target/RedisTarget.java:93`、`:160`,`source/RedisSource.java:121`,都经 `redis.commands()` | **Cluster 模式下,写入和 WAIT 走的不是同一条连接**:<br>- 写入走 `connection.sync()`,按 slot 路由。Lettuce 的 `PooledClusterConnectionProvider.getWriteConnection` 用 `ConnectionKey(WRITE, host, port)`(7.5.2 源码 `:199`)。<br>- `WAIT` 走 `connection.getConnection(nodeId)`,即 `ConnectionKey(WRITE, nodeId)`(`:445`)。<br>- 两个 key 按 `ClusterNodeConnectionFactory.ConnectionKey.equals` 不相等,所以是两条 TCP 连接。<br>- `WAIT` 只统计本连接自己的写入,在一条没写过的连接上会立即返回副本数。<br>结果是 Cluster 上的 4.x 封存、5.0 raise 和 prepare 实际上都不等副本确认。09 §3 和 README 所说的"与运行时 `wait=auto` 一致"在 Cluster 上不成立(redis README:41 自己也写了"WAIT 只统计本连接的写入")。运行时没有这个问题:`LettuceRedisScripts.java:101` 在同一条 nodeId 连接上执行 EVALSHA 和 WAIT。standalone 模式写入和 WAIT 共用 `sync`(`:82`),是正确的。`RedisConnectionTest` 只用了代理桩,没有真实 Cluster 的用例 | Cluster 模式下写入也在 `nodeOf(key)` 返回的连接上执行(做法同 `LettuceRedisScripts`);补一个副本被冻结的 Cluster 测试(仿 `ClusterReplicaWaitTest`) | 无 |
| R2-mig-05 | MINOR | 安全(R1-mig-01 / D-M2 残留) | `target/JdbcTarget.java:57-59`、`source/JdbcSource.java:63-71`、`:128`、`Main.java:136-143`;运行时 `JdbcStore.java:88-93` 把它作为 cause 抛出 | URL 本身已经脱敏,但后面拼上的 `e.getMessage()` 可能就是原始 URL:JDK 的 `DriverManager.getConnection` 找不到驱动时抛 `"No suitable driver found for " + url`(URL 前缀拼错,或者 fat jar 里没有那个驱动)。url 中带 `password=` 时,密码仍会进 stderr。运行时 `JdbcStore.detect` 把同一个 SQLException 作为 cause 抛出,Spring 启动失败时日志会打印出来 | 拼接 cause 的消息前,也用 `StoreLocation.redactUrl` / `DriverManagerDataSource.redacted` 处理一遍;或者遇到 SQLState `08001` 时只报脱敏后的 URL | 无 |
| R2-parity-03 | MINOR | 特性、迁移 | `ConfigConverter.java:591-598`(`pad-start` 只在 4.x 为 true 时写出);`spring/autoconfigure/.../CosIdProperties.java:266`(`Friendly` 默认 `true`);4.x `IdConverterDefinition.java:364`(snowflake friendly `padStart = false`)、4.x `AsStringSerializer.java:43,77-78` 与 `SnowflakeIdStateParser.java:102`("no padding");`integrations/jackson/.../AsString.java:84`;golden `migration/src/test/resources/config/yaml/input.yaml:29-31` → `expected.yaml:55-60` | friendly 的默认补零与 4.x 相反:4.x 的 snowflake friendly 转换器与 `@AsString(FRIENDLY_ID)` 默认**不补零**,5.0 的 `friendly.pad-start` 与 `@AsString.padStart` 默认 **true**。`config` 在 4.x 为默认 false 时什么也不写,于是迁移后 friendly 文本从 `…-1-5` 变成 `…-0001-0005`(golden 的 `trace` 就是这样);jackson 页的 4.x 对照只写了 `radixPadStart → padStart`,没说 friendly 的默认变了。`Friendly.decode` 两种都能读(`FriendlyFormat.parse` 按分隔符切分),不影响唯一性,只是文本形式悄悄改变 | `config` 对 snowflake friendly 在 4.x 为 false 时显式写 `pad-start: false`;jackson 页与 from-4x(中英文)写明"4.x FRIENDLY_ID 不补零,5.0 要保持旧文本请写 `padStart = false`"。不建议改 5.0 默认值(那是 RC2-BREAKING) | 无 |
| R2-mig-06 | MINOR | 代码清洁 | `migration/src/main/java/me/ahoo/cosid/migration/config/ConfigConverter.java:702-704` 对比 `:712-714`、`config/Legacy4x.java:233-238` | `targetLayouts` 的 javadoc 仍写"the CosIdGenerator becomes `Layout#COSID_80`",方法体(D36 之后)返回的是 `legacy.cosidLayout()`,即 4.x 的位宽与阈值,行内注释"config keeps the 4.x bits"与 javadoc 互相矛盾。`Migration.java:504` 用它做 M3 判定,读 javadoc 的人会弄错比较对象 | javadoc 改为"each time-based generator keeps its 4.x layout (the snowflakes and, D36, the CosIdGenerator)" | 无 |
| R2-remote-01 | MINOR | 架构(文档) | `store/remote/.../RemoteApi.java:26-31`、`:49`;`documentation/docs/en/guide/server.md:81`、`zh/...:77`、`server/README.md:20` | 冻结协议常量的 Javadoc 落后于 08 和实现:<br>- `ACQUIRE` 没写 `slotOffset?`,而 `RemoteSlotLeaseStore.java:51` 每次都会发送;<br>- `RENEW` / `RELEASE` 没写窗口字段 `expiresAt/floor/ceiling/storeNow`(`:97-108`);<br>- `NAMESPACE_MISMATCH` 只写了 "limit or tolerance"。<br>第三方非 JVM 客户端会照着这里实现。站点与 server README 的 API 表同样缺 `/slots:acquire` 的 `slotOffset?`,也没说 `409 namespace_mismatch` 包括 slotOffset 不一致(spring 审查的 R2-server-03,合并于此) | 按 08 §2 更新这几行和三处 API 表 | 无 |

**确认通过(store、migration)**:
- **#1378 选项与 key 契约**:5 个 provider 都声明了 `options()`(`JdbcStoreProvider.java:51`、`RedisStoreProvider.java:72`、`MongoStoreProvider.java:57`、`ZookeeperStoreProvider.java:63`、`RemoteStoreProvider.java:52`);归一化只有一处实现(`StoreOptions.canonical`:小写,去掉 `-` 和 `_`),由 `RuntimePlan.java:236` 与 server 的 `BackingStore.java:71` 调用,与 06 §1、basic.md、各 README 一致。key 规则(`StoreKeys.java:40,95-117`:命名空间 1–100、range key 与 owner key ≤ 255、拒绝代理项)与 5 份 schema 的列宽、索引键长上限、ZooKeeper 路径规则、Redis hash tag 都相容;一个后端存不下的 key 在启动时由 `RuntimePlan` 统一拒绝;TCK 证明每个后端都能存上限长度的非 ASCII key(`RangeStoreContract.java:215`、`SlotLeaseStoreContract.java:563`)。
- **冻结的存储格式与 06/08 逐项相符**:5 份 schema 都有 `cosid_slot_last_owner_key` 索引与 `slot_offset integer not null default 0`;Redis `:meta`(含 `slotOffset`)、`:held`、行转义与演进规则;Mongo 索引 `ns_slot`(唯一)与 `ns_lastOwnerKey`、命名空间文档 `{limit, toleranceMs, slotOffset}`;ZooKeeper 元数据文本 `limit=/tolerance=/slotOffset=`。迁移工具的 target 写的格式与各 store 一致;缺 `slotOffset` 时读作 0,每个后端都有测试。`RemoteApi` 的路径与错误码与 08 §2 一致(javadoc 落后,R2-remote-01)。
- **#1376 / #1399 迁移**:共享命名空间的默认偏移 `2^nodeBits − 2^bits`、`headroom` 与 `guaranteed` 报告、LOW HEADROOM 警告、`offset > M` 与 `offset + 2^bits ≤ 2^nodeBits` 检查、滚动 apply、verify 发现 4.x 机器号 ≥ offset 时报错(`Migration.java:209-340,573-605,729-748,850-857`);`config` 写出 `cosid.machines.generator`(`ConfigConverter.java:397-431`);standalone Redis 的 WAIT 正确(`RedisConnection.java:82`)。
- **#1379 / #1392 / #1393 / #1396**:generated-key 守卫(`JdbcRangeStore.java:95`)与 `FetchPlan.TRANSACTION`;MySQL reserve 对已打开的 key 先走 2 个参数的 update;renew 的 CAS 语义不变;ZooKeeper 按记住的版本 `setData`,`czxid` 变了就丢弃这次区间(`ZookeeperRangeStore.java:95-120`);Mongo 只按 `lastOwnerKey` 匹配。
- **依赖方向**:migration 依赖 `store/jdbc` 与 `store/redis` 的 `slot-row.lua`,没有模块依赖 migration;store 之间互不依赖。

**不单列的小问题**(并入相应 PR 顺手改):`JdbcSlotLeaseStore.java:56-57` 的类 javadoc 仍写 renew 先读整行(#1392 之后先只读存储时钟);§5.9 的 javadoc 缺口(`ZookeeperStoreProvider.TYPE`、`JdbcStoreException`/`RedisStoreException` 的构造器)仍未补;`JdbcStore.addMissingIndex`(`JdbcStore.java:145`)是 public static,只给迁移工具用,已经成了冻结 API 的一部分,保留无害;`MongoTarget.prepare` 逐个 `insertOne` 预建槽位,可改 `insertMany`。

**性能(store)**:与 4.x 对照的往返次数:MySQL reserve 1 次(相同)、renew 2 次(相同),ZooKeeper reserve 快速路径 1 次(4.x 2 次),Mongo reserve/renew 各 1 次 `findOneAndUpdate`。#1392、#1393、#1396 没有在 reserve/renew 上增加工作。§5.11 剩余各项按 §9.2 为 GA 后(R1-jdbc-05/10、R1-redis-04、R1-mongo-02),本轮代码未变。

### 10.7 spring/*、server、integrations、build、benchmark

范围:`spring/autoconfigure`、`spring/starter`、`server`、`integrations/*`、`extras/sharding`、`bom`、`examples`、`build-logic`、`.github/workflows`、`benchmark/` 的工具,重点是 #1365、#1373–#1375、#1385、#1397、#1401、#1402、#1381/#1394/#1398/#1404、#1405、#1406。总调度复核:R2-spring-01(`CosIdSpecs.java:160-178` 在每个来源中找没有绑定的键,`boundElsewhere` 不处理被高优先级来源整体替换的列表;Spring 的列表只从一个来源绑定,属根据 Spring 源码的推断)、R2-spring-02(`:419-451` 中 snowflake、custom、segment、periodic 分支都不检查其他策略的键,只有 uuidv7 与 cosid80 检查)、R2-spring-04(`:83` 的孤立 javadoc)、R2-server-01(`BackingStore.java:127-135` 只设了 `maximumPoolSize`)、R2-server-02、R2-bench-01(`PeriodicSegmentBenchmark.java:59-60` 手工构造、listener 为 NOOP)。

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 冻结面 |
|---|---|---|---|---|---|---|
| R2-spring-01 | MINOR | 易用性 | `spring/autoconfigure/.../CosIdSpecs.java:160-178`(`UnknownKeys` 在每个来源里找未绑定的键);Spring Boot 4.1.2 `IndexedElementsBinder.java:63-67,105`(列表只从第一个提供它的来源绑定,元素也只在这个来源内绑定);`CosIdSpecsTest.java:61-68`(测试只有一个来源) | 未知键检查会把"被高优先级来源整个替换掉的列表"误报成拼写错误。`codec` 是 `List<Codec>`:`application.yaml` 写了 `codec: [ {radix: {radix: 62}}, {prefix: O} ]`,`application-prod.yaml` 或命令行 `--cosid.generators.order.codec[0].radix.radix=36` 把它改成一项时,Spring 只从后者绑定这个列表,前者的 `codec[1].prefix` 不会经过 `onSuccess`,启动于是失败,并提示 "they bind to nothing; check their spelling"。整表替换是 Spring 列表的正常语义,profile 覆盖里很常见。存疑:根据 Spring 源码推断,未实测 | 记录每个已绑定列表的根名(如 `cosid.generators.x.codec`);其他来源中位于这个列表下的键不算未知键,或者单独提示"该列表已被更高优先级来源整体替换"。补一个两个来源的测试 | 兼容(放宽校验) |
| R2-spring-02 | MINOR | 易用性 / 冻结面 | `CosIdSpecs.java:419-426`(snowflake)、`:435-437`(custom)、`:443-446`(segment)、`:447-451`(periodic);对照 `:438-442,467-490`(uuidv7)和 `:538-550,680-706`(cosid80);`:85`(`FOREIGN_KEYS`);`server/.../ServerProperties.java:36` | 写在不适用策略上的键仍被静默忽略。`strategy: segment` 加 `period: YEAR_MONTH_DAY`,得到的是不分期的普通号段,文本里也没有日期;snowflake 上的 `store`、`zone`、`max-batch`,segment 上的 `layout`、`node-bits`、`seal-after`,custom 上的 `store`,都是这样。只有 uuidv7(#1406)和 cosid80 会对其他策略的键报错,规则因策略而异。按 api-freeze §0,RC1 之后不能再收紧校验,所以这些键实际上已经被冻结为"接受并忽略"。同类情况:`cosid.server.*` 和各集成的键(`cosid.{mybatis,jdbc,activiti,flowable,axon}.*`)完全不做未知键检查,`ServerProperties` 走默认绑定,`ignoreUnknownFields`。所以 `cosid.jdbc.enable=true`、`cosid.server.ids.enable=true` 这类拼写错误都会静默失效 | 请用户在两种做法中选一种:① RC2 起改为启动失败,与 uuidv7 一致,按属性路径汇总报错;② 只打 WARN(兼容),并在 generators.md 写明"不适用的键被忽略"。server 和集成的键同样处理,至少打 WARN | RC2-BREAKING(改为报错就是收紧 `cosid.*` 的校验规则);兼容(只打 WARN) |
| R2-spring-03 | MINOR | 文档 | `documentation/docs/en/reference/config/generators.md:17`、`zh/...:15`;`en:79-80`、`zh:72`;`en:8-11`、`zh:7-9`;`CosIdSpecs.java:203-205,463-466` | ①(`strategy` 取值漏 `uuidv7`,并入 R2-doc-01)。② uuidv7 一节说其他策略的键都会让启动失败,但有默认值的键(`step`、`initial-offset`、`lookahead`、`reserve-timeout`、`prewarm`、`regression.*`)设了也不报错,`CosIdSpecs.java:463-466` 的注释也承认这一点。③ 带点的名字:环境变量和系统属性写不出方括号,而且这两类来源本来就不做未知键检查。用 Docker 部署的 cosid-server 主要靠环境变量配置,`COSID_GENERATORS_ESHOPORDERING_ORDER_*` 会变成一个名为 `eshopordering` 的生成器,报 "strategy … not [null]"。文档只讲了 YAML 和 properties。④ 名字的第二段恰好是属性名时(如 `billing.period`、`x.store`、`x.codec`),报错里没有方括号写法的提示,因为 element 3 是属性时 `bracketed` 直接返回空 | ① 见 R2-doc-01;② 改成"其他策略中没有默认值的键";③ 写明带点的名字只能来自配置文件或命令行;④ 可选:element 3 是属性、但后面还跟着属性名时,也给出方括号提示 | 无 |
| R2-spring-04 | MINOR | 代码清洁 | `CosIdSpecs.java:83`;`CosIdGeneratorRegistrar.java:53-55,157` | `CosIdSpecs:83` 是一行孤立的 javadoc("A namespace taken from spring.application.name …"),后面紧跟另一个 javadoc,不修饰任何成员,是 #1375 的残留。registrar 的类注释和 `GeneratorBean` 的 `@param <T>` 只列了 `IdGenerator`、`PartsIdGenerator`、`GroupedIdGenerator`,没有 `UuidV7Generator` | 删掉孤立注释;补上 uuidv7 的 Bean 类型,可以和 R2-uuid-01 一起改 | 无 |
| R2-server-01 | MINOR | 高性能 / 易用性(运维) | `server/.../BackingStore.java:72,127-135`;`ServerProperties.java:102`(threads 默认 64);`SharedStoreProvider.java:69-77`;`StoreHealthIndicator.java:65-71`;`guide/server.md:32` | JDBC 后备存储的 HikariCP 池只设了 `maximumPoolSize = blocking.threads`。`minimumIdle` 用 Hikari 默认值,也就是等于最大值,所以每个实例常驻 64 个连接,3 个实例就超过 MySQL 默认的 `max_connections=151`,同库的其他应用也会被挤掉。`connectionTimeout` 用默认的 30s,远大于存储 `timeout`(2s)和客户端的 `requestTimeout`:池耗尽时,阻塞线程最多会等 30s,而客户端早已超时并重试。这个池还要供 `/ids` 运行时的补充和 readiness 探针使用,所以 64 个连接并不能做到每个阻塞线程一个。也没有任何键能单独调整这个池:jdbc 的 options 由 provider 校验,多出来的键会被拒绝 | `minimumIdle` 设小(如 2),`connectionTimeout` 设为存储 `timeout`;池大小取 `threads + 生成器数 + 1`,或者新增 `cosid.server.jdbc-pool.max-size` / `min-idle`;文档写明每个实例占用的连接数 | 兼容(新增键,可选) |
| R2-server-02 | MINOR | 易用性(协议文档) | `server/.../IdController.java:80-93`;`Bodies.java:168`;`documentation/docs/{en,zh}/guide/server.md`(API 表与"Issuing ids from the server"一节) | `/ids[/{g}]` 以 JSON 数字返回 63 位 long。08 §1 和 D37 把 `/ids` 定位为给"非 JVM 语言"发号,但 JavaScript/TypeScript 的 `JSON.parse` 会对大于 2^53 的整数做舍入。`SNOWFLAKE_MS` 的 id 早已超过 2^53:时间左移 22 位,纪元后约 25 天就超过了。在 2^62 附近,舍入粒度是 512,相邻的 id 在 JS 端会变成同一个数,客户端看到的就是重号。文档没有任何提示 | 文档写明 JS 客户端应使用 `/ids/{g}/strings`(没有 codec 时就是十进制文本)或 `JS_SAFE_*` 布局,OpenAPI 描述同步修改。协议不变 | 兼容(只改文档) |
| R2-build-01 | MINOR | 架构(兼容性验证) | `build-logic/.../cosid.java.gradle.kts:68-86`(`-PspringBootVersion` 把强制平台加到所有 `*Classpath`,`compileClasspath` 也在内);`.github/workflows/integration-test.yml:676-681`;`package-deploy.yml:62`(发布构建不带 `-PspringBootVersion`) | 兼容矩阵在 Boot 4.0.9 下先重新编译主代码再跑测试,而发布的 jar 是在 4.1.2 下编译的。"用新版编译、在最低版上运行"这种情况从来没有测过:如果 4.1 新增了重载或改了签名,源码在 4.0.9 下照样能编译、测试也能通过,发布的字节码却会在 4.0.x 上抛 `NoSuchMethodError`。`store/redis` 也一样:矩阵把 Lettuce 强制为 Boot 4.0.9 的 6.8.2 后重新编译,而发布的 `cosid-store-redis` 是针对 7.9.0 编译的 | 矩阵中 4.0.9 的那几格只替换 `testRuntimeClasspath`,主代码和测试仍按默认 BOM 编译;或者另加一个 job:用 `projectBuildRepo` 里的发布 jar 组装一个 Boot 4.0.9 示例(Maven 和 Gradle 各一个),跑 examples 的冒烟测试 | 无 |
| R2-build-02 | MINOR(存疑) | 架构(D45 发布元数据) | `cosid.library.gradle.kts:40-45`(`fromResolutionResult`,即构建 BOM 4.1.2 下解析出的版本),对照 `cosid.spring.gradle.kts:31-56`(4.0.9 的 `minimumVersions`);`store/mongo/build.gradle.kts`(`api(libs.mongodb.driver.sync)`,不写版本);`gradle/libs.versions.toml:21-23`(Lettuce 为安全修复固定在 7.9.0);`documentation/docs/en/guide/getting-started.md:3`(支持 Boot 4.0+) | `spring/*` 发布最低 Boot 的版本,让应用保留自己 Boot 管理的版本。但 `store/*`(按 D14 由应用自己添加的依赖)发布的是构建 Boot 4.1.2 下的版本:`cosid-store-mongo` 发布 mongodb-driver-sync 5.8.1,而 Boot 4.0.9 管理的是 5.6.5;`cosid-store-redis` 发布 Lettuce 7.9.0,而 Boot 4.0.9 管理的是 6.8.2,大版本都不同。用 Gradle 原生 `platform()` 的 Boot 4.0.x 应用会被升级到 Lettuce 7 和 Mongo 5.8,这与该 Boot 的 Spring Data 组合没有测过。Maven 和 dependency-management 插件则相反,会把版本压回 6.8.2,于是针对 7.9 编译的字节码跑在 6.8.2 上,同样没有测过(见 R2-build-01)。以上版本号取自本机缓存中 `spring-boot-dependencies` 4.0.9 和 4.1.2 的 POM;是否真的不兼容没有验证 | 至少在 R2-build-01 的"发布 jar + Boot 4.0.9"冒烟测试中覆盖 redis 和 mongo;在 store 页写明各 Boot 版本下实际得到的驱动版本。`store/*` 是否也按 `minimumVersions` 发布(Lettuce 的安全固定除外),请总调度决定 | 无 |
| R2-bench-01 | MINOR | 高性能(门槛覆盖,补充 R2-perf-02) | `benchmark/v5/.../PeriodicSegmentBenchmark.java:59-60`;对照 `core/.../internal/RuntimeParts.java:187,212`(`ObservedStores.ranges` 加上运行时的 listener) | P8 直接 `new PeriodicSegmentGenerator(…, new V5.MemoryRangeStore(), …, GeneratorListener.NOOP)`,没有经过 `CosIdRuntime`。生产路径每次补充都要经过 `ObservedStores` 计时和 listener,这正是 R1-core-06 指出过的那一层,而 #1380 只把 P5、P6 改过来了。P8 跨期的 p999 恰恰取决于补充。R2-perf-02 说 P1–P4 不经过运行时,但测到的是同一个对象;P8 不属于这种情况 | P8 改用 `GeneratorSpec.PeriodicSegment.builder("memory", new AcceleratedDay(DAY_MILLIS), ZONE)`,按 `V5.segmentRuntime` 的方式由运行时组装(`AcceleratedDay` 已经实现了 `Period`,见 `:83`);4.x 一侧不变 | 无 |
| R2-bench-02 | MINOR | 高性能(门槛覆盖,补充 R2-perf-02) | `.github/workflows/release-gate.yml:63-87`(8 个组,没有 remote);`benchmark/scenarios.md` 的 P7、P10 表;4.x 的 `proxy/cosid-proxy/.../ProxyIdSegmentDistributor.java`、`ProxyMachineLeaseStore.java`、`proxy/cosid-proxy-server/.../SegmentController.java`、`MachineLeaseController.java` | 按 08 §1,cosid-server 的主要角色是远程区间/租约服务,它的客户端 `cosid-store-remote` 是一个发布的存储。R2-perf-02 以"4.x proxy 也没有基准"为由只提了 `/ids`。但 `/ranges:reserve` 和 `/slots:*` 在 4.x 有直接的对照:proxy 的 segment 与 lease 接口,加上 `ProxyIdSegmentDistributor` 和 `ProxyMachineLeaseStore`。D21 对这条路径没有任何证据,11 §7 只给了功能测试。而 5.x 在这条路径上还多了 JSON、bearer 校验、有界池、虚拟线程和异步派发 | GA 前至少加上 5.x 的趋势行:`RemoteChainIdBenchmark`(P7 remote,`V5.storeRuntime` 指向进程内的 `CosIdServer`,后端用内存存储)和 `RemoteStoreBenchmark`(P10);有余力再做 4.x proxy 的对照组 | 无 |

**确认通过(spring、server、build)**:
- **配置冻结**:rc.1 jar 中生成的 `spring-configuration-metadata.json` 与 `CosIdProperties` 的 `@DefaultValue` 一致(含 `cosid.machine.leased.acquire-timeout`、`slot-offset`、`cosid.metrics.generated.enabled`、9 个集成键;hint 中 strategy 含 `uuidv7`,layout 含 `COSID_80`,store type 含 `remote`)。`cosid.stores`/`machines`/`generators` 是 Map,条目内的键没有 IDE 元数据,是配置处理器的通常限制,参考页已补全。未知键让启动失败(`CosIdSpecs.java:104-106,145-182`),`stores.*.options` 交给 provider 的 `StoreOptions` 校验。
- **带点的生成器名(#1405)**:只改了报错(`bracketed()`,`CosIdSpecs.java:196-222`),绑定本身就是 Spring 原生的 `[a.b]`;测试覆盖 properties、YAML、machine、store 与 `stores[db.main].options`。环境变量写不出方括号这一点见 R2-spring-03。
- **server**:client 路径白名单并随 servlet 前缀移动、其他路径 403(`AuthFilter.java:62-65,108-111`);管理端口独立时拒绝启动(`CosIdServer.java:91-98`);所有存储与发号调用经有界池(`BlockingCalls.submit`),池满 503;readiness 用自己的线程、结果复用 1 s、超时 2 s;`/ids/{g}/strings` 走 `runtime.text`,对 uuidv7 调 `/ids/{g}` 返回 404;贡献者名 `cosidServerStore`。
- **D45**:`checkPublishedMetadata` 确认每个依赖都带版本、没有 enforced platform、没有 `spring-boot-dependencies`;rc.1 的 POM 中 `cosid-spring-boot-autoconfigure` 依赖 `spring-boot-autoconfigure:4.0.9`(最低 Boot)。兼容矩阵的缺口见 R2-build-01/02。
- **集成**:4 个集成都用 `runtime.text(name)`;extras/sharding 本轮只改了 javadoc;bom 仍是 16 个模块,由 `checkBom` 守住;examples 的 yaml 与 rc.1 的键一致。
- **发布门槛工作流**:先解析出 SHA,所有组测同一个提交;JDK 补丁版本固定;存储组 ABBA;harness 错误(退出码 4)与 FAIL 分开;P13 标为 TREND(`compare:89`),只报告不判定。

**性能与门槛覆盖(spring 等)**:P5、P6、P7(4 个后端)、P12、P13 经由 `CosIdRuntime`,P11 由 Spring 组装,P1–P4 测到的是运行时交出的同一个对象(§10.2),P8 没有经过运行时(R2-bench-01)。`AnnotatedIdFiller` 用 `ClassValue` 缓存;Jackson 的 `createContextual` 每个属性解析一次;server 请求路径没有全局锁;指标标签都有界。门槛没有测到的发布热路径:P8 生产路径上的 `ObservedStores` 与 listener、周期号段的文本、remote 存储的原语与客户端(有 4.x proxy 可对照,R2-bench-02)、Spring 应用常见的 Micrometer listener(P5、P7 用 NOOP)、`IdGenerators.uuid()`(与 4.x 的实现相同),以及 R2-perf-02 已列出的各项。

### 10.8 文档站、设计文档与跨模块

文档站、迁移指南、README、skills 与 11(总调度复核:R2-doc-03 的 4.x 默认转换器是 `IdConverterDefinition.type = RADIX`(`origin/4.x`),5.0 空 codec 列表是十进制(`Codecs.java:37`),from-4x 全文没有提到;R2-doc-02、R2-doc-04、R2-doc-05、R2-doc-07 的原文都已核对):

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 冻结面 |
|---|---|---|---|---|---|---|
| R2-doc-03 | MAJOR | 文档(迁移) | `documentation/docs/en/guide/migration/from-4x.md:372-388`、`zh/.../from-4x.md:335-351`;4.x `cosid-core/.../IdGenerator.java:44-58,80-81`;5.0 `core/.../runtime/GeneratorSpec.java:130`(`codec = List.of()`) | 4.x 最常用的文本 API `IdGenerator.generateAsString()` / `idConverter()` 在 API 对照表里没有对应项(5.0:`runtime.stringGenerator(name)`、`runtime.text(name)`、`runtime.get(name, IdCodec.class)`)。更要紧的是**默认文本形式变了**:4.x `idConverter()` 默认是 `Radix62IdConverter.PAD_START`(11 位补零 radix62),5.0 规格的默认 codec 是 `[]`(十进制);Spring 用户经 `config` 会得到显式的 radix62(`ConfigConverter.java:584`),但**编程式**使用 4.x 的用户换成 `stringGenerator` 后文本由 radix62 变成十进制,且没有任何提示。同表还缺:`@CosId` 的包 `me.ahoo.cosid.annotation` → `me.ahoo.cosid.accessor`、`CosIdEventListener` → `GeneratorListener`、`Stat`/`Statistical` → `describe()`(只在 runtime.md:145 提到) | API 对照表(中英文)加 `generateAsString()`/`idConverter()` 一行并写明"4.x 默认 radix62 补零 11 位,5.0 默认十进制;要保持文本请写 `codec: [ { radix: { radix: 62, width: 11, pad-start: true } } ]` / `CodecSpec.Radix(62, 11, true)`";补上其余三行 | 无 |
| R2-doc-01 | MINOR | 文档 | `reference/config/generators.md:17`(en)/`:15`(zh);`guide/concepts/runtime.md:45-56`(en)/`:46-54`(zh);`guide/introduction.md:16-23,29-38`(en)/`:13-20,26-34`(zh);`guide/integrations/spring-boot-starter.md:68`(en)/`:66`(zh);`guide/integrations/annotation.md:23`(en)/`:22`(zh);`README.md:32-37`、`README.zh-CN.md:24-29` | uuidv7(D46)只在 generators 页自己的一节、runtime 页两行注释与 server 页出现,**各处的策略清单都没有它**:`strategy` 一行的取值列表只到 `custom`(代码 `CosIdSpecs.java:403` 与 metadata hint 都有 `uuidv7`);runtime 页的规格表没有 `GeneratorSpec.UuidV7`;introduction 说"唯一性只有两种来源"(uuidv7 是第三种:无协调、概率唯一)且"你可以选择什么"表没有它;spring-boot-starter 的 Bean 表没有 `UuidV7Generator`(uuidv7)和 `custom` 的 `IdGenerator`;annotation 页 `String` 字段的说明没有 uuidv7;README 策略表没有 uuidv7 | 各处(中英文)补上 uuidv7(与 custom);introduction 的"两种来源"改为三种或加脚注 | 无 |
| R2-doc-02 | MINOR | 文档 | `README.md:17-19`、`README.zh-CN.md:17-18`;对照 `documentation/docs/en/index.md:50-52`、`docs/release/5.0.0-rc.1.md:9-15` | README 仍是"`5.0.0-alpha.x` is a preview with **no compatibility promise** … may change between alphas",与 rc.1 的"兼容承诺从本版开始"矛盾;站点首页与 getting-started 已改成 RC 提示 | README 两个语言版本换成与站点相同的 RC 提示 | 无 |
| R2-doc-04 | MINOR | 文档 | `gradle/libs.versions.toml:33`(`spring-boot-minimum = "4.0.9"`);`docs/release/5.0.0-rc.1.md:176,210`;`guide/getting-started.md:3`、`guide/integrations/spring-boot-starter.md:4`、`guide/introduction.md:13`、`guide/migration/from-4x.md:370`(中英文);`README.md:46`、`README.zh-CN.md:37` | 发布说明写明支持 Spring Boot **4.0.9** 到 4.1.x,发布的元数据要求 4.0.9;站点与 README 仍写"Spring Boot 4.0+ / 4.0 and later" | 统一写成"4.0.9 及以上(已测 4.0.9、4.1.x)" | 无 |
| R2-doc-05 | MINOR | 文档(skills) | `skills/cosid-strategy-guide/SKILL.md:23-30`;`skills/cosid-spring-boot/SKILL.md:3`;`skills/plugins.json`(description) | 随仓库发布的 `ahoo-cosid-skills` 插件仍是 alpha 时的内容:策略表与描述没有 uuidv7、custom;`SKILL.md:30` 写"Every runtime has one slot shared by all its time-based generators (D18)",与 D37 命名 machine 矛盾,代理照此会给不出跨服务唯一的配置;四个 skill 都没有 `cosid.machines` / `slot-offset` | 按 RC1 更新四个 skill 与 plugins.json(uuidv7、custom、命名 machine、slotOffset) | 无 |
| R2-doc-06 | MINOR | 文档(javadoc) | `core/src/main/java/me/ahoo/cosid/observe/GeneratorListener.java:41-93`;`codec/PartsCodec.java:27-29`;`codec/GroupedCodec.java:29-31`;`IdGenerator.java:21`(及 `PartsIdGenerator`、`GroupedIdGenerator`) | 导出包里由用户实现的 SPI 方法缺方法级契约:`GeneratorListener` 14 个回调中 11 个无 javadoc(接口级的线程/阻塞/失败契约 R1-core-09 已有,但 `onClockSkew` 的符号与单位、`onDuplicateOwnerKey`/`onBatchResized` 何时触发没写);`PartsCodec`/`GroupedCodec` 的 `encode`/`decode` 没有 `IdCodec` 那样的失败契约(R1-core-16 只做了 `IdCodec`),而它们可经 `PartsCodecSpec.Instance`/`GroupedCodecSpec.Instance` 由用户提供;`IdGenerator.generate()`(`custom`/`Instance` 的实现方)没写线程安全要求 | 给这些方法补一句话的契约(线程安全、何时触发、非法输入抛 `IllegalArgumentException`);平凡访问器仍按 §9.2 GA 后 | 无 |
| R2-doc-07 | MINOR | 文档(升级说明) | `docs/release/5.0.0-rc.1.md:202`、`5.0.0-alpha.2.md:161`;对照 `store/jdbc/.../SqlServerDialect.java:91-92` | 升级步骤 6 给的 `alter table cosid_namespace add column slot_offset …` 在 SQL Server 上是语法错误(T-SQL 是 `add slot_offset …`,方言的 `addColumn` 与 `JdbcTarget.java:225` 的提示都是对的)。报错是响亮的,不影响唯一性 | release notes 注明 SQL Server 去掉 `column`,或直接给出各方言的语句 | 无 |
| R2-parity-05 | MINOR | 特性、文档(server) | 4.x `proxy/cosid-proxy-server/.../ProxyServerProperties.java:55-59`(v4.1.1);`server/src/main/java/me/ahoo/cosid/server/ServerProperties.java:120,141`;11 L119;`ConfigConverter.java:202-205`;`documentation/docs/{en,zh}/reference/config/server.md` | ① 4.x `cosid.proxy.server.namespaces`(整台服务只服务这些命名空间,其他一律 403,不管 token)在 5.0 没有对应:`cosid.server.auth.tokens` 里的 token 不受命名空间限制,只能把每个 token 都改写成带 `namespaces` 的 client 才能达到同样效果;11 L119 没有这一项。② 4.x proxy server 的配置(`cosid.proxy.server.{max-clock-skew,min-safe-guard-duration,namespaces,segment.max-step,segment.max-distributors,blocking.*,auth.*}`)在文档里没有到 `cosid.server.*` 的对照(只有设计文档 `docs/architecture/08-remote.md:57` 提了一句 `max-distributors`),`config` 会把它们都报成"no 5.0 counterpart, dropped" | server.md 或 from-4x(中英文)加一张 4.x proxy server → cosid-server 的对照表(含"`namespaces` 无对应,用 client 的 `namespaces` 代替");11 L119 补注。若要等价,可在次版本加 `cosid.server.auth.namespaces`(默认空 = 全部) | 兼容 |
| R2-parity-06 | MINOR | 特性、文档(指标) | 4.x `CosIdMetricsProperties.java:31,38`;`spring/autoconfigure/.../CosIdMetricsConfiguration.java:38`;`documentation/docs/en/guide/operations/monitoring.md:10`;`from-4x.md:150`(en)/`:136`(zh);11 L97 | 4.x 的指标总开关 `cosid.metrics.enabled`(默认 true)在 5.0 没有对应键,文档也没有说怎么关掉 CosId 的 meter(只能用 Boot 的 meter filter,如 `management.metrics.enable.cosid=false`);第 1 轮 §6.9 已提到,但没进 §9.2 分诊,至今未处理 | monitoring 页与 from-4x"其他键"一句(中英文)写明替代写法;11 L97 补注 | 无 |
| R2-parity-07 | MINOR | 文档(11) | `docs/architecture/11-feature-parity.md` L10、20、21、35、39、49、50、58、61、64、81、89–93、96、99、123、130;L83 | ① 11 的 292 个测试链接文件与方法全部存在,但 **33 处 `#L` 锚点再次漂移**(#1383 重新生成之后的 PR 改了测试):`CosIdSpecsTest` 偏 +13…+65 行、`CosIdAutoConfigurationTest` 偏 +7…+67 行、`RemoteStoreProviderTest.opensAStoreOnTheBaseUrl` 写 #L45 实为 L62(清单见下文)。② §9.2 R1-spring-02 要求"11 §4'注入的执行器'一行对 Spring 用户要加注(并入 docs)",L83 至今未加 | 重新生成锚点(CI 校验仍按 §9.2 GA 后);L83 加"Spring 用户不能替换执行器(R1-spring-02,GA 后)" | 无 |

跨模块(设计文档、依赖):

| 编号 | 严重度 | 维度 | 位置 | 问题 | 建议修复 | 冻结面 |
|---|---|---|---|---|---|---|
| R2-dep-01 | MAJOR | 易用性(安全)、依赖 | `integrations/activiti/build.gradle.kts:21`(`api(libs.activiti.engine)`);`gradle/libs.versions.toml:40`(`activiti = "7.0.0.SR1"`) | main 上唯一未关闭的 Dependabot 告警 [#95](https://github.com/Ahoo-Wang/CosId/security/dependabot/95)(high,GHSA-qq48-m4jx-xqh8,MyBatis < 3.5.6 的反序列化问题,2026-10-28 新出现,在 #1401 之后):`org.mybatis:mybatis:3.4.2` 经 `activiti-engine:7.0.0.SR1` 传递进入。`cosid-activiti` 以 `api` 声明 Activiti(六个集成都是 `api`;4.x 的 `cosid-activiti` 是 `implementation`),所以它进入用户的运行时类路径。rc-review §4.3 第 9 项要求 GA 前处理 Dependabot 告警 | 按 D45(上游没有修复时才显式声明传递依赖,并在声明处写明原因与移除条件):Activiti 在 Maven Central 上没有比 7.0.0.SR1 更新的版本,所以在 `cosid-activiti` 中约束 `org.mybatis:mybatis` ≥ 3.5.6,由现有的 Activiti 端到端测试证明 7.0.0.SR1 能在它上面运行;跑不通时,在告警上写明理由后关闭(CosId 不经 MyBatis 反序列化,用户的 Activiti 本来就带着同一个 jar),并在 integrations README 写明这个已知的传递依赖 | 兼容(只改发布的依赖元数据,不在 api-freeze §0 的冻结面内) |
| R2-arch-01 | MINOR | 架构(文档) | `docs/architecture/README.md:3,33-74`;`AGENTS.md:7` | main 的决策表只到 D38(另有 D24);D39–D47 只在 `wip/ga`(Draft #1368)上,而 main 上的代码与文档已经在引用它们:07(6 处)、10(D44)、11(D46 等)、api-freeze、AGENTS.md 的 D45。README 的状态行仍是"RC 前全面审查进行中(2026-10-27)",AGENTS.md 写"decisions D1–D31"。只读 main 的人查不到 D39–D47 的定义(core 审查的 R2-core-04 也记了这一点,合并于此) | 把 `wip/ga` 的决策表(D39–D47)与状态行合入 main(协调会话的阶段节点即可);AGENTS.md 改为"D1–D47" | 无 |
| R2-arch-02 | MINOR | 架构(文档) | `origin/wip/ga:docs/architecture/README.md` 的 D46,对比 `core/.../time/UuidV7Generator.java:24-34,41-55` | D46 写"`rand_a` 的 12 位作为计数器……`rand_b` 的 62 位取自 `ThreadLocalRandom`";#1406 最终实现的是 **16 位计数器**(`rand_a` 的 12 位加 `rand_b` 的高 4 位)和 **58 位**随机数,新毫秒的起点是一个 `< 2^15` 的随机值。代码、javadoc、generators.md 与 PR 描述彼此一致,只有决策表落后 | 合入 main 之前,把 D46 改成 16 位计数器、58 位随机 | 无 |

### 10.9 第 1 轮"已修复"抽查

按 §9 标为"已修复"的条目抽查了 **62** 项(按抽查表的行计):core 16 项(R1-core-01–06、09、11、12、14–18,以及 R1-x-03/05、R1-x-06 的 core 部分)、store 与 migration 29 项、spring/server/integrations 11 项,另有总调度复核的跨模块 6 项(R1-x-01、R1-x-04、R1-x-07、R1-x-08、D43 的 `cosid.metrics.generated.enabled`、R1-doc-01);docs 审查另外读到了 R1-doc-03/04/06 与 R1-core-15。证据见各路笔记的"第 1 轮修复抽查"表。

- **全部 ✅**:core(16)、spring/server/integrations(R1-spring-03/05/06/08/09/10、R1-x-05 的 Spring 部分、R1-server-01/02/03、R1-int-01/02、§9.3 的 readiness 改名)、store 与 migration 中的 24 项(R1-spi-02/04/05、R1-jdbc-01/02/03/04/06/08/11/12、R1-redis-02/03、R1-mongo-01/03/04、R1-zk-01/02、R1-remote-03、R1-tck-01、R1-mig-03/05、R1-x-04、R1-x-07)、跨模块 6 项(R1-x-01:from-4x"静态代码中取号"一节与 `CosIdRuntime` 类注释;R1-x-08:`slot-lease.md` 的"跨服务唯一"一节;R1-x-04:`LayoutCheck.sameIdSpace` 不比较阈值;R1-x-07:命名 machine `generator` 的转换;D43:`cosid.metrics.generated.enabled`;R1-doc-01:安全回滚)。
- **修了一部分或有残留,已记为第 2 轮发现**:
  - R1-spi-01:运行时 ✅,迁移工具 ❌ → R2-mig-02;
  - R1-mig-02:standalone ✅,Cluster ❌(写入与 WAIT 不在同一连接)→ R2-mig-03;
  - R1-mig-01 / R1-jdbc-09:URL 与 `toString` 已脱敏,但异常消息仍可能带原始 URL → R2-mig-05;
  - R1-remote-01:08 ✅,`RemoteApi` javadoc 与站点 API 表落后 → R2-remote-01;
  - R1-core-15 / R1-doc-06:#1383 重新生成过锚点,之后的 PR 又让 33 处漂移;§9.2 要求的 11 第 83 行补注(R1-spring-02)没有加 → R2-parity-07;
  - R1-core-17:键表已有,但 #1406 新增的 uuidv7 键不在其中 → R2-uuid-02。
- **没有发现修复失效导致的回退**。R2-core-01 是 #1380(R1-core-04 的修复)新引入的观测问题,不影响发号。
