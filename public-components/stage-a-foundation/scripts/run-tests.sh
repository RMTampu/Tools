#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
BUILD="$ROOT/build"
MAIN_CLASSES="$BUILD/classes"
TEST_CLASSES="$BUILD/test-classes"
PACKAGE_DIR="$BUILD/package"
JAR="$PACKAGE_DIR/toolbox-stage-a-foundation-0.1.0.jar"
REPRO_JAR="$PACKAGE_DIR/toolbox-stage-a-foundation-0.1.0-repro.jar"

mkdir -p "$BUILD"
rm -rf "$MAIN_CLASSES" "$TEST_CLASSES" "$PACKAGE_DIR"
rm -f "$BUILD/dependency-main-sources.txt" "$BUILD/stage-main-sources.txt" "$BUILD/test-sources.txt" "$BUILD/main-sources.txt"
rm -f "$BUILD/self-test-output.txt" "$BUILD/property-test-output.txt" "$BUILD/simulator-output.txt" "$BUILD/test-summary.txt"
mkdir -p "$MAIN_CLASSES" "$TEST_CLASSES" "$PACKAGE_DIR"
cat > "$BUILD/dependency-main-sources.txt" <<EOF
$REPO/public-components/runtime-contracts/src/main/java/io/toolbox/contracts/runtime/Contracts.java
$REPO/public-components/runtime-contracts/src/main/java/io/toolbox/contracts/runtime/ProductRegistry.java
$REPO/public-components/runtime-safety-contracts/src/main/java/io/toolbox/contracts/safety/SafetyContracts.java
$REPO/public-components/runtime-safety-contracts/src/main/java/io/toolbox/contracts/safety/DiagnosticBuffer.java
$REPO/public-components/runtime-safety-contracts/src/main/java/io/toolbox/contracts/safety/ResourceGuard.java
$REPO/public-components/runtime-safety-contracts/src/main/java/io/toolbox/contracts/safety/RecoveryMachine.java
EOF
find "$ROOT/src/main/java" -type f -name '*.java' | LC_ALL=C sort > "$BUILD/stage-main-sources.txt"
find "$ROOT/src/test/java" -type f -name '*.java' | LC_ALL=C sort > "$BUILD/test-sources.txt"
cat "$BUILD/dependency-main-sources.txt" "$BUILD/stage-main-sources.txt" > "$BUILD/main-sources.txt"
test -s "$BUILD/main-sources.txt"; test -s "$BUILD/test-sources.txt"
javac --release 11 -Xlint:all -Werror -d "$MAIN_CLASSES" @"$BUILD/main-sources.txt"
javac --release 11 -Xlint:all -Werror -cp "$MAIN_CLASSES" -d "$TEST_CLASSES" @"$BUILD/test-sources.txt"
java -ea -cp "$MAIN_CLASSES:$TEST_CLASSES" io.toolbox.stagea.StageAFoundationSelfTest | tee "$BUILD/self-test-output.txt"
java -ea -cp "$MAIN_CLASSES:$TEST_CLASSES" io.toolbox.stagea.StageAFoundationPropertyTest | tee "$BUILD/property-test-output.txt"
java -ea -cp "$MAIN_CLASSES:$TEST_CLASSES" io.toolbox.stagea.StageAIntegrationSimulator | tee "$BUILD/simulator-output.txt"
grep -Fx 'PUBLIC_STAGE_A_FOUNDATION_TESTS = PASS' "$BUILD/self-test-output.txt"
grep -Fx 'SELF_TEST_CASES=23' "$BUILD/self-test-output.txt"
grep -Fx 'PUBLIC_STAGE_A_FOUNDATION_PROPERTY_TESTS = PASS' "$BUILD/property-test-output.txt"
grep -Fx 'AVAILABILITY_CROSS_PRODUCT_CASES=9' "$BUILD/property-test-output.txt"
grep -Fx 'RESOURCE_BOUNDARY_CASES=111' "$BUILD/property-test-output.txt"
grep -Fx 'CONCURRENT_ADMISSION_CASES=8000' "$BUILD/property-test-output.txt"
grep -Fx 'DIAGNOSTIC_RETENTION_CASES=10' "$BUILD/property-test-output.txt"
grep -Fx 'STAGE_A_INTEGRATION_SIMULATOR = PASS' "$BUILD/simulator-output.txt"
grep -Fx 'REGISTRY_ROUTE=PASS' "$BUILD/simulator-output.txt"
grep -Fx 'EXECUTION_GUARD_ROUTE=PASS' "$BUILD/simulator-output.txt"
grep -Fx 'RECOVERY_ROUTE=PASS' "$BUILD/simulator-output.txt"
grep -Fx 'SAFE_UI_CONTRACT_ROUTE=PASS' "$BUILD/simulator-output.txt"
grep -Fx 'HEALTH_DIAGNOSTIC_ROUTE=PASS' "$BUILD/simulator-output.txt"
grep -Fx 'PRIVATE_CONTENT_USED=0' "$BUILD/simulator-output.txt"
grep -Fx 'ANDROID_RUNTIME_CALLS=0' "$BUILD/simulator-output.txt"
grep -Fx 'NETWORK_CALLS=0' "$BUILD/simulator-output.txt"
grep -Fx 'PLUGIN_LOADS=0' "$BUILD/simulator-output.txt"
grep -Fx 'FIREBASE_USED=0' "$BUILD/simulator-output.txt"
jar --create --file "$JAR" --date=1980-01-01T00:00:02Z -C "$MAIN_CLASSES" io/toolbox/stagea
test -s "$JAR"
jar --create --file "$REPRO_JAR" --date=1980-01-01T00:00:02Z -C "$MAIN_CLASSES" io/toolbox/stagea
cmp --silent "$JAR" "$REPRO_JAR"; rm -f "$REPRO_JAR"
cat > "$BUILD/test-summary.txt" <<'EOF'
PROJECT_ID=ToolBox
STAGE_ID=A
COMPONENT_ID=public.stage-a-foundation
COMPONENT_VERSION=0.1.0
CONTRACT_ID=toolbox.stage.a.foundation
CONTRACT_VERSION=1.0.0
JAVA_RELEASE=11
DEPENDENCY_RUNTIME_CONTRACTS=PASS
DEPENDENCY_RUNTIME_SAFETY_CONTRACTS=PASS
SELF_TEST=PASS
PROPERTY_TEST=PASS
REGISTRY_INTEGRATION_SIMULATOR=PASS
RECOVERY_ROUTE=PASS
SAFE_UI_CONTRACT_ROUTE=PASS
HEALTH_DIAGNOSTIC_ROUTE=PASS
REPRODUCIBLE_JAR=PASS
SELF_TEST_CASES=23
AVAILABILITY_CROSS_PRODUCT_CASES=9
RESOURCE_BOUNDARY_CASES=111
CONCURRENT_ADMISSION_CASES=8000
DIAGNOSTIC_RETENTION_CASES=10
PRIVATE_CONTENT_USED=0
ANDROID_RUNTIME_CALLS=0
NETWORK_CALLS=0
PLUGIN_LOADS=0
FIREBASE_USED=0
EOF
printf 'PUBLIC_STAGE_A_FOUNDATION_BUILD_TEST = PASS\n'
