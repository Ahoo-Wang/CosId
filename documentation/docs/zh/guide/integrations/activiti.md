# Activiti

`ActivitiIdGenerator`（artifact `cosid-activiti`，包 `me.ahoo.cosid.activiti`）使 Activiti 的 ID 成为 CosId 文本 ID，经由指定名称生成器的
编解码器生成，不论其策略为何。已在 Activiti 7.0.0.SR1 上测试。

| 属性 | 默认值 |
|---|---|
| `cosid.activiti.enabled` | `true` |
| `cosid.activiti.generator-name` | `default` |

使用 Spring Boot 时由一个 `ProcessEngineConfigurationConfigurer` 设置。没有 Spring 属性时，生成器取自系统属性
`cosid.activiti`，否则为 `default`。不使用 Spring 时：
`configuration.setIdGenerator(new ActivitiIdGenerator(runtime, "default"))`。
