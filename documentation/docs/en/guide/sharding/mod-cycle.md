# Modulo Sharding Algorithm

<p align="center" >
  <img src="../../../public/assets/design/CosIdModShardingAlgorithm.png" alt="CosIdModShardingAlgorithm"/>
</p>

- Algorithm complexity: O(1)
- Performance: Compared to `org.apache.shardingsphere.sharding.algorithm.sharding.mod.ModShardingAlgorithm`, performance is *1200~4000* times higher. And stability is higher, no serious performance degradation occurs.

| **PreciseShardingValue**                                                                                      | **RangeShardingValue**                                                                                      |
|---------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| <img src="../../../public/assets/perf/sharding/Throughput-Of-ModShardingAlgorithm-PreciseShardingValue.png"/> | <img src="../../../public/assets/perf/sharding/Throughput-Of-ModShardingAlgorithm-RangeShardingValue.png"/> |

## Range Sharding Semantics

- Range (`Range`) queries resolve nodes by span: a span covering at least the divisor returns all nodes. In particular, "unbounded" queries expressed as `Range.closed(0, Long.MAX_VALUE)` route to all nodes correctly (since v3.2.1, large spans are no longer affected by int overflow).
- Sharding values must be non-negative integers (snowflake and segment IDs are non-negative); negative sharding values are not supported.

