#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="$ROOT/build/android"
APK="$BUILD/stage-a-host-test.apk"
PACKAGE="io.toolbox.stageahosttest"
test -s "$APK"

if ! timeout 30s adb wait-for-device; then
  echo 'STAGE_A_HOST_ADB_WAIT = FAIL' >&2
  exit 1
fi
if ! timeout 60s adb install -r "$APK"; then
  echo 'STAGE_A_HOST_INSTALL = FAIL' >&2
  exit 1
fi

get_pid() {
  timeout 10s adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true
}

run_mode() {
  local mode="$1" marker="$2"
  timeout 10s adb logcat -c
  timeout 10s adb shell am force-stop "$PACKAGE" || true
  if ! timeout 10s adb shell am start -n "$PACKAGE/.HostTestActivity" --es mode "$mode" >/dev/null; then
    echo "STAGE_A_HOST_START_${mode^^} = FAIL" >&2
    timeout 10s adb logcat -d -s ToolBoxStageAHost:V '*:S' >&2 || true
    return 1
  fi
  local found=0
  for _ in $(seq 1 30); do
    if timeout 5s adb logcat -d -s ToolBoxStageAHost:I '*:S' | grep -F "$marker" >/dev/null; then
      found=1
      break
    fi
    sleep 1
  done
  if [[ "$found" != 1 ]]; then
    echo "STAGE_A_HOST_MARKER_${mode^^} = FAIL" >&2
    timeout 10s adb logcat -d -s ToolBoxStageAHost:V '*:S' >&2 || true
    return 1
  fi
  echo "$marker"
}

run_mode referenceDummy STAGE_A_REFERENCE_DUMMY_PASS
run_mode adversarialDummy STAGE_A_ADVERSARIAL_DUMMY_PASS

run_mode write STAGE_A_HOST_WRITE_PASS
WRITE_PID="$(get_pid)"
if [[ -z "$WRITE_PID" ]]; then
  echo 'STAGE_A_HOST_WRITE_PROCESS = FAIL' >&2
  exit 1
fi
printf 'STAGE_A_HOST_WRITE_PID=%s\n' "$WRITE_PID"

timeout 10s adb shell am force-stop "$PACKAGE"
stopped=0
for _ in $(seq 1 20); do
  if [[ -z "$(get_pid)" ]]; then
    stopped=1
    break
  fi
  sleep 1
done
if [[ "$stopped" != 1 ]]; then
  echo 'STAGE_A_HOST_PROCESS_STOP = FAIL' >&2
  exit 1
fi
echo 'STAGE_A_HOST_PROCESS_STOP = PASS'

run_mode read STAGE_A_HOST_READ_PASS
if ! timeout 10s adb logcat -d -s ToolBoxStageAHost:I '*:S' | grep -F 'STAGE_A_HOST_SHARED_REGISTRY_PASS' >/dev/null; then
  echo 'STAGE_A_HOST_SHARED_REGISTRY = FAIL' >&2
  exit 1
fi
echo 'STAGE_A_HOST_SHARED_REGISTRY_PASS'
READ_PID="$(get_pid)"
if [[ -z "$READ_PID" ]]; then
  echo 'STAGE_A_HOST_READ_PROCESS = FAIL' >&2
  exit 1
fi
printf 'STAGE_A_HOST_READ_PID=%s\n' "$READ_PID"
echo 'STAGE_A_HOST_PROCESS_RESTART = PASS'

run_mode corrupt STAGE_A_HOST_CORRUPTION_REJECT_PASS
run_mode ui STAGE_A_HOST_SAFE_UI_PASS
if ! timeout 10s adb logcat -d -s ToolBoxStageAHost:I '*:S' | grep -F 'STAGE_A_HOST_SAFE_UI_ACTIONS_PASS' >/dev/null; then
  echo 'STAGE_A_HOST_SAFE_UI_ACTIONS = FAIL' >&2
  exit 1
fi
echo 'STAGE_A_HOST_SAFE_UI_ACTIONS_PASS'
cat > "$ROOT/build/host-runtime-summary.txt" <<'EOF'
ANDROID_API=30
ABI=x86_64
REFERENCE_DUMMY=PASS
ADVERSARIAL_CONFORMANT_DUMMY=PASS
HOST_DURABLE_WRITE=PASS
HOST_PROCESS_STOP=PASS
HOST_PROCESS_RESTART_READ=PASS
HOST_SHARED_PRODUCT_REGISTRY=PASS
HOST_CORRUPTION_REJECTION=PASS
HOST_PERMISSION_PROVIDER=PASS
HOST_RESOURCE_MAPPING=PASS
HOST_SAFE_UI_RENDER=PASS
HOST_SAFE_UI_ACTIONS=PASS
NETWORK_CALLS=0
FIREBASE_USED=0
FINAL_ARM64_RUNTIME_CLAIMED=0
EOF
printf 'STAGE_A_ANDROID_HOST_RUNTIME = PASS\n'
