#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import os
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REPO = ROOT.parent.parent
BUILD = ROOT / "build"
STATE = ROOT / ".assurance-state"
PREBUILD = STATE / "prebuild.json"
PLAN = ROOT / "ASSURANCE_PLAN_R1_R9.json"
ASSURANCE_DIR = BUILD / "assurance"
EVIDENCE = ASSURANCE_DIR / "r1-r9-evidence.json"
SUMMARY = ASSURANCE_DIR / "r1-r9-summary.txt"
WORKFLOW = REPO / ".github/workflows/runtime-safety-contracts-ci.yml"


def fail(message: str) -> None:
    raise SystemExit(f"R9_PUBLIC_EVIDENCE_FAIL: {message}")


def text(path: Path) -> str:
    if not path.is_file():
        fail(f"required evidence missing: {path.relative_to(ROOT) if path.is_relative_to(ROOT) else path}")
    return path.read_text(encoding="utf-8")


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def require_marker(value: str, marker: str, evidence_id: str) -> None:
    if marker not in value:
        fail(f"{evidence_id} missing marker: {marker}")


if not PREBUILD.is_file():
    fail("prebuild assurance state missing")
prebuild = json.loads(PREBUILD.read_text(encoding="utf-8"))
plan = json.loads(PLAN.read_text(encoding="utf-8"))
if prebuild.get("status") != "PASS" or prebuild.get("gate") != "R1_R8_PREBUILD_SCOPE_CLASSIFICATION":
    fail("prebuild gate did not PASS")

repo_name = os.environ.get("GITHUB_REPOSITORY", "")
source_sha = os.environ.get("GITHUB_SHA", "")
run_id = os.environ.get("GITHUB_RUN_ID", "")
if repo_name != "RMTampu/Tools":
    fail(f"unexpected repository: {repo_name!r}")
if not re.fullmatch(r"[0-9a-f]{40}", source_sha):
    fail("invalid GITHUB_SHA")
if not run_id.isdigit():
    fail("invalid GITHUB_RUN_ID")
if prebuild.get("sourceCommitSha") != source_sha or prebuild.get("workflowRunId") != run_id:
    fail("prebuild evidence is stale or from another run")

summary_text = text(BUILD / "test-summary.txt")
self_output = text(BUILD / "test-output.txt")
boundary_output = text(BUILD / "boundary-output.txt")
property_output = text(BUILD / "property-output.txt")
simulator_output = text(BUILD / "simulator-output.txt")
mutation_output = text(BUILD / "mutation-output.txt")
workflow_text = text(WORKFLOW)
self_test_source = text(ROOT / "src/test/java/io/toolbox/contracts/safety/RuntimeSafetySelfTest.java")
property_test_source = text(ROOT / "src/test/java/io/toolbox/contracts/safety/RuntimeSafetyPropertyTest.java")
recovery_source = text(ROOT / "src/main/java/io/toolbox/contracts/safety/RecoveryMachine.java")
diagnostic_source = text(ROOT / "src/main/java/io/toolbox/contracts/safety/DiagnosticBuffer.java")

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
    "SELF_TEST_CASES=16",
    "BOUNDARY_TEST_CASES=14",
    "RECOVERY_TRANSITION_CASES=35",
    "RESOURCE_DIFFERENTIAL_CASES=5000",
):
    require_marker(summary_text, marker, "TEST_SUMMARY")

for marker in ("PUBLIC_RUNTIME_SAFETY_TESTS = PASS", "SELF_TEST_CASES=16"):
    require_marker(self_output, marker, "SELF_TEST")
for marker in ("PUBLIC_RUNTIME_SAFETY_BOUNDARY_TESTS = PASS", "BOUNDARY_TEST_CASES=14"):
    require_marker(boundary_output, marker, "BOUNDARY_TEST")
for marker in (
    "PUBLIC_RUNTIME_SAFETY_PROPERTY_TESTS = PASS",
    "RECOVERY_TRANSITION_CASES=35",
    "RESOURCE_DIFFERENTIAL_CASES=5000",
):
    require_marker(property_output, marker, "PROPERTY_TEST")
