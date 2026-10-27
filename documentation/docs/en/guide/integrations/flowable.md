# Flowable

`FlowableIdGenerator` (artifact `cosid-flowable`, package `me.ahoo.cosid.flowable`) makes Flowable's ids CosId text ids, through the named generator's
codec. Tested with Flowable 8.0.

| Property | Default |
|---|---|
| `cosid.flowable.enabled` | `true` |
| `cosid.flowable.generator-name` | `default` |

With Spring Boot an `EngineConfigurationConfigurer<SpringProcessEngineConfiguration>` sets it. Without a Spring
property, the generator comes from the system property `cosid.flowable`, else `default`. Without Spring:
`configuration.setIdGenerator(new FlowableIdGenerator(runtime, "default"))`.
