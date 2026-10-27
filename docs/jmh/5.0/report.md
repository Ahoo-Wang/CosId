# CosId 5.0 release gate

4.1.0 (`benchmark/baseline-4x`) and 5.0 (`benchmark/v5`), each scenario group in its own CI job and both sides of a group on the same runner, interleaved per scenario, judged by `benchmark/compare` with the rules of [10-performance-and-parity.md §1.3](../../architecture/10-performance-and-parity.md). A row that failed was run again on both sides in fresh forks and passes only if that re-run passes; both runs are shown.

Commit 614d5df92c64b33d67b1750814e2bded4a0d116b; release (3 forks, 5 x 2 s warmup, 5 x 2 s measurement); JMH 1.37, `-prof gc`; Linux 7.0.0-1017-azure (x86_64); unknown (a shared virtual machine: not controllable).

| Group | Scenarios | Runner | JDK | Rows | Re-run | Result |
|---|---|---|---|---|---|---|
| codec-filler | P9 P12 | AMD EPYC 9V45 96-Core Processor, 4 cores | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS | 7 | 4 | **FAIL** (4 rows) |
| mongo | MongoStoreBenchmark | AMD EPYC 9V74 80-Core Processor, 4 cores | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS | 6 | 2 | **FAIL** (1 rows) |
| mysql | MySqlChainIdBenchmark MySqlStoreBenchmark | Intel(R) Xeon(R) Platinum 8370C CPU @ 2.80GHz, 4 cores | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS | 18 | 7 | **FAIL** (7 rows) |
| redis | RedisChainIdBenchmark RedisStoreBenchmark | AMD EPYC 7763 64-Core Processor, 4 cores | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS | 18 | 0 | PASS |
| segment | P5 P6 P8 | AMD EPYC 9V74 80-Core Processor, 4 cores | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS | 21 | 1 | **FAIL** (1 rows) |
| snowflake | P1 P2 P3 P4 | AMD EPYC 9V45 96-Core Processor, 4 cores | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS | 9 | 0 | PASS |
| spring-start | P11 | Intel(R) Xeon(R) Platinum 8370C CPU @ 2.80GHz, 4 cores | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS | 1 | 0 | PASS |
| zookeeper | ZookeeperStoreBenchmark | AMD EPYC 7763 64-Core Processor, 4 cores | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS | 8 | 4 | **FAIL** (4 rows) |

**FAIL**: 5 of 8 groups failed.

## Rows that still fail after their re-run

Each goes to the phase that owns it; the suspected cause is filled in when the row is investigated.

| Group | Benchmark | Mode | Threads | Params | Owner | Suspected cause |
|---|---|---|---|---|---|---|
| codec-filler | CodecBenchmark.radix36Decode_t1 | thrpt | 1 |  | R1-A (time-based generators, codecs) | to investigate |
| codec-filler | CodecBenchmark.radix36Encode_t1 | thrpt | 1 |  | R1-A (time-based generators, codecs) | to investigate |
| codec-filler | CodecBenchmark.radix62Decode_t1 | thrpt | 1 |  | R1-A (time-based generators, codecs) | to investigate |
| codec-filler | CodecBenchmark.radix62Encode_t1 | thrpt | 1 |  | R1-A (time-based generators, codecs) | to investigate |
| mongo | MongoStoreBenchmark.reserve_t1 | avgt | 1 |  | R2-Mongo | to investigate |
| mysql | MySqlChainIdBenchmark.generate_t1 | sample | 1 | step=100 | R2-JDBC | to investigate |
| mysql | MySqlStoreBenchmark.acquire_t1 | avgt | 1 |  | R2-JDBC | to investigate |
| mysql | MySqlStoreBenchmark.acquire_t1 | sample | 1 |  | R2-JDBC | to investigate |
| mysql | MySqlStoreBenchmark.renew_t1 | avgt | 1 |  | R2-JDBC | to investigate |
| mysql | MySqlStoreBenchmark.renew_t1 | sample | 1 |  | R2-JDBC | to investigate |
| mysql | MySqlStoreBenchmark.reserve_t1 | avgt | 1 |  | R2-JDBC | to investigate |
| mysql | MySqlStoreBenchmark.reserve_t1 | sample | 1 |  | R2-JDBC | to investigate |
| segment | SegmentNoPrefetchBenchmark.generate_t1 | thrpt | 1 |  | R1-B (segments) | to investigate |
| zookeeper | ZookeeperStoreBenchmark.renew_t1 | avgt | 1 |  | R2-ZK | to investigate |
| zookeeper | ZookeeperStoreBenchmark.renew_t1 | sample | 1 |  | R2-ZK | to investigate |
| zookeeper | ZookeeperStoreBenchmark.reserve_t1 | avgt | 1 |  | R2-ZK | to investigate |
| zookeeper | ZookeeperStoreBenchmark.reserve_t1 | sample | 1 |  | R2-ZK | to investigate |

## codec-filler

4.1.0 (`benchmark/baseline-4x`) and 5.0 (`benchmark/v5`) on the same machine, interleaved per scenario (4.x then 5.x), judged by `benchmark/compare` with the rules of [10-performance-and-parity.md §1.3](../../architecture/10-performance-and-parity.md).

| | |
|---|---|
| Command | `benchmark/run-release P9 P12` |
| JMH | 1.37, release (3 forks, 5 x 2 s warmup, 5 x 2 s measurement), `-prof gc` |
| Date | 2026-10-27T15:48:25+00:00 |
| Cpu | AMD EPYC 9V45 96-Core Processor |
| Cores | 4 |
| Memory | 15 GB |
| Os | Linux 7.0.0-1017-azure (x86_64) |
| Frequency scaling | unknown (a shared virtual machine: not controllable) |
| Runner | GitHub Actions 1000413052, image ubuntu24 20261026.354.1, run 40534275762 |
| Jdk | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS |
| Cosid 4.x | 4.1.0 |
| Commit | 614d5df92c64b33d67b1750814e2bded4a0d116b |
| Load at start | 4.80 1.66 0.60 |

Load average (1 minute) before each scenario: CodecBenchmark 4.80, IdFillerBenchmark 1.20


| Benchmark | Mode | Threads | Params | Metric | 4.x | 5.x | Limit | Result |
|---|---|---|---|---|---|---|---|---|
| CodecBenchmark.friendlyDecode_t1 | thrpt | 1 |  | score (ops/ms) | 1,539 | 36,736 | >= 1,507 | PASS |
| CodecBenchmark.friendlyDecode_t1 | thrpt | 1 |  | alloc (B/op) | 2,360 | 0.000 | <= 2,373 | PASS |
| CodecBenchmark.friendlyEncode_t1 | thrpt | 1 |  | score (ops/ms) | 8,579 | 19,729 | >= 8,340 | PASS |
| CodecBenchmark.friendlyEncode_t1 | thrpt | 1 |  | alloc (B/op) | 576.000 | 120.000 | <= 576.000 | PASS |
| CodecBenchmark.radix36Decode_t1 | thrpt | 1 |  | score (ops/ms) | 73,938 | 64,643 | >= 73,230 | FAIL |
| CodecBenchmark.radix36Decode_t1 | thrpt | 1 |  | alloc (B/op) | 0.000 | 0.000 | <= 0.010 | PASS |
| CodecBenchmark.radix36Encode_t1 | thrpt | 1 |  | score (ops/ms) | 48,142 | 44,510 | >= 47,686 | FAIL |
| CodecBenchmark.radix36Encode_t1 | thrpt | 1 |  | alloc (B/op) | 104.000 | 88.000 | <= 104.000 | PASS |
| CodecBenchmark.radix62Decode_t1 | thrpt | 1 |  | score (ops/ms) | 81,604 | 68,844 | >= 80,452 | FAIL |
| CodecBenchmark.radix62Decode_t1 | thrpt | 1 |  | alloc (B/op) | 0.000 | 0.000 | <= 0.010 | PASS |
| CodecBenchmark.radix62Encode_t1 | thrpt | 1 |  | score (ops/ms) | 54,303 | 46,625 | >= 52,296 | FAIL |
| CodecBenchmark.radix62Encode_t1 | thrpt | 1 |  | alloc (B/op) | 96.000 | 88.000 | <= 96.000 | PASS |
| IdFillerBenchmark.fill_t1 | thrpt | 1 |  | score (ops/ms) | 35,484 | 114,671 | >= 34,885 | PASS |
| IdFillerBenchmark.fill_t1 | thrpt | 1 |  | alloc (B/op) | 64.001 | 24.001 | <= 64.001 | PASS |

