# ZooKeeper

[`cosid-zookeeper`](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-zookeeper) 通过 Apache Curator 把号段计数器和机器号租约保存在 ZooKeeper 中：

- `ZookeeperIdSegmentDistributor`：号段计数器，使用 Curator 的分布式原子长整型。
- `ZookeeperMachineLeaseStore`：机器号租约，用带版本的事务申领和释放。

ZooKeeper 没有客户端可读取的服务器时钟，租约时间取自主机时钟。**请保持各主机时钟同步**（NTP）：时钟偏快的实例可能会在另一个实例的租约到期之前就认为它已过期。

只有 `type` 选择了 ZooKeeper 时，Starter 才会连接 ZooKeeper。

## 安装

::: code-group
```kotlin [Gradle (Kotlin)]
dependencies {
    implementation(platform("me.ahoo.cosid:cosid-bom:$cosidVersion"))
    implementation("me.ahoo.cosid:cosid-spring-boot-starter")
    implementation("me.ahoo.cosid:cosid-zookeeper")
}
```
```xml [Maven]
<dependencies>
    <dependency>
        <groupId>me.ahoo.cosid</groupId>
        <artifactId>cosid-spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>me.ahoo.cosid</groupId>
        <artifactId>cosid-zookeeper</artifactId>
    </dependency>
</dependencies>
```
:::

`cosid-zookeeper` 会引入 `curator-recipes`。

## 配置

```yaml
cosid:
  namespace: ${spring.application.name}
  zookeeper:
    connect-string: localhost:2181
  machine:
    enabled: true # Snowflake ID 和 CosIdGenerator 需要
    distributor:
      type: zookeeper
  segment:
    enabled: true # 号段 ID 需要
    distributor:
      type: zookeeper
```

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `cosid.zookeeper.enabled` | `true` | 选择 ZooKeeper 时创建 Curator 客户端 |
| `cosid.zookeeper.connect-string` | `localhost:2181` | ZooKeeper 服务器地址 |
| `cosid.zookeeper.session-timeout` | `60s` | 会话超时 |
| `cosid.zookeeper.connection-timeout` | `15s` | 连接超时 |
| `cosid.zookeeper.block-until-connected-wait` | `10s` | 启动时等待连接建立的时长 |
| `cosid.zookeeper.retry.base-sleep-time-ms` | `100` | `ExponentialBackoffRetry`：首次重试前的等待时间（毫秒） |
| `cosid.zookeeper.retry.max-retries` | `5` | 最大重试次数 |
| `cosid.zookeeper.retry.max-sleep-ms` | `500` | 两次重试之间的最长等待时间（毫秒） |
| `cosid.segment.distributor.zookeeper.timeout` | `5s` | 每次号段 `nextMaxId` |
| `cosid.machine.distributor.zookeeper.timeout` | `5s` | 租约续约和释放（Curator 没有单次操作超时）；必须为正数 |

应用也可以自行定义 `CuratorFramework` Bean。可运行的示例见 [CosId-Example-Zookeeper](https://github.com/Ahoo-Wang/CosId/tree/main/examples/cosid-example-zookeeper)。
