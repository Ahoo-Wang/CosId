# AGENTS.md

Workspace instructions for ZCode agents working in **CosId** — a universal, flexible, high-performance
distributed ID generator for the JVM. Group `me.ahoo.cosid`, current version in `gradle.properties`.

## Toolchain

- **Java 17** (toolchain is pinned to 17 in the root `build.gradle.kts`; do not change). JDK 8 is supported only on 1.x.
- **Spring Boot alignment:** 4.x (`main`) and 3.x (`3.x` branch) → Spring Boot 4.x. 2.x → Spring Boot 3.x. 1.x → Spring Boot 2.x / Java 8.
- **Gradle (Kotlin DSL)** with version catalog at `gradle/libs.versions.toml`. Always use `./gradlew`, never a system `gradle`.
- **Lombok** is used project-wide (`lombok.config` enables `addLombokGeneratedAnnotation`). Configure annotation processing in your IDE; Lombok-generated code is excluded from coverage.
- Library modules are published with Javadoc + sources JARs and GPG signing (CI-only via `SIGNING_*` env vars). Publishing is triggered by GitHub release (`package-deploy.yml`).

## Common commands

```bash
# Build + full verification (compile, checkstyle, spotbugs, test, jacoco) for one module
./gradlew cosid-core:check
./gradlew cosid-jdbc:check

# Clean check (what CI runs per module)
./gradlew cosid-core:clean cosid-core:check

# Single test class / method
./gradlew cosid-core:test --tests "me.ahoo.cosid.segment.DefaultSegmentIdTest"
./gradlew cosid-core:test --tests "me.ahoo.cosid.segment.DefaultSegmentIdTest.shouldGenerate"

# JMH benchmark (JMH plugin applied to every library project)
./gradlew cosid-core:jmh
./gradlew cosid-spring-redis:jmh                    # needs Redis on localhost:6379
./gradlew cosid-jdbc:jmh                            # needs MySQL initialized

# Aggregate coverage report
./gradlew code-coverage-report:check
```

`check` runs **Checkstyle 9.2.1** (`config/checkstyle/checkstyle.xml`) and **SpotBugs**
(`config/spotbugs/exclude.xml`) on every library project — failing these fails the build, so keep edits
style-clean. Tests run on JUnit 5 (`useJUnitPlatform`); in CI (`CI=true`) flakes get up to 2 retries /
20 failures via the `test-retry` plugin.

## Module layout & architecture boundaries

Root `settings.gradle.kts` includes all modules. Note several modules live under non-obvious directories:

- `cosid-core` — core ID algorithms. **No Spring, no DB, no middleware deps, no Guava.** Contains `snowflake`, `segment` /
  `SegmentChainId`, `machine` (MachineId leases), `converter`, `provider`, `stat`. Everything else depends on this.
- `cosid-sharding` — the sharding algorithms (`ModCycle`, `IntervalTimeline`, `CachedSharding`), package `me.ahoo.cosid.sharding`.
- `cosid-accessor` — id field accessors and the `@CosId` annotation, packages `me.ahoo.cosid.accessor` and `me.ahoo.cosid.annotation`.
- `cosid-jdbc`, `cosid-spring-redis`, `cosid-zookeeper`, `cosid-mongo`, `cosid-spring-data-jdbc` — `IdSegmentDistributor`
  / machine-state backends. Each typically `api(project(":cosid-core"))` + `testImplementation(project(":cosid-test"))`.
- `cosid-spring-boot-starter` — Spring Boot autoconfiguration aggregator. Backend deps are wired as **Gradle feature
  variants** (`springRedisSupportImplementation`, `jdbcSupportImplementation`, `proxySupportImplementation`, …). When
  adding a backend, register a `registerFeature(...)` here and a corresponding optional starter dependency.
- `proxy/` → `cosid-proxy-api`, `cosid-proxy`, `cosid-proxy-server` (paths remapped in `settings.gradle.kts`).
- `examples/` → `cosid-example-*` (proxy, redis, redis-cosid, zookeeper).
  Examples + `cosid-proxy-server` are excluded from publishing.
- `cosid-bom`, `cosid-dependencies` — Gradle platform BOMs. `cosid-dependencies` is the central version catalog consumers
  must `api(platform(...))`. **Add/upgrade dependency versions here**, not in individual modules.
- `cosid-test`, `cosid-mod-test` — shared test infrastructure (excluded from coverage, see `codecov.yml`).
- `cosid-jackson`, `cosid-mybatis`, `cosid-axon`, `cosid-activiti`, `cosid-flowable` — integration/adapter layers.
- `cosid-benchmark` — JMH benchmarks (excluded from coverage).

