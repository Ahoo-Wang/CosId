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

## Integration switches

Each optional integration applies when its module is on the classpath, and can be turned off (since 3.4.0):

| Name | Description | Default |
|------|-------------|---------|
| `cosid.mybatis.enabled` | MyBatis plugin that fills id fields | `true` |
| `cosid.jdbc.enabled` | Spring Data JDBC integration: the `@Id` field parser and the before-convert callback. The `@Id` parser joins the global field parser, so it also applies on other paths such as MyBatis. While it is off, an INFO at startup names the Spring Data JDBC entities that have `@CosId` fields. | `false` (`true` in 3.x) |
| `cosid.flowable.enabled` | Flowable id generator | `true` |
| `cosid.activiti.enabled` | Activiti id generator | `true` |
| `cosid.actuator.delete.enabled` | The `DELETE` operation of the `cosid` actuator endpoint, which removes an id generator at runtime. | `false` (`true` in 3.x) |

The `cosid`, `cosidGenerator` and `cosidStringGenerator` actuator endpoints are only created when they are available (enabled and exposed, `management.endpoints.web.exposure.include`).
