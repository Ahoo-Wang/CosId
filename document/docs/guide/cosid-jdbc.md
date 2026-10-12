# CosId-Jdbc 模块

> 本文仅适用于 CosId 3.x 及更早版本，其中的部分类已在 4.0 移除。最新文档见 [cosid.ahoo.me](https://cosid.ahoo.me/zh/)，升级说明见[迁移指南](https://github.com/Ahoo-Wang/CosId/blob/main/docs/MIGRATION-4.0.md)。
> This page covers CosId 3.x and earlier; some classes it names were removed in 4.0. See [cosid.ahoo.me](https://cosid.ahoo.me/) and the [migration guide](https://github.com/Ahoo-Wang/CosId/blob/main/docs/MIGRATION-4.0.md).

[cosid-jdbc](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-jdbc) 使用**关系型数据库**作为**雪花算法**(`SnowflakeId`) 的 机器号分配器 (`MachineIdDistributor`) 、**号段算法**(`SegmentId`)的号段分发器 (`IdSegmentDistributor`)。

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
