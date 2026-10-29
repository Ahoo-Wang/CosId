# 01 第一性原理与设计原则

## 1. 问题的本质

分布式 ID 生成器只有一个不可妥协的性质:**全局唯一**。趋势递增、低延迟、高可用、可读性都是可权衡的次要性质。

唯一性只有两种来源:

| 来源 | 机制 | 需要的协调原语 |
|---|---|---|
| **空间切分**(Snowflake、CosIdGenerator) | `(时间, 槽位, 序列)` 按位布局打包 | 槽位上**时间窗口的独占租约**(相继持有者的窗口互不重叠) |
| **区间预留**(Segment) | 原子地从计数器取走 `[start, end)`,本地发放 | 每个 key 一次**原子递增** |

由此,整个系统可以分成三层,且只有三层:

```
┌──────────────────────────────────────────────┐
│ 装配层   配置 → RuntimeSpec → CosIdRuntime    │  Spring / Server / 手写代码,都是薄壳
├──────────────────────────────────────────────┤
│ 本地算法 布局、序列器、编解码、区间缓冲、租约维持 │  纯 Java,无 I/O 以外的依赖
├──────────────────────────────────────────────┤
│ 协调原语 RangeStore.reserve                   │  每个后端只实现这两个原语
│          SlotLeaseStore.acquire/renew/release │  一套 TCK 验证
└──────────────────────────────────────────────┘
```

## 2. 4.x 审查结论

4.x 把这三层混在了一起:分配策略写在每个后端里,装配逻辑只存在于 Spring starter,线程与全局状态散落在核心各处。

### 2.1 核心算法(约 60 个公开类型,本质只需约 12 个)
- 位布局存了 4 份(`SnowflakeLayout`、`AbstractSnowflakeId` 字段、`SnowflakeIdStateParser` 掩码、`RadixCosIdGenerator` 私有布局);时间单位靠 `instanceof SecondSnowflakeId` 推断。
- Snowflake 与 CosId 两个家族各一套 ClockSync/Leased 装饰器(复制粘贴);装饰器顺序只靠 starter 注释保证(`SnowflakeIdBeanRegistrar.java:190-202`)。
- **缺陷**:`IdGeneratorDecorator` 不委托 `idConverter()`,包装后 `generateAsString()` 静默退回 Radix62。
- `IdGenerator extends StringIdGenerator`,编码绑死在生成器身份上;根包 `IdConverter` 反向依赖 `segment.grouped`;`GroupedPrefixIdConverter` 违反里氏替换。
- 全局可变静态:`CosIdEvents`(热路径调用)、`CallTimeout` 线程池、`Clock.CACHE` 守护线程、`DefaultIdGeneratorProvider.INSTANCE`。
- 时间源不可注入;秒级序列耗尽时**持锁 park**,所有线程最长停顿 1s;`getLastTimestamp()` 无锁读非 volatile 字段。
- 15 个 Stat 类镜像类树;输出依赖服务器默认时区。

### 2.2 号段
- 存储 SPI 12 个方法,其中 8 个是核心的链构建逻辑;后端只返回 `maxId`,offset 由 `maxId - step` 推断。
- 区间有 4 种表示(`IdSegment`、`DefaultIdSegment`、`MergedIdSegment`、`IdSegmentChain`)。
- **存储 I/O 在 `synchronized` 内**(`IdSegmentChain.java:85-90`);同步取段无超时;超时取消会丢掉已提交的区间。
- 自研 `Thread` 子类 + `LockSupport` 调度器,存在丢失唤醒窗口(`DefaultPrefetchWorker.java:98-103`);静态池 + 构造器注册 shutdown hook。#1299、#1302、#1303、#1305 都是这套自研设施的缺陷,而非算法缺陷。
- 日期分组渗透到区间、链、生成器、转换器;跨期时所有预取段同时失效,热路径同步建 key;每期一行永不清理。

### 2.3 机器号租约
- 同一租约 4 种表示(`Lease`、`MachineState`、`MachineIdLease`、`GuardianState`)。
- SPI 8 个方法,4 个 `claim*` 把分配策略强加给每个后端;`now` 由调用方传入,时钟所有权泄漏。
- **最严重风险**:身份无 incarnation。`claimSelf` 无论是否过期都收回租约,两个进程同 instanceId 时,最长 1 分钟内在同一机器号上同时发号。
- 过期由认领方用自己的 safeGuard 判断,而非存在租约上;安全性依赖"所有实例 safeGuard 相同"的隐式约定。
- 本地水位线是"写文件时的墙钟",不是生成器真实的最后时间戳,滞后可达 1s。
- 3.x/4.0 双格式等兼容包袱;stable 租约永不过期;Redis Lua 内 O(n) 扫描。

### 2.4 Spring 与构建
- starter 89 个类、27 个自动配置;每个后端"直接 Bean + BuiltInStoreProvider"两条路径,11 个自定义条件注解仲裁。
- **装配逻辑只存在于 Spring**,proxy-server 不得不依赖整个 starter。
- 三棵形状不同的生成器配置树;存储配置按"号段/机器号"各复制一份;同一特性下所有生成器只能用一种存储。
- 生成器既是 Bean 又在 Provider 里(双重真相);Gradle feature variants 无法被 Maven 用户表达。
- 4 套文档目录;`cosid-sharding` 与 ID 生成无关。

## 3. 设计原则

1. **存储只做原子原语,不做策略。** 分配顺序、重试、缓冲、分组全在核心实现一次,由 TCK 统一验证。
2. **唯一性不依赖时钟。** 租约是槽位上的时间窗口,窗口边界只在存储内单调推进;时钟(由存储打戳)只用于过期判断,只影响可用性。
3. **无全局状态。** 时间源、执行器、监听器、注册表全部注入;谁创建谁关闭(`AutoCloseable`)。
4. **组合优于装饰链。** 时钟策略、租约守卫、计数是构造参数,不存在"包装顺序"。
5. **生成与编码分离。** 生成器产出 `long`(或 `IdParts`),字符串由 `IdCodec` 组合得到。
6. **装配不依赖 Spring。** `RuntimeSpec → CosIdRuntime` 是纯 Java 工厂;Spring、Server、手写代码共享同一条路径。
7. **热路径不在锁内等待、无分配、无 I/O。** 号段热路径无锁,锁只出现在"切换区间"这类低频路径;时间型发号是很短的临界区(D25),所有等待都在锁外;锁从不包住 I/O。
8. **契约先于实现。** 每个 SPI 先有 TCK,后有后端。
9. **允许间隙,绝不重复。** 任何失败路径都只能丢号,不能重号。
