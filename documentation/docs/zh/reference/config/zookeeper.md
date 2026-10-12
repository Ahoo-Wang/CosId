# ZooKeeper 配置

> `me.ahoo.cosid.spring.boot.starter.zookeeper.CosIdZookeeperProperties`，前缀 `cosid.zookeeper`

`cosid.machine.distributor.type` 或 `cosid.segment.distributor.type` 为 `zookeeper` 时生效；否则 Starter 不会连接 ZooKeeper。见 [ZooKeeper](../../guide/backends/zookeeper.md)。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enabled` | `boolean` | `true` | 选择 ZooKeeper 时创建 Curator 客户端 |
| `connect-string` | `String` | `localhost:2181` | ZooKeeper 服务器地址 |
| `session-timeout` | `Duration` | `60s` | 会话超时 |
| `connection-timeout` | `Duration` | `15s` | 连接超时 |
| `block-until-connected-wait` | `Duration` | `10s` | 启动时等待连接建立的时长 |
| `retry.base-sleep-time-ms` | `int` | `100` | `ExponentialBackoffRetry`：首次重试前的等待时间（毫秒） |
| `retry.max-retries` | `int` | `5` | 最大重试次数 |
| `retry.max-sleep-ms` | `int` | `500` | 两次重试之间的最长等待时间（毫秒） |

单次调用的超时由 `cosid.segment.distributor.zookeeper.timeout` 和 `cosid.machine.distributor.zookeeper.timeout` 控制，默认均为 `5s`。

```yaml
cosid:
  zookeeper:
    connect-string: localhost:2181
    retry:
      base-sleep-time-ms: 100
      max-retries: 5
      max-sleep-ms: 500
  segment:
    distributor:
      type: zookeeper
  machine:
    distributor:
      type: zookeeper
```
