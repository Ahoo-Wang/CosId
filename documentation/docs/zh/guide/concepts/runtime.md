# 运行时

`CosIdRuntime` 是唯一的注册中心，也是唯一的生命周期所有者。Spring Boot、cosid-server、迁移工具和手写代码都以同样的方式启动运行时：
一个 `RuntimeSpec`（配置）加一个 `RuntimeOptions`（配置之外的一切）。

```java
RuntimeSpec spec = RuntimeSpec.builder("order-service")       // 命名空间
    .store("db", StoreSpec.builder("jdbc").build())
    .machine(MachineSpec.Leased.builder("db").build())         // 默认 machine
    .machine("global", MachineSpec.Leased.builder("db")        // 命名 machine
        .namespace("global-ids").slotOffset(1024).build())
    .generator("default", GeneratorSpec.Snowflake.builder().build())
    .generator("token", GeneratorSpec.CosId80.builder().machine("global").build())
    .generator("order", GeneratorSpec.Segment.builder("db").build())
    .build();

public final class CosIdRuntime implements AutoCloseable {
    public static CosIdRuntime start(RuntimeSpec spec, RuntimeOptions options);
    public IdGenerator generator(String name);              // snowflake、segment、custom
    public StringIdGenerator stringGenerator(String name);  // 带其编解码器
    public Supplier<String> text(String name);              // 任意策略的文本 ID，含 cosid80 与 uuidv7
    public PeriodicSegmentGenerator periodic(String name);
    public <T> T get(String name, Class<T> type);           // TimeBasedGenerator, SegmentGenerator, UuidV7Generator, PartsCodec, GroupedCodec, IntIdGenerator, ...
    public OptionalLong generated(String name);             // 需开启 countGenerated
    public Map<String, GeneratorInfo> describe();
    public Optional<SlotLease> slot();                      // 默认 machine's
    public Optional<SlotLease> slot(String machine);
    public Set<String> machines();                          // 已启动的 machine
    public IdFiller filler();
    public void close();
}
```

## Spec

每个 spec 都用 `builder(<必填项>)` 构造，其余项有默认值。

| Spec | 必填 | 可选（默认值） |
|---|---|---|
| `StoreSpec`：一个存储；`type` 选择 `StoreProvider` | `type` | `timeout`（2s）、`option(k, v)` |
| `MachineSpec.Leased`：从存储租用的槽位 | `store` | `bits`（10）、`ttl`（30s）、`tolerance`（1s）、`acquireTimeout`（等于 ttl）、`slotOffset`（0）、`preferredSlot`、`ownerKey`、`namespace`（运行时的命名空间） |
| `MachineSpec.Static`：由部署保证唯一的槽位 | `slot` | `bits`（10）、`stateFile`、`ahead`（10s）、`notBefore`、`namespace` |
| `GeneratorSpec.Snowflake`：时间型 `long` 生成器 | | `layout`（`SNOWFLAKE_MS`）、`regression`、`codec`、`machine`（`default`） |
| `GeneratorSpec.CosId80`：80 位 CosId，通过 `PartsCodecSpec` 输出文本 | | `layout`（`COSID_80`）、`regression`、`codec`（radix 62）、`machine` |
| `GeneratorSpec.Segment`：号段 | `store` | `batch`、`codec` |
| `GeneratorSpec.PeriodicSegment`：周期号段 | `store`、`period`、`zone` | `batch`、`periodic`、`codec` |
| `GeneratorSpec.Custom`：自己的 `long` 生成器，按名引用 | `reference` | `codec` |
| `GeneratorSpec.Instance`：自己的 `long` 生成器，直接给实例 | `ids` | `codec` |
| `GeneratorSpec.Disabled(spec)`：已声明但不创建；调用时抛出 `GeneratorDisabledException`（D24） | | |

`Custom` 与 `Instance` 对应 4.x 的 `IdGeneratorProvider.set(name, generator)`。`RuntimeOptions.generators` 解析 `Custom`
的引用（Spring 中是 Bean 名；找不到即启动失败）。运行时不拥有这类生成器：不关闭、不续约，但与其他生成器一样提供
（`generator`、`text`、`IdFiller`、策略为 `custom` 的 `describe()`、`countGenerated`、`Disabled`）。基于布局的编解码器（`friendly`）不能用于它。