### Re-run of the failed rows

Both sides again, in fresh forks; a row that failed above passes only if it passes here.

| Benchmark | Mode | Threads | Params | Metric | 4.x | 5.x | Limit | Result |
|---|---|---|---|---|---|---|---|---|
| CodecBenchmark.radix36Decode_t1 | thrpt | 1 |  | score (ops/ms) | 75,475 | 64,425 | >= 71,562 | FAIL |
| CodecBenchmark.radix36Decode_t1 | thrpt | 1 |  | alloc (B/op) | 0.000 | 0.000 | <= 0.010 | PASS |
| CodecBenchmark.radix36Encode_t1 | thrpt | 1 |  | score (ops/ms) | 49,384 | 46,141 | >= 48,390 | FAIL |
| CodecBenchmark.radix36Encode_t1 | thrpt | 1 |  | alloc (B/op) | 104.000 | 88.000 | <= 104.000 | PASS |
| CodecBenchmark.radix62Decode_t1 | thrpt | 1 |  | score (ops/ms) | 80,496 | 69,614 | >= 77,662 | FAIL |
| CodecBenchmark.radix62Decode_t1 | thrpt | 1 |  | alloc (B/op) | 0.000 | 0.000 | <= 0.010 | PASS |
| CodecBenchmark.radix62Encode_t1 | thrpt | 1 |  | score (ops/ms) | 53,023 | 46,317 | >= 52,637 | FAIL |
| CodecBenchmark.radix62Encode_t1 | thrpt | 1 |  | alloc (B/op) | 96.000 | 88.000 | <= 96.000 | PASS |

**FAIL**: 4 of 7 baseline rows failed (4 failed the first run and were re-run).

## mongo

4.1.0 (`benchmark/baseline-4x`) and 5.0 (`benchmark/v5`) on the same machine, interleaved per scenario (4.x then 5.x), judged by `benchmark/compare` with the rules of [10-performance-and-parity.md §1.3](../../architecture/10-performance-and-parity.md).

| | |
|---|---|
| Command | `benchmark/run-release MongoStoreBenchmark` |
| JMH | 1.37, release (3 forks, 5 x 2 s warmup, 5 x 2 s measurement), `-prof gc` |
| Date | 2026-10-27T15:49:14+00:00 |
| Cpu | AMD EPYC 9V74 80-Core Processor |
| Cores | 4 |
| Memory | 15 GB |
| Os | Linux 6.17.0-1022-azure (x86_64) |
| Frequency scaling | unknown (a shared virtual machine: not controllable) |
| Runner | GitHub Actions 1000413057, image ubuntu24 20261023.334.3, run 40534275762 |
| Jdk | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS |
| Cosid 4.x | 4.1.0 |
| Commit | 614d5df92c64b33d67b1750814e2bded4a0d116b |
| Load at start | 3.60 1.75 0.68 |

Load average (1 minute) before each scenario: MongoStoreBenchmark 3.60


| Benchmark | Mode | Threads | Params | Metric | 4.x | 5.x | Limit | Result |
|---|---|---|---|---|---|---|---|---|
| MongoStoreBenchmark.acquire_t1 | avgt | 1 |  | score (us/op) | 5,573 | 6,667 | <= 7,023 | PASS |
| MongoStoreBenchmark.acquire_t1 | avgt | 1 |  | alloc (B/op) | 146,368 | 120,074 | <= 147,705 | PASS |
| MongoStoreBenchmark.acquire_t1 | sample | 1 |  | p99.0 (us/op) | 87,345 | 51,072 | <= 88,176 | PASS |
| MongoStoreBenchmark.acquire_t1 | sample | 1 |  | p99.9 (us/op) | 234,787 | 206,670 | <= 235,617 | PASS |
| MongoStoreBenchmark.acquire_t1 | sample | 1 |  | alloc (B/op) | 147,803 | 120,803 | <= 150,508 | PASS |
| MongoStoreBenchmark.renew_t1 | avgt | 1 |  | score (us/op) | 2,266 | 3,044 | <= 3,013 | FAIL |
| MongoStoreBenchmark.renew_t1 | avgt | 1 |  | alloc (B/op) | 49,669 | 44,045 | <= 50,698 | PASS |
| MongoStoreBenchmark.renew_t1 | sample | 1 |  | p99.0 (us/op) | 25,014 | 10,055 | <= 25,336 | PASS |
| MongoStoreBenchmark.renew_t1 | sample | 1 |  | p99.9 (us/op) | 181,843 | 141,647 | <= 182,164 | PASS |
| MongoStoreBenchmark.renew_t1 | sample | 1 |  | alloc (B/op) | 49,841 | 44,718 | <= 50,838 | PASS |
| MongoStoreBenchmark.reserve_t1 | avgt | 1 |  | score (us/op) | 3,747 | 2,417 | <= 5,784 | PASS |
| MongoStoreBenchmark.reserve_t1 | avgt | 1 |  | alloc (B/op) | 32,747 | 33,377 | <= 33,019 | FAIL |
| MongoStoreBenchmark.reserve_t1 | sample | 1 |  | p99.0 (us/op) | 61,849 | 7,035 | <= 62,367 | PASS |
| MongoStoreBenchmark.reserve_t1 | sample | 1 |  | p99.9 (us/op) | 294,281 | 81,295 | <= 294,799 | PASS |
| MongoStoreBenchmark.reserve_t1 | sample | 1 |  | alloc (B/op) | 32,873 | 33,460 | <= 33,626 | PASS |

### Re-run of the failed rows

Both sides again, in fresh forks; a row that failed above passes only if it passes here.

| Benchmark | Mode | Threads | Params | Metric | 4.x | 5.x | Limit | Result |
|---|---|---|---|---|---|---|---|---|
| MongoStoreBenchmark.renew_t1 | avgt | 1 |  | score (us/op) | 2,497 | 2,568 | <= 3,211 | PASS |
| MongoStoreBenchmark.renew_t1 | avgt | 1 |  | alloc (B/op) | 49,297 | 43,349 | <= 50,348 | PASS |
| MongoStoreBenchmark.reserve_t1 | avgt | 1 |  | score (us/op) | 2,003 | 2,096 | <= 2,598 | PASS |
| MongoStoreBenchmark.reserve_t1 | avgt | 1 |  | alloc (B/op) | 31,715 | 33,262 | <= 32,104 | FAIL |

**FAIL**: 1 of 6 baseline rows failed (2 failed the first run and were re-run).

## mysql

