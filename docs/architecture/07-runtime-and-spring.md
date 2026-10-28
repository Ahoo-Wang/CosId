# 07 运行时、装配与 Spring Boot 集成

## 1. CosIdRuntime:唯一的注册表与生命周期所有者

```java
// 配置与 SPI 值类型是 final class + builder(D39,api-freeze.md §4);下面按字段列出,访问器同名
public final class RuntimeSpec {
    String namespace;                          // 号段 key 与默认 machine 的命名空间
    Map<String, StoreSpec> stores;
    Optional<MachineSpec> machine;             // 默认 machine
    Map<String, MachineSpec> machines;         // 命名的 machine(D37),名字不能是 "default"
    Map<String, GeneratorSpec> generators;     // 名为 "default" 的生成器即 4.x 的 share
    static Builder builder(String namespace); Builder toBuilder();
}

public final class StoreSpec { String type; Duration timeout; Map<String, Object> options; }

public sealed interface MachineSpec {          // 次版本可加变体(D40)
    int bits();
    Optional<String> namespace();              // 槽位命名空间,空则取 RuntimeSpec.namespace
    final class Leased implements MachineSpec { String store; int bits; Duration ttl; Duration tolerance; Duration acquireTimeout;
                                                OptionalInt preferredSlot; Optional<String> ownerKey; Optional<String> namespace; }
                                                // ownerKey 为空时按 §1.1 推导;acquireTimeout 默认 = ttl(R1-core-03)
    final class Static implements MachineSpec { int bits; int slot; Optional<Path> stateFile; Duration ahead;
                                                Optional<Instant> notBefore; Optional<String> namespace; }   // notBefore 由迁移工具给出
}

public sealed interface GeneratorSpec {        // 次版本可加变体(D40)
    final class Snowflake implements GeneratorSpec { Layout layout; ClockRegressionPolicy regression; List<CodecSpec> codec; Optional<String> machine; }
    final class CosId80 implements GeneratorSpec { Layout layout; /* 默认 COSID_80,> 63 位,Unix epoch,毫秒(D36) */
                                                   ClockRegressionPolicy regression; PartsCodecSpec codec; Optional<String> machine; }
    final class Segment implements GeneratorSpec { String store; BatchPolicy batch; List<CodecSpec> codec; }
    final class PeriodicSegment implements GeneratorSpec { String store; Period period; ZoneId zone; BatchPolicy batch; PeriodicPolicy periodic;
                                                           GroupedCodecSpec codec; }
    final class Custom implements GeneratorSpec { String reference; List<CodecSpec> codec; }      // 按名引用用户的 IdGenerator(D41)
    final class Instance implements GeneratorSpec { IdGenerator ids; List<CodecSpec> codec; }     // 代码里直接给实例(D41)
    record Disabled(GeneratorSpec generator) implements GeneratorSpec {}
}

public final class CosIdRuntime implements AutoCloseable {
    public static CosIdRuntime start(RuntimeSpec spec, RuntimeOptions options);
    // options:TimeSource、Executor、GeneratorListener、额外的 StoreProvider、已有客户端(按存储名)、
    //          codecs / generators(解析 CodecSpec.Custom / GeneratorSpec.Custom 的引用)、countGenerated
    public IdGenerator generator(String name);
    public StringIdGenerator stringGenerator(String name);
    public Supplier<String> text(String name);                 // 任意策略的文本 ID(R1-int-01)
    public PeriodicSegmentGenerator periodic(String name);
    public <T> T get(String name, Class<T> type);
    public Map<String, GeneratorInfo> describe();
    public Optional<SlotLease> slot();                         // 默认 machine
    public Optional<SlotLease> slot(String machine);           // D37
    public Set<String> machines();                             // 已启动的 machine,含 "default"
    public IdFiller filler();
    public void close();      // 按依赖逆序:生成器(终止) → 各 LeaseKeeper(release) → 存储 → 自建执行器
}
```

- **装配逻辑全部在 core**:Spring、`server`、`migration`、手写代码都通过同一个 `CosIdRuntime.start` 启动。
- 机器槽位按需启用:时间型生成器选了一个不存在的 machine(默认 machine 未配置,或命名 machine 不存在)时启动失败,并给出明确的错误信息;
  没有被引用的 machine 不建立租约,也不打开它用到的存储。
