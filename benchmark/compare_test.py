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

import importlib.machinery
import importlib.util
import os
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

    def test_a_missing_candidate_row_fails(self):
        self.assertEqual(1, self.failed(row("P.a"), row("P.b")))

    def test_a_failed_row_passes_only_if_its_rerun_passes(self):
        base, slow = row("P.a", score=100, error=0), row("P.a", score=80)
        other = row("P.b", score=100, error=0)
        lines, failed, keys = compare.compare({**base, **other}, {**slow, **other}, rerun=(base, row("P.a", score=100)))
        self.assertEqual(0, failed)
        self.assertIn("## Re-run of the failed rows", lines)
        self.assertEqual(1, compare.compare(base, slow, rerun=(base, slow))[1])
        # A row the re-run did not produce fails.
        self.assertEqual(1, compare.compare(base, slow, rerun=({}, {}))[1])

    def test_failed_rows_are_listed_for_a_rerun(self):
        base = row("P.a", score=100, error=0)
        self.assertEqual([("P.a", "thrpt", 1, "")], compare.compare(base, row("P.a", score=80))[2])
        self.assertEqual([], compare.compare(base, base)[2])

    def test_rows_match_by_threads(self):
        self.assertEqual(1, self.failed(row("P.a", threads=4), row("P.a", threads=1)))


if __name__ == "__main__":
    unittest.main()
