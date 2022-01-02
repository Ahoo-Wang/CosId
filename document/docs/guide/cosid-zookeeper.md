# CosId-ZooKeeper 模块

[cosid-zookeeper](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-zookeeper) 使用**Redis**作为**雪花算法**(`SnowflakeId`) 的 机器号分配器 (`MachineIdDistributor`) 、**号段算法**(`SegmentId`)的号段分发器 (`IdSegmentDistributor`)。

## 安装

> Kotlin DSL

``` kotlin
    implementation("me.ahoo.cosid:cosid-zookeeper:${cosidVersion}")
```

## ZookeeperIdSegmentDistributor

## ZookeeperMachineIdDistributor
