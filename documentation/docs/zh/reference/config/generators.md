# 配置参考：生成器与编解码器

`cosid.generators.<name>`：每个生成器一项，由 `CosIdProperties.Generator` 绑定。哪些属性适用取决于策略。
`default` 为主 `IdGenerator` Bean（即 4.x 的 share）。生成器名与 machine 名只能是每个后端都能存的：不含 `/`、空白，
不是 `.` 或 `..`，不含控制字符或私用区字符；号段的键 `namespace/name[/period]` 至多 255 个字符。其他名字会让启动失败。

名字可以带点，例如 `eshop-ordering.order`（每个聚合一个生成器）。在 Spring 配置中要写在方括号里，否则点会把名字拆开：
YAML 写作 `cosid.generators."[eshop-ordering.order]"`，properties 写作 `cosid.generators[eshop-ordering.order].strategy=segment`。
不加方括号时，启动会因未知键失败，报错信息中给出加方括号的写法。

## 所有策略

| 属性 | 默认值 | 含义 |
|---|---|---|
| `strategy` | | `snowflake`、`cosid80`、`segment`、`periodic-segment` 或 `custom` |
| `disabled` | `false` | 已声明但不创建；其 Bean 抛出 `GeneratorDisabledException`（D24） |
| `codec` | `[]`（十进制） | 编解码器列表，按顺序应用（见下文） |

## `snowflake` 与 `cosid80`

| 属性 | 默认值 | 含义 |
|---|---|---|
| `layout` | snowflake 为 `SNOWFLAKE_MS`；cosid80 为 `COSID_80` | 预设：snowflake 为 `SNOWFLAKE_MS`、`SNOWFLAKE_S`、`JS_SAFE_MS` 或 `JS_SAFE_S`；cosid80 只接受 `COSID_80` |
| `epoch` | 预设的值 | snowflake：覆盖 epoch（一个 `Instant`，`2019-12-24T16:00:00Z`）；在 cosid80 上报错（固定 Unix epoch） |
| `unit` | 预设的值 | snowflake：`MILLIS` 或 `SECONDS`；在 cosid80 上报错（固定毫秒） |
| `time-bits`、`node-bits`、`seq-bits` | 预设的值 | 覆盖各段位宽（cosid80 对应 4.x 的 `timestamp-bit`、`machine-bit`、`sequence-bit`）；`node-bits` 须容纳所选 machine 的 `slot-offset + 2^bits` |
| `seq-reset-threshold` | 预设的值；设置了 `seq-bits` 时为序列范围的一半 | 新时间单位起始序列的上限（4.x `sequence-reset-threshold`）；取值 `0` 到 `2^seq-bits − 1` |
| `machine` | `cosid.machine` | ID 所用槽位的命名 machine（`cosid.machines.<name>`） |
| `regression.borrow-up-to` | `5ms` | 在最后一个时间单位上继续发号所允许的最大时钟回拨 |
| `regression.wait-up-to` | `1s` | 等待恢复的最大回拨；超出则抛出 `ClockBackwardsException` |

`cosid80` 以 `Layout.COSID_80`（自 Unix epoch 起的 44 位毫秒、20 位节点、16 位序列）为起点，总位数必须大于 63，且 `time-bits + seq-bits`
不超过 63。其编解码器为单个项：`radix` 只接受 `radix` 62 或 36（各段宽度由布局决定，设置 `width`、`pad-start` 会报错），或 `friendly`，
其 `pad-start` 生效（对应 4.x `cosid.generator.pad-start`）。

```yaml
cosid:
  generators:
    token:
      strategy: cosid80
      node-bits: 16              # 4.x cosid.generator.machine-bit
      seq-reset-threshold: 1023  # 4.x cosid.generator.sequence-reset-threshold
      codec: [ { friendly: { zone: Asia/Shanghai, pad-start: false } } ]
```

