# integrations

The integration adapters of CosId 5 ([07-runtime-and-spring.md §5](../docs/architecture/07-runtime-and-spring.md)),
split by dependency (D29): one module per third-party library, each depending on `cosid-core` and that library only, and
never on another integration. With Spring Boot, `CosIdIntegrationsConfiguration` (in `cosid-spring-boot-autoconfigure`)
sets up each module on the classpath from the context's `CosIdRuntime`.

| Artifact (directory) | Adapter | Spring Boot |
|---|---|---|
| `cosid-jackson` (`jackson/`, Jackson 3) | `@AsString` with `AsStringSerializer` / `AsStringDeserializer`; `CosIdJacksonModule` for `@AsString(generator = …)` | a `CosIdJacksonModule` bean |
| `cosid-mybatis` (`mybatis/`) | `CosIdInterceptor`: fills `@CosId` before an INSERT, for the entity or each one under `listKey` | `cosid.mybatis.enabled` (default `true`), `cosid.mybatis.list-key` (default `list`) |
| `cosid-spring-data-jdbc` (`spring-data-jdbc/`) | `CosIdBeforeConvertCallback`: `@CosId`, then an unset `@Id` from the `default` generator | `cosid.jdbc.enabled` (default `false`, as in 4.x), `cosid.jdbc.id-generator` |
| `cosid-activiti` (`activiti/`, tested 7.0.0.SR1) | `ActivitiIdGenerator` | `cosid.activiti.enabled` (default `true`), `cosid.activiti.generator-name` |
| `cosid-flowable` (`flowable/`, tested 8.0) | `FlowableIdGenerator` | `cosid.flowable.enabled` (default `true`), `cosid.flowable.generator-name` |
| `cosid-axon` (`axon/`, Axon 4, tested 4.13) | `CosIdIdentifierFactory` (`META-INF/services`) | binds the runtime, `cosid.axon.generator-name` |

Text ids come from the named generator whatever its strategy: a `long` generator through its codec, the 80-bit CosId
through its `PartsCodec`, a periodic segment through its `GroupedCodec`. Without a Spring name, Activiti, Flowable and
Axon take the generator from the system property `cosid.activiti`, `cosid.flowable` or `cosid.axon`, else `default`.

`cosid-activiti` depends on MyBatis 3.5.19 itself: Activiti 7.0.0.SR1, the latest Activiti, brings MyBatis 3.4.2,
which has a known vulnerability (GHSA-qq48-m4jx-xqh8, fixed in 3.5.6). The direct dependency puts the patched one on
the application's classpath, with Maven and Gradle alike; the Activiti engine test runs on it. MyBatis 3.6 does not
run Activiti 7 (`TypeHandlerRegistry.register(Class, JdbcType, TypeHandler)` is gone); the `cosid-mybatis` integration
is built on 3.6.

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
- With nothing bound, generating an identifier throws an `IllegalStateException` (as in 4.x): having `cosid-axon` means
  Axon's identifiers come from CosId.

Axon 5 has no `IdentifierFactory`; the adapter serves Axon 4.
