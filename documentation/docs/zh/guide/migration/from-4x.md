# 从 4.x 迁移到 5.0

5.0 不保留任何兼容代码（D1）：API、配置、存储格式都不兼容。唯一的桥梁是离线工具 **`cosid-migration`**。
它读取 4.x 数据，写入 5.0 数据，保证新 ID 永远不会与 4.x 已发出的 ID 冲突，并转换配置。

::: danger 不允许新旧混跑
4.x 与 5.0 **绝不在同一命名空间中运行**，哪怕一瞬间也不行。该命名空间的每个 4.x 实例都在迁移前停止，5.0 在迁移后启动。
这不是滚动升级。
:::

## 工具的保证

| # | 约束 | 方式 |
|---|---|---|
| M1 | 号段：5.0 的第一个区间位于 4.x 预留过的一切之后 | 按 key，`next = lastMaxId + 1 + gap` |
| M2 | 时间型 ID：5.0 的第一个时间戳晚于所有 4.x 时间戳 | 命名空间的每个槽位都设置 `next_floor = W` |
| M3 | 5.0 与 4.x 的时间型 ID 绝不在同一列中相遇 | 保留 4.x 的布局与 epoch（D16）；`plan` 检查自定义的布局 |

`W = max(T_apply, T4 + 1s) + sourceSkew`：`T_apply` 是 `apply` 写入时目标存储的时钟，`T4` 是 4.x 机器号记录中的最大时间戳，
`sourceSkew`（10s）是 4.x 允许的时钟偏差。迁移后第一个时间型 ID 要等到时钟越过 W，最多约 `sourceSkew`。

## 获取工具

`cosid-migration.jar` 是一个可执行的 fat jar（Java 21），附在 release 中，也可以通过
`./gradlew :migration:shadowJar` 构建（`migration/build/libs/cosid-migration.jar`）。

```bash
java -jar cosid-migration.jar --help
```

源与目标可以是不同的存储类型（Redis → JDBC、MongoDB → ZooKeeper……）：

| 位置 | 读取的 4.x 数据 | 写入的 5.0 数据 |
|---|---|---|
| `jdbc:<url>`，选项 `user`、`password` | `cosid(name, last_max_id)`、`cosid_machine` | `cosid_range`、`cosid_slot`、`cosid_namespace`（`initSchema=true` 会创建它们） |
| `redis://` / `rediss://`，选项 `cluster=true` | `cosid:{ns.name}.adder`、`cosid:{ns}:*` 机器号 key | `cosid:{key}`、`cosid:{ns}:meta`、`:slot`、`:free` |
| `mongodb://`，选项 `database` | `cosid`、`cosid_machine` | `cosid_range`、`cosid_namespace`、`cosid_slot` |
| `zookeeper://host:port[/chroot]` | `/cosid/{ns}.{name}`、`/cosid/{ns}/…` | `/cosid/range/{key}`、`/cosid/slot/{ns}/…` |

密码在每条命令中通过 `--from-option password=…` / `--to-option password=…`，或 `COSID_MIGRATION_FROM_PASSWORD` /
`COSID_MIGRATION_TO_PASSWORD` 提供。它们永远不会写入 `plan.json`。

## 操作手册

### 1. 停止所有 4.x 实例

停止该命名空间的**每一个** 4.x 实例：包括只用号段的服务，以及不留下机器号记录的 Manual 或 StatefulSet 实例。
工具无法证明它们都已停止，因此从三个方面保护你：由你确认（`--confirm-stopped`）、观察变化（静默期）、封存源数据（第 3 步）。

### 2. 规划（只读）

```bash
java -jar cosid-migration.jar plan \
  --from redis://redis-4x:6379 \
  --to jdbc:mysql://db/cosid --to-option user=cosid \
  --namespace shop \
  --layout-config application.yaml     # 4.x 配置：布局、分组模式、share
```

阅读报告。它列出每个 key 及其新 key 与新值、`NOT MIGRATED` 的 key 及原因、W 的估计值、布局检查与警告。
按要求修正后再次规划：

- `--rename old=new`：不是合法 5.0 生成器名（不能含 `/`）的 4.x 名字，或你想改的名字。4.x 的 share `__share__` 变为 `default`。
- `--group-pattern name=yyyy-MM-dd`：分组键不是 5.0 预设（`yyyy`、`yyyyMM`、`yyyyMMdd`）的分组号段。它的 key 会被重新格式化为
  同粒度的预设。**每个周期都会迁移，包括过去的周期**，因为 4.x 使用服务器的默认时区。
- `--slot-bits`（4.x 的 `machine-bit`，默认 10）、`--tolerance`（1s）：5.0 命名空间的槽位参数。

key 映射：`{ns}.{name}` → `{ns}/{name}`；分组的 `{ns}.{name}@{groupKey}` → `{ns}/{name}/{period}`。

### 3. 应用

```bash
java -jar cosid-migration.jar apply --plan plan.json --confirm-stopped --layout-config application.yaml
```

1. **静默期**：对每个源 key 与机器号记录做快照，等待 `--observe`（2 分钟），再次读取，**任何变化都会中止**：说明仍有 4.x 实例在运行。
2. **封存源数据**（默认开启；`--no-seal-source` 关闭）：每个 4.x 号段做一次 `2^40` 的原子自增（`--seal-gap`；多次运行请保持一致），
   并从封存后的值读取 `lastMaxId`。被遗漏的 4.x 实例会拿到远高于 5.0 的区间，`verify` 会报告它。
