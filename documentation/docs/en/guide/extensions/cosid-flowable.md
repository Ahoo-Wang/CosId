# CosId-Flowable Module

The _Flowable module_ provides `CosId` support for `Flowable`, implementing the `org.flowable.common.engine.impl.cfg.IdGenerator` interface of _Flowable_.

## Installation

::: code-group
```kotlin [Gradle(Kotlin)]
    val cosidVersion = "latestVersion"
    implementation("me.ahoo.cosid:cosid-flowable:${cosidVersion}")
```
```xml [Maven]
    <dependencies>
        <dependency>
            <groupId>me.ahoo.cosid</groupId>
            <artifactId>cosid-flowable</artifactId>
            <version>${cosid.version}</version>
        </dependency>
    </dependencies>
```
:::

## Configuration

By default, `FlowableIdGenerator` will retrieve the ID generator named `__share__` from the *ID generator container* (`IdGeneratorProvider`).

Developers can also customize the ID generator name by configuring the system property `cosid.flowable`.


