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
   封存只保护**已封存的 key**:遗漏的 4.x 实例此后在这些 key 上拿到的区间远离 5.0 的区间。遗漏实例在第二次快照之后**新建的 key**(例如跨期产生的新周期 key)既没有迁移也没有封存,5.0 会再次发出它发过的号;它的时间型 ID 也不受封存保护。
   `verify` 报告 4.x 中存在而 `apply-result.json` 中没有的 key(用 `--allow-unmigrated` 放行的除外;RC 审查 D-M1),但发现时可能已经重号。所以停写仍是前提,封存只是兜底。
   封存后回滚是安全的:回到 4.x 时,4.x 从封存后的值继续发号,只要 5.0 在该 key 上预留的号少于 `sealGap`,就不会重复。
   **未封存时回滚会重号**(`--no-seal-source`,或 §6.3 的手工迁移跳过了封存;GA 审查 R1-doc-01):4.x 从 `lastMaxId` 继续取段,与 5.0 从 `lastMaxId + 1` 起已发出的号重叠。
   所以未封存的回滚必须先停止 5.0,再把每个迁移过的 4.x 值推进到 5.0 在该 key 上预留过的最大值之上(例如补做一次 `+sealGap` 的原子递增,各后端的命令见迁移指南"回滚"一节),然后才启动 4.x。

升级流程:**停止 4.x → `plan` → `apply` → `verify` → 用 `apply` 的输出生成 5.0 配置 → 启动 5.0**。不支持 4.x 与 5.0 在同一命名空间混跑。

## 3. 命令

```bash
java -jar cosid-migration.jar plan   --from <4.x 存储> --to <5.0 存储> --namespace app [--layout-config old-application.yaml] [--machine-from <4.x 机器号存储>] [--machine-to <5.0 槽位存储>]
java -jar cosid-migration.jar apply  --plan plan.json --confirm-stopped [--observe 2m] [--gap 0] [--source-skew 10s] [--no-seal-source] [--allow-unmigrated <4.x key>]...
java -jar cosid-migration.jar verify --plan plan.json --result apply-result.json
java -jar cosid-migration.jar config --input application.yaml --result apply-result.json --output application-5.yaml [--allow-new <generator>]...
java -jar cosid-migration.jar purge  --to <5.0 存储> --namespace app --before <period>    # 运维命令:物理清理已封存的周期 key;必须先停止所有 5.0 实例(--confirm-stopped)
```

- `plan`:只读。列出每个 key 的旧值、新 key、新值,每个命名空间的 W 估计值,以及布局校验结果;输出 `plan.json` 与可读报告。未识别的 key 单独列出。
  给了 `--layout-config` 时,`plan` 把 4.x 的 `cosid.segment.distributor.type` 与 `--from` 比较,`cosid.machine.distributor.type` 与 `--from` 或 `--machine-from` 比较,不一致即拒绝;
  4.x 把号段与机器号放在不同存储时还要求 `--machine-to`(5.0 中接收槽位的存储,即 `cosid.machine.leased` 的存储,可以与 `--to` 相同)。
- `apply`:幂等。在目标存储中只把 `next` 与 `next_floor` 往大的方向推进(取 `max(当前值, 计划值)`),重复执行不会出错。
  **"迁移时刻"指 `apply` 写入时目标存储的时钟 `T_apply`**,记录在 `apply-result.json` 中。
  有无法映射的 4.x key(5.0 读不懂的分组格式、名字含 `/`)时 `apply` 中止,除非每个 key 都用 `--allow-unmigrated <ns>.<name>` 显式放行。
- `verify`:重新读取两端,确认 M1–M3 成立,并且源在封存之后没有继续增长;还报告 4.x 中存在而结果中没有的 key(例如 `apply` 之后遗漏实例新建的 key)。
- `config`:把 4.x 的 `cosid.*` 配置尽量转换为 5.0 的配置模型,无法转换的项写进报告;StaticSlot 的 `not-before` 取自 `apply-result.json`。
  每个号段与周期号段生成器都必须接上 `apply-result.json` 中的某个 key,否则拒绝(名字对不上的生成器会从头发号,重复 4.x 的号);4.x 从未发过号的生成器用 `--allow-new <generator>` 放行。
- 源与目标可以是不同的存储类型,例如从 Redis 迁移到 JDBC。
- Redis(源或目标):每次写入(4.x 封存、5.0 raise、prepare)之后,若写入的节点有已连接的副本,工具执行 `WAIT 1`;5s 内没有副本确认就报错停止,
  因为此时 failover 会丢掉这次写入。与运行时的 `wait=auto` 一致(GA 审查 R1-mig-02);确认复制正常后重跑该步骤即可(`apply` 幂等)。

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

**独立的 `cosid.generator.namespace`(GA 审查 R1-x-07)**:4.x 的 CosIdGenerator 在 `cosid.generator.namespace`(空则用 `cosid.namespace`)里分配机器号,
用同一个分配器与同一种记录格式,常见用法是让多个服务共享这个命名空间,使 80 位 ID 跨服务唯一。