machine：`machine(spec)` 是默认 machine；`machine(name, spec)` 增加一个[命名 machine](slot-lease.md#命名-machine)，
时间型生成器用 `machine(name)` 选择它。只有被启用的生成器选中的 machine 才会启动。

名为 **`default`** 的生成器即 4.x 的 *share*：在 Spring 中是主 bean，也是 cosid-server 的 `/ids` 在不带名字时使用的生成器。

## 启动与关闭

1. 校验所有内容，并一次性报告全部问题，每行一个。例如：未知的存储、`nodeBits <
   machine.bits`、存储超时大于某个号段的 `reserveTimeout`、时间型生成器没有 `machine`、生成器名包含 `/`。
2. 打开已启用的生成器或机器号所用的存储，且只打开这些。
3. 如果存在时间型生成器，获取槽位。
4. 创建生成器。

任何一步失败都会关闭已打开的资源。`close()` 按相反顺序执行：生成器停止（各自报告精确的最后时间戳），用它释放租约，
然后关闭存储以及运行时自己创建的执行器。

生成器集合在启动时确定（D17）。没有全局单例：一个 JVM 可以运行多个运行时，每个都有自己的 incarnation，互不可见。
要以编程方式增加、删除或修改生成器，请在 `start` 之前编辑 spec（在 Spring 中：一个 `RuntimeSpecCustomizer` bean）。

## 存储

```java
public interface StoreProvider {
    String type();                       // "jdbc"、"redis"、"mongo"、"zookeeper"、"remote" 或自定义
    Store open(StoreConfig config);
}
public interface Store extends AutoCloseable {
    RangeStore ranges();
    SlotLeaseStore slots();
}
public final class StoreConfig { String name(); String type(); Duration timeout(); Map<String, Object> options(); Optional<Object> client(); }
```

Provider 通过 `ServiceLoader` 发现。`RuntimeOptions.storeProviders`（在 Spring 中为 `StoreProvider` bean）可以添加更多 provider，
且对同一 type 优先生效。`client` 是一个已有的连接对象：`DataSource`、Lettuce 客户端或连接、`MongoClient`、`CuratorFramework`。
没有它时，provider 根据 `options` 自行构建并负责关闭。`timeout` 由每个存储原生实现（JDBC 查询超时、Redis 命令超时、
Mongo `timeoutMS`、Curator、HTTP）。

自定义后端实现这两个原语，并通过[存储 TCK](../backends/test-suite.md)。

## RuntimeOptions

| 选项 | 默认值 |
|---|---|
| `time` | `TimeSource.SYSTEM` |
| `executor` | 空：运行时创建一个虚拟线程执行器并负责关闭 |
| `listener` | `GeneratorListener.NOOP`：[指标](../operations/monitoring.md)所用的事件 |
| `storeProviders` | 除 `ServiceLoader` 之外没有其他 |
| `clients` | 按存储名提供的已有客户端对象 |
| `hostResolver`、`port` | 所有者 key：`host:port`，没有端口时为 `host:pid` |
| `codecs` | 解析 `custom` 编解码器；默认按类名解析 |
| `generators` | 解析 `custom` 生成器（`GeneratorSpec.Custom`）；默认没有 |
| `countGenerated` | 关闭：统计每个视图的 ID 数（`generated(name)`），每个 ID 有少量开销 |

## Java 类型的兼容性

从 RC1 起，公开类型只做增量变化（api-freeze D39/D40）：

- **spec、选项与 SPI 值类型是带 builder 的 final class**（`RuntimeSpec`、`StoreSpec`、`MachineSpec.*`、除 `Disabled` 外的
  `GeneratorSpec.*`、`BatchPolicy`、`PeriodicPolicy`、`RuntimeOptions`、`StoreConfig`、`SlotLease`、`AcquireResult.Acquired`、
  `GeneratorInfo`）。用 `builder(...)` 构造，用 `toBuilder()` 或 `with*` 修改，没有 public 构造器。之后的次版本只往 builder
  里加字段，默认值等于加字段之前的行为，从不往 `builder(...)` 加必填参数。
- **封闭的值保持 record**：`Range`、`IdParts`、`GroupedId`、`Owner`、`Layout`、`ClockRegressionPolicy`、编解码器规格
  （`CodecSpec.*`、`PartsCodecSpec.*`、`GroupedCodecSpec.*`）、`AcquireResult.Exhausted` 与 `GeneratorSpec.Disabled`。
  它们的构造器与记录模式都属于兼容承诺。
- **sealed 规格接口可在次版本新增变体**：`GeneratorSpec`、`MachineSpec`、`CodecSpec`、`PartsCodecSpec`、`GroupedCodecSpec`。
  对它们做 `switch` 时请保留 `default` 分支。`AcquireResult` 是封闭的。
- 不在承诺内：`internal` 包与 Spring 绑定类 `CosIdProperties`（承诺的是 `cosid.*` 属性键，不是 Java 类型）。

## 所有者 key

租用槽位的所有者 key 依次取：若设置了则为 `machine.leased.owner-key`；已知端口时为 `host:port`（在 Spring 中为 Web 服务器端口）；
否则为 `host:pid`。存在 Spring Cloud 的 `InetUtils` 时由它提供主机名。该 key 只用于粘性选槽和观测；唯一性不依赖它。

## describe()

`describe()` 取代了 4.x 的 Stat 类：给出每个生成器的策略、存储与细节（布局、槽位、窗口、批量大小、持有的 ID、周期……）。
Actuator 的 `cosid` 端点提供它。
