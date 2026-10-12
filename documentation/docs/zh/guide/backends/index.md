# 选择后端

CosId 需要存储来做两件事：保存[号段 ID](../concepts/segment.md) 的计数器，以及保存 Snowflake ID 和 `CosIdGenerator` 的[机器号](../concepts/machine-id.md)租约。请选择你已经在运行、并且运维得好的存储；生成过程在内存中完成，所以无论用哪种存储，生成器的性能都一样。

`cosid.segment.distributor.type` 和 `cosid.machine.distributor.type` 分别为两部分选择存储，互不影响。取值只比较字母和数字、忽略大小写，因此 `zookeeper`、`ZooKeeper` 和 `zoo-keeper` 是同一个值。

| 后端 | `type` | 模块 | 号段 | 机器号 | 租约时钟 | 适用场景 |
|------|--------|------|:----:|:------:|----------|----------|
| [Redis](./redis.md) | `redis` | `cosid-spring-redis` + Spring Data Redis | 是 | 是 | Redis 服务器 | 已在运行开启了持久化的 Redis。号段的默认存储 |
| [JDBC](./jdbc.md) | `jdbc` | `cosid-jdbc` + `DataSource` | 是 | 是 | 数据库服务器 | 使用 MySQL、MariaDB 或 PostgreSQL，希望计数器保存在数据库中 |
| [MongoDB](./mongo.md) | `mongo` | `cosid-mongo` + Spring Data MongoDB | 是 | 是 | MongoDB 服务器 | 已在运行 MongoDB |
| [ZooKeeper](./zookeeper.md) | `zookeeper` | `cosid-zookeeper`（Curator） | 是 | 是 | 主机时钟 | 已在运行 ZooKeeper，并保持各主机时钟同步 |
| [CosId Proxy](./proxy.md) | `proxy` | `cosid-proxy` + CoApi | 是 | 是 | Proxy 所用存储 | 应用不能直接访问存储 |
| StatefulSet | `stateful_set` | 无 | 否 | 是 | 无 | Kubernetes StatefulSet 中的 Pod：Pod 序号即机器号 |
| Manual | `manual` | 无 | 否 | 是 | 无 | 单实例或测试 |
| 自定义 | 任意 | 你的 `CosIdStoreProvider` Bean | 自行决定 | 自行决定 | 自行决定 | CosId 未内置的存储，例如 etcd |

## 需要考虑什么

- **持久性。** 存储丢失写入后计数器回退，就会再次发放同样的 ID。请使用能持久化每次写入的存储：开启 AOF 的 Redis、数据库、开启日志（journaling）的 MongoDB，或 ZooKeeper。
- **时钟。** 存储有自己的时钟时（Redis、数据库、MongoDB），机器号租约按存储的时钟判断。ZooKeeper 没有可读取的服务器时钟，租约使用主机时钟，因此各主机时钟必须同步。
- **可用性。** 号段链会预留 ID，Snowflake 生成器只在启动时以及每分钟续约时需要存储，所以存储短暂不可用不会中断生成。开启 `cosid.machine.guarder.fail-fast`（默认）时，Snowflake 生成器和 `CosIdGenerator` 会在最后一次成功续约之后约 `safe-guard-duration - min(10s, safe-guard-duration / 10)`（默认 4 分 50 秒）停止生成。
- **网络访问。** Proxy 让应用不必持有存储的凭据，代价是多运行并保护一个服务。见 [Proxy 部署安全](./proxy.md#部署安全)。

两部分可以使用不同的存储，例如号段放在数据库，机器号放在 Redis。

## CosId 未内置的存储

定义一个 `CosIdStoreProvider` Bean，并把 `type` 设置为它的 `getType()`。Provider 可以创建机器号分配器、号段分配器工厂或两者；对于机器号，实现一个 `MachineLeaseStore` 并用 `LeaseStoreMachineIdDistributor` 包装，即可获得内置存储同样的申领顺序、续约和时钟处理：

```java
public class EtcdStoreProvider implements CosIdStoreProvider {
    private final MachineLeaseStore leaseStore;

    public EtcdStoreProvider(MachineLeaseStore leaseStore) {
        this.leaseStore = leaseStore;
    }

    @Override
    public String getType() {
        return "etcd";
    }

    @Override
    public MachineIdDistributor createMachineIdDistributor(MachineStoreContext context) {
        return new LeaseStoreMachineIdDistributor(leaseStore, context.getMachineStateStorage(), context.getClockBackwardsSynchronizer());
    }
}
```

请以具体类型（`EtcdStoreProvider`，而不是 `CosIdStoreProvider`）声明该 Bean，放在应用自己的配置中，或放在排在 CosId 之前的自动配置中。与内置后端同名的 Provider 会替换内置后端。请用[兼容性测试套件](./test-suite.md)验证你的存储。

## AOT 预处理与原生镜像

| 后端 | Spring AOT 预处理 | GraalVM 原生镜像 |
|------|------------------|-----------------|
| Redis | CI 未测试 | 未测试；4.0.1 起附带其 Lua 脚本的运行时提示 |
| JDBC | CI 未测试 | 未测试 |
| MongoDB | CI 未测试 | 未测试 |
| ZooKeeper | CI 未测试 | 未测试 |
| Proxy | **不支持**：无法生成 Proxy 客户端定义 | **不支持** |

starter 以 AOT 预处理能够处理的 Bean 定义方式注册号段和 Snowflake 生成器；CI 使用内存存储对号段生成器执行了 AOT 预处理。使用 Proxy 的应用请运行在 JVM 上。
