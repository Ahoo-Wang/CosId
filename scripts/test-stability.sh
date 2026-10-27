#!/usr/bin/env bash
# Runs the tests of a project N times, each run in a fresh test JVM with no retries, keeps each run's JUnit reports and
# writes a per-class table (runs, failed runs, the failing cases) to OUT/summary.md (L4: integration test stability).
#
#   scripts/test-stability.sh PROJECT ITERATIONS OUT [GRADLE ARGUMENT...]
#
# Exits 1 when any run of any class failed, 2 when a run left no reports (a build failure).
set -uo pipefail

project="$1" iterations="$2" out="$3"
shift 3
dir="${project#:}"
dir="${dir//://}/build/test-results/test"
rm -rf "$out"
mkdir -p "$out"

for i in $(seq 1 "$iterations"); do
  echo "::group::$project run $i of $iterations"
  rm -rf "$dir"
  ./gradlew "$project:test" --rerun --continue -Pcosid.test.retries=0 "$@"
  echo "::endgroup::"
  mkdir -p "$out/$i"
  cp "$dir"/TEST-*.xml "$out/$i/" 2>/dev/null || echo "::warning::$project run $i left no test reports"
done

python3 -I - "$out" "$iterations" "$project" <<'PY'
import collections, glob, os, sys, xml.etree.ElementTree as ET

out, iterations, project = sys.argv[1], int(sys.argv[2]), sys.argv[3]
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
        if case.find("skipped") is not None:
            skipped[name] += 1
    failed[name] += bad
lines = [f"## {project}: {iterations} runs", "", "| Class | Runs | Failed runs | Skipped cases | Failing cases |", "|---|---|---|---|---|"]
for name in sorted(runs):
    detail = ", ".join(f"`{case}` ×{count}" for case, count in sorted(cases[name].items()))
    lines.append(f"| {name} | {runs[name]} | {failed[name]} | {skipped[name]} | {detail} |")
incomplete = [name for name in runs if runs[name] != iterations]
lines += ["", f"**{'FAIL' if sum(failed.values()) else 'PASS'}**: {sum(1 for n in failed if failed[n])} of {len(runs)} classes failed at least once."]
if incomplete:
    lines.append(f"Classes without a report in every run: {', '.join(sorted(incomplete))}.")
with open(os.path.join(out, "summary.md"), "w") as file:
    file.write("\n".join(lines) + "\n")
print("\n".join(lines))
sys.exit(1 if sum(failed.values()) else 2 if incomplete or not runs else 0)
PY
