#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REPO = ROOT.parent.parent
BUILD = ROOT / "build"
REQ = ROOT / "PRIVATE_INTEGRATION_REQUIREMENTS.json"
REPORT = ROOT / "SANITIZED_PRIVATE_FAILURE_2026-09-03.md"
ADDENDUM = ROOT / "R6_PRIVATE_HANDOFF_ASSURANCE_ADDENDUM.md"
WORKFLOW = REPO / ".github" / "workflows" / "runtime-contracts-ci.yml"
EVIDENCE = BUILD / "integration-handoff-evidence.json"


def fail(message: str) -> None:
    raise SystemExit(f"PRIVATE_INTEGRATION_HANDOFF_FAIL: {message}")


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


for path in (REQ, REPORT, ADDENDUM, WORKFLOW):
    if not path.is_file():
        fail(f"required sanitized handoff file missing: {path.name}")

data = json.loads(REQ.read_text(encoding="utf-8"))
expected = {
    "schemaVersion": 2,
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
    "toolchainArtifactSourceParityRequired",
    "trustGenerationAndRegressionSourceParityRequired",
    "alternateBuildToolArtifactsMustBeBoundBeforeRegression",
    "regressionMustFailClosedOnUnboundBuildToolArtifact",
):
    if host.get(key) is not True:
        fail(f"host requirement not locked: {key}")

strategies = data.get("allowedPrivateResolutionStrategies", [])
if not isinstance(strategies, list) or len(strategies) != 2:
    fail("Private resolution strategy boundary is not explicit")

report = REPORT.read_text(encoding="utf-8")
for marker in (
    "PRIVATE_R6_DEPENDENCY_TRUST_HANDOFF_GAP",
    "PRIVATE_R6_TOOLCHAIN_ARTIFACT_SOURCE_MISMATCH",
    "TRUST_GENERATION_ARTIFACT_SOURCE != REGRESSION_ARTIFACT_SOURCE",
    "UNBOUND_BUILD_TOOL_ARTIFACT_REQUESTED_DURING_REGRESSION",
    "PRIVATE_CONTENT_INCLUDED = 0",
    "PRIVATE_SOURCE_INCLUDED = 0",
    "PRIVATE_PATH_INCLUDED = 0",
    "PRIVATE_ARTIFACT_INCLUDED = 0",
):
    if marker not in report:
        fail(f"sanitized report marker missing: {marker}")

addendum = ADDENDUM.read_text(encoding="utf-8")
for marker in (
    "DEPENDENCY_BUILD_INPUT_TRUST_STALE",
    "TOOLCHAIN_ARTIFACT_SOURCE_PHASE_MISMATCH",
    "TOOLCHAIN_ARTIFACT_SOURCE_PARITY_REQUIRED = PASS",
    "ALTERNATE_ARTIFACT_PRIVATE_TRUST_BINDING_REQUIRED = PASS",
    "UNBOUND_REGRESSION_ARTIFACT_FAIL_CLOSED = PASS",
    "R6_HANDOFF_ASSURANCE = PASS",
    "R9_HANDOFF_COMPLETENESS = PASS",
    "PRIVATE_CONTENT_INCLUDED = 0",
):
    if marker not in addendum:
        fail(f"R6/R9 handoff addendum marker missing: {marker}")

# Public evidence must never contain concrete Private implementation details.
for text_name, text in (("report", report), ("addendum", addendum)):
    for forbidden in (
        "toolbox-app/build.gradle.kts",
        "settings.gradle.kts",
        "detachedConfiguration",
        "com.android.tools.build:aapt2",
        "aapt2-8.",
        "TOOLBOX_KEYSTORE",
        "TOOLBOX_KEY_PASSWORD",
        ".apk",
        "database/state dump",
    ):
        if forbidden in text:
            fail(f"{text_name} contains forbidden Private detail: {forbidden}")

workflow = WORKFLOW.read_text(encoding="utf-8")
ordered_steps = (
    "Compile, unit/failure/concurrency/boundary/property test, simulator",
    "Validate sanitized Private integration handoff",
    "R6 package mutation challenge",
    "R9 Public evidence completeness gate",
    "Validate Promotion Package",
    "Bind sanitized integration handoff to Promotion Manifest",
    "Verify READY_PRIVATE manifest binding",
)
positions = []
for marker in ordered_steps:
    position = workflow.find(marker)
    if position < 0:
        fail(f"workflow handoff gate missing: {marker}")
    positions.append(position)
if positions != sorted(positions) or len(set(positions)) != len(positions):
    fail("workflow handoff gate ordering invalid")

BUILD.mkdir(parents=True, exist_ok=True)
payload = {
    "status": "PASS",
    "claim": "PRIVATE_INTEGRATION_HANDOFF_CONTRACT",
    "sourceRepository": "RMTampu/Tools",
    "sourceCommitSha": os.environ.get("GITHUB_SHA", "LOCAL_NOT_GITHUB"),
    "workflowRunId": os.environ.get("GITHUB_RUN_ID", "LOCAL_NOT_GITHUB"),
    "requirementsSha256": sha256(REQ),
    "sanitizedFailureReportSha256": sha256(REPORT),
    "r6R9HandoffAddendumSha256": sha256(ADDENDUM),
    "failureClasses": [
        "PRIVATE_R6_DEPENDENCY_TRUST_HANDOFF_GAP",
        "PRIVATE_R6_TOOLCHAIN_ARTIFACT_SOURCE_MISMATCH",
    ],
    "phaseArtifactSourceParityRequired": True,
    "alternateArtifactsRequirePrivateTrustBinding": True,
    "unboundRegressionArtifactFailsClosed": True,
    "r6HandoffAssurance": "PASS",
    "r9HandoffCompleteness": "PASS",
    "privateContentIncluded": False,
    "privateTrustManifestOwnedByPublic": False,
}
EVIDENCE.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
print("PRIVATE_INTEGRATION_HANDOFF = PASS")
print("R6_HANDOFF_ASSURANCE = PASS")
print("R9_HANDOFF_COMPLETENESS = PASS")
print("TOOLCHAIN_ARTIFACT_SOURCE_PARITY_REQUIRED = PASS")
print("PRIVATE_TRUST_MANIFEST_KNOWN_TO_PUBLIC = 0")
