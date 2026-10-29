# 快速开始

CosId 5 需要 **Java 21** 或更高版本。在 Spring Boot 下支持 Boot 4.0 及更高版本。

::: warning 候选发布版
`5.0.0-rc.x` 是候选发布版。从 RC1 起,Java API、`cosid.*` 配置以及存储格式与协议冻结，之后的 5.x 只做向后兼容的增量。生产环境仍建议等待 GA,在此之前请继续使用 4.x（[4.x 文档](https://github.com/Ahoo-Wang/CosId/tree/4.x/documentation/docs/zh)）。
:::

## 1. 添加依赖

所有 artifact 的 group 都是 `me.ahoo.cosid`；`cosid-bom` 统一它们的版本。添加 starter 和所用的存储：
starter 只有一个，存储是独立的 artifact，就像 JPA starter 需要数据库驱动一样。

::: code-group

```kotlin [Gradle (Kotlin)]
dependencies {
    implementation(platform("me.ahoo.cosid:cosid-bom:__COSID_VERSION__"))
    implementation("me.ahoo.cosid:cosid-spring-boot-starter")
    implementation("me.ahoo.cosid:cosid-store-jdbc")   // 或 -redis、-mongo、-zookeeper、-remote
}
```

```xml [Maven]
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>me.ahoo.cosid</groupId>
            <artifactId>cosid-bom</artifactId>
            <version>__COSID_VERSION__</version>
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
        <artifactId>cosid-store-jdbc</artifactId>
    </dependency>
</dependencies>
```

:::

## 2. 配置

每个存储只声明一次，然后是机器号槽位（供时间型生成器使用），最后是生成器。下面使用应用的 `DataSource`：

```yaml
cosid:
  namespace: shop                 # 隔离一个应用的槽位与 key
  stores:
    db:
      type: jdbc                  # 使用上下文中的 DataSource
      timeout: 2s
      options:
        initSchema: true          # 创建三张表；生产环境请改为执行 schema-<dialect>.sql
  machine:
    leased:
      store: db                   # 机器号槽位从该存储租用，每 ttl/3 续约一次
  generators:
    default:                      # "default" 是主 IdGenerator bean
      strategy: snowflake
      codec:
        - radix: { radix: 62, width: 11, pad-start: true }
    order:
      strategy: segment
      store: db
```

## 3. 使用

每个生成器都是一个以其名字命名的 bean。`long` 生成器（`snowflake`、`segment`）是 `IdGenerator` bean；
`CosIdRuntime` 提供文本视图：

```java
@Service
public class OrderService {
    private final IdGenerator ids;          // "default"
    private final IdGenerator orderIds;
    private final CosIdRuntime runtime;

    public OrderService(IdGenerator ids, @Qualifier("order") IdGenerator orderIds, CosIdRuntime runtime) {
        this.ids = ids;
        this.orderIds = orderIds;
        this.runtime = runtime;
    }

    public void place() {
        long id = ids.generate();                                     // 63 位 Snowflake ID
        String text = runtime.stringGenerator("default").generate();  // 11 位 base-62 字符
        long orderNo = orderIds.generate();                           // 1, 2, 3, ...
    }
}
```

标注了 `@CosId` 的字段由 `runtime.filler()` 填充，MyBatis 与 Spring Data JDBC 集成也会在插入前填充：
见 [@CosId 字段填充](integrations/annotation.md)。

## 不使用 Spring

`CosIdRuntime` 是唯一的装配路径；Spring 只是把 `cosid.*` 绑定到 `RuntimeSpec`。同样的配置用代码表示：

```java
RuntimeSpec spec = RuntimeSpec.builder("shop")
    .store("db", StoreSpec.builder("jdbc").option("initSchema", "true").build())
    .machine(MachineSpec.Leased.builder("db").build())        // 默认 10 位、ttl 30s、tolerance 1s
    .generator("default", GeneratorSpec.Snowflake.builder()
        .codec(List.of(new CodecSpec.Radix(62, 11, true)))
        .build())
    .generator("order", GeneratorSpec.Segment.builder("db").build())
    .build();

RuntimeOptions options = RuntimeOptions.builder()
    .client("db", dataSource)                                 // 按存储名提供已有客户端
    .build();

try (CosIdRuntime runtime = CosIdRuntime.start(spec, options)) {
    long id = runtime.generator("default").generate();
    String order = runtime.stringGenerator("order").generate();
}
```

没有提供客户端时，存储根据自己的 options（`url`、`uri`、`connectString`）自行建立连接。
`close()` 会停止生成器、释放槽位（使下一任持有者可以从最后发出的时间戳开始），并关闭运行时自己打开的资源。
见[运行时](concepts/runtime.md)。

## 下一步

- [布局与编解码器](concepts/layout-and-codecs.md)、[槽位租约](concepts/slot-lease.md)、
  [号段](concepts/segment.md)：每种策略做什么以及如何调优。
- [选择后端](backends/index.md)：各存储支持的版本与前提条件。
- 上线前阅读[生产检查清单](operations/production.md)。
- [示例](https://github.com/Ahoo-Wang/CosId/tree/main/examples)：

  | 示例 | 运行 |
  |---|---|
  | `segment-jdbc` | `./gradlew :examples:segment-jdbc:bootRun` |
  | `snowflake-redis` | `REDIS_URI=redis://localhost:6379 ./gradlew :examples:snowflake-redis:bootRun` |
  | `server`（cosid-server 的客户端，compose 文件会一并启动 server） | `./gradlew :server:dockerImage :examples:server:bootJar && docker compose -f examples/server/compose.yaml up` |
  | `plain-stores`（只有测试，没有应用；需要 Docker） | `./gradlew :examples:plain-stores:test` |
