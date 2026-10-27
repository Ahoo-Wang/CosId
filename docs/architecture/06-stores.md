# 06 存储:SPI、后端实现与 TCK

## 1. SPI

两个原语的契约见 [04-segment.md](04-segment.md) §1 与 [05-machine-lease.md](05-machine-lease.md) §2。发现与创建:

```java
public interface StoreProvider {
    String type();                                   // "jdbc"、"redis"、"mongo"、"zookeeper"、"remote" 或自定义
    Store open(StoreConfig config);
}

public interface Store extends AutoCloseable {
    RangeStore ranges();                             // 不支持时抛 UnsupportedOperationException
    SlotLeaseStore slots();
}

public record StoreConfig(String name, String type, Duration timeout, Map<String, Object> options, Optional<Object> client) {}
```

- `StoreProvider` 通过 `ServiceLoader` 发现;Spring 环境下也可以作为 Bean 提供。**两种来源走同一条创建路径**,由 `CosIdRuntime` 按 `type` 选择,Bean 优先。取代 4.x 的 `CosIdStoreProvider`。
- `client` 用于注入已有连接对象(`DataSource`、Lettuce 连接、`MongoClient`、`CuratorFramework`);为空时 provider 按 `options` 自行创建并负责关闭。
- `timeout` 由后端用原生机制实现(JDBC `queryTimeout`、Redis 命令超时、Mongo `timeoutMS`、Curator 操作超时、HTTP 请求超时),core 不再包装超时线程池。
- **存储时钟**一律取 UTC 纪元毫秒。安全性不依赖存储时钟(05 §3),它只影响过期判断。
- 命名空间元数据:首次 `acquire` 时记录 `{limit, tolerance}`,之后参数不一致即拒绝。

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

