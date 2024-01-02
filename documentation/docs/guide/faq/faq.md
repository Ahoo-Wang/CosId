# 常见问题

## CosId 需要部署服务端吗？

虽然并没有规定 [CosId](https://github.com/Ahoo-Wang/CosId) 的使用方式，但是强烈推荐以本地 SDK 的方式使用，用户只需要安装一下 **CosId** 的依赖包做一些简单配置（ [DEMO](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-example) ） 即可。

:::tip
分布式ID是不适合使用服务端部署模式的(C/S)。使用服务端部署模式，必然会产生网络IO（*Client*通过远程过程调用*Server*，获取ID），你想想我们费了那么大劲消除网络IO是为了什么？
:::

## PrefetchWorker 是如何维护安全距离的？

- 定时维护：每隔一段时间**PrefetchWorker**会主动检测安全距离是否满足配置要求，如果不满足则执行`NextMaxId`预取，保证安全距离。
- 被动饥饿唤醒：当获取ID的线程获取ID时没有可用号段，会尝试获取新的号段，并主动唤醒**PrefetchWorker**并告诉他你太慢了，被唤醒的**PrefetchWorker**会检测安全距离是否需要膨胀，然后进行安全距离的维护。

## 本机单调、全局趋势递增-为什么还要尽可能保证单调递增？

从上文的论述中我们不难理解本机单调递增，全局趋势递增是权衡后的设计结果。
但是全局趋势递增的背面是周期内ID乱序，所以尽可能向单调递增优化（降低ID乱序程度）是优化目标，这俩点并不冲突。

如果各位同学还有其他问题请至 [Issues](https://github.com/Ahoo-Wang/CosId/issues) 提交你的疑问。

