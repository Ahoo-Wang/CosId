# MongoDB

[`cosid-mongo`](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-mongo) 通过应用的同步 `MongoClient` 把号段计数器和机器号租约保存在 MongoDB 中：

- `MongoIdSegmentDistributor`：号段计数器，每个生成器在 `cosid` 集合中占一个文档。
- `MongoMachineLeaseStore`：机器号租约，每个机器号在 `cosid_machine` 集合中占一个文档。租约时间取自服务器的 `localTime`（`hello` 命令）。

集合不存在时会在启动时创建。

::: warning 仅支持同步驱动
CosId 需要 MongoDB 4.2 或更高版本：获取号段使用一条更新管道，并采用服务端时钟（`$$NOW`）。启动时 Starter 会确保机器号集合及其两个复合索引 `{namespace: 1, machineId: 1}` 和 `{namespace: 1, instanceId: 1}` 存在，旧版本创建的集合也会补建。已有相同键但名称不同的索引（例如 DBA 创建的）会保留并以 INFO 记录；其他失败只以 WARN 记录一次，没有索引也能工作，只是更慢。旧版本的哈希索引 `machineId_hashed`、`instanceId_hashed` 不再使用但会保留，因为旧节点会重新创建它们：所有节点都升级到 4.1 后，可在 `cosid.machine.distributor.mongo.database` 所指的数据库中手动删除：

```javascript
db.cosid_machine.dropIndex("machineId_hashed")
db.cosid_machine.dropIndex("instanceId_hashed")
```

租约查询使用复合索引这一点，已在单机和副本集上用 `explain` 验证；分片集群中各分片独立生成执行计划，未做验证。

响应式相关类（`me.ahoo.cosid.mongo.reactive`）已在 4.0 移除。请使用同步驱动 `org.mongodb:mongodb-driver-sync`，`spring-boot-starter-data-mongodb` 已包含它。classpath 中只有响应式驱动时，Starter 会在启动时失败。
:::

## 安装

::: code-group
```kotlin [Gradle (Kotlin)]
dependencies {
    implementation(platform("me.ahoo.cosid:cosid-bom:$cosidVersion"))
    implementation("me.ahoo.cosid:cosid-spring-boot-starter")
    implementation("me.ahoo.cosid:cosid-mongo")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
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
        <artifactId>cosid-mongo</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-mongodb</artifactId>
    </dependency>
</dependencies>
```
:::

## 配置

```yaml
spring:
  mongodb:
    uri: mongodb://localhost:27017
cosid:
  namespace: ${spring.application.name}
  machine:
    enabled: true # Snowflake ID 和 CosIdGenerator 需要
    distributor:
      type: mongo
  segment:
    enabled: true # 号段 ID 需要
    distributor:
      type: mongo
```

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `cosid.segment.distributor.mongo.database` | `cosid_db` | `cosid` 集合所在的数据库 |
| `cosid.segment.distributor.mongo.timeout` | `5s` | 每次号段操作（驱动的 `timeoutMS`）；`0` 表示不限制 |
| `cosid.machine.distributor.mongo.database` | `cosid_db` | `cosid_machine` 集合所在的数据库 |
| `cosid.machine.distributor.mongo.timeout` | `5s` | 每次机器号操作（驱动的 `timeoutMS`）；必须为正数 |

请开启 MongoDB 日志（journaling，默认开启），并在副本集上使用 `majority` 写关注，确保已确认的计数器更新在故障切换后不会丢失。
