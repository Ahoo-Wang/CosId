# 运行时

`CosIdRuntime` 是唯一的注册中心，也是唯一的生命周期所有者。Spring Boot、cosid-server、迁移工具和手写代码都以同样的方式启动运行时：
一个 `RuntimeSpec`（配置）加一个 `RuntimeOptions`（配置之外的一切）。

```java
public record RuntimeSpec(String namespace,
                          Map<String, StoreSpec> stores,
                          Optional<MachineSpec> machine,
                          Map<String, GeneratorSpec> generators) {}

public final class CosIdRuntime implements AutoCloseable {
    public static CosIdRuntime start(RuntimeSpec spec, RuntimeOptions options);
    public IdGenerator generator(String name);              // snowflake、segment
    public StringIdGenerator stringGenerator(String name);  // 带其编解码器
    public PeriodicSegmentGenerator periodic(String name);
    public <T> T get(String name, Class<T> type);           // TimeBasedGenerator, SegmentGenerator, PartsCodec, GroupedCodec, IntIdGenerator, ...
    public Map<String, GeneratorInfo> describe();
    public Optional<SlotLease> slot();
    public IdFiller filler();
    public void close();
}
```

## Spec

| Spec | 描述 |
|---|---|
| `StoreSpec(type, timeout, options)` | 一个存储；`type` 选择 `StoreProvider` |
| `MachineSpec.Leased(store, bits, ttl, tolerance, preferredSlot, ownerKey)` | 从存储租用的槽位 |
| `MachineSpec.Static(bits, slot, stateFile, ahead, notBefore)` | 由部署保证唯一的槽位 |
| `GeneratorSpec.Snowflake(layout, regression, codec)` | 时间型 `long` 生成器 |
| `GeneratorSpec.CosId80(regression, codec)` | 80 位 CosId，通过 `PartsCodecSpec` 输出文本 |
| `GeneratorSpec.Segment(store, batch, codec)` | 号段 |
| `GeneratorSpec.PeriodicSegment(store, period, zone, batch, periodic, codec)` | 周期号段 |
| `GeneratorSpec.Disabled(spec)` | 已声明但不创建；调用时抛出 `GeneratorDisabledException`（D24） |

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
public record StoreConfig(String name, String type, Duration timeout, Map<String, Object> options, Optional<Object> client) {}
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
| `countGenerated` | 关闭：统计每个视图的 ID 数（`generated(name)`），每个 ID 有少量开销 |

## 所有者 key

租用槽位的所有者 key 依次取：若设置了则为 `machine.leased.owner-key`；已知端口时为 `host:port`（在 Spring 中为 Web 服务器端口）；
否则为 `host:pid`。存在 Spring Cloud 的 `InetUtils` 时由它提供主机名。该 key 只用于粘性选槽和观测；唯一性不依赖它。

## describe()

`describe()` 取代了 4.x 的 Stat 类：给出每个生成器的策略、存储与细节（布局、槽位、窗口、批量大小、持有的 ID、周期……）。
Actuator 的 `cosid` 端点提供它。
