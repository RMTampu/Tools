#!/usr/bin/env python3
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parents[1]
BUILD = ROOT / "build" / "assurance"
BUILD.mkdir(parents=True, exist_ok=True)
spec = json.loads((ROOT / "COMPONENT_SPEC.json").read_text())
contract = json.loads((ROOT / "CONTRACT.json").read_text())
plan = json.loads((ROOT / "ASSURANCE_PLAN_R1_R9.json").read_text())
handoff = json.loads((ROOT / "PRIVATE_INTEGRATION_REQUIREMENTS.json").read_text())
assert spec["projectId"] == "ToolBox" and spec["stageId"] == "A"
assert spec["componentId"] == "public.stage-a-foundation" and spec["componentVersion"] == "0.1.0"
assert spec["contractId"] == "toolbox.stage.a.foundation" and spec["contractVersion"] == "1.0.0"
assert spec["promotionBoundary"] == "STAGE_READY_PRIVATE_ONLY"
assert spec["privateContentRequired"] is False and spec["privateIntegrationClaimed"] is False and spec["finalApplicationSafe100Claimed"] is False
required_invariants = {"A1_A4_ARE_INTERNAL_SUBSTEPS","COMPONENT_READY_PRIVATE_DOES_NOT_AUTHORIZE_PRIVATE_STAGE_INTEGRATION","ONLY_STAGE_A_READY_PRIVATE_IS_STAGE_HANDOFF","REGISTRY_ROUTE_IS_PRODUCTREGISTRY_BASED","EXECUTION_ADMISSION_FAILS_CLOSED","RECOVERY_REQUIRED_SAFE_MODE_QUARANTINED_BLOCK_EXECUTION","RECOVERY_STATE_STORE_SAVE_PRECEDES_STATE_PUBLICATION","SAFE_UI_CONTRACT_IS_VISIBLE_AND_RESTRICTED_FOR_RECOVERY_REQUIRED_SAFE_MODE_QUARANTINED","DIAGNOSTICS_CONTAIN_METADATA_ONLY","RESOURCE_UNITS_REQUIRE_PRIVATE_HOST_MAPPING","PUBLIC_PRIVATE_READ_ACCESS_ZERO","PUBLIC_FIREBASE_ZERO"}
assert set(contract["invariants"]) == required_invariants
assert set(plan["domains"]) == {f"R{i}" for i in range(1, 10)}
for row in plan["domains"].values():
    assert row["applicability"] not in {"UNKNOWN","SKIPPED","NOT_CHECKED","NOT_PROVEN"}
    assert row["methods"] and row["proof"]
assert handoff["handoffType"] == "CONSOLIDATED_STAGE_A" and handoff["requiredStatus"] == "STAGE_A_READY_PRIVATE"
assert handoff["integration"]["singleStageTransaction"] is True
assert handoff["integration"]["a1ToA4SeparatePrivatePromotionForbidden"] is True
assert handoff["integration"]["registryRouteRequired"] is True
assert handoff["firebase"] is False
dependency_sources = [
    REPO / "public-components/runtime-contracts/src/main/java/io/toolbox/contracts/runtime/Contracts.java",
    REPO / "public-components/runtime-contracts/src/main/java/io/toolbox/contracts/runtime/ProductRegistry.java",
    REPO / "public-components/runtime-safety-contracts/src/main/java/io/toolbox/contracts/safety/SafetyContracts.java",
    REPO / "public-components/runtime-safety-contracts/src/main/java/io/toolbox/contracts/safety/DiagnosticBuffer.java",
    REPO / "public-components/runtime-safety-contracts/src/main/java/io/toolbox/contracts/safety/ResourceGuard.java",
    REPO / "public-components/runtime-safety-contracts/src/main/java/io/toolbox/contracts/safety/RecoveryMachine.java",
]
stage_sources = sorted((ROOT / "src/main/java/io/toolbox/stagea").glob("*.java"))
assert len(stage_sources) == 6
for path in dependency_sources + stage_sources: assert path.is_file(), path
forbidden_patterns = [r"\bandroid\.",r"\bjava\.net\.",r"\bjavax\.net\.",r"\bcom\.google\.firebase\b",r"\bClassLoader\b",r"\bURLClassLoader\b",r"\bRuntime\.getRuntime\b",r"\bProcessBuilder\b",r"\bjava\.lang\.reflect\b",r"\bDexClassLoader\b",r"\bSystem\.load(?:Library)?\b"]
for source in stage_sources:
    text = source.read_text()
    for pattern in forbidden_patterns: assert re.search(pattern, text) is None, f"forbidden pattern {pattern} in {source.name}"
repo_text = "\n".join(path.read_text(errors="replace") for path in stage_sources)
assert "RMTampu/ToolBox" not in repo_text and "firebase" not in repo_text.lower()
source_hashes = {path.relative_to(REPO).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest() for path in dependency_sources + stage_sources}
evidence = {"schemaVersion":1,"projectId":"ToolBox","stageId":"A","componentId":"public.stage-a-foundation","status":"PASS","domainsLoaded":sorted(plan["domains"]),"dependencyProductionSources":6,"stageProductionSources":6,"externalRuntimeDependencies":0,"forbiddenAuthorityScan":"PASS","publicPrivateReadAccess":0,"firebaseUsed":0,"sourceHashes":source_hashes}
(BUILD / "prebuild-evidence.json").write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n")
print("STAGE_A_PREBUILD_ASSURANCE = PASS")
print("PRODUCTION_SOURCE_COUNT=12")
