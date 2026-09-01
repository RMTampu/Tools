#!/usr/bin/env python3
"""Prevent recurrence of proven-invalid runtime, artifact, and device-state routes."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
static = (ROOT / ".github/workflows/application-safe-100.yml").read_text(encoding="utf-8")
runtime = (ROOT / ".github/workflows/application-runtime-r9.yml").read_text(encoding="utf-8")
environment_gate = (ROOT / "verification/scripts/runtime_environment_gate.py").read_text(encoding="utf-8")
runtime_gate = (ROOT / "verification/scripts/runtime_gate.py").read_text(encoding="utf-8")
errors: list[str] = []

if (ROOT / ".github/workflows/android11-arm64-runtime-host-probe.yml").exists():
    errors.append("obsolete hosted runtime probe exists")
if "\n  application-runtime-r9:\n" in static:
    errors.append("static workflow still owns runtime job")
if "Prepare canonical runtime bundle" not in static or "path: verification/runtime-bundle/" not in static:
    errors.append("canonical artifact bundle missing")
if "candidate/verification/" in runtime:
    errors.append("wrong nested artifact path reintroduced")
if "runs-on: [self-hosted, linux, toolbox-android11-arm64-runtime]" not in runtime:
    errors.append("runtime not bound to qualified self-hosted label")
if "runtime_environment_gate.py" not in runtime:
    errors.append("runtime target qualification missing")

# Feature-branch runtime must be push-driven. workflow_dispatch is not a valid
# canonical trigger until the workflow file exists on the repository default branch.
if "\n  push:\n    branches: [kernel-foundation-hardening]\n" not in runtime:
    errors.append("automatic feature-branch runtime push trigger missing")
if "workflow_dispatch:" in runtime:
    errors.append("misleading feature-branch workflow_dispatch route reintroduced")
if "Wait for exact successful static candidate" not in runtime:
    errors.append("runtime no longer waits for exact static candidate")
if "STATIC_CANDIDATE_WAIT_TIMEOUT" not in runtime or "time.sleep(20)" not in runtime:
    errors.append("bounded exact-static polling contract missing")
if "Reject stale proof revision before device access" not in runtime:
    errors.append("stale revision rejection missing")
if "group: toolbox-android11-arm64-physical-runtime" not in runtime or "cancel-in-progress: false" not in runtime:
    errors.append("physical runtime serialization contract missing")

job = runtime.split("\n  runtime-r9:\n", 1)[1] if "\n  runtime-r9:\n" in runtime else runtime
for token in (
    "sdkmanager ",
    "avdmanager ",
    "system-images;android-30",
    "-avd toolbox",
    "runs-on: macos-",
    "runs-on: ubuntu-24.04-arm",
):
    if token in job:
        errors.append(f"closed hosted emulator route token: {token}")
for token in (":toolbox-app:assembleDebug", ":toolbox-app:assembleRelease"):
    if token in job:
        errors.append(f"production rebuild in runtime workflow: {token}")

required_runtime_workflow_markers = (
    "Qualify and bind attached Android 11 ARM64 runtime",
    "adb -s \"$ANDROID_SERIAL\"",
    "Isolate post-instrumentation state",
    "Isolate debug runtime before release",
)
for marker in required_runtime_workflow_markers:
    if marker not in runtime:
        errors.append(f"runtime isolation/binding marker missing: {marker}")

required_environment_markers = (
    "runtime-device-unique",
    "runtime-device-serial-exported",
    "runtime-target-package-absent-",
    "ANDROID_SERIAL",
)
for marker in required_environment_markers:
    if marker not in environment_gate:
        errors.append(f"runtime environment safety marker missing: {marker}")

required_runtime_gate_markers = (
    "runtime-device-serial-bound",
    "target-package-absent-before-test",
    "device-state-snapshot-complete",
    "device-state-restored",
    "setting_restore",
    "restore_wm",
    "restore_night_mode",
)
for marker in required_runtime_gate_markers:
    if marker not in runtime_gate:
        errors.append(f"runtime device-state safety marker missing: {marker}")

if "adb('uninstall'" in runtime_gate or 'adb("uninstall"' in runtime_gate:
    errors.append("runtime gate may erase a pre-existing package before proving clean target state")

if errors:
    print("RUNTIME_CI_ROUTE_GUARD = NOT_PROVEN", file=sys.stderr)
    for error in errors:
        print("FAIL " + error, file=sys.stderr)
    raise SystemExit(1)

print("RUNTIME_CI_ROUTE_GUARD = PASS")
