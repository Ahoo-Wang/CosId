# 机器号槽位租约

Snowflake ID 是 `(time, slot, sequence)`。只要两个实例从不在**同一槽位上发出相同的时间戳**，它们就不会冲突。
4.x 试图通过给每个实例分配一个槽位（它的"机器号"）来保证这一点。于是它不得不相信：各时钟相差不大、每个实例使用相同的
safe-guard 时长、本地水位线及时写盘。5.0 去掉了这些假设。

## 思路：租用时间窗口，而不是槽位

5.0 中的租约是在一个槽位上发出**某个窗口 `(floor, ceiling]` 内时间戳**的独占权：

```
the holder may issue timestamp t only if   floor < t ≤ ceiling
the next holder's floor                  ≥ the largest ceiling the previous holder could have used
```

同一槽位上相继的窗口首尾相接、互不重叠。存储自己维护这条不等式，每次变更一次原子 compare-and-set；
生成器在发出每个 ID 之前都会检查它是否落在窗口内。**唯一性仅由此得出（D22）：不依赖任何时钟准确、不依赖 GC 停顿短暂、
不依赖网络快速。** 时钟只决定租约*何时*过期、他人何时可以取得该槽位。这是可用性，而不是安全性。

```mermaid
gantt
    dateFormat  X
    axisFormat  %s
    section slot 3
    holder A (floor 0, ceiling 40)   :a, 0, 40
    holder B (floor 40, ceiling 95)  :b, 40, 95
    holder C (floor 95, ...)         :c, 95, 130
```

| 发生的情况 | 影响 | 唯一性 |
|---|---|---|
| 主机时钟比存储快出超过 `tolerance` | 生成器提前到达 ceiling，等待续约 | 保持 |
| 主机时钟比存储慢 | 新持有者需要等待更久才能越过 floor | 保持 |
| 存储时钟跳变（故障切换、NTP） | 租约提前或推迟过期 | 保持：floor 与 ceiling 只增不减 |
| 进程暂停超过过期时间（GC、虚拟机冻结） | 暂停结束后其时间戳超出 ceiling：拒绝发号 | 保持 |

## 租约

```java
public record SlotLease(String namespace, int slot, Owner owner,
                        long fence,      // 每次所有权变化 +1；续约不变
                        long expiresAt,  // 存储时钟，毫秒：他人何时可以取得该槽位
                        long floor,      // 毫秒，不含：本任持有者的时间戳 > floor
                        long ceiling,    // 毫秒，含：本任持有者的时间戳 ≤ ceiling
                        long storeNow) {}

public record Owner(String key, UUID incarnation) {}
```

- `ceiling = expiresAt + tolerance`。`tolerance`（默认 1s，D20）是持有者在存储过期时间之后仍可发号的量。
  它只用于权衡可用性：tolerance 越大，能扛住的时钟偏差越大，崩溃持有者的槽位不可用的时间也越长。
- **所有者 key** 是逻辑身份（默认为 `host:port` 或 `host:pid`，也可配置）。**incarnation** 是每次运行时启动生成的 UUID。
  同一 key 但不同 incarnation 的存活租约**绝不**被接管：新进程拿到另一个槽位，并报告 `onDuplicateOwnerKey`。
  4.x 会把槽位收回，可能在长达一分钟内发出重复 ID。

### 选槽

每次尝试都是对单个槽位的一次原子条件更新；顺序只是一种偏好：

1. 该 `(key, incarnation)` 已持有的槽位：幂等重试；
2. `preferred-slot`（若空闲），例如 StatefulSet 序号；
3. 上一任所有者 key 相同的空闲槽位（"粘性"，即 4.x 的 *stable*，但没有永不过期的租约）；
4. `2^bits` 以下编号最小的空闲槽位；
5. 都没有：`Exhausted`，附带需要等待的最早过期时间。

### 存储端变更

| 操作 | 条件 | 变更 |
|---|---|---|
| acquire | 槽位空闲或已过期 | `fence + 1`；`floor = next_floor`；`expiresAt = now + ttl`；`ceiling = max(expiresAt + tolerance, floor)`；`next_floor = ceiling` |
| renew | incarnation 与 fence 均匹配 | `expiresAt = max(expiresAt, now + ttl)`；`ceiling` 与 `next_floor` 随之更新；fence 不变，因此重放的续约也会成功 |
| release | incarnation 与 fence 均匹配 | 清除 owner；`next_floor = max(floor, min(lastIssued, ceiling))`；`fence + 1` |

正常释放会把精确的最后发出时间戳交给下一任持有者，下一任可以紧接其后开始。

## LeaseKeeper

每个 `CosIdRuntime` 有一个 `LeaseKeeper`（D18），运行时内的所有时间型生成器共用它的槽位。

- 每 `ttl/3` 续约一次。续约失败时退避重试（从 100ms 开始，最多 `ttl/6`）。在到达 ceiling 之前生成器持续发号。
- 续约发现租约已丢失时，旧的绑定失效（生成器以 `SlotLostException` 快速失败），并获取一个新槽位与新窗口。
- 启动时，命名空间槽位耗尽会在 `acquireTimeout`（即 ttl）内重试。超过后启动失败。
- `storeNow` 会与本地时钟比较。偏差超过 `tolerance/2` 时触发 `onClockSkew`（只是一个指标，不是安全检查）。
- **不提供租约丢失后继续发号的开关**（P1）：在任何模型下这都可能产生重复 ID。

关闭是有序的：每个生成器先进入关闭状态并报告其精确的最后时间戳，然后用它释放槽位，最后关闭存储与执行器。

## 默认值

| 设置 | 默认值 | |
|---|---|---|
| `ttl` | 30s | 崩溃实例的槽位最多 30s（+ `tolerance`）后再次空闲 |
| 续约 | `ttl/3` | |
| `tolerance` | 1s | 按命名空间（D20）：命名空间记录 `{limit, tolerance}`，其他取值会被拒绝 |
| `bits` | 10 | 1024 个槽位；不超过布局的 node 位数 |
| 获取超时 | `ttl` | |

## 静态槽位

当部署本身已保证槽位唯一（每台主机一个固定编号、StatefulSet 序号）时，`machine.static` 跳过存储。此时窗口来自一个本地文件：

```yaml
cosid:
  machine:
    static:
      slot: ${POD_ORDINAL}
      state-file: /var/lib/cosid/state   # 在重启之间保留 ceiling
      ahead: 10s
```

- 发出第一个 ID 之前，生成器把 `ceiling = now + ahead` 写入文件并 fsync。剩余不足 `ahead/2` 时，在后台写入下一个 ceiling。
- 重启时 `floor = max(ceiling in the file, not-before)`：崩溃后再发生时钟回跳也不会重复发出 ID。
- **没有 `state-file` 时，无法防范跨重启的时钟回拨。** 生产环境请使用它。
- `not-before` 是文件尚不存在时的 floor。迁移工具会写入它（见[迁移指南](../migration/from-4x.md)中的 W）。
