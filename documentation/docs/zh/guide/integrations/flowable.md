# Flowable

`FlowableIdGenerator`（artifact `cosid-flowable`，包 `me.ahoo.cosid.flowable`）使 Flowable 的 ID 成为 CosId 文本 ID，经由指定名称生成器的
编解码器生成。已在 Flowable 8.0 上测试。

| 属性 | 默认值 |
|---|---|
| `cosid.flowable.enabled` | `true` |
| `cosid.flowable.generator-name` | `default` |

使用 Spring Boot 时由一个 `EngineConfigurationConfigurer<SpringProcessEngineConfiguration>` 设置。没有 Spring
属性时，生成器取自系统属性 `cosid.flowable`，否则为 `default`。不使用 Spring 时：
`configuration.setIdGenerator(new FlowableIdGenerator(runtime, "default"))`。
