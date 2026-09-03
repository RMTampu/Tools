#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
BUILD="$ROOT/build/jvm"
rm -rf "$BUILD"
mkdir -p "$BUILD/classes"
cat > "$BUILD/sources.txt" <<EOF
$REPO/public-components/runtime-contracts/src/main/java/io/toolbox/contracts/runtime/Contracts.java
$REPO/public-components/runtime-safety-contracts/src/main/java/io/toolbox/contracts/safety/SafetyContracts.java
$ROOT/src/main/java/io/toolbox/stagea/android/StateFileCodec.java
$ROOT/src/main/java/io/toolbox/stagea/android/NormalizedResourceMath.java
$REPO/public-components/stage-a-foundation/src/main/java/io/toolbox/stagea/StageAContracts.java
$ROOT/src/test/java/io/toolbox/stagea/android/HostPureJvmSelfTest.java
EOF
javac --release 11 -Xlint:all -Werror -d "$BUILD/classes" @"$BUILD/sources.txt"
java -ea -cp "$BUILD/classes" io.toolbox.stagea.android.HostPureJvmSelfTest | tee "$BUILD/output.txt"
grep -Fx 'STAGE_A_ANDROID_HOST_JVM_TESTS = PASS' "$BUILD/output.txt"
grep -Fx 'HOST_JVM_TEST_CASES=5' "$BUILD/output.txt"
printf 'STAGE_A_ANDROID_HOST_JVM_BUILD = PASS\n'
