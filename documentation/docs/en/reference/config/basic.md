# Basic Configuration

> `me.ahoo.cosid.spring.boot.starter.CosIdProperties`

| Name        | Data Type      | Description                   | Default Value     |
|-----------|-----------|----------------------|---------|
| enabled   | `boolean` | Whether to enable CosId           | `true`  |
| namespace | `String`  | Namespace for isolating distributed IDs between different applications | `cosid` |

**YAML Configuration Example**

```yaml
cosid:
  namespace: ${spring.application.name}
```

## IdConverterDefinition

> `me.ahoo.cosid.spring.boot.starter.IdConverterDefinition`

| Name       | Data Type                             | Description                                          | Default Value     |
|----------|----------------------------------|---------------------------------------------|---------|
| type     | `IdConverterDefinition.Type`     | Type: `TO_STRING`/`SNOWFLAKE_FRIENDLY`/`RADIX` | `RADIX` |
| prefix   | `String`                         | Prefix                                          | `""`    |
| radix    | `IdConverterDefinition.Radix`    | `Radix62IdConverter` converter configuration                  |         |
| friendly | `IdConverterDefinition.Friendly` | Converter configuration                                       |         |

### Radix

| Name        | Data Type      | Description                                                    | Default Value     |
|-----------|-----------|-------------------------------------------------------|---------|
| char-size | `String`  | String ID length                                               | `11`    |
| pad-start | `boolean` | When the string does not meet `charSize`, whether to pad characters(`'0'`). If ordered strings are needed, enable this feature | `false` |

### Friendly

| Name        | Data Type      | Description                                                    | Default Value     |
|-----------|-----------|-------------------------------------------------------|---------|
| pad-start | `boolean` | When the string does not meet `charSize`, whether to pad characters(`'0'`). If ordered strings are needed, enable this feature | `false` |

**YAML Configuration Example**

```yaml
cosid:
  snowflake:
    share:
      converter:
        prefix: cosid_
        radix:
          pad-start: false
          char-size: 11
  segment:
    share:
      converter:
        prefix: cosid_
        radix:
          pad-start: false
          char-size: 8
```
