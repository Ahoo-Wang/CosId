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

Developers can also customize the ID generator name by configuring the system property `cosid.axon`.

