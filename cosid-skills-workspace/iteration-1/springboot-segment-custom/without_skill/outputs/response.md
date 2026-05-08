# CosId SegmentChainId + MySQL 配置

以下 `application.yml` 配置使用 MySQL 作为后端，SegmentChainId 模式，ID 格式为 `ORD-` 前缀 + 10 位零填充。

## application.yml

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/cosid_db?useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver

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
    provider:
      order_id:
        converter:
          type: to_string
          prefix: ORD-
          to-string:
            char-size: 10
            pad-start: true
```

## 关键配置说明

| 配置项 | 值 | 说明 |
|--------|------|------|
| `cosid.segment.mode` | `chain` | 使用 SegmentChainId 模式（预取链式，高性能） |
| `cosid.segment.distributor.type` | `jdbc` | 使用 JDBC（MySQL）作为号段分发器 |
| `cosid.machine.distributor.type` | `jdbc` | 机器 ID 分配也使用 JDBC |
| `enable-auto-init-cosid-table` | `true` | 自动创建 `cosid` 表（首次启动时） |
| `enable-auto-init-id-segment` | `true` | 自动初始化号段记录 |
| `converter.type` | `to_string` | 十进制字符串转换（零填充） |
| `converter.prefix` | `ORD-` | ID 前缀 |
| `converter.to-string.char-size` | `10` | 数值部分 10 位 |
| `converter.to-string.pad-start` | `true` | 左侧零填充 |

## 生成的 ID 示例

```
ORD-0000000001
ORD-0000000002
ORD-0000000003
...
```

## MySQL 自动建表 DDL（参考）

如果关闭 `enable-auto-init-cosid-table`，需要手动建表：

```sql
CREATE TABLE IF NOT EXISTS cosid
(
    name            VARCHAR(100) NOT NULL COMMENT '名称',
    last_max_id     BIGINT       NOT NULL DEFAULT 0 COMMENT '上一次分配的最大ID',
    last_fetch_time BIGINT       NOT NULL DEFAULT 0 COMMENT '上一次分配时间(时间戳)',
    PRIMARY KEY (name)
) ENGINE = InnoDB COMMENT 'CosID 号段分配表';

INSERT INTO cosid (name, last_max_id, last_fetch_time)
VALUES ('order_id', 0, 0);
```

## Maven 依赖

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>me.ahoo.cosid</groupId>
            <artifactId>cosid-bom</artifactId>
            <version>2.x.x</version>
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
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
    </dependency>
    <!-- Spring Boot Starter 会通过 capability 自动引入 cosid-jdbc -->
</dependencies>
```

## Gradle 依赖

```groovy
dependencies {
    implementation platform("me.ahoo.cosid:cosid-bom:2.x.x")
    implementation "me.ahoo.cosid:cosid-spring-boot-starter"
    runtimeOnly "com.mysql:mysql-connector-j"
}
```

## 使用方式

```java
@Autowired
@Qualifier("order_id")
private StringIdGenerator orderIdGenerator;

public void example() {
    String id = orderIdGenerator.generateAsString();
    // 输出示例: ORD-0000000001
}
```
