# Modulo Sharding Algorithm

<p align="center" >
  <img src="../../../public/assets/design/CosIdModShardingAlgorithm.png" alt="CosIdModShardingAlgorithm"/>
</p>

- Algorithm complexity: O(1)
- Performance: Compared to `org.apache.shardingsphere.sharding.algorithm.sharding.mod.ModShardingAlgorithm`, performance is *1200~4000* times higher. And stability is higher, no serious performance degradation occurs.

| **PreciseShardingValue**                                                                                   | **RangeShardingValue**                                                                                   |
|------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| <img src="../../public/assets/perf/sharding/Throughput-Of-ModShardingAlgorithm-PreciseShardingValue.png"/> | <img src="../../public/assets/perf/sharding/Throughput-Of-ModShardingAlgorithm-RangeShardingValue.png"/> |

