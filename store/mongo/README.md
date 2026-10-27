# cosid-store-mongo

The MongoDB store of CosId 5 (`docs/architecture/06-stores.md` §2.3): `MongoRangeStore` (`cosid_range`) and
`MongoSlotLeaseStore` (`cosid_slot`, `cosid_namespace`) over `mongodb-driver-sync`, opened by `MongoStoreProvider`
(type `mongo`). It needs a replica set (or a sharded cluster): every operation reads and writes with `majority` concern.

- **Supported:** MongoDB **6.0 or later**. CI runs the store TCK on `mongo:6.0` (the minimum) and `mongo:8.0`.
- **Options:** `uri` (required unless a `MongoClient` is injected through `StoreConfig.client`), `database`
  (default `cosid_db`). `StoreConfig.timeout` becomes the driver's `timeoutMS`; zero means none.

## 实测兼容性(不承诺)

低于 6.0 的版本只做过一次实测(D28),用来记录事实,**不是支持承诺**,也不为它们保留任何兼容代码。CI 的
`Store MongoDB probe` job 每次在这些版本上跑一次 TCK,结果写在 job summary 里,不会阻塞合入。

| 版本 | TCK 结果 | 说明 |
|---|---|---|
| 4.4 | 通过(46/46,单节点副本集) | 并发首次使用同一命名空间时,`cosid_namespace` 的 `$setOnInsert` upsert 输家会收到 E11000,4.4 的服务端不会替它重试(8.0 上没有观察到)。这在 #1311 中导致 `concurrentAcquiresGetDistinctSlots` 偶发失败。现在的实现在所有版本上都会对 E11000 重试并读回比较 `{limit, toleranceMs}`,所以 4.4 上也通过了。 |
| 5.0 | 通过(46/46,单节点副本集) | 无 |
