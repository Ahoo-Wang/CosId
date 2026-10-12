#!/usr/bin/env bash
# Runs ./gradlew with the given arguments, and once more when the first run failed only because a dependency could not
# be resolved (a transient miss of a remote repository). Any other failure, such as a failing test, is returned as is.
set -uo pipefail

LOG="$(mktemp)"
trap 'rm -f "$LOG"' EXIT

./gradlew "$@" 2>&1 | tee "$LOG"
status=${PIPESTATUS[0]}
# A build reports each failure in a "* What went wrong:" block, and several when tasks run in parallel: retry only when
# every one of them is a dependency resolution failure.
failures=$(grep -c "^\* What went wrong:" "$LOG")
resolution_failures=$(grep -cE "^> Could not resolve all (files|dependencies|artifacts) for configuration" "$LOG")
if [ "$status" -ne 0 ] && [ "$failures" -gt 0 ] && [ "$failures" -eq "$resolution_failures" ]; then
  echo "::warning::Dependency resolution failed, retrying once: $(grep -m1 'Could not find\|Could not resolve' "$LOG")"
  sleep 15
  ./gradlew "$@" --refresh-dependencies
  status=$?
fi
exit "$status"
