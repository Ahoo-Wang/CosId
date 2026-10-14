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

### Several application contexts

With the starter, each Spring context binds its `IdGeneratorProvider` to `CosIdIdentifierFactory`, and factories created by Axon without a provider use the most recent binding. When different providers are bound (several contexts in one JVM, such as tests), a warning is logged. To choose the provider, create the factory yourself:

```java
IdentifierFactory factory = new CosIdIdentifierFactory(idGeneratorProvider);
```
