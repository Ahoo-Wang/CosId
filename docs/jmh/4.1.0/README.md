# CosId 4.1.0 benchmark baseline

The scenarios of [benchmark/scenarios.md](../../../benchmark/scenarios.md) on the released 4.1.0 jars
(`benchmark/baseline-4x`), the baseline [benchmark/compare](../../../benchmark/compare) judges 5.x against
([10-performance-and-parity.md §1.3](../../architecture/10-performance-and-parity.md)).

## smoke-apple-m4-jdk21.json

A short run for development and PR smoke comparisons, **not** the release gate. The release gate runs on CI (the
`Release Gate` workflow, see [benchmark/scenarios.md](../../../benchmark/scenarios.md)): its 4.1.0 side lands here as
`release-ci.json` and `release-ci.md`, the clean baseline with release settings, and the comparison in `docs/jmh/5.0/`.

| | |
|---|---|
| Command | `./gradlew :benchmark:baseline-4x:jmh` (smoke profile) |
| Settings | 1 fork, 2 × 1 s warmup, 3 × 1 s measurement, `-prof gc` |
| Machine | Apple M4 (10 cores: 4 performance + 6 efficiency), 16 GB, macOS 27.0.1; frequency scaling not controlled (Apple Silicon has no turbo switch); other work was running |
| JDK | Eclipse Temurin 21.0.12.1+1-LTS (aarch64), default GC (G1) |
| JMH | 1.37 |
| Date | 2026-10-26 |
| CosId | 4.1.0 from Maven Central |

| # | Benchmark | Mode | Threads | Score | Error | Unit | p99 | p99.9 | Alloc (B/op) |
|---|---|---|---|---|---|---|---|---|---|
| P1 | SnowflakeMillisBenchmark.generate_t1 | thrpt | 1 | 4,097.687 | 55.287 | ops/ms |  |  | 0.008 |
| P1 | SnowflakeMillisBenchmark.generate_t16 | thrpt | 16 | 4,097.572 | 26.452 | ops/ms |  |  | 0.010 |
| P1 | SnowflakeMillisBenchmark.generate_t4 | thrpt | 4 | 4,096.011 | 21.638 | ops/ms |  |  | 0.008 |
| P2 | SnowflakeSecondsBenchmark.generate_t1 | thrpt | 1 | 4,405.254 | 1,494.105 | ops/ms |  |  | 0.002 |
| P2 | SnowflakeSecondsBenchmark.generate_t16 | thrpt | 16 | 4,316.174 | 457.395 | ops/ms |  |  | 0.004 |
| P3 | SnowflakeStringBenchmark.friendly_t1 | thrpt | 1 | 4,097.623 | 21.162 | ops/ms |  |  | 376.008 |
| P3 | SnowflakeStringBenchmark.radix62_t1 | thrpt | 1 | 4,098.556 | 16.457 | ops/ms |  |  | 96.008 |
| P4 | CosId80Benchmark.friendly_t1 | thrpt | 1 | 7,745.251 | 2,174.488 | ops/ms |  |  | 523.638 |
| P4 | CosId80Benchmark.radix62_t1 | thrpt | 1 | 18,993.719 | 4,808.539 | ops/ms |  |  | 208.000 |
| P5 | SegmentBenchmark.generate_t1/t4/t16/t64, `@Param step` = 10, 100 | thrpt, sample | 1/4/16/64 | 待干净重跑 | | | | | |
| P6 | SegmentNoPrefetchBenchmark.generate_t1 | thrpt | 1 | 20,985.033 | 3,091.064 | ops/ms |  |  | 40.425 |
| P9 | CodecBenchmark.friendlyDecode_t1 | thrpt | 1 | 2,334.467 | 237.002 | ops/ms |  |  | 2352.003 |
| P9 | CodecBenchmark.friendlyEncode_t1 | thrpt | 1 | 9,352.368 | 772.316 | ops/ms |  |  | 576.001 |
| P9 | CodecBenchmark.radix36Decode_t1 | thrpt | 1 | 82,371.897 | 10,274.479 | ops/ms |  |  | 0.000 |
| P9 | CodecBenchmark.radix36Encode_t1 | thrpt | 1 | 46,001.777 | 8,213.161 | ops/ms |  |  | 104.000 |
| P9 | CodecBenchmark.radix62Decode_t1 | thrpt | 1 | 76,219.472 | 18,891.864 | ops/ms |  |  | 0.000 |
| P9 | CodecBenchmark.radix62Encode_t1 | thrpt | 1 | 53,441.080 | 1,405.722 | ops/ms |  |  | 96.000 |
| P12 | IdFillerBenchmark.fill_t1 (step 100) | thrpt | 1 | 待干净重跑 | | | | | |

Notes:

- P1–P3 are capped at about 4,096 ops/ms: the 12 sequence bits allow 4,096 ids per millisecond, so 4.x waits for the
  next millisecond once a millisecond is used up; P2 likewise at 2^22 ids per second (about 4,194 per ms). 5.x has
  the same bounds for `SNOWFLAKE_MS` and `SNOWFLAKE_S`.
- P5 and P12 are **待干净重跑** (pending a clean rerun): they now fix the segment step on both sides (P5 `@Param step`
  = 10, 100; P12 step 100), and the rerun on this shared machine was too noisy to store. PRs compare against a fresh
  4.x run instead (`benchmark/run-smoke`); the release gate reruns everything with release settings (`release-ci.*`).
- Paths in the JSON (`jvm`, `jvmArgs`) are shortened to `~/.gradle/jdks` and `<repo>`.
