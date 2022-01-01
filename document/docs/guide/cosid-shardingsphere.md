# CosId-ShardingSphere 模块

> `CosIdKeyGenerateAlgorithm`、`CosIdModShardingAlgorithm`、`CosIdIntervalShardingAlgorithm` 已合并至 [ShardingSphere](https://github.com/apache/shardingsphere/pull/14132) 官方，未来 *[cosid-shardingsphere](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-shardingsphere)* 模块的维护可能会以官方为主。

> Kotlin DSL

``` kotlin
    implementation("me.ahoo.cosid:cosid-shardingsphere:${cosidVersion}")
```

## CosIdKeyGenerateAlgorithm (分布式主键)

```yaml
spring:
  shardingsphere:
    rules:
      sharding:
        key-generators:
          cosid:
            type: COSID
            props:
              id-name: __share__
```

## 基于间隔的时间范围分片算法

<p align="center" >
  <img :src="$withBase('/assets/design/CosIdIntervalShardingAlgorithm.png')" alt="CosIdIntervalShardingAlgorithm"/>
</p>

- 易用性: 支持多种数据类型 (`Long`/`LocalDateTime`/`DATE`/ `String` / `SnowflakeId`)，而官方实现是先转换成字符串再转换成`LocalDateTime`，转换成功率受时间格式化字符影响。
- 性能 : 相比于 `org.apache.shardingsphere.sharding.algorithm.sharding.datetime.IntervalShardingAlgorithm` 性能高出 *1200~4000* 倍。

| **PreciseShardingValue**                                                                                          | **RangeShardingValue**                                                                                          |
|-------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| <img :src="$withBase('/assets/perf/sharding/Throughput-Of-IntervalShardingAlgorithm-PreciseShardingValue.png')"/> | <img :src="$withBase('/assets/perf/sharding/Throughput-Of-IntervalShardingAlgorithm-RangeShardingValue.png')"/> |

- SmartIntervalShardingAlgorithm
    - type: COSID_INTERVAL
- DateIntervalShardingAlgorithm
    - type: COSID_INTERVAL_DATE
- LocalDateTimeIntervalShardingAlgorithm
    - type: COSID_INTERVAL_LDT
- TimestampIntervalShardingAlgorithm
    - type: COSID_INTERVAL_TS
- TimestampOfSecondIntervalShardingAlgorithm
    - type: COSID_INTERVAL_TS_SECOND
- SnowflakeIntervalShardingAlgorithm
    - type: COSID_INTERVAL_SNOWFLAKE

```yaml
spring:
  shardingsphere:
    rules:
      sharding:
        sharding-algorithms:
          alg-name:
            type: COSID_INTERVAL_{type_suffix}
            props:
              logic-name-prefix: logic-name-prefix
              id-name: cosid-name
              datetime-lower: 2021-12-08 22:00:00
              datetime-upper: 2022-12-01 00:00:00
              sharding-suffix-pattern: yyyyMM
              datetime-interval-unit: MONTHS
              datetime-interval-amount: 1
```

## 取模分片算法

<p align="center" >
  <img :src="$withBase('/assets/design/CosIdModShardingAlgorithm.png')" alt="CosIdModShardingAlgorithm"/>
</p>

- 性能 : 相比于 `org.apache.shardingsphere.sharding.algorithm.sharding.mod.ModShardingAlgorithm` 性能高出 *1200~4000* 倍。并且稳定性更高，不会出现严重的性能退化。

| **PreciseShardingValue**                                                                                     | **RangeShardingValue**                                                                                     |
|--------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| <img :src="$withBase('/assets/perf/sharding/Throughput-Of-ModShardingAlgorithm-PreciseShardingValue.png')"/> | <img :src="$withBase('/assets/perf/sharding/Throughput-Of-ModShardingAlgorithm-RangeShardingValue.png')"/> |

```yaml
spring:
  shardingsphere:
    rules:
      sharding:
        sharding-algorithms:
          alg-name:
            type: COSID_MOD
            props:
              mod: 4
              logic-name-prefix: t_table_
```
