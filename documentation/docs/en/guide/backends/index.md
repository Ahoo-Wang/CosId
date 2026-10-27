# Choosing a Backend

A backend implements two primitives: `RangeStore.reserve` (segments) and `SlotLeaseStore` (machine slots). Strategy,
retries and buffering live in `cosid-core`, so every backend behaves the same, and one [TCK](test-suite.md) proves it.
Stores are declared once under `cosid.stores` and referenced by name, so one application can keep its segments in a
database and its slot in Redis.

| Backend | Artifact | Type | Supported from | Uniqueness rests on |
|---|---|---|---|---|
| [JDBC](jdbc.md) | `cosid-store-jdbc` | `jdbc` | MySQL 8.4, MariaDB 10.11, PostgreSQL 15, SQL Server 2019, H2 2.x | the database's transactions |
| [Redis](redis.md) | `cosid-store-redis` | `redis` | Redis 7.0 | **acknowledged writes never being lost**: AOF `appendfsync always`, or `WAIT` on every replica |
| [MongoDB](mongo.md) | `cosid-store-mongo` | `mongo` | MongoDB 6.0, replica set or sharded cluster | `majority` reads and writes |
| [ZooKeeper](zookeeper.md) | `cosid-store-zookeeper` | `zookeeper` | ZooKeeper 3.8 (3.9 primary) | ZooKeeper's quorum writes |
| [Remote](../server.md) | `cosid-store-remote` | `remote` | the cosid-server of the same version | the server's backing store |

**Where uniqueness must hold under any failover, prefer JDBC or ZooKeeper.** Redis is fast and simple, but under
asynchronous replication a failover can lose writes, and with them ranges and lease windows.

## Supported versions and measured compatibility {#supported-versions-and-measured-compatibility}

Support starts at the minimum version of each backend (D26, D27), and CI runs the TCK on the minimum and the latest. Below
the minimum, each version was run through the TCK once (D28). The result is recorded as a fact, **not a support
commitment (实测兼容性,不承诺)**: those versions get no fixes and no compatibility code, and a later release may break them.

| Backend | Version | TCK | Notes |
|---|---|---|---|
| MySQL | 8.0 (8.0.46) | pass, 28 / 28 | |
| MariaDB | 10.6 | pass, 28 / 28 | |
| PostgreSQL | 12, 13, 14 | pass, 25 / 25 each | |
| SQL Server | 2017 | pass, 25 / 25 | `2017-latest` |
| Redis | 6.2.24 | pass, 96 cases | standalone, primary + replica with `WAIT`, 3-primary Cluster; the scripts use no 7.x-only command |
| MongoDB | 4.4 | pass, 46 / 46 | single-node replica set; concurrent first use of a namespace hits E11000 on 4.4, which the store retries on every version |
| MongoDB | 5.0 | pass, 46 / 46 | single-node replica set |
| ZooKeeper | 3.7.2, 3.6.4 | pass, 55 cases each | single server |
| ZooKeeper | 3.5 and older | not run | expected to fail: acquire reads slots with a read-only `multi` (3.6+) |

Measured on 2026-10-26/27 locally and by the non-blocking `probe` CI jobs. The module READMEs keep these tables
current.

## Operational preconditions

| Backend | Before production |
|---|---|
| JDBC | create the tables from `schema-<dialect>.sql` (or `initSchema: true`); rows are never deleted |
| Redis | AOF `appendfsync always` or `WAIT` covering every replica; `maxmemory-policy noeviction` (keys must never be evicted) |
| MongoDB | a replica set; the user may create the collections and indexes |
| ZooKeeper | never restore a slot node on a server whose clock is behind the node's original `mtime`, or wait one ttl (plus tolerance) before starting clients after such a restore |
| Remote | a token (auth is on by default); the server's backing store meets its own precondition |

## Store options common to all

```yaml
cosid:
  stores:
    <name>:
      type: jdbc            # jdbc | redis | mongo | zookeeper | remote | a StoreProvider bean's type
      timeout: 2s           # every store operation; at most the segments' reserve-timeout
      client: myDataSource  # optional: the bean to use as the client
      options: { ... }      # backend options, below on each page
```

Without `client`, Spring Boot uses the single bean of the type's client in the context (`DataSource`, a Lettuce
client or Spring Data `RedisConnectionFactory`, `MongoClient`, `CuratorFramework`), if there is one. Without a client,
the store connects from its options.