4.1.0 (`benchmark/baseline-4x`) and 5.0 (`benchmark/v5`) on the same machine, interleaved per scenario (4.x then 5.x), judged by `benchmark/compare` with the rules of [10-performance-and-parity.md §1.3](../../architecture/10-performance-and-parity.md).

| | |
|---|---|
| Command | `benchmark/run-release MySqlChainIdBenchmark MySqlStoreBenchmark` |
| JMH | 1.37, release (3 forks, 5 x 2 s warmup, 5 x 2 s measurement), `-prof gc` |
| Date | 2026-10-27T15:50:15+00:00 |
| Cpu | Intel(R) Xeon(R) Platinum 8370C CPU @ 2.80GHz |
| Cores | 4 |
| Memory | 15 GB |
| Os | Linux 6.17.0-1022-azure (x86_64) |
| Frequency scaling | turbo ON (a shared virtual machine: not controllable) |
| Runner | GitHub Actions 1000413065, image ubuntu24 20261023.334.3, run 40534275762 |
| Jdk | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS |
| Cosid 4.x | 4.1.0 |
| Commit | 614d5df92c64b33d67b1750814e2bded4a0d116b |
| Load at start | 3.38 1.54 0.59 |

Load average (1 minute) before each scenario: MySqlChainIdBenchmark 3.43, MySqlStoreBenchmark 5.35


| Benchmark | Mode | Threads | Params | Metric | 4.x | 5.x | Limit | Result |
|---|---|---|---|---|---|---|---|---|
| MySqlChainIdBenchmark.generate_t1 | sample | 1 | step=10 | p99.0 (us/op) | 0.059 | 0.075 | <= 0.140 | PASS |
| MySqlChainIdBenchmark.generate_t1 | sample | 1 | step=10 | p99.9 (us/op) | 532.480 | 0.252 | <= 532.561 | PASS |
| MySqlChainIdBenchmark.generate_t1 | sample | 1 | step=10 | alloc (B/op) | 6.893 | 0.024 | <= 8.583 | PASS |
| MySqlChainIdBenchmark.generate_t1 | sample | 1 | step=100 | p99.0 (us/op) | 0.060 | 0.075 | <= 0.065 | FAIL |
| MySqlChainIdBenchmark.generate_t1 | sample | 1 | step=100 | p99.9 (us/op) | 0.211 | 0.299 | <= 0.216 | FAIL |
| MySqlChainIdBenchmark.generate_t1 | sample | 1 | step=100 | alloc (B/op) | 0.393 | 0.024 | <= 0.488 | PASS |
| MySqlChainIdBenchmark.generate_t1 | thrpt | 1 | step=10 | score (ops/us) | 4.042 | 158.126 | >= 0.494 | PASS |
| MySqlChainIdBenchmark.generate_t1 | thrpt | 1 | step=10 | alloc (B/op) | 5.590 | 0.022 | <= 8.485 | PASS |
| MySqlChainIdBenchmark.generate_t1 | thrpt | 1 | step=100 | score (ops/us) | 24.836 | 158.224 | >= 21.450 | PASS |
| MySqlChainIdBenchmark.generate_t1 | thrpt | 1 | step=100 | alloc (B/op) | 0.463 | 0.022 | <= 0.567 | PASS |
| MySqlChainIdBenchmark.generate_t16 | sample | 16 | step=10 | p99.0 (us/op) | 0.382 | 0.238 | <= 23.533 | PASS |
| MySqlChainIdBenchmark.generate_t16 | sample | 16 | step=10 | p99.9 (us/op) | 633.856 | 0.460 | <= 657.007 | PASS |
| MySqlChainIdBenchmark.generate_t16 | sample | 16 | step=10 | alloc (B/op) | 64.668 | 0.084 | <= 136.422 | PASS |
| MySqlChainIdBenchmark.generate_t16 | sample | 16 | step=100 | p99.0 (us/op) | 0.402 | 0.235 | <= 2.634 | PASS |
| MySqlChainIdBenchmark.generate_t16 | sample | 16 | step=100 | p99.9 (us/op) | 1.171 | 0.396 | <= 3.404 | PASS |
| MySqlChainIdBenchmark.generate_t16 | sample | 16 | step=100 | alloc (B/op) | 3.615 | 0.082 | <= 8.392 | PASS |
| MySqlChainIdBenchmark.generate_t16 | thrpt | 16 | step=10 | score (ops/us) | 25.351 | 48.912 | >= -0.231 | PASS |
| MySqlChainIdBenchmark.generate_t16 | thrpt | 16 | step=10 | alloc (B/op) | 112.766 | 0.025 | <= 186.490 | PASS |
| MySqlChainIdBenchmark.generate_t16 | thrpt | 16 | step=100 | score (ops/us) | 5.705 | 48.854 | >= 1.466 | PASS |
| MySqlChainIdBenchmark.generate_t16 | thrpt | 16 | step=100 | alloc (B/op) | 5.069 | 0.025 | <= 11.092 | PASS |
| MySqlChainIdBenchmark.generate_t4 | sample | 4 | step=10 | p99.0 (us/op) | 0.434 | 0.237 | <= 5.792 | PASS |
| MySqlChainIdBenchmark.generate_t4 | sample | 4 | step=10 | p99.9 (us/op) | 626.688 | 6.912 | <= 632.046 | PASS |
| MySqlChainIdBenchmark.generate_t4 | sample | 4 | step=10 | alloc (B/op) | 39.550 | 0.036 | <= 55.600 | PASS |
| MySqlChainIdBenchmark.generate_t4 | sample | 4 | step=100 | p99.0 (us/op) | 0.441 | 0.237 | <= 0.991 | PASS |
| MySqlChainIdBenchmark.generate_t4 | sample | 4 | step=100 | p99.9 (us/op) | 7.520 | 6.920 | <= 8.070 | PASS |
| MySqlChainIdBenchmark.generate_t4 | sample | 4 | step=100 | alloc (B/op) | 1.368 | 0.036 | <= 1.816 | PASS |
| MySqlChainIdBenchmark.generate_t4 | thrpt | 4 | step=10 | score (ops/us) | 0.367 | 48.722 | >= 0.184 | PASS |
| MySqlChainIdBenchmark.generate_t4 | thrpt | 4 | step=10 | alloc (B/op) | 47.564 | 0.025 | <= 65.820 | PASS |
| MySqlChainIdBenchmark.generate_t4 | thrpt | 4 | step=100 | score (ops/us) | 6.684 | 49.986 | >= 4.181 | PASS |
| MySqlChainIdBenchmark.generate_t4 | thrpt | 4 | step=100 | alloc (B/op) | 2.142 | 0.024 | <= 3.161 | PASS |
| MySqlStoreBenchmark.acquire_t1 | avgt | 1 |  | score (us/op) | 1,301 | 2,192 | <= 1,314 | FAIL |
| MySqlStoreBenchmark.acquire_t1 | avgt | 1 |  | alloc (B/op) | 50,588 | 63,220 | <= 50,735 | FAIL |
| MySqlStoreBenchmark.acquire_t1 | sample | 1 |  | p99.0 (us/op) | 1,741 | 3,236 | <= 1,745 | FAIL |
| MySqlStoreBenchmark.acquire_t1 | sample | 1 |  | p99.9 (us/op) | 2,945 | 4,194 | <= 2,949 | FAIL |
| MySqlStoreBenchmark.acquire_t1 | sample | 1 |  | alloc (B/op) | 50,749 | 63,508 | <= 50,927 | FAIL |
| MySqlStoreBenchmark.renew_t1 | avgt | 1 |  | score (us/op) | 735.480 | 674.334 | <= 749.930 | PASS |
| MySqlStoreBenchmark.renew_t1 | avgt | 1 |  | alloc (B/op) | 16,410 | 21,142 | <= 16,474 | FAIL |
| MySqlStoreBenchmark.renew_t1 | sample | 1 |  | p99.0 (us/op) | 1,038 | 1,015 | <= 1,040 | PASS |
| MySqlStoreBenchmark.renew_t1 | sample | 1 |  | p99.9 (us/op) | 1,738 | 1,369 | <= 1,740 | PASS |
| MySqlStoreBenchmark.renew_t1 | sample | 1 |  | alloc (B/op) | 16,417 | 21,198 | <= 16,463 | FAIL |
| MySqlStoreBenchmark.reserve_t1 | avgt | 1 |  | score (us/op) | 550.679 | 557.650 | <= 558.616 | PASS |
| MySqlStoreBenchmark.reserve_t1 | avgt | 1 |  | alloc (B/op) | 8,618 | 14,367 | <= 8,673 | FAIL |
| MySqlStoreBenchmark.reserve_t1 | sample | 1 |  | p99.0 (us/op) | 801.792 | 741.376 | <= 803.146 | PASS |
| MySqlStoreBenchmark.reserve_t1 | sample | 1 |  | p99.9 (us/op) | 1,217 | 1,109 | <= 1,218 | PASS |
| MySqlStoreBenchmark.reserve_t1 | sample | 1 |  | alloc (B/op) | 8,660 | 14,385 | <= 8,694 | FAIL |

