# Benchmarks

**5.0 must not be slower than 4.x** (D21). This is a release gate, not a goal. Every scenario runs on the released
4.1.0 jars and on 5.0, and the comparison decides.

## How it is measured

4.x and 5.0 share the package `me.ahoo.cosid`, so they cannot share a classpath. The benchmarks are two JMH projects
with the same scenarios and the same class and method names (`<Scenario>Benchmark.<op>_t<threads>`):

```
benchmark/
  baseline-4x/   me.ahoo.cosid:*:4.1.0
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
| P6 | segment, step 1, no prefetch | throughput |
| P7 | segment on MySQL / Redis / MongoDB / ZooKeeper | throughput, p99 / p99.9 |
| P8 | periodic segment (daily) across period switches | throughput, p99.9 at the switch |
| P9 | codecs: Radix62/36 encode and decode, Friendly | throughput, allocation |
| P10 | store primitives: reserve, acquire, renew, per backend | mean and p99 latency |
| P11 | Spring context start to the first id | time |
| P12 | `IdFiller.fill` | throughput, allocation |

A row's limit is the wider of 4.x with its error (JMH's 99.9% confidence interval) and 4.x with a tolerance: 5% for
throughput and average time, 10% for p99 / p99.9, and the larger of 5% and +64 B for allocation. A row that fails runs
again in fresh forks, and only a failed re-run counts as a regression. The gate is end-to-end ID generation (D44): a
row of a component (P9 codecs, P10 store primitives) that misses these limits only warns, up to 20% for throughput and
average time, 30% for p99 / p99.9 and the larger of 25% and +64 B for allocation, and fails beyond them. Release numbers come only from a fixed, quiet physical machine. Numbers from a
shared machine or another time are noise.

Run it yourself:

```bash
benchmark/run-smoke P5 P12                          # 4.x and 5.0 back to back per scenario, then compare --smoke
./gradlew :benchmark:v5:jmh -PjmhProfile=release    # the release-gate settings; -PjmhIncludes=A,B narrows
benchmark/compare BASELINE.json CANDIDATE.json
```

## 5.0 results

<!-- R5-A fills this section with the 5.0 release-gate report: machine, JDK, the compare table per scenario, and a
     link to docs/jmh/5.0.0/. -->

::: info Pending
The 5.0 release-gate report is produced by the release benchmarks (R5-A) on a dedicated machine. Its results will be
committed to [`docs/jmh/`](https://github.com/Ahoo-Wang/CosId/tree/main/docs/jmh) (`docs/jmh/<version>/`, with the
machine and the JDK) and summarized here.
:::

## Historical results

`docs/jmh/` also holds 4.x results (`SegmentChainId` on MySQL and Redis), kept for reference. They were measured on
other hardware and are not comparable with the release gate.
