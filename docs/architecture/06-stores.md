# 06 存储:SPI、后端实现与 TCK

## 1. SPI

两个原语的契约见 [04-segment.md](04-segment.md) §1 与 [05-machine-lease.md](05-machine-lease.md) §2。发现与创建:

```java
public interface StoreProvider {
    String type();                                   // "jdbc"、"redis"、"mongo"、"zookeeper"、"remote" 或自定义
    default Set<String> options() { return Set.of(); } // 读取的 option 键(R1-spi-01)
    Store open(StoreConfig config);
}

public interface Store extends AutoCloseable {
    RangeStore ranges();                             // 不支持时抛 UnsupportedOperationException
    SlotLeaseStore slots();
}

public final class StoreConfig { /* name, type, timeout(为正), options, client;builder 构造(D39) */ }
```

- `StoreProvider` 通过 `ServiceLoader` 发现;Spring 环境下也可以作为 Bean 提供。**两种来源走同一条创建路径**,由 `CosIdRuntime` 按 `type` 选择,Bean 优先。取代 4.x 的 `CosIdStoreProvider`。
- **option 键(R1-spi-01、R1-spring-01)**:每个 provider 用 `options()` 声明自己读取的键(驼峰原样)。运行时按"忽略大小写、`-` 与 `_`"把配置的键对应到声明的键
  (`init-schema`、`INITSCHEMA`、环境变量绑定出的 `initschema` 都是 `initSchema`;`.` 仍是分隔符),以声明的拼写传给 `open`;不认识的键、或两个键对应同一个键时启动失败并列出可用键。
  对应规则在 `StoreOptions`,自己打开存储的代码(如 cosid-server 的后备存储)也用它。默认 `options()` 为空:读取 option 的第三方 provider 必须声明。
- `client` 用于注入已有连接对象(`DataSource`、Lettuce 连接、`MongoClient`、`CuratorFramework`);为空时 provider 按 `options` 自行创建并负责关闭。
- `timeout` 由后端用原生机制实现(JDBC `queryTimeout`、Redis 命令超时、Mongo `timeoutMS`、Curator 操作超时、HTTP 请求超时),core 不再包装超时线程池。
- **可移植的键(R1-spi-02,冻结面)**:只接受所有后端都能存的键,`RuntimePlan` 启动时校验(`StoreKeys`),换后端不会到运行时才失败:
  - 命名空间:1–100 个 `A-Z a-z 0-9 . _ : -`,以字母或数字开头(JDBC `varchar(100)`、ZooKeeper 节点名、Redis hash tag;Spring 与 cosid-server 用同一规则);
  - 生成器名、machine 名:非空,不含 `/`、空白与 ZooKeeper 路径拒绝的字符(控制字符、代理项、`U+E000..U+F8FF`、`U+FFF0..U+FFFF`),不是 `.` / `..`
    (SQL Server 比较时忽略尾随空格;cosid-server 拒绝 range key 中的空白);
  - range key(`ns/name`、`ns/name/period`)与 owner key:至多 255 个 UTF-16 单位(JDBC `varchar(255)` / SQL Server `nvarchar(255)`),首尾无空白。
  列宽一旦冻结只能放宽不能收紧;TCK 用例 `storesTheLongestPortableKey`、`storesTheLongestPortableNamespaceAndOwnerKey` 证明每个后端都能存下上限(含非 ASCII 字符)。
- **超时(R1-spi-04)**:`StoreConfig.timeout` 必须为正,各后端不再有"0 = 不限"的分支。
- **存储时钟**一律取 UTC 纪元毫秒。安全性不依赖存储时钟(05 §3),它只影响过期判断。
- 命名空间元数据:首次 `acquire` 时记录 `{limit, tolerance, slotOffset}`(`slotOffset` 见 api-freeze §2.1.1、§2.5.1,只记录并比对,不影响选槽),之后任一不一致即抛 `NamespaceMismatchException`。
  `slotOffset` 是 5.0.0-alpha.1 之后追加的字段:各后端读到没有它的旧元数据时按 0 处理,也不改写旧元数据;旧读者忽略它。各后端的格式:
  JDBC `cosid_namespace.slot_offset integer not null default 0`(`initSchema` 为已有的 alpha.1 表补列;未补列时 offset 0 照常工作,非 0 报错并给出 `alter table` 语句);
  Redis `cosid:{ns}:meta` 哈希追加字段 `slotOffset`;Mongo `cosid_namespace` 文档追加 `slotOffset`(int32);ZooKeeper `/cosid/slot/{ns}` 的元数据文本追加一行 `slotOffset=`;
  remote 的 `/slots:acquire` 请求体追加可选的 `slotOffset`(08)。

