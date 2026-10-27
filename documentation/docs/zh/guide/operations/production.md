# 生产检查清单

## 上线前

- [ ] **按所需保证选择存储。** 若唯一性必须经受任何故障切换，选择 JDBC 或 ZooKeeper。
  Redis 只能在满足[其持久化前提](../backends/redis.md#durability)时使用。
- [ ] **满足存储的最低版本**（MySQL 8.4、MariaDB 10.11、PostgreSQL 15、SQL Server 2019、Redis 7.0、
  副本集上的 MongoDB 6.0、ZooKeeper 3.8）。更早的版本[只是实测过，不受支持](../backends/index.md#supported-versions-and-measured-compatibility)。
- [ ] **提前创建表结构**（`schema-<dialect>.sql`）。`initSchema` 留给开发环境。
- [ ] **每个应用一个命名空间**，每个实例上相同。共享同一命名空间的实例共享其槽位与 key，
  并且必须使用相同的 `bits` 与 `tolerance`（不一致会被拒绝）。
- [ ] **确定机器号位数**：至少能容纳可同时运行的实例数，包括滚动更新时的超额实例。
  `bits ≤ layout.nodeBits`（`SNOWFLAKE_*` 为 10，`JS_SAFE_*` 为 3）。
- [ ] **静态槽位**（`machine.static`）：部署必须保证槽位唯一，且 `state-file` 必须位于持久卷上。
  没有它，就无法防护跨重启的时钟回拨。
- [ ] **存储 timeout ≤ 每个号段的 `reserve-timeout`**（默认分别为 2s 与 3s；启动时会检查）。
- [ ] **在主机与存储上运行 NTP。** 时钟偏差绝不会破坏唯一性，但偏差超过 `tolerance` 时，接近窗口 ceiling
  会暂停发号。
- [ ] **cosid-server**：配置 token（默认开启认证）、按客户端划分命名空间、在其前面部署 TLS，并运行多于一个实例。
- [ ] **采集指标并配置告警**（见下文）。向编排系统暴露 `/actuator/health`。
- [ ] **优雅停机**（SIGTERM，而不是 SIGKILL）。正常关闭会以精确的最后时间戳释放槽位，下一个持有者紧接其后开始。
  强制杀死只会让该槽位的复用最多推迟 `ttl + tolerance`。

## 值得配置的告警

| 信号 | 含义 |
|---|---|
| 健康检查 `cosId` 为 `DOWN` | 槽位租约已丢失且未能重新获取：时间型生成器快速失败 |
| `cosid.machine.lease.renew.failures` 上升 | 存储不可达或缓慢；id 会继续发出，直到 ceiling |
| `cosid.machine.window.remaining` 接近 0 | 续约没有成功；发号即将暂停 |
| `cosid.machine.clock.skew` 超过 `tolerance / 2` | 修复 NTP |
| `cosid.segment.refill.failures` 上升 | 存储出现问题；手头的 id 用完后，调用方会收到 `RangeUnavailableException` |
| `cosid.machine.owner.duplicates` > 0 | 两个存活进程报告了相同的 owner key；请设置不同的 `owner-key` |
| `cosid.clock.backwards{action=fail}` | 时钟回拨超过了 `wait-up-to` |

## 升级与重启

除租约外，5.x 实例是无状态的：滚动重启无需协调。重启的实例通常会拿回之前的槽位（粘性选择）。
配置了 `preferred-slot`（StatefulSet 序号）时，它会先请求该槽位。超额扩容期间，额外的实例会获取其他空闲槽位。

从 4.x 升级不是滚动升级：4.x 与 5.0 绝不共享命名空间。请参照[迁移指南](../migration/from-4x.md)。
