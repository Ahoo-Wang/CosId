---
name: cosid-sharding
description: Guide for using CosId sharding algorithms with ShardingSphere for database sharding. Use this skill whenever the user mentions table sharding, database splitting, ShardingSphere integration, sharding by ID, sharding by time, monthly table partitioning, or ModCycle/IntervalTimeline configuration — even if they just say "I need to split my table into shards" without mentioning CosId. Also trigger when the user asks about PreciseSharding, RangeSharding, CachedSharding, or ceiling radix sharding.
---

# CosId Database Sharding Integration Guide

## When to Use This Skill

Use this skill when a developer needs sharding algorithms for database table splitting — either standalone or integrated with ShardingSphere. Key scenarios:

- Splitting tables by ID value (modulo sharding)
- Splitting tables by time range (monthly, daily, hourly)
- Registering CosId sharding algorithms as ShardingSphere SPI
- Registering sharding algorithms as Spring beans for ShardingSphere auto-discovery

CosId sharding algorithms live in `cosid-core` (no extra modules). They implement both `PreciseSharding` (for `=` / `IN` queries) and `RangeSharding` (for `BETWEEN` queries), so ShardingSphere can use them for all query types.

## 1. Add Dependencies

```kotlin
dependencies {
    implementation(platform("me.ahoo.cosid:cosid-bom"))
    implementation("me.ahoo.cosid:cosid-core")
}
```

For Spring Boot + ShardingSphere integration:

```kotlin
implementation("me.ahoo.cosid:cosid-spring-boot-starter")
implementation("org.apache.shardingsphere:shardingsphere-jdbc")
```

## 2. PreciseSharding (Modulo-Based)

Why modulo sharding: It's the simplest sharding strategy. Given an ID, `id % numShards` determines the table. Works well when IDs are evenly distributed (e.g. SnowflakeId). The trade-off: range queries always hit all shards because modulo doesn't preserve locality.

### Using ModCycle

```java
import me.ahoo.cosid.sharding.ModCycle;
import me.ahoo.cosid.sharding.PreciseSharding;

// Constructor: (int divisor, String logicNamePrefix)
// divisor comes first because it's the primary parameter — the number of
// shards determines the routing logic. The prefix is just a naming convention.
PreciseSharding<Long> sharding = new ModCycle<>(
    4,           // number of shards
    "t_order_"   // logical table name prefix (end with separator)
);

String node = sharding.sharding(123456789L);
// "t_order_1" (123456789 % 4 = 1)

String node2 = sharding.sharding(123456790L);
// "t_order_2" (123456790 % 4 = 2)
```

### ShardingSphere YAML Configuration

```yaml
rules:
  - !SHARDING
    tables:
      t_order:
        actualDataNodes: ds_${0..1}.t_order_${0..3}
        tableStrategy:
          standard:
            shardingColumn: order_id
            shardingAlgorithmName: cosid-mod
    shardingAlgorithms:
      cosid-mod:
        type: COSID_MOD
        props:
          logic-name-prefix: t_order_
          mod: 4
```

### Using CachedSharding for Better Performance

Why: If the same ID is queried repeatedly (e.g. foreign key lookups), caching the modulo result avoids recomputation. The cache is a simple map — negligible memory cost for significant CPU savings on hot paths.

```java
import me.ahoo.cosid.sharding.CachedSharding;

PreciseSharding<Long> cached = new CachedSharding<>(new ModCycle<>(4, "t_order_"));
```

## 3. IntervalSharding (Time-Based)

Why time-based sharding: For time-series data (logs, events, orders), splitting by time range gives natural data lifecycle management. Old tables can be archived or dropped independently. Each time interval maps to a physical table.

### Using IntervalTimeline

```java
import me.ahoo.cosid.sharding.IntervalTimeline;
import me.ahoo.cosid.sharding.IntervalStep;
import com.google.common.collect.Range;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

// Monthly sharding: 2024-01 ~ 2025-12
IntervalTimeline timeline = new IntervalTimeline(
    "t_order_",                                              // logical table prefix
    Range.closed(
        LocalDateTime.of(2024, 1, 1, 0, 0),                 // start time
        LocalDateTime.of(2025, 12, 31, 23, 59)              // end time
    ),
    IntervalStep.of(ChronoUnit.MONTHS),                     // step by month
    DateTimeFormatter.ofPattern("yyyyMM")                    // suffix format
);

// Precise sharding: route by time to a specific table
String node = timeline.sharding(LocalDateTime.of(2024, 6, 15, 10, 30));
// "t_order_202406"

// Range sharding: find all tables for a time range
Collection<String> nodes = timeline.sharding(
    Range.closed(
        LocalDateTime.of(2024, 3, 1, 0, 0),
        LocalDateTime.of(2024, 6, 30, 23, 59)
    )
);
// ["t_order_202403", "t_order_202404", "t_order_202405", "t_order_202406"]
```

