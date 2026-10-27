# Release gate analysis (WIP)

Analysis of the formal release gate run
[40534275762](https://github.com/Ahoo-Wang/CosId/actions/runs/40534275762) (ref `main`, publish = true): the rows that
still fail after their re-run, with the suspected cause and the phase that owns each. Investigated from the CI
artifacts (`release-gate-*`, `logs-*`) only, never from local benchmark runs.

## Status

- [ ] Run finished; report PR opened by the workflow (4 of 8 groups done)
- [ ] Report PR reviewed
- [ ] Each still-failing row investigated (suspected cause + owner)
- [ ] Summary sent to the coordinating session

## Rows that still fail

Groups done so far: spring-start PASS (1 row), snowflake PASS (9 rows), mongo FAIL (1 of 6), codec-filler FAIL (4 of 7).
Runner of each group: see the report.

| Group | Row | First run | Re-run | Owner | Suspected cause |
|---|---|---|---|---|---|
| codec-filler | `CodecBenchmark.radix62Decode_t1` thrpt | 81,604 vs 68,844 ops/ms (-16%) | 80,496 vs 69,614 (-14%) | R1-A | `Radix.readDigits` serves every radix with the radix in a field: each digit is an overflow-checked `multiplyExact(value, radix)` with a non-constant multiplier plus a `VALUES` bounds check; 4.x's `Radix62IdConverter` had the radix as a constant of its subclass. Candidate fix: a constant-radix loop per common radix (as `writeDigits` already does), and skip the overflow check when the width cannot overflow a long. Allocation is equal (0). |
| codec-filler | `CodecBenchmark.radix36Decode_t1` thrpt | 73,938 vs 64,643 (-13%) | 75,475 vs 64,425 (-15%) | R1-A | Same as radix62Decode. |
| codec-filler | `CodecBenchmark.radix62Encode_t1` thrpt | 54,303 vs 46,625 (-14%) | 53,023 vs 46,317 (-13%) | R1-A | `Radix.encode` writes a `byte[]`, pads it with `Arrays.fill`, then `new String(buf, …, ISO_8859_1)` copies it again (the decode path of the charset); allocation is lower (88 vs 96 B/op) but the extra pass and the charset path cost throughput. Candidate fix: build the String without the second copy (e.g. a char[]/`String` from a pre-sized LATIN1 path, or only write the significant digits and pad on copy). |
| codec-filler | `CodecBenchmark.radix36Encode_t1` thrpt | 48,142 vs 44,510 (-8%) | 49,384 vs 46,141 (-7%) | R1-A | Same as radix62Encode. |
| mongo | `MongoStoreBenchmark.reserve_t1` avgt, allocation | 32,747 vs 33,377 B/op (+1.9%) | 31,715 vs 33,262 (+4.9%) | R2-Mongo | Only the allocation fails, by 0.6–1.5 KB/op of ~33 KB, and latency is much better than 4.x (avgt 2.1 ms vs 2.0–3.7 ms; p99 7 ms vs 62 ms). The 5.x reserve is one `findOneAndUpdate` with an update pipeline (`$$NOW`, the bounds) and majority concerns; its larger BSON command and reply most likely account for the extra bytes, all inside the driver. Options: trim the command (fewer fields in the projection/pipeline), or accept with data — a decision for the coordinator. |

_Pending: segment, mysql, redis, zookeeper._

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
