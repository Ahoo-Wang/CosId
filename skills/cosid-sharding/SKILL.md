---
name: cosid-sharding
description: Design and configure CosId sharding algorithms for database sharding and ShardingSphere. Use when the user mentions table or database sharding, ShardingSphere COSID_MOD or COSID_INTERVAL rules, modulo sharding, date/time interval sharding, range routing, SnowflakeId timestamp extraction, ModCycle, IntervalTimeline, CachedSharding, PreciseSharding, RangeSharding, or SnowflakeLocalDateTimeConvertor.
---

# CosId Sharding Algorithms

Use this skill to choose, configure, and validate CosId sharding behavior.

## Workflow

1. Identify the sharding key type: numeric ID, SnowflakeId, `LocalDateTime`, or an existing timestamp column.
2. Choose the algorithm: `ModCycle` for uniform numeric distribution, `IntervalTimeline` for time ranges, or `CachedSharding` to cache repeated range routing.
3. Confirm both precise and range queries. ShardingSphere routes `=`, `IN`, and range predicates differently.
4. Define effective nodes and bounds explicitly. For interval sharding, include lower/upper datetime bounds and suffix format.
5. Provide a minimal Java or ShardingSphere YAML example and a routing test.

## Sharding Algorithm Types

| Algorithm | Class | Best For |
|---|---|---|
| **Modulo (ModCycle)** | `ModCycle<T>` | Uniform distribution, numeric IDs |
| **Interval Timeline** | `IntervalTimeline` | Date-based partitioning, time-series data |
| **Cached Sharding** | `CachedSharding<T>` | Wraps any algorithm to cache range lookups |

## Architecture

The sharding hierarchy:

```
Sharding<T>              (combines precise + range)
├── PreciseSharding<T>   (single value → node)
└── RangeSharding<T>     (value range → collection of nodes)

Implementations:
├── ModCycle<T>          (modulo-based, numeric IDs)
├── IntervalTimeline     (time-based intervals)
└── CachedSharding<T>    (caching decorator)
```

## ModCycle - Modulo Sharding

Distributes numeric IDs across nodes using `value % divisor`. Best for uniform distribution when using SnowflakeId or SegmentId.

Use `ModCycle` when the sharding key is already numeric and the desired distribution is even across a fixed number of tables or databases.

### Usage

```java
import me.ahoo.cosid.sharding.ModCycle;

// Shard across 4 nodes: table_0, table_1, table_2, table_3
ModCycle<Long> sharding = new ModCycle<>(4, "table_");

// Precise sharding
String node = sharding.sharding(42L);  // → "table_2"

// Range sharding
Range<Long> range = Range.closed(1L, 10L);
Collection<String> nodes = sharding.sharding(range);  // all 4 nodes
```

### ShardingSphere Integration

```yaml
# ShardingSphere YAML configuration
rules:
  - !SHARDING
    tables:
      t_order:
        actualDataNodes: ds_${0..1}.t_order_${0..3}
        tableStrategy:
          standard:
            shardingColumn: order_id
            shardingAlgorithmName: t_order_mod
    shardingAlgorithms:
      t_order_mod:
        type: COSID_MOD
        props:
          divisor: 4
          logic-name-prefix: t_order_
```

## IntervalTimeline - Time-Based Sharding

Distributes data across time-based intervals. Each interval maps to a specific node named with a formatted date suffix.

Use `IntervalTimeline` when table names encode time periods such as day, month, or hour. It is also appropriate when a SnowflakeId can be converted back into event time.

### Usage

```java
import me.ahoo.cosid.sharding.IntervalTimeline;
import me.ahoo.cosid.sharding.IntervalStep;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.google.common.collect.Range;

// Daily sharding for 2024
IntervalTimeline timeline = new IntervalTimeline(
    "t_order_",                                                    // logic name prefix
    Range.closed(
        LocalDateTime.of(2024, 1, 1, 0, 0),
        LocalDateTime.of(2024, 12, 31, 23, 59, 59)
    ),
    IntervalStep.of(ChronoUnit.DAYS),                              // daily intervals
    DateTimeFormatter.ofPattern("yyyyMMdd")                         // suffix format
);

// Precise: which table holds data for 2024-03-15?
String node = timeline.sharding(LocalDateTime.of(2024, 3, 15, 10, 30));
// → "t_order_20240315"

// Range: which tables cover March 2024?
Range<LocalDateTime> marchRange = Range.closed(
    LocalDateTime.of(2024, 3, 1, 0, 0),
    LocalDateTime.of(2024, 3, 31, 23, 59, 59)
);
Collection<String> nodes = timeline.sharding(marchRange);
// → ["t_order_20240301", "t_order_20240302", ..., "t_order_20240331"]
```

### Interval Step Options

