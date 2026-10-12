# SnowflakeId Configuration

> `me.ahoo.cosid.spring.boot.starter.snowflake.SnowflakeIdProperties`, prefix `cosid.snowflake`

Snowflake IDs need a machine id: set `cosid.machine.enabled=true` as well, see [machine ID configuration](./machine.md).

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `boolean` | `false` | Register the Snowflake generators |
| `zone-id` | `ZoneId` | system default | Time zone of `snowflake_friendly` strings |
| `epoch` | `long` | `1577203200000` (2019-12-24T16:00:00Z) | Default epoch of every generator, in the unit of its timestamp |
| `share` | `IdDefinition` | | The share generator, `__share__SnowflakeId` |
| `share.enabled` | `boolean` | `true` | Register the share generator |
| `provider` | `Map<String, IdDefinition>` | empty | Named generators, `{name}SnowflakeId` |

## IdDefinition

The properties of `share` and of each `provider.{name}`:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `namespace` | `String` | `cosid.namespace` | Namespace of the generator's machine id |
| `timestamp-unit` | `TimestampUnit` | `millisecond` | `millisecond` or `second` |
| `epoch` | `long` | `cosid.snowflake.epoch` | Epoch of this generator; `0` uses the default. With `timestamp-unit: second`, give it in seconds |
| `timestamp-bit` | `int` | `41` | Timestamp bits |
| `machine-bit` | `Integer` | `cosid.machine.machine-bit` | Machine id bits, at most 31. Give a generator with fewer bits its own `namespace` |
| `sequence-bit` | `int` | `12` | Sequence bits |
| `sequence-reset-threshold` | `long` | `2047` | The sequence restarts in the next time unit once it reaches this value. The default is half the 12-bit range and does not follow `sequence-bit`: set it when you change the bits |
| `clock-sync` | `boolean` | `true` | Wait out a clock that moved backwards (`ClockSyncSnowflakeId`) instead of throwing |
| `converter` | `IdConverterDefinition` | `radix` | Format of `generateAsString()`, see [ID converter](./basic.md#id-converter). `group-prefix` is not allowed: Snowflake IDs have no group |

The bits must add up to at most 63. The defaults (`41 + 10 + 12`) are those of a millisecond timestamp; with `timestamp-unit: second`, set the bits as well, for example `31 + 10 + 22`.

## Example

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
