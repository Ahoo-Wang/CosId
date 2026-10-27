# Store TCK

`cosid-store-tck` holds the contracts every store passes, an in-memory store, and test tools. Use it to test a custom
`StoreProvider`, a new `JdbcDialect`, or your application without a database.

```kotlin
testImplementation("me.ahoo.cosid:cosid-store-tck")
```

```java
class MyRangeStoreTest extends RangeStoreContract {
    @Override
    protected RangeStore newStore() { return new MyStore(...).ranges(); }
}
class MySlotLeaseStoreTest extends SlotLeaseStoreContract {
    @Override
    protected SlotLeaseStore newStore() { return new MyStore(...).slots(); }
}
```

| Area | Cases |
|---|---|
| ranges | the first range starts at `initialOffset`; one of concurrent first creations wins; N threads × M store instances never overlap and increase strictly; values above 2^53 exact; `RangeExhaustedException` at the end; sealed keys fail and keep their value; one connection reserving different keys (no stale `last_insert_id`) |
| lease basics | the slot choice order (idempotent, preferred, sticky, lowest); `Exhausted`; the `limit` boundary |
| safety | after a takeover `floor ≥` the previous final `ceiling`; after a release `floor ≥ lastIssued`; `ceiling` and `next_floor` monotonic; the same key with another incarnation is never taken over; an expired holder's renew and release fail; renewals are idempotent |
| clock | `storeNow` comes from the store; `expiresAt − storeNow == ttl` from the same write |
| concurrency | N clients acquiring in one namespace get distinct slots |
| metadata | other namespace parameters are refused |

Also in the module:

- `InMemoryStore` passes every contract; the core and Spring tests use it, and so can yours (a `memory` provider).
- `ManualClock` drives the store clock in tests.
- Fault injection wrappers add latency, timeouts, and lost responses that are replayed.
- `ContractsRan` reads JUnit XML reports and fails when contract cases were skipped. Every CosId backend runs it in
  `check`, so a CI job without its database cannot pass silently.
