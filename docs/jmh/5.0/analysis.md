# Release gate analysis (WIP)

Analysis of the formal release gate run
[40534275762](https://github.com/Ahoo-Wang/CosId/actions/runs/40534275762) (ref `main`, publish = true): the rows that
still fail after their re-run, with the suspected cause and the phase that owns each. Investigated from the CI
artifacts (`release-gate-*`, `logs-*`) only, never from local benchmark runs.

## Status

- [ ] Run finished; report PR opened by the workflow
- [ ] Report PR reviewed
- [ ] Each still-failing row investigated (suspected cause + owner)
- [ ] Summary sent to the coordinating session

## Rows that still fail

_To be filled in once the run completes._

## Process notes (to resume on any machine)

- Context: R5-A. Part 1 merged (#1335 parity tests / stability loop / CI dedup, #1336 parity gaps, #1343 release gate on
  CI). Stability: every integration class 20x on CI, 0 failures (run 40497781751).
- The gate runs only on CI (user rule; local CPU load must stay <= 90%). Never trigger a second gate run without the
  coordinating session's go.
- Fetch results: `gh run download 40534275762 -n release-gate-report` (joined report), `-n release-gate-<group>`
  (per group), `-n logs-<group>` (JMH logs of every run and re-run, for a missing row or an odd number).
- Known: benchmark/v5 imports netty-bom because ZooKeeper's Netty 4.1 epoll breaks Lettuce 7 on Linux; R2-ZK is
  fixing store/zookeeper (option a: exclude ZooKeeper's Netty). Drop the BOM after that lands and check Redis rows.
- Dry-run signals to watch (smoke settings, x86 runners; not results): P6 SegmentNoPrefetch alloc 24 -> 32 B/op;
  P10 MySQL alloc (reserve 9.2K -> 15K, acquire 53K -> 66K, renew 17K -> 22K B/op); codec decode ~15% lower thrpt.
- Owners (aggregate-release): codec / snowflake -> R1-A, segment -> R1-B, MySQL -> R2-JDBC, Redis -> R2-Redis,
  Mongo -> R2-Mongo, ZooKeeper -> R2-ZK, IdFiller -> R3, Spring start -> R3-S.
