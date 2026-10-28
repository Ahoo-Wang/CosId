# cosid-store-jdbc

The JDBC store of CosId 5 ([06-stores.md §2.1](../../docs/architecture/06-stores.md)): `RangeStore` and
`SlotLeaseStore` over a `javax.sql.DataSource`. It depends on `cosid-core` and the JDK's `java.sql` only; the driver
and the pool are the application's choice.

## Supported databases (D27)

| Database | Minimum | Dialect |
|---|---|---|
| MySQL | 8.4 LTS | `mysql` |
| MariaDB | 10.11 LTS | `mariadb` |
| PostgreSQL | 15 | `postgresql` |
| SQL Server | 2019 | `sqlserver` |
| H2 | 2.x (development and tests) | `h2` |

The dialect is detected from the connection metadata (MariaDB also behind the MySQL driver). Another database gets a
`JdbcDialect` of its own, registered with `ServiceLoader` under `me.ahoo.cosid.store.jdbc.JdbcDialect`; registered
dialects are tried before the built-in ones and can be named by `options.dialect`.

## Opening a store

`ServiceLoader` finds `JdbcStoreProvider` (type `jdbc`). `StoreConfig.client` is a `DataSource`, which should be pooled;
without one the provider connects through `DriverManager` from `options.url`, unpooled. Closing the store never closes
the data source.

| Option | Default | Meaning |
|---|---|---|
| `url`, `username`, `password` | | the connection, when there is no `DataSource` client |
| `dialect` | detected | `mysql`, `mariadb`, `postgresql`, `sqlserver`, `h2` or a registered dialect |
| `initSchema` | `false` | create the tables that are missing, and add `cosid_namespace.slot_offset` and the index `cosid_slot_last_owner_key` to tables of 5.0.0-alpha.1 |
| `rangeTable`, `slotTable`, `namespaceTable` | `cosid_range`, `cosid_slot`, `cosid_namespace` | table names: identifiers, optionally schema-qualified |
| `fetchPlan` | `statement` | `transaction` behind an SQL proxy or read-write splitting: a reserve updates and reads its row in one transaction instead of reading a generated key (4.x `fetch-plan`) |

`StoreConfig.timeout` is the `queryTimeout` of every statement, rounded up to seconds (it is positive); a statement that
runs out of it fails with `StoreTimeoutException` (`JdbcDialect.isTimeout`). So does a pool that hands out no connection
in time (`SQLTransientConnectionException`, SQL state class `08`), and an acquire, renew or release whose
compare-and-set loop is still losing races when the timeout has passed. In code:
`JdbcStore.of(dataSource, timeout)`, or `new JdbcStore(dataSource, dialect, tables, timeout[, fetchPlan])`.

## Schema

`src/main/resources/me/ahoo/cosid/store/jdbc/schema-{mysql,mariadb,postgresql,sqlserver,h2}.sql`, runnable as they
are for the default names. Rows are never deleted (D23): a sealed range key keeps its value.

`cosid_slot` has an index on `(namespace, last_owner_key)`, named after the slot table (`<slotTable>_last_owner_key`):
an acquire reads the rows its owner key holds or held last through it instead of scanning the namespace. It came after
5.0.0-alpha.1; `initSchema` adds it to an older table (`create index … on cosid_slot (namespace, last_owner_key)`), and
without it acquires still work, scanning.

`cosid_namespace (namespace, slot_limit, tolerance_ms, slot_offset)` records the parameters of a namespace on its first
use. `slot_offset integer not null default 0` came after 5.0.0-alpha.1, whose `cosid_namespace` lacks it (and
`create table if not exists` does not add it):

- with `initSchema`, the store adds it (`alter table cosid_namespace add column slot_offset integer not null default 0`;
  SQL Server: `add slot_offset …`, without `column`); existing namespaces read offset 0;
- without, the store detects the missing column once (`DatabaseMetaData.getColumns`) and keeps working for slot offset
  0: the offset reads as 0 and new namespaces are recorded without the column. A slot offset other than 0 then fails
  with an `IllegalStateException` that names the `alter table` to run.

