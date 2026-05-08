---
name: cosid-spring-boot
description: Guide for integrating CosId distributed ID generator with Spring Boot. Use this skill whenever the user mentions CosId, distributed ID generation, SnowflakeId, SegmentId, SegmentChainId, CosIdGenerator, machine ID allocation, or needs help configuring ID generation in a Spring Boot application. Also use when the user asks about ID conversion (Radix62, Radix36, SnowflakeFriendly), sharding with CosId, or configuring machine ID distributors (Redis, JDBC, MongoDB, ZooKeeper) for Spring Boot. Triggers on cosid YAML configuration, application.yml ID setup, or any CosId Spring Boot starter questions.
---

# CosId Spring Boot Integration

CosId is a universal, flexible, high-performance distributed ID generator for Java 17+. The Spring Boot starter (`cosid-spring-boot-starter`) provides auto-configuration for all ID generation strategies.

## Dependency Setup

Add the BOM and starter to your `build.gradle`:

```groovy
dependencies {
    implementation platform("me.ahoo.cosid:cosid-bom:${cosidVersion}")
    implementation "me.ahoo.cosid:cosid-spring-boot-starter"
    
    // Add exactly ONE distributor backend based on your infrastructure:
    implementation "me.ahoo.cosid:cosid-spring-boot-starter:springRedisSupport"  // Redis
    // implementation "me.ahoo.cosid:cosid-spring-boot-starter:jdbcSupport"     // JDBC/MySQL
    // implementation "me.ahoo.cosid:cosid-spring-boot-starter:mongoSupport"    // MongoDB
    // implementation "me.ahoo.cosid:cosid-spring-boot-starter:zookeeperSupport" // ZooKeeper
}
```

Or with Maven:

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
    <!-- Redis variant -->
    <dependency>
        <groupId>me.ahoo.cosid</groupId>
        <artifactId>cosid-spring-boot-starter</artifactId>
        <classifier>springRedisSupport</classifier>
    </dependency>
</dependencies>
```

## Choosing an ID Strategy

There are 4 ID generation strategies in CosId. The right choice depends on your requirements:

| Strategy | Throughput | Trend | Best For |
|---|---|---|---|
| **CosIdGenerator** | ~15M+/s | Time-ordered | Standalone apps, no distributed coordination needed |
| **SnowflakeId** | ~4M+/s | Time-ordered | Distributed systems needing sortable IDs, typical microservices |
| **SegmentId** | ~20M+/s | Monotonic | High-throughput with simple coordination, trend-increasing |
| **SegmentChainId** | ~127M+/s | Monotonic | Maximum throughput, lock-free prefetching, production workloads |

### Decision Guide

- **Need maximum performance and have Redis/JDBC available?** → SegmentChainId (default segment mode)
- **Need time-sortable IDs across machines?** → SnowflakeId
- **Simple standalone app?** → CosIdGenerator (no external dependencies)
- **Database-friendly monotonic IDs?** → SegmentId or SegmentChainId

## Configuration Templates

### Full-featured Redis Setup (Most Common)

This is the most typical production configuration with both SnowflakeId and SegmentChainId:

```yaml
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true
    distributor:
      type: redis
  generator:
    enabled: true
  snowflake:
    enabled: true
    share:
      enabled: true  # shared SnowflakeId as default IdGenerator
    provider:
      order_id:
        converter:
          type: radix
          prefix: ORDER
          radix:
            char-size: 11
            pad-start: true
  segment:
    enabled: true
    mode: chain  # CHAIN = SegmentChainId (recommended), SEGMENT = basic SegmentId
    distributor:
      type: redis
    share:
      enabled: true  # shared SegmentChainId as default StringIdGenerator
    provider:
      user_id:
        step: 100
        converter:
          type: to_string
          to-string:
            char-size: 10
            pad-start: true
```

### JDBC Backend Setup

For environments where only a relational database is available:

```yaml
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true
    distributor:
      type: jdbc
  segment:
    enabled: true
    mode: chain
    distributor:
      type: jdbc
      jdbc:
        enable-auto-init-cosid-table: true
        enable-auto-init-id-segment: true
```

This auto-creates the `cosid` table and segment rows. The table schema:

```sql
CREATE TABLE IF NOT EXISTS cosid (
    name VARCHAR(100) NOT NULL,
    last_max_id BIGINT NOT NULL DEFAULT 0,
    last_fetch_time BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (name)
);
```

### MongoDB Backend Setup

```yaml
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true
    distributor:
      type: mongo
      mongo:
        database: cosid_db
  segment:
    enabled: true
    mode: chain
    distributor:
      type: mongo
      mongo:
        database: cosid_db
```

### ZooKeeper Backend Setup

```yaml
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true
    distributor:
      type: zookeeper
  snowflake:
    enabled: true
  segment:
    enabled: true
    mode: chain
    distributor:
      type: zookeeper
```

### Manual Machine ID (for fixed-instance deployments)

When you have a known, fixed set of instances:

```yaml
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true
    distributor:
      type: manual
      manual:
        machine-id: 1  # must be unique per instance
  snowflake:
    enabled: true
