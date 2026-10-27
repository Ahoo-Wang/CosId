# 11 特性对照表(4.x → 5.0)

> D21 的发布门槛:每一行都必须是「保留」或「等价」,并有测试证明。「待定」行需要先做决策。
> 状态列:**保留** = 同一能力、同一语义;**等价** = 能力不减,表达方式不同;**待定** = 存在冲突,需要决策。

## 1. 生成器

| 4.x 能力 | 5.0 | 状态 |
|---|---|---|
| 毫秒 Snowflake(epoch、各位宽、`sequenceResetThreshold` 可配) | `TimeBasedGenerator` + `Layout`(全部参数) | 等价 |
| 秒级 Snowflake | `Layout.SNOWFLAKE_S` / 自定义 `unit = SECONDS` | 等价 |
| JS 安全 Snowflake 与 `isSafeJavaScript(id)` | `Layout.JS_SAFE_MS/S`、`Layout.isJsSafe(id)` | 等价 |
| 时钟回拨同步(`ClockSync*`,spin / broken 阈值) | `ClockRegressionPolicy(borrowUpTo, waitUpTo)` | 等价 |
| 租约丢失时 fail fast(`Leased*`) | 窗口检查(总是开启) | 等价 |
| 计数(`Counted*`)与 `generated` 指标 | listener + `LongAdder`,`metrics.generated` | 等价 |
| Snowflake 内省(布局、最后时间戳、机器号) | `layout()`、`lastIssuedMillis()`、`slot()`、`describe()` | 等价 |
| Friendly ID(`friendlyId` / `ofFriendlyId`,时区、补零) | `Friendly.of(layout, zone, padStart)` 的 encode/decode | 等价 |
| ID 解析(时间、机器、序列) | `Layout.unpack` → `IdParts` | 等价 |
| CosIdGenerator Radix62 / Radix36 / Friendly,`generateAsState` | `cosid80` + `PartsCodec`(radix 62/36、friendly),`nextParts()` | 等价 |
| `DefaultSegmentId`(单段,ttl) | `SegmentGenerator`,`lookahead = 0`,`rangeTtl` | 等价 |
| `SegmentChainId`(safeDistance、maxPrefetchDistance、prefetch 周期与超时) | `lookahead`、`maxBatch`、`reserveTimeout`(按时间预算自适应) | 等价 |
| 号段 offset、step | `BatchPolicy.initialOffset`、`step` | 保留 |
| 号段 ttl(趋势递增) | `BatchPolicy.rangeTtl` | 保留 |
| 按年 / 年月 / 年月日分组号段,`generateGrouped`、`GroupedId` | `PeriodicSegmentGenerator`、`GroupedId` | 等价(跨期不再卡顿) |
| `IntegerIdGenerator`(溢出异常) | `IdGenerators.toInt` | 等价 |
| `UncertaintyIdGenerator` | `IdGenerators.uncertain` | 等价 |
| `AtomicLongGenerator`、`UuidGenerator` | `IdGenerators.local()`、`IdGenerators.uuid()` | 等价 |
| `LazyIdGenerator` 与 `as*` 转换 | `IdGenerators.lazy`、`CosIdRuntime.get(name, type)` | 等价 |
| `IdGeneratorProvider`(按名、按类型查询,share,增删) | `CosIdRuntime` 查询;`default` 生成器;增删通过 `RuntimeSpecCustomizer` 在启动前完成 | 等价 |
| Stat 体系 | `describe()` / `GeneratorInfo` | 等价 |
| 事件监听(12 个钩子) | `GeneratorListener`(14 个回调,覆盖全部原有钩子) | 等价 |

## 2. 编解码

| 4.x 能力 | 5.0 | 状态 |
|---|---|---|
| Radix62 / Radix36 / ToString(padStart、charSize) | `Radix.of(62/36/10, width, padStart)` | 等价 |
| Snowflake Friendly 转换器 | `Friendly` | 等价 |
| 前缀、后缀 | `withPrefix`、`withSuffix` / codec 列表项 | 保留 |
| 日期前缀(pattern、delimiter、before-prefix) | `DatePrefix`(列表中的位置决定前后) | 等价 |
| 分组前缀(delimiter、before-prefix) | `GroupedCodec.datePrefix` + 列表 | 等价 |
| 自定义转换器(类名) | 自定义 `IdCodec`(类名或 Bean 名) | 等价 |
| 所有转换器的 `asLong` 反解 | `decode` | 保留 |

## 3. 机器号

| 4.x 能力 | 5.0 | 状态 |
|---|---|---|
| manual | `machine.static` | 等价 |
| StatefulSet(HOSTNAME 序号) | `machine.static.slot: ${POD_ORDINAL}` 或 `leased.preferred-slot` | 等价 |
| redis / jdbc / zookeeper / mongo / proxy 分配 | `machine.leased` + 对应存储 | 等价 |
| 自定义存储类型(`CosIdStoreProvider`) | `StoreProvider` | 等价 |
| instance-id、port、host:pid 推导,Spring Cloud `InetUtils` | `ownerKey` 推导(07 §1.1) | 保留 |
| stable(重启保留同一机器号) | 粘性选槽 + `preferredSlot` | 等价(不再永生占用) |
| 本地状态存储(`~/.cosid-machine-state`) | StaticSlot 本地窗口文件;租约模式下由存储内的 floor 取代 | 等价(更强) |
| guarder:initial-delay、delay、safe-guard-duration | `ttl`、续约间隔 `ttl/3` | 等价 |
| guarder.fail-fast = false(租约丢失后继续发号) | 不提供(P1):任何模型下继续发号都可能重号 | 以安全为由不提供 |
| machine-bit、`MachineIdOverflowException` | `bits`、启动校验 | 保留 |
| 健康检查 `MachineIdHealthIndicator` | `CosIdHealthIndicator` | 保留 |
| `jdbc.server-time-sql` | 方言提供存储时钟;`JdbcDialect` SPI 可覆盖 | 等价 |

