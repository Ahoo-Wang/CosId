# Getting Started

CosId 5 needs **Java 21** or later. With Spring Boot it supports Boot 4.0 and later.

::: warning Preview
`5.0.0-alpha.x` is a preview with no compatibility promise. The API, configuration, storage format and cosid-server
HTTP API may change between alphas and before GA, and no data migration is provided between alphas. Do not use it in
production: stay on 4.x there ([4.x docs](https://github.com/Ahoo-Wang/CosId/tree/4.x/documentation/docs/en)).
:::

## 1. Add the dependencies

Every artifact has the group `me.ahoo.cosid`; `cosid-bom` aligns their versions. Add the starter and the store(s) you
use: there is one starter, and stores are separate artifacts, the way a JPA starter needs a database driver.

::: code-group

```kotlin [Gradle (Kotlin)]
dependencies {
    implementation(platform("me.ahoo.cosid:cosid-bom:__COSID_VERSION__"))
    implementation("me.ahoo.cosid:cosid-spring-boot-starter")
    implementation("me.ahoo.cosid:cosid-store-jdbc")   // or -redis, -mongo, -zookeeper, -remote
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

## 2. Configure

Declare each store once, then the machine slot (for time-based generators), then the generators. This uses the
application's `DataSource`:

```yaml
cosid:
  namespace: shop                 # isolates the slots and keys of one application
  stores:
    db:
      type: jdbc                  # uses the context's DataSource
      timeout: 2s
      options:
        initSchema: true          # creates the three tables; in production run schema-<dialect>.sql instead
  machine:
    leased:
      store: db                   # the machine slot is leased from the store, renewed every ttl/3
  generators:
    default:                      # "default" is the primary IdGenerator bean
      strategy: snowflake
      codec:
        - radix: { radix: 62, width: 11, pad-start: true }
    order:
      strategy: segment
      store: db
```

## 3. Use it

Every generator is a bean named after it. `long` generators (`snowflake`, `segment`) are `IdGenerator` beans;
`CosIdRuntime` gives the text views:

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
        long id = ids.generate();                                     // a 63-bit Snowflake id
        String text = runtime.stringGenerator("default").generate();  // 11 base-62 digits
        long orderNo = orderIds.generate();                           // 1, 2, 3, ...
    }
}
```

Fields annotated `@CosId` are filled by `runtime.filler()`, and by the MyBatis and Spring Data JDBC integrations before
an insert: see [@CosId field filling](integrations/annotation.md).

## Without Spring

`CosIdRuntime` is the one assembly path; Spring only binds `cosid.*` to a `RuntimeSpec`. The same configuration in code:

```java
RuntimeSpec spec = RuntimeSpec.builder("shop")
    .store("db", StoreSpec.builder("jdbc").option("initSchema", "true").build())
    .machine(MachineSpec.Leased.builder("db").build())        // default 10 bits, ttl 30s, tolerance 1s
    .generator("default", GeneratorSpec.Snowflake.builder()
        .codec(List.of(new CodecSpec.Radix(62, 11, true)))
        .build())
    .generator("order", GeneratorSpec.Segment.builder("db").build())
    .build();

RuntimeOptions options = RuntimeOptions.builder()
    .client("db", dataSource)                                 // existing clients by store name
    .build();

try (CosIdRuntime runtime = CosIdRuntime.start(spec, options)) {
    long id = runtime.generator("default").generate();
    String order = runtime.stringGenerator("order").generate();
}
```

Without a client, a store opens its own connection from its options (`url`, `uri`, `connectString`).
`close()` stops the generators, releases the slot (so the next holder can start at the last issued timestamp) and closes
what the runtime opened. See [the runtime](concepts/runtime.md).

## Next

- [Layouts and codecs](concepts/layout-and-codecs.md), [the slot lease](concepts/slot-lease.md),
  [segments](concepts/segment.md): what each strategy does and how to tune it.
- [Choosing a backend](backends/index.md): supported versions and the preconditions of each store.
- [Production checklist](operations/production.md) before going live.
- [Examples](https://github.com/Ahoo-Wang/CosId/tree/main/examples): `segment-jdbc`, `snowflake-redis` and `server`,
  each runnable with `./gradlew :examples:<name>:bootRun`.
