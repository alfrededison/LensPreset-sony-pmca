#!/usr/bin/env bash
# Unit tests: core/ (engine, optics, profile parsing, every bundled profile combination, the
# language tables) and the camera adapter's Android-free classes (ADAPTER_PURE below). Needs a JDK
# (17+) and nothing else — no Android SDK.
#
#   ./tools/test.sh            # everything
#   ./tools/test.sh Engine     # only test classes whose name contains "Engine"
#
# JUnit's console launcher is fetched from Maven Central into out/test/ on first use and checked
# against the SHA-256 below; set JUNIT_JAR=<path> to use a local copy when offline.
set -euo pipefail
cd "$(dirname "$0")/.."

JUNIT_VERSION=1.11.4
JUNIT_SHA256=b016ef6b1c3454d6d7c2c88ce081dabf289699686af6622d6e4e2e1b54b4a2fc
OUT=out/test
mkdir -p "$OUT"

if [ -z "${JUNIT_JAR:-}" ]; then
  JUNIT_JAR="$OUT/junit-platform-console-standalone-$JUNIT_VERSION.jar"
  if [ ! -f "$JUNIT_JAR" ]; then
    curl -sSfL -o "$JUNIT_JAR.part" \
      "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/$JUNIT_VERSION/junit-platform-console-standalone-$JUNIT_VERSION.jar"
    mv "$JUNIT_JAR.part" "$JUNIT_JAR"
  fi
fi
sha256() { if command -v sha256sum >/dev/null; then sha256sum "$1"; else shasum -a 256 "$1"; fi; }
actual=$(sha256 "$JUNIT_JAR" | cut -d' ' -f1)
if [ "$actual" != "$JUNIT_SHA256" ]; then
  echo "checksum mismatch for $JUNIT_JAR: $actual" >&2
  exit 1
fi

# Adapter classes with no Android or Sony imports; logic that needs no camera belongs in one of
# these, with a test. Compiling them without android.jar keeps them that way.
ADAPTER=camera-adapter/src/main/java/com/lenspreset/adapter
ADAPTER_PURE=("$ADAPTER/SonyValues.java" "$ADAPTER/ApplyReport.java" "$ADAPTER/ProbeReport.java"
  "$ADAPTER/PlaybackText.java" "$ADAPTER/PlaybackGeometry.java")

rm -rf "$OUT/main" "$OUT/classes"
mkdir -p "$OUT/main" "$OUT/classes"
# This code runs on Android 2.3.7: compile it as Java 8 without android.jar. build.sh compiles it
# again against the API 10 platform jar, which catches library calls Android 2.3 lacks.
{ find core/src/main/java -name '*.java'; printf '%s\n' "${ADAPTER_PURE[@]}"; } > "$OUT/main.txt"
javac -encoding UTF-8 --release 8 -Xlint:-options -d "$OUT/main" "@$OUT/main.txt"
find core/src/test/java camera-adapter/src/test/java -name '*.java' > "$OUT/tests.txt"
javac -encoding UTF-8 --release 17 -cp "$OUT/main:$JUNIT_JAR" -d "$OUT/classes" "@$OUT/tests.txt"

select_args=(--scan-classpath "$OUT/classes")
if [ $# -gt 0 ]; then
  select_args+=(--include-classname ".*$1.*")
fi
java -Drepo.root="$PWD" -jar "$JUNIT_JAR" execute \
  --class-path "$OUT/main:$OUT/classes" "${select_args[@]}" \
  --disable-banner --details=summary --details-theme=ascii \
  --reports-dir "$OUT/reports" --fail-if-no-tests