### Re-run of the failed rows

Both sides again, in fresh forks; a row that failed above passes only if it passes here.

| Benchmark | Mode | Threads | Params | Metric | 4.x | 5.x | Limit | Result |
|---|---|---|---|---|---|---|---|---|
| MySqlChainIdBenchmark.generate_t1 | sample | 1 | step=100 | p99.0 (us/op) | 0.065 | 0.079 | <= 0.078 | FAIL |
| MySqlChainIdBenchmark.generate_t1 | sample | 1 | step=100 | p99.9 (us/op) | 0.321 | 0.458 | <= 0.334 | FAIL |
| MySqlChainIdBenchmark.generate_t1 | sample | 1 | step=100 | alloc (B/op) | 0.364 | 0.024 | <= 0.430 | PASS |
| MySqlStoreBenchmark.acquire_t1 | avgt | 1 |  | score (us/op) | 1,341 | 2,271 | <= 1,381 | FAIL |
| MySqlStoreBenchmark.acquire_t1 | avgt | 1 |  | alloc (B/op) | 50,611 | 63,358 | <= 50,832 | FAIL |
| MySqlStoreBenchmark.acquire_t1 | sample | 1 |  | p99.0 (us/op) | 1,691 | 3,432 | <= 1,695 | FAIL |
| MySqlStoreBenchmark.acquire_t1 | sample | 1 |  | p99.9 (us/op) | 3,157 | 5,059 | <= 3,161 | FAIL |
| MySqlStoreBenchmark.acquire_t1 | sample | 1 |  | alloc (B/op) | 50,523 | 63,527 | <= 50,622 | FAIL |
| MySqlStoreBenchmark.renew_t1 | avgt | 1 |  | score (us/op) | 753.869 | 699.949 | <= 770.500 | PASS |
| MySqlStoreBenchmark.renew_t1 | avgt | 1 |  | alloc (B/op) | 16,389 | 21,171 | <= 16,437 | FAIL |
| MySqlStoreBenchmark.renew_t1 | sample | 1 |  | p99.0 (us/op) | 1,022 | 1,063 | <= 1,024 | FAIL |
| MySqlStoreBenchmark.renew_t1 | sample | 1 |  | p99.9 (us/op) | 1,561 | 1,546 | <= 1,562 | PASS |
| MySqlStoreBenchmark.renew_t1 | sample | 1 |  | alloc (B/op) | 16,500 | 21,281 | <= 16,569 | FAIL |
| MySqlStoreBenchmark.reserve_t1 | avgt | 1 |  | score (us/op) | 547.443 | 557.667 | <= 551.230 | FAIL |
| MySqlStoreBenchmark.reserve_t1 | avgt | 1 |  | alloc (B/op) | 8,637 | 14,431 | <= 8,677 | FAIL |
| MySqlStoreBenchmark.reserve_t1 | sample | 1 |  | p99.0 (us/op) | 786.432 | 779.264 | <= 787.589 | PASS |
| MySqlStoreBenchmark.reserve_t1 | sample | 1 |  | p99.9 (us/op) | 1,232 | 1,175 | <= 1,234 | PASS |
| MySqlStoreBenchmark.reserve_t1 | sample | 1 |  | alloc (B/op) | 8,670 | 14,423 | <= 8,707 | FAIL |

**FAIL**: 7 of 18 baseline rows failed (7 failed the first run and were re-run).

## redis

4.1.0 (`benchmark/baseline-4x`) and 5.0 (`benchmark/v5`) on the same machine, interleaved per scenario (4.x then 5.x), judged by `benchmark/compare` with the rules of [10-performance-and-parity.md §1.3](../../architecture/10-performance-and-parity.md).

| | |
|---|---|
| Command | `benchmark/run-release RedisChainIdBenchmark RedisStoreBenchmark` |
| JMH | 1.37, release (3 forks, 5 x 2 s warmup, 5 x 2 s measurement), `-prof gc` |
| Date | 2026-10-27T15:49:51+00:00 |
| Cpu | AMD EPYC 7763 64-Core Processor |
| Cores | 4 |
| Memory | 15 GB |
| Os | Linux 6.17.0-1022-azure (x86_64) |
| Frequency scaling | unknown (a shared virtual machine: not controllable) |
| Runner | GitHub Actions 1000413064, image ubuntu24 20261023.334.3, run 40534275762 |
| Jdk | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS |
| Cosid 4.x | 4.1.0 |
| Commit | 614d5df92c64b33d67b1750814e2bded4a0d116b |
| Load at start | 3.79 1.59 0.60 |

Load average (1 minute) before each scenario: RedisChainIdBenchmark 3.64, RedisStoreBenchmark 5.40


