# 配置参考：运行时、存储、机器号

Spring Boot starter 的全部 `cosid.*` 属性及其默认值，由 `cosid-spring-boot-autoconfigure` 的 `CosIdProperties`
record 绑定。生成器与编解码器：[生成器](generators.md)。cosid-server：
[`cosid.server.*`](server.md)。时长采用 Spring 的格式（`500ms`、`2s`、`1m`、ISO-8601）。

## 顶层

| 属性 | 默认值 | 含义 |
|---|---|---|
| `cosid.enabled` | `true` | 总开关；没有按功能划分的开关 |
| `cosid.namespace` | `cosid` | 隔离槽位与 key：同一应用的所有实例使用同一个命名空间 |
| `cosid.stores.<name>.*` | | 按名称配置的存储（见下文） |
| `cosid.machine.*` | | 机器号槽位，时间型生成器需要（见下文） |
| `cosid.generators.<name>.*` | | 按名称配置的生成器（[生成器](generators.md)）；`default` 为主 Bean |
| `cosid.metrics.generated` | `false` | 把每个生成器发出的 id 计入 `cosid.generator.generated`；会给每次调用增加一个计数器 |

## 存储：`cosid.stores.<name>`

| 属性 | 默认值 | 含义 |
|---|---|---|
| `type` | | `jdbc`、`redis`、`mongo`、`zookeeper`、`remote`，或某个 `StoreProvider` Bean 的类型 |
| `timeout` | `2s` | 每个存储操作；不超过其上号段的 `reserve-timeout` |
| `client` | | 用作客户端的 Bean；默认使用上下文中该类型客户端的唯一 Bean（`DataSource`；Lettuce 客户端或 `RedisConnectionFactory`；`MongoClient`；`CuratorFramework`） |
| `options.*` | | 后端选项，字符串 |

后端选项：

| 类型 | 选项（默认值） |
|---|---|
| `jdbc` | `url`、`username`、`password`；`dialect`（自动探测）；`initSchema`（`false`）；`rangeTable`（`cosid_range`）、`slotTable`（`cosid_slot`）、`namespaceTable`（`cosid_namespace`） |
| `redis` | `uri`；`cluster`（`false`）；`wait`（`auto` / `off` / 副本数）；`sweepBatch`（`16`） |
| `mongo` | `uri`；`database`（`cosid_db`） |
| `zookeeper` | `connectString`（`localhost:2181`）；`sessionTimeout`（`60s`）；`connectionTimeout`（`15s`）；`blockUntilConnectedWait`（`10s`）；`retry.baseSleepTime`（`100ms`）、`retry.maxRetries`（`5`）、`retry.maxSleep`（`500ms`） |
| `remote` | `baseUrl`（必填）；`token`；`connectTimeout`（`5s`）；`requestTimeout`（存储的 `timeout`） |

## 机器号槽位：`cosid.machine`

`leased` 与 `static` 必须且只能配置一个。带有时间型生成器（`snowflake`、`cosid80`）的运行时需要它。
没有时间型生成器时不获取槽位。

### `cosid.machine.leased`

| 属性 | 默认值 | 含义 |
|---|---|---|
| `store` | | 租用槽位所用的存储名称 |
| `bits` | `10` | 槽位范围为 `[0, 2^bits)`；不超过每个时间型布局的节点位数 |
| `ttl` | `30s` | 租约存活时间；每 `ttl/3` 续约一次；启动时 acquire 最多等待 `ttl` |
| `tolerance` | `1s` | 窗口 ceiling 超出过期时间的量（D20）；每个命名空间一个值 |
| `preferred-slot` | 无 | 优先尝试的槽位，例如 StatefulSet 序号（`${POD_ORDINAL:}`） |
| `owner-key` | `host:port`，否则 `host:pid` | 逻辑身份，用于粘性选择与观测 |

### `cosid.machine.static`

| 属性 | 默认值 | 含义 |
|---|---|---|
| `bits` | `10` | 槽位位数 |
| `slot` | | 槽位，由部署保证唯一 |
| `state-file` | 无 | 窗口文件；**没有它，就无法防护跨重启的时钟回拨** |
| `ahead` | `10s` | ceiling 提前写入的量 |
| `not-before` | 无 | 状态文件尚不存在时的 floor（由迁移工具写入） |

## 集成

| 属性 | 默认值 | 含义 |
|---|---|---|
| `cosid.mybatis.enabled` | `true` | MyBatis 拦截器，存在 MyBatis 时生效 |
| `cosid.mybatis.list-key` | `list` | 批量插入的参数 key |
| `cosid.jdbc.enabled` | `false` | Spring Data JDBC 回调 |
| `cosid.jdbc.id-generator` | `default` | 为未设置的 `@Id` 生成 id 的生成器 |
| `cosid.activiti.enabled` | `true` | Activiti id 生成器 |
| `cosid.activiti.generator-name` | `default`（系统属性 `cosid.activiti`） | |
| `cosid.flowable.enabled` | `true` | Flowable id 生成器 |
| `cosid.flowable.generator-name` | `default`（系统属性 `cosid.flowable`） | |
| `cosid.axon.generator-name` | `default`（系统属性 `cosid.axon`） | Axon 标识符工厂使用的生成器 |

## Actuator

| 属性 | 默认值 | 含义 |
|---|---|---|
| `management.health.cosid.enabled` | `true` | `cosId` 健康检查 |
| `management.endpoints.web.exposure.include` | Spring 的默认值 | 加入 `cosid` 以暴露该端点 |
