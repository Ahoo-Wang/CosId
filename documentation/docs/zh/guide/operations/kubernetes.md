# Kubernetes

本页把各部分组合到集群里：ID 在哪里生成，谁持有槽位与号段，一个 Deployment 需要多少槽位，以及 pod 如何启动和停止才既不丢号也不重号。

## 推荐架构

```
┌──────── 命名空间 shop ──────────┐      ┌──────── 命名空间 cosid ─────────┐
│ order-service pods              │      │ cosid-server pods（2 个以上）   │
│   CosIdRuntime（进程内）        │      │   无状态，ClusterIP Service     │
│   segment / snowflake / cosid80 │─────▶│   /ranges:*  /slots:*           │──▶ MySQL / Redis /
│   store: remote                 │ HTTP │                                 │    MongoDB / ZooKeeper
│ payment-service pods  ...       │─────▶│                                 │
└─────────────────────────────────┘      └─────────────────────────────────┘
```

- **ID 在业务 pod 内生成**，由各自的 `CosIdRuntime` 发号。发一个号不需要网络调用：号段从已预留的区间取值，时间型生成器在已租到的槽位上发号。
- **cosid-server 是租约与号段的权威。** 业务 pod 通过 `remote` 存储访问它，它把每次调用转给自己的后端存储。它不保存状态，因此作为普通 Deployment 运行两个以上副本即可。只有它持有数据库凭据，业务 pod 不需要。
- **每个 Deployment 租用槽位**（`cosid.machine.leased`）：pod 启动时取得一个空闲槽位，停止时释放，pod 不需要固定身份。
- **跨服务全局唯一的 ID** 来自共享命名空间中的[命名 machine](../concepts/slot-lease.md#命名-machine)（`cosid.machines.<name>.namespace`）；其余部分仍各用各服务的命名空间。

业务 pod 也可以直接使用存储（`type: jdbc`、`redis` 等），不经过 cosid-server。以下情况适合用服务端：很多服务共用一个存储；不希望它们持有存储凭据；其他语言的客户端需要调用 `/ids`。

## cosid-server

示例用 MySQL 作为服务端的后端存储，并以**免 token** 方式运行。这只在受信任的网络内才安全（见[受信任的内网免 token 部署](../server.md#受信任的内网免-token-部署)）：只用 `ClusterIP` Service，绝不暴露到集群外，并用 `NetworkPolicy` 只放行业务命名空间。改用 token 时，去掉 `COSID_SERVER_AUTH_ALLOW_ANONYMOUS`，从 Secret 设置 `COSID_SERVER_AUTH_TOKENS_0`（至少 16 个字符）。

```yaml
apiVersion: v1
kind: Secret
metadata: { name: cosid-server-db, namespace: cosid }
stringData:
  url: jdbc:mysql://mysql.db.svc:3306/cosid_db
  username: cosid
  password: change-me
---
apiVersion: apps/v1
kind: Deployment
metadata: { name: cosid-server, namespace: cosid }
spec:
  replicas: 2
  selector: { matchLabels: { app: cosid-server } }
  template:
    metadata: { labels: { app: cosid-server } }
    spec:
      terminationGracePeriodSeconds: 30
      containers:
        - name: cosid-server
          image: ahoowang/cosid-server:__COSID_VERSION__
          ports: [ { name: http, containerPort: 8688 } ]
          env:
            - { name: COSID_STORES_MAIN_TYPE, value: jdbc }
            - { name: COSID_STORES_MAIN_TIMEOUT, value: 2s }
            - { name: COSID_STORES_MAIN_OPTIONS_URL, valueFrom: { secretKeyRef: { name: cosid-server-db, key: url } } }
            - { name: COSID_STORES_MAIN_OPTIONS_USERNAME, valueFrom: { secretKeyRef: { name: cosid-server-db, key: username } } }
            - { name: COSID_STORES_MAIN_OPTIONS_PASSWORD, valueFrom: { secretKeyRef: { name: cosid-server-db, key: password } } }
            - { name: COSID_SERVER_AUTH_ALLOW_ANONYMOUS, value: "true" }
            - { name: SERVER_SHUTDOWN, value: graceful }
          # liveness 不访问存储；readiness 会在存储上预留一次，存储不可用的服务端不再接收调用。
          livenessProbe:
            httpGet: { path: /actuator/health/liveness, port: http }
            periodSeconds: 10
          readinessProbe:
            httpGet: { path: /actuator/health/readiness, port: http }
            periodSeconds: 5
            failureThreshold: 2
          lifecycle:
            preStop: { sleep: { seconds: 5 } }   # 先让 Service 摘掉该 pod，再停止接收调用
          resources:
            requests: { cpu: 250m, memory: 512Mi }
            limits: { memory: 512Mi }
---
apiVersion: v1
kind: Service
metadata: { name: cosid-server, namespace: cosid }
spec:
  type: ClusterIP
  selector: { app: cosid-server }
  ports: [ { name: http, port: 8688, targetPort: http } ]
---
# 只有业务命名空间（带标签 cosid-client=true）能访问服务端。
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata: { name: cosid-server, namespace: cosid }
spec:
  podSelector: { matchLabels: { app: cosid-server } }
  policyTypes: [ Ingress ]
  ingress:
    - from: [ { namespaceSelector: { matchLabels: { cosid-client: "true" } } } ]
      ports: [ { port: 8688 } ]
```

请事先用[表结构文件](../backends/jdbc.md#表结构)建表（`initSchema` 只用于开发环境）。`sleep` 形式的 preStop 需要 Kubernetes 1.30 及以上；更早的集群用 `exec: { command: [ sleep, "5" ] }`，镜像里需要有 `sleep`。

## 业务应用

```yaml
# order-service 的 application.yaml
spring:
  application:
    name: order-service
  lifecycle:
    timeout-per-shutdown-phase: 20s
server:
  shutdown: graceful
cosid:
  namespace: ${spring.application.name}        # 本服务自己的槽位与 range key
  stores:
    remote:
      type: remote
      timeout: 2s
      options:
        baseUrl: http://cosid-server.cosid.svc:8688
  machine:
    leased:
      store: remote
      bits: 6                                  # 64 个槽位，见下文"需要多少槽位"
  machines:
    global:                                    # 所有签发 cosid80 token 的服务共用
      namespace: global-ids
      leased: { store: remote, bits: 10 }
  generators:
    order:   { strategy: segment, store: remote }
    event:   { strategy: snowflake }           # 默认 machine，命名空间 order-service
    token:   { strategy: cosid80, machine: global }
management:
  endpoint:
    health:
      probes:
        enabled: true
      group:
        readiness:
          include: readinessState, cosid       # 丢失槽位的 pod 不再接收流量
```

`/actuator/health/cosid` 在某个 machine 丢失租约且未能重新获取时为 `DOWN`，此时其上的时间型生成器以 `SlotLostException` 快速失败。把它放进 readiness 组，这样的 pod 会被移出 Service，直到重新持有槽位。不要放进 liveness：重启并不能让存储恢复。

### 跨服务全局唯一

每个签发 `token` 的服务都声明同样的 `cosid.machines.global`：相同的 `namespace`、存储、`bits`、`tolerance` 与 `slot-offset`。第一个使用 `global-ids` 的运行时会记录这些参数，参数不同的运行时启动失败。号段和默认 machine 留在各服务自己的命名空间里，服务之间不共享这部分 range key 与槽位。发号很少或不在 JVM 上的客户端，也可以改为调用 cosid-server 的 `/ids`（`cosid.server.ids.enabled`）。

## 需要多少槽位

租到的槽位在 pod 存活期间一直被占用；pod 被强制杀死、没有释放时，要等租约到期（`ttl + tolerance`，默认 31s）才空出来。`bits` 要按**峰值**选择：

```
所需槽位 ≥ 最大副本数（HPA 的 maxReplicas，或 replicas）
         + 滚动更新的 maxSurge
         + 最近 ttl + tolerance 内被杀死的 pod（崩溃重启、驱逐）
```

再取留有余量的下一个 2 的幂。最多 20 个副本、`maxSurge: 25%` 的 Deployment 在滚动更新时需要 25 个槽位，`bits: 6`（64）给崩溃留出了余量。`global-ids` 这样的共享命名空间，要把其中**所有**服务的峰值加起来。`bits` 不能超过 layout 的 node 位数（`SNOWFLAKE_MS` 为 10，cosid80 为 20）。槽位全被占用时，启动中的 pod 最多等待 `acquire-timeout`（即 `ttl`），之后启动失败：startup 探针至少要给这么长时间。

## Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata: { name: order-service, namespace: shop }
spec:
  replicas: 4
  strategy:
    type: RollingUpdate
    rollingUpdate: { maxSurge: 25%, maxUnavailable: 0 }
  selector: { matchLabels: { app: order-service } }
  template:
    metadata: { labels: { app: order-service } }
    spec:
      # preStop（5s）+ 优雅停机阶段（20s）+ 释放槽位（一次存储调用，最多 2s timeout），再留余量。
      terminationGracePeriodSeconds: 40
      containers:
        - name: order-service
          image: registry.example.com/shop/order-service:1.0.0
          ports: [ { name: http, containerPort: 8080 } ]
          startupProbe:
            httpGet: { path: /actuator/health/liveness, port: http }
            periodSeconds: 5
            failureThreshold: 12                # 60s：长于所有槽位被占用时的 acquire-timeout（30s）
          livenessProbe:
            httpGet: { path: /actuator/health/liveness, port: http }
          readinessProbe:
            httpGet: { path: /actuator/health/readiness, port: http }
            periodSeconds: 5
          lifecycle:
            preStop: { sleep: { seconds: 5 } }
```

命名空间 `shop` 带有标签 `cosid-client: "true"`，上面的 `NetworkPolicy` 才会放行它。

## 停止 pod

收到 `SIGTERM` 后，pod 依次经过以下步骤，全部要在 `terminationGracePeriodSeconds` 之内完成：

1. `preStop` 睡眠期间，端点从 Service 中移除，正在关闭的 pod 不再收到新请求。
2. Spring 优雅停机让处理中的请求完成（`spring.lifecycle.timeout-per-shutdown-phase`）。
3. 上下文关闭 `CosIdRuntime`：生成器停止，然后每个 machine **以精确的最后发号时间戳释放槽位**，最后关闭存储。

释放的槽位立即可用，下一个持有者紧接着最后的时间戳开始发号。如果先用完了宽限期，Kubernetes 会发送 `SIGKILL`：ID 仍然唯一，但槽位要到租约到期（最多 `ttl + tolerance`）才空出来，这就是槽位计算里"被杀死的 pod"一项的来源。宽限期要大于上述各步之和，如示例所示。

## StatefulSet

StatefulSet 的 pod 有稳定的序号。可以把它作为租用 machine 的 `preferred-slot`（`preferred-slot: ${POD_ORDINAL:}`，`POD_ORDINAL` 通过 Downward API 取自标签 `apps.kubernetes.io/pod-index`）：租约仍然保护这个槽位，发现槽位被占用的 pod 会拿到另一个。[静态槽位](../concepts/slot-lease.md#静态槽位)（`cosid.machine.static`）完全不经过存储，但这时只能靠 StatefulSet 保证槽位唯一，且 `state-file` 必须放在持久卷上。

## 检查清单

- [ ] cosid-server：两个以上副本，只用 `ClusterIP`；配置 token，或 `allow-anonymous` 加 `NetworkPolicy`。
- [ ] readiness 探针：服务端用 `/actuator/health/readiness`（会访问存储）；业务应用的 readiness 组包含 `cosid`。
- [ ] `bits` 覆盖峰值副本 + surge + 最近被杀死的 pod；共享命名空间的每个服务 `bits` 相同。
- [ ] `terminationGracePeriodSeconds` > `preStop` + 停机阶段 + 存储 timeout。
- [ ] startup 探针长于 `acquire-timeout`。
- [ ] 其余见[生产环境清单](production.md)。
