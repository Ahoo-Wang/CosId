# 5.0.0 release gate

The evidence of the 5.0.0 release (D21, 10-performance-and-parity.md §1.3), from the eighth run of the release gate,
[run 40887469149](https://github.com/Ahoo-Wang/CosId/actions/runs/40887469149), on `be35de36`, the GA candidate, against 4.1.1 on GitHub's shared runners (D31):

| File | What |
|---|---|
| [report.md](report.md) | the verdict by the rules of D50 (#1421), judged again on the run's raw JSON ([run 40969568615](https://github.com/Ahoo-Wang/CosId/actions/runs/40969568615)): **8 of 8 groups pass, with one exception the user accepted**, `MySqlStoreBenchmark.acquire_t1` p99.9 (a component row, P10) |
| [report-d34.md](report-d34.md) | the run's own verdict, under the rules before D50: FAIL, 3 of 8 groups, each with one p99.9 row |
| [baseline-4x.md](baseline-4x.md) | the 4.1.1 side of the run alone |

The raw JMH JSON stays with the run (`release-gate-report`, until it expires). Each group ran on a runner of its own
(AMD EPYC 9V45, 9V74 or 7763, 4 cores; report.md lists them). The segment group's runner, an EPYC 9V45, was slower
than the seventh run's: a bare `AtomicLong` reached 214 ops/µs at one thread (the P5 reference), and a segment at one
thread reached 101–102% of it on 5.x, 73–74% on 4.x.
