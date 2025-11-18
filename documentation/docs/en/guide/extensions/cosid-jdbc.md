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
    constraint cosid_machine_pk
        primary key (name)
) engine = InnoDB;

create index if not exists idx_namespace on cosid_machine (namespace);
create index if not exists idx_instance_id on cosid_machine (instance_id);
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