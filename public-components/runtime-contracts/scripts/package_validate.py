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
summary_text = SUMMARY.read_text(encoding="utf-8")
for required in (
    "UNIT_TEST=PASS",
    "FAILURE_TEST=PASS",
    "CONCURRENCY_TEST=PASS",
    "SIMULATOR=PASS",
    "ENGINE_CALLBACKS_EXECUTED=0",
):
    if required not in summary_text:
        fail(f"required evidence missing: {required}")

source_root = ROOT / "src" / "main" / "java"
source_files = sorted(source_root.rglob("*.java"))
contract_doc = ROOT / "CONTRACT.md"
if not source_files or not contract_doc.is_file():
    fail("promotable source or contract documentation missing")

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

source_hashes = write_deterministic_source_zip([contract_doc, *source_files])
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
dependency_digest = hashlib.sha256(b"runtimeDependencies=0;javaRelease=11").hexdigest()
source_sha = os.environ.get("GITHUB_SHA", "LOCAL_NOT_GITHUB")
workflow_run = os.environ.get("GITHUB_RUN_ID", "LOCAL_NOT_GITHUB")

payload = {
    "schemaVersion": 2,
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
    },
    "compatibility": {
        "metadataOnly": True,
        "externalRuntimeDependencies": 0,
        "privateSourceRequired": False,
        "firebaseRequired": False,
        "signingRequired": False,
    },
    "dependencyLockOrDigest": dependency_digest,
    "artifact": {
        "fileName": JAR.name,
        "sha256": sha256(JAR),
        "sizeBytes": JAR.stat().st_size,
        "classMajorMax": 55,
    },
    "sourceArchive": {
        "fileName": SOURCE_ZIP.name,
        "sha256": sha256(SOURCE_ZIP),
        "sizeBytes": SOURCE_ZIP.stat().st_size,
        "filesSha256": source_hashes,
        "promotionAllowed": True,
    },
    "testStatus": {
        "unit": "PASS",
        "failure": "PASS",
        "concurrency": "PASS",
        "simulator": "PASS",
        "engineCallbacksExecuted": 0,
        "packageValidation": "PASS",
    },
    "testEvidenceRefs": [
        "test-summary.txt",
        "test-output.txt",
        "simulator-output.txt",
    ],
    "promotionScope": "PUBLIC_COMPONENT_SOURCE_AND_BINARY",
    "privateContentIncluded": False,
}
MANIFEST.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
print(f"PACKAGE_VALIDATION = PASS jar_sha256={payload['artifact']['sha256']}")
print(f"SOURCE_SNAPSHOT = PASS source_sha256={payload['sourceArchive']['sha256']}")
print("PROMOTION_STATUS = READY_PRIVATE")
