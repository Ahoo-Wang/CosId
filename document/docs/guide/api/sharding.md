# Sharding

> 分片
 
<p align="center">
  <img :src="$withBase('/assets/design/Sharding-impl-class.png')" alt="Sharding implementation class diagram"/>
</p>

## CachedSharding

## IntervalTimeline
> 按照时间间隔的分区，时间单位根据`ChronoUnit`, 比如：`20220510`按照`ChronoUnit.MONTHS`进行分区，结果分区至`202205`

## ModCycle<T>



