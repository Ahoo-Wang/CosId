# Redis

`cosid-store-redis`: `RangeStore` and `SlotLeaseStore` as Lua scripts, run through the minimal `RedisScripts`
interface by Lettuce. It has no Spring dependency (D6); the Spring Data adapter is in the autoconfigure module.
**Redis 7.0 or later** (D27). 6.2.24 passed the TCK once, which is not a support commitment.

## Durability precondition {#durability}

::: danger Uniqueness rests on "an acknowledged write is never lost" (已确认的写入不会丢失)
Run **one** of:

- a single node with AOF and `appendfsync always`, or
- replicas with `WAIT` covering **every** replica (`options.wait` set to the replica count).

Under asynchronous replication a failover can lose range and lease writes, and then hand out duplicate ids. The store
uses a range or a ceiling only after its write is acknowledged, but it cannot make Redis keep it. For the same reason
the keys must never be evicted: keep `maxmemory-policy noeviction` (the default). **Where uniqueness is strict,
prefer [JDBC](jdbc.md) or [ZooKeeper](zookeeper.md).**
:::

## Driver version {#driver}

`cosid-store-redis` is built and tested on **Lettuce 7.9.0**, which brings Netty 4.2.17; Spring Boot 4.1.2 manages
Lettuce 7.5.2. Which one an application gets depends on its build. On Spring Boot 4.1.2 (CI job `published-jars`,
[run 40881059003](https://github.com/Ahoo-Wang/CosId/actions/runs/40881059003)):

| Build | Lettuce | |
|---|---|---|
| Maven, `spring-boot-starter-parent` or the BOM imported | 7.5.2 | the BOM's version wins; Netty is the BOM's 4.2.19 |
| Gradle, `platform("org.springframework.boot:spring-boot-dependencies")` | 7.9.0 | the higher version wins |

The store runs on both. For Lettuce 7.9.0 with Maven, set `<lettuce.version>7.9.0.RELEASE</lettuce.version>`.

## Configuration

```yaml
cosid:
  stores:
    redis:
      type: redis
      timeout: 1s           # every command, and the WAIT timeout
      options:
        uri: redis://redis:6379
        wait: auto
```

| Option | Default | Meaning |
|---|---|---|
| `uri` | | a Redis URI, when no client is given |
| `cluster` | `false` | `uri` names a Redis Cluster |
| `wait` | `auto` | `auto`: `WAIT 1` after every write when the server has replicas (`INFO replication` at open; in a cluster, when every primary has one); `off`; or the replica count |
| `sweepBatch` | 16 | expired leases one acquire moves back to the free set, at most |

What the store does with the client it is given (`StoreConfig.client`, or the context's bean in Spring):

| Client | The store |
|---|---|
| `RedisScripts` (the Spring Data adapter, for a `RedisConnectionFactory`) | uses it as it is, `WAIT` included |
| `RedisClient` / `RedisClusterClient` | opens a connection of its own and closes only that one |
| `StatefulRedisConnection` / `StatefulRedisClusterConnection` | runs on it and never closes it; `wait` must resolve to 0, since `WAIT` must not block a shared connection |
| none | creates a client from `uri` and closes it |

A write that fewer replicas acknowledge fails with `RedisStoreException`. Its outcome is unknown, so acquire, renew and
reserve may be retried; a lost range is only a gap.

## Keys

Each group of keys shares one hash tag, so every script runs on one Cluster node. Keys are never deleted (D23).

| Key | Type | Content |
|---|---|---|
| `cosid:{key}` | string | the next value of a range key, decimal (exact beyond 2^53) |
| `cosid:{key}:sealed` | string | the seal marker |
| `cosid:{ns}:meta` | hash | `limit`, `tolerance` of the namespace |
| `cosid:{ns}:slot` | hash | slot → `ownerKey\|incarnation\|lastOwnerKey\|fence\|expiresAt\|floor\|ceiling\|nextFloor` |
| `cosid:{ns}:free` | sorted set | free slots by number, prefilled `0..limit-1` |
| `cosid:{ns}:live` | sorted set | live leases by `expiresAt` |
| `cosid:{ns}:owner` | hash | owner key → the slot it took last (sticky choice) |
| `cosid:{ns}:held` | hash | owner key → the slots whose row names it |

An acquire sweeps at most `sweepBatch` expired leases into `free`, then tries the held, preferred, sticky and lowest
free slot: a few hash and sorted-set operations, O(log n), never a scan (4.x scanned in Lua). The store clock is `TIME`
inside the script; Redis 7 replicates script effects, so that is safe.
