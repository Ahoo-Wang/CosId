# 基础配置

> `me.ahoo.cosid.spring.boot.starter.CosIdProperties`，前缀 `cosid`

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `cosid.enabled` | `boolean` | `true` | 为 `false` 时关闭整个 Starter |
| `cosid.namespace` | `String` | `cosid` | 在同一个存储中把一个应用的计数器和机器号与其他应用隔离。建议设置为应用名：`${spring.application.name}` |
| `cosid.provider.isolated` | `boolean` | `false` | `false`：`IdGeneratorProvider` Bean 就是 `DefaultIdGeneratorProvider.INSTANCE`。`true`：每个 `ApplicationContext` 拥有自己的容器，并在上下文关闭时清空，生成器不会在上下文之间共享（devtools 重启、缓存的测试上下文）；此时 `INSTANCE` 不再持有 Spring 注册的生成器 |
| `cosid.metrics.enabled` | `boolean` | `true` | 类路径上有 Micrometer 时注册 CosId 指标，见[监控](../../guide/operations/monitoring.md#指标) |
| `cosid.metrics.generated.enabled` | `boolean` | `false` | 统计每个号段和 Snowflake 生成器生成的 ID 数（`cosid.generator.generated`），会用计数装饰器包装生成器 |

各生成器有自己的配置页：[机器号](./machine.md)（`cosid.machine`）、[SnowflakeId](./snowflake.md)（`cosid.snowflake`）、[SegmentId](./segment.md)（`cosid.segment`）和 [CosIdGenerator](./cosid-generator.md)（`cosid.generator`）。后端属性见 [ZooKeeper](./zookeeper.md)、[Proxy](./proxy.md) 配置页以及各后端指南。

```yaml
cosid:
  namespace: ${spring.application.name}
```

## ID 转换器

> `me.ahoo.cosid.spring.boot.starter.IdConverterDefinition`

每个 Snowflake 和号段生成器都有一个 `converter`，决定 `generateAsString()` 的格式。各转换器的输出见 [ID 转换器](../../guide/concepts/id-converter.md)。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `type` | `Type` | `radix` | `to_string`、`radix`、`radix36`、`snowflake_friendly`（仅 Snowflake ID）或 `custom` |
| `prefix` | `String` | 无 | ID 之前的文本 |
| `suffix` | `String` | 无 | ID 之后的文本 |
| `radix.char-size` | `int` | `11` | 62 进制字符串的长度，最多 11 |
| `radix.pad-start` | `boolean` | `true` | 用 `0` 补齐到 `char-size`，使字符串排序与数字一致 |
| `radix36.char-size` | `int` | `13` | 36 进制字符串的长度，最多 13 |
| `radix36.pad-start` | `boolean` | `true` | 用 `0` 补齐到 `char-size` |
| `to-string.char-size` | `int` | `11` | 补齐时的长度 |
| `to-string.pad-start` | `boolean` | `false` | 用 `0` 补齐到 `char-size` |
| `friendly.pad-start` | `boolean` | `false` | `snowflake_friendly`：用 `0` 补齐机器号和序列号，使字符串排序与 ID 一致 |
| `date-prefix.enabled` | `boolean` | `false` | 加上生成时的日期前缀 |
| `date-prefix.pattern` | `String` | `yyMMdd` | 日期格式 |
| `date-prefix.delimiter` | `String` | `-` | 日期与 ID 之间的分隔符 |
| `date-prefix.before-prefix` | `boolean` | `true` | 日期放在 `prefix` 之内（`true`）还是之外 |
| `group-prefix.enabled` | `boolean` | `false` | 加上分组号段 ID 的分组前缀；需要 `group.by` |
| `group-prefix.delimiter` | `String` | `-` | 分组与 ID 之间的分隔符 |
| `group-prefix.before-prefix` | `boolean` | `true` | 分组放在 `prefix` 之内（`true`）还是之外 |
| `custom.type` | `Class<? extends IdConverter>` | 无 | `custom`：转换器类，需要公共无参构造器 |

```yaml
cosid:
  segment:
    share:
      converter:
        type: radix
        prefix: cosid_
        radix:
          char-size: 8
          pad-start: true
```

## 集成开关

每个可选集成在其模块位于 classpath 时生效，并且可以关闭：

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `cosid.mybatis.enabled` | `true` | 填充 ID 字段的 MyBatis 插件 |
| `cosid.jdbc.enabled` | `false`（3.x 为 `true`） | Spring Data JDBC 集成：`@Id` 字段解析器和转换前回调。`@Id` 解析器会加入全局字段解析器，因此也作用于 MyBatis 等其他路径。关闭时，启动日志会以 INFO 级别列出带 `@Id` 属性或 `@CosId` 字段、CosId 不会填充的 Spring Data JDBC 实体 |
| `cosid.flowable.enabled` | `true` | Flowable ID 生成器 |
| `cosid.activiti.enabled` | `true` | Activiti ID 生成器 |
| `cosid.actuator.delete.enabled` | `false`（3.x 为 `true`） | `cosid` actuator 端点的 `DELETE` 操作，可在运行时删除 ID 生成器 |

## Actuator 端点

classpath 中有 Spring Boot Actuator 时，Starter 会添加三个端点，只有在可用（启用并通过 `management.endpoints.web.exposure.include` 暴露）时才会创建：

| 端点 | 操作 |
|------|------|
| `cosid` | `GET /actuator/cosid`：所有生成器及其状态；`GET /actuator/cosid/{name}`：单个生成器；`DELETE /actuator/cosid/{name}`：删除一个生成器，需开启 `cosid.actuator.delete.enabled` |
| `cosidGenerator` | `GET /actuator/cosidGenerator`：共享生成器的一个 ID；`GET /actuator/cosidGenerator/{name}`：指定 `long` 生成器的一个 ID |
| `cosidStringGenerator` | `GET /actuator/cosidStringGenerator`、`GET /actuator/cosidStringGenerator/{name}`：同上，以字符串返回 |

机器号租约续约失败期间，`machineId` 健康指示器报告 `DOWN`。
