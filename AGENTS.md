# AGENTS.md

Workspace instructions for agents working in **CosId** — a universal, flexible, high-performance distributed ID
generator for the JVM. Group `me.ahoo.cosid`, current version in `gradle.properties`.

**This is the `main` branch: the clean-room 5.0 rewrite** (the former `5.x`; 4.x maintenance lives on `4.x`). The design lives in [`docs/architecture/`](docs/architecture/README.md)
(decisions D1–D24, one document per area) and the phases in [`docs/architecture/roadmap.md`](docs/architecture/roadmap.md).
Read the documents of the area you touch before changing it; public API changes go to the design documents first.
The 4.x code stays on `main` as reference: read it with `git show main:<path>` (e.g.
`git show main:cosid-core/src/main/java/me/ahoo/cosid/snowflake/MillisecondSnowflakeId.java`). Nothing here is
compatible with 4.x (D1); the offline migration tool (`migration/`, 09-migration.md) is the only bridge.

## Toolchain

- **Java 21** toolchain (D3), set by the `cosid.java` convention plugin. Gradle finds an installed JDK 21 or provisions
  one (foojay resolver in `settings.gradle.kts`); the Gradle daemon itself may run on 17+.
- **Gradle (Kotlin DSL)**; always `./gradlew`. Versions live in `gradle/libs.versions.toml` only.
- **No Lombok** (D13): `record` for values, explicit code otherwise, `LoggerFactory.getLogger` for logging.
- `-parameters` is on for every module (Spring endpoints read parameter names): keep it.

## Common commands

```bash
./gradlew check                                    # everything: compile, Checkstyle, SpotBugs, tests, JaCoCo,
                                                   # dependency rules, build-logic tests
./gradlew :core:check                              # one module (paths follow directories: :store:tck, :spring:starter)
./gradlew :core:test --tests "me.ahoo.cosid.time.LayoutTest"
./gradlew javadoc                                  # what publishing builds; doclint is on (missing comments allowed)
./gradlew checkDependencyRules                     # the dependency direction rules alone
./gradlew checkDependencyRules '-Pcosid.dependencyRules.probe=:core->:store:jdbc'   # negative check: must FAIL

benchmark/run-smoke [P5 P12 ...]                   # 4.x and 5.x back to back per scenario, then compare --smoke:
                                                   # how a PR reports performance (paste build/benchmark-smoke/report.md)
./gradlew :benchmark:baseline-4x:jmh               # JMH on the released 4.1.0 jars alone (smoke profile)
./gradlew :benchmark:v5:jmh -PjmhProfile=release   # JMH on 5.x, release-gate settings; -PjmhIncludes=A,B narrows
benchmark/run-release [--dry-run] [P1 ...]       # the release gate: all of P1..P12, release settings, load guard,
                                                   # machine info; writes docs/jmh/5.0/ (see benchmark/scenarios.md)
benchmark/compare BASELINE.json CANDIDATE.json --smoke
python3 -I benchmark/compare_test.py               # tests of the compare script
```

`scripts/test-stability.sh :store:redis 20 build/stability` runs a project's tests 20 times without retries and prints
a per-class table (the `Test Stability` workflow, L4). `-Pcosid.test.skipProjects=:a,:b` leaves those projects' tests
out of a build (the CI `check` job, whose store, server and Spring tests run in their own jobs);
`-Pcosid.test.exclude=*.SomeTest` leaves classes out.

`check` fails on any Checkstyle (`config/checkstyle/checkstyle.xml`, Checkstyle 10.x) or SpotBugs
(`config/spotbugs/exclude.xml`) finding. In CI (`CI=true`) flaky tests get up to 2 retries via `test-retry`.

## Layout (02-modules.md §1)

Directories carry no `cosid-` prefix (D2). The published artifactId is derived from the path by
`build-logic/.../ArtifactIds.kt` (D11): `"cosid-" + path.replace('/', '-')`, `spring/*` → `cosid-spring-boot-*`,
`extras/*` → `cosid-*` (so `extras/sharding` stays `cosid-sharding`).

