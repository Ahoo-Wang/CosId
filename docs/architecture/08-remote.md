# 08 远程服务

## 1. 边界

4.x proxy 同时承担三种角色:远程存储(v2)、远程分配器(v1)、发号服务。5.0 重新划定边界(D8):

| 角色 | 5.0 | 模块 |
|---|---|---|
| 远程租约/区间服务(主) | 把 `RangeStore` 与 `SlotLeaseStore` 暴露为 HTTP;选槽策略在服务端执行,时间由底层存储打戳;一次往返 | `server` + `store/remote` |
| 发号服务(可选) | 服务端本身是一个普通的 `CosIdRuntime`,为非 JVM 语言或无法直连存储的环境发号 | `server`,`cosid.server.ids.enabled` |
| 4.x v1/v2 API | 删除 | — |

服务端的底层存储由 `cosid.stores` 配置(jdbc、redis、mongo、zookeeper 任一)。

## 2. HTTP API(不带版本前缀,D30)

key 与命名空间放在请求体中,不放在路径里,避免 `/` 需要编码的问题。

| 方法 | 路径 | 请求 | 响应 |
|---|---|---|---|
| POST | `/ranges:reserve` | `{key, size, initialOffset}` | `{start, end}`;封存为 `410` |
| POST | `/ranges:seal` | `{key}` | `204` |
| POST | `/slots:acquire` | `{namespace, ownerKey, incarnation, limit, ttlMs, toleranceMs, slotOffset?, preferredSlot?}`(`slotOffset` 默认 0,记入命名空间元数据,api-freeze §2.5.1) | `SlotLease`,或 `409 {earliestExpiresAt}` |
| POST | `/slots:renew` | `{namespace, slot, ownerKey, incarnation, fence, ttlMs, toleranceMs}` | `SlotLease` 或 `409 lease_lost` |
| POST | `/slots:release` | `{namespace, slot, ownerKey, incarnation, fence, lastIssued}` | `204` 或 `409 lease_lost` |
| GET | `/ids/{generator}?count=n` | — | `{ids: [...]}`(可选) |
| GET | `/ids/{generator}/strings?count=n` | — | `{ids: ["..."]}`(可选,使用生成器的 codec) |
| GET | `/ids/{generator}/periodic?count=n` | — | `{period, values: [...]}`(可选) |

- 未指定 `{generator}` 的路径 `/ids`、`/ids/strings` 使用名为 `default` 的生成器(4.x 的 share)。
- 服务端限制(`cosid.server.limits.*`):`ttlMs ∈ [min-ttl, max-ttl]`(默认 100ms、1h)、`size ≤ max-reserve`(默认 1e12)、`count ≤ max-count`(默认 1000);key 与命名空间做长度与字符集校验。
- key 数量上限按 client、按服务实例在内存中计数(4.x `max-distributors`):`max-keys-per-namespace`(默认 10000)与 `max-namespaces-per-client`(默认 1000,不含 `/` 的 key 自成一个命名空间),
  新 key 超出任一上限返回 `429 too_many_keys` / `429 too_many_namespaces`;`key-idle-timeout`(默认 1h,0 表示永不过期)内未使用的 key 不再计数;reserve 失败的 key 不计数。
- 阻塞操作在有界池上执行(默认 64 线程,队列 1024);满时返回 `503` 与 `Retry-After: 1`。
- 参数错误返回 `400`,统一错误体 `{code, message}`。底层存储超时返回 `504 store_timeout`(客户端以 `StoreTimeoutException` 为 cause 报告,可重试);其他存储失败为 `500 store_error`,细节只写日志。
- R4-A 补充:renew / release 请求体带 `ownerKey`(ZooKeeper 的 release 需要比对),并附带租约窗口 `expiresAt`、`floor`、`ceiling`、`storeNow`(可选,供需要的后端使用);acquire 的 200 响应是扁平的租约加 `reason` 与 `duplicateOwnerKey`;
  状态码 `422 range_exhausted`、`409 namespace_mismatch`(limit、tolerance 或 slotOffset 与首次使用不同)/ `slots_exhausted`(以 `code` 区分)、`501 seal_unsupported`(客户端返回 false)、`409 generator_disabled`;
  命名 machine(api-freeze §2)经 remote 存储时用自己的命名空间,协议不变;受限 client token 的 `namespaces` 须同时包含服务命名空间与全局命名空间(如 `[order-service, global-ids]`),否则 `403`;
  `periodic` 端点只返回同一期的值,跨期时截断;新增 `limits.max-slot-limit`(默认 65536)与 `limits.max-tolerance`(默认 1m);限流指标 `cosid.server.call.rejections{reason}`。

## 3. 安全

- Bearer token 鉴权**默认开启**:未配置 token 时服务拒绝启动,除非显式设置 `cosid.server.auth.allow-anonymous=true`。
- 支持全局 token(`auth.tokens`),也支持按客户端配置 token 及其可访问的命名空间与 key 前缀(`auth.clients`,越权返回 `403`,无 token 返回 `401`)。
- token 至少 16 个字符(去掉首尾空白后);含 `${`(未解析的占位符)、为空或重复时服务拒绝启动。
- client token 只能访问 client 路径(5 个存储原语与 `/ids/**`,随 `spring.mvc.servlet.path` 前移);其他路径(Actuator,其 `cosid` 端点能发号;springdoc)只有 `auth.tokens` 能访问,client token 得到 `403`。
  5.0.0-alpha.1 的 `auth.admin-paths` 已删除。Actuator 配到单独的 `management.server.port` 时不经过鉴权过滤器,因此除匿名模式外服务拒绝启动。
- 公开路径(`auth.public-paths`)默认只有 `/actuator/health`、`/actuator/health/**`,可配置;`cosid.server.docs.public=true` 再加上 springdoc 的只读路径。
- 响应带 `Cache-Control: no-store`。

## 4. 部署与运维

- 服务端无状态,可水平扩展;所有一致性由底层存储保证。
- 发布 Docker 镜像;提供 OpenAPI 文档,以及 health、prometheus、cosid 端点。
- 客户端(`store/remote`,provider 类型 `remote`)配置:`baseUrl`、`token`、`connectTimeout`(默认 5s)、`requestTimeout`(默认取存储的 `timeout`)。