```

### Kubernetes StatefulSet

For StatefulSet deployments, the pod ordinal is used as the machine ID:

```yaml
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true
    distributor:
      type: stateful_set
  snowflake:
    enabled: true
```

## ID Converter Types

Converters transform `long` IDs into `String` representations. Configure via `converter` in each provider definition.

| Type | Description | Example Output |
|---|---|---|
| `radix` (default) | Base62 encoding (0-9, A-Z, a-z) | `ORDER-0Gjk3R0p` |
| `radix36` | Base36 encoding (0-9, A-Z) | `BIZ-00001234` |
| `to_string` | Plain decimal string with padding | `0000000001` |
| `snowflake_friendly` | Human-readable snowflake timestamp | `20240101-120000-1-0-0` |
| `custom` | Your own `IdConverter` implementation | — |

### Converter Configuration Examples

```yaml
# Short alphanumeric ID (radix62)
converter:
  type: radix
  prefix: ORDER
  radix:
    char-size: 11
    pad-start: true

# Numeric string with date prefix
converter:
  type: to_string
  prefix: BIZ-
  date-prefix:
    enabled: true
    pattern: yyMMdd
  to-string:
    char-size: 10
    pad-start: true

# Human-readable snowflake
converter:
  type: snowflake_friendly
  friendly:
    pad-start: true

# With group-based prefix (for date-partitioned segments)
converter:
  type: to_string
  prefix: BIZ-
  group-prefix:
    enabled: true
  to-string:
    char-size: 8
    pad-start: true
```

## Using the ID Generator in Code

### Injecting the Shared IdGenerator

When `share.enabled: true`, a default `IdGenerator` bean is registered:

```java
@Service
public class OrderService {
    private final IdGenerator idGenerator;
    
    public OrderService(IdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }
    
    public Order createOrder() {
        long orderId = idGenerator.generate();
        String orderIdStr = idGenerator.generateAsString();
        // ...
    }
}
```

### Injecting Named Generators

Named generators from `provider` are available via `IdGeneratorProvider`:

```java
@Service
public class UserService {
    private final IdGenerator userIdGenerator;
    
    public UserService(IdGeneratorProvider provider) {
        this.userIdGenerator = provider.get("user_id");
    }
    
    public User createUser() {
        long userId = userIdGenerator.generate();
        // ...
    }
}
```

### Using @CosId Annotation

The `@CosId` annotation auto-assigns IDs to entity fields:

```java
import me.ahoo.cosid.annotation.CosId;

public class Order {
    @CosId("order_id")
    private Long id;
    
    // getters/setters
}
```

### SnowflakeId State Parsing

Parse snowflake IDs back into their components:

```java
SnowflakeIdState state = snowflakeId.getStateParser().parse(id);
// state.getTimestamp(), state.getMachineId(), state.getSequence()
```

## SnowflakeId Bit Layout Customization

The default MillisecondSnowflakeId uses 41-bit timestamp, 10-bit machineId, 12-bit sequence. Customize per-provider:

```yaml
cosid:
  snowflake:
    provider:
      short_lived_id:
        timestamp-unit: second  # use seconds instead of milliseconds
        epoch: 1577203200       # custom epoch (2020-01-01)
        timestamp-bit: 31
        machine-bit: 10
        sequence-bit: 22
```

Bit allocation must satisfy: `timestampBit + machineBit + sequenceBit = 63`.

## Segment Grouping (Date-partitioned IDs)

Group segments by time period for date-based ID sequences:

```yaml
cosid:
  segment:
    provider:
      daily_order:
        group:
          by: year_month_day  # or year, year_month
          pattern: yyMMdd
        converter:
          type: to_string
          prefix: BIZ-
          group-prefix:
            enabled: true
          to-string:
            char-size: 8
            pad-start: true
```

## Machine ID Management

### Guarder Configuration

The guarder keeps machine ID registrations alive via heartbeat:

```yaml
cosid:
  machine:
    enabled: true
    distributor:
      type: redis
    guarder:
      enabled: true
      safe-guard-duration: 5m  # how long the guard is valid
      initial-delay: 1s
      delay: 10s
```

### Clock Backwards Synchronization

Handle clock drift in distributed environments:

```yaml
cosid:
  machine:
    enabled: true
    clock-backwards:
      spin-threshold: 100
      broken-threshold: 2000
```

- `spin-threshold`: Small clock drift is handled by spinning/waiting
- `broken-threshold`: Large clock drift throws `ClockTooManyBackwardsException`

### State Storage

Machine state persists locally to survive restarts:

```yaml
cosid:
  machine:
    enabled: true
    state-storage:
      local:
        state-location: .cosid-machine-state  # default path
```

## Proxy Mode

For architectures that prefer a dedicated ID service:

```yaml
# Client side
cosid:
  proxy:
    enabled: true
  segment:
    enabled: true
    mode: chain
    distributor:
      type: proxy
```

## Actuator / Monitoring

Enable Spring Boot Actuator endpoints for monitoring:

```yaml
management:
  endpoints:
    web:
      exposure:
        include:
          - cosid
          - cosidGenerator
          - cosidStringGenerator
          - health
  endpoint:
    health:
      show-details: always
```

The `cosid` endpoint shows all registered ID generators and their stats.
