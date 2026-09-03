#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import struct
import sys
import zipfile

ROOT = Path(__file__).resolve().parent.parent
BUILD = ROOT / "build"
JAR = BUILD / "package" / "toolbox-runtime-contracts-0.1.0.jar"
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
for source in source_root.rglob("*.java"):
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

PROMOTION.mkdir(parents=True, exist_ok=True)
dependency_digest = hashlib.sha256(b"runtimeDependencies=0;javaRelease=11").hexdigest()
source_sha = os.environ.get("GITHUB_SHA", "LOCAL_NOT_GITHUB")
workflow_run = os.environ.get("GITHUB_RUN_ID", "LOCAL_NOT_GITHUB")

payload = {
    "schemaVersion": 1,
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
    "promotionScope": "PUBLIC_COMPONENT_ONLY",
    "privateContentIncluded": False,
}
MANIFEST.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
print(f"PACKAGE_VALIDATION = PASS jar_sha256={payload['artifact']['sha256']}")
print("PROMOTION_STATUS = READY_PRIVATE")
