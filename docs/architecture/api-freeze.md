# RC1 API 冻结设计

> 状态:**已审,按协调会话结论定稿**(2026-10-28,基于 `main` @ `5021602e`;§12 第 5 项改为 RC 前纳入 `slotOffset`)。只定设计,不改代码;实现按 §10 一次一个 PR,PR1–PR5 已合入(#1372–#1376),文档为 PR6。
> 输入:README D36–D38;[ga-review.md](ga-review.md) §2、§3、§3.1、§4 与各模块"触及冻结面"的条目。
> 决策表的新增或修改见 §11,由协调会话转录进 README;本文件不改 README、rc-review、roadmap。
> 设计细节同步进 [03](03-core-model.md)、[05](05-machine-lease.md)、[07](07-runtime-and-spring.md)、[09](09-migration.md)、[11](11-feature-parity.md)。

## 0. 冻结面与兼容承诺的口径

RC1 冻结的是以下三类,GA 之后只做向后兼容的增量:

| 冻结面 | 范围 | GA 后允许 |
|---|---|---|
| Java API | core 导出的 9 个包(`internal` 除外)、各 store 的公开类型与 SPI、`spring/autoconfigure` 中用户会直接使用的类型(`RuntimeSpecCustomizer`、`CosIdEndpoint` 的响应形状) | 新类型、新方法、builder 新字段(有默认值)、sealed 规格接口的新变体(§4.3) |
| 配置 | `cosid.*` 的属性名、默认值、校验规则;`cosid.stores.*.options` 的键 | 新属性(有默认值);**不**收紧已有的校验 |
| 存储格式与协议 | 06 的库表/键/节点格式,08 的 HTTP 协议 | 追加字段(旧读者忽略) |

不属于冻结面:`internal` 包;Spring 的**绑定类** `CosIdProperties` 及其嵌套 record(承诺的是属性键,不是 Java 类型,javadoc 写明);指标的**取值**(名称与标签属于冻结面)。

---

## 1. D36:cosid80 可配置(R1-x-03、R1-x-05、R1-spring-04)

### 1.1 core

```java
// GeneratorSpec.CosId80(final class,见 §4)
public static Builder builder();                 // layout 默认 Layout.COSID_80
Layout layout();                                 // 新增
ClockRegressionPolicy regression();
PartsCodecSpec codec();
Optional<String> machine();                      // 新增,D37

// PartsCodecSpec(sealed,变体保持 record,§4.2)
record Radix(int radix) implements PartsCodecSpec {}                 // 不变:62 或 36
record Friendly(ZoneId zone, boolean padStart) implements PartsCodecSpec {   // 新增 padStart,默认 true
    public Friendly(ZoneId zone) { this(zone, true); }
}
record Instance(PartsCodec codec) implements PartsCodecSpec {}

// PartsCodec
static PartsCodec friendly(Layout layout, ZoneId zone);                  // 不变,= padStart true
static PartsCodec friendly(Layout layout, ZoneId zone, boolean padStart); // 新增,复用 FriendlyFormat 已有的 padStart
```

**校验(`RuntimePlan`,汇总报错)**:

| 规则 | 理由 |
|---|---|
| `layout.totalBits() > 63`,否则 "fits a long: use snowflake" | 与 snowflake 的 `≤ 63` 互补,两个策略不重叠 |
| `layout.epoch() == Instant.EPOCH` 且 `unit == MILLIS` | 4.x `RadixCosIdGenerator` 直接用 `System.currentTimeMillis()`;80 位文本在各语言间互通(D4),epoch 固定可避免"同名格式不兼容" |
| `layout.nodeBits() ≥ 所选 machine 的 bits`(D37) | 同 snowflake |
| `Layout` 自身的分段校验(time 1–63、node 0–31、seq 1–62、阈值在序列范围内) | 已有 |

`RuntimeParts` 用 `cosId80.layout()` 代替常量 `Layout.COSID_80`;`PartsCodecSpec.Radix/Friendly` 按该布局构造 `PartsCodec`。

### 1.2 Spring

复用 snowflake 的布局键,按策略决定默认值与可用的键:

| 键 | snowflake | cosid80 |
|---|---|---|
| `layout` | 预设名,默认 `SNOWFLAKE_MS` | 只接受 `COSID_80`(默认) |
| `epoch`、`unit` | 可覆盖 | **报错**(固定 Unix epoch、毫秒) |
| `time-bits`、`node-bits`、`seq-bits` | 可覆盖 | 可覆盖(4.x `timestamp-bit`/`machine-bit`/`sequence-bit`) |
| `seq-reset-threshold` | 可覆盖;只改 `seq-bits` 时取 `Layout.defaultSeqResetThreshold(seqBits)` | 同左(4.x `sequence-reset-threshold`) |
| `codec[0].radix.radix` | 任意 | 62 或 36 |
| `codec[0].radix.width`、`.pad-start` | 可用 | **报错**:cosid80 的 radix 文本各段宽度由布局决定 |
| `codec[0].friendly.zone`、`.pad-start` | 可用 | 可用(`pad-start` 默认 true,4.x `cosid.generator.pad-start`) |
| `codec` 的其他项(prefix、suffix、date-prefix、custom) | 可用 | **报错**(已有:只能一个 radix 或 friendly) |

实现要点:`CosIdProperties.Generator.layout` 去掉 `@DefaultValue("SNOWFLAKE_MS")`,默认值在 `CosIdSpecs` 里按策略取;`CosIdProperties.Radix.padStart` 改为 `Boolean`(区分"未设置"),`Friendly.padStart` 保持 `@DefaultValue("true")`。
报错与其他校验一样按属性路径(`cosid.generators.token.codec[0].radix.width: ...`)汇总。

```yaml
cosid:
  generators:
    token:
      strategy: cosid80
      machine: global            # D37,可省略
      node-bits: 16              # 4.x cosid.generator.machine-bit
      seq-reset-threshold: 1023  # 4.x cosid.generator.sequence-reset-threshold
      codec: [ { friendly: { zone: Asia/Shanghai, pad-start: false } } ]
```

### 1.3 与 4.x 对照

| 4.x `cosid.generator.*` | 5.0 `cosid.generators.<name>.*` |
|---|---|
| `type: radix62 / radix36 / friendly` | `codec[0]: radix{62} / radix{36} / friendly` |
| `timestamp-bit`(44)、`machine-bit`(20)、`sequence-bit`(16) | `time-bits`、`node-bits`、`seq-bits`(默认相同) |
| `sequence-reset-threshold`(32767) | `seq-reset-threshold`(默认相同) |
| `zone-id` | `codec[0].friendly.zone` |
| `pad-start`(true,作用于 friendly) | `codec[0].friendly.pad-start`(true) |
| `namespace` | `machine: <name>` + `cosid.machines.<name>.namespace`(D37) |

### 1.4 迁移工具

- `config`:4.x 位宽、阈值与默认不同时写出 `time-bits`/`node-bits`/`seq-bits`/`seq-reset-threshold`(与 snowflake 的写法一致,`ConfigConverter.java:393-424`);
  `pad-start: false` 写成 `codec[0].friendly.pad-start: false`。删除"5.0 cosid80 resets at …"与"always padded"两条报告(`ConfigConverter.java:625-629`)。
- 4.x 阈值超出 5.0 的合法范围(`> 2^seqBits − 1`,4.x 不校验)时:写 `2^seqBits − 1` 并在报告中说明(行为等价:4.x 在该情况下也从不重置)。
- M3:已按 R1-x-04 只比较 epoch、unit 与三个位宽;位宽不同按"不同布局"的不等式判定,不变。

### 1.5 测试要点

- core:`totalBits ≤ 63` 被拒;epoch/unit 非默认被拒;`nodeBits < machine.bits` 被拒;自定义位宽的 `nextParts` 与 `PartsCodec.radix/friendly` 往返;低阈值时新时间单位的起始序列 ≤ 阈值;friendly `padStart=false` 往返(含序列为 0)。
- spring:默认 layout 按策略;cosid80 上设置 `epoch`/`unit`/`radix.width`/`radix.pad-start`/`layout: SNOWFLAKE_MS` 各报一条错;位宽与阈值映射。
- migration:`ConfigBindingTest` 增加 4.x 自定义位宽、阈值、`pad-start: false` 三例,并断言转换结果能启动(`CosIdSpecs` 无错)。

---

## 2. D37:命名的 machine(R1-x-06、R1-x-07 的最终修复)

### 2.1 core 模型

```java
// RuntimeSpec(final class,§4)
String namespace();                       // 号段 key 与默认 machine 的命名空间
Map<String, StoreSpec> stores();
Optional<MachineSpec> machine();          // 默认 machine,不变
Map<String, MachineSpec> machines();      // 新增:命名的 machine,默认空
Map<String, GeneratorSpec> generators();
public static final String DEFAULT_MACHINE = "default";

// MachineSpec(sealed,Leased/Static 为 final class)
int bits();
Optional<String> namespace();             // 新增:槽位租约的命名空间,空则取 RuntimeSpec.namespace()

// GeneratorSpec.Snowflake / CosId80
Optional<String> machine();               // 新增:空或 "default" 选默认 machine

// CosIdRuntime
Optional<SlotLease> slot();               // 不变:默认 machine 的租约
Optional<SlotLease> slot(String machine); // 新增
Set<String> machines();                   // 新增:已启动的 machine 名("default" 在内),名序
```

`MachineSpec.Leased` 的全部字段(`store`、`bits`、`ttl`、`tolerance`、`preferredSlot`、`ownerKey`、`acquireTimeout`(§6)、`slotOffset`(§2.1.1)、`namespace`)与
`MachineSpec.Static` 的全部字段(`bits`、`slot`、`stateFile`、`ahead`、`notBefore`、`namespace`)对默认 machine 和命名 machine 一样可用。

### 2.1.1 `slotOffset`:与 4.x 机器号错开的节点区间(滚动迁移)

```java
// MachineSpec.Leased
int slotOffset();      // 默认 0;ID 中的节点号 = slotOffset + slot
```

- 存储中的槽位编号不变,仍是 `[0, 2^bits)`;`SlotLease.slot()` 仍是存储槽位。偏移只在客户端打包 ID 时加上:`TimeSequencer` 用 `slotOffset + binding.slot()` 作为节点号,
  `describe()` 的时间型生成器增加 `node` 键(= 实际写进 ID 的节点号)。
- 用途:4.x 与 5.0 在同一个(全局)命名空间里**同时在线**。4.x 的机器号都 `< slotOffset`,5.0 的节点号都 `≥ slotOffset`,两边的 ID 节点段不相交,
  即使时间段重叠也不会重号,于是各服务可以逐个切换(§2.7)。4.x 的机器号记录(`cosid_machine` 等)与 5.0 的槽位(`cosid_slot` 等)在存储中是不同的表/键,可以共存。
- 偏移一旦用过就**永远不改**:改小会让新槽位落进 4.x 或旧 5.0 已用过的节点号。4.x 全部下线之后也保留原值(代价只是节点空间的低段不再使用)。
- 只给 `Leased`。`Static` 的槽位由用户直接指定,需要错开时直接选更大的 `slot`,不需要偏移。

### 2.2 校验(`RuntimePlan`)

1. machine 名:非空,不含 `/`,不能是保留名 `default`(它指默认 machine)。
2. 生成器的 `machine` 必须指向存在的 machine;不存在报 `generators.<g>.machine: no machine [x]`。默认 machine 缺失而有时间型生成器选了它,沿用现有报错。
3. **一个运行时里,所有被启用的 machine 的有效命名空间两两不同**(Leased 与 Static 一起比较)。理由:同一命名空间两个租约是同一进程占两个槽位,
   没有用处;而且命名空间不同,listener、指标、健康检查就能继续只用命名空间区分 machine,`GeneratorListener` 的签名不必改。
   两个 Static machine 的 `stateFile` 也不能相同。
4. 每个 machine 按现有规则校验(bits、ttl、tolerance、slot 范围、ownerKey),错误前缀为 `machine:` 或 `machines.<name>:`。
5. `checkNodeBits` 按生成器所选 machine:`slotOffset + 2^bits ≤ 2^nodeBits`,**对引用该 machine 的每一个时间型生成器**都要成立(`slotOffset = 0` 时退化为现有的 `bits ≤ nodeBits`)。
   `slotOffset ≥ 0`;`slotOffset + 2^bits` 用 `long` 计算防止溢出。
6. **只启动被启用的时间型生成器引用到的 machine**,只打开这些 machine 用到的存储(与现有"只打开用到的存储"一致)。未被引用的命名 machine 在启动日志里以 INFO 列出,不报错
   (同一份配置可被多个服务共用,其中有的服务没有 cosid80)。
7. 存储端:命名空间元数据由 `{limit, tolerance}` 扩为 `{limit, tolerance, slotOffset}`,三者任一不一致时 acquire 抛 `NamespaceMismatchException`。
   共享一个全局命名空间的**所有服务必须使用相同的 `bits`、`tolerance` 与 `slotOffset`**,文档写明。旧元数据没有 `slotOffset` 时按 0 读取(向前兼容的追加字段)。
   这需要 SPI 与格式的小改动,见 §2.5.1。

### 2.3 启动与关闭顺序

- **启动**:打开存储 → 按"默认 machine,然后命名 machine 按名序"逐个 `start()`(每个阻塞到取得槽位或 `acquireTimeout`)→ 创建生成器 → 返回。
  串行而非并行:顺序确定、失败时回滚简单;每次 acquire 是一次存储往返,串行的启动代价可以忽略。任何一步失败,已取得的槽位按逆序 release(`lastIssued = 0`)。
- **关闭**(05 §4.3 的推广):
  1. 所有时间型生成器进入 `CLOSED`,各自得到精确的 `lastIssued`;
  2. 其他生成器关闭;
  3. **每个 machine 用"选了它的生成器"的 `max(lastIssued)` release**(不是全运行时的最大值,否则一个 machine 的 next_floor 会被另一个 machine 的时间戳无谓抬高);命名 machine 按启动的逆序,最后是默认 machine;
  4. 存储逆序关闭;5. 自建执行器。
- 每个 machine 一个 `LeaseKeeper`(或 `StaticSlot`),各自的 incarnation、续约与退避;共用运行时的执行器与 `TimeSource`。`ownerKey` 的推导(07 §1.1)对所有 machine 相同,
  它们在不同命名空间里,不会互相视为 duplicate owner。

### 2.4 观测

| 面 | 设计 |
|---|---|
| `GeneratorListener` | 签名不变。`onSlot*` 带 `SlotLease`(含 namespace),`onClockSkew`/`onDuplicateOwnerKey` 带 namespace;由 §2.2 第 3 条,namespace 在运行时内唯一确定 machine |
| 指标 | `cosid.machine.*` 保留 `namespace` 标签,**新增 `machine` 标签**(`default` 或名字),由 `CosIdMeterListener` 按 spec 的 namespace → machine 映射填写;`lease.remaining`、`window.remaining`、`clock.skew` 三个 gauge 每个已启动的 machine 一组 |
| 健康检查 | `details` 改为 `{ machines: { default: {namespace, slot, valid, windowRemainingMs, …}, global: {…} } }`;任一已启动的 machine 失效即 DOWN。没有 machine 时 `details` 为空、UP(与现状一致) |
| `describe()` / Actuator | 时间型生成器的 `details` 增加 `machine`(名字)与 `machineNamespace`(R1-core-17 的键表同步) |

### 2.5 cosid-server 与 remote 存储

- `/slots:acquire|renew|release` 的请求本来就带 `namespace`(08 §2),命名 machine 用 `store: remote` 时发的是**自己的**命名空间,协议不变。
- 受限 client token 的 `namespaces` 必须同时包含服务命名空间与全局命名空间(例如 `[order-service, global-ids]`);否则 `403`。server 文档与 08 补一句。
- `max-namespaces-per-client` 只计号段 key 的命名空间(`ServerProperties.java:56`),全局命名空间只用于槽位,不占配额,不变。
- server 自己的 `/ids` 运行时由同一个 Spring 自动配置装配,天然支持 `cosid.machines`,无需另做。

### 2.5.1 `slotOffset` 进入存储元数据(SPI、格式、协议)

| 面 | 变更 |
|---|---|
| SPI | `SlotLeaseStore.acquire(namespace, owner, limit, ttl, tolerance, preferredSlot)` 增加 `int slotOffset` 参数(只用于元数据比对与创建,不影响选槽)。`renew`/`release` 不变 |
| 存储格式(06) | 各后端的命名空间元数据追加 `slot_offset`/`slotOffset`(JDBC `cosid_namespace` 加列,默认 0;Redis `:meta` 追加字段;Mongo `cosid_namespace` 文档加字段;ZK 元数据文本加一行 `slotOffset=`);缺失时读作 0 |
| 协议(08) | `/slots:acquire` 请求体增加可选 `slotOffset`(默认 0);`409 namespace_mismatch` 的说明加上 slotOffset |
| TCK | `SlotLeaseStoreContract` 增加:offset 不一致抛 `NamespaceMismatchException`;无该字段的旧元数据按 0 处理 |
| 迁移 target | 准备命名空间时写入 `slotOffset` |

这些都是 RC 冻结面(存储格式与协议),所以随 PR3 一起做,而不是 GA 后。

### 2.6 Spring 配置

```yaml
cosid:
  namespace: order-service          # D38:省略时取 spring.application.name
  stores:
    cache: { type: redis, timeout: 1s }
  machine:                          # 默认 machine,写法不变
    leased: { store: cache, bits: 10 }
  machines:                         # 新增
    global:
      namespace: global-ids         # 必填:命名 machine 必须写出命名空间(省略即与默认 machine 冲突,见 §2.2 第 3 条)
      leased: { store: cache, bits: 10, ttl: 30s, tolerance: 1s, acquire-timeout: 30s, slot-offset: 1024 }   # slot-offset 默认 0
      # 或 static: { bits: 10, slot: 3, state-file: ..., ahead: 10s }
  generators:
    order: { strategy: segment, store: cache }     # 号段仍在 order-service 命名空间
    trace: { strategy: snowflake }                  # 默认 machine
    token: { strategy: cosid80, machine: global }   # 跨服务唯一的 80 位 ID
```

- 绑定类:`CosIdProperties.machines: Map<String, NamedMachine>`,`NamedMachine(String namespace, Leased leased, @Name("static") Static fixed)`;
  `CosIdProperties.Machine` 增加可选 `namespace`(默认 machine 也可指定,少用)。`Leased`/`Static` 复用现有 record。
- `cosid.generators.<g>.machine`:字符串,只对 `snowflake`/`cosid80` 有效,在号段策略上设置报错。
- 现有配置(只有 `cosid.machine`)语义完全不变。
- `preferred-slot: ${POD_ORDINAL:}` 只宜用在服务自己的 machine:全局命名空间里各服务的 StatefulSet 序号会互相撞,只是退化为非优先,文档提醒。

### 2.7 迁移工具(R1-x-07 的最终修复)

**`config`**:CosIdGenerator 启用且 `cosid.generator.namespace` 与 `cosid.namespace` 不同时,解除 `Legacy4x.unsupported()` 的拒绝,改为写出:

```yaml
cosid.machines.generator:
  namespace: <cosid.generator.namespace>
  leased: { store: <默认 machine 的存储名>, bits: 10, ttl: ..., tolerance: ... }   # ttl/tolerance 同默认 machine
cosid.generators.cosid:            # 或 cosid80(已有命名规则)
  strategy: cosid80
  machine: generator
```

- machine 名固定为 `generator`(对应 `cosid.generator.namespace`)。
- 存储:4.x CosIdGenerator 与 snowflake 用同一个分配器,所以复用默认 machine 的存储。默认 machine 是 static(4.x manual/stateful-set)时,
  4.x 的 CosIdGenerator 同样是手工机器号,写 `static`,`not-before` 取 W(§4.4 的现有规则);错开节点号用更大的 `slot`。
- `bits`:**默认写 10**,不照抄 4.x `cosid.generator.machine-bit`(默认 20)。5.0 的 `bits` 是租约槽位数(每个命名空间预建 `2^bits` 行),不是节点位宽;20 位意味着 100 万行。
  报告提示:全局命名空间被所有服务共享,**峰值 Pod 总数(含滚动发布的 surge)超过 1024 时要调大**,且受 `slotOffset + 2^bits ≤ 2^nodeBits` 约束;
  共享该命名空间的全部服务必须用相同的 `bits`、`tolerance`、`slotOffset`。
- `slot-offset`:取自 `apply-result.json`(与 bits/tolerance 一样由 `config` 从结果读取,D-N2),见下文;没有结果时写 0 并报告。
- 节点位宽 `node-bits` 仍按 4.x `machine-bit` 写出(§1.4)。

**`plan`/`apply`**:共享命名空间是多个服务共用的,只准备一次(第二次会覆盖已经发出的 5.0 槽位)。

1. **共享命名空间单独迁移一次**:`plan/apply --namespace <global>`,该命名空间只有机器号记录、没有号段 key,工具只做 M2。
   - `plan` 从 4.x 机器号记录读出该命名空间**出现过的最大机器号** `M`(含已过期与 stable 的记录),默认 `slotOffset = 2^nodeBits − 2^bits`
     (5.0 槽位放在节点空间顶端,4.x 余量最大),报告并在 `plan.json` 记录 `headroom = offset − (M + 1)`,低于 `2^bits` 时升级为醒目警告;
     4.x 硬上限 `2^(cosid.generator.machine-bit)` 等于 `2^nodeBits`,所以 `guaranteed` 实际为 false(仅当 offset ≥ 该上限时为 true)。并校验 `offset > M` 与 `slotOffset + 2^bits ≤ 2^nodeBits`(nodeBits 取 `--layout-config` 中 4.x CosIdGenerator 的 `machine-bit`)。
   - 报告给出两种做法,由 `apply --slot-offset` 选择(默认取推荐值):
     - **滚动迁移**(`slotOffset > 0`,推荐):不要求 4.x 停止,不做静止观察;各服务逐个把 cosid80 切到 5.0,4.x 与 5.0 同时在线。
     - **一次性切换**(`--slot-offset 0`):沿用原规则,静止观察覆盖所有共享它的服务,它们必须都已停止。
   - 两种做法都写入 `W` 并预建槽位 `next_floor = W`(有偏移时 W 不是唯一性所必需的,作为纵深防护保留);元数据写入 `{limit, tolerance, slotOffset}`;结果写进 `apply-result.json`。
2. **服务自己的迁移**(带 `--layout-config`,其中有独立的 `cosid.generator.namespace`):`apply` 检查目标存储中该共享命名空间已准备好,元数据的 `limit`/`tolerance`/`slotOffset` 与生成配置一致,
   否则拒绝并提示先做第 1 步。一次性切换时,共享命名空间的记录照旧计入本服务的 `T4`;滚动迁移时不计入(4.x 还在发号,T4 没有意义)。
3. `verify` 对共享命名空间报告元数据与 `next_floor ≥ W`;滚动迁移时另报告 4.x 记录中的最大机器号,一旦 `≥ slotOffset` 即报错(不变式被破坏)。

**滚动期间的不变式**:`所有 4.x 机器号 < slotOffset ≤ 所有 5.0 节点号 < slotOffset + 2^bits ≤ 2^nodeBits`。

- 5.0 一侧由 §2.2 第 5 条与元数据比对保证。
- 4.x 一侧靠操作约束:4.x 分配器按"最小空闲机器号"分配,所以 4.x 的机器号上限约等于 4.x 同时在线实例的峰值。迁移期间**不能扩容 4.x**到 `slotOffset` 个以上实例(含 surge);
  默认偏移把余量做到最大,报告给出 headroom,`verify` 负责事后检查。
- M2 的变化:有偏移时,4.x 与 5.0 时间段重叠也不会重号,所以不要求停机、不做静止观察;W 照样写入,防止偏移配置错误时退化为重号。

一次性切换(偏移 0)仍然可用,适合 4.x 实例很少或节点位很紧的部署。09 §4.2 同步。

### 2.8 测试要点

- core:`slotOffset` 写进 ID 的节点号(`IdParts.node = offset + slot`);`offset + 2^bits > 2^nodeBits` 被拒(对每个引用该 machine 的生成器);两个 machine 各自取槽、各自续约;同一命名空间被两个 machine 使用被拒;未引用的 machine 不启动、其存储不打开;生成器引用不存在的 machine 被拒;
  node bits 按所选 machine 校验;启动中第二个 machine 失败时第一个被 release;关闭时每个 machine 的 `lastIssued` 只取自自己的生成器;`slot(name)`/`machines()`。
- store/tck:元数据 slotOffset 不一致抛 `NamespaceMismatchException`,缺字段按 0(全部后端 + remote);remote:命名 machine 经 remote 存储用自己的命名空间(server 集成测试,受限 token 缺少全局命名空间时 403)。
- spring:`cosid.machines` 绑定与校验;指标带 `machine` 标签、每个 machine 一组 gauge;健康检查 `machines` 结构、任一失效即 DOWN。
- migration:`plan` 从 4.x 记录算出推荐 offset;滚动迁移不要求 4.x 停止;`verify` 发现 4.x 机器号 ≥ offset 报错;`config` 从结果写出 `slot-offset`;`config` 写出命名 machine 并启动通过;共享命名空间未准备时 `apply` 拒绝;单独迁移共享命名空间(无号段 key)成功;`Legacy4x.unsupported()` 删除后的回归。

---

## 3. D38:`cosid.namespace` 默认取 `spring.application.name`

- `CosIdProperties.namespace` 去掉 `@DefaultValue("cosid")`(`null` 表示未设置)。
- 解析放在 `CosIdAutoConfiguration`(`CosIdSpecs` 保持纯函数,接收解析后的命名空间):`cosid.namespace` → `spring.application.name` → `"cosid"`。
  走到第三步时启动 WARN 一次:"`cosid.namespace` and `spring.application.name` are both unset: using [cosid]; two such applications share their segment keys and slots"。
- 取到的应用名同样经过 R1-spi-02 的可移植命名空间校验(不合法时报错并提示显式设置 `cosid.namespace`)。
- 不影响:`CosIdGeneratorRegistrar`(Bean 名与命名空间无关);cosid-server(`application.yaml:35` 已是 `${spring.application.name}`);迁移 `config` 始终显式写出 `cosid.namespace`。
- 风险提示(写进升级说明):5.0 alpha 用户没写 `cosid.namespace` 但设置了应用名的,升级后命名空间从 `cosid` 变成应用名,号段从 `initialOffset` 重新开始。alpha 不做兼容承诺(D33),只在 release notes 提醒。
- 测试:三种来源各一例;WARN 只在第三种出现;应用名不合法时报错。

---

## 4. R1-core-01:公开值类型的演进策略

### 4.1 方案比较

record 加分量会同时改掉规范构造器(源码与二进制都不兼容)和记录模式的元数(`case Leased(var a, …)` 编译失败)。可选做法:

| | A. 保持 record,加重载构造器 | B. record + 静态工厂/builder,声明"规范构造器不属于兼容承诺" | **C. final class + builder + `with*`(推荐)** |
|---|---|---|---|
| 加字段的兼容性 | 构造调用:保留旧元数的重载即兼容,但重载随版本累积(`RuntimeOptions` 已有一个 8 参数重载);记录模式:不兼容 | 只是约定:规范构造器仍是 public,IDE 会补全,用户照样会用;记录模式同样不兼容 | 完全兼容:新字段进 builder 并有默认值 |
| 模式匹配 | 类型模式与记录模式都可用 | 同 A | 类型模式可用(`case MachineSpec.Leased l`,sealed 穷举不受影响);**没有记录模式** |
| 等值语义 | 自动 | 自动 | 手写 `equals/hashCode/toString`(输出沿用 record 的 `Type[a=…, b=…]` 格式) |
| 序列化 | 均不涉及:这些类型不实现 `Serializable`,也不直接出现在 HTTP/JSON 中(server 用自己的 body 类型,`Bodies.java`) | 同 | 同 |
| Spring 绑定 | 不涉及:Spring 绑定的是 `CosIdProperties`,`CosIdSpecs` 再转成 core 类型 | 同 | 同 |
| 改动量 | 小 | 小 | 中:约 230 处 `new`(主要在测试,§10 PR1 一次性机械替换),仓库中没有对这些类型使用记录模式 |

结论:**C**。理由:冻结后加字段是必然的(R1-core-01 已举出 `RuntimeOptions.countGenerated`,本设计又要给 `Leased` 加 `acquireTimeout`/`namespace`、给 `CosId80` 加 `layout`/`machine`);
记录模式对配置类型几乎没有使用价值(没有人会解构 9 个分量的 `RuntimeOptions`);B 的"约定"挡不住用户调用 public 构造器。

### 4.2 逐类型结论

**保留 record(封闭的值,规范构造器与记录模式都属于兼容承诺)**:

| 类型 | 理由 |
|---|---|
| `Range(start, end)`、`IdParts(time, node, seq)`、`GroupedId`、`Owner(key, incarnation)` | 数学上封闭的值 |
| `Layout` | 位布局的定义就是这 6 个分量;新预设是常量,不是字段 |
| `ClockRegressionPolicy(borrowUpTo, waitUpTo)` | 两段阈值是完整语义;新的回拨策略应是新类型 |
| `AcquireResult.Exhausted(earliestExpiresAt)` | 封闭 |
| `CodecSpec.*`、`PartsCodecSpec.*`、`GroupedCodecSpec.*`、`GeneratorSpec.Disabled` | 各自 1:1 对应一个 codec 工厂方法的参数;codec 的新能力以新变体表达(§4.3)。`PartsCodecSpec.Friendly` 在 RC 前加 `padStart`(§1.1)后冻结 |

**改为 final class + builder**:

| 类型 | 必填(进 `builder(...)` 参数) | 可选(builder 方法,括号内为默认值) | `with*` |
|---|---|---|---|
| `RuntimeSpec` | `namespace` | `store(name, StoreSpec)`、`machine(MachineSpec)`、`machine(name, MachineSpec)`、`generator(name, GeneratorSpec)`、`removeGenerator(name)` | `toBuilder()`(供 `RuntimeSpecCustomizer` 修改) |
| `StoreSpec` | `type` | `timeout`(2s)、`option(k, v)`/`options(Map)` | — |
| `MachineSpec.Leased` | `store` | `bits`(10)、`ttl`(30s)、`tolerance`(1s)、`acquireTimeout`(= ttl,§6)、`slotOffset`(0,§2.1.1)、`preferredSlot`、`ownerKey`、`namespace` | `withNamespace` |
| `MachineSpec.Static` | `slot` | `bits`(10)、`stateFile`、`ahead`(10s)、`notBefore`、`namespace` | `withNamespace` |
| `GeneratorSpec.Snowflake` | — | `layout`(`SNOWFLAKE_MS`)、`regression`(DEFAULT)、`codec(List)`、`machine` | `withMachine` |
| `GeneratorSpec.CosId80` | — | `layout`(`COSID_80`)、`regression`、`codec`(`Radix(62)`)、`machine` | `withMachine` |
| `GeneratorSpec.Segment` | `store` | `batch`(`BatchPolicy.DEFAULT`)、`codec` | — |
| `GeneratorSpec.PeriodicSegment` | `store`、`period`、`zone` | `batch`、`periodic`、`codec` | — |
| `GeneratorSpec.Custom` / `Instance`(§5,新) | `reference` / `ids` | `codec` | — |
| `BatchPolicy` | — | `step`(100)、`initialOffset`(1)、`lookahead`(1s)、`maxBatch`(`defaultMaxBatch(step)`)、`reserveTimeout`(3s)、`rangeTtl` | `withStep`(未显式设 maxBatch 时按新 step 重算默认值)、`withLookahead` |
| `PeriodicPolicy` | — | `prewarm`(1min)、`sealAfter` | — |
| `RuntimeOptions` | — | `time`、`executor`、`listener`、`storeProvider(...)`、`client(name, obj)`、`hostResolver`、`port`、`codecs`、`generators`(§5)、`countGenerated` | `toBuilder()`;删除现有 8 参数重载与 `withCountGenerated`(由 builder 取代) |
| `StoreConfig`(SPI 入参) | `name`、`type` | `timeout`(必须为正,R1-spi-04)、`options`、`client` | — ;另加 `option(String key)`/`option(key, default)` 读取辅助(R1-spi-01 的规范化可放在这里) |
| `SlotLease`(SPI 值) | — | 全部 8 个字段;`builder()` 与 `toBuilder()` | — |
| `AcquireResult.Acquired`(SPI 值) | `lease`、`reason` | `duplicateOwnerKey`(false) | — |
| `GeneratorInfo` | `name`、`strategy` | `store`("")、`details` | — ;键表见 §7 R1-core-17 |

共同约定(写进各类型 javadoc 与 03 §6):

- 访问器沿用 record 风格(`step()`、`ttl()`),读取方的源码不变。可选字段访问器返回 `Optional`,builder 方法收普通值,`null` 一律拒绝。
- `build()` 做全部字段校验(即现在紧凑构造器里的检查);跨字段/跨对象的校验仍在 `RuntimePlan`。
- 值等价:`equals/hashCode` 覆盖全部字段;`toString` 与 record 相同格式。类不可变,集合字段 `copyOf`。
- 不提供 public 构造器;builder 是唯一的构造方式。
- **GA 后加字段的规则**:只加到 builder 的可选方法,必须有默认值,默认值等于加字段之前的行为;新增同名访问器;`equals` 纳入新字段。从不往 `builder(...)` 的必填参数里加东西。
- **具体生成器与租约类的公开构造器**(R2-core-03,用户决定:保留,只定约定):`TimeBasedGenerator`、`SegmentGenerator`、`PeriodicSegmentGenerator`、`LeaseKeeper`、`StaticSlot`、`UuidV7Generator` 的公开构造器是 `CosIdRuntime` 的装配入口,签名保持不变,属于兼容承诺。**以后新参数只走 builder 或静态工厂,不再增加位置参数重载**(现有 5 对重载保留)。`StoreOptions.resolve(store, type, known, options, problems)` 与 SPI `SlotLeaseStore.acquire` 同此:签名不变,新参数走新方法或值类型。各类的 javadoc 写明这一约定。

### 4.3 sealed 接口的演进(与 R1-core-02 一起定)

Java 的 `switch` 对 sealed 接口做穷举检查,GA 后加变体会让用户没有 `default` 分支的 `switch` 编译失败(已编译的代码在运行时抛 `MatchException`)。逐个定:

| 接口 | 结论 |
|---|---|
| `AcquireResult` | **封闭**:获取要么成功要么耗尽;store 实现方与 core 都穷举它 |
| `GeneratorSpec`、`MachineSpec`、`CodecSpec`、`PartsCodecSpec`、`GroupedCodecSpec` | **允许在次版本加变体**。javadoc 写明"这是规格的封闭集合的当前快照,次版本可能新增变体;对它做 `switch` 时请保留 `default` 分支"。用户极少对规格做 `switch`(他们构造规格,由运行时消费);有了 `GeneratorSpec.Custom/Instance`、`CodecSpec.Custom/Instance`,新能力多数不需要新变体 |

---

## 5. R1-core-02:自定义生成器

**结论:加两个变体**,与 `CodecSpec.Custom`/`Instance` 对称:

```java
// GeneratorSpec
final class Custom implements GeneratorSpec {     // 按名引用,配置用
    public static Builder builder(String reference);   // codec 可选,规则同 Segment(无布局:不能用 friendly 与 ID_TIME 的 date-prefix)
    String reference(); List<CodecSpec> codec();
}
final class Instance implements GeneratorSpec {   // 代码里直接给实例
    public static Builder builder(IdGenerator ids);
    IdGenerator ids(); List<CodecSpec> codec();
}

// RuntimeOptions
Function<String, Optional<IdGenerator>> generators();   // 解析 Custom 的引用;默认总是 empty(找不到即启动失败)
```

- 语义:`long` 生成器,运行时**不拥有**它:不关闭、不续约、不预取;它照样参与 `generator(name)`、`stringGenerator(name)`、`get(name, type)`、`IdFiller`、`describe()`(`strategy = "custom"`,`details = {class}`)、计数(`countGenerated`)与 `Disabled` 包装。
- 不需要存储也不需要 machine;生成器名的规则不变。
- 只覆盖 `long` 生成器。4.x 里 `UuidGenerator` 这类只有文本的 share 很少见,5.0 直接用 `IdGenerators.uuid()`,11 写明。
- Spring:

  ```yaml
  cosid.generators.legacy: { strategy: custom, ref: myLegacyIdGenerator, codec: [ { radix: { radix: 62 } } ] }
  ```

  `RuntimeOptions.generators` 在 Spring 中是 `name -> beanFactory.getBean(name, IdGenerator.class)`(与 `codecs` 的 Bean 解析相同,`CosIdAutoConfiguration.java:87`)。
  注册器按 `segment` 的 Bean 类型登记 `legacy`。校验:`ref` 必填;`ref` 不能等于任何生成器名(否则与 CosId 注册的同名 Bean 冲突);被引用的 Bean 不能依赖 `CosIdRuntime`(循环依赖,文档写明)。
  代码方式:`RuntimeSpecCustomizer` 里 `spec.toBuilder().generator("legacy", GeneratorSpec.Instance.builder(ids).build()).build()`。
- 4.x 对照:`IdGeneratorProvider.set(name, gen)` → `Custom`/`Instance`;`setShare(gen)` → 名为 `default` 的 `Custom`/`Instance`。11 §1 的 `IdGeneratorProvider` 行改为引用新测试。
- sealed 兼容:变体在 RC 前加入,不存在 GA 后的兼容问题;以后的新变体按 §4.3。
- 测试:`Instance` 与 `Custom` 各自可取 long/text、可被 `IdFiller` 填充、出现在 `describe()`、可被禁用、关闭运行时不关闭它;引用不存在的 Bean/名字启动失败;codec 里放 friendly 被拒;Spring `ref` 与生成器名相同被拒。

---

## 6. R1-core-03:`acquireTimeout`

**结论:加**(不删)。

- `MachineSpec.Leased.acquireTimeout`:默认等于 `ttl`(即现在的行为,`RuntimeParts.java:143-144` 传 `leased.ttl()`);`≥ 0`,`0` 表示只尝试一次,耗尽即启动失败。
- Spring:`cosid.machine.leased.acquire-timeout`、`cosid.machines.<name>.leased.acquire-timeout`;未设置时取 `ttl`(不是写死 30s:改了 ttl 的用户默认跟随)。
- 理由:K8s 滚动发布时新 Pod 先启动、旧 Pod 后退出,槽位紧张时启动会等待;用户需要让这个等待与 `startupProbe` 对齐(更短以快速失败重启,或更长以等旧 Pod 释放)。
  builder 化之后加字段没有成本,删掉反而要在 05 写明一个用户无法调整的阻塞上限。
- 与 4.x:4.x 没有对应项(获取失败立即抛异常),5.0 默认更宽容;11 不需要新行。
- 测试:`LeaseKeeperTest` 已有超时用例,补 `0` 与自定义值;Spring 默认跟随 ttl。
- 同一批新增 `Leased` 字段:`slotOffset`(默认 0,`cosid.machine.leased.slot-offset`、`cosid.machines.<name>.leased.slot-offset`),设计见 §2.1.1;随 PR3 实现(它需要 SPI 与存储格式改动)。

---

## 7. 其余触及冻结面的条目

| 编号 | 结论 | 设计 |
|---|---|---|
| R1-x-02 静态入口 | **保持现状**(方案 A) | 只做 R1-x-01 的文档;官方 holder 是新增类,GA 后可在次版本加入,不阻塞 RC |
| R1-x-05 / R1-spring-04 | RC 前改 | 并入 §1 |
| R1-core-11 `uncertain` 的异常 | **RC 前改** | 抛 `IdOverflowException`;`IdOverflowException` 增加 `public IdOverflowException(long id, String message)`,原构造器保留("overflows int") |
| R1-core-12 时间位溢出 | **RC 前改** | 新增 `TimeOverflowException extends CosIdException`(对应 4.x `TimestampOverflowException`),时间超出 `timeBits` 时抛;epoch 在未来改由 `RuntimePlan` 在启动时拒绝(按 `TimeSource` 的当前时间),运行中时钟回到 epoch 之前按回拨处理(`ClockBackwardsException`)。11 §1 加一行 |
| R1-core-17 `describe()` 的键 | **RC 前改(文档 + 测试)** | `GeneratorInfo` javadoc 列出各策略的键,承诺"已列出的键不改名、不删;次版本可加键;值只用 String/Number/Boolean";一个测试锁定键集合。不加常量类。键表:snowflake/cosid80 `epoch unit timeBits nodeBits seqBits lastIssuedMillis slot floor ceiling slotValid machine machineNamespace`;segment `key step maxBatch lookahead reserveTimeout batch held`;periodic-segment 另加 `zone prewarm sealAfter period`;custom `class`;任一策略 `generated`(计数开启时)、`disabled` |
| R1-int-01 `CosIdRuntime.text(name)` | RC 前加(建议,不阻塞) | `Supplier<String> text(String name)`:任意策略的文本 ID;4 份包内 `TextIds` 与端点的策略名分支删掉。加方法本身是增量,但顺手在 PR3 做掉成本最低 |
| R1-spring-03 未知属性 | **RC 前改** | 绑定 `cosid` 时用 `NoUnboundElementsBindHandler`,`cosid.stores.*.options` 除外(由 R1-spi-01 的 provider 校验);拼错的键启动失败。RC 后再收紧会让已能启动的配置失败 |
| R1-spring-05 健康检查名 | **RC 前改** | Bean 名改为 `cosidHealthIndicator`,路径 `/actuator/health/cosid`;同步 server 的 readiness group 与文档 |
| R1-spring-06 指标基数 | **RC 前改** | `cosid.segment.reserve`、`cosid.segment.refill.failures` 去掉 `key` 标签,只留 `name`(普通号段的 key 等于 `namespace/name`,本来就冗余;周期号段每期一个 key 会无界增长) |
| `cosid.metrics.generated`(§6.9) | **RC 前改** | 改为 `cosid.metrics.generated.enabled`(默认 false),与 4.x 同名;迁移 `config` 直接照抄。布尔值以后无法加子属性 |
| R1-spi-04 / R1-spi-05 `StoreConfig` | RC 前改 | 并入 §4(builder,`timeout` 必须为正,删去 0 = 无超时的分支与 JDBC README 中的说法) |
| R1-spi-01 / 02 / 03、R1-spring-01、R1-jdbc-01 / 07、R1-redis-01、R1-mongo-01、R1-remote-01、R1-tck-01 | 归 **WP-store-contract** | 不在本设计范围;与本设计的交点只有 `StoreConfig`(§4.2)。WP-store-contract 在 `StoreConfig` builder 之上实现选项键规范化与未知键校验。**已实现(R1-spi-01/02/04、R1-spring-01)**:`StoreProvider.options()` + `StoreOptions`(忽略大小写、`-`、`_` 对应声明的键,未知键启动失败);`StoreKeys` 定义可移植的命名空间、名字、range key 与 owner key(06 §1),`RuntimePlan` 启动时校验;`StoreConfig.timeout` 各后端一律为正 |
| R1-server-01 | 已完成(#1365) | — |

---

## 8. 变更清单(按模块)

**core**

| 类型 | 变更 |
|---|---|
| `runtime.RuntimeSpec` | record → final class + builder;新增 `machines()`、`DEFAULT_MACHINE` |
| `runtime.StoreSpec`、`runtime.RuntimeOptions` | → final class + builder;`RuntimeOptions` 新增 `generators()`,删 8 参数重载与 `withCountGenerated` |
| `runtime.MachineSpec` | `Leased`/`Static` → final class + builder;接口新增 `namespace()`;`Leased` 新增 `acquireTimeout()`、`slotOffset()` |
| `runtime.GeneratorSpec` | 4 个策略变体 → final class + builder;`Snowflake`/`CosId80` 新增 `machine()`;`CosId80` 新增 `layout()`;新增 `Custom`、`Instance`;`Disabled` 保留 record |
| `runtime.PartsCodecSpec.Friendly` | 新增 `padStart`(record 分量 + 单参构造器) |
| `runtime.CosIdRuntime` | 新增 `slot(String)`、`machines()`、`text(String)` |
| `segment.BatchPolicy`、`segment.PeriodicPolicy` | → final class + builder + `with*` |
| `store.StoreConfig` | → final class + builder;`timeout` 必须为正 |
| `lease.SlotLease`、`lease.AcquireResult.Acquired` | → final class + builder |
| `observe.GeneratorInfo` | → final class + builder;javadoc 键表 |
| `lease.SlotLeaseStore.acquire` | 增加 `int slotOffset` 参数(§2.5.1) |
| `codec.PartsCodec` | 新增 `friendly(Layout, ZoneId, boolean)` |
| `IdOverflowException` | 新增 `(long, String)` 构造器 |
| `TimeOverflowException` | 新增 |
| `IdGenerators.uncertain` | 抛 `IdOverflowException` |
| internal | `RuntimePlan`(D36/D37 校验、epoch 在未来)、`RuntimeParts`(多 machine、按 machine release、Custom/Instance)、`GeneratorEntry`(键)、`TimeSequencer`(`TimeOverflowException`) |

**spring/autoconfigure**

| 键或类型 | 变更 |
|---|---|
| `cosid.namespace` | 默认取 `spring.application.name`,再回退 `cosid` + WARN |
| `cosid.machine.namespace` | 新增(可选) |
| `cosid.machine.leased.acquire-timeout` | 新增,默认 = ttl |
| `cosid.machine.leased.slot-offset` | 新增,默认 0 |
| `cosid.machines.<name>.{namespace, leased.*, static.*}` | 新增 |
| `cosid.generators.<g>.machine` | 新增(snowflake/cosid80) |
| `cosid.generators.<g>.layout` | 默认值按策略(`SNOWFLAKE_MS` / `COSID_80`) |
| cosid80 的 `time-bits`/`node-bits`/`seq-bits`/`seq-reset-threshold` | 生效;`epoch`/`unit`/`radix.width`/`radix.pad-start` 报错 |
| `cosid.generators.<g>.codec[0].friendly.pad-start` | 对 cosid80 生效 |
| `cosid.generators.<g>.strategy: custom` + `ref` | 新增 |
| `cosid.metrics.generated` | → `cosid.metrics.generated.enabled` |
| 未知 `cosid.*` 键 | 启动失败(`options` 除外) |
| 健康检查 | 贡献者名 `cosid`;`details.machines` 结构 |
| 指标 | `cosid.machine.*` 增加 `machine` 标签;`cosid.segment.reserve`/`refill.failures` 去掉 `key` 标签 |
| `CosIdProperties` | javadoc 声明是绑定类,不属于 API 兼容承诺 |

**migration**:`plan` 计算推荐 `slotOffset`,`apply --slot-offset`,滚动迁移不做静止观察,`verify` 检查 4.x 机器号 < offset;`config` 写出 cosid80 位宽/阈值/`pad-start`,写出 `cosid.machines.generator`(含 `slot-offset`),删除 `Legacy4x.unsupported()` 的拒绝;`apply` 检查共享命名空间已准备;`cosid.metrics.generated.enabled` 照抄。

**server**:`/slots:acquire` 请求体增加可选 `slotOffset`(§2.5.1);文档补受限 token 的全局命名空间、健康检查路径。

**store/***:随 `StoreConfig`、`SlotLease`、`AcquireResult.Acquired` 的 builder 机械替换构造调用;命名空间元数据追加 `slotOffset`(06 同步,JDBC 五份 schema 加列);TCK 增加比对用例。

---

## 9. 文档改动(随各 PR)

- `documentation/docs/{en,zh}/reference/config/*`:新键与默认值;删"`cosid80` 始终使用 `Layout.COSID_80`"。
- 概念页新增"跨服务全局唯一"(R1-x-08):命名 machine、全局命名空间的 bits/tolerance 必须一致、cosid-server `/ids` 作为多语言/低频补充。
- 迁移指南:`cosid.generator.*` 全部键的对照(§1.3)、共享命名空间的一次性迁移步骤(§2.7)、`cosid.metrics.generated.enabled`。
- release notes:D38 对 alpha 用户的影响、`/health/cosid` 改名、指标标签变化、record → builder。

## 10. 推荐的实现拆分(一次一个 PR,顺序依赖)

| PR | 内容 | 依赖 | 状态 |
|---|---|---|---|
| PR1 `refactor(core)!: evolvable value types` | §4:类型改为 final class + builder,全仓构造调用机械替换;`StoreConfig` timeout 为正;不加任何新字段、不改行为 | — | 已合入 #1372(`ba294a5f`) |
| PR2 `feat(core)!: cosid80 layout, acquire timeout, custom generators` | §1.1、§5、§6、§7 的 R1-core-11/12/17 | PR1 | 已合入 #1373(`7225296a`) |
| PR3 `feat(core)!: named machines and slot offset` | §2.1–2.4 的 core 部分;`Leased.slotOffset` 与 §2.5.1(SPI 参数、各后端元数据、remote 协议、TCK、06/08);`CosIdRuntime.slot(name)/machines()/text(name)` | PR2 | 已合入 #1374(`a6e91a88`) |
| PR4 `feat(spring)!: freeze the configuration` | §1.2、§3、§5/§6 的 Spring 键、§7 的 R1-spring-03/05/06 与 `metrics.generated.enabled`、§2.6 的 `cosid.machines` 与指标/健康检查 | PR3 | 已合入 #1375(`8144607a`) |
| PR5 `feat(migration): named machine for cosid.generator.namespace` | §1.4、§2.7;09 同步 | PR4 | 已合入 #1376(`385d363e`) |
| PR6 `docs: API freeze` | §9(中英文);11 的新行与测试链接 | PR5 | 本 PR |

PR4 偏大时可拆成 4a(D36/D38/R1-core-02/03 的键与 R1-spring-03/05/06)与 4b(`cosid.machines`、指标与健康检查)。WP-store-contract 可在 PR1 之后与 PR2–PR5 交替进行(只依赖 `StoreConfig` builder)。

## 11. 决策表增改(供协调会话转录进 README)

| 编号 | 决策 | 状态 |
|---|---|---|
| D36(补充) | cosid80 的 `epoch`/`unit` 固定(Unix、毫秒),设置即报错;Spring 的 `layout` 默认值按策略;radix 的 `width`/`pad-start` 在 cosid80 上报错;迁移 `config` 写出 4.x 位宽、阈值与 `pad-start` | 待确认 |
| D37(补充) | 一个运行时内各 machine 的有效命名空间两两不同;machine 名 `default` 保留;只启动被引用的 machine;串行启动、逆序释放,各 machine 按自己的生成器取 `lastIssued`;指标加 `machine` 标签,健康检查按 machine 汇总;`Leased.slotOffset`(默认 0,节点号 = offset + slot,`offset + 2^bits ≤ 2^nodeBits`,记入命名空间元数据 `{limit, tolerance, slotOffset}`,不一致即拒绝,RC 前随 PR3 实现);迁移把 `cosid.generator.namespace` 写成命名 machine `generator`(`bits: 10`,`slot-offset` 默认 `2^nodeBits − 2^bits`,即 5.0 槽位放在节点空间顶端,`plan` 报告并记录 headroom `offset − (M + 1)`),共享命名空间单独迁移一次,默认滚动迁移(4.x 与 5.0 共存),可选一次性切换(offset 0) | 已定(协调会话 2026-10-28) |
| D38(补充) | 解析在自动配置中完成,应用名同样校验命名空间语法 | 待确认 |
| D39 | 公开值类型的演进:封闭的值(`Range`、`IdParts`、`GroupedId`、`Owner`、`Layout`、`ClockRegressionPolicy`、codec 规格、`Exhausted`、`Disabled`)保留 record;配置与 SPI 值类型改为 final class + builder,GA 后只往 builder 加有默认值的字段;`CosIdProperties` 不属于 API 承诺 | 已定(按推荐,协调会话 2026-10-28) |
| D40 | sealed 规格接口(`GeneratorSpec`、`MachineSpec`、`CodecSpec`、`PartsCodecSpec`、`GroupedCodecSpec`)次版本可加变体,javadoc 要求 `switch` 保留 `default`;`AcquireResult` 封闭 | 已定(按推荐,协调会话 2026-10-28) |
| D41 | `GeneratorSpec.Custom`(按名,Spring Bean)与 `Instance`(实例)覆盖 4.x `IdGeneratorProvider.set`;运行时不拥有其生命周期 | 已定(按推荐,协调会话 2026-10-28) |
| D42 | `MachineSpec.Leased.acquireTimeout`(默认 ttl,`cosid.machine.leased.acquire-timeout`) | 已定(按推荐,协调会话 2026-10-28) |
| D43 | 其余冻结面 MINOR:R1-core-11/12/17、R1-spring-03/05/06、`metrics.generated.enabled` 在 RC 前改;R1-x-02 只做文档;R1-int-01 在 PR3 顺带加入 | 已定(按推荐) |

## 12. 待决定(每项附推荐)

> 2026-10-28 协调会话审查:第 1–4、6、7 项按推荐定稿;第 5 项改为 RC 前纳入 `slotOffset`(下文已更新)。本节保留原始推荐以备查。

1. **公开值类型的形式(D39)**。推荐 **C:final class + builder**(§4.1)。备选 A(保持 record + 重载)改动最小,但 GA 后每加一个字段就多一个重载,且记录模式会断。
2. **sealed 规格接口 GA 后能否加变体(D40)**。推荐**允许,javadoc 声明**;备选是"5.x 内封闭,加变体等 6.0",更严格但会逼着新能力挤进 `Custom`。
3. **自定义生成器(D41)**。推荐**加 `Custom` + `Instance`**;备选是在 11 写成"不提供"(4.x `IdGeneratorProvider.set` 的用户需要改成直接注入自己的 Bean,`@CosId` 无法引用它)。
4. **`acquireTimeout`(D42)**。推荐**加配置项,默认 ttl**;备选是从 05 删除、固定等于 ttl。
5. **共享全局命名空间的迁移方式**。~~原推荐:RC 只支持一次性切换,`slotOffset` 留到 GA 后。~~ **定稿:RC 前纳入 `Leased.slotOffset`(PR3),默认滚动迁移**(§2.1.1、§2.5.1、§2.7):
   K8s 上大量微服务共享全局命名空间时,一起停机切换不现实;offset 让 4.x 机器号与 5.0 节点号不相交,两边可以同时在线、逐个服务切换。一次性切换(offset 0)仍可选。bits 默认 10。
6. **静态入口(R1-x-02)**。推荐**方案 A,只做文档**;GA 后需要时在 `spring/autoconfigure` 加只读 holder(增量)。
7. **`cosid.metrics.generated` 改名**。推荐改为 `cosid.metrics.generated.enabled`(与 4.x 同名);备选保持布尔值,放弃以后加子属性。
