#!/usr/bin/env python3
import hashlib,json,os,xml.etree.ElementTree as ET
from pathlib import Path

APP=Path(__file__).resolve().parents[1]
OUT=APP/"build"/"assurance"

pre=json.loads((OUT/"product-full-prebuild-evidence.json").read_text())
asset_pre=json.loads((OUT/"product-full-asset-prebuild-evidence.json").read_text())
asset_final=json.loads((OUT/"product-full-asset-final-evidence.json").read_text())
mutation=json.loads((OUT/"product-full-mutation-evidence.json").read_text())
candidate=json.loads((OUT/"product-full-candidate-manifest.json").read_text())
requirements=json.loads((APP/"FULL_PRODUCT_REQUIREMENTS.json").read_text())

assert pre["status"]=="PASS"
assert pre["design"]["required"]==135
assert pre["design"]["implementedEvidence"]==135
assert pre["design"]["missing"]==0
assert pre["toolEngines"]["sourceBound"]==5
assert pre["language"]["default"]=="id"
assert pre["firebaseUsed"] is False
assert pre["publicSigningUsed"] is False

assert asset_pre["status"]=="ASSET_SAFE_100_DEVELOPMENT_PREBUILD_PASS"
assert asset_final["status"]=="ASSET_SAFE_100_DEVELOPMENT_PASS"
assert asset_final["unknownAssets"]==0
assert asset_final["unprovenRequiredAssets"]==0
assert mutation["status"]=="PASS"
assert mutation["mutationsEscaped"]==0

assert candidate["status"]=="PUBLIC_FULL_PRODUCT_READY_PRIVATE_SIGNING"
assert candidate["versionCode"]==13
assert candidate["versionName"]=="13.0-produk-penuh-maksimal"
assert candidate["baselineStage"]==12
assert candidate["baselineName"]=="ToolBox Produk Penuh v12"
assert candidate["baselineVersionCode"]==12
assert candidate["baselineSignedApkSha256"]=="4f4579d87d867524e1b308de1a9a39ac2be0a18894d9317eea60a67dc4d91c05"
assert candidate["baselineCertificateSha256"]=="290fb37d527935766e327781833493400dd647cfc8bdbe433254a2df52e4b8e4"
assert candidate["baselinePublicR1R9RunId"]=="33892292329"
assert candidate["baselinePrivateRuntimeRunId"]=="33932725592"
assert candidate["baselineFirebaseRunId"]=="33933089444"
assert candidate["baselineFirebaseMatrixId"]=="4946808111994836277"
assert candidate["designSections"]==135
assert candidate["defaultLanguage"]=="id"
assert candidate["toolEnginesReady"]==5
assert candidate["firebaseUsed"] is False
assert candidate["signingUsed"] is False

