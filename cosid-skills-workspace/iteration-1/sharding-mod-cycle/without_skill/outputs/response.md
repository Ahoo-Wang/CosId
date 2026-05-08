# Configuring ShardingSphere with CosId for 8-Shard Order Table

To split an order table (`t_order`) into 8 shards (`t_order_0` through `t_order_7`) using `order_id` as the sharding key, you use CosId's **COSID_MOD** sharding algorithm. This algorithm is O(1) for precise lookups and performs 1200~4000x better than ShardingSphere's built-in `ModShardingAlgorithm`.

## Prerequisites

**For ShardingSphere v5.1.0+**, CosId's sharding algorithms are already merged into ShardingSphere (via [PR #14132](https://github.com/apache/shardingsphere/pull/14132)), so you only need the ShardingSphere dependency:

```xml
<dependency>
    <groupId>org.apache.shardingsphere</groupId>
    <artifactId>shardingsphere-jdbc-core-spring-boot-starter</artifactId>
    <version>5.4.1</version>
</dependency>
```

**For ShardingSphere v5.0.0**, you also need the cosid-shardingsphere module:

```xml
<dependency>
    <groupId>me.ahoo.cosid</groupId>
    <artifactId>cosid-shardingsphere</artifactId>
    <version>1.8.15</version>
</dependency>
```

## Configuration

Here is the complete YAML configuration for your use case:

```yaml
spring:
  shardingsphere:
    datasource:
      names: ds0
      ds0:
        type: com.zaxxer.hikari.HikariDataSource
        driver-class-name: com.mysql.cj.jdbc.Driver
        jdbc-url: jdbc:mysql://localhost:3306/demo_ds
        username: root
        password: root

    rules:
      sharding:
        # 1. Configure the CosId key generator for distributed IDs
        key-generators:
          cosid:
            type: COSID
            props:
              id-name: __share__

        # 2. Configure the CosId modulo sharding algorithm
        sharding-algorithms:
          t-order-mod:
            type: COSID_MOD
            props:
              mod: 8                          # Number of shards (divisor)
              logic-name-prefix: t_order_     # Prefix for actual table names

        # 3. Configure the sharded table
        tables:
          t_order:
            actual-data-nodes: ds0.t_order_$->{0..7}  # Actual tables: t_order_0..t_order_7
            table-strategy:
              standard:
                sharding-column: order_id              # Sharding key column
                sharding-algorithm-name: t-order-mod   # Reference to algorithm above
            key-generate-strategy:
              column: order_id                         # Auto-generated ID column
              key-generator-name: cosid                # Reference to key generator above
```

## How It Works

The `COSID_MOD` algorithm maps each `order_id` to a shard using modulo arithmetic:

```
shard_index = order_id % 8
```

For example:
- `order_id = 100` -> `100 % 8 = 4` -> routed to `t_order_4`
- `order_id = 207` -> `207 % 8 = 7` -> routed to `t_order_7`
- `order_id = 312` -> `312 % 8 = 0` -> routed to `t_order_0`

This ensures uniform distribution across all 8 shards.

## Configuration Properties Reference

| Property            | Type     | Description                              | Your Value  |
|---------------------|----------|------------------------------------------|-------------|
| `logic-name-prefix` | `String` | Prefix for actual table/datasource names | `t_order_`  |
| `mod`               | `int`    | Divisor (number of shards)               | `8`         |

## Important Notes

1. **The `logic-name-prefix` must end with `_`** (or whatever separator your table naming convention uses). The algorithm appends the computed index directly to this prefix, so `t_order_` + `0` = `t_order_0`.

2. **The `mod` value must match the number of actual tables.** If you declare `actual-data-nodes: ds0.t_order_$->{0..7}` (8 tables), then `mod` must be `8`.

3. **The sharding key (`order_id`) should be a numeric type** (Long/Integer). The algorithm computes `order_id % mod` to determine the target shard.

4. **For range queries** (e.g., `WHERE order_id BETWEEN 100 AND 300`), the algorithm efficiently computes which shards contain values in the range, avoiding unnecessary cross-shard queries when the range is small relative to the number of shards.
