---
name: cosid-sharding
description: Design, implement, and validate database sharding with the cosid-sharding module (ModCycle, IntervalTimeline, CachedSharding, ShardingRange, StandardLocalDateTimeConvertor, SnowflakeLocalDateTimeConvertor on a Layout) or ShardingSphere's COSID_MOD, COSID_INTERVAL and COSID_INTERVAL_SNOWFLAKE algorithms. Use for modulo routing, date/time partitions, Snowflake timestamp routing, exact/IN/range behavior, effective nodes, suffix naming, or routing tests. Do not use for choosing an ID generator unless sharding is the primary concern.
---

# Design CosId Sharding

Treat the core Java sharding API and ShardingSphere configuration as separate integration surfaces. The algorithms
live in `me.ahoo.cosid:cosid-sharding` (package `me.ahoo.cosid.sharding`), released with CosId and depending on
`cosid-core` only (D7). In CosId 5 a range is a `ShardingRange` (Guava's `Range` is gone; the factory names are the
same), and the Snowflake convertor takes a `Layout`. Verify the target CosId and ShardingSphere versions before copying
property names.

## Workflow

1. Identify the sharding key type and every query operator that must route: exact, `IN`, and range.
2. Define physical nodes, naming, bounds, and future partition provisioning before selecting an algorithm.
3. Choose `ModCycle` for fixed-count numeric distribution or `IntervalTimeline` for time partitions.
4. Add Snowflake conversion only when the id `Layout` (epoch, unit, bits) is known.
5. Build a routing matrix that covers boundaries, out-of-range values, and full-range queries.

## Core Algorithms

| API | Use | Important behavior |
|---|---|---|
| `ModCycle<T extends Number>` | fixed number of numeric nodes | exact route is `floorMod(value, divisor)` → `{prefix}{index}`; a range spanning `divisor` values or unbounded above routes to every node |
| `IntervalTimeline` | bounded `LocalDateTime` partitions | exact values outside the effective interval throw `IllegalArgumentException`; a range not connected to it routes to no node |
| `StandardLocalDateTimeConvertor(zone, isSecondTs, formatter)` | column values to `LocalDateTime` | `LocalDateTime`, `ZonedDateTime`, `OffsetDateTime`, `Instant`, `LocalDate`, `Date`, `YearMonth`, `Year`, epoch `Long`, formatted `String` |
| `SnowflakeLocalDateTimeConvertor(layout, zone[, codec])` | Snowflake `Long` or text to its issue time | `layout.unpack`; text through the codec, base 62 / 11 digits / padded by default |
| `CachedSharding<T>` | reuse identical range results | bounded (10 000 ranges by default, emptied when full) |

### Modulo Example

```java
ModCycle<Long> sharding = new ModCycle<>(4, "t_order_");

String exact = sharding.sharding(42L); // t_order_2
Collection<String> range = sharding.sharding(ShardingRange.closed(1L, 10L));
```

A range spanning at least `divisor` consecutive values routes to every node. Include open and unbounded ranges in tests.

### Interval Example

```java
IntervalTimeline timeline = new IntervalTimeline(
    "t_order_",
    ShardingRange.closed(
        LocalDateTime.of(2026, 1, 1, 0, 0),
        LocalDateTime.of(2026, 12, 31, 23, 59, 59)
    ),
    IntervalStep.of(ChronoUnit.MONTHS),
    DateTimeFormatter.ofPattern("yyyyMM")
);

String exact = timeline.sharding(LocalDateTime.of(2026, 3, 15, 10, 30));
Collection<String> range = timeline.sharding(ShardingRange.closed(
    LocalDateTime.of(2026, 3, 1, 0, 0),
    LocalDateTime.of(2026, 4, 30, 23, 59, 59)
));
```

Supported `IntervalStep` units are years, months, days, hours, minutes, and seconds. Ensure the suffix format is unique for the selected step.

### Snowflake Conversion

```java
SnowflakeLocalDateTimeConvertor convertor =
    new SnowflakeLocalDateTimeConvertor(Layout.SNOWFLAKE_MS, ZoneId.of("Asia/Shanghai"));

LocalDateTime time = convertor.toLocalDateTime(runtime.generator("default").generate());
```

Pass the generator's real layout (`runtime.get(name, TimeBasedGenerator.class).layout()`). The convertor accepts `Long`
and text values only; text decodes through its `IdCodec`.

## ShardingSphere

ShardingSphere's `COSID_*` algorithms are maintained in ShardingSphere, not in `cosid-sharding`, and depend on the CosId version ShardingSphere bundles; their availability and properties vary by release. Inspect the target artifact or official source before emitting a type. When `COSID_MOD` is available, its divisor property is named `mod`, not `divisor`:

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

- Prefer the core API shown above when application code owns routing; `SnowflakeLocalDateTimeConvertor(layout, zone)` follows the actual generator's `Layout`.
- In Apache ShardingSphere 5.4.0, `COSID_INTERVAL_SNOWFLAKE` reads only `epoch` and `zone-id` and hardcodes the default millisecond Snowflake layout. Use it only for that layout; `id-name` does not align a custom generator.
- For every other ShardingSphere release, verify that the CosId plugin and requested properties exist. Do not emit `COSID_INTERVAL_SNOWFLAKE` merely because an older document lists it.
- For a custom bit layout, use a version-verified integration that resolves the actual generator or a minimal `StandardShardingAlgorithm` that delegates to `SnowflakeLocalDateTimeConvertor` and `IntervalTimeline`.

Ensure `actualDataNodes` enumerates only valid physical suffixes; a daily numeric range such as `${20240101..20241231}` also generates impossible dates and must not be used.

## Validation Matrix

Test at least:

- first and last exact key for every boundary node;
- one `IN` set spanning multiple nodes;
- closed, open, unbounded, empty, and out-of-domain ranges;
- negative values for `ModCycle` (floor-mod);
- an interval transition such as month-end or year-end;
- Snowflake values generated with the actual epoch, unit, and bit layout;
- equality between algorithm effective nodes and provisioned physical nodes.

## Response Contract

Return the chosen algorithm, physical naming model, minimal Java or version-matched ShardingSphere configuration, and the routing matrix. Call out any partition provisioning risk explicitly.
