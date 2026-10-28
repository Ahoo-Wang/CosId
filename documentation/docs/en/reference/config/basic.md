# Configuration Reference: Runtime, Stores, Machine

Every `cosid.*` property of the Spring Boot starter, with its default, as bound by the `CosIdProperties` records of
`cosid-spring-boot-autoconfigure`. Generators and codecs: [generators](generators.md). cosid-server:
[`cosid.server.*`](server.md). Durations take Spring's forms (`500ms`, `2s`, `1m`, ISO-8601).

A `cosid.*` key that binds to nothing fails the start, so a typo does not fall back to a default silently. The
exceptions are `cosid.stores.<name>.options.*` (each backend checks its own, below), the keys of the integrations and the
server listed below, and system properties and environment variables, which the rest of the process shares.

## Top level

| Property | Default | Meaning |
|---|---|---|
| `cosid.enabled` | `true` | the master switch; there are no per-feature switches |
| `cosid.namespace` | `spring.application.name`, else `cosid` with a `WARN` | isolates slots and keys: every instance of one application uses the same one; a namespace is 1 to 100 letters, digits, `.`, `_`, `:`, `-`, starting with a letter or digit (what every backend stores), for named machines too; an application name that is no valid namespace fails the start: set `cosid.namespace` |
| `cosid.stores.<name>.*` | | the stores, by name (below) |
| `cosid.machine.*` | | the default machine slot, needed by time-based generators (below) |
| `cosid.machines.<name>.*` | | named machine slots, each in a namespace of its own (below) |
| `cosid.generators.<name>.*` | | the generators, by name ([generators](generators.md)); `default` is the primary bean |
| `cosid.metrics.generated.enabled` | `false` | count every generator's ids as `cosid.generator.generated`; adds a counter to each call |

## Stores: `cosid.stores.<name>`

| Property | Default | Meaning |
|---|---|---|
| `type` | | `jdbc`, `redis`, `mongo`, `zookeeper`, `remote`, or a `StoreProvider` bean's type |
| `timeout` | `2s` | every store operation; at most the `reserve-timeout` of the segments on it |
| `client` | | the bean used as the client; by default the single bean of the type's client in the context (`DataSource`; Lettuce client or `RedisConnectionFactory`; `MongoClient`; `CuratorFramework`) |
| `options.*` | | backend options, strings |

Backend options. Case, `-` and `_` do not count, so `init-schema`, `INIT_SCHEMA` and the environment variable
`COSID_STORES_DB_OPTIONS_INITSCHEMA` all set `initSchema`; `.` still separates (`retry.baseSleepTime`). A key the backend
does not know fails the start and lists the ones it does:

| Type | Options (default) |
|---|---|
| `jdbc` | `url`, `username`, `password`; `dialect` (detected); `initSchema` (`false`); `rangeTable` (`cosid_range`), `slotTable` (`cosid_slot`), `namespaceTable` (`cosid_namespace`) |
| `redis` | `uri`; `cluster` (`false`); `wait` (`auto` / `off` / a replica count); `sweepBatch` (`16`) |
| `mongo` | `uri`; `database` (`cosid_db`); `writeConcern` (`majority`; weaker settings log a `WARN`) |
| `zookeeper` | `connectString` (`localhost:2181`); `sessionTimeout` (`60s`); `connectionTimeout` (`15s`); `blockUntilConnectedWait` (`10s`); `retry.baseSleepTime` (`100ms`), `retry.maxRetries` (`5`), `retry.maxSleep` (`500ms`) |
| `remote` | `baseUrl` (required); `token`; `connectTimeout` (`5s`); `requestTimeout` (the store `timeout`) |

## Machine slot: `cosid.machine`

Exactly one of `leased` and `static`. A runtime with a time-based generator (`snowflake`, `cosid80`) that chooses no
named machine needs it. Without one, no slot is taken. `cosid.machine.namespace` (default `cosid.namespace`) moves the
slot into another namespace.

### `cosid.machine.leased`

| Property | Default | Meaning |
|---|---|---|
| `store` | | the store name the slot is leased from |
| `bits` | `10` | slots are `[0, 2^bits)`; at most each time-based layout's node bits |
| `ttl` | `30s` | the lease time to live; renewed every `ttl/3` |
| `tolerance` | `1s` | the window ceiling lies this far past the expiry (D20); one value per namespace |
| `acquire-timeout` | the `ttl` | how long the start waits for a slot while every slot is held; `0s` tries once; align it with the startup probe of a rolling update |
| `slot-offset` | `0` | added to the slot to give the node of the ids: the nodes are `[slot-offset, slot-offset + 2^bits)`, apart from 4.x machine ids during a rolling migration; one value per namespace, never changed once used |
| `preferred-slot` | none | the slot to try first, such as a StatefulSet ordinal (`${POD_ORDINAL:}`) |
| `owner-key` | `host:port`, else `host:pid` | the logical identity, for the sticky choice and observation |

### `cosid.machine.static`

| Property | Default | Meaning |
|---|---|---|
| `bits` | `10` | the slot bits |
| `slot` | | the slot, which the deployment keeps unique |
| `state-file` | none | the window file; **without it, no protection against a clock set back across restarts** |
| `ahead` | `10s` | how far ahead the ceiling is written |
| `not-before` | none | the floor while the state file does not exist yet (written by the migration tool) |

## Named machines: `cosid.machines.<name>`

A slot of its own for the generators that choose it with `cosid.generators.<g>.machine: <name>`, such as a slot in a
namespace shared by several services for cosid80 ids unique across them. Only the machines an enabled generator chooses
start, so one configuration can serve services that do not use them all.

| Property | Default | Meaning |
|---|---|---|
| `namespace` | | required: the namespace of the slot, other than the default machine's; every service sharing it uses the same `bits`, `tolerance` and `slot-offset` |
| `leased.*` | | as `cosid.machine.leased` |
| `static.*` | | as `cosid.machine.static`; two static machines need two state files |

```yaml
cosid:
  machine:
    leased: { store: cache }
  machines:
    global:
      namespace: global-ids
      leased: { store: cache, slot-offset: 1024 }
  generators:
    token: { strategy: cosid80, machine: global }
```

A restricted cosid-server token must list both namespaces (`[order-service, global-ids]`).

## Integrations

| Property | Default | Meaning |
|---|---|---|
| `cosid.mybatis.enabled` | `true` | the MyBatis interceptor, when MyBatis is present |
| `cosid.mybatis.list-key` | `list` | the parameter key of batch inserts |
| `cosid.jdbc.enabled` | `false` | the Spring Data JDBC callback |
| `cosid.jdbc.id-generator` | `default` | the generator of an unset `@Id` |
| `cosid.activiti.enabled` | `true` | the Activiti id generator |
| `cosid.activiti.generator-name` | `default` (system property `cosid.activiti`) | |
| `cosid.flowable.enabled` | `true` | the Flowable id generator |
| `cosid.flowable.generator-name` | `default` (system property `cosid.flowable`) | |
| `cosid.axon.generator-name` | `default` (system property `cosid.axon`) | the Axon identifier factory's generator |

## Actuator

| Property | Default | Meaning |
|---|---|---|
| `management.health.cosid.enabled` | `true` | the `cosid` health indicator |
| `management.endpoints.web.exposure.include` | Spring's | add `cosid` to expose the endpoint |
