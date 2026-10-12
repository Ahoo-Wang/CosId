# ZooKeeper

[`cosid-zookeeper`](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-zookeeper) stores segment counters and machine id leases in ZooKeeper, through Apache Curator:

- `ZookeeperIdSegmentDistributor`: segment counters, as Curator distributed atomic longs.
- `ZookeeperMachineLeaseStore`: machine id leases, claimed and released with versioned transactions.

ZooKeeper has no server clock that clients can read, so lease times come from the host clocks. **Keep the hosts' clocks in sync** (NTP): an instance whose clock runs ahead can see another instance's lease as expired before it is.

The starter connects to ZooKeeper only when a `type` selects it.

## Installation

::: code-group
```kotlin [Gradle (Kotlin)]
dependencies {
    implementation(platform("me.ahoo.cosid:cosid-bom:$cosidVersion"))
    implementation("me.ahoo.cosid:cosid-spring-boot-starter")
    implementation("me.ahoo.cosid:cosid-zookeeper")
}
```
```xml [Maven]
<dependencies>
    <dependency>
        <groupId>me.ahoo.cosid</groupId>
        <artifactId>cosid-spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>me.ahoo.cosid</groupId>
        <artifactId>cosid-zookeeper</artifactId>
    </dependency>
</dependencies>
```
:::

`cosid-zookeeper` brings `curator-recipes`.

## Configuration

```yaml
cosid:
  namespace: ${spring.application.name}
  zookeeper:
    connect-string: localhost:2181
  machine:
    enabled: true # for Snowflake IDs and CosIdGenerator
    distributor:
      type: zookeeper
  segment:
    enabled: true # for segment IDs
    distributor:
      type: zookeeper
```

| Property | Default | Description |
|----------|---------|-------------|
| `cosid.zookeeper.enabled` | `true` | Create the Curator client when ZooKeeper is selected |
| `cosid.zookeeper.connect-string` | `localhost:2181` | ZooKeeper servers |
| `cosid.zookeeper.session-timeout` | `60s` | Session timeout |
| `cosid.zookeeper.connection-timeout` | `15s` | Connection timeout |
| `cosid.zookeeper.block-until-connected-wait` | `10s` | How long startup waits for the connection |
| `cosid.zookeeper.retry.base-sleep-time-ms` | `100` | `ExponentialBackoffRetry`: initial wait between retries, in milliseconds |
| `cosid.zookeeper.retry.max-retries` | `5` | Maximum retries |
| `cosid.zookeeper.retry.max-sleep-ms` | `500` | Maximum wait between retries, in milliseconds |
| `cosid.segment.distributor.zookeeper.timeout` | `5s` | Each segment `nextMaxId` |
| `cosid.machine.distributor.zookeeper.timeout` | `5s` | Lease renewal and release (Curator has no per-operation timeout); must be positive |

An application can define its own `CuratorFramework` bean instead. See [CosId-Example-Zookeeper](https://github.com/Ahoo-Wang/CosId/tree/main/examples/cosid-example-zookeeper) for a runnable application.