| Benchmark | Mode | Threads | Params | Metric | 4.x | 5.x | Limit | Result |
|---|---|---|---|---|---|---|---|---|
| RedisChainIdBenchmark.generate_t1 | sample | 1 | step=10 | p99.0 (us/op) | 214.784 | 0.041 | <= 214.910 | PASS |
| RedisChainIdBenchmark.generate_t1 | sample | 1 | step=10 | p99.9 (us/op) | 251.392 | 0.131 | <= 251.518 | PASS |
| RedisChainIdBenchmark.generate_t1 | sample | 1 | step=10 | alloc (B/op) | 58.392 | 0.006 | <= 91.778 | PASS |
| RedisChainIdBenchmark.generate_t1 | sample | 1 | step=100 | p99.0 (us/op) | 0.069 | 0.041 | <= 0.085 | PASS |
| RedisChainIdBenchmark.generate_t1 | sample | 1 | step=100 | p99.9 (us/op) | 0.268 | 0.120 | <= 0.283 | PASS |
| RedisChainIdBenchmark.generate_t1 | sample | 1 | step=100 | alloc (B/op) | 3.118 | 0.006 | <= 4.249 | PASS |
| RedisChainIdBenchmark.generate_t1 | thrpt | 1 | step=10 | score (ops/us) | 0.521 | 404.677 | >= 0.335 | PASS |
| RedisChainIdBenchmark.generate_t1 | thrpt | 1 | step=10 | alloc (B/op) | 55.231 | 0.005 | <= 80.201 | PASS |
| RedisChainIdBenchmark.generate_t1 | thrpt | 1 | step=100 | score (ops/us) | 6.173 | 404.505 | >= 3.773 | PASS |
| RedisChainIdBenchmark.generate_t1 | thrpt | 1 | step=100 | alloc (B/op) | 4.332 | 0.005 | <= 5.747 | PASS |
| RedisChainIdBenchmark.generate_t16 | sample | 16 | step=10 | p99.0 (us/op) | 0.401 | 0.251 | <= 0.612 | PASS |
| RedisChainIdBenchmark.generate_t16 | sample | 16 | step=10 | p99.9 (us/op) | 12.368 | 0.441 | <= 12.579 | PASS |
| RedisChainIdBenchmark.generate_t16 | sample | 16 | step=10 | alloc (B/op) | 0.534 | 0.070 | <= 0.757 | PASS |
| RedisChainIdBenchmark.generate_t16 | sample | 16 | step=100 | p99.0 (us/op) | 0.391 | 0.251 | <= 0.544 | PASS |
| RedisChainIdBenchmark.generate_t16 | sample | 16 | step=100 | p99.9 (us/op) | 2.192 | 0.451 | <= 2.345 | PASS |
| RedisChainIdBenchmark.generate_t16 | sample | 16 | step=100 | alloc (B/op) | 0.168 | 0.070 | <= 0.205 | PASS |
| RedisChainIdBenchmark.generate_t16 | thrpt | 16 | step=10 | score (ops/us) | 16.348 | 48.812 | >= 15.031 | PASS |
| RedisChainIdBenchmark.generate_t16 | thrpt | 16 | step=10 | alloc (B/op) | 0.593 | 0.008 | <= 0.725 | PASS |
| RedisChainIdBenchmark.generate_t16 | thrpt | 16 | step=100 | score (ops/us) | 23.045 | 48.675 | >= 21.886 | PASS |
| RedisChainIdBenchmark.generate_t16 | thrpt | 16 | step=100 | alloc (B/op) | 0.061 | 0.008 | <= 0.103 | PASS |
| RedisChainIdBenchmark.generate_t4 | sample | 4 | step=10 | p99.0 (us/op) | 0.401 | 0.251 | <= 18.494 | PASS |
| RedisChainIdBenchmark.generate_t4 | sample | 4 | step=10 | p99.9 (us/op) | 236.800 | 0.711 | <= 254.893 | PASS |
| RedisChainIdBenchmark.generate_t4 | sample | 4 | step=10 | alloc (B/op) | 54.859 | 0.022 | <= 143.202 | PASS |
| RedisChainIdBenchmark.generate_t4 | sample | 4 | step=100 | p99.0 (us/op) | 0.400 | 0.260 | <= 1.515 | PASS |
| RedisChainIdBenchmark.generate_t4 | sample | 4 | step=100 | p99.9 (us/op) | 11.760 | 0.681 | <= 12.875 | PASS |
| RedisChainIdBenchmark.generate_t4 | sample | 4 | step=100 | alloc (B/op) | 0.818 | 0.022 | <= 3.116 | PASS |
| RedisChainIdBenchmark.generate_t4 | thrpt | 4 | step=10 | score (ops/us) | 1.508 | 49.237 | >= -2.240 | PASS |
| RedisChainIdBenchmark.generate_t4 | thrpt | 4 | step=10 | alloc (B/op) | 124.009 | 0.008 | <= 193.233 | PASS |
| RedisChainIdBenchmark.generate_t4 | thrpt | 4 | step=100 | score (ops/us) | 3.695 | 49.209 | >= -0.117 | PASS |
| RedisChainIdBenchmark.generate_t4 | thrpt | 4 | step=100 | alloc (B/op) | 10.840 | 0.008 | <= 18.452 | PASS |
| RedisStoreBenchmark.acquireRelease_t1 | avgt | 1 |  | score (us/op) | 1,005 | 481.088 | <= 1,018 | PASS |
| RedisStoreBenchmark.acquireRelease_t1 | avgt | 1 |  | alloc (B/op) | 25,062 | 7,506 | <= 25,318 | PASS |
| RedisStoreBenchmark.acquireRelease_t1 | sample | 1 |  | p99.0 (us/op) | 1,298 | 640.000 | <= 1,301 | PASS |
| RedisStoreBenchmark.acquireRelease_t1 | sample | 1 |  | p99.9 (us/op) | 2,463 | 1,033 | <= 2,466 | PASS |
| RedisStoreBenchmark.acquireRelease_t1 | sample | 1 |  | alloc (B/op) | 25,172 | 8,433 | <= 25,576 | PASS |
| RedisStoreBenchmark.renew_t1 | avgt | 1 |  | score (us/op) | 483.771 | 224.968 | <= 490.991 | PASS |
| RedisStoreBenchmark.renew_t1 | avgt | 1 |  | alloc (B/op) | 10,038 | 3,399 | <= 10,164 | PASS |
| RedisStoreBenchmark.renew_t1 | sample | 1 |  | p99.0 (us/op) | 611.328 | 287.744 | <= 612.194 | PASS |
| RedisStoreBenchmark.renew_t1 | sample | 1 |  | p99.9 (us/op) | 1,161 | 562.948 | <= 1,162 | PASS |
| RedisStoreBenchmark.renew_t1 | sample | 1 |  | alloc (B/op) | 10,126 | 3,721 | <= 10,183 | PASS |
| RedisStoreBenchmark.reserve_t1 | avgt | 1 |  | score (us/op) | 209.289 | 196.067 | <= 211.176 | PASS |
| RedisStoreBenchmark.reserve_t1 | avgt | 1 |  | alloc (B/op) | 5,308 | 2,324 | <= 5,343 | PASS |
| RedisStoreBenchmark.reserve_t1 | sample | 1 |  | p99.0 (us/op) | 254.976 | 248.832 | <= 255.231 | PASS |
| RedisStoreBenchmark.reserve_t1 | sample | 1 |  | p99.9 (us/op) | 526.798 | 504.792 | <= 527.053 | PASS |
| RedisStoreBenchmark.reserve_t1 | sample | 1 |  | alloc (B/op) | 5,314 | 2,346 | <= 5,373 | PASS |

**PASS**: 0 of 18 baseline rows failed.

## segment

4.1.0 (`benchmark/baseline-4x`) and 5.0 (`benchmark/v5`) on the same machine, interleaved per scenario (4.x then 5.x), judged by `benchmark/compare` with the rules of [10-performance-and-parity.md §1.3](../../architecture/10-performance-and-parity.md).

| | |
|---|---|
| Command | `benchmark/run-release P5 P6 P8` |
| JMH | 1.37, release (3 forks, 5 x 2 s warmup, 5 x 2 s measurement), `-prof gc` |
| Date | 2026-10-27T15:49:39+00:00 |
| Cpu | AMD EPYC 9V74 80-Core Processor |
| Cores | 4 |
| Memory | 15 GB |
| Os | Linux 6.17.0-1022-azure (x86_64) |
| Frequency scaling | unknown (a shared virtual machine: not controllable) |
| Runner | GitHub Actions 1000413066, image ubuntu24 20261023.334.3, run 40534275762 |
| Jdk | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS |
| Cosid 4.x | 4.1.0 |
| Commit | 614d5df92c64b33d67b1750814e2bded4a0d116b |
| Load at start | 2.87 1.20 0.45 |

Load average (1 minute) before each scenario: SegmentBenchmark 2.87, SegmentNoPrefetchBenchmark 53.04, PeriodicSegmentBenchmark 8.02