4.x 的 `cosid.generator.namespace`（多个服务共用机器号）对应命名 machine：生成器上的 `machine: <name>` 加
`cosid.machines.<name>.namespace`（见[基础配置](basic.md)与[跨服务全局唯一](../../guide/concepts/slot-lease.md#跨服务全局唯一)）。

## `custom`

用户提供的 `IdGenerator` Bean（对应 4.x `IdGeneratorProvider.set`）；运行时对外提供它（它的 Bean、`IdFiller`、端点），但不负责关闭它。

| 属性 | 默认值 | 含义 |
|---|---|---|
| `ref` | | 必填：Bean 名，不能与任何生成器同名；该 Bean 不能依赖 `CosIdRuntime` |

其 `codec` 规则同 `segment`（不能用 `friendly`，也不能用取自 ID 时间的 `date-prefix`）。

## `uuidv7`

UUIDv7 id（[RFC 9562 §5.7](https://www.rfc-editor.org/rfc/rfc9562#section-5.7)，D46）：48 位 Unix 毫秒时间戳、版本 7、16 位计数器
（`rand_a` 的 12 位加 `rand_b` 的高 4 位，§6.2 方法 1 允许这样做）、variant `10` 与 58 位随机数。文本为 36 个字符的规范形式，例如 `019a2c5e-3f40-7123-8c4d-2f1e9a7b6c50`；radix 等其他文本编解码器可能在以后的版本中加入。

```yaml
cosid:
  generators:
    order:
      strategy: uuidv7
```

它**不需要 machine、存储或租约**：生成器全部为 `uuidv7` 的应用，不配置任何 `cosid.machine.*` 或 `cosid.stores.*` 也能启动。它没有自己的属性；
配置 `machine` 会让启动失败，其他策略的键（`layout`、`epoch`、`time-bits`、`store`、`period`、`codec` 等）也一样。

- **无需协调即可唯一**：同一实例的 id 不会碰撞（由计数器区分）；不同实例的两个 id 只有在毫秒、计数器取值和 58 位随机数都相同时才会碰撞。
  每个实例在每毫秒以小于 2¹⁵ 的随机值作为计数起点，因此 m 个实例在同一毫秒内各发 k 个 id 时，该毫秒内的期望碰撞数约为 m²k² / 2⁷⁴。
  例如 100 个实例、每个每毫秒 1000 个 id（合计每秒 1 亿个），约 60 年才期望出现一次碰撞。
- **单个生成器实例内有序**：每个 id 都大于前一个，`UUID` 与文本都是如此。计数器在毫秒内从小于 2¹⁵ 的随机起点递增，
  所以单个实例每毫秒至少能以时钟的时间戳发出 2¹⁵（32768）个 id，约每秒 3300 万个。只有两种情况时间戳会超前于时钟：持续超过这个速率
  （计数器用尽时借用下一毫秒），以及时钟回拨（沿用上次的时间戳，直到时钟追上）。不同实例或进程的 id 只按毫秒有序。
- **不是秘密**：随机数取自 `ThreadLocalRandom`，时间戳明文可见。不要把 id 用作令牌或访问凭据。

运行时以 `runtime.text("order")`（以及 `@CosId` 的 `String` 字段）提供文本，以 `runtime.get("order", UuidV7Generator.class)` 提供生成器
（`UUID generateUuid()`、`String generate()`）；Bean `order` 就是这个生成器。它不发 `long` id：`generator("order")` 和
`stringGenerator("order")` 会失败，与 `cosid80` 相同。

| | `uuidv7` | `snowflake` | `cosid80` |
|---|---|---|---|
| 长度 | 128 位（`UUID`，36 个字符） | 63 位（`long`） | 80 位（文本） |
| 协调 | 无 | 机器槽位（租约或静态） | 机器槽位（租约或静态） |
| 唯一性 | 跨实例为概率性（随机计数起点与 58 位随机数） | 由槽位保证 | 由槽位保证 |
| 有序性 | 实例内有序；跨实例按毫秒有序 | 实例内有序；跨实例按时间单位有序 | 实例内有序；跨实例按毫秒有序 |
| 适合 | 已经以 UUID 为键的系统、无存储的大量写入方 | `long` 主键、紧凑索引 | 需要保证唯一的文本主键、更多节点 |

## `segment`

| 属性 | 默认值 | 含义 |
|---|---|---|
| `store` | | 存储名称 |
| `step` | `100` | 最小预留量 |
| `initial-offset` | `1` | 新 key 的起点：它的第一个 id（默认不会是 0，与 4.x 一致） |
| `lookahead` | `1s` | 手头的 id 应能维持多久；`0` 关闭预取 |
| `max-batch` | `max(step × 1000, 2^20)`，按 `step` 取整 | 最大预留量；`step` 的倍数 |
| `reserve-timeout` | `3s` | 调用方等待补充的时长；不小于存储的 `timeout` |
| `range-ttl` | 无 | 闲置这么久的区间会被丢弃（跨实例趋势递增） |

## `periodic-segment`

包含 `segment` 的全部属性，另加：

| 属性 | 默认值 | 含义 |
|---|---|---|
| `period` | | `YEAR`、`YEAR_MONTH` 或 `YEAR_MONTH_DAY` |
| `zone` | 系统时区 | 周期所用的时区；请显式设置 |
| `prewarm` | `1m` | 在当前周期结束前这么久预热下一个周期 |
| `seal-after` | 无 | 在周期结束这么久后封存该周期的 key（一天或更长） |

## 编解码器列表：`codec`

每一项恰好是以下之一：

| 项 | 字段（默认值） | 说明 |
|---|---|---|
| `radix` | `radix`（`62`，2 到 62）、`width`（`Long.MAX_VALUE` 的位数）、`pad-start`（`false`） | 某个进制的文本 |
| `friendly` | `zone`（系统时区）、`pad-start`（`true`） | `yyyyMMddHHmmssSSS-node-seq`；仅限时间型 |
| `prefix` | 一个字符串 | |
| `suffix` | 一个字符串 | |
| `date-prefix` | `pattern`（`yyyyMMdd`）、`delimiter`（`-`）、`source`（`ID_TIME` 或 `NOW`）、`zone`（系统时区） | 在周期号段中为周期前缀（其 pattern 来自周期） |
| `custom` | Bean 名称或类名 | 你自己的 `IdCodec` |
| `period` | `enabled`（`true`）、`delimiter`（`-`） | 仅周期号段：周期 key 在文本中的位置（4.x 的 `group-prefix`） |

规则：

- 第一项把 id 转为文本（`radix`、`friendly`、`custom`）；没有这样的项时，文本为十进制，修饰项作用于它。
- `prefix`、`suffix` 与 `date-prefix` 按书写顺序包裹文本。
- `periodic-segment`：要么第一项是 `date-prefix`（周期在最前，其余项编码数值），要么在周期应出现的位置放一个 `period` 项（见下）。
- `cosid80`：一个 `radix` 或 `friendly` 项。

### 周期在文本中的位置（周期号段）

4.x 的 `group-prefix` 写法对应 `period` 项。它**之前**的各项编码值；`period` 项把周期 key 与分隔符放在该文本之前；它**之后**的各项修饰整个文本。

| 4.x `group-prefix` | 5.0 编解码器列表 | `(2026, 42)` 的文本 |
|---|---|---|
| `before-prefix: true`（默认）：业务前缀，然后周期，然后值 | `[ radix, period, prefix: "BIZ-" ]` | `BIZ-2026-000042` |
| `before-prefix: false`：周期在最前 | `[ date-prefix, radix, prefix: "BIZ-" ]`，或 `[ radix, prefix: "BIZ-", period ]` | `2026-BIZ-000042` |
| `enabled: false`：文本不含周期 | `[ radix, period: { enabled: false }, prefix: "O" ]` | `O000042` |

```yaml
cosid:
  generators:
    invoice:
      strategy: periodic-segment
      store: db
      period: YEAR
      zone: Asia/Shanghai
      codec:
        - radix: { radix: 10, width: 6, pad-start: true }
        - period: { delimiter: "-" }
        - prefix: "BIZ-"                 # BIZ-2026-000042
```

文本不含周期（`enabled: false`）时，无法从文本得知周期，因此不支持 `GroupedCodec.decode`（抛出 `UnsupportedOperationException`）。一个列表至多有一个 `period` 项，且只有周期号段可以使用。`cosid-migration config` 会把 4.x 的设置转换为这些写法。

### 示例

```yaml
cosid:
  generators:
    default:
      strategy: snowflake
      codec: [ { radix: { radix: 62, width: 11, pad-start: true } } ]
    seconds:
      strategy: snowflake
      layout: SNOWFLAKE_S
    browser:
      strategy: snowflake
      layout: JS_SAFE_MS            # 要求机器号位数 ≤ 3
    custom-layout:
      strategy: snowflake
      epoch: 2024-01-01T00:00:00Z
      time-bits: 41
      node-bits: 8
      seq-bits: 14
      seq-reset-threshold: 1024
      regression: { borrow-up-to: 10ms, wait-up-to: 2s }
    order:
      strategy: segment
      store: db
      step: 1000
      lookahead: 2s
      codec: [ { radix: { radix: 36 } }, { prefix: "O-" } ]
    bill:
      strategy: periodic-segment
      store: db
      period: YEAR_MONTH_DAY
      zone: Asia/Shanghai
      seal-after: 2d
      codec: [ { date-prefix: { delimiter: "" } }, { radix: { radix: 10, width: 6, pad-start: true } } ]
    token:
      strategy: cosid80
      codec: [ { friendly: { zone: Asia/Shanghai } } ]
```
