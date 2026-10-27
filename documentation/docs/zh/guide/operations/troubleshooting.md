# 故障排查

## 启动失败

| 消息 | 原因与解决 |
|---|---|
| `Invalid cosid.* configuration:` 后跟一个列表 | 一次列出配置中的全部问题，每行一个：未知的存储、缺少 `strategy`、错误的 codec 列表…… |
| 时间型生成器需要 `machine` | 添加 `cosid.machine.leased`（或 `static`） |
| `nodeBits` 小于 `machine.bits` | 布局容纳不下槽位：调小 `bits`（`JS_SAFE_*` 最多为 3）或加宽 `node-bits` |
| 存储超时大于 `reserve-timeout` | 自行执行补充的调用方受存储超时约束：保持 `timeout ≤ reserve-timeout` |
| `NamespaceMismatchException` | 该命名空间首次使用时的 `{limit, tolerance}` 与当前不同：各处使用相同的 `bits` 与 `tolerance`，或换一个命名空间 |
| `acquireTimeout` 后槽位耗尽 | `2^bits` 以下的槽位全部被占用：实例数多于槽位数，或崩溃的持有者的租约尚未过期（最多 `ttl`） |
| Bean 名已被占用 | 生成器名与另一个 Bean 冲突：重命名其中一个 |
| cosid-server：未配置 token | 设置 `cosid.server.auth.tokens` 或 `clients`，或者（仅限可信网络）`allow-anonymous: true` |

## 运行期间

| 现象 | 原因与解决 |
|---|---|
| `SlotWindowExceededException` | 生成器到达时间窗口 ceiling 时仍未续约，原因是存储不可达，或主机时钟领先存储超过 `tolerance`。检查 `cosid.machine.lease.renew.failures` 与 `cosid.machine.clock.skew` |
| `SlotLostException` | 租约已丢失（在长时间停顿或网络分区期间过期，或过期后被他人取得）。keeper 会自行取得新槽位；在此之前健康检查为 `DOWN` |
| `ClockBackwardsException` | 时钟回拨超过 `regression.wait-up-to`（1s） |
| `RangeUnavailableException` | 手头没有 ID，且补充失败或耗时超过 `reserve-timeout`；见 `cosid.segment.refill.failures` |
| `RangeSealedException` | 周期号段请求了一个已封存的过去周期：主机时钟落后超过 `seal-after` |
| `RangeExhaustedException` | key 已到达 `Long.MAX_VALUE` |
| `GeneratorDisabledException` | 该生成器配置了 `disabled: true` |
| 启动后的第一个 ID 需要等待 | 新持有者要等到自己的时钟越过槽位的 floor：即上一个持有者的 ceiling，或迁移刚完成时的 W（最多约 `source-skew`，10s） |
| `onDuplicateOwnerKey` / `cosid.machine.owner.duplicates` | 两个存活进程推导出相同的 owner key（同一 host:port 上的两个运行时，或复制出来的 pod）：各自获得独立的槽位，ID 仍然唯一，但应设置 `owner-key` 加以区分 |
| 号段 ID 出现间隙 | 符合预期（D19）：重启、超时与 `range-ttl` 都会丢弃区间；ID 绝不重复 |

## Redis

- 带 `WAIT` 的写入之后出现 `RedisStoreException`：在超时时间内确认的副本数不足。结果未知，操作会被重试。
  持续失败说明有副本宕机：要么恢复它，要么在清楚后果的前提下调低 `options.wait`。
- 带副本时，共享的 `StatefulRedisConnection` 客户端在打开时失败：`WAIT` 不能在共享连接上执行。
  请改为向存储提供 `RedisClient`。

## 获取更多细节

- `GET /actuator/cosid` 显示每个生成器的布局、槽位、时间窗口、批量大小与手头持有的 ID。
- `GET /actuator/health/cosId` 显示租约、剩余时间窗口与最近一次续约错误。
- 将日志器 `me.ahoo.cosid` 设为 `DEBUG`。
