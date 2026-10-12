# Distributed ID Performance Evaluation: CosId VS Meituan Leaf

## Environment

- MacBook Pro (M1)
- JDK 17
- JMH 1.36
- Running in local Docker mariadb:10.6.4

## Running

> Benchmark code: [cosid-benchmark](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-benchmark)

``` shell
git clone git@github.com:Ahoo-Wang/CosId.git
cd cosid-benchmark
```

::: code-group
```shell [Gradle]
./gradlew jmh
```
```shell [Java]
gradle jmhJar
java -jar build/libs/cosid-benchmark-2.2.6-jmh.jar -wi 1 -rf json -f 1
```
:::


## Report

```
# JMH version: 1.36
# VM version: JDK 17.0.7, OpenJDK 64-Bit Server VM, 17.0.7+7-LTS
# Warmup: 1 iterations, 10 s each
# Measurement: 1 iterations, 10 s each
# Timeout: 10 min per iteration
# Threads: 1 thread, will synchronize iterations

Benchmark                     (step)   Mode  Cnt          Score   Error  Units
AtomicLongBenchmark.generate     N/A  thrpt       142725210.565          ops/s
CosIdBenchmark.generate            1  thrpt       131920684.604          ops/s
CosIdBenchmark.generate          100  thrpt       132113994.232          ops/s
CosIdBenchmark.generate         1000  thrpt       130281016.155          ops/s
LeafBenchmark.generate             1  thrpt        25787669.815          ops/s
LeafBenchmark.generate           100  thrpt        23897328.183          ops/s
LeafBenchmark.generate          1000  thrpt        23550106.538          ops/s
```

<p align="center" >
  <img  src="../../../public/assets/perf/CosId-VS-Leaf.png" alt="CosId VS Meituan Leaf"/>
</p>

> GitHub Action environment test report: [Performance: CosId vs Leaf](https://github.com/Ahoo-Wang/CosId/issues/22)
>
> Due to GitHub Runner resource limitations, the benchmark tests running in GitHub Runner have a significant gap compared to real environment benchmarks (nearly 2 times),
> but for benchmarks running in the same environment configuration resources (all running in GitHub Runner), pre-commit benchmark comparisons and third-party library comparisons are still valuable.

## Conclusion

1. CosId (`SegmentChainId`) performance is 5 times that of Leaf (`segment`).
2. CosId and Leaf performance is independent of segment step size (Step).
3. CosId TPS is basically close to `AtomicLong`.