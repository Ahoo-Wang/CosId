# JDBC

`cosid-store-jdbc`：基于 `javax.sql.DataSource` 的 `RangeStore` 与 `SlotLeaseStore`。它只依赖 `cosid-core` 与
`java.sql`；驱动与连接池由应用自行选择。

## 支持的数据库（D27）

| 数据库 | 最低版本 | 方言 | 存储时钟 |
|---|---|---|---|
| MySQL | 8.4 LTS | `mysql` | `utc_timestamp(6)` |
| MariaDB | 10.11 LTS | `mariadb` | `utc_timestamp(6)` |
| PostgreSQL | 15 | `postgresql` | `clock_timestamp()` |
| SQL Server | 2019 | `sqlserver` | `sysutcdatetime()` |
| H2 | 2.x（开发与测试） | `h2` | `current_timestamp` |

方言根据连接元数据探测（使用 MySQL 驱动时也能识别 MariaDB）。存储时钟是来自数据库的 UTC 纪元毫秒，不受会话时区影响；
唯一性不依赖它。

低于最低版本的实测结果，**不承诺支持**：MySQL 8.0、MariaDB 10.6、PostgreSQL 12–14 与 SQL Server 2017
通过了 TCK（[全部结果](index.md#supported-versions-and-measured-compatibility)）。

## 配置

```yaml
spring:
  datasource:
    url: jdbc:mysql://db:3306/app
cosid:
  stores:
    db:
      type: jdbc          # 使用上下文中的 DataSource（或 `client: <bean>`）
      timeout: 2s         # 每条语句的 queryTimeout，向上取整到秒
      options:
        initSchema: false
```

| 选项 | 默认值 | 含义 |
|---|---|---|
| `url`、`username`、`password` | | 没有 `DataSource` 时的连接（通过 `DriverManager`，无连接池） |
| `dialect` | 自动探测 | `mysql`、`mariadb`、`postgresql`、`sqlserver`、`h2`，或已注册方言的名称 |
| `initSchema` | `false` | 启动时创建缺失的表 |
| `rangeTable`、`slotTable`、`namespaceTable` | `cosid_range`、`cosid_slot`、`cosid_namespace` | 表名，可带 schema 限定 |

存储绝不关闭传入的 `DataSource`。代码中：`JdbcStore.of(dataSource, timeout)`。

## 表结构

脚本在 jar 中，位于 `me/ahoo/cosid/store/jdbc/schema-{mysql,postgresql,sqlserver,h2}.sql`（MySQL 与 MariaDB
共用一个），按默认表名编写：

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

行永不删除（D23）：已封存的 key 保留其值。旧周期 key 的物理清理由离线的 `cosid-migration purge` 完成。

## 工作原理

- **reserve**：一条返回新值的 upsert，一次往返。MySQL / MariaDB 使用
  `insert … on duplicate key update next_value = last_insert_id(next_value + ?)`，从该语句自身的结果中读取；
  两个分支都会设置 `last_insert_id`，因此绝不会读到连接池中连接上残留的值。PostgreSQL 使用
  `on conflict … returning`，SQL Server 使用 `merge … with (holdlock) … output`，H2 在一个事务内 merge 并读取。
  区间为 `[next − size, next)`。已封存的 key 不更新任何行（`RangeSealedException`），超过 `Long.MAX_VALUE`
  时存储抛出 `RangeExhaustedException`。
- **acquire**：命名空间首次使用时记录 `{limit, tolerance}` 并创建 `limit` 行槽位。随后一次非锁定读取返回候选槽位
  （该 owner key 的行、preferred 槽位、前几个空闲槽位）及存储时钟。槽位选择的每一步是一次自动提交的比较并交换，
  `update … where namespace = ? and slot = ? and
  fence = ? and … (free by the database clock)`；影响 0 行则换下一个候选。新的持有者需要两次往返。
- **renew / release**：读取租约所在行，然后对 incarnation 与 fence 做比较并交换。

## 方言 SPI

其他数据库或不同的 SQL，通过以 `ServiceLoader` 注册的 `JdbcDialect` 实现
（`META-INF/services/me.ahoo.cosid.store.jdbc.JdbcDialect`）。已注册的方言先于内置方言尝试，
并可通过 `options.dialect` 指定名称。这取代了 4.x 的自定义 SQL 配置与 `server-time-sql`。

```java
public interface JdbcDialect {
    String name();
    boolean supports(DatabaseMetaData metaData) throws SQLException;
    List<String> schema(JdbcTables tables);
    String nowMillis();                         // 存储时钟，UTC 纪元毫秒，以 SQL 表达式表示
    OptionalLong reserve(JdbcSession session, String key, long size, long initialOffset) throws SQLException;
    void provisionSlots(JdbcSession session, String namespace, int limit) throws SQLException;
    // 默认方法：firstRows、insertNamespaceIfAbsent、isDuplicateKey、isRetryable
}
```

方言必须在其数据库上通过[存储 TCK](test-suite.md)。
