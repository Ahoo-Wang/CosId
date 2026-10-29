# 03 核心模型:生成、编码、时间型生成器

## 1. 生成与编码分离

```java
@FunctionalInterface
public interface IdGenerator { long generate(); }

public interface IdCodec {
    String encode(long id);
    long decode(String text);
    default IdCodec withPrefix(String prefix) { ... }
    default IdCodec withSuffix(String suffix) { ... }
}

public record StringIdGenerator(IdGenerator ids, IdCodec codec) {
    public String generate() { return codec.encode(ids.generate()); }
}
```

- `IdGenerator` 不携带转换器,也不继承任何接口,4.x 中"包装后编码静默改变"的缺陷在类型上不可能出现。
- 编解码实现:
  - `Radix.of(radix, width, padStart)`:一个类覆盖 base 10/36/62,取代 `ToStringIdConverter`、`Radix36IdConverter`、`Radix62IdConverter`。
    `maxValue` 用整数运算求得,`decode` 时校验溢出。
  - `Friendly.of(layout, zoneId, padStart)`:基于 `Layout.unpack`,**必须显式给出时区**。格式化使用预计算的字段宽度直接写 `char[]`,不经过 `DateTimeFormatter`。
  - `DatePrefix.of(pattern, delimiter, source)` 只描述前缀;`apply(IdCodec inner, ZoneId zone, TimeSource time, Optional<Layout> layout)` 把它套到一个 codec 上。
    `source = ID_TIME` 取 ID 自身时间(必须提供 `layout`,仅时间型);`NOW` 取注入的 `TimeSource`(与 4.x 号段行为一致)。时区必须显式给出。
  - 自定义:用户实现 `IdCodec` 并在配置中按类名或 Bean 名引用。
- 分组 ID 有自己的 `GroupedCodec`(见 [04-segment.md](04-segment.md)),不进入 `IdCodec`。

## 2. Layout:位运算的唯一归属

```java
public record Layout(Instant epoch, ChronoUnit unit, int timeBits, int nodeBits, int seqBits, long seqResetThreshold) {
    public long pack(long time, int node, long seq);
    public IdParts unpack(long id);
    public int totalBits();
    public boolean isJsSafe();                       // totalBits ≤ 53
    public static boolean isJsSafe(long id);
    public Layout withNodeBits(int nodeBits);        // 只改节点位,构造时校验总位数

    public static final Layout SNOWFLAKE_MS;   // epoch 1577203200000 ms,41/10/12(D16,沿用 4.x)
    public static final Layout SNOWFLAKE_S;    // epoch 1577203200 s,31/10/22
    public static final Layout JS_SAFE_MS;     // 41/3/9,共 53 位(沿用 4.x)
    public static final Layout JS_SAFE_S;      // 31/3/19
    public static final Layout COSID_80;       // epoch 0(Unix),44 位毫秒 + 20 位节点 + 16 位序列(沿用 4.x),只能经由 PartsCodec 输出
                                               // cosid80 的默认布局;位宽与阈值可改,epoch/unit 固定(D36,见 api-freeze.md §1)
}

public record IdParts(long time, int node, long seq) {   // time 为从 epoch 起的 unit 计数
    public Instant instant(Layout layout);
}
```

- 构造时按段校验:time 1–63、node 0–31、seq 1–62,`seqResetThreshold` 在序列范围内,`unit` 只能是 `MILLIS` 或 `SECONDS`,`SECONDS` 的 epoch 必须是整秒。
  总位数不在构造时限制(`COSID_80` 也是 `Layout`):超过 63 位的布局调用 `pack` / `generate` 会抛异常,只能用 `nextParts()`;
  `withNodeBits` 拒绝把原本 ≤ 63 位的布局扩到 63 位以上。
- 节点位与机器槽位(D18、D37):每个 machine 持有一个槽位,`limit = 2^machine.bits`;运行时有一个默认 machine,另可有命名的 machine(07 §1.2)。
  装配时校验每个时间型生成器 `layout.nodeBits ≥ 所选 machine 的 bits`,不满足即启动失败。
  `withNodeBits` 只改变节点位,不会偷偷缩减时间位或序列位。
  因此 JS 安全布局(3 位节点)要求 `machine.bits ≤ 3`,与 4.x `SafeJavaScriptSnowflakeId` 的约束相同。
- 解析:4.x 的 `SnowflakeIdStateParser`、`CosIdIdStateParser` 及其 `parse`/`parseTimestamp`/`parseMachineId`/`parseSequence`,
  由 `Layout.unpack` 加 `Friendly.decode` / `PartsCodec.decode` 取代。

