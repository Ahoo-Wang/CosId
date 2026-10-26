# 05 机器号:槽位上的时间窗口租约

## 1. 目标与核心思想

时间型 ID = `(时间, 槽位, 序列)`。只要**同一槽位上任意两次发号的时间戳区间不相交**,ID 就全局唯一。

因此,5.0 的租约不是"对槽位的独占使用权",而是**对槽位上一段时间窗口的独占使用权**:

```
持有者只能发出时间戳 t,满足  floor < t ≤ ceiling
下一任持有者的 floor ≥ 上一任可能用到的最大 ceiling
```

相继持有者的窗口首尾相接、互不重叠。**唯一性只依赖这条存储内的不等式,不依赖任何时钟的准确性、GC 停顿、网络延迟或过期判断。**
时钟只影响可用性:时钟偏差大时,持有者会更早撞到 ceiling 而暂停发号,或者新持有者需要等待更久才能越过 floor。

这修复了 4.x 中依赖"所有实例 safeGuard 相同"、"主机时钟偏差小于阈值"、"本地水位线及时写盘"的整套隐式假设。

## 2. 模型

```java
public record Owner(String key, UUID incarnation) {}
// key:逻辑身份(主机名、Pod 名),只用于粘性选槽与观测
// incarnation:每个 LeaseKeeper(即每个 CosIdRuntime)启动时生成;同一 JVM 中的多个运行时互不相同

public record SlotLease(String namespace, int slot, Owner owner,
                        long fence,        // 每次所有权变化 +1(获取、接管、释放);续约不变
                        long expiresAt,    // 存储时钟,毫秒;仅用于判断能否被他人接管
                        long floor,        // 毫秒,不含;本任持有者的时间戳必须 > floor
                        long ceiling,      // 毫秒,含;本任持有者的时间戳必须 ≤ ceiling
                        long storeNow) {}

public sealed interface AcquireResult {
    record Acquired(SlotLease lease) implements AcquireResult {}
    record Exhausted(long earliestExpiresAt) implements AcquireResult {}
}

public interface SlotLeaseStore extends AutoCloseable {
    AcquireResult acquire(String namespace, Owner owner, int limit, Duration ttl, Duration tolerance, OptionalInt preferredSlot);
    Optional<SlotLease> renew(SlotLease lease, Duration ttl, Duration tolerance);
    boolean release(SlotLease lease, long lastIssued);
}
```

- `tolerance`(D20,默认 1s):允许持有者时间戳超出存储过期时间的量,即 `ceiling = expiresAt + tolerance`。它只影响可用性,不影响唯一性。
- 每个槽位在存储中保存:`owner`、`lastOwnerKey`、`fence`、`expiresAt`、`ceiling`、`floor`(下一任的起点,见 §2.2)。

### 2.1 选槽(尽力而为的优先级 + 单槽原子 CAS)

令 `now` 为存储时钟,"可取" = `owner == null` 或 `expiresAt < now`。按以下顺序尝试,**每次尝试都是对单个槽位的原子条件更新**;
顺序本身不要求原子,并发下拿到的不是最优槽位也不影响正确性:

1. 已被同一 `(key, incarnation)` 持有的槽位:幂等重试,原样返回(不改变 fence)。
2. `preferredSlot`(若可取)。
3. `lastOwnerKey == key` 的可取槽位(粘性,取代 4.x 的 "stable")。
4. 编号最小的可取槽位(`< limit`)。
5. 都没有:`Exhausted`。

同一 key 但不同 incarnation 的存活租约**绝不被接管**。新运行时只会拿到别的槽位,并发出 `onDuplicateOwnerKey` 事件。

### 2.2 状态变更(存储端)

| 操作 | 条件(CAS) | 变更 |
|---|---|---|
| acquire(取得可取槽位) | 槽位仍可取 | `fence+1`;`owner = 新`;`floor = 槽位.next_floor`;`expiresAt = now + ttl`;`ceiling = max(expiresAt + tolerance, floor)`;`next_floor = ceiling` |
| renew | `owner.incarnation` 与 `fence` 均匹配 | `expiresAt = max(expiresAt, now + ttl)`;`ceiling = max(ceiling, expiresAt + tolerance)`;`next_floor = ceiling`;fence 不变 |
| release | `owner.incarnation` 与 `fence` 均匹配 | `owner = null`;`expiresAt = now`;`next_floor = max(floor, min(lastIssued, ceiling))`;`fence+1` |

- `next_floor` 始终等于本任可能用到的最大时间戳:未释放时是 `ceiling`,释放时是精确的 `lastIssued`(可以让下一任更早开始)。
- 续约不改变 fence 且是幂等的:响应丢失后重试仍会成功,不会出现"续约已生效但客户端以为失败"的假丢失。
- `ceiling` 与 `next_floor` 只增不减。

## 3. 安全性论证

**不变式 W**:对每个槽位,第 k 任持有者的窗口 `(floor_k, ceiling_k]` 满足 `floor_{k+1} ≥ ceiling_k`(本任释放时为 `≥ lastIssued_k`)。

