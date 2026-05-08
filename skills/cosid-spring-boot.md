---
name: cosid-spring-boot
description: Guide for integrating CosId in Spring Boot projects via auto-configuration. Use when users mention Spring Boot, starter, application.yml, auto-configuration, or need help configuring SnowflakeId/SegmentId/SegmentChainId in Spring Boot.
---

# CosId Spring Boot 集成指南

TRIGGER: 用户在 Spring Boot 项目中集成 CosId（关键词：Spring Boot、自动配置、starter、application.yml、snowflake、segment）

## 1. 添加依赖

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    // cosid-bom 管理所有 CosId 模块的版本，无需指定具体版本号
    implementation(platform("me.ahoo.cosid:cosid-bom"))
    implementation("me.ahoo.cosid:cosid-spring-boot-starter")
}
```

重要：不要在 `cosid-bom` 后面硬编码版本号（如 `cosid-bom:3.0.5`）。BOM 的作用是统一管理版本，硬编码版本会导致版本不一致。

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

### 按需添加后端支持（使用 Gradle Capability）

根据使用的 MachineId/Segment 分配后端，添加对应的 capability 依赖：

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

Maven 示例（Redis）：
```xml
<dependency>
    <groupId>me.ahoo.cosid</groupId>
    <artifactId>cosid-spring-boot-starter</artifactId>
    <classifier>spring-redis-support</classifier>
</dependency>
```

## 2. 核心配置（application.yml）

### SnowflakeId 配置

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
      enabled: true  # 共享的默认 SnowflakeId 生成器
    provider:
      order_id:  # 自定义名称，注入时使用 @IdGenerator("order_id")
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

### SegmentChainId 配置（推荐）

```yaml
cosid:
  namespace: ${spring.application.name}
  segment:
    enabled: true
    mode: chain  # chain（推荐，高性能）| segment（基础模式）
    distributor:
      type: redis  # redis | jdbc | mongo | zookeeper | proxy
    chain:
      safe-distance: 10  # 预取安全距离
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

### CosIdGenerator 配置（独立高性能 ID 生成器）

```yaml
cosid:
  namespace: ${spring.application.name}
  generator:
    enabled: true
```

## 3. MachineId 分配策略

配置 `cosid.machine.distributor.type` 选择后端：

| 类型 | 配置值 | 说明 |
|------|--------|------|
| Redis | `redis` | 基于 Spring Data Redis，推荐 |
| JDBC | `jdbc` | 基于 JDBC/HikariCP，需数据库表 |
| MongoDB | `mongo` | 基于 MongoDB |
| ZooKeeper | `zookeeper` | 基于 ZooKeeper Curator |
| Proxy | `proxy` | 通过 CosId Proxy 服务远程分配 |

每个后端需要确保对应的 Spring Boot starter 依赖已添加（如 `spring-boot-starter-data-redis`）。

## 4. 使用方式

### 注入 IdGenerator

```java
@Autowired
private IdGenerator idGenerator;  // 默认的 share 生成器

public void createOrder() {
    long orderId = idGenerator.generate();
    String orderIdStr = idGenerator.generateAsString();
}
```

### 使用 @CosId 注解（推荐）

```java
@CosId("order_id")  // 引用 provider 中定义的名称
private IdGenerator orderIdGenerator;
```

### 注入 StringIdGenerator

```java
@Autowired
private StringIdGenerator stringIdGenerator;

public void createUser() {
    String userId = stringIdGenerator.generateAsString();
}
```

### 使用指定 provider

```java
@Bean
public SomeService someService(@IdGenerator("order_no") IdGenerator orderNoGenerator) {
    return new SomeService(orderNoGenerator);
}
```

## 5. Actuator 监控（要点提示）

需要添加 actuator capability 依赖：

```kotlin
implementation("me.ahoo.cosid:cosid-spring-boot-starter") {
    capabilities {
        requireCapability("me.ahoo.cosid:actuator-support")
    }
}
```

然后在 `application.yml` 中暴露端点：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: cosid,cosidGenerator,cosidStringGenerator,health
```

可用的 Actuator 端点：
- `/actuator/cosid` — 所有 ID 生成器的统计信息
- `/actuator/cosidGenerator` — 通过 HTTP 生成 long 型 ID
- `/actuator/cosidStringGenerator` — 通过 HTTP 生成 String 型 ID
- `/actuator/health` — 包含 `MachineIdHealthIndicator`，报告 MachineId 守护状态

## 6. 常见问题

- **时钟回拨：** SnowflakeId 检测到时钟回拨时会通过 `ClockBackwardsSynchronizer` 自动同步等待。严重回拨会抛出 `ClockTooManyBackwardsException`。
- **MachineId 冲突：** 同一 namespace 下相同 instance 重复注册会导致冲突，确保每个实例有唯一的 instanceId（默认使用 host + port）。
- **Sequence 溢出：** 单毫秒内生成超过 4096 个 ID 时会自旋等待下一毫秒，不会丢失数据。
- **SegmentChainId 性能调优：** 增大 `safe-distance` 和 `step` 可以提高吞吐量，但会占用更多后端资源。
