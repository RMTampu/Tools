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
assert plan["stage"]=="Tahap 6" and plan["stageMap"]=="F"
assert plan["parentBaseline"]["name"]=="Tahap 5"
assert plan["parentBaseline"]["apkSha256"]=="717f36b59d9c005b17bfeee72b1de7f872ed3c6b406471e6b9d80c9efba91f49"
assert plan["target"]["androidApi"]==30
assert asset["stage"]=="Tahap 6" and asset["stageMap"]=="F"

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
assert re.search(r"\bversionCode\s+6\b",gradle)
assert 'android:name=".MainActivity"' in manifest
assert 'android:theme="@style/AppTheme"' in manifest

required=[
 "src/main/java/com/toolbox/tools/core/StableId.java",
 "src/main/java/com/toolbox/tools/core/AppKernel.java",
 "src/main/java/com/toolbox/tools/core/VerificationManager.java",
 "src/main/java/com/toolbox/tools/library/LibraryManager.java",
 "src/main/java/com/toolbox/tools/library/TemplateRegistry.java",
 "src/main/java/com/toolbox/tools/runtime/SharedRuntimeModel.java",
 "src/main/java/com/toolbox/tools/editor/EditorShellController.java",
 "src/main/java/com/toolbox/tools/editor/VisualEditorSession.java",
 "src/main/java/com/toolbox/tools/authoring/AuthoringSection.java",
 "src/main/java/com/toolbox/tools/authoring/AuthoringSearchQuery.java",
 "src/main/java/com/toolbox/tools/authoring/AuthoringSearchIndex.java",
 "src/main/java/com/toolbox/tools/authoring/AuthoringDraftStore.java",
 "src/main/java/com/toolbox/tools/authoring/TemplateAuthoringDraft.java",
 "src/main/java/com/toolbox/tools/authoring/TemplateAuthoringService.java",
 "src/main/java/com/toolbox/tools/authoring/UnifiedAuthoringWorkspace.java",
 "src/main/java/com/toolbox/tools/authoring/DefaultAuthoringFactory.java",
]
for rel in required:
    assert (APP/rel).is_file(),rel
combined="\n".join((APP/rel).read_text() for rel in required)+"\n"+main

checks={
 "R1":["StableId.require","MAX_QUERY_LENGTH = 128","DraftLifecycle","resolveExact"],
 "R2":["MAX_RESULTS = 100","MAX_HISTORY = 32","activeSection","Collections.unmodifiableList"],
 "R3":["PUBLISHED","DISCARDED","draft terminal","VALIDATED"],
 "R4":["revision() + 1","TemplateInstantiationPlan","preview","registry"],
 "R5":["search is read-only","DependencyResolutionResult","template dependency validation"],
 "R6":["versionCode 6"],
 "R7":[],
 "R8":["UI","Logic","Data","Binding","Asset","Cari Tombol"],
 "R9":["TemplateAuthoringValidation","AuthoringDraftStore","VerificationManager"],
}
for domain,markers in checks.items():
    for marker in markers:
        if marker=="versionCode 6":
            assert re.search(r"\bversionCode\s+6\b",gradle)
        elif marker=="search is read-only":
            assert "search(" in combined and "execute(" not in combined
        elif marker=="registry":
            assert "templates.publishReady" in combined
        elif marker=="template dependency validation":
            assert "resolver.resolveTemplate" in combined
        else:
            assert marker in combined,(domain,marker)

tests=[
 "src/test/java/com/toolbox/tools/authoring/UnifiedAuthoringSearchTest.java",
 "src/test/java/com/toolbox/tools/authoring/AuthoringDraftStoreTest.java",
 "src/test/java/com/toolbox/tools/authoring/TemplateAuthoringServiceTest.java",
 "src/test/java/com/toolbox/tools/editor/BubbleShellTest.java",
 "src/test/java/com/toolbox/tools/editor/VisualEditorSessionTest.java",
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
 "schemaVersion":5,
 "projectId":"ToolBox",
 "stage":"Tahap 6","stageMap":"F","status":"PASS",
 "parentBaseline":{"name":"Tahap 5","apkSha256":plan["parentBaseline"]["apkSha256"],"unchangedByPublicWork":True},
 "androidApi":30,"versionCode":6,
 "researchMethodCounts":method_counts,
 "domains":{k:{"applicability":v["applicability"],"prebuild":"PASS"} for k,v in plan["domains"].items()},
 "authoring":{
   "sections":["UI","LOGIC","DATA","BINDING","ASSET"],
   "sharedModel":"BOUND",
   "search":"BOUNDED_DETERMINISTIC",
   "draftLifecycle":"BOUND",
   "templateAuthoring":"BOUND"
 },
 "assetChain":"BOUND","r7":"N_A_SCOPE_PROVEN","sourceHashes":hashes,
 "publicBoundaries":{"privateContentIncluded":False,"firebaseUsed":False,"signingUsed":False,"arbitraryExecutableRuntimeAdded":False}
}
(OUT/"tahap6-r1-r8-prebuild-evidence.json").write_text(json.dumps(evidence,indent=2,sort_keys=True)+"\n")
print("TAHAP_6_R1_R8_PREBUILD = PASS")
print("R1_R9_RESEARCH_CORPUS = BOUND")
print("ASSET_SAFE_CHAIN = BOUND")
print("R7 = N_A_SCOPE_PROVEN")
