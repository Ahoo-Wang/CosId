---
name: cosid-spring-boot
description: Guide for integrating CosId in Spring Boot projects via auto-configuration. Use when users mention Spring Boot, starter, application.yml, auto-configuration, or need help configuring SnowflakeId/SegmentId/SegmentChainId in Spring Boot.
---

# CosId Spring Boot Integration Guide

TRIGGER: User is integrating CosId in a Spring Boot project (keywords: Spring Boot, auto-configuration, starter, application.yml, snowflake, segment)

## 1. Add Dependencies

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    // cosid-bom manages versions for all CosId modules - do NOT hardcode versions
    implementation(platform("me.ahoo.cosid:cosid-bom"))
    implementation("me.ahoo.cosid:cosid-spring-boot-starter")
}
```

Important: Do NOT hardcode a version after `cosid-bom` (e.g. `cosid-bom:3.0.5`). The BOM exists to unify versions; hardcoding defeats that purpose.

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

Depending on which backend you use for MachineId/Segment distribution, add the corresponding capability:

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
      enabled: true  # shared default SnowflakeId generator
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

### SegmentChainId Configuration (Recommended)

```yaml
cosid:
  namespace: ${spring.application.name}
  segment:
    enabled: true
    mode: chain  # chain (recommended, high-performance) | segment (basic)
    distributor:
      type: redis  # redis | jdbc | mongo | zookeeper | proxy
    chain:
      safe-distance: 10  # prefetch safety distance
      prefetch-worker:
        prefetch-period: 1s
        core-pool-size: 2
    share:
      enabled: true
    provider:
      order_no:
        offset: 10000
        step: 100
        converter:
          type: to_string
          prefix: ORD
          to-string:
            char-size: 10
            pad-start: true
```

### CosIdGenerator Configuration (Standalone High-Performance ID Generator)

```yaml
cosid:
  namespace: ${spring.application.name}
  generator:
    enabled: true
```

## 3. MachineId Distribution Strategy

Configure `cosid.machine.distributor.type` to choose a backend:

| Type | Config Value | Description |
|------|-------------|-------------|
| Redis | `redis` | Spring Data Redis-based, recommended |
| JDBC | `jdbc` | JDBC/HikariCP-based, requires database table |
| MongoDB | `mongo` | MongoDB-based |
| ZooKeeper | `zookeeper` | ZooKeeper Curator-based |
| Proxy | `proxy` | Remote distribution via CosId Proxy service |

Each backend requires the corresponding Spring Boot starter dependency (e.g. `spring-boot-starter-data-redis`).

## 4. Usage

### Inject IdGenerator

```java
@Autowired
private IdGenerator idGenerator;  // default shared generator

public void createOrder() {
    long orderId = idGenerator.generate();
    String orderIdStr = idGenerator.generateAsString();
}
```

### Use @CosId Annotation (Recommended)

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

### Inject a Named Provider

```java
@Bean
public SomeService someService(@IdGenerator("order_no") IdGenerator orderNoGenerator) {
    return new SomeService(orderNoGenerator);
}
```

## 5. Actuator Monitoring (Quick Reference)

Add the actuator capability dependency:

```kotlin
implementation("me.ahoo.cosid:cosid-spring-boot-starter") {
    capabilities {
        requireCapability("me.ahoo.cosid:actuator-support")
    }
}
```

Then expose the endpoints in `application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: cosid,cosidGenerator,cosidStringGenerator,health
```

Available actuator endpoints:
- `/actuator/cosid` — statistics for all ID generators
- `/actuator/cosidGenerator` — generate a long ID via HTTP
- `/actuator/cosidStringGenerator` — generate a String ID via HTTP
- `/actuator/health` — includes `MachineIdHealthIndicator`, reports MachineId guardian status

## 6. Common Issues

- **Clock backwards:** SnowflakeId detects clock drift and synchronizes via `ClockBackwardsSynchronizer`. Severe backwards drift throws `ClockTooManyBackwardsException`.
- **MachineId conflict:** Registering the same instanceId under the same namespace causes conflicts. Ensure each instance has a unique instanceId (defaults to host + port).
- **Sequence overflow:** Generating more than 4096 IDs per millisecond spins until the next millisecond — no data loss occurs.
- **SegmentChainId tuning:** Increasing `safe-distance` and `step` improves throughput but consumes more backend resources.
