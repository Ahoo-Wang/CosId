# CosId-Jdbc 模块

[cosid-jdbc](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-jdbc) 提供 **关系型数据库** 的支持。实现了：

- `JdbcMachineLeaseStore`：作为**雪花算法**(`SnowflakeId`)的机器号租约存储，由 `LeaseStoreMachineIdDistributor` 使用。
- `IdSegmentDistributor`：作为**号段算法**(`SegmentId`)的号段分发器 (`IdSegmentDistributor`)。

## 安装

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

### 创建 `cosid` 表

`cosid` 表作为号段分发器的号段分发记录表。

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

### 创建 `cosid_machine` 表

```sql
create table if not exists cosid_machine
(
    name            varchar(100) not null comment '{namespace}.{machine_id}',
    namespace       varchar(100) not null,
    machine_id      integer      not null default 0,
    last_timestamp  bigint unsigned not null default 0,
    instance_id     varchar(100) not null default '',
    distribute_time bigint unsigned not null default 0,
    revert_time     bigint unsigned not null default 0,
    stable          tinyint      not null default 0,
    renew_time      bigint unsigned not null default 0,
    watermark       bigint unsigned not null default 0,
    lease_version   bigint unsigned not null default 0,
    constraint cosid_machine_pk
        primary key (name)
) engine = InnoDB;

create index if not exists idx_namespace on cosid_machine (namespace);
create index if not exists idx_instance_id on cosid_machine (instance_id);
```

`stable` 标记稳定实例（`cosid.machine.stable=true`）的机器号：即使持有者离线超过安全守护时长，其他实例也不会回收它。
3.3 之前创建的表没有这一列：4.0 启动时会失败，并给出添加该列的语句（3.4 只打印告警）：

```sql
alter table cosid_machine add column stable tinyint not null default 0;
```

4.0 把机器号租约保存在 `renew_time`、`watermark` 和 `lease_version` 三列中。3.x 创建的表需要在启动 4.0 实例前加上它们（3.x 实例加列后照常工作）；缺少这几列时启动会失败，并给出要执行的语句：

```sql
-- MySQL / MariaDB
alter table cosid_machine add column renew_time bigint unsigned not null default 0,
    add column watermark bigint unsigned not null default 0,
    add column lease_version bigint unsigned not null default 0;
-- PostgreSQL
alter table cosid_machine add column if not exists renew_time bigint not null default 0,
    add column if not exists watermark bigint not null default 0,
    add column if not exists lease_version bigint not null default 0;
```

::: warning 使用独立的 DataSource
CosId 会自己提交写入（连接池关闭自动提交时也是）。请给它一个不参与业务事务的 `DataSource`：如果传入 `TransactionAwareDataSourceProxy` 或 JTA `DataSource`，
会提前提交调用方的事务。
:::

### PostgreSQL

默认的建表语句和号段语句使用 MySQL 语法。`me.ahoo.cosid.jdbc.PostgreSqlDialect` 提供了 PostgreSQL 版本（在 PostgreSQL 16 上测试过）：
传给 `JdbcIdSegmentInitializer`、`JdbcIdSegmentDistributorFactory` 和 `JdbcMachineIdInitializer`，或者配置到对应的 `cosid.*.distributor.jdbc` SQL 属性。
机器号租约存储会读取数据库时钟：starter 会为 MySQL、MariaDB 和 PostgreSQL 选择对应的查询，其他数据库必须配置
`cosid.machine.distributor.jdbc.server-time-sql`。不使用 starter 时，PostgreSQL 用户需要把 `PostgreSqlDialect.SERVER_TIME_SQL` 传给 `JdbcMachineLeaseStore`。

使用 Spring Boot starter 时：

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

## 配置案例

```yaml {4,10,14}
spring:
  datasource:
    url:  # Jdbc 分发器直接依赖 DataSource
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true # 可选，当需要使用雪花算法时，需要设置为 true
    distributor:
      type: jdbc
  segment:
    enabled: true # 可选，当需要使用号段算法时，需要设置为 true
    distributor:
      type: jdbc
```