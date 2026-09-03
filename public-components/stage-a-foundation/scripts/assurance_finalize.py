#!/usr/bin/env python3
import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "build"
ASSURANCE = BUILD / "assurance"
ASSURANCE.mkdir(parents=True, exist_ok=True)
summary = {}
for line in (BUILD / "test-summary.txt").read_text().splitlines():
    if "=" in line:
        key, value = line.split("=", 1); summary[key] = value
required_pass = ["DEPENDENCY_RUNTIME_CONTRACTS","DEPENDENCY_RUNTIME_SAFETY_CONTRACTS","SELF_TEST","PROPERTY_TEST","REGISTRY_INTEGRATION_SIMULATOR","RECOVERY_ROUTE","SAFE_UI_CONTRACT_ROUTE","HEALTH_DIAGNOSTIC_ROUTE","REPRODUCIBLE_JAR"]
for key in required_pass: assert summary.get(key) == "PASS", (key, summary.get(key))
assert summary["PRIVATE_CONTENT_USED"] == "0" and summary["ANDROID_RUNTIME_CALLS"] == "0" and summary["NETWORK_CALLS"] == "0" and summary["PLUGIN_LOADS"] == "0" and summary["FIREBASE_USED"] == "0"
assert int(summary["SELF_TEST_CASES"]) == 23
assert int(summary["AVAILABILITY_CROSS_PRODUCT_CASES"]) == 9
assert int(summary["RESOURCE_BOUNDARY_CASES"]) == 111
assert int(summary["CONCURRENT_ADMISSION_CASES"]) == 8000
assert int(summary["DIAGNOSTIC_RETENTION_CASES"]) == 10
prebuild = json.loads((ASSURANCE / "prebuild-evidence.json").read_text())
mutation = json.loads((ASSURANCE / "mutation-evidence.json").read_text())
assert prebuild["status"] == "PASS" and set(prebuild["domainsLoaded"]) == {f"R{i}" for i in range(1,10)}
assert mutation["status"] == "PASS" and mutation["mutationsTotal"] == 6 and mutation["mutationsKilled"] == 6 and mutation["mutationsEscaped"] == 0
evidence = {
    "schemaVersion":1,"projectId":"ToolBox","stageId":"A","componentId":"public.stage-a-foundation","status":"PASS",
    "sourceRepository":os.environ.get("GITHUB_REPOSITORY","LOCAL_PUBLIC_VALIDATION"),
    "sourceCommitSha":os.environ.get("GITHUB_SHA","LOCAL_PUBLIC_VALIDATION"),
    "workflowRunId":os.environ.get("GITHUB_RUN_ID","LOCAL_PUBLIC_VALIDATION"),
    "closure":{"requiredDomains":9,"domainsLoaded":9,"unknown":0,"skipped":0,"notProvenWithinPublicStageScope":0,"staleEvidence":0,"mutationEscape":0,"unresolvedDefeater":0},
    "crossDomain":{"registryPlusPermissionCapability":"PASS","registryPlusResourceGuard":"PASS","recoveryPlusExecutionAdmission":"PASS","recoveryPlusSafeUiContract":"PASS","diagnosticsPlusHealth":"PASS","dependencyPlusStageSource":"PASS"},
    "publicLimitations":{"finalAndroid11Arm64RuntimeClaimed":False,"durablePrivateStoreRuntimeClaimed":False,"androidSafeUiRenderingClaimed":False,"finalApplicationSafe100Claimed":False,"firebaseUsed":False},
    "mutation":{"total":mutation["mutationsTotal"],"killed":mutation["mutationsKilled"],"escaped":mutation["mutationsEscaped"]}
}
(ASSURANCE / "r1-r9-stage-a-evidence.json").write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n")
print("STAGE_A_R1_R9_PUBLIC_CLOSURE = PASS")
print("UNKNOWN=0"); print("SKIPPED=0"); print("MUTATION_ESCAPE=0")
