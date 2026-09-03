#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import struct
import zipfile

ROOT = Path(__file__).resolve().parent.parent
BUILD = ROOT / "build"
PACKAGE = BUILD / "package"
JAR = PACKAGE / "toolbox-runtime-safety-contracts-0.1.0.jar"
SOURCE_ZIP = PACKAGE / "toolbox-runtime-safety-contracts-0.1.0-production-sources.zip"
SUMMARY = BUILD / "test-summary.txt"
MUTATION = BUILD / "mutation-output.txt"
ASSURANCE_SUMMARY = BUILD / "assurance" / "r1-r9-summary.txt"
ASSURANCE_EVIDENCE = BUILD / "assurance" / "r1-r9-evidence.json"
PROMOTION = BUILD / "promotion"
MANIFEST = PROMOTION / "promotion-manifest.json"
HANDOFF = ROOT / "PRIVATE_INTEGRATION_REQUIREMENTS.json"


def fail(message: str) -> None:
    raise SystemExit(f"PACKAGE_VALIDATION_FAIL: {message}")


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def deterministic_zip(files: list[Path]) -> dict[str, str]:
    PACKAGE.mkdir(parents=True, exist_ok=True)
    hashes: dict[str, str] = {}
    with zipfile.ZipFile(SOURCE_ZIP, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(files, key=lambda value: value.as_posix()):
            relative = path.relative_to(ROOT).as_posix()
            data = path.read_bytes()
            hashes[relative] = hashlib.sha256(data).hexdigest()
            info = zipfile.ZipInfo(relative, date_time=(1980, 1, 1, 0, 0, 2))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, data)
    return hashes


for path in (JAR, SUMMARY, MUTATION, ASSURANCE_SUMMARY, ASSURANCE_EVIDENCE, HANDOFF):
    if not path.is_file() or path.stat().st_size == 0:
        fail(f"required package input missing: {path.name}")

summary_text = SUMMARY.read_text(encoding="utf-8")
for marker in (
    "UNIT_TEST=PASS",
    "FAILURE_TEST=PASS",
    "CONCURRENCY_TEST=PASS",
    "BOUNDARY_TEST=PASS",
    "RECOVERY_EXHAUSTIVE_TEST=PASS",
    "RESOURCE_DIFFERENTIAL_TEST=PASS",
    "METAMORPHIC_TEST=PASS",
    "SIMULATOR=PASS",
    "REPRODUCIBLE_JAR=PASS",
    "PERSISTENT_WRITES=0",
    "NETWORK_CALLS=0",
    "PLUGIN_LOADS=0",
    "UI_DEVICE_CALLS=0",
    "FIREBASE_USED=0",
):
    if marker not in summary_text:
        fail(f"test evidence missing: {marker}")

mutation_text = MUTATION.read_text(encoding="utf-8")
for marker in (
    "R1_R2_R5_SOURCE_MUTATION = PASS",
    "SOURCE_MUTATIONS_KILLED=4",
    "R6_PACKAGE_MUTATION = PASS",
    "PACKAGE_MUTATIONS_KILLED=3",
    "MUTATIONS_KILLED=7",
):
    if marker not in mutation_text:
        fail(f"mutation evidence missing: {marker}")

assurance_summary_text = ASSURANCE_SUMMARY.read_text(encoding="utf-8")
for marker in (
    "R1_PUBLIC_COMPONENT_PASS=PASS",
    "R2_PUBLIC_COMPONENT_PASS=PASS",
    "R3_PUBLIC_COMPONENT_PASS=PASS",
    "R4_PUBLIC_SCOPE_CLOSED=PASS",
    "R5_PUBLIC_COMPONENT_PASS=PASS",
    "R6_PUBLIC_COMPONENT_PASS=PASS",
    "R7_PUBLIC_SCOPE_CLOSED=PASS",
    "R8_PUBLIC_SCOPE_CLOSED=PASS",
    "R9_PUBLIC_EVIDENCE_COMPLETENESS=PASS",
    "R1_R9_PUBLIC_ASSURANCE=PASS",
    "UNKNOWN=0",
    "SKIPPED=0",
    "NOT_PROVEN=0",
    "STALE_EVIDENCE=0",
    "FAULT_ESCAPE=0",
    "PUBLIC_FINAL_APPLICATION_SAFE_100=0",
    "PRIVATE_CONTENT_INCLUDED=0",
    "FIREBASE_USED=0",
):
    if marker not in assurance_summary_text:
        fail(f"assurance summary missing: {marker}")

