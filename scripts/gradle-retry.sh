#!/usr/bin/env bash
# Runs ./gradlew with the given arguments, and once more when the first run failed only because a dependency could not
# be resolved (a transient miss of a remote repository). Any other failure, such as a failing test, is returned as is.
set -uo pipefail

LOG="$(mktemp)"
trap 'rm -f "$LOG"' EXIT

./gradlew "$@" 2>&1 | tee "$LOG"
status=${PIPESTATUS[0]}
if [ "$status" -ne 0 ] && grep -q "Could not resolve all \(files\|dependencies\) for configuration" "$LOG" \
  && ! grep -q "There were failing tests" "$LOG"; then
  echo "::warning::Dependency resolution failed, retrying once: $(grep -m1 'Could not find\|Could not resolve' "$LOG")"
  sleep 15
  ./gradlew "$@" --refresh-dependencies
  status=$?
fi
exit "$status"
