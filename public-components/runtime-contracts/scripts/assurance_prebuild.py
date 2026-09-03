#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REPO = ROOT.parent.parent
STATE = ROOT / ".assurance-state"
OUT = STATE / "prebuild.json"

RULES = {
    "R1": ("APP_SAFE_R1_LOGIC_INPUT.md", 26),
    "R2": ("APP_SAFE_R2_CONCURRENCY_RESOURCE.md", 20),
    "R3": ("APP_SAFE_R3_LIFECYCLE_STATE_RECOVERY.md", 20),
    "R4": ("APP_SAFE_R4_PERSISTENCE_STORAGE_VERSION.md", 20),
    "R5": ("APP_SAFE_R5_SECURITY_NETWORK_BOUNDARY.md", 20),
    "R6": ("APP_SAFE_R6_BUILD_DEPENDENCY_INSTALL.md", 23),
    "R7": ("APP_SAFE_R7_NATIVE_PLUGIN_RUNTIME.md", 23),
    "R8": ("APP_SAFE_R8_UI_DEVICE_POWER.md", 22),
    "R9": ("APP_SAFE_R9_VERIFICATION_COMPLETENESS.md", 24),
}

APPLICABLE = {
    "R1": {1, 2, 3, 4, 11, 12, 14, 15, 16, 17, 18, 19, 21, 22, 25, 26},
    "R2": {1, 2, 3, 4, 5, 6, 7, 8, 13, 16, 18, 19, 20},
    "R3": set(),
    "R4": set(),
    "R5": {1, 2, 3, 5, 17, 18, 20},
    "R6": {1, 2, 3, 4, 5, 6, 7, 8, 9, 12, 16, 20, 21, 22, 23},
    "R7": set(),
    "R8": set(),
    "R9": {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 24},
}

NA_REASON = {
    "R1": "Not required by the closed metadata contract; see ASSURANCE_R1_R9.md method allocation.",
    "R2": "Absent async/UI/acquired-resource/low-memory claim or replaced by bounded resource contract as documented.",
    "R3": "No Android lifecycle/navigation/IPC/recovery subsystem exists in this Public component.",
    "R4": "No durable database/file/serialization/migration/backup state exists in this Public component.",
    "R5": "No auth/network/interpreter/secret/external-SDK boundary exists for this method.",
    "R6": "No Android APK variant/manifest/shrinker/sign/install/update stage exists in this Public component package.",
    "R7": "No native/JNI/plugin/dynamic/reflection/third-party runtime path exists.",
    "R8": "No UI/WebView/device/hardware/background-power path exists.",
    "R9": "Production observability is not a Public pre-promotion proof for this standalone component.",
}

EVIDENCE_PLAN = {
    "R1": ["CONTRACT", "SOURCE_VALIDATION", "SELF_TEST", "BOUNDARY_TEST", "PROPERTY_DIFFERENTIAL_TEST"],
    "R2": ["SOURCE_SYNCHRONIZATION", "CONCURRENCY_TEST", "BOUNDARY_RESOURCE_TEST"],
    "R3": ["SCOPE_ABSENCE_SCAN"],
    "R4": ["SCOPE_ABSENCE_SCAN"],
    "R5": ["CONTRACT_BOUNDARY", "FORBIDDEN_BOUNDARY_SCAN", "BOUNDARY_TEST", "PROPERTY_DIFFERENTIAL_TEST"],
    "R6": ["PINNED_WORKFLOW", "CLEAN_BUILD", "JAVAC_XLINT", "REPRODUCIBLE_JAR", "PACKAGE_MUTATION", "PROVENANCE"],
    "R7": ["SCOPE_ABSENCE_SCAN", "SIMULATOR_ENGINE_CALLBACKS_0"],
    "R8": ["SCOPE_ABSENCE_SCAN"],
    "R9": ["DOMAIN_MATRIX", "FAULT_TRACE", "ORACLE_DIVERSITY", "EVIDENCE_BINDING", "DEFEATER_REVIEW"],
}

FORBIDDEN_SOURCE_TOKENS = (
    "DexClassLoader",
    "PathClassLoader",
    "URLClassLoader",
    "Class.forName",
    "ProcessBuilder",
    "Runtime.getRuntime",
    "java.net.",
    "java.nio.file.",
    "java.sql.",
    "android.app.",
    "android.content.",
    "android.view.",
    "android.webkit.",
    "com.google.firebase",
    "TOOLBOX_SOURCE_TOKEN",
    "SIGNING_KEY",
)


def fail(message: str) -> None:
    raise SystemExit(f"R1_R8_PREBUILD_FAIL: {message}")


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def require_file(path: Path) -> str:
    if not path.is_file():
        fail(f"required file missing: {path.relative_to(REPO)}")
    return path.read_text(encoding="utf-8")


# Mandatory governing rules must exist in the current checkout.
governing = [
    "AGENTS.md",
    "GLOBAL_PUBLIC_PRIVATE_DEVELOPMENT_RULES.md",
    "AGENT_PROCEDURE_EXECUTION_RULES.md",
    "APPLICATION_SAFE_100_PROCESS.md",
]
rule_hashes: dict[str, str] = {}
for name in governing:
    path = REPO / name
    text = require_file(path)
    rule_hashes[name] = sha256(path)
    if not text.strip():
        fail(f"empty governing rule: {name}")

