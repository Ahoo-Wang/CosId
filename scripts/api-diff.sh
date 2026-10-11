#!/usr/bin/env bash
# Report-only API diff of the library modules against a baseline release (default: the latest 3.4 tag).
# Usage: scripts/api-diff.sh [baseline-ref] [output-dir]
# Writes one japicmp HTML and Markdown report per module, and a summary (also to $GITHUB_STEP_SUMMARY in CI).
set -euo pipefail

BASELINE="${1:-v3.4.0}"
OUT="$(mkdir -p "${2:-build/api-diff}" && cd "${2:-build/api-diff}" && pwd)"
JAPICMP_VERSION="0.23.1"
ROOT="$(git rev-parse --show-toplevel)"
WORK="$(mktemp -d)"
trap 'git -C "$ROOT" worktree remove --force "$WORK/baseline" >/dev/null 2>&1 || true; rm -rf "$WORK"' EXIT

curl -sSfL -o "$WORK/japicmp.jar" \
  "https://repo1.maven.org/maven2/com/github/siom79/japicmp/japicmp/${JAPICMP_VERSION}/japicmp-${JAPICMP_VERSION}-jar-with-dependencies.jar"

git -C "$ROOT" fetch --quiet --tags origin || true
git -C "$ROOT" worktree add --quiet --detach "$WORK/baseline" "$BASELINE"

build_jars() {
  (cd "$1" && ./gradlew jar --quiet -x test)
}
build_jars "$WORK/baseline"
build_jars "$ROOT"

version_of() {
  sed -n 's/^version=//p' "$1/gradle.properties"
}
OLD_VERSION="$(version_of "$WORK/baseline")"
NEW_VERSION="$(version_of "$ROOT")"

# The main jar of a module at a version: <module>-<version>.jar, no classifier.
main_jar() {
  local jar="$1/build/libs/$(basename "$1")-$2.jar"
  if [ -f "$jar" ]; then
    echo "$jar"
  fi
}

SUMMARY="$OUT/summary.md"
{
  echo "## API diff of ${NEW_VERSION} against ${BASELINE} (${OLD_VERSION}, report only)"
  echo
  echo "| Module | Result |"
  echo "|---|---|"
} > "$SUMMARY"

while IFS= read -r libs; do
  module_dir="${libs%/build/libs}"
  module="${module_dir#"$ROOT"/}"
  new_jar="$(main_jar "$module_dir" "$NEW_VERSION")"
  old_jar="$(main_jar "$WORK/baseline/$module" "$OLD_VERSION")"
  name="$(basename "$module")"
  if [ -z "$new_jar" ]; then
    continue
  fi
  if [ -z "$old_jar" ]; then
    echo "| \`$module\` | new module |" >> "$SUMMARY"
    continue
  fi
  # japicmp exits with 1 when it finds a binary or source incompatible change.
  if java -jar "$WORK/japicmp.jar" --old "$old_jar" --new "$new_jar" --only-modified --ignore-missing-classes \
    --error-on-binary-incompatibility --error-on-source-incompatibility \
    --html-file "$OUT/$name.html" --markdown > "$OUT/$name.md" 2> "$OUT/$name.err"; then
    result="compatible"
  else
    result="**incompatible changes**, see \`$name.html\`"
  fi
  echo "| \`$module\` | $result |" >> "$SUMMARY"
done < <(find "$ROOT" -path "$ROOT/build" -prune -o -path '*/build/libs' -type d -print | grep -v '/examples/\|/cosid-benchmark/\|/code-coverage-report/' | sort)

cat "$SUMMARY"
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  cat "$SUMMARY" >> "$GITHUB_STEP_SUMMARY"
fi
