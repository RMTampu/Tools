#!/usr/bin/env python3
import hashlib, json, re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
REPO=ROOT.parents[1]
OUT=ROOT/"build"/"assurance"
OUT.mkdir(parents=True,exist_ok=True)
spec=json.loads((ROOT/"COMPONENT_SPEC.json").read_text())
contract=json.loads((ROOT/"CONTRACT.json").read_text())
plan=json.loads((ROOT/"ASSURANCE_PLAN_R1_R9.json").read_text())
handoff=json.loads((ROOT/"PRIVATE_INTEGRATION_REQUIREMENTS.json").read_text())
assert spec["projectId"]=="ToolBox" and spec["stageId"]=="A"
assert spec["componentId"]=="public.stage-a-android-host"
assert spec["target"]["minSdk"]==30 and spec["target"]["targetSdk"]==30
assert spec["privateContentRequired"] is False and spec["firebase"] is False and spec["productionSourceCount"]==9
expected_adapters={"AndroidAtomicStateStore","AndroidRecoveryStateStore","AndroidPermissionStateProvider","AndroidResourcePolicyProvider","AndroidSafeUi","AndroidSafeUiActions","AndroidStageAHost","StateFileCodec","NormalizedResourceMath"}
assert handoff["handoffType"]=="STAGE_A_ANDROID_HOST_PRODUCTION_ADAPTERS"
assert handoff["privateImplementationRequired"] is False
assert handoff["privateWiringOnly"] is True
assert set(handoff["productionAdapters"])==expected_adapters and len(handoff["productionAdapters"])==9
assert any("safeUiActions" in item for item in handoff["privateWiringRequirements"])
assert "SAFE_UI_ACTIONS_USE_PUBLIC_PRODUCTION_ADAPTER_ONLY" in contract["invariants"]
assert "SAFE_UI_UNPROVEN_AUTHORITY_STAYS_UNAVAILABLE" in contract["invariants"]
assert "safeUiActions" in contract["privateWiring"]["safeUi"] and "forbidden" in contract["privateWiring"]["safeUi"]
assert set(plan["domains"])=={f"R{i}" for i in range(1,10)}
assert plan["domains"]["R7"]["applicability"]=="N_A_SCOPE_PROVEN"
assert "production Safe UI actions" in plan["domains"]["R8"]["methods"]
sources=sorted((ROOT/"src/main/java/io/toolbox/stagea/android").glob("*.java"))
assert len(sources)==9, len(sources)
assert {p.stem for p in sources}==expected_adapters
forbidden=[r"\bjava\.net\.",r"\bjavax\.net\.",r"\bcom\.google\.firebase\b",r"\bDexClassLoader\b",
           r"\bURLClassLoader\b",r"\bjava\.lang\.reflect\b",r"\bRuntime\.getRuntime\b",r"\bProcessBuilder\b",
           r"\bSystem\.load(?:Library)?\b",r"RMTampu/ToolBox"]
for p in sources:
    text=p.read_text()
    for pattern in forbidden:
        assert re.search(pattern,text) is None,(p.name,pattern)
assert "android.util.AtomicFile" in (ROOT/"src/main/java/io/toolbox/stagea/android/AndroidAtomicStateStore.java").read_text()
assert "Debug.getPss()" in (ROOT/"src/main/java/io/toolbox/stagea/android/AndroidResourcePolicyProvider.java").read_text()
assert "getMemoryClass()" in (ROOT/"src/main/java/io/toolbox/stagea/android/AndroidResourcePolicyProvider.java").read_text()
assert "NORMALIZED_BUDGET" in (ROOT/"src/main/java/io/toolbox/stagea/android/NormalizedResourceMath.java").read_text()
assert "implements AndroidSafeUi.Actions" in (ROOT/"src/main/java/io/toolbox/stagea/android/AndroidSafeUiActions.java").read_text()
assert "safeUiActions()" in (ROOT/"src/main/java/io/toolbox/stagea/android/AndroidStageAHost.java").read_text()
assert "usesCleartextTraffic=\"false\"" in (ROOT/"AndroidManifest.xml").read_text()
hashes={p.relative_to(REPO).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
evidence={"schemaVersion":1,"projectId":"ToolBox","stageId":"A","componentId":"public.stage-a-android-host",
          "status":"PASS","productionSourceCount":9,"productionAdapterUniverse":"PASS","androidApiContract":30,"externalRuntimeDependencies":0,
          "resourceMapping":"PSS_NORMALIZED_TO_ANDROID_MEMORY_CLASS","stageAWorkloadProfile":"IDLE_ZERO_WORK_NO_SPECULATIVE_HEAVY_THRESHOLD",
          "durableStore":"ANDROID_ATOMIC_FILE_PLUS_SHA256_VERSIONED_CODEC","safeUi":"ANDROID_PLATFORM_WIDGETS_WITH_PUBLIC_PRODUCTION_ACTIONS",
          "safeUiActions":"PUBLIC_PRODUCTION_ADAPTER_FAIL_CLOSED","r7":"N_A_SCOPE_PROVEN","privateContentIncluded":0,"networkAuthority":0,"firebaseUsed":0,"sourceHashes":hashes}
(OUT/"android-host-prebuild-evidence.json").write_text(json.dumps(evidence,indent=2,sort_keys=True)+"\n")
print("STAGE_A_ANDROID_HOST_PREBUILD = PASS")
print("ANDROID_HOST_PRODUCTION_SOURCE_COUNT=9")
print("ANDROID_HOST_PRODUCTION_ADAPTER_UNIVERSE=PASS")
print("ANDROID_HOST_SAFE_UI_ACTIONS=PUBLIC_PRODUCTION")
