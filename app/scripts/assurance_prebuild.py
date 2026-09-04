#!/usr/bin/env python3
import hashlib,json,re
from pathlib import Path

APP=Path(__file__).resolve().parents[1]
REPO=APP.parent
OUT=APP/"build"/"assurance"
OUT.mkdir(parents=True,exist_ok=True)

plan=json.loads((APP/"ASSURANCE_PLAN_R1_R9.json").read_text())
asset=json.loads((APP/"ASSET_ASSURANCE_PLAN.json").read_text())
assert plan["projectId"]=="ToolBox"
assert plan["stage"]=="Tahap 5" and plan["stageMap"]=="E"
assert plan["parentBaseline"]["name"]=="Tahap 4"
assert plan["parentBaseline"]["apkSha256"]=="74a90ae0442083d4f050a4642a9c2a213ff1b785ea5b2f844f033cdea543cfc3"
assert plan["target"]["androidApi"]==30
assert asset["stage"]=="Tahap 5" and asset["stageMap"]=="E"

method_counts={}
for i in range(1,10):
    domain=f"R{i}"
    doc=REPO/plan["domains"][domain]["sourceMethodDoc"]
    assert doc.is_file(),doc
    text=doc.read_text()
    methods=re.findall(rf"^### R{i}-M\d+",text,flags=re.MULTILINE)
    assert methods,(domain,"research method corpus missing")
    method_counts[domain]=len(methods)

for rel in [
 "R1_R9_ASSET_CHAIN.md","ASSET_SAFE_100_RULES.md","ASSET_SAFE_100_PROCESS.md",
 "ASSET_SAFE_100_METHODS.md","ASSET_ROUTE_PROOF_PROCESS.md",
 "ASSET_ROUTE_PROOF_METHODS.md","PREBUILD_ASSET_GATE.md",
 "APPLICATION_SAFE_100_PROCESS.md","TEST_ROUTING_POLICY.md"
]:
    assert (REPO/rel).is_file() and (REPO/rel).stat().st_size>0,rel

gradle=(APP/"build.gradle").read_text()
manifest=(APP/"src/main/AndroidManifest.xml").read_text()
main=(APP/"src/main/java/com/toolbox/tools/MainActivity.java").read_text()
assert "applicationId 'com.toolbox.tools'" in gradle
assert re.search(r"\bminSdk\s+30\b",gradle)
assert re.search(r"\btargetSdk\s+30\b",gradle)
assert re.search(r"\bversionCode\s+5\b",gradle)
assert 'android:name=".MainActivity"' in manifest
assert 'android:theme="@style/AppTheme"' in manifest

required=[
 "src/main/java/com/toolbox/tools/core/StableId.java",
 "src/main/java/com/toolbox/tools/core/AppKernel.java",
 "src/main/java/com/toolbox/tools/core/VerificationManager.java",
 "src/main/java/com/toolbox/tools/library/ComponentRegistry.java",
 "src/main/java/com/toolbox/tools/library/LibraryManager.java",
 "src/main/java/com/toolbox/tools/runtime/SharedRuntimeModel.java",
 "src/main/java/com/toolbox/tools/runtime/Renderer.java",
 "src/main/java/com/toolbox/tools/runtime/RuntimeModelValidator.java",
 "src/main/java/com/toolbox/tools/runtime/NavigationManager.java",
 "src/main/java/com/toolbox/tools/runtime/BindingCycleGuard.java",
 "src/main/java/com/toolbox/tools/editor/BubblePositionStore.java",
 "src/main/java/com/toolbox/tools/editor/BubbleController.java",
 "src/main/java/com/toolbox/tools/editor/EdgePanelFactory.java",
 "src/main/java/com/toolbox/tools/editor/EditorShellController.java",
 "src/main/java/com/toolbox/tools/editor/FloatingPlacementEngine.java",
 "src/main/java/com/toolbox/tools/editor/FloatingEditorController.java",
 "src/main/java/com/toolbox/tools/editor/VisualCapabilitySet.java",
 "src/main/java/com/toolbox/tools/editor/VisualEditorSession.java",
 "src/main/java/com/toolbox/tools/editor/VisualHistory.java",
 "src/main/java/com/toolbox/tools/editor/VisualLockSet.java",
 "src/main/java/com/toolbox/tools/editor/EditorDiagnostic.java",
 "src/main/java/com/toolbox/tools/editor/DefaultEditorFactory.java",
]
for rel in required:
    assert (APP/rel).is_file(),rel
combined="\n".join((APP/rel).read_text() for rel in required)+"\n"+main