## 4. 存储

| 4.x 能力 | 5.0 | 状态 |
|---|---|---|
| JDBC:MySQL / MariaDB / PostgreSQL / SQL Server / H2,方言自动探测 | 同上(最低版本见 06 §2.1) | 保留 |
| JDBC 自动建表、自动初始化号段行 | `options.initSchema`;号段行按需 upsert | 等价 |
| JDBC 自定义 SQL(increment / fetch / init) | 表名可配置 + `JdbcDialect` SPI | 等价 |
| JDBC query-timeout、fetch-plan | `timeout`;fetch-plan 不再需要(一次往返) | 等价 |
| Redis 超时、wait-replicas、wait-timeout、hash tag | `timeout`、`options.wait`、hash tag | 保留 |
| Mongo database、timeout、write-concern | `options.database`、`timeout`、`options.writeConcern` | 保留 |
| ZooKeeper 连接(connect-string、重试、会话超时等) | `stores.<name>.options` 或注入 `CuratorFramework` | 等价 |
| `max-concurrent-calls` 与调用超时 | 每个生成器单飞(并发 ≤ 1)+ 原生超时 | 等价(更强) |
| 预取工作线程池(core-pool-size、shutdown-hook) | 注入的执行器(默认虚拟线程) | 等价 |

## 5. Spring Boot

| 4.x 能力 | 5.0 | 状态 |
|---|---|---|
| 属性绑定:snowflake / segment / generator / machine / zookeeper / metrics | 统一模型(07 §4.1),`cosid-migration config` 负责转换 | 等价 |
| 每个 ID 一个 Bean(`{name}SnowflakeId` / `{name}SegmentId`) | 每个生成器一个 Bean,以生成器名命名 | 等价 |
| `Customize*Properties` 编程式增删改 | `RuntimeSpecCustomizer` | 等价 |
| `provider.isolated` | 每个上下文一个运行时(默认) | 等价(更强) |
| Actuator `cosid` 读取统计,`cosidGenerator` / `cosidStringGenerator` 生成 | `CosIdEndpoint`(describe、生成 long / string) | 保留 |
| Actuator DELETE 删除生成器(默认关闭) | 只读的禁用开关 `cosid.generators.<name>.disabled`(P2) | 等价 |
| Micrometer 指标(segment、clock、sequence、lease、call 等 15 项) | 同等指标集合,以 listener 实现;名称按 5.0 模型整理 | 等价 |
| 原生镜像提示 | `CosIdRuntimeHints` | 保留 |
| 后端 feature variants | 单 starter + store 依赖 | 等价 |

## 6. 集成

| 4.x 能力 | 5.0 | 状态 |
|---|---|---|
| `@CosId`(字段 / 类型 + field、方法或字段访问、父类、泛型、long/int/String、未设置才填充) | 同上(07 §3) | 保留 |
| accessor registry / scanner | 懒加载缓存 | 等价 |
| Jackson `@AsString`(TO_STRING / RADIX / FRIENDLY_ID 及参数,序列化与反序列化) | `@AsString(codec)`,可内联或引用生成器 | 等价 |
| MyBatis 插件(单个实体与批量 listKey) | 同上 | 保留 |
| Spring Data JDBC 回调 | 同上 | 保留 |
| Axon / Activiti / Flowable(Spring 属性与系统属性) | 同上 | 保留 |
| 分片:`ModCycle`、`IntervalTimeline`、`CachedSharding`、各种时间转换器(含 Snowflake) | `extras/sharding` 原样保留;Snowflake 转换器改用 `Layout`,因此依赖 core | 保留 |

## 7. 远程服务

| 4.x 能力 | 5.0 | 状态 |
|---|---|---|
| `GET /ids`、`/ids/{name}`、`/ids/as-string[/{name}]` | `/ids[/{name}]`、`/strings`,支持 `count` | 等价(更强) |
| 远程号段与远程机器号 | `/ranges:*`、`/slots:*` | 等价 |
| Bearer token、按客户端限定命名空间、公开路径、allow-anonymous | 同上,另增 key 前缀限定 | 保留 |
| max-step、max-distributors(429)、阻塞池满返回 503 + Retry-After | `maxReserve`、单命名空间 key 上限、同样的 503 | 保留 |
| max-clock-skew、min-safe-guard-duration | ttl 上下限;时钟偏差不再影响安全 | 等价 |
| Swagger、health、prometheus 端点 | OpenAPI、health、prometheus、cosid | 保留 |
| 客户端超时、token;COAPI / HTTP Interface 两种实现 | `store/remote`(JDK HttpClient,单一实现) | 等价 |

## 8. 已决策的冲突项

| # | 冲突 | 决策 |
|---|---|---|
| P1 | guarder.fail-fast = false:允许租约丢失后继续发号 | 不提供。它以唯一性换可用性,违背唯一的硬约束 |
| P2 | Actuator DELETE 生成器 vs D17 运行时不可变 | 不提供运行时删除;提供配置项 `cosid.generators.<name>.disabled`,被禁用的生成器不创建,其 Bean 调用时抛出 `GeneratorDisabledException` |
