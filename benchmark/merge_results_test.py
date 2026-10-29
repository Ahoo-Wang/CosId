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
"""Tests of benchmark/merge-results: python3 -I benchmark/merge_results_test.py"""

import importlib.machinery
import importlib.util
import json
import math
import os
import tempfile
import unittest

_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "merge-results")
_loader = importlib.machinery.SourceFileLoader("merge_results", _path)
_spec = importlib.util.spec_from_loader("merge_results", _loader)
merge_results = importlib.util.module_from_spec(_spec)
_loader.exec_module(merge_results)


def metric(forks):
    return {"score": 0.0, "scoreError": 0.0, "scoreUnit": "ops/us", "rawData": forks}


def row(forks, mode="thrpt", benchmark="me.ahoo.cosid.benchmark.MySqlStoreBenchmark.reserve_t1", alloc=None):
    data = {"benchmark": benchmark, "mode": mode, "threads": 1, "forks": len(forks), "params": {}}
    if mode == "sample":
        data["primaryMetric"] = {"score": 0.0, "scoreError": 0.0, "scoreUnit": "us/op", "rawDataHistogram": forks}
    else:
        data["primaryMetric"] = metric(forks)
    data["secondaryMetrics"] = {"gc.alloc.rate.norm": metric(alloc)} if alloc else {}
    return data


class MergeResultsTest(unittest.TestCase):
    def test_t_quantiles_are_those_of_the_tables(self):
        for df, expected in ((1, 636.619), (4, 8.610), (9, 4.781), (14, 4.140), (29, 3.659), (1000, 3.300)):
            self.assertAlmostEqual(expected, merge_results.t_quantile(0.9995, df), places=3)

    def test_two_runs_of_a_row_are_one_row_of_their_pooled_forks(self):
        first, second = row([[10.0, 12.0, 11.0]], alloc=[[8.0, 8.0, 8.0]]), row([[20.0, 22.0, 21.0], [30.0, 31.0, 29.0]], alloc=[[9.0, 9.0, 9.0], [10.0, 10.0, 10.0]])
        merged = merge_results.merge([first, second])
        self.assertEqual(1, len(merged))
        values = [10.0, 12.0, 11.0, 20.0, 22.0, 21.0, 30.0, 31.0, 29.0]
        mean = sum(values) / 9
        sd = math.sqrt(sum((v - mean) ** 2 for v in values) / 8)
        primary = merged[0]["primaryMetric"]
        self.assertEqual(3, merged[0]["forks"])
        self.assertAlmostEqual(mean, primary["score"])
        self.assertAlmostEqual(merge_results.t_quantile(0.9995, 8) * sd / 3, primary["scoreError"])
        self.assertEqual([[10.0, 12.0, 11.0], [20.0, 22.0, 21.0], [30.0, 31.0, 29.0]], primary["rawData"])
        self.assertAlmostEqual(9.0, merged[0]["secondaryMetrics"]["gc.alloc.rate.norm"]["score"])

    def test_sample_rows_merge_their_histograms_and_percentiles(self):
        first = row([[[[1.0, 98], [100.0, 1]]]], mode="sample")
        second = row([[[[1.0, 99], [200.0, 1]]]], mode="sample")
        primary = merge_results.merge([first, second])[0]["primaryMetric"]
        self.assertAlmostEqual((197 * 1.0 + 100.0 + 200.0) / 199, primary["score"])
        self.assertEqual(1.0, primary["scorePercentiles"]["50.0"])
        self.assertEqual(200.0, primary["scorePercentiles"]["100.0"])
        # JMH's estimate: position 0.999 x (199 + 1) = 199.8, past the last of the 199 values: the max.
        self.assertEqual(200.0, primary["scorePercentiles"]["99.9"])
        self.assertEqual(2, len(primary["rawDataHistogram"]))

    def test_a_row_that_comes_once_is_written_as_it_is(self):
        once = row([[1.0, 2.0]])
        other = row([[5.0]], benchmark="me.ahoo.cosid.benchmark.SegmentBenchmark.generate_t1")
        self.assertEqual([once, other], merge_results.merge([once, other]))

    def test_rows_differing_in_mode_threads_or_params_stay_apart(self):
        a, b = row([[1.0]]), row([[2.0]])
        b["params"] = {"step": "10"}
        self.assertEqual(2, len(merge_results.merge([a, b])))

    def test_main_writes_the_merged_result_and_refuses_bad_input(self):
        with tempfile.TemporaryDirectory() as directory:
            paths = []
            for n, data in enumerate(([row([[1.0, 3.0]])], [row([[5.0, 7.0]])])):
                paths.append(os.path.join(directory, f"in{n}.json"))
                with open(paths[-1], "w") as file:
                    json.dump(data, file)
            out = os.path.join(directory, "out.json")
            self.assertEqual(0, merge_results.main([out] + paths))
            with open(out) as file:
                self.assertAlmostEqual(4.0, json.load(file)[0]["primaryMetric"]["score"])
            with open(paths[0], "w") as file:
                json.dump({"not": "a list"}, file)
            self.assertEqual(2, merge_results.main([out] + paths))


if __name__ == "__main__":
    unittest.main()
