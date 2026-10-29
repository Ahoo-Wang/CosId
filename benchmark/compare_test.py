#
# Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#      http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
"""Tests of benchmark/compare: python3 -I benchmark/compare_test.py"""

import contextlib
import importlib.machinery
import importlib.util
import io
import json
import os
import tempfile
import unittest

_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "compare")
_loader = importlib.machinery.SourceFileLoader("compare", _path)
_spec = importlib.util.spec_from_loader("compare", _loader)
compare = importlib.util.module_from_spec(_spec)
_loader.exec_module(compare)


def row(benchmark, mode="thrpt", threads=1, score=100.0, error=10.0, percentiles=None, alloc=None):
    primary = {"score": score, "scoreError": error, "scoreUnit": "ops/ms"}
    if percentiles:
        primary["scorePercentiles"] = percentiles
    secondary = {compare.ALLOC: {"score": alloc, "scoreError": 0.0}} if alloc is not None else {}
    data = {"benchmark": compare.PREFIX + benchmark, "mode": mode, "threads": threads, "primaryMetric": primary,
            "secondaryMetrics": secondary}
    return {compare.key(data): data}


class CompareTest(unittest.TestCase):
    def failed(self, base, cand, smoke=False):
        return compare.compare(base, cand, smoke)[1]

    def test_throughput_within_the_baseline_error_passes(self):
        self.assertEqual(0, self.failed(row("P.a", score=100), row("P.a", score=90)))
        self.assertEqual(1, self.failed(row("P.a", score=100), row("P.a", score=89)))

    def test_throughput_within_five_percent_passes_when_the_error_is_smaller(self):
        # D34: max(error, 5%); the error of 1 alone would fail at 98.
        self.assertEqual(0, self.failed(row("P.a", score=100, error=1), row("P.a", score=95)))
        self.assertEqual(1, self.failed(row("P.a", score=100, error=1), row("P.a", score=94.99)))
        self.assertEqual(0, self.failed(row("P.a", score=100, error=0), row("P.a", score=95)))

    def test_smoke_throughput_allows_ten_percent(self):
        self.assertEqual(0, self.failed(row("P.a", score=100, error=0), row("P.a", score=90), smoke=True))
        self.assertEqual(1, self.failed(row("P.a", score=100, error=0), row("P.a", score=89), smoke=True))

    def test_latency_percentiles(self):
        # The error (0.1) is below 10% of each percentile: the limits are 2.2 and 5.5.
        base = row("P.a", mode="sample", score=1.0, error=0.1, percentiles={"99.0": 2.0, "99.9": 5.0})
        self.assertEqual(0, self.failed(base, row("P.a", mode="sample", percentiles={"99.0": 2.2, "99.9": 5.5})))
        self.assertEqual(1, self.failed(base, row("P.a", mode="sample", percentiles={"99.0": 2.21, "99.9": 5.0})))
        self.assertEqual(1, self.failed(base, row("P.a", mode="sample", percentiles={"99.0": 2.0, "99.9": 5.51})))

    def test_latency_error_above_ten_percent_rules(self):
        base = row("P.a", mode="sample", score=1.0, error=1.0, percentiles={"99.0": 2.0, "99.9": 5.0})
        self.assertEqual(0, self.failed(base, row("P.a", mode="sample", percentiles={"99.0": 3.0, "99.9": 6.0})))
        self.assertEqual(1, self.failed(base, row("P.a", mode="sample", percentiles={"99.0": 3.01, "99.9": 6.0})))

    def test_average_time_allows_five_percent_or_the_error(self):
        self.assertEqual(0, self.failed(row("P.a", mode="avgt", score=100.0, error=1.0), row("P.a", mode="avgt", score=105.0)))
        self.assertEqual(1, self.failed(row("P.a", mode="avgt", score=100.0, error=1.0), row("P.a", mode="avgt", score=105.01)))
        self.assertEqual(0, self.failed(row("P.a", mode="avgt", score=100.0, error=8.0), row("P.a", mode="avgt", score=108.0)))

    def test_single_shot_allows_five_percent(self):
        base = row("P.a", mode="ss", score=1000.0, error=0.0)
        self.assertEqual(0, self.failed(base, row("P.a", mode="ss", score=1050.0)))
        self.assertEqual(1, self.failed(base, row("P.a", mode="ss", score=1051.0)))

    def test_spring_start_is_a_startup(self):
        name = "SpringStartBenchmark.startToFirstId_t1"
        base = row(name, mode="avgt", score=1000.0, error=100.0)
        self.assertEqual(0, self.failed(base, row(name, mode="avgt", score=1050.0)))
        self.assertEqual(1, self.failed(base, row(name, mode="avgt", score=1051.0)))
        # Any other average time keeps the error rule.
        self.assertEqual(0, self.failed(row("P.a", mode="avgt", score=1000.0, error=100.0), row("P.a", mode="avgt", score=1100.0)))

    def test_allocation_grows_by_at_most_64_bytes_or_five_percent(self):
        # D34: max(+5%, +64 B): +64 B for small objects ...
        self.assertEqual(0, self.failed(row("P.a", alloc=48.0), row("P.a", alloc=112.0)))
        self.assertEqual(1, self.failed(row("P.a", alloc=48.0), row("P.a", alloc=112.01)))
        # ... 5% above 1280 B.
        self.assertEqual(0, self.failed(row("P.a", alloc=2000.0), row("P.a", alloc=2100.0)))
        self.assertEqual(1, self.failed(row("P.a", alloc=2000.0), row("P.a", alloc=2100.01)))
        self.assertEqual(1, self.failed(row("P.a", alloc=1280.0), row("P.a", alloc=1344.01)))

    def test_allocation_up_to_the_floor_passes(self):
        self.assertEqual(0, self.failed(row("P.a", alloc=0.0), row("P.a", alloc=compare.ALLOC_FLOOR_BYTES)))
        self.assertEqual(0, self.failed(row("P.a", alloc=0.001), row("P.a", alloc=0.004)))

    def test_long_hot_paths_allocate_nothing(self):
        name = "SnowflakeMillisBenchmark.generate_t1"
        self.assertEqual(0, self.failed(row(name, alloc=0.0), row(name, alloc=0.001)))
        self.assertEqual(1, self.failed(row(name, alloc=8.0), row(name, alloc=8.0)))

    # A side that produced no row is a broken harness, not a regression: neither judged nor re-run.
    def test_a_missing_candidate_row_is_a_harness_error_not_a_failed_row(self):
        harness = []
        lines, failed, keys = compare.compare(row("P.a"), row("P.b"), harness=harness)
        self.assertEqual((0, []), (failed, keys))
        self.assertEqual([("P.a", "thrpt", 1, "")], harness)
        self.assertIn("**HARNESS ERROR**: 0 of 1 baseline rows failed, 1 missing, 0 JMH failures in the logs.", lines)

    def test_a_jmh_failure_in_a_log_is_a_harness_error_with_its_exception(self):
        with tempfile.TemporaryDirectory() as logs:
            with open(os.path.join(logs, "v5-RedisChainIdBenchmark.log"), "w") as file:
                file.write("# Benchmark: me.ahoo.cosid.benchmark.RedisChainIdBenchmark.generate\n\n<failure>\n\n"
                           "me.ahoo.cosid.CosIdException: Invalid RuntimeSpec of namespace [bench]:\n"
                           "  - store [store]: no StoreProvider of type [redis]\n\tat V5.storeRuntime(V5.java:126)\n")
            with open(os.path.join(logs, "v5-RedisChainIdBenchmark.json"), "w") as file:
                file.write("[]")
            found = compare.failures(logs)
            self.assertEqual(1, len(found))
            self.assertEqual("v5-RedisChainIdBenchmark.log", found[0][0])
            self.assertEqual("  - store [store]: no StoreProvider of type [redis]", found[0][1][1])
            base = row("RedisChainIdBenchmark.generate")
            lines = compare.compare(base, base, logs=found)[0]
            self.assertIn("## HARNESS ERROR: not a result", lines)
            self.assertIn("me.ahoo.cosid.CosIdException: Invalid RuntimeSpec of namespace [bench]:", lines)
            with open(os.path.join(logs, "base.json"), "w") as file:
                json.dump(list(base.values()), file)
            with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(4, compare.main([os.path.join(logs, "base.json"), os.path.join(logs, "base.json"), "--logs", logs]))
                self.assertEqual(0, compare.main([os.path.join(logs, "base.json"), os.path.join(logs, "base.json")]))

    def test_a_failed_row_passes_only_if_its_rerun_passes(self):
        base, slow = row("P.a", score=100, error=0), row("P.a", score=80)
        other = row("P.b", score=100, error=0)
        lines, failed, keys = compare.compare({**base, **other}, {**slow, **other}, rerun=(base, row("P.a", score=100)))
        self.assertEqual(0, failed)
        self.assertIn("## Re-run of the failed rows", lines)
        self.assertEqual(1, compare.compare(base, slow, rerun=(base, slow))[1])
        # A row the re-run did not produce is a harness error.
        harness = []
        self.assertEqual(0, compare.compare(base, slow, rerun=({}, {}), harness=harness)[1])
        self.assertEqual([("P.a", "thrpt", 1, "")], harness)

    def test_failed_rows_are_listed_for_a_rerun(self):
        base = row("P.a", score=100, error=0)
        self.assertEqual([("P.a", "thrpt", 1, "")], compare.compare(base, row("P.a", score=80))[2])
        self.assertEqual([], compare.compare(base, base)[2])

    # D44: end-to-end rows fail past D34; component rows (P9, P10) only WARN there, up to their own limits.
    def test_an_end_to_end_row_just_past_d34_fails(self):
        name = "SegmentNoPrefetchBenchmark.generate_t1"
        self.assertEqual(0, self.failed(row(name, score=100, error=0), row(name, score=95)))
        self.assertEqual(1, self.failed(row(name, score=100, error=0), row(name, score=94.99)))
        self.assertEqual(compare.END_TO_END, compare.kind(name))

    def test_a_component_row_just_past_d34_warns_and_does_not_fail(self):
        name = "MySqlStoreBenchmark.reserve_t1"
        warned = []
        lines, failed, keys = compare.compare(row(name, mode="avgt", score=100.0, error=0.0),
                                              row(name, mode="avgt", score=105.01), warned=warned)
        self.assertEqual(0, failed)
        self.assertEqual([(name, "avgt", 1, "")], warned)
        self.assertIn("**PASS (1 WARN)**: 0 of 1 baseline rows failed.", lines)
        self.assertIn("## WARN: component rows past D34, within the D44 limits", lines)

    def test_a_component_row_just_past_its_soft_limit_fails(self):
        name = "MongoStoreBenchmark.reserve_t1"
        base = row(name, mode="avgt", score=100.0, error=0.0)
        self.assertEqual(0, self.failed(base, row(name, mode="avgt", score=120.0)))
        self.assertEqual(1, self.failed(base, row(name, mode="avgt", score=120.01)))

    def test_component_throughput_percentiles_and_allocation_have_soft_limits(self):
        codec = "CodecBenchmark.radix62Encode_t1"
        self.assertEqual(compare.COMPONENT, compare.kind(codec))
        self.assertEqual(0, self.failed(row(codec, score=100, error=0), row(codec, score=80)))
        self.assertEqual(1, self.failed(row(codec, score=100, error=0), row(codec, score=79.99)))
        store = "ZookeeperStoreBenchmark.reserve_t1"
        base = row(store, mode="sample", score=1.0, error=0.0, percentiles={"99.0": 10.0, "99.9": 20.0})
        self.assertEqual(0, self.failed(base, row(store, mode="sample", percentiles={"99.0": 13.0, "99.9": 26.0})))
        self.assertEqual(1, self.failed(base, row(store, mode="sample", percentiles={"99.0": 13.01, "99.9": 20.0})))
        # Allocation: max(+25%, +64 B).
        self.assertEqual(0, self.failed(row(store, mode="avgt", error=0.0, alloc=1000.0), row(store, mode="avgt", alloc=1250.0)))
        self.assertEqual(1, self.failed(row(store, mode="avgt", error=0.0, alloc=1000.0), row(store, mode="avgt", alloc=1250.01)))
        self.assertEqual(0, self.failed(row(store, mode="avgt", error=0.0, alloc=100.0), row(store, mode="avgt", alloc=164.0)))
        self.assertEqual(1, self.failed(row(store, mode="avgt", error=0.0, alloc=100.0), row(store, mode="avgt", alloc=164.01)))

    def test_a_component_row_that_failed_and_warns_on_its_rerun_passes_with_a_warning(self):
        name = "RedisStoreBenchmark.renew_t1"
        base = row(name, mode="avgt", score=100.0, error=0.0)
        warned = []
        lines, failed, keys = compare.compare(base, row(name, mode="avgt", score=130.0),
                                              rerun=(base, row(name, mode="avgt", score=110.0)), warned=warned)
        self.assertEqual(0, failed)
        self.assertEqual([(name, "avgt", 1, "")], warned)
        self.assertIn("**PASS (1 WARN)**: 0 of 1 baseline rows failed (1 failed the first run and were re-run).", lines)

    def test_smoke_rules_have_no_warn(self):
        name = "MySqlStoreBenchmark.reserve_t1"
        self.assertEqual(1, self.failed(row(name, mode="avgt", score=100.0, error=0.0), row(name, mode="avgt", score=112.0), smoke=True))

    def test_a_class_not_in_the_table_is_judged_end_to_end(self):
        self.assertEqual(compare.END_TO_END, compare.kind("SomethingNewBenchmark.run_t1"))

    def test_warned_rows_are_written_for_the_gate(self):
        import json
        import tempfile
        name = "MySqlStoreBenchmark.reserve_t1"
        with tempfile.TemporaryDirectory() as directory:
            paths = [os.path.join(directory, f) for f in ("4x.json", "5x.json", "warned.json", "failed.json")]
            for path, data in zip(paths, (row(name, mode="avgt", score=100.0, error=0.0), row(name, mode="avgt", score=110.0))):
                with open(path, "w", encoding="utf-8") as file:
                    json.dump(list(data.values()), file)
            self.assertEqual(0, compare.main([paths[0], paths[1], "--warned", paths[2], "--failed", paths[3]]))
            with open(paths[2], encoding="utf-8") as file:
                self.assertEqual([{"benchmark": compare.PREFIX + name, "mode": "avgt", "threads": 1, "params": {}}], json.load(file))
            with open(paths[3], encoding="utf-8") as file:
                self.assertEqual([], json.load(file))

    def test_every_scenario_class_is_in_the_table(self):
        root = os.path.dirname(os.path.abspath(__file__))
        for side in ("baseline-4x", "v5"):
            directory = os.path.join(root, side, "src", "jmh", "java", "me", "ahoo", "cosid", "benchmark")
            for name in os.listdir(directory):
                if name.endswith("Benchmark.java"):
                    self.assertIn(name[:-len(".java")], compare.SCENARIOS, f"{side}/{name}")

    def test_a_reference_row_is_reported_as_a_bound_and_never_judged(self):
        base = {**row("SegmentBenchmark.generate_t16", threads=16, score=25.0)}
        cand = {**row("SegmentBenchmark.generate_t16", threads=16, score=42.0),
                **row("AtomicLongBenchmark.generate_t16", threads=16, score=50.0)}
        harness = []
        lines, failed, keys = compare.compare(base, cand, harness=harness)
        self.assertEqual((0, [], []), (failed, keys, harness))
        self.assertIn("## Reference (not judged)", lines)
        self.assertIn("| AtomicLongBenchmark.generate_t16 | 16 | 50.000 | -: 25.000 (50%) | -: 42.000 (84%) |", lines)
        self.assertFalse(any(line.startswith("Only in 5.x") for line in lines))

    def test_rows_match_by_threads(self):
        harness = []
        compare.compare(row("P.a", threads=4), row("P.a", threads=1), harness=harness)
        self.assertEqual([("P.a", "thrpt", 4, "")], harness)


if __name__ == "__main__":
    unittest.main()
