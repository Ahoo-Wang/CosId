# 基础配置

> `me.ahoo.cosid.spring.boot.starter.CosIdProperties`

| 名称        | 数据类型      | 说明                   | 默认值       |
|-----------|-----------|----------------------|-----------|
| enabled   | `boolean` | 是否启用 CosId           | `true`    |
| namespace | `String`  | 命名空间，用于隔离不同应用间的分布式ID | `cosid` |

**YAML 配置样例**

```yaml
cosid:
  namespace: ${spring.application.name}
```

## IdConverterDefinition

> `me.ahoo.cosid.spring.boot.starter.IdConverterDefinition`

| 名称     | 数据类型                          | 说明                                             | 默认值                         |
|--------|-------------------------------|------------------------------------------------|-----------------------------|
| type   | `IdConverterDefinition.Type`  | 转换器类型：`TO_STRING`、`SNOWFLAKE_FRIENDLY`、`RADIX` | `Type.RADIX`                |
| prefix | `String`                      | 前缀                                             | `""`                        |
| radix  | `IdConverterDefinition.Radix` | `Radix62IdConverter` 转换器配置                     | `TimestampUnit.MILLISECOND` |

### Radix

| 名称        | 数据类型      | 说明                                                    | 默认值     |
|-----------|-----------|-------------------------------------------------------|---------|
| char-size | `String`  | 字符串ID长度                                               | `11`    |
| pad-start | `boolean` | 当字符串不满足 `charSize` 时，是否填充字符(`'0'`)。如果需要保证字符串有序，需开启该功能 | `false` |

**YAML 配置样例**

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
