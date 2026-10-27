# Activiti

`ActivitiIdGenerator` (artifact `cosid-activiti`, package `me.ahoo.cosid.activiti`) makes Activiti's ids CosId text ids, through the named generator's
codec, whatever its strategy. Tested with Activiti 7.0.0.SR1.

| Property | Default |
|---|---|
| `cosid.activiti.enabled` | `true` |
| `cosid.activiti.generator-name` | `default` |

With Spring Boot a `ProcessEngineConfigurationConfigurer` sets it. Without a Spring property, the generator comes from
the system property `cosid.activiti`, else `default`. Without Spring:
`configuration.setIdGenerator(new ActivitiIdGenerator(runtime, "default"))`.
