# cosid-store-redis

The Redis store of CosId 5 ([06-stores.md §2.2](../../docs/architecture/06-stores.md)): `RangeStore` and
`SlotLeaseStore` as Lua scripts, run through the minimal `RedisScripts` interface. It depends on `cosid-core` and
`lettuce-core` only; the Spring Data adapter lives in `spring/autoconfigure`. Redis 6 or later.

## Durability precondition

**The uniqueness of the ids rests on "an acknowledged write is never lost"** (已确认的写入不会丢失):

- a single node with AOF and `appendfsync always`, or
- `WAIT` covering every replica (`options.wait` set to the replica count).

Under asynchronous replication, a failover can lose range and lease writes and so hand out duplicate ids. The store
only hands out a range or a ceiling once its write is acknowledged, but it cannot make Redis keep it. Where uniqueness
is strict, prefer the JDBC or ZooKeeper store.

## Opening a store

`ServiceLoader` finds `RedisStoreProvider` (type `redis`). `StoreConfig.client` may hold:

| Client | What the store does |
|---|---|
| `RedisScripts` | uses it as it is, `WAIT` included (the Spring Data adapter) |
| `RedisClient` / `RedisClusterClient` | opens a connection of its own on it, and closes that connection only |
| `StatefulRedisConnection<String, String>` / `StatefulRedisClusterConnection<String, String>` | runs the scripts on it and never closes it; requires `wait` to resolve to 0, since `WAIT` must not block a shared connection |
| nothing | creates a client from `options.uri` (`options.cluster=true` for Redis Cluster) and closes it |

| Option | Default | Meaning |
|---|---|---|
| `uri` | — | a Redis URI, when no client is given |
| `cluster` | `false` | `uri` names a Redis Cluster |
| `wait` | `auto` | `auto`: `WAIT 1` after every write when the server has replicas (`INFO replication` at open; in a cluster, when every primary has one); `off`; or the replica count |
| `sweepBatch` | 16 | how many expired leases one acquire moves back to the free set at most |

`StoreConfig.timeout` bounds every command and is the `WAIT` timeout. A write that fewer replicas acknowledge fails
with `RedisStoreException`; its outcome is unknown, and acquire, renew and reserve may be retried (a lost range is a gap).

## Keys

Each group shares one hash tag, so every script runs on one Redis Cluster node. Keys are never deleted (D23).

| Key | Type | Content |
|---|---|---|
| `cosid:{key}` | string | the next value of a range key, a decimal string (exact beyond 2^53) |
| `cosid:{key}:sealed` | string | the seal marker |
| `cosid:{ns}:meta` | hash | `limit`, `tolerance`: what the namespace was first used with |
| `cosid:{ns}:slot` | hash | slot → `ownerKey\|incarnation\|lastOwnerKey\|fence\|expiresAt\|floor\|ceiling\|nextFloor` (owner keys escaped: `\` as `\\`, `\|` as `\p`) |
| `cosid:{ns}:free` | sorted set | free slots, scored by slot number; prefilled with `0..limit-1` by `ZADD NX` |
| `cosid:{ns}:live` | sorted set | live leases, scored by `expiresAt` |
| `cosid:{ns}:owner` | hash | owner key → the slot it took last (sticky choice) |
| `cosid:{ns}:held` | hash | owner key → the slots whose row names it (idempotent retries and duplicate owner keys) |

Every slot below the limit is in exactly one of `free` and `live`. An acquire sweeps at most `sweepBatch` expired
leases into `free`, then tries the held, preferred, sticky and lowest free slot (05-machine-lease.md §2.1): a few hash
and sorted set operations, O(log n), never a scan. The store clock is `TIME` inside the scripts.

## Tests

`./gradlew :store:redis:check` runs the TCK on standalone Redis (`COSID_TEST_REDIS_URI` when set, Testcontainers
otherwise), on a primary with one replica (`WAIT`) and on a three-primary Redis Cluster (Testcontainers), then
`checkContractsRan`. `-Pcosid.test.redis.image=redis:6.2-alpine` picks another image.
