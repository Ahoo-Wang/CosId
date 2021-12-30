# CosId-Jdbc 模块

## 安装

> Kotlin DSL

``` kotlin
    implementation("me.ahoo.cosid:cosid-jdbc:${cosidVersion}")
```

## JdbcIdSegmentDistributor

<p align="center" >
  <img :src="$withBase('/assets/design/SegmentId.png')" alt="SegmentId"/>
</p>

使用关系型数据库作为号段算法（`SegmentId`）的号段分发器。

### 创建 `cosid` 表

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

## JdbcMachineIdDistributor

### 创建 `cosid_machine` 表

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