| Benchmark | Mode | Threads | Params | Metric | 4.x | 5.x | Limit | Result |
|---|---|---|---|---|---|---|---|---|
| PeriodicSegmentBenchmark.generate_t1 | sample | 1 |  | p99.0 (us/op) | 0.070 | 0.061 | <= 0.073 | PASS |
| PeriodicSegmentBenchmark.generate_t1 | sample | 1 |  | p99.9 (us/op) | 1.129 | 0.190 | <= 1.131 | PASS |
| PeriodicSegmentBenchmark.generate_t1 | sample | 1 |  | alloc (B/op) | 48.005 | 24.003 | <= 48.006 | PASS |
| PeriodicSegmentBenchmark.generate_t1 | thrpt | 1 |  | score (ops/us) | 28.401 | 33.609 | >= 28.309 | PASS |
| PeriodicSegmentBenchmark.generate_t1 | thrpt | 1 |  | alloc (B/op) | 48.002 | 24.001 | <= 48.003 | PASS |
| PeriodicSegmentBenchmark.generate_t4 | sample | 4 |  | p99.0 (us/op) | 0.271 | 0.130 | <= 0.289 | PASS |
| PeriodicSegmentBenchmark.generate_t4 | sample | 4 |  | p99.9 (us/op) | 5.888 | 0.291 | <= 5.906 | PASS |
| PeriodicSegmentBenchmark.generate_t4 | sample | 4 |  | alloc (B/op) | 48.019 | 24.007 | <= 48.021 | PASS |
| PeriodicSegmentBenchmark.generate_t4 | thrpt | 4 |  | score (ops/us) | 31.611 | 70.036 | >= 30.353 | PASS |
| PeriodicSegmentBenchmark.generate_t4 | thrpt | 4 |  | alloc (B/op) | 48.001 | 24.001 | <= 48.001 | PASS |
| SegmentBenchmark.generate_t1 | sample | 1 | step=10 | p99.0 (us/op) | 0.041 | 0.040 | <= 0.042 | PASS |
| SegmentBenchmark.generate_t1 | sample | 1 | step=10 | p99.9 (us/op) | 0.170 | 0.140 | <= 0.171 | PASS |
| SegmentBenchmark.generate_t1 | sample | 1 | step=10 | alloc (B/op) | 0.036 | 0.001 | <= 0.050 | PASS |
| SegmentBenchmark.generate_t1 | sample | 1 | step=100 | p99.0 (us/op) | 0.041 | 0.031 | <= 0.042 | PASS |
| SegmentBenchmark.generate_t1 | sample | 1 | step=100 | p99.9 (us/op) | 0.140 | 0.140 | <= 0.141 | PASS |
| SegmentBenchmark.generate_t1 | sample | 1 | step=100 | alloc (B/op) | 0.002 | 0.001 | <= 0.010 | PASS |
| SegmentBenchmark.generate_t1 | thrpt | 1 | step=10 | score (ops/us) | 178.608 | 467.809 | >= 174.904 | PASS |
| SegmentBenchmark.generate_t1 | thrpt | 1 | step=10 | alloc (B/op) | 0.025 | 0.000 | < 1.0 | PASS |
| SegmentBenchmark.generate_t1 | thrpt | 1 | step=100 | score (ops/us) | 180.412 | 470.540 | >= 176.215 | PASS |
| SegmentBenchmark.generate_t1 | thrpt | 1 | step=100 | alloc (B/op) | 0.002 | 0.000 | < 1.0 | PASS |
| SegmentBenchmark.generate_t16 | sample | 16 | step=10 | p99.0 (us/op) | 0.230 | 0.160 | <= 0.320 | PASS |
| SegmentBenchmark.generate_t16 | sample | 16 | step=10 | p99.9 (us/op) | 1.512 | 0.210 | <= 1.602 | PASS |
| SegmentBenchmark.generate_t16 | sample | 16 | step=10 | alloc (B/op) | 0.085 | 0.029 | <= 0.116 | PASS |
| SegmentBenchmark.generate_t16 | sample | 16 | step=100 | p99.0 (us/op) | 0.221 | 0.161 | <= 0.305 | PASS |
| SegmentBenchmark.generate_t16 | sample | 16 | step=100 | p99.9 (us/op) | 0.320 | 0.211 | <= 0.404 | PASS |
| SegmentBenchmark.generate_t16 | sample | 16 | step=100 | alloc (B/op) | 0.045 | 0.030 | <= 0.048 | PASS |
| SegmentBenchmark.generate_t16 | thrpt | 16 | step=10 | score (ops/us) | 43.089 | 73.098 | >= 38.715 | PASS |
| SegmentBenchmark.generate_t16 | thrpt | 16 | step=10 | alloc (B/op) | 0.025 | 0.001 | < 1.0 | PASS |
| SegmentBenchmark.generate_t16 | thrpt | 16 | step=100 | score (ops/us) | 42.601 | 78.002 | >= 42.006 | PASS |
| SegmentBenchmark.generate_t16 | thrpt | 16 | step=100 | alloc (B/op) | 0.002 | 0.001 | < 1.0 | PASS |
| SegmentBenchmark.generate_t4 | sample | 4 | step=10 | p99.0 (us/op) | 0.221 | 0.161 | <= 0.244 | PASS |
| SegmentBenchmark.generate_t4 | sample | 4 | step=10 | p99.9 (us/op) | 2.392 | 0.220 | <= 2.415 | PASS |
| SegmentBenchmark.generate_t4 | sample | 4 | step=10 | alloc (B/op) | 0.025 | 0.007 | <= 0.030 | PASS |
| SegmentBenchmark.generate_t4 | sample | 4 | step=100 | p99.0 (us/op) | 0.221 | 0.160 | <= 0.236 | PASS |
| SegmentBenchmark.generate_t4 | sample | 4 | step=100 | p99.9 (us/op) | 0.361 | 0.211 | <= 0.376 | PASS |
| SegmentBenchmark.generate_t4 | sample | 4 | step=100 | alloc (B/op) | 0.011 | 0.007 | <= 0.012 | PASS |
| SegmentBenchmark.generate_t4 | thrpt | 4 | step=10 | score (ops/us) | 42.980 | 77.784 | >= 42.473 | PASS |
| SegmentBenchmark.generate_t4 | thrpt | 4 | step=10 | alloc (B/op) | 0.015 | 0.000 | < 1.0 | PASS |
| SegmentBenchmark.generate_t4 | thrpt | 4 | step=100 | score (ops/us) | 43.406 | 79.742 | >= 42.644 | PASS |
| SegmentBenchmark.generate_t4 | thrpt | 4 | step=100 | alloc (B/op) | 0.001 | 0.000 | < 1.0 | PASS |
| SegmentBenchmark.generate_t64 | sample | 64 | step=10 | p99.0 (us/op) | 0.230 | 0.160 | <= 0.467 | PASS |
| SegmentBenchmark.generate_t64 | sample | 64 | step=10 | p99.9 (us/op) | 2.812 | 0.200 | <= 3.049 | PASS |
| SegmentBenchmark.generate_t64 | sample | 64 | step=10 | alloc (B/op) | 0.306 | 0.072 | <= 0.347 | PASS |
| SegmentBenchmark.generate_t64 | sample | 64 | step=100 | p99.0 (us/op) | 0.221 | 0.160 | <= 0.435 | PASS |
| SegmentBenchmark.generate_t64 | sample | 64 | step=100 | p99.9 (us/op) | 0.320 | 0.200 | <= 0.534 | PASS |
| SegmentBenchmark.generate_t64 | sample | 64 | step=100 | alloc (B/op) | 0.109 | 0.071 | <= 0.120 | PASS |
| SegmentBenchmark.generate_t64 | thrpt | 64 | step=10 | score (ops/us) | 42.630 | 78.256 | >= 41.061 | PASS |
| SegmentBenchmark.generate_t64 | thrpt | 64 | step=10 | alloc (B/op) | 0.155 | 0.001 | < 1.0 | PASS |
| SegmentBenchmark.generate_t64 | thrpt | 64 | step=100 | score (ops/us) | 43.391 | 74.406 | >= 42.624 | PASS |
| SegmentBenchmark.generate_t64 | thrpt | 64 | step=100 | alloc (B/op) | 0.003 | 0.001 | < 1.0 | PASS |
| SegmentNoPrefetchBenchmark.generate_t1 | thrpt | 1 |  | score (ops/ms) | 11,773 | 11,641 | >= 11,748 | FAIL |
| SegmentNoPrefetchBenchmark.generate_t1 | thrpt | 1 |  | alloc (B/op) | 24.000 | 32.000 | <= 24.000 | FAIL |

