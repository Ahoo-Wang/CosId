# Monthly-Based Table Sharding with CosId and ShardingSphere

This guide walks you through setting up monthly-based table sharding for a `t_log` table using CosId's `IntervalTimeline` algorithm with ShardingSphere. Tables will be named `t_log_202401`, `t_log_202402`, ..., `t_log_202512`, covering the range 2024-01 through 2025-12.

---

## 1. Add Dependencies

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation(platform("me.ahoo.cosid:cosid-bom:2.10.2"))
    implementation("me.ahoo.cosid:cosid-core")
    // For Spring Boot + ShardingSphere integration:
    implementation("me.ahoo.cosid:cosid-spring-boot-starter")
    implementation("org.apache.shardingsphere:shardingsphere-jdbc:5.5.1")
}
```

### Maven

```xml
<dependencies>
    <dependency>
        <groupId>me.ahoo.cosid</groupId>
        <artifactId>cosid-bom</artifactId>
        <version>2.10.2</version>
        <type>pom</type>
        <scope>import</scope>
    </dependency>
    <dependency>
        <groupId>me.ahoo.cosid</groupId>
        <artifactId>cosid-core</artifactId>
    </dependency>
    <!-- For Spring Boot + ShardingSphere integration: -->
    <dependency>
        <groupId>me.ahoo.cosid</groupId>
        <artifactId>cosid-spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>org.apache.shardingsphere</groupId>
        <artifactId>shardingsphere-jdbc</artifactId>
        <version>5.5.1</version>
    </dependency>
</dependencies>
```

> **Note:** Replace version numbers with the latest versions from the CosId releases and ShardingSphere releases.

---

## 2. Pure Java API Usage

If you want to use the sharding algorithm directly in Java (without ShardingSphere), you can use `IntervalTimeline`:

```java
import com.google.common.collect.Range;
import me.ahoo.cosid.sharding.IntervalStep;
import me.ahoo.cosid.sharding.IntervalTimeline;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

// Create a monthly sharding algorithm: t_log_202401 ~ t_log_202512
IntervalTimeline timeline = new IntervalTimeline(
    "t_log_",                                                  // logical table name prefix
    Range.closed(
        LocalDateTime.of(2024, 1, 1, 0, 0),                   // start time
        LocalDateTime.of(2025, 12, 31, 23, 59)                // end time
    ),
    IntervalStep.of(ChronoUnit.MONTHS),                       // monthly step
    DateTimeFormatter.ofPattern("yyyyMM")                      // suffix format
);

// Precise sharding: route a specific time to a table
String table1 = timeline.sharding(LocalDateTime.of(2024, 6, 15, 10, 30));
// Result: "t_log_202406"

String table2 = timeline.sharding(LocalDateTime.of(2025, 3, 20, 8, 0));
// Result: "t_log_202503"

// Range sharding: find all tables that cover a time range
Collection<String> tables = timeline.sharding(
    Range.closed(
        LocalDateTime.of(2024, 11, 1, 0, 0),
        LocalDateTime.of(2025, 2, 28, 23, 59)
    )
);
// Result: ["t_log_202411", "t_log_202412", "t_log_202501", "t_log_202502"]
```

This will generate 24 physical tables: `t_log_202401` through `t_log_202512`.

---

## 3. ShardingSphere YAML Configuration (Recommended)

### Option A: Using `COSID_INTERVAL` with a datetime sharding column

Use this when your sharding column is a `LocalDateTime` / `DATE` / `TIMESTAMP` type (e.g., `create_time`):

```yaml
spring:
  shardingsphere:
    datasource:
      names: ds
      ds:
        type: com.zaxxer.hikari.HikariDataSource
        driver-class-name: com.mysql.cj.jdbc.Driver
        jdbc-url: jdbc:mysql://localhost:3306/log_db
        username: root
        password: root
    rules:
      sharding:
        tables:
          t_log:
            actual-data-nodes: ds.t_log_${202401..202512}
            table-strategy:
              standard:
                sharding-column: create_time
                sharding-algorithm-name: cosid-log-interval
        sharding-algorithms:
          cosid-log-interval:
            type: COSID_INTERVAL
            props:
              logic-name-prefix: t_log_
              datetime-lower: "2024-01-01 00:00:00"
              datetime-upper: "2025-12-31 23:59:59"
              sharding-suffix-pattern: yyyyMM
              datetime-interval-unit: MONTHS
              datetime-interval-amount: 1
