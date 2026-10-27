# cosid-store-zookeeper

The ZooKeeper store of CosId 5 ([06-stores.md §2.4](../../docs/architecture/06-stores.md)): `RangeStore` and
`SlotLeaseStore` on Curator. It depends on `cosid-core` and `curator-framework` (5.9) only.

**ZooKeeper 3.8 or later** (D27); 3.9 is the primary target, the version Curator is aligned with. CI runs the store TCK
on both.

## 实测兼容性(不承诺)

Below the 3.8 minimum the TCK was run once (D28), to record real compatibility, not to support it: no workaround for
old versions is or will be added, and the CI `probe` jobs on 3.6 and 3.7 never block a change.

| Version | TCK result | Notes |
|---|---|---|
| 3.9 (primary) | pass | CI `Store ZooKeeper (3.9)`; the in-process test server is 3.9.3 |
| 3.8 (minimum) | pass | CI `Store ZooKeeper (3.8)`; locally on `zookeeper:3.8` |
| 3.7.2 | pass | 2026-10-26, `zookeeper:3.7`, every contract and store case (55); CI probe job |
| 3.6.4 | pass | 2026-10-26, `zookeeper:3.6`, every contract and store case (55); CI probe job |
| 3.5 and older | not run | expected to fail: acquire reads the slots with a read-only `multi`, added in 3.6 |

The failover case always runs on its own in-process 3.9 ensemble, so these rows cover a single server only.

## Nodes

| Node | Data |
|---|---|
| `/cosid/range/{key}` | the next value as decimal text, `:sealed` appended once sealed; never deleted (S6) |
| `/cosid/slot/{ns}` | the namespace metadata, `limit` and `tolerance` |
| `/cosid/slot/{ns}/{slot}` | the slot as `name=value` lines; the fence is in the data, never `Stat.version`; never deleted |
| `/cosid/slot/{ns}/.clock` | written by every acquire: its `mtime` is the `now` of the expiry check |
| `/cosid/owner/{ns}/{ownerKey}` | the owner index: the slots (`3,7`) whose last owner is the key, written in the same `multi` as each acquire; the key URL-encoded, `.` and `*` too |

An acquire reads the clock and the owner index, then only the slots the index lists and the preferred one, which
decide the idempotent, duplicate and sticky steps; the lowest free slot is then searched in ascending batches (32, 64,
… up to 256 slots per read-only `multi`) and the search stops at the first free one. Only an exhausted namespace reads
every slot. A release writes the slot node alone, so no node is written by every acquire and release. Uniqueness does
not depend on the index: the slot node's compare-and-set carries invariant W.

Lease times come from the `mtime` of the write that produced them. Do not restore a slot node on a server whose clock
is behind the original `mtime`, or wait one ttl before starting clients after such a restore.

## Opening a store

Through the `StoreProvider` of type `zookeeper`: either an injected, started `CuratorFramework` in `StoreConfig.client`
(the caller closes it), or a client built from the options, which the store closes:

| Option | Default |
|---|---|
| `connectString` | `localhost:2181` |
| `sessionTimeout` | 60s |
| `connectionTimeout` | 15s |
| `blockUntilConnectedWait` | 10s |
| `retry.baseSleepTime` / `retry.maxRetries` / `retry.maxSleep` | 100ms / 5 / 500ms |

`StoreConfig.timeout` bounds every store operation as a whole, compare-and-set retries included. Directly:
`new ZookeeperStore(curatorFramework, timeout)`.

## Tests

`COSID_TEST_ZOOKEEPER_CONNECT` points the TCK at a real server; without it the tests start an in-process curator-test
server. The failover case always runs its own three-server `TestingCluster`.
