# Choosing a Backend

CosId needs a store for two things: the counters of [segment IDs](../concepts/segment.md), and the [machine id](../concepts/machine-id.md) leases of Snowflake IDs and `CosIdGenerator`. Pick a store you already run and operate well; the generators perform the same on any of them, because generation happens in memory.

`cosid.segment.distributor.type` and `cosid.machine.distributor.type` select the store for each side, independently. Values are compared by letters and digits, ignoring case, so `zookeeper`, `ZooKeeper` and `zoo-keeper` are the same.

| Backend | `type` | Module | Segments | Machine ids | Lease clock | Choose it when |
|---------|--------|--------|:--------:|:-----------:|-------------|----------------|
| [Redis](./redis.md) | `redis` | `cosid-spring-redis` + Spring Data Redis | Yes | Yes | Redis server | You run Redis with persistence. The default segment store |
| [JDBC](./jdbc.md) | `jdbc` | `cosid-jdbc` + a `DataSource` | Yes | Yes | Database server | You have MySQL, MariaDB or PostgreSQL and want the counters in the database |
| [MongoDB](./mongo.md) | `mongo` | `cosid-mongo` + Spring Data MongoDB | Yes | Yes | MongoDB server | You run MongoDB |
| [ZooKeeper](./zookeeper.md) | `zookeeper` | `cosid-zookeeper` (Curator) | Yes | Yes | Host clock | You run ZooKeeper and keep host clocks in sync |
| [CosId Proxy](./proxy.md) | `proxy` | `cosid-proxy` + CoApi | Yes | Yes | The proxy's store | Applications must not reach the store directly |
| StatefulSet | `stateful_set` | none | No | Yes | none | Kubernetes StatefulSet pods: the pod ordinal is the machine id |
| Manual | `manual` | none | No | Yes | none | One instance, or tests |
| Your own | any | your `CosIdStoreProvider` bean | Your choice | Your choice | Your choice | A store CosId does not ship, such as etcd |

## What to look at

- **Durability.** A segment counter that goes back, because the store lost a write, hands out IDs a second time. Use a store that persists every write: Redis with AOF, a database, MongoDB with journaling, or ZooKeeper.
- **Clock.** Machine id leases are judged by the store's clock where it has one (Redis, databases, MongoDB). ZooKeeper has none, so its leases use the host clocks, which must then be in sync.
- **Availability.** A segment chain keeps IDs in reserve and a Snowflake generator needs the store only at startup and to renew its lease every minute, so a short store outage does not stop generation. With `cosid.machine.guarder.fail-fast` (the default), Snowflake generators and `CosIdGenerator` stop about `safe-guard-duration - min(10s, safe-guard-duration / 10)` after their last successful renewal, 4 minutes 50 seconds by default.
- **Network access.** The proxy keeps store credentials out of applications, at the cost of running and securing one more service. See [Proxy deployment security](./proxy.md#deployment-security).

The sides can use different stores, for example segments in the database and machine ids in Redis.

## A store CosId does not ship

Define a `CosIdStoreProvider` bean and set the type to its `getType()`. The provider creates the machine id distributor, the segment distributor factory, or both; for machine ids, implement a `MachineLeaseStore` and wrap it in `LeaseStoreMachineIdDistributor`, which brings the claim order, renewal and clock handling of the built-in stores:

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

Declare the bean with its concrete type (`EtcdStoreProvider`, not `CosIdStoreProvider`), in the application's configuration or in an auto-configuration ordered before CosId's. A provider with the type of a built-in backend replaces it. Run the [compatibility test suite](./test-suite.md) against your store.

## Ahead-of-time processing and native images

| Backend | Spring AOT processing | GraalVM native image |
|---------|-----------------------|----------------------|
| Redis | Not tested in CI | Not tested; runtime hints for its Lua scripts ship since 4.0.1 |
| JDBC | Not tested in CI | Not tested |
| MongoDB | Not tested in CI | Not tested |
| ZooKeeper | Not tested in CI | Not tested |
| Proxy | Supported with `cosid.proxy.client=http-interface` (tested in CI); not with the default CoApi clients | Not tested |

The starter registers segment and Snowflake generators as bean definitions that AOT processing can handle; CI runs AOT processing for segment generators, with an in-memory store. For applications on the proxy, set `cosid.proxy.client=http-interface`: the clients are then plain beans. `lb://` hosts (Spring Cloud LoadBalancer) need the default CoApi clients, so they run on the JVM only.
