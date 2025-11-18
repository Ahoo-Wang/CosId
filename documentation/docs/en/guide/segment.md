# SegmentId

<p align="center" >
  <img src="../../public/assets/design/SegmentId.png" alt="SegmentId"/>
</p>

从上面的设计图中，不难看出**号段模式**基本设计思路是通过每次获取一定长度（Step）的可用ID（Id段/号段），来降低网络IO请求次数，提升性能。

- :thumbsdown:强依赖第三方号段分发器，可用性受到第三方分发器影响。
- :thumbsdown:每次号段用完时获取`NextMaxId`需要进行网络IO请求，此时的性能会比较低。
- 单实例ID单调递增，全局趋势递增。
  - 从设计图中不难看出**Instance 1**每次获取的`NextMaxId`，一定比上一次大，意味着下一次的号段一定比上一次大，所以从单实例上来看是单调递增的。
  - 多实例各自持有的不同的号段，意味着同一时刻不同实例生成的ID是乱序的，但是整体趋势的递增的，所以全局趋势递增。
- ID乱序程度受到Step长度以及集群规模影响（从趋势递增图中不难看出）。
  - 假设集群中只有一个实例时**号段模式**就是单调递增的。
  - `Step`越小，乱序程度越小。当`Step=1`时，将无限接近单调递增。需要注意的是这里是无限接近而非等于单调递增，具体原因你可以思考一下这样一个场景：
    - 号段分发器T<sub>1</sub>时刻给**Instance 1**分发了`ID=1`,T<sub>2</sub>时刻给**Instance 2**分发了`ID=2`。因为机器性能、网络等原因，`Instance 2`网络IO写请求先于`Instance 1`到达。那么这个时候对于数据库来说，ID依然是乱序的。

## 具体实现

```mermaid
classDiagram
direction BT
class DefaultSegmentId
class IdGenerator {
<<Interface>>

}
class SegmentChainId
class SegmentId {
<<Interface>>

}
class StringSegmentId

DefaultSegmentId  ..>  SegmentId 
SegmentChainId  ..>  SegmentId 
SegmentId  -->  IdGenerator 
StringSegmentId  ..>  IdGenerator 
StringSegmentId  ..>  SegmentId 
```

## IdSegmentDistributor

## GroupedIdSegmentDistributor


## 配置
