# Why CosId

CosId is a distributed ID generator for the JVM: it hands out ids that are unique across every instance of a service
(and [across services](concepts/slot-lease.md#ids-unique-across-services) that share a namespace for them),
fast enough that generating an id never shows up in a profile, and in the shape the application wants (a `long`, a
short base-62 text, a readable time-based text, a per-day bill number).

This site describes **CosId 5**. 5.0 is a full rewrite with no backward compatibility; the offline
[migration tool](migration/from-4x.md) moves a 4.x deployment over.

| Line | Branch | Java / Spring Boot | Documentation |
|---|---|---|---|
| 5.x | `main` (the default branch) | 21+ / 4.0+ | this site |
| 4.x | `4.x` (maintenance: bug fixes only) | 17+ / 4.x | the site of the 4.x release tags |

## Two ways to be unique

Every distributed id is unique for one of two reasons, and CosId offers both:

| Source | How | Coordination | CosId strategies |
|---|---|---|---|
| **Space partitioning** | pack `(time, slot, sequence)` into the bits of the id | an exclusive lease on a slot for a **time window** | `snowflake`, `cosid80` |
| **Range reservation** | atomically take `[start, end)` from a counter, then hand it out locally | one atomic increment per range | `segment`, `periodic-segment` |

Everything else (encoding, buffering, retries, lease keeping) is local code in `cosid-core`. A store only implements two
primitives, `RangeStore.reserve` and the three `SlotLeaseStore` operations, and one test suite (the TCK) verifies every
store the same way.

## What you can choose

| You want | Use |
|---|---|
| sortable 63-bit ids with no store round trip per id | `snowflake` on a leased (or static) machine slot |
| ids a JavaScript client can hold as a number | `snowflake` with `layout: JS_SAFE_MS` (53 bits) |
| short, dense, strictly increasing ids per key | `segment` |
| numbers that restart every year, month or day (`20261027-000042`) | `periodic-segment` |
| more than 64 bits, as text | `cosid80` (44-bit milliseconds, 20-bit node, 16-bit sequence) |
| ids for a client that is not on the JVM, or cannot reach a store | [cosid-server](server.md)'s optional id endpoints |

The [recipes](recipes.md) give the configuration of each.

## What 5.0 guarantees

- **Uniqueness depends on no clock.** A machine slot is leased as a time window `(floor, ceiling]`; the store keeps
  `next floor ≥ previous ceiling`, and a generator never issues a timestamp outside its window. Clocks only decide
  *when* a lease expires, which affects availability. See [the slot lease](concepts/slot-lease.md).
- **Gaps are allowed, duplicates never.** Every failure path (a timeout, a crash, a discarded range) can only lose ids.
- **No global state.** Time source, executor, listeners and stores are injected; a JVM may run several independent
  runtimes.
- **Performance and features not below 4.x** (D21): every 4.x capability has a 5.0 counterpart, and each benchmark
  scenario is compared with 4.1.0 as a release gate ([benchmarks](../reference/performance/benchmark.md)).

## Core metrics of an id scheme

- **Uniqueness** within a business key is the one property that cannot be traded.
- **Order**: B+ tree indexes append cheaply when ids increase; random ids split pages. Snowflake ids are trend
  increasing (by time across instances); a segment is strictly increasing within one instance.
- **Throughput and stability**: ids are generated on every write, so both the rate and the tail latency (p99, p99.9)
  matter. CosId keeps locks and I/O off the hot path.
- **Autonomy and availability**: a segment needs its store to refill (prefetching hides it), a Snowflake needs its
  slot lease renewed (one write every `ttl/3`) and a sane clock.

<p align="center">
  <img src="../../public/assets/design/monotonically-increasing.png" alt="Monotonically increasing"/>
</p>

<p align="center">
  <img src="../../public/assets/design/trend-increasing.png" alt="Trend increasing"/>
</p>

## Modules

| Artifact | Contents |
|---|---|
| `cosid-core` | generators, codecs, leases, `CosIdRuntime`, `@CosId` filling; depends on `slf4j-api` only |
| `cosid-store-jdbc`, `-redis`, `-mongo`, `-zookeeper`, `-remote` | the stores |
| `cosid-spring-boot-starter` | Spring Boot (core + autoconfigure); add the store artifacts you use |
| `cosid-jackson`, `cosid-mybatis`, `cosid-spring-data-jdbc`, `cosid-activiti`, `cosid-flowable`, `cosid-axon` | the integrations, one artifact per library (D29) |
| `cosid-sharding` | sharding algorithms (`ModCycle`, `IntervalTimeline`) |
| `cosid-store-tck` | the store contracts and an in-memory store, for tests |
| `cosid-bom` | versions of all of the above |
| cosid-server (Docker image) | the remote store service; [cosid-server](server.md) |
| `cosid-migration.jar` | the offline 4.x → 5.0 tool |
