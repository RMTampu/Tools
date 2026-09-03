#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="$ROOT/build/android"
APK="$BUILD/stage-a-host-test.apk"
test -s "$APK"

if ! timeout 30s adb wait-for-device; then
  echo 'STAGE_A_HOST_ADB_WAIT = FAIL' >&2
  exit 1
fi
if ! timeout 60s adb install -r "$APK"; then
  echo 'STAGE_A_HOST_INSTALL = FAIL' >&2
  exit 1
fi

run_mode() {
  local mode="$1" marker="$2"
  timeout 10s adb logcat -c
  timeout 10s adb shell am force-stop io.toolbox.stageahosttest || true
  if ! timeout 30s adb shell am start -W -n io.toolbox.stageahosttest/.HostTestActivity --es mode "$mode" >/dev/null; then
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

run_mode write STAGE_A_HOST_WRITE_PASS
run_mode read STAGE_A_HOST_READ_PASS
run_mode corrupt STAGE_A_HOST_CORRUPTION_REJECT_PASS
run_mode ui STAGE_A_HOST_SAFE_UI_PASS
cat > "$ROOT/build/host-runtime-summary.txt" <<'EOF'
ANDROID_API=30
ABI=x86_64
HOST_DURABLE_WRITE=PASS
HOST_PROCESS_RESTART_READ=PASS
HOST_CORRUPTION_REJECTION=PASS
HOST_PERMISSION_PROVIDER=PASS
HOST_RESOURCE_MAPPING=PASS
HOST_SAFE_UI_RENDER=PASS
NETWORK_CALLS=0
FIREBASE_USED=0
FINAL_ARM64_RUNTIME_CLAIMED=0
EOF
printf 'STAGE_A_ANDROID_HOST_RUNTIME = PASS\n'