```java
// Yearly intervals
IntervalStep.of(ChronoUnit.YEARS)

// Monthly intervals
IntervalStep.of(ChronoUnit.MONTHS)

// Daily intervals
IntervalStep.of(ChronoUnit.DAYS)

// Hourly intervals
IntervalStep.of(ChronoUnit.HOURS)

// Custom: every 3 months
IntervalStep.of(ChronoUnit.MONTHS, 3)
```

### Common Suffix Formatters

```java
// Yearly: t_order_2024, t_order_2025
DateTimeFormatter.ofPattern("yyyy")

// Monthly: t_order_202401, t_order_202402
DateTimeFormatter.ofPattern("yyyyMM")

// Daily: t_order_20240315
DateTimeFormatter.ofPattern("yyyyMMdd")

// Hourly: t_order_2024031514
DateTimeFormatter.ofPattern("yyyyMMddHH")
```

### ShardingSphere Integration for Interval Sharding

```yaml
rules:
  - !SHARDING
    tables:
      t_order:
        actualDataNodes: ds_0.t_order_${20240101..20241231}
        tableStrategy:
          standard:
            shardingColumn: create_time
            shardingAlgorithmName: t_order_interval
    shardingAlgorithms:
      t_order_interval:
        type: COSID_INTERVAL
        props:
          logic-name-prefix: t_order_
          datetime-lower: "2024-01-01 00:00:00"
          datetime-upper: "2024-12-31 23:59:59"
          sharding-suffix-pattern: yyyyMMdd
          datetime-interval-unit: DAYS
          datetime-interval-amount: 1
```

## SnowflakeLocalDateTimeConvertor

Converts SnowflakeId values to LocalDateTime for time-based sharding using SnowflakeId as the sharding key:

```java
import me.ahoo.cosid.sharding.SnowflakeLocalDateTimeConvertor;

SnowflakeLocalDateTimeConvertor convertor = new SnowflakeLocalDateTimeConvertor(
    epoch, timestampBit  // from your SnowflakeId configuration
);

// Convert a SnowflakeId to LocalDateTime
LocalDateTime time = convertor.convert(snowflakeId);
```

This enables using SnowflakeId-based IDs with IntervalTimeline sharding without a separate timestamp column.

## CachedSharding

Wraps any sharding algorithm to cache range sharding results:

```java
import me.ahoo.cosid.sharding.CachedSharding;

ModCycle<Long> modSharding = new ModCycle<>(32, "table_");
CachedSharding<Long> cachedSharding = new CachedSharding<>(modSharding);

// First range query computes and caches
Collection<String> nodes1 = cachedSharding.sharding(Range.closed(1L, 100L));

// Subsequent identical range queries use cache
Collection<String> nodes2 = cachedSharding.sharding(Range.closed(1L, 100L));
```

Range queries are often repeated (e.g., querying "last 7 days" across many requests), so caching avoids redundant computation.

## Choosing a Sharding Strategy

| Scenario | Algorithm | Why |
|---|---|---|
| Uniform numeric ID distribution | ModCycle | Simple, even distribution |
| Date-based table partitioning | IntervalTimeline | Maps time ranges to tables |
| SnowflakeId as sharding key | IntervalTimeline + SnowflakeLocalDateTimeConvertor | Extract timestamp from ID |
| High QPS range queries | CachedSharding + any | Cache avoids recomputation |
| Auto-increment / SegmentId as key | ModCycle | Even distribution of monotonic IDs |

## Validation Checklist

Use a small routing matrix before finalizing a rule:

- One exact key routes to exactly one expected node.
- An `IN` query routes to the union of expected nodes.
- A range query covers all boundary nodes and no unrelated nodes when possible.
- Values outside an `IntervalTimeline` effective range fail intentionally.
- Snowflake timestamp extraction uses the same epoch and timestamp bits as the generator.
- The ShardingSphere `actualDataNodes` expression matches every possible CosId effective node.

## Key Design Principles

1. **Precise + Range**: Every algorithm supports both single-value and range sharding. ShardingSphere uses precise for `=` and `IN`, and range for `BETWEEN`, `>`, `<`.
2. **Effective nodes**: `getEffectiveNodes()` returns all possible target nodes. This is used by ShardingSphere for routing optimization.
3. **Thread safety**: The `Sharding` interface is annotated `@ThreadSafe`; implementations follow that contract, but not every concrete class repeats the annotation.
4. **Interval bounds**: `IntervalTimeline` requires an explicit effective time range. Values outside this range throw `IllegalArgumentException`.
5. **Generator alignment**: When the sharding key is a CosId-generated ID, keep the generator epoch, timestamp unit, and converter settings aligned with the sharding rule.

## Response Template

When answering a sharding request, include:

1. The selected algorithm and why it fits the sharding key.
2. The expected table/database naming pattern.
3. A concise Java or ShardingSphere YAML example.
4. A routing test matrix for exact, `IN`, and range queries.
