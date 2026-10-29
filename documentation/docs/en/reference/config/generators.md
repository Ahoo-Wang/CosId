# Configuration Reference: Generators and Codecs

`cosid.generators.<name>`: one entry per generator, bound by `CosIdProperties.Generator`. Which properties apply
depends on the strategy. `default` is the primary `IdGenerator` bean (4.x's share). Names, like machine names, are
what every backend stores: no `/`, no whitespace, not `.` or `..`, and no control or private-use characters; a segment's
key `namespace/name[/period]` is at most 255 characters. Other names fail the start.

A name may contain a dot, such as `eshop-ordering.order` (one generator per aggregate). In Spring configuration it goes
in brackets, or the dot splits it: YAML `cosid.generators."[eshop-ordering.order]"`, properties
`cosid.generators[eshop-ordering.order].strategy=segment`. Without the brackets the start fails on the unknown keys,
and the message shows the bracketed form.

## All strategies

| Property | Default | Meaning |
|---|---|---|
| `strategy` | | `snowflake`, `cosid80`, `segment`, `periodic-segment` or `custom` |
| `disabled` | `false` | declared but not created; its bean throws `GeneratorDisabledException` (D24) |
| `codec` | `[]` (decimal) | the codec list, applied in order (below) |

## `snowflake` and `cosid80`

| Property | Default | Meaning |
|---|---|---|
| `layout` | snowflake `SNOWFLAKE_MS`; cosid80 `COSID_80` | the preset: snowflake `SNOWFLAKE_MS`, `SNOWFLAKE_S`, `JS_SAFE_MS` or `JS_SAFE_S`; cosid80 `COSID_80` only |
| `epoch` | the preset's | snowflake: overrides the epoch (an `Instant`, `2019-12-24T16:00:00Z`); an error on cosid80, which keeps the Unix epoch |
| `unit` | the preset's | snowflake: `MILLIS` or `SECONDS`; an error on cosid80, which keeps milliseconds |
| `time-bits`, `node-bits`, `seq-bits` | the preset's | override the widths (4.x `timestamp-bit`, `machine-bit`, `sequence-bit` for cosid80); `node-bits` holds the machine's `slot-offset + 2^bits` |
| `seq-reset-threshold` | the preset's; half the sequence range when `seq-bits` is set | the highest sequence a new time unit may start from (4.x `sequence-reset-threshold`); `0` to `2^seq-bits − 1` |
| `machine` | the `cosid.machine` | the named machine (`cosid.machines.<name>`) whose slot the ids carry |
| `regression.borrow-up-to` | `5ms` | the largest clock regression issued on the last time unit |
| `regression.wait-up-to` | `1s` | the largest regression waited out; beyond it, `ClockBackwardsException` |

`cosid80` starts from `Layout.COSID_80` (44-bit ms since the Unix epoch, 20-bit node, 16-bit sequence) and must stay
wider than 63 bits, with `time-bits + seq-bits` at most 63. Its codec is a single item: `radix` with `radix` 62 or 36
only (the layout gives each part its width, so `width` and `pad-start` are errors), or `friendly`, whose `pad-start`
applies (4.x `cosid.generator.pad-start`).

```yaml
cosid:
  generators:
    token:
      strategy: cosid80
      node-bits: 16              # 4.x cosid.generator.machine-bit
      seq-reset-threshold: 1023  # 4.x cosid.generator.sequence-reset-threshold
      codec: [ { friendly: { zone: Asia/Shanghai, pad-start: false } } ]
```

4.x `cosid.generator.namespace` (machine ids shared by several services) becomes a named machine:
`machine: <name>` on the generator and `cosid.machines.<name>.namespace` ([basic](basic.md), and
[ids unique across services](../../guide/concepts/slot-lease.md#ids-unique-across-services)).

## `custom`

A user `IdGenerator` bean (4.x `IdGeneratorProvider.set`); the runtime hands it out (its bean, `IdFiller`, the
endpoint) but does not close it.

| Property | Default | Meaning |
|---|---|---|
| `ref` | | required: the bean name, other than every generator's name; the bean must not depend on `CosIdRuntime` |

Its `codec` follows the rules of `segment` (no `friendly`, no `date-prefix` from the id time).

## `uuidv7`

UUIDv7 ids ([RFC 9562 §5.7](https://www.rfc-editor.org/rfc/rfc9562#section-5.7), D46): a 48-bit Unix millisecond
timestamp, version 7, a 16-bit counter (the 12 bits of `rand_a` and the top 4 bits of `rand_b`, as §6.2 method 1
allows), variant `10` and 58 random bits. Their text is the canonical 36-character form,
such as `019a2c5e-3f40-7123-8c4d-2f1e9a7b6c50`; other text codecs (radix and the like) may come in a later release.

```yaml
cosid:
  generators:
    order:
      strategy: uuidv7
```

It needs **no machine, store nor lease**: an application whose generators are all `uuidv7` starts
without any `cosid.machine.*` or `cosid.stores.*`. It has no properties of its own. `machine` fails the start, as do
the keys of the other strategies (`layout`, `epoch`, `time-bits`, `store`, `period`, `codec`, …).

- **Unique without coordination**: the ids of one instance never collide (its counter separates them), and two ids of
  different instances collide only with the same millisecond, the same counter value and the same 58 random bits. Each
  instance starts its counter at a random value below 2¹⁵ in each millisecond, so for m instances that each issue k ids
  in one millisecond the expected collisions in that millisecond are about m²k² / 2⁷⁴. For example, 100 instances at
  1,000 ids per millisecond each (100 million ids per second in all) expect about one collision in 60 years.
- **Ordered within one generator instance**: each id is greater than the one before, as a `UUID` and as text. The
  counter counts on within the millisecond, from its random start below 2¹⁵, so one instance issues at least 2¹⁵
  (32,768) ids per millisecond, about 33 million per second, on the clock's timestamp. The timestamp runs ahead of the
  clock in two cases only: more ids than that, kept up (a full counter borrows the next millisecond), and a clock that
  goes back (the last timestamp is kept until the clock catches up). Ids of different instances or processes are ordered by their
  millisecond only.
- **Not a secret**: the random bits come from `ThreadLocalRandom` and the timestamp is in the clear. Never use an id as a
  token or to guard a resource.

The runtime hands out its text with `runtime.text("order")` (and `@CosId` `String` fields) and the generator with
`runtime.get("order", UuidV7Generator.class)` (`UUID generateUuid()`, `String generate()`); the bean `order` is that
generator. It issues no `long` ids: `generator("order")` and `stringGenerator("order")` fail, as for `cosid80`.

| | `uuidv7` | `snowflake` | `cosid80` |
|---|---|---|---|
| Size | 128 bits (`UUID`, 36 characters) | 63 bits (`long`) | 80 bits (text) |
| Coordination | none | a machine slot (lease or static) | a machine slot (lease or static) |
| Uniqueness | probabilistic across instances (random counter start, 58 random bits) | guaranteed by the slot | guaranteed by the slot |
| Order | within the instance; across instances by millisecond | within the instance; across instances by time unit | within the instance; across instances by millisecond |
| Fits | systems that already store UUIDs, many writers without a store | `long` keys, compact indexes | text keys that need guaranteed uniqueness, more nodes |

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
