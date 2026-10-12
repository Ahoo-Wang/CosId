# Rolling Upgrade

Generators keep working while instances restart one by one, as long as each instance gets back a machine id no other live instance holds and segment counters only move forward. This page covers ordinary rolling restarts and the upgrade from 3.4 to 4.0.

## Rolling restarts

- **Machine ids.** A stopping instance releases its machine id after its web server has stopped, so the next instance can take it at once. An instance that restarts with the same instance id takes back its own lease; the default instance id is `host:PID`, so this needs a fixed `cosid.machine.instance-id` or `port`, or a stable PID. The lease of an instance that was killed, or failed during startup, becomes free only after `cosid.machine.guarder.safe-guard-duration` (5 minutes); a failed start does not release its lease yet (a fix is planned for 4.0.1), so repeated failed starts can use up the namespace. If the namespace has fewer free machine ids than instances starting meanwhile, startup fails with `MachineIdOverflowException`. Size `machine-bit` with room for a full rollout.
- **Clocks.** A new owner of a machine id waits until its clock passes the watermark the previous owner left. Keep host clocks in sync with each other and with the store's clock, or startup fails with `ClockTooManyBackwardsException` when the gap is longer than `cosid.machine.clock-backwards.broken-threshold` (500 ms).
- **Segments.** A restarted instance takes a new segment; the unused rest of its old segments is skipped. Nothing else is needed.
- **Configuration changes.** Change `safe-guard-duration` only when no instance with the old value runs in the namespace any more: while both values are in use, an instance with the shorter one can take over a lease its holder still considers valid. Do not change `machine-bit`, `epoch` or the timestamp and sequence bits of a running Snowflake generator: IDs issued under another layout can collide with new ones.

## Upgrading from 3.4 to 4.0

Upgrade to the latest 3.4.x first and clear its deprecation warnings; rolling upgrades are supported from 3.4.x only. 3.4.x and 4.0 instances can then share a namespace while the upgrade runs: 4.0 reads and writes both lease formats.

1. **Prepare the store.**

   | Backend | Before the first 4.0 instance starts |
   |---------|--------------------------------------|
   | JDBC | Add the lease columns (and `stable` on tables created before 3.3), see [JDBC](../backends/jdbc.md#upgrading-a-3-x-table). 4.0 fails at startup, printing the statement, when they are missing |
   | Redis | Nothing: 4.0 adds a `cosid:{namespace}:lease` hash next to the 3.4 keys |
   | ZooKeeper, MongoDB | Nothing: the nodes and documents keep the 3.4 format |
   | Proxy | Set `cosid.proxy.token` on the 3.4.x clients first (3.4 sends it, and a server without tokens ignores it); then upgrade the proxy servers with the same token in `cosid.proxy.server.auth.tokens`; then upgrade the clients. Upgrading the servers first gives 3.4 clients without a token `401`, see [CosId Proxy](../backends/proxy.md#upgrading-from-3-x) |

2. **Check the configuration** of the 4.0 instances against [Migrating to 4.0](../migration/4.0.md): the same `safe-guard-duration` as the 3.4 instances, `cosid.jdbc.enabled=true` for Spring Data JDBC entities, positive lease store timeouts. With the proxy, the server's `cosid.proxy.server.min-safe-guard-duration` (5 minutes by default) must not exceed the clients' `safe-guard-duration`, or 4.0 clients get `400` when they claim an expired machine id.
3. **Replace instances one by one.** Do not run a 3.4 and a 4.0 instance with the same instance id at the same time: they could, rarely, write the same lease version.
4. **Finish the upgrade** before removing 3.x support from the store, for example before dropping the 3.4 Redis keys.

4.0 judges expiry on the timestamp 3.4 reads, and a new owner starts after it, so 4.0 never takes a machine id earlier than 3.4 would.
