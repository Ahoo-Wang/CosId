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
| core | 进行中 |
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
