#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="$ROOT/build"
MAIN_CLASSES="$BUILD/classes"
TEST_CLASSES="$BUILD/test-classes"

rm -rf "$BUILD"
mkdir -p "$MAIN_CLASSES" "$TEST_CLASSES"

find "$ROOT/src/main/java" -type f -name '*.java' | LC_ALL=C sort > "$BUILD/main-sources.txt"
find "$ROOT/src/test/java" -type f -name '*.java' | LC_ALL=C sort > "$BUILD/test-sources.txt"

test -s "$BUILD/main-sources.txt"
test -s "$BUILD/test-sources.txt"

javac --release 11 -Xlint:all -d "$MAIN_CLASSES" @"$BUILD/main-sources.txt"
javac --release 11 -Xlint:all -cp "$MAIN_CLASSES" -d "$TEST_CLASSES" @"$BUILD/test-sources.txt"

java -ea -cp "$MAIN_CLASSES:$TEST_CLASSES" io.toolbox.contracts.runtime.RuntimeContractsSelfTest | tee "$BUILD/test-output.txt"
java -ea -cp "$MAIN_CLASSES" io.toolbox.contracts.runtime.RuntimeContractsSimulator | tee "$BUILD/simulator-output.txt"

grep -Fx 'PUBLIC_RUNTIME_CONTRACT_TESTS = PASS' "$BUILD/test-output.txt"
grep -Fx 'PUBLIC_RUNTIME_CONTRACT_SIMULATOR = PASS' "$BUILD/simulator-output.txt"
grep -Fx 'ENGINE_CALLBACKS_EXECUTED=0' "$BUILD/simulator-output.txt"

mkdir -p "$BUILD/package"
jar --create --file "$BUILD/package/toolbox-runtime-contracts-0.1.0.jar" -C "$MAIN_CLASSES" .
test -s "$BUILD/package/toolbox-runtime-contracts-0.1.0.jar"

cat > "$BUILD/test-summary.txt" <<'EOF'
COMPONENT_ID=public.runtime-contracts
COMPONENT_VERSION=0.1.0
CONTRACT_ID=toolbox.runtime.metadata
CONTRACT_VERSION=1.0.0
JAVA_RELEASE=11
UNIT_TEST=PASS
FAILURE_TEST=PASS
CONCURRENCY_TEST=PASS
SIMULATOR=PASS
ENGINE_CALLBACKS_EXECUTED=0
EOF

printf 'PUBLIC_COMPONENT_BUILD_TEST = PASS\n'