## 3. TimeSource

```java
public interface TimeSource {
    long currentMillis();
    TimeSource SYSTEM = System::currentTimeMillis;
}
```

所有时间读取都经由注入的 `TimeSource`;测试使用可控的 `ManualTimeSource`。

## 4. TimeBasedGenerator

```java
public final class TimeBasedGenerator implements IdGenerator, PartsIdGenerator, AutoCloseable {
    public TimeBasedGenerator(String name, Layout layout, SlotHandle slot, TimeSource time,
                              ClockRegressionPolicy regression, GeneratorListener listener);
    public long generate();              // 仅 totalBits ≤ 63
    public IdParts nextParts();
    public Layout layout();
    public int slot();
    public long lastIssuedMillis();
    public void close();                 // 换上终止状态,返回后不会再有任何发号
}

public record ClockRegressionPolicy(Duration borrowUpTo, Duration waitUpTo) {
    public static final ClockRegressionPolicy DEFAULT = new ClockRegressionPolicy(Duration.ofMillis(5), Duration.ofSeconds(1));
    public static final ClockRegressionPolicy FAIL = new ClockRegressionPolicy(Duration.ZERO, Duration.ZERO);
}
```

- 一个类取代 4.x 的 `MillisecondSnowflakeId`、`SecondSnowflakeId`、`Radix*CosIdGenerator`、`FriendlyCosIdGenerator`,以及 `ClockSync*`、`Leased*`、`Counted*`、`String*` 全部装饰器。
  租约、时钟策略、计数都是构造参数或 listener。
- 时钟回拨:回拨量 ≤ `borrowUpTo` 时沿用上次的时间单位继续发号(借用未来);≤ `waitUpTo` 时等待;超过则抛出 `ClockBackwardsException`。

### 4.1 发号算法

生成器(`internal.TimeSequencer`)持有 `AtomicReference<Bound>`,`Bound = (SlotBinding b, AtomicLong state)`(见 [05-machine-lease.md](05-machine-lease.md) §4.1),
以及一把锁(`synchronized`)。`state` 打包了 `(timeOffset << seqBits) | seq`;被替换或关闭的状态是哨兵 `CLOSED`,关闭后换上终止 `Bound`(`TERMINATED`)。

```
loop:
  synchronized (lock):                 // 一次尝试;锁内从不等待
    bound = this.bound.get()
    if bound == TERMINATED:            throw IllegalStateException
    if bound.b != slot.current():      old = bound; bound = Bound(slot.current(), INITIAL); this.bound.set(bound); retire(old)   // 换绑即重置序列
    b = bound.b;  s = bound.state.get()
    if !b.isValid():                   throw SlotLostException
    t, q = unpack(s);  now = time.now(unit) − epoch
    if now > t:                        next = pack(now, startSeq(now))      // startSeq 保留 4.x 的低位打散
    elif now == t:
        if q < seqMax:                 next = s + 1
        else:                          结果 = EXHAUSTED
    else:                              regression.classify(t − now)  → 借用 t / 结果 = WAIT / FAIL
    tm = toMillis(本次使用的时间单位)                                    // 该单位覆盖的毫秒区间起点
    if tm ≤ b.floor:                   结果 = WAIT(b.floor − tm)           // 首次发号与每次换绑都会走到这里
    elif tm + unitMillis − 1 > b.ceiling: 结果 = WINDOW_EXCEEDED
    else:                              bound.state.setRelease(next); 记下 b.slot
  if 已发号:                           return pack(t', slot, seq')
  // 锁外:发事件(每次调用每类一次),等待(序列耗尽、回拨、floor、在 ceiling 处等待续约至 regression.waitUpTo)或抛出,然后重试
```

- **发号本身是一个很短的临界区**(D25):读绑定与时间、检查 floor/ceiling、计算、发布新状态。**所有等待都在锁外**:floor 等待、序列耗尽、回拨等待、在 ceiling 处等待续约时,先释放锁再等待,然后重试;listener 也在锁外调用。
  R1-A 的基准表明,无锁 CAS 在 16 线程下只有 4.x 的 92–97%(缓存行在核间搬运),而短临界区与 4.x 持平;要保住的性质是"不在锁内等待",而不是"无锁"。
