# Configuration Reference: Generators and Codecs

`cosid.generators.<name>`: one entry per generator, bound by `CosIdProperties.Generator`. Which properties apply
depends on the strategy. The name may not contain `/`, and `default` is the primary `IdGenerator` bean (4.x's share).

## All strategies

| Property | Default | Meaning |
|---|---|---|
| `strategy` | | `snowflake`, `cosid80`, `segment` or `periodic-segment` |
| `disabled` | `false` | declared but not created; its bean throws `GeneratorDisabledException` (D24) |
| `codec` | `[]` (decimal) | the codec list, applied in order (below) |

## `snowflake` and `cosid80`

| Property | Default | Meaning |
|---|---|---|
| `layout` | `SNOWFLAKE_MS` | snowflake: the preset, `SNOWFLAKE_MS`, `SNOWFLAKE_S`, `JS_SAFE_MS` or `JS_SAFE_S` |
| `epoch` | the preset's | snowflake: overrides the epoch (an `Instant`, `2019-12-24T16:00:00Z`) |
| `unit` | the preset's | snowflake: `MILLIS` or `SECONDS` |
| `time-bits`, `node-bits`, `seq-bits` | the preset's | snowflake: override the widths; `node-bits ≥ machine bits` |
| `seq-reset-threshold` | the preset's; half the sequence range when `seq-bits` is set | snowflake: the highest sequence a new time unit may start from (4.x `sequence-reset-threshold`); `0` to `2^seq-bits − 1` |
| `regression.borrow-up-to` | `5ms` | the largest clock regression issued on the last time unit |
| `regression.wait-up-to` | `1s` | the largest regression waited out; beyond it, `ClockBackwardsException` |

`cosid80` always uses `Layout.COSID_80` (44-bit ms, 20-bit node, 16-bit sequence); its codec is a single `radix` or
`friendly` item.

## `segment`

| Property | Default | Meaning |
|---|---|---|
| `store` | | the store name |
| `step` | `100` | the smallest reservation |
| `initial-offset` | `1` | where a new key starts: its first id (never 0 by default, as in 4.x) |
| `lookahead` | `1s` | how long the ids at hand should last; `0` disables prefetching |
| `max-batch` | `max(step × 1000, 2^20)` rounded to `step` | the largest reservation; a multiple of `step` |
| `reserve-timeout` | `3s` | how long a caller waits for a refill; at least the store `timeout` |
| `range-ttl` | none | a range unused this long is dropped (trend increasing across instances) |

## `periodic-segment`

Everything of `segment`, plus:

| Property | Default | Meaning |
|---|---|---|
| `period` | | `YEAR`, `YEAR_MONTH` or `YEAR_MONTH_DAY` |
| `zone` | the system zone | the time zone of the periods; set it explicitly |
| `prewarm` | `1m` | the next period is warmed up this long before the current one ends |
| `seal-after` | none | seal a period's key this long after the period ends (a day or more) |

## Codec list: `codec`

Each item is exactly one of:

| Item | Fields (default) | Notes |
|---|---|---|
| `radix` | `radix` (`62`, from 2 to 62), `width` (the digits of `Long.MAX_VALUE`), `pad-start` (`false`) | text in a base |
| `friendly` | `zone` (system), `pad-start` (`true`) | `yyyyMMddHHmmssSSS-node-seq`; time-based only |
| `prefix` | a string | |
| `suffix` | a string | |
| `date-prefix` | `pattern` (`yyyyMMdd`), `delimiter` (`-`), `source` (`ID_TIME` or `NOW`), `zone` (system) | in a periodic segment, the period prefix (its pattern comes from the period) |
| `custom` | a bean name or class name | an `IdCodec` of your own |
| `period` | `enabled` (`true`), `delimiter` (`-`) | periodic segment only: where the period key goes in the text (4.x `group-prefix`) |

Rules:

- The first item turns the id into text (`radix`, `friendly`, `custom`); without one, the text is decimal and the
  modifiers apply to it.
- `prefix`, `suffix` and `date-prefix` wrap the text, in the order written.
- `periodic-segment`: either a first `date-prefix` item (the period first, the rest encode the value), or one `period`
  item where the period goes (below).
- `cosid80`: one `radix` or `friendly` item.

### Where the period goes (periodic segments)

The 4.x `group-prefix` forms map to a `period` item. The items **before** it encode the value; the item puts the period
key and its delimiter in front of that text; the items **after** it decorate the whole text.

| 4.x `group-prefix` | 5.0 codec list | Text of `(2026, 42)` |
|---|---|---|
| `before-prefix: true` (the default): business prefix, then period, then value | `[ radix, period, prefix: "BIZ-" ]` | `BIZ-2026-000042` |
| `before-prefix: false`: period first | `[ date-prefix, radix, prefix: "BIZ-" ]`, or `[ radix, prefix: "BIZ-", period ]` | `2026-BIZ-000042` |
| `enabled: false`: no period in the text | `[ radix, period: { enabled: false }, prefix: "O" ]` | `O000042` |

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

Without the period in the text (`enabled: false`), the text cannot tell the period, so `GroupedCodec.decode` is not
supported (`UnsupportedOperationException`). A list holds at most one `period` item, and only periodic segments take
one. `cosid-migration config` converts the 4.x settings to these forms.

### Example

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
      layout: JS_SAFE_MS            # needs machine bits ≤ 3
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
