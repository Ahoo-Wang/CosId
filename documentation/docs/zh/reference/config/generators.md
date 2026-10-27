# 配置参考：生成器与编解码器

`cosid.generators.<name>`：每个生成器一项，由 `CosIdProperties.Generator` 绑定。哪些属性适用取决于策略。
名称不能包含 `/`，`default` 为主 `IdGenerator` Bean（即 4.x 的 share）。

## 所有策略

| 属性 | 默认值 | 含义 |
|---|---|---|
| `strategy` | | `snowflake`、`cosid80`、`segment` 或 `periodic-segment` |
| `disabled` | `false` | 已声明但不创建；其 Bean 抛出 `GeneratorDisabledException`（D24） |
| `codec` | `[]`（十进制） | 编解码器列表，按顺序应用（见下文） |

## `snowflake` 与 `cosid80`

| 属性 | 默认值 | 含义 |
|---|---|---|
| `layout` | `SNOWFLAKE_MS` | snowflake：预设，`SNOWFLAKE_MS`、`SNOWFLAKE_S`、`JS_SAFE_MS` 或 `JS_SAFE_S` |
| `epoch` | 预设的值 | snowflake：覆盖 epoch（一个 `Instant`，`2019-12-24T16:00:00Z`） |
| `unit` | 预设的值 | snowflake：`MILLIS` 或 `SECONDS` |
| `time-bits`、`node-bits`、`seq-bits` | 预设的值 | snowflake：覆盖各段位宽；`node-bits ≥ machine bits` |
| `seq-reset-threshold` | 预设的值；设置了 `seq-bits` 时为序列范围的一半 | snowflake：新时间单位起始序列的上限（4.x `sequence-reset-threshold`）；取值 `0` 到 `2^seq-bits − 1` |
| `regression.borrow-up-to` | `5ms` | 在最后一个时间单位上继续发号所允许的最大时钟回拨 |
| `regression.wait-up-to` | `1s` | 等待恢复的最大回拨；超出则抛出 `ClockBackwardsException` |

`cosid80` 始终使用 `Layout.COSID_80`（44 位毫秒、20 位节点、16 位序列）；其编解码器为单个 `radix` 或
`friendly` 项。

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
