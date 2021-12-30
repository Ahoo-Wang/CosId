# 号段链模式

## 为什么需要*SegmentChainId*

## RedisIdSegmentDistributor、JdbcIdSegmentDistributor 均能够达到TPS=1.2亿/s？

### RedisChainIdBenchmark-Throughput

<p align="center" >
  <img :src="$withBase('/assets/perf/RedisChainIdBenchmark-Throughput.png')" alt="RedisChainIdBenchmark-Throughput"/>
</p>

### MySqlChainIdBenchmark-Throughput

<p align="center" >
  <img :src="$withBase('/assets/perf/MySqlChainIdBenchmark-Throughput.png')" alt="MySqlChainIdBenchmark-Throughput"/>
</p>

上面的两张图给许多同学带来了困扰，为什么在`Step=1000`的时候*RedisChainIdBenchmark*、*MySqlChainIdBenchmark*TPS性能几乎一致(TPS=1.2亿/s)。
*RedisIdSegmentDistributor*应该要比*JdbcIdSegmentDistributor*性能更高才对啊，为什么都能达到*AtomicLong*性能上限呢？
如果我说当`Step=1`时，只要基准测试的时间够长，那么他们依然能够达到*AtomicLong*性能级别(TPS=1.2亿/s)，你会不会更加困惑。
其实这里的*障眼法*是**PrefetchWorker**的**饥饿膨胀**导致的，*SegmentChainId*的极限性能跟分发器的TPS性能没有直接关系，因为最终都可以因饥饿膨胀到性能上限，只要给足够的时间膨胀。
而为什么在上图的`Step=1`时TPS差异还是很明显的，这是因为*RedisIdSegmentDistributor*膨胀得更快，而基准测试又没有给足测试时间而已。

**SegmentChainId**基准测试*TPS极限性能*可以近似使用以下的公式的表示：

`TPS(SegmentChainId)极限值=(Step*Expansion)*TPS(IdSegmentDistributor)*T/s<=TPS(AtomicLong)`

1. `<=TPS(AtomicLong)`：因为*SegmentChainId*的内部号段就是使用的`AtomicLong`，所以这是性能上限。
2. `Step*Expansion`：*Expansion*可以理解为饥饿膨胀系数，默认的饥饿膨胀系数是2。在*MySqlChainIdBenchmark*、*MySqlChainIdBenchmark*基准测试中这个值是一样的。
3. `TPS(IdSegmentDistributor)`: 这是公式中唯一的不同。指的是请求号段分发器`NextMaxId`的TPS。
4. `T`: 可以理解为基准测试运行时常。

从上面的公式中不难看出*RedisChainIdBenchmark*、*MySqlChainIdBenchmark*主要差异是分发器的TPS性能。
分发器的`TPS(IdSegmentDistributor)`越大，达到`TPS(AtomicLong)`所需的`T`就越少。但只要`T`足够长，那么任何分发器都可以达到近似`TPS(AtomicLong)`。
这也就解释了为什么不同TPS性能级别的号段分发器(**IdSegmentDistributor**)都可以达到TPS=1.2亿/s。
