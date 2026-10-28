# cosid-server

cosid-server 是 CosId 的远程服务（D8）。它是一个 Spring Boot 应用，以 Docker 镜像发布。它通过 HTTP 暴露其**后备存储**
（`cosid.stores` 之一：JDBC、Redis、MongoDB 或 ZooKeeper）的两个存储原语，应用通过 `remote` 存储 `cosid-store-remote` 访问它：

```
application ──(cosid-store-remote, HTTP + token)──▶ cosid-server ──▶ JDBC | Redis | MongoDB | ZooKeeper
```

- 每次 HTTP 调用对应后备存储的一次调用。存储负责选槽，并用自己的时钟为每个时间打戳，因此**服务端不保存任何状态**，
  可以在负载均衡器后水平扩展。所有一致性都由存储保证。
- 可选地（`cosid.server.ids.enabled=true`），服务端用自己的运行时发号。这服务于不在 JVM 上或无法访问存储的客户端。
- 4.x proxy 的 v1 与 v2 API 已删除（D1），路径不带版本前缀（D30）。

## 运行

```bash
docker run -d -p 8688:8688 \
  -e COSID_STORES_MAIN_TYPE=redis \
  -e COSID_STORES_MAIN_OPTIONS_URI=redis://redis:6379 \
  -e COSID_SERVER_AUTH_TOKENS_0=change-me-to-a-long-random-token \
  ahoowang/cosid-server:__COSID_VERSION__
```

