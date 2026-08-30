#!/usr/bin/env python3
"""Runtime proof gate for the exact APK under test on Android 11 ARM64."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import time
from dataclasses import dataclass, asdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCOPE = json.loads((ROOT / "verification" / "application_scope.json").read_text(encoding="utf-8"))
EVIDENCE_DIR = ROOT / "verification" / "evidence"
DIAGNOSTICS_DIR = EVIDENCE_DIR / "diagnostics"
ACTIVITY_CLASS = "io.toolbox.app.MainActivity"


@dataclass
class Check:
    name: str
    passed: bool
    detail: str


def run(*args: str, check: bool = True, timeout: int = 60, binary: bool = False):
    completed = subprocess.run(
        list(args),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=timeout,
        check=False,
    )
    if check and completed.returncode != 0:
        stdout = completed.stdout.decode(errors="replace")
        stderr = completed.stderr.decode(errors="replace")
        raise RuntimeError(f"command failed ({completed.returncode}): {' '.join(args)}\nstdout={stdout}\nstderr={stderr}")
    if binary:
        return completed.stdout
    return completed.stdout.decode(errors="replace").strip()


def adb(*args: str, check: bool = True, timeout: int = 60) -> str:
    return run("adb", *args, check=check, timeout=timeout)


def parse_pss_kb(text: str) -> int | None:
    match = re.search(r"TOTAL PSS:\s*(\d+)", text)
    if match:
        return int(match.group(1))
    for line in text.splitlines():
        match = re.match(r"\s*TOTAL\s+(\d+)\b", line)
        if match:
            return int(match.group(1))
    return None


def start_activity(package: str) -> tuple[int | None, str]:
    output = adb(
        "shell", "am", "start", "-W",
        "-n", f"{package}/{ACTIVITY_CLASS}",
        "-a", "android.intent.action.MAIN",
        "-c", "android.intent.category.LAUNCHER",
        timeout=60,
    )
    match = re.search(r"TotalTime:\s*(\d+)", output)
    return (int(match.group(1)) if match else None, output)


def pidof(package: str) -> int | None:
    output = adb("shell", "pidof", package, check=False).strip()
    if not output:
        return None
    first = output.split()[0]
    return int(first) if first.isdigit() else None


def wait_for_pid(package: str, expected_present: bool, timeout_s: float = 10.0) -> int | None:
    deadline = time.time() + timeout_s
    last = None
    while time.time() < deadline:
        last = pidof(package)
        if (last is not None) == expected_present:
            return last
        time.sleep(0.25)
    return last


def meminfo(package: str) -> tuple[int | None, str]:
    text = adb("shell", "dumpsys", "meminfo", package, timeout=30)
    return parse_pss_kb(text), text


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--package", required=True)
    parser.add_argument("--variant", required=True, choices=("debug", "release"))
    args = parser.parse_args()

    EVIDENCE_DIR.mkdir(parents=True, exist_ok=True)
    DIAGNOSTICS_DIR.mkdir(parents=True, exist_ok=True)
    checks: list[Check] = []
    pids: set[int] = set()
    observations: dict[str, object] = {}

    def check(name: str, condition: bool, detail: str) -> None:
        checks.append(Check(name, bool(condition), detail))

    try:
        adb("wait-for-device", timeout=60)
        api = adb("shell", "getprop", "ro.build.version.sdk")
        abi = adb("shell", "getprop", "ro.product.cpu.abi")
        abi_list = adb("shell", "getprop", "ro.product.cpu.abilist")
        check("runtime-api30", api == "30", f"api={api}")
        check("runtime-primary-arm64", abi == "arm64-v8a", f"abi={abi}, abilist={abi_list}")
        observations["device"] = {"api": api, "abi": abi, "abiList": abi_list}

        # Remove stale install. A missing package is an expected precondition and not a failure.
        adb("uninstall", args.package, check=False, timeout=30)
        install_output = adb("install", "-r", "-t", str(args.apk.resolve()), timeout=120)
        check("apk-install", "Success" in install_output, install_output)

        adb("logcat", "-c")
        adb("shell", "am", "force-stop", args.package, check=False)
        wait_for_pid(args.package, False, timeout_s=5)

        cold_ms, cold_output = start_activity(args.package)
        first_pid = wait_for_pid(args.package, True, timeout_s=10)
        if first_pid is not None:
            pids.add(first_pid)
        startup_budget = int(SCOPE["budgets"]["coldStartupMs"])
        check("cold-start-time-observed", cold_ms is not None, cold_output)
        check("cold-start-budget", cold_ms is not None and cold_ms <= startup_budget, f"coldMs={cold_ms}, budgetMs={startup_budget}")
        check("process-alive-after-start", first_pid is not None, f"pid={first_pid}")
        observations["coldStartMs"] = cold_ms

        baseline_pss, baseline_mem = meminfo(args.package)
        (DIAGNOSTICS_DIR / f"meminfo-{args.variant}-baseline.txt").write_text(baseline_mem, encoding="utf-8")
        check("baseline-pss-observed", baseline_pss is not None, f"pssKb={baseline_pss}")
        observations["baselinePssKb"] = baseline_pss

        # UI semantics and accessibility-visible description from the actual rendered hierarchy.
        adb("shell", "uiautomator", "dump", "/sdcard/toolbox-window.xml", timeout=30)
        hierarchy = adb("shell", "cat", "/sdcard/toolbox-window.xml", timeout=30)
        (DIAGNOSTICS_DIR / f"ui-{args.variant}.xml").write_text(hierarchy, encoding="utf-8")
        check("ui-running-semantic", "RUNNING: ToolBox" in hierarchy, "UI hierarchy must expose RUNNING status")
        check("ui-accessibility-description", "ToolBox status RUNNING: ToolBox" in hierarchy, "content-desc must expose status semantics")

        screenshot = run("adb", "exec-out", "screencap", "-p", binary=True, timeout=30)
        screenshot_path = DIAGNOSTICS_DIR / f"screen-{args.variant}.png"
        screenshot_path.write_bytes(screenshot)
        check("runtime-screenshot-captured", screenshot_path.stat().st_size > 1024, f"bytes={screenshot_path.stat().st_size}")

        # R1/R5 external-boundary robustness: unexpected values must not alter bootstrap contract.
        external_output = adb(
            "shell", "am", "start", "-W",
            "-n", f"{args.package}/{ACTIVITY_CLASS}",
            "--es", "unexpected.external.payload", "../../invalid:payload?%25%00",
            timeout=60,
        )
        check("unexpected-external-payload-survives", "Error:" not in external_output and pidof(args.package) is not None, external_output)

        cycles = int(SCOPE["budgets"]["runtimeCycles"])
        for _ in range(cycles):
            adb("shell", "input", "keyevent", "KEYCODE_HOME", timeout=15)
            adb("shell", "monkey", "-p", args.package, "-c", "android.intent.category.LAUNCHER", "1", timeout=30)
            current_pid = wait_for_pid(args.package, True, timeout_s=5)
            if current_pid is not None:
                pids.add(current_pid)
        after_cycles_pss, after_cycles_mem = meminfo(args.package)
        (DIAGNOSTICS_DIR / f"meminfo-{args.variant}-after-cycles.txt").write_text(after_cycles_mem, encoding="utf-8")
        check("post-cycle-pss-observed", after_cycles_pss is not None, f"pssKb={after_cycles_pss}")

        growth_budget = int(SCOPE["budgets"]["runtimePssGrowthKb"])
        ceiling = int(SCOPE["budgets"]["runtimePssCeilingKb"])
        growth = None if baseline_pss is None or after_cycles_pss is None else after_cycles_pss - baseline_pss
        check("pss-growth-budget", growth is not None and growth <= growth_budget, f"growthKb={growth}, budgetKb={growth_budget}")
        check("pss-ceiling", after_cycles_pss is not None and after_cycles_pss <= ceiling, f"pssKb={after_cycles_pss}, ceilingKb={ceiling}")
        observations["afterCyclesPssKb"] = after_cycles_pss
        observations["pssGrowthKb"] = growth
        observations["runtimeCycles"] = cycles

        # Explicit process-death/restart witness. No accidental in-memory survival may be required.
        old_pid = pidof(args.package)
        if old_pid is not None:
            pids.add(old_pid)
        adb("shell", "am", "force-stop", args.package)
        dead_pid = wait_for_pid(args.package, False, timeout_s=10)
        check("process-death-observed", dead_pid is None, f"pidAfterForceStop={dead_pid}")
        restart_ms, restart_output = start_activity(args.package)
        new_pid = wait_for_pid(args.package, True, timeout_s=10)
        if new_pid is not None:
            pids.add(new_pid)
        check("process-restart-alive", new_pid is not None, restart_output)
        check("process-restart-is-new-process", old_pid is None or new_pid != old_pid, f"oldPid={old_pid}, newPid={new_pid}")
        check("restart-budget", restart_ms is not None and restart_ms <= startup_budget, f"restartMs={restart_ms}, budgetMs={startup_budget}")
        observations["restartMs"] = restart_ms

        # Capture package diagnostics while the exact final process is alive.
        activity_dump = adb("shell", "dumpsys", "activity", "activities", timeout=30)
        package_dump = adb("shell", "dumpsys", "package", args.package, timeout=30)
        final_mem = adb("shell", "dumpsys", "meminfo", args.package, timeout=30)
        (DIAGNOSTICS_DIR / f"activity-{args.variant}.txt").write_text(activity_dump, encoding="utf-8")
        (DIAGNOSTICS_DIR / f"package-{args.variant}.txt").write_text(package_dump, encoding="utf-8")
        (DIAGNOSTICS_DIR / f"meminfo-{args.variant}-final.txt").write_text(final_mem, encoding="utf-8")

        logcat = adb("logcat", "-v", "threadtime", "-d", timeout=60)
        (DIAGNOSTICS_DIR / f"logcat-{args.variant}.txt").write_text(logcat, encoding="utf-8")
        package_faults = []
        lines = logcat.splitlines()
        for index, line in enumerate(lines):
            context = "\n".join(lines[index:index + 12])
            if "FATAL EXCEPTION" in line and args.package in context:
                package_faults.append(context)
            if f"ANR in {args.package}" in line:
                package_faults.append(context)
            if "StrictMode" in line and any(re.search(rf"\s{pid}\s", line) for pid in pids):
                package_faults.append(context)
        check("no-runtime-fatal-anr-strictmode", not package_faults, f"faultCount={len(package_faults)}")
        observations["knownPids"] = sorted(pids)
        observations["faultCount"] = len(package_faults)

    except Exception as exc:
        check("runtime-gate-execution", False, str(exc))

    return finish(args.variant, args.package, args.apk.resolve(), checks, observations)


def finish(variant: str, package: str, apk: Path, checks: list[Check], observations: dict) -> int:
    failed = [item for item in checks if not item.passed]
    payload = {
        "schemaVersion": 1,
        "gate": "ANDROID_RUNTIME_GATE",
        "variant": variant,
        "package": package,
        "apk": str(apk),
        "status": "PASS" if not failed else "NOT_PROVEN",
        "checks": [asdict(item) for item in checks],
        "failed": [item.name for item in failed],
        "observations": observations,
    }
    EVIDENCE_DIR.mkdir(parents=True, exist_ok=True)
    out = EVIDENCE_DIR / f"runtime-{variant}.json"
    out.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if failed:
        print(f"ANDROID_RUNTIME_GATE[{variant}] = NOT_PROVEN", file=sys.stderr)
        for item in failed:
            print(f"FAIL {item.name}: {item.detail}", file=sys.stderr)
        return 1
    print(f"ANDROID_RUNTIME_GATE[{variant}] = PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
