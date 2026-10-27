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
  -e COSID_SERVER_AUTH_TOKENS_0=change-me \
  ahoowang/cosid-server:__COSID_VERSION__
```

每项设置都可以来自环境变量（`COSID_SERVER_AUTH_TOKENS_0`）或挂载的 `application.yaml`。镜像以非 root 用户在 8688 端口运行；
`/actuator/health/liveness` 不访问存储即可应答，用作容器健康检查。带 `options.url` 的 JDBC 后备存储会得到一个大小为
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
| 其他 | 带 `status()`、`code()` 的 `RemoteStoreException`；`503` 以及没有应答的调用为 `transientFailure()` |

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

其他状态：`400 bad_request`（缺少字段、字符集或长度不合法、值超过上限）、`401`（没有 token）、`403`（token 超出其命名空间）、
`429 too_many_keys`、带 `Retry-After: 1` 的 `503 busy`，以及 `500 store_error`。每个响应都带有 `Cache-Control: no-store`。
OpenAPI 文档位于 `/v3/api-docs`，Swagger UI 位于 `/swagger-ui.html`；除非加入 `public-paths`，两者都需要 token。

## 认证

**Bearer token 默认开启。** 没有配置任何 token 或 client 时，除非设置 `cosid.server.auth.allow-anonymous=true`，否则服务端拒绝启动。

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
与 client 的命名空间做比对。

## 限制

| 属性 | 默认值 | |
|---|---|---|
| `limits.min-ttl` / `max-ttl` | `100ms` / `1h` | 租约 ttl 的范围 |
| `limits.max-tolerance` | `1m` | |
| `limits.max-slot-limit` | `65536` | 存储为每个命名空间准备 `limit` 个槽位 |
| `limits.max-reserve` | `1e12` | 最大区间大小 |
| `limits.max-count` | `1000` | 发号端点的最大 `count` |
| `limits.max-keys-per-namespace` | `10000` | 每个服务端实例上每个命名空间的不同区间 key 数（超过返回 `429`；`0` = 不限） |
| `blocking.threads` / `queue-capacity` | `64` / `1024` | 存储调用的有界线程池；满时返回 `503` + `Retry-After: 1` |

指标 `cosid.server.call.rejections{reason=busy|too_many_keys}` 统计被拒绝的调用。Actuator 暴露
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
