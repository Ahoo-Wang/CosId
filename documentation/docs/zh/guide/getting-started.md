# 快速上手

本文以 Redis 作为存储，在 Spring Boot 应用中接入 CosId。其他后端的用法完全相同，只是依赖和 `type` 不同，见[选择后端](./backends/index.md)。

## 环境要求

| CosId | Spring Boot | Java | 分支 |
|---|---|---|---|
| 4.x | 4.x | 17+ | `main` |
| 3.x | 4.x | 17+ | `3.x`（维护中） |
| 2.x | 3.x | 17+ | `2.x` |
| 1.x | 2.x | 8+ | `1.x` |

从 3.x 升级：见[迁移到 4.0](./migration/4.0.md)。

## 1. 添加依赖

导入 CosId BOM，再添加 Starter 和后端模块。最新版本见 [Maven Central](https://central.sonatype.com/artifact/me.ahoo.cosid/cosid-core)。

::: code-group
```kotlin [Gradle (Kotlin)]
val cosidVersion = "__COSID_VERSION__" // Maven Central 上的最新版本
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

## 2. 配置生成器

号段链是最简单的起点：它只需要号段存储。

```yaml
spring:
  data:
    redis:
      host: localhost # CosId 使用应用自身的 Spring Data Redis 连接
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

这会注册两个号段链：共享生成器（`__share__`）和名为 `order` 的生成器。它们的计数器按命名空间保存在 Redis 中，应用的所有实例共用。

如果要使用 Snowflake ID，还需要启用机器号，因为每个实例都需要一个：

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

::: tip 只保留一个共享生成器
`IdGeneratorProvider.getShare()` 和未指定名称的 `@CosId` 返回的都是共享生成器（`__share__`）。同时启用 `segment` 和 `snowflake` 时，请关闭其中一个的共享生成器（`cosid.snowflake.share.enabled: false` 或 `cosid.segment.share.enabled: false`），否则先注册的那个会成为共享生成器。
:::

## 3. 生成 ID

注入 `IdGeneratorProvider`，按名称查找生成器：

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

每个生成器同时也是一个 Spring Bean，名称为 `{name}SegmentId` 或 `{name}SnowflakeId`：

```java
@Service
public class InvoiceService {
    private final SegmentId orderId;

    public InvoiceService(@Qualifier("orderSegmentId") SegmentId orderId) {
        this.orderId = orderId;
    }
}
```

如果希望在插入时自动填充实体字段，请在字段上标注 [`@CosId`](./integrations/annotation.md)，并使用 [MyBatis](./integrations/mybatis.md) 或 [Spring Data JDBC](./integrations/spring-data-jdbc.md) 集成。

## 4. 检查运行状态

classpath 中有 Spring Boot Actuator 时，暴露 `cosid` 端点即可查看所有生成器及其状态：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: cosid, cosidGenerator, cosidStringGenerator, health
```

`GET /actuator/cosid` 列出所有生成器，`GET /actuator/cosidGenerator/order` 生成一个 ID；启用机器号（`cosid.machine.enabled`）时，`/actuator/health` 会包含机器号租约的状态。

## 在代码中注册生成器

也可以在运行时通过存储的 `IdSegmentDistributorFactory` 创建生成器。移除号段链时请关闭它，以停止后台预取：

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

## 下一步

- [ID 生成器](./concepts/id-generator.md)：选择哪种生成器。
- [常用 ID 配方](./recipes.md)：常见 ID 格式的配置。
- [配置参考](../reference/config/basic.md)：全部属性。
- [示例](https://github.com/Ahoo-Wang/CosId/tree/main/examples)：可直接运行的 Redis、ZooKeeper 和 Proxy 示例应用。
