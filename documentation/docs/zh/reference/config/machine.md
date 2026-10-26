# 机器号配置

> `me.ahoo.cosid.spring.boot.starter.machine.MachineProperties`，前缀 `cosid.machine`

Snowflake ID 和 `CosIdGenerator` 需要机器号。租约的工作方式见[机器号](../../guide/concepts/machine-id.md)。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enabled` | `boolean` | `false` | 分配机器号。使用 `cosid.snowflake` 和 `cosid.generator` 时必须开启 |
| `instance-id` | `String` | `{host}:{pid}:{random}`（非稳定实例）、`{host}:{pid}`（稳定实例），设置了 `port` 时为 `{host}:{port}` | 实例在租约存储中的身份。重启后身份不变的实例请同时设置它和 `stable` |
| `port` | `Integer` | 未设置 | 设置后默认实例 ID 为 `{host}:{port}`，不带随机后缀 |
| `stable` | `Boolean` | 未设置（视为 `false`） | 稳定实例的机器号永远不会被其他实例回收，它也不会接管其他实例已过期的租约，并且会把机器状态保存在本地文件中 |
| `machine-bit` | `int` | `10` | 机器号位数；生成器可以用自己的 `machine-bit` 覆盖 |
| `state-storage.local.state-location` | `String` | `~/.cosid-machine-state` | 稳定实例本地机器状态的目录 |

::: tip 稳定实例与升级
只有所有实例都运行 3.3 及以上版本时，稳定标记才能保护机器号：旧版本不读取该标记。已经离线的稳定实例，在新版本上启动一次、记录下标记之前不受保护。JDBC 需要 `stable` 列，见 [JDBC](../../guide/backends/jdbc.md#升级-3-x-创建的表)。
:::

## 分配器

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `distributor.type` | `String` | `manual` | `manual`、`stateful_set`、`redis`、`jdbc`、`mongo`、`zookeeper`、`proxy`，或应用中某个 `CosIdStoreProvider` 的 `getType()`。只比较字母和数字、忽略大小写，因此 `stateful-set` 和 `statefulSet` 同样有效 |
| `distributor.manual.machine-id` | `Integer` | 无 | `manual`：机器号；必填 |
| `distributor.redis.timeout` | `Duration` | `1s` | `redis`：租约续约和释放；必须为正数 |
| `distributor.redis.distribute-timeout` | `Duration` | `10s` | `redis`：启动时申领的每条命令，第一条命令还包含建立连接的时间；必须为正数 |
| `distributor.redis.wait-replicas` | `int` | `0` | `redis`：每次申领和续约需要确认的副本数（`WAIT`）；`0` 表示不等待 |
| `distributor.redis.wait-timeout` | `Duration` | `500ms` | `redis`：申领或续约等待副本确认的时长；必须短于 `timeout`、`distribute-timeout` 和 `spring.data.redis.timeout`，否则启动失败 |
| `distributor.jdbc.enable-auto-init-cosid-machine-table` | `boolean` | `false` | `jdbc`：启动时创建 `cosid_machine` 表 |
| `distributor.jdbc.init-cos-id-machine-table-sql` | `String` | 自动识别（未知数据库时为 MySQL 语句） | `jdbc`：建表语句。保持默认值时随数据库而定（见 [JDBC 数据库](../../guide/backends/jdbc.md#数据库)）；设置的值按配置使用 |
| `distributor.jdbc.init-namespace-idx-sql`、`init-instance-id-idx-sql` | `String` | 空 | `jdbc`：单独的建索引语句，为空时跳过（MySQL 建表语句已内联声明索引）。与默认建表语句一起保持为空时，改用识别出的数据库的 `create index`；与自定义建表语句一起时仍为空 |
| `distributor.jdbc.query-timeout` | `Duration` | `5s` | `jdbc`：每条语句；`0` 表示不限制 |
| `distributor.jdbc.server-time-sql` | `String` | 自动识别 | `jdbc`：查询数据库时钟（epoch 毫秒）的语句。未设置时，Starter 会为 MySQL、MariaDB、PostgreSQL、H2 和 SQL Server 自动选择；其他数据库必须设置 |
| `distributor.mongo.database` | `String` | `cosid_db` | `mongo`：`cosid_machine` 集合所在的数据库 |
| `distributor.mongo.timeout` | `Duration` | `5s` | `mongo`：每次操作（驱动的 `timeoutMS`）；必须为正数 |
| `distributor.mongo.write-concern` | `String` | | `mongo`：写入的写关注；未设置时使用连接串的设置 |
| `distributor.zookeeper.timeout` | `Duration` | `5s` | `zookeeper`：租约续约和释放（Curator 没有单次操作超时）；必须为正数 |

Proxy 客户端的超时见 [Proxy 配置](./proxy.md)。必须为正数的超时设置为 `0` 时启动失败。

`cosid.machine.distributor.provider` 已在 4.0 移除：请改为把 `distributor.type` 设置为 Provider 的类型。

## 守护器

守护器负责续约机器号租约。前缀 `cosid.machine.guarder`。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enabled` | `boolean` | `true` | 是否续约。为 `false` 时本实例以永不过期的方式申领且从不续约，因此同样关闭守护的实例不会回收它的机器号；但同一命名空间中开启守护的实例会在其安全守护时长过后接管该机器号，导致两者生成相同的 ID：同一命名空间要么所有实例都关闭守护，要么都不关闭。租约存储在关闭守护时运行，启动会打印告警 |
| `initial-delay` | `Duration` | `1m` | 第一次续约前的延迟 |
| `delay` | `Duration` | `1m` | 两次续约之间的间隔 |
| `safe-guard-duration` | `Duration` | `5m` | 超过这个时长未续约的租约可以被其他实例接管。**同一命名空间的所有实例必须使用相同的值** |
| `fail-fast` | `boolean` | `true` | 租约丢失，或超过 `safe-guard-duration` 减去 `min(10s, safe-guard-duration / 10)` 未续约时，生成器抛出 `MachineIdLostException` 停止生成 |
| `local-state-interval` | `Duration` | `1s` | 稳定实例把最后时间戳写入本地状态文件的间隔，用于重启后的时钟回拨检查。进程崩溃后，短于这个间隔的时钟回拨可能无法发现。`0` 表示关闭 |

## 时钟回拨

前缀 `cosid.machine.clock-backwards`。用于 `ClockSyncSnowflakeId`、`CosIdGenerator`，以及启动时与租约水位线的比对。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `spin-threshold` | `int` | `1` | 不超过这么多毫秒的回拨通过自旋等待 |
| `broken-threshold` | `int` | `500` | 不超过这么多毫秒的回拨通过休眠等待；更长的回拨抛出 `ClockTooManyBackwardsException`。必须大于 `spin-threshold` |

## 示例

```yaml
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true
    distributor:
      type: jdbc
    guarder:
      safe-guard-duration: 5m
```
