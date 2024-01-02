# CosId-Redis 模块

[cosid-spring-redis](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-spring-redis) 使用**Redis**作为**雪花算法**(`SnowflakeId`) 的 机器号分配器 (`MachineIdDistributor`) 、**号段算法**(`SegmentId`)的号段分发器 (`IdSegmentDistributor`)。

## 安装

::: code-group
```kotlin [Gradle(Kotlin)]
    val cosidVersion = "latestVersion"
    implementation("me.ahoo.cosid:cosid-spring-redis:${cosidVersion}")
```
```xml [Maven]
    <dependencies>
        <dependency>
            <groupId>me.ahoo.cosid</groupId>
            <artifactId>cosid-spring-redis</artifactId>
            <version>${cosid.version}</version>
        </dependency>
    </dependencies>
```
:::

## SpringRedisIdSegmentDistributor

<p align="center" >
  <img src="../../public/assets/design/SegmentId.png" alt="SegmentId"/>
</p>

使用**Redis**作为号段算法（`SegmentId`）的号段分发器。

## SpringRedisMachineIdDistributor

<p align="center" >
  <img src="../../public/assets/design/MachineIdDistributor.png" alt="SegmentId"/>
</p>

<p align="center">
  <img src="../../public/assets/design/Machine-Id-Safe-Guard.png" alt="Machine Id Safe Guard"/>
</p>
