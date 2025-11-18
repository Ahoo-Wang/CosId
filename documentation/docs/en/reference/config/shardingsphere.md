# ShardingSphere Configuration

::: tip Maintenance Note
`CosIdKeyGenerateAlgorithm`, `CosIdModShardingAlgorithm`, `CosIdIntervalShardingAlgorithm` have been merged into the official [ShardingSphere](https://github.com/apache/shardingsphere/pull/14132), so future maintenance of the *[cosid-shardingsphere](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-shardingsphere)* module may be primarily handled by the official team.
:::

## CosIdKeyGenerateAlgorithm

> type: COSID

| Name        | Data Type     | Description                                              | Default Value         |
|-----------|----------|-------------------------------------------------|-------------|
| id-name   | `String` | Name of the `IdGenerator` (registered in `IdGeneratorProvider`) | `__share__` |
| as-string | `String` | Whether to generate string-type IDs                                    | `false`     |

**YAML Configuration Example**

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

| Name                       | Data Type         | Description                                  | Default Value                              |
|--------------------------|--------------|-------------------------------------|----------------------------------|
| logic-name-prefix        | `String`     | Logical table/datasource name prefix                          |                                  |
| datetime-lower           | `String`     | Lower bound of time sharding, timestamp format: `yyyy-MM-dd HH:mm:ss` |                                  |
| datetime-upper           | `String`     | Upper bound of time sharding, timestamp format: `yyyy-MM-dd HH:mm:ss` |                                  |
| sharding-suffix-pattern  | `String`     | Sharding real table/datasource suffix pattern                       |                                  |
| datetime-interval-unit   | `ChronoUnit` | Sharding key time interval unit                           |                                  |
| datetime-interval-amount | `int`        | Sharding key time interval                             |                                  |
| ts-unit                  | `String`     | Timestamp unit: `SECOND`/`MILLISECOND`        | `MILLISECOND`                    |
| zone-id                  | `String`     | Sharding key time zone                               | `ZoneId.systemDefault().getId()` |

**YAML Configuration Example**

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

## CosIdSnowflakeIntervalShardingAlgorithm

::: tip Algorithm Description
We know the bit partitioning method of *SnowflakeId*, *SnowflakeId* can parse out the timestamp, that is, *SnowflakeId* can be used as time, so *SnowflakeId* can be used as the sharding algorithm for *INTERVAL*.
(When there is no `CreateTime` available for sharding [this is an extremely rare case], or when there are extremely demanding performance requirements, using *distributed ID primary key* as the query range may be a better choice for persistence layer performance.)
:::

> type: COSID_INTERVAL_SNOWFLAKE

| Name                       | Data Type         | Description                                              | Default Value         |
|--------------------------|--------------|-------------------------------------------------|-------------|
| logic-name-prefix        | `String`     | Logical table/datasource name prefix                                      |             |
| datetime-lower           | `String`     | Lower bound of time sharding, timestamp format: `yyyy-MM-dd HH:mm:ss`             |             |
| datetime-upper           | `String`     | Upper bound of time sharding, timestamp format: `yyyy-MM-dd HH:mm:ss`             |             |
| sharding-suffix-pattern  | `String`     | Sharding real table/datasource suffix pattern                                   |             |
| datetime-interval-unit   | `ChronoUnit` | Sharding key time interval unit                                       |             |
| datetime-interval-amount | `int`        | Sharding key time interval                                         |             |
| id-name                  | `String`     | Name of the `IdGenerator` (registered in `IdGeneratorProvider`) | `__share__` |

**YAML Configuration Example**

```yaml
spring:
  shardingsphere:
    rules:
      sharding:
        sharding-algorithms:
          alg-name:
            type: COSID_INTERVAL_SNOWFLAKE
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

| Name                | Data Type     | Description         | Default Value |
|-------------------|----------|------------|-----|
| logic-name-prefix | `String` | Logical table/datasource name prefix |     |
| mod               | `int`    | Divisor         |     |

**YAML Configuration Example**

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
