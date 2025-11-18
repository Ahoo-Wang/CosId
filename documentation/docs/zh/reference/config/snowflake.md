# SnowflakeId 配置

> `me.ahoo.cosid.spring.boot.starter.snowflake.SnowflakeIdProperties`

| 名称              | 数据类型                        | 说明        | 默认值                                             |
|-----------------|-----------------------------|-----------|-------------------------------------------------|
| enabled         | `boolean`                   | 是否启用      | `false`                                         |
| zone-id         | `String`                    | 时区        | `ZoneId.systemDefault().getId()`                |
| epoch           | `long`                      | EPOCH     | `CosId.COSID_EPOCH` <br> (UTC 2019-12-24 16:00) |
| machine         | `Machine`                   | 机器号分配器配置  |                                                 |
| share           | `IdDefinition`              | 共享ID生成器配置 | 有                                               |
| provider        | `Map<String, IdDefinition>` | 多ID生成器配置  | `null`                                          |

## IdDefinition

> `me.ahoo.cosid.spring.boot.starter.snowflake.SnowflakeIdProperties.IdDefinition`

| 名称             | 数据类型                         | 说明                               | 默认值                                   |
|----------------|------------------------------|----------------------------------|---------------------------------------|
| clock-sync     | `boolean`                    | 是否开启时钟同步                         | `true`                                |
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
