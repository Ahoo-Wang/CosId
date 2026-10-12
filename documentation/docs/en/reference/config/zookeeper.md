# ZooKeeper Configuration

> `me.ahoo.cosid.spring.boot.starter.zookeeper.CosIdZookeeperProperties`, prefix `cosid.zookeeper`

Used when `cosid.machine.distributor.type` or `cosid.segment.distributor.type` is `zookeeper`, or when `cosid.zookeeper.enabled=true` is set explicitly. Left unset, the client is created only when ZooKeeper is selected; `false` turns it off. See [ZooKeeper](../../guide/backends/zookeeper.md).

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `boolean` | not set | `true` creates the Curator client even when no `type` selects ZooKeeper; `false` turns it off |
| `connect-string` | `String` | `localhost:2181` | ZooKeeper servers |
| `session-timeout` | `Duration` | `60s` | Session timeout |
| `connection-timeout` | `Duration` | `15s` | Connection timeout |
| `block-until-connected-wait` | `Duration` | `10s` | How long startup waits for the connection |
| `retry.base-sleep-time-ms` | `int` | `100` | `ExponentialBackoffRetry`: initial wait between retries, in milliseconds |
| `retry.max-retries` | `int` | `5` | Maximum retries |
| `retry.max-sleep-ms` | `int` | `500` | Maximum wait between retries, in milliseconds |

The per-call timeouts are `cosid.segment.distributor.zookeeper.timeout` and `cosid.machine.distributor.zookeeper.timeout`, both `5s`; `0` disables the segment one, and the machine one must be positive.

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
