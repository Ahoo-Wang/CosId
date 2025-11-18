# ZooKeeper Configuration

> `me.ahoo.cosid.spring.boot.starter.zookeeper.CosIdZookeeperProperties`

| Name                         | Data Type       | Description              | Default Value                      |
|----------------------------|------------|-----------------|--------------------------|
| enabled                    | `boolean`  | Whether to enable *ZooKeeper* | true                     |
| connect-string             | `String`   | Connection string           | `localhost:2181`         |
| block-until-connected-wait | `Duration` | Block until client is connected wait time  | `Duration.ofSeconds(10)` |
| session-timeout            | `Duration` | Session timeout          | `Duration.ofSeconds(60)`  |
| connection-timeout         | `Duration` | Connection timeout          | `Duration.ofSeconds(15)` |
| retry                      | `Retry`    | Retry policy configuration          |                          |

## Retry (`ExponentialBackoffRetry`) Configuration

| Name              | Data Type  | Description                | Default Value   |
|-----------------|-------|-------------------|-------|
| baseSleepTimeMs | `int` | Initial amount of time to wait between retries (milliseconds) | `100` |
| maxRetries      | `int` | Maximum number of retries            | `5`   |
| maxSleepMs      | `int` | Maximum sleep time between retries (milliseconds)  | `500` |

**YAML Configuration Example**

```yaml
cosid:
  zookeeper:
    enabled: true
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
