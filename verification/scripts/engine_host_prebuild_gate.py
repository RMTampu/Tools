#!/usr/bin/env python3
"""Fail-closed structural prebuild gate for the ToolBox Engine Host."""

from __future__ import annotations

import json
import re
import sys
from dataclasses import asdict, dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCOPE = ROOT / "verification" / "application_scope.json"
SETTINGS = ROOT / "settings.gradle.kts"
BUILD = ROOT / "toolbox-engine-host" / "build.gradle.kts"
LOCK = ROOT / "toolbox-engine-host" / "gradle.lockfile"
MAIN = ROOT / "toolbox-engine-host" / "src" / "main"
TEST = ROOT / "toolbox-engine-host" / "src" / "test"
EVIDENCE = ROOT / "verification" / "evidence" / "engine-host-prebuild.json"


@dataclass
class Check:
    name: str
    passed: bool
    detail: str


checks: list[Check] = []


def check(name: str, condition: bool, detail: str) -> None:
    checks.append(Check(name, bool(condition), detail))


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def main() -> int:
    EVIDENCE.parent.mkdir(parents=True, exist_ok=True)

    required = [
        SCOPE,
        SETTINGS,
        BUILD,
        LOCK,
        ROOT / "BASELINE_CAPABILITY_CONTRACT_V1.md",
        ROOT / "EXTERNAL_PACKAGE_BOUNDARY_V1.md",
        MAIN / "kotlin" / "io" / "toolbox" / "enginehost" / "EngineModel.kt",
        MAIN / "kotlin" / "io" / "toolbox" / "enginehost" / "EngineHost.kt",
        MAIN / "kotlin" / "io" / "toolbox" / "enginehost" / "EngineRuntimeScope.kt",
        TEST / "kotlin" / "io" / "toolbox" / "enginehost" / "EngineHostTest.kt",
        TEST / "kotlin" / "io" / "toolbox" / "enginehost" / "EngineHostLifecycleTest.kt",
    ]
    missing = [str(path.relative_to(ROOT)) for path in required if not path.is_file()]
    check("required-engine-host-files", not missing, f"missing={missing}")
    if missing:
        return finish()

    scope = json.loads(read(SCOPE))
    r7 = scope.get("domainScope", {}).get("R7", {})
    check("r7-scope-required", r7.get("status") == "REQUIRED", str(r7))

    settings = read(SETTINGS)
    check("engine-host-module-declared", 'include(":toolbox-engine-host")' in settings, "settings.gradle.kts")

    build = read(BUILD)
    expected_build_fragments = {
        "kotlin-jvm-plugin": 'kotlin("jvm")',
        "kernel-project-dependency": 'implementation(project(":toolbox-kernel"))',
        "java-target-11": "targetCompatibility = JavaVersion.VERSION_11",
        "kotlin-target-11": 'kotlinOptions.jvmTarget = "11"',
        "dependency-conflict-fail": "failOnVersionConflict()",
        "junit-platform": "useJUnitPlatform()",
    }
    for name, fragment in expected_build_fragments.items():
        check(name, fragment in build, fragment)

    lock_text = read(LOCK)
    lock_rows = [line for line in lock_text.splitlines() if line.strip() and not line.startswith("#") and line.strip() != "empty="]
    check("engine-host-lock-nonempty", bool(lock_rows), f"rows={len(lock_rows)}")
    check(
        "engine-host-lock-no-dynamic-selector",
        not any(re.search(r"SNAPSHOT|latest[.]|:\+|:\[|:\(", row, re.IGNORECASE) for row in lock_rows),
        "exact locked coordinates required",
    )

    source_files = sorted(path for path in MAIN.rglob("*") if path.is_file())
    source_text = "\n".join(read(path) for path in source_files if path.suffix in {".kt", ".java"})
    check("engine-host-source-present", bool(source_files), f"files={len(source_files)}")

    forbidden_runtime_tokens = [
        "Class.forName",
        "DexClassLoader",
        "PathClassLoader",
        "System.loadLibrary",
        "System.load(",
        "ProcessBuilder",
        "Runtime.getRuntime().exec",
    ]
    forbidden_hits = [token for token in forbidden_runtime_tokens if token in source_text]
    check("no-arbitrary-dynamic-code-path", not forbidden_hits, f"hits={forbidden_hits}")

    forbidden_payload_suffixes = {".dex", ".jar", ".so", ".sh"}
    payload_hits = [
        str(path.relative_to(ROOT))
        for path in (ROOT / "toolbox-engine-host").rglob("*")
        if path.is_file() and path.suffix.lower() in forbidden_payload_suffixes
    ]
    check("no-external-executable-payload", not payload_hits, f"hits={payload_hits}")

    model = read(MAIN / "kotlin" / "io" / "toolbox" / "enginehost" / "EngineModel.kt")
    host = read(MAIN / "kotlin" / "io" / "toolbox" / "enginehost" / "EngineHost.kt")
    scope_source = read(MAIN / "kotlin" / "io" / "toolbox" / "enginehost" / "EngineRuntimeScope.kt")

    check("compiled-apk-origin-only", "enum class EngineOrigin" in model and "COMPILED_APK" in model, "EngineOrigin")
    check("metadata-first-provider-contract", "interface EngineProvider" in model and "val descriptor: EngineDescriptor" in model, "EngineProvider")
    check("lazy-engine-factory", "fun create(): ToolBoxEngine" in model and "record.provider.create()" in host, "provider materialization")
    check("android-30-default", "androidApi = 30" in host, "EngineEnvironment")
    check("arm64-default", 'abi = "arm64-v8a"' in host, "EngineEnvironment")
    check("compatibility-before-create", host.find("missingRequirement") < host.find("record.provider.create()"), "requirement check precedes materialization")
    check("engine-failure-state", "EngineState.FAILED" in host and "failActivation" in host, "failure isolation path")
    check("lease-reference-count", "activeLeases" in host and "last-lease-closed" in host, "reference-counted engine lease")
    check("owned-scope-cleanup", "cleanup.addFirst" in scope_source and "scope.close()" in host, "owned resource cleanup")
    check("closed-scope-access", "checkOpen()" in scope_source, "runtime scope access guard")
    check("host-service-start-bound", "registerHostService()" in host and "unregisterHostService()" in host, "service lifecycle binding")

    tests = "\n".join(read(path) for path in TEST.rglob("*.kt"))
    required_test_markers = [
        "registration is metadata only and acquisition is lazy",
        "last lease releases engine while shared lease keeps it alive",
        "missing required capability blocks materialization",
        "one engine activation failure stays isolated from healthy engine and kernel",
        "host service exists only while module is started and returns after restart",
    ]
    missing_tests = [marker for marker in required_test_markers if marker not in tests]
    check("required-engine-host-contract-tests", not missing_tests, f"missing={missing_tests}")

    return finish()


def finish() -> int:
    failed = [item for item in checks if not item.passed]
    payload = {
        "schemaVersion": 1,
        "gate": "ENGINE_HOST_PREBUILD_GATE",
        "status": "PASS" if not failed else "NOT_PROVEN",
        "target": "Android 11 / API 30 / arm64-v8a",
        "checks": [asdict(item) for item in checks],
        "failed": [item.name for item in failed],
    }
    EVIDENCE.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if failed:
        print("ENGINE_HOST_PREBUILD_GATE = NOT_PROVEN", file=sys.stderr)
        for item in failed:
            print(f"FAIL {item.name}: {item.detail}", file=sys.stderr)
        return 1
    print("ENGINE_HOST_PREBUILD_GATE = PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
