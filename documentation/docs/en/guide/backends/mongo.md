# MongoDB

`cosid-store-mongo`: `RangeStore` and `SlotLeaseStore` over `mongodb-driver-sync`. **MongoDB 6.0 or later** (D26),
on a **replica set or a sharded cluster**: every operation reads and writes with `majority` concern. CI runs the TCK on
6.0 and 8.0. 4.4 and 5.0 passed the TCK once, which is not a support commitment.

## Configuration

```yaml
cosid:
  stores:
    mongo:
      type: mongo
      timeout: 2s            # the driver's timeoutMS; 0 means none
      options:
        uri: mongodb://mongo-1,mongo-2,mongo-3/?replicaSet=rs0
        database: cosid_db
```

| Option | Default | Meaning |
|---|---|---|
| `uri` | | required unless a `MongoClient` is the client (Spring Boot's bean is used when there is one) |
| `database` | `cosid_db` | |

## Collections

| Collection | Documents |
|---|---|
| `cosid_range` | `{_id: key, next, sealed}`; `findOneAndUpdate` with upsert and a pipeline `next = ifNull(next, initialOffset) + size`, filtered on `sealed != true`; E11000 (a concurrent upsert) is retried once |
| `cosid_slot` | `{ns, slot, …}` with a unique index on `(ns, slot)`, all slots created up front; each step of the slot choice is a `findOneAndUpdate` whose filter tests "free" with `$expr` and `$$NOW` and carries the fence |
| `cosid_namespace` | `{limit, toleranceMs}` of each namespace |

The store clock is `$$NOW`.
