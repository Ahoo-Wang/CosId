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
| type   | `Distributor.Type`   | Machine ID distributor type: `MANUAL`/`STATEFUL_SET`/`JDBC`/`REDIS`/`ZOOKEEPER` | `MANUAL` |
| manual | `Distributor.Manual` | Manual distributor configuration                                                     |          |

#### Distributor.Manual

| Name        | Data Type      | Description  | Default Value    |
|-----------|-----------|-----|--------|
| machineId | `Integer` | Machine ID | `null` |

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
