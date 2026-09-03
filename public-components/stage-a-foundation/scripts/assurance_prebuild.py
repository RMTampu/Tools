#!/usr/bin/env python3
import hashlib
import json
import re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]; REPO=ROOT.parents[1]; BUILD=ROOT/"build"/"assurance"; BUILD.mkdir(parents=True,exist_ok=True)
spec=json.loads((ROOT/"COMPONENT_SPEC.json").read_text()); contract=json.loads((ROOT/"CONTRACT.json").read_text()); plan=json.loads((ROOT/"ASSURANCE_PLAN_R1_R9.json").read_text()); handoff=json.loads((ROOT/"PRIVATE_INTEGRATION_REQUIREMENTS.json").read_text())
host_root=REPO/"public-components/stage-a-android-host"; host_spec=json.loads((host_root/"COMPONENT_SPEC.json").read_text()); host_contract=json.loads((host_root/"CONTRACT.json").read_text())
assert spec["projectId"]=="ToolBox" and spec["stageId"]=="A" and spec["componentVersion"]=="0.2.0" and spec["contractVersion"]=="1.1.0"
assert spec["privateImplementationRequired"] is False and spec["privateWiringOnly"] is True and spec["productionSourceCount"]==22
expected_types={"StageAContracts","ExecutionGuard","DiagnosticMapper","RecoveryCoordinator","SafeUiPolicy","SafeUiActionPolicy","HealthAggregator","StateFileCodec","NormalizedResourceMath","AndroidAtomicStateStore","AndroidRecoveryStateStore","AndroidPermissionStateProvider","AndroidResourcePolicyProvider","AndroidSafeUi","AndroidSafeUiActions","AndroidStageAHost"}
assert set(contract["productionTypes"])==expected_types and len(contract["productionTypes"])==len(expected_types)
assert "SAFE_UI_ACTION_POLICY_IS_PROMOTED_PRODUCTION_SOURCE" in contract["invariants"] and "ANDROID_SAFE_UI_ACTION_ADAPTER_IS_PROMOTED_PRODUCTION_SOURCE" in contract["invariants"] and "PRIVATE_SAFE_UI_CALLBACK_IMPLEMENTATION_FORBIDDEN" in contract["invariants"]
assert contract["failureSemantics"]["safeUiUnprovenAuthority"]=="UNAVAILABLE"
assert set(plan["domains"])=={f"R{i}" for i in range(1,10)} and plan["domains"]["R4"]["applicability"]=="APPLICABLE" and plan["domains"]["R8"]["applicability"]=="APPLICABLE" and plan["domains"]["R7"]["applicability"]=="N_A_SCOPE_PROVEN"
assert "production SafeUi action policy and Android adapter" in plan["domains"]["R8"]["proof"]
assert handoff["integration"]["hostImplementationInPrivateForbidden"] is True and host_spec["productionSourceCount"]==9 and host_contract["privateWiring"]["kernelStateStore"].startswith("thin delegate")
assert any("safeUiActions" in item for item in handoff["privateWiringRequirements"]) and "AndroidSafeUiActions" in json.loads((host_root/"PRIVATE_INTEGRATION_REQUIREMENTS.json").read_text())["productionAdapters"]
deps=[REPO/"public-components/runtime-contracts/src/main/java/io/toolbox/contracts/runtime/Contracts.java",REPO/"public-components/runtime-contracts/src/main/java/io/toolbox/contracts/runtime/ProductRegistry.java",REPO/"public-components/runtime-safety-contracts/src/main/java/io/toolbox/contracts/safety/SafetyContracts.java",REPO/"public-components/runtime-safety-contracts/src/main/java/io/toolbox/contracts/safety/DiagnosticBuffer.java",REPO/"public-components/runtime-safety-contracts/src/main/java/io/toolbox/contracts/safety/ResourceGuard.java",REPO/"public-components/runtime-safety-contracts/src/main/java/io/toolbox/contracts/safety/RecoveryMachine.java"]
stage=sorted((ROOT/"src/main/java/io/toolbox/stagea").glob("*.java")); host=sorted((host_root/"src/main/java/io/toolbox/stagea/android").glob("*.java")); assert len(stage)==7 and len(host)==9
for p in deps+stage+host: assert p.is_file(),p
actual_types={p.stem for p in stage+host}; assert actual_types==expected_types,(sorted(expected_types-actual_types),sorted(actual_types-expected_types))
for p in stage:
  for pattern in [r"\bandroid\.",r"\bjava\.net\.",r"\bjavax\.net\.",r"\bcom\.google\.firebase\b",r"\bClassLoader\b",r"\bRuntime\.getRuntime\b",r"\bProcessBuilder\b",r"\bjava\.lang\.reflect\b",r"\bDexClassLoader\b",r"\bSystem\.load(?:Library)?\b"]: assert re.search(pattern,p.read_text()) is None,(p.name,pattern)
alltext="\n".join(p.read_text(errors="replace") for p in stage+host); assert "RMTampu/ToolBox" not in alltext and "firebase" not in alltext.lower()
hashes={p.relative_to(REPO).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in deps+stage+host}
evidence={"schemaVersion":2,"projectId":"ToolBox","stageId":"A","componentId":"public.stage-a-foundation","status":"PASS","domainsLoaded":sorted(plan["domains"]),"dependencyProductionSources":6,"stageProductionSources":7,"androidHostProductionSources":9,"totalProductionSources":22,"productionTypeUniverse":"PASS","safeUiProductionActionsDeclared":"PASS","externalRuntimeDependencies":0,"stageForbiddenAuthorityScan":"PASS","androidHostPrebuildRequired":True,"privateImplementationRequired":False,"privateWiringOnly":True,"publicPrivateReadAccess":0,"firebaseUsed":0,"sourceHashes":hashes}
(BUILD/"prebuild-evidence.json").write_text(json.dumps(evidence,indent=2,sort_keys=True)+"\n"); print("STAGE_A_PREBUILD_ASSURANCE = PASS"); print("PRODUCTION_SOURCE_COUNT=22"); print("PRODUCTION_TYPE_UNIVERSE=PASS"); print("SAFE_UI_PRODUCTION_ACTIONS_DECLARED=PASS")
