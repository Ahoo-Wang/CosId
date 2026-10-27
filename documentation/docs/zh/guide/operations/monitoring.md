# 指标与健康检查

当 classpath 上有 Spring Boot Actuator 与 Micrometer 时，starter 会注册一个指标 listener、一个健康检查
和一个端点。不使用 Spring 时，实现 `GeneratorListener`（通过 `RuntimeOptions` 传入），并读取
`runtime.describe()` 与 `runtime.slot()`。

## 指标

所有指标都以 `cosid.*` 命名（共 19 个）。它们来自 listener 事件，因此热路径从不调用它们；只有
`cosid.generator.generated` 对 id 计数，并且仅在 `cosid.metrics.generated: true` 时开启。

| 指标 | 类型 | 标签 | 4.x 名称 |
|---|---|---|---|
| `cosid.segment.reserve` | timer | `name`、`key` | `segment.fetch` |
| `cosid.segment.refill.failures` | counter | `name`、`key`、`error` | `segment.prefetch.failures` |
| `cosid.segment.batch.resizes` | counter | `name`、`direction`（`up`、`down`） | `segment.prefetch.expansions` |
| `cosid.segment.batch` | gauge | `name` | `segment.prefetch.distance` |
| `cosid.segment.held` | gauge | `name` | `segment.remaining` |
| `cosid.sequence.exhausted` | counter | `name` | `sequence.exhausted.wait` |
| `cosid.clock.backwards` | summary（ms） | `name`、`action`（`borrow`、`wait`、`fail`） | `clock.backwards`、`clock.sync.wait` |
| `cosid.machine.window.exceeded` | counter | `name` | |
| `cosid.machine.lease.acquired` | counter | `namespace`、`reason`（`idempotent`、`preferred`、`sticky`、`lowest`） | `machine.lease.acquired` |
| `cosid.machine.lease.renewed` | counter | `namespace` | |
| `cosid.machine.lease.released` | counter | `namespace` | |
| `cosid.machine.lease.renew.failures` | counter | `namespace` | `machine.lease.renew.failures` |
| `cosid.machine.lease.lost` | counter | `namespace` | `machine.lease.lost` |
| `cosid.machine.lease.remaining` | time gauge | `namespace` | `machine.lease.remaining` |
| `cosid.machine.window.remaining` | time gauge | `namespace` | |
| `cosid.machine.clock.skew` | time gauge | `namespace` | `machine.clock.skew` |
| `cosid.machine.owner.duplicates` | counter | `namespace` | |
| `cosid.store.timeouts` | counter | `store`、`operation` | `call.timeouts` |
| `cosid.generator.generated` | function counter | `name` | `generator.generated` |

`cosid.generator.generated` 统计运行时交出的每个视图，包括 `IdFiller`。cosid-server 另外增加
`cosid.server.call.rejections{reason}`。

## 健康检查

健康检查 `cosId`（`/actuator/health/cosId`；开关 `management.health.cosid.enabled`）：

- 当槽位租约已丢失且未能重新获取，或某个时间型生成器的槽位绑定不再有效时为 `DOWN`；否则为 `UP`。
  没有时间型生成器时不存在槽位，状态为 `UP`。
- 详情：`slot`、`owner`、`fence`、`expiresAt` 与 `expiresInMillis`、`ceiling` 与 `windowRemainingMillis`、
  每个生成器的 `slotValid`、`lost`、`renewFailures`（连续次数）以及最近一次续约错误。

```json
{"status":"UP","details":{"slot":0,"owner":"app-7d9f:8080","fence":1,"expiresAt":1793098633174,
 "expiresInMillis":28124,"ceiling":1793098634174,"windowRemainingMillis":29124,
 "slotValid":{"default":true},"lost":false,"renewFailures":0}}
```

## `cosid` 端点

| 请求 | 响应 |
|---|---|
| `GET /actuator/cosid` | `describe()`：每个生成器的策略、存储与详情 |
| `GET /actuator/cosid/{name}` | 单个生成器 |
| `GET /actuator/cosid/{name}/long` | 一个新 id（`snowflake`、`segment`） |
| `GET /actuator/cosid/{name}/string` | 一个新的文本 id（所有策略） |

用 `management.endpoints.web.exposure.include: health,cosid` 暴露它。它取代了 4.x 的 `cosid`、
`cosidGenerator` 与 `cosidStringGenerator` 端点。

## 自行监听

```java
public interface GeneratorListener {
    default void onRangeReserved(String generator, String key, Range range, Duration latency) {}
    default void onRefillFailed(String generator, String key, Throwable error) {}
    default void onBatchResized(String generator, long oldSize, long newSize) {}
    default void onSequenceExhausted(String generator) {}
    default void onClockRegression(String generator, long backwardsMillis, String action) {}
    default void onWindowExceeded(String generator) {}
    default void onSlotAcquired(SlotLease lease, String reason) {}
    default void onSlotRenewed(SlotLease lease) {}
    default void onSlotRenewFailed(SlotLease lease, Throwable error) {}
    default void onSlotLost(SlotLease lease) {}
    default void onSlotReleased(SlotLease lease) {}
    default void onClockSkew(String namespace, long skewMillis) {}
    default void onDuplicateOwnerKey(String namespace, String ownerKey) {}
    default void onStoreTimeout(String store, String operation) {}
}
```

在 Spring 中，`GeneratorListener` Bean 与指标 listener 一同接收事件。
