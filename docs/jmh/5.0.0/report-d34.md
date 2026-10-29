# CosId 5.0 release gate

> The verdict of [run 40887469149](https://github.com/Ahoo-Wang/CosId/actions/runs/40887469149) as the gate gave it, under the rules before D50: FAIL, 3 of 8 groups. The release is judged by [report.md](report.md).

4.1.1 (`benchmark/baseline-4x`) and 5.0 (`benchmark/v5`), each scenario group in its own CI job and both sides of a group on the same runner, interleaved per scenario, judged by `benchmark/compare` with the rules of [10-performance-and-parity.md §1.3](../../architecture/10-performance-and-parity.md). A row that failed was run again on both sides in fresh forks and passes only if that re-run passes; both runs are shown.

Commit be35de36e91c6fbf2d35c030c60df35aa82f583c; release (3 forks, 5 x 2 s warmup, 5 x 2 s measurement); JMH 1.37, `-prof gc`; Linux 7.0.0-1017-azure (x86_64); unknown (a shared virtual machine: not controllable).

| Group | Scenarios | Runner | JDK | Rows | Re-run | Result |
|---|---|---|---|---|---|---|
| codec-filler | P9 P12 | AMD EPYC 9V45 96-Core Processor, 4 cores | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS | 7 | 0 | PASS |
| mongo | MongoChainIdBenchmark MongoStoreBenchmark | AMD EPYC 9V45 96-Core Processor, 4 cores | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS | 18 | 0 | PASS |
| mysql | MySqlChainIdBenchmark MySqlStoreBenchmark | AMD EPYC 9V74 80-Core Processor, 4 cores | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS | 18 | 1 | **FAIL** (1 rows) |
| redis | RedisChainIdBenchmark RedisStoreBenchmark | AMD EPYC 7763 64-Core Processor, 4 cores | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS | 18 | 1 | **FAIL** (1 rows) |
| segment | P5 P6 P8 | AMD EPYC 9V45 96-Core Processor, 4 cores | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS | 21 | 1 | **FAIL** (1 rows) |
| snowflake | P1 P2 P3 P4 P13 | AMD EPYC 9V74 80-Core Processor, 4 cores | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS | 9 | 0 | PASS |
| spring-start | P11 | AMD EPYC 7763 64-Core Processor, 4 cores | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS | 1 | 0 | PASS |
| zookeeper | ZookeeperChainIdBenchmark ZookeeperStoreBenchmark | AMD EPYC 7763 64-Core Processor, 4 cores | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS | 20 | 0 | PASS |

**FAIL**: 3 of 8 groups failed.

## Rows that still fail after their re-run

Each goes to the phase that owns it; the suspected cause is filled in when the row is investigated.

| Group | Benchmark | Mode | Threads | Params | Owner | Suspected cause |
|---|---|---|---|---|---|---|
| mysql | MySqlStoreBenchmark.acquire_t1 | sample | 1 |  | R2-JDBC | to investigate |
| redis | RedisChainIdBenchmark.generate_t1 | sample | 1 | step=100 | R2-Redis | to investigate |
| segment | SegmentBenchmark.generate_t1 | sample | 1 | step=100 | R1-B (segments) | to investigate |

## codec-filler

4.1.1 (`benchmark/baseline-4x`) and 5.0 (`benchmark/v5`) on the same machine, interleaved per scenario (4.x then 5.x), judged by `benchmark/compare` with the rules of [10-performance-and-parity.md §1.3](../../architecture/10-performance-and-parity.md).

| | |
|---|---|
| Command | `benchmark/run-release P9 P12` |
| JMH | 1.37, release (3 forks, 5 x 2 s warmup, 5 x 2 s measurement), `-prof gc` |
| Date | 2026-10-29T14:04:23+00:00 |
| Cpu | AMD EPYC 9V45 96-Core Processor |
| Cores | 4 |
| Memory | 15 GB |
| Os | Linux 7.0.0-1017-azure (x86_64) |
| Frequency scaling | unknown (a shared virtual machine: not controllable) |
| Runner | GitHub Actions 1000439912, image ubuntu24 20261026.354.1, run 40887469149 |
| Jdk | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS |
| Cosid 4.x | 4.1.1 |
| Commit | be35de36e91c6fbf2d35c030c60df35aa82f583c |
| Load at start | 1.37 0.33 0.11 |

Load average (1 minute) before each scenario: CodecBenchmark 1.42, IdFillerBenchmark 1.24


| Benchmark | Mode | Threads | Params | Metric | 4.x | 5.x | Limit | Result |
|---|---|---|---|---|---|---|---|---|
| CodecBenchmark.friendlyDecode_t1 | thrpt | 1 |  | score (ops/ms) | 1,621 | 36,357 | >= 1,540 (WARN >= 1,297) | PASS |
| CodecBenchmark.friendlyDecode_t1 | thrpt | 1 |  | alloc (B/op) | 2,360 | 0.000 | <= 2,478 (WARN <= 2,950) | PASS |
| CodecBenchmark.friendlyEncode_t1 | thrpt | 1 |  | score (ops/ms) | 8,801 | 19,736 | >= 8,361 (WARN >= 7,041) | PASS |
| CodecBenchmark.friendlyEncode_t1 | thrpt | 1 |  | alloc (B/op) | 576.000 | 120.000 | <= 640.000 (WARN <= 720.000) | PASS |
| CodecBenchmark.radix36Decode_t1 | thrpt | 1 |  | score (ops/ms) | 76,868 | 81,534 | >= 73,025 (WARN >= 61,495) | PASS |
| CodecBenchmark.radix36Decode_t1 | thrpt | 1 |  | alloc (B/op) | 0.000 | 0.000 | <= 64.000 (WARN <= 64.000) | PASS |
| CodecBenchmark.radix36Encode_t1 | thrpt | 1 |  | score (ops/ms) | 47,340 | 63,564 | >= 44,973 (WARN >= 37,872) | PASS |
| CodecBenchmark.radix36Encode_t1 | thrpt | 1 |  | alloc (B/op) | 104.000 | 88.000 | <= 168.000 (WARN <= 168.000) | PASS |
| CodecBenchmark.radix62Decode_t1 | thrpt | 1 |  | score (ops/ms) | 81,838 | 89,867 | >= 77,746 (WARN >= 65,471) | PASS |
| CodecBenchmark.radix62Decode_t1 | thrpt | 1 |  | alloc (B/op) | 0.000 | 0.000 | <= 64.000 (WARN <= 64.000) | PASS |
| CodecBenchmark.radix62Encode_t1 | thrpt | 1 |  | score (ops/ms) | 54,203 | 67,066 | >= 51,493 (WARN >= 43,362) | PASS |
| CodecBenchmark.radix62Encode_t1 | thrpt | 1 |  | alloc (B/op) | 96.000 | 88.000 | <= 160.000 (WARN <= 160.000) | PASS |
| IdFillerBenchmark.fill_t1 | thrpt | 1 |  | score (ops/ms) | 35,202 | 111,923 | >= 33,442 | PASS |
| IdFillerBenchmark.fill_t1 | thrpt | 1 |  | alloc (B/op) | 64.001 | 24.001 | <= 128.001 | PASS |

**PASS**: 0 of 7 baseline rows failed.

## mongo

4.1.1 (`benchmark/baseline-4x`) and 5.0 (`benchmark/v5`) on the same machine, interleaved per scenario (4.x then 5.x), judged by `benchmark/compare` with the rules of [10-performance-and-parity.md §1.3](../../architecture/10-performance-and-parity.md).

| | |
|---|---|
| Command | `benchmark/run-release MongoChainIdBenchmark MongoStoreBenchmark` |
| JMH | 1.37, release (3 forks, 5 x 2 s warmup, 5 x 2 s measurement), `-prof gc` |
| Date | 2026-10-29T14:04:49+00:00 |
| Cpu | AMD EPYC 9V45 96-Core Processor |
| Cores | 4 |
| Memory | 15 GB |
| Os | Linux 7.0.0-1017-azure (x86_64) |
| Frequency scaling | unknown (a shared virtual machine: not controllable) |
| Runner | GitHub Actions 1000439913, image ubuntu24 20261026.354.1, run 40887469149 |
| Jdk | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS |
| Cosid 4.x | 4.1.1 |
| Commit | be35de36e91c6fbf2d35c030c60df35aa82f583c |
| Load at start | 2.01 0.54 0.18 |

Load average (1 minute) before each scenario: MongoChainIdBenchmark 2.09, MongoStoreBenchmark 0.53


| Benchmark | Mode | Threads | Params | Metric | 4.x | 5.x | Limit | Result |
|---|---|---|---|---|---|---|---|---|
| MongoChainIdBenchmark.generate_t1 | sample | 1 | step=10 | p99.0 (us/op) | 0.161 | 0.031 | <= 0.426 | PASS |
| MongoChainIdBenchmark.generate_t1 | sample | 1 | step=10 | p99.9 (us/op) | 1,100 | 0.210 | <= 1,210 | PASS |
| MongoChainIdBenchmark.generate_t1 | sample | 1 | step=10 | alloc (B/op) | 238.725 | 0.039 | <= 349.602 | PASS |
| MongoChainIdBenchmark.generate_t1 | sample | 1 | step=100 | p99.0 (us/op) | 0.050 | 0.031 | <= 0.112 | PASS |
| MongoChainIdBenchmark.generate_t1 | sample | 1 | step=100 | p99.9 (us/op) | 0.260 | 0.230 | <= 0.322 | PASS |
| MongoChainIdBenchmark.generate_t1 | sample | 1 | step=100 | alloc (B/op) | 22.053 | 0.039 | <= 86.053 | PASS |
| MongoChainIdBenchmark.generate_t1 | thrpt | 1 | step=10 | score (ops/us) | 6.520 | 206.654 | >= -1.931 | PASS |
| MongoChainIdBenchmark.generate_t1 | thrpt | 1 | step=10 | alloc (B/op) | 133.053 | 0.038 | <= 278.227 | PASS |
| MongoChainIdBenchmark.generate_t1 | thrpt | 1 | step=100 | score (ops/us) | 1.699 | 208.426 | >= 0.973 | PASS |
| MongoChainIdBenchmark.generate_t1 | thrpt | 1 | step=100 | alloc (B/op) | 22.819 | 0.038 | <= 86.819 | PASS |
| MongoChainIdBenchmark.generate_t16 | sample | 16 | step=10 | p99.0 (us/op) | 1,011 | 0.150 | <= 2,521 | PASS |
| MongoChainIdBenchmark.generate_t16 | sample | 16 | step=10 | p99.9 (us/op) | 1,243 | 0.230 | <= 2,753 | PASS |
| MongoChainIdBenchmark.generate_t16 | sample | 16 | step=10 | alloc (B/op) | 350.886 | 0.073 | <= 543.811 | PASS |
| MongoChainIdBenchmark.generate_t16 | sample | 16 | step=100 | p99.0 (us/op) | 0.300 | 0.160 | <= 55.524 | PASS |
| MongoChainIdBenchmark.generate_t16 | sample | 16 | step=100 | p99.9 (us/op) | 0.431 | 0.241 | <= 55.655 | PASS |
| MongoChainIdBenchmark.generate_t16 | sample | 16 | step=100 | alloc (B/op) | 52.171 | 0.075 | <= 116.171 | PASS |
| MongoChainIdBenchmark.generate_t16 | thrpt | 16 | step=10 | score (ops/us) | 57.912 | 65.755 | >= 31.380 | PASS |
| MongoChainIdBenchmark.generate_t16 | thrpt | 16 | step=10 | alloc (B/op) | 307.265 | 0.040 | <= 503.615 | PASS |
| MongoChainIdBenchmark.generate_t16 | thrpt | 16 | step=100 | score (ops/us) | 2.115 | 65.291 | >= -3.346 | PASS |
| MongoChainIdBenchmark.generate_t16 | thrpt | 16 | step=100 | alloc (B/op) | 71.411 | 0.040 | <= 135.411 | PASS |
| MongoChainIdBenchmark.generate_t4 | sample | 4 | step=10 | p99.0 (us/op) | 0.281 | 0.160 | <= 101.982 | PASS |
| MongoChainIdBenchmark.generate_t4 | sample | 4 | step=10 | p99.9 (us/op) | 1,168 | 0.251 | <= 1,284 | PASS |
| MongoChainIdBenchmark.generate_t4 | sample | 4 | step=10 | alloc (B/op) | 520.750 | 0.049 | <= 888.795 | PASS |
| MongoChainIdBenchmark.generate_t4 | sample | 4 | step=100 | p99.0 (us/op) | 0.280 | 0.141 | <= 16.326 | PASS |
| MongoChainIdBenchmark.generate_t4 | sample | 4 | step=100 | p99.9 (us/op) | 6.693 | 0.240 | <= 22.739 | PASS |
| MongoChainIdBenchmark.generate_t4 | sample | 4 | step=100 | alloc (B/op) | 54.917 | 0.048 | <= 118.917 | PASS |
| MongoChainIdBenchmark.generate_t4 | thrpt | 4 | step=10 | score (ops/us) | 2.538 | 64.464 | >= -1.286 | PASS |
| MongoChainIdBenchmark.generate_t4 | thrpt | 4 | step=10 | alloc (B/op) | 713.592 | 0.040 | <= 1,203 | PASS |
| MongoChainIdBenchmark.generate_t4 | thrpt | 4 | step=100 | score (ops/us) | 1.223 | 65.380 | >= 0.388 | PASS |
| MongoChainIdBenchmark.generate_t4 | thrpt | 4 | step=100 | alloc (B/op) | 53.778 | 0.040 | <= 117.778 | PASS |
| MongoStoreBenchmark.acquire_t1 | avgt | 1 |  | score (us/op) | 2,628 | 2,395 | <= 2,759 (WARN <= 3,153) | PASS |
| MongoStoreBenchmark.acquire_t1 | avgt | 1 |  | alloc (B/op) | 147,304 | 113,459 | <= 154,669 (WARN <= 184,130) | PASS |
| MongoStoreBenchmark.acquire_t1 | sample | 1 |  | p99.0 (us/op) | 4,040 | 3,994 | <= 4,444 (WARN <= 5,252) | PASS |
| MongoStoreBenchmark.acquire_t1 | sample | 1 |  | p99.9 (us/op) | 14,099 | 12,934 | <= 15,509 (WARN <= 18,329) | PASS |
| MongoStoreBenchmark.acquire_t1 | sample | 1 |  | alloc (B/op) | 146,332 | 113,887 | <= 153,648 (WARN <= 182,915) | PASS |
| MongoStoreBenchmark.renew_t1 | avgt | 1 |  | score (us/op) | 1,021 | 1,024 | <= 1,072 (WARN <= 1,225) | PASS |
| MongoStoreBenchmark.renew_t1 | avgt | 1 |  | alloc (B/op) | 49,106 | 30,387 | <= 51,561 (WARN <= 61,383) | PASS |
| MongoStoreBenchmark.renew_t1 | sample | 1 |  | p99.0 (us/op) | 1,677 | 1,525 | <= 1,844 (WARN <= 2,180) | PASS |
| MongoStoreBenchmark.renew_t1 | sample | 1 |  | p99.9 (us/op) | 6,120 | 6,021 | <= 6,732 (WARN <= 7,956) | PASS |
| MongoStoreBenchmark.renew_t1 | sample | 1 |  | alloc (B/op) | 49,090 | 29,893 | <= 51,545 (WARN <= 61,363) | PASS |
| MongoStoreBenchmark.reserve_t1 | avgt | 1 |  | score (us/op) | 1,019 | 1,009 | <= 1,070 (WARN <= 1,223) | PASS |
| MongoStoreBenchmark.reserve_t1 | avgt | 1 |  | alloc (B/op) | 31,803 | 28,058 | <= 33,393 (WARN <= 39,754) | PASS |
| MongoStoreBenchmark.reserve_t1 | sample | 1 |  | p99.0 (us/op) | 1,358 | 1,282 | <= 1,494 (WARN <= 1,765) | PASS |
| MongoStoreBenchmark.reserve_t1 | sample | 1 |  | p99.9 (us/op) | 13,953 | 4,128 | <= 15,348 (WARN <= 18,139) | PASS |
| MongoStoreBenchmark.reserve_t1 | sample | 1 |  | alloc (B/op) | 31,760 | 27,908 | <= 33,348 (WARN <= 39,700) | PASS |

**PASS**: 0 of 18 baseline rows failed.

## mysql

4.1.1 (`benchmark/baseline-4x`) and 5.0 (`benchmark/v5`) on the same machine, interleaved per scenario (4.x then 5.x), judged by `benchmark/compare` with the rules of [10-performance-and-parity.md §1.3](../../architecture/10-performance-and-parity.md).

| | |
|---|---|
| Command | `benchmark/run-release MySqlChainIdBenchmark MySqlStoreBenchmark` |
| JMH | 1.37, release (3 forks, 5 x 2 s warmup, 5 x 2 s measurement), `-prof gc` |
| Date | 2026-10-29T14:04:47+00:00 |
| Cpu | AMD EPYC 9V74 80-Core Processor |
| Cores | 4 |
| Memory | 15 GB |
| Os | Linux 7.0.0-1017-azure (x86_64) |
| Frequency scaling | unknown (a shared virtual machine: not controllable) |
| Runner | GitHub Actions 1000439908, image ubuntu24 20261026.354.1, run 40887469149 |
| Jdk | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS |
| Cosid 4.x | 4.1.1 |
| Commit | be35de36e91c6fbf2d35c030c60df35aa82f583c |
| Load at start | 2.65 0.85 0.30 |

Load average (1 minute) before each scenario: MySqlChainIdBenchmark 2.51, MySqlStoreBenchmark 2.97


| Benchmark | Mode | Threads | Params | Metric | 4.x | 5.x | Limit | Result |
|---|---|---|---|---|---|---|---|---|
| MySqlChainIdBenchmark.generate_t1 | sample | 1 | step=10 | p99.0 (us/op) | 0.070 | 0.060 | <= 0.163 | PASS |
| MySqlChainIdBenchmark.generate_t1 | sample | 1 | step=10 | p99.9 (us/op) | 580.608 | 0.292 | <= 638.669 | PASS |
| MySqlChainIdBenchmark.generate_t1 | sample | 1 | step=10 | alloc (B/op) | 29.748 | 0.016 | <= 93.748 | PASS |
| MySqlChainIdBenchmark.generate_t1 | sample | 1 | step=100 | p99.0 (us/op) | 0.051 | 0.060 | <= 0.089 | PASS |
| MySqlChainIdBenchmark.generate_t1 | sample | 1 | step=100 | p99.9 (us/op) | 1.142 | 0.271 | <= 1.257 | PASS |
| MySqlChainIdBenchmark.generate_t1 | sample | 1 | step=100 | alloc (B/op) | 0.970 | 0.016 | <= 64.970 | PASS |
| MySqlChainIdBenchmark.generate_t1 | thrpt | 1 | step=10 | score (ops/us) | 0.622 | 347.660 | >= 0.415 | PASS |
| MySqlChainIdBenchmark.generate_t1 | thrpt | 1 | step=10 | alloc (B/op) | 28.972 | 0.015 | <= 92.972 | PASS |
| MySqlChainIdBenchmark.generate_t1 | thrpt | 1 | step=100 | score (ops/us) | 9.645 | 346.698 | >= 7.277 | PASS |
| MySqlChainIdBenchmark.generate_t1 | thrpt | 1 | step=100 | alloc (B/op) | 1.631 | 0.015 | <= 65.631 | PASS |
| MySqlChainIdBenchmark.generate_t16 | sample | 16 | step=10 | p99.0 (us/op) | 0.290 | 0.240 | <= 30.937 | PASS |
| MySqlChainIdBenchmark.generate_t16 | sample | 16 | step=10 | p99.9 (us/op) | 635.904 | 0.310 | <= 699.494 | PASS |
| MySqlChainIdBenchmark.generate_t16 | sample | 16 | step=10 | alloc (B/op) | 43.874 | 0.078 | <= 107.874 | PASS |
| MySqlChainIdBenchmark.generate_t16 | sample | 16 | step=100 | p99.0 (us/op) | 0.301 | 0.231 | <= 0.851 | PASS |
| MySqlChainIdBenchmark.generate_t16 | sample | 16 | step=100 | p99.9 (us/op) | 0.701 | 0.310 | <= 1.251 | PASS |
| MySqlChainIdBenchmark.generate_t16 | sample | 16 | step=100 | alloc (B/op) | 0.562 | 0.078 | <= 64.562 | PASS |
| MySqlChainIdBenchmark.generate_t16 | thrpt | 16 | step=10 | score (ops/us) | 58.782 | 54.937 | >= 30.938 | PASS |
| MySqlChainIdBenchmark.generate_t16 | thrpt | 16 | step=10 | alloc (B/op) | 69.432 | 0.019 | <= 133.432 | PASS |
| MySqlChainIdBenchmark.generate_t16 | thrpt | 16 | step=100 | score (ops/us) | 6.752 | 55.293 | >= 4.181 | PASS |
| MySqlChainIdBenchmark.generate_t16 | thrpt | 16 | step=100 | alloc (B/op) | 2.282 | 0.019 | <= 66.282 | PASS |
| MySqlChainIdBenchmark.generate_t4 | sample | 4 | step=10 | p99.0 (us/op) | 0.301 | 0.240 | <= 16.624 | PASS |
| MySqlChainIdBenchmark.generate_t4 | sample | 4 | step=10 | p99.9 (us/op) | 636.928 | 0.331 | <= 700.621 | PASS |
| MySqlChainIdBenchmark.generate_t4 | sample | 4 | step=10 | alloc (B/op) | 72.747 | 0.031 | <= 136.747 | PASS |
| MySqlChainIdBenchmark.generate_t4 | sample | 4 | step=100 | p99.0 (us/op) | 0.301 | 0.240 | <= 1.451 | PASS |
| MySqlChainIdBenchmark.generate_t4 | sample | 4 | step=100 | p99.9 (us/op) | 10.336 | 0.321 | <= 11.486 | PASS |
| MySqlChainIdBenchmark.generate_t4 | sample | 4 | step=100 | alloc (B/op) | 2.701 | 0.032 | <= 66.701 | PASS |
| MySqlChainIdBenchmark.generate_t4 | thrpt | 4 | step=10 | score (ops/us) | 1.338 | 55.336 | >= -2.247 | PASS |
| MySqlChainIdBenchmark.generate_t4 | thrpt | 4 | step=10 | alloc (B/op) | 107.380 | 0.019 | <= 183.331 | PASS |
| MySqlChainIdBenchmark.generate_t4 | thrpt | 4 | step=100 | score (ops/us) | 3.272 | 55.252 | >= 2.215 | PASS |
| MySqlChainIdBenchmark.generate_t4 | thrpt | 4 | step=100 | alloc (B/op) | 5.010 | 0.019 | <= 69.010 | PASS |
| MySqlStoreBenchmark.acquire_t1 | avgt | 1 |  | score (us/op) | 1,326 | 1,239 | <= 1,392 (WARN <= 1,591) | PASS |
| MySqlStoreBenchmark.acquire_t1 | avgt | 1 |  | alloc (B/op) | 50,555 | 44,508 | <= 53,083 (WARN <= 63,194) | PASS |
| MySqlStoreBenchmark.acquire_t1 | sample | 1 |  | p99.0 (us/op) | 1,642 | 1,595 | <= 1,807 (WARN <= 2,135) | PASS |
| MySqlStoreBenchmark.acquire_t1 | sample | 1 |  | p99.9 (us/op) | 2,735 | 4,025 | <= 3,008 (WARN <= 3,555) | FAIL |
| MySqlStoreBenchmark.acquire_t1 | sample | 1 |  | alloc (B/op) | 50,727 | 44,600 | <= 53,263 (WARN <= 63,409) | PASS |
| MySqlStoreBenchmark.renew_t1 | avgt | 1 |  | score (us/op) | 757.524 | 603.619 | <= 795.401 (WARN <= 909.029) | PASS |
| MySqlStoreBenchmark.renew_t1 | avgt | 1 |  | alloc (B/op) | 16,409 | 16,857 | <= 17,229 (WARN <= 20,511) | PASS |
| MySqlStoreBenchmark.renew_t1 | sample | 1 |  | p99.0 (us/op) | 960.512 | 926.720 | <= 1,057 (WARN <= 1,249) | PASS |
| MySqlStoreBenchmark.renew_t1 | sample | 1 |  | p99.9 (us/op) | 1,311 | 1,188 | <= 1,442 (WARN <= 1,705) | PASS |
| MySqlStoreBenchmark.renew_t1 | sample | 1 |  | alloc (B/op) | 16,417 | 16,863 | <= 17,238 (WARN <= 20,521) | PASS |
| MySqlStoreBenchmark.reserve_t1 | avgt | 1 |  | score (us/op) | 561.893 | 568.289 | <= 589.988 (WARN <= 674.272) | PASS |
| MySqlStoreBenchmark.reserve_t1 | avgt | 1 |  | alloc (B/op) | 8,644 | 8,687 | <= 9,076 (WARN <= 10,804) | PASS |
| MySqlStoreBenchmark.reserve_t1 | sample | 1 |  | p99.0 (us/op) | 743.424 | 745.411 | <= 817.766 (WARN <= 966.451) | PASS |
| MySqlStoreBenchmark.reserve_t1 | sample | 1 |  | p99.9 (us/op) | 1,061 | 1,067 | <= 1,167 (WARN <= 1,379) | PASS |
| MySqlStoreBenchmark.reserve_t1 | sample | 1 |  | alloc (B/op) | 8,667 | 8,706 | <= 9,100 (WARN <= 10,833) | PASS |

### Re-run of the failed rows

Both sides again, in fresh forks; a row that failed above passes only if it passes here.

| Benchmark | Mode | Threads | Params | Metric | 4.x | 5.x | Limit | Result |
|---|---|---|---|---|---|---|---|---|
| MySqlStoreBenchmark.acquire_t1 | sample | 1 |  | p99.0 (us/op) | 1,598 | 1,542 | <= 1,758 (WARN <= 2,078) | PASS |
| MySqlStoreBenchmark.acquire_t1 | sample | 1 |  | p99.9 (us/op) | 2,417 | 3,413 | <= 2,659 (WARN <= 3,142) | FAIL |
| MySqlStoreBenchmark.acquire_t1 | sample | 1 |  | alloc (B/op) | 50,705 | 44,637 | <= 53,241 (WARN <= 63,382) | PASS |

**FAIL**: 1 of 18 baseline rows failed (1 failed the first run and were re-run).

## redis

4.1.1 (`benchmark/baseline-4x`) and 5.0 (`benchmark/v5`) on the same machine, interleaved per scenario (4.x then 5.x), judged by `benchmark/compare` with the rules of [10-performance-and-parity.md §1.3](../../architecture/10-performance-and-parity.md).

| | |
|---|---|
| Command | `benchmark/run-release RedisChainIdBenchmark RedisStoreBenchmark` |
| JMH | 1.37, release (3 forks, 5 x 2 s warmup, 5 x 2 s measurement), `-prof gc` |
| Date | 2026-10-29T14:04:53+00:00 |
| Cpu | AMD EPYC 7763 64-Core Processor |
| Cores | 4 |
| Memory | 15 GB |
| Os | Linux 7.0.0-1017-azure (x86_64) |
| Frequency scaling | unknown (a shared virtual machine: not controllable) |
| Runner | GitHub Actions 1000439915, image ubuntu24 20261026.354.1, run 40887469149 |
| Jdk | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS |
| Cosid 4.x | 4.1.1 |
| Commit | be35de36e91c6fbf2d35c030c60df35aa82f583c |
| Load at start | 2.23 0.65 0.23 |

Load average (1 minute) before each scenario: RedisChainIdBenchmark 2.54, RedisStoreBenchmark 4.88


| Benchmark | Mode | Threads | Params | Metric | 4.x | 5.x | Limit | Result |
|---|---|---|---|---|---|---|---|---|
| RedisChainIdBenchmark.generate_t1 | sample | 1 | step=10 | p99.0 (us/op) | 0.070 | 0.041 | <= 0.110 | PASS |
| RedisChainIdBenchmark.generate_t1 | sample | 1 | step=10 | p99.9 (us/op) | 200.704 | 0.120 | <= 220.774 | PASS |
| RedisChainIdBenchmark.generate_t1 | sample | 1 | step=10 | alloc (B/op) | 15.692 | 0.007 | <= 79.692 | PASS |
| RedisChainIdBenchmark.generate_t1 | sample | 1 | step=100 | p99.0 (us/op) | 0.050 | 0.041 | <= 0.055 | PASS |
| RedisChainIdBenchmark.generate_t1 | sample | 1 | step=100 | p99.9 (us/op) | 0.091 | 0.110 | <= 0.100 | FAIL |
| RedisChainIdBenchmark.generate_t1 | sample | 1 | step=100 | alloc (B/op) | 0.448 | 0.007 | <= 64.448 | PASS |
| RedisChainIdBenchmark.generate_t1 | thrpt | 1 | step=10 | score (ops/us) | 2.151 | 406.279 | >= 1.643 | PASS |
| RedisChainIdBenchmark.generate_t1 | thrpt | 1 | step=10 | alloc (B/op) | 15.432 | 0.005 | <= 79.432 | PASS |
| RedisChainIdBenchmark.generate_t1 | thrpt | 1 | step=100 | score (ops/us) | 26.354 | 407.975 | >= 21.363 | PASS |
| RedisChainIdBenchmark.generate_t1 | thrpt | 1 | step=100 | alloc (B/op) | 0.969 | 0.005 | <= 64.969 | PASS |
| RedisChainIdBenchmark.generate_t16 | sample | 16 | step=10 | p99.0 (us/op) | 0.390 | 0.241 | <= 0.600 | PASS |
| RedisChainIdBenchmark.generate_t16 | sample | 16 | step=10 | p99.9 (us/op) | 12.752 | 0.330 | <= 14.027 | PASS |
| RedisChainIdBenchmark.generate_t16 | sample | 16 | step=10 | alloc (B/op) | 0.654 | 0.065 | <= 64.654 | PASS |
| RedisChainIdBenchmark.generate_t16 | sample | 16 | step=100 | p99.0 (us/op) | 0.381 | 0.241 | <= 0.524 | PASS |
| RedisChainIdBenchmark.generate_t16 | sample | 16 | step=100 | p99.9 (us/op) | 1.002 | 0.331 | <= 1.145 | PASS |
| RedisChainIdBenchmark.generate_t16 | sample | 16 | step=100 | alloc (B/op) | 0.178 | 0.065 | <= 64.178 | PASS |
| RedisChainIdBenchmark.generate_t16 | thrpt | 16 | step=10 | score (ops/us) | 17.674 | 43.308 | >= 16.778 | PASS |
| RedisChainIdBenchmark.generate_t16 | thrpt | 16 | step=10 | alloc (B/op) | 0.529 | 0.008 | <= 64.529 | PASS |
| RedisChainIdBenchmark.generate_t16 | thrpt | 16 | step=100 | score (ops/us) | 22.856 | 44.431 | >= 21.713 | PASS |
| RedisChainIdBenchmark.generate_t16 | thrpt | 16 | step=100 | alloc (B/op) | 0.095 | 0.008 | <= 64.095 | PASS |
| RedisChainIdBenchmark.generate_t4 | sample | 4 | step=10 | p99.0 (us/op) | 0.371 | 0.241 | <= 1.173 | PASS |
| RedisChainIdBenchmark.generate_t4 | sample | 4 | step=10 | p99.9 (us/op) | 211.200 | 0.431 | <= 232.320 | PASS |
| RedisChainIdBenchmark.generate_t4 | sample | 4 | step=10 | alloc (B/op) | 6.088 | 0.022 | <= 70.088 | PASS |
| RedisChainIdBenchmark.generate_t4 | sample | 4 | step=100 | p99.0 (us/op) | 0.391 | 0.241 | <= 0.621 | PASS |
| RedisChainIdBenchmark.generate_t4 | sample | 4 | step=100 | p99.9 (us/op) | 12.544 | 0.440 | <= 13.798 | PASS |
| RedisChainIdBenchmark.generate_t4 | sample | 4 | step=100 | alloc (B/op) | 0.842 | 0.023 | <= 64.842 | PASS |
| RedisChainIdBenchmark.generate_t4 | thrpt | 4 | step=10 | score (ops/us) | 5.505 | 44.689 | >= 4.080 | PASS |
| RedisChainIdBenchmark.generate_t4 | thrpt | 4 | step=10 | alloc (B/op) | 4.698 | 0.008 | <= 68.698 | PASS |
| RedisChainIdBenchmark.generate_t4 | thrpt | 4 | step=100 | score (ops/us) | 13.081 | 44.458 | >= 11.250 | PASS |
| RedisChainIdBenchmark.generate_t4 | thrpt | 4 | step=100 | alloc (B/op) | 1.102 | 0.008 | <= 65.102 | PASS |
| RedisStoreBenchmark.acquireRelease_t1 | avgt | 1 |  | score (us/op) | 841.087 | 419.291 | <= 883.141 (WARN <= 1,009) | PASS |
| RedisStoreBenchmark.acquireRelease_t1 | avgt | 1 |  | alloc (B/op) | 24,782 | 8,255 | <= 26,021 (WARN <= 30,977) | PASS |
| RedisStoreBenchmark.acquireRelease_t1 | sample | 1 |  | p99.0 (us/op) | 1,085 | 531.456 | <= 1,194 (WARN <= 1,411) | PASS |
| RedisStoreBenchmark.acquireRelease_t1 | sample | 1 |  | p99.9 (us/op) | 2,039 | 750.452 | <= 2,243 (WARN <= 2,650) | PASS |
| RedisStoreBenchmark.acquireRelease_t1 | sample | 1 |  | alloc (B/op) | 24,862 | 8,591 | <= 26,105 (WARN <= 31,078) | PASS |
| RedisStoreBenchmark.renew_t1 | avgt | 1 |  | score (us/op) | 399.672 | 195.069 | <= 419.655 (WARN <= 479.606) | PASS |
| RedisStoreBenchmark.renew_t1 | avgt | 1 |  | alloc (B/op) | 10,015 | 3,955 | <= 10,515 (WARN <= 12,518) | PASS |
| RedisStoreBenchmark.renew_t1 | sample | 1 |  | p99.0 (us/op) | 515.584 | 257.280 | <= 567.142 (WARN <= 670.259) | PASS |
| RedisStoreBenchmark.renew_t1 | sample | 1 |  | p99.9 (us/op) | 857.739 | 491.453 | <= 943.513 (WARN <= 1,115) | PASS |
| RedisStoreBenchmark.renew_t1 | sample | 1 |  | alloc (B/op) | 10,053 | 3,845 | <= 10,556 (WARN <= 12,566) | PASS |
| RedisStoreBenchmark.reserve_t1 | avgt | 1 |  | score (us/op) | 173.774 | 162.618 | <= 182.462 (WARN <= 208.529) | PASS |
| RedisStoreBenchmark.reserve_t1 | avgt | 1 |  | alloc (B/op) | 5,336 | 2,207 | <= 5,602 (WARN <= 6,670) | PASS |
| RedisStoreBenchmark.reserve_t1 | sample | 1 |  | p99.0 (us/op) | 226.048 | 214.272 | <= 248.653 (WARN <= 293.862) | PASS |
| RedisStoreBenchmark.reserve_t1 | sample | 1 |  | p99.9 (us/op) | 465.408 | 438.784 | <= 511.949 (WARN <= 605.030) | PASS |
| RedisStoreBenchmark.reserve_t1 | sample | 1 |  | alloc (B/op) | 5,351 | 2,266 | <= 5,618 (WARN <= 6,688) | PASS |

### Re-run of the failed rows

Both sides again, in fresh forks; a row that failed above passes only if it passes here.

| Benchmark | Mode | Threads | Params | Metric | 4.x | 5.x | Limit | Result |
|---|---|---|---|---|---|---|---|---|
| RedisChainIdBenchmark.generate_t1 | sample | 1 | step=100 | p99.0 (us/op) | 0.050 | 0.041 | <= 0.057 | PASS |
| RedisChainIdBenchmark.generate_t1 | sample | 1 | step=100 | p99.9 (us/op) | 0.151 | 0.170 | <= 0.166 | FAIL |
| RedisChainIdBenchmark.generate_t1 | sample | 1 | step=100 | alloc (B/op) | 0.432 | 0.007 | <= 64.432 | PASS |

**FAIL**: 1 of 18 baseline rows failed (1 failed the first run and were re-run).

## segment

4.1.1 (`benchmark/baseline-4x`) and 5.0 (`benchmark/v5`) on the same machine, interleaved per scenario (4.x then 5.x), judged by `benchmark/compare` with the rules of [10-performance-and-parity.md §1.3](../../architecture/10-performance-and-parity.md).

| | |
|---|---|
| Command | `benchmark/run-release P5 P6 P8` |
| JMH | 1.37, release (3 forks, 5 x 2 s warmup, 5 x 2 s measurement), `-prof gc` |
| Date | 2026-10-29T14:04:14+00:00 |
| Cpu | AMD EPYC 9V45 96-Core Processor |
| Cores | 4 |
| Memory | 15 GB |
| Os | Linux 7.0.0-1017-azure (x86_64) |
| Frequency scaling | unknown (a shared virtual machine: not controllable) |
| Runner | GitHub Actions 1000439909, image ubuntu24 20261026.354.1, run 40887469149 |
| Jdk | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS |
| Cosid 4.x | 4.1.1 |
| Commit | be35de36e91c6fbf2d35c030c60df35aa82f583c |
| Load at start | 2.63 0.66 0.22 |

Load average (1 minute) before each scenario: SegmentBenchmark 2.63, AtomicLongBenchmark 49.16, SegmentNoPrefetchBenchmark 49.21, PeriodicSegmentBenchmark 7.13


| Benchmark | Mode | Threads | Params | Metric | 4.x | 5.x | Limit | Result |
|---|---|---|---|---|---|---|---|---|
| PeriodicSegmentBenchmark.generate_t1 | sample | 1 |  | p99.0 (us/op) | 0.061 | 0.060 | <= 0.067 | PASS |
| PeriodicSegmentBenchmark.generate_t1 | sample | 1 |  | p99.9 (us/op) | 1.552 | 0.431 | <= 1.707 | PASS |
| PeriodicSegmentBenchmark.generate_t1 | sample | 1 |  | alloc (B/op) | 48.005 | 24.004 | <= 112.005 | PASS |
| PeriodicSegmentBenchmark.generate_t1 | thrpt | 1 |  | score (ops/us) | 30.808 | 35.708 | >= 29.267 | PASS |
| PeriodicSegmentBenchmark.generate_t1 | thrpt | 1 |  | alloc (B/op) | 48.002 | 24.001 | <= 112.002 | PASS |
| PeriodicSegmentBenchmark.generate_t4 | sample | 4 |  | p99.0 (us/op) | 0.321 | 0.180 | <= 0.353 | PASS |
| PeriodicSegmentBenchmark.generate_t4 | sample | 4 |  | p99.9 (us/op) | 7.480 | 0.351 | <= 8.228 | PASS |
| PeriodicSegmentBenchmark.generate_t4 | sample | 4 |  | alloc (B/op) | 40.018 | 24.007 | <= 104.018 | PASS |
| PeriodicSegmentBenchmark.generate_t4 | thrpt | 4 |  | score (ops/us) | 30.663 | 70.958 | >= 29.129 | PASS |
| PeriodicSegmentBenchmark.generate_t4 | thrpt | 4 |  | alloc (B/op) | 48.002 | 24.001 | <= 112.002 | PASS |
| SegmentBenchmark.generate_t1 | sample | 1 | step=10 | p99.0 (us/op) | 0.031 | 0.031 | <= 0.034 | PASS |
| SegmentBenchmark.generate_t1 | sample | 1 | step=10 | p99.9 (us/op) | 0.091 | 0.081 | <= 0.100 | PASS |
| SegmentBenchmark.generate_t1 | sample | 1 | step=10 | alloc (B/op) | 0.026 | 0.001 | <= 64.026 | PASS |
| SegmentBenchmark.generate_t1 | sample | 1 | step=100 | p99.0 (us/op) | 0.031 | 0.031 | <= 0.034 | PASS |
| SegmentBenchmark.generate_t1 | sample | 1 | step=100 | p99.9 (us/op) | 0.050 | 0.090 | <= 0.055 | FAIL |
| SegmentBenchmark.generate_t1 | sample | 1 | step=100 | alloc (B/op) | 0.002 | 0.001 | <= 64.002 | PASS |
| SegmentBenchmark.generate_t1 | thrpt | 1 | step=10 | score (ops/us) | 155.346 | 218.677 | >= 145.963 | PASS |
| SegmentBenchmark.generate_t1 | thrpt | 1 | step=10 | alloc (B/op) | 0.027 | 0.000 | < 1.0 | PASS |
| SegmentBenchmark.generate_t1 | thrpt | 1 | step=100 | score (ops/us) | 157.360 | 215.900 | >= 149.492 | PASS |
| SegmentBenchmark.generate_t1 | thrpt | 1 | step=100 | alloc (B/op) | 0.002 | 0.000 | < 1.0 | PASS |
| SegmentBenchmark.generate_t16 | sample | 16 | step=10 | p99.0 (us/op) | 0.331 | 0.141 | <= 0.418 | PASS |
| SegmentBenchmark.generate_t16 | sample | 16 | step=10 | p99.9 (us/op) | 0.801 | 0.210 | <= 0.888 | PASS |
| SegmentBenchmark.generate_t16 | sample | 16 | step=10 | alloc (B/op) | 0.088 | 0.024 | <= 64.088 | PASS |
| SegmentBenchmark.generate_t16 | sample | 16 | step=100 | p99.0 (us/op) | 0.321 | 0.150 | <= 0.397 | PASS |
| SegmentBenchmark.generate_t16 | sample | 16 | step=100 | p99.9 (us/op) | 0.450 | 0.210 | <= 0.526 | PASS |
| SegmentBenchmark.generate_t16 | sample | 16 | step=100 | alloc (B/op) | 0.051 | 0.025 | <= 64.051 | PASS |
| SegmentBenchmark.generate_t16 | thrpt | 16 | step=10 | score (ops/us) | 40.645 | 66.923 | >= 38.613 | PASS |
| SegmentBenchmark.generate_t16 | thrpt | 16 | step=10 | alloc (B/op) | 0.034 | 0.001 | < 1.0 | PASS |
| SegmentBenchmark.generate_t16 | thrpt | 16 | step=100 | score (ops/us) | 38.937 | 69.127 | >= 36.990 | PASS |
| SegmentBenchmark.generate_t16 | thrpt | 16 | step=100 | alloc (B/op) | 0.001 | 0.001 | < 1.0 | PASS |
| SegmentBenchmark.generate_t4 | sample | 4 | step=10 | p99.0 (us/op) | 0.321 | 0.141 | <= 0.353 | PASS |
| SegmentBenchmark.generate_t4 | sample | 4 | step=10 | p99.9 (us/op) | 0.781 | 0.210 | <= 0.859 | PASS |
| SegmentBenchmark.generate_t4 | sample | 4 | step=10 | alloc (B/op) | 0.027 | 0.006 | <= 64.027 | PASS |
| SegmentBenchmark.generate_t4 | sample | 4 | step=100 | p99.0 (us/op) | 0.331 | 0.150 | <= 0.364 | PASS |
| SegmentBenchmark.generate_t4 | sample | 4 | step=100 | p99.9 (us/op) | 0.471 | 0.220 | <= 0.518 | PASS |
| SegmentBenchmark.generate_t4 | sample | 4 | step=100 | alloc (B/op) | 0.011 | 0.006 | <= 64.011 | PASS |
| SegmentBenchmark.generate_t4 | thrpt | 4 | step=10 | score (ops/us) | 38.688 | 68.034 | >= 36.753 | PASS |
| SegmentBenchmark.generate_t4 | thrpt | 4 | step=10 | alloc (B/op) | 0.027 | 0.001 | < 1.0 | PASS |
| SegmentBenchmark.generate_t4 | thrpt | 4 | step=100 | score (ops/us) | 37.582 | 66.195 | >= 35.703 | PASS |
| SegmentBenchmark.generate_t4 | thrpt | 4 | step=100 | alloc (B/op) | 0.002 | 0.001 | < 1.0 | PASS |
| SegmentBenchmark.generate_t64 | sample | 64 | step=10 | p99.0 (us/op) | 0.311 | 0.150 | <= 0.511 | PASS |
| SegmentBenchmark.generate_t64 | sample | 64 | step=10 | p99.9 (us/op) | 1.342 | 0.210 | <= 1.542 | PASS |
| SegmentBenchmark.generate_t64 | sample | 64 | step=10 | alloc (B/op) | 0.277 | 0.061 | <= 64.277 | PASS |
| SegmentBenchmark.generate_t64 | sample | 64 | step=100 | p99.0 (us/op) | 0.310 | 0.140 | <= 0.504 | PASS |
| SegmentBenchmark.generate_t64 | sample | 64 | step=100 | p99.9 (us/op) | 0.420 | 0.210 | <= 0.614 | PASS |
| SegmentBenchmark.generate_t64 | sample | 64 | step=100 | alloc (B/op) | 0.121 | 0.063 | <= 64.121 | PASS |
| SegmentBenchmark.generate_t64 | thrpt | 64 | step=10 | score (ops/us) | 38.991 | 65.291 | >= 37.041 | PASS |
| SegmentBenchmark.generate_t64 | thrpt | 64 | step=10 | alloc (B/op) | 0.330 | 0.001 | < 1.0 | PASS |
| SegmentBenchmark.generate_t64 | thrpt | 64 | step=100 | score (ops/us) | 38.444 | 65.154 | >= 36.522 | PASS |
| SegmentBenchmark.generate_t64 | thrpt | 64 | step=100 | alloc (B/op) | 0.003 | 0.001 | < 1.0 | PASS |
| SegmentNoPrefetchBenchmark.generate_t1 | thrpt | 1 |  | score (ops/ms) | 13,395 | 21,353 | >= 12,725 | PASS |
| SegmentNoPrefetchBenchmark.generate_t1 | thrpt | 1 |  | alloc (B/op) | 24.000 | 0.000 | <= 88.000 | PASS |

### Re-run of the failed rows

Both sides again, in fresh forks; a row that failed above passes only if it passes here.

| Benchmark | Mode | Threads | Params | Metric | 4.x | 5.x | Limit | Result |
|---|---|---|---|---|---|---|---|---|
| SegmentBenchmark.generate_t1 | sample | 1 | step=100 | p99.0 (us/op) | 0.031 | 0.031 | <= 0.034 | PASS |
| SegmentBenchmark.generate_t1 | sample | 1 | step=100 | p99.9 (us/op) | 0.071 | 0.110 | <= 0.078 | FAIL |
| SegmentBenchmark.generate_t1 | sample | 1 | step=100 | alloc (B/op) | 0.002 | 0.001 | <= 64.002 | PASS |

### Reference (not judged)

The bound of a scenario on this runner, and each side of the scenario as a share of it at the same threads.

| Reference | Threads | ops/µs | 4.x | 5.x |
|---|---|---|---|---|
| AtomicLongBenchmark.generate_t1 | 1 | 213.769 | step=10: 155.346 (73%), step=100: 157.360 (74%) | step=10: 218.677 (102%), step=100: 215.900 (101%) |
| AtomicLongBenchmark.generate_t16 | 16 | 95.083 | step=10: 40.645 (43%), step=100: 38.937 (41%) | step=10: 66.923 (70%), step=100: 69.127 (73%) |
| AtomicLongBenchmark.generate_t4 | 4 | 96.752 | step=10: 38.688 (40%), step=100: 37.582 (39%) | step=10: 68.034 (70%), step=100: 66.195 (68%) |
| AtomicLongBenchmark.generate_t64 | 64 | 94.786 | step=10: 38.991 (41%), step=100: 38.444 (41%) | step=10: 65.291 (69%), step=100: 65.154 (69%) |

**FAIL**: 1 of 21 baseline rows failed (1 failed the first run and were re-run).

## snowflake

4.1.1 (`benchmark/baseline-4x`) and 5.0 (`benchmark/v5`) on the same machine, interleaved per scenario (4.x then 5.x), judged by `benchmark/compare` with the rules of [10-performance-and-parity.md §1.3](../../architecture/10-performance-and-parity.md).

| | |
|---|---|
| Command | `benchmark/run-release P1 P2 P3 P4 P13` |
| JMH | 1.37, release (3 forks, 5 x 2 s warmup, 5 x 2 s measurement), `-prof gc` |
| Date | 2026-10-29T14:04:25+00:00 |
| Cpu | AMD EPYC 9V74 80-Core Processor |
| Cores | 4 |
| Memory | 15 GB |
| Os | Linux 7.0.0-1017-azure (x86_64) |
| Frequency scaling | unknown (a shared virtual machine: not controllable) |
| Runner | GitHub Actions 1000439910, image ubuntu24 20261026.354.1, run 40887469149 |
| Jdk | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS |
| Cosid 4.x | 4.1.1 |
| Commit | be35de36e91c6fbf2d35c030c60df35aa82f583c |
| Load at start | 2.98 0.99 0.35 |

Load average (1 minute) before each scenario: SnowflakeMillisBenchmark 3.23, SnowflakeSecondsBenchmark 3.41, SnowflakeStringBenchmark 0.17, CosId80Benchmark 1.24, UuidV7Benchmark 1.15


| Benchmark | Mode | Threads | Params | Metric | 4.x | 5.x | Limit | Result |
|---|---|---|---|---|---|---|---|---|
| CosId80Benchmark.friendly_t1 | thrpt | 1 |  | score (ops/ms) | 4,969 | 9,186 | >= 4,721 | PASS |
| CosId80Benchmark.friendly_t1 | thrpt | 1 |  | alloc (B/op) | 524.328 | 120.000 | <= 588.328 | PASS |
| CosId80Benchmark.radix62_t1 | thrpt | 1 |  | score (ops/ms) | 12,033 | 17,060 | >= 11,431 | PASS |
| CosId80Benchmark.radix62_t1 | thrpt | 1 |  | alloc (B/op) | 208.000 | 88.000 | <= 272.000 | PASS |
| SnowflakeMillisBenchmark.generate_t1 | thrpt | 1 |  | score (ops/ms) | 4,096 | 4,096 | >= 3,892 | PASS |
| SnowflakeMillisBenchmark.generate_t1 | thrpt | 1 |  | alloc (B/op) | 0.001 | 0.001 | < 1.0 | PASS |
| SnowflakeMillisBenchmark.generate_t16 | thrpt | 16 |  | score (ops/ms) | 4,094 | 4,094 | >= 3,890 | PASS |
| SnowflakeMillisBenchmark.generate_t16 | thrpt | 16 |  | alloc (B/op) | 0.003 | 0.003 | < 1.0 | PASS |
| SnowflakeMillisBenchmark.generate_t4 | thrpt | 4 |  | score (ops/ms) | 4,096 | 4,097 | >= 3,891 | PASS |
| SnowflakeMillisBenchmark.generate_t4 | thrpt | 4 |  | alloc (B/op) | 0.001 | 0.001 | < 1.0 | PASS |
| SnowflakeSecondsBenchmark.generate_t1 | thrpt | 1 |  | score (ops/ms) | 4,229 | 4,230 | >= 4,018 | PASS |
| SnowflakeSecondsBenchmark.generate_t1 | thrpt | 1 |  | alloc (B/op) | 0.001 | 0.001 | < 1.0 | PASS |
| SnowflakeSecondsBenchmark.generate_t16 | thrpt | 16 |  | score (ops/ms) | 4,256 | 4,242 | >= 4,043 | PASS |
| SnowflakeSecondsBenchmark.generate_t16 | thrpt | 16 |  | alloc (B/op) | 0.002 | 0.003 | < 1.0 | PASS |
| SnowflakeStringBenchmark.friendly_t1 | thrpt | 1 |  | score (ops/ms) | 4,068 | 4,095 | >= 3,864 | PASS |
| SnowflakeStringBenchmark.friendly_t1 | thrpt | 1 |  | alloc (B/op) | 376.001 | 120.001 | <= 440.001 | PASS |
| SnowflakeStringBenchmark.radix62_t1 | thrpt | 1 |  | score (ops/ms) | 4,096 | 4,095 | >= 3,892 | PASS |
| SnowflakeStringBenchmark.radix62_t1 | thrpt | 1 |  | alloc (B/op) | 96.001 | 88.001 | <= 160.001 | PASS |

### 5.x trend (not judged)

5.x alone, with no 4.x counterpart: reported to follow its trend across runs, never compared.

| Benchmark | Mode | Threads | Params | Score | Error | Unit |
|---|---|---|---|---|---|---|
| UuidV7Benchmark.generate_t1 | thrpt | 1 | - | 31.821 | 0.085 | ops/us |
| UuidV7Benchmark.generate_t16 | thrpt | 16 | - | 33.587 | 10.146 | ops/us |
| UuidV7Benchmark.generate_t4 | thrpt | 4 | - | 32.071 | 12.948 | ops/us |
| UuidV7Benchmark.text_t1 | thrpt | 1 | - | 24.028 | 0.125 | ops/us |

**PASS**: 0 of 9 baseline rows failed.

## spring-start

4.1.1 (`benchmark/baseline-4x`) and 5.0 (`benchmark/v5`) on the same machine, interleaved per scenario (4.x then 5.x), judged by `benchmark/compare` with the rules of [10-performance-and-parity.md §1.3](../../architecture/10-performance-and-parity.md).

| | |
|---|---|
| Command | `benchmark/run-release P11` |
| JMH | 1.37, release (3 forks, 5 x 2 s warmup, 5 x 2 s measurement), `-prof gc` |
| Date | 2026-10-29T14:04:46+00:00 |
| Cpu | AMD EPYC 7763 64-Core Processor |
| Cores | 4 |
| Memory | 15 GB |
| Os | Linux 7.0.0-1017-azure (x86_64) |
| Frequency scaling | unknown (a shared virtual machine: not controllable) |
| Runner | GitHub Actions 1000439914, image ubuntu24 20261026.354.1, run 40887469149 |
| Jdk | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS |
| Cosid 4.x | 4.1.1 |
| Commit | be35de36e91c6fbf2d35c030c60df35aa82f583c |
| Load at start | 2.40 0.71 0.25 |

Load average (1 minute) before each scenario: SpringStartBenchmark 2.36


| Benchmark | Mode | Threads | Params | Metric | 4.x | 5.x | Limit | Result |
|---|---|---|---|---|---|---|---|---|
| SpringStartBenchmark.startToFirstId_t1 | avgt | 1 |  | time (ms/op) | 16.202 | 14.205 | <= 17.012 | PASS |
| SpringStartBenchmark.startToFirstId_t1 | avgt | 1 |  | alloc (B/op) | 7,118,806 | 6,132,584 | <= 7,474,746 | PASS |

**PASS**: 0 of 1 baseline rows failed.

## zookeeper

4.1.1 (`benchmark/baseline-4x`) and 5.0 (`benchmark/v5`) on the same machine, interleaved per scenario (4.x then 5.x), judged by `benchmark/compare` with the rules of [10-performance-and-parity.md §1.3](../../architecture/10-performance-and-parity.md).

| | |
|---|---|
| Command | `benchmark/run-release ZookeeperChainIdBenchmark ZookeeperStoreBenchmark` |
| JMH | 1.37, release (3 forks, 5 x 2 s warmup, 5 x 2 s measurement), `-prof gc` |
| Date | 2026-10-29T14:04:45+00:00 |
| Cpu | AMD EPYC 7763 64-Core Processor |
| Cores | 4 |
| Memory | 15 GB |
| Os | Linux 7.0.0-1017-azure (x86_64) |
| Frequency scaling | unknown (a shared virtual machine: not controllable) |
| Runner | GitHub Actions 1000439911, image ubuntu24 20261026.354.1, run 40887469149 |
| Jdk | Eclipse Adoptium OpenJDK 64-Bit Server VM 21.0.13+9-LTS |
| Cosid 4.x | 4.1.1 |
| Commit | be35de36e91c6fbf2d35c030c60df35aa82f583c |
| Load at start | 2.50 0.77 0.32 |

Load average (1 minute) before each scenario: ZookeeperChainIdBenchmark 2.86, ZookeeperStoreBenchmark 3.66


| Benchmark | Mode | Threads | Params | Metric | 4.x | 5.x | Limit | Result |
|---|---|---|---|---|---|---|---|---|
| ZookeeperChainIdBenchmark.generate_t1 | sample | 1 | step=10 | p99.0 (us/op) | 0.100 | 0.041 | <= 0.305 | PASS |
| ZookeeperChainIdBenchmark.generate_t1 | sample | 1 | step=10 | p99.9 (us/op) | 718.848 | 0.211 | <= 790.733 | PASS |
| ZookeeperChainIdBenchmark.generate_t1 | sample | 1 | step=10 | alloc (B/op) | 37.333 | 0.007 | <= 101.333 | PASS |
| ZookeeperChainIdBenchmark.generate_t1 | sample | 1 | step=100 | p99.0 (us/op) | 0.050 | 0.041 | <= 0.092 | PASS |
| ZookeeperChainIdBenchmark.generate_t1 | sample | 1 | step=100 | p99.9 (us/op) | 0.150 | 0.191 | <= 0.192 | PASS |
| ZookeeperChainIdBenchmark.generate_t1 | sample | 1 | step=100 | alloc (B/op) | 1.615 | 0.007 | <= 65.615 | PASS |
| ZookeeperChainIdBenchmark.generate_t1 | thrpt | 1 | step=10 | score (ops/us) | 0.332 | 401.231 | >= 0.154 | PASS |
| ZookeeperChainIdBenchmark.generate_t1 | thrpt | 1 | step=10 | alloc (B/op) | 47.339 | 0.006 | <= 111.339 | PASS |
| ZookeeperChainIdBenchmark.generate_t1 | thrpt | 1 | step=100 | score (ops/us) | 3.592 | 399.815 | >= 2.286 | PASS |
| ZookeeperChainIdBenchmark.generate_t1 | thrpt | 1 | step=100 | alloc (B/op) | 3.004 | 0.006 | <= 67.004 | PASS |
| ZookeeperChainIdBenchmark.generate_t16 | sample | 16 | step=10 | p99.0 (us/op) | 0.370 | 0.260 | <= 19.348 | PASS |
| ZookeeperChainIdBenchmark.generate_t16 | sample | 16 | step=10 | p99.9 (us/op) | 704.512 | 0.380 | <= 774.963 | PASS |
| ZookeeperChainIdBenchmark.generate_t16 | sample | 16 | step=10 | alloc (B/op) | 8.599 | 0.071 | <= 72.599 | PASS |
| ZookeeperChainIdBenchmark.generate_t16 | sample | 16 | step=100 | p99.0 (us/op) | 0.370 | 0.251 | <= 3.545 | PASS |
| ZookeeperChainIdBenchmark.generate_t16 | sample | 16 | step=100 | p99.9 (us/op) | 0.641 | 0.351 | <= 3.816 | PASS |
| ZookeeperChainIdBenchmark.generate_t16 | sample | 16 | step=100 | alloc (B/op) | 2.321 | 0.072 | <= 66.321 | PASS |
| ZookeeperChainIdBenchmark.generate_t16 | thrpt | 16 | step=10 | score (ops/us) | 40.915 | 44.019 | >= 14.698 | PASS |
| ZookeeperChainIdBenchmark.generate_t16 | thrpt | 16 | step=10 | alloc (B/op) | 9.997 | 0.008 | <= 73.997 | PASS |
| ZookeeperChainIdBenchmark.generate_t16 | thrpt | 16 | step=100 | score (ops/us) | 2.257 | 44.520 | >= 0.277 | PASS |
| ZookeeperChainIdBenchmark.generate_t16 | thrpt | 16 | step=100 | alloc (B/op) | 4.478 | 0.008 | <= 68.478 | PASS |
| ZookeeperChainIdBenchmark.generate_t4 | sample | 4 | step=10 | p99.0 (us/op) | 0.380 | 0.250 | <= 9.305 | PASS |
| ZookeeperChainIdBenchmark.generate_t4 | sample | 4 | step=10 | p99.9 (us/op) | 679.936 | 0.511 | <= 747.930 | PASS |
| ZookeeperChainIdBenchmark.generate_t4 | sample | 4 | step=10 | alloc (B/op) | 26.957 | 0.022 | <= 90.957 | PASS |
| ZookeeperChainIdBenchmark.generate_t4 | sample | 4 | step=100 | p99.0 (us/op) | 0.370 | 0.250 | <= 3.682 | PASS |
| ZookeeperChainIdBenchmark.generate_t4 | sample | 4 | step=100 | p99.9 (us/op) | 23.909 | 0.551 | <= 27.221 | PASS |
| ZookeeperChainIdBenchmark.generate_t4 | sample | 4 | step=100 | alloc (B/op) | 3.608 | 0.023 | <= 67.608 | PASS |
| ZookeeperChainIdBenchmark.generate_t4 | thrpt | 4 | step=10 | score (ops/us) | 7.105 | 44.613 | >= 1.065 | PASS |
| ZookeeperChainIdBenchmark.generate_t4 | thrpt | 4 | step=10 | alloc (B/op) | 16.662 | 0.008 | <= 80.662 | PASS |
| ZookeeperChainIdBenchmark.generate_t4 | thrpt | 4 | step=100 | score (ops/us) | 2.516 | 43.733 | >= 1.021 | PASS |
| ZookeeperChainIdBenchmark.generate_t4 | thrpt | 4 | step=100 | alloc (B/op) | 5.887 | 0.008 | <= 69.887 | PASS |
| ZookeeperStoreBenchmark.acquire1024_t1 | avgt | 1 |  | score (us/op) | 4,307 | 1,929 | <= 4,523 (WARN <= 5,169) | PASS |
| ZookeeperStoreBenchmark.acquire1024_t1 | avgt | 1 |  | alloc (B/op) | 53,279 | 36,153 | <= 55,943 (WARN <= 66,599) | PASS |
| ZookeeperStoreBenchmark.acquire1024_t1 | sample | 1 |  | p99.0 (us/op) | 7,149 | 4,350 | <= 7,864 (WARN <= 9,294) | PASS |
| ZookeeperStoreBenchmark.acquire1024_t1 | sample | 1 |  | p99.9 (us/op) | 9,725 | 6,448 | <= 10,697 (WARN <= 12,642) | PASS |
| ZookeeperStoreBenchmark.acquire1024_t1 | sample | 1 |  | alloc (B/op) | 53,582 | 36,322 | <= 56,261 (WARN <= 66,978) | PASS |
| ZookeeperStoreBenchmark.acquire_t1 | avgt | 1 |  | score (us/op) | 4,325 | 1,928 | <= 4,541 (WARN <= 5,190) | PASS |
| ZookeeperStoreBenchmark.acquire_t1 | avgt | 1 |  | alloc (B/op) | 54,074 | 36,410 | <= 56,778 (WARN <= 67,592) | PASS |
| ZookeeperStoreBenchmark.acquire_t1 | sample | 1 |  | p99.0 (us/op) | 7,748 | 4,327 | <= 8,523 (WARN <= 10,072) | PASS |
| ZookeeperStoreBenchmark.acquire_t1 | sample | 1 |  | p99.9 (us/op) | 10,515 | 6,528 | <= 11,566 (WARN <= 13,669) | PASS |
| ZookeeperStoreBenchmark.acquire_t1 | sample | 1 |  | alloc (B/op) | 54,135 | 36,565 | <= 56,842 (WARN <= 67,669) | PASS |
| ZookeeperStoreBenchmark.renew_t1 | avgt | 1 |  | score (us/op) | 707.681 | 703.458 | <= 743.065 (WARN <= 849.218) | PASS |
| ZookeeperStoreBenchmark.renew_t1 | avgt | 1 |  | alloc (B/op) | 9,021 | 8,775 | <= 9,472 (WARN <= 11,276) | PASS |
| ZookeeperStoreBenchmark.renew_t1 | sample | 1 |  | p99.0 (us/op) | 1,420 | 1,391 | <= 1,562 (WARN <= 1,845) | PASS |
| ZookeeperStoreBenchmark.renew_t1 | sample | 1 |  | p99.9 (us/op) | 3,838 | 3,686 | <= 4,222 (WARN <= 4,989) | PASS |
| ZookeeperStoreBenchmark.renew_t1 | sample | 1 |  | alloc (B/op) | 9,077 | 8,845 | <= 9,530 (WARN <= 11,346) | PASS |
| ZookeeperStoreBenchmark.reserve_t1 | avgt | 1 |  | score (us/op) | 661.977 | 406.555 | <= 695.075 (WARN <= 794.372) | PASS |
| ZookeeperStoreBenchmark.reserve_t1 | avgt | 1 |  | alloc (B/op) | 5,822 | 3,289 | <= 6,113 (WARN <= 7,277) | PASS |
| ZookeeperStoreBenchmark.reserve_t1 | sample | 1 |  | p99.0 (us/op) | 1,323 | 791.552 | <= 1,455 (WARN <= 1,719) | PASS |
| ZookeeperStoreBenchmark.reserve_t1 | sample | 1 |  | p99.9 (us/op) | 3,930 | 3,187 | <= 4,323 (WARN <= 5,109) | PASS |
| ZookeeperStoreBenchmark.reserve_t1 | sample | 1 |  | alloc (B/op) | 5,882 | 3,313 | <= 6,176 (WARN <= 7,353) | PASS |

**PASS**: 0 of 20 baseline rows failed.