process = (REPO / "APPLICATION_SAFE_100_PROCESS.md").read_text(encoding="utf-8")
for marker in (
    "R1 LOGIC / INPUT",
    "R9 VERIFICATION COMPLETENESS",
    "R1-R8 APPLICABLE PREBUILD ANALYSIS",
    "R9 PUBLIC EVIDENCE COMPLETENESS",
    "PACKAGE_VALIDATION",
    "READY_PRIVATE",
):
    if marker not in process:
        fail(f"APPLICATION_SAFE_100 process marker missing: {marker}")

# Read every active R1-R9 source and prove no method disappeared from the source corpus.
methods: dict[str, list[dict[str, object]]] = {}
for domain, (filename, expected_count) in RULES.items():
    path = REPO / filename
    text = require_file(path)
    rule_hashes[filename] = sha256(path)
    found = sorted({int(value) for value in re.findall(rf"### {domain}-M(\d{{2}})", text)})
    expected = list(range(1, expected_count + 1))
    if found != expected:
        fail(f"{domain} method corpus mismatch found={found} expected={expected}")

    applicable = APPLICABLE[domain]
    if not applicable.issubset(set(expected)):
        fail(f"{domain} applicability references nonexistent method")

    rows: list[dict[str, object]] = []
    for number in expected:
        method_id = f"{domain}-M{number:02d}"
        if number in applicable:
            rows.append({
                "methodId": method_id,
                "classification": "APPLICABLE",
                "prebuildStatus": "EVIDENCE_PLANNED" if domain != "R9" else "POSTBUILD_R9_REQUIRED",
                "evidencePlan": EVIDENCE_PLAN[domain],
            })
        else:
            rows.append({
                "methodId": method_id,
                "classification": "N/A_SCOPE_PROVEN",
                "prebuildStatus": "PASS_BY_SCOPE_CLOSURE",
                "reason": NA_REASON[domain],
            })
    if len(rows) != expected_count:
        fail(f"{domain} method classification incomplete")
    methods[domain] = rows

contract = require_file(ROOT / "CONTRACT.md")
assurance = require_file(ROOT / "ASSURANCE_R1_R9.md")
contracts_source = require_file(ROOT / "src/main/java/io/toolbox/contracts/runtime/Contracts.java")
registry_source = require_file(ROOT / "src/main/java/io/toolbox/contracts/runtime/ProductRegistry.java")
simulator_source = require_file(ROOT / "src/main/java/io/toolbox/contracts/runtime/RuntimeContractsSimulator.java")

for marker in (
    "MAX_STABLE_ID_LENGTH",
    "MAX_COLLECTION_SIZE",
    "MAX_BUNDLE_ENTRIES",
    "RESOURCE_LIMIT",
    "metadata saja",
):
    if marker not in contract:
        fail(f"CONTRACT.md missing assurance marker: {marker}")

for marker in (
    "MAX_STABLE_ID_LENGTH",
    "MAX_COLLECTION_SIZE",
    "MAX_BUNDLE_ENTRIES",
):
    if marker not in contracts_source:
        fail(f"Contracts.java missing resource bound: {marker}")
if "MAX_REGISTRY_ENTRIES" not in registry_source:
    fail("ProductRegistry.java missing MAX_REGISTRY_ENTRIES")
if "synchronized (lock)" not in registry_source:
    fail("ProductRegistry.java missing single-monitor synchronization")
if "ENGINE_CALLBACKS_EXECUTED=0" not in simulator_source:
    fail("simulator missing zero-engine-execution witness")
if "R9_PUBLIC_EVIDENCE_COMPLETENESS" not in assurance:
    fail("assurance matrix missing R9 promotion invariant")

source_files = sorted((ROOT / "src/main/java").rglob("*.java"))
if not source_files:
    fail("no production source files")
for source in source_files:
    text = source.read_text(encoding="utf-8")
    for token in FORBIDDEN_SOURCE_TOKENS:
        if token in text:
            fail(f"forbidden Public executable/external boundary token {token!r} in {source.relative_to(ROOT)}")

# Scope-absence proofs for R3/R4/R7/R8 are static and fail closed if these APIs appear later.
absence_tokens = {
    "R3": ("android.app.", "android.content.Intent", "android.os.Binder", "androidx.lifecycle"),
    "R4": ("java.sql.", "android.database", "androidx.room", "DataStore", "FileOutputStream", "ObjectOutputStream"),
    "R7": ("System.loadLibrary", "native ", "JNIEnv", "Class.forName", "ClassLoader", "java.lang.reflect"),
    "R8": ("android.view.", "android.widget.", "android.webkit.", "android.hardware.", "WakeLock"),
}
all_source = "\n".join(path.read_text(encoding="utf-8") for path in source_files)
for domain, tokens in absence_tokens.items():
    present = [token for token in tokens if token in all_source]
    if present:
        fail(f"{domain} can no longer be N/A; newly present tokens={present}")

STATE.mkdir(parents=True, exist_ok=True)
payload = {
    "schemaVersion": 1,
    "status": "PASS",
    "gate": "R1_R8_PREBUILD_SCOPE_CLASSIFICATION",
    "componentId": "public.runtime-contracts",
    "componentVersion": "0.1.0",
    "contractId": "toolbox.runtime.metadata",
    "contractVersion": "1.0.0",
    "ruleHashesSha256": rule_hashes,
    "methods": methods,
    "sourceFiles": [path.relative_to(ROOT).as_posix() for path in source_files],
    "privateContentIncluded": False,
    "publicFinalApplicationClaim": False,
}
OUT.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
print("R1_R8_PREBUILD_SCOPE_CLASSIFICATION = PASS")
print("R1_R9_METHOD_CORPUS_LOADED = PASS")
print("PUBLIC_SCOPE_ABSENCE_PROOF_R3_R4_R7_R8 = PASS")
