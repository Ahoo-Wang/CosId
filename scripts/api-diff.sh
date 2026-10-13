#!/usr/bin/env bash
# API diff of the library modules against a baseline release (default: v4.0.0).
# Usage: scripts/api-diff.sh [baseline-ref] [output-dir]
# Writes one japicmp HTML and Markdown report per module, and a summary (also to $GITHUB_STEP_SUMMARY in CI).
# Fails on a binary-incompatible change, unless API_DIFF_ENFORCE=false (the api-break-approved PR label in CI). Source
# incompatibilities (such as a method added to an interface) are reported only. Not compared: me.ahoo.cosid.internal,
# the constructors of the starter's auto-configurations, and the cosid-test, cosid-mod-test and cosid-proxy-server modules.
# cosid-core, cosid-sharding and cosid-accessor are also compared together, so that packages that moved between them
# (sharding and accessor left cosid-core in 4.0) are not reported as removed.
set -euo pipefail

BASELINE="${1:-v4.0.0}"
OUT="$(mkdir -p "${2:-build/api-diff}" && cd "${2:-build/api-diff}" && pwd)"
JAPICMP_VERSION="0.23.1"
ROOT="$(git rev-parse --show-toplevel)"
WORK="$(mktemp -d)"
trap 'git -C "$ROOT" worktree remove --force "$WORK/baseline" >/dev/null 2>&1 || true; rm -rf "$WORK"' EXIT

curl -sSfL -o "$WORK/japicmp.jar" \
  "https://repo1.maven.org/maven2/com/github/siom79/japicmp/japicmp/${JAPICMP_VERSION}/japicmp-${JAPICMP_VERSION}-jar-with-dependencies.jar"

# japicmp of two jars into $OUT/<name>.html|.md, printing the summary row's result.
# japicmp exits with 1 both when it finds an incompatible change and when it fails, for example on a jar it cannot read:
# only its incompatibility message is a result, any other failure is recorded in $FAILED and fails the script.
INCOMPATIBLE_MESSAGE="There is at least one incompatibility"
FAILED="$WORK/failed"
BROKEN="$WORK/broken"
ENFORCE="${API_DIFF_ENFORCE:-true}"
# Not part of the public API: the internal package, and auto-configuration constructors, which only Spring calls.
EXCLUDES="me.ahoo.cosid.internal;me.ahoo.cosid.internal.*;me.ahoo.cosid.spring.boot.starter.*AutoConfiguration#*AutoConfiguration(*)"
EXCLUDED_MODULES='/cosid-test$|/cosid-mod-test$|/cosid-proxy-server$'
# Classes moved between these three in 4.0: each is reported alone, but only their combined comparison is enforced.
COMBINED_MODULES='/cosid-core$|/cosid-sharding$|/cosid-accessor$'

# japicmp exit code of a binary-only comparison: 0 compatible, 1 a binary-incompatible change (or a failure, which the
# full comparison records).
binary_compatible() {
  local old_jar="$1" new_jar="$2" name="$3"
  shift 3
  java -jar "$WORK/japicmp.jar" --old "$old_jar" --new "$new_jar" "$@" --only-modified --ignore-missing-classes \
    --exclude "$EXCLUDES" --error-on-binary-incompatibility --markdown > "$OUT/$name.binary.md" 2> "$OUT/$name.binary.err"
}
compare() {
  local old_jar="$1" new_jar="$2" name="$3" old_cp="${4:-}" new_cp="${5:-}" enforced="${6:-true}"
  local classpath=()
  # With the dependencies on the classpath, japicmp also checks superclasses and interfaces from other jars, which
  # --ignore-missing-classes would otherwise skip.
  if [ -n "$old_cp" ] && [ -n "$new_cp" ]; then
    classpath=(--old-classpath "$old_cp" --new-classpath "$new_cp")
  fi
  if java -jar "$WORK/japicmp.jar" --old "$old_jar" --new "$new_jar" "${classpath[@]+"${classpath[@]}"}" --only-modified --ignore-missing-classes \
    --exclude "$EXCLUDES" --error-on-binary-incompatibility --error-on-source-incompatibility \
    --html-file "$OUT/$name.html" --markdown > "$OUT/$name.md" 2> "$OUT/$name.err"; then
    echo "compatible"
  elif grep -q "$INCOMPATIBLE_MESSAGE" "$OUT/$name.err"; then
    if binary_compatible "$old_jar" "$new_jar" "$name" "${classpath[@]+"${classpath[@]}"}"; then
      echo "source-incompatible only (allowed), see \`$name.html\`"
    elif [ "$enforced" != "true" ]; then
      echo "binary-incompatible alone (checked combined below), see \`$name.html\`"
    else
      echo "$name" >> "$BROKEN"
      echo "**binary-incompatible**, see \`$name.html\`"
    fi
  else
    echo "$name: $(grep -v '^Picked up JAVA_TOOL_OPTIONS' "$OUT/$name.err" | head -c 500)" >> "$FAILED"
    echo "**error**, see \`$name.err\`"
  fi
}

