# MongoDB

`cosid-store-mongo`: `RangeStore` and `SlotLeaseStore` over `mongodb-driver-sync`. **MongoDB 6.0 or later** (D26),
on a **replica set**: every operation reads with `majority` concern and, by default, writes with `majority` concern. CI runs the TCK on
6.0 and 8.0. 4.4 and 5.0 passed the TCK once, which is not a support commitment.

**Sharded clusters are not verified** (no test runs through `mongos`). If you use one, do not shard the three cosid
collections (they hold a few rows per key and slot): unsharded, they live on the primary shard and every operation is
routed there, as on a replica set, and the store clock is that shard's. A sharded `cosid_slot` is not supported: its
`findOneAndUpdate` filters do not carry the shard key, which MongoDB before 7.1 refuses.

## Driver version {#driver}

`cosid-store-mongo` is built and tested on **mongodb-driver-sync 5.8.1**, the version Spring Boot 4.1.2 manages: an
application on Boot 4.1.2 gets 5.8.1 with Maven and with Gradle alike (CI job `published-jars`,
[run 40881059003](https://github.com/Ahoo-Wang/CosId/actions/runs/40881059003)).

## Configuration

```yaml
cosid:
  stores:
    mongo:
      type: mongo
      timeout: 2s            # the driver's timeoutMS, positive
      options:
        uri: mongodb://mongo-1,mongo-2,mongo-3/?replicaSet=rs0
        database: cosid_db
```

| Option | Default | Meaning |
|---|---|---|
| `uri` | | required unless a `MongoClient` is the client (Spring Boot's bean is used when there is one) |
| `database` | `cosid_db` | |
| `writeConcern` | `majority` | `majority`, `acknowledged`, `w1`, `w2`, `w3`, `journaled` (any case), or a member count `n ≥ 1`; `unacknowledged`, `0` and anything else fail the store at open. Reads stay `majority` |

An operation that runs out of `timeout` fails with `StoreTimeoutException`.

::: warning Keep `writeConcern: majority`
Uniqueness rests on "an acknowledged write is never lost". Only `majority` guarantees it across a failover: with a weaker
write concern, a failover can lose range and lease writes that fewer than a majority acknowledged, and then hand out
duplicate ids. The provider logs a start-up `WARN` for every store with a weaker setting. `acknowledged` and
`journaled` leave `w` to the server default, which is not guaranteed to be majority, so they warn too.
:::

## Collections

| Collection | Documents |
|---|---|
| `cosid_range` | `{_id: key, next, sealed}`; `findOneAndUpdate` with upsert and a pipeline `next = ifNull(next, initialOffset) + size`, filtered on `sealed != true`; E11000 (a concurrent upsert) is retried once |
| `cosid_slot` | `{ns, slot, …}` with a unique index on `(ns, slot)` and one on `(ns, lastOwnerKey)` for the slots an owner key holds or held last, all slots created up front; each step of the slot choice is a `findOneAndUpdate` whose filter tests "free" with `$expr` and `$$NOW` and carries the fence |
| `cosid_namespace` | `{limit, toleranceMs}` of each namespace |

The store clock is `$$NOW`.

Stores of 5.0.0-alpha.1 also created the index `ns_ownerKey` on `cosid_slot`, which no query uses any more. It does
no harm; to drop it:

```js
db.cosid_slot.dropIndex("ns_ownerKey")
```
