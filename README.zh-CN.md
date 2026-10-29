<p align="center" style="text-align:center">
  <img width="300" src="./documentation/docs/public/logo.png" alt="CosId Logo"/>
</p>

# [CosId](https://cosid.ahoo.me/zh/) 通用、灵活、高性能分布式 ID 生成器

[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![GitHub release](https://img.shields.io/github/release/Ahoo-Wang/CosId.svg)](https://github.com/Ahoo-Wang/CosId/releases)
[![Maven Central Version](https://img.shields.io/maven-central/v/me.ahoo.cosid/cosid-core)](https://central.sonatype.com/artifact/me.ahoo.cosid/cosid-core)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/dfd1d6237a1644409548ebfbca300dc1)](https://app.codacy.com/gh/Ahoo-Wang/CosId?utm_source=github.com&utm_medium=referral&utm_content=Ahoo-Wang/CosId&utm_campaign=Badge_Grade_Settings)
[![codecov](https://codecov.io/gh/Ahoo-Wang/CosId/branch/main/graph/badge.svg?token=L0N51NB7ET)](https://codecov.io/gh/Ahoo-Wang/CosId)
![Integration Test Status](https://github.com/Ahoo-Wang/CosId/actions/workflows/integration-test.yml/badge.svg)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/Ahoo-Wang/CosId)

> [中文文档](https://cosid.ahoo.me/zh/) | [English Document](https://cosid.ahoo.me/)

> [!NOTE]
> **CosId 5.0 已正式发布。** Java API、`cosid.*` 配置以及存储格式与协议保持稳定，之后的 5.x 只做向后兼容的增量。4.x 进入维护期；离线迁移工具 [`cosid-migration`](./migration/README.md) 负责把部署迁移过来。

服务的每个副本都需要生成 ID（主键、订单号、事件 ID），并且不能与其他副本的 ID 冲突。*CosId* 在应用进程内生成这样的 ID，每个 ID 都不需要网络调用，并替你完成保证唯一所需的协调。

**CosId 5** 是一次完全重写，不保留向后兼容。**唯一性不依赖任何时钟**：机器号槽位以时间窗口的形式租用，相继持有者的窗口互不重叠，这条规则由存储自身维护；时钟偏差、GC 停顿与故障切换只影响可用性，不影响唯一性。所有策略运行在同一个 `CosIdRuntime` 上，Spring Boot、cosid-server 与纯 Java 以同样的方式启动它。离线迁移工具 [`cosid-migration`](./migration/README.md) 负责把 4.x 部署迁移过来。

| 策略 | ID | 适用场景 |
|---|---|---|
| `snowflake` | `long`，按时间有序（任意 `Layout`：毫秒或秒，53 位 JS 安全） | ID 需要携带生成时间，且每个 ID 不访问存储 |
| `segment` | `long`，稠密，单实例内递增 | 需要较短的数字主键；预留规模按时间预算自适应 |
| `periodic-segment` | 按年 / 月 / 日分期（`20261027-000042`） | 每期重新计数的业务编号 |
| `cosid80` | 80 位文本 | 需要超过 64 位 |
| `uuidv7` | UUIDv7（RFC 9562），按时间有序 | 需要标准 UUID，且不要 machine、存储与租约 |
| `custom` | 自己的 `long` 生成器 | 已有自己的生成器，希望与其他生成器一样被管理 |

存储：JDBC（MySQL 8.4+、MariaDB 10.11+、PostgreSQL 15+、SQL Server 2019+、H2）、Redis 7.0+、MongoDB 6.0+、ZooKeeper 3.8+ 以及 cosid-server；每个存储声明一次，可自由混用。

## 版本兼容性

| CosId | Spring Boot | Java | 分支 |
|-------|-------------|------|------|
| 5.x | 4.1.2+ | 21+ | `main` |
| 4.x | 4.x | 17+ | `4.x`（维护分支，只接受缺陷修复） |
| 3.x | 4.x | 17+ | `3.x` |
| 2.x | 3.x | 17+ | `2.x` |
| 1.x | 2.x | 8+ | `1.x` |

从 4.x 升级：见[迁移指南](https://cosid.ahoo.me/zh/guide/migration/from-4x.html)。4.x 与 5.0 不能在同一命名空间中混跑。

## 快速开始

```kotlin
dependencies {
    implementation(platform("me.ahoo.cosid:cosid-bom:$cosidVersion"))
    implementation("me.ahoo.cosid:cosid-spring-boot-starter")
    implementation("me.ahoo.cosid:cosid-store-jdbc")   // 或 cosid-store-redis、-mongo、-zookeeper、-remote
}
```

```yaml
cosid:
  namespace: ${spring.application.name}
  stores:
    db:
      type: jdbc              # 使用应用的 DataSource
  machine:
    leased:
      store: db               # 机器号槽位从该存储租用
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
    long id = ids.generate();                                   // 名为 default 的 Snowflake
    String orderNo = runtime.stringGenerator("order").generate();
}
```

[快速上手](https://cosid.ahoo.me/zh/guide/getting-started.html)还介绍了 Maven 与纯 Java 的用法。

## 模块

| Artifact | 内容 |
|---|---|
| `cosid-core` | 生成器、布局与编解码器、槽位租约、`CosIdRuntime`、`@CosId` 字段填充；只依赖 `slf4j-api` |
| `cosid-store-jdbc` | JDBC 存储：MySQL、MariaDB、PostgreSQL、SQL Server、H2，以及 `JdbcDialect` SPI |
| `cosid-store-redis` | Redis 存储（Lettuce）；见其[持久化前提](./store/redis/README.md) |
| `cosid-store-mongo` | MongoDB 存储（副本集） |
| `cosid-store-zookeeper` | ZooKeeper 存储（Curator） |
| `cosid-store-remote` | cosid-server 的客户端（JDK `HttpClient`） |
| `cosid-store-tck` | 存储契约与内存存储，供测试使用 |
| `cosid-spring-boot-starter` | Spring Boot：core + `cosid-spring-boot-autoconfigure`；另加所用的存储 |
| `cosid-jackson`、`cosid-mybatis`、`cosid-spring-data-jdbc`、`cosid-activiti`、`cosid-flowable`、`cosid-axon` | 各集成，每个三方库一个 |
| `cosid-sharding` | 分片算法：`ModCycle`、`IntervalTimeline`、`CachedSharding` |
| `cosid-bom` | 以上全部的版本 |
| cosid-server | 远程存储服务，Docker 镜像（[`server/`](./server/README.md)） |
| `cosid-migration.jar` | 离线 4.x → 5.0 迁移工具（[`migration/`](./migration/README.md)） |

## 文档

- [为什么需要 CosId](https://cosid.ahoo.me/zh/guide/introduction.html)与[常用 ID 配方](https://cosid.ahoo.me/zh/guide/recipes.html)。
- 概念：[布局与编解码](https://cosid.ahoo.me/zh/guide/concepts/layout-and-codecs.html)、[机器号槽位租约](https://cosid.ahoo.me/zh/guide/concepts/slot-lease.html)、[号段](https://cosid.ahoo.me/zh/guide/concepts/segment.html)、[运行时](https://cosid.ahoo.me/zh/guide/concepts/runtime.html)。
- [选择后端](https://cosid.ahoo.me/zh/guide/backends/)：支持的版本、实测兼容性、运维前提。
- [cosid-server](https://cosid.ahoo.me/zh/guide/server.html)：API、鉴权、限制、Docker。
- [配置参考](https://cosid.ahoo.me/zh/reference/config/basic.html)：每个 `cosid.*` 属性及其默认值。
- [运维](https://cosid.ahoo.me/zh/guide/operations/production.html)：生产环境清单、指标、健康检查、故障排查。
- [从 4.x 迁移](https://cosid.ahoo.me/zh/guide/migration/from-4x.html)。
- 设计文档：[`docs/architecture/`](./docs/architecture/README.md)（决策 D1–D30）。

[示例](./examples/README.md)是可运行的应用：`segment-jdbc`、`snowflake-redis` 与 `server`。

## 性能

5.0 在任何场景下都不得慢于 4.x：每个[基准](https://cosid.ahoo.me/zh/reference/performance/benchmark.html)都分别在已发布的 4.1.0 与 5.0 上运行，对比结果是发布门槛。结果存放在 [`docs/jmh/`](./docs/jmh)。号段 ID 的热路径是一次原子加；Snowflake ID 是一个很短的临界区，从不在锁内等待。

## 参与贡献

欢迎提交问题、建议和 Pull Request。开发环境、提交规范、代码评审与发布流程见 [CONTRIBUTING.md](./CONTRIBUTING.md)；安全漏洞请按 [SECURITY.md](./SECURITY.md) 私下报告；后续规划见[路线图](./docs/architecture/roadmap.md)。

## 社区合作伙伴与赞助商

<a href="https://www.jetbrains.com/?from=CosId" target="_blank">
    <img src="./docs/jetbrains-logo.png" title="JetBrains" width=130 />
</a>
