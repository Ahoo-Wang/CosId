# SegmentId 配置

> `me.ahoo.cosid.spring.boot.starter.segment.SegmentIdProperties`

| 名称          | 数据类型                        | 说明                         | 默认值                    |
|-------------|-----------------------------|----------------------------|------------------------|
| enabled     | `boolean`                   | 是否启用                       | `false`                |
| mode        | `Mode`                      | 号段生成器模式：`DEFAULT`/ `CHAIN` | `CHAIN`                |
| ttl         | `long`                      | 号段的生存期（秒）                  | `TIME_TO_LIVE_FOREVER` |
| distributor | `Distributor`               | 号段分发器                      |                        |
| chain       | `Chain`                     | 号段链模式配置                    |                        |
| share       | `IdDefinition`              | 共享ID生成器配置                  |                        |
| provider    | `Map<String, IdDefinition>` | 多ID生成器配置                   |                        |

## Distributor

> `me.ahoo.cosid.spring.boot.starter.segment.SegmentIdProperties.Distributor`

| 名称   | 数据类型               | 说明                                  | 默认值          |
|------|--------------------|-------------------------------------|--------------|
| type | `Distributor.Type` | 号段分发器类型： `REDIS`/`JDBC`/`ZOOKEEPER` | `Type.REDIS` |
| jdbc | `Distributor.Jdbc` | Jdbc号段生成器配置                         |              |

### Distributor.Jdbc

| 名称                           | 数据类型      | 说明             | 默认值     |
|------------------------------|-----------|----------------|---------|
| enable-auto-init-cosid-table | `boolean` | 自动创建号段`cosid`表 | `false` |
| enable-auto-init-id-segment  | `boolean` | 自动创建号段行        | `true`  |

## Chain

> `me.ahoo.cosid.spring.boot.starter.segment.SegmentIdProperties.Chain`

| 名称              | 数据类型                   | 说明         | 默认值    |
|-----------------|------------------------|------------|--------|
| safe-distance   | `int`                  | 安全距离       | `10`   |
| prefetch-worker | `Chain.PrefetchWorker` | 号段预取工作者线程池 | `true` |

### Chain.PrefetchWorker

| 名称              | 数据类型       | 说明    | 默认值                                          |
|-----------------|------------|-------|----------------------------------------------|
| prefetch-period | `Duration` | 预取周期  | `Duration.ofSeconds(1)`                      |
| core-pool-size  | `int`      | 线程池大小 | `Runtime.getRuntime().availableProcessors()` |

## IdDefinition

> `me.ahoo.cosid.spring.boot.starter.segment.SegmentIdProperties.IdDefinition`

| 名称        | 数据类型                    | 说明                         | 默认值                   |
|-----------|-------------------------|----------------------------|-----------------------|
| mode      | `Mode`                  | 号段生成器模式：`DEFAULT`/ `CHAIN` | `cosid.segment.mode`  |
| offset    | `int`                   | 号段初始偏移量                    | `0`                   |
| step      | `long`                  | 步长                         | 100                   |
| ttl       | `long`                  | 号段的生存期（秒）                  | `cosid.segment.ttl`   |
| chain     | `Chain`                 | 号段链模式配置                    | `cosid.segment.chain` |
| converter | `IdConverterDefinition` | Id转换器配置                    |                       |

**YAML 配置样例**

```yaml
cosid:
  namespace: ${spring.application.name}
  segment:
    enabled: true
    mode: chain
    chain:
      safe-distance: 5
      prefetch-worker:
        core-pool-size: 2
        prefetch-period: 1s
    distributor:
      type: redis
    share:
      offset: 0
      step: 100
      converter:
        prefix: cosid_
        type: radix
        radix:
          char-size: 6
          pad-start: false
    provider:
      order:
        offset: 10000
        step: 100
```