create table cosid_namespace (
    namespace    varchar(100) not null primary key,
    slot_limit   integer      not null,
    tolerance_ms bigint       not null
);
```

`reserve`,每种方言一次往返:

| 方言 | 语句 |
|---|---|
| MySQL / MariaDB | `insert into cosid_range (range_key, next_value) values (?, last_insert_id(? + ?)) on duplicate key update next_value = last_insert_id(next_value + ?)`,然后在同一连接上 `select last_insert_id()`。插入分支与更新分支都会设置 `last_insert_id`,不会读到连接池中残留的旧值 |
| PostgreSQL | `insert … values (?, ? + ?) on conflict (range_key) do update set next_value = cosid_range.next_value + ? where not cosid_range.sealed returning next_value` |
| SQL Server | `merge … with (holdlock) … output inserted.next_value` |
| H2 | `merge into … key (range_key)`,随后在事务内读取 |

结果区间为 `[next − size, next)`。key 已封存时更新零行,抛出 `RangeSealedException`。

槽位:
- 命名空间首次使用时幂等预建 `limit` 行(MySQL `insert ignore`;PostgreSQL `on conflict do nothing`;SQL Server `merge`;H2 `merge`)。
- `acquire` 按 05 §2.1 的顺序逐个尝试,走 CAS 路径(R2 裁定,往返次数不多于 4.x):
  一次非锁定读取得到候选槽位(幂等、preferred、粘性、前几个空闲槽位)与存储时钟;每一步是一条自动提交的
  `update … where namespace = ? and slot = ? and fence = ? and (以数据库时钟判断"可取")`,影响 0 行则换下一个候选。
  LOWEST 一步在前 k 个空闲槽位中随机选取以分散竞争。PostgreSQL 可用单条 `update … where slot = (select … for update skip locked limit 1) returning`。
  正确性只依赖单槽位的原子条件更新。
- 存储时钟(UTC):MySQL `timestampdiff(microsecond, '1970-01-01 00:00:00', utc_timestamp(6)) div 1000`(不受会话时区与夏令时影响);PostgreSQL `(extract(epoch from clock_timestamp()) * 1000)::bigint`;
  SQL Server `datediff_big(ms, '1970-01-01', sysutcdatetime())`;H2 同 PostgreSQL 写法。
- 是否自动建表由 `options.initSchema` 控制(默认关闭);表名可通过 `options.rangeTable` / `slotTable` / `namespaceTable` 配置。

### 2.2 Redis(最低 7.0,D27)

模块只依赖 `lettuce-core`,并定义最小执行接口;Spring Data 的适配放在 `spring/autoconfigure`(D6):

```java
public interface RedisScripts {
    List<Object> eval(String sha, String script, List<String> keys, List<String> args);
}
```

- `reserve`:key `cosid:{range-key}`,`cosid:{range-key}:sealed` 标记封存。Lua 内先用 `setnx` 设置初值再 `incrby`;参数与返回值保持字符串,避免超过 2^53 的精度损失。
- 槽位,每个命名空间共用一个 hash tag `{ns}`:
  - `cosid:{ns}:slot` 为 Hash,`slot → "ownerKey|incarnation|lastOwnerKey|fence|expiresAt|floor|ceiling|nextFloor"`;
  - `cosid:{ns}:free` 为 ZSet,`slot → slot`(score 即槽位号),存放空闲槽位,首次使用时以 `ZADD NX` 预填 `0..limit-1`;
  - `cosid:{ns}:live` 为 ZSet,`slot → expiresAt`,存放存活租约;
  - `cosid:{ns}:owner` 为 Hash,`ownerKey → 最近槽位`,用于粘性选槽。
- `acquire` 在一个 Lua 中完成:`now = TIME`;用 `ZRANGEBYSCORE live -inf now LIMIT 0 k` 把至多 k 个已过期的槽位移入 `free`;
  依次尝试 preferred、粘性槽位;否则 `ZRANGEBYSCORE free 0 (limit−1) LIMIT 0 1` 取编号最小的空闲槽位。复杂度 O(log n)。
- `release`:从 `live` 移除,加入 `free`(score 为槽位号),`expiresAt = now`。
- 存储时钟:Lua 内 `redis.call('TIME')`。Redis 7 只使用脚本效果复制,`TIME` 可以安全使用。
- `WAIT` 默认 auto:启动时读取 `INFO replication`,有副本时每次写操作后执行 `WAIT 1 timeout`,未确认则操作失败。
  `WAIT` 在 store 自建的专用连接上执行,不会阻塞用户注入的多路复用连接。
- 文档声明:Redis 后端的唯一性以"已确认的写入不会丢失"为前提,即单节点开启 AOF `appendfsync always`,或者 `WAIT` 覆盖所有副本。
  在异步复制下发生 failover 时,号段与租约的写入都可能丢失,进而产生重复 ID。对唯一性要求严格的场景推荐 JDBC 或 ZooKeeper。
  客户端只在写入被确认后才使用新的区间或 ceiling。

### 2.3 MongoDB(最低 6.0,D26)

- `cosid_range`:`{_id: key, next, sealed}`。`findOneAndUpdate`,`upsert`,管道更新 `next = ifNull(next, initialOffset) + size`,过滤 `sealed != true`,返回更新后的文档;遇到 `E11000`(并发 upsert)重试一次。
- `cosid_slot`:`{ns, slot, …}`,在 `(ns, slot)` 上建唯一索引,预建所有槽位。05 §2.1 的每次尝试都是一个 `findOneAndUpdate`:
  过滤条件用 `$expr` 与 `$$NOW` 表达"可取",按 `slot` 升序,条件中带上 `fence`。
- 写关注由 `options.writeConcern` 配置,默认 `majority`;设置得比 majority 弱时,启动时告警:failover 时可能丢写并导致重复(#1336)。读关注 `majority`。存储时钟取 `$$NOW`。数据库名通过 `options.database` 配置(默认 `cosid_db`)。

### 2.4 ZooKeeper(最低 3.8,以 3.9 为主,D27)

- 区间:znode `/cosid/range/{key}`,数据为 `next`(以及封存标记);用版本化 `setData` 实现 CAS,不存在时 `create`,遇到 `NodeExists` 重试。
- 槽位:`/cosid/slot/{ns}/{slot}`,数据为 `{ownerKey, incarnation, lastOwnerKey, fence, ttl, floor, ceiling, nextFloor}`。
  - **fence 存在数据里**,版本化 `setData` 只用来实现 CAS,所以节点被重建也不会让 fence 回退(运行时从不删除槽位节点)。
  - 节点数据实际为 `{ownerKey, incarnation, lastOwnerKey, fence, ttl, tolerance, floor, expiresMin, ceilingMin, nextFloor}`(R2 实现):
    `expiresAt = max(expiresMin, mtime + ttl)`,`ceiling = max(ceilingMin, expiresAt + tolerance)`;下界取自被替换的那次写入,任期内二者不回退。
  - 运维前提:人工重建槽位节点时,写入重建的服务端时钟不能落后于原节点的 `mtime`,否则应在恢复后等待一个 ttl 再启动。
  - 命名空间不能含 `/`,range key 必须是合法的 ZooKeeper 路径。
  - 存储时钟:`storeNow` 与 `expiresAt` 都取自**同一次写入**的 `Stat.mtime`(`expiresAt = mtime + ttl`)。
    判断"是否可取"时,先写 `/cosid/slot/{ns}/.clock` 取得当前 `mtime` 作为 `now`。安全性不依赖它(05 §3)。
  - 选槽:用异步批量 `getData` 读取全部槽位节点后按 05 §2.1 尝试。acquire 是低频操作,1024 个槽位的批量读可以接受;实际耗时由 R2 基准(P10)验证。

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
