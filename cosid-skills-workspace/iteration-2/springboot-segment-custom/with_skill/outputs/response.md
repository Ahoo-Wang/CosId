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
    // MySQL 驱动
    runtimeOnly("com.mysql:mysql-connector-j")
    // HikariCP 连接池（Spring Boot 默认包含）
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
    name: my-app
  datasource:
    url: jdbc:mysql://localhost:3306/cosid_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      pool-name: cosid-pool
      maximum-pool-size: 10

cosid:
  namespace: ${spring.application.name}
  segment:
    enabled: true
    mode: chain                  # 使用 SegmentChainId（高性能链模式）
    distributor:
      type: jdbc                 # MySQL 作为后端
      jdbc:
        enable-auto-init-cosid-table: true   # 自动创建 cosid segment 分配表
        enable-auto-init-id-segment: true    # 自动初始化 ID segment 记录
    chain:
      safe-distance: 10          # 预取安全距离
      prefetch-worker:
        prefetch-period: 1s      # 预取周期
        core-pool-size: 2        # 预取线程池大小
    provider:
      order_id:                  # 自定义 ID 生成器名称，注入时使用 @IdGenerator("order_id")
        offset: 10000            # 起始值
        step: 100                # 每次从数据库批量获取的 ID 数量
        converter:
          type: to_string        # 使用十进制字符串转换
          prefix: "ORD-"         # ID 前缀
          to-string:
            char-size: 10        # 数字部分固定 10 位
            pad-start: true      # 左侧补零
```

生成的 ID 格式示例：`ORD-0000010001`, `ORD-0000010002`, ...

## 3. JDBC 自动建表说明

配置 `enable-auto-init-cosid-table: true` 后，CosId 会自动在 MySQL 中创建如下表：

```sql
CREATE TABLE IF NOT EXISTS cosid (
    name VARCHAR(100) NOT NULL COMMENT '命名空间',
    last_max_id BIGINT NOT NULL DEFAULT 0 COMMENT '当前最大 ID',
    last_fetch_time BIGINT NOT NULL DEFAULT 0 COMMENT '最后获取时间戳',
    CONSTRAINT cosid_pk PRIMARY KEY (name)
) COMMENT 'CosId Segment 分配表';
```

配置 `enable-auto-init-id-segment: true` 后，会自动插入 `order_id` 对应的初始记录。

## 4. 使用方式

### 方式一：使用 @IdGenerator 注解（推荐）

```java
import me.ahoo.cosid.annotation.IdGenerator;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    @IdGenerator("order_id")
    private me.ahoo.cosid.IdGenerator orderIdGenerator;

    public String createOrder() {
        // 生成 ID：ORD-0000010001
        String orderId = orderIdGenerator.generateAsString();
        return orderId;
    }
}
```

### 方式二：通过 @Qualifier 注入

```java
import me.ahoo.cosid.IdGenerator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CosIdConfig {

    @Bean
    public OrderService orderService(@Qualifier("order_id") IdGenerator orderIdGenerator) {
        return new OrderService(orderIdGenerator);
    }
}
```

### 方式三：注入共享的默认生成器

```yaml
# 在 application.yml 中启用 share
cosid:
  segment:
    share:
      enabled: true
```

```java
@Autowired
private IdGenerator idGenerator;  // 默认的 share 生成器
```

## 5. 性能调优建议

| 参数 | 说明 | 调优建议 |
|------|------|----------|
| `step` | 每次从 DB 批量获取的 ID 数 | 越大 DB 访问越少，但浪费越多。高并发场景建议 100~1000 |
| `safe-distance` | 预取触发阈值 | 越大预取越早，减少等待。建议 >= step/10 |
| `prefetch-period` | 预取检查周期 | 高并发可缩短至 100ms~500ms |
| `core-pool-size` | 预取线程数 | 默认 CPU 核心数，一般无需调整 |

SegmentChainId 是高性能模式（官方基准测试 ~127M+ ops/s），适合高并发场景。

## 6. 常见问题

- **表不存在**：确保 `enable-auto-init-cosid-table: true`，或手动执行建表 SQL。
- **ID 不连续**：Segment 模式下 ID 在单个节点内连续，多节点间不保证连续（这是正常行为）。
- **ID 重复**：确保不同业务使用不同的 `provider` 名称（如 `order_id`, `user_id`），它们各自独立分配。
- **prefix 中的特殊字符**：`ORD-` 中的 `-` 是合法字符，直接写在 `prefix` 字段即可。
