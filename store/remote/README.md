# cosid-store-remote

The remote store of CosId 5 (`docs/architecture/06-stores.md` §2.5, `08-remote.md`). `RemoteStore` implements
`RangeStore` and `SlotLeaseStore` as calls to [cosid-server](../../server/README.md). The server runs each primitive on
its backing store in one round trip; that store also runs the slot choice and stamps the times. The client uses the JDK
`HttpClient` and has no third-party dependency. `RemoteStoreProvider` opens it as type `remote`.

- **Options:** `baseUrl` (required), `token` (the bearer token), `connectTimeout` (default 5s), `requestTimeout`
  (default `StoreConfig.timeout`; a request that runs out of it fails with a `RemoteStoreException` whose cause is a
  `StoreTimeoutException`). Durations are a `Duration`, milliseconds, ISO-8601 or `500ms` / `3s` / `1m` / `1h`.
- **Errors:** the answers a primitive has an outcome for map to that outcome:
  - `410` → `RangeSealedException`
  - `422` → `RangeExhaustedException`
  - `409 slots_exhausted` → `AcquireResult.Exhausted`
  - `409 lease_lost` → an empty renew or a false release
  - `409 namespace_mismatch` → `NamespaceMismatchException`
  - `501` on seal → `false`

  Anything else throws `RemoteStoreException`, which carries `status()` and `code()`. Its `transientFailure()` is true
  for `503` and for calls that got no answer.
- **TCK:** the contracts run in `server/` against a real server started in process. They run on `InMemoryStore`, on H2
  (JDBC), and on Redis when `COSID_TEST_REDIS_URI` is set. They live there because nothing may depend on `server`.
