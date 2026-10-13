# JDBC

[`cosid-jdbc`](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-jdbc) stores segment counters and machine id leases in a relational database, through the application's `DataSource`:

- `JdbcIdSegmentDistributor`: segment counters, one row per generator in the `cosid` table.
- `JdbcMachineLeaseStore`: machine id leases, one row per machine id in the `cosid_machine` table. Lease times come from the database clock.

MySQL and MariaDB work with the defaults; PostgreSQL needs the statements of `PostgreSqlDialect`, see [below](#postgresql). Other databases work with their own statements.

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
| `cosid.segment.distributor.jdbc.init-cosid-table-sql`, `init-id-segment-sql`, `increment-max-id-sql`, `fetch-max-id-sql` | MySQL statements | The segment statements |
| `cosid.segment.distributor.jdbc.query-timeout` | `5s` | Each segment statement; `0` disables it |
| `cosid.machine.distributor.jdbc.enable-auto-init-cosid-machine-table` | `false` | Create the `cosid_machine` table at startup |
| `cosid.machine.distributor.jdbc.init-cos-id-machine-table-sql`, `init-namespace-idx-sql`, `init-instance-id-idx-sql` | MySQL statements | The machine table statements; empty index statements are skipped |
| `cosid.machine.distributor.jdbc.query-timeout` | `5s` | Each machine id statement; `0` disables it |
| `cosid.machine.distributor.jdbc.server-time-sql` | detected | The query for the database clock in epoch milliseconds. Unset, the starter picks it for MySQL, MariaDB and PostgreSQL; other databases must set it |

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

See [Rolling upgrade](../operations/rolling-upgrade.md) for the order of the steps.

## How a segment is fetched

With the default MySQL statements, a segment takes two statements under auto-commit and no transaction: `update cosid set last_max_id=last_insert_id(last_max_id + ?), ...` and `select last_insert_id()`, which reads the new value on the same connection (MySQL, MariaDB). With the PostgreSQL statements of `PostgreSqlDialect`, it takes one: `update ... returning last_max_id`. Any other configured statements run as before, the update then the select in one transaction. The connection goes back to the pool with the auto-commit mode it came with. The initializers' statements time out after `query-timeout`; a table, index or segment row that already exists is skipped quietly, any other error is logged at WARN with its cause.

## PostgreSQL

`me.ahoo.cosid.jdbc.PostgreSqlDialect` holds the PostgreSQL statements (tested on PostgreSQL 16). Set them as the properties above, or pass them to `JdbcIdSegmentInitializer`, `JdbcIdSegmentDistributorFactory` and `JdbcMachineIdInitializer` without the starter. The starter detects PostgreSQL for the server time query; without the starter, pass `PostgreSqlDialect.SERVER_TIME_SQL` to `JdbcMachineLeaseStore`.

```yaml
cosid:
  segment:
    distributor:
      type: jdbc
      jdbc:
        enable-auto-init-cosid-table: true
        init-cosid-table-sql: >-
          create table if not exists cosid (name varchar(100) not null, last_max_id bigint not null default 0,
          last_fetch_time bigint not null default 0, constraint cosid_pk primary key (name))
        init-id-segment-sql: >-
          insert into cosid (name, last_max_id, last_fetch_time) values (?, ?, cast(extract(epoch from now()) as bigint))
        increment-max-id-sql: >-
          update cosid set last_max_id=(last_max_id + ?),last_fetch_time=cast(extract(epoch from now()) as bigint) where name = ?
        fetch-max-id-sql: select last_max_id from cosid where name = ?
  machine:
    distributor:
      type: jdbc
      jdbc:
        enable-auto-init-cosid-machine-table: true
        init-cos-id-machine-table-sql: >-
          create table if not exists cosid_machine (name varchar(100) not null, namespace varchar(100) not null,
          machine_id integer not null default 0, last_timestamp bigint not null default 0,
          instance_id varchar(100) not null default '', distribute_time bigint not null default 0,
          revert_time bigint not null default 0, stable smallint not null default 0,
          renew_time bigint not null default 0, watermark bigint not null default 0, lease_version bigint not null default 0,
          constraint cosid_machine_pk primary key (name))
        init-namespace-idx-sql: create index if not exists idx_namespace on cosid_machine (namespace)
        init-instance-id-idx-sql: create index if not exists idx_instance_id on cosid_machine (instance_id)
```
