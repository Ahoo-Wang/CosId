# Redis

`cosid-store-redis`：以 Lua 脚本实现的 `RangeStore` 与 `SlotLeaseStore`，由 Lettuce 通过最小的 `RedisScripts`
接口执行。它不依赖 Spring（D6）；Spring Data 适配器在 autoconfigure 模块中。
**要求 Redis 7.0 或更高版本**（D27）。6.2.24 通过过一次 TCK，但不承诺支持。

## 持久化前提 {#durability}

::: danger 唯一性依赖于“已确认的写入不会丢失”
以下**二选一**：

- 单节点，开启 AOF 且 `appendfsync always`；或
- 有副本时，`WAIT` 覆盖**每一个**副本（`options.wait` 设为副本数）。

在异步复制下，故障切换可能丢失区间与租约的写入，进而发出重复的 id。存储只在写入被确认后才使用区间或 ceiling，
但它无法让 Redis 保住这些写入。出于同样的原因，key 绝不能被淘汰：保持 `maxmemory-policy noeviction`（默认值）。
**若对唯一性要求严格，优先选择 [JDBC](jdbc.md) 或 [ZooKeeper](zookeeper.md)。**
:::

## 配置

```yaml
cosid:
  stores:
    redis:
      type: redis
      timeout: 1s           # 每条命令的超时，也是 WAIT 的超时
      options:
        uri: redis://redis:6379
        wait: auto
```

| 选项 | 默认值 | 含义 |
|---|---|---|
| `uri` | | 未提供客户端时使用的 Redis URI |
| `cluster` | `false` | `uri` 指向 Redis Cluster |
| `wait` | `auto` | `auto`：服务端有副本时每次写入后执行 `WAIT 1`（打开时通过 `INFO replication` 判断；在集群中，要求每个主节点都有副本）；`off`；或副本数 |
| `sweepBatch` | 16 | 一次 acquire 最多把多少个过期租约移回空闲集合 |

存储如何处理传入的客户端（`StoreConfig.client`，或 Spring 中上下文里的 Bean）：

| 客户端 | 存储的行为 |
|---|---|
| `RedisScripts`（Spring Data 适配器，用于 `RedisConnectionFactory`） | 原样使用，包括 `WAIT` |
| `RedisClient` / `RedisClusterClient` | 自行打开一个连接，并且只关闭这个连接 |
| `StatefulRedisConnection` / `StatefulRedisClusterConnection` | 在其上执行且绝不关闭它；`wait` 必须解析为 0，因为 `WAIT` 不能阻塞共享连接 |
| 无 | 根据 `uri` 创建客户端并负责关闭 |

确认副本数不足的写入以 `RedisStoreException` 失败。其结果未知，因此 acquire、renew 与 reserve 可以重试；
丢失的区间只是一个间隙。

## Key

每组 key 共用一个 hash tag，因此每个脚本都在同一个 Cluster 节点上执行。key 永不删除（D23）。

| Key | 类型 | 内容 |
|---|---|---|
| `cosid:{key}` | string | 区间 key 的下一个值，十进制（超过 2^53 仍然精确） |
| `cosid:{key}:sealed` | string | 封存标记 |
| `cosid:{ns}:meta` | hash | 命名空间的 `limit`、`tolerance` |
| `cosid:{ns}:slot` | hash | 槽位 → `ownerKey\|incarnation\|lastOwnerKey\|fence\|expiresAt\|floor\|ceiling\|nextFloor` |
| `cosid:{ns}:free` | sorted set | 按编号排列的空闲槽位，预填 `0..limit-1` |
| `cosid:{ns}:live` | sorted set | 按 `expiresAt` 排列的存活租约 |
| `cosid:{ns}:owner` | hash | owner key → 它上次获取的槽位（粘性选择） |
| `cosid:{ns}:held` | hash | owner key → 行中记录该 key 的槽位 |

一次 acquire 最多把 `sweepBatch` 个过期租约清扫回 `free`，然后依次尝试已持有的、preferred、粘性的以及最小的空闲槽位：
只是若干次 hash 与 sorted set 操作，O(log n)，绝不扫描（4.x 在 Lua 中扫描）。存储时钟是脚本内的 `TIME`；
Redis 7 复制脚本的效果，因此这是安全的。
