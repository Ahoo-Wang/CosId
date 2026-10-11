# CosId Proxy 模块

<center>

![CosId Proxy](../../public/assets/design/CosId-Proxy.png)
</center>

CosId Proxy 支持远程 ID 分发，允许客户端通过代理服务器分发机器 ID 和号段 ID，而不是直接连接底层存储。

## 概述

CosId Proxy 提供客户端-服务器架构：

- **代理服务器**：使用底层存储（Redis、ZooKeeper、JDBC 等）管理机器 ID 和号段 ID 的实际分发
- **代理客户端**：使用 `ProxyMachineIdDistributor` 和 `ProxyIdSegmentDistributor` 通过 HTTP/REST API 与代理服务器通信

此架构适用于：
- 直接访问存储基础设施受限的场景
- 需要集中管理 ID 的场景
- 需要增强安全性和审计的场景

## 核心组件

### ProxyMachineIdDistributor

`ProxyMachineIdDistributor` 是 `MachineIdDistributor` 的远程实现，通过与代理服务器通信来实现：
- 分发机器 ID
- 回滚机器 ID
- 守护机器 ID（心跳）

### ProxyIdSegmentDistributor

`ProxyIdSegmentDistributor` 是 `IdSegmentDistributor` 的远程实现，通过与代理服务器通信来实现：
- 分配号段 ID
- 管理号段链

## 架构

```
┌─────────────────┐         HTTP/REST          ┌─────────────────┐
│  CosId 客户端   │ ◄──────────────────────► │  代理服务器      │
│                 │                           │                 │
│ ProxyMachineId  │                           │ Redis/JDBC/ZK   │
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
| `cosid.proxy.server.max-clock-skew` | `1m` | 客户端（守护、归还时）上报的 `lastTimeStamp` 最多比服务端时钟超前这么多；过于超前的时间戳会让机器号永远无法被回收。`0` 表示不检查 |
| `cosid.proxy.server.segment.max-step` | `0`（不限制） | 客户端可申请的最大号段 `step`。号段链一次申请 `step × 号段数`，负载高时预取得更多，所以至少要设为所有客户端中最大的 `step × 最大预取距离`。`offset` 必须 `>= 0`，且 `offset + step` 不能溢出 |

超出这些限制的请求返回 `400`。

## 高可用：服务端重启自愈

代理服务器的号段分发器缓存仅存在于内存中。由于客户端只在启动时调用一次 `createDistributor`，服务端重启后缓存未命中时，`nextMaxId` 会基于底层存储自动重建分发器（懒重建使用默认 `offset=0`，仅在后端首次初始化时生效），已分配的号段状态不会丢失。即：**代理服务器重启后，已运行的客户端无需重启即可自动恢复取号**（自 v3.2.1 起）。

## 鉴权

默认情况下，代理服务端对所有能访问到它的人开放，启动时会打印告警。
可以在服务端配置一个或多个 Bearer token，并在每个客户端配置相同的 token：

```yaml
# 代理服务端
cosid:
  proxy:
    server:
      auth:
        tokens:
          - ${COSID_PROXY_TOKEN}   # 可以同时配置多个，用于轮换
        public-paths:              # 无需 token 的路径，默认：/actuator/health、/actuator/health/**
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
