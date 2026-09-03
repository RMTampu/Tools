#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
: "${ANDROID_SDK_ROOT:?ANDROID_SDK_ROOT required}"
BT="$ANDROID_SDK_ROOT/build-tools/34.0.0"
ANDROID_JAR="$ANDROID_SDK_ROOT/platforms/android-30/android.jar"
BUILD="$ROOT/build/android"
rm -rf "$BUILD"
mkdir -p "$BUILD/classes" "$BUILD/dex"
test -s "$ANDROID_JAR"
test -x "$BT/d8"
test -x "$BT/aapt2"
test -x "$BT/apksigner"
test -x "$BT/zipalign"
{
  printf '%s\n' \
    "$REPO/public-components/runtime-contracts/src/main/java/io/toolbox/contracts/runtime/Contracts.java" \
    "$REPO/public-components/runtime-contracts/src/main/java/io/toolbox/contracts/runtime/ProductRegistry.java" \
    "$REPO/public-components/runtime-safety-contracts/src/main/java/io/toolbox/contracts/safety/SafetyContracts.java" \
    "$REPO/public-components/runtime-safety-contracts/src/main/java/io/toolbox/contracts/safety/DiagnosticBuffer.java" \
    "$REPO/public-components/runtime-safety-contracts/src/main/java/io/toolbox/contracts/safety/ResourceGuard.java" \
    "$REPO/public-components/runtime-safety-contracts/src/main/java/io/toolbox/contracts/safety/RecoveryMachine.java"
  find "$REPO/public-components/stage-a-foundation/src/main/java" -type f -name '*.java' | LC_ALL=C sort
  find "$ROOT/src/main/java" -type f -name '*.java' | LC_ALL=C sort
  find "$ROOT/harness" -type f -name '*.java' | LC_ALL=C sort
} > "$BUILD/sources.txt"
javac --release 11 -Xlint:all -Werror -cp "$ANDROID_JAR" -d "$BUILD/classes" @"$BUILD/sources.txt"
mapfile -d '' CLASS_FILES < <(find "$BUILD/classes" -type f -name '*.class' -print0)
"$BT/d8" --min-api 30 --output "$BUILD/dex" "${CLASS_FILES[@]}"
test -s "$BUILD/dex/classes.dex"
"$BT/aapt2" link \
  -I "$ANDROID_JAR" \
  --manifest "$ROOT/AndroidManifest.xml" \
  --min-sdk-version 30 \
  --target-sdk-version 30 \
  --version-code 1 \
  --version-name 0.1.0-stage-a-host-test \
  -o "$BUILD/test-unsigned.apk"
(cd "$BUILD/dex" && zip -q -u "$BUILD/test-unsigned.apk" classes.dex)
keytool -genkeypair -noprompt \
  -keystore "$BUILD/test.jks" -storepass android -keypass android -alias androiddebugkey \
  -dname 'CN=ToolBox Public Stage A Host Test,O=ToolBox,C=ID' \
  -keyalg RSA -keysize 2048 -validity 3650
"$BT/zipalign" -f 4 "$BUILD/test-unsigned.apk" "$BUILD/test-aligned.apk"
"$BT/apksigner" sign --ks "$BUILD/test.jks" --ks-pass pass:android --key-pass pass:android \
  --out "$BUILD/stage-a-host-test.apk" "$BUILD/test-aligned.apk"
"$BT/apksigner" verify --verbose "$BUILD/stage-a-host-test.apk"
"$BT/aapt2" dump badging "$BUILD/stage-a-host-test.apk" | tee "$BUILD/badging.txt"
grep -F "package: name='io.toolbox.stageahosttest'" "$BUILD/badging.txt"
grep -F "sdkVersion:'30'" "$BUILD/badging.txt"
grep -F "targetSdkVersion:'30'" "$BUILD/badging.txt"
printf 'STAGE_A_ANDROID_HOST_TEST_APK_BUILD = PASS\n'
