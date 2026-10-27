---
name: cosid-strategy-guide
description: Choose a CosId 5 ID generation strategy for Java distributed systems. Use when the user compares snowflake, cosid80, segment and periodic-segment, or asks about id representation, layouts, ordering, clock sensitivity, JavaScript safety, which store (JDBC, Redis, MongoDB, ZooKeeper, cosid-server) to coordinate with, gaps, throughput, or production tradeoffs. Do not use for implementation-only requests after the strategy is fixed; use cosid-spring-boot or cosid-manual-integration instead.
---

# Choose a CosId Strategy

Recommend one strategy from explicit requirements. Do not default to the largest benchmark number.

## Workflow

1. Confirm the target CosId major version. In a CosId checkout, read `gradle.properties` (5.x on `main`, 4.x on the
   `4.x` branch). This skill describes **5.x**; 5.0 is not compatible with 4.x, and the offline `cosid-migration` tool
   is the only bridge.
2. Collect the id type, ordering scope, peak and burst rate, instance count, JavaScript exposure, acceptable gaps,
   available infrastructure, and outage behaviour.
3. Recommend one primary strategy. Add a fallback only when a real constraint creates a close tradeoff.
4. State the operational cost and the exact ordering guarantee.
5. Hand implementation to `$cosid-spring-boot` or `$cosid-manual-integration`.

## Decision Table

| Strategy | Output | Ordering | Coordination | Main constraint |
|---|---|---|---|---|
| `snowflake` | `long` (≤ 63 bits), text through a codec | locally monotonic; globally trend-ordered by time | a machine slot leased as a time window (`machine.leased`) or a static slot | `2^bits` concurrent instances; issuing pauses at the window ceiling when renewals fail |
| `cosid80` | 80-bit text (`PartsCodec`) | like snowflake | the same slot | text only, no `long` |
| `segment` | `long`, text through a codec | strictly increasing per instance; trend-ordered across instances | one atomic `reserve` per range | gaps on restart; callers wait (≤ `reserve-timeout`) when the store is down and the ids at hand run out |
| `periodic-segment` | `GroupedId(period, value)`, text through `GroupedCodec` | increasing within a period per instance | one key per period | the zone must be explicit; past periods may be sealed |

Every runtime has one slot shared by all its time-based generators (D18). Segments need no slot.

## Recommendation Rules

- `snowflake` (`SNOWFLAKE_MS`, the 4.x default layout) for a numeric primary key that encodes its creation time and
  needs no store call per id.
- `segment` with `initial-offset: 1` for dense numeric keys or business numbers; its batch size adapts to a time
  budget (`lookahead`, default 1s), so there is no step tuning in most cases.
- `periodic-segment` for numbers that restart every year, month or day (`20261027-000042`).
- `cosid80` only when more than 64 bits are needed.
- JavaScript: return ids as strings by default (Jackson `@AsString` in `cosid-jackson`). Use `layout: JS_SAFE_MS` (53
  bits, 3 node bits: at most 8 slots) only when a numeric JS-safe value is required.
- Store: reuse what the system already runs. JDBC or ZooKeeper where uniqueness must survive any failover. Redis only
  with AOF `appendfsync always` or `WAIT` on every replica. MongoDB needs a replica set. cosid-server when applications
  must not reach the store, or are not on the JVM.
- `machine.static` only when the deployment guarantees a unique slot (fixed numbers, StatefulSet ordinals), with a
  `state-file` on a persistent volume.

## Ordering and Availability

- No strategy gives strict global generation-time order across concurrent instances.
- **Uniqueness of time-based ids depends on no clock** (D22): the store keeps `next floor ≥ previous ceiling`, and a
  generator never issues outside its window. Skew costs availability only: a host ahead of the store by more than
  `tolerance` (1s) pauses near its ceiling, and a host behind it waits longer to pass a new floor.
- Clock regression on one host: up to `borrow-up-to` (5ms) is issued on the last time unit, up to `wait-up-to` (1s) is
  waited out, beyond that `ClockBackwardsException`.
- Segments allocate disjoint ranges: one instance can emit a higher range while another still emits a lower one.
  `range-ttl` bounds how stale a held range gets.
- Gaps are allowed, duplicates never (D19). There is no switch to keep issuing after a lost lease (P1).

If the requirement is strict global sequencing by request time, say that CosId does not provide it and recommend a
serialized authority such as a database sequence.

## Stable Facts to Use Carefully

- `Layout.SNOWFLAKE_MS`: 41 time / 10 node / 12 sequence bits, epoch `2019-12-24T16:00:00Z` (`1577203200000` ms).
- `SNOWFLAKE_S`: 31 / 10 / 22. `JS_SAFE_MS`: 41 / 3 / 9 (53 bits). `JS_SAFE_S`: 31 / 3 / 19. `COSID_80`: 44 / 20 / 16,
  Unix epoch.
- `machine.leased.bits` (default 10) must not exceed any time-based layout's node bits.
- Lease defaults: `ttl` 30s, renewed every `ttl/3`, `tolerance` 1s.
- Minimum store versions: MySQL 8.4, MariaDB 10.11, PostgreSQL 15, SQL Server 2019, Redis 7.0, MongoDB 6.0,
  ZooKeeper 3.8.

Verify these against the target version (`core/src/main/java/me/ahoo/cosid/time/Layout.java`) before emitting custom
configuration.

## Response Contract

Include:

1. one recommendation and the requirements it satisfies;
2. the precise ordering, gap, and failure semantics;
3. the store and machine-slot choice;
4. one focused validation or benchmark;
5. the correct implementation skill for the next step.
