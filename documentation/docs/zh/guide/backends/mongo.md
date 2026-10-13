# MongoDB

[`cosid-mongo`](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-mongo) 通过应用的同步 `MongoClient` 把号段计数器和机器号租约保存在 MongoDB 中：

- `MongoIdSegmentDistributor`：号段计数器，每个生成器在 `cosid` 集合中占一个文档。
- `MongoMachineLeaseStore`：机器号租约，每个机器号在 `cosid_machine` 集合中占一个文档。租约时间取自服务器的 `localTime`（`hello` 命令）。

集合不存在时会在启动时创建。

::: warning 仅支持同步驱动
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
| `cosid.segment.distributor.mongo.write-concern`、`cosid.machine.distributor.mongo.write-concern` | 连接串的设置 | CosId 写入的写关注：`majority`、`w1`、`w2`、`w3`、`acknowledged`、`journaled` 或成员数 |

请开启 MongoDB 日志（journaling，默认开启），并在副本集上使用 `majority` 写关注，确保已确认的计数器更新或机器号申领在故障切换后不会丢失：只被少数成员确认的写入，在没有该写入的成员成为主节点时会被回滚，其中的 ID 会被再次发放。`write-concern` 只为 CosId 设置写关注；未设置时使用连接串的设置（`w=majority`），或服务器的默认值（MongoDB 5.0 起为 `majority`）。在副本集上，启动时如果写关注弱于 `majority`，会以 WARN 记录。
