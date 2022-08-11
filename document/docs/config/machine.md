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

### StateStorage

| 名称      | 数据类型                 | 说明         | 默认值    |
|---------|----------------------|------------|--------|
| enabled | `boolean`            | 是否启用状态存储   | `true` |
| local   | `StateStorage.Local` | 本地机器状态存储配置 |        |

#### StateStorage.Local

| 名称             | 数据类型     | 说明     | 默认值                      |
|----------------|----------|--------|--------------------------|
| state-location | `String` | 状态存储位置 | `./cosid-machine-state/` |

### Distributor

| 名称     | 数据类型                 | 说明                                                          | 默认值      |
|--------|----------------------|-------------------------------------------------------------|----------|
| type   | `Distributor.Type`   | 机器号分配器类型：`MANUAL`/`STATEFUL_SET`/`JDBC`/`REDIS`/`ZOOKEEPER` | `MANUAL` |
| manual | `Distributor.Manual` | 手动分配器配置                                                     |          |

#### Distributor.Manual

| 名称        | 数据类型      | 说明  | 默认值    |
|-----------|-----------|-----|--------|
| machineId | `Integer` | 机器号 | `null` |

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