- 新状态用 `setRelease` 发布(R1-core-05):下一次尝试在同一把锁内读取;锁外唯一的读者 `lastIssuedMillis()` 可以容忍旧值,直到 `close()` 或换绑用 `getAndSet` 退役状态,那时是精确的。
- floor/ceiling 检查是安全条件,在发布新状态之前对本次实际使用的时间戳进行,与 GC 停顿、时钟跳变无关。
- 换绑后序列器随新绑定一起重置,ID 中的槽位总是取自同一个绑定。
- `close()`:在锁内换上终止 `Bound`,并把被替换的状态 `getAndSet` 为 `CLOSED`,由此得到精确的 `lastIssued`。之后的尝试读到终止 `Bound` 并抛出 `IllegalStateException`。

## 5. 工具与小众能力(D21:特性不回退)

```java
public final class IdGenerators {
    public static IntIdGenerator toInt(IdGenerator ids);                                   // 取代 IntegerIdGenerator,溢出抛 IdOverflowException
    public static IdGenerator uncertain(IdGenerator ids, int bits, RandomGenerator random); // 取代 UncertaintyIdGenerator,原 id 太大时抛 IdOverflowException(R1-core-11)
    public static IdGenerator local();                                                     // 取代 AtomicLongGenerator
    public static Supplier<String> uuid();                                                 // 取代 UuidGenerator
    public static IdGenerator lazy(Supplier<? extends IdGenerator> lookup);                // 取代 LazyIdGenerator
}
@FunctionalInterface public interface IntIdGenerator { int generate(); }
```

这些都是不到 50 行的组合函数,不引入新的类层次。

时间位溢出抛 `TimeOverflowException extends CosIdException`(对应 4.x `TimestampOverflowException`);epoch 在未来由 `RuntimePlan` 在启动时拒绝(R1-core-12)。

## 6. 公开 API 清单与演进规则

本章范围内的核心类型:`IdGenerator`、`IntIdGenerator`、`StringIdGenerator`、`IdGenerators`、`IdCodec`、`Radix`、`Friendly`、`DatePrefix`、`PartsCodec`、
`Layout`、`IdParts`、`TimeSource`、`TimeBasedGenerator`、`ClockRegressionPolicy`、`GroupedId`(4.x 同一范围约 60 个);core 导出的全部类型见 02 §4。

**值类型的演进(D39,R1-core-01,详见 [api-freeze.md](api-freeze.md) §4)**:

- 封闭的值保留 `record`,规范构造器与记录模式都属于兼容承诺:`Range`、`IdParts`、`GroupedId`、`Owner`、`Layout`、`ClockRegressionPolicy`、
  `CodecSpec.*`、`PartsCodecSpec.*`、`GroupedCodecSpec.*`、`GeneratorSpec.Disabled`、`AcquireResult.Exhausted`,以及 `StringIdGenerator(ids, codec)`(一个 `long` 生成器与它的 codec)。
- 具体的生成器与租约类(`TimeBasedGenerator`、`SegmentGenerator`、`PeriodicSegmentGenerator`、`UuidV7Generator`、`LeaseKeeper`、`StaticSlot`)
  是 final class,公开构造器是 `CosIdRuntime` 的装配入口,签名不变;新参数只走 builder 或静态工厂(R2-core-03,api-freeze §4.2)。
- 配置与 SPI 值类型是 **final class + builder**(无 public 构造器,访问器沿用 record 风格,值等价,`toString` 同 record 格式):
  `RuntimeSpec`、`StoreSpec`、`RuntimeOptions`、`MachineSpec.Leased/Static`、`GeneratorSpec` 的策略变体、`BatchPolicy`、`PeriodicPolicy`、
  `StoreConfig`、`SlotLease`、`AcquireResult.Acquired`、`GeneratorInfo`。GA 后只往 builder 加有默认值的可选字段。
- sealed 规格接口(`GeneratorSpec`、`MachineSpec`、`CodecSpec`、`PartsCodecSpec`、`GroupedCodecSpec`)次版本可加变体,对它们 `switch` 须保留 `default`;
  `AcquireResult` 封闭(D40)。

## 7. 删除项

`Decorator` 体系、15 个 `*Stat` 类(由 `describe()` 取代)、`SnowflakeIdState`/`CosIdState` 及三套 Parser、`SafeJavaScriptSnowflakeId`(由 `Layout.JS_SAFE_*` 取代)、
`IntegerIdGenerator`、`UncertaintyIdGenerator`、`AtomicLongGenerator`、`UuidGenerator`、`LazyIdGenerator`(由 `IdGenerators` 取代)、
`CosIdEvents`、`CallTimeout`、`Clock.CACHE`、`ProcessId`、`LocalDateTimeConvert`、`Exceptions`。
