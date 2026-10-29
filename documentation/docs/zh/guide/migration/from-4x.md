# 从 4.x 迁移到 5.0

5.0 不保留任何兼容代码（D1）：API、配置、存储格式都不兼容。唯一的桥梁是离线工具 **`cosid-migration`**。
它读取 4.x 数据，写入 5.0 数据，保证新 ID 永远不会与 4.x 已发出的 ID 冲突，并转换配置。

::: danger 不允许新旧混跑
4.x 与 5.0 **绝不在同一命名空间中运行**，哪怕一瞬间也不行。该命名空间的每个 4.x 实例都在迁移前停止，5.0 在迁移后启动。
这不是滚动升级。
:::

简单的部署也可以不用工具，停机后手工迁移，见[不用工具的停机迁移](#不用工具的停机迁移)。

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
- `--machine-from <4.x 存储>` 与 `--machine-to <5.0 存储>`：4.x 把机器号放在与号段不同的存储里时（`cosid.machine.distributor.type`
  与 `cosid.segment.distributor.type` 不同），两个都要指定；`plan` 会用 `--layout-config` 比对，未声明的拆分会被拒绝。
- `--target-config application-5.yaml`：已经写好的 5.0 配置；布局检查改用它，而不是转换出来的 4.x 布局。
- 账号选项：`--from-option user=…` / `--to-option user=…`，也可以写 `username`（与 5.0 JDBC 存储的名字一致）；
  `password`（或环境变量 `COSID_MIGRATION_FROM_PASSWORD` / `_TO_PASSWORD`）不会写入 `plan.json`。

全部选项及其防护作用见[工具的 README](https://github.com/Ahoo-Wang/CosId/blob/main/migration/README.md#commands)。

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

`apply` 的其他选项：

- `--allow-unmigrated <ns>.<name>`：计划中有 `NOT MIGRATED` 的 key（5.0 读不了的分组模式、含 `/` 的名字）时 `apply` 会中止；
  逐个列出你接受不迁移的 key。它在 4.x 中的值不会带过来，所以不能让任何 5.0 生成器发出同一批 ID。
- `--gap <n>`（0）：每个 5.0 key 从 `lastMaxId + 1` 再加 `n` 开始，是在封存之外自己选的余量。
- `--machine-from` / `--machine-to`、`--target-config`、`--layout-config`：同 `plan`，用于再次检查。

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
每个号段生成器都必须在结果文件中有对应的 key，否则失败；4.x 从未发过号的生成器用 `--allow-new <generator>` 指定。
静态槽位会从结果中得到 `not-before: W`。4.x 的 proxy 配置变为一个 `remote` 存储（`baseUrl`、`token`、`connectTimeout`、`requestTimeout`）。

CosIdGenerator 的键按下表对应（`config` 会写出）：

| 4.x `cosid.generator.*` | 5.0 `cosid.generators.<name>.*`（策略 `cosid80`） |
|---|---|
| `type: radix62` / `radix36` / `friendly` | `codec[0]`：`radix: { radix: 62 }` / `radix: { radix: 36 }` / `friendly` |
| `timestamp-bit`（44）、`machine-bit`（20）、`sequence-bit`（16） | `time-bits`、`node-bits`、`seq-bits`（默认相同） |
| `sequence-reset-threshold`（32767） | `seq-reset-threshold`（默认相同；超出序列范围的 4.x 取值按最大值处理） |
| `zone-id` | `codec[0].friendly.zone` |
| `pad-start`（true，只作用于 friendly 文本） | `codec[0].friendly.pad-start`（true） |
| `namespace` | `machine: generator` + `cosid.machines.generator.namespace`（见[下文](#共享的-cosidgenerator-命名空间)） |

其他键：`cosid.metrics.generated.enabled` 沿用 4.x 的名字。健康检查名为 `cosid`（`/actuator/health/cosid`，开关
`management.health.cosid.enabled`）。5.0 不认识的 `cosid.*` 键会导致启动失败。

### 6. 启动 5.0

使用新配置和 5.0 artifact（见下文）部署。第一个时间型 ID 要等到时钟越过 W，最多约 `sourceSkew`。

### 之后：清理旧周期

运行时周期号段的 key 只封存、不删除（D23）。物理删除过去的周期是一个离线步骤，**必须先停止所有 5.0 实例**：store 正在使用的 key，其节点或行不得被删除。

```bash
java -jar cosid-migration.jar purge --to jdbc:… --namespace shop --before 20250101 --dry-run
java -jar cosid-migration.jar purge --to jdbc:… --namespace shop --before 20250101 --confirm-stopped
```

所有命令的退出码：`0` 完成，`1` 检查失败或命令中止，`2` 用法错误。

## 不用工具的停机迁移

**`cosid-migration` 仍是推荐路径**：它会观察 4.x 是否真的停止、自动封存源数据、为时间型生成器写入 floor，并用 `verify` 核对结果。
如果部署足够简单，也可以不用工具，停机后手工迁移。手工迁移没有这些防护，是否满足上面的 M1–M3 完全由你保证。

### 何时可以手工迁移

只有当该命名空间中的**每一个**生成器都属于下列情况之一时才可以：

- **时间型生成器，布局不变**：Snowflake 或 CosIdGenerator，epoch、时间单位和各段位宽都与 4.x 相同，也就是 4.x 的默认值
  （5.0 预设沿用了它们）。
- **普通（非周期）号段**，包括 share（5.0 的 `default` 生成器）。

以下情况**必须用工具**：

- 周期（分组）号段。手工迁移靠 `initial-offset` 越过 4.x 的值，而 `initial-offset` 是**每个新 key** 的起点；周期生成器每个新周期都是一个新 key，
  于是以后每个周期都会从这个大偏移开始，例如按天从 1 重置的单号不再从 1 开始。
- 自定义布局或自定义 epoch，包括秒级 Snowflake 的自定义 epoch。
- key 很多，难以逐个核对；或者你需要静止观察、自动封存、`verify` 这些防护措施。

与工具一样，4.x 与 5.0 不能在同一命名空间混跑：所有 4.x 实例先停止，5.0 后启动。

### 时间型生成器

1. **停止所有 4.x 实例**，记下最后一个实例停止的时刻（停机时刻）。遗漏的 4.x 实例与 5.0 实例取到同一机器号时，同一时刻会发出相同的 ID，
   而手工迁移不会替你发现它。
2. **保持停机**，时长不少于所有主机之间的最大时钟偏差，且**至少 1 分钟**。4.x 还允许主机比存储快 10s，估算偏差时要算上。
3. **核对每台主机的 NTP 同步状态**（例如 `chronyc tracking`、`timedatectl`），确认时钟偏差在停机时长之内。
4. 使用 StaticSlot 时，可以额外设置 floor：时钟落后的主机会等到越过它才发号。

   ```yaml
   cosid:
     machine:
       static:
         not-before: 2026-11-01T02:00:10Z   # 停机时刻 + 最大时钟偏差
   ```

5. **启动 5.0**。

原理：布局相同时，时间位于 ID 的最高位，时间更晚的 ID 数值一定更大；停机时长保证 5.0 的第一个时间戳晚于 4.x 发出过的任何时间戳。

::: warning 时钟落后的主机不会被拦下
工具会给新命名空间的每个槽位写入 floor `W`。手工迁移时 5.0 槽位的 floor 为 0：如果某台主机的时钟落后超过停机时长，
它会发出与 4.x 时间范围重叠的 ID，并在机器号相同时重号。租约模式没有可以手工设置的 floor，只能靠停机时长与 NTP 核对。
:::

### 普通号段

1. **停止所有 4.x 实例**。
2. **读出每个号段的 4.x 值 `last_max_id`**。`{ns}` 是 4.x 的 `cosid.namespace`，`{name}` 是号段名，share 号段的名字是 `__share__`：

   | 4.x 后端 | 位置 | 读取 |
   |---|---|---|
   | JDBC | 表 `cosid`，`name = '{ns}.{name}'` | `select last_max_id from cosid where name = 'shop.order';` |
   | Redis | 字符串 `cosid:{ns.name}.adder`（花括号是 key 的一部分） | `GET "cosid:{shop.order}.adder"` |
   | MongoDB | 集合 `cosid`，`_id = '{ns}.{name}'` | `db.cosid.findOne({_id: "shop.order"}).lastMaxId` |
   | ZooKeeper | 节点 `/cosid/{ns}.{name}`，8 字节大端整数 | `zkCli` 不能直接显示，用下面的 `plan` 读取 |

   也可以用只读的 `cosid-migration plan`（操作手册第 2 步）一次读出全部值，报告中每个号段都列出 4.x 的值。
3. **（建议）手工封存**：把 4.x 的值原子地加上一个大数（例如 `2^40` = `1099511627776`），然后取 `last_max_id = 封存后的值 − 2^40`。
   这样封存前的取段都被计入，遗漏的 4.x 实例此后取到的区间也远离 5.0；回滚时 4.x 从更大的值继续，同样不会重号；不封存的话，回滚前要多做一步（见[回滚](#回滚)）。

   | 4.x 后端 | 封存（返回或读回封存后的值） |
   |---|---|
   | JDBC | 在一个事务中：`update cosid set last_max_id = last_max_id + 1099511627776 where name = 'shop.order';` 然后 `select last_max_id from cosid where name = 'shop.order';` |
   | Redis | `INCRBY "cosid:{shop.order}.adder" 1099511627776` |
   | MongoDB | `db.cosid.findOneAndUpdate({_id: "shop.order"}, {$inc: {lastMaxId: NumberLong("1099511627776")}}, {returnDocument: "after"})` |
   | ZooKeeper | 没有可用的 shell 命令；需要封存时请用工具 |

4. **在 5.0 中给对应的生成器设置 `initial-offset = last_max_id + 1`**（见[生成器配置](../../reference/config/generators.md)）。
   share 号段对应 `default` 生成器。

   ```yaml
   cosid:
     namespace: shop
     generators:
       order:
         strategy: segment
         store: main
         initial-offset: 120001   # 4.x 的 last_max_id 为 120000
   ```

5. **启动 5.0**。

原理：4.x 每次取段都把 `last_max_id` 原子地推进一个 step，所以它是所有实例已预留区间的上界；5.0 的第一个 ID 是 `last_max_id + 1`。

::: warning `initial-offset` 只对新 key 生效
它只在 5.0 的 key 首次创建时使用。必须在 5.0 **第一次启动之前**配置好；如果 5.0 已经用这个 key 发过号（例如提前试运行过），
它不再起作用，请先确认 5.0 存储中没有这个 key。
:::

## 回滚

先停止该命名空间的所有 5.0 实例：回退时 4.x 与 5.0 同样不能同时运行。

- **号段，已封存**（`apply` 没有带 `--no-seal-source`，或做过手工封存）：可以直接重新启动 4.x。封存把每个 4.x 的值调高了封存间隔
  （2^40），只要 5.0 在这个 key 上预留的号少于 2^40，4.x 就会在 5.0 发出的一切之上继续。
- **号段，未封存**（`apply --no-seal-source`，或手工迁移时跳过了第 3 步）：**不能直接启动 4.x**。4.x 会从它的 `last_max_id`
  继续取段，而 5.0 已经从 `last_max_id + 1` 起发过号：4.x 会再发一遍这些号。启动 4.x 之前，先把每个 4.x 的值推进到 5.0 在这个
  key 上预留过的一切之上，例如现在补加一次封存间隔，命令与[手工封存](#普通号段)相同：

  | 4.x 后端 | 调高 4.x 的值 |
  |---|---|
  | JDBC | `update cosid set last_max_id = last_max_id + 1099511627776 where name = 'shop.order';` |
  | Redis | `INCRBY "cosid:{shop.order}.adder" 1099511627776` |
  | MongoDB | `db.cosid.updateOne({_id: "shop.order"}, {$inc: {lastMaxId: NumberLong("1099511627776")}})` |
  | ZooKeeper | 没有可用的 shell 命令：在所有 4.x 与 5.0 实例都停止时，用 ZooKeeper 客户端（Curator `setData`）写入节点 `/cosid/shop.order` 的 8 字节大端值 |

  只要 5.0 在这个 key 上预留的号少于 2^40，加 2^40 就够了；否则把 4.x 的值设为不小于 5.0 预留过的最大值。每个迁移过的 key 都要做，
  包括 share（`{ns}.__share__`）和各周期 key。
- **时间型 ID**：等最后一个 5.0 ceiling 过去（租约 `ttl` 加 `tolerance`，默认 31s）之后再重新启动 4.x。

## 共享的 CosIdGenerator 命名空间

- **独立命名空间的 CosIdGenerator 先单独迁移一次，再迁移各服务。** 4.x 部署若把 `cosid.generator.namespace` 设成与 `cosid.namespace`
  不同的命名空间，就是在各服务之间共享这个命名空间里 CosIdGenerator 的机器号。5.0 用命名 machine `cosid.machines.generator` 承接：
  1. 单独迁移共享命名空间一次：`plan --namespace <共享命名空间> --layout-config <任一服务的配置>`，再 `apply`。计划默认把 5.0 的槽位放在 CosIdGenerator
     节点空间的顶端（`--slot-offset` 可改），并报告余量：4.x 还能分出多少机器号才会碰到 5.0 的节点号（4.x 默认位宽下约 104 万个）。
     没有机制阻止 4.x 用完余量，所以计划记录 `guaranteed: false`。有偏移时 4.x 可以继续运行，各服务逐个切换；切换期间不要把 4.x 扩容到偏移以上（`verify` 会检查）。
     `--slot-offset 0` 表示一次性切换，共享它的服务都必须停止。
  2. 照常迁移各服务：在第 1 步完成之前，服务的 `apply` 会拒绝执行；`config` 会写出 `cosid.machines.generator`，并给 cosid80 写 `machine: generator`。
  共享该命名空间的所有服务必须使用相同的 `bits`、`tolerance` 与 `slot-offset`。（5.0.0-alpha.1 会拒绝这样的配置。）

## 静态代码中取号

4.x 代码常在任意位置用 `DefaultIdGeneratorProvider.INSTANCE.getShare().generate()` 取号。5.0 没有全局单例（D17）：每个运行时
都属于启动它的一方。替代做法：

1. **注入生成器。** 名为 `default` 的生成器是主 `IdGenerator` Bean，每个生成器也以自己的名字注册为 Bean
   （`@Qualifier("order") IdGenerator`）。大多数静态调用所在的代码都可以改为接收 Bean。
2. **必须静态调用时**（实体构造器、静态工具类），在应用里自己放一个 holder：上下文启动后赋值，关闭时清空：

```java
@Component
public class Ids implements SmartInitializingSingleton, DisposableBean {
    private static volatile IdGenerator generator;
    private final IdGenerator injected;

    public Ids(IdGenerator generator) {          // 名为 default 的生成器，即主 IdGenerator Bean
        this.injected = generator;
    }

    @Override
    public void afterSingletonsInstantiated() {
        generator = injected;
    }

    @Override
    public void destroy() {
        generator = null;
    }

    public static long next() {
        IdGenerator current = generator;
        if (current == null) {
            throw new IllegalStateException("CosId is not started yet, or its context is closed");
        }
        return current.generate();
    }
}
```

   上下文启动前或关闭后调用会失败，而不是从已关闭的运行时发号。同一个 JVM 里有两个 Spring 上下文时（测试、父子上下文），
   每个都会给 holder 赋值：每个上下文一个 holder，或者不用 holder。
3. **不用 Spring 时**，调用 `CosIdRuntime.start` 的代码持有运行时（并负责关闭它）：把运行时或其生成器传给使用它们的地方。

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
| `IdGeneratorProvider`、`DefaultIdGeneratorProvider.INSTANCE`、share | `CosIdRuntime`、名为 `default` 的生成器（[静态代码中取号](#静态代码中取号)） |
| `IdGeneratorProvider.set(name, generator)` | `strategy: custom` + `ref: <bean>`（`GeneratorSpec.Custom`），或在 `RuntimeSpecCustomizer` 中用 `GeneratorSpec.Instance` |
| `MachineIdDistributor` 系列、guarder、`~/.cosid-machine-state` | `cosid.machine.leased` / `static`（见[槽位租约](../concepts/slot-lease.md)） |
| `IntegerIdGenerator`、`UncertaintyIdGenerator`、`LazyIdGenerator` | `IdGenerators.toInt`、`.uncertain`、`.lazy` |
| `CosIdStoreProvider` | `StoreProvider` |
| `Customize*Properties` | `RuntimeSpecCustomizer` |
| Actuator 对生成器的 DELETE | `cosid.generators.<name>.disabled: true` |
| `guarder.fail-fast: false` | 不提供：它以唯一性换取可用性（P1） |