每项设置都可以来自环境变量（`COSID_SERVER_AUTH_TOKENS_0`）或挂载的 `application.yaml`。镜像以非 root 用户在 8688 端口运行；
`/actuator/health/liveness` 不访问存储即可应答，用作容器健康检查。`/actuator/health/readiness` 还会检查后备存储
（在 key `cosid-server/readiness` 上预留大小为 1 的区间，同一时间最多一次，结果复用 1 秒，2 秒内无应答即为 `DOWN`），
存储宕机时负载均衡器就不再向该实例发送调用。带 `options.url` 的 JDBC 后备存储会得到一个大小为
`blocking.threads` 的 HikariCP 连接池，镜像内置 MySQL、MariaDB、PostgreSQL 与 SQL Server 驱动。
[`examples/server/compose.yaml`](https://github.com/Ahoo-Wang/CosId/tree/main/examples/server) 在 Redis 上运行服务端，并附带一个客户端。
从源码构建镜像：`./gradlew :server:dockerImage`（或 `:server:bootBuildImage`）。

## 客户端

```yaml
cosid:
  stores:
    remote:
      type: remote
      timeout: 2s
      options:
        baseUrl: http://cosid-server:8688
        token: ${COSID_SERVER_TOKEN}
        connectTimeout: 5s
        requestTimeout: 2s    # 默认为 StoreConfig.timeout
  machine:
    leased:
      store: remote
  generators:
    order:
      strategy: segment
      store: remote
```

客户端使用 JDK `HttpClient`，没有第三方依赖。时长可以是 `Duration`、毫秒数、ISO-8601，或 `500ms` / `3s` / `1m` / `1h`。
有明确结果的应答会映射为对应结果：

| 状态 | 客户端 |
|---|---|
| `410 range_sealed` | `RangeSealedException` |
| `422 range_exhausted` | `RangeExhaustedException` |
| `409 slots_exhausted` | `AcquireResult.Exhausted` |
| `409 lease_lost` | 空的 renew 结果、`false` 的 release 结果 |
| `409 namespace_mismatch` | `NamespaceMismatchException` |
| seal 时返回 `501` | `false` |
| `504 store_timeout` | 原因为 `StoreTimeoutException` 的 `RemoteStoreException`（计为存储超时） |
| 其他 | 带 `status()`、`code()` 的 `RemoteStoreException`；`503`、`504` 以及没有应答的调用为 `transientFailure()` |

## API

key 与命名空间放在 JSON 请求体中，从不放在路径里。错误为 `{code, message}`。

| 方法 | 路径 | 请求 | 响应 |
|---|---|---|---|
| POST | `/ranges:reserve` | `{key, size, initialOffset}` | `{start, end}`；`410 range_sealed`；`422 range_exhausted` |
| POST | `/ranges:seal` | `{key}` | `204`；`501 seal_unsupported` |
| POST | `/slots:acquire` | `{namespace, ownerKey, incarnation, limit, ttlMs, toleranceMs, preferredSlot?}` | 租约 + `{reason, duplicateOwnerKey}`；`409 slots_exhausted {earliestExpiresAt}`；`409 namespace_mismatch` |
| POST | `/slots:renew` | `{namespace, slot, ownerKey, incarnation, fence, ttlMs, toleranceMs}` | 租约；`409 lease_lost` |
| POST | `/slots:release` | `{namespace, slot, ownerKey, incarnation, fence, lastIssued}` | `204`；`409 lease_lost` |
| GET | `/ids[/{generator}]?count=n` | | `{ids: [...]}`（可选） |
| GET | `/ids[/{generator}]/strings?count=n` | | `{ids: ["..."]}`，经生成器的编解码器编码（可选） |
| GET | `/ids/{generator}/periodic?count=n` | | `{period, values: [...]}`，全部属于同一周期（可选） |

租约为 `{namespace, slot, ownerKey, incarnation, fence, expiresAt, floor, ceiling, storeNow}`。路径中没有生成器时，
发号端点使用 `default`。`periodic` 应答在周期边界处截止，因此可能少于 `count` 个值；已禁用的生成器应答 `409 generator_disabled`。

其他状态：`400 bad_request`（缺少字段、字符集或长度不合法、值超过上限）、`401`（没有 token）、`403`（token 超出其命名空间，
或 client token 访问业务路径以外的路径）、`429 too_many_keys` / `too_many_namespaces`、带 `Retry-After: 1` 的 `503 busy`、`504 store_timeout`
以及 `500 store_error`。后备存储的错误只返回通用消息，细节写入服务端日志。每个响应都带有 `Cache-Control: no-store`。
OpenAPI 文档位于 `/v3/api-docs`，Swagger UI 位于 `/swagger-ui.html`；两者都需要不受限的 token，除非设置了 `cosid.server.docs.public=true`（见下文）。

## 认证

**Bearer token 默认开启。** 没有配置任何 token 或 client 时，除非设置 `cosid.server.auth.allow-anonymous=true`
（见[受信任的内网](#受信任的内网免-token-部署)），否则服务端拒绝启动。服务端还拒绝短于 16 个字符的 token，也拒绝仍含 `${` 的
token：环境变量没有设置时，占位符不能变成一个谁都能在文档里读到的 token。

```yaml
cosid:
  server:
    auth:
      tokens: [ "${COSID_SERVER_TOKEN}" ]          # 可以访问一切
      clients:
        - name: orders
          token: ${COSID_ORDERS_TOKEN}
          namespaces: [ orders ]                   # 槽位命名空间，以及第一段为其中之一的区间 key
          key-prefixes: [ shared/orders- ]         # 或带有这些前缀之一的区间 key
      public-paths: [ /actuator/health, /actuator/health/** ]
```

client token 只能访问其命名空间（`*` 表示全部）与 key 前缀：其他一律 `403`。发号端点用服务端自身运行时的 `cosid.namespace`
与 client 的命名空间做比对。client token **只能访问业务路径**：存储原语（`/ranges:*`、`/slots:*`）与 `/ids/**`，设置了
`spring.mvc.servlet.path` 时位于其下。其他路径对它一律 `403`，不论 `management.endpoints.web.base-path` 或 `springdoc.*.path`
把 Actuator 与 springdoc 移到哪里：它们不区分命名空间（`cosid` 端点可以为任意生成器发号），所以只有不受限的 `tokens` 可以访问。
公开路径先于它判断，health 仍然公开；公开路径按配置原样匹配，移动 Actuator 时要一并修改。带 `.` 或 `..` 路径段的请求返回 `400`。

Actuator 运行在独立端口（`management.server.port` 不同于 `server.port`）时不经过这套认证，所以服务端拒绝启动，匿名模式除外。
请让 Actuator 留在主端口；health 在主端口上公开，可供探针使用。

### 在浏览器里查看 Swagger UI

浏览器打开页面时不会带 Bearer token，所以要在浏览器里查看 Swagger UI，需要设置 `cosid.server.docs.public=true`
（`COSID_SERVER_DOCS_PUBLIC=true`，默认 `false`）。它把 springdoc 的只读路径（`/swagger-ui.html`、`/swagger-ui/**`、
`/v3/api-docs`、`/v3/api-docs/**`、`/v3/api-docs.yaml`，随 `springdoc.*.path` 与 servlet 路径变化）追加到 `public-paths`，
而不是替换它，health 仍然公开。此后能访问服务端的任何人，不论是否带 token，都能阅读 API 文档，其中也包括 Actuator 端点
（`springdoc.show-actuator: true`；设为 `false` 即不列出），但无法调用任何端点："Try it out" 需要先通过 Authorize 填入 token，
并像其他调用一样鉴权；Actuator 仍然需要不受限的 token。

### 受信任的内网免 token 部署

在受信任的网络内（例如 Kubernetes 集群内），服务端可以不用 token 运行。不配置任何 token 与 client，并允许匿名调用：

```yaml
# 服务端容器
env:
  - name: COSID_SERVER_AUTH_ALLOW_ANONYMOUS
    value: "true"
```

客户端不配置 `token`：

```yaml
cosid:
  stores:
    remote:
      type: remote
      options:
        baseUrl: http://cosid-server.cosid.svc:8688
```

匿名的服务端像对待不受限 token 一样应答每个调用：**所有调用方都能访问所有命名空间与 key，以及所有路径，包括 Actuator**：
`cosid` 端点可以为任意生成器发号，`prometheus` 与 Swagger UI 都公开，即使带了 token 也会被忽略。只有网络本身就是边界时才这样部署：

- 只通过 `ClusterIP` Service 暴露，不要用 Ingress、`LoadBalancer` 或 `NodePort` 对外暴露；
- 用 `NetworkPolicy` 限定只有使用它的业务 namespace 可以访问；
- 一旦有这些 namespace 之外的调用方需要访问，就改为配置 token（每个调用方一个，或按访问范围配置 client token）。

## 限制

| 属性 | 默认值 | |
|---|---|---|
| `limits.min-ttl` / `max-ttl` | `100ms` / `1h` | 租约 ttl 的范围 |
| `limits.max-tolerance` | `1m` | |
| `limits.max-slot-limit` | `65536` | 存储为每个命名空间准备 `limit` 个槽位 |
| `limits.max-reserve` | `1e12` | 最大区间大小 |
| `limits.max-count` | `1000` | 发号端点的最大 `count` |
| `limits.max-keys-per-namespace` | `10000` | 每个服务端实例上，一个 client 在一个命名空间中使用的区间 key 数（`429 too_many_keys`；`0` = 不限） |
| `limits.max-namespaces-per-client` | `1000` | 每个服务端实例上，一个 client 使用区间 key 的命名空间数；不含 `/` 的 key 自成一个命名空间（`429 too_many_namespaces`；`0` = 不限） |
| `limits.key-idle-timeout` | `1h` | 超过这么久未使用的 key 不再计数（`0` = 一直计数） |
| `blocking.threads` / `queue-capacity` | `64` / `1024` | 存储调用的有界线程池；满时返回 `503` + `Retry-After: 1` |

**请求默认运行在虚拟线程上**（镜像的 `application.yaml` 中 `spring.threads.virtual.enabled: true`），不再受 Tomcat 线程数限制。
存储与发号调用仍然运行在上面的有界线程池中，由它限制对存储的压力。设置 `SPRING_THREADS_VIRTUAL_ENABLED=false` 即可回到
Tomcat 的平台线程。

每个 client 各自计数，一个 client 用满不会挡住共享该命名空间的其他 client；预留失败的 key 不计数。
指标 `cosid.server.call.rejections{reason=busy|too_many_keys|too_many_namespaces}` 统计被拒绝的调用。Actuator 暴露
`health`（公开，带 `liveness` 与 `readiness` 探针）、`info`、`prometheus` 与 `cosid`。

## 由服务端发号

```yaml
cosid:
  namespace: shop
  server:
    ids:
      enabled: true
  generators:
    ticket:
      strategy: segment
      store: main
```

```bash
curl -H "Authorization: Bearer $TOKEN" 'http://cosid-server:8688/ids/ticket?count=10'
```

此时服务端就是一个普通的 `CosIdRuntime`：时间型生成器同样需要 `cosid.machine`，每个服务端实例租用自己的槽位。

全部 `cosid.server.*` 属性见[配置参考](../reference/config/server.md)。
