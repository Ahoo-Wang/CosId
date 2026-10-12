# 生产环境清单

## 生成器

- **根据需求选择生成器**，见 [ID 生成器](../concepts/id-generator.md#如何选择生成器)。数字主键默认选择号段链。
- **设置 `cosid.namespace`** 为应用名。共用存储的应用，除非共享 ID，否则不要共用命名空间。
- **只保留一个共享生成器。** 同时启用 `snowflake` 和 `segment` 时关闭其中一个 `share`，重要的 ID 使用命名生成器。
- **不要原地修改 Snowflake 布局**：生成器的 `epoch`、`timestamp-bit`、`machine-bit` 和 `sequence-bit` 在其 ID 的整个生命周期内保持不变。
- **把 Snowflake ID 以字符串形式发给浏览器**，或使用 53 位布局，见 [JavaScript 安全的 ID](../concepts/snowflake.md#javascript-安全的-id)。
- **验证转换器输出**是否适合目标列：长度、补齐、前缀，以及 62 进制的大小写敏感。

## 号段

- **按有序性和空洞选择 `step`，而不是按吞吐量**：号段链在高负载时会扩大预取，因此较小的步长（10 到 100）通常就够了。每次重启都会跳过该实例剩余的号段。
- **只有日志中频繁出现 `Hunger` 扩容时**，才增大 `chain.safe-distance`。
- **使用能持久化每次写入的存储。** 计数器回退会再次发放同样的 ID。
- 表中已有数据时，**把 `offset` 设置**到现有最大 ID 之上。
- **Proxy 的 `segment.max-step`** 不小于所有客户端的 `step × max-prefetch-distance`。

## 机器号

- **从存储租用机器号**；`manual` 只用于单实例。
- 同一命名空间的所有实例**使用相同的 `cosid.machine.guarder.safe-guard-duration`**。
- **保持 `cosid.machine.guarder.fail-fast` 开启**（默认）：请求失败好过生成重复 ID。
- **`machine-bit` 要足够**容纳发布期间的最大实例数，以及租约尚未过期的实例。
- **只把身份固定的实例标记为 `stable`**，并同时设置固定的 `instance-id`。
- **保持各主机时钟彼此同步、并与存储的时钟同步**（NTP 或 chrony，使用渐进调整而不是跳变），这样重启时不必等待水位线。
  - 使用 Redis、JDBC、MongoDB 和 Proxy 时，租约按存储的时钟判定：主机与存储之间的时钟偏差不会让机器号提前过期。
  - **ZooKeeper 没有 CosId 能读取的时钟**：租约按各主机的时钟判定。若某主机时钟比另一主机快出超过余量 `min(10s, safe-guard-duration / 10)`，就可能在对方仍在生成 ID 时接管其机器号，导致两者生成相同的 ID。请监控时钟偏差，并使其远小于该余量。

## 运维

- **为 `machineId` 健康指示器配置告警**，见[监控](./monitoring.md)。
- **只向可信调用方暴露 actuator 端点**；`cosidGenerator` 会生成 ID，`cosid.actuator.delete.enabled` 保持关闭。
- **在内网运行 Proxy 服务端并启用令牌**，见 [Proxy 部署安全](../backends/proxy.md#部署安全)。
- **为 JDBC 后端提供独立的 `DataSource`**，不参与业务事务。
- 重启和版本升级**遵循[滚动升级](./rolling-upgrade.md)**。
