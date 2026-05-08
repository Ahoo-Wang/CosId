---
name: cosid-sharding
description: Guide for using CosId sharding algorithms with ShardingSphere for database sharding. Use when users mention sharding, 分库分表, ShardingSphere, PreciseSharding, IntervalSharding, IntervalTimeline, or database partitioning.
---

# CosId 分库分表集成指南

TRIGGER: 用户使用 CosId 分片算法配合 ShardingSphere 进行分库分表（关键词：分片、分库分表、ShardingSphere、ShardingAlgorithm、PreciseSharding、IntervalSharding）

## 1. 添加依赖

分片算法在 `cosid-core` 中，无需额外模块：

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation(platform("me.ahoo.cosid:cosid-bom"))
    implementation("me.ahoo.cosid:cosid-core")
}
```

如果与 Spring Boot + ShardingSphere 配合使用，还需添加：

```kotlin
implementation("me.ahoo.cosid:cosid-spring-boot-starter")
implementation("org.apache.shardingsphere:shardingsphere-jdbc")
```

## 2. PreciseSharding 精确分片

基于取模的精确分片算法，适用于按 ID 值精确路由到分表。

### 使用 ModCycle

```java
import me.ahoo.cosid.sharding.ModCycle;
import me.ahoo.cosid.sharding.PreciseSharding;

// 创建取模分片算法：逻辑表名 "t_order"，分为 4 个节点
PreciseSharding<Long> sharding = new ModCycle<>(
    "t_order",   // 逻辑表名前缀
    4            // 分片数量
);

// 根据订单 ID 路由到具体的分表
String node = sharding.sharding(123456789L);
// "t_order_1"

String node2 = sharding.sharding(123456790L);
// "t_order_2"
```

### ShardingSphere YAML 配置

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

### 使用 CachedSharding 提升性能

```java
import me.ahoo.cosid.sharding.CachedSharding;

// 缓存分片计算结果，适合高频查询场景
PreciseSharding<Long> cached = new CachedSharding<>(new ModCycle<>("t_order_", 4));
```

## 3. IntervalSharding 区间分片

基于时间区间的分片算法，适用于按时间范围分表（如按月、按天）。

### 使用 IntervalTimeline

```java
import me.ahoo.cosid.sharding.IntervalTimeline;
import me.ahoo.cosid.sharding.IntervalStep;
import com.google.common.collect.Range;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

// 按月分表：2024-01 ~ 2025-12
IntervalTimeline timeline = new IntervalTimeline(
    "t_order_",                                              // 逻辑表名前缀
    Range.closed(
        LocalDateTime.of(2024, 1, 1, 0, 0),                 // 起始时间
        LocalDateTime.of(2025, 12, 31, 23, 59)              // 结束时间
    ),
    IntervalStep.of(ChronoUnit.MONTHS),                     // 按月步进
    DateTimeFormatter.ofPattern("yyyyMM")                    // 后缀格式
);

// 精确分片：根据时间路由到具体分表
String node = timeline.sharding(LocalDateTime.of(2024, 6, 15, 10, 30));
// "t_order_202406"

// 范围分片：查询一个时间范围涉及哪些分表
Collection<String> nodes = timeline.sharding(
    Range.closed(
        LocalDateTime.of(2024, 3, 1, 0, 0),
        LocalDateTime.of(2024, 6, 30, 23, 59)
    )
);
// ["t_order_202403", "t_order_202404", "t_order_202405", "t_order_202406"]
```

### 按天分表

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

### ShardingSphere YAML 配置（区间分片）

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

## 4. CeilingRadixSharding（要点提示）

基于 62 进制的分片算法，将 long 型 ID 转为 Radix62 字符后取模分片。适用于 ID 本身需要体现分片信息的场景。

特点：
- 分片结果与 ID 的进制表示关联
- 适合需要从 ID 反推分片位置的场景
- 分片数量建议为 62 的因子（2、31、62）以获得均匀分布

## 5. 与 Spring Boot + ShardingSphere 集成

在 Spring Boot 项目中，CosId 的分片算法可以注册为 Spring Bean，供 ShardingSphere 自动发现：

```java
@Configuration
public class ShardingConfiguration {

    @Bean
    public PreciseSharding<Long> orderModSharding() {
        return new CachedSharding<>(new ModCycle<>("t_order_", 4));
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

## 6. 常见问题

- **分片数量设计：** 取模分片的数量建议为 2 的幂次（4、8、16、32），便于后续扩容（成倍扩展）。
- **边界值处理：** IntervalTimeline 的时间必须在配置的有效区间内，超出范围会抛出 `IllegalArgumentException`。设计时预留足够的时间范围。
- **ShardingSphere 版本兼容：** CosId 的 ShardingSphere 集成支持 ShardingSphere 5.x 系列。不同小版本之间的 SPI 注册方式可能略有差异。
- **雪花 ID 分片：** 使用 SnowflakeId 作为分片键时，由于 ID 本身带有时间戳信息，按时间区间分片可获得均匀分布。使用取模分片也是常见做法。
