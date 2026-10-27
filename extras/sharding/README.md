# cosid-sharding

The sharding algorithms of CosId (D7, [11-feature-parity.md §6](../../docs/architecture/11-feature-parity.md)), kept
from 4.x and released with every major version. They decide which physical table (or database) a sharding value goes to,
and which ones a range query must visit. The module depends on `cosid-core` alone.

| Type | What it does |
|---|---|
| `ModCycle<T extends Number>` | `value mod divisor` → `{prefix}{index}`; a range visits the nodes it covers (all of them once it spans `divisor` values) |
| `IntervalTimeline` | time intervals of an `IntervalStep` (`ChronoUnit` × amount) over an effective `ShardingRange<LocalDateTime>` → `{prefix}{suffix}`, the suffix formatted by a `DateTimeFormatter` |
| `CachedSharding<T>` | caches the range results of another `Sharding`; bounded (`DEFAULT_MAXIMUM_SIZE` 10 000 ranges) |
| `StandardLocalDateTimeConvertor` | `LocalDateTime`, `ZonedDateTime`, `OffsetDateTime`, `Instant`, `LocalDate`, `Date`, `YearMonth`, `Year`, epoch `Long` (milliseconds or seconds) and formatted `String` → `LocalDateTime` |
| `SnowflakeLocalDateTimeConvertor` | a Snowflake id (`Long`, or text through an `IdCodec`, base 62 by default) → the `LocalDateTime` it was issued, via `Layout.unpack` |
| `ShardingRange<C>`, `BoundType` | the range a range query asks for |
| `ExactCollection<E>` | the fixed-size node collection the algorithms return |

```java
ModCycle<Long> orders = new ModCycle<>(4, "t_order_");
orders.sharding(10L);                                   // t_order_2
orders.sharding(ShardingRange.closed(1L, 2L));          // [t_order_1, t_order_2]

IntervalTimeline logs = new IntervalTimeline("t_log_",
    ShardingRange.closed(LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 12, 31, 23, 59)),
    IntervalStep.of(ChronoUnit.MONTHS), DateTimeFormatter.ofPattern("yyyyMM"));
var issued = new SnowflakeLocalDateTimeConvertor(Layout.SNOWFLAKE_MS, ZoneId.of("Asia/Shanghai"));
logs.sharding(issued.toLocalDateTime(snowflakeId));    // t_log_202610
```

## Changes from 4.x

- `ShardingRange` replaces Guava's `Range` in `RangeSharding.sharding` and in the `IntervalTimeline` constructor; it
  keeps Guava's factory names (`closed`, `closedOpen`, `atLeast`, `all`, …) and semantics (`isConnected`, `isEmpty`), so
  porting code is a rename. The value type is bounded by `Comparable<? super T>`.
- `SnowflakeLocalDateTimeConvertor(Layout, ZoneId[, IdCodec])` replaces the constructor taking a
  `SnowflakeIdStateParser`; the zone is explicit.
- `CachedSharding` empties its cache once it reaches the maximum size (4.x: a Guava LRU cache), so lookups take no lock.
- Guava, Lombok and the error-prone annotations are gone.
