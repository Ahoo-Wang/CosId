---
name: cosid-spring-boot
description: Configure and troubleshoot CosId in Spring Boot with cosid-spring-boot-starter. Use for Gradle feature capabilities or Maven modules, application.yml, Redis/JDBC/MongoDB/ZooKeeper/proxy distributors, SnowflakeId, CosIdGenerator, DefaultSegmentId, SegmentChainId, shared or named generators, converters, @CosId persistence integration, machine guarding, clock rollback, JDBC initialization, or Actuator. Do not use for programmatic non-Spring wiring.
---

# Configure CosId for Spring Boot

Produce the smallest configuration for one chosen strategy and backend. Do not enable every generator in a single template.

## Workflow

1. Confirm compatibility: CosId 3.x aligns with Spring Boot 4.x; CosId 2.x aligns with Spring Boot 3.x; both require Java 17.
2. Confirm the generator strategy. Use `$cosid-strategy-guide` only when it is undecided.
3. Add the starter capability or Maven backend modules required by that strategy.
4. Emit minimal YAML and one consumption pattern.
5. Verify context startup, generator registration, uniqueness, and the selected backend's state.

In a CosId checkout, treat these as the source of truth:

- capabilities: `cosid-spring-boot-starter/build.gradle.kts`;
- property names/defaults: `cosid-spring-boot-starter/src/main/java/.../*Properties.java`;
- binding examples: corresponding `*PropertiesTest.java` files;
- bean names and provider behavior: `SegmentIdBeanRegistrar` and `SnowflakeIdBeanRegistrar`.

## Dependencies

Use the BOM and repeat the starter dependency for every required Gradle capability:

```kotlin
dependencies {
    implementation(platform("me.ahoo.cosid:cosid-bom:$cosidVersion"))
    implementation("me.ahoo.cosid:cosid-spring-boot-starter") {
        capabilities {
            requireCapability("me.ahoo.cosid:spring-redis-support")
        }
    }
    implementation("me.ahoo.cosid:cosid-spring-boot-starter") {
        capabilities {
            requireCapability("me.ahoo.cosid:mybatis-support")
        }
    }
}
```

Available capabilities include `spring-redis-support`, `jdbc-support`, `mongo-support`, `zookeeper-support`, `proxy-support`, `mybatis-support`, `data-jdbc-support`, and `actuator-support`. Add only those used by the application, with one dependency block per capability.

For Maven, import `cosid-bom`, add `cosid-spring-boot-starter`, and add the concrete backend/integration module such as `cosid-spring-redis`, `cosid-jdbc`, `cosid-mongo`, `cosid-zookeeper`, `cosid-mybatis`, or `cosid-spring-data-jdbc`. Also include the application framework/runtime dependency required to create its client, such as the Redis, JDBC, or MongoDB Spring Boot starter.

## Minimal Configurations

### Snowflake with a Fixed Machine ID

Use only when deployment assigns a unique machine ID to every live instance.

```yaml
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true
    distributor:
      type: manual
      manual:
        machine-id: ${COSID_MACHINE_ID}
  snowflake:
    enabled: true
```

For Redis/JDBC/MongoDB/ZooKeeper/proxy allocation, replace the distributor type and add its capability. Keep `machine.enabled: true` for SnowflakeId and CosIdGenerator.

### SegmentChainId with Redis

Segment generators do not require machine-ID configuration.

```yaml
cosid:
  namespace: ${spring.application.name}
  segment:
    enabled: true
    mode: chain
    distributor:
      type: redis
    share:
      enabled: false
    provider:
      order:
        step: 100
```

Use `mode: segment` for `DefaultSegmentId`. The default mode is `chain`; spelling it out makes intent visible.

### SegmentChainId with JDBC

```yaml
cosid:
  namespace: ${spring.application.name}
  segment:
    enabled: true
    mode: chain
    distributor:
      type: jdbc
      jdbc:
        enable-auto-init-cosid-table: true
        enable-auto-init-id-segment: true
```

Automatic DDL is convenient for controlled environments. Prefer migrations when schema ownership matters. Machine allocation through JDBC has a separate property: `cosid.machine.distributor.jdbc.enable-auto-init-cosid-machine-table`. A custom `JdbcMachineIdInitializer` bean disables that auto-initializer through conditional back-off.

### CosIdGenerator

```yaml
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true
    distributor:
      type: redis
  generator:
    enabled: true
    type: radix62
```

Inject it as `CosIdGenerator` and call `generateAsString()`. Its `generate()` method is unsupported.

## Consume Generators Safely

Named segment and Snowflake generators are registered in `IdGeneratorProvider` and as singleton beans named `<name>SegmentId` or `<name>SnowflakeId`:

```java
IdGenerator orderId = idGeneratorProvider.getRequired("order");
long id = orderId.generate();
```

`IdGeneratorProvider.get(name)` returns `Optional<IdGenerator>`; use `getRequired(name)` when absence is an error.

The shared definition is enabled by default under `__share__`. If Snowflake and Segment are both enabled, they compete for the same shared provider entry. Disable one share or use named definitions and qualified concrete beans; do not rely on registration order.

## `@CosId` Boundary

The annotation alone does not assign IDs. Enable a persistence integration that invokes the accessor registry, such as `mybatis-support` for inserts or `data-jdbc-support` for Spring Data JDBC before-convert callbacks, then register the referenced generator:

```java
public class Order {
    @CosId("order")
    private Long id;
}
```

Do not promise annotation support for an ORM or persistence path without verifying that its CosId integration is present.

## Operational Rules

- Machine IDs must be unique within a namespace and within the configured machine-bit range.
- The machine guarder is enabled by default. Keep lease duration longer than heartbeat delay and expose failures through health monitoring when Actuator is used.
- Snowflake definitions default to clock sync. Custom second-based definitions require an epoch in seconds; millisecond definitions require milliseconds.
- Snowflake timestamp, machine, and sequence bits must total 63.
- Proxy activation uses `proxy-support`, `cosid.proxy.host`, and `distributor.type: proxy`; there is no `cosid.proxy.enabled` property.
- Actuator endpoints require `actuator-support` and explicit endpoint exposure. The `cosid` endpoint includes a delete operation that removes a registered generator; for status-only access on Spring Boot 4, set `management.endpoint.cosid.access: read-only` and protect the management endpoint with HTTP authorization or network isolation. On Spring Boot versions without endpoint access levels, deny non-read methods or do not expose it.
- Converter output must be tested for prefix, padding, radix, maximum length, and target-column capacity.

For a Spring Boot 4 status-only endpoint, start with:

```yaml
management:
  endpoints:
    access:
      default: none
    web:
      exposure:
        include: cosid
  endpoint:
    cosid:
      access: read-only
```

## Validation

Run one focused Spring Boot context test with the selected capability and properties. Assert the expected bean/provider name, generate concurrent IDs, and inspect the backend row/key/lease or Actuator state. Integration tests require the actual Redis, JDBC, MongoDB, ZooKeeper, or proxy service.

## Response Contract

Return version alignment, exact dependency capability/modules, minimal YAML for one strategy, one correct consumption example, one operational warning, and one runnable verification.
