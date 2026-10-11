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

## Phase 1: contract tests and SPI (3.x) — next

- Run every backend (Redis, JDBC, ZooKeeper, MongoDB, proxy) against one shared TCK, including races and expiry.
- Move the machine ID allocation algorithm into core, behind a small store SPI with compare-and-set primitives (`claimSelf`, `claimReleased`, `claimNew`, `claimExpired`, `renew`, `release`), so that backends only implement the primitives.
- Prefer the store's clock over the host clock where the store provides one.

## Phase 2: module split (4.0) — planned

- Split modules along the target architecture (core, store SPI and backends, starter), with breaking API changes allowed.
- Model the machine ID lease explicitly in generators.
