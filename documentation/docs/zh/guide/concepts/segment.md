# SegmentId 与 SegmentChainId

号段 ID 来自存储中的一个计数器。实例不必每个 ID 都访问存储，而是一次申请 `step` 个 ID，即一个*号段*，再从内存中逐个发放。

<p align="center">
  <img src="../../../public/assets/design/SegmentId.png" alt="SegmentId"/>
</p>

## 原理

存储为命名空间中的每个生成器名称维护一个计数器，即*最大 ID*。`IdSegmentDistributor` 原子地给它加上 `step` 并返回新值，于是该实例拥有从 `maxId - step + 1` 到 `maxId` 的 ID，其他实例拿不到这些 ID。

- **有序性。** 每个实例发放递增的 ID，下一个号段总比上一个大：ID 在单实例内单调递增。不同实例同时持有不同的号段，因此跨实例的 ID 趋势递增，乱序窗口大约是 `step` 乘以实例数。只有一个实例时，ID 单调递增。
- **步长。** 即使 `step=1`，跨实例也不是严格单调递增：两个实例可能拿到相邻的 ID，却以相反的顺序写入。步长越小，ID 越接近连续，重启时丢弃的 ID 也越少（号段中未用完的部分会被跳过）；步长越大，访问存储的次数越少。
- **偏移量。** `offset` 是计数器创建时的初始值，例如用于接续已有表中的 ID。它只对尚不存在的计数器生效。
- **可用性。** 每当需要新号段时，生成就要依赖存储。号段链会预留号段，以扛过存储短暂的不可用。

## DefaultSegmentId

`DefaultSegmentId`（`cosid.segment.mode: segment`）在当前号段用完时，由调用线程获取下一个号段。该线程要等待一次存储往返，这会体现在尾延迟上：CosId 1.x 的历史 JMH 结果中 P99.99 为 46.6 µs（[方案对比](../../reference/performance/comparison.md)、[基准测试](../../reference/performance/benchmark.md)）。

## SegmentChainId

<p align="center">
  <img src="../../../public/assets/design/SegmentChainId.png" alt="SegmentChainId"/>
</p>

`SegmentChainId`（`cosid.segment.mode: chain`，默认）在正在使用的号段之后维护一条号段链。后台的*预取任务*（PrefetchWorker）让链的长度至少保持 `safe-distance` 个号段（默认 2），因此调用线程几乎从不等待存储：在同一批历史结果中每秒约 1.27 亿个 ID，P99.99 为 0.208 µs，接近 `AtomicLong`。

号段链会根据需求自适应：

- **定时预取。** 预取任务每隔 `prefetch-worker.prefetch-period`（1 秒）把链补足到安全距离。
- **饥饿扩容。** 调用线程发现没有可用号段时，会自己获取一个并唤醒预取任务；预取任务把预取距离翻倍（最多 `max-prefetch-distance`，10,000 个号段），并在一次存储调用中申请这么多号段。需求下降后，距离再减半，直到回到安全距离。

因此号段链适合较小的 `step`：流量需要时预取距离会增长，不需要时 ID 仍然接近连续。

`SegmentChainId` 实现了 `AutoCloseable`：`close()` 停止后台预取，之后生成仍可继续，只是不再预取。`prefetch-timeout` 限制每次后台预取的时长（默认不限制）。

### 为什么不同存储都能达到同样的峰值

基准测试中，在 `step=1000` 时 Redis 和 MySQL 的号段链都能达到每秒约 1.2 亿个 ID，尽管 Redis 的响应要快得多。原因在于饥饿扩容：预取任务不断把每次申请的号段数翻倍，只要时间足够，任何存储发放 ID 的速度都会超过线程消耗的速度，峰值就是内存计数器的速度。

`TPS(SegmentChainId) ≈ step × 扩容系数 × TPS(IdSegmentDistributor) × T ≤ TPS(AtomicLong)`

其中扩容系数是饥饿时预取距离的增长倍数（2，即翻倍），`T` 是基准测试已运行的时长。更快的存储只是更早达到峰值（`T` 更小）。

<p align="center">
  <img src="../../../public/assets/perf/Throughput-Of-SegmentChainId.png" alt="Throughput-Of-SegmentChainId"/>
</p>

## 号段有效期

`ttl`（单位为秒，默认永不过期）让号段过期：实例不再使用超过有效期的号段，跳过其中剩余的 ID 并申请新号段。分组号段会在分组结束时自动过期。

## 分组号段

分组号段在每个分组（例如每年、每月或每天）重新开始计数，可用于生成 `BIZ-20260101-00000001` 这样的业务编号：

```yaml
cosid:
  segment:
    enabled: true
    provider:
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

每个分组在存储中是独立的计数器，名称为 `{name}@{group}`（例如 `order_no@20260101`），从 `offset` 开始。`SegmentId.generateGrouped()` 返回同时包含分组和值的 `GroupedId`，`converter.asString(groupedId)` 会把分组作为前缀格式化。更多示例见[常用 ID 配方](../recipes.md)。

## 号段存储

`cosid.segment.distributor.type` 选择存储：`redis`（默认）、`jdbc`、`mongo`、`zookeeper` 或 `proxy`，也可以是应用中某个 `CosIdStoreProvider` 的类型。见[选择后端](../backends/index.md)。

## 配置

```yaml
cosid:
  namespace: ${spring.application.name}
  segment:
    enabled: true
    distributor:
      type: redis
    share:
      step: 100
    provider:
      order:
        offset: 10000
        step: 100
```

这会注册共享生成器（`__share__SegmentId`）和名为 `order` 的生成器，两者都是号段链。全部属性见 [SegmentId 配置](../../reference/config/segment.md)。
