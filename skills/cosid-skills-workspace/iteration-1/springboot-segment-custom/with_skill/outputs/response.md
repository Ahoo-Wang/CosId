# CosId SegmentChainId + MySQL 配置指南

## 1. 添加依赖

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation(platform("me.ahoo.cosid:cosid-bom"))
    implementation("me.ahoo.cosid:cosid-spring-boot-starter") {
        capabilities {
            requireCapability("me.ahoo.cosid:jdbc-support")
        }
    }
    // Spring Boot JDBC + MySQL 驱动
    runtimeOnly("com.mysql:mysql-connector-j")
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
        <classifier>jdbc-support</classifier>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-jdbc</artifactId>
    </dependency>
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

## 2. application.yml 配置

```yaml
spring:
  application:
    name: your-service-name
  datasource:
    url: jdbc:mysql://localhost:3306/cosid_db
    username: root
    password: your_password

cosid:
  namespace: ${spring.application.name}
  segment:
    enabled: true
    mode: chain
    distributor:
      type: jdbc
      jdbc:
        # 自动创建 cosid 表（首次启动时建议开启，之后可关闭）
        enable-auto-init-cosid-table: true
        # 自动初始化 IdSegment 行记录
        enable-auto-init-id-segment: true
    chain:
      safe-distance: 10
      prefetch-worker:
        prefetch-period: 1s
        core-pool-size: 2
    share:
      enabled: true
    provider:
      order_id:
        offset: 0
        step: 100
        converter:
          type: to_string
          prefix: ORD-
          to-string:
            char-size: 10
            pad-start: true
```

### 配置说明

| 配置项 | 值 | 说明 |
|--------|-----|------|
| `cosid.segment.enabled` | `true` | 启用 Segment 模式 |
| `cosid.segment.mode` | `chain` | 使用 SegmentChainId（高性能链式模式） |
| `cosid.segment.distributor.type` | `jdbc` | 使用 MySQL/JDBC 作为后端 |
| `cosid.segment.chain.safe-distance` | `10` | 预取安全距离，值越大吞吐越高 |
| `cosid.segment.provider.order_id.converter.type` | `to_string` | 十进制数字字符串转换 |
| `cosid.segment.provider.order_id.converter.prefix` | `ORD-` | ID 前缀 |
| `cosid.segment.provider.order_id.converter.to-string.char-size` | `10` | 数字部分固定 10 位 |
| `cosid.segment.provider.order_id.converter.to-string.pad-start` | `true` | 零填充至 10 位 |

## 3. 生成的 ID 格式

生成的字符串 ID 格式为：`ORD-0000000001`、`ORD-0000000002` ... `ORD-0000001234`

- 前缀 `ORD-` 是固定部分
- 数字部分始终 10 位，不足部分左侧用 `0` 填充

## 4. MySQL 建表

当 `enable-auto-init-cosid-table: true` 时，CosId 会自动创建表。如果需要手动建表：

```sql
CREATE TABLE IF NOT EXISTS cosid
(
    name            VARCHAR(100) NOT NULL COMMENT '{namespace}.{name}',
    last_max_id     BIGINT UNSIGNED NOT NULL DEFAULT 0,
    last_fetch_time BIGINT UNSIGNED NOT NULL DEFAULT 0,
    CONSTRAINT cosid_pk
        PRIMARY KEY (name)
) ENGINE = InnoDB;
```

## 5. 在代码中使用

### 方式一：通过 `@CosId` 注入（推荐）

```java
import me.ahoo.cosid.annotation.CosId;
import me.ahoo.cosid.api.IdGenerator;

@Service
public class OrderService {

    @CosId("order_id")
    private IdGenerator orderIdGenerator;

    public String createOrder() {
        String orderId = orderIdGenerator.generateAsString();
        // orderId = "ORD-0000000001"
        return orderId;
    }
}
```

### 方式二：通过 `@IdGenerator` 注入

```java
import me.ahoo.cosid.api.IdGenerator;
import me.ahoo.cosid.spring.boot.starter.IdGenerator;

@Configuration
public class AppConfig {

    @Bean
    public OrderService orderService(@IdGenerator("order_id") IdGenerator orderIdGenerator) {
        return new OrderService(orderIdGenerator);
    }
}
```

### 方式三：使用默认 share 生成器

如果 `cosid.segment.share.enabled: true`，可以直接注入默认生成器：

```java
@Autowired
private IdGenerator idGenerator;
```

## 6. 性能调优建议

- **`step`**：每个 Segment 的步长。增大 `step`（如 100、1000）可减少对 MySQL 的访问频率，提高吞吐量。推荐起始值 100。
- **`safe-distance`**：预取安全距离。增大此值可在高并发场景下提供更大缓冲，减少 ID 耗尽的风险。
- **`core-pool-size`**：预取线程池核心线程数，默认为 CPU 核心数，一般无需调整。
