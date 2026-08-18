---
name: cosid-sharding
description: Design, implement, and validate CosId database sharding with core ModCycle, IntervalTimeline, CachedSharding, and SnowflakeLocalDateTimeConvertor APIs or ShardingSphere COSID_MOD, COSID_INTERVAL, and COSID_INTERVAL_SNOWFLAKE algorithms. Use for modulo routing, date/time partitions, Snowflake timestamp routing, exact/IN/range behavior, effective nodes, suffix naming, or routing tests. Do not use for choosing an ID generator unless sharding is the primary concern.
---

# Design CosId Sharding

Treat core Java sharding APIs and ShardingSphere configuration as separate integration surfaces. Verify the target CosId and ShardingSphere versions before copying property names.

## Workflow

1. Identify the sharding key type and every query operator that must route: exact, `IN`, and range.
2. Define physical nodes, naming, bounds, and future partition provisioning before selecting an algorithm.
3. Choose `ModCycle` for fixed-count numeric distribution or `IntervalTimeline` for time partitions.
4. Add Snowflake conversion only when the ID layout is known and aligned with the parser.
5. Build a routing matrix that covers boundaries, out-of-range values, and full-range queries.

## Core Algorithms

| API | Use | Important behavior |
|---|---|---|
| `ModCycle<T>` | Fixed number of numeric nodes | Exact route is `value % divisor`; current implementation requires non-negative values |
| `IntervalTimeline` | Bounded `LocalDateTime` partitions | Exact values outside the effective interval throw `IllegalArgumentException` |
| `SnowflakeLocalDateTimeConvertor` | Convert Snowflake `Long` or radix-62 `String` to time | Parser must match the generator epoch and bit layout |
| `CachedSharding<T>` | Reuse identical range results | `@Beta` and backed by an unbounded cache; use only for demonstrably low-cardinality repeated ranges |

### Modulo Example

```java
ModCycle<Long> sharding = new ModCycle<>(4, "t_order_");

String exact = sharding.sharding(42L); // t_order_2
Collection<String> range = sharding.sharding(Range.closed(1L, 10L));
```

A range spanning at least `divisor` consecutive values routes to every node. Open and unbounded ranges must be included in tests. Do not pass negative values unless the implementation has been changed to use floor-mod semantics.

### Interval Example

```java
IntervalTimeline timeline = new IntervalTimeline(
    "t_order_",
    Range.closed(
        LocalDateTime.of(2024, 1, 1, 0, 0),
        LocalDateTime.of(2024, 12, 31, 23, 59, 59)
    ),
    IntervalStep.of(ChronoUnit.MONTHS),
    DateTimeFormatter.ofPattern("yyyyMM")
);

String exact = timeline.sharding(LocalDateTime.of(2024, 3, 15, 10, 30));
Collection<String> range = timeline.sharding(Range.closed(
    LocalDateTime.of(2024, 3, 1, 0, 0),
    LocalDateTime.of(2024, 4, 30, 23, 59, 59)
));
```

Supported `IntervalStep` units are years, months, days, hours, minutes, and seconds. Ensure the suffix format is unique for the selected step.

### Snowflake Conversion

```java
SnowflakeIdStateParser parser = SnowflakeIdStateParser.of(snowflakeId);
SnowflakeLocalDateTimeConvertor convertor =
    new SnowflakeLocalDateTimeConvertor(parser);

LocalDateTime time = convertor.toLocalDateTime(snowflakeId.generate());
```

The converter accepts only `Long` and radix-62 `String` values. It is not a general converter for arbitrary numeric types or custom string encodings.

## ShardingSphere

CosId algorithm availability and properties vary by ShardingSphere release. Inspect the target artifact or official source before emitting a type. When `COSID_MOD` is available, its divisor property is named `mod`, not `divisor`:

```yaml
rules:
  - !SHARDING
    tables:
      t_order:
        actualDataNodes: ds_0.t_order_$->{0..3}
        tableStrategy:
          standard:
            shardingColumn: order_id
            shardingAlgorithmName: t_order_mod
    shardingAlgorithms:
      t_order_mod:
        type: COSID_MOD
        props:
          mod: 4
          logic-name-prefix: t_order_
```

For ordinary time keys, configure an available `COSID_INTERVAL` implementation with `datetime-lower`, `datetime-upper`, `sharding-suffix-pattern`, `datetime-interval-unit`, and `datetime-interval-amount`.

Handle Snowflake interval routing by version:

- Prefer the core API shown above when application code owns routing; `SnowflakeIdStateParser.of(snowflakeId)` follows the actual generator's epoch and bit layout.
- In Apache ShardingSphere 5.4.0, `COSID_INTERVAL_SNOWFLAKE` reads only `epoch` and `zone-id` and hardcodes the default millisecond Snowflake layout. Use it only for that layout; `id-name` does not align a custom generator.
- For every other ShardingSphere release, verify that the CosId plugin and requested properties exist. Do not emit `COSID_INTERVAL_SNOWFLAKE` merely because an older document lists it.
- For a custom bit layout, use a version-verified integration that resolves the actual generator or a minimal `StandardShardingAlgorithm` that delegates to `SnowflakeIdStateParser` and `IntervalTimeline`.

Ensure `actualDataNodes` enumerates only valid physical suffixes; a daily numeric range such as `${20240101..20241231}` also generates impossible dates and must not be used.

## Validation Matrix

Test at least:

- first and last exact key for every boundary node;
- one `IN` set spanning multiple nodes;
- closed, open, unbounded, empty, and out-of-domain ranges;
- negative input rejection for `ModCycle`;
- an interval transition such as month-end or year-end;
- Snowflake values generated with the actual epoch, unit, and bit layout;
- equality between algorithm effective nodes and provisioned physical nodes.

## Response Contract

Return the chosen algorithm, physical naming model, minimal Java or version-matched ShardingSphere configuration, and the routing matrix. Call out any partition provisioning or cache-growth risk explicitly.
