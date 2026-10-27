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
| P5 | `SegmentBenchmark` | `generate_t1/t4/t16/t64`, `@Param step` = 10, 100 | thrpt, sample (p99, p99.9) | `SegmentChainId(distributor)`; in-memory distributor at `step`, no simulated latency; other settings default | `SegmentGenerator` on an in-memory range store, one atomic add per reservation (`V5.MemoryRangeStore`, until R1-C's `InMemoryStore`); `BatchPolicy.DEFAULT` with `step` (and `maxBatch = step × 1000`) |
| P6 | `SegmentNoPrefetchBenchmark` | `generate_t1` | thrpt | `DefaultSegmentId`, in-memory distributor, step 1, no ttl | `SegmentGenerator`, step 1, `lookahead = 0`, `maxBatch = 1`, no `rangeTtl` |
| P7 | — | — | thrpt, sample | TODO(R2-JDBC, R2-Redis): `SegmentChainId` on MySQL / Redis | real MySQL / Redis stores |
| P7 (Redis) | `RedisChainIdBenchmark` | `generate_t1/t4/t16`, `@Param step` = 10, 100 | thrpt, sample | `SegmentChainId(SpringRedisIdSegmentDistributor)`, offset 0, on a `StringRedisTemplate` (Lettuce) at `COSID_TEST_REDIS_URI` (default `redis://localhost:6379`) | `SegmentGenerator` on the `redis` store opened from the same URI (`wait=auto`), `V5.batch(step)` |
| P8 | `PeriodicSegmentBenchmark` | `generate_t1/t4` | thrpt, sample (p99, p99.9) | grouped `SegmentChainId` (`DefaultGroupedIdSegmentDistributor`, in-memory distributor), step 100; a `GroupBySupplier` keyed `yyyyMMdd` whose day lasts 2s of real time | `PeriodicSegmentGenerator` on the same accelerated `yyyyMMdd` day, `BatchPolicy.DEFAULT` with step 100, prewarm 1/1440 of the day (the default minute, scaled) |
| P9 | `CodecBenchmark` | `radix62Encode/Decode_t1`, `radix36Encode/Decode_t1`, `friendlyEncode/Decode_t1` | thrpt | `Radix62IdConverter.PAD_START`, `Radix36IdConverter.PAD_START`, `SnowflakeFriendlyIdConverter` (Asia/Shanghai, padded) | `Radix.of(62, 11, true)`, `Radix.of(36, 13, true)`, `Friendly.of(SNOWFLAKE_MS, Asia/Shanghai, true)` |
| P10 | — | — | avgt, sample | TODO(R2-*): reserve, acquire, renew per backend | the same on 5.x stores |
| P10 (Redis) | `RedisStoreBenchmark` | `reserve_t1`, `acquireRelease_t1`, `renew_t1` | avgt, sample | `nextMaxId(100)`; `MachineLeaseManager.acquire` (a new instance, 10 machine bits) then `release`; `renew` of a held lease (`SpringRedisMachineLeaseStore`, command timeout 10s, as the 5.x store timeout) | `reserve(key, 100, 0)`; `acquire` (a new owner, limit 1024) then `release`; `renew` of a held lease |
| P10 (ZooKeeper) | `ZookeeperStoreBenchmark` | `reserve_t1`, `renew_t1`, `acquire_t1`, `acquire1024_t1` | avgt, sample | `ZookeeperIdSegmentDistributor.nextMaxId(100)` (no call timeout); `MachineLeaseManager` on `ZookeeperMachineLeaseStore` (call timeout 5s): `renew`, `acquire` + `release` at machine bit 10, empty or with 1023 ids held | `ZookeeperStore` (timeout 5s): `reserve(key, 100, 0)`; `renew`, `acquire` + `release` at limit 1024, empty or with 1023 slots held; both on one in-process ZooKeeper 3.9 (curator-test) per trial |
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

### P7 / P10 on JDBC (R2-JDBC)

`MySqlChainIdBenchmark` (P7: `generate_t1/t4/t16`, `@Param step` = 10, 100; thrpt, sample) and `MySqlStoreBenchmark`
(P10: `reserve_t1`, `acquire_t1`, `renew_t1`; avgt, sample) run on the MySQL of `COSID_TEST_MYSQL_URL` /
`_USERNAME` / `_PASSWORD` (default `jdbc:mysql://localhost:3306/cosid_db`, root / root) behind a HikariCP pool of 32;
start one first (`docker run -d -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=cosid_db -p 3306:3306 mysql:8.4`), then
`benchmark/run-smoke MySqlChainIdBenchmark MySqlStoreBenchmark`.

| # | 4.x | 5.x |
|---|---|---|
| P7 | `SegmentChainId` on `JdbcIdSegmentDistributor` at `step` | `SegmentGenerator` on `JdbcStore` (MySQL dialect), `BatchPolicy.DEFAULT` with `step` |
| P10 reserve | `JdbcIdSegmentDistributor.nextMaxId(100)` | `JdbcStore.ranges().reserve(key, 100, 0)` |
| P10 acquire | `MachineLeaseManager.acquire` on `JdbcMachineLeaseStore`, a new instance each call, released after it (not measured) | `acquire` by a new owner in a namespace of 1024 slots, released after it (not measured) |
| P10 renew | `MachineLeaseManager.renew` of one lease | `renew` of one lease |
