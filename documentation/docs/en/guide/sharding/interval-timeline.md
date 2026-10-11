# Interval-Based Time Range Sharding Algorithm

::: tip Module
Since 4.0 the sharding algorithms are in their own artifact, `me.ahoo.cosid:cosid-sharding` (package `me.ahoo.cosid.sharding`, unchanged).
:::

<p align="center" >
  <img src="../../../public/assets/design/CosIdIntervalShardingAlgorithm.png" alt="CosIdIntervalShardingAlgorithm"/>
</p>

- Algorithm complexity: O(1)
- Usability: Supports multiple data types (`Long`/`LocalDateTime`/`DATE`/ `String` / `SnowflakeId`), while the official implementation converts to string first then to `LocalDateTime`, success rate affected by time formatting characters.
- Performance: Compared to `org.apache.shardingsphere.sharding.algorithm.sharding.datetime.IntervalShardingAlgorithm`, performance is *1200~4000* times higher.

| **PreciseShardingValue**                                                                                           | **RangeShardingValue**                                                                                           |
|--------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| <img src="../../../public/assets/perf/sharding/Throughput-Of-IntervalShardingAlgorithm-PreciseShardingValue.png"/> | <img src="../../../public/assets/perf/sharding/Throughput-Of-IntervalShardingAlgorithm-RangeShardingValue.png"/> |

