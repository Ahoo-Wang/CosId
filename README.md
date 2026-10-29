<p align="center" style="text-align:center">
  <img width="300" src="./documentation/docs/public/logo.png"/>
</p>

# [CosId](https://cosid.ahoo.me/) Universal, flexible, high-performance distributed ID generator

[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![GitHub release](https://img.shields.io/github/release/Ahoo-Wang/CosId.svg)](https://github.com/Ahoo-Wang/CosId/releases)
[![Maven Central Version](https://img.shields.io/maven-central/v/me.ahoo.cosid/cosid-core)](https://central.sonatype.com/artifact/me.ahoo.cosid/cosid-core)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/dfd1d6237a1644409548ebfbca300dc1)](https://app.codacy.com/gh/Ahoo-Wang/CosId?utm_source=github.com&utm_medium=referral&utm_content=Ahoo-Wang/CosId&utm_campaign=Badge_Grade_Settings)
[![codecov](https://codecov.io/gh/Ahoo-Wang/CosId/branch/main/graph/badge.svg?token=L0N51NB7ET)](https://codecov.io/gh/Ahoo-Wang/CosId)
![Integration Test Status](https://github.com/Ahoo-Wang/CosId/actions/workflows/integration-test.yml/badge.svg)
<a href="https://atomgit.com/Ahoo-Wang/CosId"><img src="https://atomgit.com/Ahoo-Wang/CosId/star/new_badge.svg" alt="AtomGit G-Star" height="20px"/></a>

> [中文文档](https://cosid.ahoo.me/zh/) | [English Document](https://cosid.ahoo.me/)

> [!WARNING]
> **`5.0.0-rc.x` is a release candidate.** From RC1 on, the Java API, the `cosid.*` configuration and the storage
> formats and protocols are frozen: later 5.x releases only add to them. Production use should still wait for GA; until
> then, stay on 4.x there.

Every replica of a service needs ids (primary keys, order numbers, event ids) that never collide with those of the other
replicas. *CosId* generates them inside your process, without a network call per id, and does the coordination that
keeps them unique.

**CosId 5** is a full rewrite with no backward compatibility. In it, **uniqueness depends on no clock**: a machine slot
is leased as a time window, successive holders' windows never overlap, and the store itself keeps that rule. Clock
skew, GC pauses and failovers cost availability, never uniqueness. Every strategy runs on one `CosIdRuntime`, and Spring
Boot, cosid-server and plain Java start it the same way. The offline [`cosid-migration`](./migration/README.md) tool
moves a 4.x deployment over.

| Strategy | Id | Pick it when |
|---|---|---|
| `snowflake` | `long`, time-ordered (any `Layout`: ms or s, JS-safe 53 bits) | ids should carry their creation time, with no store call per id |
| `segment` | `long`, dense, increasing per instance | you want short numeric keys; reservations size themselves to a time budget |
| `periodic-segment` | per year / month / day (`20261027-000042`) | business numbers that restart each period |
| `cosid80` | 80-bit text | you need more than 64 bits |
| `uuidv7` | UUIDv7 (RFC 9562), time-ordered | you want standard UUIDs, with no machine, store nor lease |
| `custom` | your own `long` generator | you have one, and want it served like the others |

Stores: JDBC (MySQL 8.4+, MariaDB 10.11+, PostgreSQL 15+, SQL Server 2019+, H2), Redis 7.0+, MongoDB 6.0+, ZooKeeper
3.8+, and cosid-server; declared once, mixed freely.

## Version compatibility

| CosId | Spring Boot | Java | Branch |
|-------|-------------|------|--------|
| 5.x | 4.1.2+ | 21+ | `main` |
| 4.x | 4.x | 17+ | `4.x` (maintenance: bug fixes only) |
| 3.x | 4.x | 17+ | `3.x` |
| 2.x | 3.x | 17+ | `2.x` |
| 1.x | 2.x | 8+ | `1.x` |

Upgrading from 4.x: the [migration guide](https://cosid.ahoo.me/guide/migration/from-4x.html). 4.x and 5.0 never run in
one namespace.

## Quick start

```kotlin
dependencies {
    implementation(platform("me.ahoo.cosid:cosid-bom:$cosidVersion"))
    implementation("me.ahoo.cosid:cosid-spring-boot-starter")
    implementation("me.ahoo.cosid:cosid-store-jdbc")   // or cosid-store-redis, -mongo, -zookeeper, -remote
}
```

```yaml
cosid:
  namespace: ${spring.application.name}
  stores:
    db:
      type: jdbc              # the application's DataSource
  machine:
    leased:
      store: db               # the machine slot, leased from the store
  generators:
    default:
      strategy: snowflake
    order:
      strategy: segment
      store: db
      codec: [ { radix: { radix: 62 } }, { prefix: "O" } ]
```

```java
public OrderService(IdGenerator ids, @Qualifier("order") IdGenerator orderIds, CosIdRuntime runtime) {
    long id = ids.generate();                                   // the "default" Snowflake
    String orderNo = runtime.stringGenerator("order").generate();
}
```

[Getting Started](https://cosid.ahoo.me/guide/getting-started.html) covers Maven and plain Java too.

## Modules

| Artifact | Contents |
|---|---|
| `cosid-core` | generators, layouts and codecs, slot leases, `CosIdRuntime`, `@CosId` field filling; depends on `slf4j-api` only |
| `cosid-store-jdbc` | JDBC store: MySQL, MariaDB, PostgreSQL, SQL Server, H2, and the `JdbcDialect` SPI |
| `cosid-store-redis` | Redis store (Lettuce); see its [durability precondition](./store/redis/README.md) |
| `cosid-store-mongo` | MongoDB store (replica set) |
| `cosid-store-zookeeper` | ZooKeeper store (Curator) |
| `cosid-store-remote` | the client of cosid-server (JDK `HttpClient`) |
| `cosid-store-tck` | the store contracts and an in-memory store, for tests |
| `cosid-spring-boot-starter` | Spring Boot: core + `cosid-spring-boot-autoconfigure`; add the stores you use |
| `cosid-jackson`, `cosid-mybatis`, `cosid-spring-data-jdbc`, `cosid-activiti`, `cosid-flowable`, `cosid-axon` | integrations, one per library |
| `cosid-sharding` | sharding algorithms: `ModCycle`, `IntervalTimeline`, `CachedSharding` |
| `cosid-bom` | the versions of all of the above |
| cosid-server | the remote store service, a Docker image ([`server/`](./server/README.md)) |
| `cosid-migration.jar` | the offline 4.x → 5.0 tool ([`migration/`](./migration/README.md)) |

## Documentation

- [Why CosId](https://cosid.ahoo.me/guide/introduction.html) and [ID recipes](https://cosid.ahoo.me/guide/recipes.html).
- Concepts: [layouts and codecs](https://cosid.ahoo.me/guide/concepts/layout-and-codecs.html),
  [the machine slot lease](https://cosid.ahoo.me/guide/concepts/slot-lease.html),
  [segments](https://cosid.ahoo.me/guide/concepts/segment.html), [the runtime](https://cosid.ahoo.me/guide/concepts/runtime.html).
- [Choosing a backend](https://cosid.ahoo.me/guide/backends/): supported versions, measured compatibility, preconditions.
- [cosid-server](https://cosid.ahoo.me/guide/server.html): API, authentication, limits, Docker.
- [Configuration reference](https://cosid.ahoo.me/reference/config/basic.html): every `cosid.*` property and its default.
- [Operations](https://cosid.ahoo.me/guide/operations/production.html): production checklist, metrics, health, troubleshooting.
- [Migrating from 4.x](https://cosid.ahoo.me/guide/migration/from-4x.html).
- The design: [`docs/architecture/`](./docs/architecture/README.md) (decisions D1–D30).

The [examples](./examples/README.md) are runnable applications: `segment-jdbc`, `snowflake-redis` and `server`.

## Performance

5.0 must not be slower than 4.x in any scenario: every [benchmark](https://cosid.ahoo.me/reference/performance/benchmark.html)
runs on the released 4.1.0 and on 5.0, and the comparison is a release gate. Results go to [`docs/jmh/`](./docs/jmh).
The hot path of a segment id is one atomic add; a Snowflake id is a short critical section that never waits under its
lock.

## Contributing

Bug reports, ideas and pull requests are welcome. Read [CONTRIBUTING.md](./CONTRIBUTING.md) for the development setup, commit conventions, review and release process, and [SECURITY.md](./SECURITY.md) to report a vulnerability privately. The planned work is in the [roadmap](./docs/architecture/roadmap.md).

## Community Partners and Sponsors

<a href="https://www.jetbrains.com/?from=CosId" target="_blank">
    <img src="./docs/jetbrains-logo.png" title="JetBrains" width=130 />
</a>
