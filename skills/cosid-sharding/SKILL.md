---
name: cosid-sharding
description: Guide for using CosId sharding algorithms for database sharding with ShardingSphere. Use this skill whenever the user mentions database sharding, table sharding, ShardingSphere, interval sharding, modulo sharding, date-based sharding, range sharding, or needs to distribute data across multiple database tables or nodes. Also use when the user asks about ModCycle, IntervalTimeline, CachedSharding, PreciseSharding, RangeSharding, or SnowflakeLocalDateTimeConvertor.
---

# CosId Sharding Algorithms

CosId provides sharding algorithms designed for database sharding, compatible with Apache ShardingSphere. All algorithms implement both precise sharding (single key lookup) and range sharding (key range lookup).

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

## Key Design Principles

1. **Precise + Range**: Every algorithm supports both single-value and range sharding. ShardingSphere uses precise for `=` and `IN`, and range for `BETWEEN`, `>`, `<`.

2. **Effective nodes**: `getEffectiveNodes()` returns all possible target nodes. This is used by ShardingSphere for routing optimization.

3. **Thread safety**: All sharding implementations are thread-safe (`@ThreadSafe`).

4. **Interval bounds**: IntervalTimeline requires an explicit effective time range. Values outside this range throw `IllegalArgumentException`.
