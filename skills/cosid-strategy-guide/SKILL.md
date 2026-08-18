---
name: cosid-strategy-guide
description: Choose a CosId ID generation strategy for Java distributed systems. Use when the user compares CosIdGenerator, SnowflakeId, SegmentId, or SegmentChainId, or asks about ID representation, ordering guarantees, clock sensitivity, JavaScript safety, coordination backends, gaps, throughput, or production tradeoffs. Do not use for implementation-only requests after the strategy is already fixed; use cosid-spring-boot or cosid-manual-integration instead.
---

# Choose a CosId Strategy

Recommend one strategy from explicit requirements. Do not default to the largest benchmark number.

## Workflow

1. Confirm the target CosId major version. In a CosId checkout, read `gradle.properties`; do not assume the current branch matches the user's application.
2. Collect the required ID type, ordering scope, peak and burst rate, instance count, JavaScript exposure, acceptable gaps, available infrastructure, and outage behavior.
3. Recommend one primary strategy. Add a fallback only when a real constraint creates a close tradeoff.
4. State the operational cost and the exact ordering guarantee.
5. Hand implementation to `$cosid-spring-boot` or `$cosid-manual-integration`.

## Decision Table

| Strategy | Output | Ordering | Coordination | Main constraint |
|---|---|---|---|---|
| `CosIdGenerator` | `String` only | Locally time-ordered; globally affected by clocks | Unique machine ID | Clock-sensitive; `generate()` is unsupported |
| `SnowflakeId` | `long` and converted `String` | Locally monotonic; globally trend-ordered | Unique machine ID | Clock-sensitive and bounded by bit allocation |
| `DefaultSegmentId` | `long` and converted `String` | Locally monotonic; globally trend-ordered by allocated ranges | `IdSegmentDistributor` at segment rollover | Allocation can stall or fail when the current segment is exhausted |
| `SegmentChainId` | `long` and converted `String` | Locally monotonic; globally trend-ordered by allocated ranges | `IdSegmentDistributor` plus background prefetch | More lifecycle work; gaps can grow with prefetched ranges |

Benchmark results are hardware- and workload-specific. Use them only as evidence that a strategy meets a class of demand, then benchmark the selected strategy with realistic concurrency and backend latency.

## Recommendation Rules

- Choose `SnowflakeId` for a numeric primary key that should roughly encode creation time and continue generating locally after machine-ID allocation.
- Choose `CosIdGenerator` when callers require compact structured strings and do not need a `long`. Its default 20 machine bits provide a larger instance space than default Snowflake.
- Choose `DefaultSegmentId` when local monotonicity matters, gaps are acceptable, and synchronous range allocation is operationally simple enough.
- Choose `SegmentChainId` when distributor latency at rollover matters enough to justify prefetching. Prefetch reduces stalls; it does not make the distributor irrelevant after reserved ranges run out.
- Return IDs as strings to JavaScript by default. Use `SafeJavaScriptSnowflakeId` only when a numeric JavaScript-safe value is an explicit requirement.
- Reuse Redis, JDBC, MongoDB, ZooKeeper, or proxy infrastructure already operated by the system. Do not add a backend solely because one benchmark is faster.
- Use manual or StatefulSet machine IDs only when instance identities and uniqueness are operationally guaranteed.

## Ordering and Availability

- No listed strategy provides strict global generation-time order across concurrent instances.
- Snowflake and CosIdGenerator depend on wall clocks; clock-sync wrappers wait for bounded rollback and fail when the configured broken threshold is exceeded.
- Segment generators allocate disjoint ranges. One instance can emit a higher range while another still emits a lower range.
- Segment restart or prefetch can leave unused IDs. Increase `step` or prefetch distance only after accepting that gap tradeoff.
- Manual duplicate machine IDs can produce duplicate Snowflake or CosIdGenerator IDs.

If the requirement is strict global sequencing by request time, say that CosId's distributed generators do not provide it and recommend a serialized authority such as a database sequence or dedicated sequencer.

## Stable Facts to Use Carefully

- Default millisecond Snowflake layout: 41 timestamp bits, 10 machine bits, 12 sequence bits; the sign bit is reserved.
- Default second Snowflake layout: 31 timestamp bits, 10 machine bits, 22 sequence bits.
- Snowflake bit allocation must total 63.
- Default epoch: `2019-12-24T16:00:00` UTC (`1577203200000` ms or `1577203200` s).
- Default CosIdGenerator layout is independent of Snowflake: 44 timestamp bits, 20 machine bits, and 16 sequence bits.

Verify these values against the target version before emitting custom configuration.

## Response Contract

Include:

1. one recommendation and the requirements it satisfies;
2. the precise ordering, gap, and failure semantics;
3. the machine-ID or segment-distributor choice;
4. one focused validation or benchmark;
5. the correct implementation skill for the next step.
