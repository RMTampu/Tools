#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STATE = ROOT / ".assurance-state"
OUT = STATE / "prebuild.json"
SPEC = ROOT / "COMPONENT_SPEC.json"
CONTRACT = ROOT / "CONTRACT.json"
PLAN = ROOT / "ASSURANCE_PLAN_R1_R9.json"
SOURCE_ROOT = ROOT / "src" / "main" / "java"


def fail(message: str) -> None:
    raise SystemExit(f"R1_R8_PREBUILD_FAIL: {message}")


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load(path: Path) -> dict:
    if not path.is_file():
        fail(f"required input missing: {path.name}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as error:
        fail(f"invalid JSON {path.name}: {error}")


spec = load(SPEC)
contract = load(CONTRACT)
plan = load(PLAN)
expected = {
    "projectId": "ToolBox",
    "componentId": "public.runtime-safety-contracts",
    "componentVersion": "0.1.0",
    "contractId": "toolbox.runtime.safety",
    "contractVersion": "1.0.0",
}
for key, value in expected.items():
    if key in spec and spec.get(key) != value:
        fail(f"spec {key} mismatch")
    if key in contract and contract.get(key) != value:
        fail(f"contract {key} mismatch")
    if key in plan and plan.get(key) != value:
        fail(f"assurance plan {key} mismatch")

if spec.get("scope") != "PUBLIC_COMPONENT_ONLY":
    fail("component scope is not Public-only")
if spec.get("target", {}).get("javaRelease") != 11 or contract.get("javaRelease") != 11:
    fail("Java release is not locked to 11")
if spec.get("dependencies", {}).get("externalRuntimeDependencies") != 0:
    fail("external runtime dependencies must be zero")
if spec.get("privateContentRequired") is not False:
    fail("Private content requirement must be false")
if spec.get("finalApplicationSafe100Claimed") is not False:
    fail("final application claim is forbidden in Public")

for field in (
    "androidLifecycle",
    "persistentStorage",
    "network",
    "permissionGrant",
    "nativeOrPluginLoading",
    "dynamicCodeLoading",
    "uiOrHardware",
    "signing",
    "firebase",
):
    if spec.get("runtimeAuthority", {}).get(field) is not False:
        fail(f"runtime authority must be false: {field}")

expected_domain_status = {
    "R1": "APPLICABLE",
    "R2": "APPLICABLE",
    "R3": "APPLICABLE",
    "R4": "N_A_SCOPE_PROVEN",
    "R5": "APPLICABLE",
    "R6": "APPLICABLE",
    "R7": "N_A_SCOPE_PROVEN",
    "R8": "N_A_SCOPE_PROVEN",
    "R9": "APPLICABLE",
}
domains = plan.get("domains")
if not isinstance(domains, dict):
    fail("assurance plan domains missing")
for domain, status in expected_domain_status.items():
    row = domains.get(domain)
    if not isinstance(row, dict) or row.get("status") != status:
        fail(f"{domain} classification mismatch")
    if status == "APPLICABLE" and not row.get("methodsApplicable"):
        fail(f"{domain} applicable methods missing")
    if status == "N_A_SCOPE_PROVEN" and not row.get("reason"):
        fail(f"{domain} N/A reason missing")

source_files = sorted(SOURCE_ROOT.rglob("*.java"))
if len(source_files) != 5:
    fail(f"expected 5 Public main Java files, found {len(source_files)}")

required_names = {
    "SafetyContracts.java",
    "DiagnosticBuffer.java",
    "ResourceGuard.java",
    "RecoveryMachine.java",
    "RuntimeSafetySimulator.java",
}
if {path.name for path in source_files} != required_names:
    fail("main source universe mismatch")

forbidden_tokens = (
    "android.app.",
    "android.content.",
    "android.os.",
    "androidx.",
    "java.net.",
    "java.nio.file.",
    "java.io.File",
    "FileInputStream",
    "FileOutputStream",
    "ProcessBuilder",
    "Runtime.getRuntime",
    "Class.forName",
    "ClassLoader",
    "DexClassLoader",
    "PathClassLoader",
    "System.load",
    "System.loadLibrary",
    "com.google.firebase",
    "TOOLBOX_SOURCE_TOKEN",
    "SIGNING_KEY",
    "TOOLBOX_KEYSTORE",
)
for source in source_files:
    text = source.read_text(encoding="utf-8")
    for token in forbidden_tokens:
        if token in text:
            fail(f"forbidden authority token {token!r} in {source.relative_to(ROOT)}")

# R2/R3 architectural witnesses: the two mutable components must explicitly serialize state access.
diagnostic_text = (SOURCE_ROOT / "io/toolbox/contracts/safety/DiagnosticBuffer.java").read_text(encoding="utf-8")
recovery_text = (SOURCE_ROOT / "io/toolbox/contracts/safety/RecoveryMachine.java").read_text(encoding="utf-8")
for marker in ("public synchronized void record", "public synchronized List", "public synchronized long droppedCount"):
    if marker not in diagnostic_text:
        fail(f"diagnostic synchronization witness missing: {marker}")
for marker in ("public synchronized SafetyContracts.RecoveryState state", "public synchronized SafetyContracts.Transition apply"):
    if marker not in recovery_text:
        fail(f"recovery synchronization witness missing: {marker}")

# R3 must remain a model only; no durable recovery implementation may enter the Public component.
for token in ("checkpoint", "rollback", "restoreFile", "database", "DataStore"):
    for source in source_files:
        if token in source.read_text(encoding="utf-8") and source.name != "RecoveryMachine.java":
            fail(f"unexpected durable-recovery token {token!r} in {source.name}")

STATE.mkdir(parents=True, exist_ok=True)
payload = {
    "schemaVersion": 1,
    "status": "PASS",
    "gate": "R1_R8_PREBUILD_SCOPE_CLASSIFICATION",
    "projectId": expected["projectId"],
    "componentId": expected["componentId"],
    "componentVersion": expected["componentVersion"],
    "contractId": expected["contractId"],
    "contractVersion": expected["contractVersion"],
    "sourceRepository": os.environ.get("GITHUB_REPOSITORY", "LOCAL_NOT_GITHUB"),
    "sourceCommitSha": os.environ.get("GITHUB_SHA", "LOCAL_NOT_GITHUB"),
    "workflowRunId": os.environ.get("GITHUB_RUN_ID", "LOCAL_NOT_GITHUB"),
    "domainStatus": expected_domain_status,
    "inputsSha256": {
        "COMPONENT_SPEC.json": sha256(SPEC),
        "CONTRACT.json": sha256(CONTRACT),
        "ASSURANCE_PLAN_R1_R9.json": sha256(PLAN),
    },
    "sourceSha256": {str(path.relative_to(ROOT)): sha256(path) for path in source_files},
    "externalRuntimeDependencies": 0,
    "privateContentIncluded": False,
    "publicFirebaseAccess": False,
    "unknown": 0,
    "skipped": 0,
    "notProven": 0,
}
OUT.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
print("R1_R8_PREBUILD_SCOPE_CLASSIFICATION = PASS")
print("PUBLIC_BOUNDARY = PASS")
print("PRIVATE_CONTENT_INCLUDED=0")
print("PUBLIC_FIREBASE_ACCESS=0")
