# Troubleshooting

## Start-up fails

| Message | Cause and fix |
|---|---|
| `Invalid cosid.* configuration:` followed by a list | every problem of the configuration at once, one per line: an unknown store, a missing `strategy`, a bad codec list, … |
| a time-based generator needs `machine` | add `cosid.machine.leased` (or `static`) |
| `nodeBits` smaller than `machine.bits` | the layout cannot hold the slot: lower `bits` (3 at most for `JS_SAFE_*`) or widen `node-bits` |
| store timeout above `reserve-timeout` | a caller that runs a refill itself is bounded by the store timeout: keep `timeout ≤ reserve-timeout` |
| `NamespaceMismatchException` | the namespace was first used with another `{limit, tolerance}`: use the same `bits` and `tolerance` everywhere, or another namespace |
| slots exhausted after `acquireTimeout` | every slot below `2^bits` is held: more instances than slots, or crashed holders whose leases have not expired yet (at most `ttl`) |
| bean name taken | a generator's name collides with another bean: rename one |
| cosid-server: no token configured | set `cosid.server.auth.tokens` or `clients`, or (only for trusted networks) `allow-anonymous: true` |

## While running

| Symptom | Cause and fix |
|---|---|
| `SlotWindowExceededException` | the generator reached its window ceiling without a renewal, because the store is unreachable or the host clock runs ahead of the store by more than `tolerance`. Check `cosid.machine.lease.renew.failures` and `cosid.machine.clock.skew` |
| `SlotLostException` | the lease was lost (expired during a long pause or partition, or taken after expiry). The keeper acquires a new slot by itself; health is `DOWN` until then |
| `ClockBackwardsException` | the clock went back by more than `regression.wait-up-to` (1s) |
| `RangeUnavailableException` | no ids at hand and the refill failed or took longer than `reserve-timeout`; see `cosid.segment.refill.failures` |
| `StoreTimeoutException` | a store call ran out of the store `timeout`; counted in `cosid.store.timeouts{operation}` (with `reserve-wait` for refill waits past `reserve-timeout`) |
| `RangeSealedException` | a periodic segment asked for a sealed past period: the host clock lags by more than `seal-after` |
| `RangeExhaustedException` | the key reached `Long.MAX_VALUE` |
| `GeneratorDisabledException` | the generator is `disabled: true` |
| the first id after start waits | a new holder waits until its clock passes the slot's floor: the previous holder's ceiling, or W right after a migration (at most about `source-skew`, 10s) |
| `onDuplicateOwnerKey` / `cosid.machine.owner.duplicates` | two live processes derive the same owner key (two runtimes on one host:port, or a copied pod): each gets its own slot, so ids stay unique, but set `owner-key` to tell them apart |
| gaps in segment ids | expected (D19): restarts, timeouts and `range-ttl` discard ranges; ids are never repeated |

## Redis

- `RedisStoreException` after a write with `WAIT`: fewer replicas acknowledged it within the timeout. The outcome is
  unknown, and the operation is retried. A lasting failure means a replica is down: either restore it, or lower
  `options.wait` knowingly.
- A shared `StatefulRedisConnection` client with replicas fails at open: `WAIT` must not run on a shared connection.
  Give the store a `RedisClient` instead.

## Getting more detail

- `GET /actuator/cosid` shows each generator's layout, slot, window, batch and ids held.
- `GET /actuator/health/cosId` shows the lease, the window left and the last renewal error.
- Set the logger `me.ahoo.cosid` to `DEBUG`.
