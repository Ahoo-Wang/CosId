# Redis

[`cosid-spring-redis`](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-spring-redis) 通过应用自身的 Spring Data Redis 连接（`StringRedisTemplate`）把号段计数器和机器号租约保存在 Redis 中：

- `SpringRedisIdSegmentDistributor`：号段计数器，每个生成器一个键，`cosid:{namespace.name}.adder`。
- `SpringRedisMachineLeaseStore`：机器号租约，保存在 `cosid:{namespace}:lease` 哈希中，与 3.4 使用的键并存。租约时间取自 Redis 的 `TIME` 命令。

键带有哈希标签（`{...}`），因此支持 Redis Cluster：同一个计数器或同一个命名空间的键落在同一个槽。

## 安装

::: code-group
```kotlin [Gradle (Kotlin)]
dependencies {
    implementation(platform("me.ahoo.cosid:cosid-bom:$cosidVersion"))
    implementation("me.ahoo.cosid:cosid-spring-boot-starter")
    implementation("me.ahoo.cosid:cosid-spring-redis")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
}
```
```xml [Maven]
<dependencies>
    <dependency>
        <groupId>me.ahoo.cosid</groupId>
        <artifactId>cosid-spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>me.ahoo.cosid</groupId>
        <artifactId>cosid-spring-redis</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
</dependencies>
```
:::

## 配置

```yaml
spring:
  data:
    redis:
      host: localhost # CosId 使用的连接
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true # Snowflake ID 和 CosIdGenerator 需要
    distributor:
      type: redis
  segment:
    enabled: true # 号段 ID 需要
    distributor:
      type: redis
```

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `cosid.segment.distributor.redis.timeout` | `1s` | 每条号段命令；`0` 表示不限制 |
| `cosid.machine.distributor.redis.timeout` | `1s` | 租约续约和释放；必须为正数 |
| `cosid.machine.distributor.redis.distribute-timeout` | `10s` | 启动时申领机器号的每条命令，第一条命令还包含建立连接的时间；必须为正数 |

::: warning 持久化
Redis 重启后如果丢失数据，计数器回退，会再次发放同样的 ID。请开启 AOF 持久化（`appendonly yes`），或为号段选择其他存储。
:::

可运行的示例见 [CosId-Example-Redis](https://github.com/Ahoo-Wang/CosId/tree/main/examples/cosid-example-redis)。