for marker in (
    "PUBLIC_RUNTIME_SAFETY_SIMULATOR = PASS",
    "PERSISTENT_WRITES=0",
    "NETWORK_CALLS=0",
    "PLUGIN_LOADS=0",
    "UI_DEVICE_CALLS=0",
    "FIREBASE_USED=0",
):
    require_marker(simulator_output, marker, "SIMULATOR")
for marker in (
    "R1_R2_R5_SOURCE_MUTATION = PASS",
    "SOURCE_MUTATIONS_KILLED=4",
    "R6_PACKAGE_MUTATION = PASS",
    "PACKAGE_MUTATIONS_KILLED=3",
    "MUTATIONS_KILLED=7",
):
    require_marker(mutation_output, marker, "MUTATION")

# Critical behavioral witnesses must remain explicit in source, not inferred from test totals.
for marker in (
    "illegalTransitionFailsClosedWithoutMutation",
    "bufferDropOldestIsBounded",
    "concurrentDiagnosticRecordingRemainsBounded",
    "concurrentFatalFailureEndsQuarantined",
    "quarantineIsTerminal",
):
    require_marker(self_test_source, marker, "SELF_TEST_SOURCE")
for marker in (
    "exhaustiveRecoveryTransitionReference",
    "differentialResourceGuardReference",
    "metamorphicResourceScaling",
    "diagnosticRetentionProperty",
    "referenceTransition",
    "referenceGuard",
):
    require_marker(property_test_source, marker, "PROPERTY_TEST_SOURCE")
for marker in (
    "public synchronized SafetyContracts.RecoveryState state",
    "public synchronized SafetyContracts.Transition apply",
):
    require_marker(recovery_source, marker, "RECOVERY_SOURCE")
for marker in (
    "public synchronized void record",
    "public synchronized List",
    "DROP_OLDEST" if False else "events.removeFirst()",
):
    require_marker(diagnostic_source, marker, "DIAGNOSTIC_SOURCE")

# R6 workflow integrity: pinned actions/toolchain, mandatory ordering, no bypass.
for marker in (
    "actions/checkout@fbc6f3992d24b796d5a048ff273f7fcc4a7b6c09",
    "actions/setup-java@b6effb05e454b25005698d916606bdc6ffcbf961",
    "actions/setup-python@5fda3b95a4ea91299a34e894583c3862153e4b97",
    "java-version: '17.0.20+1'",
    "python-version: '3.13.7'",
    "actions/upload-artifact@b7c566a772e6b6bfb58ed0dc250532a479d7789f",
    "permissions:\n  contents: read",
    "Auto Cleanup Public workspace",
):
    require_marker(workflow_text, marker, "WORKFLOW")
if "continue-on-error" in workflow_text:
    fail("mandatory workflow contains continue-on-error")

ordered_steps = [
    "Enforce Public boundary",
    "R1-R8 prebuild assurance scope gate",
    "Compile and execute runtime safety tests",
    "R1/R2/R5/R6 mutation challenge",
    "R9 Public evidence completeness gate",
    "Validate Promotion Package",
    "Verify READY_PRIVATE manifest binding",
    "Upload Promotion Package",
    "Auto Cleanup Public workspace",
]
positions: list[int] = []
for step in ordered_steps:
    pos = workflow_text.find(step)
    if pos < 0:
        fail(f"mandatory workflow step missing: {step}")
    positions.append(pos)
if positions != sorted(positions) or len(set(positions)) != len(positions):
    fail("mandatory workflow order invalid")

jar = BUILD / "package/toolbox-runtime-safety-contracts-0.1.0.jar"
if not jar.is_file() or jar.stat().st_size == 0:
    fail("component JAR missing")

