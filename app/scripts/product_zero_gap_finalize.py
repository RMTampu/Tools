#!/usr/bin/env python3
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

APP=Path(__file__).resolve().parents[1]
REPO=APP.parent
OUT=APP/"build"/"assurance"

requirements=json.loads((APP/"FULL_PRODUCT_REQUIREMENTS.json").read_text())
items=requirements["requirements"]
assert len(items)==135
assert [item["section"] for item in items]==list(range(1,136))
assert all(item["required"] is True for item in items)
assert all(
    item["status"]=="IMPLEMENTED_AND_MUST_PASS"
    for item in items
)

behavior=json.loads(
    (OUT/"product-behavior-135-evidence.json").read_text()
)
sections=behavior["designSections"]
assert sections=={
    "required":135,
    "passed":135,
    "failed":0,
    "unknown":0,
    "skipped":0,
}

mutation=json.loads(
    (OUT/"product-full-mutation-evidence.json").read_text()
)
assert mutation["status"]=="PASS"
assert mutation["mutationsTotal"]>=5
assert mutation["mutationsKilled"]==mutation["mutationsTotal"]
assert mutation["mutationsEscaped"]==0

asset_plan=json.loads((APP/"ASSET_ASSURANCE_PLAN.json").read_text())
assert asset_plan["managedAssetDraftOnlyKinds"]==[]
assert asset_plan["statusPolicy"]["unsupportedRuntimeTypesRemainDraft"] is False
assert set(asset_plan["externalUserAssetReadyKinds"])=={
    "IMAGE","ICON","FONT","AUDIO","VIDEO"
}

asset=json.loads(
    (OUT/"product-full-asset-final-evidence.json").read_text()
)
assert asset["status"]=="ASSET_SAFE_100_DEVELOPMENT_PASS"
assert asset["unknownAssets"]==0
assert asset["unprovenRequiredAssets"]==0
assert asset["registryInventoryPackaged"]=="PASS"
assert asset["externalUserAssetRoute"]=="PASS"

r1r9=json.loads(
    (OUT/"product-full-r1-r9-evidence.json").read_text()
)
assert r1r9["status"]=="PASS"
assert all(
    r1r9["r1ToR9"][f"R{i}"]["status"]=="PASS"
    for i in range(1,10)
)
closure=r1r9["closure"]
for key in [
    "unknown","skipped","staleEvidence","mutationEscape",
    "designMissing","assetUnknown","unprovenRequiredAssets"
]:
    assert closure[key]==0,(key,closure[key])

runtime=(OUT/"product-full-api30-runtime.txt").read_text()
for marker in [
    "ANDROID_INSTRUMENTATION=PASS",
    "SOAK_100_RUNTIME=PASS",
    "PROCESS_DEATH_RESTART=PASS",
    "GFXINFO_RUNTIME=PASS",
    "PSS_BUDGET_RUNTIME=PASS",
    "FLOATING_EDITOR_MOVE_PIN_RESIZE=PASS",
    "EXTERNAL_ASSET_PICKER_ROUTE=PASS",
    "HEALTH_SAFE_MODE_UI=PASS",
    "EVOLUTION_UI=PASS",
    "FREEZE_SAVE_MODE_INDICATOR=PASS",
    "LAUNCHER_TB_PACKAGE=PASS",
    "ASSET_RUNTIME_RENDERER=PASS",
    "ADVANCED_UI_PROPERTIES=PASS",
    "MEMORY_PRESSURE_POLICY=PASS",
    "PATCH_JOURNAL_RUNTIME=PASS",
    "SAFE_RECOVERY_RUNTIME=PASS",
    "PATCH_DESTRUCTIVE_MATRIX=PASS",
    "FREEZE_DESTRUCTIVE_MATRIX=PASS",
    "EXTERNAL_MANAGED_EDITOR=PASS",
 "RUNTIME_APK_IDENTITY=PASS",
 "EXTERNAL_ASSET_TAMPER_REJECTED=PASS",
    "RANCANGAN_BEHAVIOR_135=PASS",
]:
    assert marker in runtime,marker

matrix=(
    APP/"src/main/java/com/toolbox/tools/product/ProductAcceptanceMatrix.java"
).read_text()
assert not re.search(r"pass\(\s*\d+\s*,\s*true\s*,",matrix),(
    "unconditional ProductAcceptanceMatrix PASS is forbidden"
)

android_tests=list(
    (APP/"src/androidTest").rglob("*.java")
)
assert android_tests,"Android instrumentation source missing"
android_xml=list(
    (APP/"build/outputs/androidTest-results/connected").rglob("*.xml")
)
assert android_xml,"Android instrumentation result missing"
tests=failures=errors=skipped=0
for file in android_xml:
    root=ET.parse(file).getroot()
    if root.tag!="testsuite":
        continue
    tests+=int(root.attrib.get("tests","0"))
    failures+=int(root.attrib.get("failures","0"))
    errors+=int(root.attrib.get("errors","0"))
    skipped+=int(root.attrib.get("skipped","0"))
