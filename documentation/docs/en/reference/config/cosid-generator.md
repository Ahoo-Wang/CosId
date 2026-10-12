# CosIdGenerator Configuration

> `me.ahoo.cosid.spring.boot.starter.cosid.CosIdGeneratorProperties`, prefix `cosid.generator`

`CosIdGenerator` needs a machine id: set `cosid.machine.enabled=true` as well, see [machine ID configuration](./machine.md).

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `boolean` | `false` | Register the `CosIdGenerator` bean, named `cosid` in the `IdGeneratorProvider` |
| `type` | `Type` | `radix62` | `radix62`, `radix36` or `friendly` |
| `namespace` | `String` | `cosid.namespace` | Namespace of the generator's machine id |
| `timestamp-bit` | `int` | `44` | Timestamp bits |
| `machine-bit` | `int` | `20` | Machine id bits; independent of `cosid.machine.machine-bit` |
| `sequence-bit` | `int` | `16` | Sequence bits |
| `sequence-reset-threshold` | `int` | `32767` | The sequence restarts in the next millisecond once it reaches this value |
| `zone-id` | `ZoneId` | system default | `friendly`: time zone of the timestamp |
| `pad-start` | `boolean` | `true` | `friendly`: pad the machine id and sequence with `0` |

## Example

```yaml
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true
    distributor:
      type: jdbc
  generator:
    enabled: true
```