- alpha.1 的止血(#1369)已由命名 machine(D37,PR5)取代:CosIdGenerator 已关闭时,独立的 `cosid.generator.namespace` 照旧作为只含机器号记录的附加命名空间进入 `T4` 与静止观察。
- **CosIdGenerator 启用时(设计见 [api-freeze.md](api-freeze.md) §2.7)**:
  - `config` 写出命名 machine `cosid.machines.generator`(`namespace` 取 4.x 值;存储同默认 machine;默认 machine 为 static 时写 static,槽位 `${COSID_GENERATOR_SLOT}`;
    `bits`/`tolerance`/`slot-offset` 取自本服务 `apply-result.json` 记录的共享命名空间元数据(未迁移时写 offset 0、bits 10 并报告);bits 默认沿用 `cosid.machine.machine-bit`(10),
    因为 5.0 的 bits 是每个命名空间预建的槽位数,不是 4.x 的 20 位节点宽度),并让转换出的 `cosid80` 写 `machine: generator`。
    报告提示:峰值 Pod 总数(含 surge)超过 1024 时调大 bits,且受 `slotOffset + 2^bits ≤ 2^nodeBits` 约束;共享该命名空间的全部服务必须用相同的 `bits`、`tolerance`、`slotOffset`。
  - 共享命名空间**单独迁移一次**:`plan/apply --namespace <共享命名空间>`,只做 M2。`plan` 读出 4.x 记录中出现过的最大机器号 `M`,
    默认 `slotOffset = 2^nodeBits − 2^bits`,把 5.0 的槽位放在节点空间顶端,给 4.x 留出最大余量(cosid80 默认节点 20 位、bits 10 时约 104 万个机器号);`--slot-offset` 可改。
    `plan` 报告并在 `plan.json` 记录 `headroom = offset − (M + 1)`(4.x 还能分出多少机器号才会与 5.0 相交),低于 `2^bits` 时升级为醒目警告。
    4.x 分配器的硬上限是 `2^(cosid.generator.machine-bit)`(4.1.0 的 `AbstractMachineIdDistributor` 对 `> maxMachineId(machineBit)` 抛 `MachineIdOverflowException`),
    `config` 把它原样写成 5.0 的 `node-bits`,所以保留 4.x 位宽时不可能由构造保证:`guaranteed` 仅当 offset ≥ 该上限时为 true,滚动迁移实际为 false。
    任何偏移都检查 `offset > M` 与 `offset + 2^bits ≤ 2^nodeBits`(nodeBits 为 4.x `cosid.generator.machine-bit`,超出时给出可用的 `--slot-bits`)。
    报告两种做法:**滚动迁移**(offset > 0:4.x 与 5.0 同时在线、各服务逐个切换,`apply` 不需要 `--confirm-stopped`,不做静止观察)
    与**一次性切换**(`--slot-offset 0`:沿用静止观察,共享它的服务都必须停止)。两种做法都写入 W 与元数据 `{limit, tolerance, slotOffset}`;`apply` 幂等,重复执行不变。
    `M` 由各存储的 4.x 记录读出:JDBC `machine_id`;Redis `adder`(最后发出的号)与 `itc_idx`/`revert`/`lease` 中的号;Mongo `machineId`;ZooKeeper `__itc_idx`/`__revert` 中的号与 `__counter − 1`。
  - 滚动期间的不变式:`4.x 机器号 < slotOffset ≤ 5.0 节点号`。靠操作约束(headroom 即余量):4.x 按最小空闲号分配,迁移期间不能把 4.x 扩容到 `slotOffset` 个实例以上;
    两种情况下 `verify` 发现 4.x 机器号 ≥ offset 都会报错。
    有偏移时时间段重叠不会重号,W 作为纵深防护保留。
  - 各服务自己的 `plan` 读出目标中该命名空间的元数据,`apply` 检查它已准备好且与计划一致,否则拒绝并提示先做这一步;结果写进 `apply-result.json` 供 `config` 使用。
    一次性切换时共享命名空间的记录照旧计入本服务的 `T4` 与静止观察,滚动迁移时不计入。
  - 4.x CosIdGenerator 的 `timestamp-bit + sequence-bit > 63` 时,`plan` 与 `config` 直接报错(5.0 cosid80 把时间与序列装进一个 `long`,D36),不生成启动失败的配置。设计见 [api-freeze.md](api-freeze.md) §2.7。

### 4.3 布局校验(M3)

工具从 `--layout-config` 读取 4.x 的 Snowflake 与 CosIdGenerator 布局,与 5.0 配置比较:

- 布局与 epoch 都相同:由 M2 保证不相交,通过。"相同"只比较 `epoch`、`unit` 与三个位宽;`seqResetThreshold` 只决定每个时间单位的序列从哪里开始,
  不改变数值空间,不同时照样通过并写进报告(GA 审查 R1-x-04)。`config` 把 4.x 的 `sequence-reset-threshold` 写成 5.0 的 `seq-reset-threshold`
  (D36 落地后,cosid80 的位宽、阈值与 friendly 的 `pad-start` 同样写出;4.x 阈值超出 5.0 的序列范围时写 `2^seqBits − 1` 并报告)。
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

## 6. 不用工具的停机迁移(D32)

工具是推荐路径。对于简单的部署,也可以不用工具,停机后手工迁移。它只改文档,不新增配置项,
依靠的仍是 §1 的三条约束,只是由操作者代替工具去满足。§2 的前提不变:**所有 4.x 实例必须先停止**,不支持新旧混跑;
没有静止观察,也没有 `verify`,是否全部停止只能由操作者保证。

### 6.1 适用边界

| 适用 | 不适用(必须用工具) |
|---|---|
| 时间型生成器(Snowflake、CosIdGenerator),**布局不变**:epoch、单位和各段位宽都与 4.x 相同,即 5.0 预设沿用的 4.x 默认值(D16) | 自定义布局或自定义 epoch,包括秒级 Snowflake 的自定义 epoch(D-M3) |
| 普通(非周期)号段,包括 share | 周期(分组)号段 |
| key 数量少,可以逐个手工核对 | key 很多;或者需要静止观察、自动封存、`verify` 这些防护措施 |

周期号段不适用的原因:手工迁移靠 `initial-offset`(`BatchPolicy.initialOffset`)把号段抬过 4.x 的值,而它是**每个新 key** 的起点。
周期生成器每进入一个新周期就创建一个新 key,所以以后每个周期都会从这个大偏移开始,改变了业务语义(例如按天从 1 重置的单号)。
工具则只推进已迁移的 key,不影响以后的周期。

### 6.2 时间型(M2、M3)

步骤:停止所有 4.x 实例 → 停机时长不少于所有主机之间的最大时钟偏差(4.x 还允许主机比存储快 10s),且至少 1 分钟 → 核对各主机的 NTP 同步状态 → 启动 5.0。

- **M3**:布局相同时,时间位于最高位,时间更晚的 ID 数值一定更大。只要 5.0 的每个时间戳都晚于 4.x 发出过的任何时间戳,两边的 ID 就不相交。
- **M2**:由停机时长保证。4.x 最后一个时间戳不晚于"停机时刻 + 4.x 主机的超前量";5.0 主机的时钟只要落后真实时间不超过停机时长,第一个时间戳就晚于它。
- **风险**:手工迁移时,5.0 新命名空间的槽位 `next_floor` 为 0,时钟落后超过停机时长的主机**不会被拦下**,会发出与 4.x 时间范围重叠的 ID;
  若它与 4.x 某实例的机器号相同,就会重号。这正是工具写入 `next_floor = W` 的原因(§4.2)。所以停机时长要按实际的最大偏差取,并在启动前核对 NTP。
- **StaticSlot**:可以手工设置 `cosid.machine.static.not-before`(取停机时刻 + 最大时钟偏差,ISO-8601 时刻),作用与工具写入的 W 相同(§4.4),
  时钟落后的主机会等到越过它才发号。租约模式没有对应的配置项(命名空间级 `not-before` 留待 RC 之后评估)。
- 遗漏的 4.x 实例与 5.0 实例取到同一机器号时,同一时刻会发出相同的 ID。手工迁移没有静止观察,必须确认全部停止。

### 6.3 普通号段(M1)

步骤:停止所有 4.x 实例 → 读出每个号段在 4.x 存储中的值(位置见 §4.1;也可以用只读的 `cosid-migration plan` 读出)
→ 在 5.0 中给对应生成器设置 `cosid.generators.<name>.initial-offset = last_max_id + 1` → 启动 5.0。

- **M1**:4.x 每次取段都把 `last_max_id` 原子地推进一个 step,发出 `(max − step, max]`,所以 `last_max_id` 是所有实例已预留区间的上界。
  5.0 的第一个区间从 `last_max_id + 1` 开始,越过了 4.x 预留过的一切(相当于 `gap = 0`)。
- `initial-offset` 挂在生成器上,与 key 映射无关;5.0 key 仍为 `{namespace}/{name}`。share 号段对应 5.0 的 `default` 生成器,要读取共享的那个 4.x key(`{namespace}.__share__`)。
- `initial-offset` 只在 5.0 的 key **首次创建**时生效(S2):必须在 5.0 第一次启动前配置好;key 已存在时它不起作用。
- **手工封存(建议,可选)**:对 4.x 的值做一次大幅原子递增(例如 `+2^40`,与工具的 `sealGap` 相同),并取 `last_max_id = 封存后的值 − 2^40`。
  这样封存之前的取段都被计入,遗漏的 4.x 实例此后取到的区间远离 5.0。封存后回滚是安全的;**没有封存时,回滚前必须先把 4.x 的值推进到 5.0 已预留的最大值之上**,否则重号(§2)。
