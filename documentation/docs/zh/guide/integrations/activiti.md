# CosId-Activiti 模块

_Activiti 模块_ 为 `Activiti` 提供了 `CosId` 的支持，实现了 _Activiti_ 的 `org.activiti.engine.impl.cfg.IdGenerator` 接口。

## 安装

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

## 配置

默认情况下 `ActivitiIdGenerator` 将从*ID生成器容器*(`IdGeneratorProvider`)中获取以 `__share__` 为名称的ID生成器。

如需使用共享生成器以外的 ID 生成器，可在配置中指定其名称（4.1 起）：

```yaml
cosid:
  activiti:
    generator-name: bpm
```

不使用 Starter，或未设置 `cosid.activiti.generator-name` 时，由系统属性 `cosid.activiti` 指定。

