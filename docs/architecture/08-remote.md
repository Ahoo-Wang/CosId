# 08 远程服务

## 1. 边界

4.x proxy 同时承担三种角色:远程存储(v2)、远程分配器(v1)、发号服务。5.0 重新划定边界(D8):

| 角色 | 5.0 | 模块 |
|---|---|---|
| 远程租约/区间服务(主) | 把 `RangeStore` 与 `SlotLeaseStore` 暴露为 HTTP;选槽策略在服务端执行,时间由底层存储打戳;一次往返 | `server` + `store/remote` |
| 发号服务(可选) | 服务端本身是一个普通的 `CosIdRuntime`,为非 JVM 语言或无法直连存储的环境发号 | `server`,`cosid.server.ids.enabled` |
| 4.x v1/v2 API | 删除 | — |

服务端的底层存储由 `cosid.stores` 配置(jdbc、redis、mongo、zookeeper 任一)。

## 2. HTTP API(前缀 `/v5`)

key 与命名空间放在请求体中,不放在路径里,避免 `/` 需要编码的问题。

| 方法 | 路径 | 请求 | 响应 |
|---|---|---|---|
| POST | `/v5/ranges:reserve` | `{key, size, initialOffset}` | `{start, end}`;封存为 `410` |
| POST | `/v5/ranges:seal` | `{key}` | `204` |
| POST | `/v5/slots:acquire` | `{namespace, ownerKey, incarnation, limit, ttlMs, toleranceMs, preferredSlot?}` | `SlotLease`,或 `409 {earliestExpiresAt}` |
| POST | `/v5/slots:renew` | `{namespace, slot, incarnation, fence, ttlMs, toleranceMs}` | `SlotLease` 或 `409` |
| POST | `/v5/slots:release` | `{namespace, slot, incarnation, fence, lastIssued}` | `204` 或 `409` |
| GET | `/v5/ids/{generator}?count=n` | — | `{ids: [...]}`(可选) |
| GET | `/v5/ids/{generator}/strings?count=n` | — | `{ids: ["..."]}`(可选,使用生成器的 codec) |
| GET | `/v5/ids/{generator}/periodic?count=n` | — | `{period, values: [...]}`(可选) |

- 未指定 `{generator}` 的路径 `/v5/ids`、`/v5/ids/strings` 使用名为 `default` 的生成器(4.x 的 share)。
- 服务端限制:`ttlMs ∈ [min, max]`、`size ≤ maxReserve`(默认 1e12)、`count ≤ maxCount`、单命名空间 key 数量上限(超出为 `429`);key 与命名空间做长度与字符集校验。
- 阻塞操作在有界池上执行(默认 64 线程,队列 1024);满时返回 `503` 与 `Retry-After: 1`。
- 参数错误返回 `400`,统一错误体 `{code, message}`。
- R4-A 补充:renew / release 请求体带 `ownerKey`(ZooKeeper 的 release 需要比对);acquire 的 200 响应是扁平的租约加 `reason` 与 `duplicateOwnerKey`;
  状态码 `422 range_exhausted`、`409 namespace_mismatch` / `slots_exhausted`(以 `code` 区分)、`501 seal_unsupported`(客户端返回 false)、`409 generator_disabled`;
  `periodic` 端点只返回同一期的值,跨期时截断;新增 `limits.max-slot-limit`(默认 65536)与 `limits.max-tolerance`(默认 1m);限流指标 `cosid.server.call.rejections{reason}`。

## 3. 安全

- Bearer token 鉴权**默认开启**:未配置 token 时服务拒绝启动,除非显式设置 `cosid.server.auth.allow-anonymous=true`。
- 支持全局 token,也支持按客户端配置 token 及其可访问的命名空间与 key 前缀(越权返回 `403`,无 token 返回 `401`)。
- 公开路径默认只有 `/actuator/health/**`,可配置。
- 响应带 `Cache-Control: no-store`。

## 4. 部署与运维

- 服务端无状态,可水平扩展;所有一致性由底层存储保证。
- 发布 Docker 镜像;提供 OpenAPI 文档,以及 health、prometheus、cosid 端点。
- 客户端(`store/remote`)配置:`baseUrl`、`token`、连接超时、请求超时。
