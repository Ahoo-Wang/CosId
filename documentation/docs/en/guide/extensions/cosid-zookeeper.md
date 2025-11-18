# CosId-ZooKeeper Module

The [cosid-zookeeper](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-zookeeper) module provides support for *ZooKeeper*. Implements:

- `MachineIdDistributor`: As the machine ID distributor for **Snowflake algorithm** (`SnowflakeId`).
- `IdSegmentDistributor`: As the segment distributor for **segment algorithm** (`SegmentId`).

## Installation

::: code-group
```kotlin [Gradle(Kotlin)]
    val cosidVersion = "latestVersion"
    implementation("me.ahoo.cosid:cosid-zookeeper:${cosidVersion}")
```
```xml [Maven]
    <dependencies>
        <dependency>
            <groupId>me.ahoo.cosid</groupId>
            <artifactId>cosid-zookeeper</artifactId>
            <version>${cosid.version}</version>
        </dependency>
    </dependencies>
```
:::

## Configuration

- Configuration class: [CosIdZookeeperProperties](https://github.com/Ahoo-Wang/CosId/blob/main/cosid-spring-boot-starter/src/main/java/me/ahoo/cosid/spring/boot/starter/zookeeper/CosIdZookeeperProperties.java)
- Prefix: `cosid.zookeeper.`

| Name                         | Data Type       | Description              | Default Value                      |
|------------------------------|-----------------|--------------------------|------------------------------------|
| enabled                      | `boolean`       | Whether to enable *ZooKeeper* | true                               |
| connect-string               | `String`        | Connection string        | `localhost:2181`                   |
| block-until-connected-wait   | `Duration`      | Block until client connected wait time | `Duration.ofSeconds(10)`          |
| session-timeout              | `Duration`      | Session timeout          | `Duration.ofSeconds(60)`           |
| connection-timeout           | `Duration`      | Connection timeout       | `Duration.ofSeconds(15)`           |
| retry                        | `Retry`         | Retry policy configuration |                                    |

### Retry (`ExponentialBackoffRetry`) Configuration

| Name              | Data Type  | Description                | Default Value   |
|-------------------|------------|----------------------------|-----------------|
| baseSleepTimeMs   | `int`      | Initial amount of time to wait between retries (milliseconds) | `100`           |
| maxRetries        | `int`      | Maximum number of retries  | `5`             |
| maxSleepMs        | `int`      | Maximum sleep time per retry (milliseconds) | `500`           |

### Configuration Example

[CosId-Example-Zookeeper](https://github.com/Ahoo-Wang/CosId/tree/main/examples/cosid-example-zookeeper)

```yaml {2-8,11,14}
cosid:
  zookeeper:
    connect-string: localhost:2181
    retry:
      base-sleep-time-ms: 100
      max-retries: 5
      max-sleep-ms: 500
    block-until-connected-wait: 10s
  segment:
    distributor:
      type: zookeeper
  machine:
    distributor:
      type: zookeeper
```
