# Spring Boot Starter

`cosid-spring-boot-starter` 是唯一的 starter（D14）。它只引入 `cosid-core` 与 `cosid-spring-boot-autoconfigure`，
不引入其他依赖；用到哪些存储就自行添加对应的 artifact。支持 Spring Boot 4.1.2 及以上（D49，不支持 4.0.x）、Java 21+：发布的元数据要求 Boot 4.1.2 的依赖版本。

```kotlin
implementation(platform("me.ahoo.cosid:cosid-bom:__COSID_VERSION__"))
implementation("me.ahoo.cosid:cosid-spring-boot-starter")
implementation("me.ahoo.cosid:cosid-store-redis")
```

## 统一的配置模型

存储只声明一次、按名称引用，不同策略的生成器可以使用不同的存储；生成器、存储与 machine 没有 `enabled` 开关：用到什么由声明了什么决定
（关闭某个生成器用 `disabled: true`）。总开关是 `cosid.enabled`（默认 `true`）；各集成保留自己的开关（`cosid.mybatis.enabled`、
`cosid.jdbc.enabled`、`cosid.activiti.enabled`、`cosid.flowable.enabled`，见[配置参考](../../reference/config/basic.md)）。

```yaml
cosid:
  namespace: shop
  stores:
    db:    { type: jdbc, timeout: 2s }
    cache: { type: redis, timeout: 1s, options: { uri: "redis://redis:6379", wait: auto } }
  machine:
    leased:
      store: cache
      bits: 10
      ttl: 30s
      tolerance: 1s
      preferred-slot: ${POD_ORDINAL:}
  generators:
    default:
      strategy: snowflake
      codec: [ { radix: { radix: 62, width: 11, pad-start: true } } ]
    order:
      strategy: segment
      store: db
      codec: [ { radix: { radix: 62 } }, { prefix: "O" } ]
    trace:
      strategy: snowflake
      codec: [ { friendly: { zone: Asia/Shanghai } } ]
    bill:
      strategy: periodic-segment
      store: db
      period: YEAR_MONTH_DAY
      zone: Asia/Shanghai
      codec: [ { date-prefix: { delimiter: "" } }, { radix: { radix: 10, width: 6, pad-start: true } } ]
    token:
      strategy: cosid80
      codec: [ { radix: { radix: 62 } } ]
    legacy:
      strategy: segment
      store: db
      disabled: true          # 已声明但不创建:其 Bean 抛出 GeneratorDisabledException
  metrics:
    generated: false
```

全部属性与默认值见[配置参考](../../reference/config/basic.md)。

## Bean

| Bean | 类型 |
|---|---|
| `cosIdRuntime` | `CosIdRuntime`，随上下文关闭 |
| 每个生成器一个，**以生成器名命名** | `IdGenerator`（`snowflake`、`segment`、`custom`；`default` 为 `@Primary`）、`PartsIdGenerator`（`cosid80`）、`GroupedIdGenerator`（`periodic-segment`）、`UuidV7Generator`（`uuidv7`，具体类） |
| `IdFiller` | `runtime.filler()` |

```java
public OrderService(IdGenerator ids,                                  // "default"
                    @Qualifier("order") IdGenerator orderIds,
                    @Qualifier("bill") GroupedIdGenerator billNos,
                    CosIdRuntime runtime) { ... }
```

文本视图从运行时获取：`runtime.stringGenerator("order")`、`runtime.get("token", PartsCodec.class)`、
`runtime.get("bill", GroupedCodec.class)`；具体生成器通过 `runtime.get(name, TimeBasedGenerator.class)` 或
`SegmentGenerator.class` 获取。

## 存储客户端

存储使用 `cosid.stores.<name>.client` 指定名称的 Bean；未指定时使用上下文中该类型客户端的唯一 Bean：
`jdbc` 为 `DataSource`；`redis` 为 Lettuce 的 `RedisClient` / `RedisClusterClient` 或 Spring Data 的
`RedisConnectionFactory`（经由 `SpringDataRedisScripts`）；`mongo` 为 `MongoClient`；`zookeeper` 为
`CuratorFramework`。都没有时，存储按其 options 自行建立连接。`StoreProvider` Bean 可以添加自定义的存储类型。

## 在代码中修改生成器

```java
@Bean
static RuntimeSpecCustomizer moreGenerators() {          // static:它在处理 Bean 定义期间运行
    return spec -> spec.toBuilder()
        .generator("audit", GeneratorSpec.Segment.builder("db").build())
        .build();
}
```

Customizer 在运行时启动前按 `@Order` 依次执行，它们添加的生成器同样会注册 Bean。Customizer 不能依赖其他 Bean
（它是 `static @Bean`），与 4.x 相同。

自己的 `IdGenerator` 按 Bean 名加入运行时（对应 4.x 的 `IdGeneratorProvider.set`）：

```yaml
cosid:
  generators:
    legacy: { strategy: custom, ref: legacyIdGenerator, codec: [ { radix: { radix: 62 } } ] }
```

运行时像其他生成器一样提供它（Bean、`IdFiller`、端点），但不负责关闭；该 Bean 不能依赖 `CosIdRuntime`。
代码中可用 `GeneratorSpec.Instance.builder(ids).build()` 直接传入实例。

## Actuator、指标与健康检查

- 端点 `cosid`：`GET /actuator/cosid` 描述所有生成器，`/actuator/cosid/{name}` 描述单个生成器，
  `/actuator/cosid/{name}/long` 或 `/string` 生成一个 ID。端点只读（D17）；4.x 的删除操作改为
  `disabled` 属性。
- 健康检查 `cosid`（`/actuator/health/cosid`，开关 `management.health.cosid.enabled`）：机器号槽位租约。
- Micrometer：19 个 `cosid.*` 指标。见[指标与健康检查](../operations/monitoring.md)。
- 各集成（Jackson、MyBatis、Spring Data JDBC、Activiti、Flowable、Axon）在其库与对应的 CosId artifact（`cosid-jackson`、`cosid-mybatis`……）
  位于 classpath 上时自动装配。
- Native image：`CosIdRuntimeHints` 注册运行时所需的反射信息。在 AOT 下（native image，或 `spring.aot.enabled`）
  **生成器 Bean 的集合在构建期固定**：运行时启动时仍会绑定 `cosid.*`，所以部署时可以改生成器的设置，但部署时新增的生成器没有
  Bean（用 `CosIdRuntime.generator(name)` 获取），部署时删掉的生成器其 Bean 在使用时失败。增删生成器要改构建期可见的配置。

每个 Spring 上下文拥有自己的运行时（4.x 的 `provider.isolated` 是默认且唯一的模式）。