3. **写入目标**：把每个 5.0 key 提升到 `next`，记录 `{limit, tolerance}`，并以 `next_floor = W` 准备每个槽位。目标值只升不降
   （`max(current, planned)`）。

`apply` 是幂等的。如果中途停止（崩溃、超时），再运行一次即可。当某个 5.0 运行时已在该命名空间持有槽位，或命名空间的槽位参数不同时，
它会拒绝执行。结果文件 `apply-result.json` 包含 `T_apply`、W 和 `notBefore`（ISO-8601 格式的 W）。

### 4. 验证

```bash
java -jar cosid-migration.jar verify --plan plan.json --result apply-result.json
```

它再次读取两边的存储，检查 M1–M3，以及封存后没有任何 4.x 号段或机器号记录发生变化。退出码 `1` 表示存在问题。
5.0 启动后该检查依然有效。

### 5. 转换配置

```bash
java -jar cosid-migration.jar config --input application.yaml --result apply-result.json \
  --output application-5.yaml --zone Asia/Shanghai
```

4.x 的 `cosid.*` 配置被转换为 5.0 模型。**所有无法原样转换的内容都以注释形式放在输出文件顶部：请逐条审阅。**
静态槽位会从结果中得到 `not-before: W`。4.x 的 proxy 配置变为一个 `remote` 存储（`baseUrl`、`token`、`connectTimeout`、`requestTimeout`）。

### 6. 启动 5.0

使用新配置和 5.0 artifact（见下文）部署。第一个时间型 ID 要等到时钟越过 W，最多约 `sourceSkew`。

### 之后：清理旧周期

运行时周期号段的 key 只封存、不删除（D23）。物理删除过去的周期是一个离线步骤：

```bash
java -jar cosid-migration.jar purge --to jdbc:… --namespace shop --before 20250101 --dry-run
java -jar cosid-migration.jar purge --to jdbc:… --namespace shop --before 20250101 --confirm-stopped
```

所有命令的退出码：`0` 完成，`1` 检查失败或命令中止，`2` 用法错误。

## 回滚

- **号段**：安全。封存只会把 4.x 的值调高，因此 4.x 会在 5.0 可能用过的一切之上继续。
- **时间型 ID**：等最后一个 5.0 ceiling 过去（租约 `ttl` 加 `tolerance`，默认 31s）之后再重新启动 4.x。

## Artifact 与代码

| 4.x | 5.0 |
|---|---|
| 带 Gradle feature variants 的 `cosid-spring-boot-starter` | `cosid-spring-boot-starter` + `cosid-store-<backend>` |
| `cosid-spring-redis`、`cosid-jdbc`、`cosid-mongo`、`cosid-zookeeper` | `cosid-store-redis`、`-jdbc`、`-mongo`、`-zookeeper` |
| proxy 客户端 / proxy 服务端 | `cosid-store-remote` / cosid-server 镜像（仅新 API） |
| `cosid-jackson`、`cosid-mybatis`、`cosid-spring-data-jdbc`、`cosid-activiti`、`cosid-flowable`、`cosid-axon` | 同名 artifact（D29），各自只依赖 `cosid-core` 与自己的三方库 |
| `cosid-accessor` | `cosid-core`（`me.ahoo.cosid.accessor`） |
| `cosid-test` | `cosid-store-tck` |
| `cosid-dependencies` | `cosid-bom` |
| `cosid-sharding` | `cosid-sharding`（用 `ShardingRange` 取代 Guava 的 `Range`） |
| Java 17 | **Java 21**，Spring Boot 4.0+ |

| 4.x API | 5.0 |
|---|---|
| `SnowflakeId`、`MillisecondSnowflakeId`、`SecondSnowflakeId`、`ClockSync*`、`StringSnowflakeId` | `IdGenerator` bean；`TimeBasedGenerator`、`Layout`、`ClockRegressionPolicy`；文本通过编解码器输出 |
| `SegmentId`、`SegmentChainId`、`DefaultSegmentId` | 带 `BatchPolicy` 的 `SegmentGenerator` |
| 分组号段、`generateGrouped` | `PeriodicSegmentGenerator`、`GroupedId` |
| `CosIdGenerator`（Radix62 / Friendly） | `cosid80` + `PartsCodec` |
| `IdConverter` 系列 | `IdCodec`：`Radix`、`Friendly`、`DatePrefix`、前缀 / 后缀 |
| `SnowflakeIdStateParser` | `Layout.unpack`、`Friendly.decode` |
| `IdGeneratorProvider`、`DefaultIdGeneratorProvider.INSTANCE`、share | `CosIdRuntime`、名为 `default` 的生成器 |
| `MachineIdDistributor` 系列、guarder、`~/.cosid-machine-state` | `cosid.machine.leased` / `static`（见[槽位租约](../concepts/slot-lease.md)） |
| `IntegerIdGenerator`、`UncertaintyIdGenerator`、`LazyIdGenerator` | `IdGenerators.toInt`、`.uncertain`、`.lazy` |
| `CosIdStoreProvider` | `StoreProvider` |
| `Customize*Properties` | `RuntimeSpecCustomizer` |
| Actuator 对生成器的 DELETE | `cosid.generators.<name>.disabled: true` |
| `guarder.fail-fast: false` | 不提供：它以唯一性换取可用性（P1） |
