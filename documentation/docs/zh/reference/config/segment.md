# SegmentId 配置

> `me.ahoo.cosid.spring.boot.starter.segment.SegmentIdProperties`，前缀 `cosid.segment`

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enabled` | `boolean` | `false` | 注册号段生成器 |
| `mode` | `Mode` | `chain` | `chain`（`SegmentChainId`）或 `segment`（`DefaultSegmentId`） |
| `ttl` | `long` | 永不过期 | 号段有效期，单位为秒 |
| `distributor` | `Distributor` | | 号段存储 |
| `chain` | `Chain` | | 号段链设置 |
| `share` | `IdDefinition` | | 共享生成器，`__share__SegmentId` |
| `share.enabled` | `boolean` | `true` | 注册共享生成器 |
| `provider` | `Map<String, IdDefinition>` | 空 | 命名生成器，`{name}SegmentId` |

## 分配器

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `distributor.type` | `String` | `redis` | `redis`、`jdbc`、`mongo`、`zookeeper`、`proxy`，或应用中某个 `CosIdStoreProvider` 的 `getType()`。只比较字母和数字、忽略大小写 |
| `distributor.redis.timeout` | `Duration` | `1s` | `redis`：每条命令；`0` 表示不限制 |
| `distributor.jdbc.enable-auto-init-cosid-table` | `boolean` | `false` | `jdbc`：启动时创建 `cosid` 表 |
| `distributor.jdbc.enable-auto-init-id-segment` | `boolean` | `true` | `jdbc`：启动时插入缺失的生成器行 |
| `distributor.jdbc.init-cosid-table-sql` | `String` | MySQL DDL | `jdbc`：建表语句 |
| `distributor.jdbc.init-id-segment-sql` | `String` | MySQL | `jdbc`：插入生成器行 |
| `distributor.jdbc.increment-max-id-sql` | `String` | MySQL | `jdbc`：给计数器加上步长 |
| `distributor.jdbc.fetch-max-id-sql` | `String` | MySQL | `jdbc`：读取计数器 |
| `distributor.jdbc.query-timeout` | `Duration` | `5s` | `jdbc`：每条语句；`0` 表示不限制 |
| `distributor.mongo.database` | `String` | `cosid_db` | `mongo`：`cosid` 集合所在的数据库 |
| `distributor.mongo.timeout` | `Duration` | `5s` | `mongo`：每次操作（驱动的 `timeoutMS`）；`0` 表示不限制 |
| `distributor.zookeeper.timeout` | `Duration` | `5s` | `zookeeper`：每次 `nextMaxId`；`0` 表示不限制 |

语句随数据库而定：见 [JDBC：数据库](../../guide/backends/jdbc.md#数据库)。`cosid.segment.distributor.provider` 已在 4.0 移除：请改为把 `distributor.type` 设置为 Provider 的类型。

## 号段链

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `chain.safe-distance` | `int` | `2` | 预取任务在正在使用的号段之后预先准备的号段数 |
| `chain.max-prefetch-distance` | `int` | `10000` | 号段链饥饿时单次预取最多申请的号段数；不小于 `safe-distance` |
| `chain.prefetch-timeout` | `Duration` | `0` | 每次后台预取的时长上限；`0` 表示不限制 |
| `chain.prefetch-worker.prefetch-period` | `Duration` | `1s` | 预取任务补足号段链的周期 |
| `chain.prefetch-worker.core-pool-size` | `int` | 可用处理器数 | 预取线程数 |
| `chain.prefetch-worker.shutdown-hook` | `boolean` | `true` | 随 JVM 关闭预取线程 |

## IdDefinition

`share` 和每个 `provider.{name}` 的属性：

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `namespace` | `String` | `cosid.namespace` | 该生成器计数器所在的命名空间 |
| `mode` | `Mode` | `cosid.segment.mode` | `chain` 或 `segment` |
| `offset` | `long` | `0` | 计数器尚不存在时的初始值 |
| `step` | `long` | `10` | 每个号段的 ID 数量 |
| `ttl` | `Long` | `cosid.segment.ttl` | 号段有效期，单位为秒 |
| `chain` | `Chain` | `cosid.segment.chain` | 该生成器的号段链设置：`safe-distance`、`max-prefetch-distance`、`prefetch-timeout` |
| `group.by` | `GroupBy` | `never` | `year`、`year_month`、`year_month_day` 或 `never`：每个分组重新计数 |
| `group.pattern` | `String` | 无 | 分组键的日期格式，例如 `yyyyMMdd` |
| `converter` | `IdConverterDefinition` | `radix` | `generateAsString()` 的格式，见 [ID 转换器](./basic.md#id-转换器) |

生成器的 `chain` 会整体替换 `cosid.segment.chain`。预取线程是共享的，因此请在 `cosid.segment.chain` 上设置 `prefetch-worker`。

## 示例

```yaml
cosid:
  namespace: ${spring.application.name}
  segment:
    enabled: true
    mode: chain
    chain:
      safe-distance: 4
      prefetch-worker:
        core-pool-size: 2
        prefetch-period: 1s
    distributor:
      type: redis
    share:
      step: 100
      converter:
        prefix: cosid_
        type: radix
        radix:
          char-size: 6
          pad-start: true
    provider:
      order:
        offset: 10000
        step: 100
      order_no:
        group:
          by: year_month_day
          pattern: yyyyMMdd
        converter:
          type: to_string
          prefix: BIZ-
          group-prefix:
            enabled: true
          to-string:
            pad-start: true
            char-size: 8
```
