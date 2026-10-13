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
