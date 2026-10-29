# JDBC

`cosid-store-jdbc`: `RangeStore` and `SlotLeaseStore` over a `javax.sql.DataSource`. It depends on `cosid-core` and
`java.sql` only; the driver and the pool are the application's choice.

## Supported databases (D27)

| Database | Minimum | Dialect | Store clock |
|---|---|---|---|
| MySQL | 8.4 LTS | `mysql` | `utc_timestamp(6)` |
| MariaDB | 10.11 LTS | `mariadb` | `utc_timestamp(6)` |
| PostgreSQL | 15 | `postgresql` | `clock_timestamp()` |
| SQL Server | 2019 | `sqlserver` | `sysutcdatetime()` |
| H2 | 2.x (development and tests) | `h2` | `current_timestamp` |

The dialect is detected from the connection metadata (MariaDB also behind the MySQL driver). The store clock is UTC
epoch milliseconds from the database, unaffected by session time zones; uniqueness does not depend on it.

Measured below the minimum, **not a support commitment**: MySQL 8.0, MariaDB 10.6, PostgreSQL 12–14 and SQL Server
2017 passed the TCK ([all results](index.md#supported-versions-and-measured-compatibility)).

## Configuration

```yaml
spring:
  datasource:
    url: jdbc:mysql://db:3306/app
cosid:
  stores:
    db:
      type: jdbc          # the context's DataSource (or `client: <bean>`)
      timeout: 2s         # the queryTimeout of every statement, rounded up to seconds
      options:
        initSchema: false
```

| Option | Default | Meaning |
|---|---|---|
| `url`, `username`, `password` | | a connection without a `DataSource` (through `DriverManager`, unpooled) |
| `dialect` | detected | `mysql`, `mariadb`, `postgresql`, `sqlserver`, `h2`, or a registered dialect's name |
| `initSchema` | `false` | create the missing tables at start-up, and the column and index tables of 5.0.0-alpha.1 lack |
| `rangeTable`, `slotTable`, `namespaceTable` | `cosid_range`, `cosid_slot`, `cosid_namespace` | table names, optionally schema-qualified |
| `fetchPlan` | `statement` | `transaction` behind an SQL proxy or read-write splitting (4.x `fetch-plan`): a reserve updates and reads its row in one transaction instead of reading the generated key |

The store never closes a `DataSource` it was given. In code: `JdbcStore.of(dataSource, timeout)`.

## Schema

The scripts are in the jar, at `me/ahoo/cosid/store/jdbc/schema-{mysql,postgresql,sqlserver,h2}.sql` (MySQL and MariaDB
share one), ready for the default names:

```sql
create table cosid_range (
    range_key   varchar(255) not null primary key,
    next_value  bigint       not null,
    sealed      boolean      not null default false
);
create table cosid_slot (
    namespace      varchar(100) not null,
    slot           integer      not null,
    owner_key      varchar(255),
    incarnation    char(36),
    last_owner_key varchar(255),
    fence          bigint       not null default 0,
    expires_at     bigint       not null default 0,
    floor_ms       bigint       not null default 0,
    ceiling_ms     bigint       not null default 0,
    next_floor_ms  bigint       not null default 0,
    primary key (namespace, slot)
);
create table cosid_namespace (
    namespace    varchar(100) not null primary key,
    slot_limit   integer      not null,
    tolerance_ms bigint       not null
);
```

Rows are never deleted (D23): a sealed key keeps its value. Physical clean-up of old period keys is the offline
`cosid-migration purge`.

## How it works

- **reserve**: one upsert that returns the new value, in one round trip. MySQL / MariaDB use
  `insert … on duplicate key update next_value = last_insert_id(next_value + ?)`, read from the statement's own result;
  both branches set `last_insert_id`, so a value left on a pooled connection is never read. PostgreSQL uses
  `on conflict … returning`, SQL Server `merge … with (holdlock) … output`, and H2 merges and reads in a transaction.
  The range is `[next − size, next)`. A sealed key updates no row (`RangeSealedException`), and past `Long.MAX_VALUE`
  the store throws `RangeExhaustedException`.
- **SQL proxies** (as 4.x): a `next_value` below the size cannot be the one the statement wrote, and the reserve fails
  instead of handing out a range; on MySQL / MariaDB so does a reserve that returned no generated key for a key that is
  neither sealed nor full. Behind a proxy that rewrites or loses `last_insert_id`, or with read-write splitting, set
  `fetchPlan: transaction`.
- **acquire**: the first use of a namespace records `{limit, tolerance}` and creates its `limit` slot rows. Then one
  non-locking read returns the candidates (the owner key's rows, the preferred slot, the first free slots) with the store
  clock. Each step of the slot choice is one auto-commit compare-and-set, `update … where namespace = ? and slot = ? and
  fence = ? and … (free by the database clock)`; zero rows moves on to the next candidate. A new owner takes two round
  trips.
- **renew / release**: read the lease's row, then a compare-and-set on incarnation and fence.

## Dialect SPI

Another database, or different SQL, is a `JdbcDialect` registered with `ServiceLoader`
(`META-INF/services/me.ahoo.cosid.store.jdbc.JdbcDialect`). Registered dialects are tried before the built-in ones and
can be named by `options.dialect`. This replaces 4.x's custom SQL settings and `server-time-sql`.

```java
public interface JdbcDialect {
    String name();
    boolean supports(DatabaseMetaData metaData) throws SQLException;
    List<String> schema(JdbcTables tables);
    String nowMillis();                         // the store clock, UTC epoch ms, as a SQL expression
    OptionalLong reserve(JdbcSession session, String key, long size, long initialOffset) throws SQLException;
    void provisionSlots(JdbcSession session, String namespace, int limit) throws SQLException;
    // defaults: firstRows, insertNamespaceIfAbsent, isDuplicateKey, isRetryable
}
```

A dialect must pass the [store TCK](test-suite.md) on its database.
