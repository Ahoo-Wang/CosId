# MongoDB

`cosid-store-mongo`：基于 `mongodb-driver-sync` 的 `RangeStore` 与 `SlotLeaseStore`。**要求 MongoDB 6.0 或更高版本**（D26），
部署为**副本集或分片集群**：每个操作都以 `majority` concern 读取，默认也以 `majority` concern 写入。CI 在 6.0 与 8.0 上运行 TCK。
4.4 与 5.0 通过过一次 TCK，但不承诺支持。

## 配置

```yaml
cosid:
  stores:
    mongo:
      type: mongo
      timeout: 2s            # 驱动的 timeoutMS；0 表示不限
      options:
        uri: mongodb://mongo-1,mongo-2,mongo-3/?replicaSet=rs0
        database: cosid_db
```

| 选项 | 默认值 | 含义 |
|---|---|---|
| `uri` | | 必填，除非客户端是一个 `MongoClient`（存在 Spring Boot 的 Bean 时使用它） |
| `database` | `cosid_db` | |
| `writeConcern` | `majority` | `majority`、`acknowledged`、`w1`、`w2`、`w3`、`journaled`（不区分大小写），或成员数 `n ≥ 1`；`unacknowledged`、`0` 及其他值在打开存储时报错。读取始终为 `majority` |

操作超过 `timeout` 时以 `StoreTimeoutException` 失败。

::: warning 保持 `writeConcern: majority`
唯一性以"已确认的写入不会丢失"为前提。只有 `majority` 能在故障切换后保证这一点：写关注更弱时，故障切换可能丢失只被少数成员确认的区间与租约写入，进而发出重复 ID。对每个设置更弱的存储，provider 在启动时记录一条 `WARN`。`acknowledged` 与 `journaled` 把 `w` 交给服务端默认值，而它不保证是 majority，因此同样告警。
:::

## 集合

| 集合 | 文档 |
|---|---|
| `cosid_range` | `{_id: key, next, sealed}`；使用带 upsert 的 `findOneAndUpdate` 与 pipeline `next = ifNull(next, initialOffset) + size`，过滤条件为 `sealed != true`；E11000（并发 upsert）重试一次 |
| `cosid_slot` | `{ns, slot, …}`，在 `(ns, slot)` 上建唯一索引，所有槽位预先创建；槽位选择的每一步是一次 `findOneAndUpdate`，其过滤条件用 `$expr` 与 `$$NOW` 判断“空闲”，并带上 fence |
| `cosid_namespace` | 每个命名空间的 `{limit, toleranceMs}` |

存储时钟是 `$$NOW`。
