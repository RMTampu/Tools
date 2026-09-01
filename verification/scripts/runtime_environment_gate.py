#!/usr/bin/env python3
"""Fail-closed qualification of the actually attached Android 11 ARM64 target."""
from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import subprocess
from dataclasses import asdict, dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / "verification/evidence"
RUNTIME_PACKAGES = (
    "io.toolbox.app.debug.test",
    "io.toolbox.app.debug",
    "io.toolbox.app",
)


@dataclass
class Check:
    name: str
    passed: bool
    detail: str


def run(*args: str, check: bool = True, timeout: int = 30) -> str:
    proc = subprocess.run(
        args,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        timeout=timeout,
        check=False,
    )
    if check and proc.returncode != 0:
        raise RuntimeError(
            f"command failed ({proc.returncode}): {' '.join(args)}; "
            f"stderr={proc.stderr.strip()}"
        )
    return proc.stdout.strip()


def source_sha() -> str | None:
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


def serial_digest(serial: str | None) -> str | None:
    if not serial:
        return None
    return hashlib.sha256(serial.encode("utf-8")).hexdigest()


def main() -> int:
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    checks: list[Check] = []
    observations: dict[str, object] = {}
    sha = source_sha()

    def add(name: str, condition: bool, detail: str) -> None:
        checks.append(Check(name, bool(condition), detail))

    add("runtime-source-sha-known", sha is not None, f"gitSha={sha}")
    adb = shutil.which("adb")
    add("runtime-adb-present", adb is not None, adb or "adb missing")

    try:
        if adb:
            adb_version = run(adb, "version")
            add(
                "runtime-adb-version-known",
                bool(adb_version),
                adb_version.splitlines()[0] if adb_version else "",
            )

            ready: list[str] = []
            for line in run(adb, "devices", "-l").splitlines()[1:]:
                parts = line.strip().split()
                if len(parts) >= 2 and parts[1] == "device":
                    ready.append(parts[0])

            requested = os.environ.get("ANDROID_SERIAL", "").strip()
            add("runtime-device-unique", len(ready) == 1, f"readyDevices={len(ready)}")
            serial = ready[0] if len(ready) == 1 else None
            if requested:
                add(
                    "runtime-device-request-match",
                    serial == requested,
                    "requestedSerialMatchesUniqueReadyDevice=" + str(serial == requested),
                )
            add("runtime-device-connected", serial is not None, "one ready adb target required")

            if serial:
                if "\n" in serial or "\r" in serial:
                    raise RuntimeError("invalid adb serial contains newline")

                github_env = os.environ.get("GITHUB_ENV", "").strip()
                if github_env:
                    with open(github_env, "a", encoding="utf-8") as handle:
                        handle.write(f"ANDROID_SERIAL={serial}\n")
                    add("runtime-device-serial-exported", True, "bound through GITHUB_ENV")
                else:
                    os.environ["ANDROID_SERIAL"] = serial
                    add("runtime-device-serial-exported", True, "bound in current process")

                prefix = (adb, "-s", serial)
                state = run(*prefix, "get-state")
                api = run(*prefix, "shell", "getprop", "ro.build.version.sdk")
                release = run(*prefix, "shell", "getprop", "ro.build.version.release")
                abi = run(*prefix, "shell", "getprop", "ro.product.cpu.abi")
                abi_list = run(*prefix, "shell", "getprop", "ro.product.cpu.abilist")
                boot = run(*prefix, "shell", "getprop", "sys.boot_completed")
                fingerprint = run(*prefix, "shell", "getprop", "ro.build.fingerprint")
                qemu = run(*prefix, "shell", "getprop", "ro.kernel.qemu", check=False)

                add("runtime-device-state-ready", state == "device", f"state={state}")
                add("runtime-device-api30", api == "30", f"api={api}")
                add("runtime-device-android11", release == "11", f"release={release}")
                add(
                    "runtime-device-arm64",
                    abi == "arm64-v8a",
                    f"abi={abi}, abiList={abi_list}",
                )
                add("runtime-device-boot-complete", boot == "1", f"boot={boot}")
                add(
                    "runtime-device-fingerprint-known",
                    bool(fingerprint),
                    f"fingerprintPresent={bool(fingerprint)}",
                )
                add(
                    "runtime-transport-qualified",
                    state == "device" and boot == "1",
                    "adb ready + boot complete",
                )

                package_state: dict[str, bool] = {}
                for package_name in RUNTIME_PACKAGES:
                    installed = bool(
                        run(
                            *prefix,
                            "shell",
                            "pm",
                            "path",
                            package_name,
                            check=False,
                        ).strip()
                    )
                    package_state[package_name] = installed
                    safe_name = package_name.replace(".", "-")
                    add(
                        f"runtime-target-package-absent-{safe_name}",
                        not installed,
                        f"preinstalled={installed}",
                    )

                observations = {
                    "adbVersion": adb_version,
                    "deviceSerialSha256": serial_digest(serial),
                    "device": {
                        "api": api,
                        "release": release,
                        "abi": abi,
                        "abiList": abi_list,
                        "fingerprint": fingerprint,
                        "runtimeKind": "emulator" if qemu == "1" else "device",
                    },
                    "preexistingPackages": package_state,
                }
    except Exception as exc:
        add("runtime-environment-gate-execution", False, str(exc))

    failed = [item for item in checks if not item.passed]
    payload = {
        "schemaVersion": 3,
        "gate": "RUNTIME_ENVIRONMENT_GATE",
        "status": "PASS" if not failed else "NOT_PROVEN",
        "gitSha": sha,
        "hostArchitectureClaim": "NONE",
        "requiredTarget": {
            "androidRelease": "11",
            "api": 30,
            "primaryAbi": "arm64-v8a",
            "transport": "single-bound-adb-target",
            "preexistingToolBoxPackages": 0,
        },
        "checks": [asdict(item) for item in checks],
        "failed": [item.name for item in failed],
        "observations": observations,
        "unknown": 0 if not failed else len(failed),
        "missing": 0
        if not failed
        else sum(
            1
            for item in failed
            if "present" in item.name or "connected" in item.name
        ),
        "unproven": 0 if not failed else len(failed),
        "skipped": 0,
        "indeterminate": 0,
        "staleEvidence": 0,
        "faultEscape": 0,
    }
    (EVIDENCE / "runtime-tools.json").write_text(
        json.dumps(payload, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )

    if failed:
        print("RUNTIME_ENVIRONMENT_GATE = NOT_PROVEN", file=os.sys.stderr)
        for item in failed:
            print(f"FAIL {item.name}: {item.detail}", file=os.sys.stderr)
        return 1

    print("RUNTIME_ENVIRONMENT_GATE = PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