| Directory | Artifact | State |
|---|---|---|
| `core/` | `cosid-core` | R1 + R3: generators, codecs, leases, and `CosIdRuntime` (the one assembly path) with `IdFiller`; internals in `me.ahoo.cosid.internal` |
| `store/tck/` | `cosid-store-tck` | R1-C: the contracts, `InMemoryStore` (passes them), `ManualClock`, fault injection, `ContractsRan` |
| `store/jdbc/` | `cosid-store-jdbc` | R2-JDBC: `JdbcStore` over a `DataSource`; MySQL, MariaDB, PostgreSQL, SQL Server, H2 dialects and the `JdbcDialect` SPI; `schema-*.sql` |
| `store/mongo/` | `cosid-store-mongo` | R2-Mongo: `MongoStore` (`RangeStore` + `SlotLeaseStore`, sync driver, MongoDB 6.0+), `StoreProvider` type `mongo` |
| `store/redis/` | `cosid-store-redis` | R2-Redis: Lua scripts behind `RedisScripts`, Lettuce, `WAIT` auto; see its README (durability precondition) |
| `store/zookeeper/` | `cosid-store-zookeeper` | R2-ZK: `ZookeeperStore` (Curator 5.9), provider type `zookeeper`, ZooKeeper 3.8+ (3.9 primary, D27); TCK on `COSID_TEST_ZOOKEEPER_CONNECT` or an in-process curator-test server |
| `spring/autoconfigure/` | `cosid-spring-boot-autoconfigure` | R3-S: `cosid.*` records → `RuntimeSpec` (`CosIdSpecs`, pure), the `CosIdRuntime` bean, one bean per generator (`CosIdGeneratorRegistrar`), `SpringDataRedisScripts`, Micrometer, the `cosid` endpoint, health, native hints; stores `compileOnly` |
| `store/remote/` | `cosid-store-remote` | R4-A: `RemoteStore` over the JDK `HttpClient` (no third-party dependency), provider type `remote` (`baseUrl`, `token`, `connectTimeout`, `requestTimeout`); `RemoteApi` holds the `/v5` paths and error codes the server shares |
| `server/` | not published | R4-A: cosid-server, a Spring Boot app (`CosIdServer`): the two primitives of its backing store (`cosid.stores`, `cosid.server.store`) under `/v5`, optional `/v5/ids/**`, bearer auth on by default, limits, 503 pool, OpenAPI, Actuator; `Dockerfile` + `:server:dockerImage` |
| `integrations/*` | `cosid-jackson`, `cosid-mybatis`, `cosid-spring-data-jdbc`, `cosid-activiti`, `cosid-flowable`, `cosid-axon` | R4-B, split by dependency (D29): one module per library, each on `core` and that library only (no integration depends on another). `@AsString`, `CosIdInterceptor`, `CosIdBeforeConvertCallback`, `ActivitiIdGenerator` / `FlowableIdGenerator`, `CosIdIdentifierFactory`; see `integrations/README.md` (the Axon static binding) |
| `spring/starter/` | `cosid-spring-boot-starter` | R3-S: dependencies only (core + autoconfigure, D14); applications add the store artifacts |
| `bom/` | `cosid-bom` | the project's own artifacts; add each published module |
| `benchmark/baseline-4x/` | not published | JMH on `me.ahoo.cosid:*:4.1.0`; see `benchmark/scenarios.md` |
| `benchmark/v5/` | not published | the same scenarios on 5.x |
| `migration/` | not published | R4-C: the offline 4.x → 5.0 migration fat jar (`:migration:shadowJar` → `build/libs/cosid-migration.jar`): 4.x readers, a narrow 5.0 admin path per store, `plan` / `apply` / `verify` / `config` / `purge`; see its README |
| `extras/sharding/`, `examples/` | | not created yet (R4–R5) |
| `build-logic/` | — | included build: convention plugins and build rules (see below) |
| `docs/architecture/` | — | the design; `docs/jmh/<version>/` benchmark results |
| `documentation/` | — | the VitePress site, still 4.x content until R5-B |

To add a module: create the directory with a `build.gradle.kts` applying `cosid.library` (or `cosid.benchmark`),
`include(":path:name")` in `settings.gradle.kts`, add it to `bom/` if published, and make sure a rule in
`build-logic/src/main/kotlin/me/ahoo/cosid/gradle/DependencyRules.kt` covers it — a project without a rule fails the build.

### core packages (02 §4)

`me.ahoo.cosid` (generators, `GroupedId`, exceptions), `.codec`, `.time`, `.segment`, `.lease`, `.store`, `.runtime`,
`.observe`, `.accessor` are exported by `core/src/main/java/module-info.java` (D12); `me.ahoo.cosid.internal.*` is not.
Adding a package means deciding whether `module-info.java` exports it.