### Re-run of the failed rows

Both sides again, in fresh forks; a row that failed above passes only if it passes here.

| Benchmark | Mode | Threads | Params | Metric | 4.x | 5.x | Limit | Result |
|---|---|---|---|---|---|---|---|---|
| SegmentNoPrefetchBenchmark.generate_t1 | thrpt | 1 |  | score (ops/ms) | 11,768 | 11,571 | >= 11,741 | FAIL |
| SegmentNoPrefetchBenchmark.generate_t1 | thrpt | 1 |  | alloc (B/op) | 24.000 | 32.000 | <= 24.000 | FAIL |

**FAIL**: 1 of 21 baseline rows failed (1 failed the first run and were re-run).

## snowflake

4.1.0 (`benchmark/baseline-4x`) and 5.0 (`benchmark/v5`) on the same machine, interleaved per scenario (4.x then 5.x), judged by `benchmark/compare` with the rules of [10-performance-and-parity.md §1.3](../../architecture/10-performance-and-parity.md).

| | |
|---|---|
| Command | `benchmark/run-release P1 P2 P3 P4` |
| JMH | 1.37, release (3 forks, 5 x 2 s warmup, 5 x 2 s measurement), `-prof gc` |
| Date | 2026-10-27T15:49:04+00:00 |
| Cpu | AMD EPYC 9V45 96-Core Processor |
| Cores | 4 |
| Memory | 15 GB |
| Os | Linux 6.17.0-1022-azure (x86_64) |
| Frequency scaling | unknown (a shared virtual machine: not controllable) |
| Runner | GitHub Actions 1000413061, image ubuntu24 20261023.334.3, run 40534275762 |
| Jdk | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS |
| Cosid 4.x | 4.1.0 |
| Commit | 614d5df92c64b33d67b1750814e2bded4a0d116b |
| Load at start | 3.13 1.15 0.42 |

Load average (1 minute) before each scenario: SnowflakeMillisBenchmark 3.12, SnowflakeSecondsBenchmark 3.11, SnowflakeStringBenchmark 0.05, CosId80Benchmark 1.04


| Benchmark | Mode | Threads | Params | Metric | 4.x | 5.x | Limit | Result |
|---|---|---|---|---|---|---|---|---|
| CosId80Benchmark.friendly_t1 | thrpt | 1 |  | score (ops/ms) | 7,229 | 11,244 | >= 7,148 | PASS |
| CosId80Benchmark.friendly_t1 | thrpt | 1 |  | alloc (B/op) | 523.775 | 120.000 | <= 523.983 | PASS |
| CosId80Benchmark.radix62_t1 | thrpt | 1 |  | score (ops/ms) | 15,418 | 17,518 | >= 15,236 | PASS |
| CosId80Benchmark.radix62_t1 | thrpt | 1 |  | alloc (B/op) | 208.000 | 88.000 | <= 208.000 | PASS |
| SnowflakeMillisBenchmark.generate_t1 | thrpt | 1 |  | score (ops/ms) | 4,096 | 4,097 | >= 4,096 | PASS |
| SnowflakeMillisBenchmark.generate_t1 | thrpt | 1 |  | alloc (B/op) | 0.001 | 0.001 | < 1.0 | PASS |
| SnowflakeMillisBenchmark.generate_t16 | thrpt | 16 |  | score (ops/ms) | 4,096 | 4,102 | >= 4,093 | PASS |
| SnowflakeMillisBenchmark.generate_t16 | thrpt | 16 |  | alloc (B/op) | 0.003 | 0.003 | < 1.0 | PASS |
| SnowflakeMillisBenchmark.generate_t4 | thrpt | 4 |  | score (ops/ms) | 4,096 | 4,096 | >= 4,096 | PASS |
| SnowflakeMillisBenchmark.generate_t4 | thrpt | 4 |  | alloc (B/op) | 0.001 | 0.001 | < 1.0 | PASS |
| SnowflakeSecondsBenchmark.generate_t1 | thrpt | 1 |  | score (ops/ms) | 4,223 | 4,215 | >= 4,203 | PASS |
| SnowflakeSecondsBenchmark.generate_t1 | thrpt | 1 |  | alloc (B/op) | 0.001 | 0.001 | < 1.0 | PASS |
| SnowflakeSecondsBenchmark.generate_t16 | thrpt | 16 |  | score (ops/ms) | 4,229 | 4,198 | >= 4,147 | PASS |
| SnowflakeSecondsBenchmark.generate_t16 | thrpt | 16 |  | alloc (B/op) | 0.002 | 0.003 | < 1.0 | PASS |
| SnowflakeStringBenchmark.friendly_t1 | thrpt | 1 |  | score (ops/ms) | 4,083 | 4,096 | >= 4,063 | PASS |
| SnowflakeStringBenchmark.friendly_t1 | thrpt | 1 |  | alloc (B/op) | 387.639 | 120.001 | <= 409.427 | PASS |
| SnowflakeStringBenchmark.radix62_t1 | thrpt | 1 |  | score (ops/ms) | 4,097 | 4,097 | >= 4,096 | PASS |
| SnowflakeStringBenchmark.radix62_t1 | thrpt | 1 |  | alloc (B/op) | 96.001 | 88.001 | <= 96.001 | PASS |

**PASS**: 0 of 9 baseline rows failed.

## spring-start

4.1.0 (`benchmark/baseline-4x`) and 5.0 (`benchmark/v5`) on the same machine, interleaved per scenario (4.x then 5.x), judged by `benchmark/compare` with the rules of [10-performance-and-parity.md §1.3](../../architecture/10-performance-and-parity.md).

| | |
|---|---|
| Command | `benchmark/run-release P11` |
| JMH | 1.37, release (3 forks, 5 x 2 s warmup, 5 x 2 s measurement), `-prof gc` |
| Date | 2026-10-27T15:49:50+00:00 |
| Cpu | Intel(R) Xeon(R) Platinum 8370C CPU @ 2.80GHz |
| Cores | 4 |
| Memory | 15 GB |
| Os | Linux 6.17.0-1022-azure (x86_64) |
| Frequency scaling | turbo ON (a shared virtual machine: not controllable) |
| Runner | GitHub Actions 1000413063, image ubuntu24 20261023.334.3, run 40534275762 |
| Jdk | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS |
| Cosid 4.x | 4.1.0 |
| Commit | 614d5df92c64b33d67b1750814e2bded4a0d116b |
| Load at start | 3.68 1.43 0.53 |

