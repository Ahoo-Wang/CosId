# 配置参考：cosid-server

`cosid.server.*`，由 cosid-server 的 `ServerProperties` 绑定。除此之外，服务端也读取普通的 `cosid.*`
属性（[运行时、存储、机器号](basic.md)、[生成器](generators.md)）：其后备存储是 `cosid.stores` 中的一个。
每个属性都可以从环境变量设置，例如 `COSID_SERVER_AUTH_TOKENS_0`，存储选项也一样
（`COSID_STORES_MAIN_OPTIONS_CONNECTSTRING` 设置 `connectString`：选项键不区分大小写）。

| 属性 | 默认值 | 含义 |
|---|---|---|
| `cosid.server.store` | 唯一的存储 | `cosid.stores` 中的后备存储；存在多个存储时必填 |
| `cosid.server.auth.allow-anonymous` | `false` | 未配置任何 token 与客户端时，允许所有调用不带 token；**两者都没有时，服务端拒绝启动** |
| `cosid.server.auth.tokens` | `[]` | 可使用所有命名空间与 key 的 token |
| `cosid.server.auth.clients[i].name` | | 客户端名称，用于日志 |
| `cosid.server.auth.clients[i].token` | | 它的 token |
| `cosid.server.auth.clients[i].namespaces` | `[]` | 槽位命名空间，以及首段为其中之一的区间 key；`*` 表示全部；id 端点检查 `cosid.namespace` |
| `cosid.server.auth.clients[i].key-prefixes` | `[]` | 带有这些前缀之一的区间 key |
| `cosid.server.auth.public-paths` | `/actuator/health`、`/actuator/health/**` | 无需 token 的路径模式 |
| `cosid.server.limits.min-ttl` | `100ms` | 最短的租约 ttl |
| `cosid.server.limits.max-ttl` | `1h` | 最长的租约 ttl |
| `cosid.server.limits.max-tolerance` | `1m` | 最大的 tolerance |
| `cosid.server.limits.max-slot-limit` | `65536` | 命名空间的最大槽位上限 |
| `cosid.server.limits.max-reserve` | `1000000000000` | 一次 reserve 获取的最大区间 |
| `cosid.server.limits.max-count` | `1000` | 一次 id 请求获取的最多 id 数 |
| `cosid.server.limits.max-keys-per-namespace` | `10000` | 单个服务端实例上，一个 client 在一个命名空间中使用的区间 key 数；`0` 表示不设上限 |
| `cosid.server.limits.max-namespaces-per-client` | `1000` | 单个服务端实例上，一个 client 使用区间 key 的命名空间数；`0` 表示不设上限 |
| `cosid.server.limits.key-idle-timeout` | `1h` | 超过这么久未使用的区间 key 不再计数；`0` 表示一直计数 |
| `cosid.server.blocking.threads` | `64` | 执行存储调用的有界线程池 |
| `cosid.server.blocking.queue-capacity` | `1024` | 可以排队等待的调用数；超出时返回 `503` 与 `Retry-After: 1` |
| `cosid.server.ids.enabled` | `false` | 可选的 `/ids/**` 端点；开启后拒绝名为 `strings` 或 `periodic` 的生成器 |
| `cosid.server.docs.public` | `false` | OpenAPI 文档与 Swagger UI 无需 token（追加到 `auth.public-paths`）；"Try it out" 发出的调用仍然需要 |

client token 只能访问存储原语与 `/ids/**`（位于 `spring.mvc.servlet.path` 之下）；其他路径，包括 Actuator 与 springdoc，都需要
`tokens` 中的 token。`management.server.port` 不同于 `server.port` 时服务端拒绝启动，匿名模式除外。5.0.0-alpha.1 的
`auth.admin-paths` 已删除：不再需要列出管理路径。

镜像自身的默认值（`application.yaml`）：`server.port: 8688`，请求运行在虚拟线程上（`spring.threads.virtual.enabled: true`；
设置 `SPRING_THREADS_VIRTUAL_ENABLED=false` 即可关闭），开启健康探针且 readiness 组包含后备存储（健康贡献者 `cosidServerStore`），Actuator 暴露
`health`、`info`、`prometheus` 与 `cosid`。`cosid.namespace` 默认为 `${spring.application.name}`
（`cosid-server`）。

## 从 4.x proxy server 迁移

`cosid-migration config` 会把所有 `cosid.proxy.server.*` 键报为丢弃；它们在 5.0 最接近的设置：

| 4.x `cosid.proxy.server.*` | cosid-server |
|---|---|
| `namespaces`（整台服务只服务这些命名空间） | 没有整台服务的列表：把每个 token 都写成带 `namespaces` 的 client（`auth.clients[i]`）；`auth.tokens` 中的 token 可访问所有命名空间 |
| `auth.*` | `auth.tokens`、`auth.clients[i]`、`auth.public-paths`、`auth.allow-anonymous` |
| `segment.max-step` | `limits.max-reserve` |
| `segment.max-distributors` | `limits.max-keys-per-namespace`（按 client 与命名空间） |
| `min-safe-guard-duration` | `limits.min-ttl`：租约是时间窗口，没有 guard |
| `max-clock-skew` | `limits.max-tolerance`：时钟偏差只在租约 tolerance 内影响可用性，从不影响唯一性（D22） |
| `blocking.*` | `blocking.threads`、`blocking.queue-capacity` |
