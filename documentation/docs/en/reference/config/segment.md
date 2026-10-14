# SegmentId Configuration

> `me.ahoo.cosid.spring.boot.starter.segment.SegmentIdProperties`, prefix `cosid.segment`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `boolean` | `false` | Register the segment generators |
| `mode` | `Mode` | `chain` | `chain` (`SegmentChainId`) or `segment` (`DefaultSegmentId`) |
| `ttl` | `long` | forever | Segment time to live, in seconds |
| `distributor` | `Distributor` | | The segment store |
| `chain` | `Chain` | | Segment chain settings |
| `share` | `IdDefinition` | | The share generator, `__share__SegmentId` |
| `share.enabled` | `boolean` | `true` | Register the share generator |
| `provider` | `Map<String, IdDefinition>` | empty | Named generators, `{name}SegmentId` |

## Distributor

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `distributor.type` | `String` | `redis` | `redis`, `jdbc`, `mongo`, `zookeeper`, `proxy`, or the `getType()` of an application `CosIdStoreProvider`. Compared by letters and digits, ignoring case |
| `distributor.redis.timeout` | `Duration` | `1s` | `redis`: each command; `0` disables it |
| `distributor.redis.wait-replicas` | `int` | `0` | `redis`: replicas that must acknowledge each increment (`WAIT`); `0` does not wait |
| `distributor.redis.wait-timeout` | `Duration` | `500ms` | `redis`: how long an increment waits for them; shorter than `timeout` |
| `distributor.jdbc.enable-auto-init-cosid-table` | `boolean` | `false` | `jdbc`: create the `cosid` table at startup |
| `distributor.jdbc.enable-auto-init-id-segment` | `boolean` | `true` | `jdbc`: insert a generator's row at startup when it is missing |
| `distributor.jdbc.init-cosid-table-sql` | `String` | MySQL DDL | `jdbc`: the table DDL |
| `distributor.jdbc.init-id-segment-sql` | `String` | MySQL | `jdbc`: inserts a generator's row |
| `distributor.jdbc.increment-max-id-sql` | `String` | MySQL | `jdbc`: adds the step to the counter |
| `distributor.jdbc.fetch-max-id-sql` | `String` | MySQL | `jdbc`: reads the counter |
| `distributor.jdbc.query-timeout` | `Duration` | `5s` | `jdbc`: each statement; `0` disables it |
| `distributor.mongo.database` | `String` | `cosid_db` | `mongo`: database of the `cosid` collection |
| `distributor.mongo.timeout` | `Duration` | `5s` | `mongo`: each operation (driver `timeoutMS`); `0` disables it |
| `distributor.mongo.write-concern` | `String` | | `mongo`: write concern of the writes; unset, the connection string's |
| `distributor.zookeeper.timeout` | `Duration` | `5s` | `zookeeper`: each `nextMaxId`; `0` disables it |

The PostgreSQL statements are on the [JDBC page](../../guide/backends/jdbc.md#postgresql). `cosid.segment.distributor.provider` was removed in 4.0: set `distributor.type` to the type of the provider instead.

## Chain

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `chain.safe-distance` | `int` | `2` | Segments the prefetch worker keeps ready ahead of the one in use |
| `chain.max-prefetch-distance` | `int` | `10000` | Most segments one prefetch may request when the chain is hungry; at least `safe-distance` |
| `chain.prefetch-timeout` | `Duration` | `0` | Bound of each background prefetch; `0` means none |
| `chain.prefetch-worker.prefetch-period` | `Duration` | `1s` | How often the worker tops up the chain |
| `chain.prefetch-worker.core-pool-size` | `int` | available processors | Threads of the prefetch workers |
| `chain.prefetch-worker.shutdown-hook` | `boolean` | `true` | Shut the prefetch workers down with the JVM |

## IdDefinition

The properties of `share` and of each `provider.{name}`:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `namespace` | `String` | `cosid.namespace` | Namespace of the generator's counter |
| `mode` | `Mode` | `cosid.segment.mode` | `chain` or `segment` |
| `offset` | `long` | `0` | Start value of a counter that does not exist yet |
| `step` | `long` | `10` | IDs per segment |
| `ttl` | `Long` | `cosid.segment.ttl` | Segment time to live, in seconds |
| `chain` | `Chain` | `cosid.segment.chain` | Chain settings of this generator: `safe-distance`, `max-prefetch-distance`, `prefetch-timeout` |
| `group.by` | `GroupBy` | `never` | `year`, `year_month`, `year_month_day` or `never`: restart the counter in every group |
| `group.pattern` | `String` | none | Date pattern of the group key, for example `yyyyMMdd` |
| `converter` | `IdConverterDefinition` | `radix` | Format of `generateAsString()`, see [ID converter](./basic.md#id-converter) |

A generator's `chain` replaces `cosid.segment.chain` as a whole. The prefetch workers are shared, so set `prefetch-worker` on `cosid.segment.chain`.

## Example

```yaml
cosid:
  namespace: ${spring.application.name}
  segment:
    enabled: true
    mode: chain
    chain:
      safe-distance: 4
      prefetch-worker:
        core-pool-size: 2
        prefetch-period: 1s
    distributor:
      type: redis
    share:
      step: 100
      converter:
        prefix: cosid_
        type: radix
        radix:
          char-size: 6
          pad-start: true
    provider:
      order:
        offset: 10000
        step: 100
      order_no:
        group:
          by: year_month_day
          pattern: yyyyMMdd
        converter:
          type: to_string
          prefix: BIZ-
          group-prefix:
            enabled: true
          to-string:
            pad-start: true
            char-size: 8
```
