# 04 号段:区间预留与本地发放

## 1. 契约

```java
public record Range(long start, long end) {}           // [start, end),end > start

public interface RangeStore extends AutoCloseable {
    /** 原子预留 [start, start + size);key 不存在时以 initialOffset 创建(第一个区间从 initialOffset 开始)。key 已封存时抛 RangeSealedException。 */
    Range reserve(String key, long size, long initialOffset);
    /** 可选:封存 key。封存后 reserve 永远失败,key 的值保留。不支持时返回 false。 */
    default boolean seal(String key) { return false; }
}
```

不变式(TCK 验证):

| # | 不变式 |
|---|---|
| S1 | 同一 key 的所有 `reserve` 结果两两不相交,且按完成顺序严格递增 |
| S2 | key 不存在时,第一个区间的 `start == initialOffset`;并发首次创建只有一个生效 |
| S3 | 返回值即权威,客户端从不推断区间边界 |
| S4 | 允许间隙(超时、进程退出、区间过期丢弃),绝不重复(D19) |
| S5 | 值域 `[0, Long.MAX_VALUE)`;溢出时抛出 `RangeExhaustedException`,不回绕 |
| S6 | 已封存的 key 永远不会被重建,运行时代码中不存在物理删除 key 的路径 |

key 命名:`{namespace}/{generator}`;周期号段为 `{namespace}/{generator}/{period}`。

## 2. SegmentGenerator

```java
public final class SegmentGenerator implements IdGenerator, AutoCloseable {
    public SegmentGenerator(String name, String key, RangeStore store, BatchPolicy policy,
                            Executor refillExecutor, TimeSource time, GeneratorListener listener);
    public long generate();
    public void close();
}

public record BatchPolicy(long step,              // 默认 100;单次预留的最小规模
                          long initialOffset,     // 默认 1(不发 0,与 4.x 一致;IdFiller 把 0 视为未设置)
                          Duration lookahead,     // 默认 1s;手里的余量希望撑多久;ZERO 表示不预取(等价 4.x DefaultSegmentId)
                          long maxBatch,          // 默认 step × 1000;必须是 step 的整数倍
                          Duration reserveTimeout,// 默认 3s;调用方同步等待补充的上限
                          Optional<Duration> rangeTtl) {}  // 默认不设;对应 4.x segment ttl:区间取得后超过该时长未用完则丢弃
```

- 预留规模 = `min(maxBatch, roundUpToStep(速率估计 × lookahead))`,且不小于 `step`。按**时间预算**自适应,重启后不会一次烧掉大量号段。
- `rangeTtl` 让多实例下的 ID 保持"趋势递增"(4.x 的 ttl 能力)。只有设置了它才启用带过期检查的游标实现,未设置时热路径没有额外开销。

### 2.1 内部结构

```
current : volatile Cursor           // Cursor = (AtomicLong next, long end[, long expiresAtNanos])
ready   : ArrayDeque<Range>         // 受 switchLock 保护,只在切换区间时访问
inflight: CompletableFuture<Range>  // 单飞:任意时刻至多一个 reserve 在途
CLOSED  : Cursor(next = 0, end = Long.MIN_VALUE)   // 终止哨兵
```

热路径,**只读一次 `current`**:

```
c = current
v = c.next.getAndIncrement()
if v < c.end: return v                             // 一次原子加,无锁,无分配
return slowPath(c)
```

慢路径(只在区间用尽、过期或关闭时进入):

```
switchLock.lock()
  if current == CLOSED: unlock; throw IllegalStateException
  if current != c: unlock; 重试热路径                // 已被其他线程切换
  if ready 非空: current = Cursor(ready.poll())
  maybeRefill()                                    // 余量低于 lookahead 预算时发起单飞补充
  f = (ready 为空 且 current 已用尽) ? ensureInflight() : null
switchLock.unlock()
if f != null: 在锁外等待 f,最多 reserveTimeout
              失败或超时 → 抛出 RangeUnavailableException(cause)
重试热路径
```

