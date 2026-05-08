# CosId Sharding Configuration for 8 Shards

## Your Scenario

You have an `order` table that needs to be split into 8 shards using `order_id` as the sharding key:
- Actual tables: `t_order_0` through `t_order_7`
- Sharding algorithm: Modulo-based (ModCycle)

## 1. Add Dependencies

The sharding algorithms are in `cosid-core`, so no additional modules are needed:

**Gradle (Kotlin DSL):**

```kotlin
dependencies {
    implementation(platform("me.ahoo.cosid:cosid-bom"))
    implementation("me.ahoo.cosid:cosid-core")

    // If using Spring Boot + ShardingSphere
    implementation("me.ahoo.cosid:cosid-spring-boot-starter")
    implementation("org.apache.shardingsphere:shardingsphere-jdbc")
}
```

## 2. Option A: Programmatic Configuration (Java)

Use `ModCycle` to create a modulo-based sharding algorithm:

```java
import me.ahoo.cosid.sharding.ModCycle;
import me.ahoo.cosid.sharding.PreciseSharding;
import me.ahoo.cosid.sharding.CachedSharding;

// Create sharding algorithm: 8 shards, table prefix "t_order_"
PreciseSharding<Long> sharding = new ModCycle<>(8, "t_order_");

// Route an order ID to the correct shard
String table = sharding.sharding(123456789L);
// Result: "t_order_1" (123456789 % 8 = 1)

String table2 = sharding.sharding(123456792L);
// Result: "t_order_0" (123456792 % 8 = 0)
```

**For better performance in high-throughput scenarios, wrap with `CachedSharding`:**

```java
import me.ahoo.cosid.sharding.CachedSharding;

// Cache sharding computation results for frequently queried IDs
PreciseSharding<Long> cachedSharding = new CachedSharding<>(new ModCycle<>(8, "t_order_"));
```

## 3. Option B: ShardingSphere YAML Configuration

If you prefer declarative configuration via ShardingSphere:

```yaml
rules:
  - !SHARDING
    tables:
      t_order:
        # Define actual data nodes (adjust datasource name as needed)
        actualDataNodes: ds_${0..1}.t_order_${0..7}
        tableStrategy:
          standard:
            shardingColumn: order_id
            shardingAlgorithmName: cosid-mod
    shardingAlgorithms:
      cosid-mod:
        type: COSID_MOD
        props:
          logic-name-prefix: t_order_
          mod: 8
```

**Key configuration points:**
- `shardingColumn: order_id` - specifies which column to use for sharding
- `logic-name-prefix: t_order_` - prefix for actual table names
- `mod: 8` - number of shards (divisor for modulo operation)
- `actualDataNodes: ds_${0..1}.t_order_${0..7}` - defines the mapping between datasources and actual tables

## 4. Option C: Spring Boot Bean Configuration

For Spring Boot applications, you can register the sharding algorithm as a Spring bean:

```java
import me.ahoo.cosid.sharding.ModCycle;
import me.ahoo.cosid.sharding.PreciseSharding;
import me.ahoo.cosid.sharding.CachedSharding;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ShardingConfiguration {

    @Bean
    public PreciseSharding<Long> orderSharding() {
        return new CachedSharding<>(new ModCycle<>(8, "t_order_"));
    }
}
```

## 5. How ModCycle Works

The `ModCycle` algorithm uses modulo arithmetic to distribute IDs across shards:

```java
// From ModCycle.java
int nodeIdx = (int) (shardingValue.longValue() % divisor);
return effectiveNodes.get(nodeIdx);
```

**Example mapping for 8 shards:**

| order_id | order_id % 8 | Result Table |
|----------|--------------|--------------|
| 0        | 0            | t_order_0    |
| 1        | 1            | t_order_1    |
| 7        | 7            | t_order_7    |
| 8        | 0            | t_order_0    |
| 9        | 1            | t_order_1    |
| 123456789| 1            | t_order_1    |

## 6. Best Practices

- **Shard count:** Use powers of 2 (4, 8, 16, 32) for easier future expansion (doubling shards).
- **Even distribution:** Ensure your `order_id` values are well-distributed (e.g., using SnowflakeId) to avoid hotspots.
- **Caching:** For high-throughput read scenarios, use `CachedSharding` to cache modulo computation results.
- **Range queries:** If you frequently query by `order_id` ranges, `ModCycle` will query ALL shards since modulo doesn't preserve locality. Consider this when designing your sharding strategy.

## 7. Verification

To verify your configuration works correctly:

```java
PreciseSharding<Long> sharding = new ModCycle<>(8, "t_order_");

// Test various order IDs
for (long id = 0; id < 16; id++) {
    System.out.println("order_id=" + id + " -> " + sharding.sharding(id));
}
// Output:
// order_id=0 -> t_order_0
// order_id=1 -> t_order_1
// ...
// order_id=7 -> t_order_7
// order_id=8 -> t_order_0
// order_id=9 -> t_order_1
// ...
```

## References

- CosId Sharding module: `cosid-core/src/main/java/me/ahoo/cosid/sharding/`
- ModCycle implementation: `ModCycle.java`
- CachedSharding: `CachedSharding.java`
- ShardingSphere SPI types: `COSID_MOD`, `COSID_INTERVAL`, `COSID_INTERVAL_SNOWFLAKE`
