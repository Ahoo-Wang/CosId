# Benchmark scenarios

The scenarios of [10-performance-and-parity.md §1.2](../docs/architecture/10-performance-and-parity.md), implemented
twice with the same JMH class and method names so that [`compare`](compare) can match them row by row:

- [`baseline-4x/`](baseline-4x): on the released `me.ahoo.cosid:*:4.1.1` jars (Maven Central), never on a project of
  this build (the dependency rules forbid it: both use the package `me.ahoo.cosid`).
- [`v5/`](v5): on the 5.x modules.

Every class is `me.ahoo.cosid.benchmark.<Class>`; a method name ends with `_t<threads>` and carries a `@Threads`. P9 and
P10 measure one component; every other scenario measures ID generation end to end. The gate judges them differently
(D44, [10 §1.3](../docs/architecture/10-performance-and-parity.md)), by the `SCENARIOS` table of [`compare`](compare),
which every class here must be in (`compare_test.py` checks it). A p99 / p99.9 limit is never below 4.x + 100 ns (D50): in the
sample modes below a microsecond, a tail moves by the timer's resolution and the runner's noise more than by 10%. The 5.x side of every P7 runs the production path: the
generator a `CosIdRuntime` hands out, on the store as the runtime opens and wraps it (`V5.storeRuntime`).

