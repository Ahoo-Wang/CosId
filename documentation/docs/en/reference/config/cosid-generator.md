# CosId Generator Configuration

> `me.ahoo.cosid.spring.boot.starter.cosid.CosIdGeneratorProperties`

| Name            | Data Type      | Description                                | Default Value       |
|---------------|-----------|-----------------------------------|-----------|
| enabled       | `boolean` | Whether to enable                              | `false`   |
| type          | `enum`    | Type: `RADIX62`/`RADIX36`/`FRIENDLY` | `RADIX62` |
| namespace     | `String`  | Namespace                              | `cosid`   |
| timestamp-bit | `int`     | Timestamp bits                             | `44`      |
| machine-bit   | `int`     | Machine bits                              | `20`      |
| sequence-bit  | `int`     | Sequence bits                              | `16`      |


## Configuration Example

```yaml {7-8}
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true
    distributor:
      type: jdbc
  generator:
    enabled: true
```
