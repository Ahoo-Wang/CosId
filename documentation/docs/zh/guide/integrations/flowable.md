# CosId-Flowable 模块

_Flowable 模块_ 为 `Flowable` 提供了 `CosId` 的支持，实现了 _Flowable_ 的 `org.flowable.common.engine.impl.cfg.IdGenerator` 接口。

## 安装

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

## 配置

默认情况下 `FlowableIdGenerator` 将从*ID生成器容器*(`IdGeneratorProvider`)中获取以 `__share__` 为名称的ID生成器。

如需使用共享生成器以外的 ID 生成器，可在配置中指定其名称（4.1 起）：

```yaml
cosid:
  flowable:
    generator-name: bpm
```

不使用 Starter，或未设置 `cosid.flowable.generator-name` 时，由系统属性 `cosid.flowable` 指定。


