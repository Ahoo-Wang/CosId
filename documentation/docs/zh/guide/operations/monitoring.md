# 监控

## 健康检查

使用 Spring Boot Actuator 时，机器号租约续约失败期间，`machineId` 健康指示器报告 `DOWN`，并附带错误和失败续约的时间。请为它配置告警：在最后一次成功续约之后约 4 分 50 秒（`safe-guard-duration - min(10s, safe-guard-duration / 10)`），Snowflake 生成器和 `CosIdGenerator` 会抛出 `MachineIdLostException` 停止生成。

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
| `Guard Failed`（ERROR） | 一次租约续约失败；在下一次成功之前健康指示器为 `DOWN` |
| `Detected clock backwards`（WARN） | 生成器或机器号的新持有者正在等待时钟 |
| `Prefetch [...] - Hunger, Safety distance expansion`（INFO） | 号段链耗尽并扩大预取距离；频繁出现说明 `step` 或 `safe-distance` 对当前负载太小 |
| `AppendChain [...] - prefetch failed`（ERROR），之后是 `prefetch still failing`（WARN） | 号段链无法从存储获取号段；生成会继续使用预留的号段，直到用完 |
| `Revert machine id of [...] failed`（ERROR） | 关闭时没有释放机器号；它会在安全守护时长后空出 |

设置 `logging.level.me.ahoo.cosid=debug` 可以详细跟踪分配、续约和预取过程。
