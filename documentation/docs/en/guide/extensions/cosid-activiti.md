# CosId-Activiti Module

_Activiti module_ provides `CosId` support for `Activiti`, implementing _Activiti_'s `org.activiti.engine.impl.cfg.IdGenerator` interface.

## Installation

::: code-group
```kotlin [Gradle(Kotlin)]
    val cosidVersion = "latestVersion"
    implementation("me.ahoo.cosid:cosid-activiti:${cosidVersion}")
```
```xml [Maven]
    <dependencies>
        <dependency>
            <groupId>me.ahoo.cosid</groupId>
            <artifactId>cosid-activiti</artifactId>
            <version>${cosid.version}</version>
        </dependency>
    </dependencies>
```
:::

## Configuration

By default `ActivitiIdGenerator` will get the ID generator named `__share__` from the *ID generator container* (`IdGeneratorProvider`).

Developers can also customize the ID generator name through the system property `cosid.activiti`.

