# 09 4.x → 5.0 离线迁移

5.0 不保留任何兼容代码(D1)。4.x 与 5.0 之间唯一的连接点是离线迁移工具 `cosid-migration`(位于 `migration/`,可执行 fat jar)。
它只在迁移时运行;运行时代码中不存在读取 4.x 格式的逻辑。

## 1. 唯一的硬约束

**迁移后发出的 ID 不得与 4.x 已发出的 ID 重复。** 具体为三条:

| # | 约束 | 手段 |
|---|---|---|
| M1 | 号段:5.0 的第一个区间必须越过 4.x 已预留的最大值 | 5.0 的 `next = lastMaxId + 1 + gap`(4.x 发出的是 `(max − step, max]`) |
| M2 | 时间型:5.0 的第一个时间戳必须晚于 4.x 任何实例发出过的时间戳 | 每个命名空间所有槽位的 `next_floor = W`,W 的取法见 §4.2 |
| M3 | 数值空间:同一业务列中,5.0 时间型 ID 与 4.x ID 不相交 | 默认 Layout 与 epoch 沿用 4.x(D16);自定义布局时工具会做校验(§4.3) |

## 2. 前提:停写

迁移是离线的:**所有 4.x 实例必须先停止**。仅靠机器号记录无法证明这一点:只用号段的部署,以及 Manual/StatefulSet/stable 实例,都没有续约记录。所以工具采用三重保护:

1. **操作者确认**:`apply` 必须带 `--confirm-stopped`。
2. **静止观察**:`apply` 先对所有源 key 和机器号记录做快照,等待观察窗口(默认 2 分钟,可配置),再次读取,有任何变化即中止。
3. **封存源**(默认开启,可用 `--no-seal-source` 关闭):**先封存、后写目标**(R4-C 实现):对每个 4.x 号段做一次原子递增 `sealGap`(默认 2^40,与 4.x 取段是同一种操作),
   取 `lastMaxId = 封存后的值 − sealGap`,这样快照与封存之间 4.x 的取段也被覆盖。已达到 `计划值 + sealGap` 的号段视为已封存,重跑不会重复封存(`--seal-gap` 在多次运行间必须一致)。
   对**已封存的 key**,即使还有遗漏的 4.x 实例在运行,它拿到的区间也远离 5.0 的区间,不会重复。但遗漏实例在封存之后**新建的 key**(例如跨期产生的新周期 key)不受封存保护,`verify` 必须标出 4.x 中存在而迁移结果中没有的 key(RC 审查 D-M1)。所以停写仍是前提,封存只是兜底。
   封存不影响回滚:回到 4.x 时,4.x 会从更大的值继续发号,仍然不会重复。

升级流程:**停止 4.x → `plan` → `apply` → `verify` → 用 `apply` 的输出生成 5.0 配置 → 启动 5.0**。不支持 4.x 与 5.0 在同一命名空间混跑。

## 3. 命令

```bash
java -jar cosid-migration.jar plan   --from <4.x 存储> --to <5.0 存储> --namespace app [--layout-config old-application.yaml]
java -jar cosid-migration.jar apply  --plan plan.json --confirm-stopped [--observe 2m] [--gap 0] [--source-skew 10s] [--no-seal-source]
java -jar cosid-migration.jar verify --plan plan.json --result apply-result.json
java -jar cosid-migration.jar config --input application.yaml --result apply-result.json --output application-5.yaml
java -jar cosid-migration.jar purge  --to <5.0 存储> --namespace app --before <period>    # 运维命令:物理清理已封存的周期 key
```

- `plan`:只读。列出每个 key 的旧值、新 key、新值,每个命名空间的 W 估计值,以及布局校验结果;输出 `plan.json` 与可读报告。未识别的 key 单独列出。
- `apply`:幂等。在目标存储中只把 `next` 与 `next_floor` 往大的方向推进(取 `max(当前值, 计划值)`),重复执行不会出错。
  **"迁移时刻"指 `apply` 写入时目标存储的时钟 `T_apply`**,记录在 `apply-result.json` 中。
