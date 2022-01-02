# ShardingSphere 配置

::: tip 维护说明
`CosIdKeyGenerateAlgorithm`、`CosIdModShardingAlgorithm`、`CosIdIntervalShardingAlgorithm` 已合并至 [ShardingSphere](https://github.com/apache/shardingsphere/pull/14132) 官方，未来 *[cosid-shardingsphere](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-shardingsphere)* 模块的维护可能会以官方为主。
:::

## CosIdKeyGenerateAlgorithm

> type: COSID

| 名称        | 数据类型     | 说明                                              | 默认值         |
|-----------|----------|-------------------------------------------------|-------------|
| id-name   | `String` | `IdGenerator` 的名称（在 `IdGeneratorProvider` 中已注册） | `__share__` |
| as-string | `String` | 是否生成字符串类型的ID                                    | `fasle`     |

**YAML 配置样例**

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

## CosIdIntervalShardingAlgorithm

> type: COSID_INTERVAL

| 名称                       | 数据类型         | 说明                                  | 默认值                              |
|--------------------------|--------------|-------------------------------------|----------------------------------|
| logic-name-prefix        | `String`     | 逻辑表/数据源名前缀                          |                                  |
| datetime-lower           | `String`     | 时间分片下界值，时间戳格式：`yyyy-MM-dd HH:mm:ss` |                                  |
| datetime-upper           | `String`     | 时间分片上界值，时间戳格式：`yyyy-MM-dd HH:mm:ss` |                                  |
| sharding-suffix-pattern  | `String`     | 分片真实表/数据源后缀格式                       |                                  |
| datetime-interval-unit   | `ChronoUnit` | 分片键时间间隔单位                           |                                  |
| datetime-interval-amount | `int`        | 分片键时间间隔                             |                                  |
| ts-type                  | `String`     | 时间戳单位：`SECOND`/`MILLISECOND`        | `MILLISECOND`                    |
| zone-id                  | `String`     | 分片键时区                               | `ZoneId.systemDefault().getId()` |

**YAML 配置样例**

```yaml
spring:
  shardingsphere:
    rules:
      sharding:
        sharding-algorithms:
          alg-name:
            type: COSID_INTERVAL
            props:
              logic-name-prefix: logic-name-prefix
              datetime-lower: 2021-12-08 22:00:00
              datetime-upper: 2022-12-01 00:00:00
              sharding-suffix-pattern: yyyyMM
              datetime-interval-unit: MONTHS
              datetime-interval-amount: 1
```

## SnowflakeIntervalShardingAlgorithm

::: tip 算法说明
我们知道*SnowflakeId*的位分区方式，*SnowflakeId*可以解析出时间戳，即*SnowflakeId*可以作为时间，所以*SnowflakeId*可以作为*INTERVAL*的分配算法。 
（当没有`CreateTime`可用作分片时[这是一个非常极端的情况]，或者对性能有非常极端的要求时，*分布式ID主键*作为查询范围可能是持久层性能更好的选择。 )
:::

> type: COSID_INTERVAL_SNOWFLAKE

| 名称                       | 数据类型         | 说明                                              | 默认值         |
|--------------------------|--------------|-------------------------------------------------|-------------|
| logic-name-prefix        | `String`     | 逻辑表/数据源名前缀                                      |             |
| datetime-lower           | `String`     | 时间分片下界值，时间戳格式：`yyyy-MM-dd HH:mm:ss`             |             |
| datetime-upper           | `String`     | 时间分片上界值，时间戳格式：`yyyy-MM-dd HH:mm:ss`             |             |
| sharding-suffix-pattern  | `String`     | 分片真实表/数据源后缀格式                                   |             |
| datetime-interval-unit   | `ChronoUnit` | 分片键时间间隔单位                                       |             |
| datetime-interval-amount | `int`        | 分片键时间间隔                                         |             |
| id-name                  | `String`     | `IdGenerator` 的名称（在 `IdGeneratorProvider` 中已注册） | `__share__` |

**YAML 配置样例**

```yaml
spring:
  shardingsphere:
    rules:
      sharding:
        sharding-algorithms:
          alg-name:
            type: COSID_INTERVAL
            props:
              logic-name-prefix: logic-name-prefix
              datetime-lower: 2021-12-08 22:00:00
              datetime-upper: 2022-12-01 00:00:00
              sharding-suffix-pattern: yyyyMM
              datetime-interval-unit: MONTHS
              datetime-interval-amount: 1
              id-name: cosid-name
```

## CosIdModShardingAlgorithm

> type: COSID_MOD

| 名称                | 数据类型     | 说明         | 默认值 |
|-------------------|----------|------------|-----|
| logic-name-prefix | `String` | 逻辑表/数据源名前缀 |     |
| mod               | `int`    | 除数         |     |

**YAML 配置样例**

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