- 启动顺序:打开存储 → 取得槽位(默认 machine,然后命名 machine 按名序,逐个)→ 创建生成器 → 返回。任何一步失败都会回滚已创建的部分(已取得的槽位逆序 release)。
- 生成器集合在启动时确定(D17),没有全局单例,一个 JVM 中可以有多个互不影响的运行时,每个运行时有自己的 incarnation。
- R3 确定的细节:
  - 禁用生成器表示为 `GeneratorSpec.Disabled(spec)`,保留原 spec 以便 Spring 知道其 Bean 类型;被禁用的生成器不校验,也不需要 machine 或存储。
  - 只打开被启用的生成器与 machine 实际引用的存储。
  - 生成器名不能含 `/`,避免与周期号段的 key 冲突。
  - 校验错误汇总为一个 `CosIdException`,逐行列出;启动步骤中的其他异常保留 cause 后包装。
  - codec 列表为空时使用十进制;转成文本的项(Radix / Friendly / Custom / Instance)只能放在第一位,修饰项放在第一位时作用于十进制。
  - cosid80 与周期号段不提供 `generator` / `stringGenerator` 视图,文本 ID 经 `PartsCodec` / `GroupedCodec` 获得;`IdFiller` 的 `String` 字段自动使用它们。

### 1.0 生成器视图接口(R3-S 补充)

`PartsIdGenerator { IdParts nextParts(); }`(`TimeBasedGenerator` 实现)与 `GroupedIdGenerator { GroupedId generate(); }`(`PeriodicSegmentGenerator` 实现):
cosid80 与周期号段的 Bean 按接口注册,被禁用时可以是调用即抛 `GeneratorDisabledException` 的代理(D24)。生成计数由 `RuntimeOptions` 的计数开关在运行时统一挂上,关闭时热路径不受影响。

### 1.0.1 自定义生成器(D41,R1-core-02)

`GeneratorSpec.Custom(reference)` 经 `RuntimeOptions.generators` 解析(Spring 中按 Bean 名取 `IdGenerator`),`Instance(ids)` 直接给实例。
运行时**不拥有**它们(不关闭、不续约、不预取),其余与内置策略相同:`generator`/`stringGenerator`/`text`/`get`、`IdFiller`、`describe()`(`strategy = custom`)、计数、`Disabled`。
codec 规则同号段(没有布局,不能用 friendly 与 `ID_TIME` 的日期前缀)。只覆盖 `long` 生成器。

### 1.1 ownerKey 推导(沿用 4.x 的 instance-id 规则)

1. 显式配置的 `ownerKey`;
2. 否则,若配置了端口,取 `host:port`;
3. 否则取 `host:pid`。

host 由 `HostResolver` 提供,Spring Cloud 环境下使用 `InetUtils`。ownerKey 只影响粘性选槽与观测,与唯一性无关(05 §2.1)。
一个运行时的所有 machine 用同一个 ownerKey(它们在不同命名空间里)。

### 1.2 命名的 machine(D37,R1-x-06)

一个运行时有一个默认 machine(`RuntimeSpec.machine`)与任意个命名 machine(`RuntimeSpec.machines`)。号段 key 始终在运行时命名空间;
时间型生成器用 `machine` 选择槽位来自哪个 machine,默认选默认 machine。典型用法是跨服务全局唯一:各服务的 `global` machine 用同一个命名空间与同一个存储(或经 cosid-server `remote`),业务生成器仍在服务命名空间。

- 校验:machine 名不含 `/`,不能是 `default`;生成器引用的 machine 必须存在;**一个运行时内各 machine 的有效命名空间两两不同**,两个 Static 的 `stateFile` 不同;
  每个时间型生成器的 `nodeBits ≥ 所选 machine 的 bits`;只启动被引用的 machine(未引用的在日志 INFO 列出)。
- 存储端:共享命名空间的元数据 `{limit, tolerance}` 不一致时 acquire 抛 `NamespaceMismatchException`,所以共享一个命名空间的各服务须用相同的 `bits` 与 `tolerance`。
- 生命周期:每个 machine 一个 `LeaseKeeper`/`StaticSlot`;串行启动;关闭时每个 machine 用"选了它的生成器"的 `max(lastIssued)` release,命名 machine 逆序,最后是默认 machine。
- 观测:`GeneratorListener` 签名不变(namespace 在运行时内唯一确定 machine);指标 `cosid.machine.*` 增加 `machine` 标签;健康检查按 machine 汇总;
  时间型生成器的 `describe()` 增加 `machine`、`machineNamespace`。
- cosid-server:`/slots:*` 本来就按请求中的命名空间工作;受限 token 的 `namespaces` 须包含全局命名空间。

## 2. 可观测性

```java
public interface GeneratorListener {
    default void onRangeReserved(String generator, String key, Range range, Duration latency) {}
    default void onRefillFailed(String generator, String key, Throwable error) {}
    default void onBatchResized(String generator, long oldSize, long newSize) {}
    default void onSequenceExhausted(String generator) {}
    default void onClockRegression(String generator, long backwardsMillis, String action) {}   // borrow / wait / fail
    default void onWindowExceeded(String generator) {}
    default void onSlotAcquired(SlotLease lease, String reason) {}   // idempotent / preferred / sticky / lowest
    default void onSlotRenewed(SlotLease lease) {}
    default void onSlotRenewFailed(SlotLease lease, Throwable error) {}
    default void onSlotLost(SlotLease lease) {}
    default void onSlotReleased(SlotLease lease) {}
    default void onClockSkew(String namespace, long skewMillis) {}
    default void onDuplicateOwnerKey(String namespace, String ownerKey) {}
    default void onStoreTimeout(String store, String operation) {}   // 补充等待超时、存储调用超时(StoreTimeoutException)时触发(#1336)
}
public final class GeneratorInfo { String name; String strategy; String store; Map<String, Object> details; }   // builder(D39)
```