source_sha = os.environ.get("GITHUB_SHA", "")
workflow_run = os.environ.get("GITHUB_RUN_ID", "")
assurance = json.loads(ASSURANCE_EVIDENCE.read_text(encoding="utf-8"))
if assurance.get("status") != "PASS" or assurance.get("claim") != "R9_PUBLIC_EVIDENCE_COMPLETENESS":
    fail("R9 assurance did not PASS")
if assurance.get("sourceRepository") != "RMTampu/Tools":
    fail("R9 repository mismatch")
if assurance.get("sourceCommitSha") != source_sha or assurance.get("workflowRunId") != workflow_run:
    fail("R9 evidence not bound to current workflow revision")
if assurance.get("privateContentIncluded") is not False or assurance.get("firebaseUsed") is not False:
    fail("R9 boundary violation")
for key, value in assurance.get("closure", {}).items():
    if value != 0:
        fail(f"R9 closure is not zero: {key}={value}")

handoff = json.loads(HANDOFF.read_text(encoding="utf-8"))
if handoff.get("integrationBoundary") != "PRIVATE_HOST_ONLY":
    fail("Private integration boundary missing")
if handoff.get("privateContentRequired") is not False or handoff.get("firebaseRequired") is not False:
    fail("handoff boundary mismatch")
production_allowlist = handoff.get("productionPromotionAllowlist")
expected_allowlist = [
    "src/main/java/io/toolbox/contracts/safety/SafetyContracts.java",
    "src/main/java/io/toolbox/contracts/safety/DiagnosticBuffer.java",
    "src/main/java/io/toolbox/contracts/safety/ResourceGuard.java",
    "src/main/java/io/toolbox/contracts/safety/RecoveryMachine.java",
]
if production_allowlist != expected_allowlist:
    fail("production source allowlist mismatch")
if handoff.get("simulatorOnly") != ["src/main/java/io/toolbox/contracts/safety/RuntimeSafetySimulator.java"]:
    fail("simulator-only classification mismatch")

required_classes = {
    "io/toolbox/contracts/safety/SafetyContracts.class",
    "io/toolbox/contracts/safety/DiagnosticBuffer.class",
    "io/toolbox/contracts/safety/ResourceGuard.class",
    "io/toolbox/contracts/safety/RecoveryMachine.class",
    "io/toolbox/contracts/safety/RuntimeSafetySimulator.class",
}
max_major = 0
with zipfile.ZipFile(JAR) as archive:
    names = set(archive.namelist())
    missing = sorted(required_classes - names)
    if missing:
        fail(f"required classes missing: {missing}")
    class_entries = sorted(name for name in names if name.endswith(".class"))
    if not class_entries:
        fail("JAR contains no classes")
    for name in class_entries:
        if not name.startswith("io/toolbox/contracts/safety/"):
            fail(f"class outside safety namespace: {name}")
        data = archive.read(name)
        if len(data) < 8 or data[:4] != b"\xca\xfe\xba\xbe":
            fail(f"invalid class header: {name}")
        major = struct.unpack(">H", data[6:8])[0]
        max_major = max(max_major, major)
        if major > 55:
            fail(f"class newer than Java 11: {name} major={major}")

all_main_sources = sorted((ROOT / "src/main/java").rglob("*.java"))
forbidden_tokens = (
    "android.app.",
    "android.content.",
    "android.os.",
    "androidx.",
    "java.net.",
    "java.nio.file.",
    "ProcessBuilder",
    "Runtime.getRuntime",
    "Class.forName",
    "DexClassLoader",
    "PathClassLoader",
    "com.google.firebase",
    "TOOLBOX_SOURCE_TOKEN",
    "SIGNING_KEY",
    "TOOLBOX_KEYSTORE",
)
for source in all_main_sources:
    value = source.read_text(encoding="utf-8")
    for token in forbidden_tokens:
        if token in value:
            fail(f"forbidden authority token {token!r} in {source.relative_to(ROOT)}")

production_files = [ROOT / path for path in expected_allowlist]
document_files = [
    ROOT / "COMPONENT_SPEC.json",
    ROOT / "CONTRACT.json",
    ROOT / "ASSURANCE_PLAN_R1_R9.json",
    HANDOFF,
]
for path in [*production_files, *document_files]:
    if not path.is_file():
        fail(f"production promotion input missing: {path.relative_to(ROOT)}")
