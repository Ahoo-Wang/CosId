# CosId Spring Boot + Redis Setup Guide

Here is a complete setup for generating unique order IDs in a Spring Boot e-commerce app using CosId with Redis as the backend.

---

## 1. Gradle Dependencies (Kotlin DSL)

In your `build.gradle.kts`, add the CosId BOM, the Spring Boot starter with Redis capability, and Spring Data Redis:

```kotlin
dependencies {
    // CosId BOM - manages all CosId module versions
    implementation(platform("me.ahoo.cosid:cosid-bom:3.0.5"))

    // CosId Spring Boot Starter with Redis support
    implementation("me.ahoo.cosid:cosid-spring-boot-starter") {
        capabilities {
            requireCapability("me.ahoo.cosid:spring-redis-support")
        }
    }

    // Spring Data Redis (required for Redis backend)
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
}
```

The `cosid-bom` platform manages versions for all CosId modules, so you do not need to specify versions on individual CosId artifacts. The `spring-redis-support` capability automatically pulls in the `cosid-spring-redis` module which provides Redis-based MachineId distribution and Segment allocation.

---

## 2. Application Configuration (`application.yml`)

You have two main ID generation strategies to choose from. For e-commerce order IDs, **SegmentChainId** is recommended for the highest throughput.

### Option A: SegmentChainId (Recommended for order IDs)

SegmentChainId provides the best performance (~127M+ ops/s) with lock-free prefetching:

```yaml
spring:
  application:
    name: ecommerce-app
  data:
    redis:
      host: localhost
      port: 6379

cosid:
  namespace: ${spring.application.name}
  segment:
    enabled: true
    mode: chain
    distributor:
      type: redis
    chain:
      safe-distance: 10
      prefetch-worker:
        prefetch-period: 1s
        core-pool-size: 2
    share:
      enabled: true
    provider:
      order_id:
        offset: 10000
        step: 100
        converter:
          type: to_string
          prefix: ORD-
          to-string:
            char-size: 10
            pad-start: true
```

This configuration:
- Uses Redis as the segment distributor backend
- `mode: chain` enables SegmentChainId (high-performance, lock-free prefetching)
- `safe-distance: 10` controls how aggressively new segments are prefetched
- Defines a provider `order_id` that generates zero-padded string IDs like `ORD-0000001001`
- `offset: 10000` starts IDs from 10000; `step: 100` allocates 100 IDs per segment batch

### Option B: SnowflakeId

SnowflakeId generates 64-bit IDs with a timestamp + machine ID + sequence layout. Good if you need time-orderable IDs:

```yaml
spring:
  application:
    name: ecommerce-app
  data:
    redis:
      host: localhost
      port: 6379

cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true
    distributor:
      type: redis
  snowflake:
    enabled: true
    share:
      enabled: true
    provider:
      order_id:
        converter:
          type: radix
          prefix: ORD-
```

This configuration:
- Uses Redis for MachineId allocation (each app instance gets a unique machine ID)
- Defines a SnowflakeId provider named `order_id` with a Radix62 string converter and `ORD-` prefix
- Generated IDs look like `ORD-gXaLrPq3` (Radix62 encoded)

---

## 3. Using the ID Generator in Your Code

### Inject by name (recommended)

```java
import me.ahoo.cosid.annotation.IdGenerator;
import me.ahoo.cosid.api.IdGenerator;

@Service
public class OrderService {

    @IdGenerator("order_id")
    private IdGenerator orderIdGenerator;

    public Order createOrder(OrderRequest request) {
        long orderId = orderIdGenerator.generate();
        String orderIdStr = orderIdGenerator.generateAsString();
        // ...
    }
}
```

### Inject the shared (default) generator

```java
import me.ahoo.cosid.api.IdGenerator;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class OrderService {

    @Autowired
    private IdGenerator idGenerator;

    public Order createOrder(OrderRequest request) {
        long orderId = idGenerator.generate();
        // ...
    }
}
```

### Inject StringIdGenerator for string-only usage

```java
import me.ahoo.cosid.api.StringIdGenerator;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class OrderService {

    @Autowired
    private StringIdGenerator stringIdGenerator;

    public Order createOrder(OrderRequest request) {
        String orderId = stringIdGenerator.generateAsString();
        // ...
    }
}
```

---

## 4. Notes

- **Redis dependency**: Make sure `spring-boot-starter-data-redis` is on the classpath. CosId uses Spring Data Redis infrastructure for both MachineId distribution and Segment allocation.
- **MachineId**: For SnowflakeId, each application instance gets a unique machine ID via Redis. For SegmentChainId, machine ID is not required since it uses segment-based allocation.
- **Clock backwards**: SnowflakeId automatically handles minor clock drift via `ClockBackwardsSynchronizer`. Severe clock backwards will throw `ClockTooManyBackwardsException`.
- **SegmentChainId tuning**: Increase `safe-distance` and `step` for higher throughput, at the cost of more Redis connections and potentially wasted ID ranges.
- **BOM version**: Replace `3.0.5` with the latest CosId version available on Maven Central if newer.