Load average (1 minute) before each scenario: SpringStartBenchmark 3.55


| Benchmark | Mode | Threads | Params | Metric | 4.x | 5.x | Limit | Result |
|---|---|---|---|---|---|---|---|---|
| SpringStartBenchmark.startToFirstId_t1 | avgt | 1 |  | time (ms/op) | 14.185 | 12.332 | <= 14.894 | PASS |
| SpringStartBenchmark.startToFirstId_t1 | avgt | 1 |  | alloc (B/op) | 7,106,536 | 5,874,032 | <= 7,119,068 | PASS |

**PASS**: 0 of 1 baseline rows failed.

## zookeeper

4.1.0 (`benchmark/baseline-4x`) and 5.0 (`benchmark/v5`) on the same machine, interleaved per scenario (4.x then 5.x), judged by `benchmark/compare` with the rules of [10-performance-and-parity.md §1.3](../../architecture/10-performance-and-parity.md).

| | |
|---|---|
| Command | `benchmark/run-release ZookeeperStoreBenchmark` |
| JMH | 1.37, release (3 forks, 5 x 2 s warmup, 5 x 2 s measurement), `-prof gc` |
| Date | 2026-10-27T15:49:57+00:00 |
| Cpu | AMD EPYC 7763 64-Core Processor |
| Cores | 4 |
| Memory | 15 GB |
| Os | Linux 6.17.0-1022-azure (x86_64) |
| Frequency scaling | unknown (a shared virtual machine: not controllable) |
| Runner | GitHub Actions 1000413062, image ubuntu24 20261023.334.3, run 40534275762 |
| Jdk | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS |
| Cosid 4.x | 4.1.0 |
| Commit | 614d5df92c64b33d67b1750814e2bded4a0d116b |
| Load at start | 4.99 2.00 0.76 |

Load average (1 minute) before each scenario: ZookeeperStoreBenchmark 4.99


| Benchmark | Mode | Threads | Params | Metric | 4.x | 5.x | Limit | Result |
|---|---|---|---|---|---|---|---|---|
| ZookeeperStoreBenchmark.acquire1024_t1 | avgt | 1 |  | score (us/op) | 4,967 | 2,343 | <= 5,179 | PASS |
| ZookeeperStoreBenchmark.acquire1024_t1 | avgt | 1 |  | alloc (B/op) | 53,572 | 36,869 | <= 54,333 | PASS |
| ZookeeperStoreBenchmark.acquire1024_t1 | sample | 1 |  | p99.0 (us/op) | 14,777 | 7,493 | <= 14,846 | PASS |
| ZookeeperStoreBenchmark.acquire1024_t1 | sample | 1 |  | p99.9 (us/op) | 21,782 | 17,113 | <= 21,851 | PASS |
| ZookeeperStoreBenchmark.acquire1024_t1 | sample | 1 |  | alloc (B/op) | 54,015 | 37,043 | <= 54,719 | PASS |
| ZookeeperStoreBenchmark.acquire_t1 | avgt | 1 |  | score (us/op) | 4,969 | 2,310 | <= 5,122 | PASS |
| ZookeeperStoreBenchmark.acquire_t1 | avgt | 1 |  | alloc (B/op) | 54,338 | 36,947 | <= 54,913 | PASS |
| ZookeeperStoreBenchmark.acquire_t1 | sample | 1 |  | p99.0 (us/op) | 13,730 | 8,671 | <= 13,787 | PASS |
| ZookeeperStoreBenchmark.acquire_t1 | sample | 1 |  | p99.9 (us/op) | 19,314 | 17,757 | <= 19,370 | PASS |
| ZookeeperStoreBenchmark.acquire_t1 | sample | 1 |  | alloc (B/op) | 54,482 | 37,128 | <= 55,116 | PASS |
| ZookeeperStoreBenchmark.renew_t1 | avgt | 1 |  | score (us/op) | 837.502 | 852.200 | <= 850.937 | FAIL |
| ZookeeperStoreBenchmark.renew_t1 | avgt | 1 |  | alloc (B/op) | 8,982 | 9,162 | <= 9,023 | FAIL |
| ZookeeperStoreBenchmark.renew_t1 | sample | 1 |  | p99.0 (us/op) | 1,200 | 1,398 | <= 1,210 | FAIL |
| ZookeeperStoreBenchmark.renew_t1 | sample | 1 |  | p99.9 (us/op) | 11,341 | 12,357 | <= 11,351 | FAIL |
| ZookeeperStoreBenchmark.renew_t1 | sample | 1 |  | alloc (B/op) | 9,020 | 9,233 | <= 9,051 | FAIL |
| ZookeeperStoreBenchmark.reserve_t1 | avgt | 1 |  | score (us/op) | 780.377 | 838.117 | <= 790.851 | FAIL |
| ZookeeperStoreBenchmark.reserve_t1 | avgt | 1 |  | alloc (B/op) | 5,861 | 6,961 | <= 5,904 | FAIL |
| ZookeeperStoreBenchmark.reserve_t1 | sample | 1 |  | p99.0 (us/op) | 1,458 | 1,526 | <= 1,469 | FAIL |
| ZookeeperStoreBenchmark.reserve_t1 | sample | 1 |  | p99.9 (us/op) | 11,949 | 12,782 | <= 11,959 | FAIL |
| ZookeeperStoreBenchmark.reserve_t1 | sample | 1 |  | alloc (B/op) | 5,926 | 7,034 | <= 5,969 | FAIL |

### Re-run of the failed rows

Both sides again, in fresh forks; a row that failed above passes only if it passes here.

| Benchmark | Mode | Threads | Params | Metric | 4.x | 5.x | Limit | Result |
|---|---|---|---|---|---|---|---|---|
| ZookeeperStoreBenchmark.renew_t1 | avgt | 1 |  | score (us/op) | 844.069 | 858.546 | <= 859.892 | PASS |
| ZookeeperStoreBenchmark.renew_t1 | avgt | 1 |  | alloc (B/op) | 8,964 | 9,162 | <= 8,999 | FAIL |
| ZookeeperStoreBenchmark.renew_t1 | sample | 1 |  | p99.0 (us/op) | 1,196 | 1,337 | <= 1,206 | FAIL |
| ZookeeperStoreBenchmark.renew_t1 | sample | 1 |  | p99.9 (us/op) | 11,481 | 11,801 | <= 11,491 | FAIL |
| ZookeeperStoreBenchmark.renew_t1 | sample | 1 |  | alloc (B/op) | 9,030 | 9,230 | <= 9,065 | FAIL |
| ZookeeperStoreBenchmark.reserve_t1 | avgt | 1 |  | score (us/op) | 786.462 | 832.018 | <= 799.699 | FAIL |
| ZookeeperStoreBenchmark.reserve_t1 | avgt | 1 |  | alloc (B/op) | 5,845 | 6,961 | <= 5,899 | FAIL |
| ZookeeperStoreBenchmark.reserve_t1 | sample | 1 |  | p99.0 (us/op) | 1,257 | 1,208 | <= 1,267 | PASS |
| ZookeeperStoreBenchmark.reserve_t1 | sample | 1 |  | p99.9 (us/op) | 11,454 | 11,969 | <= 11,464 | FAIL |
| ZookeeperStoreBenchmark.reserve_t1 | sample | 1 |  | alloc (B/op) | 5,886 | 7,030 | <= 5,936 | FAIL |

**FAIL**: 4 of 8 baseline rows failed (4 failed the first run and were re-run).