source_hashes = deterministic_zip([*document_files, *production_files])
with zipfile.ZipFile(SOURCE_ZIP) as archive:
    if sorted(archive.namelist()) != sorted(source_hashes):
        fail("production source archive file universe mismatch")
    if "src/main/java/io/toolbox/contracts/safety/RuntimeSafetySimulator.java" in archive.namelist():
        fail("simulator source leaked into production source archive")
    for relative, expected_hash in source_hashes.items():
        if hashlib.sha256(archive.read(relative)).hexdigest() != expected_hash:
            fail(f"production source archive hash mismatch: {relative}")

PROMOTION.mkdir(parents=True, exist_ok=True)
dependency_digest = hashlib.sha256(
    b"runtimeDependencies=0;javaRelease=11;jdk=17.0.20+1;python=3.13.7"
).hexdigest()

payload = {
    "schemaVersion": 1,
    "status": "READY_PRIVATE",
    "projectId": "ToolBox",
    "componentId": "public.runtime-safety-contracts",
    "componentVersion": "0.1.0",
    "contractId": "toolbox.runtime.safety",
    "contractVersion": "1.0.0",
    "sourceRepository": "RMTampu/Tools",
    "sourceCommitSha": source_sha,
    "workflowRunId": workflow_run,
    "targetPlatform": {
        "productTarget": "Android 11 / API 30",
        "javaRelease": 11,
        "abiSpecificCode": False,
        "publicTargetRuntimeWitnessClaimed": False,
    },
    "toolchain": {
        "runner": "ubuntu-24.04",
        "temurinJdk": "17.0.20+1",
        "python": "3.13.7",
    },
    "compatibility": {
        "externalRuntimeDependencies": 0,
        "privateSourceRequired": False,
        "firebaseRequired": False,
        "signingRequired": False,
        "persistentStorageCode": False,
        "nativeOrPluginRuntimeCode": False,
        "uiDevicePowerCode": False,
        "durableRecoveryProvided": False,
    },
    "dependencyLockOrDigest": dependency_digest,
    "verificationJar": {
        "fileName": JAR.name,
        "sha256": sha256(JAR),
        "sizeBytes": JAR.stat().st_size,
        "classMajorMax": max_major,
        "reproducibleBuild": True,
        "privateProductionUseAllowed": False,
        "reason": "contains Public simulator; Private production promotion uses allowlisted source archive",
    },
    "productionSourceArchive": {
        "fileName": SOURCE_ZIP.name,
        "sha256": sha256(SOURCE_ZIP),
        "sizeBytes": SOURCE_ZIP.stat().st_size,
        "filesSha256": source_hashes,
        "promotionAllowed": True,
        "productionSourceAllowlist": expected_allowlist,
        "simulatorSourceIncluded": False,
    },
    "privateIntegrationRequirements": {
        "fileName": HANDOFF.name,
        "sha256": sha256(HANDOFF),
    },
    "assurance": {
        "framework": "APPLICATION_SAFE_100 Public component scope",
        "r1ToR9PublicAssurance": "PASS",
        "r9EvidenceCompleteness": "PASS",
        "evidenceFile": ASSURANCE_EVIDENCE.name,
        "evidenceSha256": sha256(ASSURANCE_EVIDENCE),
        "summaryFile": ASSURANCE_SUMMARY.name,
        "summarySha256": sha256(ASSURANCE_SUMMARY),
        "finalApplicationSafe100Claimed": False,
    },
    "testStatus": {
        "unit": "PASS",
        "failure": "PASS",
        "concurrency": "PASS",
        "boundary": "PASS",
        "recoveryExhaustive": "PASS",
        "resourceDifferential": "PASS",
        "metamorphic": "PASS",
        "simulator": "PASS",
        "reproducibleJar": "PASS",
        "sourceMutation": "PASS",
        "packageMutation": "PASS",
        "mutationsKilled": 7,
        "packageValidation": "PASS",
    },
    "publicBoundaries": {
        "privateContentIncluded": False,
        "privateExecutionThroughPublic": False,
        "firebaseUsed": False,
        "finalApplicationBuild": False,
        "finalSigning": False,
    },
    "promotionScope": "PUBLIC_COMPONENT_PRODUCTION_SOURCE",
}
MANIFEST.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
print(f"PACKAGE_VALIDATION = PASS jar_sha256={payload['verificationJar']['sha256']}")
print(f"PRODUCTION_SOURCE_SNAPSHOT = PASS source_sha256={payload['productionSourceArchive']['sha256']}")
print("SIMULATOR_IN_PRODUCTION_SOURCE_ARCHIVE=0")
print("R1_R9_PROMOTION_GATE = PASS")
print("PROMOTION_STATUS = READY_PRIVATE")
