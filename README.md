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

Every replica of a service needs IDs (primary keys, order numbers, event ids) that never collide with those of the other replicas. *CosId* generates them inside your process, without a network call per ID, and does the coordination that keeps them unique: it leases a distinct machine id to every Snowflake instance from a store you already run, takes ranges of segment IDs ahead of time, and handles clocks that move backwards.

| Generator | ID | Throughput on one instance (historical JMH) | Pick it when |
|-----------|----|---------------------------:|--------------|
| `SegmentChainId` | `long`, near-sequential | 127,439,148 ops/s | You want short numeric keys or business numbers. The default segment mode |
| `SnowflakeId` | `long`, time-ordered | 4,096,000 ops/s (theoretical maximum) | IDs should carry their creation time, with no store call after startup |
| `CosIdGenerator` | `String`, 15 characters, time-ordered | — | You run very many instances (up to a million machine ids) |

Stores: Redis, JDBC (MySQL, MariaDB, PostgreSQL), MongoDB, ZooKeeper, the CosId proxy, or your own. Integrations: Spring Boot, MyBatis, Spring Data JDBC, Jackson, ShardingSphere, Activiti, Flowable and Axon.

## Version compatibility

| CosId | Spring Boot | Java | Branch |
|-------|-------------|------|--------|
| 4.x | 4.x | 17+ | `main` |
| 3.x | 4.x | 17+ | `3.x` (maintenance) |
| 2.x | 3.x | 17+ | `2.x` |
| 1.x | 2.x | 8+ | `1.x` |

Upgrading from 3.x: see [MIGRATION-4.0.md](./docs/MIGRATION-4.0.md).

## Quick start

```kotlin
dependencies {
    implementation(platform("me.ahoo.cosid:cosid-bom:$cosidVersion"))
    implementation("me.ahoo.cosid:cosid-spring-boot-starter")
    implementation("me.ahoo.cosid:cosid-spring-redis") // or cosid-jdbc, cosid-mongo, cosid-zookeeper, cosid-proxy
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
}
```

```yaml
cosid:
  namespace: ${spring.application.name}
  segment:
    enabled: true
    distributor:
      type: redis
    provider:
      order:
        step: 100
```

```java
long orderId = idGeneratorProvider.getRequired("order", IdGenerator.class).generate();
```

The [Getting Started](https://cosid.ahoo.me/guide/getting-started.html) guide walks through it, including Snowflake IDs and Maven.

## Documentation

- [Why CosId](https://cosid.ahoo.me/guide/introduction.html): the problem and the trade-offs between the generators.
- [Core concepts](https://cosid.ahoo.me/guide/concepts/id-generator.html): generators, [machine ids](https://cosid.ahoo.me/guide/concepts/machine-id.html), segments and converters.
- [Choosing a backend](https://cosid.ahoo.me/guide/backends/): Redis, JDBC, MongoDB, ZooKeeper and the proxy.
- [Configuration reference](https://cosid.ahoo.me/reference/config/basic.html): every property and its default.
- [Operations](https://cosid.ahoo.me/guide/operations/production.html): production checklist, rolling upgrades, monitoring and troubleshooting.
- [Migrating to 4.0](https://cosid.ahoo.me/guide/migration/4.0.html), and the full [migration guide](./docs/MIGRATION-4.0.md).

The [examples](./examples) are runnable applications for Redis, ZooKeeper, the proxy and `CosIdGenerator`.

## Performance

Historical [JMH benchmarks](https://cosid.ahoo.me/reference/performance/benchmark.html) on one development notebook (MacBook Pro M1, Redis on the same machine):

<p align="center">
  <img src="./documentation/docs/public/assets/perf/Throughput-Of-SegmentChainId.png" alt="Throughput of SegmentChainId"/>
</p>

<p align="center">
  <img src="./documentation/docs/public/assets/perf/Percentile-Sample-Of-SegmentChainId.png" alt="Percentile sample of SegmentChainId"/>
</p>

`SegmentChainId` is about five times as fast as Meituan Leaf's segment mode, see [CosId VS Leaf](https://cosid.ahoo.me/reference/performance/cosid-vs-leaf.html).

## Contributing

Bug reports, ideas and pull requests are welcome. Read [CONTRIBUTING.md](./CONTRIBUTING.md) for the development setup, commit conventions, review and release process, and [SECURITY.md](./SECURITY.md) to report a vulnerability privately. The planned work is in the [roadmap](./docs/ROADMAP.md).

## Community Partners and Sponsors

<a href="https://www.jetbrains.com/?from=CosId" target="_blank">
    <img src="./docs/jetbrains-logo.png" title="JetBrains" width=130 />
</a>
