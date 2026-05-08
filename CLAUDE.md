# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CosId is a universal, flexible, high-performance distributed ID generator for Java 17+. It provides several ID generation strategies:

- **CosIdGenerator**: Standalone high-performance ID generator (~15M+ ops/s)
- **SnowflakeId**: 64-bit distributed ID with machine ID allocation and clock sync
- **SegmentId**: Batch ID allocation to reduce network IO
- **SegmentChainId**: Lock-free enhancement of SegmentId (~127M+ ops/s)

## Build Commands

```bash
# Build entire project
./gradlew build

# Build specific module
./gradlew :cosid-core:build

# Run all tests
./gradlew test

# Run single test class
./gradlew :cosid-core:test --tests "me.ahoo.cosid.snowflake.MillisecondSnowflakeIdTest"

# Run single test method
./gradlew :cosid-core:test --tests "me.ahoo.cosid.snowflake.MillisecondSnowflakeIdTest.generate"

# Lint/Check (Checkstyle + SpotBugs)
./gradlew check

# Run JMH benchmark (all)
./gradlew :cosid-core:jmh

# Run JMH benchmark (filtered by class name)
./gradlew :cosid-core:jmh -PjmhIncludes=SnowflakeIdBenchmark

# Run Spring Boot example
./gradlew :cosid-example-redis:bootRun
```

## Architecture

### Module Dependency Graph

`cosid-dependencies` (BOM/platform managing all versions) is the root dependency platform. Every library module depends on it. `cosid-bom` publishes version constraints for all library modules as a Maven BOM.

`cosid-core` is the foundation with no external dependencies (only jspecify for null-safety annotations). All other library modules depend on `cosid-core`.

Distributor implementations depend on `cosid-core` and provide their respective backend client:
- `cosid-spring-redis` → Spring Data Redis
- `cosid-jdbc` → JDBC/HikariCP
- `cosid-zookeeper` → ZooKeeper Curator
- `cosid-mongo` → MongoDB driver (sync + reactive)

`cosid-spring-boot-starter` is the main integration module. It uses Gradle **capability features** to optionally pull in distributor modules. Each capability (`springRedisSupport`, `jdbcSupport`, `mongoSupport`, `zookeeperSupport`, `proxySupport`, `mybatisSupport`, `cloudSupport`, `actuatorSupport`, `flowableSupport`, `activitiSupport`, `dataJdbcSupport`) is a separate Gradle feature variant that conditionally includes the corresponding implementation module.

### Core ID Generation (`cosid-core`)

The type hierarchy: `IdGenerator` is the base interface with `generate()` (long) and `generateAsString()` (String). `IdConverter` handles long↔String conversion (default: `Radix62IdConverter.PAD_START`).

**Snowflake family**: `SnowflakeId` interface → `AbstractSnowflakeId` → `MillisecondSnowflakeId`, `SecondSnowflakeId`. `SafeJavaScriptSnowflakeId` wraps any SnowflakeId to constrain IDs to JavaScript's `Number.MAX_SAFE_INTEGER`. Bit layout: 63 bits total (timestamp + machineId + sequence), configurable per implementation.

**Segment family**: `SegmentId` interface represents a single ID segment (offset→maxId range). `SegmentChainId` chains segments with a lock-free prefetch worker (`PrefetchWorkerExecutorService`) that dynamically adjusts prefetch distance based on consumption rate.

**Machine ID**: `MachineIdDistributor` allocates unique machine IDs per namespace. Implementations (Redis, JDBC, ZooKeeper, MongoDB, Manual, StatefulSet) handle distribute/revert/guard (heartbeat). `ClockBackwardsSynchronizer` handles clock drift.

**Sharding**: `sharding/` package provides sharding algorithms (ShardingSphere-compatible) including `PreciseSharding`, `IntervalSharding`, and `CeilingRadixSharding`.

### Proxy (`proxy/`)

Standalone proxy server for remote ID generation over HTTP:
- `cosid-proxy-api` → API/client contracts
- `cosid-proxy` → Client implementation (uses Spring Web + coapi)
- `cosid-proxy-server` → Server (Spring Boot WebFlux app)

### Shared Test Infrastructure

- `cosid-test` → Shared test utilities (Hamcrest matchers, test base classes, Testcontainers support)
- `cosid-mod-test` → Additional module-level test support

Integration tests across the project use Testcontainers (MySQL, MongoDB, Redis via Spring Boot test slices).

## Development Notes

- **Java toolchain**: Java 17 (enforced via Gradle toolchain)
- **Dependencies**: Managed via `gradle/libs.versions.toml` (Gradle Version Catalogs) and `cosid-dependencies` platform
- **Lombok**: Used extensively throughout the project for boilerplate reduction
- **Code style**: Checkstyle with Google Java Style (4-space indent, 200 char line limit). Config in `config/checkstyle/`
- **Static analysis**: SpotBugs with exclusions at `config/spotbugs/exclude.xml`
- **Import order** (enforced by Checkstyle): STATIC → `me.ahoo.*` (SPECIAL_IMPORTS) → THIRD_PARTY → STANDARD_JAVA_PACKAGE. Each group separated by a blank line.
- **Test retry**: Enabled on CI only (`CI` env var) — max 2 retries, max 20 failures before stopping. `failOnPassedAfterRetry = true`
- **JMH benchmarks**: Use `-PjmhIncludes=ClassName` to filter, `-PjmhThreads=N` for thread count, `-PjmhMode=thrpt` for mode
- **Compiler flag**: `-parameters` is set globally (required for Spring Boot 3.2.0+ parameter name extraction)
- **JVM test args**: `--add-opens=java.base/java.util=ALL-UNNAMED` and `--add-opens=java.base/java.lang=ALL-UNNAMED`
