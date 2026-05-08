---
name: cosid-spring-boot
description: Guide for integrating CosId distributed ID generator in Spring Boot projects. Use this skill whenever the user mentions distributed IDs, unique IDs, order numbers, Snowflake IDs, segment IDs, ID generation, or needs to set up ID generation in a Spring Boot application — even if they don't explicitly mention "CosId". Also trigger when the user asks about machine ID allocation, clock synchronization for distributed systems, or ID converter configuration in Spring Boot.
---

# CosId Spring Boot Integration Guide

## When to Use This Skill

Use this skill when a developer is integrating CosId into a Spring Boot project. The key scenarios:

- Setting up CosId with Spring Boot for the first time
- Configuring SnowflakeId, SegmentId, or SegmentChainId via `application.yml`
- Choosing a backend for MachineId distribution (Redis, JDBC, MongoDB, ZooKeeper)
- Using `@CosId` or `@IdGenerator` annotations to inject ID generators
- Enabling Actuator monitoring for ID generation metrics

If the user is NOT using Spring Boot, use `cosid-manual-integration` instead.

## 1. Add Dependencies

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    // cosid-bom manages versions for all CosId modules - do NOT hardcode versions
    // Why: The BOM ensures all CosId modules use compatible versions. Hardcoding
    // defeats this and can cause version conflicts when upgrading.
    implementation(platform("me.ahoo.cosid:cosid-bom"))
    implementation("me.ahoo.cosid:cosid-spring-boot-starter")
}
```

### Maven

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>me.ahoo.cosid</groupId>
            <artifactId>cosid-bom</artifactId>
            <version>${cosid.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>me.ahoo.cosid</groupId>
        <artifactId>cosid-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

### Add Backend Support (Gradle Capability)

CosId uses Gradle feature variants to conditionally include backend modules. This avoids pulling unnecessary dependencies — for example, if you use Redis, you don't need the JDBC driver on your classpath.

**Redis:**
```kotlin
implementation("me.ahoo.cosid:cosid-spring-boot-starter") {
    capabilities {
        requireCapability("me.ahoo.cosid:spring-redis-support")
    }
}
```

**JDBC:**
```kotlin
implementation("me.ahoo.cosid:cosid-spring-boot-starter") {
    capabilities {
        requireCapability("me.ahoo.cosid:jdbc-support")
    }
}
```

**MongoDB:**
```kotlin
implementation("me.ahoo.cosid:cosid-spring-boot-starter") {
    capabilities {
        requireCapability("me.ahoo.cosid:mongo-support")
    }
}
```

**ZooKeeper:**
```kotlin
implementation("me.ahoo.cosid:cosid-spring-boot-starter") {
    capabilities {
        requireCapability("me.ahoo.cosid:zookeeper-support")
    }
}
```

Maven example (Redis):
```xml
<dependency>
    <groupId>me.ahoo.cosid</groupId>
    <artifactId>cosid-spring-boot-starter</artifactId>
    <classifier>spring-redis-support</classifier>
</dependency>
```

## 2. Core Configuration (application.yml)

### SnowflakeId Configuration

SnowflakeId generates 64-bit IDs with a timestamp + machineId + sequence layout. Each application instance needs a unique machineId, which CosId allocates automatically via the chosen backend.

```yaml
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true
    distributor:
      type: redis  # redis | jdbc | mongo | zookeeper
  snowflake:
    enabled: true
    share:
      enabled: true  # creates a shared default IdGenerator bean
    provider:
      order_id:  # custom name, inject with @IdGenerator("order_id")
        converter:
          type: radix
          prefix: ORD-
      user_friendly_id:
        converter:
          type: snowflake_friendly
      short_id:
        converter:
          type: radix
          radix:
            char-size: 11
```

### SegmentChainId Configuration (Recommended for High Throughput)

SegmentChainId allocates IDs in batches from the backend, reducing network round-trips. The "chain" mode adds lock-free prefetching for ~127M+ ops/s throughput. Unlike SnowflakeId, it does not require machineId allocation.

```yaml
cosid:
  namespace: ${spring.application.name}
  segment:
    enabled: true
    mode: chain  # chain (recommended, high-performance) | segment (basic)
    distributor:
      type: redis  # redis | jdbc | mongo | zookeeper | proxy
    chain:
      safe-distance: 10  # how aggressively to prefetch before current segment runs out
      prefetch-worker:
        prefetch-period: 1s
        core-pool-size: 2
    share:
      enabled: true
    provider:
      order_no:
        offset: 10000
        step: 100  # number of IDs allocated per batch from backend
        converter:
          type: to_string
          prefix: ORD
          to-string:
            char-size: 10
            pad-start: true
