# Why CosId

Every replica of a service needs to create IDs (primary keys, order numbers, event ids) that never collide with the IDs of the other replicas. A database auto-increment column stops working once the table is sharded or the ID is needed before the insert, and a random UUID is 128 bits and unordered, so a B+ tree index splits pages on every insert.

*[CosId](https://github.com/Ahoo-Wang/CosId)* generates such IDs inside your process, without a network call per ID, and handles the coordination that keeps them unique:

- **Snowflake IDs** need a distinct *machine id* for every running instance. CosId leases machine ids from a store you already run (Redis, a relational database, MongoDB, ZooKeeper, or the CosId proxy), renews the lease, and stops generating when the lease is lost, so two instances never share one.
- **Segment IDs** need a central counter. CosId takes a range of IDs (a *segment*) from the store at a time and prefetches the next ones in the background, so generation stays in memory.
- **Clocks go backwards.** CosId waits out a short clock jump, refuses to generate across a long one, and remembers the last timestamp of each machine id so that a restarted instance does not reuse it.

## The generators at a glance

| Generator | ID | Ordering | Needs | Pick it when |
|-----------|----|----------|-------|--------------|
| [`SegmentChainId`](./concepts/segment.md) | `long` | Monotonic per instance, near-sequential across instances | A segment store | You want short, near-sequential numbers such as primary keys or order numbers. The default segment mode |
| [`SnowflakeId`](./concepts/snowflake.md) | `long` (63 bits) | Time-ordered | A machine id per instance | You want IDs that carry their creation time, or no store call after startup |
| [`CosIdGenerator`](./concepts/cosid-generator.md) | `String`, 15 to 17 characters | Time-ordered | A machine id per instance | You run very many instances (up to about a million machine ids) and want string IDs |
| [`DefaultSegmentId`](./concepts/segment.md) | `long` | Monotonic per instance | A segment store | Rarely: a segment chain is faster and as easy to use |

[ID Generators](./concepts/id-generator.md) explains the choice in more detail.

## What a distributed ID has to get right

- **Uniqueness** within one kind of ID. IDs of `t_order` must not collide across replicas; whether they collide with IDs of `t_order_item` does not matter.
- **Ordering.** Ordered keys only append to a B+ tree index. Without a global clock, a distributed generator can be monotonic per instance and *trend increasing* across instances, which is what CosId's generators are.
- **Throughput and latency.** An ID is generated for every insert. Look at the tail latency (P99.99), not the average: a generator that blocks on a network call once per segment shows it there.
- **Autonomy and availability.** A generator that calls a store for every ID fails with the store. Segment chains keep IDs in reserve, and Snowflake IDs need the store only at startup and to renew the lease.
- **Size.** A 64-bit key is half the size of a UUID in every secondary index.

| Scheme | Unique | Ordering | Throughput (ops/s) | P99.99 (µs/op) | Depends on | Size |
|--------|:------:|----------|-------------------:|---------------:|------------|------|
| UUID | Yes | None | 3,078,638 | 0.325 | Nothing | 128 bits |
| SnowflakeId | Yes | Monotonic per instance, trend increasing (host clocks) | 4,096,000 (theoretical maximum, 4096 per ms) | 0.244 | Clock, machine id | 64 bits |
| DefaultSegmentId | Yes | Monotonic per instance, trend increasing (step) | 29,506,073 | 46.624 | Segment store | 64 bits |
| SegmentChainId | Yes | Monotonic per instance, trend increasing (step, safe distance) | 127,439,148 | 0.208 | Segment store, with IDs in reserve | 64 bits |

The numbers are historical JMH results of CosId 1.x on one machine, from the [comparison of ID schemes](../reference/performance/comparison.md); they show the relative cost of each scheme, not what your hardware reaches. See the [JMH benchmark](../reference/performance/benchmark.md) for how to run them.

### Monotonic and trend increasing

<p align="center">
  <img src="../../public/assets/design/monotonically-increasing.png" alt="Monotonically increasing"/>
</p>

*Monotonic*: if T<sub>n+1</sub> > T<sub>n</sub>, then F(T<sub>n+1</sub>) > F(T<sub>n</sub>). A database auto-increment key is monotonic, though not continuous: it may skip values.

<p align="center">
  <img src="../../public/assets/design/trend-increasing.png" alt="Trend increasing"/>
</p>

*Trend increasing*: IDs created later are larger in general, with some disorder inside a short window. For Snowflake IDs the window comes from the clock differences between hosts; for segment IDs, from the size of the segment each instance holds.

## Next steps

- [Getting Started](./getting-started.md): add CosId to a Spring Boot application.
- [Core Concepts](./concepts/id-generator.md): the generators, machine ids and converters.
