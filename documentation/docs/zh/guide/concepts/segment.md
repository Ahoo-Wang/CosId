# 号段与周期号段

号段生成器一步原子地从存储预留一个区间 `[start, end)`，然后在本地发放其中的值，每个值一次原子加。
ID 在单个实例内稠密且严格递增，每个区间只访问存储一次。

## 契约

```java
public record Range(long start, long end) {}

public interface RangeStore extends AutoCloseable {
    /** Atomically reserves [start, start + size); a new key starts at initialOffset. */
    Range reserve(String key, long size, long initialOffset);
    /** Optional: seal the key; every later reserve fails, the value is kept. */
    default boolean seal(String key) { return false; }
}
```

每个存储都通过同一套契约测试：

| # | 不变式 |
|---|---|
| S1 | 同一 key 的区间互不重叠，并按完成顺序严格递增 |
| S2 | 新 key 的第一个区间从 `initialOffset` 开始；并发的首次预留中只有一个创建它 |
| S3 | 返回的区间就是事实：客户端从不推断边界（4.x 推断 `maxId - step`） |
| S4 | 允许间隙（超时、崩溃、丢弃的区间），绝不重复（D19） |
| S5 | 值位于 `[0, Long.MAX_VALUE)`：超出后抛出 `RangeExhaustedException`，绝不回绕 |
| S6 | 已封存的 key 绝不会被重建；运行时代码从不删除 key |

key 为 `{namespace}/{generator}`，周期号段为 `{namespace}/{generator}/{period}`。

## SegmentGenerator 及其批量策略

```java
public record BatchPolicy(long step,                 // default 100: the smallest reservation
                          long initialOffset,        // default 1, as in 4.x
                          Duration lookahead,        // default 1s: how long the ids at hand should last; 0 = no prefetch
                          long maxBatch,             // default max(step × 1000, 2^20), rounded to step
                          Duration reserveTimeout,   // default 3s: how long a caller waits for a refill
                          Optional<Duration> rangeTtl) {}  // default none: drop a range unused this long
```

- **按时间预算确定大小。** 一次预留为 `min(maxBatch, roundUp(rate × lookahead, step))`，至少为 `step`。繁忙的生成器取更大的区间，
  空闲的生成器取较小的区间，重启永远不会浪费一大块。4.x 的 `safeDistance` / `maxPrefetchDistance` / 预取周期都由这一个数取代。
- **热路径只读一个 volatile 字段并做一次原子加。** 不加锁，也不分配对象。只有切换到下一个区间时才加锁，且该锁从不覆盖 I/O。
- **同一时刻只有一个补充在进行。** 预留在补充执行器上运行（默认使用虚拟线程）。发现没有可用 ID 的调用方会等待这一次预留，
  最多 `reserveTimeout`，之后得到 `RangeUnavailableException`。如果执行器尚未取走该任务，调用方会自己执行它，
  因此存储的 `timeout` 不得超过 `reserveTimeout`（启动时会检查）。
- **失败时退避。** 补充失败后，在重试时间之前调用方快速失败（100ms 起翻倍，最多 5s）。不会出现重试风暴。每次失败都会触发 `onRefillFailed`。
- **超时的预留不会被取消。** 它的区间到达后照常使用；超时的只是调用方的等待。
- `lookahead: 0` 只在区间用完时才预留。再配合 `step: 1` 即与 4.x 的 `DefaultSegmentId` 一致。
- `range-ttl` 让多个实例保持"趋势递增"：持有超过该时长的区间会被丢弃（即 4.x 的号段 ttl）。只有设置了它，热路径才会检查过期。

## 周期号段

周期号段每年、每月或每天重新开始序列，用于 `20261027-000042` 这样的单据号（即 4.x 的分组号段）。

```java
public final class PeriodicSegmentGenerator implements GroupedIdGenerator, AutoCloseable {
    public GroupedId generate();          // GroupedId(period, value): ("20261027", 42)
}
public record PeriodicPolicy(Duration prewarm,             // default 1m
                             Optional<Duration> sealAfter) {} // default none
```

- `Period.YEAR`、`YEAR_MONTH`、`YEAR_MONTH_DAY` 分别给出 key `2026`、`202610`、`20261027`；**时区显式指定**（4.x 使用服务器的默认时区）。
- 它最多同时持有两个号段生成器：当前周期的，以及从周期结束前 `prewarm` 起的下一周期的。
  因此跨越午夜时热路径无需访问存储，4.x 在周期切换时的停顿不复存在。
- `seal-after` 在周期结束后经过该时长时封存过去周期的 key（请设为一天或更长）。时钟滞后、仍在请求旧周期的实例会得到
  `RangeSealedException`：宁可失败也不重复（S6）。
- 运行时从不删除 key（D23）。旧周期通过 `cosid-migration purge --before <period>` 离线移除。
- 文本形式由编解码器列表决定：第一项必须是 `date-prefix`（周期）；其余各项编码值。

```yaml
cosid:
  generators:
    bill:
      strategy: periodic-segment
      store: db
      period: YEAR_MONTH_DAY
      zone: Asia/Shanghai
      codec:
        - date-prefix: { delimiter: "-" }
        - radix: { radix: 10, width: 6, pad-start: true }   # 20261027-000042
```

用 `runtime.get("bill", GroupedCodec.class).encode(id)` 读取文本，或者让 `@CosId` 填充一个 `String` 字段。
