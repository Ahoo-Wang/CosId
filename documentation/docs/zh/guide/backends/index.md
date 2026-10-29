# 选择后端

后端实现两个原语：`RangeStore.reserve`（号段）与 `SlotLeaseStore`（机器号槽位）。策略、重试与缓冲都在 `cosid-core` 中，
因此每个后端的行为一致，由同一套 [TCK](test-suite.md) 验证。存储在 `cosid.stores` 下声明一次、按名称引用，
所以一个应用可以把号段放在数据库、把槽位放在 Redis。

| 后端 | Artifact | 类型 | 支持起始版本 | 唯一性依赖于 |
|---|---|---|---|---|
| [JDBC](jdbc.md) | `cosid-store-jdbc` | `jdbc` | MySQL 8.4、MariaDB 10.11、PostgreSQL 15、SQL Server 2019、H2 2.x | 数据库事务 |
| [Redis](redis.md) | `cosid-store-redis` | `redis` | Redis 7.0 | **已确认的写入不会丢失**：AOF `appendfsync always`，或对每个副本 `WAIT` |
| [MongoDB](mongo.md) | `cosid-store-mongo` | `mongo` | MongoDB 6.0，副本集（分片集群[未经验证](mongo.md)） | `majority` 读写（`writeConcern: majority`，默认值） |
| [ZooKeeper](zookeeper.md) | `cosid-store-zookeeper` | `zookeeper` | ZooKeeper 3.8（主要目标 3.9） | ZooKeeper 的 quorum 写入 |
| [Remote](../server.md) | `cosid-store-remote` | `remote` | 同版本的 cosid-server | 服务端的后备存储 |

**若唯一性必须在任何故障切换下成立，优先选择 JDBC 或 ZooKeeper。** Redis 快速且简单，但在异步复制下，
故障切换可能丢失写入，连同其中的区间与租约窗口。

## 支持版本与实测兼容性 {#supported-versions-and-measured-compatibility}

支持从各后端的最低版本开始（D26、D27），CI 在最低版本与最新版本上运行 TCK。低于最低版本的各版本各运行过一次 TCK（D28）。
结果作为事实记录，**实测兼容性，不承诺**：这些版本不会得到修复，也不会有兼容代码，后续版本可能使其失效。

| 后端 | 版本 | TCK | 说明 |
|---|---|---|---|
| MySQL | 8.0（8.0.46） | 通过，28 / 28 | |
| MariaDB | 10.6 | 通过，28 / 28 | |
| PostgreSQL | 12、13、14 | 各通过 25 / 25 | |
| SQL Server | 2017 | 通过，25 / 25 | `2017-latest` |
| Redis | 6.2.24 | 通过，96 个用例 | 单机、主节点 + 副本加 `WAIT`、3 主节点 Cluster；脚本未使用 7.x 独有的命令 |
| MongoDB | 4.4 | 通过，46 / 46 | 单节点副本集；4.4 上并发首次使用同一命名空间会触发 E11000，存储在所有版本上都会重试 |
| MongoDB | 5.0 | 通过，46 / 46 | 单节点副本集 |
| ZooKeeper | 3.7.2、3.6.4 | 各通过 55 个用例 | 单服务器 |
| ZooKeeper | 3.5 及更早 | 未运行 | 预计失败：acquire 用只读 `multi`（3.6+）读取槽位 |

于 2026-10-26/27 在本地及非阻塞的 `probe` CI 任务中实测。各模块 README 维护这些表格的最新状态。

## 运维前提

| 后端 | 上线前 |
|---|---|
| JDBC | 用 `schema-<dialect>.sql` 建表（或 `initSchema: true`）；行永不删除 |
| Redis | AOF `appendfsync always`，或 `WAIT` 覆盖每个副本；`maxmemory-policy noeviction`（key 绝不能被淘汰） |
| MongoDB | 副本集；保持 `writeConcern: majority`；用户有权创建集合与索引 |
| ZooKeeper | 绝不在时钟落后于槽位节点原始 `mtime` 的服务器上恢复该节点，否则在恢复后等待一个 ttl（加上 tolerance）再启动客户端 |
| Remote | token（默认开启认证）；服务端的后备存储满足其自身的前提 |

## 所有存储通用的选项

```yaml
cosid:
  stores:
    <name>:
      type: jdbc            # jdbc | redis | mongo | zookeeper | remote | 某个 StoreProvider Bean 的类型
      timeout: 2s           # 每个存储操作；不超过其上号段的 reserve-timeout
      client: myDataSource  # 可选：用作客户端的 Bean
      options: { ... }      # 后端选项，见各后端页面
```

未配置 `client` 时，Spring Boot 使用上下文中该类型客户端的唯一 Bean（`DataSource`、Lettuce 客户端或 Spring Data
`RedisConnectionFactory`、`MongoClient`、`CuratorFramework`），如果存在的话。没有客户端时，存储根据其选项自行连接。
