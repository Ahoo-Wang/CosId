# Sharding

> 分片
 
<p align="center">
  <img :src="$withBase('/assets/design/Sharding-impl-class.png')" alt="Sharding implementation class diagram"/>
</p>

## CachedSharding
> 已缓存的分区，对Sharding子类具体实现的包装，内部通过具体的`Sharding`进行分区，目的为了通过缓存减少内存对象，提升整体性能。

## IntervalTimeline
> 按照时间间隔的分区，时间单位根据`ChronoUnit`, 比如：`20220510`按照`ChronoUnit.MONTHS`进行分区，结果分区至`202205`

## ModCycle<T>
> 可以提前预知节点数量的场，按照节点取模算法进行分区，比如在4个分区节点：`0，1，2，3`，分区值为`3`，`4`的值会被分区至节点`3`,`0`


