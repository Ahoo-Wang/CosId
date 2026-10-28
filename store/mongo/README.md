# cosid-store-mongo

The MongoDB store of CosId 5 (`docs/architecture/06-stores.md` §2.3): `MongoRangeStore` (`cosid_range`) and
`MongoSlotLeaseStore` (`cosid_slot`, `cosid_namespace`) over `mongodb-driver-sync`, opened by `MongoStoreProvider`
(type `mongo`). It needs a replica set (or a sharded cluster): every operation reads with `majority` concern and, by
default, writes with `majority` concern.

- **Supported:** MongoDB **6.0 or later**. CI runs the store TCK on `mongo:6.0` (the minimum) and `mongo:8.0`.
  MongoDB 8.0.21 and later refuse to start on Linux 6.19 up to 7.0.13
  ([SERVER-125742](https://jira.mongodb.org/browse/SERVER-125742), the kernel's rseq change breaks tcmalloc), the
  kernel of newer `ubuntu-24.04` runner images, so the `mongo:8.0` job runs on `ubuntu-22.04` (Linux 6.8). It fails,
  naming the kernel, if its runner ever moves to an affected kernel. This is MongoDB's limit, not the store's.
- **Options:** `uri` (required unless a `MongoClient` is injected through `StoreConfig.client`), `database`
  (default `cosid_db`), `writeConcern` (default `majority`, see below). `StoreConfig.timeout` becomes the driver's
  `timeoutMS`; zero means none. An operation that runs out of it fails with `StoreTimeoutException`.

## Write concern

`options.writeConcern` takes `majority` (the default), `acknowledged`, `w1`, `w2`, `w3`, `journaled` (any case), or a
number of members (`w: n`, at least 1). `unacknowledged`, `0` and anything else fail the store at open. Reads stay
`majority` on the primary whatever the write concern.

**The uniqueness of the ids rests on "an acknowledged write is never lost"** (已确认的写入不会丢失). Only `majority`
guarantees it across a failover: with anything weaker, a failover can lose range and lease writes acknowledged by fewer
than a majority, and so hand out duplicate ids. The provider logs a startup `WARN` for every such store. `acknowledged`
and `journaled` leave `w` to the server default (majority on most replica sets since 5.0, but not guaranteed), so they
warn too.

## 实测兼容性(不承诺)

低于 6.0 的版本只做过一次实测(D28),用来记录事实,**不是支持承诺**,也不为它们保留任何兼容代码。CI 的
`Store MongoDB probe` job 每次在这些版本上跑一次 TCK,结果写在 job summary 里,不会阻塞合入。

| 版本 | TCK 结果 | 说明 |
|---|---|---|
| 4.4 | 通过(46/46,单节点副本集) | 并发首次使用同一命名空间时,`cosid_namespace` 的 `$setOnInsert` upsert 输家会收到 E11000,4.4 的服务端不会替它重试(8.0 上没有观察到)。这在 #1311 中导致 `concurrentAcquiresGetDistinctSlots` 偶发失败。现在的实现在所有版本上都会对 E11000 重试并读回比较 `{limit, toleranceMs, slotOffset}`(5.0.0-alpha.1 写的文档没有 `slotOffset`,按 0 读),所以 4.4 上也通过了。 |
| 5.0 | 通过(46/46,单节点副本集) | 无 |
