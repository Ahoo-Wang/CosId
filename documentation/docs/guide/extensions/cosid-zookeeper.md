# CosId-ZooKeeper 模块

[cosid-zookeeper](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-zookeeper) 使用**ZooKeeper**作为**雪花算法**(`SnowflakeId`)
的 机器号分配器 (`MachineIdDistributor`) 、**号段算法**(`SegmentId`)的号段分发器 (`IdSegmentDistributor`)。

## 安装

::: code-group
```kotlin [Gradle(Kotlin)]
    val cosidVersion = "latestVersion"
    implementation("me.ahoo.cosid:cosid-zookeeper:${cosidVersion}")
```
```xml [Maven]
    <dependencies>
        <dependency>
            <groupId>me.ahoo.cosid</groupId>
            <artifactId>cosid-zookeeper</artifactId>
            <version>${cosid.version}</version>
        </dependency>
    </dependencies>
```
:::

## ZookeeperIdSegmentDistributor

> 基于zookeeper的Id片段分发器。维护一个step步长id。

主要方法：获取下一个步长最大值

```java
long nextMaxId(long step);
```

## ZookeeperMachineIdDistributor
> 基于zookeeper的机器号分发器。
