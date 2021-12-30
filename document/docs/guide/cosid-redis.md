# CosId-Redis 模块

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
