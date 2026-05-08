# CosId + ShardingSphere 8 分片配置（t_order 按 order_id 分表）

## 1. 添加依赖

```kotlin
dependencies {
    implementation(platform("me.ahoo.cosid:cosid-bom"))
    implementation("me.ahoo.cosid:cosid-core")
    implementation("org.apache.shardingsphere:shardingsphere-jdbc")
}
```

如果使用 Spring Boot：
```kotlin
implementation("me.ahoo.cosid:cosid-spring-boot-starter")
```

## 2. ShardingSphere YAML 配置

```yaml
rules:
  - !SHARDING
    tables:
      t_order:
        actualDataNodes: ds0.t_order_${0..7}
        tableStrategy:
          standard:
            shardingColumn: order_id
            shardingAlgorithmName: cosid-mod
        keyGenerateStrategy:
          column: order_id
          keyGeneratorName: cosid
    shardingAlgorithms:
      cosid-mod:
        type: COSID_MOD
        props:
          logic-name-prefix: t_order_
          mod: 8
    keyGenerators:
      cosid:
        type: COSID
```

**关键配置说明：**
- `logic-name-prefix: t_order_` — 逻辑表名前缀，必须以分隔符结尾
- `mod: 8` — 分片数量，与实际表数量（t_order_0 到 t_order_7）一致
- `actualDataNodes: ds0.t_order_${0..7}` — 实际数据节点范围
- `keyGenerateStrategy` — 配合 CosId KeyGenerator 自动生成分布式 ID

## 3. 使用 ModCycle 的 Java API

如果需要在代码中直接使用分片算法（不通过 ShardingSphere）：

```java
import me.ahoo.cosid.sharding.ModCycle;
import me.ahoo.cosid.sharding.PreciseSharding;
import me.ahoo.cosid.sharding.CachedSharding;

// 创建取模分片算法
PreciseSharding<Long> sharding = new CachedSharding<>(
    new ModCycle<>("t_order_", 8)
);

// 根据 order_id 路由到具体分表
String node = sharding.sharding(123456789L);
// "t_order_5"

String node2 = sharding.sharding(123456792L);
// "t_order_0"
```

## 4. 分片数量设计建议

- 建议使用 2 的幂次（4、8、16、32），便于后续成倍扩容
- 8 个分片可支持大部分中小型业务场景
- 如果数据量增长到单表 500 万以上，可考虑扩容到 16 或 32 分片
- 扩容时需要迁移数据，提前规划好分片策略
