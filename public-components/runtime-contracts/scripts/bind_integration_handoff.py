#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BUILD = ROOT / "build"
MANIFEST = BUILD / "promotion" / "promotion-manifest.json"
EVIDENCE = BUILD / "integration-handoff-evidence.json"
REQ = ROOT / "PRIVATE_INTEGRATION_REQUIREMENTS.json"
REPORT = ROOT / "SANITIZED_PRIVATE_FAILURE_2026-09-03.md"
ADDENDUM = ROOT / "R6_PRIVATE_HANDOFF_ASSURANCE_ADDENDUM.md"


def fail(message: str) -> None:
    raise SystemExit(f"PRIVATE_INTEGRATION_HANDOFF_BIND_FAIL: {message}")


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


for path in (MANIFEST, EVIDENCE, REQ, REPORT, ADDENDUM):
    if not path.is_file():
        fail(f"required binding input missing: {path.name}")

manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
evidence = json.loads(EVIDENCE.read_text(encoding="utf-8"))
source_sha = os.environ.get("GITHUB_SHA", "")
run_id = os.environ.get("GITHUB_RUN_ID", "")

if manifest.get("status") != "READY_PRIVATE":
    fail("promotion manifest is not READY_PRIVATE")
if manifest.get("sourceRepository") != "RMTampu/Tools":
    fail("promotion repository mismatch")
if manifest.get("sourceCommitSha") != source_sha or manifest.get("workflowRunId") != run_id:
    fail("promotion provenance mismatch")
if evidence.get("status") != "PASS" or evidence.get("claim") != "PRIVATE_INTEGRATION_HANDOFF_CONTRACT":
    fail("handoff evidence did not PASS")
if evidence.get("sourceCommitSha") != source_sha or evidence.get("workflowRunId") != run_id:
    fail("handoff evidence provenance mismatch")
for key in (
    "phaseArtifactSourceParityRequired",
    "alternateArtifactsRequirePrivateTrustBinding",
    "unboundRegressionArtifactFailsClosed",
):
    if evidence.get(key) is not True:
        fail(f"handoff evidence missing invariant: {key}")
if evidence.get("r6HandoffAssurance") != "PASS" or evidence.get("r9HandoffCompleteness") != "PASS":
    fail("R6/R9 handoff assurance incomplete")
if evidence.get("privateContentIncluded") is not False:
    fail("handoff evidence reports Private content")

manifest["integrationHandoff"] = {
    "status": "PASS",
    "claim": "PRIVATE_INTEGRATION_HANDOFF_CONTRACT",
    "requirementsFile": REQ.name,
    "requirementsSha256": sha256(REQ),
    "sanitizedFailureReportFile": REPORT.name,
    "sanitizedFailureReportSha256": sha256(REPORT),
    "r6R9HandoffAddendumFile": ADDENDUM.name,
    "r6R9HandoffAddendumSha256": sha256(ADDENDUM),
    "evidenceFile": EVIDENCE.name,
    "evidenceSha256": sha256(EVIDENCE),
    "phaseArtifactSourceParityRequired": True,
    "alternateArtifactsRequirePrivateTrustBinding": True,
    "unboundRegressionArtifactFailsClosed": True,
    "r6HandoffAssurance": "PASS",
    "r9HandoffCompleteness": "PASS",
    "privateContentIncluded": False,
}
MANIFEST.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
print("PRIVATE_INTEGRATION_HANDOFF_MANIFEST_BINDING = PASS")
print("R6_R9_HANDOFF_MANIFEST_BINDING = PASS")
