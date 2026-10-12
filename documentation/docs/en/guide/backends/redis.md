# Redis

[`cosid-spring-redis`](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-spring-redis) stores segment counters and machine id leases in Redis, through the application's Spring Data Redis connection (`StringRedisTemplate`):

- `SpringRedisIdSegmentDistributor`: segment counters, one key per generator, `cosid:{namespace.name}.adder`.
- `SpringRedisMachineLeaseStore`: machine id leases in a `cosid:{namespace}:lease` hash, next to the keys 3.x used. Lease times come from the Redis `TIME` command.

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

::: warning Persistence
A counter that goes back after Redis restarts without its data hands out IDs a second time. Run Redis with AOF persistence (`appendonly yes`), or use another store for segments.
:::

See [CosId-Example-Redis](https://github.com/Ahoo-Wang/CosId/tree/main/examples/cosid-example-redis) for a runnable application.
