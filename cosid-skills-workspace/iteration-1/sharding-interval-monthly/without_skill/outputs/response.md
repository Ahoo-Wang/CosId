# Monthly Interval Sharding for `t_log` with CosId + ShardingSphere

## Configuration

Use the `COSID_INTERVAL` sharding algorithm type in your ShardingSphere configuration. Here is the complete YAML setup:

```yaml
spring:
  shardingsphere:
    datasource:
      names: ds0
      ds0:
        driver-class-name: com.mysql.cj.jdbc.Driver
        jdbc-url: jdbc:mysql://localhost:3306/demo_ds
        username: root
        password: root
    rules:
      sharding:
        sharding-algorithms:
          t-log-interval:
            type: COSID_INTERVAL
            props:
              logic-name-prefix: t_log_
              datetime-lower: 2024-01-01 00:00:00
              datetime-upper: 2025-12-31 23:59:59
              sharding-suffix-pattern: yyyyMM
              datetime-interval-unit: MONTHS
              datetime-interval-amount: 1
        tables:
          t_log:
            actual-data-nodes: ds0.t_log_$->{['202401','202402','202403','202404','202405','202406','202407','202408','202409','202410','202411','202412','202501','202502','202503','202504','202505','202506','202507','202508','202509','202510','202511','202512']}
            table-strategy:
              standard:
                sharding-column: create_time
                sharding-algorithm-name: t-log-interval
```

## Key Properties Explained

| Property | Value | Purpose |
|---|---|---|
| `logic-name-prefix` | `t_log_` | Prefix for actual table names; appended with the formatted suffix to produce names like `t_log_202401` |
| `datetime-lower` | `2024-01-01 00:00:00` | Lower bound of the sharding time range |
| `datetime-upper` | `2025-12-31 23:59:59` | Upper bound of the sharding time range |
| `sharding-suffix-pattern` | `yyyyMM` | `DateTimeFormatter` pattern applied to each interval's start time to generate the table suffix (e.g. `202401`, `202402`) |
| `datetime-interval-unit` | `MONTHS` | The `ChronoUnit` for the interval step -- one month per shard |
| `datetime-interval-amount` | `1` | Number of units per interval (1 month) |

## Actual Tables Created

This configuration maps to 24 actual tables spanning January 2024 through December 2025:

```
t_log_202401  t_log_202407  t_log_202501  t_log_202507
t_log_202402  t_log_202408  t_log_202502  t_log_202508
t_log_202403  t_log_202409  t_log_202503  t_log_202509
t_log_202404  t_log_202410  t_log_202504  t_log_202510
t_log_202405  t_log_202411  t_log_202505  t_log_202511
t_log_202406  t_log_202412  t_log_202506  t_log_202512
```

## How It Works

The `COSID_INTERVAL` algorithm (backed by `IntervalTimeline` in CosId core) pre-computes all time intervals at initialization:

1. The effective range `[2024-01-01, 2025-12-31]` is divided into 1-month intervals using `IntervalStep.of(ChronoUnit.MONTHS)`.
2. Each interval's lower-bound timestamp is formatted with the `sharding-suffix-pattern` (`yyyyMM`) and concatenated with `logic-name-prefix` to form the actual table name.
3. **Precise sharding** (e.g. `WHERE create_time = '2024-06-15'`) uses O(1) offset calculation to find `t_log_202406`.
4. **Range sharding** (e.g. `WHERE create_time BETWEEN ... AND ...`) computes the inclusive set of tables spanning the range.

## Dependency Setup

Add the CosId ShardingSphere dependency (or use the Spring Boot starter with ShardingSphere):

**Gradle (Kotlin):**
```kotlin
implementation("me.ahoo.cosid:cosid-shardingsphere:latestVersion")
```

**Maven:**
```xml
<dependency>
    <groupId>me.ahoo.cosid</groupId>
    <artifactId>cosid-shardingsphere</artifactId>
    <version>${cosid.version}</version>
</dependency>
```

> Note: `CosIdIntervalShardingAlgorithm` has been merged into the official ShardingSphere project (since PR #14132), so if you are using a recent version of ShardingSphere, the algorithm type `COSID_INTERVAL` is available without the separate `cosid-shardingsphere` dependency.

## Alternative: SnowflakeId as Sharding Key

If your sharding column is a SnowflakeId (Long) instead of a datetime, use `COSID_INTERVAL_SNOWFLAKE` instead. This algorithm extracts the timestamp embedded in the SnowflakeId to perform interval-based routing. Add the `id-name` property to reference the SnowflakeId generator registered in `IdGeneratorProvider`:

```yaml
t-log-interval:
  type: COSID_INTERVAL_SNOWFLAKE
  props:
    logic-name-prefix: t_log_
    datetime-lower: 2024-01-01 00:00:00
    datetime-upper: 2025-12-31 23:59:59
    sharding-suffix-pattern: yyyyMM
    datetime-interval-unit: MONTHS
    datetime-interval-amount: 1
    id-name: snowflake_friendly
```

## Performance Note

Compared to ShardingSphere's built-in `IntervalShardingAlgorithm`, CosId's `COSID_INTERVAL` is 1200--4000x faster because it uses pre-computed interval arrays with O(1) offset lookup instead of parsing/formatting timestamps at query time.
