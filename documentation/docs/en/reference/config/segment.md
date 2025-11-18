# SegmentId Configuration

> `me.ahoo.cosid.spring.boot.starter.segment.SegmentIdProperties`

| Name          | Data Type                        | Description                         | Default Value                    |
|-------------|-----------------------------|----------------------------|------------------------|
| enabled     | `boolean`                   | Whether to enable                       | `false`                |
| mode        | `Mode`                      | Segment ID generator mode: `DEFAULT`/ `CHAIN` | `CHAIN`                |
| ttl         | `long`                      | Lifetime of the segment (seconds)                  | `TIME_TO_LIVE_FOREVER` |
| distributor | `Distributor`               | Segment distributor                      |                        |
| chain       | `Chain`                     | Segment chain mode configuration                    |                        |
| share       | `IdDefinition`              | Shared ID generator configuration                  |                        |
| provider    | `Map<String, IdDefinition>` | Multi-ID generator configuration                   |                        |

## Distributor

> `me.ahoo.cosid.spring.boot.starter.segment.SegmentIdProperties.Distributor`

| Name   | Data Type               | Description                                  | Default Value          |
|------|--------------------|-------------------------------------|--------------|
| type | `Distributor.Type` | Segment distributor type: `REDIS`/`JDBC`/`ZOOKEEPER` | `Type.REDIS` |
| jdbc | `Distributor.Jdbc` | JDBC segment generator configuration                         |              |

### Distributor.Jdbc

| Name                           | Data Type      | Description             | Default Value     |
|------------------------------|-----------|----------------|---------|
| enable-auto-init-cosid-table | `boolean` | Automatically create cosid segment table | `false` |
| enable-auto-init-id-segment  | `boolean` | Automatically create segment rows        | `true`  |

## Chain

> `me.ahoo.cosid.spring.boot.starter.segment.SegmentIdProperties.Chain`

| Name              | Data Type                   | Description         | Default Value    |
|-----------------|------------------------|------------|--------|
| safe-distance   | `int`                  | Safe distance       | `10`   |
| prefetch-worker | `Chain.PrefetchWorker` | Segment prefetch worker thread pool | `true` |

### Chain.PrefetchWorker

| Name              | Data Type       | Description    | Default Value                                          |
|-----------------|------------|-------|----------------------------------------------|
| prefetch-period | `Duration` | Prefetch period  | `Duration.ofSeconds(1)`                      |
| core-pool-size  | `int`      | Thread pool size | `Runtime.getRuntime().availableProcessors()` |

## IdDefinition

> `me.ahoo.cosid.spring.boot.starter.segment.SegmentIdProperties.IdDefinition`

| Name        | Data Type                    | Description                         | Default Value                   |
|-----------|-------------------------|----------------------------|-----------------------|
| mode      | `Mode`                  | Segment ID generator mode: `DEFAULT`/ `CHAIN` | `cosid.segment.mode`  |
| offset    | `int`                   | Segment initial offset                    | `0`                   |
| step      | `long`                  | Step                         | 100                   |
| ttl       | `long`                  | Lifetime of the segment (seconds)                  | `cosid.segment.ttl`   |
| chain     | `Chain`                 | Segment chain mode configuration                    | `cosid.segment.chain` |
| converter | `IdConverterDefinition` | ID converter configuration                    |                       |

**YAML Configuration Example**

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
