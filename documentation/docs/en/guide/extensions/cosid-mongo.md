# CosId-Mongo 模块

[cosid-mongo](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-mongo) 提供 **MongoDB** 的支持。实现了：

- `MachineIdDistributor`：作为**雪花算法**(`SnowflakeId`)的机器号分配器 (`MachineIdDistributor`)。
- `IdSegmentDistributor`：作为**号段算法**(`SegmentId`)的号段分发器 (`IdSegmentDistributor`)。

## 安装

::: code-group
```kotlin [Gradle(Kotlin)]
    val cosidVersion = "latestVersion"
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("me.ahoo.cosid:cosid-mongo:${cosidVersion}")
```
```xml [Maven]
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-mongodb</artifactId>
            <version>${springboot.version}</version>
        </dependency>
        <dependency>
            <groupId>me.ahoo.cosid</groupId>
            <artifactId>cosid-mongo</artifactId>
            <version>${cosid.version}</version>
        </dependency>
    </dependencies>
```
:::

## 配置案例

```yaml {4,10,14}
spring:
  data:
    mongodb:
      uri:  # Mongo 分发器直接依赖 spring-data-mongodb，这样可以省去额外的配置。
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true # 可选，当需要使用雪花算法时，需要设置为 true
    distributor:
      type: mongo
  segment:
    enabled: true # 可选，当需要使用号段算法时，需要设置为 true
    distributor:
      type: mongo
```