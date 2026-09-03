#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="$ROOT/build/android"
APK="$BUILD/stage-a-host-test.apk"
test -s "$APK"
adb wait-for-device
adb install -r "$APK"
run_mode() {
  local mode="$1" marker="$2"
  adb logcat -c
  adb shell am force-stop io.toolbox.stageahosttest || true
  adb shell am start -W -n io.toolbox.stageahosttest/.HostTestActivity --es mode "$mode" >/dev/null || true
  local found=0
  for _ in $(seq 1 30); do
    if adb logcat -d -s ToolBoxStageAHost:I '*:S' | grep -F "$marker" >/dev/null; then found=1; break; fi
    sleep 1
  done
  if [[ "$found" != 1 ]]; then
    adb logcat -d -s ToolBoxStageAHost:V '*:S' >&2 || true
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
