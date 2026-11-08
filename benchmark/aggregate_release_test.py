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
"""Tests of benchmark/aggregate-release: python3 -I benchmark/aggregate_release_test.py"""

import contextlib
import importlib.machinery
import importlib.util
import io
import json
import os
import tempfile
import unittest

_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "aggregate-release")
_loader = importlib.machinery.SourceFileLoader("aggregate_release", _path)
_spec = importlib.util.spec_from_loader("aggregate_release", _loader)
aggregate = importlib.util.module_from_spec(_spec)
_loader.exec_module(aggregate)


def group(root, name, verdict=True):
    """A run-release --out directory of the group name that passed; without its verdict when verdict is False."""
    directory = os.path.join(root, name)
    os.makedirs(directory)
    files = {"machine.json": {"cpu": "cpu", "cores": "4", "jdk": "jdk", "commit": "abc"},
             "baseline-4x.json": [], "v5.json": []}
    if verdict:
        files["verdict.json"] = {"scenarios": ["P1"], "rows": 1, "failedFirstRun": [], "failed": [], "warned": [], "status": 0}
    for file_name, content in files.items():
        with open(os.path.join(directory, file_name), "w") as file:
            json.dump(content, file)
    with open(os.path.join(directory, "report.md"), "w") as file:
        file.write("# CosId 5.0 release gate\n\n**PASS**: 0 of 1 baseline rows failed.\n")
    return directory


def run(*argv):
    """(exit status, report.md) of aggregate-release argv; argv[0] is the output directory."""
    with contextlib.redirect_stderr(io.StringIO()):
        status = aggregate.main(list(argv))
    with open(os.path.join(argv[argv.index("--expect") + 2 if "--expect" in argv else 0], "report.md")) as file:
        return status, file.read()


class AggregateReleaseTest(unittest.TestCase):
    def test_every_expected_group_with_a_result_passes(self):
        with tempfile.TemporaryDirectory() as root:
            groups = [group(root, "mongo"), group(root, "snowflake")]
            status, report = run("--expect", "snowflake mongo", os.path.join(root, "out"), *groups)
            self.assertEqual(0, status, report)
            self.assertIn("**PASS**: 0 of 2 groups failed.", report)

    # #1465: a group whose job uploaded nothing is no result, not a group left out of the count.
    def test_an_expected_group_without_its_directory_is_no_result(self):
        with tempfile.TemporaryDirectory() as root:
            groups = [group(root, "snowflake")]
            status, report = run("--expect", "snowflake mongo", os.path.join(root, "out"), *groups)
            self.assertEqual(1, status, report)
            self.assertIn("| mongo | - | - | - | - | - | **no result** (no artifact: the job failed before it uploaded its "
                          "results) |", report)
            self.assertIn("**FAIL**: 1 of 2 groups failed.", report)

    def test_no_group_at_all_fails_every_expected_one(self):
        with tempfile.TemporaryDirectory() as root:
            status, report = run("--expect", "snowflake,mongo", os.path.join(root, "out"))
            self.assertEqual(1, status, report)
            self.assertIn("**FAIL**: 2 of 2 groups failed.", report)

    def test_a_group_without_a_verdict_is_no_result(self):
        with tempfile.TemporaryDirectory() as root:
            groups = [group(root, "snowflake"), group(root, "mongo", verdict=False)]
            status, report = run("--expect", "snowflake mongo", os.path.join(root, "out"), *groups)
            self.assertEqual(1, status, report)
            self.assertIn("| mongo | - | - | - | - | - | **no result** (the job failed before its report) |", report)

    def test_without_expect_the_report_is_no_verdict_on_the_whole_gate(self):
        with tempfile.TemporaryDirectory() as root:
            status, report = run(os.path.join(root, "out"), group(root, "snowflake"))
            self.assertEqual(0, status, report)
            self.assertIn("**PASS**: 0 of 1 groups failed (only the groups given, without --expect: no verdict on the "
                          "whole gate).", report)


if __name__ == "__main__":
    unittest.main()
