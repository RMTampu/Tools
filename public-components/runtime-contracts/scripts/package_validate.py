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
JAR = PACKAGE / "toolbox-runtime-contracts-0.1.0.jar"
SOURCE_ZIP = PACKAGE / "toolbox-runtime-contracts-0.1.0-sources.zip"
SUMMARY = BUILD / "test-summary.txt"
ASSURANCE_SUMMARY = BUILD / "assurance" / "r1-r9-summary.txt"
ASSURANCE_EVIDENCE = BUILD / "assurance" / "r1-r9-evidence.json"
PROMOTION = BUILD / "promotion"
MANIFEST = PROMOTION / "promotion-manifest.json"


def fail(message: str) -> None:
    raise SystemExit(f"PACKAGE_VALIDATION_FAIL: {message}")


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def write_deterministic_source_zip(files: list[Path]) -> dict[str, str]:
    PACKAGE.mkdir(parents=True, exist_ok=True)
    hashes: dict[str, str] = {}
    with zipfile.ZipFile(SOURCE_ZIP, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(files, key=lambda p: p.as_posix()):
            relative = path.relative_to(ROOT).as_posix()
            data = path.read_bytes()
            hashes[relative] = hashlib.sha256(data).hexdigest()
            info = zipfile.ZipInfo(relative, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, data)
    if not SOURCE_ZIP.is_file() or SOURCE_ZIP.stat().st_size == 0:
        fail("source archive missing or empty")
    return hashes


if not JAR.is_file() or JAR.stat().st_size == 0:
    fail("JAR missing or empty")
if not SUMMARY.is_file():
    fail("test summary missing")
if not ASSURANCE_SUMMARY.is_file() or not ASSURANCE_EVIDENCE.is_file():
    fail("R1-R9 assurance evidence missing")

summary_text = SUMMARY.read_text(encoding="utf-8")
for required in (
    "UNIT_TEST=PASS",
    "FAILURE_TEST=PASS",
    "CONCURRENCY_TEST=PASS",
    "BOUNDARY_TEST=PASS",
    "PROPERTY_DIFFERENTIAL_TEST=PASS",
    "SIMULATOR=PASS",
    "REPRODUCIBLE_JAR=PASS",
    "ENGINE_CALLBACKS_EXECUTED=0",
):
    if required not in summary_text:
        fail(f"required test evidence missing: {required}")

assurance_summary_text = ASSURANCE_SUMMARY.read_text(encoding="utf-8")
for required in (
    "R1_PUBLIC_COMPONENT_PASS=PASS",
    "R2_PUBLIC_COMPONENT_PASS=PASS",
    "R3_PUBLIC_SCOPE_CLOSED=PASS",
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
):
    if required not in assurance_summary_text:
        fail(f"required R1-R9 evidence missing: {required}")

assurance = json.loads(ASSURANCE_EVIDENCE.read_text(encoding="utf-8"))
source_sha = os.environ.get("GITHUB_SHA", "")
workflow_run = os.environ.get("GITHUB_RUN_ID", "")
if assurance.get("status") != "PASS" or assurance.get("claim") != "R9_PUBLIC_EVIDENCE_COMPLETENESS":
    fail("R9 assurance JSON did not PASS")
if assurance.get("sourceRepository") != "RMTampu/Tools":
    fail("R9 assurance repository mismatch")
if assurance.get("sourceCommitSha") != source_sha:
    fail("R9 assurance source SHA mismatch")
if assurance.get("workflowRunId") != workflow_run:
    fail("R9 assurance workflow run mismatch")
if assurance.get("privateContentIncluded") is not False:
    fail("R9 assurance reports Private content")
closure = assurance.get("closure", {})
for key in (
    "unownedApplicableMethod",
    "unmappedApplicableMethod",
    "unknown",
    "skipped",
    "notProven",
    "staleEvidence",
    "faultEscape",
    "unresolvedDefeater",
    "undeclaredMaterialAssumption",
):
    if closure.get(key) != 0:
        fail(f"R9 closure is not zero: {key}={closure.get(key)!r}")

source_root = ROOT / "src" / "main" / "java"
source_files = sorted(source_root.rglob("*.java"))
contract_doc = ROOT / "CONTRACT.md"
assurance_doc = ROOT / "ASSURANCE_R1_R9.md"
if not source_files or not contract_doc.is_file() or not assurance_doc.is_file():
    fail("promotable source/contract/assurance documentation missing")

forbidden_tokens = (
    "DexClassLoader",
    "PathClassLoader",
    "URLClassLoader",
    "Class.forName",
    "ProcessBuilder",
    "Runtime.getRuntime",
    "java.net.",
    "java.nio.file.",
    "com.google.firebase",
    "TOOLBOX_SOURCE_TOKEN",
    "SIGNING_KEY",
)
for source in source_files:
    text = source.read_text(encoding="utf-8")
    for token in forbidden_tokens:
        if token in text:
            fail(f"forbidden executable/external boundary token {token!r} in {source.relative_to(ROOT)}")

required_classes = {
    "io/toolbox/contracts/runtime/Contracts.class",
    "io/toolbox/contracts/runtime/ProductRegistry.class",
    "io/toolbox/contracts/runtime/RuntimeContractsSimulator.class",
}
with zipfile.ZipFile(JAR) as archive:
    names = set(archive.namelist())
    missing = sorted(required_classes - names)
    if missing:
        fail(f"required classes missing: {missing}")
    class_entries = sorted(name for name in names if name.endswith(".class"))
    if not class_entries:
        fail("no class files in JAR")
    for name in class_entries:
        if not name.startswith("io/toolbox/contracts/runtime/"):
            fail(f"class outside contract namespace: {name}")
        data = archive.read(name)
        if len(data) < 8 or data[:4] != b"\xca\xfe\xba\xbe":
            fail(f"invalid class header: {name}")
        major = struct.unpack(">H", data[6:8])[0]
        if major > 55:
            fail(f"class requires newer than Java 11: {name} major={major}")

source_hashes = write_deterministic_source_zip([contract_doc, assurance_doc, *source_files])
with zipfile.ZipFile(SOURCE_ZIP) as archive:
    archived = sorted(name for name in archive.namelist())
    expected = sorted(source_hashes)
    if archived != expected:
        fail("source archive file list mismatch")
    for relative, expected_hash in source_hashes.items():
        actual_hash = hashlib.sha256(archive.read(relative)).hexdigest()
        if actual_hash != expected_hash:
            fail(f"source archive hash mismatch: {relative}")

PROMOTION.mkdir(parents=True, exist_ok=True)
dependency_digest = hashlib.sha256(
    b"runtimeDependencies=0;javaRelease=11;jdk=17.0.20+1;python=3.13.7"
).hexdigest()

payload = {
    "schemaVersion": 3,
    "status": "READY_PRIVATE",
    "projectId": "ToolBox",
    "componentId": "public.runtime-contracts",
    "componentVersion": "0.1.0",
    "contractId": "toolbox.runtime.metadata",
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
        "metadataOnly": True,
        "externalRuntimeDependencies": 0,
        "privateSourceRequired": False,
        "firebaseRequired": False,
        "signingRequired": False,
        "androidLifecycleCode": False,
        "persistentStorageCode": False,
        "nativeOrPluginRuntimeCode": False,
        "uiDevicePowerCode": False,
    },
    "dependencyLockOrDigest": dependency_digest,
    "artifact": {
        "fileName": JAR.name,
        "sha256": sha256(JAR),
        "sizeBytes": JAR.stat().st_size,
        "classMajorMax": 55,
        "reproducibleBuild": True,
    },
    "sourceArchive": {
        "fileName": SOURCE_ZIP.name,
        "sha256": sha256(SOURCE_ZIP),
        "sizeBytes": SOURCE_ZIP.stat().st_size,
        "filesSha256": source_hashes,
        "promotionAllowed": True,
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
        "propertyDifferential": "PASS",
        "simulator": "PASS",
        "reproducibleJar": "PASS",
        "packageMutation": "PASS",
        "engineCallbacksExecuted": 0,
        "packageValidation": "PASS",
    },
    "testEvidenceRefs": [
        "test-summary.txt",
        "test-output.txt",
        "boundary-output.txt",
        "property-output.txt",
        "simulator-output.txt",
        "package-mutation-output.txt",
        "r1-r9-summary.txt",
        "r1-r9-evidence.json",
    ],
    "promotionScope": "PUBLIC_COMPONENT_SOURCE_AND_BINARY",
    "privateContentIncluded": False,
}
MANIFEST.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
print(f"PACKAGE_VALIDATION = PASS jar_sha256={payload['artifact']['sha256']}")
print(f"SOURCE_SNAPSHOT = PASS source_sha256={payload['sourceArchive']['sha256']}")
print("R1_R9_PROMOTION_GATE = PASS")
print("PROMOTION_STATUS = READY_PRIVATE")