## 2. 各后端实现

**版本策略(D27、D28)**:支持承诺从下文各节写明的最低版本开始,CI 必须覆盖最低版本与最新版本。另外在低于最低版本的若干版本上各跑一次 TCK
(非阻塞的 probe job),结果写入各模块 README 的"实测兼容性(不承诺)"表,只用于如实说明兼容情况,不为旧版本加任何兼容代码。
R5-B 把各模块的实测结果汇总到文档站。

### 2.1 JDBC

支持的数据库与最低版本(D27):MySQL 8.4 LTS、MariaDB 10.11 LTS、PostgreSQL 15、SQL Server 2019、H2 2.x(开发与测试)。CI 对每种方言覆盖最低版本与最新版本。方言自动探测,也可以通过 `JdbcDialect` SPI 扩展,取代 4.x 的自定义 SQL 配置。

```sql
create table cosid_range (
    range_key   varchar(255) not null primary key,
    next_value  bigint       not null,
    sealed      boolean      not null default false
);

create table cosid_slot (
    namespace      varchar(100) not null,
    slot           integer      not null,
    owner_key      varchar(255),
    incarnation    char(36),
    last_owner_key varchar(255),
    fence          bigint       not null default 0,
    expires_at     bigint       not null default 0,
    floor_ms       bigint       not null default 0,
    ceiling_ms     bigint       not null default 0,
    next_floor_ms  bigint       not null default 0,
    primary key (namespace, slot)
);
-- R1-jdbc-01:acquire 读取 owner key 自己的行;名字随 slotTable(<slotTable>_last_owner_key),initSchema 为 alpha.1 的表补建
create index cosid_slot_last_owner_key on cosid_slot (namespace, last_owner_key);

create table cosid_namespace (
    namespace    varchar(100) not null primary key,
    slot_limit   integer      not null,
    tolerance_ms bigint       not null,
    slot_offset  integer      not null default 0
);
```

上面是 PostgreSQL / H2 的写法(`schema-*.sql`,均为 `create … if not exists`)。键列按二进制比较、保留尾随空格(R1-spi-02:`Order`、`order`、`order ` 是三个 key):
MySQL 用 `character set utf8mb4 collate utf8mb4_0900_bin`,MariaDB 用 `utf8mb4_nopad_bin`(二者 `engine = InnoDB`,索引写在 `create table` 内);
SQL Server 用 `nvarchar` / `nchar` 加 `collate Latin1_General_100_BIN2`,`sealed` 为 `bit`;PostgreSQL、H2 默认即精确比较。

`reserve`,每种方言一次往返:

| 方言 | 语句 |
|---|---|
| MySQL / MariaDB | 先 `update cosid_range set next_value = last_insert_id(next_value + ?) where range_key = ? and not sealed`(2 个参数,与 4.x 相同,常规 reserve 只有这一条);匹配 0 行(新 key 或已封存)或溢出(MySQL/MariaDB 在任何 SQL 模式下都以错误 1690 拒绝 BIGINT 溢出,不会回绕)时,才执行 `insert into cosid_range (range_key, next_value) values (?, last_insert_id(? + ?)) on duplicate key update next_value = if(sealed or next_value > ?, next_value + last_insert_id(0), last_insert_id(next_value + ?))`(`? = Long.MAX_VALUE − size`,溢出检查),从该语句自己的 OK 包(generated key)读取。各分支都设置 `last_insert_id`,不会读到连接池中残留的旧值;generated key 为空或 0 时只读该行确认已封存或已满,**不再**在连接上 `select last_insert_id()`(SQL 代理可能从另一个后端连接回答,R1-jdbc-06) |
| PostgreSQL | `insert into cosid_range as r … values (?, ? + ?) on conflict (range_key) do update set next_value = r.next_value + ? where not r.sealed and r.next_value <= ? returning next_value` |
| SQL Server | `merge … with (holdlock) … when matched and r.sealed = 0 and r.next_value <= ? then update … output inserted.next_value` |
| H2 | `merge into … using (values (?)) … when matched and not r.sealed and r.next_value <= ? then update … when not matched then insert …`,随后在同一事务内读取该行 |

结果区间为 `[next − size, next)`。key 已封存或已满时更新零行,分别抛出 `RangeSealedException` / `RangeExhaustedException`。

