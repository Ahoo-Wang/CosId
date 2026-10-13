# JDBC

[`cosid-jdbc`](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-jdbc) 通过应用的 `DataSource` 把号段计数器和机器号租约保存在关系数据库中：

- `JdbcIdSegmentDistributor`：号段计数器，每个生成器在 `cosid` 表中占一行。
- `JdbcMachineLeaseStore`：机器号租约，每个机器号在 `cosid_machine` 表中占一行。租约时间取自数据库时钟。

MySQL 和 MariaDB 使用默认语句即可；PostgreSQL 需要使用 `PostgreSqlDialect` 中的语句，见[下文](#postgresql)。其他数据库可以配置自己的语句。

## 安装

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

同时添加对应数据库的 JDBC 驱动。

## 配置

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/cosid_db
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true # Snowflake ID 和 CosIdGenerator 需要
    distributor:
      type: jdbc
  segment:
    enabled: true # 号段 ID 需要
    distributor:
      type: jdbc
      jdbc:
        enable-auto-init-cosid-table: true
```

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `cosid.segment.distributor.jdbc.enable-auto-init-cosid-table` | `false` | 启动时用 `init-cosid-table-sql` 创建 `cosid` 表 |
| `cosid.segment.distributor.jdbc.enable-auto-init-id-segment` | `true` | 启动时用 `init-id-segment-sql` 插入缺失的生成器行 |
| `cosid.segment.distributor.jdbc.init-cosid-table-sql`、`init-id-segment-sql`、`increment-max-id-sql`、`fetch-max-id-sql` | MySQL 语句 | 号段相关语句 |
| `cosid.segment.distributor.jdbc.query-timeout` | `5s` | 每条号段语句；`0` 表示不限制 |
| `cosid.segment.distributor.jdbc.fetch-plan` | `auto` | `auto`：默认的 MySQL 语句在自动提交模式下以 `last_insert_id` 执行，PostgreSQL 语句以一条 `update ... returning` 执行，其他语句在事务中执行。`transaction`：始终在事务中先更新再查询。在 SQL 代理和读写分离中间件（ShardingSphere、MyCat、ProxySQL 读写分离、Vitess）之后请使用 `transaction`，它们可能不会传回更新语句的生成键 |
| `cosid.machine.distributor.jdbc.enable-auto-init-cosid-machine-table` | `false` | 启动时创建 `cosid_machine` 表 |
| `cosid.machine.distributor.jdbc.init-cos-id-machine-table-sql`、`init-namespace-idx-sql`、`init-instance-id-idx-sql` | MySQL 语句 | 机器号表相关语句；为空的索引语句会被跳过 |
| `cosid.machine.distributor.jdbc.query-timeout` | `5s` | 每条机器号语句；`0` 表示不限制 |
| `cosid.machine.distributor.jdbc.server-time-sql` | 自动识别 | 查询数据库时钟（epoch 毫秒）的语句。未设置时，Starter 会为 MySQL、MariaDB 和 PostgreSQL 自动选择；其他数据库必须设置 |

::: warning 使用独立的 DataSource
CosId 会自行提交它的写入（连接池设置了 `autoCommit=false` 时也一样）。请给它一个不参与业务事务的 `DataSource`：`TransactionAwareDataSourceProxy` 或 JTA `DataSource` 会提前提交调用方的事务。
:::

## 表结构

可以自己建表，也可以通过 `enable-auto-init-*` 属性让 CosId 建表。

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

### 升级 3.x 创建的表

4.0 把租约保存在 `renew_time`、`watermark` 和 `lease_version` 中，缺少这些列或 `stable` 列（3.3 新增）时，启动会失败并打印所需语句。请在启动 4.0 实例之前添加；3.x 实例在添加之后仍可正常工作：

```sql
-- MySQL / MariaDB
alter table cosid_machine add column stable tinyint not null default 0; -- 仅限 3.3 之前创建的表
alter table cosid_machine add column renew_time bigint unsigned not null default 0,
    add column watermark bigint unsigned not null default 0,
    add column lease_version bigint unsigned not null default 0;
-- PostgreSQL
alter table cosid_machine add column if not exists stable smallint not null default 0;
alter table cosid_machine add column if not exists renew_time bigint not null default 0,
    add column if not exists watermark bigint not null default 0,
    add column if not exists lease_version bigint not null default 0;
```

步骤顺序见[滚动升级](../operations/rolling-upgrade.md)。

## 号段的获取方式

使用默认的 MySQL 语句时，获取一个号段在自动提交模式下只执行一条语句，不开事务：`update cosid set last_max_id=last_insert_id(last_max_id + ?), ...`，它返回的生成键就是新值（MySQL、MariaDB）。使用 `PostgreSqlDialect` 的 PostgreSQL 语句时只执行一条：`update ... returning last_max_id`。其他自定义语句仍和以前一样，在一个事务中先更新再查询；设置 `fetch-plan: transaction` 时所有获取都如此。没有返回生成键，或生成键小于步长，说明 SQL 代理没有把更新的值传回：此时获取会失败并提示 `fetch-plan: transaction`，而不是发出重复的 ID。语句按原样比较，只忽略空白和末尾的 `;`：`update COSID ...` 不是默认语句。连接归还连接池时保持取出时的自动提交模式。初始化语句在 `query-timeout` 后超时；已存在的表、索引或号段行会被静默跳过，其他错误以 WARN 级别连同原因记录。

## PostgreSQL

`me.ahoo.cosid.jdbc.PostgreSqlDialect` 提供了 PostgreSQL 版本的语句（在 PostgreSQL 16 上测试）。可以设置为上面的属性，不使用 Starter 时则传给 `JdbcIdSegmentInitializer`、`JdbcIdSegmentDistributorFactory` 和 `JdbcMachineIdInitializer`。Starter 会为 PostgreSQL 自动识别服务器时间查询；不使用 Starter 时，把 `PostgreSqlDialect.SERVER_TIME_SQL` 传给 `JdbcMachineLeaseStore`。

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
