---
name: cosid-spring-boot
description: Configure and troubleshoot CosId 5 in Spring Boot with cosid-spring-boot-starter. Use for the starter and store artifacts (cosid-store-jdbc, -redis, -mongo, -zookeeper, -remote), the cosid.stores / cosid.machine / cosid.machines / cosid.generators model, snowflake, cosid80, segment, periodic-segment, uuidv7 and custom generators, named machines and slot-offset, codec lists, generator beans, RuntimeSpecCustomizer, @CosId filling with MyBatis or Spring Data JDBC, Jackson @AsString, health, metrics and the cosid Actuator endpoint, or start-up errors. Do not use for programmatic non-Spring wiring (cosid-manual-integration) or for 4.x applications.
---

# Configure CosId for Spring Boot

Produce the smallest configuration for one chosen strategy and store. Do not enable every generator in one template.

## Workflow

1. Confirm compatibility: **CosId 5.x needs Java 21+ and Spring Boot 4.1.2+ (not 4.0.x).** CosId 4.x (Java 17) is a different
   configuration model on the `4.x` branch; to move a 4.x application, use the offline `cosid-migration` tool (plan,
   apply, verify, `config`), never a mixed fleet.
2. Confirm the strategy. Use `$cosid-strategy-guide` only when it is undecided.
3. Add the starter and the store artifact(s) the configuration uses.
4. Emit minimal YAML and one consumption pattern.
5. Verify: the context starts, the generator beans exist, ids are unique, the `cosid` health is `UP`.

In a CosId checkout, the source of truth is:

- properties: `spring/autoconfigure/src/main/java/me/ahoo/cosid/spring/autoconfigure/CosIdProperties.java` (records with
  `@DefaultValue`), mapped by `CosIdSpecs`;
- beans: `CosIdAutoConfiguration`, `CosIdGeneratorRegistrar`, `CosIdIntegrationsConfiguration`;
- runnable apps: `examples/segment-jdbc`, `examples/snowflake-redis`, `examples/server`;
- the documentation site: `documentation/docs/en/reference/config/`.

## Dependencies

There is one starter (D14). It brings `cosid-core` and `cosid-spring-boot-autoconfigure` only. Add stores like
database drivers:

```kotlin
dependencies {
    implementation(platform("me.ahoo.cosid:cosid-bom:<version>"))
    implementation("me.ahoo.cosid:cosid-spring-boot-starter")
    implementation("me.ahoo.cosid:cosid-store-jdbc")        // or -redis, -mongo, -zookeeper, -remote
}
```

Integrations are separate artifacts, one per library: `cosid-jackson`, `cosid-mybatis`, `cosid-spring-data-jdbc`,
`cosid-activiti`, `cosid-flowable`, `cosid-axon`. There are no Gradle feature variants.

## The Model

Stores are declared once and referenced by name. The machine slot serves every time-based generator of the runtime,
unless a generator chooses a named machine (`machine: <name>`, D37). Generators are named; `default` is the `@Primary`
`IdGenerator`.

```yaml
cosid:
  namespace: ${spring.application.name}   # the same on every instance of the application
  stores:
    db:
      type: jdbc                          # the context's DataSource; or client: <bean>
      timeout: 2s                         # ≤ each segment's reserve-timeout (3s)
      options: { initSchema: true }       # dev only; production runs schema-<dialect>.sql
  machine:
    leased:
      store: db
      bits: 10                            # ≤ the layout's node bits
  generators:
    default:
      strategy: snowflake
      codec: [ { radix: { radix: 62, width: 11, pad-start: true } } ]
```

### Segment on JDBC

```yaml
cosid:
  stores:
    db: { type: jdbc }
  generators:
    order:
      strategy: segment
      store: db
      codec: [ { radix: { radix: 62 } }, { prefix: "O" } ]
```

### Snowflake on Redis

```yaml
cosid:
  stores:
    redis:
      type: redis
      timeout: 1s
      options: { uri: "redis://redis:6379", wait: auto }
  machine:
    leased: { store: redis }
  generators:
    default:
      strategy: snowflake
    trace:
      strategy: snowflake
      codec: [ { friendly: { zone: Asia/Shanghai } } ]
```

State the Redis precondition every time: uniqueness needs acknowledged writes to survive (AOF `appendfsync always`, or
`WAIT` covering every replica), and `maxmemory-policy noeviction`.

### Static slot (no store)

