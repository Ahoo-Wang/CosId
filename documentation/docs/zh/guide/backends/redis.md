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
| `cosid.segment.distributor.redis.wait-replicas`、`cosid.machine.distributor.redis.wait-replicas` | `0` | 每次号段递增、每次机器号申领和续约需要多少个副本确认（`WAIT`）；`0` 表示不等待。见[复制与故障切换](#复制与故障切换) |
| `cosid.segment.distributor.redis.wait-timeout`、`cosid.machine.distributor.redis.wait-timeout` | `500ms` | 写入等待副本确认的时长；必须短于 `timeout`、`distribute-timeout`（机器号）和 `spring.data.redis.timeout`，否则启动失败 |

::: warning 持久化
Redis 重启后如果丢失数据，计数器回退，会再次发放同样的 ID。请开启 AOF 持久化（`appendonly yes`），或为号段选择其他存储。
:::

## 复制与故障切换

Redis 采用异步复制：主节点已确认的写入，如果副本还没收到就被提升为新主节点，这次写入会丢失。对号段而言，新主节点会再次发放同样的 ID；对机器号而言，另一个实例可能申领到其所有者仍在使用的机器号。有两种设置可以缩小这个窗口，但都不能让 Redis 变成强一致的存储：

- `wait-replicas` 和 `wait-timeout`：每次号段递增、每次机器号申领和续约之后，CosId 在执行写入的连接上运行 `WAIT <wait-replicas> <wait-timeout>`。超时内确认的副本不足即视为失败：该号段不会发放（其中的 ID 被跳过），申领或续约按失败处理。在 Redis Cluster 上，等待在命名空间所在的节点上执行，此时需要 Lettuce（Spring Boot 的默认客户端）。
- 在 Redis 一侧，`min-replicas-to-write` 可以让主节点在已连接的副本不足时拒绝写入。

`WAIT` 只说明有 `wait-replicas` 个副本收到了这次写入。如果故障切换提升的副本不在其中，写入仍会丢失；`wait-replicas` 小于副本数时就可能发生。`WAIT` 也不会把写入持久化到磁盘：请继续开启 AOF。

::: warning 可用性
设置 `wait-replicas` 后，CosId 的写入需要这么多副本在线并确认。副本不足期间，每次号段获取、每次机器号续约都会失败：号段生成器用完已持有的号段后开始失败；机器号超过安全守护时长未续约即失效，快速失败（默认开启）会让其雪花算法生成器停止生成；新实例无法申领机器号，也就无法启动。请把 `wait-replicas` 设为不超过“副本数减去允许故障的副本数”，例如两个副本时设为 `1`。
:::

`WAIT` 返回之前会占用所在的连接。Spring Boot 默认的客户端 Lettuce 默认共享一个原生连接，应用的其他 Redis 命令会排在每次 `WAIT` 后面，最长等待 `wait-timeout`；CosId 在启动时发现共享连接会记录一条 WARN。Spring Boot 没有对应的配置项：请在 `LettuceConnectionFactory` 上把 `shareNativeConnection` 设为 `false`，并配置连接池（`spring.data.redis.lettuce.pool.enabled=true` 和 `org.apache.commons:commons-pool2`），例如通过 `BeanPostProcessor`：

```java
@Bean
static BeanPostProcessor unsharedLettuceConnection() {
    return new BeanPostProcessor() {
        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) {
            if (bean instanceof LettuceConnectionFactory factory) {
                factory.setShareNativeConnection(false);
            }
            return bean;
        }
    };
}
```

当 `wait-timeout` 不短于 Lettuce 的命令超时（`spring.data.redis.timeout`，未设置时为 60s）时，启动也会失败；在 Redis Cluster 上使用 Jedis 时同样启动失败，因为 `WAIT` 可能跑在与写入不同的连接上。

可运行的示例见 [CosId-Example-Redis](https://github.com/Ahoo-Wang/CosId/tree/main/examples/cosid-example-redis)。