**SQL 代理防线(R1-jdbc-06,恢复 4.x 的检查)**:`next < size` 不可能是本语句写入的值(代理改写或丢失了 `last_insert_id`),reserve 失败而不发出区间;MySQL/MariaDB 拿不到 generated key 且该行既未封存也未满时同样失败。
退路 `options.fetchPlan=transaction`(4.x `fetch-plan=transaction`,`FetchPlan.TRANSACTION`):同一事务内 `update … set next_value = next_value + ?`(新 key 时 `insert`),再读该行;行锁保证读到的就是写入的值,
不依赖连接状态,适用于 SQL 代理与读写分离(事务留在主库)。多一次往返。默认 `statement`。迁移工具对 4.x 源的封存与 5.0 目标的抬升同样校验读回值,不一致即报错并提示直连主库。

槽位:
- 命名空间首次使用时幂等预建 `limit` 行(MySQL `insert ignore`;PostgreSQL `on conflict do nothing`;SQL Server `merge`;H2 `merge`)。
- `acquire` 按 05 §2.1 的顺序逐个尝试,走 CAS 路径(R2 裁定,往返次数不多于 4.x):
  一次非锁定读取得到候选槽位(幂等、preferred、粘性、前几个空闲槽位)与存储时钟。读取是三个 `union all` 分支,各走一个索引(R1-jdbc-01):
  owner key 自己的行按 `(namespace, last_owner_key)`(`take` 同时写 `owner_key` 与 `last_owner_key`,`release` 保留后者,所以持有中的行必在其中),preferred 与空闲槽位按主键;每一步是一条自动提交的
  `update … where namespace = ? and slot = ? and fence = ? and (以数据库时钟判断"可取")`,影响 0 行则换下一个候选。
  LOWEST 一步在前 k 个空闲槽位中随机选取以分散竞争。PostgreSQL 可用单条 `update … where slot = (select … for update skip locked limit 1) returning`。
  正确性只依赖单槽位的原子条件更新。
- 存储时钟(UTC):MySQL `timestampdiff(microsecond, '1970-01-01 00:00:00', utc_timestamp(6)) div 1000`(不受会话时区与夏令时影响);PostgreSQL `(extract(epoch from clock_timestamp()) * 1000)::bigint`;
  SQL Server `datediff_big(ms, '1970-01-01', sysutcdatetime())`;H2 `cast(extract(epoch from current_timestamp) * 1000 as bigint)`。
- 是否自动建表由 `options.initSchema` 控制(默认关闭);表名可通过 `options.rangeTable` / `slotTable` / `namespaceTable` 配置。

### 2.2 Redis(最低 7.0,D27)

模块只依赖 `lettuce-core`,并定义最小执行接口;Spring Data 的适配放在 `spring/autoconfigure`(D6):

```java
public interface RedisScripts {
    List<Object> eval(String sha, String script, List<String> keys, List<String> args);
}
```

