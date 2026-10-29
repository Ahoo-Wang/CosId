# 5.0.0-RC1 前全面审查(WIP)

> 状态:**审查完成,修复进行中**(2026-10-27)。下一步:逐个审查各修复 PR,CI 全绿后合入;全部 BLOCKER 关闭后核对 §5 并请用户决定 RC。本文件是审查的唯一进度记录,每收到一路结果就更新并提交,
> 以便会话中断(额度限制等)后由任何会话接着做。

## 0. 恢复方法(会话中断后从这里继续)

1. 在任何机器上:`git fetch origin && git switch wip/ga`(Draft PR [#1368](https://github.com/Ahoo-Wang/CosId/pull/1368);RC 前审查阶段的 WIP 已于 2026-10-28 随 [#1344](https://github.com/Ahoo-Wang/CosId/pull/1344) 作为 alpha.1 里程碑合入 main),读本文件 §2 的状态表与 §3 的发现。WIP 只存在于 GitHub 上的 PR 分支,不依赖本机状态。
2. 状态为"进行中"的审查路,如果没有结果,按 §1 的范围重新派一个只读审查(不跑构建,遵守 `AGENTS.md` 的 CPU 规则)。
3. 所有路都有结果后:逐条在代码中核实 → 填 §4 修复计划 → 按归属派会话修复 → 每个修复 PR 合入后在 §4 勾掉。
4. §5 的 RC 准入清单全部通过后,再请用户决定是否发布 RC1。

相关进度见 [roadmap.md](roadmap.md) §5(阶段、里程碑、遗留事项 L1–L16)。

## 1. 审查分工(只读,对照设计文档 01–11)

| 路 | 范围 | 重点 |
|---|---|---|
| A | `core` | 任何可能重号的路径(时间型临界区与窗口、换绑、CLOSED 哨兵、号段切换与补充、周期滚动与封存、LeaseKeeper/StaticSlot、运行时关闭顺序);并发;API 与 module-info;编解码边界 |
| B | `store/*`(jdbc、redis、mongo、zookeeper、remote)与 `store/tck` | 原子性与不变式 W、封存不重建(S6)、超时映射与资源释放、SQL/Lua 注入与参数编码、与 06 / 模块 README 一致 |
| C | `spring/*`、`server`、`integrations/*`、`examples/*` | server 安全(默认鉴权、常量时间比较、作用域、限制、暴露面)、Spring 装配与禁用生成器、可选依赖缺失时能否启动、集成生命周期、残留 `/v5` |
| D | `migration/` | M1–M3、4.x 各 key 形态映射、先封存后写、W 公式、管理写路径与当前存储格式一致(含 ZK owner 索引)、`config` 转换、purge 与凭据 |
| E | 发布就绪 | 发布模块与 artifactId、BOM、签名与 POM、Docker 镜像与 `latest` 规则、文档站、CI 分支、残留 TODO/@Disabled/临时排除项、哪些遗留事项阻塞 RC |

严重度:**BLOCKER**(阻塞 RC)/ **MAJOR** / **MINOR**。

## 2. 状态

| 路 | 状态 | 结果 |
|---|---|---|
| A core | ✅ 完成 | 无 BLOCKER、无重号路径;1 MAJOR(由 MINOR 上调)、6 MINOR(见 §3.A) |
| B stores | ✅ 完成 | 1 BLOCKER、1 MAJOR(由 MINOR 上调)、4 MINOR(见 §3.B) |
| C spring/server/integrations | ✅ 完成,已核实 | 2 BLOCKER(由 MAJOR 上调)、1 MAJOR、5 MINOR(见 §3.C) |
| D migration | ✅ 完成 | 4 BLOCKER(含 3 个由 MAJOR 上调)、3 MAJOR、4 MINOR(见 §3.D) |
| E 发布就绪 | ✅ 完成,已核实 | 3 BLOCKER、3 MAJOR、9 MINOR(见 §3.E) |
| 发布门槛(CI) | 第一次 [run 40534275762](https://github.com/Ahoo-Wang/CosId/actions/runs/40534275762)(`614d5df9`)FAIL;**第二次 [run 40623986450](https://github.com/Ahoo-Wang/CosId/actions/runs/40623986450)(`a68a0191`,含全部已合入修复,publish=false)运行中** | 分析 Draft PR [#1345](https://github.com/Ahoo-Wang/CosId/pull/1345)(`docs/jmh/5.0/analysis.md`) |
| #1342 ZK 排除 Netty | ✅ 已合入 | — |

## 3. 发现(核实后填写)

### 3.A core(main @ e272cdde)

**唯一性逐项核实通过**:时间型序列器(临界区内不等待、按实际使用的时间戳检查 floor/ceiling、换绑重置、终止状态给出精确 lastIssued、秒级布局)、号段(单次读取 current、highWater 拒绝重叠区间、单飞补充无丢失唤醒、关闭后迟到的完成被丢弃)、周期号段(只前进、不重建已封存 key)、LeaseKeeper/SlotBinding(续约只抬高 ceiling、丢失先失效、释放在锁外)、StaticSlot(预写并 fsync 后再抬高)、运行时关闭顺序、module-info 不泄漏 internal 类型。

| # | 严重度 | 发现 | 位置 | 修复 |
|---|---|---|---|---|
| A-M1 | MAJOR(上调) | `LeaseKeeper` 在持锁时做存储 I/O(renew/acquire),`StaticSlot` 在锁内 fsync;网络分区时 `close()` 与续约调度会被阻塞整个存储超时,违反设计原则 7 | `lease/LeaseKeeper.java:360-442`、`lease/StaticSlot.java:260` | 锁内取快照,锁外做 I/O,再加锁复核状态与 generation 后发布 |
| A-N1 | MINOR | 热路径 listener 风暴:序列耗尽时每次自旋都触发 `onSequenceExhausted`,借用期间每个 ID 触发一次回拨事件;违反 07 §2,且争用监视器(可能影响 P1/P2 基准) | `internal/TimeSequencer.java:149-209,386-389` | 每次 `nextId` 最多触发一次,借用事件合并 |
| A-N2 | MINOR | `TimeSequencer` 调 listener 无保护;公开构造器不包 `SafeListener`,listener 抛异常会丢号或让 generate 失败 | 同上 | 构造器里包 `SafeListener.of` |
| A-N3 | MINOR | 秒级布局下 1ms 回拨可能被判为失败(用 `ahead` 而非真实回拨量判定) | `TimeSequencer.java:304,315-318` | 按 `backwards` 判定,按 `ahead` 决定等待时长 |
| A-N4 | MINOR | `PeriodicSegmentGenerator.open()` 先改状态后校验,自定义 Period 有误时每次调用都累积 PendingSeal 与生成器 | `segment/PeriodicSegmentGenerator.java:225-238` | 先算并校验 end,再修改状态 |
| A-N5 | MINOR | `batch * 64` 在 batch 超过 Long.MAX/64 时溢出,补充永不触发并忙等 | `segment/SegmentGenerator.java:326` | 饱和乘法或在 `BatchPolicy` 限制上界 |
| A-N6 | MINOR | `Friendly.decode` 在夏令时回拨那一小时无法往返(本地时间文本的固有问题,4.x 同样) | `codec/FriendlyFormat.java:120` | 文档写明,建议 UTC 或固定偏移时区 |
| A-N7 | MINOR | `DatePrefix`(ID_TIME)不校验 ID 宽度,超宽 ID 得到错误日期 | `codec/DatePrefix.java:117-122` | 与 `Friendly` 一样检查 `id >>> totalBits` |

### 3.B stores(main @ e272cdde)

已核实正确:MySQL/MariaDB `last_insert_id` 两个分支都设置(封存/满时为 0)、PostgreSQL upsert、SQL Server `holdlock` MERGE、H2;JDBC `take` 的 CAS 条件;Redis Lua 超过 2^53 的精确值、free/live/owner/held 记账、清扫、封存标记、hash tag;Mongo 管道更新与 E11000 处理、CSOT 超时映射;ZK 版本化写与 owner 索引 multi、fence 存在数据中、floor 由被替换节点的 mtime 推出;注入的客户端从不被关闭;表名经标识符校验、其余全部参数绑定;各处最低版本一致;运行时代码不可达任何 delete/purge。

| # | 严重度 | 发现 | 位置 | 修复 | 核实 |
|---|---|---|---|---|---|
| B-B1 | BLOCKER | Redis Cluster 上 `WAIT` 与脚本不在同一条 TCP 连接:脚本按 slot 路由到 `(host,port)` 连接,`WAIT` 用 `getConnection(nodeId)` 拿到另一条连接,该连接没有写入,`WAIT` 立即返回。failover 时租约/区间写入可丢失 → 窗口重叠、区间重复 | `LettuceRedisScripts.java:86-95,157-162` | 对 key 所在节点取同一个 `StatefulRedisConnection`,`EVALSHA` 与 `WAIT` 都在它上面执行;加带副本的 Cluster 测试证明同一连接(`CLIENT ID` 或暂停副本后期望失败) | 代码结构已核实;Lettuce 连接键差异按审查报告,待修复 PR 的测试证实 |
| B-M1 | MAJOR(上调) | MySQL/MariaDB/SQL Server 默认排序规则不区分大小写(SQL Server、MariaDB 还忽略尾随空格):`Order` 与 `order` 共用一行,封存互相影响、后者 `initialOffset` 被忽略;换后端时会悄悄合并 key。schema 发布后再改即为破坏性变更,所以放在 RC 前处理 | `schema-mysql.sql:7,14,16`、`schema-sqlserver.sql` | key 列使用二进制排序规则(`utf8mb4_bin`、`Latin1_General_100_BIN2`) | — |
| B-N1 | MINOR | ZK 在 CAS 冲突中超时抛普通 `CosIdException`,不是 `StoreTimeoutException` | `store/zookeeper/.../Zk.java:164` | 改为 `StoreTimeoutException` | — |
| B-N2 | MINOR | JDBC 只按语句映射超时:连接池取连接超时、多语句循环没有整体期限 | `store/jdbc/.../Jdbc.java:57-66`、`JdbcSlotLeaseStore` 循环 | 连接池超时视为超时;循环受 `timeout` 约束 | — |
| B-N3 | MINOR | 远程:后端存储超时被映射为普通 500,客户端无法区分 | `server/.../ApiErrors.java:70-72`、`store/remote/.../RemoteClient.java:49-53` | 增加 `store_timeout`(如 504)并映射为 `StoreTimeoutException` | — |
| B-N4 | MINOR(存疑) | Redis 幂等 acquire 分支不写入,重连后的重试 `WAIT` 恒成功,可能使用未复制的租约 | `acquire.lua:85-87` | 幂等分支重写一次行(会被复制),让随后的 `WAIT` 覆盖它 | — |

### 3.C spring / server / integrations / examples(main @ e272cdde)

| # | 严重度 | 发现 | 位置 | 修复 | 核实 |
|---|---|---|---|---|---|
| C-B1 | BLOCKER(安全,上调) | actuator `cosid` 端点绕过客户端作用域与 `cosid.server.ids.enabled`:只限 `orders` 命名空间的 token 也能 `GET /actuator/cosid/{generator}/long` 发号,并能读 prometheus、describe、`/v3/api-docs` | `server/src/main/resources/application.yaml:17-21`;`AuthFilter.java`(只认证不鉴权) | `/actuator/**` 与 springdoc 路径要求不受限的 grant,或默认不暴露 `cosid`;加"受限 token 访问得 403"的测试 | ✅ |
| C-B2 | BLOCKER(安全,上调) | 未解析的 `${…}` 占位符会成为真实 token:忘记设环境变量时,服务以文档里公开的字面量 `${COSID_SERVER_TOKEN}` 作 token 启动,"无 token 拒绝启动"被绕过 | `Grants.java` `requireToken`;`application.yaml`、`documentation/docs/*/guide/server.md` | `requireToken` 拒绝含 `${` 的 token,并设最小长度 | 部分(代码中无该检查) |
| C-M1 | MAJOR | `max-keys-per-namespace` 可绕过(不含 `/` 的 key 各自成为命名空间)、可被一个租户用满而锁死共享命名空间的其他租户(永不淘汰)、503/存储失败也计数 | `KeyCap.java:40-57`、`Requests.java:43-46` | 同时限制命名空间数量或按 grant 计数;预留成功后才计数;LRU/TTL 淘汰 | — |
| C-N1 | MINOR | 500/400 错误体泄漏后端细节(Redis 地址、Mongo 集群描述) | `ApiErrors.java:67-72` | 返回通用消息,细节只记日志 | — |
| C-N2 | MINOR | `/ids/strings` 遮蔽名为 `strings` 的生成器 | `IdController.java:64,70` | `ids.enabled` 时拒绝保留名 `strings`、`periodic` | — |
| C-N3 | MINOR | `remote` 存储缺少原生镜像提示与配置元数据提示 | `CosIdRuntimeHints.java:30-34`;`additional-spring-configuration-metadata.json` | 补上 | — |
| C-N4 | MINOR | 07 §4.1 示例 `codec: { radix: 62 }` 无法绑定 | `docs/architecture/07-runtime-and-spring.md` | 改为列表形式 | — |
| C-N5 | MINOR | roadmap 残留一处 `/v5` | `docs/architecture/roadmap.md:78` | 改为"不带版本前缀(D30)" | — |

C 路确认通过的:默认鉴权与拒绝启动、常量时间 token 比较、`no-store`、默认公开路径只有 health、输入限制、503 有界池、存储 API 的作用域(key/命名空间只在请求体中,未找到绕过)、每个生成器一个 Bean 且 `default` 为 primary、禁用生成器调用时才抛异常、customizer 顺序、关闭顺序、可选依赖条件装配、`initial-offset` 默认 1、MyBatis 批量、Spring Data JDBC、Axon 绑定生命周期与未绑定报错、Activiti/Flowable 依赖隔离、示例符合 D30。

### 3.D migration(main @ e272cdde)

迁移工具唯一的硬约束是"不与 4.x 重号",因此凡是合理配置下会**悄悄产生重号**的,一律定为 BLOCKER。

已核实正确:`next = lastMaxId + 1 + gap` 与两边语义一致;封存在第二次快照之后且为原子递增;"已封存"判断在 `--seal-gap` 变化时双向安全;只升不降的写入;四种后端的 5.0 管理写路径与存储格式一致(含 ZK owner 索引);T4 覆盖 4.x 全部机器号时间字段;StaticSlot `not-before = W`;`offset → initial-offset X+1`、`before-prefix`、`seq-reset-threshold`、`proxy → remote` 的转换;80 位布局校验;purge 需确认且只删已封存的过期周期 key。

| # | 严重度 | 发现 | 位置 | 修复 |
|---|---|---|---|---|
| D-B1 | BLOCKER | 4.x 中有独立 `namespace` 的生成器(key 为 `{ownNs}.{name}`)在 5.0 被放到默认命名空间下,从 1 重新开始 → 重号;按工具提示操作也无法映射 | `config/Legacy4x.java:225-240`、`ConfigConverter.java:399-403`、`source/*Source.java` | plan/apply 从 `--layout-config` 读出额外命名空间,把 `{ownNs}.{name}` 映射为 `{ns}/{name}`;至少报告并让 apply 拒绝;加测试 |
| D-B2 | BLOCKER(上调) | `config` 生成的生成器名与实际迁移的 key 不一致(两个 share 同时开启时 `__share__` → `default`,而 config 写成 `segment-share`;`--rename` 同理)→ 新 key 从 1 开始 → 重号 | `ConfigConverter.java:113-177`、`KeyMapper.java:52-53` | `config` 校验每个号段/周期生成器都对应 `apply-result.json` 中的 key,否则失败;无 `--layout-config` 时 plan 拒绝映射 `__share__` |
| D-B3 | BLOCKER(上调) | 存在无法映射的 key 时 `apply` 只告警继续(如分组格式 `yyyy-MM-dd`)→ 当期 key 在 5.0 从初始值开始 → 重号 | `Migration.java:355-359`(`ApplyGuardsTest` 还断言了这种行为) | 有未映射 key 时中止,除非显式 `--allow-unmigrated <key>` |
| D-B4 | BLOCKER(上调) | 未检测 4.x 存储分离(号段在 Redis、机器号在 JDBC):T4 读成 0、无人观察 `cosid_machine`,槽位准备在错误的目标上,5.0 运行时自建槽位时 `next_floor = 0` → 时钟落后的主机可能重发 4.x 已发的时间戳 | `Migration.plan/apply`、`ConfigConverter.java:339` | 有 `--layout-config` 时比对两种 distributor 类型与 `--from/--to`,不一致时拒绝或要求 `--machine-from/--machine-to` |
| D-M1 | MAJOR | 第二次快照之后新建的 4.x key(遗漏实例跨期)既不封存也不迁移,`verify` 也看不到;09 §2 的"不会重复"表述过度 | `Migration.java:330,426-447` | `verify` 标出结果中没有的 4.x key;可选:预先封存 4.x 当期与下一期 key;修正 09 §2 表述 |
| D-M2 | MAJOR(安全) | 凭据写进 `plan.json`/`apply-result.json`:`redacted()` 保留了 url 原文(Mongo 必须把密码写在 url 里,Redis/JDBC 同理),与 README 承诺不符 | `StoreLocation.java:127`、`MongoSource.java:47`、`MongoTarget.java:56` | `redacted()` 去掉 url 中的 userinfo 与密码参数;Mongo 支持 `user`/`password` 选项 |
| D-M3 | MAJOR | 4.x 秒级 Snowflake 的自定义 epoch 以秒为单位,被当成毫秒转换 → 5.0 布局错误(2038 左右溢出,或启动失败),M3 校验因两边都误读而通过 | `config/Legacy4x.java:154-160` | 秒级单位时 epoch × 1000(默认 epoch 除外) |
| D-N1 | MINOR | 命名空间前缀 `shop.` 也匹配 `shop.v2`,apply 会误封存别的命名空间 | `source/*Source.java` | 排除属于其他已知命名空间的 key,或要求命名空间不含 `.` |
| D-N2 | MINOR | `config` 不写 `tolerance`、`bits` 不取自 `result.slots()`,用了 `--slot-bits`/`--tolerance` 时 5.0 启动报 `NamespaceMismatchException` | `ConfigConverter.java:319,345` | 从 apply 结果取值 |
| D-N3 | MINOR(存疑) | W 只用目标存储时钟,跨存储迁移且目标时钟落后时 W 偏小 | `Migration.java:317-319` | 取源与目标时钟的较大者 |
| D-N4 | MINOR | `purge` 不检查存活租约 | `Migration.java:500-504` | 与 apply 一样检查 `live()` |

### 3.E 发布就绪(main @ e272cdde)

| # | 严重度 | 发现 | 位置 | 修复 | 核实 |
|---|---|---|---|---|---|
| E-B1 | BLOCKER | L14 未合入:发布的 `cosid-store-zookeeper` POM 会带入 Netty 4.1 epoll(Central 上 POM 不可改) | `store/zookeeper/build.gradle.kts:20-26` | 合入 #1342 | ✅ |
| E-B2 | BLOCKER | 文档承诺 `cosid-server` Docker 镜像,但 `main` 没有发布镜像的 workflow | `.github/workflows/`(无 docker-deploy);`documentation/docs/*/guide/server.md` | 新增 `docker-deploy.yml`:tag `v*.*.*` 与 `main`,`ahoowang/cosid-server` + ghcr + aliyun,多架构;`latest` 只在主版本 ≥ 5 的 GA tag 上打 | ✅ |
| E-B3 | BLOCKER | 文档说迁移工具 jar 随 release 附带,但没有任何 workflow 上传 | `package-deploy.yml`;`documentation/docs/*/guide/migration/from-4x.md` | package-deploy 增加 job:`:migration:shadowJar` + `gh release upload`(需 `contents: write`) | ✅ |
| E-M1 | MAJOR | 4.x 的 document-deploy 在 release 时部署到同一个 Pages 站点,GA 后 4.x 的补丁发布会覆盖 5.0 文档站 | 分支 `4.x`:`document-deploy.yml` | 4.x 去掉 release 触发或部署到子路径(改 4.x 需用户同意);**RC 必须以 pre-release 发布**,否则 5.0 RC 文档会覆盖当前 4.x 站点 | — |
| E-M2 | MAJOR | 文档站 javadoc 目录名漏了 `integrations/*` 映射,会生成 `cosid-integrations-jackson` | `document-deploy.yml:75-79` | 增加 `integrations/*) artifact="cosid-${module#integrations/}"`,或改由 Gradle 输出名称 | ✅ |
| E-M3 | MAJOR | 发布到 Central 前没有任何校验 job(发布不可逆) | `package-deploy.yml:66-83` | 部署 job `needs:` 一个跑 `check`(或至少 javadoc + checkPublishedMetadata + 发布到本地仓库)的 job | — |
| E-N1 | MINOR | AGENTS.md 仍写"4.x 代码在 `main`" | `AGENTS.md:9-10` | 改为 `git show 4.x:<path>` | — |
| E-N2 | MINOR | `checkPublishedMetadata` 只在 spring 模块上运行,且不拒绝 `enforced-platform` | `build-logic/.../cosid.spring.gradle.kts:82-128` | 挪到 `cosid.library`,并拒绝任何 enforced-platform | — |
| E-N3 | MINOR | 没有自动检查 BOM 是否列全所有发布模块(目前 16 个一致) | `bom/build.gradle.kts` | 增加根项目检查 | — |
| E-N4 | MINOR | POM 的 SCM `connection` 缺 `scm:git:` 前缀,没有 `developerConnection` | `cosid.publish.gradle.kts:56-59` | 补齐 | — |
| E-N5 | MINOR | roadmap 链接的 rc-review.md 尚未在 main 上 | — | 本 PR(#1344)合入后解决 | ✅ |
| E-N6 | MINOR | 基准代码里残留 `TODO(R1)` | `benchmark/v5/.../V5.java:39,75` | 删除 | — |
| E-N7 | MINOR | jar 内资源(Lua、JDBC schema SQL)与 `server/Dockerfile` 无许可证头 | — | 补头 | — |
| E-N8 | MINOR | codeql `paths-ignore` 仍是 4.x 路径 | `codeql-analysis.yml` | 更新 | — |
| E-N9 | MINOR(存疑) | `release: created` 在保存草稿 release 时也会触发 | `package-deploy.yml:16-17` | 改为 `published` | — |

E 路确认通过的:artifactId 与发布范围、示例/基准/server/迁移工具不发布、源码与 javadoc jar 与签名与 POM、BOM 当前完整、无 enforced-platform 泄漏、Docker 镜像名不与 4.x `cosid-proxy` 冲突、文档站侧边栏与重定向、README 坐标、renovate 与 CI 分支、无 `@Disabled`、临时排除项已清除。
发布前手工步骤:`gradle.properties` 的 `version` 改为 `5.0.0-RC1`,`server/README.md:64` 的硬编码版本同步。

## 4. 修复计划

合计:**BLOCKER 10**(B 1、C 2、D 4、E 3)。每个工作包一个 Draft PR(先开 PR 再推送 WIP),遵守 CPU 规则,只在 CI 上验证。

| 工作包 | 内容 | 归属会话 | PR | 状态 |
|---|---|---|---|---|
| WP-Redis | **B-B1**、B-N4 | R2-Redis'(会话 `r2-redis-b-b1`) | [#1355](https://github.com/Ahoo-Wang/CosId/pull/1355) | ✅ 已合入 `abb5cada`(2026-10-28,用户同意转 ready):7.0 根因为集群总线未传播副本角色,auto 改为逐主节点取 `max(INFO connected_slaves, CLUSTER REPLICAS)`,部分有副本时 open 失败;`wait>0` 时不跟随 MOVED(README 已写明) |
| WP-JDBC | B-M1、B-N2 | R2-JDBC | [#1354](https://github.com/Ahoo-Wang/CosId/pull/1354) | ✅ 已合入 `d6264e11`;偏差:SQL Server 无论排序规则都忽略尾随空格,方言改为拒绝以空格结尾的 key(不合并为同一 key,唯一性不受损),总调度认可 |
| WP-ZK | B-N1;**E-B1** 即 #1342 | R2-ZK | [#1342](https://github.com/Ahoo-Wang/CosId/pull/1342) | ✅ 已合入 `a0104227` |
| WP-server | **C-B1**、**C-B2**、C-M1、C-N1、C-N2、B-N3 | R4-A | [#1349](https://github.com/Ahoo-Wang/CosId/pull/1349) | ✅ 已合入 `2068c48f` |
| WP-spring | C-N3 | R3-S | [#1353](https://github.com/Ahoo-Wang/CosId/pull/1353) | ✅ 已合入 `fa515223`(2026-10-28) |
| WP-migration | **D-B1**、**D-B2**、**D-B3**、**D-B4**、D-M1、D-M2、D-M3、D-N1–N4;修正 09 §2 表述 | R4-C | [#1350](https://github.com/Ahoo-Wang/CosId/pull/1350) | ✅ 已合入 `40d3e13c` |
| WP-lease | A-M1 | R1-C | [#1351](https://github.com/Ahoo-Wang/CosId/pull/1351) | ✅ 已合入 `a68a0191` |
| WP-time-codec | A-N1、A-N2、A-N3、A-N6、A-N7 | R1-A | [#1352](https://github.com/Ahoo-Wang/CosId/pull/1352) | ✅ 已合入 `0ae7c45c`(2026-10-28) |
| WP-segment | A-N4、A-N5 | R1-B | [#1347](https://github.com/Ahoo-Wang/CosId/pull/1347) | ✅ 已合入 `3e25d483` |
| WP-release | **E-B2**、**E-B3**、E-M2、E-M3、E-N2、E-N3、E-N4、E-N6–N9 | R5-B | [#1348](https://github.com/Ahoo-Wang/CosId/pull/1348) | ✅ 已合入 `9de77578` |
| WP-docs | E-N1、C-N4、C-N5;09 §2 表述 | 总调度(本 PR) | #1344 | ✅ 已在本 PR 修改 |
| WP-migration-doc | D32 停机手工迁移(09 §6、from-4x 中英文) | 会话 `docs-manual-migration` | [#1359](https://github.com/Ahoo-Wang/CosId/pull/1359) | ✅ 已合入 `f2f21f75`(2026-10-28) |
| WP-alpha1 | D33:版本号 `5.0.0-alpha.1`、alpha 提示、4.x 文档入口、release notes(含门槛已知问题)、PR 上的文档站构建检查;4.x document-deploy 只保留手动触发 | 会话 `alpha1-prep` | [#1360](https://github.com/Ahoo-Wang/CosId/pull/1360)(main)、[#1361](https://github.com/Ahoo-Wang/CosId/pull/1361)(4.x) | ✅ 已合入:#1360 `2f6b4582`、#1361 `3937b6c2`(4.x)(2026-10-28) |
| 需用户决定 | E-M1:改 `4.x` 的 document-deploy(GA 后避免覆盖 5.0 文档站);RC 以 pre-release 发布 | 用户 | | ✅ 已决定(2026-10-28,D33):先发 `5.0.0-alpha.1`(pre-release,不做兼容性承诺);4.x document-deploy 去掉 release 触发 |

## 4.1 当前待办(交接用)

**收尾规则(用户 2026-10-27):** 修复 PR 转为 ready 且 CI 全绿后审查;**审查通过即 squash 合入,不通过则登记到 §4.2**,不阻塞其他 PR。

**收尾阶段(用户 2026-10-27 最新指示):** 不开新任务;已 ready 的 PR 等 CI 全绿后合并;最后登记文档。本机代码必须全部推送到 GitHub。

**收尾结果(2026-10-27):** 用户授权后 #1349、#1351、#1354 已转 ready。已合入 8 个修复 PR;BLOCKER 已解决 9/10,**仅余 B-B1(#1355,Draft)**。**2026-10-28 更新:#1355 合入(`abb5cada`),10 个 BLOCKER 全部解决**。其余 Draft:#1352、#1353、#1355、#1356。发布门槛 FAIL(见 §4.2 #1357)。RC1 受阻:B-B1 与门槛回退(codec、MySQL store)。

**已合入:** [#1346](https://github.com/Ahoo-Wang/CosId/pull/1346)(Mongo 超时测试去抖,`6efa7d69`)、[#1347](https://github.com/Ahoo-Wang/CosId/pull/1347)(WP-segment,`3e25d483`)、[#1350](https://github.com/Ahoo-Wang/CosId/pull/1350)(WP-migration,D-B1–B4 等,`40d3e13c`)、[#1342](https://github.com/Ahoo-Wang/CosId/pull/1342)(WP-ZK,E-B1 + B-N1,`a0104227`)、[#1348](https://github.com/Ahoo-Wang/CosId/pull/1348)(WP-release,E-B2/E-B3 等,`9de77578`)、[#1354](https://github.com/Ahoo-Wang/CosId/pull/1354)(WP-JDBC,B-M1、B-N2,`d6264e11`)、[#1349](https://github.com/Ahoo-Wang/CosId/pull/1349)(WP-server,**C-B1**、**C-B2**、C-M1 等,`2068c48f`)、[#1351](https://github.com/Ahoo-Wang/CosId/pull/1351)(WP-lease,A-M1,`a68a0191`)。

**#1356 已合入 `2c5f8d47`(2026-10-28)。** **PR 审查(2026-10-27,全部为 Draft):** #1352(WP-time-codec,A-N1–N3、A-N6、A-N7)通过;#1353(WP-spring,C-N3)通过;#1356(Mongo range E11000 重试的确定性测试)通过,CI 全绿。#1352、#1353 唯一的失败是 mongo:8.0,已重跑。#1355 不通过,见 §4.2。#1357 共 +3,041,743 行(多为基准 JSON 原始数据),即使有 CI 也不应合入 main,报告应只提交摘要。

**发布门槛:** run 40534275762 结论 **FAIL**(5/8 组),见 §4.2 #1357;D21 未满足,RC 阻断。

**门槛第二次运行(2026-10-28,[run 40623986450](https://github.com/Ahoo-Wang/CosId/actions/runs/40623986450),main `a68a0191`,含 #1351/#1354,不含 #1352/#1355):FAIL,6/8 组。** 重跑后仍失败:
- 确定的回退:codec radix36/62 编解码吞吐 −9~−18%(原因见 #1345);MySQL acquire/renew/reserve 分配 +25~+67%、acquire 耗时 +15%;ZK reserve 分配 +19%;SegmentNoPrefetch 分配 24→32 B、吞吐 −6%。
- 接近噪声:Mongo reserve 分配 +5%、p99/p99.9 +2~4%;Redis reserve p99.9 一行;ZK renew 分配 +2%;MySQL 多行延迟 +0.8~8%。
- **门槛判定本身过严**:阈值只比 4.x 宽 0.3%~1.5%(例如 renew 906.4 vs 913.5 µs,阈值 908.9),低于共享 runner 的噪声,同一代码两次运行也会有不同的行失败。需要用户裁定 D21 的判定口径(例如吞吐与平均延迟 5%、p99.9 10%、分配 5% 或 +64 B,可重复失败才算回退)。
- 判定口径已定为 **D34**(容差:吞吐/平均 5%、p99/p99.9 10%、分配 max(5%, +64 B),重跑仍失败才算回退)。
- 新偶发测试(2026-10-28):`spring/autoconfigure` 的 `CosIdRuntimeHintsTest.theStoreTypeHintsNameEveryStore` 用 `ClassPathResource("META-INF/spring-configuration-metadata.json")` 读取,classpath 上有多份同名文件(含 Spring Boot 自身),取到哪份取决于顺序(#1362 的 push Codecov run 40640793637 失败 3 次,同提交的 pull_request run 通过)。修复:读取本模块生成的那份(按 jar/目录过滤,或 `getResources` 合并后查找)。并入下一个修复会话。
- ✅ codec 修复 [#1362](https://github.com/Ahoo-Wang/CosId/pull/1362) 已合入 `81a9ef59`(dry-run:decode +8~13%、encode +30~32%,分配 88 B)。
- ✅ **`v5.0.0-alpha.1` pre-release 已创建**(2026-10-28,目标 `81a9ef59`):**已核对**:Packages Deploy(校验、构建、Central、GitHub Packages、迁移工具 jar)与 Docker Image Deploy 全部成功;Central 06:34 可解析(`cosid-bom`、`cosid-core`、`cosid-spring-boot-starter`、各 store);Docker Hub 有 `5.0.0-alpha.1`、无 `latest`;release 附 `cosid-migration.jar`;文档站已由 tag 部署(run 40644322052),页面显示 `5.0.0-alpha.1`、alpha 提示与 4.x 文档入口。✅ main 已改回 `5.0.0-SNAPSHOT` 并修复 `CosIdRuntimeHintsTest`([#1363](https://github.com/Ahoo-Wang/CosId/pull/1363),`3a1cdaae`)。**alpha.1 发布步骤全部完成。**
- **第 1 轮完成**(2026-10-28,[#1364](https://github.com/Ahoo-Wang/CosId/pull/1364)):95 条,BLOCKER 1(R1-x-07)、MAJOR 19(10 条触及冻结面)、MINOR 75;总调度抽查核实(R1-x-03/06/07、R1-core-04、R1-jdbc-01 无 `owner_key` 索引、R1-server-01 等)。修复分组与顺序(一次一个子会话):
  1. ✅ **WP-server**([#1365](https://github.com/Ahoo-Wang/CosId/pull/1365) `f641e1ae`):`docs.public` 开关、虚拟线程默认开启、匿名内网部署文档、R1-server-01(admin 改白名单)/02/03。
  2. ✅ **WP-migration-safety**([#1369](https://github.com/Ahoo-Wang/CosId/pull/1369) `598fa80f`;R1-jdbc-06 迁移侧移到 WP-store-contract):R1-x-07 先做止血(`config`/`plan` 遇到与 `cosid.namespace` 不同的 `cosid.generator.namespace` 时拒绝并报告,T4 纳入全局命名空间)、R1-doc-01(未封存回滚会重号)、R1-x-04、R1-jdbc-06 迁移侧。
  3. **WP-API-freeze**:设计已定稿并合入([#1371](https://github.com/Ahoo-Wang/CosId/pull/1371) `6d69bf5a`,`api-freeze.md`,D39–D43);实现按 api-freeze.md §10 分 6 个 PR 串行:✅ PR1 值类型 builder([#1372](https://github.com/Ahoo-Wang/CosId/pull/1372) `ba294a5f`)→ ✅ PR2 cosid80 布局/acquireTimeout/自定义生成器([#1373](https://github.com/Ahoo-Wang/CosId/pull/1373) `7225296a`;另加 `timeBits + seqBits ≤ 63`,PR5 迁移须同样校验) → ✅ PR3 命名 machine + slotOffset([#1374](https://github.com/Ahoo-Wang/CosId/pull/1374) `a6e91a88`;五后端元数据加 `slotOffset`,兼容 alpha.1 数据;R1-int-01) → ✅ PR4 Spring 配置冻结([#1375](https://github.com/Ahoo-Wang/CosId/pull/1375) `8144607a`:`cosid.machines`、cosid80 布局键、D38 namespace 默认应用名、`custom`+`ref`、`acquire-timeout`、未知 `cosid.*` 键启动失败、health 名 `cosid`、`metrics.generated.enabled`) → ✅ PR5 迁移命名 machine([#1376](https://github.com/Ahoo-Wang/CosId/pull/1376) `385d363e`:共享 `cosid.generator.namespace` 一次性迁移为 `cosid.machines.generator`,默认 slotOffset = `2^nodeBits − 2^bits` 并报告 headroom,解除 #1369 止血,63 位规则前置) → ✅ PR6 用户文档([#1377](https://github.com/Ahoo-Wang/CosId/pull/1377) `2fc23371`:§9 中英文、11 新行与测试锚点、api-freeze.md 定稿、javadoc、`docs/release/5.0.0-rc.1.md` 草稿)。**API 冻结全部完成(2026-10-28)**;下一步 WP-store-contract → WP-perf → 门槛重跑。原条目:先在 03/05/07 定稿再改代码:D36(R1-x-03/05)、D37(R1-x-06,命名 machine,含 R1-x-07 的最终修复)、D38(namespace 默认应用名)、R1-core-01(公开 record 演进策略)、R1-core-02(自定义生成器)、R1-core-03(`acquireTimeout`)及冻结面 MINOR。
  4. **WP-store-contract**(✅;✅ A 选项与 key 契约 [#1378](https://github.com/Ahoo-Wang/CosId/pull/1378) `48bcb039`:`StoreProvider.options()` 声明 + 宽松匹配 + 未知键报错,`StoreKeys` 可移植 key 规则 + TCK,timeout 必须为正;✅ B JDBC [#1379](https://github.com/Ahoo-Wang/CosId/pull/1379) `16687821`:`(namespace, last_owner_key)` 索引 + 三段 union 候选查询(各库 EXPLAIN 断言),alpha.1 表由 initSchema 补建;R1-jdbc-06 恢复 4.x 值校验、去掉 `last_insert_id()` 兜底、`fetchPlan=transaction`;迁移侧校验无专用测试(需代理,接受)。**WP-store-contract 完成**):R1-spi-01/R1-spring-01(options 键规范与未知键报错)、R1-spi-02(可移植 key 语法)、R1-jdbc-01(`cosid_slot` 索引,DDL)、R1-jdbc-06(SQL 代理下 generated key 校验,含迁移侧)。
  5. **WP-perf**(代码完成;门槛第三次运行 [run 40772963183](https://github.com/Ahoo-Wang/CosId/actions/runs/40772963183) @ `1ed9aaeb`:**FAIL 3/8**。codec-filler、redis、segment、snowflake、spring-start 5 组全部 PASS,codec 和 P6 回退已消除。重跑后仍失败的行:
     - Mongo:reserve avgt 1060→1152 µs(+8.7%),分配 31.1→32.8 KB(+5.4%);renew p99.9 5840→6998 µs(+20%)。
     - MySQL:延迟全部通过;renew 分配 16.4→20.5 KB(+25%),p99 1561→2079 µs;reserve 分配 8.6→14.0 KB(+64%),疑与 R1-jdbc-04 有关(upsert 5 个参数,update 分支的 generated keys 有 2 行)。
     - ZooKeeper:reserve 分配 5.8→6.5 KB(+12%),p99 +15%,p99.9 +14%;avgt 通过。
     以上都在 I/O 主导的存储操作上(µs 级,分配为 KB 级),按 D34 仍算回退。安排 WP-perf P3 逐项定位修复(先用分配剖析,不放宽容差);确实无法修复的,作为 D34 补充登记给用户决定。
     ✅ **P3** [#1392](https://github.com/Ahoo-Wang/CosId/pull/1392) `4eebc74a`(未放宽 D34,没有需要登记的不可消除项)。本地对比:
     - MySQL:未封存的已有 key 的 reserve 改为一条 2 参数的 `update … last_insert_id(…)`(与 4.x 相同),分配 14.1→8.7 KB(4.x 8.7);renew 首次尝试只读存储时钟,分配 20.4→17.0 KB(4.x 16.5)。
     - Mongo:filter 和 pipeline 直接用 BSON 值构造，renew 的返回只投影 lease 需要的字段;reserve 分配 33.9→28.4 KB(4.x 31.3),renew 分配 43.9→30.7 KB。
     - ZooKeeper:记住上次写入的版本，先按该版本做一次 `setData`,一次往返;未命中则回退为先读后写。reserve 分配 26.8→16.0 KB(4.x 25.7)。
     审查遗留(后续加固,不阻塞门槛):按版本写的 ZK 快路径存在 ABA 前提——节点被删除并重建后,版本号可能回到原值。目前只有迁移工具的 `delete` 会删除已封存的 key,本身已要求实例停止。加固项:记住 czxid,`setData` 返回的 czxid 不一致即报存储回退并清除记忆;把"持有期间节点不得删除"写进 06。 ✅ 已由 [#1393](https://github.com/Ahoo-Wang/CosId/pull/1393) `45f6a82d` 完成。局限:ZK 无法以 czxid 为写入条件，这次写入本身撤不回，只能保证该号段不发出且报错，所以首要防线是不变式(06 §2.4、ZK 后端文档、09/迁移指南:`purge` 必须在所有 5.0 实例停止后执行)。
     → 第四次正式门槛 [run 40797246965](https://github.com/Ahoo-Wang/CosId/actions/runs/40797246965) @ `4eebc74a` 运行中。
     **D44(用户 2026-10-28):端到端发号是最终指标，组件只要求不过度退化。** 门槛改为:端到端行(P1–P8、P11、P12)按 D34 硬判;组件行(P9、P10)按 D34 判 PASS,超出 D34 但未超过软上限(吞吐/平均 20%、尾延迟 30%、分配 25%)判 WARN,超过软上限才 FAIL。
     - 按此口径回看第三次运行:Mongo reserve +8.7%/+5.4%、renew p99.9 +20%,ZK reserve 分配 +12%、尾延迟约 15%,都只是 WARN;MySQL reserve 分配 +64%、renew p99 +33% 仍为 FAIL,已由 P3 修复。
     - 缺口:Mongo 和 ZooKeeper 只有存储原语基准，没有端到端号段链基准。
     - 实现交给子会话:WP-perf P4 = `benchmark/compare` 的行分类与 WARN 档 + Mongo/ZK 号段链基准(两侧)+ 10 §1.2/§1.3。
     - ✅ **P4** [#1394](https://github.com/Ahoo-Wang/CosId/pull/1394) `ad8a1499`:
       - `compare` 的 `SCENARIOS` 单表定义行的类型，未列出的类按端到端判定，测试保证每个基准类都在表中;
       - 组件行超出 D34 时判 WARN,`run-release`/`aggregate-release` 显示为 "PASS (n WARN)";
       - 新增 Mongo/ZK 号段链基准(两侧),四个后端的 P7 在 5.x 侧都走 `CosIdRuntime` 生产路径;
       - 10 §1.2/§1.3 已同步，删除了被跟踪的 `__pycache__`;
       - remote 端到端行暂不加，可作为 GA 后 5.x 自身的趋势行。
     - 第四次运行(run 40797246965 @ `4eebc74a`)已取消：其口径和基准集已被 P4 取代。
     - 第五次正式门槛 [run 40800116981](https://github.com/Ahoo-Wang/CosId/actions/runs/40800116981) @ `ad8a1499`(D44):**FAIL 3/8,但不是性能回退**。
       - **原因**:5.x 侧的 Redis、MySQL、Mongo 号段链基准在 setup 阶段就失败了(`no StoreProvider of type [redis]`),所以报告显示 "missing"。`benchmark/v5` 的 jmhJar 没有合并 `META-INF/services`,只剩 ZooKeeper 一个 provider。
       - **有效数据**:存储原语行全部 PASS(Redis 5.x 约为 4.x 的一半耗时、四成分配),只有 ZK renew sample 一行 WARN;ZK 端到端链 PASS;segment、snowflake、codec-filler、spring-start 全部 PASS。
       - **修复**(交给子会话):jmhJar 合并 service 文件;`run-release` 把某一侧数据缺失或 JMH `<failure>` 当作装配错误直接失败，不记成回退行;CI 加基准装配自检;基准改动交回前必须先跑 `release-gate-check/**` dry-run。修复后由协调会话重跑正式门槛。
       - **教训**:P4 只用 javac 加类路径做了本地 smoke,没有跑 dry-run,所以 jar 装配问题没有暴露。
     - ✅ [#1398](https://github.com/Ahoo-Wang/CosId/pull/1398) `632fdfdf`:
       - 基准约定插件合并各模块的 `META-INF/services`;
       - `checkJmhJar` 核对 jar 内的 provider,CI Check 与 run-smoke/run-release 都先过这一步;
       - 某一侧数据缺失视为装配错误(`compare` exit 4),不计入回退行;
       - AGENTS.md 增加 gotcha。
       dry-run [run 40808254473](https://github.com/Ahoo-Wang/CosId/actions/runs/40808254473) 已证实四个后端的 5.x 链基准都有数据(dry-run 不作为结果)。
     - 第六次正式门槛 [run 40810418780](https://github.com/Ahoo-Wang/CosId/actions/runs/40810418780) @ `632fdfdf`(D44):**7/8 组 PASS,所有端到端行 PASS。**
       - 四个后端的号段链 `generate_t1`(step=10)吞吐:Mongo 48→452、MySQL 0.51→442、Redis 2.5→409、ZK 0.35→354 ops/µs。
       - 4 个组件行 WARN:Mongo reserve/renew sample、MySQL renew p99.9 +15%、ZK renew sample。
       - **唯一 FAIL**:组件行 `MySqlStoreBenchmark.reserve_t1` 的 sample p99.9。首次 29.0→79.0 ms,重跑 61.9→83.9 ms(+35.6%,超过 30% 软上限)。同一行的 avgt(784→720 µs)、p99(3.2 ms 持平)和分配(8.7 KB 持平)全部通过。
       - 4.x 自己在两次运行之间的 p99.9 就从 29 ms 变到 62 ms,这个指标的噪声大于容差。第三次运行时同一行 p99.9 还在 4.2/4.4 ms(runner 不同)。
       - 另有两行端到端链基准 4.x 更高，只因 4.x 误差太大才判 PASS:MySQL t16 step=10 102.0 vs 68.7,ZK t16 step=10 83.2 vs 53.6 ops/µs;同后端 4.x t4 只有 1.3/4.7,呈两极分化。v5 多线程上限约 43–71 ops/µs,与内存 P5 t16 的 42 一致，疑为号段游标的单点 CAS 竞争。并入 P5 调查：判断是否值得在 GA 前优化高并发热路径，以及对单调性和空洞的代价。
       - 处理：交给子会话做 P5,判断这个尾延迟是不是结构性的(P3 改为先 update、1690 回退、连接或提交路径)。若不是结构性的，作为数据完整的例外交用户裁定(RC1 准入允许"每个例外有数据并经用户裁定")。✅ P1 修回退 [#1380](https://github.com/Ahoo-Wang/CosId/pull/1380) `1ed9aaeb`:P6 本地 22k→71k ops/ms、32→0 B/op,P5/P6 走生产路径,JDBC SQL 预构建,ZK 分配削减;✅ P2 门槛工具 [#1381](https://github.com/Ahoo-Wang/CosId/pull/1381) `ec0d4b71`:D34 容差、compare_test 15 例、MySQL P10 不对称修正):R1-core-04/05/06、R1-jdbc-02、R1-zk-01、修正 MySQL 基准不对称 → 门槛实现 D34 → 按 D34 重跑门槛。
       - **P5 结论(2026-10-29)**:
         1. MySQL `reserve_t1` 的 p99.9 **不是结构性问题，是运行顺序造成的**。在 CI runner 上按 ABBA 顺序各跑 8 次，合并后 p99.9:5.x 17.4 ms,4.x 29.1 ms;≥20 ms 的样本每万次 8.9 对 12.6;均值 612 对 677 µs。紧跟另一侧之后运行的那次，p99.9 中位数是 32.5 ms,紧跟同侧之后是 15.7 ms。门禁重跑时 5.x 总在 4.x 之后。处理：门禁对存储行改为 ABBA 交替，属于测量流程的修正，容差不变(§4.3 第 16 项)。
         2. t16 链基准:4.x 每个 iteration 在 0.1 到 166 之间摆动，它的高值只出现在预取恰好领先的窗口;5.x 在 t4/t16/t64 下都稳定(MySQL 约 68、ZK 约 54、内存 42–44),上限来自号段游标这一个计数器。
         3. AtomicLong 参照：在 4 核 runner 上做 smoke,5.x 在 t1/t4/t16/t64 下分别是 AtomicLong 的 99%/57%/63%/51%,4.x 是 33%/32%/38%/35%;在 M4 Pro 上,5.x 是 91%/81%/80%/76%。4.x 宣称的"约 89% 接近 AtomicLong",5.x 在单线程下做到了;同一台 runner 上 4.x 只有 33%。
         4. **GA 前不做分条带。** 分条带会让跨线程不再递增，并留下空洞，API 冻结后这属于语义变化。可选的零语义代价改进是给游标字段做填充、避开伪共享，先测量再决定，列为 GA 前的可选项。
       - ✅ [#1404](https://github.com/Ahoo-Wang/CosId/pull/1404) `457c7266`:
         - 新增 AtomicLong 参照基准，只报告不判定;
         - 存储组(P7/P10)按 ABBA 交替运行，同一侧两次运行的原始数据合并后重新计算统计量(`merge-results`);
         - dry-run 40828410128 通过。dry-run 的参照数据(smoke 参数)如下:t1 时 4.x 是 AtomicLong 的 73–78%,5.x 是 101%;t4 时 4.x 42–44%,5.x 75–77%;t16 时 4.x 45%,5.x 68–72%。
       - ✅ **第七次正式门禁 [run 40832851823](https://github.com/Ahoo-Wang/CosId/actions/runs/40832851823) @ `457c7266`:PASS,8 组中 0 组失败**(基线 4.1.1,依赖来自 Spring Boot BOM,存储组按 ABBA 顺序运行,D44 口径)。**这是 RC1 的性能依据。**
         - 所有端到端行 PASS。号段链 t1 step=10 吞吐，4.x → 5.x(ops/µs):Mongo 1.75 → 392、MySQL 0.47 → 209、Redis 0.86 → 211、ZK 0.28 → 405;内存号段 t1 141 → 418,t16 24.6 → 44.8。
         - AtomicLong 参照(正式参数):t1 时 5.x 达到 AtomicLong 的 99%,4.x 为 34%;t4 时 5.x 65–68%,4.x 37%;t16 时 5.x 63–65%,4.x 35%。
         - 3 个组件行 WARN(不阻塞):Mongo reserve avgt +5.9%,Mongo reserve p99 +21%,MySQL acquire avgt +5.5%。
         - 第六次运行中唯一失败的 MySQL reserve p99.9,这次是 3.3 → 2.3 ms,PASS,与 P5 的"运行顺序噪声"结论一致。
  6. ✅ **WP-docs**:D1 [#1382](https://github.com/Ahoo-Wang/CosId/pull/1382) `5ada30ea`(Kubernetes 部署指南中英文、R1-mongo-04 分片收窄为"未验证、cosid 集合不分片"、R1-x-01/08、R1-doc-03/04/05/07;R1-doc-01/02 核实已修)+ D2 [#1383](https://github.com/Ahoo-Wang/CosId/pull/1383) `8438d0d6`(设计文档 01–08 与实现对齐,11 锚点 287 个全部复核、补 2 行)。
  7. **依赖与 MINOR**(进行中;MINOR 分诊 ✅ [#1395](https://github.com/Ahoo-Wang/CosId/pull/1395) `feb0edd5`,ga-review §9:75 条中已修 32、本次修 25(stores 9 / core-javadoc+spring-config 8 / docs+migration 8,分 3 个 PR 串行:✅ stores [#1396](https://github.com/Ahoo-Wang/CosId/pull/1396) `520b1426`;✅ core-javadoc+spring-config [#1397](https://github.com/Ahoo-Wang/CosId/pull/1397) `5b0eb1d3`(含 readiness 改名 `cosidServerStore`,修复 Boot 4.0 下 `- friendly: {}` 的绑定问题);✅ docs+migration [#1399](https://github.com/Ahoo-Wang/CosId/pull/1399) `926a4b2b`。**RC 前的 25 条全部完成**)、GA 后 16、不修 2。协调会话对 §9.4 待定 3 项的决定见 §4.3 第 14 项):Dependabot 11 条全部在 `documentation/pnpm-lock.yaml` → ✅ [#1384](https://github.com/Ahoo-Wang/CosId/pull/1384) `b2e40694`(pnpm overrides:vite 6.4.5、esbuild 0.25.12、source-map-js 1.2.2、vue 3.5.44;73 页渲染与 main 对比一致);剩余 MINOR 先出分诊表(已修/本次修/GA 后/不修)再按主题修。Renovate:✅ #1366 jackson 2.22.4 已合入(`0e05cf23`);#1367 cosid4 4.1.1 暂缓——它改变门槛对比的 4.x 基线,待本轮门槛结论后再定(见 §4.3 第 12 项)。
  8. ✅ **WP-ci 重构 CI(工程质量、执行效率)**([#1385](https://github.com/Ahoo-Wang/CosId/pull/1385) `157eb656`;试验 PR #1386–#1390 验证后已关闭)。实测:
     - 改 core:75.1 → 约 35–38 runner·分钟(缓存预热后),`check` 由 3 遍变为 1 遍,关键路径约 6 分钟;
     - 只改单个 store:约 67 → 约 20–29;
     - 只改 docs:0.9;
     - 汇总检查 `CI` 在 job 失败时确实变红。
     - 协调会话取舍:不采用"PR 上每个 store 只跑一个版本"的进一步提速(只省约 8 分钟,但当前版本的问题要到 nightly 才暴露)。目标修订为:单模块改动 ≤ 30、全量运行 ≤ 40。
     - 合入后 main 首次运行 CodeQL 时,旧配置 `analyze/language:java` 会成为陈旧配置,可在 Security → Code scanning 中删除(仓库设置,由用户操作)。
     - 试验运行还发现了 #1380 的顺序回退,已由 #1391 修复。
     原计划:用户 2026-10-28 提出;排在文档依赖 PR 之后、MINOR 分诊之前)。现状(以 #1375 一次推送为样本):
     - 每次推送约 59 runner·分钟:Integration Test 22 个 job 约 37 分钟,CodeQL autobuild 5 分钟,Codecov 在 `pull_request` 和**所有分支的 `push`** 上各跑一次全量 `check`(8–9 分钟)。同一份 `./gradlew check` 每次推送跑 3 遍(IT Check + Codecov×2)。关键路径约 9 分钟。
     - 没有 `concurrency`:同一个 PR 连续推送时,旧运行跑到底。没有 `timeout-minutes`:mongo:8 卡住时跑满 6 小时默认值。
     - 不支持版本的 probe(Mongo 4.4/5.0、ZK 3.6/3.7、Redis 6.2、JDBC probe×3,共 8 个 job)每个 PR 都跑;store 和 Spring job 不看改了什么,全部都跑。
     - 约 10 个 job 重复写 checkout + setup-java + `cache: gradle`,没有使用 `gradle/actions/setup-gradle`(构建缓存、wrapper 校验、依赖图提交)。
     - labeler 的 `cancel-in-progress` 在 PR 检查中留下 CANCELLED 噪声。branch protection 只把 `Conventional Commits title` 设为必需检查;"全绿才能合并"目前靠人工判断。
     - 目标(可度量):PR 每次推送 ≤ 30 runner·分钟(代码改动)、只改 docs 时 ≤ 2 分钟;关键路径 ≤ 8 分钟;每次推送只跑 1 遍 `check`;所有 job 都有超时;旧运行自动取消;一个汇总检查 `CI` 代表全部结果。
     - 方案:
       ① 删掉独立的 Codecov workflow,覆盖率在 IT `Check` 的同一次 `check` 中上传(按 Codecov flags 拆分,保留 patch 80%)。
       ② 所有 PR workflow 加 `concurrency: ${{ workflow }}-${{ PR 号 }}`,`cancel-in-progress: true`;push 到 main 不取消。
       ③ 新增 composite action `.github/actions/setup-build`(checkout、JDK、setup-gradle,PR 上缓存只读、main 上可写)。
       ④ 新增 `changes` job,用路径过滤决定 store-*/server/spring 是否运行:core、tck、构建脚本改动时全部运行。
       ⑤ probe 和 Spring 兼容全矩阵移到 nightly + main;PR 上 Spring 只跑 最低 Boot/J21 + 当前 Boot/J25 两个对角组合。
       ⑥ CodeQL Java 在 PR 上用 `build-mode: none`;main 和每周 schedule 保持完整构建。
       ⑦ 所有 job 加 `timeout-minutes`。
       ⑧ 新增汇总 job `CI`(`needs` 全部、`if: always()`,skipped 视为通过、failure 或 cancelled 视为失败),作为合并依据。
       ⑨ labeler 改为不取消。
       ⑩ 用 setup-gradle 提交依赖图,让 Dependabot 看到 Gradle 依赖。
       ⑪ 失败时上传 JUnit 报告并生成注释。
     - 不在范围内:release-gate、package-deploy、docker-deploy、document-deploy 这些发布链路只加 timeout,不重构。
     - 验证:同一提交在旧/新 workflow 下的 runner·分钟和关键路径对比写进 PR 描述;docs-only、单 store、core 三种改动各推一次验证路径过滤。
  6. **WP-docs**:R1-x-01/08、R1-doc-01–07、R1-mongo-04(加分片集群 CI 或收窄承诺)、k8s 部署指南、设计文档与 11 对照表同步、javadoc。
- 原派发队列(一次一个子会话):① codec(✅)→ alpha.1 发布(✅)→ ② **GA 前审查第 1 轮**(下一个)(§6)→ ③ 门槛实现 D34(`benchmark/compare` 及测试、10 §1.3)→ ④ 修复:第 1 轮发现 + MySQL 分配 + 号段与 ZK 分配(按严重度排序,一次一个)→ ⑤ 在 main 上按 D34 重跑门槛 → RC1(用户确认)→ 第 2 轮审查 → GA。alpha.1 不等门槛(D33),数据写入 release notes 已知问题。

**协调(2026-10-28,用户同意执行顺序):**
1. 第一次门槛测的是修复合入前的 `614d5df9`;#1354(JDBC 排序规则与超时)、#1351(存储 I/O 移出锁)会改变 MySQL 与租约的数据。因此先在 main 上重跑:[run 40623986450](https://github.com/Ahoo-Wang/CosId/actions/runs/40623986450)(`a68a0191`,publish=false)。结果出来后再派发回退修复:R1-A' codec radix 编解码(在 #1352 合入后的 main 上做,避免冲突;原因见 #1345)、R2-JDBC' MySQL store;mongo/zookeeper/segment 用两次结果判断是否为噪声,仍超阈值的分别派发或请用户裁定例外。
2. 已派发会话 `r2-redis-b-b1`:#1355 查清 Redis 7.0 下 `wait=auto` 解析为 0 的根因并修复。
3. 已派发会话 `ci-gate-mongo`(新 Draft PR):门槛报告改为 job summary + artifact,不再自动开 PR(报告是证据,不进 main;#1357 关闭);mongo:8.0 容器起不来时打印容器日志并查根因(疑为 runner 不支持 AVX)。
4. #1352、#1353、#1356 审查已通过,转 ready 与同步 main 待用户明确同意(自动权限拒绝了未经明确同意的转 ready 与 update-branch)。#1353 的 mongo:8.0 失败已确认为 `Container exited with code 1`,已重跑。

**本机代码备份:** 12 个只存在于本机的旧分支已推送为 `origin/archive/<原分支名>`(SHA 已核对),包括 4.x 时期的 `feat/replication-window`、`feat/jdbc-dialect`、`feat/generator-name-properties`、`build/unpublished-platform`、`test/deflake-meter-binder-zk-timeout`、`chore/release-4.1.1`、`fix/b5-virtual-thread-pinning`,以及 5.0 时期的 `r3-runtime`、`claude/r0-skeleton-004bc6`、`claude/magical-gould-a7e144`、`feat/r5a-parity-gaps`、`refactor/d29-split-integrations`。本机 `main` 的 4 个提交已包含在 `origin/4.x`。两个曾有未提交改动的工作区已推送:R4-C 的改动在 #1350(`fc17ef84`),R2-Mongo 的在 #1356。

- 各修复 PR(#1347–#1353、#1342、#1346)CI 全绿后:审查 → squash 合入 → 在 §4 标记。合并前必须确认 CI 状态非空且全部 SUCCESS。
- 发布门槛:[run 40534275762](https://github.com/Ahoo-Wang/CosId/actions/runs/40534275762),分析在 #1345;仍失败的行按 §4 归属派发。
- 待用户决定:E-M1(是否修改 `4.x` 的 document-deploy);RC1 必须以 pre-release 发布;发布前 `gradle.properties` 改为 `5.0.0-RC1`。
- 规则(2026-10-28 用户追加):**同一时间最多 1 个工作中的子会话**,串行派发,防止与其他会话争抢 CPU。
- 规则:所有 WIP 在 Draft PR 上;CPU ≤ 90%,只在 CI 验证;提交英文、作者 Ahoo Wang、无 Co-authored-by;回复用户用中文。

- **CI 偶发问题(✅ 已由 #1358 解决:根因 SERVER-125742,mongo:8.0 改在 ubuntu-22.04 上运行):** `Store MongoDB (mongo:8.0)` 在部分 runner 上整个 job 的容器都起不来(`Container exited with code 1`,等不到 `waiting for connections`),同一 job 在别的 runner 上通过(#1347 16:26、#1350 17:09)。#1342 两次、#1348 一次因此失败,均为基础设施问题,与改动无关,已重跑。RC 后需查明(可能与 runner 的 CPU 或镜像有关,建议 job 失败时打印容器日志)。

**发布授权(用户 2026-10-28):** alpha 版本由总调度自行发布,无需再确认;RC/GA 必须经用户确认。**用户另授权**:总调度可自行推进,审查通过且 CI 全绿的 PR 可直接转 ready 并合入,默认按总调度的推荐执行。alpha.1 按推荐顺序:#1362(codec)审查合入后发布,release notes 的 codec 条目在 #1362 中同步更新。

**alpha.1 发布步骤(D33):** 1) ✅ #1360、#1361 已合入(main 版本号已为 `5.0.0-alpha.1`);2) release notes 的已知问题按门槛结论填写;3) 用户最终确认后,在合入 #1360 的提交上创建 pre-release `v5.0.0-alpha.1`(会触发 package-deploy:校验、发布 Central、上传迁移工具 jar;以及 docker-deploy,不打 `latest`);4) 用该 tag 手动触发 `document-deploy`;5) 跟踪各 workflow,在 Central、Docker Hub、站点上核对;6) main 改回 SNAPSHOT 版本(例如 `5.0.0-SNAPSHOT`),避免之后的构建冒用 alpha.1 版本号。

## 4.3 待用户确认的决策(用户休息期间按总调度推荐执行,2026-10-28 起)

用户授权:默认按总调度推荐推进,PR 审查通过后由总调度合并;RC/GA 发布仍需用户决定。以下决策已按推荐执行或排期,**用户回来后请逐项确认或推翻**(推翻越早代价越小,冻结面相关项须在 RC1 前定):

| # | 决策 | 已执行/排期 | 推翻的影响 |
|---|---|---|---|
| 1 | D36 cosid80 可配置布局、friendly `padStart`、不适用属性报错 | api-freeze PR2 | 改为"不支持"只需改文档 |
| 2 | D37 命名 machine + `slotOffset` 滚动迁移(总调度改定:子会话原推荐 RC 只支持一次性停机切换) | api-freeze PR3/PR5 | 去掉 `slotOffset` 则共享全局命名空间的服务须一起停机迁移 |
| 3 | D38 `cosid.namespace` 默认 `spring.application.name`,未设置回退 `cosid` + WARN | api-freeze PR4 | 保持默认 `cosid` |
| 4 | D39 配置/SPI 值类型改 final class + builder(约 230 处调用) | api-freeze PR1(进行中) | 保持 record + 重载;须在 PR1 合入前提出 |
| 5 | D40 sealed 规格接口次版本可加变体;D41 `Custom`/`Instance` 自定义生成器;D42 `acquireTimeout`;D43 其余冻结面 MINOR | api-freeze PR2/PR4 | 逐项 |
| 6 | D34 门槛容差(吞吐/平均 5%、p99 10%、分配 max(5%, +64 B)) | 门槛实现排在 WP-perf | 用户已同意,列此备查 |
| 7 | cosid-server:`docs.public` 默认关;虚拟线程默认开;admin 路径改为白名单(删除 `auth.admin-paths`);管理端口独立时拒绝启动 | 已合入 #1365 | 可再调 |
| 8 | Mongo 分片集群:推荐加 CI job 证实"集合不分片"可用并写明不要分片(R1-mongo-04) | WP-docs | 改为文档收窄为副本集 |
| 9 | main 上 11 个 Dependabot 告警(3 high)须在 GA 前处理(Renovate) | GA 准入核对项 | — |
| 10 | 是否发布 RC1、何时发布 | 待门槛 PASS 与 API 冻结完成后请用户决定 | — |
| 11 | RC 版本号写法:`5.0.0-rc.1`(与 `5.0.0-alpha.1` 同为 SemVer 点分预发布标识,`alpha.1 < rc.1` 在 SemVer 与 Maven 中都成立;大写 `RC1` 在 SemVer ASCII 排序中会排在 `alpha` 之前)。roadmap 中 `5.0.0-RC1` 指同一版本 | 协调会话按推荐(PR6 #1377 的草稿 release notes 已用 `rc.1`) | 改用 `5.0.0-RC1` |
| 12 | 门槛 4.x 基线:RC1 门槛按已文档化的 4.1.0 判定(本轮 run 40772963183);Renovate #1367(cosid4 → 4.1.1,4.x 分支只含 bug 修复)在门槛结论后合入,并在 RC 期间用 4.1.1 复跑一次门槛作为 GA 依据 | ~~协调会话按推荐~~ 被 D45 取代(用户 2026-10-29:依赖升到最新，基线改用 4.1.1) | 立即升 4.1.1 并重跑 |
| 13 | WP-ci 完成后把 branch protection 的必需检查设为汇总检查 `CI`(加上现有的 `Conventional Commits title`),由 GitHub 强制"全绿才能合并"。仓库设置的改动须由用户本人操作或明确授权 | ✅ 用户已决定(2026-10-28):由用户本人在 GitHub 设置中把 `CI` 设为 main 的必需检查。之后协调会话合并 PR 时以 `CI` 为准(skipped 视为通过) | — |
| 14 | MINOR 分诊 §9.4 的三项:① R1-spi-03 RC 前只写异常契约，不加 `StoreException` 基类(以后在现有层次中插入基类是兼容的);② R1-mongo-01 RC 前修:不再创建 `ns_ownerKey` 索引(属于存储格式，冻结前清掉);已有库保留这个无用索引不影响正确性，文档说明可手动删除;③ server readiness 贡献者 `cosIdServerStore` 改名为 `cosidServerStore`,与 R1-spring-05 的 `cosid` 一致，不保留别名(alpha.1 无兼容承诺,D33),K8s 指南和 release notes 同步 | 协调会话按推荐 | 保留 `cosIdServerStore` 或加别名 |
| 16 | 门禁对存储组(P7/P10)的 4.x/5.x 运行顺序改为 ABBA 交替(依据 P5:先后顺序会让 p99.9 差 2 倍);容差和判定规则不变。不在 GA 前做分条带;游标字段填充(伪共享)列为可选项，先测量 | 协调会话按推荐 | 保持 4.x→5.x 固定顺序，则 MySQL reserve p99.9 这一行交用户裁定 |
| 17 | D46 UUIDv7 生成器(`strategy: uuidv7`)**进 5.0,在 RC1 之前完成**。它自包含、不依赖存储，在 RC1 冻结前定型，就能一并纳入兼容承诺 | ✅ 用户已确认(2026-10-29) | 改为 5.1:RC1 不必等它，按 D40 以后作为新增变体追加 |
| 18 | 第 2 轮中触及冻结面的两项都按兼容方式处理:① R2-spring-02:写在不适用策略上的键改为打 WARN,不改为启动失败(RC1 后校验不能收紧);② R2-core-03:具体生成器与租约类的公开构造器继续留在兼容承诺内，约定今后新增参数只走 builder 或静态工厂，不再增加位置参数重载。结论：不需要 RC2 | 协调会话按推荐 | ① 改为启动失败需要发 RC2;② 把构造器移出兼容承诺等于收窄 RC1 的冻结面 |

## 4.2 审查不通过登记

| PR | 问题 | 处理 |
|---|---|---|
| [#1355](https://github.com/Ahoo-Wang/CosId/pull/1355) | WP-Redis(B-B1)审查不通过:`Store Redis TCK (Redis 7.0)` 稳定失败(8.10 和 6.2 probe 通过)。`ClusterReplicaWaitTest` 中 `wait=auto` 在 7.0 上解析为 0(期望 1),之后 WAIT 不执行,冻结副本时写入照常成功。这是产品缺陷,不只是测试问题:启动时 auto 若解析为 0,WAIT 就会一直被静默跳过,与 B-B1 属同类问题。可能原因:Lettuce 缓存的拓扑还没显示副本,或 7.0 的 CLUSTER NODES 解析不同 | 已退回 R2-Redis:查清 7.0 的根因;auto 解析改为刷新拓扑或按各主节点的 INFO replication 计数,存在副本却解析为 0 时不能静默;同连接执行 WAIT 和 B-N4 的设计认可 |
| [#1357](https://github.com/Ahoo-Wang/CosId/pull/1357)(✅ 已关闭,2026-10-28) | 发布门槛自动报告(run 40534275762,基于 `614d5df9`,即各修复合入前):**FAIL,8 组中 5 组失败**。重跑后仍失败:codec-filler 4 行(radix36/62 编解码单线程吞吐低约 12–15%);mongo 1 行(reserve 分配多约 4%);mysql 7 行(`MySqlStoreBenchmark.acquire` 耗时约 +70%,acquire/renew/reserve 分配 +25–65%,ChainId p99/p99.9 偏高);segment 1 行;zookeeper 4 行(renew/reserve 小幅超阈值 1–7%)。另外该 PR 由 workflow 的 GITHUB_TOKEN 创建,不触发 CI,处于 BLOCKED,不满足“CI 全绿”规则 | 不合入。D21(性能不回退)未满足,是 **RC 阻断项**;分析在 #1345(R5-A)。收尾阶段不开新任务,RC 前需派发:codec 与 MySQL store 为主要回退;mongo/zookeeper/segment 行接近阈值,需先确认是否为噪声。报告需改为由能触发 CI 的身份创建(或改为直接推到 #1345) |
| [#1358](https://github.com/Ahoo-Wang/CosId/pull/1358) | CI(会话 `ci-gate-mongo`):门槛报告改为 job summary + artifact、不再开 PR,通过;mongo:8.0 根因为 MongoDB 8.0.21+ 在 Linux 6.19–7.0.13 上启动即退出(SERVER-125742,runner 镜像 20261025),认可。不通过:检测到不兼容时跳过 TCK,job 显示绿色而 8.0 实际未覆盖 | 已退回(2026-10-28):`mongo:8*` 改在 `ubuntu-22.04`(内核 6.8)上真跑;探测到不兼容时 job 失败而非跳过。**复审通过**(`5cd86293`,CI 全绿,8.0 TCK 在内核 6.8.0-1064-azure 上跑完),待用户同意转 ready;✅ **已合入 `0873ed9e`**(2026-10-28) |
| [#1376](https://github.com/Ahoo-Wang/CosId/pull/1376) | API 冻结 PR5(迁移:共享 `cosid.generator.namespace` → 命名 machine `generator` + slotOffset)。共享命名空间一次性迁移、各后端读 M、元数据 slotOffset、63 位规则、config 输出均通过。不通过:默认 offset = `roundUpToPowerOfTwo(M+1)` 无余量(M=511 → 512,余量 0),滚动期间 4.x 扩容即可分出 ≥ offset 的 id 与 5.0 节点重叠,唯一性依赖运维纪律 | 已退回(2026-10-28,head `38ba6731`):默认 offset 取 4.x 分配器硬上限 `2^(4.x cosid.machine.machine-bit)`,由构造保证不相交;放不下时要求显式 `--slot-offset` 并标记 `guaranteed: false` + 警告,`verify` 事后检查保留。**复审(`00b107fc`)再退回**:我的前提有误——4.x CosIdGenerator 按 `cosid.generator.machine-bit` 分配、5.0 保留为 node-bits,上限恒为 2^nodeBits,构造性保证在保留位宽时不可达,修复后默认路径恒抛错。改为默认 offset = `2^nodeBits − 2^bits`(slot 置于节点空间顶端,默认 20/10 位时 4.x 余量约 104 万),plan 报告 `headroom`,余量 < 2^bits 时醒目警告,`verify` 保留。**第三版复审通过**,CI 32 项全绿,✅ 已合入 `385d363e`(2026-10-28) |
| [#1391](https://github.com/Ahoo-Wang/CosId/pull/1391) | 修复 #1380(R1-core-04 take-first)引入的顺序回退。`slowPath` 先后两次检查当前游标,两次之间热路径可能已在锁外用完游标,于是 ready 里还有号段时调用方就自己 reserve 并先发出新号段的首值。ID 仍唯一,但发号越过了 ready 号段,紧接着 `close()` 就会留下空洞。由 WP-ci 试验运行发现(`SegmentGeneratorTest` 偶发失败,本地 1/12)。修复:只有 ready 为空时才等待或自行 reserve | 审查通过(循环必然终止,P6 不受影响),CI 29 项全绿,✅ 已合入 `9488ad60`(2026-10-28) |

## 5. RC1 准入清单

- [x] 审查 A–E 无未关闭的 BLOCKER(#1355 合入后清零,2026-10-28)
- [x] 发布门槛报告:全部 PASS(第七次运行 40832851823,8/8 组 PASS,3 个组件行 WARN 不阻塞,D44 口径)
- [x] #1342 合入(L14)
- [x] 遗留事项 L1–L16 中阻塞 RC 的已关闭(2026-10-29 核对):
  - L1、L2、L15 由 CI 正式门禁接管;
  - L4 稳定性:nightly `test-stability` 每类 20 次，连续两晚通过(40590957166、40779346122),L12、L16 的偶发测试在其中没有复现;
  - L5 由 `check -Pcosid.test.skipProjects` 去重(WP-ci);
  - L6 由 WP-docs 覆盖;
  - L7–L11、L13、L14 已完成。
- [x] 发布配置核对通过(E 路):
  - E 路 BLOCKER 已清零;
  - E-N6 `TODO(R1)` 已从 main 清除;
  - E-N8 CodeQL 路径与 E-N9 `release: published` 已在 main 上;
  - 发布前的手工步骤(版本号、server README)放进 RC1 准备 PR。
- [x] D46 UUIDv7 生成器合入([#1406](https://github.com/Ahoo-Wang/CosId/pull/1406) `ac3de73f`:16 位计数器，状态是一个 `AtomicLong`(48 位毫秒 + 16 位计数),58 位随机数，无需 machine,文本经 `text(name)` 获取;smoke 中 t1 生成 UUID 约 28 ops/µs、文本约 21 ops/µs,多线程的 smoke 误差很大，以后看正式门禁 P13 的趋势行)
  - 用户 2026-10-29 决定：先发 **`v5.0.0-alpha.3`**(包含 UUIDv7、带点名字的测试、AtomicLong/ABBA,以及第七次门禁 PASS),RC1 之后再说。发布步骤同 alpha.2。
  - ✅ **`v5.0.0-alpha.3` 已发布**(2026-10-29,pre-release,目标 `f4f5bece` = #1409):
    - Packages Deploy、Docker Image Deploy、document-deploy 全部成功;
    - Central 在 09:37 已可解析(core、bom、starter、各 store);
    - Docker Hub 有 `5.0.0-alpha.3`,没有打 `latest`;
    - release 附件中有 `cosid-migration.jar`;
    - 文档站 generators 页已有 uuidv7 一节;
    - main 已改回 SNAPSHOT(#1410 `b73c9e0e`)。
- [x] 用户确认发布 RC1(2026-10-29,用户在协调会话中:"Release RC1")
- ✅ **`v5.0.0-rc.1` 已发布**(2026-10-29,pre-release,目标 `c154e69d` = #1411):
  - Packages Deploy、Docker Image Deploy、document-deploy 全部成功;
  - Central 在 10:24 已可解析(core、bom、starter、jdbc/redis/mongo/zookeeper);`cosid-store-redis` 的 POM 中是具体版本(Lettuce 7.9.0、slf4j 2.0.20),没有 BOM import;
  - Docker Hub 有 `5.0.0-rc.1`,没有打 `latest`;
  - release 附件中有 `cosid-migration.jar`;
  - 文档站已按 tag 部署;
  - main 已改回 SNAPSHOT(#1412 `8ebde66d`);
  - 已通知 Wow。
  **兼容承诺从本版开始。** 下一阶段：RC 期间进行 GA 前审查第 2 轮(D35),并按 §4.3 第 12 项在 RC 期间以 4.1.1 复跑门禁(第七次运行已经用的是 4.1.1,此项视为已满足)。
  - **GA 前审查第 2 轮**(D35,用户 2026-10-29 要求开始):由独立的新会话 `ga-review-round-2` 进行，避免受前面实现上下文的影响。只出发现表，不改代码，结果追加到 ga-review.md。审查要明确给出 D35 的三项结论(性能、特性、文档);触及冻结面的问题须标明能否兼容地修复，需要不兼容改动的标 RC2-BREAKING,交用户决定。
    - ✅ 第 2 轮报告 [#1413](https://github.com/Ahoo-Wang/CosId/pull/1413) 已合入(ga-review.md §10)。共 41 条:BLOCKER 2、MAJOR 4、MINOR 35;RC2-BREAKING 的候选只有 1 条(可选做法)。
      - D35 三项结论：性能 PASS(第七次门禁);特性方面运行时 PASS,但迁移工具 `config` 有缺口;文档 PASS,有缺口。
      - 两条 BLOCKER 都出在迁移工具 `config`:R2-mig-01 没有读取 `apply-result.json` 的 `target`;R2-parity-01 丢掉了 4.x snowflake 自带的 namespace。两条都可兼容地修复，因此**不需要 RC2**。
      - 修复分三个 PR 串行进行:WP-migration-r2(包含两条 BLOCKER),然后 WP-core-spring-r2,最后 WP-docs-r2。完成后在 GA 提交上重跑门禁(R2-perf-01)。
      - 协调会话按推荐做的两项决定(§4.3 第 18 项):R2-spring-02 对不适用策略的键只打 WARN,不改为启动失败;R2-core-03 的公开构造器保留，约定今后新增参数只通过 builder 或静态工厂。
  - 用户 2026-10-29 决定：在 RC1 之前先发 **`v5.0.0-alpha.2`**。alpha 发布已获授权(D33);发布准备 PR #1400 由 rc.1 改为 alpha.2。发布步骤同 alpha.1:
    1. 合入 #1400;
    2. 在合入提交上创建 pre-release,会触发 package-deploy 和 docker-deploy(不打 `latest`);
    3. 用 tag 手动触发 `document-deploy`;
    4. 在 Central、Docker Hub 和站点上核对;
    5. main 改回 `5.0.0-SNAPSHOT`。
    RC1 仍需用户确认。
  - **下游 Wow V10**(2026-10-29,跨会话沟通):
    - wow-cosid 正从 CosId 3.4.1 升级到 5.0.0-alpha.2。它按名字从运行时解析生成器，命名约定是聚合的限定名 `<context>.<aggregate>`(带点),全局默认生成器为 `default`。
    - 我们已确认带点的名字受支持，并属于冻结面。会补一个回归测试 PR,覆盖 Spring 中的 `"[a.b]"` 写法、未知键检查、运行时查找和 TCK,PR 号出来后告知 Wow。
    - Wow 的 rc/GA 跟随 CosId RC1,由用户决定。
    - 已提醒 Wow:跨服务唯一的 ID 要用共享命名空间的命名 machine(D37)。
    - CoSec 与 CosId 5 不能共存于同一类路径(CoSec 依赖 4.x API)。CosId **不提供兼容层**(D1),建议 CoSec 改为依赖 `LongSupplier`/`Supplier<String>` 这类函数式接口来解耦。
  - ✅ 已转告 Wow(alpha.3 发布后):`stringGenerator(name)` 只服务 long 生成器;cosid80 和 uuidv7 的文本请用已有的 `runtime.text(name)`(alpha.2 已提供)。D46 会把异常信息改成提示 `text(name)`。
  - ✅ **`v5.0.0-alpha.2` 已发布**(2026-10-29,pre-release,目标 `f3c80934` = #1400):
    - Packages Deploy、Docker Image Deploy、document-deploy 全部成功;
    - Central 在 06:13 可以解析(core、bom、starter、各 store);`cosid-store-zookeeper` 的 POM 中是具体版本(Curator 5.9.0、ZooKeeper 3.9.6、Guava 33.7.2),没有 BOM import;
    - Docker Hub 上有 `5.0.0-alpha.2`,没有打 `latest`;
    - 文档站和 Kubernetes 指南页面返回 200;
    - release 附件中有 `cosid-migration.jar`;
    - main 已经改回 `5.0.0-SNAPSHOT`(#1403 `074fdddd`)。
  - **发布前插入 WP-deps**:WP-ci 加入 Gradle 依赖图提交后,Dependabot 新增 55 条告警(2 critical:netty-handler;high 涉及 netty、jackson 2/3、zookeeper、httpcore5、snappy、guava、mybatis 3.5.6 等)。这些告警全部记在 `settings.gradle.kts` 名下，来源不明。处理:
    1. 按来源归类：发布模块的 runtime、server 镜像、测试、benchmark(含 4.x 基线)、构建插件;
    2. 修复发布物和 server 镜像中的告警;
    3. 依赖图只提交发布物的 runtime classpath,不手动 dismiss 告警;
    4. 4.x 基线的依赖不动。
    alpha.2 等 WP-deps 合入后再发，原因是不想发布一个带已知 critical 的 server 镜像。
    ✅ WP-deps [#1401](https://github.com/Ahoo-Wang/CosId/pull/1401) `3a76f1de`:Lettuce 7.9.0(Netty 4.2.17),store-zookeeper 显式声明 ZooKeeper 3.9.5 和 Guava 33.7.2,支持的 Boot 下限 4.0.9,依赖图只提交发布物。剩余 1 条(MyBatis 3.4.2,来自 Activiti 7.0),写进文档。
    → 合入后在 main 上提交依赖图，告警从 55 条降到 **1 条**(mybatis 3.4.2,来自 Activiti 7.0),与预期一致。
    → ✅ D45 依赖升级 [#1402](https://github.com/Ahoo-Wang/CosId/pull/1402) `9290f371`:
      - 所有模块在构建期导入 Spring Boot 4.1.2 BOM,版本目录只保留 BOM 不管理的依赖;
      - Lettuce 7.9.0 为安全修复高于 BOM,写明了移除条件;
      - Checkstyle 升到 14.3.0,删掉 #1401 留下的 5 条工具链约束，只保留 Guava 一条;
      - cosid4 基线升到 4.1.1,并入 #1367,Renovate 会自动关闭它;
      - 发布 POM 中没有 BOM import,版本都是具体值(Mongo driver 回到 BOM 的 5.8.1)。
      CI 全绿,dry-run 40822764071 通过。下一步发布 alpha.2。
- [x] GA 前审查第 1 轮完成,BLOCKER/MAJOR 已修复或经用户裁定(2026-10-29:BLOCKER 1、MAJOR 19 已修;RC 前要修的 25 条 MINOR 已修，#1396/#1397/#1399)(见 §6;涉及 API、配置、存储格式的改动必须在 RC 冻结前完成)

## 6. GA 前两轮全面审查(D35)

用户 2026-10-28 要求:GA 前做 **2 轮**全面代码审查。维度:**架构**(分层与模块边界、依赖方向、与 01–11 设计一致)、**代码清洁**(命名、重复、死代码、注释与代码一致)、**高内聚低耦合**(包与类职责、internal 不外泄、模块间只经 SPI/API)、**高性能**(热路径分配与锁、I/O 不在锁内、与 4.x 对照)、**高扩展性**(SPI、自定义 Store/Codec/Layout 的扩展点是否足够且稳定)、**易用性**(公开 API、配置项与默认值、错误信息、Spring 装配、示例与快速开始)。并核对三条结果条件:**性能无明显回退**(D21 按 D34 判定)、**无特性缺失**(11-feature-parity 逐项对照 4.x,含 L8 能力缺口)、**文档齐备**(文档站、模块 README、迁移、运维、配置参考、javadoc、release notes)。

| 轮次 | 时机 | 目的 | 产出 |
|---|---|---|---|
| 第 1 轮 | alpha.1 之后、RC1 之前 | 发现问题;API/配置/存储格式类问题必须在 RC 冻结前修 | 发现清单(严重度 BLOCKER/MAJOR/MINOR,逐条核实),按归属派修复 |
| 第 2 轮 | RC 观察期、GA 之前 | 复核第 1 轮修复,检查 RC 期间的改动与遗漏 | GA 准入结论 |

执行方式:同一时间只有 1 个子会话。审查只读(不跑构建与基准),每轮一个子会话按维度逐模块审查(可在会话内用只读子代理),发现写入 Draft PR 上的 `docs/architecture/ga-review.md`,由总调度逐条核实后排入修复队列。

### 第 1 轮进展

- 进行中:会话 `ga-review-r1`,Draft PR [#1364](https://github.com/Ahoo-Wang/CosId/pull/1364)(`docs/architecture/ga-review.md`)。
- 已核实(总调度):R1-x-03 MAJOR(cosid80 位宽与阈值不可配,11 第 19 行"等价"不准确,触及冻结面)、R1-x-04 MAJOR(迁移的布局相同判定包含 `seqResetThreshold`)、R1-x-05 MINOR(cosid80 静默忽略 friendly `pad-start` 等属性)。处理按 D36(方案 1:补齐)。R1-x-06 MAJOR(触及冻结面):5.0 一个运行时只有一个命名空间与一个 machine,缺少 4.x CosIdGenerator 的全局命名空间与独立机器号,无法做到"业务在服务命名空间、cosid80 跨服务全局唯一";处理按 D37(命名的 machine),并已请审查会话补查迁移与文档。**R1-x-07 BLOCKER**:迁移工具不认识 `cosid.generator.namespace`,`config` 静默丢弃,`plan/apply` 只读 `--namespace` 的机器号记录 → 迁移后各服务的 cosid80 跨服务重号;已在 alpha.1 的 GitHub release 已知问题中提示(勿用 alpha.1 迁移跨服务共享命名空间的 CosIdGenerator)。随 D37 修复。R1-x-08 MAJOR:文档没有跨服务全局唯一的说明。core(§4,18 条:0 BLOCKER、4 MAJOR、14 MINOR;A-M1、A-N1–N7 复核正确,无重号路径):R1-core-01 公开配置/SPI 值类型是 record,加分量即破坏兼容(触及冻结面,影响面最大,RC 前须定演进策略);R1-core-02 `GeneratorSpec` sealed,无法注册自定义生成器(4.x `IdGeneratorProvider.set`),11 标"等价"不准;R1-core-03 `acquireTimeout` 设计有而规格/配置缺;R1-core-04 P6(step 1)无快路径,每个 id 走慢路径并分配 `RangeCursor`(24→32 B 回退的原因)。文档缺口(用户关注 k8s 微服务):新增"Kubernetes 部署"指南(推荐架构:进程内发号 + cosid-server 作为租约/号段中心、`remote` 存储;Deployment 用租约槽位、StatefulSet 可用 `preferred-slot`;bits 按峰值 Pod 含 surge;优雅停机与 `terminationGracePeriodSeconds`;健康探针;token 用 Secret、按服务限定命名空间;全局 machine 用共享命名空间,依赖 D37),随 D37 一起完成。全部发现出齐后统一排入修复队列。

### GA 准入清单

- [ ] 第 1、2 轮审查无未关闭的 BLOCKER/MAJOR(或经用户裁定)
- [ ] 发布门槛在 GA 提交上按 D34 PASS
- [ ] 11-feature-parity 逐项为"等价"或"更强",例外经用户裁定
- [ ] 文档齐备核对通过
- [ ] RC 期间无阻塞问题
- [ ] 用户确认发布 GA

> WIP 通过分支 `wip/ga` 上的 Draft PR 保存(此前为 #1344 的 `wip/rc-review`,已合入):本文件与路线图的更新都推送到该分支,换机器也能继续;在阶段节点合入 `main`。各子会话同样一开工就开 Draft PR,WIP 推送到各自 PR 分支。
