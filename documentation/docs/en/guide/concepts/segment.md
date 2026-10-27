# Segments and Periodic Segments

A segment generator reserves a range `[start, end)` from a store in one atomic step, then hands the values out
locally, one atomic add each. Ids are dense and strictly increasing within one instance, and the store is touched only
once per range.

## The contract

```java
public record Range(long start, long end) {}

public interface RangeStore extends AutoCloseable {
    /** Atomically reserves [start, start + size); a new key starts at initialOffset. */
    Range reserve(String key, long size, long initialOffset);
    /** Optional: seal the key; every later reserve fails, the value is kept. */
    default boolean seal(String key) { return false; }
}
```

Every store passes the same contract tests:

| # | Invariant |
|---|---|
| S1 | ranges of one key never overlap and grow strictly in completion order |
| S2 | a new key's first range starts at `initialOffset`; of concurrent first reservations only one creates it |
| S3 | the returned range is the truth: the client never infers a boundary (4.x inferred `maxId - step`) |
| S4 | gaps are allowed (a timeout, a crash, a discarded range), duplicates never (D19) |
| S5 | values lie in `[0, Long.MAX_VALUE)`: past it `RangeExhaustedException`, never a wrap-around |
| S6 | a sealed key is never recreated; runtime code never deletes a key |

Keys are `{namespace}/{generator}`, and `{namespace}/{generator}/{period}` for periodic segments.

## SegmentGenerator and its batch policy

```java
public record BatchPolicy(long step,                 // default 100: the smallest reservation
                          long initialOffset,        // default 1, as in 4.x
                          Duration lookahead,        // default 1s: how long the ids at hand should last; 0 = no prefetch
                          long maxBatch,             // default max(step × 1000, 2^20), rounded to step
                          Duration reserveTimeout,   // default 3s: how long a caller waits for a refill
                          Optional<Duration> rangeTtl) {}  // default none: drop a range unused this long
```

- **Sized by a time budget.** A reservation is `min(maxBatch, roundUp(rate × lookahead, step))`, at least `step`. A busy
  generator takes bigger ranges and a quiet one small ranges, and a restart never burns a large block. 4.x's
  `safeDistance` / `maxPrefetchDistance` / prefetch period are replaced by this one number.
- **The hot path reads one volatile field and does one atomic add.** It takes no lock and allocates nothing. Only
  switching to the next range takes a lock, and that lock never covers I/O.
- **One refill in flight.** Reservations run on the refill executor (virtual threads by default). A caller that finds
  no ids left waits for that one reservation, at most `reserveTimeout`, then gets `RangeUnavailableException`. If the
  executor has not picked the job up, the caller runs it itself, so the store `timeout` must not exceed
  `reserveTimeout` (start-up checks it).
- **Failures back off.** After a failed refill, callers fail fast until a retry time (100ms doubling to 5s). There is
  no retry storm. Each failure reaches `onRefillFailed`.
- **A timed-out reservation is not cancelled.** Its range is used when it arrives; only the caller's wait timed out.
- `lookahead: 0` reserves only when the range runs out. Together with `step: 1` it matches 4.x's `DefaultSegmentId`.
- `range-ttl` keeps several instances "trend increasing": a range held longer than this is dropped (the 4.x segment
  ttl). Only then does the hot path check an expiry.

## Periodic segments

A periodic segment restarts its sequence each year, month or day, for bill numbers like `20261027-000042` (4.x's
grouped segments).

```java
public final class PeriodicSegmentGenerator implements GroupedIdGenerator, AutoCloseable {
    public GroupedId generate();          // GroupedId(period, value): ("20261027", 42)
}
public record PeriodicPolicy(Duration prewarm,             // default 1m
                             Optional<Duration> sealAfter) {} // default none
```

- `Period.YEAR`, `YEAR_MONTH`, `YEAR_MONTH_DAY` give the keys `2026`, `202610`, `20261027`; the **zone is explicit**
  (4.x used the server's default zone).
- It holds at most two segment generators: the current period and, from `prewarm` before the period ends, the next one.
  Crossing midnight therefore needs no store call on the hot path, so 4.x's stall at the period switch is gone.
- `seal-after` seals a past period's key that long after the period ended (use a day or more). An instance whose clock
  lags and still asks for the old period gets `RangeSealedException`: it fails rather than duplicate (S6).
- Keys are never deleted at run time (D23). Old periods are removed offline with
  `cosid-migration purge --before <period>`.
- The text form is the codec list: a first `date-prefix` item puts the period first, and the rest encode the value. A
  `period` item instead places the period after a business prefix (4.x `before-prefix: true`), or leaves it out of the
  text, which then cannot be decoded. See [where the period goes](../../reference/config/generators.md).

```yaml
cosid:
  generators:
    bill:
      strategy: periodic-segment
      store: db
      period: YEAR_MONTH_DAY
      zone: Asia/Shanghai
      codec:
        - date-prefix: { delimiter: "-" }
        - radix: { radix: 10, width: 6, pad-start: true }   # 20261027-000042
```

Read the text with `runtime.get("bill", GroupedCodec.class).encode(id)`, or let `@CosId` fill a `String` field.