- 计数与指标是 listener 的实现(Micrometer 绑定在 `spring/autoconfigure`),不再是装饰器。生成计数(4.x 的 `generated`)用 `LongAdder` 实现,只在开启时才挂上。
- 热路径上不调用 listener,只有区间切换、补充、等待、租约这类事件才会触发。
- `describe()` 取代 4.x 的 Stat 体系,包含布局、槽位、窗口、就绪区间余量、批大小等信息。`details` 的键是冻结面(R1-core-17):
  已列出的键不改名、不删除,次版本可加键,值只用 String/Number/Boolean;键表在 `GeneratorInfo` 的 javadoc 与 [api-freeze.md](api-freeze.md) §7。

## 3. 字段填充(原 cosid-accessor)

```java
@Target({FIELD, TYPE}) @Retention(RUNTIME)
public @interface CosId {
    String value() default "default";    // 生成器名
    String field() default "id";         // 注解在类型上时使用
}
public interface IdFiller { void fill(Object entity); }
```

- 支持 `long`/`Long`、`int`/`Integer`(范围检查)、`String`(使用生成器的 codec)。
- 优先使用 setter/getter,否则直接访问字段;沿父类查找;解析泛型字段类型;每个类只允许一个 ID。
- 只在 ID 未设置(`≤ 0` 或空串)时填充。
- 按类缓存 `MethodHandle`,首次访问时解析。4.x 的 scanner 预注册改为懒加载。

## 4. Spring Boot 集成

### 4.1 配置模型

```yaml
cosid:
  namespace: app                                             # 省略时取 spring.application.name,再回退 cosid 并 WARN(D38)
  stores:
    db:    { type: jdbc, timeout: 2s }                       # 使用上下文中的 DataSource
    cache: { type: redis, timeout: 1s, options: { wait: auto } }
  machine:
    leased:
      store: cache
      bits: 10
      ttl: 30s
      tolerance: 1s
      preferred-slot: ${POD_ORDINAL:}
      acquire-timeout: 30s                                   # 默认 = ttl(R1-core-03)
    # 或者:
    # static: { bits: 10, slot: 3, state-file: /var/lib/cosid/state, ahead: 10s, not-before: 2026-11-01T00:00:00Z }
  machines:                                                  # 命名的 machine(D37),键与 machine 相同,另需 namespace
    global:
      namespace: global-ids
      leased: { store: cache, bits: 10 }
  generators:
    default:
      strategy: snowflake
      layout: SNOWFLAKE_MS
      codec: [ { radix: { radix: 62, width: 11, pad-start: true } } ]
    order:
      strategy: segment
      store: db
      step: 100
      lookahead: 1s
      codec: [ { radix: { radix: 62 } }, { prefix: "O" } ]
    trace:
      strategy: snowflake
      layout: SNOWFLAKE_MS
      codec: [ { friendly: { zone: Asia/Shanghai } } ]
    bill:
      strategy: periodic-segment
      store: db
      period: YEAR_MONTH_DAY
      zone: Asia/Shanghai
      codec: [ { date-prefix: { delimiter: "" } }, { radix: { radix: 10, width: 6, pad-start: true } } ]
    token:
      strategy: cosid80
      machine: global                                        # 跨服务唯一的 80 位 ID
      codec: [ { radix: { radix: 62 } } ]
    legacy:
      strategy: custom                                       # D41:按 Bean 名引用用户的 IdGenerator
      ref: myLegacyIdGenerator
  metrics: { generated: { enabled: false } }                 # 4.x 同名(api-freeze.md §7)
```