```yaml
cosid:
  machine:
    static: { slot: "${POD_ORDINAL}", state-file: /var/lib/cosid/state }
  generators:
    default: { strategy: snowflake }
```

Without `state-file` there is no protection against a clock set back across restarts.

### A named machine (a namespace shared by services)

```yaml
cosid:
  machines:
    global:
      namespace: global-ids          # every service sharing it: the same bits, tolerance and slot-offset
      leased: { store: redis, bits: 10 }
  generators:
    trace: { strategy: snowflake, machine: global }
```

`slot-offset` (on `leased`) moves the nodes of the slots, for a rolling migration next to 4.x machine ids
(`cosid-migration` writes it).

### UUIDv7 (no machine, no store)

```yaml
cosid:
  generators:
    event: { strategy: uuidv7 }
```

Its bean is the concrete `UuidV7Generator`; its text comes from `runtime.text("event")` or a `String` `@CosId` field.

### Periodic segment and cosid80

```yaml
    bill:
      strategy: periodic-segment
      store: db
      period: YEAR_MONTH_DAY               # YEAR | YEAR_MONTH | YEAR_MONTH_DAY
      zone: Asia/Shanghai                  # always explicit
      codec: [ { date-prefix: { delimiter: "-" } }, { radix: { radix: 10, width: 6, pad-start: true } } ]
    token:
      strategy: cosid80
      codec: [ { radix: { radix: 62 } } ]  # one radix or friendly item
```

### Through cosid-server

```yaml
cosid:
  stores:
    remote:
      type: remote
      options: { baseUrl: "http://cosid-server:8688", token: "${COSID_SERVER_TOKEN}" }
  machine:
    leased: { store: remote }
```

## Codec Lists

Applied in order. The first item turns the id into text (`radix`, `friendly`, `custom`); without one the text is
decimal. `prefix`, `suffix` and `date-prefix` wrap it. A periodic segment's first item must be `date-prefix`.

## Consume Generators Safely

- Inject by name: `@Qualifier("order") IdGenerator` (`snowflake`, `segment`), `PartsIdGenerator` (`cosid80`),
  `GroupedIdGenerator` (`periodic-segment`). An unqualified `IdGenerator` is `default`.
- Text: `runtime.stringGenerator("order").generate()`, `runtime.get("token", PartsCodec.class)`,
  `runtime.get("bill", GroupedCodec.class)`.
- Do not create generators yourself next to the runtime: one runtime per context owns the slot and the stores.
- Add or change generators in code with a `static @Bean RuntimeSpecCustomizer` (no dependencies). Generators are fixed
  at start-up; `disabled: true` declares one without creating it.

## `@CosId` Boundary

`@CosId("name")` on a `long`/`Long`/`int`/`Integer`/`String` field is filled by `IdFiller` when unset (`≤ 0`, null,
empty). `cosid-mybatis` fills before INSERT (`cosid.mybatis.enabled`, default true; `list-key`, default `list`).
`cosid-spring-data-jdbc` fills before convert, and is off by default (`cosid.jdbc.enabled`). Elsewhere call the
`IdFiller` bean.

## Operational Rules

- Every instance of an application uses the same `cosid.namespace`, `bits` and `tolerance`; a mismatch is refused
  (`NamespaceMismatchException`).
- `bits` must cover the peak instance count, including a rolling update's surge.
- Store `timeout` ≤ segment `reserve-timeout`.
- Uniqueness of time-based ids does not depend on clocks (D22); skew beyond `tolerance` only pauses issuing. Run NTP.
- Shut down with SIGTERM: a clean close releases the slot with the exact last timestamp.
- There is no option to keep issuing after the lease is lost.

## Validation

- `GET /actuator/health/cosid` is `UP` (the health component is named `cosid`; switch `management.health.cosid.enabled`).
- `GET /actuator/cosid` describes every generator; `/actuator/cosid/{name}/long` or `/string` issues one (expose `cosid`).
- Metrics: 19 `cosid.*` meters, for example `cosid.machine.lease.renew.failures`, `cosid.machine.window.remaining`,
  `cosid.segment.refill.failures`.
- Start-up errors list every configuration problem at once (`Invalid cosid.* configuration:`).

## Response Contract

Return:

1. the dependencies;
2. the minimal `cosid.*` YAML;
3. one consumption snippet;
4. the store's precondition and one operational warning;
5. one validation step (health, endpoint, or a test).
