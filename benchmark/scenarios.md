# Benchmark scenarios

The scenarios of [10-performance-and-parity.md §1.2](../docs/architecture/10-performance-and-parity.md), implemented
twice with the same JMH class and method names so that [`compare`](compare) can match them row by row:

- [`baseline-4x/`](baseline-4x): on the released `me.ahoo.cosid:*:4.1.0` jars (Maven Central), never on a project of
  this build (the dependency rules forbid it: both use the package `me.ahoo.cosid`).
- [`v5/`](v5): on the 5.x modules. Until R1 the 5.x generators are skeletons, so these compile but fail at setup.

Every class is `me.ahoo.cosid.benchmark.<Class>`; a method name ends with `_t<threads>` and carries a `@Threads`.

| # | Class | Methods | Modes | 4.x configuration | 5.x configuration |
|---|---|---|---|---|---|
| P1 | `SnowflakeMillisBenchmark` | `generate_t1/t4/t16` | thrpt | `ClockSync(Leased(MillisecondSnowflakeId(1), FOREVER))`, as the 4.x starter builds it | `TimeBasedGenerator`, `Layout.SNOWFLAKE_MS`, `StaticSlot` 1 |
| P2 | `SnowflakeSecondsBenchmark` | `generate_t1/t16` | thrpt | the same with `SecondSnowflakeId` | `Layout.SNOWFLAKE_S` |
| P3 | `SnowflakeStringBenchmark` | `radix62_t1`, `friendly_t1` | thrpt | `StringSnowflakeId(…, Radix62IdConverter.PAD_START)`, `DefaultSnowflakeFriendlyId(…, Asia/Shanghai)` | `StringIdGenerator` with `Radix.of(62, 11, true)` / `Friendly.of(SNOWFLAKE_MS, Asia/Shanghai, true)` |
| P4 | `CosId80Benchmark` | `radix62_t1`, `friendly_t1` | thrpt | `ClockSync(Leased(Radix62CosIdGenerator(1)))`, `ClockSync(Leased(FriendlyCosIdGenerator(1, Asia/Shanghai, true)))` | `TimeBasedGenerator` on `Layout.COSID_80` + `PartsCodec.radix(…, 62)` / `PartsCodec.friendly` |
| P5 | `SegmentBenchmark` | `generate_t1/t4/t16/t64`, `@Param step` = 10, 100 | thrpt, sample (p99, p99.9) | `SegmentChainId(distributor)`; in-memory distributor at `step`, no simulated latency; other settings default | `SegmentGenerator` on `InMemoryStore`; `BatchPolicy.DEFAULT` with `step` (and `maxBatch = step × 1000`) |
| P6 | `SegmentNoPrefetchBenchmark` | `generate_t1` | thrpt | `DefaultSegmentId`, in-memory distributor, step 1, no ttl | `SegmentGenerator`, step 1, `lookahead = 0`, `maxBatch = 1`, no `rangeTtl` |
| P7 | — | — | thrpt, sample | TODO(R2-JDBC, R2-Redis): `SegmentChainId` on MySQL / Redis | real MySQL / Redis stores |
| P8 | — | — | thrpt, sample | TODO(R1-B): grouped `SegmentChainId` by day, crossing a day boundary; step fixed explicitly on both sides | `PeriodicSegmentGenerator`, `YEAR_MONTH_DAY`, same step |
| P9 | `CodecBenchmark` | `radix62Encode/Decode_t1`, `radix36Encode/Decode_t1`, `friendlyEncode/Decode_t1` | thrpt | `Radix62IdConverter.PAD_START`, `Radix36IdConverter.PAD_START`, `SnowflakeFriendlyIdConverter` (Asia/Shanghai, padded) | `Radix.of(62, 11, true)`, `Radix.of(36, 13, true)`, `Friendly.of(SNOWFLAKE_MS, Asia/Shanghai, true)` |
| P10 | — | — | avgt, sample | TODO(R2-*): reserve, acquire, renew per backend | the same on 5.x stores |
| P11 | — | — | ss | TODO(R3-S): Spring context start to first id | the same with the 5.x starter |
| P12 | `IdFillerBenchmark` | `fill_t1` | thrpt | `DefaultAccessorRegistry.ensureId` on a new entity; `SegmentChainId` share, in-memory distributor, step 100 | `CosIdRuntime.filler().fill`; `default` segment generator on `InMemoryStore`, step 100 |

Choices that keep the comparison fair:

- **Every scenario fixes its parameters explicitly on both sides**, so both run the same workload; where the 4.x and
  5.x defaults differ (the segment step: 10 in 4.x, 100 in 5.x), the scenario sets the value or makes it a `@Param`.
  Layouts, machine id 1, the time zone (Asia/Shanghai) and the Radix widths are the same on both sides.
- The 4.x time-based generators are wrapped as the 4.x starter wraps them (`Leased*` and `ClockSync*`; `Counted*` only
  with metrics on, so not here), since the 5.x generator always does the window check and the clock regression
  handling.
- Every scenario reports `gc.alloc.rate.norm` (the JMH `gc` profiler): allocation per operation is a gate of its own.

## Reporting performance in a PR (R1 and later)

A PR that touches a scenario reports `benchmark/run-smoke`. It runs 4.x and 5.x **back to back in one invocation,
interleaved per scenario** (4.x then 5.x for each), and judges them with `compare --smoke`. The result therefore never
depends on numbers stored from another time or machine, which matters on a shared machine:

```bash
benchmark/run-smoke                      # the PR smoke set P1 P3 P5 P9 (10 §1.3)
benchmark/run-smoke P5 P12               # chosen scenarios (P-numbers or class names)
benchmark/run-smoke --release P5         # release JMH settings and release rules
```

Paste `build/benchmark-smoke/report.md` into the PR. The script builds both JMH jars, runs them on the Java 21
toolchain, waits rather than overlapping when another JMH run holds the machine-wide JMH lock, and exits with the status
of `compare`. A scenario that fails on one side (such as the 5.x skeletons before R1) has its rows reported missing.

## Running by hand

```bash
./gradlew :benchmark:baseline-4x:jmh                 # smoke profile: 1 fork, 2 x 1s warmup, 3 x 1s measurement
./gradlew :benchmark:v5:jmh -PjmhProfile=release     # release profile: 3 forks, 5 x 2s warmup, 5 x 2s measurement
./gradlew :benchmark:v5:jmh -PjmhIncludes='SegmentBenchmark.generate,IdFillerBenchmark'   # comma = or
benchmark/compare BASELINE.json CANDIDATE.json [--smoke]
python3 -I benchmark/compare_test.py                 # the tests of compare
```

Results land in `build/results/jmh/results.json` of each project. `docs/jmh/4.1.0/` keeps a reference smoke run of
4.x (P5 and P12 pending a clean rerun); it is for reading, not for gating. The release gate (10 §1.3) runs both sides
with `-PjmhProfile=release` on one fixed physical machine, one JDK 21 patch release, turbo off, one after the other,
and stores the JSON and the report under `docs/jmh/5.0/`.
