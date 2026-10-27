# AGENTS.md

Workspace instructions for agents working in **CosId** — a universal, flexible, high-performance distributed ID
generator for the JVM. Group `me.ahoo.cosid`, current version in `gradle.properties`.

**This is the `5.x` branch: the clean-room 5.0 rewrite.** The design lives in [`docs/architecture/`](docs/architecture/README.md)
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
benchmark/compare BASELINE.json CANDIDATE.json --smoke
python3 -I benchmark/compare_test.py               # tests of the compare script
```

`check` fails on any Checkstyle (`config/checkstyle/checkstyle.xml`, Checkstyle 10.x) or SpotBugs
(`config/spotbugs/exclude.xml`) finding. In CI (`CI=true`) flaky tests get up to 2 retries via `test-retry`.

## Layout (02-modules.md §1)

Directories carry no `cosid-` prefix (D2). The published artifactId is derived from the path by
`build-logic/.../ArtifactIds.kt` (D11): `"cosid-" + path.replace('/', '-')`, `spring/*` → `cosid-spring-boot-*`,
`extras/*` → `cosid-*` (so `extras/sharding` stays `cosid-sharding`).

| Directory | Artifact | State |
|---|---|---|
| `core/` | `cosid-core` | R0: public API frozen; bodies throw `UnsupportedOperationException("R1-x")` until R1/R3 |
| `store/tck/` | `cosid-store-tck` | R1-C: the contracts, `InMemoryStore` (passes them), `ManualClock`, fault injection, `ContractsRan` |
| `store/mongo/` | `cosid-store-mongo` | R2-Mongo: `MongoStore` (`RangeStore` + `SlotLeaseStore`, sync driver, MongoDB 6.0+), `StoreProvider` type `mongo` |
| `store/redis/` | `cosid-store-redis` | R2-Redis: Lua scripts behind `RedisScripts`, Lettuce, `WAIT` auto; see its README (durability precondition) |
| `store/zookeeper/` | `cosid-store-zookeeper` | R2-ZK: `ZookeeperStore` (Curator 5.9), provider type `zookeeper`, ZooKeeper 3.8+ (3.9 primary, D27); TCK on `COSID_TEST_ZOOKEEPER_CONNECT` or an in-process curator-test server |
| `bom/` | `cosid-bom` | the project's own artifacts; add each published module |
| `benchmark/baseline-4x/` | not published | JMH on `me.ahoo.cosid:*:4.1.0`; see `benchmark/scenarios.md` |
| `benchmark/v5/` | not published | the same scenarios on 5.x |
| `store/{jdbc,redis,mongo,zookeeper,remote}`, `server/`, `spring/{autoconfigure,starter}`, `integrations/`, `extras/sharding/`, `migration/`, `examples/` | | not created yet (R2–R5) |
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
- `cosid.platform`: the BOM. `cosid.benchmark`: `cosid.java` + JMH (`me.champeau.jmh`) — JMH is applied nowhere else.
- `cosid.root` (root project): `checkDependencyRules`, wired into `check` with build-logic's own tests.
- **Dependency rules (02 §3)** are data in `DependencyRules.kt`, unit tested in `DependencyRulesTest.kt`: `core` depends
  on `slf4j-api` only; `store/*`, `integrations`, `extras/*` on `core`; `store/tck` is a test-scope dependency for
  everyone; nothing depends on `server`, `migration`, `examples` or `benchmark`; `benchmark/baseline-4x` depends on no
  project at all (it shares the package `me.ahoo.cosid` with 5.x). Changing a rule means changing 02-modules.md first.
- The Spring Boot compatibility matrix (`-PspringBootVersion`) returns with `spring/*` in R3-S, scoped to `spring/*`,
  `server` and `integrations` (02 §5).

## Conventions

- **Base package** `me.ahoo.cosid.*`; tests in the same package under `src/test/java`.
- **Every source file starts with the Apache 2.0 license header** (`config/checkstyle/java.header`); Checkstyle
  enforces it on main, test and JMH sources (tests and JMH skip every other check).
- **Logging:** SLF4J API only; Logback is test-only (`config/logback.xml`, `config/logback-jmh.xml`).
- **Skeleton bodies** throw `UnsupportedOperationException("<phase>")` naming the phase that implements them. There is
  no `URF_UNREAD_FIELD` exclusion any more: a skeleton keeps a field only if something reads it.
- **Coverage** (`codecov.yml`): patch and project targets 80%; `benchmark/`, `store/tck/`, `build-logic/` ignored.
- **Commits and PR titles** follow Conventional Commits (`feat(core)!:`, `build!:`); PRs of 5.x phases target `5.x`.

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
  ports by a `MappingSocketAddressResolver`. `-Pcosid.test.redis.image=redis:6.2-alpine` runs them on another Redis.
- `skills/` and `documentation/` still describe 4.x; they are rewritten in R5-B.
