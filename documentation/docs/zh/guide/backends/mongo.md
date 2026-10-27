# MongoDB

`cosid-store-mongo`：基于 `mongodb-driver-sync` 的 `RangeStore` 与 `SlotLeaseStore`。**要求 MongoDB 6.0 或更高版本**（D26），
部署为**副本集或分片集群**：每个操作都以 `majority` concern 读写。CI 在 6.0 与 8.0 上运行 TCK。
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

## 集合

| 集合 | 文档 |
|---|---|
| `cosid_range` | `{_id: key, next, sealed}`；使用带 upsert 的 `findOneAndUpdate` 与 pipeline `next = ifNull(next, initialOffset) + size`，过滤条件为 `sealed != true`；E11000（并发 upsert）重试一次 |
| `cosid_slot` | `{ns, slot, …}`，在 `(ns, slot)` 上建唯一索引，所有槽位预先创建；槽位选择的每一步是一次 `findOneAndUpdate`，其过滤条件用 `$expr` 与 `$$NOW` 判断“空闲”，并带上 fence |
| `cosid_namespace` | 每个命名空间的 `{limit, toleranceMs}` |

存储时钟是 `$$NOW`。
