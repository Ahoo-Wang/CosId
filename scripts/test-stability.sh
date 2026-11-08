#!/usr/bin/env bash
# Runs the tests of a project N times, each run in a fresh test JVM with no retries, keeps each run's JUnit reports and
# writes a per-class table (runs, failed runs, the failing cases) to OUT/summary.md (L4: integration test stability).
#
#   scripts/test-stability.sh PROJECT ITERATIONS OUT [GRADLE ARGUMENT...]
#
# Exits 1 when any run of any class failed, 2 when the runs are incomplete: a run whose Gradle build exited with an error
# though none of its reports holds a failing test (a test JVM that died, a build that broke after some reports were
# written), a class without a report in every run, or no report at all. A run's own reports never outweigh its status.
set -uo pipefail

project="$1" iterations="$2" out="$3"
shift 3
dir="${project#:}"
dir="${dir//://}/build/test-results/test"
rm -rf "$out"
mkdir -p "$out"

statuses=()
for i in $(seq 1 "$iterations"); do
  echo "::group::$project run $i of $iterations"
  rm -rf "$dir"
  status=0
  ./gradlew "$project:test" --rerun --continue -Pcosid.test.retries=0 "$@" || status=$?
  statuses+=("$status")
  echo "::endgroup::"
  mkdir -p "$out/$i"
  cp "$dir"/TEST-*.xml "$out/$i/" 2>/dev/null || echo "::warning::$project run $i left no test reports"
  ((status == 0)) || echo "::warning::$project run $i: Gradle exited with status $status"
done

python3 -I - "$out" "$iterations" "$project" "${statuses[@]}" <<'PY'
import collections, glob, os, sys, xml.etree.ElementTree as ET

out, iterations, project = sys.argv[1], int(sys.argv[2]), sys.argv[3]
statuses = [int(status) for status in sys.argv[4:]]
failing_runs = set()
runs = collections.Counter()
failed = collections.Counter()
cases = collections.defaultdict(collections.Counter)
skipped = collections.Counter()
for report in glob.glob(os.path.join(out, "*", "TEST-*.xml")):
    suite = ET.parse(report).getroot()
    name = suite.get("name").rsplit(".", 1)[-1]
    runs[name] += 1
    bad = False
    for case in suite.iter("testcase"):
        if case.find("failure") is not None or case.find("error") is not None:
            cases[name][case.get("name")] += 1
            bad = True
            failing_runs.add(os.path.basename(os.path.dirname(report)))
        if case.find("skipped") is not None:
            skipped[name] += 1
    failed[name] += bad
lines = [f"## {project}: {iterations} runs", "", "| Class | Runs | Failed runs | Skipped cases | Failing cases |", "|---|---|---|---|---|"]
for name in sorted(runs):
    detail = ", ".join(f"`{case}` ×{count}" for case, count in sorted(cases[name].items()))
    lines.append(f"| {name} | {runs[name]} | {failed[name]} | {skipped[name]} | {detail} |")
incomplete = [name for name in runs if runs[name] != iterations]
# A build that failed with no failing test in its reports broke (a test JVM that died, a task after the tests): its
# partial reports are not a passing run (#1443).
broken = [(run, status) for run, status in enumerate(statuses, 1) if status != 0 and str(run) not in failing_runs]
verdict = "FAIL" if sum(failed.values()) else "INCOMPLETE" if broken or incomplete or not runs else "PASS"
lines += ["", f"**{verdict}**: {sum(1 for n in failed if failed[n])} of {len(runs)} classes failed at least once."]
if broken:
    lines.append("Runs whose Gradle build failed without a failing test: "
                 + ", ".join(f"{run} (status {status})" for run, status in broken) + ".")
if incomplete:
    lines.append(f"Classes without a report in every run: {', '.join(sorted(incomplete))}.")
if not runs:
    lines.append("No run left a test report.")
with open(os.path.join(out, "summary.md"), "w") as file:
    file.write("\n".join(lines) + "\n")
print("\n".join(lines))
sys.exit(1 if sum(failed.values()) else 2 if broken or incomplete or not runs else 0)
PY
