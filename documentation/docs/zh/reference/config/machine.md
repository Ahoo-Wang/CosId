# Machine 配置

> `me.ahoo.cosid.spring.boot.starter.machine.MachineProperties`

| 名称              | 数据类型             | 说明                   | 默认值       |
|-----------------|------------------|----------------------|-----------|
| stable          | `boolean`        | 是否为稳定的实例,稳定实例将不回收机器号 | `false`   |
| port            | `Integer`        | 端口号                  | 进程ID（PID） |
| instanceId      | `String`         | 应用实例编号(全局唯一)         | 应用IP:PID  |
| machineBit      | `int`            | 机器位数                 | `10`      |
| stateStorage    | `StateStorage`   | 机器状态存储               |           |
| distributor     | `Distributor`    | 机器号分发器               |           |
| guarder         | `Guarder`        | 机器号(心跳)守护            |           |
| clock-backwards | `ClockBackwards` | 时钟回拨配置               |           |

::: tip 稳定实例
即使稳定实例离线超过安全守护时长，其他实例也不会回收它的机器号。从旧版本升级时请注意：

- 只有所有实例都升级到新版本后才完全生效：滚动升级期间，旧版本实例不读取 stable 标记，仍可能回收稳定实例的机器号。
- 已经离线的稳定实例，在新版本上启动一次（写入标记）之前不受保护。
- JDBC：需要先加上 `stable` 列（见 [CosId-Jdbc](../../guide/extensions/cosid-jdbc.md)）并重启，因为该列只在启动时检测一次。
:::

### StateStorage

> 状态存储配置

| 名称      | 数据类型                 | 说明         | 默认值    |
|---------|----------------------|------------|--------|
| local   | `StateStorage.Local` | 本地机器状态存储配置 |        |

#### StateStorage.Local

| 名称             | 数据类型     | 说明     | 默认值                      |
|----------------|----------|--------|--------------------------|
| state-location | `String` | 状态存储位置 | `./cosid-machine-state/` |

### Distributor

> 机器号分配器配置

| 名称     | 数据类型                 | 说明                                                          | 默认值      |
|--------|----------------------|-------------------------------------------------------------|----------|
| type   | `Distributor.Type`   | 机器号分配器类型：`MANUAL`/`STATEFUL_SET`/`JDBC`/`REDIS`/`ZOOKEEPER`/`MONGO`/`PROXY` | `MANUAL` |
| provider | `String` | 使用该名称的 `CosIdStoreProvider` 替代 `type` 对应的内置实现，用于接入 CosId 未内置的存储（3.4.0 起） | |
| manual | `Distributor.Manual` | 手动分配器配置                                                     |          |

#### Distributor.Manual

| 名称        | 数据类型      | 说明  | 默认值    |
|-----------|-----------|-----|--------|
| machineId | `Integer` | 机器号 | `null` |

### Guarder

> 机器号（心跳）守护配置

| 名称                 | 数据类型       | 说明 | 默认值 |
|--------------------|------------|----|-----|
| enabled            | `boolean`  | 是否开启机器号守护 | `true` |
| initialDelay       | `Duration` | 首次守护前的延迟 | `PT1M` |
| delay              | `Duration` | 守护（远端心跳）间隔 | `PT1M` |
| safeGuardDuration  | `Duration` | 超过该时长未守护的机器号可能被其他实例回收 | `PT5M` |
| failFast           | `boolean`  | 机器号租约丢失或过期后停止发号 | `true` |
| localStateInterval | `Duration` | 本地机器状态（重启后用于时钟回拨保护的时间水位）的刷新间隔。崩溃重启时，短于该间隔的时钟回拨可能检测不到。仅在开启守护且使用本地文件状态存储（`stateStorage.local`）时生效。`0` 表示关闭 | `PT1S` |

### 后端调用超时

| 属性 | 默认值 | 说明 |
|----|-----|----|
| `cosid.machine.distributor.redis.timeout` | `1s` | Redis 机器号守护与归还 |
| `cosid.machine.distributor.jdbc.query-timeout` | `5s` | 每条 JDBC 语句 |
| `cosid.machine.distributor.zookeeper.timeout` | `5s` | ZooKeeper 机器号守护与归还（Curator 没有单次操作超时） |
| `cosid.machine.distributor.mongo.timeout` | `5s` | 每次 MongoDB 操作（驱动的 `timeoutMS`）；`0` 表示沿用 `MongoClient` 的设置 |
| `cosid.segment.distributor.mongo.timeout` | `5s` | 每次 MongoDB 号段操作 |
| `cosid.segment.distributor.zookeeper.timeout` | `5s` | 每次 ZooKeeper 号段 `nextMaxId` |
| `cosid.proxy.connect-timeout` | `2s` | 连接 CosId Proxy 服务端 |
| `cosid.proxy.read-timeout` | `5s` | 等待 CosId Proxy 服务端响应，也包括启动时的机器号分配 |

`0` 表示不设超时。两个 Proxy 超时都为 `0` 时，沿用应用自己的 HTTP 请求工厂。

## ClockBackwards

> `me.ahoo.cosid.spring.boot.starter.machine.MachineProperties.ClockBackwards`

| 名称               | 数据类型  | 说明                                           | 默认值    |
|------------------|-------|----------------------------------------------|--------|
| spin-threshold   | `int` | 自旋同步阈值（ms）                                   | `10`   |
| broken-threshold | `int` | 抛出异常（`ClockTooManyBackwardsException`）阈值（ms） | `2000` |

**YAML 配置样例**

```yaml
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true
    distributor:
      type: jdbc
    guarder:
      enabled: true
```