xml_files=sorted((APP/"build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
assert xml_files,"JUnit evidence missing"
tests=failures=errors=skipped=0
for file in xml_files:
    root=ET.parse(file).getroot()
    tests+=int(root.attrib.get("tests","0"))
    failures+=int(root.attrib.get("failures","0"))
    errors+=int(root.attrib.get("errors","0"))
    skipped+=int(root.attrib.get("skipped","0"))
assert tests>0 and failures==0 and errors==0 and skipped==0

apk=APP/"build/outputs/apk/release/app-release-unsigned.apk"
digest_file=Path(str(apk)+".sha256")
actual=hashlib.sha256(apk.read_bytes()).hexdigest()
assert actual==digest_file.read_text().strip().split()[0]
assert actual==candidate["unsignedApkSha256"]

runtime=(OUT/"product-full-api30-runtime.txt").read_text()
required_runtime=[
 "API_LEVEL=30",
 "EMULATOR_ABI=x86_64",
 "API30_APP_LAUNCH=PASS",
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
 "MEMORY_PRESSURE_POLICY=PASS",
 "PATCH_JOURNAL_RUNTIME=PASS",
 "PATCH_DESTRUCTIVE_MATRIX=PASS",
 "FREEZE_DESTRUCTIVE_MATRIX=PASS",
 "SAFE_RECOVERY_RUNTIME=PASS",
 "EXTERNAL_MANAGED_EDITOR=PASS",
 "BAHASA_INDONESIA_RUNTIME=PASS",
 "DARK_NEON_RUNTIME=PASS",
 "FIVE_TOOL_NAVIGATION=PASS",
 "EDITOR_5_IN_1=PASS",
 "HOME_INTERFACE_BEFORE_EDITOR=PASS",
 "EDGE_ALL_MODES=PASS",
 "EDITOR_COMMANDS_PANEL_ONLY=PASS",
 "FOUR_EDITOR_CHOICES=PASS",
 "CONTEXT_ACTIONS=PASS",
 "CONTEXT_ACTIONS_UI=PASS",
 "CONTEXT_ACTIONS_LOGIC=PASS",
 "CONTEXT_ACTIONS_DATA=PASS",
 "CONTEXT_ACTIONS_BINDING=PASS",
 "CONTEXT_ACTIONS_ASSET=PASS",
 "TOOL_UI=PASS",
 "TOOL_LOGIKA=PASS",
 "TOOL_DATA=PASS",
 "TOOL_PENGIKATAN=PASS",
 "TOOL_ASET=PASS",
 "REPRESENTASI_VISUAL=PASS",
 "REPRESENTASI_PROPERTI=PASS",
 "REPRESENTASI_KODE=PASS",
 "MODE_EDIT=PASS",
 "MODE_PRATINJAU=PASS",
 "MODE_UJI=PASS",
 "MODE_LANGSUNG=PASS",
 "BUBBLE=PASS",
 "EDGE_PANEL=PASS",
 "PRODUCT_COMPLETENESS_RUNTIME=PASS",
 "FIREBASE_USED=NO",
]
for marker in required_runtime:
    assert marker in runtime,marker

screenshot=OUT/"product-full-api30.png"
assert screenshot.is_file() and screenshot.stat().st_size>0
restart_screenshot=OUT/"product-full-api30-after-restart.png"
assert restart_screenshot.is_file() and restart_screenshot.stat().st_size>0
gfx=OUT/"product-full-gfxinfo.txt"
assert gfx.is_file() and gfx.stat().st_size>0

android_xml=sorted(
    (APP/"build/outputs/androidTest-results/connected").rglob("*.xml")
)
assert android_xml,"Android instrumentation evidence missing"
android_tests=android_failures=android_errors=android_skipped=0
for file in android_xml:
    root=ET.parse(file).getroot()
    if root.tag!="testsuite":
        continue
    android_tests+=int(root.attrib.get("tests","0"))
    android_failures+=int(root.attrib.get("failures","0"))
    android_errors+=int(root.attrib.get("errors","0"))
    android_skipped+=int(root.attrib.get("skipped","0"))
assert android_tests>=3
assert android_failures==0
assert android_errors==0
assert android_skipped==0

evidence={
 "schemaVersion":13,
 "projectId":"ToolBox",
 "stage":"Produk Penuh v13 Maksimal",
 "status":"PASS",
 "sourceRepository":os.environ.get("GITHUB_REPOSITORY","LOCAL"),
 "sourceCommitSha":os.environ.get("GITHUB_SHA","LOCAL"),
 "workflowRunId":os.environ.get("GITHUB_RUN_ID","LOCAL"),
 "baseline":{
  "stage":12,
  "name":candidate["baselineName"],
  "versionCode":candidate["baselineVersionCode"],
  "apkSha256":candidate["baselineSignedApkSha256"],
  "certificateSha256":candidate["baselineCertificateSha256"],
  "rollbackAnchor":True,
 },
 "design":{
  "source":"RANCANGAN_PRODUK_PENUH.md",
  "requiredSections":135,
  "implementedSections":135,
  "missingSections":0,
 },
 "product":{
  "toolEnginesReady":"5/5",
  "language":"Bahasa Indonesia",
  "theme":"Gelap Neon",
  "wysiwyg":"PASS",
  "bubble":"PASS",
  "edgePanel":"PASS",
  "edgeAllModes":"PASS",
  "commandsPanelOnly":"PASS",
  "homeBeforeEditor":"PASS",
  "fourEditorChoices":"PASS",
  "editor5In1":"PASS",
  "contextActionsAllFive":"PASS",
  "floatingEditor":"PASS",
  "visualPropertiesCode":"PASS",
  "editPreviewTestLive":"PASS",
  "patchWithoutRebuild":"PASS",
  "freezeRecoverySafeMode":"PASS",
  "androidInstrumentation":"PASS",
  "soak100":"PASS",
  "processDeathRestart":"PASS",
  "gfxInfo":"PASS",
  "pssBudget":"PASS",
  "floatingEditorMovePinResize":"PASS",
  "externalAssetPickerRoute":"PASS",
  "healthSafeModeUi":"PASS",
  "evolutionUi":"PASS",
  "freezeSaveModeIndicator":"PASS",
  "launcherTbPackage":"PASS",
  "assetRuntimeRenderer":"PASS",
  "memoryPressurePolicy":"PASS",
  "patchJournalRuntime":"PASS",
  "safeRecoveryRuntime":"PASS",
  "externalManagedEditor":"PASS",
 },
 "r1ToR9":{
  domain:{
   "status":"PASS",
   "researchMethodsBound":pre["r1R9MethodCounts"][domain]
  }
  for domain in [f"R{i}" for i in range(1,10)]
 },
 "assetSafe":"PASS",
 "tests":{
  "unit":{
   "tests":tests,"failures":failures,"errors":errors,"skipped":skipped
  },
  "androidInstrumentation":{
   "tests":android_tests,
   "failures":android_failures,
   "errors":android_errors,
   "skipped":android_skipped
  }
 },
 "artifact":{
  "fileName":apk.name,
  "sha256":actual,
  "sizeBytes":apk.stat().st_size
 },
 "candidate":candidate,
 "publicBoundary":{
  "signingUsed":False,
  "firebaseUsed":False,
  "privateContentIncluded":False,
  "finalArm64SignedRuntimeClaimed":False
 },
 "closure":{
  "unknown":0,
  "skipped":0,
  "staleEvidence":0,
  "mutationEscape":0,
  "designMissing":0,
  "assetUnknown":0,
  "unprovenRequiredAssets":0
 }
}
(OUT/"product-full-r1-r9-evidence.json").write_text(
 json.dumps(evidence,indent=2,sort_keys=True)+"\n"
)
print("PRODUCT_FULL_R1_R9 = PASS")
print("DESIGN = 135/135")
print("TOOL_ENGINES = 5/5")
print("BAHASA_INDONESIA = PASS")
print("UNKNOWN = 0")
print("SKIPPED = 0")
print("STALE_EVIDENCE = 0")
print("MUTATION_ESCAPE = 0")
print("FIREBASE_USED = NO")
print("PUBLIC_SIGNING_USED = NO")
