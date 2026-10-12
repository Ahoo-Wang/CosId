# CosId-Axon 模块

_Axon 模块_ 为 `Axon-Framework` 提供了 `CosId` 的支持，实现了 _Axon-Framework_ 的 `org.axonframework.common.IdentifierFactory` 接口。

## 安装

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

## 配置

默认情况下 `CosIdIdentifierFactory` 将从*ID生成器容器*(`IdGeneratorProvider`)中获取以 `__share__` 为名称的ID生成器。

开发者也可以通过配置系统属性 `cosid.axon` 自定义ID生成器的名称。

### 多个应用上下文

使用 starter 时，每个 Spring 上下文都会把自己的 `IdGeneratorProvider` 绑定到 `CosIdIdentifierFactory`，Axon 未指定 provider 创建的工厂使用最近一次绑定。绑定了不同的 provider 时（例如同一 JVM 中有多个上下文，如测试），会打印警告。如需指定 provider，请自行创建工厂：

```java
IdentifierFactory factory = new CosIdIdentifierFactory(idGeneratorProvider);
```
