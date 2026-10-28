# Spring Boot Starter

`cosid-spring-boot-starter` is the only starter (D14). It brings `cosid-core` and `cosid-spring-boot-autoconfigure`
and nothing else; add the store artifacts you use. It supports Spring Boot 4.0 and later on Java 21+.

```kotlin
implementation(platform("me.ahoo.cosid:cosid-bom:__COSID_VERSION__"))
implementation("me.ahoo.cosid:cosid-spring-boot-starter")
implementation("me.ahoo.cosid:cosid-store-redis")
```

## One configuration model

Stores are declared once and referenced by name, generators of different strategies can use different stores, and
there are no per-feature `enabled` switches: what is used follows from what is declared. The one master switch is
`cosid.enabled` (default `true`).

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
      disabled: true          # declared, not created: its bean throws GeneratorDisabledException
  metrics:
    generated: false
```

Every property and default: [configuration reference](../../reference/config/basic.md).

## Beans

| Bean | Type |
|---|---|
| `cosIdRuntime` | `CosIdRuntime`, closed with the context |
| one per generator, **named after it** | `IdGenerator` (`snowflake`, `segment`; `default` is `@Primary`), `PartsIdGenerator` (`cosid80`), `GroupedIdGenerator` (`periodic-segment`) |
| `IdFiller` | `runtime.filler()` |

```java
public OrderService(IdGenerator ids,                                  // "default"
                    @Qualifier("order") IdGenerator orderIds,
                    @Qualifier("bill") GroupedIdGenerator billNos,
                    CosIdRuntime runtime) { ... }
```

Text views come from the runtime: `runtime.stringGenerator("order")`, `runtime.get("token", PartsCodec.class)`,
`runtime.get("bill", GroupedCodec.class)`; the concrete generators from `runtime.get(name, TimeBasedGenerator.class)` or
`SegmentGenerator.class`.

## Store clients

A store uses the bean named by `cosid.stores.<name>.client`; otherwise the single bean of its type's client in the
context: a `DataSource` for `jdbc`; a Lettuce `RedisClient` / `RedisClusterClient` or Spring Data's
`RedisConnectionFactory` for `redis` (through `SpringDataRedisScripts`); a `MongoClient` for `mongo`; a
`CuratorFramework` for `zookeeper`. Without one, the store connects from its options. `StoreProvider` beans add
store types of your own.

## Changing generators in code

```java
@Bean
static RuntimeSpecCustomizer moreGenerators() {          // static: it runs while bean definitions are processed
    return spec -> spec.toBuilder()
        .generator("audit", GeneratorSpec.Segment.builder("db").build())
        .build();
}
```

Customizers run in `@Order` before the runtime starts, and the generators they add get beans too. A customizer must
not depend on other beans (it is a `static @Bean`), as in 4.x.

An `IdGenerator` of your own joins the runtime by its bean name (4.x `IdGeneratorProvider.set`):

```yaml
cosid:
  generators:
    legacy: { strategy: custom, ref: legacyIdGenerator, codec: [ { radix: { radix: 62 } } ] }
```

The runtime serves it like the others (bean, `IdFiller`, endpoint) but does not close it, and the bean must not depend
on `CosIdRuntime`. In code, `GeneratorSpec.Instance.builder(ids).build()` takes the instance itself.

## Actuator, metrics, health

- Endpoint `cosid`: `GET /actuator/cosid` describes every generator, `/actuator/cosid/{name}` one, and
  `/actuator/cosid/{name}/long` or `/string` generates one id. It is read-only (D17); the 4.x delete operation became the
  `disabled` property.
- Health `cosid` (`/actuator/health/cosid`, switch `management.health.cosid.enabled`): the slot lease.
- Micrometer: 19 `cosid.*` meters. See [metrics and health](../operations/monitoring.md).
- The integrations (Jackson, MyBatis, Spring Data JDBC, Activiti, Flowable, Axon) set themselves up when their library
  and the matching CosId artifact (`cosid-jackson`, `cosid-mybatis`, …) are on the classpath.
- Native images: `CosIdRuntimeHints` registers the reflection the runtime needs.

Each Spring context gets its own runtime (4.x's `provider.isolated` is the default and only mode).
