# cosid-integrations

The integration adapters of CosId 5 ([07-runtime-and-spring.md §5](../docs/architecture/07-runtime-and-spring.md), D15)
in one module. It depends on `cosid-core`; every third-party library is `compileOnly`, so an adapter works when the
application has its library, and the classes of the others are never loaded. With Spring Boot,
`CosIdIntegrationsConfiguration` (in `cosid-spring-boot-autoconfigure`) sets each one up from the context's
`CosIdRuntime`.

| Library | Adapter | Spring Boot |
|---|---|---|
| Jackson 3 | `@AsString` with `AsStringSerializer` / `AsStringDeserializer`; `CosIdJacksonModule` for `@AsString(generator = …)` | a `CosIdJacksonModule` bean |
| MyBatis | `CosIdInterceptor`: fills `@CosId` before an INSERT, for the entity or each one under `listKey` | `cosid.mybatis.enabled` (default `true`), `cosid.mybatis.list-key` (default `list`) |
| Spring Data JDBC | `CosIdBeforeConvertCallback`: `@CosId`, then an unset `@Id` from the `default` generator | `cosid.jdbc.enabled` (default `false`, as in 4.x), `cosid.jdbc.id-generator` |
| Activiti (tested 7.0.0.SR1) | `ActivitiIdGenerator` | `cosid.activiti.enabled` (default `true`), `cosid.activiti.generator-name` |
| Flowable (tested 8.0) | `FlowableIdGenerator` | `cosid.flowable.enabled` (default `true`), `cosid.flowable.generator-name` |
| Axon 4 (tested 4.13) | `CosIdIdentifierFactory` (`META-INF/services`) | binds the runtime, `cosid.axon.generator-name` |

Text ids come from the named generator whatever its strategy: a `long` generator through its codec, the 80-bit CosId
through its `PartsCodec`, a periodic segment through its `GroupedCodec`. Without a Spring name, Activiti, Flowable and
Axon take the generator from the system property `cosid.activiti`, `cosid.flowable` or `cosid.axon`, else `default`.

## Jackson: `@AsString`

```java
@AsString long id;                                                    // radix 62, 11 digits, padded (as in 4.x)
@AsString(generator = "order") long orderId;                          // the codec of generator "order"
@AsString(codec = Codec.TO_STRING) long a;                            // decimal text
@AsString(radix = 36, width = 13, padStart = false) long b;
@AsString(codec = Codec.FRIENDLY, layout = Preset.SNOWFLAKE_S, zone = "Asia/Shanghai") long c;
@AsString(codec = Codec.FRIENDLY, timeBits = 41, nodeBits = 5, seqBits = 17, epoch = 1700000000000L) long d;
```

The inline codecs need no module. `generator = …` needs `new CosIdJacksonModule(runtime)` registered with the
`JsonMapper` (Spring Boot registers it). Reading accepts the text, a JSON number the codec could have written, and an
empty string as `null`.

## Axon: the one static state

Axon creates its `IdentifierFactory` through `ServiceLoader` and keeps it in a JVM-wide static, so the factory cannot be
handed a runtime. `CosIdIdentifierFactory` therefore keeps a static stack of **bindings**, the only static state of
CosId 5:

- `CosIdIdentifierFactory.bind(runtime, generator)` pushes a binding; the most recent one gives Axon's identifiers.
  Closing the returned `Binding` removes exactly that one, so closing one of several runtimes (several Spring contexts)
  leaves the others bound.
- Spring Boot binds the context's runtime right after it is started and unbinds it right before it is closed (a
  `DestructionAwareBeanPostProcessor` on the `CosIdRuntime` bean), so the binding follows the runtime's lifecycle.
- Without Spring, bind it yourself:
  `try (var binding = CosIdIdentifierFactory.bind(runtime, null)) { … }`, or set
  `new CosIdIdentifierFactory(runtime, generator)` where Axon takes a factory.
- With nothing bound, the factory falls back to random UUIDs (Axon's own default) and logs a warning once: an
  application that has this module for another integration keeps Axon's identifiers.

Axon 5 has no `IdentifierFactory`; the adapter serves Axon 4.
