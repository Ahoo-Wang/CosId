# Redis

[`cosid-spring-redis`](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-spring-redis) stores segment counters and machine id leases in Redis, through the application's Spring Data Redis connection (`StringRedisTemplate`):

- `SpringRedisIdSegmentDistributor`: segment counters, one key per generator, `cosid:{namespace.name}.adder`.
- `SpringRedisMachineLeaseStore`: machine id leases in a `cosid:{namespace}:lease` hash, next to the keys 3.4 used. Lease times come from the Redis `TIME` command.

Keys carry a hash tag (`{...}`), so Redis Cluster works: all keys of one counter or one namespace land in one slot.

## Installation

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

## Configuration

```yaml
spring:
  data:
    redis:
      host: localhost # the connection CosId uses
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true # for Snowflake IDs and CosIdGenerator
    distributor:
      type: redis
  segment:
    enabled: true # for segment IDs
    distributor:
      type: redis
```

| Property | Default | Description |
|----------|---------|-------------|
| `cosid.segment.distributor.redis.timeout` | `1s` | Each segment command; `0` disables it |
| `cosid.machine.distributor.redis.timeout` | `1s` | Lease renewal and release; must be positive |
| `cosid.machine.distributor.redis.distribute-timeout` | `10s` | Each command of the claim at startup, whose first command also pays for connecting; must be positive |
| `cosid.segment.distributor.redis.wait-replicas`, `cosid.machine.distributor.redis.wait-replicas` | `0` | Replicas that must acknowledge each segment increment, or each machine id claim and renewal (`WAIT`); `0` does not wait. See [Replication and failover](#replication-and-failover) |
| `cosid.segment.distributor.redis.wait-timeout`, `cosid.machine.distributor.redis.wait-timeout` | `500ms` | How long a write waits for them; shorter than `timeout`, `distribute-timeout` (machine) and `spring.data.redis.timeout`, or startup fails |

::: warning Persistence
A counter that goes back after Redis restarts without its data hands out IDs a second time. Run Redis with AOF persistence (`appendonly yes`), or use another store for segments.
:::

## Replication and failover

Redis replicates asynchronously: a write the primary acknowledged can be lost when a replica that had not received it is promoted. For a segment, the new primary hands the same ids out again; for a machine id, another instance can claim the same machine id while its owner keeps using it. Two settings narrow this window; neither makes Redis strongly consistent:

- `wait-replicas` and `wait-timeout`: after each segment increment, and each machine id claim and renewal, CosId runs `WAIT <wait-replicas> <wait-timeout>` on the connection that wrote it. Fewer replicas within the timeout is a failure: the segment is not handed out (the ids in it are skipped), the claim or renewal counts as failed. On Redis Cluster the wait runs on the node of the namespace; it needs Lettuce there (Spring Boot's default client).
- On the Redis side, `min-replicas-to-write` makes the primary refuse writes while too few replicas are connected.

`WAIT` only tells that `wait-replicas` replicas received the write. A failover still loses it when it promotes a replica that was not one of them, which can happen while `wait-replicas` is lower than the number of replicas. `WAIT` does not make the write durable on disk either: keep AOF on.

::: warning Availability
With `wait-replicas` set, CosId's writes need that many replicas online and acknowledging. While fewer are, every segment fetch and every machine id renewal fails: segment generators fail once they have used up the segments they already hold; a machine id that is not renewed for the safe guard duration lapses, and fail-fast (the default) stops its snowflake generators; new instances can not claim a machine id and do not start. Set `wait-replicas` to at most the number of replicas minus the replica failures to tolerate, such as `1` with two replicas.
:::

`WAIT` holds the connection it runs on until it returns. Lettuce, Spring Boot's default client, shares one native connection by default, so the application's other Redis commands queue behind each `WAIT`, for up to `wait-timeout`; CosId logs a WARN at startup when it finds a shared connection. Spring Boot has no property for it: set `shareNativeConnection` to `false` on the `LettuceConnectionFactory`, with a connection pool (`spring.data.redis.lettuce.pool.enabled=true` and `org.apache.commons:commons-pool2`), for example with a `BeanPostProcessor`:

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

Startup also fails when `wait-timeout` is not shorter than Lettuce's command timeout (`spring.data.redis.timeout`, 60s when unset), and with Jedis on a Redis Cluster, where `WAIT` may run on another connection than the write.

See [CosId-Example-Redis](https://github.com/Ahoo-Wang/CosId/tree/main/examples/cosid-example-redis) for a runnable application.
