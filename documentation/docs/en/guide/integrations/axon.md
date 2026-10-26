# CosId-Axon Module

The _Axon module_ provides `CosId` support for `Axon-Framework`, implementing the `org.axonframework.common.IdentifierFactory` interface of _Axon-Framework_.

## Installation

::: code-group
```kotlin [Gradle(Kotlin)]
    val cosidVersion = "latestVersion"
    implementation("me.ahoo.cosid:cosid-axon:${cosidVersion}")
```
```xml [Maven]
    <dependencies>
        <dependency>
            <groupId>me.ahoo.cosid</groupId>
            <artifactId>cosid-axon</artifactId>
            <version>${cosid.version}</version>
        </dependency>
    </dependencies>
```
:::

## Configuration

By default, `CosIdIdentifierFactory` will retrieve the ID generator named `__share__` from the *ID generator container* (`IdGeneratorProvider`).

To use another ID generator than the shared one, name it in the configuration (since 4.1):

```yaml
cosid:
  axon:
    generator-name: event
```

Without the starter, or when `cosid.axon.generator-name` is unset, the system property `cosid.axon` names it.

### Before the context binds

Axon's `IdentifierFactory.getInstance()` can be called before the Spring context has bound its provider (for example, from a static initializer or early in startup). Until then, the factory resolves from `DefaultIdGeneratorProvider.INSTANCE`, and `cosid.axon.generator-name` is not applied yet: the generator is the one named by the system property `cosid.axon`, else `__share__`. After the context binds, the same factory uses the context's provider and configured name.

### Several application contexts

With the starter, each Spring context binds its `IdGeneratorProvider` to `CosIdIdentifierFactory`, and factories created by Axon without a provider use the most recent binding. When different providers are bound (several contexts in one JVM, such as tests), or the same provider is bound with different generator names (several contexts without `cosid.provider.isolated`), a warning is logged. Closing a context removes only its own binding. To choose the provider, create the factory yourself:

```java
IdentifierFactory factory = new CosIdIdentifierFactory(idGeneratorProvider);
// or, to choose the generator as well:
IdentifierFactory named = new CosIdIdentifierFactory("event", idGeneratorProvider);
```
