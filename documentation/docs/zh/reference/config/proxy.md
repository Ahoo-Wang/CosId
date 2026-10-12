# Proxy 配置

Proxy 的工作方式和部署方法见 [CosId Proxy](../../guide/backends/proxy.md)。

## 客户端

> `me.ahoo.cosid.spring.boot.starter.ProxyProperties`，前缀 `cosid.proxy`

`cosid.machine.distributor.type` 或 `cosid.segment.distributor.type` 为 `proxy` 时生效。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `host` | `String` | `http://localhost:8688` | Proxy 服务端的基础 URL |
| `token` | `String` | 无 | 每个请求携带的 Bearer 令牌；必须与服务端的某个令牌一致 |
| `connect-timeout` | `Duration` | `2s` | 连接服务端的超时；`0` 表示不限制 |
| `read-timeout` | `Duration` | `5s` | 等待响应的超时，包括启动时申领机器号；`0` 表示不限制，但 `cosid.machine.distributor.type=proxy` 时必须为正数 |

## 服务端

`cosid-proxy-server` 的属性；它的存储与应用一样通过 `cosid.machine` 和 `cosid.segment` 属性选择。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `cosid.proxy.server.auth.tokens` | `List<String>` | 空 | 接受的 Bearer 令牌；可配置多个以便轮换。发行包读取 `COSID_PROXY_TOKEN`。既没有令牌也没有开启 `allow-anonymous` 时，服务端启动失败 |
| `cosid.proxy.server.auth.public-paths` | `List<String>` | 健康检查端点 | 无需令牌的路径模式。默认为 `{base-path}/health` 和 `{base-path}/health/**`，`base-path` 即 `management.endpoints.web.base-path` |
| `cosid.proxy.server.auth.allow-anonymous` | `boolean` | `false` | 不使用令牌运行；仅在可信的网络边界之内使用 |
| `cosid.proxy.server.min-safe-guard-duration` | `Duration` | `5m` | 只有租约最后一次续约距今至少这么久，客户端才能接管该机器号。请不要大于客户端的 `cosid.machine.guarder.safe-guard-duration`；不能为负数 |
| `cosid.proxy.server.max-clock-skew` | `Duration` | `1m` | 客户端上报的最后时间戳最多可以比存储的时钟（v1 API 为服务端时钟）快多少；`0` 表示不检查；不能为负数 |
| `cosid.proxy.server.segment.max-step` | `long` | `1000000000000` | 客户端可申请的最大号段步长；`0` 表示不限制 |

超出限制的请求返回 `400`；没有有效令牌的请求返回 `401`。
