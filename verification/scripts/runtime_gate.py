#!/usr/bin/env python3
"""Runtime proof for the exact ToolBox APK on Android 11 ARM64."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import time
from dataclasses import asdict, dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCOPE = json.loads((ROOT / "verification/application_scope.json").read_text(encoding="utf-8"))
EVIDENCE = ROOT / "verification/evidence"
DIAG = EVIDENCE / "diagnostics"
ACTIVITY = "io.toolbox.app.MainActivity"
SERIAL = os.environ.get("ANDROID_SERIAL", "").strip()


@dataclass
class Check:
    name: str
    passed: bool
    detail: str


def run(*args: str, check: bool = True, timeout: int = 60, binary: bool = False):
    command = list(args)
    proc = subprocess.run(
        command,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=timeout,
        check=False,
    )
    if check and proc.returncode != 0:
        raise RuntimeError(
            f"command failed ({proc.returncode}): {' '.join(command)}\n"
            f"stdout={proc.stdout.decode(errors='replace')}\n"
            f"stderr={proc.stderr.decode(errors='replace')}"
        )
    return proc.stdout if binary else proc.stdout.decode(errors="replace").strip()


def adb(*args: str, check: bool = True, timeout: int = 60):
    command = ["adb"]
    if SERIAL:
        command += ["-s", SERIAL]
    command += list(args)
    return run(*command, check=check, timeout=timeout)


def adb_binary(*args: str, check: bool = True, timeout: int = 60) -> bytes:
    command = ["adb"]
    if SERIAL:
        command += ["-s", SERIAL]
    command += list(args)
    return run(*command, check=check, timeout=timeout, binary=True)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def git_sha() -> str | None:
    value = os.environ.get("GITHUB_SHA", "").strip().lower()
    if re.fullmatch(r"[0-9a-f]{40}", value):
        return value
    try:
        value = subprocess.check_output(
            ["git", "rev-parse", "HEAD"],
            cwd=ROOT,
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip().lower()
        return value if re.fullmatch(r"[0-9a-f]{40}", value) else None
    except Exception:
        return None


def serial_digest() -> str | None:
    return hashlib.sha256(SERIAL.encode("utf-8")).hexdigest() if SERIAL else None


def pidof(package_name: str) -> int | None:
    output = adb("shell", "pidof", package_name, check=False).strip()
    first = output.split()[0] if output else ""
    return int(first) if first.isdigit() else None


def wait_pid(package_name: str, present: bool, timeout: int = 10) -> int | None:
    end = time.time() + timeout
    last = None
    while time.time() < end:
        last = pidof(package_name)
        if (last is not None) == present:
            return last
        time.sleep(0.25)
    return last


def start(package_name: str):
    output = adb(
        "shell",
        "am",
        "start",
        "-W",
        "-n",
        f"{package_name}/{ACTIVITY}",
        "-a",
        "android.intent.action.MAIN",
        "-c",
        "android.intent.category.LAUNCHER",
    )
    match = re.search(r"TotalTime:\s*(\d+)", output)
    return (int(match.group(1)) if match else None, output)


def parse_pss(text: str) -> int | None:
    match = re.search(r"TOTAL PSS:\s*(\d+)", text)
    if match:
        return int(match.group(1))
    for line in text.splitlines():
        match = re.match(r"\s*TOTAL\s+(\d+)\b", line)
        if match:
            return int(match.group(1))
    return None


def setting_get(namespace: str, key: str) -> str:
    return adb("shell", "settings", "get", namespace, key, check=False).strip()


def setting_restore(namespace: str, key: str, value: str) -> None:
    if value in ("", "null"):
        adb("shell", "settings", "delete", namespace, key, check=False, timeout=15)
    else:
        adb("shell", "settings", "put", namespace, key, value, timeout=15)


def wm_override(kind: str) -> str | None:
    output = adb("shell", "wm", kind, timeout=15)
    match = re.search(rf"Override {re.escape(kind)}:\s*(\S+)", output, re.IGNORECASE)
    return match.group(1) if match else None


def restore_wm(kind: str, value: str | None) -> None:
    if value is None:
        adb("shell", "wm", kind, "reset", timeout=15)
    else:
        adb("shell", "wm", kind, value, timeout=15)


def current_night_mode() -> str | None:
    output = adb("shell", "cmd", "uimode", "night", check=False, timeout=15)
    match = re.search(r"Night mode:\s*([A-Za-z_]+)", output)
    if match:
        value = match.group(1).lower()
        return "custom_schedule" if value == "custom" else value

    secure = setting_get("secure", "ui_night_mode")
    mapping = {"0": "auto", "1": "no", "2": "yes", "3": "custom_schedule"}
    return mapping.get(secure)


def restore_night_mode(value: str) -> None:
    adb("shell", "cmd", "uimode", "night", value, timeout=15)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--package", required=True)
    parser.add_argument("--variant", required=True, choices=("debug", "release"))
    args = parser.parse_args()

    EVIDENCE.mkdir(parents=True, exist_ok=True)
    DIAG.mkdir(parents=True, exist_ok=True)
    checks: list[Check] = []
    observations: dict[str, object] = {}
    pids: set[int] = set()
    apk = args.apk.resolve()
    source_sha = git_sha()
    state_snapshot: dict[str, object] | None = None

    def check(name: str, condition: bool, detail: str) -> None:
        checks.append(Check(name, bool(condition), detail))

    def witness(label: str) -> None:
        adb(
            "shell",
            "am",
            "start",
            "-W",
            "-n",
            f"{args.package}/{ACTIVITY}",
            timeout=60,
        )
        time.sleep(1.2)
        remote = f"/sdcard/toolbox-{args.variant}-{label}.xml"
        adb("shell", "uiautomator", "dump", remote, timeout=30)
        ui = adb("shell", "cat", remote, timeout=30)
        adb("shell", "rm", "-f", remote, check=False, timeout=15)
        (DIAG / f"ui-{args.variant}-{label}.xml").write_text(ui, encoding="utf-8")
        check(
            f"ui-running-{label}",
            "RUNNING: ToolBox" in ui,
            f"{label} RUNNING semantic",
        )
        check(
            f"ui-accessibility-{label}",
            "ToolBox status RUNNING: ToolBox" in ui,
            f"{label} accessibility semantic",
        )
        screenshot = adb_binary("exec-out", "screencap", "-p", timeout=30)
        screenshot_path = DIAG / f"screen-{args.variant}-{label}.png"
        screenshot_path.write_bytes(screenshot)
        check(
            f"screenshot-{label}",
            screenshot_path.stat().st_size > 1024,
            f"bytes={screenshot_path.stat().st_size}",
        )

    try:
        check("runtime-source-sha-known", source_sha is not None, f"gitSha={source_sha}")
        check("runtime-apk-present", apk.is_file() and apk.stat().st_size > 0, str(apk))
        check(
            "runtime-device-serial-bound",
            bool(SERIAL),
            f"serialSha256={serial_digest()}",
        )
        if not SERIAL:
            raise RuntimeError("ANDROID_SERIAL is not bound by runtime_environment_gate.py")

        observations["gitSha"] = source_sha
        observations["apkSha256"] = sha256(apk) if apk.is_file() else None
        observations["deviceSerialSha256"] = serial_digest()

        adb("wait-for-device", timeout=120)
        state = adb("get-state")
        api = adb("shell", "getprop", "ro.build.version.sdk")
        release = adb("shell", "getprop", "ro.build.version.release")
        abi = adb("shell", "getprop", "ro.product.cpu.abi")
        abi_list = adb("shell", "getprop", "ro.product.cpu.abilist")
        fingerprint = adb("shell", "getprop", "ro.build.fingerprint")
        boot = adb("shell", "getprop", "sys.boot_completed")
        check("runtime-device-state-ready", state == "device", f"state={state}")
        check("runtime-api30", api == "30", f"api={api}")
        check("runtime-android11", release == "11", f"release={release}")
        check(
            "runtime-primary-arm64",
            abi == "arm64-v8a",
            f"abi={abi}, abilist={abi_list}",
        )
        check("runtime-boot-complete", boot == "1", f"boot={boot}")
        observations["device"] = {
            "api": api,
            "release": release,
            "abi": abi,
            "abiList": abi_list,
            "fingerprint": fingerprint,
        }

        state_snapshot = {
            "font_scale": setting_get("system", "font_scale"),
            "accelerometer_rotation": setting_get("system", "accelerometer_rotation"),
            "user_rotation": setting_get("system", "user_rotation"),
            "size_override": wm_override("size"),
            "density_override": wm_override("density"),
            "night_mode": current_night_mode(),
        }
        snapshot_complete = (
            state_snapshot["font_scale"] != ""
            and state_snapshot["accelerometer_rotation"] != ""
            and state_snapshot["user_rotation"] != ""
            and state_snapshot["night_mode"] in {"yes", "no", "auto", "custom_schedule"}
        )
        check(
            "device-state-snapshot-complete",
            snapshot_complete,
            "font/rotation/night/size/density captured",
        )
        if not snapshot_complete:
            raise RuntimeError("device state snapshot incomplete; refusing runtime mutations")

        preexisting = adb("shell", "pm", "path", args.package, check=False).strip()
        check(
            "target-package-absent-before-test",
            not preexisting,
            f"preinstalled={bool(preexisting)}",
        )
        if preexisting:
            raise RuntimeError(
                f"{args.package} is already installed; refusing to erase or overwrite existing app state"
            )

        install = adb("install", "-t", str(apk), timeout=180)
        check("apk-clean-install", "Success" in install, install)
        reinstall = adb("install", "-r", "-t", str(apk), timeout=180)
        check("apk-same-version-reinstall", "Success" in reinstall, reinstall)

        adb("logcat", "-c")
        adb("shell", "am", "force-stop", args.package, check=False)
        wait_pid(args.package, False, 5)
        cold, cold_output = start(args.package)
        first = wait_pid(args.package, True, 15)
        startup_budget = int(SCOPE["budgets"]["coldStartupMs"])
        check("cold-start-time-observed", cold is not None, cold_output)
        check(
            "cold-start-budget",
            cold is not None and cold <= startup_budget,
            f"coldMs={cold}, budgetMs={startup_budget}",
        )
        check("process-alive-after-start", first is not None, f"pid={first}")
        observations["coldStartMs"] = cold
        if first:
            pids.add(first)

        base_mem = adb("shell", "dumpsys", "meminfo", args.package, timeout=30)
        base_pss = parse_pss(base_mem)
        (DIAG / f"meminfo-{args.variant}-baseline.txt").write_text(
            base_mem, encoding="utf-8"
        )
        check("baseline-pss-observed", base_pss is not None, f"pssKb={base_pss}")
        observations["baselinePssKb"] = base_pss
        witness("baseline")

        external = adb(
            "shell",
            "am",
            "start",
            "-W",
            "-n",
            f"{args.package}/{ACTIVITY}",
            "--es",
            "unexpected.external.payload",
            "../../invalid:payload?%25%00",
        )
        check(
            "unexpected-external-payload-survives",
            "Error:" not in external and pidof(args.package) is not None,
            external,
        )

        adb("shell", "settings", "put", "system", "accelerometer_rotation", "0")
        adb("shell", "settings", "put", "system", "user_rotation", "1")
        witness("landscape")
        adb("shell", "settings", "put", "system", "user_rotation", "0")
        witness("portrait")

        adb("shell", "settings", "put", "system", "font_scale", "1.3")
        witness("font-1_3")
        adb("shell", "settings", "put", "system", "font_scale", "2.0")
        witness("font-2_0")
        adb("shell", "settings", "put", "system", "font_scale", "1.0")

        adb("shell", "cmd", "uimode", "night", "yes")
        witness("night")
        adb("shell", "cmd", "uimode", "night", "no")
        witness("day")

        adb("shell", "wm", "size", "480x800")
        adb("shell", "wm", "density", "240")
        witness("compact-screen")
        restore_wm("size", state_snapshot["size_override"])
        restore_wm("density", state_snapshot["density_override"])

        adb("shell", "dumpsys", "gfxinfo", args.package, "reset", check=False)
        cycles = int(SCOPE["budgets"]["runtimeCycles"])
        for _ in range(cycles):
            adb("shell", "input", "keyevent", "KEYCODE_HOME", timeout=15)
            adb(
                "shell",
                "monkey",
                "-p",
                args.package,
                "-c",
                "android.intent.category.LAUNCHER",
                "1",
                timeout=30,
            )
            current = wait_pid(args.package, True, 5)
            if current:
                pids.add(current)

        after_mem = adb("shell", "dumpsys", "meminfo", args.package, timeout=30)
        after_pss = parse_pss(after_mem)
        (DIAG / f"meminfo-{args.variant}-after-cycles.txt").write_text(
            after_mem, encoding="utf-8"
        )
        growth = None if base_pss is None or after_pss is None else after_pss - base_pss
        check("post-cycle-pss-observed", after_pss is not None, f"pssKb={after_pss}")
        check(
            "pss-growth-budget",
            growth is not None and growth <= int(SCOPE["budgets"]["runtimePssGrowthKb"]),
            f"growthKb={growth}",
        )
        check(
            "pss-ceiling",
            after_pss is not None and after_pss <= int(SCOPE["budgets"]["runtimePssCeilingKb"]),
            f"pssKb={after_pss}",
        )
        observations.update(
            {
                "afterCyclesPssKb": after_pss,
                "pssGrowthKb": growth,
                "runtimeCycles": cycles,
            }
        )

        gfx = adb("shell", "dumpsys", "gfxinfo", args.package, timeout=30)
        (DIAG / f"gfxinfo-{args.variant}.txt").write_text(gfx, encoding="utf-8")
        jank_match = re.search(r"Janky frames:\s*\d+\s*\(([0-9.]+)%\)", gfx)
        jank = float(jank_match.group(1)) if jank_match else None
        check("janky-frame-percent-observed", jank is not None, f"jankyPercent={jank}")
        check(
            "janky-frame-budget",
            jank is not None and jank <= float(SCOPE["budgets"]["jankyFramePercent"]),
            f"actual={jank}, max={SCOPE['budgets']['jankyFramePercent']}",
        )
        observations["jankyFramePercent"] = jank

        old = pidof(args.package)
        adb("shell", "am", "force-stop", args.package)
        dead = wait_pid(args.package, False, 10)
        check("process-death-observed", dead is None, f"pidAfterForceStop={dead}")
        restart, restart_output = start(args.package)
        new = wait_pid(args.package, True, 15)
        check("process-restart-alive", new is not None, restart_output)
        check(
            "process-restart-is-new-process",
            old is None or new != old,
            f"oldPid={old}, newPid={new}",
        )
        check(
            "restart-budget",
            restart is not None and restart <= startup_budget,
            f"restartMs={restart}",
        )
        observations["restartMs"] = restart
        if old:
            pids.add(old)
        if new:
            pids.add(new)

        logcat = adb("logcat", "-v", "threadtime", "-d", timeout=60)
        (DIAG / f"logcat-{args.variant}.txt").write_text(logcat, encoding="utf-8")
        faults: list[str] = []
        lines = logcat.splitlines()
        for index, line in enumerate(lines):
            context = "\n".join(lines[index : index + 12])
            if "FATAL EXCEPTION" in line and args.package in context:
                faults.append(context)
            if f"ANR in {args.package}" in line:
                faults.append(context)
            if "StrictMode" in line and any(
                re.search(rf"\s{pid}\s", line) for pid in pids
            ):
                faults.append(context)
        check(
            "no-runtime-fatal-anr-strictmode",
            not faults,
            f"faultCount={len(faults)}",
        )
        observations["faultCount"] = len(faults)
        observations["knownPids"] = sorted(pids)

    except Exception as exc:
        check("runtime-gate-execution", False, str(exc))
    finally:
        if state_snapshot is not None:
            restore_errors: list[str] = []
            try:
                restore_wm("size", state_snapshot["size_override"])
                restore_wm("density", state_snapshot["density_override"])
                setting_restore("system", "font_scale", str(state_snapshot["font_scale"]))
                setting_restore("system", "user_rotation", str(state_snapshot["user_rotation"]))
                setting_restore(
                    "system",
                    "accelerometer_rotation",
                    str(state_snapshot["accelerometer_rotation"]),
                )
                restore_night_mode(str(state_snapshot["night_mode"]))
            except Exception as exc:
                restore_errors.append(str(exc))

            try:
                restored = (
                    setting_get("system", "font_scale") == state_snapshot["font_scale"]
                    and setting_get("system", "accelerometer_rotation")
                    == state_snapshot["accelerometer_rotation"]
                    and setting_get("system", "user_rotation") == state_snapshot["user_rotation"]
                    and wm_override("size") == state_snapshot["size_override"]
                    and wm_override("density") == state_snapshot["density_override"]
                    and current_night_mode() == state_snapshot["night_mode"]
                )
                check(
                    "device-state-restored",
                    restored and not restore_errors,
                    "exact pre-test font/rotation/night/size/density restored"
                    if restored and not restore_errors
                    else f"restoreErrors={restore_errors}",
                )
            except Exception as exc:
                check("device-state-restored", False, str(exc))

    failed = [item for item in checks if not item.passed]
    payload = {
        "schemaVersion": 3,
        "gate": "ANDROID_RUNTIME_GATE",
        "variant": args.variant,
        "package": args.package,
        "apk": str(apk),
        "status": "PASS" if not failed else "NOT_PROVEN",
        "gitSha": source_sha,
        "apkSha256": observations.get("apkSha256"),
        "checks": [asdict(item) for item in checks],
        "failed": [item.name for item in failed],
        "observations": observations,
    }
    (EVIDENCE / f"runtime-{args.variant}.json").write_text(
        json.dumps(payload, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )

    if failed:
        print(f"ANDROID_RUNTIME_GATE[{args.variant}] = NOT_PROVEN", file=sys.stderr)
        for item in failed:
            print(f"FAIL {item.name}: {item.detail}", file=sys.stderr)
        return 1

    print(f"ANDROID_RUNTIME_GATE[{args.variant}] = PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