- `verify`:重新读取两端,确认 M1–M3 成立,并且源在封存之后没有继续增长。
- `config`:把 4.x 的 `cosid.*` 配置尽量转换为 5.0 的配置模型,无法转换的项写进报告;StaticSlot 的 `not-before` 取自 `apply-result.json`。
- 源与目标可以是不同的存储类型,例如从 Redis 迁移到 JDBC。

## 4. 4.x 数据映射

### 4.1 号段

| 4.x 后端 | 4.x 位置 | 读取的值 |
|---|---|---|
| JDBC | 表 `cosid`,`name = {namespace}.{name}` | `last_max_id` |
| Redis | `cosid:{namespace.name}.adder` | 字符串值 |
| MongoDB | 集合 `cosid`,以 `{namespace}.{name}` 标识的文档 | `lastMaxId` |
| ZooKeeper | `/cosid/{namespace}.{name}` | 计数器值 |
| Proxy | 代理背后的实际存储 | 按实际存储读取 |

- 普通号段:5.0 key 为 `{namespace}/{name}`,`next = lastMaxId + 1 + gap`。
- 分组号段:4.x 的 `{namespace}.{name}@{groupKey}` 映射为 5.0 的 `{namespace}/{name}/{period}`。**默认迁移所有周期**,包括过去的周期:
  4.x 的周期 key 取决于服务器默认时区与时钟,5.0 改用显式时区后,仍可能落入工具认为"已过去"的周期。
  工具会校验 `groupKey` 格式与 5.0 的 `Period` 预设(`yyyy`、`yyyyMM`、`yyyyMMdd`)一致,不一致时要求操作者提供映射。

### 4.2 机器号与时间型 ID(M2)

4.x 的 `cosid_machine` 表、Redis `cosid:{ns}:*`、Mongo 集合 `cosid_machine`、ZooKeeper `/cosid/{ns}/…` 用于两件事:
静止观察,以及取得 4.x 记录中的最大时间戳 `T4`(取 `last_timestamp`、`watermark`、`renew_time` 的最大值)。

4.x 的记录会滞后于实际发号(本地状态每秒写一次),而且 4.x 允许主机时钟比存储快 10s。因此:

```
W = max(T_apply, T4 + 1s) + sourceSkew        // sourceSkew 默认 10s,对应 4.x 的时钟偏差上限
```

工具为每个命名空间写入元数据 `{limit, tolerance}`,并预建 `limit` 个空闲槽位,`next_floor = W`。槽位分配关系不迁移。
5.0 实例启动后重新选槽,首次发号前最多等待约 `sourceSkew`。

### 4.3 布局校验(M3)

工具从 `--layout-config` 读取 4.x 的 Snowflake 与 CosIdGenerator 布局,与 5.0 配置比较:

- 布局与 epoch 都相同:由 M2 保证不相交,通过。
- 不同:要求 `new.pack(W, 0, 0) > old.pack(Tmax4, maxNode, maxSeq)`,其中 `Tmax4 = W` 作为 4.x 可能发出的最大时间戳的上界。
  不成立则拒绝,并提示保持 4.x 布局,或者改用新的业务列。

### 4.4 StaticSlot 用户

4.x 的本地状态文件(`~/.cosid-machine-state`)不迁移。`config` 子命令写入 `cosid.machine.static.not-before = W`,
5.0 StaticSlot 首次启动时以它作为 floor。

## 5. 测试

- 用 4.x 的 jar(测试依赖 `me.ahoo.cosid:*:4.1.x`)向每种后端写入真实数据,包括分组号段与机器号;然后迁移,再用 5.0 运行时发号,断言 M1–M3。
- 模拟遗漏实例:迁移期间保持一个 4.x 实例运行,断言静止观察会中止 `apply`;关闭观察后,断言封存仍然避免重复。
- `apply` 的幂等性,以及中断后重跑。
- 跨存储类型迁移(Redis → JDBC)。
