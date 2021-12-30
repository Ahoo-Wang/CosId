# 性能评测

## SegmentChainId-吞吐量 (ops/s)

### RedisChainIdBenchmark-Throughput

<p align="center" >
  <img :src="$withBase('/assets/perf/RedisChainIdBenchmark-Throughput.png')" alt="RedisChainIdBenchmark-Throughput"/>
</p>

### MySqlChainIdBenchmark-Throughput

<p align="center" >
  <img :src="$withBase('/assets/perf/MySqlChainIdBenchmark-Throughput.png')" alt="MySqlChainIdBenchmark-Throughput"/>
</p>

## SegmentChainId-每次操作耗时的百分位数(us/op)

### RedisChainIdBenchmark-Percentile

<p align="center" >
  <img :src="$withBase('/assets/perf/RedisChainIdBenchmark-Sample.png')" alt="RedisChainIdBenchmark-Sample"/>
</p>

### MySqlChainIdBenchmark-Percentile

<p align="center" >
  <img :src="$withBase('/assets/perf/MySqlChainIdBenchmark-Sample.png')" alt="MySqlChainIdBenchmark-Sample"/>
</p>

## 基准测试报告运行环境说明

- 基准测试运行环境：笔记本开发机(MacBook-Pro-(M1))
- 所有基准测试都在开发笔记本上执行。