## Build logic (`build-logic/`)

- `cosid.java`: Java 21, `-parameters`, Checkstyle, SpotBugs, JaCoCo (XML reports for Codecov), JUnit 5 + Hamcrest,
  Logback for tests, `-PtestJavaVersion=25` to run tests on another JDK.
- `cosid.library`: `cosid.java` + sources/javadoc jars + Maven publication (`cosid.publish`).
- `cosid.spring` (`spring/*`, `integrations/*`; `server` applies `cosid.java` + the Spring Boot plugin and the BOM itself, since it is not published): `cosid.library` + the Spring Boot BOM on the build's
  classpaths only (never published), published versions of `spring-boot-minimum`, `checkPublishedMetadata` (in `check`),
  and the compatibility matrix: `-PspringBootVersion=4.0.8` builds and tests on another Boot (02 §5), with
  `-PtestJavaVersion=25`; CI job `spring-compatibility`.
- `cosid.platform`: the BOM. `cosid.benchmark`: `cosid.java` + JMH (`me.champeau.jmh`) — JMH is applied nowhere else.
- `cosid.root` (root project): `checkDependencyRules`, wired into `check` with build-logic's own tests.
- **Dependency rules (02 §3)** are data in `DependencyRules.kt`, unit tested in `DependencyRulesTest.kt`: `core` depends
  on `slf4j-api` only; `store/*`, `integrations/*`, `extras/*` on `core`; `store/tck` is a test-scope dependency for
  everyone; nothing depends on `server`, `migration`, `examples` or `benchmark`; `benchmark/baseline-4x` depends on no
  project at all (it shares the package `me.ahoo.cosid` with 5.x). Changing a rule means changing 02-modules.md first.

## Conventions

- **Base package** `me.ahoo.cosid.*`; tests in the same package under `src/test/java`.
- **Every source file starts with the Apache 2.0 license header** (`config/checkstyle/java.header`); Checkstyle
  enforces it on main, test and JMH sources (tests and JMH skip every other check).
- **Logging:** SLF4J API only; Logback is test-only (`config/logback.xml`, `config/logback-jmh.xml`).
- **Skeleton bodies** throw `UnsupportedOperationException("<phase>")` naming the phase that implements them. There is
  no `URF_UNREAD_FIELD` exclusion any more: a skeleton keeps a field only if something reads it.
- **Coverage** (`codecov.yml`): patch and project targets 80%; `benchmark/`, `store/tck/`, `build-logic/` ignored.
- **Commits and PR titles** follow Conventional Commits (`feat(core)!:`, `build!:`); 5.0 PRs target `main`; 4.x fixes target `4.x`.
  The `4.x` maintenance branch takes **bug fixes only, no dependency updates**; Renovate targets `main` only (`baseBranches`).
  Commit messages are in English, authored by `Ahoo Wang <ahoowang@gmail.com>`, and never carry a `Co-authored-by:` trailer;
  squash merges pass an explicit `--body` so GitHub adds none.

## Gotchas

- **Benchmarks:** both benchmark projects use the same class and method names (`<Scenario>Benchmark.<op>_t<threads>`)
  so `benchmark/compare` can match rows. PRs report `benchmark/run-smoke`, never a comparison with stored numbers:
  smoke numbers from another time or a shared machine are noise. Results land in
  `benchmark/*/build/results/jmh/results.json`; committed results go to `docs/jmh/<version>/` with the machine and JDK noted. The `v5` scenarios fail at setup until R1.
- **Store integration tests** (R2) will follow the 4.x convention: `COSID_TEST_REDIS_URI`, `COSID_TEST_MYSQL_URL`/
  `_USERNAME`/`_PASSWORD`, `COSID_TEST_POSTGRES_URL`/…, `COSID_TEST_MONGODB_URI`; Testcontainers otherwise; one CI job
  per `store/*` module running the TCK. Each backend registers a `checkContractsRan` task like `store/tck/build.gradle.kts`
  (it runs `ContractsRan` on the JUnit XML reports), so a job whose contract cases were skipped fails.
- **MongoDB tests** (`store/mongo`) need a replica set (`majority` concerns): `COSID_TEST_MONGODB_URI`, otherwise a
  Testcontainers single-node replica set on `COSID_TEST_MONGODB_IMAGE` (default `mongo:7.0`; CI runs 6.0 and 8.0).
  P10 for Mongo is `benchmark/run-smoke MongoStoreBenchmark` with `COSID_BENCHMARK_MONGODB_URI` pointing at a replica set
  (default `mongodb://localhost:27017`).
