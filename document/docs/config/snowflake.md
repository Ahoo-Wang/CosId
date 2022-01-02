# SnowflakeId 配置

> `me.ahoo.cosid.spring.boot.starter.snowflake.SnowflakeIdProperties`

| 名称              | 数据类型                        | 说明        | 默认值                                             |
|-----------------|-----------------------------|-----------|-------------------------------------------------|
| enabled         | `boolean`                   | 是否启用      | `false`                                         |
| zone-id         | `String`                    | 时区        | `ZoneId.systemDefault().getId()`                |
| epoch           | `long`                      | EPOCH     | `CosId.COSID_EPOCH` <br> (UTC 2019-12-24 16:00) |
| clock-backwards | `ClockBackwards`            | 时钟回拨配置    |                                                 |
| machine         | `Machine`                   | 机器号分配器配置  |                                                 |
| share           | `IdDefinition`              | 共享ID生成器配置 | 有                                               |
| provider        | `Map<String, IdDefinition>` | 多ID生成器配置  | `null`                                          |

## ClockBackwards

> `me.ahoo.cosid.spring.boot.starter.snowflake.SnowflakeIdProperties.ClockBackwards`

| 名称               | 数据类型  | 说明                                           | 默认值    |
|------------------|-------|----------------------------------------------|--------|
| spin-threshold   | `int` | 自旋同步阈值（ms）                                   | `10`   |
| broken-threshold | `int` | 抛出异常（`ClockTooManyBackwardsException`）阈值（ms） | `2000` |

## Machine

> `me.ahoo.cosid.spring.boot.starter.snowflake.SnowflakeIdProperties.Machine`

| 名称           | 数据类型                   | 说明                   | 默认值       |
|--------------|------------------------|----------------------|-----------|
| stable       | `boolean`              | 是否为稳定的实例,稳定实例将不回收机器号 | `false`   |
| port         | `Integer`              | 端口号                  | 进程ID（PID） |
| instanceId   | `String`               | 应用实例编号(全局唯一)         | 应用IP:PID  |
| machineBit   | `int`                  | 机器位数                 | `10`      |
| stateStorage | `Machine.StateStorage` | 机器状态存储               |           |
| distributor  | `Machine.Distributor`  | 机器号分发器               |           |

### Machine.StateStorage

| 名称      | 数据类型                         | 说明         | 默认值    |
|---------|------------------------------|------------|--------|
| enabled | `boolean`                    | 是否启用状态存储   | `true` |
| local   | `Machine.StateStorage.Local` | 本地机器状态存储配置 |        |

#### Machine.StateStorage.Local

| 名称             | 数据类型     | 说明     | 默认值                      |
|----------------|----------|--------|--------------------------|
| state-location | `String` | 状态存储位置 | `./cosid-machine-state/` |

### Machine.Distributor

| 名称     | 数据类型                         | 说明                                                          | 默认值      |
|--------|------------------------------|-------------------------------------------------------------|----------|
| type   | `Machine.Distributor.Type`   | 机器号分配器类型：`MANUAL`/`STATEFUL_SET`/`JDBC`/`REDIS`/`ZOOKEEPER` | `MANUAL` |
| manual | `Machine.Distributor.Manual` | 手动分配器配置                                                     |          |

#### Machine.Distributor.Manual

| 名称        | 数据类型      | 说明  | 默认值    |
|-----------|-----------|-----|--------|
| machineId | `Integer` | 机器号 | `null` |

## IdDefinition

> `me.ahoo.cosid.spring.boot.starter.snowflake.SnowflakeIdProperties.IdDefinition`

| 名称             | 数据类型                         | 说明                               | 默认值                                   |
|----------------|------------------------------|----------------------------------|---------------------------------------|
| clock-sync     | `boolean`                    | 是否开启时钟同步                         | `true`                                |
| friendly       | `boolean`                    | 是否启用`SnowflakeFriendlyId`        | `true`                                |
| timestamp-unit | `IdDefinition.TimestampUnit` | 时间戳位的单位：`SECOND` / `MILLISECOND` | `TimestampUnit.MILLISECOND`           |
| epoch          | `int`                        | EPOCH                            | `cosid.snowflake.epoch`               |
| timestamp-bit  | `int`                        | 时间戳位数                            | 41                                    |
| machine-bit    | `int`                        | 机器位数                             | `cosid.snowflake.machine.machine-bit` |
| sequence-bit   | `int`                        | 序列位数                             | 12                                    |
| converter      | `IdConverterDefinition`      | Id转换器配置                          |                                       |

**YAML 配置样例**

```yaml
cosid:
  namespace: ${spring.application.name}
  snowflake:
    enabled: true
    zone-id: Asia/Shanghai
    epoch: 1577203200000
    clock-backwards:
      spin-threshold: 10
      broken-threshold: 2000
    machine:
      distributor:
        type: redis
      state-storage:
        local:
          state-location: ./cosid-machine-state/
    share:
      clock-sync: true
      friendly: true
    provider:
      short_id:
        converter:
          prefix: cosid_
          type: radix
          radix:
            char-size: 11
            pad-start: false
      safe-js:
        machine-bit: 3
        sequence-bit: 9
```