**Layer rule:** `cosid-core` must stay dependency-free of Spring and middleware. Backends depend on core; the starter
depends on backends. Don't introduce core → backend or backend → starter cycles.

## Conventions

- **Base package:** `me.ahoo.cosid.*`, mirrored under `src/main/java`/`src/test/java` per module.
- **Every source file starts with the Apache 2.0 license header** (`config/checkstyle/java.header`). Checkstyle's `Header`
  check enforces it on main, test and JMH Java sources, so add it to every new file.
- **Logging:** SLF4J API only in library code (`org.slf4j:slf4j-api`); Logback is a test-only dependency. Use Lombok
  `@Slf4j`. Test JVMs are launched with `-Dlogback.configurationFile=.../config/logback.xml`.
- **Testing:** JUnit Jupiter + `junit-pioneer` + Hamcrest; shared helpers live in `cosid-test`. Prefer adding tests under
  the same package in the module's `src/test/java`.
- **Coverage gates** (`codecov.yml`): patch target 60%, project target 80%. `cosid-test`, `examples`, `cosid-benchmark`,
  and `cosid-proxy-server` are ignored.
- **Commit messages and PR titles** follow Conventional Commits (`feat(scope):`, `fix(deps):`, `chore(release): bump version ...`).
  The `Pull Request Title` workflow enforces it; the `Pull Request Labeler` workflow labels PRs from the title type and changed files.
  See `CONTRIBUTING.md`.
  Releases are tagged `v<x.y.z>` and bump `version=` in `gradle.properties`.

## Gotchas

- **Integration tests need live services:** the Redis, MySQL, PostgreSQL and MongoDB tests read
  `COSID_TEST_REDIS_URI`, `COSID_TEST_MYSQL_URL`/`_USERNAME`/`_PASSWORD`, `COSID_TEST_POSTGRES_URL`/`_USERNAME`/`_PASSWORD`
  and `COSID_TEST_MONGODB_URI`; without them they start the service with Docker, and are skipped when neither is
  available. CI starts the services in `integration-test.yml` and checks that the contract tests ran. Scope to
  a unit test class with `--tests` when no backend is at hand.
- **Other Spring Boot and Java versions:** `-PspringBootVersion=4.0.8` builds against another Boot, `-PtestJavaVersion=21`
  runs the tests on another JDK (compilation stays on 17); the `cosid-compatibility-test` CI job runs the matrix. The
  published metadata requires the versions of `spring-boot-minimum` (libs.versions.toml), never the build platform:
  `checkPublishedMetadata` (part of `check`) fails otherwise.
- **`-parameters` compiler flag is required** (set root-wide) — Spring Boot needs it for `CosIdEndpoint` parameter names; don't remove it.
- **Tests open JDK internals:** test JVMs pass `--add-opens=java.base/java.util=ALL-UNNAMED` and `...java.lang=...`. If a
  new test touches JDK internals, the existing flags already cover it.
- **`cosid-skills-workspace/`** is git-ignored agent eval scratch space — don't treat it as product code.
- Documentation sites: `document/` (VuePress, older, 3.x and earlier) and `documentation/` (VitePress, current at `cosid.ahoo.me`). UI/logo assets live under `documentation/docs/public/`.
- **Site layout** (`documentation/docs/{en,zh}`, kept in sync page for page): `guide/introduction` and `getting-started`, `guide/concepts/`
  (generators, machine id, converters, provider), `guide/backends/` (one page per store, `index` chooses), `guide/integrations/`,
  `guide/operations/` (production checklist, rolling upgrade, monitoring, troubleshooting, FAQ), `guide/migration/`, and
  `reference/config/` (every `cosid.*` property with its default, taken from the `*Properties` classes). When a page moves, add
  its old path to `documentation/docs/.vitepress/theme/redirects.ts` and update both sidebars in `.vitepress/configs/`.
  Build with `pnpm install && pnpm docs:build` in `documentation/`; the site deploys on every push to `main` touching `documentation/**`.

## Further reading before sensitive changes

- `documentation/docs/en/guide/concepts/` — design rationale for `SnowflakeId`, `SegmentId`, `SegmentChainId` and machine id leases.
- `docs/MIGRATION-4.0.md` — every breaking change of 4.0; `docs/ROADMAP.md` — what each phase delivered.
- Domain-specific skills are available and should be used for matching tasks: `cosid-spring-boot`, `cosid-sharding`,
  `cosid-strategy-guide`, `cosid-manual-integration`.