```

### Option B: Using `COSID_INTERVAL_SNOWFLAKE` with a Snowflake ID column

Use this when your sharding column is a Snowflake ID (`BIGINT`). The algorithm extracts the timestamp from the Snowflake ID to determine the target table:

```yaml
spring:
  shardingsphere:
    datasource:
      names: ds
      ds:
        type: com.zaxxer.hikari.HikariDataSource
        driver-class-name: com.mysql.cj.jdbc.Driver
        jdbc-url: jdbc:mysql://localhost:3306/log_db
        username: root
        password: root
    rules:
      sharding:
        tables:
          t_log:
            actual-data-nodes: ds.t_log_${202401..202512}
            table-strategy:
              standard:
                sharding-column: id
                sharding-algorithm-name: cosid-log-snowflake-interval
            key-generate-strategy:
              column: id
              key-generator-name: cosid-snowflake
        key-generators:
          cosid-snowflake:
            type: COSID
            props:
              id-name: __share__
        sharding-algorithms:
          cosid-log-snowflake-interval:
            type: COSID_INTERVAL_SNOWFLAKE
            props:
              logic-name-prefix: t_log_
              datetime-lower: "2024-01-01 00:00:00"
              datetime-upper: "2025-12-31 23:59:59"
              sharding-suffix-pattern: yyyyMM
              datetime-interval-unit: MONTHS
              datetime-interval-amount: 1
              id-name: __share__
```

---

## 4. Spring Bean Configuration (Alternative to YAML)

You can also register the sharding algorithm as a Spring Bean for programmatic use:

```java
import com.google.common.collect.Range;
import me.ahoo.cosid.sharding.CachedSharding;
import me.ahoo.cosid.sharding.IntervalStep;
import me.ahoo.cosid.sharding.IntervalTimeline;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Configuration
public class LogShardingConfiguration {

    @Bean
    public IntervalTimeline logIntervalSharding() {
        return new IntervalTimeline(
            "t_log_",
            Range.closed(
                LocalDateTime.of(2024, 1, 1, 0, 0),
                LocalDateTime.of(2025, 12, 31, 23, 59)
            ),
            IntervalStep.of(ChronoUnit.MONTHS),
            DateTimeFormatter.ofPattern("yyyyMM")
        );
    }

    // Optional: wrap with CachedSharding for better range query performance
    @Bean
    public CachedSharding<LocalDateTime> cachedLogIntervalSharding(IntervalTimeline logIntervalSharding) {
        return new CachedSharding<>(logIntervalSharding);
    }
}
```

---

## 5. Physical Table Names

The configuration above will produce 24 physical tables:

| # | Table Name     | Covers                |
|---|----------------|-----------------------|
| 1 | `t_log_202401` | 2024-01-01 ~ 2024-01-31 |
| 2 | `t_log_202402` | 2024-02-01 ~ 2024-02-29 |
| 3 | `t_log_202403` | 2024-03-01 ~ 2024-03-31 |
| ... | ... | ... |
| 12 | `t_log_202412` | 2024-12-01 ~ 2024-12-31 |
| 13 | `t_log_202501` | 2025-01-01 ~ 2025-01-31 |
| ... | ... | ... |
| 24 | `t_log_202512` | 2025-12-01 ~ 2025-12-31 |

Make sure all 24 tables are created in your database before running the application.

---

## 6. Important Notes

1. **Out-of-range values:** Any time value outside the configured range `[2024-01-01, 2025-12-31 23:59]` will throw an `IllegalArgumentException`. Plan your time range with enough margin for future data.

2. **Suffix pattern matters:** The `sharding-suffix-pattern` (`yyyyMM`) must match the suffix format used in `actual-data-nodes`. With `t_log_${202401..202512}`, the range expression produces integers, which must align with the formatted month suffix.

3. **Performance advantage:** CosId's interval sharding algorithm (`COSID_INTERVAL`) is **1200~4000x faster** than ShardingSphere's built-in `IntervalShardingAlgorithm`, according to the project benchmarks.

4. **ShardingSphere version:** CosId's sharding algorithms have been merged into the official ShardingSphere project (since PR [#14132](https://github.com/apache/shardingsphere/pull/14132)). If you are using ShardingSphere 5.x, these algorithm types (`COSID_INTERVAL`, `COSID_MOD`, `COSID_INTERVAL_SNOWFLAKE`) are available out of the box.

5. **Timestamp column type:** If your sharding column stores timestamps as milliseconds (`Long`), set the `ts-unit` property to `MILLISECOND` (this is the default). If using seconds, set it to `SECOND`.