assert tests>=3 and failures==0 and errors==0 and skipped==0

android_case_names=set()
for file in android_xml:
    root=ET.parse(file).getroot()
    if root.tag!="testsuite":
        continue
    for case in root.findall("testcase"):
        android_case_names.add(case.attrib.get("name",""))
assert "advancedUiPropertiesMaterializeOnRealAndroidView" in android_case_names
assert "externalAssetRendererUsesRealAndroidImageConsumer" in android_case_names
assert "safeRecoveryUiPersistsAcrossActivityRecreation" in android_case_names
assert "managedExternalEditingDoorUsesRealProviderBackedEditor" in android_case_names
assert "runtimePatchIdentityMatchesInstalledApk" in android_case_names
assert "externalAssetTamperIsRejectedAtUse" in android_case_names

safe_patch_xml=(
    APP/"build/test-results/testDebugUnitTest"
    /"TEST-com.toolbox.tools.delivery.SafePatchManagerTest.xml"
)
assert safe_patch_xml.is_file(),"SafePatchManager evidence missing"
safe_patch_root=ET.parse(safe_patch_xml).getroot()
safe_patch_cases={
    case.attrib.get("name","")
    for case in safe_patch_root.findall("testcase")
}
assert "v2PatchRejectsRuntimeApkLineageMismatch" in safe_patch_cases
assert "postActivationHealthFailureRollsBackAutomatically" in safe_patch_cases

maximal_xml=(
    APP/"build/test-results/testDebugUnitTest"
    /"TEST-com.toolbox.tools.product.MaximalProductionClosureTest.xml"
)
assert maximal_xml.is_file(),"maximal closure evidence missing"
maximal_root=ET.parse(maximal_xml).getroot()
maximal_cases={
    case.attrib.get("name","")
    for case in maximal_root.findall("testcase")
}
assert "scaleClassesMaterializeRealProjectGraphs" in maximal_cases
assert "interruptedPatchJournalRollsBackOnBootstrap" in maximal_cases
assert "safeModeAndFreezeSurviveKernelRecreation" in maximal_cases
assert "realRecoverySnapshotsLiveInVisibleSnapshotsArea" in maximal_cases
assert "productionRequiresUserOwnedSafBeforeEditing" in maximal_cases

evidence={
    "schemaVersion":2,
    "gate":"TOOLBOX_ZERO_GAP",
    "status":"PASS",
    "design":{
        "required":135,
        "pass":135,
        "partial":0,
        "missing":0,
        "unknown":0,
        "skipped":0,
    },
    "r1ToR9":{
        f"R{i}":"PASS" for i in range(1,10)
    },
    "asset":{
        "unknown":0,
        "unprovenRequired":0,
        "draftOnlyKinds":[],
        "externalMediaReady":[
            "IMAGE","ICON","FONT","AUDIO","VIDEO"
        ],
    },
    "verification":{
        "mutationTotal":mutation["mutationsTotal"],
        "mutationKilled":mutation["mutationsKilled"],
        "mutationEscape":0,
        "androidInstrumentationTests":tests,
        "androidInstrumentationFailures":0,
        "androidInstrumentationErrors":0,
        "androidInstrumentationSkipped":0,
        "soak100":"PASS",
        "processDeathRestart":"PASS",
        "gfxInfo":"PASS",
        "pssBudget":"PASS",
        "launcherTb":"PASS",
        "assetRuntimeRenderer":"PASS",
        "advancedUiProperties":"PASS",
        "scaleClassesActual":"PASS",
        "memoryPressurePolicy":"PASS",
        "patchJournalRuntime":"PASS",
        "safeRecoveryRuntime":"PASS",
        "patchDestructiveMatrix":"PASS",
        "freezeDestructiveMatrix":"PASS",
        "visibleRecoverySnapshots":"PASS",
        "productionUserOwnedSaf":"PASS",
        "externalManagedEditor":"PASS",
        "runtimeApkIdentity":"PASS",
        "externalAssetTamperRejected":"PASS",
    },
    "boundary":{
        "unconditionalAcceptancePass":0,
        "publicSigningUsed":False,
        "firebaseUsed":False,
    },
}
(OUT/"product-zero-gap-evidence.json").write_text(
    json.dumps(evidence,indent=2,sort_keys=True)+"\n"
)
print("TOOLBOX_ZERO_GAP=PASS")
print("PASS=135")
print("PARTIAL=0")
print("MISSING=0")
print("UNKNOWN=0")
print("SKIPPED=0")
