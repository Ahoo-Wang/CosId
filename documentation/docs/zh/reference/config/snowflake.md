# SnowflakeId 配置

> `me.ahoo.cosid.spring.boot.starter.snowflake.SnowflakeIdProperties`，前缀 `cosid.snowflake`

Snowflake ID 需要机器号：请同时设置 `cosid.machine.enabled=true`，见[机器号配置](./machine.md)。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enabled` | `boolean` | `false` | 注册 Snowflake 生成器 |
| `zone-id` | `ZoneId` | 系统默认 | `snowflake_friendly` 字符串使用的时区 |
| `epoch` | `long` | `1577203200000`（2019-12-24T16:00:00Z） | 所有生成器的默认 epoch，单位与其时间戳一致 |
| `share` | `IdDefinition` | | 共享生成器，`__share__SnowflakeId` |
| `share.enabled` | `boolean` | `true` | 注册共享生成器 |
| `provider` | `Map<String, IdDefinition>` | 空 | 命名生成器，`{name}SnowflakeId` |

## IdDefinition

`share` 和每个 `provider.{name}` 的属性：

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `namespace` | `String` | `cosid.namespace` | 该生成器机器号所在的命名空间 |
| `timestamp-unit` | `TimestampUnit` | `millisecond` | `millisecond` 或 `second` |
| `epoch` | `long` | `cosid.snowflake.epoch` | 该生成器的 epoch；`0` 表示使用默认值。`timestamp-unit: second` 时以秒为单位 |
| `timestamp-bit` | `int` | `41` | 时间戳位数 |
| `machine-bit` | `Integer` | `cosid.machine.machine-bit` | 机器号位数，最多 31。位数更少的生成器请设置单独的 `namespace` |
| `sequence-bit` | `int` | `12` | 序列号位数 |
| `sequence-reset-threshold` | `long` | `2047` | 序列号达到该值后在下一个时间单位重新开始。默认值是 12 位范围的一半，不会随 `sequence-bit` 变化：修改位数时请一并设置 |
| `clock-sync` | `boolean` | `true` | 时钟回拨时等待（`ClockSyncSnowflakeId`）而不是抛出异常 |
| `converter` | `IdConverterDefinition` | `radix` | `generateAsString()` 的格式，见 [ID 转换器](./basic.md#id-转换器)。不允许 `group-prefix`：Snowflake ID 没有分组 |

各部分位数之和不能超过 63。默认值（`41 + 10 + 12`）对应毫秒时间戳；`timestamp-unit: second` 时请同时设置位数，例如 `31 + 10 + 22`。

## 示例

```yaml
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true
    distributor:
      type: redis
  snowflake:
    enabled: true
    zone-id: Asia/Shanghai
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
      friendly_second:
        timestamp-unit: second
        epoch: 1577203200
        timestamp-bit: 31
        sequence-bit: 22
        converter:
          type: snowflake_friendly
```