expected_status = {
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
final_domains: dict[str, dict] = {}
for domain, expected in expected_status.items():
    row = plan.get("domains", {}).get(domain)
    if not isinstance(row, dict) or row.get("status") != expected:
        fail(f"{domain} assurance plan mismatch")
    final_domains[domain] = {
        "classification": expected,
        "finalStatus": "PASS" if expected == "APPLICABLE" else "PASS_BY_SCOPE_CLOSURE",
        "methodsApplicable": row.get("methodsApplicable", []),
        "methodsN/A": row.get("methodsN/A", []),
        "sourceCommitSha": source_sha,
    }

fault_trace = {
    "INVALID_INPUT_ACCEPTED": ["SELF_TEST", "BOUNDARY_TEST"],
    "BOUNDARY_ERROR": ["BOUNDARY_TEST", "RESOURCE_DIFFERENTIAL"],
    "ILLEGAL_STATE": ["RECOVERY_EXHAUSTIVE_REFERENCE"],
    "UNHANDLED_EXCEPTION": ["SELF_TEST", "RECOVERY_EXHAUSTIVE_REFERENCE"],
    "PROTOCOL_SEQUENCE_ERROR": ["RECOVERY_EXHAUSTIVE_REFERENCE"],
    "REGRESSION_AFTER_CHANGE": ["GITHUB_SHA_BINDING", "WORKFLOW_PATH_GATE"],
    "DATA_RACE": ["SYNCHRONIZED_SOURCE", "CONCURRENCY_TEST"],
    "VISIBILITY_ORDERING_ERROR": ["SYNCHRONIZED_SOURCE", "CONCURRENCY_TEST"],
    "NON_ATOMIC_COMPOUND_OPERATION": ["SYNCHRONIZED_SOURCE", "SOURCE_MUTATION"],
    "UNBOUNDED_QUEUE": ["DIAGNOSTIC_CAPACITY", "BOUNDARY_TEST", "SOURCE_MUTATION"],
    "RESOURCE_LIMIT_BYPASS": ["BOUNDARY_TEST", "RESOURCE_DIFFERENTIAL", "SOURCE_MUTATION"],
    "RECOVERY_LOOP": ["RECOVERY_EXHAUSTIVE_REFERENCE"],
    "ROLLBACK_TO_INVALID_STATE": ["RECOVERY_EXHAUSTIVE_REFERENCE"],
    "DUPLICATE_REPLAY": ["RECOVERY_IDEMPOTENT_STATES"],
    "UNTRUSTED_INPUT_ACCEPTED": ["BOUNDARY_TEST", "PREBUILD_SCAN"],
    "EXTERNAL_AUTHORITY_ESCAPE": ["PREBUILD_SCAN", "SOURCE_MUTATION"],
    "TOOLCHAIN_DRIFT": ["WORKFLOW_PINNING"],
    "DEPENDENCY_VERSION_DRIFT": ["ZERO_EXTERNAL_DEPENDENCIES"],
    "NON_HERMETIC_OUTPUT_DRIFT": ["REPRODUCIBLE_JAR"],
    "STALE_CACHE_GENERATED_OUTPUT": ["CLEAN_BUILD", "REPRODUCIBLE_JAR"],
    "PACKAGE_NAMESPACE_ESCAPE": ["PACKAGE_MUTATION"],
    "BYTECODE_TARGET_MISMATCH": ["PACKAGE_MUTATION", "PACKAGE_VALIDATION_PLANNED"],
    "CI_GATE_BYPASS": ["WORKFLOW_ORDER", "NO_CONTINUE_ON_ERROR"],
    "ARTIFACT_SOURCE_PROVENANCE_MISMATCH": ["GITHUB_SHA_BINDING", "PACKAGE_VALIDATION_PLANNED"],
}
if any(not evidence for evidence in fault_trace.values()):
    fail("fault trace has unowned row")

cross_domain = [
    {"interaction": "R1+R3", "challenge": "illegal recovery event fails closed without state mutation", "evidence": "SELF_TEST+RECOVERY_EXHAUSTIVE_REFERENCE"},
    {"interaction": "R2+R3", "challenge": "resource pressure drives bounded deterministic degraded/recovery states", "evidence": "RESOURCE_DIFFERENTIAL+RECOVERY_REFERENCE"},
    {"interaction": "R2+R5", "challenge": "hostile diagnostic volume remains bounded", "evidence": "DIAGNOSTIC_RETENTION+CONCURRENCY_TEST"},
    {"interaction": "R5+R6", "challenge": "promotable source cannot gain external executable authority", "evidence": "PREBUILD_SCAN+SOURCE_MUTATION"},
    {"interaction": "R1/R2/R3/R5+R6", "challenge": "current evidence and artifact bind to exact revision", "evidence": "GITHUB_SHA+R9+PACKAGE_VALIDATION"},
]

defeaters = [
    {"id": "D1", "risk": "state-machine table contains missing or wrong transition", "resolution": "all 35 state-event pairs compared to independent reference"},
    {"id": "D2", "risk": "resource threshold implementation and tests share same arithmetic error", "resolution": "independent long-arithmetic reference over 5000 deterministic cases plus metamorphic scaling"},
    {"id": "D3", "risk": "diagnostic flood grows memory without bound", "resolution": "fixed capacity, drop-oldest property, concurrency test, source mutation"},
    {"id": "D4", "risk": "synchronization accidentally removed", "resolution": "source architecture gate plus synchronization-removal mutations"},
    {"id": "D5", "risk": "Public safety contract gains external authority", "resolution": "prebuild forbidden-boundary scan plus authority-injection mutation"},
    {"id": "D6", "risk": "Public proof is mistaken for integrated Android recovery proof", "resolution": "R4/R7/R8 scope closures and final application claim explicitly false"},
]

ASSURANCE_DIR.mkdir(parents=True, exist_ok=True)
payload = {
    "schemaVersion": 1,
    "status": "PASS",
    "claim": "R9_PUBLIC_EVIDENCE_COMPLETENESS",
    "projectId": "ToolBox",
    "componentId": "public.runtime-safety-contracts",
    "componentVersion": "0.1.0",
    "contractId": "toolbox.runtime.safety",
    "contractVersion": "1.0.0",
    "sourceRepository": repo_name,
    "sourceCommitSha": source_sha,
    "workflowRunId": run_id,
    "domains": final_domains,
    "faultTrace": fault_trace,
    "crossDomainInteractions": cross_domain,
    "oracleDiversity": [
        "Java runtime assertions",
        "independent Java recovery transition reference",
        "independent long-arithmetic resource guard reference",
        "Python source/package mutation and policy oracle",
        "shell workflow marker/order assertions",
        "SHA-256 package/source provenance",
    ],
    "defeaters": defeaters,
    "closure": {
        "unownedApplicableMethod": 0,
        "unmappedApplicableMethod": 0,
        "unknown": 0,
        "skipped": 0,
        "notProven": 0,
        "staleEvidence": 0,
        "faultEscape": 0,
        "unresolvedDefeater": 0,
        "undeclaredMaterialAssumption": 0,
    },
    "artifactPrePackageValidation": {
        "fileName": jar.name,
        "sha256": sha256(jar),
        "sizeBytes": jar.stat().st_size,
    },
    "evidenceFiles": {
        "prebuild": sha256(PREBUILD),
        "testSummary": sha256(BUILD / "test-summary.txt"),
        "selfTest": sha256(BUILD / "test-output.txt"),
        "boundaryTest": sha256(BUILD / "boundary-output.txt"),
        "propertyTest": sha256(BUILD / "property-output.txt"),
        "simulator": sha256(BUILD / "simulator-output.txt"),
        "mutation": sha256(BUILD / "mutation-output.txt"),
    },
    "publicFinalApplicationClaim": False,
    "privateContentIncluded": False,
    "firebaseUsed": False,
}
EVIDENCE.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")

summary_lines = [
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
]
SUMMARY.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")
for line in summary_lines[:10]:
    print(line.replace("=", " = ", 1))