- **ZooKeeper tests** (`store/zookeeper`) need no Docker: without `COSID_TEST_ZOOKEEPER_CONNECT` they start a curator-test
  server in process (ZooKeeper 3.9, the Curator one; CI also runs the TCK on 3.8), and the failover case always runs its own three-server `TestingCluster`. P10 on ZooKeeper
  (`benchmark/run-smoke ZookeeperStoreBenchmark`) likewise starts one per trial unless `COSID_BENCH_ZOOKEEPER_CONNECT` is set.
- **Lease tests** drive `LeaseKeeper` / `StaticSlot` without threads: a capturing executor, the package-private
  constructor taking an `internal.Ticker`, and `runDue()` / `extendIfDue()` for each step; `store/tck`'s `ManualClock`
  is the store or client clock. `core` tests may use `store/tck` (test scope).
- **Redis store tests** (`store/redis`) start a primary with a replica and a three-primary cluster in Testcontainers even
  when `COSID_TEST_REDIS_URI` is set (it covers standalone only); the cluster's node addresses are mapped to the published
  ports by a `MappingSocketAddressResolver`. `-Pcosid.test.redis.image=redis:7.0-alpine` runs them on another Redis (minimum 7.0, D27; default the latest 8.x).
- **spring/autoconfigure tests** use `ApplicationContextRunner` with a `memory` `StoreProvider` bean (`MemoryStores`, on
  `InMemoryStore`), H2 for JDBC, and Redis from `COSID_TEST_REDIS_URI` or Testcontainers (`-Pcosid.test.redis.image`).
  Generator beans are registered in a `BeanDefinitionRegistryPostProcessor`, so `RuntimeSpecCustomizer` beans must be
  dependency-free (`static @Bean`).
- **integrations** (`integrations/*`): tests run each adapter end to end on `store/tck`'s `TestRuntimes` (every
  strategy on `InMemoryStore`): MyBatis and Spring Data JDBC on H2, Activiti and Flowable as in-memory engines, Axon through
  `IdentifierFactory.getInstance()`. `CosIdIdentifierFactory` keeps the only static state of the project (its bindings);
  a test that binds must unbind; unbound, it throws. `TextIds` (text ids of any strategy) is a package-private copy in
  each module that needs it, since integrations must not depend on each other. The Spring side is `CosIdIntegrationsConfiguration` in `spring/autoconfigure`.
- `skills/` and `documentation/` still describe 4.x; they are rewritten in R5-B.
- **migration tests** write real 4.x data with the 4.1.0 jars: the `legacy4x` source set (`migration/src/test/legacy4x`) is
  compiled against them alone and loaded in its own class loader (path in `-Dcosid.migration.legacy4x`), so run them
  through Gradle. H2 (TCP) and ZooKeeper always run, Redis and MongoDB only with Docker. The admin path writes the 5.0
  store formats directly (`migration/src/main/.../target`): a change of a store's schema or node format must change it too.
- **store/jdbc tests** run the TCK on H2 always and on MySQL, MariaDB, PostgreSQL and SQL Server from
  `COSID_TEST_{MYSQL,MARIADB,POSTGRES,SQLSERVER}_URL` (+ `_USERNAME` / `_PASSWORD`) or Testcontainers at the D27 minimum
  (`mysql:8.4`, `mariadb:10.11`, `postgres:15-alpine`, SQL Server 2019; `COSID_TEST_<NAME>_IMAGE` overrides); without
  either the class skips. `checkContractsRan` checks each dialect's reports on its own: H2 by default, every dialect with
  `-Pcosid.store.jdbc.dialects=h2,mysql,mariadb,postgresql,sqlserver` (the `Store JDBC` CI job).
- **Remote TCK** (`server/`, since nothing may depend on `server`): `store/remote` runs the full TCK against a server
  started in process (`TestServer`, `server.port=0` as a command line argument so it beats `application.yaml`) on
  `InMemoryStore` with a `ManualClock`, on H2 through the JDBC store, and on Redis when `COSID_TEST_REDIS_URI` is set
  (the `Server` CI job). Contract servers relax `cosid.server.limits` (`min-ttl=1ms`, `max-reserve=Long.MAX_VALUE`);
  `server`'s own `checkContractsRan` is part of its `check`. `store/remote`'s own tests use a JDK `HttpServer` stub.
