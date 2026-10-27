# Sharding Algorithms

`cosid-sharding` keeps the 4.x sharding algorithms (D7) and releases them with every major version. They decide
which physical table, or database, a sharding value goes to, and which ones a range query has to visit. The module
depends on `cosid-core` only.

```kotlin
implementation("me.ahoo.cosid:cosid-sharding")
```

```java
public interface Sharding<T extends Comparable<? super T>> extends PreciseSharding<T>, RangeSharding<T> {
    String sharding(T shardingValue);                         // one value → one node
    Collection<String> sharding(ShardingRange<T> range);      // a range → the nodes it touches
    Collection<String> getEffectiveNodes();
}
```

## ModCycle: modulo sharding

`ModCycle<T extends Number>` maps `value mod divisor` to `{prefix}{index}`. A range visits only the nodes it covers,
and all of them once it spans `divisor` values.

<p align="center"><img src="../../../public/assets/design/CosIdModShardingAlgorithm.png" alt="ModCycle"/></p>

```java
ModCycle<Long> orders = new ModCycle<>(4, "t_order_");
orders.sharding(10L);                                     // t_order_2
orders.sharding(ShardingRange.closed(1L, 2L));            // [t_order_1, t_order_2]
orders.sharding(ShardingRange.atLeast(0L));               // all four
```

## IntervalTimeline: time interval sharding

`IntervalTimeline` cuts an effective time range into intervals of an `IntervalStep` (a `ChronoUnit` and an amount).
Each interval is a node named `{prefix}{suffix}`, the suffix formatted by a `DateTimeFormatter`. Finding a node is
arithmetic, not a search.

<p align="center"><img src="../../../public/assets/design/CosIdIntervalShardingAlgorithm.png" alt="IntervalTimeline"/></p>

```java
IntervalTimeline logs = new IntervalTimeline("t_log_",
    ShardingRange.closed(LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 12, 31, 23, 59)),
    IntervalStep.of(ChronoUnit.MONTHS), DateTimeFormatter.ofPattern("yyyyMM"));
logs.sharding(LocalDateTime.of(2026, 10, 27, 12, 0));    // t_log_202610
```

A value outside the effective range is rejected (`IllegalArgumentException`); a range outside it gets no node.

### Convertors

The interval algorithm shards a `LocalDateTime`. A convertor turns the column value into one:

| Convertor | Accepts |
|---|---|
| `StandardLocalDateTimeConvertor(zone, isSecondTs, formatter)` | `LocalDateTime`, `ZonedDateTime`, `OffsetDateTime`, `Instant`, `LocalDate`, `Date`, `YearMonth`, `Year`, an epoch `Long` (milliseconds, or seconds when `isSecondTs`), a `String` in the formatter's pattern |
| `SnowflakeLocalDateTimeConvertor(layout, zone[, codec])` | a Snowflake id, `Long` or text (base 62, 11 digits, padded by default): the time it was issued, through `Layout.unpack` |

So a table sharded by month can be keyed by the Snowflake id alone:

```java
var issued = new SnowflakeLocalDateTimeConvertor(Layout.SNOWFLAKE_MS, ZoneId.of("Asia/Shanghai"));
logs.sharding(issued.toLocalDateTime(orderId));
```

## CachedSharding

`new CachedSharding<>(sharding)` caches the range results of another algorithm. It is bounded: by default it keeps up
to 10 000 ranges and empties itself once full, so lookups take no lock.

## Changes from 4.x

- `ShardingRange` replaces Guava's `Range`. It keeps Guava's factory names (`closed`, `open`, `closedOpen`,
  `openClosed`, `atLeast`, `atMost`, `greaterThan`, `lessThan`, `all`, `singleton`) and semantics, so porting is a
  rename. `BoundType` is `me.ahoo.cosid.sharding.BoundType`.
- `SnowflakeLocalDateTimeConvertor` takes a `Layout` and a zone instead of a `SnowflakeIdStateParser`.
- `cosid-sharding` no longer depends on Guava or Lombok.
- ShardingSphere ships its own CosId-based algorithms; they are maintained in ShardingSphere, not here.
