# 基于间隔的时间范围分片算法

<p align="center" >
  <img src="../../../public/assets/design/CosIdIntervalShardingAlgorithm.png" alt="CosIdIntervalShardingAlgorithm"/>
</p>

- 算法复杂度：O(1)
- 易用性: 支持多种数据类型 (`Long`/`LocalDateTime`/`DATE`/ `String` / `SnowflakeId`)，而官方实现是先转换成字符串再转换成`LocalDateTime`，转换成功率受时间格式化字符影响。
- 性能 : 相比于 `org.apache.shardingsphere.sharding.algorithm.sharding.datetime.IntervalShardingAlgorithm` 性能高出 *1200~4000* 倍。

| **PreciseShardingValue**                                                                                        | **RangeShardingValue**                                                                                        |
|-----------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| <img src="../../public/assets/perf/sharding/Throughput-Of-IntervalShardingAlgorithm-PreciseShardingValue.png"/> | <img src="../../public/assets/perf/sharding/Throughput-Of-IntervalShardingAlgorithm-RangeShardingValue.png"/> |

