# CosId-Jdbc Module

[cosid-jdbc](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-jdbc) provides support for **relational databases**. Implements:

- `MachineIdDistributor`: As the machine ID distributor for **Snowflake algorithm** (`SnowflakeId`).
- `IdSegmentDistributor`: As the segment distributor for **segment algorithm** (`SegmentId`).

## Installation

::: code-group
```kotlin [Gradle(Kotlin)]
    val cosidVersion = "latestVersion"
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("me.ahoo.cosid:cosid-jdbc:${cosidVersion}")
```
```xml [Maven]
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jdbc</artifactId>
            <version>${springboot.version}</version>
        </dependency>
        <dependency>
            <groupId>me.ahoo.cosid</groupId>
            <artifactId>cosid-jdbc</artifactId>
            <version>${cosid.version}</version>
        </dependency>
    </dependencies>
```
:::

### Create `cosid` Table

The `cosid` table serves as the segment distribution record table for the segment distributor.

```sql
create table if not exists cosid
(
    name            varchar(100) not null comment '{namespace}.{name}',
    last_max_id     bigint       not null default 0,
    last_fetch_time bigint       not null,
    constraint cosid_pk
        primary key (name)
) engine = InnoDB;
```

### Create `cosid_machine` Table

```sql
create table if not exists cosid_machine
(
    name            varchar(100) not null comment '{namespace}.{machine_id}',
    namespace       varchar(100) not null,
    machine_id      integer      not null default 0,
    last_timestamp  bigint       not null default 0,
    instance_id     varchar(100) not null default '',
    distribute_time bigint       not null default 0,
    revert_time     bigint       not null default 0,
    stable          tinyint      not null default 0,
    renew_time      bigint       not null default 0,
    watermark       bigint       not null default 0,
    lease_version   bigint       not null default 0,
    constraint cosid_machine_pk
        primary key (name)
) engine = InnoDB;

create index if not exists idx_namespace on cosid_machine (namespace);
create index if not exists idx_instance_id on cosid_machine (instance_id);
```

`stable` marks the machine id of a stable instance (`cosid.machine.stable=true`): other instances never recycle it, even after
its owner has been offline for longer than the safe guard duration. Tables created by an earlier version lack the column and
keep the old behavior (a warning is logged at startup); add it with the statement below and restart, since the column is detected once at startup:

```sql
alter table cosid_machine add column stable tinyint not null default 0;
```

4.0 keeps the machine id lease in `renew_time`, `watermark` and `lease_version`. A table created by 3.x needs them before 4.0 instances start (3.x instances keep working with them); startup fails naming the statement when they are missing:

```sql
alter table cosid_machine add column renew_time bigint unsigned not null default 0,
    add column watermark bigint unsigned not null default 0,
    add column lease_version bigint unsigned not null default 0;
```

::: warning Use a dedicated DataSource
CosId commits its own writes (also when the pool has `autoCommit=false`). Give it a `DataSource` that does not take part in
business transactions: a `TransactionAwareDataSourceProxy` or a JTA `DataSource` would commit the caller's transaction early.
:::

### PostgreSQL

The default DDL and segment statements use MySQL syntax. `me.ahoo.cosid.jdbc.PostgreSqlDialect` holds PostgreSQL versions
(tested on PostgreSQL 16): pass them to `JdbcIdSegmentInitializer`, `JdbcIdSegmentDistributorFactory` and
`JdbcMachineIdInitializer`, or set them as the corresponding `cosid.*.distributor.jdbc` SQL properties.
The machine id distributor's statements are standard SQL and need no change.

With the Spring Boot starter:

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

## Configuration Example

```yaml {4,10,14}
spring:
  datasource:
    url:  # Jdbc distributor directly depends on DataSource
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true # Optional, needs to be set to true when using Snowflake algorithm
    distributor:
      type: jdbc
  segment:
    enabled: true # Optional, needs to be set to true when using segment algorithm
    distributor:
      type: jdbc
```