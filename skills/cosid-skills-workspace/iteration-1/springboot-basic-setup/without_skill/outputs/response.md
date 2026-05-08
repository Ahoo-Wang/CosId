# Setting Up CosId with Redis for Spring Boot (Gradle Kotlin DSL)

Here is a complete setup guide for generating unique order IDs in a Spring Boot e-commerce application using CosId with Redis.

## 1. Gradle Build Script (`build.gradle.kts`)

```kotlin
plugins {
    id("org.springframework.boot") version "4.0.5"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.spring") version "2.1.21"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    // CosId BOM - manages all CosId module versions
    implementation(platform("me.ahoo.cosid:cosid-bom:3.0.5"))

    // CosId Spring Boot Starter (core auto-configuration)
    implementation("me.ahoo.cosid:cosid-spring-boot-starter")

    // CosId Redis distributor (required for Redis-backed segment/snowflake)
    implementation("me.ahoo.cosid:cosid-spring-redis")

    // Spring Boot Redis starter
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Spring Boot Web
    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

> **Key points:**
> - The `cosid-bom` platform ensures all CosId modules use a consistent version (3.0.5 is the latest).
> - `cosid-spring-boot-starter` provides auto-configuration (it detects `cosid-spring-redis` on the classpath and wires up the Redis `MachineIdDistributor` and `SegmentDistributor` automatically).
> - `cosid-spring-redis` is the Redis-backed implementation for both machine ID distribution and segment-based ID distribution.

## 2. Application Configuration (`application.yaml`)

For generating **order IDs**, the **segment chain** strategy is recommended. It is the highest-throughput option (~127M+ ops/s) and produces monotonically increasing numeric IDs ideal for database primary keys.

```yaml
spring:
  application:
    name: ecommerce-order-service
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
    guarder:
      enabled: true
  segment:
    enabled: true
    mode: chain          # lock-free chain mode for max throughput
    distributor:
      type: redis
    provider:
      order_id:          # named ID generator for order IDs
        converter:
          type: to_string
          prefix: ORD-
          to-string:
            char-size: 10
            pad-start: true
```

**What this configures:**

| Section | Purpose |
|---|---|
| `cosid.namespace` | Isolation boundary per service. Machine IDs and segments are namespaced. |
| `cosid.machine` | Enables automatic machine ID allocation via Redis. Each app instance gets a unique machine ID. `guarder` enables heartbeats so stale machine IDs get reclaimed. |
| `cosid.segment` | Enables segment-based ID generation. `mode: chain` uses the lock-free `SegmentChainId` with prefetching. |
| `cosid.segment.provider.order_id` | Defines a named ID generator called `order_id`. IDs are zero-padded to 10 digits with an `ORD-` prefix, producing IDs like `ORD-0000000001`. |

### Alternative: Snowflake IDs

If you prefer time-ordered 64-bit snowflake IDs instead of segment IDs:

```yaml
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true
    distributor:
      type: redis
    guarder:
      enabled: true
  snowflake:
    enabled: true
    provider:
      order_id:
        converter:
          type: radix
          radix:
            char-size: 11
            pad-start: true
```

Snowflake IDs encode timestamp + machine ID + sequence in a single 64-bit integer. The `radix` converter encodes them as short strings.

### Alternative: CosIdGenerator (standalone, highest single-node throughput)

If you do not need coordination across multiple instances:

```yaml
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true
    distributor:
      type: redis
  generator:
    enabled: true
```

This enables `CosIdGenerator` (~15M+ ops/s standalone) which can be injected directly.

## 3. Using the ID Generator in Your Code

### Option A: Inject by Name (Recommended for Segment IDs)

```java
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.segment.SegmentId;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final SegmentId orderIdGenerator;

    public OrderService(IdGeneratorProvider provider) {
        // "order_id" matches the provider name in YAML
        this.orderIdGenerator = (SegmentId) provider.get("order_id");
    }

    public String createOrder(OrderRequest request) {
        String orderId = orderIdGenerator.generateAsString();
        // ... save order with orderId
        return orderId;
    }
}
```

### Option B: Inject Directly via `@Qualifier`

```java
import me.ahoo.cosid.segment.SegmentId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final SegmentId orderIdGenerator;

    public OrderService(@Qualifier("order_id") SegmentId orderIdGenerator) {
        this.orderIdGenerator = orderIdGenerator;
    }

    public String createOrder(OrderRequest request) {
        String orderId = orderIdGenerator.generateAsString();
        // ... save order
        return orderId;
    }
}
```

### Option C: Kotlin Variant

```kotlin
import me.ahoo.cosid.provider.IdGeneratorProvider
import me.ahoo.cosid.segment.SegmentId
import org.springframework.stereotype.Service

@Service
class OrderService(provider: IdGeneratorProvider) {

    private val orderIdGenerator: SegmentId =
        provider.get("order_id") as SegmentId

    fun createOrder(request: OrderRequest): String {
        val orderId = orderIdGenerator.generateAsString()
        // ... save order
        return orderId
    }
}
```

## 4. Redis Setup

Make sure Redis is running. For local development with Docker:

```bash
docker run -d --name redis -p 6379:6379 redis:7
```

## 5. Run the Application

```bash
./gradlew bootRun
```

Test the ID generation:

```bash
# If you expose an endpoint that calls orderIdGenerator.generateAsString()
curl http://localhost:8080/api/orders/new-id
# Response: "ORD-0000000001"
```

## Summary of Dependency Flow

```
cosid-bom (version management)
  └── cosid-spring-boot-starter (auto-configuration)
        ├── cosid-core (core interfaces: IdGenerator, SegmentId, SnowflakeId, etc.)
        └── cosid-spring-redis (Redis MachineIdDistributor + Redis SegmentDistributor)
              └── spring-boot-starter-data-redis (Redis connection)
```

The Spring Boot auto-configuration in `cosid-spring-boot-starter` detects `cosid-spring-redis` on the classpath and automatically registers Redis-based `MachineIdDistributor` and `SegmentDistributor` beans. You only need to declare the desired ID generators in your `application.yaml`.
