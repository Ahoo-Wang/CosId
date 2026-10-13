# 监控

## 健康检查

使用 Spring Boot Actuator 时，`machineId` 健康指示器在以下情况报告 `DOWN`：

- 机器号租约续约失败期间，并附带错误和失败续约的时间（`error`、`guardAt`）；下一次续约成功后恢复为 `UP`；
- 租约已过期或已丢失（被其他实例接管）。即使之后续约成功也保持 `DOWN`，因为该机器号的生成器已经停止：请重启实例。

请为它配置告警：在最后一次成功续约之后约 4 分 50 秒（`safe-guard-duration - min(10s, safe-guard-duration / 10)`），Snowflake 生成器和 `CosIdGenerator` 会抛出 `MachineIdLostException` 停止生成。

`leases` 详情按命名空间和实例 ID 列出实例持有的每个租约：

| 字段 | 含义 |
|------|------|
| `machineId` | 租约的机器号 |
| `instanceId` | 持有它的实例 ID |
| `remainingMillis` | 租约过期前的剩余时间；降到零时生成器停止 |
| `lost` | 机器号被其他实例接管后为 `true` |
| `expired` | 租约未续约而到期后为 `true` |
| `released` | 租约已归还（关闭时）后为 `true` |

```yaml
management:
  endpoint:
    health:
      show-details: when-authorized
```

## 指标

类路径上有 Micrometer 时（Spring Boot Actuator 会引入），starter 会注册 `CosIdMeterBinder`。设置 `cosid.metrics.enabled=false` 可以关闭。

| 指标 | 类型 | 标签 | 含义 |
|------|------|------|------|
| `cosid.segment.remaining` | gauge | `name`、`segment` | 号段生成器已持有号段中剩余的 ID；持续接近零时请告警 |
| `cosid.segment.prefetch.distance` | gauge | `name`、`segment` | 号段链预取的号段数；负载高时增长，最大为 `max-prefetch-distance` |
| `cosid.segment.fetch` | timer | `segment`、`outcome` | 从存储获取号段，`success` 或 `failure` |
| `cosid.segment.prefetch.expansions` | counter | `segment` | 号段链耗尽并扩大预取距离的次数 |
| `cosid.segment.prefetch.failures` | counter | `segment` | 预取失败次数 |
| `cosid.machine.lease.remaining` | time gauge | `namespace` | 机器号租约的剩余时间；降到零时 Snowflake 生成器停止 |
| `cosid.machine.clock.skew` | time gauge | `namespace` | 最近一次续约时本机时钟比存储时钟快多少 |
| `cosid.machine.lease.acquired` | counter | `namespace`、`claim` | 申领的租约：`self`、`released`、`new` 或 `expired` |
| `cosid.machine.lease.renew.failures` | counter | `namespace` | 续约失败次数 |
| `cosid.machine.lease.lost` | counter | `namespace` | 被其他实例接管的租约数 |
| `cosid.clock.backwards` | counter | `generator` | 生成器发现时钟回拨的次数 |
| `cosid.clock.sync.wait` | timer | `generator` | 等待时钟追上的时间 |
| `cosid.sequence.exhausted.wait` | timer | | 序列号用完后等待下一个时间单位的时间 |
| `cosid.call.timeouts`、`cosid.call.rejections` | counter | `operation` | 超时的存储调用，或超出 `cosid.max-concurrent-calls` 被拒绝的存储调用 |
| `cosid.generator.generated` | function counter | `name` | 生成的 ID 数，仅在 `cosid.metrics.generated.enabled=true` 时注册 |

`cosid.metrics.generated.enabled` 默认关闭：开启后号段和 Snowflake 生成器会被计数装饰器（`CountedSegmentId`、`CountedSnowflakeId`）包装，每个 ID 一次原子加法。Bean 仍是 `SegmentId` 和 `SnowflakeId` 类型，但注入具体类（例如 `SegmentChainId`）的代码会拿到包装器；可以用 `Decorator.getActual` 获取原生成器。

设置 `cosid.provider.isolated=true` 时，每个应用上下文只统计自己生成器的号段事件和自己命名空间的租约事件。时钟、序列号和存储调用指标无法区分上下文，统计的是整个 JVM。

## 生成器状态

`cosid` 端点列出每个生成器的类型、转换器和状态：Snowflake 生成器的机器号和最后时间戳，号段生成器的当前号段。

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, cosid
```

`GET /actuator/cosid` 返回所有生成器，`GET /actuator/cosid/{name}` 返回其中一个。`cosidGenerator` 和 `cosidStringGenerator` 端点会生成 ID：只暴露给可信的调用方。`cosid` 的 `DELETE` 操作会删除生成器，除非设置 `cosid.actuator.delete.enabled=true`，否则不可用。请像保护其他管理接口一样保护管理端口。

## 日志

| 日志内容 | 含义 |
|----------|------|
| `Guard Failed`（ERROR） | 一次租约续约失败；在下一次成功之前健康指示器为 `DOWN`，如果期间租约丢失或过期则一直为 `DOWN` |
| `Detected clock backwards`（WARN） | 生成器或机器号的新持有者正在等待时钟 |
| `Prefetch [...] - Hunger, Safety distance expansion`（INFO） | 号段链耗尽并扩大预取距离；频繁出现说明 `step` 或 `safe-distance` 对当前负载太小 |
| `AppendChain [...] - prefetch failed`（ERROR），之后是 `prefetch still failing`（WARN） | 号段链无法从存储获取号段；生成会继续使用预留的号段，直到用完 |
| `Revert machine id of [...] failed`（ERROR） | 关闭时没有释放机器号；它会在安全守护时长后空出 |

设置 `logging.level.me.ahoo.cosid=debug` 可以详细跟踪分配、续约和预取过程。
