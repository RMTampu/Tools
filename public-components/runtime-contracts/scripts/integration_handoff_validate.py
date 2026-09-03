#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BUILD = ROOT / "build"
REQ = ROOT / "PRIVATE_INTEGRATION_REQUIREMENTS.json"
REPORT = ROOT / "SANITIZED_PRIVATE_FAILURE_2026-09-03.md"
EVIDENCE = BUILD / "integration-handoff-evidence.json"


def fail(message: str) -> None:
    raise SystemExit(f"PRIVATE_INTEGRATION_HANDOFF_FAIL: {message}")


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


if not REQ.is_file() or not REPORT.is_file():
    fail("required sanitized handoff files missing")

data = json.loads(REQ.read_text(encoding="utf-8"))
expected = {
    "projectId": "ToolBox",
    "componentId": "public.runtime-contracts",
    "componentVersion": "0.1.0",
    "contractId": "toolbox.runtime.metadata",
    "contractVersion": "1.0.0",
    "integrationBoundary": "PRIVATE_HOST_ONLY",
    "runtimeModel": "METADATA_ONLY",
    "javaRelease": 11,
    "externalRuntimeDependencies": 0,
}
for key, value in expected.items():
    if data.get(key) != value:
        fail(f"{key} mismatch")

host = data.get("hostRequirements", {})
for key in (
    "dependencyTrustInputUniverseRefreshRequired",
    "dependencyTrustHashEvidenceRefreshRequired",
    "refreshBeforeRegressionOrBuild",
    "failClosedIfTrustStateNotProven",
    "privateHostOwnsTrustManifest",
    "publicMustNotKnowPrivateInputList",
):
    if host.get(key) is not True:
        fail(f"host requirement not locked: {key}")

report = REPORT.read_text(encoding="utf-8")
for marker in (
    "PRIVATE_R6_DEPENDENCY_TRUST_HANDOFF_GAP",
    "PRIVATE_CONTENT_INCLUDED = 0",
    "PRIVATE_SOURCE_INCLUDED = 0",
    "PRIVATE_PATH_INCLUDED = 0",
    "PRIVATE_ARTIFACT_INCLUDED = 0",
):
    if marker not in report:
        fail(f"sanitized report marker missing: {marker}")

# Public evidence must never contain a Private source path, token, secret, APK, or dump.
for forbidden in (
    "toolbox-app/build.gradle.kts",
    "settings.gradle.kts",
    "TOOLBOX_KEYSTORE",
    "TOOLBOX_KEY_PASSWORD",
    ".apk",
    "database/state dump",
):
    if forbidden in report:
        fail(f"sanitized report contains forbidden private detail: {forbidden}")

BUILD.mkdir(parents=True, exist_ok=True)
payload = {
    "status": "PASS",
    "claim": "PRIVATE_INTEGRATION_HANDOFF_CONTRACT",
    "sourceRepository": "RMTampu/Tools",
    "sourceCommitSha": os.environ.get("GITHUB_SHA", "LOCAL_NOT_GITHUB"),
    "workflowRunId": os.environ.get("GITHUB_RUN_ID", "LOCAL_NOT_GITHUB"),
    "requirementsSha256": sha256(REQ),
    "sanitizedFailureReportSha256": sha256(REPORT),
    "privateContentIncluded": False,
    "privateTrustManifestOwnedByPublic": False,
}
EVIDENCE.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
print("PRIVATE_INTEGRATION_HANDOFF = PASS")
print("PRIVATE_TRUST_MANIFEST_KNOWN_TO_PUBLIC = 0")