| # | Class | Methods | Modes | 4.x configuration | 5.x configuration |
|---|---|---|---|---|---|
| P1 | `SnowflakeMillisBenchmark` | `generate_t1/t4/t16` | thrpt | `ClockSync(Leased(MillisecondSnowflakeId(1), FOREVER))`, as the 4.x starter builds it | `TimeBasedGenerator`, `Layout.SNOWFLAKE_MS`, `StaticSlot` 1 |
| P2 | `SnowflakeSecondsBenchmark` | `generate_t1/t16` | thrpt | the same with `SecondSnowflakeId` | `Layout.SNOWFLAKE_S` |
| P3 | `SnowflakeStringBenchmark` | `radix62_t1`, `friendly_t1` | thrpt | `StringSnowflakeId(…, Radix62IdConverter.PAD_START)`, `DefaultSnowflakeFriendlyId(…, Asia/Shanghai)` | `StringIdGenerator` with `Radix.of(62, 11, true)` / `Friendly.of(SNOWFLAKE_MS, Asia/Shanghai, true)` |
| P4 | `CosId80Benchmark` | `radix62_t1`, `friendly_t1` | thrpt | `ClockSync(Leased(Radix62CosIdGenerator(1)))`, `ClockSync(Leased(FriendlyCosIdGenerator(1, Asia/Shanghai, true)))` | `TimeBasedGenerator` on `Layout.COSID_80` + `PartsCodec.radix(…, 62)` / `PartsCodec.friendly` |
| P5 | `SegmentBenchmark` | `generate_t1/t4/t16/t64`, `@Param step` = 10, 100 | thrpt, sample (p99, p99.9) | `SegmentChainId(distributor)`; in-memory distributor at `step`, no simulated latency; other settings default | the `long` generator a `CosIdRuntime` hands out (counted, on its store wrapped by `ObservedStores`, as in production) on an in-memory range store, one atomic add per reservation (`V5.MemoryRangeStore`, `V5.segmentRuntime`); `BatchPolicy.DEFAULT` with `step` (and `maxBatch = BatchPolicy.defaultMaxBatch(step)`, `max(step × 1000, 2^20)` rounded to `step`) |
| P5 reference | `AtomicLongBenchmark` | `generate_t1/t4/t16/t64` | thrpt | — (5.x only) | one shared `AtomicLong`, `incrementAndGet`: the bound of a segment's hot path, one volatile read and one `getAndAdd`. Never judged: `compare` reports P5 on both sides as a share of it at the same threads; `run-smoke` runs it on 5.x right after P5, on the same runner |
| P6 | `SegmentNoPrefetchBenchmark` | `generate_t1` | thrpt | `DefaultSegmentId`, in-memory distributor, step 1, no ttl | the same runtime path as P5, step 1, `lookahead = 0`, `maxBatch = 1`, no `rangeTtl` |
| P7 (MySQL) | `MySqlChainIdBenchmark` | `generate_t1/t4/t16`, `@Param step` = 10, 100 | thrpt, sample | see [P7 / P10 on JDBC](#p7--p10-on-jdbc-r2-jdbc) | |
| P7 (Redis) | `RedisChainIdBenchmark` | `generate_t1/t4/t16`, `@Param step` = 10, 100 | thrpt, sample | `SegmentChainId(SpringRedisIdSegmentDistributor)`, offset 0, on a `StringRedisTemplate` (Lettuce) at `COSID_TEST_REDIS_URI` (default `redis://localhost:6379`) | the `long` generator a `CosIdRuntime` hands out (`V5.storeRuntime`), on the `redis` store opened from the same URI (`wait=auto`, timeout 2s), `V5.batch(step)` |
| P7 (MongoDB) | `MongoChainIdBenchmark` | `generate_t1/t4/t16`, `@Param step` = 10, 100 | thrpt, sample | `SegmentChainId(MongoIdSegmentDistributor)`, offset 0, on the replica set at `COSID_BENCHMARK_MONGODB_URI` | the `long` generator a `CosIdRuntime` hands out (`V5.storeRuntime`), on the `mongo` store opened from the same client (timeout 2s), `V5.batch(step)` |
| P7 (ZooKeeper) | `ZookeeperChainIdBenchmark` | `generate_t1/t4/t16`, `@Param step` = 10, 100 | thrpt, sample | `SegmentChainId(ZookeeperIdSegmentDistributor)` (no call timeout), on one in-process ZooKeeper 3.9 (curator-test) per trial | the `long` generator a `CosIdRuntime` hands out (`V5.storeRuntime`), on the `zookeeper` store opened from the same Curator client (timeout 2s), `V5.batch(step)` |
| P8 | `PeriodicSegmentBenchmark` | `generate_t1/t4` | thrpt, sample (p99, p99.9) | grouped `SegmentChainId` (`DefaultGroupedIdSegmentDistributor`, in-memory distributor), step 100; a `GroupBySupplier` keyed `yyyyMMdd` whose day lasts 2s of real time | `PeriodicSegmentGenerator` from a `CosIdRuntime` (its store wrapping and listener, R2-bench-01) on the same accelerated `yyyyMMdd` day, `BatchPolicy.DEFAULT` with step 100, prewarm 1/1440 of the day (the default minute, scaled) |
| P9 | `CodecBenchmark` | `radix62Encode/Decode_t1`, `radix36Encode/Decode_t1`, `friendlyEncode/Decode_t1` | thrpt | `Radix62IdConverter.PAD_START`, `Radix36IdConverter.PAD_START`, `SnowflakeFriendlyIdConverter` (Asia/Shanghai, padded) | `Radix.of(62, 11, true)`, `Radix.of(36, 13, true)`, `Friendly.of(SNOWFLAKE_MS, Asia/Shanghai, true)` |
| P10 (MySQL) | `MySqlStoreBenchmark` | `reserve_t1`, `acquire_t1`, `renew_t1` | avgt, sample | see [P7 / P10 on JDBC](#p7--p10-on-jdbc-r2-jdbc) | |
| P10 (MongoDB) | `MongoStoreBenchmark` | `reserve_t1`, `acquire_t1`, `renew_t1` | avgt, sample | `MongoIdSegmentDistributor.nextMaxId(100)`; `LeaseStoreMachineIdDistributor` on `MongoMachineLeaseStore` (timeout 10s): `distribute` + `revert` of a machine id (10 bits), `guard` of a held one | `MongoStore` (timeout 10s): `reserve(key, 100, 0)`; `acquire` + `release` (limit 1024), `renew` of a held lease; both on the replica set at `COSID_BENCHMARK_MONGODB_URI` (default `mongodb://localhost:27017`) |
| P10 (Redis) | `RedisStoreBenchmark` | `reserve_t1`, `acquireRelease_t1`, `renew_t1` | avgt, sample | `nextMaxId(100)`; `MachineLeaseManager.acquire` (a new instance, 10 machine bits) then `release`; `renew` of a held lease (`SpringRedisMachineLeaseStore`, command timeout 10s, as the 5.x store timeout) | `reserve(key, 100, 0)`; `acquire` (a new owner, limit 1024) then `release`; `renew` of a held lease |
| P10 (ZooKeeper) | `ZookeeperStoreBenchmark` | `reserve_t1`, `renew_t1`, `acquire_t1`, `acquire1024_t1` | avgt, sample | `ZookeeperIdSegmentDistributor.nextMaxId(100)` (no call timeout); `MachineLeaseManager` on `ZookeeperMachineLeaseStore` (call timeout 5s): `renew`, `acquire` + `release` at machine bit 10, empty or with 1023 ids held | `ZookeeperStore` (timeout 5s): `reserve(key, 100, 0)`; `renew`, `acquire` + `release` at limit 1024, empty or with 1023 slots held; both on one in-process ZooKeeper 3.9 (curator-test) per trial |
| P11 | `SpringStartBenchmark` | `startToFirstId_t1` | avgt | a Spring Boot context (`@EnableAutoConfiguration`, no web) started with `cosid-spring-boot-starter:4.1.1`, manual machine id 1 and the share snowflake generator, the first id, the context closed | the same with the 5.x starter: `cosid.machine.static.slot=1` and a `default` snowflake generator; both sides on the Spring Boot of `spring/*` (`spring-boot-starter`, so the same logging); `avgt` over repeated starts rather than `ss`, so one smoke run gives a stable mean |
| P12 | `IdFillerBenchmark` | `fill_t1` | thrpt | `DefaultAccessorRegistry.ensureId` on a new entity; `SegmentChainId` share, in-memory distributor, step 100 | `CosIdRuntime.filler().fill`; `default` segment generator on `InMemoryStore`, step 100 |
| P13 | `UuidV7Benchmark` | `generate_t1/t4/t16`, `text_t1` | thrpt | — (5.x only, D46) | the `UuidV7Generator` a `CosIdRuntime` hands out, as `UUID`s and as canonical text. No 4.x counterpart: `compare` reports it as a 5.x trend and never judges it; the gate runs it in the `snowflake` group |

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
benchmark/run-smoke P7 P10               # the store scenarios: every class of each (they need the stores, below)
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
4.x (P5 and P12 pending a clean rerun); it is for reading, not for gating. The release gate (10 §1.3) runs on CI, never
on a developer machine: below.

## The release gate (R5-A): the `Release Gate` workflow

The workflow (`.github/workflows/release-gate.yml`, run by hand with a `ref`, default `main`) measures one commit in
eight jobs, one per scenario group: `snowflake` (P1–P4), `segment` (P5, P6, P8), `codec-filler` (P9, P12),
`spring-start` (P11), and one per store for P7 / P10 — `mysql` (MySQL 8.4), `redis` (Redis 7), `mongo` (a MongoDB 7.0
single-node replica set) and `zookeeper` (ZooKeeper 3.9), each starting only its own store, so no other container
shares the runner. Every job uses the same pinned JDK 21 patch release (Temurin, `JAVA_VERSION` in the workflow; the
Gradle toolchain may use no other) and runs `benchmark/run-release` on its group, so 4.1.1 and 5.x of a scenario run
one after the other on the same runner.

`run-release` is `run-smoke --release` (3 forks, 5 x 2 s warmup, 5 x 2 s measurement, `-prof gc`) with these steps:

- It records the runner (CPU model, cores, memory, OS, image), the JDK, JMH and the commit.
- It refuses to start when a store the group needs does not answer.
- **The store scenarios run ABBA** (P7, P10: the `mysql`, `redis`, `mongo` and `zookeeper` groups): each side runs twice,
  4.x 5.x 5.x 4.x, with half the forks each (2 + 2 in release settings), and `benchmark/merge-results` joins the two
  runs of a side into one row: forks pooled, score, JMH's 99.9 % error and percentiles computed again from the raw
  data. Neither side then always runs right after the other on the store, which changes its tail latency (the P5
  investigation). Tolerances and judging are unchanged.
- **Runners are shared virtual machines**, so on CI (`CI=true`, or `--rerun`) a row that fails runs once more, both
  sides again in fresh forks, a store row ABBA again (`compare --failed` lists the rows, `compare --rerun` judges them on
  that re-run alone).
  A row that failed passes only if its re-run passes; the report shows both runs.
- Outside CI it still aborts (status 3) when the 1-minute load average is above 1.5 before a scenario; on CI the load
  of a runner says nothing about its neighbours, so that check is off.

The `Report` job joins the groups with `benchmark/aggregate-release` into one `report.md` (a table of the groups and
their runners, then each group's comparison), `baseline-4x.json`, `v5.json`, the re-run JSON, and `baseline-4x.md`
(the 4.1.1 side alone), uploaded as the `release-gate-report` artifact. The verdict and every group's comparison are
written to the job summary of the run, and the markdown alone (`report.md`, `baseline-4x.md`, without the raw JSON) is
the `release-gate-summary` artifact. The report is evidence, kept with the run: the workflow commits nothing and opens
no PR. To keep a result in the repository, commit the summary by hand (`report.md` to `docs/jmh/5.0/`,
`baseline-4x.md` to `docs/jmh/4.1.1/release-ci.md`, the clean 4.1.1 baseline, L1) and link the run. A FAIL row is
reported the same way: investigate it, fix a real regression, and run the gate again.

A push to a `release-gate-check/**` branch runs the same pipeline with smoke settings and re-runs every row
(`COSID_RELEASE_RERUN_ALL=true`), to check the workflow and the scripts; it is never a result. Its smoke rows are
noise, so it fails only when a group produced no result.
`benchmark/run-release --dry-run CodecBenchmark` checks the script locally in a minute or two, likewise never a
result.

### Paths the gate does not measure (R2-perf-02, R2-bench-02)

D21 covers the scenarios above. These published paths are in none of them, and nothing here is evidence of a
regression on them:

| Path | Why not in the gate |
|---|---|
| cosid-server `/ids/**` | the 4.x proxy had no id endpoint to compare with; a 5.x trend row would only report |
| `cosid-store-remote` with cosid-server (`/ranges:reserve`, `/slots:*`) | it has a 4.x counterpart (the proxy's segment and lease endpoints) but no scenario yet: a P7 / P10 remote group would be one, as a trend first |
| Jackson `@AsString`, MyBatis and Spring Data JDBC filling | P12 measures `IdFiller.fill` itself; the integrations add the library's own work around it |
| counting (`cosid.metrics.generated.enabled: true`) | one `LongAdder.increment` per id on the views of the runtime; off in every scenario |
| a Micrometer listener | P5, P7 run with the runtime's default listener; the Micrometer one records on range switches and lease changes only, never per id |
| the text of periodic segments | P8 measures `GroupedId`s; the `GroupedCodec` is P9's kind of work |
| `IdGenerators.uuid()` | `UUID.randomUUID().toString()`, the same code as the 4.x `UuidGenerator` |

P1–P4 build the `TimeBasedGenerator` themselves rather than through `CosIdRuntime`; without counting the runtime hands
out that same object, so they measure the production path. P8 goes through the runtime (R2-bench-01).

### P7 / P10 on JDBC (R2-JDBC)

`MySqlChainIdBenchmark` (P7: `generate_t1/t4/t16`, `@Param step` = 10, 100; thrpt, sample) and `MySqlStoreBenchmark`
(P10: `reserve_t1`, `acquire_t1`, `renew_t1`; avgt, sample) run on the MySQL of `COSID_TEST_MYSQL_URL` /
`_USERNAME` / `_PASSWORD` (default `jdbc:mysql://localhost:3306/cosid_db`, root / root) behind a HikariCP pool of 32;
start one first (`docker run -d -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=cosid_db -p 3306:3306 mysql:8.4`), then
`benchmark/run-smoke MySqlChainIdBenchmark MySqlStoreBenchmark`.

| # | 4.x | 5.x |
|---|---|---|
| P7 | `SegmentChainId` on `JdbcIdSegmentDistributor` at `step` | the `long` generator a `CosIdRuntime` hands out (`V5.storeRuntime`), on the `jdbc` store (MySQL dialect) opened from the same data source (timeout 2s), `BatchPolicy.DEFAULT` with `step` |
| P10 reserve | `JdbcIdSegmentDistributor.nextMaxId(100)` | `JdbcStore.ranges().reserve(key, 100, 0)` |
| P10 acquire | `MachineLeaseManager.acquire` on `JdbcMachineLeaseStore`, a new instance each call, released after it (not measured) | `acquire` by a new owner in a namespace of 1024 slots, released after it (not measured) |
| P10 renew | `MachineLeaseManager.renew` of one lease | `renew` of one lease |