Keys are exact, as on every other backend: range keys, namespaces and owner keys that differ only in case or in a
trailing space are different keys. The key columns therefore use binary collations that keep trailing spaces
(`utf8mb4_0900_bin` on MySQL, `utf8mb4_nopad_bin` on MariaDB; PostgreSQL and H2 compare exactly anyway). SQL Server
uses `Latin1_General_100_BIN2`, which makes keys case-sensitive, but it ignores trailing spaces when comparing strings
under any collation: there the store rejects a key that ends with a space (`IllegalArgumentException`) rather than
let it share the row of the key without it.

## How it works

- **reserve**: one upsert per reservation that also returns the new value, so one round trip on MySQL / MariaDB
  (`insert … values (?, last_insert_id(? + ?)) on duplicate key update next_value = last_insert_id(…)`, read from the
  statement's own OK packet; every branch sets `last_insert_id`, so a value left on a pooled connection is never read;
  without a generated key the store checks the row is sealed or full and otherwise fails, rather than ask the connection),
  PostgreSQL (`on conflict … returning`) and SQL Server (`merge … with (holdlock) … output`); H2 merges and reads in a
  transaction. The update branch stops at `Long.MAX_VALUE` (`RangeExhaustedException`) and at a sealed key
  (`RangeSealedException`).
- **SQL proxies** (as 4.x): a `next_value` below the size cannot be the one the statement wrote, and the reserve fails
  instead of handing out a range. Behind a proxy that rewrites or loses `last_insert_id`, or with read-write splitting,
  set `fetchPlan: transaction`: the update and a read of the row in one transaction, one more round trip.
- **acquire**: the first use of a namespace records `{limit, tolerance, slotOffset}` and creates its `limit` slot
  rows (the offset does not change the slot choice); another use with other parameters fails with
  `NamespaceMismatchException`. Then one non-locking read returns the candidates (the rows of the owner key through the `last_owner_key` index, the preferred slot, the first 8 free slots) with
  the store clock, and each step of 05 §2.1 is one auto-commit compare-and-set `update … where namespace = ? and slot =
  ? and fence = ? and next_floor_ms = ? and (free by the store clock)`; 0 rows moves on to the next candidate. After
  losing the lowest free slot to a concurrent acquire, the store picks a random one of the other candidates. Two round
  trips for a new owner.
- **renew / release**: read the row of the lease, then a compare-and-set on incarnation, fence, expiry and ceiling.
- **store clock**: UTC epoch milliseconds from the database (`utc_timestamp(6)`, `clock_timestamp()`,
  `sysutcdatetime()`, `current_timestamp`); uniqueness does not depend on it (05 §3).

## Tests

The TCK runs on H2 always, and on each database from `COSID_TEST_{MYSQL,MARIADB,POSTGRES,SQLSERVER}_URL` (with
`_USERNAME` / `_PASSWORD`) or on Testcontainers at the minimum version (`COSID_TEST_<NAME>_IMAGE` picks another image);
without either a database's classes skip. `checkContractsRan` checks each dialect's reports on their own: H2 by default,
all of them with `-Pcosid.store.jdbc.dialects=h2,mysql,mariadb,postgresql,sqlserver` (the `Store JDBC` CI job, which runs
the minimum and a current version of every database).

## 实测兼容性(不承诺)

The TCK (`RangeStoreContract`, `SlotLeaseStoreContract`, and for MySQL / MariaDB the stale `last_insert_id` cases) run
once on versions below the minimum (D28), to record what actually works. **This is no support commitment**: these
versions get no fixes, and a pass here says nothing about future releases. No workaround was added for any of them. The
`Store JDBC (probe …)` CI entries rerun them without failing the build; their summary shows the outcome.

| Version | TCK | Notes |
|---|---|---|
| MySQL 8.0 (8.0.46) | PASS (28 / 28) | |
| MariaDB 10.6 | PASS (28 / 28) | |
| PostgreSQL 12 | PASS (25 / 25) | |
| PostgreSQL 13 | PASS (25 / 25) | |
| PostgreSQL 14 | PASS (25 / 25) | |
| SQL Server 2017 | PASS (25 / 25) | `2017-latest`; locally under emulation and by the CI probe |

Measured on 2026-10-27 locally (Apple M4, Docker, Testcontainers images `mysql:8.0`, `mariadb:10.6`,
`postgres:{12,13,14}-alpine`, `mcr.microsoft.com/mssql/server:2017-latest`) and again by the CI probes.