- `reserve`:key `cosid:{range-key}`(十进制字符串),`cosid:{range-key}:sealed` 标记封存。Lua 内先用 `setnx` 设置初值再 `incrby`(溢出时返回 exhausted);参数与返回值保持字符串,避免超过 2^53 的精度损失。
- 槽位,每个命名空间共用一个 hash tag `{ns}`:
  - `cosid:{ns}:meta` 为 Hash,`limit`、`tolerance`、`slotOffset`(命名空间元数据,§1);
  - `cosid:{ns}:slot` 为 Hash,`slot → "ownerKey|incarnation|lastOwnerKey|fence|expiresAt|floor|ceiling|nextFloor"`;
    owner key 转义(`\` 写作 `\\`,`|` 写作 `\p`),空闲槽位的 ownerKey 与 incarnation 为空;按位置解码,只读前 8 个字段,多余字段被忽略;
  - `cosid:{ns}:free` 为 ZSet,`slot → slot`(score 即槽位号),存放空闲槽位,首次使用时以 `ZADD NX` 预填 `0..limit-1`;
  - `cosid:{ns}:live` 为 ZSet,`slot → expiresAt`,存放存活租约;
  - `cosid:{ns}:owner` 为 Hash,`ownerKey → 最近槽位`,用于粘性选槽(槽位被别的 key 取走时删除该项,条目数不超过 `limit`);
  - `cosid:{ns}:held` 为 Hash,`ownerKey → 逗号分隔的槽位`,即行中写着该 owner key 的槽位(存活或已过期),用于幂等判断与 `duplicateOwnerKey`。
- `acquire` 在一个 Lua 中完成:`now = TIME`;用 `ZRANGEBYSCORE live -inf (now LIMIT 0 k` 把至多 k 个(`options.sweepBatch`,默认 16)已过期的槽位移入 `free`;
  按 `held` 找到本 incarnation 已持有的槽位(幂等),再依次尝试 preferred、粘性槽位;否则 `ZRANGEBYSCORE free 0 (limit−1) LIMIT 0 1` 取编号最小的空闲槽位。复杂度 O(log n)。
- `release`:从 `live` 移除,加入 `free`(score 为槽位号),`expiresAt = now`。
- 存储时钟:Lua 内 `redis.call('TIME')`。Redis 7 只使用脚本效果复制,`TIME` 可以安全使用。
- `WAIT` 默认 auto:启动时读取 `INFO replication`,有副本时每次写操作后执行 `WAIT 1 timeout`,未确认则操作失败。
  `WAIT` 在 store 自建的专用连接上执行,不会阻塞用户注入的多路复用连接。
- 文档声明:Redis 后端的唯一性以"已确认的写入不会丢失"为前提,即单节点开启 AOF `appendfsync always`,或者 `WAIT` 覆盖所有副本。
  在异步复制下发生 failover 时,号段与租约的写入都可能丢失,进而产生重复 ID。对唯一性要求严格的场景推荐 JDBC 或 ZooKeeper。
  客户端只在写入被确认后才使用新的区间或 ceiling。

### 2.3 MongoDB(最低 6.0,D26)

- `cosid_range`:`{_id: key, next, sealed}`。`findOneAndUpdate`,`upsert`,管道更新 `next = ifNull(next, initialOffset) + size`,过滤 `sealed != true` 且 `next` 留得下 `size`,返回更新后的文档;
  遇到 `E11000`(并发首次 upsert,或已有 key 不满足过滤)时读取该 key,已封存或已满即报告,否则重试,共至多 3 次(`ATTEMPTS`)。
- `cosid_slot`:`{ns, slot, ownerKey, incarnation, lastOwnerKey, fence, expiresAt, floor, ceiling, nextFloor, writtenAt}`,索引 `ns_slot`(唯一)、`ns_ownerKey`、`ns_lastOwnerKey`,
  命名空间首次使用时预建所有槽位。acquire 先用一次聚合(带 `$$NOW`)读出该 owner key 持有或最近持有的槽位(幂等、重复 key 检查、粘性候选);
  05 §2.1 的每次尝试都是一个 `findOneAndUpdate`:过滤条件用 `$expr` 与 `$$NOW` 表达"可取",按 `slot` 升序,条件中带上 `fence`。
  管道更新先写 `writtenAt = $$NOW` 再由它推出过期时间,`storeNow` 与 `expiresAt` 取自同一次写入。
- `cosid_namespace`:`{_id: ns, limit, toleranceMs, slotOffset}`;首次使用的 upsert 输掉竞争(`E11000`)时重试,至多 5 次(`NAMESPACE_ATTEMPTS`)。
- 写关注由 `options.writeConcern` 配置,默认 `majority`;设置得比 majority 弱时,启动时告警:failover 时可能丢写并导致重复(#1336)。读关注 `majority`。存储时钟取 `$$NOW`。数据库名通过 `options.database` 配置(默认 `cosid_db`)。

### 2.4 ZooKeeper(最低 3.8,以 3.9 为主,D27)

节点(数据均为 ASCII 文本;字符串按 `URLEncoder` 编码):

| 节点 | 数据 |
|---|---|
| `/cosid/range/{key}` | 下一个值的十进制文本,封存后追加 `:sealed`;从不删除(S6) |
| `/cosid/slot/{ns}` | 命名空间元数据,`name=value` 行:`limit=…`、`tolerance=…`、`slotOffset=…`(alpha.1 写入的节点没有 `slotOffset` 行,按 0 处理) |
| `/cosid/slot/{ns}/{slot}` | 槽位(`SlotState`),`name=value` 行:`owner`、`incarnation`(仅持有时)、`lastOwner`(有过持有者时)、`fence`、`ttl`、`tolerance`、`floor`、`expiresMin`、`ceilingMin`、`nextFloor`;首次 acquire 时创建,从不删除 |
| `/cosid/slot/{ns}/.clock` | 每次 acquire 都写入,它的 `mtime` 就是过期判断用的 `now` |
| `/cosid/owner/{ns}/{ownerKey}` | owner 索引:最近持有者为该 key 的槽位(如 `3,7`),与每次 acquire 在同一个 `multi` 中写入;key 经 URL 编码,`.` 与 `*` 也转义 |

- 区间:用版本化 `setData` 实现 CAS,不存在时 `create`,遇到 `NodeExists` 重试。store 记住自己上次写入的值与版本,下一次 reserve 先直接在该版本上 `setData`(一次往返);版本已变(别的实例 reserve 过或已封存)则失败,改为先读后写,并在其后 8 次 reserve 中先读。版本校验保证这与先读后写同样安全,前提是**不变式:store 使用某个 key 期间,其节点不得被删除**(运行时从不删除区间节点;迁移工具的 `purge` 只能在所有实例停止后运行)。节点删除重建后版本从 0 重新计数,可能恰好回到记住的版本(ABA):store 同时记住节点的 `czxid`,`setData` 成功但返回的 `czxid` 不同时,忘掉该 key 并抛出存储回退(与生成器的 `highWater` 同类),这次写入的区间不发出。这次写入本身无法撤回,所以不变式才是主要防线,`czxid` 校验是为了让违反不变式的情况大声失败,而不是悄悄重号。
- **fence 存在数据里**,版本化 `setData` 只用来实现 CAS,所以节点被重建也不会让 fence 回退(运行时从不删除槽位节点)。
- 租约时间取自产生它的那次写入的 `Stat.mtime`:`expiresAt = max(expiresMin, mtime + ttl)`,`ceiling = max(ceilingMin, expiresAt + tolerance)`;
  下界取自被替换的那次写入,任期内二者不回退。`storeNow` 与 `expiresAt` 取自**同一次写入**。安全性不依赖它(05 §3)。
- 运维前提:人工重建槽位节点时,写入重建的服务端时钟不能落后于原节点的 `mtime`,否则应在恢复后等待一个 ttl 再启动。
- 命名空间不能含 `/`,range key 必须是合法的 ZooKeeper 路径(另见 §1 的可移植键)。
- 选槽:先读 `.clock` 与 owner 索引,再只读索引列出的槽位与 preferred 槽位(决定幂等、重复 key、粘性三步);最小空闲槽位按升序分批查找
  (每个只读 `multi` 读 32、64……至多 256 个槽位),找到第一个空闲槽位即停止。只有命名空间耗尽时才读全部槽位。
  每次状态变更都是对一个槽位节点按所读版本的 CAS(acquire 在同一个 `multi` 中写 owner 索引);release 只写槽位节点。唯一性不依赖索引。

### 2.5 Remote

把两个原语映射为 HTTP 调用(见 [08-remote.md](08-remote.md)),由底层存储打戳,一次往返完成。客户端使用 JDK `HttpClient`,无三方依赖。

## 3. TCK(`store/tck`)

JUnit 5 抽象契约类,后端测试继承即可:

```java
public abstract class RangeStoreContract { protected abstract RangeStore newStore(); … }
public abstract class SlotLeaseStoreContract { protected abstract SlotLeaseStore newStore(); … }
```

| 类别 | 用例 |
|---|---|
| 区间 | 首次创建从 `initialOffset` 开始;并发首次创建只有一个生效;N 线程 × M 个存储实例并发 `reserve` 无重叠且严格递增;超过 2^53 的值精确;溢出抛出 `RangeExhaustedException`;封存后 `reserve` 失败且值不变;同一连接先后预留不同 key 时结果互不影响(防 `last_insert_id` 残留) |
| 租约基础 | 选槽顺序(幂等、preferred、粘性、最小空闲);`Exhausted`;`limit` 边界 |
| 安全 | 接管后 `floor ≥` 上一任存储中的最终 `ceiling`;释放后 `floor ≥ lastIssued`;`ceiling`、`next_floor` 单调;同 key 不同 incarnation 不接管;过期持有者的 renew/release 失败;续约幂等(重放不改变 fence、不导致失败) |
| 时钟 | `storeNow` 来自存储;`expiresAt − storeNow == ttl`(取自同一次写入) |
| 并发 | N 个客户端并发 acquire 同一命名空间,所得槽位两两不同 |
| 元数据 | 命名空间参数不一致时拒绝 |

`store/tck` 同时提供内存参考实现 `InMemoryStore`,它本身必须通过全部契约,供 core 与 Spring 层的单元测试使用。
TCK 还提供"故障注入包装器"(延迟、超时、响应丢失后重放),供 R1 的生成器测试使用。
