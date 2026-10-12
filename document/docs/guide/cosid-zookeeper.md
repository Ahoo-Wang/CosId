# CosId-ZooKeeper 模块

> 本文仅适用于 CosId 3.x 及更早版本，其中的部分类已在 4.0 移除。最新文档见 [cosid.ahoo.me](https://cosid.ahoo.me/zh/)，升级说明见[迁移指南](https://github.com/Ahoo-Wang/CosId/blob/main/docs/MIGRATION-4.0.md)。
> This page covers CosId 3.x and earlier; some classes it names were removed in 4.0. See [cosid.ahoo.me](https://cosid.ahoo.me/) and the [migration guide](https://github.com/Ahoo-Wang/CosId/blob/main/docs/MIGRATION-4.0.md).

[cosid-zookeeper](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-zookeeper) 使用**ZooKeeper**作为**雪花算法**(`SnowflakeId`)
的 机器号分配器 (`MachineIdDistributor`) 、**号段算法**(`SegmentId`)的号段分发器 (`IdSegmentDistributor`)。

## 安装

> Kotlin DSL

``` kotlin
    implementation("me.ahoo.cosid:cosid-zookeeper:${cosidVersion}")
```

## ZookeeperIdSegmentDistributor

> 基于zookeeper的Id片段分发器。维护一个step步长id。

主要方法：获取下一个步长最大值

```java
long nextMaxId(long step);
```

## ZookeeperMachineIdDistributor
> 基于zookeeper的机器号分发器。
