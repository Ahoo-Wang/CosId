# CosId 4.1.0 benchmark baseline

The scenarios of [benchmark/scenarios.md](../../../benchmark/scenarios.md) on the released 4.1.0 jars
(`benchmark/baseline-4x`), the baseline [benchmark/compare](../../../benchmark/compare) judges 5.x against
([10-performance-and-parity.md §1.3](../../architecture/10-performance-and-parity.md)).

## smoke-apple-m4-jdk21.json

A short run for development and PR smoke comparisons, **not** the release gate (that one runs both sides with
`-PjmhProfile=release` on a fixed physical machine and lands in `docs/jmh/5.0/`).

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
| P5 | SegmentBenchmark.generate_t1 | sample | 1 | 0.019 | 0.001 | us/op | 0.042 | 0.166 | 0.027 |
| P5 | SegmentBenchmark.generate_t1 | thrpt | 1 | 246.945 | 9.439 | ops/us |  |  | 0.027 |
| P5 | SegmentBenchmark.generate_t16 | sample | 16 | 2.899 | 0.414 | us/op | 2.124 | 28.864 | 0.349 |
| P5 | SegmentBenchmark.generate_t16 | thrpt | 16 | 26.131 | 46.792 | ops/us |  |  | 0.028 |
| P5 | SegmentBenchmark.generate_t4 | sample | 4 | 0.352 | 0.004 | us/op | 1.124 | 6.496 | 0.075 |
| P5 | SegmentBenchmark.generate_t4 | thrpt | 4 | 31.136 | 12.884 | ops/us |  |  | 0.240 |
| P5 | SegmentBenchmark.generate_t64 | sample | 64 | 10.129 | 0.833 | us/op | 1.916 | 19.200 | 1.441 |
| P5 | SegmentBenchmark.generate_t64 | thrpt | 64 | 24.092 | 27.857 | ops/us |  |  | 0.044 |
| P6 | SegmentNoPrefetchBenchmark.generate_t1 | thrpt | 1 | 20,985.033 | 3,091.064 | ops/ms |  |  | 40.425 |
| P9 | CodecBenchmark.friendlyDecode_t1 | thrpt | 1 | 2,334.467 | 237.002 | ops/ms |  |  | 2352.003 |
| P9 | CodecBenchmark.friendlyEncode_t1 | thrpt | 1 | 9,352.368 | 772.316 | ops/ms |  |  | 576.001 |
| P9 | CodecBenchmark.radix36Decode_t1 | thrpt | 1 | 82,371.897 | 10,274.479 | ops/ms |  |  | 0.000 |
| P9 | CodecBenchmark.radix36Encode_t1 | thrpt | 1 | 46,001.777 | 8,213.161 | ops/ms |  |  | 104.000 |
| P9 | CodecBenchmark.radix62Decode_t1 | thrpt | 1 | 76,219.472 | 18,891.864 | ops/ms |  |  | 0.000 |
| P9 | CodecBenchmark.radix62Encode_t1 | thrpt | 1 | 53,441.080 | 1,405.722 | ops/ms |  |  | 96.000 |
| P12 | IdFillerBenchmark.fill_t1 | thrpt | 1 | 47,405.078 | 5,163.615 | ops/ms |  |  | 64.019 |

Notes:

- P1–P3 are capped at about 4,096 ops/ms: the 12 sequence bits allow 4,096 ids per millisecond, so 4.x waits for the
  next millisecond once a millisecond is used up; P2 likewise at 2^22 ids per second (about 4,194 per ms). 5.x has
  the same bounds for `SNOWFLAKE_MS` and `SNOWFLAKE_S`.
- The multi-threaded P5 rows have wide errors with 3 short iterations; compare them with the release profile only.
- Paths in the JSON (`jvm`, `jvmArgs`) are shortened to `~/.gradle/jdks` and `<repo>`.
