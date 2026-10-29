# CosId 4.1.1 baseline (release gate)

The 4.x side of the release gate (release (3 forks, 5 x 2 s warmup, 5 x 2 s measurement), GitHub Actions runners; see [report.md](report.md) for each group's runner). Commit be35de36e91c6fbf2d35c030c60df35aa82f583c.

| Benchmark | Mode | Threads | Params | Score | Error | Unit | p99 | p99.9 | Alloc (B/op) |
|---|---|---|---|---|---|---|---|---|---|
| CodecBenchmark.friendlyDecode_t1 | thrpt | 1 |  | 1,621.385 | 40.320 | ops/ms |  |  | 2,360.002 |
| CodecBenchmark.friendlyEncode_t1 | thrpt | 1 |  | 8,801.133 | 128.384 | ops/ms |  |  | 576.000 |
| CodecBenchmark.radix36Decode_t1 | thrpt | 1 |  | 76,868.356 | 676.404 | ops/ms |  |  | 0.000 |
| CodecBenchmark.radix36Encode_t1 | thrpt | 1 |  | 47,340.310 | 670.746 | ops/ms |  |  | 104.000 |
| CodecBenchmark.radix62Decode_t1 | thrpt | 1 |  | 81,838.184 | 1,549.301 | ops/ms |  |  | 0.000 |
| CodecBenchmark.radix62Encode_t1 | thrpt | 1 |  | 54,202.848 | 762.318 | ops/ms |  |  | 96.000 |
| CosId80Benchmark.friendly_t1 | thrpt | 1 |  | 4,969.316 | 58.070 | ops/ms |  |  | 524.328 |
| CosId80Benchmark.radix62_t1 | thrpt | 1 |  | 12,033.151 | 76.343 | ops/ms |  |  | 208.000 |
| IdFillerBenchmark.fill_t1 | thrpt | 1 |  | 35,201.990 | 654.094 | ops/ms |  |  | 64.001 |
| MongoChainIdBenchmark.generate_t1 | sample | 1 | step=10 | 6.072 | 0.265 | us/op | 0.161 | 1,099.889 | 238.725 |
| MongoChainIdBenchmark.generate_t1 | sample | 1 | step=100 | 0.446 | 0.062 | us/op | 0.050 | 0.260 | 22.053 |
| MongoChainIdBenchmark.generate_t1 | thrpt | 1 | step=10 | 6.520 | 8.451 | ops/us |  |  | 133.053 |
| MongoChainIdBenchmark.generate_t1 | thrpt | 1 | step=100 | 1.699 | 0.726 | ops/us |  |  | 22.819 |
| MongoChainIdBenchmark.generate_t16 | sample | 16 | step=10 | 3,112.572 | 1,510.254 | us/op | 1,010.688 | 1,243.136 | 350.886 |
| MongoChainIdBenchmark.generate_t16 | sample | 16 | step=100 | 107.172 | 55.224 | us/op | 0.300 | 0.431 | 52.171 |
| MongoChainIdBenchmark.generate_t16 | thrpt | 16 | step=10 | 57.912 | 26.531 | ops/us |  |  | 307.265 |
| MongoChainIdBenchmark.generate_t16 | thrpt | 16 | step=100 | 2.115 | 5.462 | ops/us |  |  | 71.411 |
| MongoChainIdBenchmark.generate_t4 | sample | 4 | step=10 | 90.793 | 101.701 | us/op | 0.281 | 1,167.706 | 520.750 |
| MongoChainIdBenchmark.generate_t4 | sample | 4 | step=100 | 12.735 | 16.046 | us/op | 0.280 | 6.693 | 54.917 |
| MongoChainIdBenchmark.generate_t4 | thrpt | 4 | step=10 | 2.538 | 3.824 | ops/us |  |  | 713.592 |
| MongoChainIdBenchmark.generate_t4 | thrpt | 4 | step=100 | 1.223 | 0.835 | ops/us |  |  | 53.778 |
| MongoStoreBenchmark.acquire_t1 | avgt | 1 |  | 2,627.522 | 100.889 | us/op |  |  | 147,304.080 |
| MongoStoreBenchmark.acquire_t1 | sample | 1 |  | 2,602.478 | 20.366 | us/op | 4,039.885 | 14,099.415 | 146,331.863 |
| MongoStoreBenchmark.renew_t1 | avgt | 1 |  | 1,020.587 | 12.320 | us/op |  |  | 49,106.130 |
| MongoStoreBenchmark.renew_t1 | sample | 1 |  | 1,026.594 | 7.226 | us/op | 1,676.759 | 6,120.022 | 49,090.353 |
| MongoStoreBenchmark.reserve_t1 | avgt | 1 |  | 1,018.842 | 23.487 | us/op |  |  | 31,802.821 |
| MongoStoreBenchmark.reserve_t1 | sample | 1 |  | 1,041.201 | 11.939 | us/op | 1,357.824 | 13,952.926 | 31,759.707 |
| MySqlChainIdBenchmark.generate_t1 | sample | 1 | step=10 | 1.516 | 0.093 | us/op | 0.070 | 580.608 | 29.748 |
| MySqlChainIdBenchmark.generate_t1 | sample | 1 | step=100 | 0.291 | 0.038 | us/op | 0.051 | 1.142 | 0.970 |
| MySqlChainIdBenchmark.generate_t1 | thrpt | 1 | step=10 | 0.622 | 0.207 | ops/us |  |  | 28.972 |
| MySqlChainIdBenchmark.generate_t1 | thrpt | 1 | step=100 | 9.645 | 2.367 | ops/us |  |  | 1.631 |
| MySqlChainIdBenchmark.generate_t16 | sample | 16 | step=10 | 96.530 | 30.647 | us/op | 0.290 | 635.904 | 43.874 |
| MySqlChainIdBenchmark.generate_t16 | sample | 16 | step=100 | 3.423 | 0.550 | us/op | 0.301 | 0.701 | 0.562 |
| MySqlChainIdBenchmark.generate_t16 | thrpt | 16 | step=10 | 58.782 | 27.844 | ops/us |  |  | 69.432 |
| MySqlChainIdBenchmark.generate_t16 | thrpt | 16 | step=100 | 6.752 | 2.570 | ops/us |  |  | 2.282 |
| MySqlChainIdBenchmark.generate_t4 | sample | 4 | step=10 | 29.885 | 16.323 | us/op | 0.301 | 636.928 | 72.747 |
| MySqlChainIdBenchmark.generate_t4 | sample | 4 | step=100 | 1.679 | 1.150 | us/op | 0.301 | 10.336 | 2.701 |
| MySqlChainIdBenchmark.generate_t4 | thrpt | 4 | step=10 | 1.338 | 3.585 | ops/us |  |  | 107.380 |
| MySqlChainIdBenchmark.generate_t4 | thrpt | 4 | step=100 | 3.272 | 1.058 | ops/us |  |  | 5.010 |
| MySqlStoreBenchmark.acquire_t1 | avgt | 1 |  | 1,325.686 | 9.982 | us/op |  |  | 50,554.893 |
| MySqlStoreBenchmark.acquire_t1 | sample | 1 |  | 1,349.659 | 2.928 | us/op | 1,642.496 | 2,734.604 | 50,726.831 |
| MySqlStoreBenchmark.renew_t1 | avgt | 1 |  | 757.524 | 7.361 | us/op |  |  | 16,409.000 |
| MySqlStoreBenchmark.renew_t1 | sample | 1 |  | 757.381 | 1.290 | us/op | 960.512 | 1,311.336 | 16,417.088 |
| MySqlStoreBenchmark.reserve_t1 | avgt | 1 |  | 561.893 | 5.425 | us/op |  |  | 8,643.527 |
| MySqlStoreBenchmark.reserve_t1 | sample | 1 |  | 565.766 | 0.946 | us/op | 743.424 | 1,060.864 | 8,666.771 |
| PeriodicSegmentBenchmark.generate_t1 | sample | 1 |  | 0.063 | 0.004 | us/op | 0.061 | 1.552 | 48.005 |
| PeriodicSegmentBenchmark.generate_t1 | thrpt | 1 |  | 30.808 | 0.537 | ops/us |  |  | 48.002 |
| PeriodicSegmentBenchmark.generate_t4 | sample | 4 |  | 0.208 | 0.012 | us/op | 0.321 | 7.480 | 40.018 |
| PeriodicSegmentBenchmark.generate_t4 | thrpt | 4 |  | 30.663 | 0.912 | ops/us |  |  | 48.002 |
| RedisChainIdBenchmark.generate_t1 | sample | 1 | step=10 | 0.811 | 0.040 | us/op | 0.070 | 200.704 | 15.692 |
| RedisChainIdBenchmark.generate_t1 | sample | 1 | step=100 | 0.052 | 0.004 | us/op | 0.050 | 0.091 | 0.448 |
| RedisChainIdBenchmark.generate_t1 | thrpt | 1 | step=10 | 2.151 | 0.508 | ops/us |  |  | 15.432 |
| RedisChainIdBenchmark.generate_t1 | thrpt | 1 | step=100 | 26.354 | 4.991 | ops/us |  |  | 0.969 |
| RedisChainIdBenchmark.generate_t16 | sample | 16 | step=10 | 3.708 | 0.210 | us/op | 0.390 | 12.752 | 0.654 |
| RedisChainIdBenchmark.generate_t16 | sample | 16 | step=100 | 2.187 | 0.143 | us/op | 0.381 | 1.002 | 0.178 |
| RedisChainIdBenchmark.generate_t16 | thrpt | 16 | step=10 | 17.674 | 0.896 | ops/us |  |  | 0.529 |
| RedisChainIdBenchmark.generate_t16 | thrpt | 16 | step=100 | 22.856 | 0.940 | ops/us |  |  | 0.095 |
| RedisChainIdBenchmark.generate_t4 | sample | 4 | step=10 | 3.722 | 0.802 | us/op | 0.371 | 211.200 | 6.088 |
| RedisChainIdBenchmark.generate_t4 | sample | 4 | step=100 | 0.775 | 0.230 | us/op | 0.391 | 12.544 | 0.842 |
| RedisChainIdBenchmark.generate_t4 | thrpt | 4 | step=10 | 5.505 | 1.425 | ops/us |  |  | 4.698 |
| RedisChainIdBenchmark.generate_t4 | thrpt | 4 | step=100 | 13.081 | 1.830 | ops/us |  |  | 1.102 |
| RedisStoreBenchmark.acquireRelease_t1 | avgt | 1 |  | 841.087 | 6.419 | us/op |  |  | 24,781.567 |
| RedisStoreBenchmark.acquireRelease_t1 | sample | 1 |  | 847.156 | 1.607 | us/op | 1,085.440 | 2,038.702 | 24,862.259 |
| RedisStoreBenchmark.renew_t1 | avgt | 1 |  | 399.672 | 3.230 | us/op |  |  | 10,014.657 |
| RedisStoreBenchmark.renew_t1 | sample | 1 |  | 401.887 | 0.690 | us/op | 515.584 | 857.739 | 10,053.023 |
| RedisStoreBenchmark.reserve_t1 | avgt | 1 |  | 173.774 | 2.021 | us/op |  |  | 5,335.644 |
| RedisStoreBenchmark.reserve_t1 | sample | 1 |  | 173.311 | 0.211 | us/op | 226.048 | 465.408 | 5,350.790 |
| SegmentBenchmark.generate_t1 | sample | 1 | step=10 | 0.032 | 0.001 | us/op | 0.031 | 0.091 | 0.026 |
| SegmentBenchmark.generate_t1 | sample | 1 | step=100 | 0.031 | 0.001 | us/op | 0.031 | 0.050 | 0.002 |
| SegmentBenchmark.generate_t1 | thrpt | 1 | step=10 | 155.346 | 9.383 | ops/us |  |  | 0.027 |
| SegmentBenchmark.generate_t1 | thrpt | 1 | step=100 | 157.360 | 6.642 | ops/us |  |  | 0.002 |
| SegmentBenchmark.generate_t16 | sample | 16 | step=10 | 1.431 | 0.087 | us/op | 0.331 | 0.801 | 0.088 |
| SegmentBenchmark.generate_t16 | sample | 16 | step=100 | 1.067 | 0.076 | us/op | 0.321 | 0.450 | 0.051 |
| SegmentBenchmark.generate_t16 | thrpt | 16 | step=10 | 40.645 | 1.314 | ops/us |  |  | 0.034 |
| SegmentBenchmark.generate_t16 | thrpt | 16 | step=100 | 38.937 | 1.036 | ops/us |  |  | 0.001 |
| SegmentBenchmark.generate_t4 | sample | 4 | step=10 | 0.160 | 0.009 | us/op | 0.321 | 0.781 | 0.027 |
| SegmentBenchmark.generate_t4 | sample | 4 | step=100 | 0.158 | 0.007 | us/op | 0.331 | 0.471 | 0.011 |
| SegmentBenchmark.generate_t4 | thrpt | 4 | step=10 | 38.688 | 0.754 | ops/us |  |  | 0.027 |
| SegmentBenchmark.generate_t4 | thrpt | 4 | step=100 | 37.582 | 1.028 | ops/us |  |  | 0.002 |
| SegmentBenchmark.generate_t64 | sample | 64 | step=10 | 5.316 | 0.200 | us/op | 0.311 | 1.342 | 0.277 |
| SegmentBenchmark.generate_t64 | sample | 64 | step=100 | 4.498 | 0.194 | us/op | 0.310 | 0.420 | 0.121 |
| SegmentBenchmark.generate_t64 | thrpt | 64 | step=10 | 38.991 | 1.330 | ops/us |  |  | 0.330 |
| SegmentBenchmark.generate_t64 | thrpt | 64 | step=100 | 38.444 | 1.860 | ops/us |  |  | 0.003 |
| SegmentNoPrefetchBenchmark.generate_t1 | thrpt | 1 |  | 13,395.202 | 150.411 | ops/ms |  |  | 24.000 |
| SnowflakeMillisBenchmark.generate_t1 | thrpt | 1 |  | 4,096.493 | 0.284 | ops/ms |  |  | 0.001 |
| SnowflakeMillisBenchmark.generate_t16 | thrpt | 16 |  | 4,094.425 | 3.565 | ops/ms |  |  | 0.003 |
| SnowflakeMillisBenchmark.generate_t4 | thrpt | 4 |  | 4,096.067 | 0.563 | ops/ms |  |  | 0.001 |
| SnowflakeSecondsBenchmark.generate_t1 | thrpt | 1 |  | 4,229.043 | 15.477 | ops/ms |  |  | 0.001 |
| SnowflakeSecondsBenchmark.generate_t16 | thrpt | 16 |  | 4,255.770 | 41.709 | ops/ms |  |  | 0.002 |
| SnowflakeStringBenchmark.friendly_t1 | thrpt | 1 |  | 4,067.634 | 26.558 | ops/ms |  |  | 376.001 |
| SnowflakeStringBenchmark.radix62_t1 | thrpt | 1 |  | 4,096.362 | 0.516 | ops/ms |  |  | 96.001 |
| SpringStartBenchmark.startToFirstId_t1 | avgt | 1 |  | 16.202 | 3.634 | ms/op |  |  | 7,118,805.694 |
| ZookeeperChainIdBenchmark.generate_t1 | sample | 1 | step=10 | 5.499 | 0.205 | us/op | 0.100 | 718.848 | 37.333 |
| ZookeeperChainIdBenchmark.generate_t1 | sample | 1 | step=100 | 0.260 | 0.042 | us/op | 0.050 | 0.150 | 1.615 |
| ZookeeperChainIdBenchmark.generate_t1 | thrpt | 1 | step=10 | 0.332 | 0.178 | ops/us |  |  | 47.339 |
| ZookeeperChainIdBenchmark.generate_t1 | thrpt | 1 | step=100 | 3.592 | 1.306 | ops/us |  |  | 3.004 |
| ZookeeperChainIdBenchmark.generate_t16 | sample | 16 | step=10 | 45.213 | 18.978 | us/op | 0.370 | 704.512 | 8.599 |
| ZookeeperChainIdBenchmark.generate_t16 | sample | 16 | step=100 | 10.114 | 3.175 | us/op | 0.370 | 0.641 | 2.321 |
| ZookeeperChainIdBenchmark.generate_t16 | thrpt | 16 | step=10 | 40.915 | 26.217 | ops/us |  |  | 9.997 |
| ZookeeperChainIdBenchmark.generate_t16 | thrpt | 16 | step=100 | 2.257 | 1.980 | ops/us |  |  | 4.478 |
| ZookeeperChainIdBenchmark.generate_t4 | sample | 4 | step=10 | 16.273 | 8.925 | us/op | 0.380 | 679.936 | 26.957 |
| ZookeeperChainIdBenchmark.generate_t4 | sample | 4 | step=100 | 3.592 | 3.312 | us/op | 0.370 | 23.909 | 3.608 |
| ZookeeperChainIdBenchmark.generate_t4 | thrpt | 4 | step=10 | 7.105 | 6.040 | ops/us |  |  | 16.662 |
| ZookeeperChainIdBenchmark.generate_t4 | thrpt | 4 | step=100 | 2.516 | 1.495 | ops/us |  |  | 5.887 |
| ZookeeperStoreBenchmark.acquire1024_t1 | avgt | 1 |  | 4,307.262 | 161.799 | us/op |  |  | 53,279.447 |
| ZookeeperStoreBenchmark.acquire1024_t1 | sample | 1 |  | 4,242.592 | 22.853 | us/op | 7,149.158 | 9,724.887 | 53,582.079 |
| ZookeeperStoreBenchmark.acquire_t1 | avgt | 1 |  | 4,324.888 | 130.564 | us/op |  |  | 54,073.934 |
| ZookeeperStoreBenchmark.acquire_t1 | sample | 1 |  | 4,292.661 | 26.236 | us/op | 7,747.994 | 10,514.596 | 54,135.084 |
| ZookeeperStoreBenchmark.renew_t1 | avgt | 1 |  | 707.681 | 11.717 | us/op |  |  | 9,020.657 |
| ZookeeperStoreBenchmark.renew_t1 | sample | 1 |  | 709.113 | 3.334 | us/op | 1,419.571 | 3,837.952 | 9,076.622 |
| ZookeeperStoreBenchmark.reserve_t1 | avgt | 1 |  | 661.977 | 17.328 | us/op |  |  | 5,821.520 |
| ZookeeperStoreBenchmark.reserve_t1 | sample | 1 |  | 658.357 | 3.177 | us/op | 1,322.619 | 3,930.210 | 5,882.231 |
