---
name: cosid-sharding
description: Guide for using CosId sharding algorithms with ShardingSphere for database sharding. Use when users mention sharding, database splitting, ShardingSphere, PreciseSharding, IntervalSharding, IntervalTimeline, or database partitioning.
---

# CosId Database Sharding Integration Guide

TRIGGER: User is using CosId sharding algorithms with ShardingSphere for database sharding (keywords: sharding, database splitting, ShardingSphere, ShardingAlgorithm, PreciseSharding, IntervalSharding)

## 1. Add Dependencies

Sharding algorithms are included in `cosid-core`, no extra modules needed:

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation(platform("me.ahoo.cosid:cosid-bom"))
    implementation("me.ahoo.cosid:cosid-core")
}
```

For Spring Boot + ShardingSphere integration, also add:

```kotlin
implementation("me.ahoo.cosid:cosid-spring-boot-starter")
implementation("org.apache.shardingsphere:shardingsphere-jdbc")
```

## 2. PreciseSharding (Modulo-Based)

Modulo-based sharding algorithm for routing by ID value.

### Using ModCycle

```java
import me.ahoo.cosid.sharding.ModCycle;
import me.ahoo.cosid.sharding.PreciseSharding;

// Create modulo sharding: 4 shards, table prefix "t_order_"
PreciseSharding<Long> sharding = new ModCycle<>(
    4,           // number of shards
    "t_order_"   // logical table name prefix (end with separator)
);

// Route an order ID to the correct shard
String node = sharding.sharding(123456789L);
// "t_order_1"

String node2 = sharding.sharding(123456790L);
// "t_order_2"
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

```java
import me.ahoo.cosid.sharding.CachedSharding;

// Cache sharding computation results, good for high-frequency queries
PreciseSharding<Long> cached = new CachedSharding<>(new ModCycle<>(4, "t_order_"));
```

## 3. IntervalSharding (Time-Based)

Time-interval sharding algorithm for splitting tables by time range (e.g. monthly, daily).

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

### ShardingSphere YAML Configuration (Interval Sharding)

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

CosId provides the following ShardingSphere SPI algorithm types, usable directly in YAML configuration:

| SPI Type | Purpose | Sharding Key Type | Description |
|----------|---------|-------------------|-------------|
| `COSID` | Distributed ID generator | - | KeyGenerator for distributed primary keys |
| `COSID_MOD` | Modulo-based precise sharding | `Long` | `order_id % mod` routing |
| `COSID_INTERVAL` | Time-interval sharding | `LocalDateTime` / `String` | Routes by time range (create_time column) |
| `COSID_INTERVAL_SNOWFLAKE` | Snowflake ID time sharding | `Long` | Extracts timestamp from SnowflakeId for interval routing, useful when no create_time column exists |

### COSID_INTERVAL_SNOWFLAKE Configuration

When the sharding key is a SnowflakeId (Long) rather than a datetime column, use this algorithm to extract the timestamp from the ID:

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

Sharding algorithm based on Radix62 encoding. Converts a long ID to a Radix62 string, then applies modulo sharding. Useful when the shard position needs to be embedded in the ID itself.

Characteristics:
- Sharding result correlates with the ID's radix representation
- Good for scenarios where you need to reverse-engineer the shard from the ID
- Recommend shard counts that are factors of 62 (2, 31, 62) for even distribution

## 6. Spring Boot + ShardingSphere Integration

In Spring Boot projects, register CosId sharding algorithms as Spring beans for ShardingSphere auto-discovery:

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

- **Shard count design:** Use powers of 2 (4, 8, 16, 32) for modulo sharding to facilitate future expansion (double the shards).
- **Boundary handling:** IntervalTimeline times must be within the configured effective range. Values outside throw `IllegalArgumentException`. Design with sufficient time range headroom.
- **ShardingSphere version compatibility:** CosId's ShardingSphere integration supports ShardingSphere 5.x. `COSID_MOD`, `COSID_INTERVAL`, and `COSID_INTERVAL_SNOWFLAKE` have been merged into official ShardingSphere ([#14132](https://github.com/apache/shardingsphere/pull/14132)).
- **Snowflake ID sharding:** When using SnowflakeId as the sharding key, the embedded timestamp ensures even distribution with interval sharding. Modulo sharding also works well.
