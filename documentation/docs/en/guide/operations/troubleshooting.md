# Troubleshooting

## Startup errors

| Error message contains | Cause and fix |
|------------------------|---------------|
| `Table cosid_machine has no lease columns` | A 3.x table: add the lease columns, see [JDBC](../backends/jdbc.md#upgrading-a-3-x-table) |
| `Table cosid_machine has no stable column` | A table created before 3.3: add the `stable` column, see [JDBC](../backends/jdbc.md#upgrading-a-3-x-table) |
| `[cosid.machine.distributor.provider] was removed in 4.0` (or `cosid.segment.distributor.provider`) | Set the `type` the message names instead; it also selects your `CosIdStoreProvider` by its `getType()` |
| `must be positive: the machine id lease store holds a lock during each call` | Set the named timeout (`cosid.machine.distributor.redis.timeout`, `redis.distribute-timeout`, `zookeeper.timeout`, `mongo.timeout`, `cosid.proxy.read-timeout`) to a positive duration |
| `CosId's MongoDB reactive support was removed in 4.0` | Add `org.mongodb:mongodb-driver-sync`, see [MongoDB](../backends/mongo.md) |
| `has converter.group-prefix enabled but no group` | Set `group.by` on that segment generator, or turn `converter.group-prefix.enabled` off |
| `MachineIdOverflowException` | Every machine id of the namespace is held by a live or stable instance, or by a lease not yet expired. Wait for the safe guard duration after killed instances (a crash loop can use up the namespace; failed starts release their lease since 4.0.1), raise `cosid.machine.machine-bit`, or check for instances that use the namespace by mistake |
| `ClockTooManyBackwardsException` | The host clock is behind the machine id's watermark by more than `broken-threshold` (500 ms): the previous owner's clock ran ahead. Fix clock synchronization; the machine id is released again |
| `cosid.machine.distributor.manual.machineId can not be null` | `manual` needs `cosid.machine.distributor.manual.machine-id`; or choose a lease store |
| A bean of type `InstanceId` or `GuardDistribute` could not be found | Snowflake IDs or `CosIdGenerator` are enabled without `cosid.machine.enabled=true` |
| `No [cosid.proxy.server.auth.tokens] configured` (proxy server) | Set `cosid.proxy.server.auth.tokens` (the distribution reads `COSID_PROXY_TOKEN`), or `allow-anonymous=true` behind a trusted network |

## Errors while running

| Exception | Meaning | What to do |
|-----------|---------|------------|
| `MachineIdLostException` | The machine id lease is lost (another instance owns it now), was released on shutdown, or has not been renewed for about the safe guard duration; generators stop so that no duplicate is issued | Check the `machineId` health indicator and the `Guard Failed` errors in the log: usually the store was unreachable for minutes. An expired lease recovers by itself after the next successful renewal; a lost one does not, so restart the instance to claim a machine id again |
| `ClockBackwardsException` | The clock moved back while a generator with `clock-sync: false` was running; it refuses to generate rather than repeat an ID | Fix clock synchronization, or keep `clock-sync: true` |
| `ClockTooManyBackwardsException` | The clock jumped back by more than `broken-threshold` while running | Fix clock synchronization (slew rather than step the clock) |
| `IdSegmentRollbackException` | The store returned a segment below one already issued: the counter went back, for example after Redis lost data | Stop and restore the counter above the highest issued ID before generating again |
| `NotFoundIdGeneratorException` | No generator is registered under the name | Check the name in `@CosId` or the lookup, and that the generator is configured under `cosid.segment.provider` or `cosid.snowflake.provider` |
| `IllegalArgumentException` naming two types on a lookup | A typed lookup asked for a `long` generator under a name that holds a string-only one (such as `cosid`) | Use `getRequired(name)` and `generateAsString()`, or a `long` generator |
| `CosIdException` naming a field and a generator | A `long` or `int` field is bound to a string-only generator | Bind the field to a segment or Snowflake generator |

## Suspected duplicate IDs

Duplicates come from coordination, not from the generators. Check, in order:

1. **Two instances with one machine id**: `manual` or `stateful_set` values assigned twice, or a namespace whose instances use different `safe-guard-duration` values.
2. **Clocks**: hosts out of sync with ZooKeeper leases, or an instance killed and restarted right after a large clock jump.
3. **Layouts**: a generator whose `epoch`, `machine-bit` or other bits changed while IDs from the old layout are still in the table.
4. **Segment counters**: a store that lost writes (Redis without persistence), or an `offset` set below IDs already issued.
5. **Namespaces and names**: two applications writing the same table must share one counter, so the same namespace and generator name; applications writing different tables should use different namespaces or generator names.

`GET /actuator/cosid` shows each generator's machine id, last timestamp and segment state, which tells most of these apart.
