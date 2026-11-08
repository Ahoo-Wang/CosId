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
"""Tests of scripts/test-stability.sh with a stand-in gradlew: python3 -I scripts/test_stability_test.py"""

import os
import shutil
import subprocess
import tempfile
import unittest

SCRIPT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "test-stability.sh")
PASSED = '<testsuite name="audit.CompletedTest" tests="1"><testcase name="completed" classname="audit.CompletedTest"/></testsuite>'
FAILED = ('<testsuite name="audit.CompletedTest" tests="1"><testcase name="completed" classname="audit.CompletedTest">'
          '<failure message="boom"/></testcase></testsuite>')


def stability(report, status):
    """Runs test-stability.sh twice over a gradlew that writes report and exits with status: (exit status, summary)."""
    with tempfile.TemporaryDirectory() as root:
        os.makedirs(os.path.join(root, "scripts"))
        shutil.copy(SCRIPT, os.path.join(root, "scripts", "test-stability.sh"))
        gradlew = os.path.join(root, "gradlew")
        with open(gradlew, "w") as file:
            file.write("#!/usr/bin/env bash\n"
                       "mkdir -p store/remote/build/test-results/test\n"
                       f"cat > store/remote/build/test-results/test/TEST-Probe.xml <<'XML'\n{report}\nXML\n"
                       f"exit {status}\n")
        os.chmod(gradlew, 0o755)
        result = subprocess.run(["bash", "scripts/test-stability.sh", ":store:remote", "2", "build/stability"], cwd=root,
                                capture_output=True, text=True, timeout=60)
        with open(os.path.join(root, "build", "stability", "summary.md")) as file:
            return result.returncode, file.read()


class TestStabilityTest(unittest.TestCase):
    def test_passing_runs_pass(self):
        status, summary = stability(PASSED, 0)
        self.assertEqual(0, status, summary)
        self.assertIn("**PASS**: 0 of 1 classes failed at least once.", summary)

    def test_a_failing_test_fails(self):
        status, summary = stability(FAILED, 1)
        self.assertEqual(1, status, summary)
        self.assertIn("**FAIL**: 1 of 1 classes failed at least once.", summary)

    # #1443: a build that exited with an error after writing passing reports is no passing run.
    def test_a_failed_build_with_passing_reports_is_incomplete(self):
        status, summary = stability(PASSED, 1)
        self.assertEqual(2, status, summary)
        self.assertIn("**INCOMPLETE**", summary)
        self.assertIn("Runs whose Gradle build failed without a failing test: 1 (status 1), 2 (status 1).", summary)


if __name__ == "__main__":
    unittest.main()