- 存储只声明一次,生成器各自引用,可以混用不同存储。
- Snowflake 布局可在预设上覆盖 `epoch`、`unit`、`time-bits`、`node-bits`、`seq-bits`、`seq-reset-threshold`(#1336)。号段默认 `initial-offset` 为 1。
- cosid80 复用同一组键(D36):`layout` 默认且只能是 `COSID_80`,`time-bits`/`node-bits`/`seq-bits`/`seq-reset-threshold` 可覆盖,`epoch`/`unit` 设置即报错;
  codec 只能是一个 `radix`(62/36,`width`/`pad-start` 报错)或 `friendly`(`zone`、`pad-start`,默认 true)。
- `cosid.namespace` 默认取 `spring.application.name`(D38),解析在 `CosIdAutoConfiguration` 中完成,`CosIdSpecs` 仍是纯函数;两者都未设置时用 `cosid` 并 WARN。
- 未知的 `cosid.*` 键启动失败(`NoUnboundElementsBindHandler`,`cosid.stores.*.options` 除外,R1-spring-03)。
- 不再需要各特性的 `enabled` 开关,依赖关系从声明中推导;只保留总开关 `cosid.enabled`(默认 true)。
  单个生成器可以用 `cosid.generators.<name>.disabled: true` 禁用(D24):不创建实例,其 Bean 调用时抛出 `GeneratorDisabledException`。
- codec 一律是有序列表,写的顺序就是应用顺序。周期号段的列表第一项必须是分组前缀(`date-prefix`),其余项作用于值部分。
- 绑定类是不可变的 `record`;`CosIdProperties → RuntimeSpec` 是纯函数,无需 Spring 即可单测。绑定类不属于 API 兼容承诺,承诺的是属性键(api-freeze.md §0)。
- 编程式扩展:`RuntimeSpecCustomizer` Bean(按 `@Order` 执行)可以在启动前增加、删除或修改生成器(取代 4.x 的 `Customize*Properties`)。
  由 customizer 增加的生成器同样会注册为 Bean:注册器在 `BeanDefinitionRegistryPostProcessor` 阶段只登记名字和类型,实例在 `CosIdRuntime` 启动后提供。
  因此 customizer 必须是无依赖的 Bean(与 4.x 的限制相同),文档中写明。

### 4.2 自动配置

| 类 | 职责 |
|---|---|
| `CosIdAutoConfiguration` | 绑定属性 → `RuntimeSpec`;应用 customizer;收集 `StoreProvider` Bean 与现有客户端;启动 `CosIdRuntime` Bean(`destroyMethod = close`) |
| `CosIdGeneratorRegistrar` | 每个生成器注册为一个以其名字命名的 Bean,实例由 `CosIdRuntime` 提供,保证唯一真相 |
| `SpringDataRedisScripts` | `RedisScripts` 的 Spring Data 适配,仅在没有 Lettuce 原生连接时使用 |
| `CosIdMetricsConfiguration` | Micrometer 版 `GeneratorListener` 与仪表(见 11);`cosid.machine.*` 带 `namespace` 与 `machine` 标签;号段指标只带 `name`,不带 `key`(R1-spring-06) |
| `CosIdEndpoint` | Actuator:`describe()`、按名查看、按名生成 long/string(取代 4.x 的 `cosid`、`cosidGenerator`、`cosidStringGenerator` 三个端点) |
| `CosIdHealthIndicator` | 贡献者名 `cosid`(`/actuator/health/cosid`,R1-spring-05);按 machine 汇总槽位租约:`details.machines.<name>` 含命名空间、槽位、窗口剩余、是否有效;任一失效即 DOWN |
| `CosIdIntegrationsConfiguration` | 对每个集成按 `@ConditionalOnClass` 注册 |
| `CosIdRuntimeHints` | 原生镜像提示 |

目标是 10–15 个类(4.x 为 89 个)。

### 4.3 Starter(D14)

`cosid-spring-boot-starter` 只依赖 `core` 与 `autoconfigure`。用户另加 `cosid-store-jdbc` 等存储依赖,就像 JPA starter 需要另加数据库驱动一样。删除 Gradle feature variants。

## 5. 集成(`integrations/*`,D29:按依赖拆分,每个模块一个 artifact)

| 集成 | 形式 |
|---|---|
| Jackson(仅 Jackson 3) | `@AsString(generator = "…")` 引用生成器的 codec,或 `@AsString(codec = Codec.X, …)` 内联 `radix` / `friendly` / `to-string`,二者互斥;同时提供序列化与反序列化 |
| MyBatis | `Interceptor`,INSERT 前对实体或批量列表(`listKey` 可配置)调用 `IdFiller` |
| Spring Data JDBC | `BeforeConvertCallback`,填充 `@Id` 与 `@CosId` 字段 |
| Activiti / Flowable | 一个通用的 `IdGenerator` 适配器;生成器名来自 Spring 配置,非 Spring 环境回退到系统属性 `cosid.activiti` / `cosid.flowable` |
| Axon | `IdentifierFactory`。Axon 通过 ServiceLoader 静态加载它,因此需要一个静态绑定点(全项目唯一的静态状态,只存在于 `integrations`):Spring 中随 `CosIdRuntime` Bean 绑定和解绑,非 Spring 用 `try (var b = CosIdIdentifierFactory.bind(runtime, name))`;未绑定时抛出异常(独立 jar,引入即表示要用 CosId)。生成器名也可以通过系统属性 `cosid.axon` 指定 |
