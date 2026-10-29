# Benchmarks

**5.0 must not be slower than 4.x** (D21). This is a release gate, not a goal. Every scenario runs on the released
4.1.1 jars and on 5.0, and the comparison decides.

## How it is measured

4.x and 5.0 share the package `me.ahoo.cosid`, so they cannot share a classpath. The benchmarks are two JMH projects
with the same scenarios and the same class and method names (`<Scenario>Benchmark.<op>_t<threads>`):

```
benchmark/
  baseline-4x/   me.ahoo.cosid:*:4.1.1
  v5/            the 5.0 modules
  scenarios.md   the scenarios: parameters, threads, warm-up, iterations
  compare        reads both JMH JSON results and prints PASS / FAIL per row
```

| # | Scenario | Metric |
|---|---|---|
| P1 | Snowflake milliseconds, 1 / 4 / 16 threads | throughput |
| P2 | Snowflake seconds, 1 / 16 threads | throughput |
| P3 | Snowflake + Radix62 text, + Friendly text | throughput, allocation |
| P4 | 80-bit CosId Radix62 / Friendly | throughput, allocation |
| P5 | segment on an in-memory store, 1 / 4 / 16 / 64 threads | throughput, p99 / p99.9 |
| P5 reference | one shared `AtomicLong`, 1 / 4 / 16 / 64 threads (5.x side only, not judged): P5 on both sides as a share of it | throughput |
| P6 | segment, step 1, no prefetch | throughput |
| P7 | segment on MySQL / Redis / MongoDB / ZooKeeper | throughput, p99 / p99.9 |
| P8 | periodic segment (daily) across period switches | throughput, p99.9 at the switch |
| P9 | codecs: Radix62/36 encode and decode, Friendly | throughput, allocation |
| P10 | store primitives: reserve, acquire, renew, per backend | mean and p99 latency |
| P11 | Spring context start to the first id | time |
| P12 | `IdFiller.fill` | throughput, allocation |
| P13 | UUIDv7 (5.x only, a trend, not judged), 1 / 4 / 16 threads | throughput |

A row's limit is the wider of 4.x with its error (JMH's 99.9% confidence interval) and 4.x with a tolerance: 5% for
throughput and average time, 10% for p99 / p99.9, and the larger of 5% and +64 B for allocation. A row that fails runs
again in fresh forks, and only a failed re-run counts as a regression. The store scenarios (P7, P10) run each side
twice in ABBA order (4.x, 5.x, 5.x, 4.x), so that neither side always runs right after the other on the store. The gate is end-to-end ID generation (D44): a
row of a component (P9 codecs, P10 store primitives) that misses these limits only warns, up to 20% for throughput and
average time, 30% for p99 / p99.9 and the larger of 25% and +64 B for allocation, and fails beyond them. The gate runs
on GitHub's shared runners (D31): both sides of a scenario run on the same runner, interleaved, and are judged against
each other with these tolerances, never against numbers from another runner or another run, which are noise.

Run it yourself:

```bash
benchmark/run-smoke P5 P12                          # 4.x and 5.0 back to back per scenario, then compare --smoke
./gradlew :benchmark:v5:jmh -PjmhProfile=release    # the release-gate settings; -PjmhIncludes=A,B narrows
benchmark/compare BASELINE.json CANDIDATE.json
```

## 5.0 results

<!-- R2-perf-01: the summary of the gate on the GA commit, committed to docs/jmh/5.0.0/, goes here. -->

The gate passed on the release candidate: the seventh run against 4.1.1
([run 40832851823](https://github.com/Ahoo-Wang/CosId/actions/runs/40832851823)), 8 of 8 groups, with 3 component rows
warning (D44). End to end, the segment chains at one thread, step 10, issue 4.x → 5.0:

| Store | 4.1.1 (ops/µs) | 5.0 (ops/µs) |
|---|---|---|
| MongoDB | 1.75 | 392 |
| MySQL | 0.47 | 209 |
| Redis | 0.86 | 211 |
| ZooKeeper | 0.28 | 405 |

::: info GA
The gate runs again on the GA commit; its summary (`report.md`, and the 4.1.1 side as `baseline-4x.md`) is committed to
[`docs/jmh/`](https://github.com/Ahoo-Wang/CosId/tree/main/docs/jmh) (`docs/jmh/5.0.0/`) and summarized here.
:::

## Historical results

`docs/jmh/` also holds 4.x results (`SegmentChainId` on MySQL and Redis), kept for reference. They were measured on
other hardware and are not comparable with the release gate.
