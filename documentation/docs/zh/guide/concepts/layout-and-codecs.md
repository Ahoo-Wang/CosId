# 布局与编解码器

CosId 生成器产出一个数值；**编解码器**把它转成文本。两者相互独立（生成过程从不感知编码），因此对生成器做包装或计数永远不会改变它的文本形式。

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

## Layout：时间型 ID 的位划分

`Layout` 是 Snowflake 类 ID 位运算的唯一所在：从高位到低位依次为 `time | node | seq`。

<p align="center"><img src="../../../public/assets/design/Snowflake-identifier.png" alt="Snowflake layout"/></p>

```java
public record Layout(Instant epoch, ChronoUnit unit, int timeBits, int nodeBits, int seqBits, long seqResetThreshold) {
    public long pack(long time, int node, long seq);
    public IdParts unpack(long id);          // IdParts(time, node, seq)；parts.instant(layout) 即时间
    public int totalBits();
    public boolean isJsSafe();               // totalBits <= 53
    public static boolean isJsSafe(long id);
    public Layout withNodeBits(int nodeBits);
}
```

| 预设 | Epoch | 单位 | time / node / seq | 总位数 | 说明 |
|---|---|---|---|---|---|
| `SNOWFLAKE_MS`（默认） | 2019-12-24T16:00:00Z（`1577203200000`） | ms | 41 / 10 / 12 | 63 | 4.x 的默认值（D16）：迁移后 ID 数值保持连续 |
| `SNOWFLAKE_S` | 同上 | s | 31 / 10 / 22 | 63 | |
| `JS_SAFE_MS` | 同上 | ms | 41 / 3 / 9 | 53 | 可放进 JavaScript 的 `number`；最多 8 个槽位 |
| `JS_SAFE_S` | 同上 | s | 31 / 3 / 19 | 53 | |
| `COSID_80` | Unix epoch | ms | 44 / 20 / 16 | 80 | 80 位 CosId：只有文本形式，通过 `PartsCodec` 输出 |

- 布局在构建时校验：time 1–63 位，node 0–31 位，sequence 1–62 位，单位为 `MILLIS` 或 `SECONDS`（秒级 epoch 必须是整秒）。
- **node 位必须能容纳机器号槽位。** 一个运行时持有 `[0, 2^machine.bits)` 中的一个槽位（D18）；除非每个时间型生成器都满足
  `layout.nodeBits ≥ machine.bits`，否则启动失败。因此 `JS_SAFE_*`（3 个 node 位）需要 `machine.leased.bits: 3` 或更小。
- `seqResetThreshold` 保留了 4.x 的低位打散：新的时间单位从不超过该阈值的某个值（默认为范围的一半）开始计序列，
  使 ID 对一个小数取模时分布均匀。
- 在 Spring 中，`layout` 指定一个预设，`epoch`、`unit`、`time-bits`、`node-bits`、`seq-bits` 覆盖其字段。

### TimeBasedGenerator

一个类发出所有时间型 ID，适用于任意布局；4.x 每种单位、每种编码各有一个类，外加若干装饰器。

```java
public final class TimeBasedGenerator implements IdGenerator, PartsIdGenerator, AutoCloseable {
    public long generate();            // 至多 63 位的布局
    public IdParts nextParts();        // 任意布局，包括 COSID_80
    public Layout layout();
    public int slot();
    public long lastIssuedMillis();
}
```

发出一个 ID 是一个短临界区：读取时间、检查它是否落在槽位的窗口内、推进序列。
**所有等待都发生在锁外**（新槽位后的第一个 ID、序列耗尽、时钟回拨），这使 5.0 在 16 线程下与 4.x 持平（D25）。

### 时钟回拨

`ClockRegressionPolicy(borrowUpTo, waitUpTo)`，默认 `5ms` / `1s`：

| 时钟回拨了 | 生成器 |
|---|---|
| 不超过 `borrowUpTo` | 继续在最后一个时间单位上发号（"借用"未来） |
| 不超过 `waitUpTo` | 等待时钟追上 |
| 更多 | 抛出 `ClockBackwardsException` |

`ClockRegressionPolicy.FAIL` 会立即失败。这些都不影响跨实例的唯一性：那是[槽位租约](slot-lease.md)窗口的职责。

## 编解码器

| 编解码器 | 文本 | 说明 |
|---|---|---|
| `Radix.of(radix, width, padStart)` | 2–62 进制 | 10、36、62 进制共用一个类；`decode` 会检查溢出。`Radix.of(62, 11, true)` 即 4.x 的 `Radix62IdConverter.PAD_START` |
| `Friendly.of(layout, zone, padStart)` | `yyyyMMddHHmmssSSS-node-seq` | 用于时间型 ID；时区总是显式指定；`padStart` 把 node 与序列补齐到各自宽度 |
| `withPrefix`、`withSuffix` | `O` + 文本 | |
| `DatePrefix.of(pattern, delimiter, source)` | `20261027-` + 文本 | `source = ID_TIME`（ID 自身的时间，仅限时间型）或 `NOW`（时钟，与 4.x 号段的做法相同） |
| `PartsCodec.radix(layout, radix)`、`PartsCodec.friendly(layout, zone)` | 80 位 CosId 文本 | 每个部分定宽，因此文本的排序与 ID 一致 |
| `GroupedCodec.datePrefix(delimiter, valueCodec)` | `20261027-000042` | 周期号段：先是周期，再是值 |
| 自定义 `IdCodec` | | 以类名或 bean 名指定（`custom`） |

在配置中，编解码器是一个**有序列表**，按书写顺序应用。第一项把 ID 转成文本（`radix`、`friendly` 或 `custom`）；没有这一项时文本为十进制。
其后的各项（`prefix`、`suffix`、`date-prefix`）对其进行包装：

```yaml
cosid:
  generators:
    order:
      strategy: segment
      store: db
      codec:
        - radix: { radix: 62 }                     # 1. 62 进制
        - date-prefix: { pattern: yyMMdd, delimiter: "", source: NOW }   # 2. 261027qZ
        - prefix: "O"                              # 3. O261027qZ
    trace:
      strategy: snowflake
      codec:
        - friendly: { zone: Asia/Shanghai }        # 20261027153012123-0003-0017
    token:
      strategy: cosid80
      codec:
        - radix: { radix: 62 }                     # 一个 radix 或 friendly 项
```

每个编解码器也都能解码：`codec.decode(text)` 返回 ID，`layout.unpack(id)` 返回其各个部分，取代了 4.x 的 `SnowflakeIdStateParser`。

## 小工具

`IdGenerators` 组合出其余能力，取代 4.x 中的这些类：

| 4.x | 5.0 |
|---|---|
| `IntegerIdGenerator` | `IdGenerators.toInt(ids)`：一个 `IntIdGenerator`，超过 `Integer.MAX_VALUE` 时抛出 `IdOverflowException` |
| `UncertaintyIdGenerator` | `IdGenerators.uncertain(ids, bits, random)` |
| `AtomicLongGenerator`、`UuidGenerator` | `IdGenerators.local()`、`IdGenerators.uuid()` |
| `LazyIdGenerator` | `IdGenerators.lazy(() -> runtime.generator("order"))` |
| `SafeJavaScriptSnowflakeId` | `Layout.JS_SAFE_MS` / `JS_SAFE_S` |