- acquire 时 `floor = next_floor`;在本任期间 `next_floor` 始终跟随 `ceiling`,而 ceiling 只增不减。所以下一任的 floor ≥ 本任存储中最终的 ceiling ≥ 本任客户端所知的任何 ceiling。
- 客户端只用自己收到的最新 ceiling(可能比存储中的小,这只会更保守)。
- 释放前生成器已进入终止状态(§4.3),`lastIssued` 是精确的最大值。

**客户端规则**:每次发号读取一次绑定(§4.1),取时间戳 `t`(换算为毫秒的区间 `[t_ms, t_ms + unit − 1]`):
- `t_ms ≤ floor` → 等待(首次发号与每次换绑都会如此);
- `t_ms + unit − 1 > ceiling` → 不发号,抛出 `SlotWindowExceededException`(或在 `WaitUpTo` 策略下等待续约);
- 否则在该绑定的序列器上 CAS。

由 W 与客户端规则,同一槽位上不同任期发出的时间戳区间不相交;同一任期内由序列器保证不重复。**结论不依赖任何时钟。**

**时钟与可用性**

| 情况 | 后果 | 安全性 |
|---|---|---|
| 主机时钟超前存储 > tolerance | 发号接近 ceiling 时暂停,直到续约 | 不受影响 |
| 主机时钟落后存储 | 新持有者等待越过 floor 的时间变长 | 不受影响 |
| 存储时钟跳变(failover、NTP) | 租约提前或推迟过期 | 不受影响(floor/ceiling 只增) |
| 进程 GC 停顿跨过过期 | 恢复后的时间戳超出 ceiling,拒绝发号 | 不受影响 |

## 4. 客户端组件

### 4.1 SlotBinding

```java
public interface SlotHandle {
    SlotBinding current();      // 一次 volatile 读
}

public final class SlotBinding {           // 不可变外壳 + 本绑定专属的序列器状态
    int slot(); long floor(); volatile long ceiling(); long generation();
    // 序列器的 AtomicLong 也在这里:换绑时序列器自然随之重置
}
```

- 生成器每次 `generate` 只读一次 `current()`,槽位、窗口与序列器都取自这一个对象,不会混用两次绑定。
- 续约只提高当前绑定的 `ceiling`(volatile 写);重新获取槽位则创建新绑定(`generation + 1`)。

### 4.2 LeaseKeeper

- 每个运行时一个(D18),自己生成 incarnation;所有时间型生成器共享它的 `SlotHandle`。
- 在 `ttl/3` 时续约;失败时按退避重试(100ms 起倍增,上限 `ttl/6`)。在 ceiling 之前生成器照常发号。
- 续约返回空(所有权已变):旧绑定标记失效;重新 `acquire`,成功后发布新绑定。
- `Exhausted`:启动时在 `acquireTimeout`(默认等于 `ttl`)内按 `earliestExpiresAt` 重试,超时则启动失败;运行中则持续重试,期间生成器 fail fast。
- 续约在注入的执行器上执行(默认虚拟线程)。
- 健康检查(非安全条件):用 `storeNow` 与本地墙钟估计偏差,超过 `tolerance/2` 时发出 `onClockSkew` 事件。

### 4.3 关闭顺序

1. 每个生成器把序列器状态 CAS 为终止哨兵 `CLOSED`,并从被替换的状态中取得精确的 `lastIssued`。之后任何并发 CAS 都会失败并抛出 `IllegalStateException`。
2. `release(lease, max(lastIssued))`。
3. 关闭存储与自建执行器。

### 4.4 StaticSlot(本地窗口)

`Manual` 与 `StatefulSet`(Pod 序号)场景:槽位由使用者保证唯一,不经过存储。窗口由本地文件提供,机制与租约相同:

- 文件中保存 `ceiling`。启动时 `floor = 文件中的 ceiling`(不存在时取 `notBefore`,或者 0)。
- 发号前先**预写**:`ceiling = now + ahead`(默认 10s),fsync 后才允许使用;剩余窗口小于 `ahead/2` 时异步续写。
- 崩溃加时钟回拨也不会重号:重启后的 floor 是崩溃前已预写的 ceiling。
- 不配置文件时没有回拨保护,文档明确声明。

## 5. 默认参数

| 参数 | 默认值 | 说明 |
|---|---|---|
| `ttl` | 30s | 进程崩溃后其槽位最多空置 30s |
| 续约间隔 | `ttl/3` | |
| `tolerance` | 1s | 命名空间级(D20);命名空间元数据记录 `{limit, tolerance}`,参数不一致则拒绝 |
| `bits` | 10 | `limit = 2^bits`;装配时校验 `bits ≤ layout.nodeBits`(JS 安全布局为 3,见 03) |
| `acquireTimeout` | `ttl` | |
| StaticSlot `ahead` | 10s | |

## 6. 删除项

`MachineIdDistributor` 及全部实现、`AbstractMachineIdDistributor`、`LeaseStoreMachineIdDistributor`、`MachineLeaseManager`、
`MachineLeaseStore`(8 个方法)、`Lease`、`MachineState`、`MachineIdLease`、`GuardianState`、`NamespacedInstanceId`、
`MachineIdGuarder`、`MachineStateStorage`(双文件格式)、`ClockBackwardsSynchronizer`、`MachineOperates`、"stable" 永生租约、
基于主机时钟偏差的安全校验。
