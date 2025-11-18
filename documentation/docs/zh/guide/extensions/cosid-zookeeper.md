# CosId-ZooKeeper 模块

[cosid-zookeeper](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-zookeeper) 模块提供 *ZooKeeper* 的支持。实现了：

- `MachineIdDistributor`：作为**雪花算法**(`SnowflakeId`)的机器号分配器 (`MachineIdDistributor`)。
- `IdSegmentDistributor`：作为**号段算法**(`SegmentId`)的号段分发器 (`IdSegmentDistributor`)。

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

## 配置

- 配置类：[CosIdZookeeperProperties](https://github.com/Ahoo-Wang/CosId/blob/main/cosid-spring-boot-starter/src/main/java/me/ahoo/cosid/spring/boot/starter/zookeeper/CosIdZookeeperProperties.java)
- 前缀：`cosid.zookeeper.`

| 名称                         | 数据类型       | 说明              | 默认值                      |
|----------------------------|------------|-----------------|--------------------------|
| enabled                    | `boolean`  | 是否开启*ZooKeeper* | true                     |
| connect-string             | `String`   | 链接字符串           | `localhost:2181`         |
| block-until-connected-wait | `Duration` | 阻塞直到客户端已连接等待时间  | `Duration.ofSeconds(10)` |
| session-timeout            | `Duration` | 会话超时时间          | `Duration.ofSeconds(60`  |
| connection-timeout         | `Duration` | 连接超时时间          | `Duration.ofSeconds(15)` |
| retry                      | `Retry`    | 重试策略配置          |                          |

### Retry (`ExponentialBackoffRetry`) 配置

| 名称              | 数据类型  | 说明                | 默认值   |
|-----------------|-------|-------------------|-------|
| baseSleepTimeMs | `int` | 重试之间等待的初始时间量 （毫秒） | `100` |
| maxRetries      | `int` | 最大重试次数            | `5`   |
| maxSleepMs      | `int` | 每次重试时的最大睡眠时间（毫秒）  | `500` |

### 配置案例

[CosId-Example-Zookeeper](https://github.com/Ahoo-Wang/CosId/tree/main/examples/cosid-example-zookeeper)

```yaml {2-8,11,14}
cosid:
  zookeeper:
    connect-string: localhost:2181
    retry:
      base-sleep-time-ms: 100
      max-retries: 5
      max-sleep-ms: 500
    block-until-connected-wait: 10s
  segment:
    distributor:
      type: zookeeper
  machine:
    distributor:
      type: zookeeper
```
