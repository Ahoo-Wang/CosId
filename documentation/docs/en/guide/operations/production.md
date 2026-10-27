# Production Checklist

## Before going live

- [ ] **Pick the store for the guarantee you need.** JDBC or ZooKeeper where uniqueness must survive any failover.
  Redis only with [its durability precondition](../backends/redis.md#durability); MongoDB with `writeConcern: majority`
  (the default).
- [ ] **Meet the store's minimum version** (MySQL 8.4, MariaDB 10.11, PostgreSQL 15, SQL Server 2019, Redis 7.0,
  MongoDB 6.0 on a replica set, ZooKeeper 3.8). Older versions are [measured, not supported](../backends/index.md#supported-versions-and-measured-compatibility).
- [ ] **Create the schema** ahead of time (`schema-<dialect>.sql`). Keep `initSchema` for development.
- [ ] **One namespace per application**, the same on every instance. Instances that share a namespace share its
  slots and keys, and must use the same `bits` and `tolerance` (a mismatch is refused).
- [ ] **Size the machine bits**: at least the number of instances that can run at once, including a rolling update's
  surge. `bits ≤ layout.nodeBits` (10 for `SNOWFLAKE_*`, 3 for `JS_SAFE_*`).
- [ ] **Static slots** (`machine.static`): the deployment must keep slots unique, and `state-file` must be on a
  persistent volume. Without it there is no protection against a clock set back across a restart.
- [ ] **Store timeout ≤ `reserve-timeout`** of each segment (2s and 3s by default; start-up checks it).
- [ ] **Run NTP** on hosts and store. Skew never breaks uniqueness, but skew beyond `tolerance` pauses issuing near the
  window ceiling.
- [ ] **cosid-server**: tokens configured (auth is on by default), per-client namespaces, TLS in front of it, and more
  than one instance.
- [ ] **Scrape the metrics and alert** (below). Expose `/actuator/health` to the orchestrator.
- [ ] **Shut down gracefully** (SIGTERM, not SIGKILL). A clean close releases the slot with the exact last timestamp,
  and the next holder starts right after it. A kill only delays the slot's reuse by up to `ttl + tolerance`.

## Alerts worth having

| Signal | Meaning |
|---|---|
| health `cosId` `DOWN` | the slot lease was lost and not acquired again: time-based generators fail fast |
| `cosid.machine.lease.renew.failures` rising | the store is unreachable or slow; ids continue until the ceiling |
| `cosid.machine.window.remaining` near 0 | renewals are not getting through; issuing is about to pause |
| `cosid.machine.clock.skew` beyond `tolerance / 2` | fix NTP |
| `cosid.segment.refill.failures` rising | a store problem; callers get `RangeUnavailableException` once the ids at hand run out |
| `cosid.machine.owner.duplicates` > 0 | two live processes report the same owner key; set distinct `owner-key`s |
| `cosid.clock.backwards{action=fail}` | the clock went back by more than `wait-up-to` |

## Upgrades and restarts

5.x instances are stateless apart from their lease: rolling restarts need no coordination. A restarted instance usually
gets its previous slot back (sticky choice). With `preferred-slot` (a StatefulSet ordinal) it asks for that slot first.
During a surge the extra instances take other free slots.

Upgrading from 4.x is not a rolling upgrade: 4.x and 5.0 never share a namespace. Follow the
[migration guide](../migration/from-4x.md).
