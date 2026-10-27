# 配置参考：cosid-server

`cosid.server.*`，由 cosid-server 的 `ServerProperties` 绑定。除此之外，服务端也读取普通的 `cosid.*`
属性（[运行时、存储、机器号](basic.md)、[生成器](generators.md)）：其后备存储是 `cosid.stores` 中的一个。
每个属性都可以从环境变量设置，例如 `COSID_SERVER_AUTH_TOKENS_0`。

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
| `cosid.server.limits.max-keys-per-namespace` | `10000` | 单个服务端实例上每个命名空间的区间 key 数；`0` 表示不设上限 |
| `cosid.server.blocking.threads` | `64` | 执行存储调用的有界线程池 |
| `cosid.server.blocking.queue-capacity` | `1024` | 可以排队等待的调用数；超出时返回 `503` 与 `Retry-After: 1` |
| `cosid.server.ids.enabled` | `false` | 可选的 `/ids/**` 端点 |

镜像自身的默认值（`application.yaml`）：`server.port: 8688`，开启健康探针，Actuator 暴露
`health`、`info`、`prometheus` 与 `cosid`。`cosid.namespace` 默认为 `${spring.application.name}`
（`cosid-server`）。
