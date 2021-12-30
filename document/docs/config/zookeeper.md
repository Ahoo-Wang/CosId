# ZooKeeper 配置

> `me.ahoo.cosid.spring.boot.starter.zookeeper.CosIdZookeeperProperties`

| 名称                         | 数据类型       | 说明              | 默认值                      |
|----------------------------|------------|-----------------|--------------------------|
| enabled                    | `boolean`  | 是否开启*ZooKeeper* | true                     |
| connect-string             | `String`   | 链接字符串           | `localhost:2181`         |
| block-until-connected-wait | `Duration` | 阻塞直到客户端已连接等待时间  | `Duration.ofSeconds(10)` |
| session-timeout            | `Duration` | 会话超时时间          | `Duration.ofSeconds(60`  |
| connection-timeout         | `Duration` | 连接超时时间          | `Duration.ofSeconds(15)` |
| retry                      | `Retry`    | 重试策略配置          |                          |

## Retry (`ExponentialBackoffRetry`) 配置

| 名称              | 数据类型  | 说明                | 默认值   |
|-----------------|-------|-------------------|-------|
| baseSleepTimeMs | `int` | 重试之间等待的初始时间量 （毫秒） | `100` |
| maxRetries      | `int` | 最大重试次数            | `5`   |
| maxSleepMs      | `int` | 每次重试时的最大睡眠时间（毫秒）  | `500` |

**YAML 配置样例**

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
  snowflake:
    machine:
      distributor:
        type: zookeeper
```
