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

To use another ID generator than the shared one, name it in the configuration (since 4.1):

```yaml
cosid:
  flowable:
    generator-name: bpm
```

Without the starter, or when `cosid.flowable.generator-name` is unset, the system property `cosid.flowable` names it.