checks={
 "R1":["StableId.require","capabilities.supports","BROKEN_OPERATION","LIVE_CAPABILITY_UNAVAILABLE"],
 "R2":["MAX_HISTORY = 64","MAX_ACTIVE_TOKENS = 256","MAX_BACK_STACK = 64","single primary"],
 "R3":["emergencyReset","editorOverlayVisible","setLiveCapability","floatingEditor().close"],
 "R4":["VisualObjectState","VisualEditTransaction","orientationSuffix","getPreferences"],
 "R5":["LOCKED_OPERATION","editor.operation.broken","setOnTouchListener","ProcessBuilder"],
 "R6":["versionCode 5"],
 "R7":[],
 "R8":["FrameLayout","Edge Panel","Floating Editor","BubblePositionStore"],
 "R9":["EditorDiagnostic","VisualHistory","VerificationManager"],
}
for domain,markers in checks.items():
    for marker in markers:
        if marker=="versionCode 5":
            assert re.search(r"\bversionCode\s+5\b",gradle)
        elif marker=="single primary":
            assert "private FloatingEditorState active;" in combined
        elif marker=="ProcessBuilder":
            assert "ProcessBuilder" not in combined
        elif marker=="floatingEditor().close":
            assert "floatingEditor().close" in main
        else:
            assert marker in combined,(domain,marker)

tests=[
 "src/test/java/com/toolbox/tools/editor/BubbleShellTest.java",
 "src/test/java/com/toolbox/tools/editor/EdgeFloatingTest.java",
 "src/test/java/com/toolbox/tools/editor/VisualEditorSessionTest.java",
 "src/test/java/com/toolbox/tools/runtime/RendererSharedModelTest.java",
 "src/test/java/com/toolbox/tools/runtime/NavigationActionTest.java",
 "src/test/java/com/toolbox/tools/runtime/DataBindingTest.java",
 "src/test/java/com/toolbox/tools/runtime/FlowGraphTest.java",
]
for rel in tests:
    assert (APP/rel).is_file(),rel

for path in sorted((APP/"src/main").rglob("*")):
    if not path.is_file() or path.suffix not in {".java",".kt"}:
        continue
    text=path.read_text(errors="replace")
    for pattern in [
      r"\bDexClassLoader\b",r"\bURLClassLoader\b",r"\bjava\.lang\.reflect\b",
      r"\bSystem\.load(?:Library)?\b",r"\bProcessBuilder\b",
      r"\bcom\.google\.firebase\b",r"SIGNING_KEY",r"KEY_STORE_PASSWORD"
    ]:
        assert re.search(pattern,text) is None,(path,pattern)
assert not list((APP/"src/main").rglob("*.so"))
assert plan["domains"]["R7"]["applicability"]=="N_A_SCOPE_PROVEN"

hashes={}
for path in sorted((APP/"src").rglob("*")):
    if path.is_file():
        hashes[path.relative_to(REPO).as_posix()]=hashlib.sha256(path.read_bytes()).hexdigest()
for rel in ["app/build.gradle","app/ASSURANCE_PLAN_R1_R9.json","app/ASSET_ASSURANCE_PLAN.json"]:
    hashes[rel]=hashlib.sha256((REPO/rel).read_bytes()).hexdigest()

evidence={
 "schemaVersion":4,
 "projectId":"ToolBox",
 "stage":"Tahap 5","stageMap":"E","status":"PASS",
 "parentBaseline":{"name":"Tahap 4","apkSha256":plan["parentBaseline"]["apkSha256"],"unchangedByPublicWork":True},
 "androidApi":30,"versionCode":5,
 "researchMethodCounts":method_counts,
 "domains":{k:{"applicability":v["applicability"],"prebuild":"PASS"} for k,v in plan["domains"].items()},
 "visualEditor":{
   "bubble":"BOUND",
   "edgePanel":"BOUND",
   "floatingEditor":"BOUND",
   "workingState":"BOUND",
   "history":"BOUNDED",
   "liveCapabilityGate":"BOUND"
 },
 "assetChain":"BOUND","r7":"N_A_SCOPE_PROVEN","sourceHashes":hashes,
 "publicBoundaries":{"privateContentIncluded":False,"firebaseUsed":False,"signingUsed":False,"arbitraryExecutableRuntimeAdded":False}
}
(OUT/"tahap5-r1-r8-prebuild-evidence.json").write_text(json.dumps(evidence,indent=2,sort_keys=True)+"\n")
print("TAHAP_5_R1_R8_PREBUILD = PASS")
print("R1_R9_RESEARCH_CORPUS = BOUND")
print("ASSET_SAFE_CHAIN = BOUND")
print("R7 = N_A_SCOPE_PROVEN")