### Daily Sharding

```java
IntervalTimeline dailyTimeline = new IntervalTimeline(
    "t_log_",
    Range.closed(
        LocalDateTime.of(2024, 1, 1, 0, 0),
        LocalDateTime.of(2024, 12, 31, 23, 59)
    ),
    IntervalStep.of(ChronoUnit.DAYS),
    DateTimeFormatter.ofPattern("yyyyMMdd")
);
```

### ShardingSphere YAML Configuration (Interval)

```yaml
rules:
  - !SHARDING
    tables:
      t_order:
        actualDataNodes: ds.t_order_${202401..202412}
        tableStrategy:
          standard:
            shardingColumn: create_time
            shardingAlgorithmName: cosid-interval
    shardingAlgorithms:
      cosid-interval:
        type: COSID_INTERVAL
        props:
          logic-name-prefix: t_order_
          datetime-lower: "2024-01-01 00:00:00"
          datetime-upper: "2024-12-31 23:59:59"
          sharding-suffix-pattern: yyyyMM
          datetime-interval-unit: MONTHS
          datetime-interval-amount: 1
```

## 4. ShardingSphere SPI Algorithm Types

CosId provides these ShardingSphere SPI algorithm types, usable directly in YAML:

| SPI Type | Purpose | Sharding Key | Description |
|----------|---------|-------------|-------------|
| `COSID` | ID generator | - | KeyGenerator for distributed primary keys |
| `COSID_MOD` | Modulo sharding | `Long` | `id % mod` routing |
| `COSID_INTERVAL` | Time-interval sharding | `LocalDateTime` / `String` | Routes by time range (datetime column) |
| `COSID_INTERVAL_SNOWFLAKE` | Snowflake time sharding | `Long` | Extracts timestamp from SnowflakeId for interval routing |

### COSID_INTERVAL_SNOWFLAKE Configuration

Why: When your sharding key is a SnowflakeId (Long) rather than a datetime column, this algorithm extracts the embedded timestamp and applies interval routing. Useful when you don't have a `create_time` column but still want time-based sharding.

```yaml
rules:
  - !SHARDING
    shardingAlgorithms:
      cosid-interval-snowflake:
        type: COSID_INTERVAL_SNOWFLAKE
        props:
          logic-name-prefix: t_order_
          datetime-lower: "2024-01-01 00:00:00"
          datetime-upper: "2025-12-31 23:59:59"
          sharding-suffix-pattern: yyyyMM
          datetime-interval-unit: MONTHS
          datetime-interval-amount: 1
          id-name: __share__
```

## 5. CeilingRadixSharding (Quick Reference)

Why: This algorithm converts a long ID to Radix62, then applies modulo on the first character. The shard position is embedded in the ID itself, so you can determine which shard an ID belongs to just by looking at it. Use it when you need self-describing IDs.

Characteristics:
- Shard count should be a factor of 62 (2, 31, 62) for even distribution
- The first character of the Radix62 string determines the shard

## 6. Spring Boot + ShardingSphere Integration

Why register as beans: When sharding algorithms are Spring beans, ShardingSphere auto-discovers them — no need for SPI registration or class-name configuration. This also lets you inject other beans (e.g. a shared `IdGeneratorProvider`) into your sharding logic.

```java
@Configuration
public class ShardingConfiguration {

    @Bean
    public PreciseSharding<Long> orderModSharding() {
        return new CachedSharding<>(new ModCycle<>(4, "t_order_"));
    }

    @Bean
    public IntervalTimeline orderIntervalSharding() {
        return new IntervalTimeline(
            "t_order_",
            Range.closed(
                LocalDateTime.of(2024, 1, 1, 0, 0),
                LocalDateTime.of(2025, 12, 31, 23, 59)
            ),
            IntervalStep.of(ChronoUnit.MONTHS),
            DateTimeFormatter.ofPattern("yyyyMM")
        );
    }
}
```

## 7. Common Issues

- **Shard count design:** Use powers of 2 (4, 8, 16, 32) for modulo sharding. This makes doubling shards straightforward during expansion.
- **Boundary handling:** IntervalTimeline throws `IllegalArgumentException` for times outside the configured range. Always design with sufficient headroom — extend the upper bound well into the future.
- **ShardingSphere compatibility:** Supports ShardingSphere 5.x. The `COSID_MOD`, `COSID_INTERVAL`, and `COSID_INTERVAL_SNOWFLAKE` types have been merged into official ShardingSphere ([#14132](https://github.com/apache/shardingsphere/pull/14132)).
- **Snowflake ID sharding:** SnowflakeId embeds a timestamp, so interval sharding produces even distribution. Modulo sharding also works since SnowflakeId values are sequential.
