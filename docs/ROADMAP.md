# CosId Roadmap

This roadmap follows the architecture review of October 2026. Each item links to the pull request that delivered it.

## Phase 0: correctness and safety fixes (3.3.0) — done

Behavior fixes only, no breaking API changes.

| Item | Change | PR |
|------|--------|----|
| A1 | Generators stop issuing IDs once the machine ID lease is lost or expired (fail fast, on by default) | #1151 |
| A2 | Distribution always confirms a locally cached machine ID with the remote store | #1149 |
| A3 | The machine ID of a stable instance is never recycled by other instances (all backends) | #1160 |
| A4 | ZooKeeper claims and releases machine IDs with versioned transactions; guard checks ownership | #1146 |
| A5 | Proxy server is stateless for client machine IDs; optional bearer token auth; input limits | #1145, #1164, #1165 |
| A6 | Redis keeps the segment offset when the adder key is lost; values above 2^53 stay exact | #1147 |
| A7/A8 | Every distributed machine ID is reverted after the web server stops | #1150 |
| A9 | Backend calls are bounded by timeouts (JDBC, Redis, ZooKeeper, MongoDB, proxy) | #1153, #1162, #1163 |
| A10 | The local clock-backwards watermark is refreshed every second | #1161 |
| A11 | Each Spring context uses its own `IdGeneratorProvider` | #1154 |
| C1 | A hungry segment chain prefetch is capped | #1152 |
| D1 | Redis and JDBC run against the shared distributor specs (contract tests) | #1148, #1158 |

## Phase 1: contract tests and SPI (3.4.0) — done

| Item | Change | PR |
|------|--------|----|
| P1-1..3 | Shared lease spec (`MachineLeaseStoreSpec`), lease SPI and core allocation (`MachineLeaseManager`, `LeaseStoreMachineIdDistributor`) | #1173, #1177 |
| P1-5..7 | Every backend runs the shared distributor specs (PostgreSQL, proxy end to end); one segment rollback rule | #1169, #1170, #1178 |
| P1-8..11 | Lease stores for Redis, JDBC (MySQL, PostgreSQL), ZooKeeper and MongoDB, using the store clock where it has one | #1181, #1182, #1183, #1185 |
| P1-12 | Shared timestamp and sequence logic (`TimestampSequencer`) | #1180 |
| P1-13 | Starter backend SPI (`CosIdStoreProvider`) | #1188, #1191 |
| P1-14 | Explicit grouping API (`generateGrouped()`, `GroupedId`) | #1186 |
| P1-4 | Fake JDBC and Redis backends replaced by real ones | #1187 |
| C5, D3 | ZooKeeper connects only when selected; optional integrations gated and switchable | #1189, #1190 |

## Phase 2: 4.0 — in progress on `main`

`main` is the 4.0 line; 3.x gets fixes on the `3.x` maintenance branch. Breaking changes are listed in [MIGRATION-4.0.md](MIGRATION-4.0.md). Releases go 4.0.0-M1, then RC1, then GA.

### Decisions

- Java 17 and Spring Boot 4.x only.
- Artifact ids and package names stay. New modules `cosid-sharding` and `cosid-accessor`; no separate api module.
- Minimal generator split: a new `StringIdGenerator`, which `IdGenerator` extends; `CosIdGenerator` and the UUID generator implement only `StringIdGenerator`.
- The old machine id distributors are removed; the starter uses `MachineLeaseStore` with `LeaseStoreMachineIdDistributor`.
- 4.0 reads and writes both the old and the new lease formats, so 3.x and 4.0 instances can run side by side. JDBC needs an `ALTER TABLE`; startup fails naming the statement when the columns are missing.
- The proxy keeps its v1 API, deprecated and delegating to the new v2 lease API.
- `cosid.jdbc.enabled` and `cosid.actuator.delete.enabled` default to `false`.

### Plan

| Item | Change | Milestone |
|------|--------|-----------|
| P3-0 | Open the 4.0 line: `4.0.0-SNAPSHOT`, release workflows for both lines, report-only API diff against 3.4, migration guide skeleton | M1 |
| P3-1 | Remove MongoDB reactive support | M1 |
| P3-2 | Remove the thread-local grouped path | M1 |
| P3-3 | Remove the remaining `forRemoval` APIs | M1 |
| P3-4 | Flip the `cosid.jdbc.enabled` and `cosid.actuator.delete.enabled` defaults | M1 |
| P3-5 | Move the test-only segment distributors to `cosid-test` | M1 |
| P3-6 | New `cosid-sharding` module | M1 |
| P3-7 | New `cosid-accessor` module | M1 |
| P3-8 | Remove Guava from `cosid-core` | M1 |
| P3-9 | `StringIdGenerator` and typed `IdGeneratorProvider` lookups | M1 |
| P3-10 | `SnowflakeLayout` | M1 |
| P3-16 | `SegmentChainId` lifecycle: `AutoCloseable`, injected prefetch executor, per-task timeout | M1 |
| P3-17 | Axon identifier factory warns about multiple providers | M1 |
| P3-11 | Lease model with watermark, renewal time and version; new store formats that still read the old ones (Redis: a `lease` hash; JDBC: three columns and an `ALTER`; ZooKeeper and MongoDB keep the 3.4 format on purpose, since 3.4 parses ZooKeeper nodes strictly and the 3.4 timestamp already covers the lease) | RC1 |
| P3-12 | Proxy v2 lease API; v1 delegates to it | RC1 |
| P3-13 | Starter uses the lease stores; old distributors removed | RC1 |
| P3-14 | Property model: string distributor type, records, `ZoneId` | RC1 |
| P3-15 | Generator registration as a bean definition registry post-processor | RC1 |
| P3-18 | Migration guide, README, docs and samples | RC1 |
