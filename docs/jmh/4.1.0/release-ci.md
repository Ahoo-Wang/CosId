# CosId 4.1.0 baseline (release gate)

The 4.x side of the release gate (release (3 forks, 5 x 2 s warmup, 5 x 2 s measurement), GitHub Actions runners; see [../5.0/report.md](../5.0/report.md) for each group's runner). Commit 614d5df92c64b33d67b1750814e2bded4a0d116b.

| Benchmark | Mode | Threads | Params | Score | Error | Unit | p99 | p99.9 | Alloc (B/op) |
|---|---|---|---|---|---|---|---|---|---|
| CodecBenchmark.friendlyDecode_t1 | thrpt | 1 |  | 1,538.620 | 31.989 | ops/ms |  |  | 2,360.002 |
| CodecBenchmark.friendlyEncode_t1 | thrpt | 1 |  | 8,578.570 | 238.654 | ops/ms |  |  | 576.000 |
| CodecBenchmark.radix36Decode_t1 | thrpt | 1 |  | 73,938.096 | 707.858 | ops/ms |  |  | 0.000 |
| CodecBenchmark.radix36Encode_t1 | thrpt | 1 |  | 48,142.482 | 456.891 | ops/ms |  |  | 104.000 |
| CodecBenchmark.radix62Decode_t1 | thrpt | 1 |  | 81,603.709 | 1,151.468 | ops/ms |  |  | 0.000 |
| CodecBenchmark.radix62Encode_t1 | thrpt | 1 |  | 54,302.507 | 2,006.849 | ops/ms |  |  | 96.000 |
| CosId80Benchmark.friendly_t1 | thrpt | 1 |  | 7,228.742 | 80.959 | ops/ms |  |  | 523.775 |
| CosId80Benchmark.radix62_t1 | thrpt | 1 |  | 15,417.815 | 181.661 | ops/ms |  |  | 208.000 |
| IdFillerBenchmark.fill_t1 | thrpt | 1 |  | 35,484.147 | 599.072 | ops/ms |  |  | 64.001 |
| MongoStoreBenchmark.acquire_t1 | avgt | 1 |  | 5,572.529 | 1,450.330 | us/op |  |  | 146,367.707 |
| MongoStoreBenchmark.acquire_t1 | sample | 1 |  | 6,798.536 | 830.735 | us/op | 87,345.070 | 234,786.652 | 147,802.534 |
| MongoStoreBenchmark.renew_t1 | avgt | 1 |  | 2,265.868 | 746.814 | us/op |  |  | 49,668.756 |
| MongoStoreBenchmark.renew_t1 | sample | 1 |  | 2,188.230 | 321.125 | us/op | 25,014.436 | 181,843.001 | 49,840.971 |
| MongoStoreBenchmark.reserve_t1 | avgt | 1 |  | 3,747.486 | 2,036.213 | us/op |  |  | 32,746.506 |
| MongoStoreBenchmark.reserve_t1 | sample | 1 |  | 2,875.109 | 518.163 | us/op | 61,848.945 | 294,280.757 | 32,873.446 |
| MySqlChainIdBenchmark.generate_t1 | sample | 1 | step=10 | 0.965 | 0.081 | us/op | 0.059 | 532.480 | 6.893 |
| MySqlChainIdBenchmark.generate_t1 | sample | 1 | step=100 | 0.040 | 0.005 | us/op | 0.060 | 0.211 | 0.393 |
| MySqlChainIdBenchmark.generate_t1 | thrpt | 1 | step=10 | 4.042 | 3.548 | ops/us |  |  | 5.590 |
| MySqlChainIdBenchmark.generate_t1 | thrpt | 1 | step=100 | 24.836 | 3.386 | ops/us |  |  | 0.463 |
| MySqlChainIdBenchmark.generate_t16 | sample | 16 | step=10 | 75.657 | 23.151 | us/op | 0.382 | 633.856 | 64.668 |
| MySqlChainIdBenchmark.generate_t16 | sample | 16 | step=100 | 6.575 | 2.232 | us/op | 0.402 | 1.171 | 3.615 |
| MySqlChainIdBenchmark.generate_t16 | thrpt | 16 | step=10 | 25.351 | 25.582 | ops/us |  |  | 112.766 |
| MySqlChainIdBenchmark.generate_t16 | thrpt | 16 | step=100 | 5.705 | 4.238 | ops/us |  |  | 5.069 |
| MySqlChainIdBenchmark.generate_t4 | sample | 4 | step=10 | 18.957 | 5.358 | us/op | 0.434 | 626.688 | 39.550 |
| MySqlChainIdBenchmark.generate_t4 | sample | 4 | step=100 | 1.086 | 0.550 | us/op | 0.441 | 7.520 | 1.368 |
| MySqlChainIdBenchmark.generate_t4 | thrpt | 4 | step=10 | 0.367 | 0.183 | ops/us |  |  | 47.564 |
| MySqlChainIdBenchmark.generate_t4 | thrpt | 4 | step=100 | 6.684 | 2.503 | ops/us |  |  | 2.142 |
| MySqlStoreBenchmark.acquire_t1 | avgt | 1 |  | 1,301.047 | 12.826 | us/op |  |  | 50,587.781 |
| MySqlStoreBenchmark.acquire_t1 | sample | 1 |  | 1,366.579 | 4.027 | us/op | 1,740.800 | 2,944.602 | 50,748.722 |
| MySqlStoreBenchmark.renew_t1 | avgt | 1 |  | 735.480 | 14.450 | us/op |  |  | 16,409.663 |
| MySqlStoreBenchmark.renew_t1 | sample | 1 |  | 759.910 | 2.062 | us/op | 1,038.336 | 1,737.578 | 16,417.104 |
| MySqlStoreBenchmark.reserve_t1 | avgt | 1 |  | 550.679 | 7.937 | us/op |  |  | 8,617.914 |
| MySqlStoreBenchmark.reserve_t1 | sample | 1 |  | 555.657 | 1.354 | us/op | 801.792 | 1,216.788 | 8,660.348 |
| PeriodicSegmentBenchmark.generate_t1 | sample | 1 |  | 0.067 | 0.003 | us/op | 0.070 | 1.129 | 48.005 |
| PeriodicSegmentBenchmark.generate_t1 | thrpt | 1 |  | 28.401 | 0.093 | ops/us |  |  | 48.002 |
| PeriodicSegmentBenchmark.generate_t4 | sample | 4 |  | 0.211 | 0.018 | us/op | 0.271 | 5.888 | 48.019 |
| PeriodicSegmentBenchmark.generate_t4 | thrpt | 4 |  | 31.611 | 1.259 | ops/us |  |  | 48.001 |
| RedisChainIdBenchmark.generate_t1 | sample | 1 | step=10 | 4.986 | 0.126 | us/op | 214.784 | 251.392 | 58.392 |
| RedisChainIdBenchmark.generate_t1 | sample | 1 | step=100 | 0.128 | 0.016 | us/op | 0.069 | 0.268 | 3.118 |
| RedisChainIdBenchmark.generate_t1 | thrpt | 1 | step=10 | 0.521 | 0.186 | ops/us |  |  | 55.231 |
| RedisChainIdBenchmark.generate_t1 | thrpt | 1 | step=100 | 6.173 | 2.400 | ops/us |  |  | 4.332 |
| RedisChainIdBenchmark.generate_t16 | sample | 16 | step=10 | 3.474 | 0.211 | us/op | 0.401 | 12.368 | 0.534 |
| RedisChainIdBenchmark.generate_t16 | sample | 16 | step=100 | 2.027 | 0.153 | us/op | 0.391 | 2.192 | 0.168 |
| RedisChainIdBenchmark.generate_t16 | thrpt | 16 | step=10 | 16.348 | 1.317 | ops/us |  |  | 0.593 |
| RedisChainIdBenchmark.generate_t16 | thrpt | 16 | step=100 | 23.045 | 1.159 | ops/us |  |  | 0.061 |
| RedisChainIdBenchmark.generate_t4 | sample | 4 | step=10 | 18.873 | 18.093 | us/op | 0.401 | 236.800 | 54.859 |
| RedisChainIdBenchmark.generate_t4 | sample | 4 | step=100 | 0.705 | 1.115 | us/op | 0.400 | 11.760 | 0.818 |
| RedisChainIdBenchmark.generate_t4 | thrpt | 4 | step=10 | 1.508 | 3.748 | ops/us |  |  | 124.009 |
| RedisChainIdBenchmark.generate_t4 | thrpt | 4 | step=100 | 3.695 | 3.812 | ops/us |  |  | 10.840 |
| RedisStoreBenchmark.acquireRelease_t1 | avgt | 1 |  | 1,005.191 | 12.807 | us/op |  |  | 25,062.085 |
| RedisStoreBenchmark.acquireRelease_t1 | sample | 1 |  | 1,010.627 | 2.530 | us/op | 1,298.432 | 2,463.416 | 25,172.244 |
| RedisStoreBenchmark.renew_t1 | avgt | 1 |  | 483.771 | 7.220 | us/op |  |  | 10,037.527 |
| RedisStoreBenchmark.renew_t1 | sample | 1 |  | 482.769 | 0.866 | us/op | 611.328 | 1,161.124 | 10,126.457 |
| RedisStoreBenchmark.reserve_t1 | avgt | 1 |  | 209.289 | 1.887 | us/op |  |  | 5,307.639 |
| RedisStoreBenchmark.reserve_t1 | sample | 1 |  | 208.775 | 0.255 | us/op | 254.976 | 526.798 | 5,313.647 |
| SegmentBenchmark.generate_t1 | sample | 1 | step=10 | 0.035 | 0.001 | us/op | 0.041 | 0.170 | 0.036 |
| SegmentBenchmark.generate_t1 | sample | 1 | step=100 | 0.034 | 0.001 | us/op | 0.041 | 0.140 | 0.002 |
| SegmentBenchmark.generate_t1 | thrpt | 1 | step=10 | 178.608 | 3.704 | ops/us |  |  | 0.025 |
| SegmentBenchmark.generate_t1 | thrpt | 1 | step=100 | 180.412 | 4.197 | ops/us |  |  | 0.002 |
| SegmentBenchmark.generate_t16 | sample | 16 | step=10 | 1.248 | 0.090 | us/op | 0.230 | 1.512 | 0.085 |
| SegmentBenchmark.generate_t16 | sample | 16 | step=100 | 1.058 | 0.084 | us/op | 0.221 | 0.320 | 0.045 |
| SegmentBenchmark.generate_t16 | thrpt | 16 | step=10 | 43.089 | 4.374 | ops/us |  |  | 0.025 |
| SegmentBenchmark.generate_t16 | thrpt | 16 | step=100 | 42.601 | 0.595 | ops/us |  |  | 0.002 |
| SegmentBenchmark.generate_t4 | sample | 4 | step=10 | 0.150 | 0.023 | us/op | 0.221 | 2.392 | 0.025 |
| SegmentBenchmark.generate_t4 | sample | 4 | step=100 | 0.134 | 0.015 | us/op | 0.221 | 0.361 | 0.011 |
| SegmentBenchmark.generate_t4 | thrpt | 4 | step=10 | 42.980 | 0.506 | ops/us |  |  | 0.015 |
| SegmentBenchmark.generate_t4 | thrpt | 4 | step=100 | 43.406 | 0.761 | ops/us |  |  | 0.001 |
| SegmentBenchmark.generate_t64 | sample | 64 | step=10 | 5.850 | 0.237 | us/op | 0.230 | 2.812 | 0.306 |
| SegmentBenchmark.generate_t64 | sample | 64 | step=100 | 4.593 | 0.214 | us/op | 0.221 | 0.320 | 0.109 |
| SegmentBenchmark.generate_t64 | thrpt | 64 | step=10 | 42.630 | 1.569 | ops/us |  |  | 0.155 |
| SegmentBenchmark.generate_t64 | thrpt | 64 | step=100 | 43.391 | 0.767 | ops/us |  |  | 0.003 |
| SegmentNoPrefetchBenchmark.generate_t1 | thrpt | 1 |  | 11,772.638 | 24.815 | ops/ms |  |  | 24.000 |
| SnowflakeMillisBenchmark.generate_t1 | thrpt | 1 |  | 4,096.393 | 0.468 | ops/ms |  |  | 0.001 |
| SnowflakeMillisBenchmark.generate_t16 | thrpt | 16 |  | 4,096.147 | 2.882 | ops/ms |  |  | 0.003 |
| SnowflakeMillisBenchmark.generate_t4 | thrpt | 4 |  | 4,096.432 | 0.594 | ops/ms |  |  | 0.001 |
| SnowflakeSecondsBenchmark.generate_t1 | thrpt | 1 |  | 4,223.246 | 20.175 | ops/ms |  |  | 0.001 |
| SnowflakeSecondsBenchmark.generate_t16 | thrpt | 16 |  | 4,229.356 | 82.189 | ops/ms |  |  | 0.002 |
| SnowflakeStringBenchmark.friendly_t1 | thrpt | 1 |  | 4,082.882 | 20.337 | ops/ms |  |  | 387.639 |
| SnowflakeStringBenchmark.radix62_t1 | thrpt | 1 |  | 4,096.547 | 0.710 | ops/ms |  |  | 96.001 |
| SpringStartBenchmark.startToFirstId_t1 | avgt | 1 |  | 14.185 | 2.431 | ms/op |  |  | 7,106,535.617 |
| ZookeeperStoreBenchmark.acquire1024_t1 | avgt | 1 |  | 4,966.946 | 212.342 | us/op |  |  | 53,571.868 |
| ZookeeperStoreBenchmark.acquire1024_t1 | sample | 1 |  | 4,989.888 | 69.541 | us/op | 14,776.730 | 21,781.873 | 54,014.540 |
| ZookeeperStoreBenchmark.acquire_t1 | avgt | 1 |  | 4,969.370 | 152.530 | us/op |  |  | 54,338.017 |
| ZookeeperStoreBenchmark.acquire_t1 | sample | 1 |  | 4,880.140 | 56.121 | us/op | 13,730.447 | 19,313.721 | 54,481.508 |
| ZookeeperStoreBenchmark.renew_t1 | avgt | 1 |  | 837.502 | 13.435 | us/op |  |  | 8,982.395 |
| ZookeeperStoreBenchmark.renew_t1 | sample | 1 |  | 840.667 | 10.072 | us/op | 1,200.128 | 11,341.038 | 9,019.577 |
| ZookeeperStoreBenchmark.reserve_t1 | avgt | 1 |  | 780.377 | 10.474 | us/op |  |  | 5,861.457 |
| ZookeeperStoreBenchmark.reserve_t1 | sample | 1 |  | 794.410 | 10.513 | us/op | 1,458.176 | 11,948.851 | 5,926.293 |
