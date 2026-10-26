# Getting Started

This guide adds CosId to a Spring Boot application, with Redis as the store. Every other backend works the same way with its own dependency and `type`, see [Choosing a backend](./backends/index.md).

## Requirements

| CosId | Spring Boot | Java | Branch |
|---|---|---|---|
| 4.x | 4.x | 17+ | `main` |
| 3.x | 4.x | 17+ | `3.x` (maintenance) |
| 2.x | 3.x | 17+ | `2.x` |
| 1.x | 2.x | 8+ | `1.x` |

Since 4.1 every release is tested on each of these:

| Spring Boot | Java |
|---|---|
| 4.0.x | 17, 21, 25 |
| 4.1.x | 17, 21, 25 |

CosId's published metadata requires the dependency versions of Spring Boot 4.0.8, the lowest it supports, and does not bring CosId's build platform (`cosid-dependencies`, with the Spring Boot BOM) along: your Spring Boot version, from its BOM or plugin, decides. To use exactly the versions CosId was built with, import `me.ahoo.cosid:cosid-dependencies` as a platform. A few platforms still come along:

- `cosid-axon`, and the starter's `axon-support` feature, bring the Axon BOM (`org.axonframework:axon-bom`), which manages only Axon's artifacts.
- `cosid-proxy-api`, `cosid-proxy` and the starter's `proxy-support` feature depend on CoApi 3.1.0, whose Gradle metadata brings the Spring Boot 4.1 BOM. A Gradle application on Spring Boot 4.0.x that uses them is raised to Boot 4.1's versions unless it imports its Boot BOM with `enforcedPlatform(...)` or applies the `io.spring.dependency-management` plugin. Maven builds are not affected: their own `dependencyManagement` wins.
- With a plain `platform(...)` import, Gradle picks the highest version requested, so an application on Spring Boot 4.0.0 to 4.0.7 is raised to 4.0.8's versions; use `enforcedPlatform(...)` or the `io.spring.dependency-management` plugin to keep yours.

Upgrading from 3.x: see [Migrating to 4.0](./migration/4.0.md).

## 1. Add the dependencies

Import the CosId BOM, then add the starter and the backend module. The latest version is on [Maven Central](https://central.sonatype.com/artifact/me.ahoo.cosid/cosid-core).

::: code-group
```kotlin [Gradle (Kotlin)]
val cosidVersion = "__COSID_VERSION__" // the latest release on Maven Central
dependencies {
    implementation(platform("me.ahoo.cosid:cosid-bom:$cosidVersion"))
    implementation("me.ahoo.cosid:cosid-spring-boot-starter")
    implementation("me.ahoo.cosid:cosid-spring-redis")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
}
```
```xml [Maven]
<properties>
    <cosid.version>__COSID_VERSION__</cosid.version>
</properties>
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

## 2. Configure a generator

A segment chain is the simplest start: it needs only the segment store.

```yaml
spring:
  data:
    redis:
      host: localhost # CosId uses the application's Spring Data Redis connection
cosid:
  namespace: ${spring.application.name}
  segment:
    enabled: true
    distributor:
      type: redis
    provider:
      order:
        step: 100
```

This registers two segment chains: the share generator (`__share__`), and one named `order`. Their counters live in Redis under the namespace, so every instance of the application shares them.

To use Snowflake IDs instead, enable the machine id as well, since every instance needs one:

```yaml
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true
    distributor:
      type: redis
  snowflake:
    enabled: true
```

::: tip One share generator
The share generator (`__share__`) is the one `IdGeneratorProvider.getShare()` and `@CosId` without a name return. If you enable both `segment` and `snowflake`, turn one share off (`cosid.snowflake.share.enabled: false` or `cosid.segment.share.enabled: false`); otherwise the first one registered becomes the share generator.
:::

## 3. Generate IDs

Inject the `IdGeneratorProvider` and look generators up by name:

```java
@Service
public class OrderService {
    private final IdGeneratorProvider provider;

    public OrderService(IdGeneratorProvider provider) {
        this.provider = provider;
    }

    public long newOrderId() {
        return provider.getRequired("order", IdGenerator.class).generate();
    }

    public String newOrderNo() {
        return provider.getRequired("order").generateAsString();
    }
}
```

Each generator is also a Spring bean, named `{name}SegmentId` or `{name}SnowflakeId`:

```java
@Service
public class InvoiceService {
    private final SegmentId orderId;

    public InvoiceService(@Qualifier("orderSegmentId") SegmentId orderId) {
        this.orderId = orderId;
    }
}
```

To fill entity fields on insert instead, annotate them with [`@CosId`](./integrations/annotation.md) and use the [MyBatis](./integrations/mybatis.md) or [Spring Data JDBC](./integrations/spring-data-jdbc.md) integration.

## 4. Check it

With Spring Boot Actuator on the classpath, expose the `cosid` endpoint to see every generator and its state:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: cosid, cosidGenerator, cosidStringGenerator, health
```

`GET /actuator/cosid` lists the generators, `GET /actuator/cosidGenerator/order` generates an ID, and `/actuator/health` includes the machine id lease when the machine id is enabled (`cosid.machine.enabled`).

## Registering a generator in code

Generators can also be created at runtime from the store's `IdSegmentDistributorFactory`. Close a segment chain you remove, so that its background prefetch stops:

```java
@Service
public class TenantIds {
    private final IdGeneratorProvider provider;
    private final IdSegmentDistributorFactory distributorFactory;
    private final String namespace;

    public TenantIds(IdGeneratorProvider provider, IdSegmentDistributorFactory distributorFactory, CosIdProperties cosIdProperties) {
        this.provider = provider;
        this.distributorFactory = distributorFactory;
        this.namespace = cosIdProperties.getNamespace();
    }

    public void register(String tenant) {
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, tenant, 0, 100);
        IdSegmentDistributor distributor = distributorFactory.create(definition);
        provider.set(tenant, new SegmentChainId(distributor));
    }

    public void unregister(String tenant) {
        if (provider.remove(tenant) instanceof SegmentChainId segmentChainId) {
            segmentChainId.close(); // stops its background prefetch
        }
    }
}
```

## Next steps

- [ID Generators](./concepts/id-generator.md): which generator to use.
- [ID recipes](./recipes.md): configurations for common ID formats.
- [Configuration reference](../reference/config/basic.md): every property.
- [Examples](https://github.com/Ahoo-Wang/CosId/tree/main/examples): runnable applications for Redis, ZooKeeper and the proxy.
