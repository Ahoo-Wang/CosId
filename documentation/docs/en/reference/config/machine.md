# Machine ID Configuration

> `me.ahoo.cosid.spring.boot.starter.machine.MachineProperties`, prefix `cosid.machine`

The machine id is needed by Snowflake IDs and `CosIdGenerator`. See [Machine ID](../../guide/concepts/machine-id.md) for how leases work.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `boolean` | `false` | Distribute a machine id. Required for `cosid.snowflake` and `cosid.generator` |
| `instance-id` | `String` | `{host}:{pid}:{random}` (non-stable), `{host}:{pid}` (stable), or `{host}:{port}` when `port` is set | The instance's identity in the lease store. Set it, with `stable`, for an instance that keeps its identity across restarts |
| `port` | `Integer` | not set | With it, the default instance id is `{host}:{port}`, without a random suffix |
| `stable` | `Boolean` | not set (`false`) | A stable instance's machine id is never recycled by others, and it never takes over another instance's expired lease. It also keeps its machine state in a local file |
| `machine-bit` | `int` | `10` | Machine id bits; a generator can override it with its own `machine-bit` |
| `state-storage.local.state-location` | `String` | `~/.cosid-machine-state` | Directory of the local machine state of a stable instance |

::: tip Stable instances and upgrades
The stable flag protects a machine id only once every instance runs 3.3 or later: older instances do not read it. A stable instance that is already offline is unprotected until it has started once on the new version, which records the flag. JDBC needs the `stable` column, see [JDBC](../../guide/backends/jdbc.md#upgrading-a-3-x-table).
:::

## Distributor

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `distributor.type` | `String` | `manual` | `manual`, `stateful_set`, `redis`, `jdbc`, `mongo`, `zookeeper`, `proxy`, or the `getType()` of an application `CosIdStoreProvider`. Compared by letters and digits, ignoring case, so `stateful-set` and `statefulSet` work too |
| `distributor.manual.machine-id` | `Integer` | none | `manual`: the machine id; required |
| `distributor.redis.timeout` | `Duration` | `1s` | `redis`: lease renewal and release; must be positive |
| `distributor.redis.distribute-timeout` | `Duration` | `10s` | `redis`: each command of the claim at startup, whose first command also pays for connecting; must be positive |
| `distributor.jdbc.enable-auto-init-cosid-machine-table` | `boolean` | `false` | `jdbc`: create the `cosid_machine` table at startup |
| `distributor.jdbc.init-cos-id-machine-table-sql` | `String` | MySQL DDL | `jdbc`: the table DDL |
| `distributor.jdbc.init-namespace-idx-sql`, `init-instance-id-idx-sql` | `String` | empty | `jdbc`: separate index DDL, skipped when empty (the MySQL DDL declares the indexes inline) |
| `distributor.jdbc.query-timeout` | `Duration` | `5s` | `jdbc`: each statement; `0` disables it |
| `distributor.jdbc.server-time-sql` | `String` | detected | `jdbc`: the query for the database clock in epoch milliseconds. Unset, the starter picks it for MySQL, MariaDB and PostgreSQL; other databases must set it |
| `distributor.mongo.database` | `String` | `cosid_db` | `mongo`: database of the `cosid_machine` collection |
| `distributor.mongo.timeout` | `Duration` | `5s` | `mongo`: each operation (driver `timeoutMS`); must be positive |
| `distributor.zookeeper.timeout` | `Duration` | `5s` | `zookeeper`: lease renewal and release (Curator has no per-operation timeout); must be positive |

The proxy client's timeouts are on the [proxy page](./proxy.md). A timeout that must be positive fails startup when set to `0`.

`cosid.machine.distributor.provider` was removed in 4.0: set `distributor.type` to the type of the provider instead.

## Guarder

The guarder renews the machine id lease. Prefix `cosid.machine.guarder`.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `boolean` | `true` | Renew the lease. With `false`, this instance claims without expiry and never renews, so instances that also run without the guarder never recycle its machine id. Any instance of the namespace that runs the guarder takes it over once its safe guard duration has passed, and both then generate the same ids: disable the guarder on every instance of a namespace, or on none. Startup logs a warning when a lease store runs without the guarder |
| `initial-delay` | `Duration` | `1m` | Delay before the first renewal |
| `delay` | `Duration` | `1m` | Delay between renewals |
| `safe-guard-duration` | `Duration` | `5m` | A lease not renewed for this long may be taken over by another instance. **Use the same value on every instance of a namespace** |
| `fail-fast` | `boolean` | `true` | Generators stop with `MachineIdLostException` once the lease is lost, or not renewed for `safe-guard-duration` minus `min(10s, safe-guard-duration / 10)` |
| `local-state-interval` | `Duration` | `1s` | How often a stable instance writes its last timestamp to the local state file, the clock-backwards check after a restart. After a crash, a clock jump shorter than about this interval may go undetected. `0` disables it |

## Clock backwards

Prefix `cosid.machine.clock-backwards`. Used by `ClockSyncSnowflakeId`, `CosIdGenerator` and the startup check against the lease watermark.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `spin-threshold` | `int` | `1` | Clock jumps up to this many milliseconds are waited out by spinning |
| `broken-threshold` | `int` | `500` | Jumps up to this many milliseconds are waited out by sleeping; longer ones throw `ClockTooManyBackwardsException`. Must be greater than `spin-threshold` |

## Example

```yaml
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true
    distributor:
      type: jdbc
    guarder:
      safe-guard-duration: 5m
```
