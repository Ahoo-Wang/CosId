# 基础配置

> `me.ahoo.cosid.spring.boot.starter.CosIdProperties`

| 名称        | 数据类型      | 说明                   | 默认值     |
|-----------|-----------|----------------------|---------|
| enabled   | `boolean` | 是否启用 CosId           | `true`  |
| namespace | `String`  | 命名空间，用于隔离不同应用间的分布式ID | `cosid` |

**YAML 配置样例**

```yaml
cosid:
  namespace: ${spring.application.name}
```

## IdConverterDefinition

> `me.ahoo.cosid.spring.boot.starter.IdConverterDefinition`

| 名称       | 数据类型                             | 说明                                          | 默认值     |
|----------|----------------------------------|---------------------------------------------|---------|
| type     | `IdConverterDefinition.Type`     | 类型：`TO_STRING`/`SNOWFLAKE_FRIENDLY`/`RADIX` | `RADIX` |
| prefix   | `String`                         | 前缀                                          | `""`    |
| radix    | `IdConverterDefinition.Radix`    | `Radix62IdConverter` 转换器配置                  |         |
| friendly | `IdConverterDefinition.Friendly` | 转换器配置                                       |         |

### Radix

| 名称        | 数据类型      | 说明                                                    | 默认值     |
|-----------|-----------|-------------------------------------------------------|---------|
| char-size | `String`  | 字符串ID长度                                               | `11`    |
| pad-start | `boolean` | 当字符串不满足 `charSize` 时，是否填充字符(`'0'`)。如果需要保证字符串有序，需开启该功能 | `false` |

### Friendly

| 名称        | 数据类型      | 说明                                                    | 默认值     |
|-----------|-----------|-------------------------------------------------------|---------|
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

## 集成开关

各可选集成在其模块位于类路径时生效，并可单独关闭（3.4.0 起）：

| 名称 | 说明 | 默认值 |
|------|------|--------|
| `cosid.mybatis.enabled` | 填充 ID 字段的 MyBatis 插件 | `true` |
| `cosid.jdbc.enabled` | Spring Data JDBC 集成：`@Id` 字段解析器与 before-convert 回调。`@Id` 解析器会加入全局字段解析器，因此也会作用于 MyBatis 等其他路径。关闭时，若 Spring Data JDBC 实体含 `@CosId` 字段，启动时会打印一条 INFO 列出这些实体。 | `false`（3.x 为 `true`） |
| `cosid.flowable.enabled` | Flowable ID 生成器 | `true` |
| `cosid.activiti.enabled` | Activiti ID 生成器 | `true` |
| `cosid.actuator.delete.enabled` | `cosid` Actuator 端点的 `DELETE` 操作（运行时移除 ID 生成器）。 | `false`（3.x 为 `true`） |

`cosid`、`cosidGenerator`、`cosidStringGenerator` Actuator 端点仅在可用（启用且已暴露，`management.endpoints.web.exposure.include`）时创建。