# Self-check: a jar japicmp cannot read must count as an error, not as an incompatible change.
: > "$WORK/unreadable.jar"
if [ "$(compare "$WORK/unreadable.jar" "$WORK/unreadable.jar" self-check)" != "**error**, see \`self-check.err\`" ]; then
  echo "api-diff self-check failed: an unreadable jar was not reported as an error" >&2
  exit 1
fi
rm -f "$FAILED" "$BROKEN" "$OUT"/self-check.*

git -C "$ROOT" fetch --quiet --tags origin || true
git -C "$ROOT" worktree add --quiet --detach "$WORK/baseline" "$BASELINE"

# Writes the compile classpath of every Java module to <dir>/<module path with / as __>.cp.
cat > "$WORK/classpath.init.gradle" <<'GRADLE'
allprojects {
    plugins.withId('java') {
        def outDir = new File(System.getProperty('apiDiffClasspathDir'))
        def compileClasspath = configurations.named('compileClasspath')
        def key = rootProject.relativePath(projectDir).replace('/', '__')
        tasks.register('apiDiffClasspath') {
            doLast {
                outDir.mkdirs()
                new File(outDir, key + '.cp').text = compileClasspath.get().files.join(File.pathSeparator)
            }
        }
    }
}
GRADLE

build_jars() {
  (cd "$1" && ./gradlew jar apiDiffClasspath --quiet -x test -I "$WORK/classpath.init.gradle" -DapiDiffClasspathDir="$2")
}
build_jars "$WORK/baseline" "$WORK/classpath-old"
build_jars "$ROOT" "$WORK/classpath-new"

# The compile classpath of a module, empty when it was not written.
classpath_of() {
  local file="$1/${2//\//__}.cp"
  if [ -f "$file" ]; then
    cat "$file"
  else
    echo "::warning::No compile classpath for $2 in $1: comparing it without its dependencies." >&2
  fi
}

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

# One jar with the classes of the core modules that exist at a version.
combined_core_jar() {
  local root="$1" version="$2" target="$3" module jar
  rm -rf "$target.d" && mkdir -p "$target.d"
  for module in cosid-core cosid-sharding cosid-accessor; do
    jar="$(main_jar "$root/$module" "$version")"
    if [ -n "$jar" ]; then
      (cd "$target.d" && unzip -qo "$jar" -x 'META-INF/MANIFEST.MF')
    fi
  done
  (cd "$target.d" && zip -qr "$target" .)
}

SUMMARY="$OUT/summary.md"
{
  echo "## API diff of ${NEW_VERSION} against ${BASELINE} (${OLD_VERSION})"
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
  if [ -z "$new_jar" ] || [[ "$module_dir" =~ $EXCLUDED_MODULES ]]; then
    continue
  fi
  if [ -z "$old_jar" ]; then
    echo "| \`$module\` | new module |" >> "$SUMMARY"
    continue
  fi
  enforced=true
  if [[ "$module_dir" =~ $COMBINED_MODULES ]]; then
    enforced=false
  fi
  echo "| \`$module\` | $(compare "$old_jar" "$new_jar" "$name" "$(classpath_of "$WORK/classpath-old" "$module")" "$(classpath_of "$WORK/classpath-new" "$module")" "$enforced") |" >> "$SUMMARY"
done < <(find "$ROOT" -path "$ROOT/build" -prune -o -path '*/build/libs' -type d -print | grep -v '/examples/\|/cosid-benchmark/\|/code-coverage-report/' | sort)

combined_core_jar "$WORK/baseline" "$OLD_VERSION" "$WORK/core-old.jar"
combined_core_jar "$ROOT" "$NEW_VERSION" "$WORK/core-new.jar"
# The dependencies of the three modules together.
combined_classpath() {
  local dir="$1" module joined=""
  for module in cosid-core cosid-sharding cosid-accessor; do
    joined="$joined:$(classpath_of "$dir" "$module")"
  done
  echo "${joined#:}"
}
echo "| \`cosid-core\` + \`cosid-sharding\` + \`cosid-accessor\` (combined) | $(compare "$WORK/core-old.jar" "$WORK/core-new.jar" cosid-core-combined "$(combined_classpath "$WORK/classpath-old")" "$(combined_classpath "$WORK/classpath-new")") |" >> "$SUMMARY"

cat "$SUMMARY"
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  cat "$SUMMARY" >> "$GITHUB_STEP_SUMMARY"
fi
if [ -s "$FAILED" ]; then
  echo "japicmp failed:" >&2
  cat "$FAILED" >&2
  exit 1
fi
if [ -s "$BROKEN" ]; then
  echo "Binary-incompatible changes in: $(sort -u "$BROKEN" | tr '\n' ' ')" >&2
  if [ "$ENFORCE" = "true" ]; then
    echo "::error::Binary-incompatible API changes. 4.x keeps the API binary compatible: deprecate instead of removing, or label the PR api-break-approved." >&2
    exit 1
  fi
  echo "::warning::Binary-incompatible API changes, allowed by the api-break-approved label." >&2
fi
