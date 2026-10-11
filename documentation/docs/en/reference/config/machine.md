# Machine Configuration

> `me.ahoo.cosid.spring.boot.starter.machine.MachineProperties`

| Name              | Data Type             | Description                   | Default Value       |
|-----------------|------------------|----------------------|-----------|
| stable          | `boolean`        | Whether it is a stable instance, stable instances will not recycle machine IDs | `false`   |
| port            | `Integer`        | Port number                  | Process ID (PID) |
| instanceId      | `String`         | Application instance ID (globally unique)         | Application IP:PID  |
| machineBit      | `int`            | Machine bits                 | `10`      |
| stateStorage    | `StateStorage`   | Machine state storage               |           |
| distributor     | `Distributor`    | Machine ID distributor               |           |
| guarder         | `Guarder`        | Machine ID (heartbeat) guarder            |           |
| clock-backwards | `ClockBackwards` | Clock backward configuration               |           |

::: tip Stable instances
Other instances never recycle the machine ID of a stable instance, even after it has been offline for longer than the safe guard duration.
When upgrading from an earlier version:

- The protection is effective only once every instance runs the new version: older instances do not read the stable flag and can still recycle a stable machine ID during a rolling upgrade.
- A stable instance that is already offline is unprotected until it has started once on the new version, which records the flag.
- JDBC: add the `stable` column (see [CosId-Jdbc](../../guide/extensions/cosid-jdbc.md)) and restart, since the column is detected once at startup.
:::

### StateStorage

> State Storage Configuration

| Name      | Data Type                 | Description         | Default Value    |
|---------|----------------------|------------|--------|
| local   | `StateStorage.Local` | Local machine state storage configuration |        |

#### StateStorage.Local

| Name             | Data Type     | Description     | Default Value                      |
|----------------|----------|--------|--------------------------|
| state-location | `String` | State storage location | `./cosid-machine-state/` |

### Distributor

> Machine ID Distributor Configuration

| Name     | Data Type                 | Description                                                          | Default Value      |
|--------|----------------------|-------------------------------------------------------------|----------|
| type   | `Distributor.Type`   | Machine ID distributor type: `MANUAL`/`STATEFUL_SET`/`JDBC`/`REDIS`/`ZOOKEEPER`/`MONGO`/`PROXY` | `MANUAL` |
| provider | `String` | Name of a `CosIdStoreProvider` bean to use instead of the one of `type`, for a backend CosId does not ship (since 3.4.0) | |
| manual | `Distributor.Manual` | Manual distributor configuration                                                     |          |

#### Distributor.Manual

| Name        | Data Type      | Description  | Default Value    |
|-----------|-----------|-----|--------|
| machineId | `Integer` | Machine ID | `null` |

### Guarder

> Machine ID (heartbeat) guarder configuration

| Name               | Data Type  | Description | Default Value |
|--------------------|------------|-------------|---------------|
| enabled            | `boolean`  | Whether machine ID guarding is enabled | `true` |
| initialDelay       | `Duration` | Initial delay before the first guard | `PT1M` |
| delay              | `Duration` | Delay between guards (remote heartbeat) | `PT1M` |
| safeGuardDuration  | `Duration` | Machine IDs not guarded for this long may be recycled by other instances | `PT5M` |
| failFast           | `boolean`  | Stop generating IDs once the machine ID lease is lost or expired | `true` |
| localStateInterval | `Duration` | How often the local machine state (the clock-backwards watermark used after a restart) is refreshed. After a crash, clock backwards shorter than about this interval may go undetected. Applies only with the guarder enabled and local file state storage (`stateStorage.local`). `0` disables it | `PT1S` |

### Backend Call Timeouts

| Property | Default Value | Description |
|----------|---------------|-------------|
| `cosid.machine.distributor.redis.timeout` | `1s` | Redis machine ID guard and revert |
| `cosid.machine.distributor.jdbc.query-timeout` | `5s` | Each JDBC statement |
| `cosid.machine.distributor.zookeeper.timeout` | `5s` | ZooKeeper machine ID guard and revert (Curator has no per-operation timeout) |
| `cosid.machine.distributor.mongo.timeout` | `5s` | Each MongoDB operation (driver `timeoutMS`); `0` keeps the `MongoClient` settings |
| `cosid.segment.distributor.mongo.timeout` | `5s` | Each MongoDB segment operation |
| `cosid.segment.distributor.zookeeper.timeout` | `5s` | Each ZooKeeper segment `nextMaxId` |
| `cosid.proxy.connect-timeout` | `2s` | Connecting to the CosId proxy server |
| `cosid.proxy.read-timeout` | `5s` | Waiting for the CosId proxy server's response, including machine ID distribution at startup |

`0` disables a timeout. With both proxy timeouts `0`, the application's own HTTP request factory is kept.

## ClockBackwards

> `me.ahoo.cosid.spring.boot.starter.machine.MachineProperties.ClockBackwards`

| Name               | Data Type  | Description                                           | Default Value    |
|------------------|-------|----------------------------------------------|--------|
| spin-threshold   | `int` | Spin synchronization threshold (ms)                                   | `10`   |
| broken-threshold | `int` | Threshold to throw exception (`ClockTooManyBackwardsException`) (ms) | `2000` |

**YAML Configuration Example**

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