```

### CosIdGenerator Configuration (Standalone, No Backend)

CosIdGenerator is a standalone high-performance ID generator (~15M+ ops/s). It does not coordinate with other instances — use it when you need fast IDs within a single application instance.

```yaml
cosid:
  namespace: ${spring.application.name}
  generator:
    enabled: true
```

## 3. MachineId Distribution Strategy

Configure `cosid.machine.distributor.type` to choose a backend. The backend stores which machineId each application instance owns, with heartbeat-based guard to reclaim stale IDs.

| Type | Config Value | Why Choose This |
|------|-------------|-----------------|
| Redis | `redis` | Most common choice. Fast, already in most Spring Boot stacks. |
| JDBC | `jdbc` | When Redis is not available. Uses your existing database. |
| MongoDB | `mongo` | When your stack is MongoDB-based. |
| ZooKeeper | `zookeeper` | When you already run ZooKeeper (e.g. Kafka/Hadoop clusters). |
| Proxy | `proxy` | When you want a centralized CosId Proxy service. |

Each backend requires the corresponding Spring Boot starter (e.g. `spring-boot-starter-data-redis`).

## 4. Usage

### Inject IdGenerator

```java
@Autowired
private IdGenerator idGenerator;  // default shared generator (from share.enabled: true)

public void createOrder() {
    long orderId = idGenerator.generate();
    String orderIdStr = idGenerator.generateAsString();
}
```

### Use @CosId Annotation (Recommended for Named Providers)

Why: `@CosId` is the idiomatic way to inject a named ID generator. It's type-safe and self-documenting — the annotation value matches the provider name in your YAML config.

```java
@CosId("order_id")  // references the provider name defined in YAML
private IdGenerator orderIdGenerator;
```

### Inject StringIdGenerator

```java
@Autowired
private StringIdGenerator stringIdGenerator;

public void createUser() {
    String userId = stringIdGenerator.generateAsString();
}
```

### Inject a Named Provider via @IdGenerator

```java
@Bean
public SomeService someService(@IdGenerator("order_no") IdGenerator orderNoGenerator) {
    return new SomeService(orderNoGenerator);
}
```

## 5. Actuator Monitoring (Quick Reference)

Why: In production, you want visibility into ID generation — how many generators are active, whether machineId is healthy, and whether you can generate test IDs via HTTP.

Add the actuator capability:

```kotlin
implementation("me.ahoo.cosid:cosid-spring-boot-starter") {
    capabilities {
        requireCapability("me.ahoo.cosid:actuator-support")
    }
}
```

Expose endpoints in `application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: cosid,cosidGenerator,cosidStringGenerator,health
```

Available endpoints:
- `/actuator/cosid` — statistics for all ID generators
- `/actuator/cosidGenerator` — generate a long ID via HTTP (useful for testing)
- `/actuator/cosidStringGenerator` — generate a String ID via HTTP
- `/actuator/health` — includes `MachineIdHealthIndicator`, reports DOWN when machineId guardian detects lease loss

## 6. Common Issues

- **Clock backwards:** SnowflakeId detects clock drift and synchronizes via `ClockBackwardsSynchronizer`. Severe backwards drift throws `ClockTooManyBackwardsException`. Fix: sync NTP with slew mode, or increase `cosid.machine.clock-backwards.broken-threshold`.
- **MachineId conflict:** Same instanceId registered twice under the same namespace. Ensure each instance has a unique instanceId (defaults to host + port, so this is usually automatic).
- **Sequence overflow:** More than 4096 IDs per millisecond spins until the next millisecond — no data loss, just a brief pause.
- **SegmentChainId tuning:** Increase `safe-distance` and `step` for higher throughput. Trade-off: more IDs are pre-fetched, so some may be wasted if the application restarts.
