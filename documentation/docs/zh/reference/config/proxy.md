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
| `cosid.proxy.server.auth.clients` | `List<Client>` | 空 | 限定命名空间的令牌：每项包含 `token` 和它可以使用的 `namespaces`；没有命名空间的项不能使用任何命名空间（启动时警告）。建议每个应用一个，令牌泄露时也无法释放其他应用的机器号。受限令牌只能访问自己的命名空间和 `/machines/v2/time`：不能访问 Actuator、API 文档和 `/ids`，也不能使用包含点号的号段名称。`tokens` 中的令牌可以使用所有命名空间和路径 |
| `cosid.proxy.server.auth.public-paths` | `List<String>` | 健康检查端点 | 无需令牌的路径模式。默认为 `{base-path}/health` 和 `{base-path}/health/**`，`base-path` 即 `management.endpoints.web.base-path` |
| `cosid.proxy.server.auth.allow-anonymous` | `boolean` | `false` | 不使用令牌运行；仅在可信的网络边界之内使用 |
| `cosid.proxy.server.min-safe-guard-duration` | `Duration` | `5m` | 只有租约最后一次续约距今至少这么久，客户端才能接管该机器号。请不要大于客户端的 `cosid.machine.guarder.safe-guard-duration`；不能为负数 |
| `cosid.proxy.server.max-clock-skew` | `Duration` | `1m` | 客户端上报的最后时间戳最多可以比存储的时钟（v1 API 为服务端时钟）快多少；`0` 表示不检查；不能为负数 |
| `cosid.proxy.server.namespaces` | `List<String>` | 空 | 服务端提供服务的命名空间；请求其他命名空间时无论令牌如何都返回 `403`。为空表示所有命名空间 |
| `cosid.proxy.server.segment.max-step` | `long` | `1000000000000` | 客户端可申请的最大号段步长；`0` 表示不限制 |
| `cosid.proxy.server.segment.max-distributors` | `int` | `0` | 服务端在内存中保留的号段分发器（命名空间和名称的组合）上限，所有命名空间共享；超出后请求新的分发器返回 `429`。`0` 表示不限制；不可信的客户端能访问服务端时请设置，例如 `10000` |

超出限制的请求返回 `400`；没有有效令牌的请求返回 `401`；请求令牌或服务端不允许的命名空间返回 `403`；超出上限的新号段分发器返回 `429`。

命名空间取自机器号 API（v1 和 v2）和号段 API 的路径。`/ids` 使用服务端自己的命名空间，需要 `tokens` 中的令牌。存储以 `namespace.name` 作为号段的键，因此受限令牌（以及设置了 `cosid.proxy.server.namespaces` 时的所有令牌）不能使用包含点号的号段名称：`/segments/a/b.c` 会访问命名空间 `a.b` 的计数器 `c`。同理，如果一个命名空间的名称是另一个命名空间的前缀，请不要在其中使用包含点号的号段名称。

```yaml
cosid:
  proxy:
    server:
      auth:
        clients:
          - token: ${ORDER_SERVICE_TOKEN}
            namespaces: [order-service]
          - token: ${USER_SERVICE_TOKEN}
            namespaces: [user-service]
```