- **锁从不包住 I/O**:`reserve` 在 `refillExecutor` 上执行,完成回调中短暂加锁把区间放入 `ready`。
- 补充失败由慢路径驱动重试:失败后记录 `retryAfter`(100ms 起倍增,上限 5s),在此之前慢路径不会再次发起补充,直接抛出 `RangeUnavailableException`,避免重试风暴。
  每次失败都会通知 `listener.onRefillFailed`。
- 超时的 `reserve` 不取消已提交的区间:它的结果到达后照常放入 `ready`;只有调用方的等待会超时。
- `refillExecutor` 默认是虚拟线程执行器,由 `CosIdRuntime` 创建并关闭。
- 等待者接手:没有余量的调用方若发现在途预留尚未被执行器取走,会在锁外亲自执行它(仍是单飞)。亲自执行的调用方只受存储超时约束,
  因此装配时要求存储 `timeout ≤ reserveTimeout`;执行器拒绝任务时调用方仍可自行预留。
- 慢路径先 `tryLock`,拿不到时短暂自旋等待其他线程完成切换;切换本身只在 `switchLock` 内进行。
- 就绪队列按 lookahead 预算补充,至少一批、至多 64 批。
- `close()`:`current = CLOSED`,取消在途补充,丢弃 `ready`(S4 允许)。此后热路径必然进入慢路径并抛出 `IllegalStateException`,热路径不需要额外检查。

## 3. 周期号段(D5)

```java
public interface Period {                        // 预设:YEAR、YEAR_MONTH、YEAR_MONTH_DAY,可自定义
    String keyOf(Instant instant, ZoneId zone);  // "2026"、"202610"、"20261026"
    Instant endOf(String key, ZoneId zone);
}

public final class PeriodicSegmentGenerator implements AutoCloseable {
    public PeriodicSegmentGenerator(String name, String key, Period period, ZoneId zone, RangeStore store,
                                    BatchPolicy policy, PeriodicPolicy periodic, Executor refillExecutor,
                                    TimeSource time, GeneratorListener listener);
    public GroupedId generate();                 // GroupedId(period, value)
}

public record PeriodicPolicy(Duration prewarm,   // 默认 1min:距期末不足该时长时预热下一期
                             Optional<Duration> sealAfter) // 默认 empty = 不封存;设置后,期末 + sealAfter 时封存旧期 key(建议 ≥ 1 天)
{}

public record GroupedId(String period, long value) {}

public interface GroupedCodec {
    String encode(GroupedId id);
    GroupedId decode(String text);
    static GroupedCodec datePrefix(String delimiter, IdCodec valueCodec);   // 例如 "20261026" + Radix.of(10, 6, true)
}
```

- 内部最多持有两个 `SegmentGenerator`:当前期与下一期,存储 key 为 `{key}/{period}`。距期末不足 `prewarm` 时创建并预热下一期,跨期时热路径不需要同步访问存储。
- 期满后关闭旧期生成器。封存旧期 key 只在 `期末 + sealAfter` 之后进行。时钟落后的实例若仍访问已封存的旧期,会得到 `RangeSealedException`,即宁可失败也不重号(S6)。
  物理清理只能通过运维命令 `cosid-migration purge --before <period>` 离线进行。
- 区间与游标完全不感知时间,周期逻辑只存在于本类。
- 4.x 的 `group-prefix`(前缀放在业务前缀前或后、分隔符)由 `GroupedCodec` 的组合表达。

## 4. 删除项

`IdSegmentDistributor`(及其 12 个方法)、`AbstractIdSegmentDistributor`、`IdSegmentDistributorDefinition/Factory`、
`IdSegment`、`DefaultIdSegment`、`MergedIdSegment`、`IdSegmentChain`、`DefaultSegmentId`、`SegmentChainId`、
`PrefetchWorker*`、`AffinityJob`、`grouped/*`、`StringSegmentId`、`CountedSegmentId`、`NextIdSegmentExpiredException`。
