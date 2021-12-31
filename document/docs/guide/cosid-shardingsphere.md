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


``` shell
gradle cosid-shardingsphere:jmh
```

```
# JMH version: 1.29
# VM version: JDK 11.0.13, OpenJDK 64-Bit Server VM, 11.0.13+8-LTS
# VM options: -Dfile.encoding=UTF-8 -Djava.io.tmpdir=/work/CosId/cosid-shardingsphere/build/tmp/jmh -Duser.country=CN -Duser.language=zh -Duser.variant
# Blackhole mode: full + dont-inline hint
# Warmup: 1 iterations, 10 s each
# Measurement: 1 iterations, 10 s each
# Timeout: 10 min per iteration
# Threads: 1 thread, will synchronize iterations
# Benchmark mode: Throughput, ops/time
Benchmark                                                         (days)   Mode  Cnt         Score   Error  Units
IntervalShardingAlgorithmBenchmark.cosid_precise_local_date_time      10  thrpt       53279788.772          ops/s
IntervalShardingAlgorithmBenchmark.cosid_precise_local_date_time     100  thrpt       38114729.365          ops/s
IntervalShardingAlgorithmBenchmark.cosid_precise_local_date_time    1000  thrpt       32714318.129          ops/s
IntervalShardingAlgorithmBenchmark.cosid_precise_local_date_time   10000  thrpt       22317905.643          ops/s
IntervalShardingAlgorithmBenchmark.cosid_precise_timestamp            10  thrpt       20028091.211          ops/s
IntervalShardingAlgorithmBenchmark.cosid_precise_timestamp           100  thrpt       19272744.794          ops/s
IntervalShardingAlgorithmBenchmark.cosid_precise_timestamp          1000  thrpt       17814417.856          ops/s
IntervalShardingAlgorithmBenchmark.cosid_precise_timestamp         10000  thrpt       12384788.025          ops/s
IntervalShardingAlgorithmBenchmark.cosid_range_local_date_time        10  thrpt       18716732.080          ops/s
IntervalShardingAlgorithmBenchmark.cosid_range_local_date_time       100  thrpt        8436553.492          ops/s
IntervalShardingAlgorithmBenchmark.cosid_range_local_date_time      1000  thrpt        1655952.254          ops/s
IntervalShardingAlgorithmBenchmark.cosid_range_local_date_time     10000  thrpt         185348.831          ops/s
IntervalShardingAlgorithmBenchmark.cosid_range_timestamp              10  thrpt        9410931.643          ops/s
IntervalShardingAlgorithmBenchmark.cosid_range_timestamp             100  thrpt        5792861.181          ops/s
IntervalShardingAlgorithmBenchmark.cosid_range_timestamp            1000  thrpt        1585344.761          ops/s
IntervalShardingAlgorithmBenchmark.cosid_range_timestamp           10000  thrpt         196663.812          ops/s
IntervalShardingAlgorithmBenchmark.office_precise_timestamp           10  thrpt          72189.800          ops/s
IntervalShardingAlgorithmBenchmark.office_precise_timestamp          100  thrpt          11245.324          ops/s
IntervalShardingAlgorithmBenchmark.office_precise_timestamp         1000  thrpt           1339.128          ops/s
IntervalShardingAlgorithmBenchmark.office_precise_timestamp        10000  thrpt            113.396          ops/s
IntervalShardingAlgorithmBenchmark.office_range_timestamp             10  thrpt          64679.422          ops/s
IntervalShardingAlgorithmBenchmark.office_range_timestamp            100  thrpt           4267.860          ops/s
IntervalShardingAlgorithmBenchmark.office_range_timestamp           1000  thrpt            227.817          ops/s
IntervalShardingAlgorithmBenchmark.office_range_timestamp          10000  thrpt              7.579          ops/s
```

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

``` shell
gradle cosid-shardingsphere:jmh
```

```
# JMH version: 1.29
# VM version: JDK 11.0.13, OpenJDK 64-Bit Server VM, 11.0.13+8-LTS
# VM options: -Dfile.encoding=UTF-8 -Djava.io.tmpdir=/work/CosId/cosid-shardingsphere/build/tmp/jmh -Duser.country=CN -Duser.language=zh -Duser.variant
# Blackhole mode: full + dont-inline hint
# Warmup: 1 iterations, 10 s each
# Measurement: 1 iterations, 10 s each
# Timeout: 10 min per iteration
# Threads: 1 thread, will synchronize iterations
# Benchmark mode: Throughput, ops/time
Benchmark                                     (divisor)   Mode  Cnt          Score   Error  Units
ModShardingAlgorithmBenchmark.cosid_precise          10  thrpt       121431137.111          ops/s
ModShardingAlgorithmBenchmark.cosid_precise         100  thrpt       119947284.141          ops/s
ModShardingAlgorithmBenchmark.cosid_precise        1000  thrpt       113095657.321          ops/s
ModShardingAlgorithmBenchmark.cosid_precise       10000  thrpt       108435323.537          ops/s
ModShardingAlgorithmBenchmark.cosid_precise      100000  thrpt        84657505.579          ops/s
ModShardingAlgorithmBenchmark.cosid_range            10  thrpt        37397323.508          ops/s
ModShardingAlgorithmBenchmark.cosid_range           100  thrpt        16905691.783          ops/s
ModShardingAlgorithmBenchmark.cosid_range          1000  thrpt         2969820.981          ops/s
ModShardingAlgorithmBenchmark.cosid_range         10000  thrpt          312881.488          ops/s
ModShardingAlgorithmBenchmark.cosid_range        100000  thrpt           31581.396          ops/s
ModShardingAlgorithmBenchmark.office_precise         10  thrpt         9135460.160          ops/s
ModShardingAlgorithmBenchmark.office_precise        100  thrpt         1356582.418          ops/s
ModShardingAlgorithmBenchmark.office_precise       1000  thrpt          104500.125          ops/s
ModShardingAlgorithmBenchmark.office_precise      10000  thrpt            8619.933          ops/s
ModShardingAlgorithmBenchmark.office_precise     100000  thrpt             629.353          ops/s
ModShardingAlgorithmBenchmark.office_range           10  thrpt         5535645.737          ops/s
ModShardingAlgorithmBenchmark.office_range          100  thrpt           83271.925          ops/s
ModShardingAlgorithmBenchmark.office_range         1000  thrpt             911.534          ops/s
ModShardingAlgorithmBenchmark.office_range        10000  thrpt               9.133          ops/s
ModShardingAlgorithmBenchmark.office_range       100000  thrpt               0.208          ops/s
```

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
