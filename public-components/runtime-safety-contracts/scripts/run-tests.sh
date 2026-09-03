#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="$ROOT/build"
MAIN_CLASSES="$BUILD/classes"
TEST_CLASSES="$BUILD/test-classes"
JAR="$BUILD/package/toolbox-runtime-safety-contracts-0.1.0.jar"
REPRO_JAR="$BUILD/package/toolbox-runtime-safety-contracts-0.1.0-repro.jar"

rm -rf "$BUILD"
mkdir -p "$MAIN_CLASSES" "$TEST_CLASSES" "$BUILD/package"

find "$ROOT/src/main/java" -type f -name '*.java' | LC_ALL=C sort > "$BUILD/main-sources.txt"
find "$ROOT/src/test/java" -type f -name '*.java' | LC_ALL=C sort > "$BUILD/test-sources.txt"

test -s "$BUILD/main-sources.txt"
test -s "$BUILD/test-sources.txt"

javac --release 11 -Xlint:all -Werror -d "$MAIN_CLASSES" @"$BUILD/main-sources.txt"
javac --release 11 -Xlint:all -Werror -cp "$MAIN_CLASSES" -d "$TEST_CLASSES" @"$BUILD/test-sources.txt"

java -ea -cp "$MAIN_CLASSES:$TEST_CLASSES" io.toolbox.contracts.safety.RuntimeSafetySelfTest | tee "$BUILD/test-output.txt"
java -ea -cp "$MAIN_CLASSES:$TEST_CLASSES" io.toolbox.contracts.safety.RuntimeSafetyBoundaryTest | tee "$BUILD/boundary-output.txt"
java -ea -cp "$MAIN_CLASSES:$TEST_CLASSES" io.toolbox.contracts.safety.RuntimeSafetyPropertyTest | tee "$BUILD/property-output.txt"
java -ea -cp "$MAIN_CLASSES" io.toolbox.contracts.safety.RuntimeSafetySimulator | tee "$BUILD/simulator-output.txt"

grep -Fx 'PUBLIC_RUNTIME_SAFETY_TESTS = PASS' "$BUILD/test-output.txt"
grep -Fx 'SELF_TEST_CASES=16' "$BUILD/test-output.txt"
grep -Fx 'PUBLIC_RUNTIME_SAFETY_BOUNDARY_TESTS = PASS' "$BUILD/boundary-output.txt"
grep -Fx 'BOUNDARY_TEST_CASES=14' "$BUILD/boundary-output.txt"
grep -Fx 'PUBLIC_RUNTIME_SAFETY_PROPERTY_TESTS = PASS' "$BUILD/property-output.txt"
grep -Fx 'RECOVERY_TRANSITION_CASES=35' "$BUILD/property-output.txt"
grep -Fx 'RESOURCE_DIFFERENTIAL_CASES=5000' "$BUILD/property-output.txt"
grep -Fx 'PUBLIC_RUNTIME_SAFETY_SIMULATOR = PASS' "$BUILD/simulator-output.txt"
grep -Fx 'PERSISTENT_WRITES=0' "$BUILD/simulator-output.txt"
grep -Fx 'NETWORK_CALLS=0' "$BUILD/simulator-output.txt"
grep -Fx 'PLUGIN_LOADS=0' "$BUILD/simulator-output.txt"
grep -Fx 'UI_DEVICE_CALLS=0' "$BUILD/simulator-output.txt"
grep -Fx 'FIREBASE_USED=0' "$BUILD/simulator-output.txt"

jar --create --file "$JAR" --date=1980-01-01T00:00:02Z -C "$MAIN_CLASSES" .
test -s "$JAR"
jar --create --file "$REPRO_JAR" --date=1980-01-01T00:00:02Z -C "$MAIN_CLASSES" .
cmp --silent "$JAR" "$REPRO_JAR"
rm -f "$REPRO_JAR"
printf 'REPRODUCIBLE_JAR = PASS\n'

cat > "$BUILD/test-summary.txt" <<'EOF'
COMPONENT_ID=public.runtime-safety-contracts
COMPONENT_VERSION=0.1.0
CONTRACT_ID=toolbox.runtime.safety
CONTRACT_VERSION=1.0.0
JAVA_RELEASE=11
UNIT_TEST=PASS
FAILURE_TEST=PASS
CONCURRENCY_TEST=PASS
BOUNDARY_TEST=PASS
RECOVERY_EXHAUSTIVE_TEST=PASS
RESOURCE_DIFFERENTIAL_TEST=PASS
METAMORPHIC_TEST=PASS
SIMULATOR=PASS
REPRODUCIBLE_JAR=PASS
PERSISTENT_WRITES=0
NETWORK_CALLS=0
PLUGIN_LOADS=0
UI_DEVICE_CALLS=0
FIREBASE_USED=0
SELF_TEST_CASES=16
BOUNDARY_TEST_CASES=14
RECOVERY_TRANSITION_CASES=35
RESOURCE_DIFFERENTIAL_CASES=5000
EOF

printf 'PUBLIC_RUNTIME_SAFETY_BUILD_TEST = PASS\n'
