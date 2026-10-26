# 02 模块与目录布局

## 1. 目录布局(D2:目录名不带 `cosid-` 前缀)

```
core/                       cosid-core                       纯算法 + SPI + 装配工厂 + 注册表 + 字段填充
store/
  tck/                      cosid-store-tck                  存储一致性测试套件 + 内存参考实现
  jdbc/                     cosid-store-jdbc                 MySQL / PostgreSQL / SQL Server 方言
  redis/                    cosid-store-redis                最小执行接口 + Lettuce 实现
  mongo/                    cosid-store-mongo
  zookeeper/                cosid-store-zookeeper
  remote/                   cosid-store-remote               远程服务客户端(JDK HttpClient)
server/                     cosid-server                     远程租约/区间服务 + 可选发号端点(Spring Boot 应用,发布 Docker 镜像)
spring/
  autoconfigure/            cosid-spring-boot-autoconfigure
  starter/                  cosid-spring-boot-starter        纯依赖聚合
integrations/               cosid-integrations               jackson / mybatis / spring-data-jdbc / activiti / flowable / axon
extras/
  sharding/                 cosid-sharding                   分片算法(仅依赖 core 的 Layout)
migration/                  cosid-migration                  4.x → 5.0 离线迁移 CLI(fat jar)
bom/                        cosid-bom
benchmark/                  (不发布)                         JMH:baseline-4x/ 与 v5/ 两个工程,见 10
examples/                   (不发布)                         segment-jdbc、snowflake-redis、server
documentation/              文档站(VitePress)
docs/                       设计与路线图
```

删除:`document/`(旧站)、`wiki/`、`cosid-test`(由 `store/tck` 取代)、`cosid-mod-test`、`cosid-dependencies`(并入 `bom`)、
`cosid-accessor`(并入 `core` 的 `accessor` 包)、`cosid-mybatis`、`cosid-jackson`、`cosid-axon`、`cosid-activiti`、`cosid-flowable`、
`cosid-spring-data-jdbc`(并入 `integrations`)、`proxy/`(拆为 `store/remote` 与 `server`)。

## 2. 发布坐标(D11)

groupId 不变:`me.ahoo.cosid`。artifactId 由构建约定从目录路径生成:`"cosid-" + path.replace('/', '-')`,
例外仅 `spring/*` 映射为 `cosid-spring-boot-*`。目录保持干净,发布的 jar 名仍可辨识来源。

## 3. 依赖规则

```
                 core
        ┌─────────┼──────────┬──────────────┐
   store/tck   store/*   integrations    migration ──→ store/*(新) + 4.x 读取器(内置,只读)
        ↑         ↑
   (测试依赖)   spring/autoconfigure ──→ spring/starter
                  ↑
                server ──→ store/*
   extras/sharding ──→ core
```

- `core` 只依赖 `slf4j-api`,无任何其他运行时依赖。
- `store/*` 只依赖 `core` 与各自驱动(JDBC 用 `javax.sql.DataSource`,Redis 用 `lettuce-core`,Mongo 用 `mongodb-driver-sync`,ZooKeeper 用 `curator-framework`)。**不依赖 Spring。**
- `spring/autoconfigure` 依赖 `core`,对 `store/*`、`integrations` 均为 `compileOnly` + `@ConditionalOnClass`。
- 任何模块不得依赖 `server`、`migration`、`examples`、`benchmark`。
- 规则由构建脚本中的依赖校验任务强制(ArchUnit 或自定义 Gradle 校验),违规即构建失败。

## 4. core 的包结构

| 包 | 内容 | 导出(D12) |
|---|---|---|
| `me.ahoo.cosid` | `IdGenerator`、`IntIdGenerator`、`StringIdGenerator`、`IdGenerators`、`GroupedId`、`CosIdException` 及异常层次 | 是 |
| `me.ahoo.cosid.codec` | `IdCodec`、`Radix`、`Friendly`、`DatePrefix`、`PartsCodec`、`GroupedCodec` | 是 |
| `me.ahoo.cosid.time` | `TimeSource`、`Layout`、`IdParts`、`TimeBasedGenerator`、`ClockRegressionPolicy` | 是 |
| `me.ahoo.cosid.segment` | `Range`、`RangeStore`、`SegmentGenerator`、`BatchPolicy`、`Period`、`PeriodicPolicy`、`PeriodicSegmentGenerator` | 是 |
| `me.ahoo.cosid.lease` | `SlotLeaseStore`、`SlotLease`、`Owner`、`AcquireResult`、`SlotHandle`、`SlotBinding`、`LeaseKeeper`、`StaticSlot` | 是 |
| `me.ahoo.cosid.store` | `StoreProvider`、`StoreConfig`、`Store` | 是 |
| `me.ahoo.cosid.runtime` | `CosIdRuntime`、`RuntimeSpec`、`GeneratorSpec`、`CodecSpec`、`MachineSpec` | 是 |
| `me.ahoo.cosid.observe` | `GeneratorListener`、`GeneratorInfo` | 是 |
| `me.ahoo.cosid.accessor` | `@CosId`、`IdFiller` | 是 |
| `me.ahoo.cosid.internal.*` | 序列器、区间缓冲、单飞补充、校验工具 | **否** |

## 5. 构建约定

- Java 21 toolchain;`-parameters` 保留(Spring 端点参数名)。
- 不使用 Lombok(D13):值类型用 `record`,日志用 `LoggerFactory.getLogger`。
- Checkstyle + SpotBugs + JaCoCo 保留;许可证头保留。
- 版本统一在 `gradle/libs.versions.toml`;`bom/` 只声明本项目 artifact 与必要的三方对齐。
- JMH 只在 `benchmark/` 中,不再对每个库模块应用插件。
- `-PspringBootVersion` / `-PtestJavaVersion` 兼容矩阵机制保留,仅作用于 `spring/*`、`server`、`integrations`。
- 集成测试:`store/*` 每个模块一个 CI job,运行 TCK(Testcontainers,环境变量覆盖与 4.x 相同的约定)。
