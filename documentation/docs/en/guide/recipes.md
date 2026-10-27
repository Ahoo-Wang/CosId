# ID Recipes

Ready-made `cosid.*` configurations for common ids. Each assumes the stores and machine slot of
[Getting Started](getting-started.md); combine as many as you need in one runtime.

## Primary keys

```yaml
cosid:
  generators:
    default:                       # a 63-bit, time-ordered BIGINT
      strategy: snowflake
```

A dense, strictly increasing `BIGINT` per table, one store write per range:

```yaml
    order:
      strategy: segment
      store: db
```

## Ids for JavaScript clients

A 53-bit Snowflake fits a JavaScript `number` (at most 8 slots, so `machine.leased.bits: 3`):

```yaml
cosid:
  machine:
    leased: { store: db, bits: 3 }
  generators:
    default:
      strategy: snowflake
      layout: JS_SAFE_MS
```

Or keep 63 bits and send them as text with Jackson's [`@AsString`](integrations/jackson.md).

## Short text ids

```yaml
    short-link:
      strategy: snowflake
      codec: [ { radix: { radix: 62, width: 11, pad-start: true } } ]     # 11 characters, sortable
    coupon:
      strategy: segment
      store: db
      codec: [ { radix: { radix: 36 } }, { prefix: "C" } ]                # C1, C2, … CA, …
```

## Readable trace ids

```yaml
    trace:
      strategy: snowflake
      codec: [ { friendly: { zone: Asia/Shanghai } } ]                    # 20261027153012123-0003-0017
```

`Friendly.decode` (and `codec.decode`) reads them back; `Layout.unpack(id)` gives the parts of a numeric id.

## Business numbers with a date

Order numbers that carry the day they were issued, with no reset:

```yaml
    order-no:
      strategy: segment
      store: db
      codec:
        - radix: { radix: 10, width: 8, pad-start: true }
        - date-prefix: { pattern: yyyyMMdd, delimiter: "", source: NOW, zone: Asia/Shanghai }   # 2026102700000042
```

Bill numbers that restart every day:

```yaml
    bill:
      strategy: periodic-segment
      store: db
      period: YEAR_MONTH_DAY
      zone: Asia/Shanghai
      seal-after: 2d
      codec:
        - date-prefix: { delimiter: "-" }
        - radix: { radix: 10, width: 6, pad-start: true }                # 20261027-000042
```

## More than 64 bits

```yaml
    token:
      strategy: cosid80                                                   # 44-bit ms, 20-bit node, 16-bit sequence
      codec: [ { radix: { radix: 62 } } ]
```

## Second-precision ids

```yaml
    event:
      strategy: snowflake
      layout: SNOWFLAKE_S                                                 # 31-bit seconds, 22-bit sequence
```

## `int` ids

```java
IntIdGenerator small = IdGenerators.toInt(runtime.generator("order"));   // IdOverflowException past Integer.MAX_VALUE
```

An `int` (or `Integer`) field annotated `@CosId` is range checked the same way.

## Trend-increasing segments across instances

Several instances each hold their own range, so ids from different instances interleave. `range-ttl` bounds how stale a
held range may get:

```yaml
    order:
      strategy: segment
      store: db
      range-ttl: 1m
```

## No database at all

A slot the deployment keeps unique, and no store:

```yaml
cosid:
  machine:
    static:
      slot: ${POD_ORDINAL}
      state-file: /var/lib/cosid/state
  generators:
    default:
      strategy: snowflake
```

## Ids for other languages

Run [cosid-server](server.md) with `cosid.server.ids.enabled: true`, and call
`GET /ids/{generator}?count=100` with a token.
