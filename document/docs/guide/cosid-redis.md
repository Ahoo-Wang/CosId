# CosId-Redis 模块

[cosid-spring-redis](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-spring-redis) 使用**Redis**作为**雪花算法**(`SnowflakeId`) 的 机器号分配器 (`MachineIdDistributor`) 、**号段算法**(`SegmentId`)的号段分发器 (`IdSegmentDistributor`)。

## 安装

> Kotlin DSL

``` kotlin
    implementation("me.ahoo.cosid:cosid-spring-redis:${cosidVersion}")
```

## SpringRedisIdSegmentDistributor

<p align="center" >
  <img :src="$withBase('/assets/design/SegmentId.png')" alt="SegmentId"/>
</p>

使用**Redis**作为号段算法（`SegmentId`）的号段分发器。

## SpringRedisMachineIdDistributor

<p align="center" >
  <img :src="$withBase('/assets/design/RedisMachineIdDistributor.png')" alt="SegmentId"/>
</p>

<p align="center">
  <img :src="$withBase('/assets/design/Machine-Id-Safe-Guard.png')" alt="Machine Id Safe Guard"/>
</p>
