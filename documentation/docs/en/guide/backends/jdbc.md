# JDBC

[`cosid-jdbc`](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-jdbc) stores segment counters and machine id leases in a relational database, through the application's `DataSource`:

- `JdbcIdSegmentDistributor`: segment counters, one row per generator in the `cosid` table.
- `JdbcMachineLeaseStore`: machine id leases, one row per machine id in the `cosid_machine` table. Lease times come from the database clock.

MySQL, MariaDB, PostgreSQL, H2 and SQL Server work without SQL properties: the starter detects the database, see [Databases](#databases). Other databases work with their own statements; Oracle is not supported.

## Installation

::: code-group
```kotlin [Gradle (Kotlin)]
dependencies {
    implementation(platform("me.ahoo.cosid:cosid-bom:$cosidVersion"))
    implementation("me.ahoo.cosid:cosid-spring-boot-starter")
    implementation("me.ahoo.cosid:cosid-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
}
```
```xml [Maven]
<dependencies>
    <dependency>
        <groupId>me.ahoo.cosid</groupId>
        <artifactId>cosid-spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>me.ahoo.cosid</groupId>
        <artifactId>cosid-jdbc</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-jdbc</artifactId>
    </dependency>
</dependencies>
```
:::

Add the JDBC driver of your database as well.

## Configuration

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/cosid_db
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true # for Snowflake IDs and CosIdGenerator
    distributor:
      type: jdbc
  segment:
    enabled: true # for segment IDs
    distributor:
      type: jdbc
      jdbc:
        enable-auto-init-cosid-table: true
```

| Property | Default | Description |
|----------|---------|-------------|
| `cosid.segment.distributor.jdbc.enable-auto-init-cosid-table` | `false` | Create the `cosid` table at startup with `init-cosid-table-sql` |
| `cosid.segment.distributor.jdbc.enable-auto-init-id-segment` | `true` | Insert the row of a generator at startup when it is missing, with `init-id-segment-sql` |
| `cosid.segment.distributor.jdbc.init-cosid-table-sql`, `init-id-segment-sql`, `increment-max-id-sql`, `fetch-max-id-sql` | detected | The segment statements. Left at their defaults (the MySQL statements), they follow the database |
| `cosid.segment.distributor.jdbc.query-timeout` | `5s` | Each segment statement; `0` disables it |
| `cosid.segment.distributor.jdbc.fetch-plan` | `auto` | `auto`: the built-in statements in one round-trip under auto-commit (see [How a segment is fetched](#how-a-segment-is-fetched)), others in a transaction. `transaction`: always the update then the select in a transaction. Use `transaction` behind SQL proxies and read/write splitters (ShardingSphere, MyCat, ProxySQL read/write splitting, Vitess), which may not pass the update's generated key through |
| `cosid.machine.distributor.jdbc.enable-auto-init-cosid-machine-table` | `false` | Create the `cosid_machine` table at startup |
| `cosid.machine.distributor.jdbc.init-cos-id-machine-table-sql`, `init-namespace-idx-sql`, `init-instance-id-idx-sql` | detected | The machine table statements; empty index statements are skipped. Left at their defaults with `enable-auto-init-cosid-machine-table`, they follow the database |
| `cosid.machine.distributor.jdbc.query-timeout` | `5s` | Each machine id statement; `0` disables it |
| `cosid.machine.distributor.jdbc.server-time-sql` | detected | The query for the database clock in epoch milliseconds. Unset, the starter picks it for MySQL, MariaDB, PostgreSQL, H2 and SQL Server; other databases must set it |

::: warning Use a dedicated DataSource
CosId commits its own writes (also when the pool has `autoCommit=false`). Give it a `DataSource` that does not take part in business transactions: a `TransactionAwareDataSourceProxy` or a JTA `DataSource` would commit the caller's transaction early.
:::

## Tables

Create the tables yourself, or let CosId create them with the `enable-auto-init-*` properties.

```sql
create table if not exists cosid
(
    name            varchar(100) not null comment '{namespace}.{name}',
    last_max_id     bigint unsigned not null default 0,
    last_fetch_time bigint unsigned not null default 0,
    constraint cosid_pk
        primary key (name)
) engine = InnoDB;

create table if not exists cosid_machine
(
    name            varchar(100)     not null comment '{namespace}.{machine_id}',
    namespace       varchar(100)     not null,
    machine_id      integer unsigned not null default 0,
    last_timestamp  bigint unsigned  not null default 0,
    instance_id     varchar(100)     not null default '',
    distribute_time bigint unsigned  not null default 0,
    revert_time     bigint unsigned  not null default 0,
    stable          tinyint          not null default 0,
    renew_time      bigint unsigned  not null default 0,
    watermark       bigint unsigned  not null default 0,
    lease_version   bigint unsigned  not null default 0,
    constraint cosid_machine_pk
        primary key (name),
    key idx_namespace (namespace),
    key idx_instance_id (instance_id)
) engine = InnoDB;
```

### Upgrading a 3.x table

4.0 keeps the lease in `renew_time`, `watermark` and `lease_version`, and fails at startup, printing the statement, when they or the `stable` column (added in 3.3) are missing. Add them before starting 4.0 instances; 3.x instances keep working with them:

```sql
-- MySQL / MariaDB
alter table cosid_machine add column stable tinyint not null default 0; -- only tables created before 3.3
alter table cosid_machine add column renew_time bigint unsigned not null default 0,
    add column watermark bigint unsigned not null default 0,
    add column lease_version bigint unsigned not null default 0;
-- PostgreSQL
alter table cosid_machine add column if not exists stable smallint not null default 0;
alter table cosid_machine add column if not exists renew_time bigint not null default 0,
    add column if not exists watermark bigint not null default 0,
    add column if not exists lease_version bigint not null default 0;
```

On H2 and SQL Server, `JdbcDialect.getAddLeaseColumnsSql()` is the statement. See [Rolling upgrade](../operations/rolling-upgrade.md) for the order of the steps.

## How a segment is fetched

With the default MySQL statements, a segment takes one statement under auto-commit and no transaction: `update cosid set last_max_id=last_insert_id(last_max_id + ?), ...`, whose generated key is the new value (MySQL, MariaDB). With the statements of the other databases it is one query too: `update ... returning last_max_id` (PostgreSQL), `select last_max_id from final table (update ...)` (H2), `update ... output inserted.last_max_id ...` (SQL Server). Any other configured statements run as before, the update then the select in one transaction, and so does every fetch with `fetch-plan: transaction`. No generated key, or one below the step, means a SQL proxy did not pass the update's value through: the fetch fails, naming `fetch-plan: transaction`, instead of handing out duplicate ids. The statements are compared exactly, apart from whitespace and a final `;`: `update COSID ...` is not the built-in statement. The connection goes back to the pool with the auto-commit mode it came with. The initializers' statements time out after `query-timeout`; a table, index or segment row that already exists is skipped quietly, any other error is logged at WARN with its cause.

## Databases

| Database | Tested on | Detected from the product name |
|----------|-----------|--------------------------------|
| MySQL, MariaDB | MySQL 8.0, 8.4 (MariaDB is detected and takes the MySQL statements, not tested in CI) | `MySQL`, `MariaDB` |
| PostgreSQL | 16 | `PostgreSQL` |
| H2 | 2.x, in Regular, MySQL, MariaDB or PostgreSQL mode; not in Oracle mode | `H2` |
| SQL Server | 2022 (2016 and later) | `Microsoft SQL Server` |

On SQL Server, instances that claim machine ids at the same time can deadlock: the database rolls one claim back (error 1205), and CosId runs that claim again, up to 8 times. SQL Server looks for deadlocks every 5 seconds, more often once it finds some, so a claim waiting on a deadlock can reach the default 5s `query-timeout` first and fail with a timeout, which is not retried. When many instances start together on SQL Server, set `cosid.machine.distributor.jdbc.query-timeout` to 10s or more.

A statement left at its default (the MySQL statement, compared apart from whitespace and a final `;`) runs as the statement of the database the connection reports, from `me.ahoo.cosid.jdbc.JdbcDialect`; any other value is used as configured. The product is read from the connection's metadata when a statement first needs it, not at startup: a database that is down or slow at startup is detected once it answers. The segment fetch keeps the choice once the product was read; the MySQL `last_insert_id` path runs only on a database that reports MySQL or MariaDB, and anything else, or a product that cannot be read, fetches in a transaction. This works the same without the starter. Oracle and other databases are not supported: set every statement, including `server-time-sql`. H2 in Oracle compatibility mode is treated as an unsupported database: like Oracle it stores `''` as `NULL`, and the machine leases mark a released row with `instance_id=''`.

The index statements (`init-namespace-idx-sql`, `init-instance-id-idx-sql`) are empty for MySQL, whose table statement declares the indexes. Left empty, they take the database's `create index` only when the table statement is left at its default too: next to a table statement of your own they stay empty, so set them if your table statement does not declare the indexes.

`JdbcDialect` also holds each database's statements, to pass explicitly (`JdbcDialect.POSTGRESQL.getIncrementMaxIdSql()`, ...); `PostgreSqlDialect` still holds the PostgreSQL ones as constants.
