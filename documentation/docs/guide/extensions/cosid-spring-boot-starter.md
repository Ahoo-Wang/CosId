# CosId-Spring-Boot-Starter 模块

_Spring-Boot-Starter_ 模块 集成了所有 _CosId_ 扩展，提供了自动装配的能力，使 _CosId_ 框架在 _Spring Boot_ 项目中更加便捷地使用。

::: tip
该模块的配置文档请参考 [配置](../../reference/config/basic)。
:::

## 安装

::: code-group
```kotlin [Gradle(Kotlin)]
    val cosidVersion = "latestVersion"
    implementation("me.ahoo.cosid:cosid-spring-boot-starter:${cosidVersion}")
```
```xml [Maven]
    <dependencies>
        <dependency>
            <groupId>me.ahoo.cosid</groupId>
            <artifactId>cosid-spring-boot-starter</artifactId>
            <version>${cosid.version}</version>
        </dependency>
    </dependencies>
```
:::