# 配置参考：运行时、存储、机器号

Spring Boot starter 的全部 `cosid.*` 属性及其默认值，由 `cosid-spring-boot-autoconfigure` 的 `CosIdProperties`
record 绑定。生成器与编解码器：[生成器](generators.md)。cosid-server：
[`cosid.server.*`](server.md)。时长采用 Spring 的格式（`500ms`、`2s`、`1m`、ISO-8601）。

绑定不到任何字段的 `cosid.*` 键会让启动失败，拼错的键不会悄悄退回默认值。例外是 `cosid.stores.<name>.options.*`（由各后端自行校验，见下文）
、下文列出的集成与 server 的键，以及与进程中其他部分共用的系统属性和环境变量。

## 顶层

| 属性 | 默认值 | 含义 |
|---|---|---|
| `cosid.enabled` | `true` | 总开关；生成器、存储与 machine 没有各自的开关，集成有（[见下文](#集成)） |
| `cosid.namespace` | `spring.application.name`，否则 `cosid` 并记录 `WARN` | 隔离槽位与 key：同一应用的所有实例使用同一个命名空间；命名空间（含命名 machine 的）是 1 到 100 个字母、数字、`.`、`_`、`:`、`-`，以字母或数字开头（每个后端都能存）；应用名不是合法命名空间时启动失败，需显式设置 `cosid.namespace` |
| `cosid.stores.<name>.*` | | 按名称配置的存储（见下文） |
| `cosid.machine.*` | | 默认机器号槽位，时间型生成器需要（见下文） |
| `cosid.machines.<name>.*` | | 命名的机器号槽位，各自有独立的命名空间（见下文） |
| `cosid.generators.<name>.*` | | 按名称配置的生成器（[生成器](generators.md)）；`default` 为主 Bean |
| `cosid.metrics.generated.enabled` | `false` | 把每个生成器发出的 id 计入 `cosid.generator.generated`；会给每次调用增加一个计数器 |

## 存储：`cosid.stores.<name>`

| 属性 | 默认值 | 含义 |
|---|---|---|
| `type` | | `jdbc`、`redis`、`mongo`、`zookeeper`、`remote`，或某个 `StoreProvider` Bean 的类型 |
| `timeout` | `2s` | 每个存储操作；不超过其上号段的 `reserve-timeout` |
| `client` | | 用作客户端的 Bean；默认使用上下文中该类型客户端的唯一 Bean（`DataSource`；Lettuce 客户端或 `RedisConnectionFactory`；`MongoClient`；`CuratorFramework`） |
| `options.*` | | 后端选项，字符串 |

后端选项。大小写、`-` 与 `_` 不区分，所以 `init-schema`、`INIT_SCHEMA` 和环境变量 `COSID_STORES_DB_OPTIONS_INITSCHEMA`
都设置 `initSchema`；`.` 仍是分隔符（`retry.baseSleepTime`）。后端不认识的键会让启动失败，并列出它认识的键：

| 类型 | 选项（默认值） |
|---|---|
| `jdbc` | `url`、`username`、`password`；`dialect`（自动探测）；`initSchema`（`false`）；`rangeTable`（`cosid_range`）、`slotTable`（`cosid_slot`）、`namespaceTable`（`cosid_namespace`）；`fetchPlan`（`statement`；位于 SQL 代理之后时为 `transaction`） |
| `redis` | `uri`；`cluster`（`false`）；`wait`（`auto` / `off` / 副本数）；`sweepBatch`（`16`） |
| `mongo` | `uri`；`database`（`cosid_db`）；`writeConcern`（`majority`；更弱的设置会记录 `WARN`） |
| `zookeeper` | `connectString`（`localhost:2181`）；`sessionTimeout`（`60s`）；`connectionTimeout`（`15s`）；`blockUntilConnectedWait`（`10s`）；`retry.baseSleepTime`（`100ms`）、`retry.maxRetries`（`5`）、`retry.maxSleep`（`500ms`） |
| `remote` | `baseUrl`（必填）；`token`；`connectTimeout`（`5s`）；`requestTimeout`（存储的 `timeout`） |

## 机器号槽位：`cosid.machine`

`leased` 与 `static` 必须且只能配置一个。带有未选择命名 machine 的时间型生成器（`snowflake`、`cosid80`）的运行时需要它。
没有时间型生成器时不获取槽位。`cosid.machine.namespace`（默认 `cosid.namespace`）可把槽位放到另一个命名空间。

### `cosid.machine.leased`

| 属性 | 默认值 | 含义 |
|---|---|---|
| `store` | | 租用槽位所用的存储名称 |
| `bits` | `10` | 槽位范围为 `[0, 2^bits)`；不超过每个时间型布局的节点位数 |
| `ttl` | `30s` | 租约存活时间；每 `ttl/3` 续约一次 |
| `tolerance` | `1s` | 窗口 ceiling 超出过期时间的量（D20）；每个命名空间一个值 |
| `acquire-timeout` | 等于 `ttl` | 所有槽位都被占用时，启动等待槽位的时长；`0s` 只尝试一次；可与滚动发布的 startupProbe 对齐 |
| `slot-offset` | `0` | 加到槽位上得到 ID 中的节点号：节点号为 `[slot-offset, slot-offset + 2^bits)`，滚动迁移期间与 4.x 机器号错开；每个命名空间一个值，一旦使用就不能再改 |
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

## 命名的机器号：`cosid.machines.<name>`

供用 `cosid.generators.<g>.machine: <name>` 选择它的生成器使用的独立槽位，例如在多个服务共享的命名空间里为跨服务唯一的 cosid80 ID
取槽位。只有被启用的生成器选中的 machine 才会启动，因此同一份配置可以给并不全部使用它们的服务共用。

| 属性 | 默认值 | 含义 |
|---|---|---|
| `namespace` | | 必填：槽位的命名空间，不能与默认 machine 的相同；共享它的所有服务必须使用相同的 `bits`、`tolerance` 与 `slot-offset` |
| `leased.*` | | 同 `cosid.machine.leased` |
| `static.*` | | 同 `cosid.machine.static`；两个 static machine 需要两个状态文件 |

```yaml
cosid:
  machine:
    leased: { store: cache }
  machines:
    global:
      namespace: global-ids
      leased: { store: cache, slot-offset: 1024 }
  generators:
    token: { strategy: cosid80, machine: global }
```

受限的 cosid-server token 需要同时列出两个命名空间（`[order-service, global-ids]`）。

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
| `management.health.cosid.enabled` | `true` | `cosid` 健康检查 |
| `management.endpoints.web.exposure.include` | Spring 的默认值 | 加入 `cosid` 以暴露该端点 |
