# CosId Proxy 模块

<center>

![CosId Proxy](../../public/assets/design/CosId-Proxy.png)
</center>

CosId Proxy 支持远程 ID 分发，允许客户端通过代理服务器分发机器 ID 和号段 ID，而不是直接连接底层存储。

## 概述

CosId Proxy 提供客户端-服务器架构：

- **代理服务器**：使用底层存储（Redis、ZooKeeper、JDBC 等）管理机器 ID 和号段 ID 的实际分发
- **代理客户端**：使用 `ProxyMachineLeaseStore` 和 `ProxyIdSegmentDistributor` 通过 HTTP/REST API 与代理服务器通信

此架构适用于：
- 直接访问存储基础设施受限的场景
- 需要集中管理 ID 的场景
- 需要增强安全性和审计的场景

## 核心组件

### ProxyMachineLeaseStore

`ProxyMachineLeaseStore` 是 `MachineLeaseStore` 的远程实现。客户端与其它后端一样用 `LeaseStoreMachineIdDistributor` 包装它，并通过服务端的租约 API（`/machines/v2`）来：
- 申领机器 ID（新的，或已释放、已过期的）
- 续约（心跳）
- 释放租约

服务端保留 v1 机器 API（`/machines/{namespace}`）供 3.x 客户端使用，该 API 已弃用；3.x 服务端没有 v2 API，因此需先升级服务端再升级客户端。

### ProxyIdSegmentDistributor

`ProxyIdSegmentDistributor` 是 `IdSegmentDistributor` 的远程实现，通过与代理服务器通信来实现：
- 分配号段 ID
- 管理号段链

## 架构

```
┌─────────────────┐         HTTP/REST          ┌─────────────────┐
│  CosId 客户端   │ ◄──────────────────────► │  代理服务器      │
│                 │                           │                 │
│ ProxyLeaseStore │                           │ Redis/JDBC/ZK   │
│ ProxyIdSegment  │                           │                 │
└─────────────────┘                           └─────────────────┘
```

## 使用场景

- **安全受限环境**：当客户端应用无法直接访问存储基础设施时
- **集中管理**：当需要集中控制 ID 生成时
- **多租户系统**：当希望通过代理隔离每个租户的 ID 生成时

## 服务端限制

代理服务端会校验客户端传来的参数：

| 属性 | 默认值 | 说明 |
|----|-----|----|
| `cosid.proxy.server.min-safe-guard-duration` | `5m` | 只有租约最后一次续约（以服务端存储时钟为准）距今至少这么久，客户端才能接管该机器 ID。上报更晚截止时间的客户端会收到 `400`；3.x 客户端上报的更短守护时长会被提升到该值并记录警告。不要大于客户端的 `cosid.machine.guarder.safe-guard-duration` |
| `cosid.proxy.server.max-clock-skew` | `1m` | 客户端（守护、归还时）上报的 `lastTimeStamp` 最多比服务端时钟超前这么多；过于超前的时间戳会让机器号永远无法被回收。`0` 表示不检查 |
| `cosid.proxy.server.segment.max-step` | `1000000000000` | 客户端可申请的最大号段 `step`；`0` 表示不限制。号段链一次申请 `step × 号段数`，负载高时预取得更多，所以至少要设为所有客户端中最大的 `step × 最大预取距离`。`offset` 必须在 `0` 到 `Long.MAX_VALUE / 2` 之间 |

超出这些限制的请求返回 `400`。

## 高可用：服务端重启自愈

代理服务器的号段分发器缓存仅存在于内存中。由于客户端只在启动时调用一次 `createDistributor`，服务端重启后缓存未命中时，`nextMaxId` 会基于底层存储自动重建分发器（懒重建使用默认 `offset=0`，仅在后端首次初始化时生效），已分配的号段状态不会丢失。即：**代理服务器重启后，已运行的客户端无需重启即可自动恢复取号**（自 v3.2.1 起）。

## 鉴权

代理服务端至少需要一个 Bearer token：没有配置时启动失败（4.0 起）。在服务端配置一个或多个 token，并在每个客户端配置相同的 token：

```yaml
# 代理服务端
cosid:
  proxy:
    server:
      auth:
        tokens:
          - ${COSID_PROXY_TOKEN}   # 可以同时配置多个，用于轮换
        public-paths:              # 无需 token 的路径，默认：management.endpoints.web.base-path 下的健康检查端点
          - /actuator/health
          - /actuator/health/**
```

```yaml
# 客户端应用
cosid:
  proxy:
    host: http://cosid-proxy:8688
    token: ${COSID_PROXY_TOKEN}
```

配置 token 后，除公开路径外，所有没有携带有效 `Authorization: Bearer <token>` 的请求都返回 `401`，包括 Actuator 端点（其中有的能发号或删除生成器）和 API 文档。公开路径是按解码后的路径匹配的路径模式。需要公开 API 文档时，加上 `/v3/api-docs/**` 和 `/swagger-ui/**`。

## 部署安全

- 代理服务端只应部署在内网，不要暴露到公网。任何能调用它的人都能释放一个仍被存活实例持有的机器号，导致两个实例生成相同的 ID。
- token 是 Bearer 凭证：持有者可以调用所有端点、操作所有命名空间。请通过 HTTPS 或带双向 TLS 的服务网格传输，存放在密钥管理系统中；轮换时同时配置新旧两个 token。
- 保持 `cosid.proxy.server.segment.max-step` 为有限值，避免单个请求耗尽或溢出号段。
- `cosid.proxy.server.auth.allow-anonymous=true` 可在不配置 token 的情况下运行，只能用于可信网络边界之内。

### JMX

服务端默认不开启 JMX。需要通过 JMX 监控时，在 `JAVA_OPTS` 中传入参数，并开启认证和 TLS：

```shell
JAVA_OPTS="-Dcom.sun.management.jmxremote.port=5555 \
  -Dcom.sun.management.jmxremote.authenticate=true \
  -Dcom.sun.management.jmxremote.password.file=/opt/cosid-proxy-server/config/jmxremote.password \
  -Dcom.sun.management.jmxremote.access.file=/opt/cosid-proxy-server/config/jmxremote.access \
  -Dcom.sun.management.jmxremote.ssl=true \
  -Djavax.net.ssl.keyStore=/opt/cosid-proxy-server/config/jmx.p12 \
  -Djavax.net.ssl.keyStorePassword=${JMX_KEYSTORE_PASSWORD}"
```

未认证的 JMX 可以完全控制 JVM；堆转储还会暴露 token 和数据库密码。
