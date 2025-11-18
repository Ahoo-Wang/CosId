# SnowflakeId Configuration

> `me.ahoo.cosid.spring.boot.starter.snowflake.SnowflakeIdProperties`

| Name              | Data Type                        | Description        | Default Value                                             |
|-----------------|-----------------------------|-----------|-------------------------------------------------|
| enabled         | `boolean`                   | Whether to enable      | `false`                                         |
| zone-id         | `String`                    | Time zone        | `ZoneId.systemDefault().getId()`                |
| epoch           | `long`                      | EPOCH     | `CosId.COSID_EPOCH` <br> (UTC 2019-12-24 16:00) |
| machine         | `Machine`                   | Machine ID distributor configuration  |                                                 |
| share           | `IdDefinition`              | Shared ID generator configuration | Yes                                               |
| provider        | `Map<String, IdDefinition>` | Multi-ID generator configuration  | `null`                                          |

## IdDefinition

> `me.ahoo.cosid.spring.boot.starter.snowflake.SnowflakeIdProperties.IdDefinition`

| Name             | Data Type                         | Description                               | Default Value                                   |
|----------------|------------------------------|----------------------------------|---------------------------------------|
| clock-sync     | `boolean`                    | Whether to enable clock synchronization                         | `true`                                |
| timestamp-unit | `IdDefinition.TimestampUnit` | Unit of timestamp bits: `SECOND` / `MILLISECOND` | `TimestampUnit.MILLISECOND`           |
| epoch          | `int`                        | EPOCH                            | `cosid.snowflake.epoch`               |
| timestamp-bit  | `int`                        | Timestamp bits                            | 41                                    |
| machine-bit    | `int`                        | Machine bits                             | `cosid.snowflake.machine.machine-bit` |
| sequence-bit   | `int`                        | Sequence bits                             | 12                                    |
| converter      | `IdConverterDefinition`      | ID converter configuration                          |                                       |

**YAML Configuration Example**

```yaml
cosid:
  namespace: ${spring.application.name}
  snowflake:
    enabled: true
    zone-id: Asia/Shanghai
    epoch: 1577203200000
    share:
      clock-sync: true
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
