# 故障排查

## 启动错误

| 错误信息包含 | 原因与解决方法 |
|--------------|----------------|
| `Table cosid_machine has no lease columns` | 3.x 创建的表：添加租约列，见 [JDBC](../backends/jdbc.md#升级-3-x-创建的表) |
| `Table cosid_machine has no stable column` | 3.3 之前创建的表：添加 `stable` 列，见 [JDBC](../backends/jdbc.md#升级-3-x-创建的表) |
| `[cosid.machine.distributor.provider] was removed in 4.0`（或 `cosid.segment.distributor.provider`） | 改为设置错误信息中给出的 `type`；它也会按 `getType()` 选择你的 `CosIdStoreProvider` |
| `must be positive: the machine id lease store holds a lock during each call` | 把指出的超时（`cosid.machine.distributor.redis.timeout`、`redis.distribute-timeout`、`zookeeper.timeout`、`mongo.timeout`、`cosid.proxy.read-timeout`）设置为正数 |
| `CosId's MongoDB reactive support was removed in 4.0` | 添加 `org.mongodb:mongodb-driver-sync`，见 [MongoDB](../backends/mongo.md) |
| `has converter.group-prefix enabled but no group` | 为该号段生成器设置 `group.by`，或关闭 `converter.group-prefix.enabled` |
| `MachineIdOverflowException` | 命名空间中所有机器号都被存活或稳定实例持有，或租约尚未过期。等待被终止或启动失败实例的安全守护时长过去（启动失败目前不会释放租约，反复失败可能耗尽命名空间）、增大 `cosid.machine.machine-bit`，或检查是否有实例误用了该命名空间 |
| `ClockTooManyBackwardsException` | 主机时钟落后于机器号水位线超过 `broken-threshold`（500 毫秒）：上一个持有者的时钟偏快。修复时钟同步；该机器号会被再次释放 |
| `cosid.machine.distributor.manual.machineId can not be null` | `manual` 需要设置 `cosid.machine.distributor.manual.machine-id`；或者改用租约存储 |
| 找不到 `InstanceId` 或 `GuardDistribute` 类型的 Bean | 启用了 Snowflake ID 或 `CosIdGenerator`，但没有设置 `cosid.machine.enabled=true` |
| `No [cosid.proxy.server.auth.tokens] configured`（Proxy 服务端） | 设置 `cosid.proxy.server.auth.tokens`（发行包读取 `COSID_PROXY_TOKEN`），或在可信网络内设置 `allow-anonymous=true` |
| `The guarder may renew the machine id lease too late`（或 `[cosid.machine.guarder.delay] must be positive`） | 守护会在租约过期之后才续约：`max(delay, initial-delay)` 加上两倍存储超时（一次续约要调用存储两次），必须短于 `safe-guard-duration` 减去余量 `min(10s, safe-guard-duration / 10)`。调小 `delay` 和 `initial-delay`（取安全守护时长的三分之一，可以容忍漏掉的续约），或调大 `safe-guard-duration`。`fail-fast: false` 时只打印告警 |

## 运行期间的错误

| 异常 | 含义 | 处理方法 |
|------|------|----------|
| `MachineIdLostException` | 机器号租约已丢失（已被其他实例持有）、已在关闭时释放，或约一个安全守护时长未续约；生成器停止生成，以免产生重复 ID | 查看 `machineId` 健康指示器和日志中的 `Guard Failed` 错误：通常是存储几分钟内不可达。过期的租约在下一次续约成功后会自动恢复；已丢失的租约不会恢复，需要重启实例重新申领机器号 |
| `ClockBackwardsException` | 设置了 `clock-sync: false` 的生成器运行期间时钟回拨；它拒绝生成，而不是重复 ID | 修复时钟同步，或保持 `clock-sync: true` |
| `ClockTooManyBackwardsException` | 运行期间时钟回拨超过 `broken-threshold` | 修复时钟同步（使用渐进调整而不是跳变） |
| `IdSegmentRollbackException` | 存储返回的号段低于已经发放过的号段：计数器回退了，例如 Redis 丢失了数据 | 停止生成，把计数器恢复到已发放的最大 ID 之上后再继续 |
| `NotFoundIdGeneratorException` | 该名称下没有注册生成器 | 检查 `@CosId` 或查找时使用的名称，以及生成器是否配置在 `cosid.segment.provider` 或 `cosid.snowflake.provider` 下 |
| 查找时抛出提及两个类型的 `IllegalArgumentException` | 类型化查找要求 `long` 生成器，但该名称下是只生成字符串的生成器（例如 `cosid`） | 改用 `getRequired(name)` 和 `generateAsString()`，或换成 `long` 生成器 |
| 提及字段和生成器的 `CosIdException` | `long` 或 `int` 字段绑定了只生成字符串的生成器 | 把字段绑定到号段或 Snowflake 生成器 |

## 怀疑出现重复 ID

重复来自协调问题，而不是生成器本身。按顺序检查：

1. **两个实例使用同一个机器号**：`manual` 或 `stateful_set` 的值被重复分配，或者同一命名空间的实例使用了不同的 `safe-guard-duration`。
2. **时钟**：使用 ZooKeeper 租约时各主机时钟不同步，或者实例在一次大幅时钟回拨后被强制终止并立即重启。
3. **布局**：生成器的 `epoch`、`machine-bit` 或其他位数被修改，而表中仍有旧布局的 ID。
4. **号段计数器**：存储丢失了写入（未开启持久化的 Redis），或者 `offset` 设置得低于已发放的 ID。
5. **命名空间与名称**：写同一张表的两个应用必须共用同一个计数器，即相同的命名空间和生成器名称；写不同表的应用应使用不同的命名空间或生成器名称。

`GET /actuator/cosid` 会显示每个生成器的机器号、最后时间戳和号段状态，能区分以上大多数情况。
