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

如需使用共享生成器以外的 ID 生成器，可在配置中指定其名称（4.1 起）：

```yaml
cosid:
  axon:
    generator-name: event
```

不使用 Starter，或未设置 `cosid.axon.generator-name` 时，由系统属性 `cosid.axon` 指定。

### 上下文绑定之前

Axon 的 `IdentifierFactory.getInstance()` 可能在 Spring 上下文绑定 provider 之前被调用（例如在静态初始化或启动早期）。在此之前，工厂从 `DefaultIdGeneratorProvider.INSTANCE` 获取生成器，且 `cosid.axon.generator-name` 尚未生效：使用系统属性 `cosid.axon` 指定的生成器，未设置时使用 `__share__`。上下文绑定后，同一个工厂改用该上下文的 provider 与配置的名称。

### 多个应用上下文

使用 starter 时，每个 Spring 上下文都会把自己的 `IdGeneratorProvider` 绑定到 `CosIdIdentifierFactory`，Axon 未指定 provider 创建的工厂使用最近一次绑定。绑定了不同的 provider 时（例如同一 JVM 中有多个上下文，如测试），或同一 provider 以不同的生成器名称绑定时（多个上下文未开启 `cosid.provider.isolated`），会打印警告。关闭上下文只会移除它自己的绑定。如需指定 provider，请自行创建工厂：

```java
IdentifierFactory factory = new CosIdIdentifierFactory(idGeneratorProvider);
// 同时指定生成器：
IdentifierFactory named = new CosIdIdentifierFactory("event", idGeneratorProvider);
```
