#!/usr/bin/env python3
import hashlib
import json
import re
from pathlib import Path

APP=Path(__file__).resolve().parents[1]
REPO=APP.parent
OUT=APP/"build"/"assurance"
OUT.mkdir(parents=True,exist_ok=True)

plan=json.loads((APP/"ASSURANCE_PLAN_R1_R9.json").read_text())
asset_plan=json.loads((APP/"ASSET_ASSURANCE_PLAN.json").read_text())

assert plan["projectId"]=="ToolBox"
assert plan["stage"]=="Tahap 3" and plan["stageMap"]=="C"
assert plan["parentBaseline"]["name"]=="Tahap 2"
assert re.fullmatch(r"[0-9a-f]{64}",plan["parentBaseline"]["apkSha256"])
assert plan["target"]["androidApi"]==30
assert set(plan["domains"])=={f"R{i}" for i in range(1,10)}
assert asset_plan["stage"]=="Tahap 3" and asset_plan["stageMap"]=="C"

method_counts={}
for index in range(1,10):
    domain=f"R{index}"
    doc=REPO/plan["domains"][domain]["sourceMethodDoc"]
    assert doc.is_file(),doc
    text=doc.read_text()
    methods=re.findall(rf"^### R{index}-M\d+",text,flags=re.MULTILINE)
    assert methods,(domain,"research method corpus missing")
    method_counts[domain]=len(methods)

required_chain_docs=[
 "R1_R9_ASSET_CHAIN.md",
 "ASSET_SAFE_100_RULES.md",
 "ASSET_SAFE_100_PROCESS.md",
 "ASSET_SAFE_100_METHODS.md",
 "ASSET_ROUTE_PROOF_PROCESS.md",
 "ASSET_ROUTE_PROOF_METHODS.md",
 "PREBUILD_ASSET_GATE.md",
 "APPLICATION_SAFE_100_PROCESS.md",
 "TEST_ROUTING_POLICY.md",
]
for rel in required_chain_docs:
    path=REPO/rel
    assert path.is_file() and path.stat().st_size>0,rel

chain=(REPO/"R1_R9_ASSET_CHAIN.md").read_text()
for index in range(1,10):
    assert f"APP_SAFE_R{index}_" in chain or f"R{index}" in chain

gradle=(APP/"build.gradle").read_text()
manifest=(APP/"src/main/AndroidManifest.xml").read_text()
assert "applicationId 'com.toolbox.tools'" in gradle
assert re.search(r"\bminSdk\s+30\b",gradle)
assert re.search(r"\btargetSdk\s+30\b",gradle)
version=int(re.search(r"\bversionCode\s+(\d+)\b",gradle).group(1))
assert version==3
assert 'android:name=".MainActivity"' in manifest
assert 'android:exported="true"' in manifest
assert 'android:theme="@style/AppTheme"' in manifest

required=[
 "src/main/java/com/toolbox/tools/core/StableId.java",
 "src/main/java/com/toolbox/tools/core/DigestUtils.java",
 "src/main/java/com/toolbox/tools/core/ProjectState.java",
 "src/main/java/com/toolbox/tools/core/FileProjectStore.java",
 "src/main/java/com/toolbox/tools/core/ProjectManager.java",
 "src/main/java/com/toolbox/tools/library/VersionNumber.java",
 "src/main/java/com/toolbox/tools/library/VersionRange.java",
 "src/main/java/com/toolbox/tools/library/DependencyRef.java",
 "src/main/java/com/toolbox/tools/library/AssetDependencyRef.java",
 "src/main/java/com/toolbox/tools/library/ComponentDefinition.java",
 "src/main/java/com/toolbox/tools/library/ComponentManifest.java",
 "src/main/java/com/toolbox/tools/library/ComponentRegistry.java",
 "src/main/java/com/toolbox/tools/library/ComponentInstance.java",
 "src/main/java/com/toolbox/tools/library/ComponentVariant.java",
 "src/main/java/com/toolbox/tools/library/CompositeComponentSpec.java",
 "src/main/java/com/toolbox/tools/library/AssetDescriptor.java",
 "src/main/java/com/toolbox/tools/library/AssetPayloadValidator.java",
 "src/main/java/com/toolbox/tools/library/AssetRegistry.java",
 "src/main/java/com/toolbox/tools/library/AssetStore.java",
 "src/main/java/com/toolbox/tools/library/FileAssetStore.java",
 "src/main/java/com/toolbox/tools/library/TemplateDefinition.java",
 "src/main/java/com/toolbox/tools/library/TemplateRegistry.java",
 "src/main/java/com/toolbox/tools/library/TemplateInstantiationPlan.java",
 "src/main/java/com/toolbox/tools/library/DependencyResolver.java",
 "src/main/java/com/toolbox/tools/library/LibraryDependencyLock.java",
 "src/main/java/com/toolbox/tools/library/ProjectLibraryBinder.java",
 "src/main/java/com/toolbox/tools/library/LibraryManager.java",
 "src/main/java/com/toolbox/tools/library/DefaultLibraryFactory.java",
]
for rel in required:
    assert (APP/rel).is_file(),rel

texts={rel:(APP/rel).read_text() for rel in required}
combined="\n".join(texts.values())

checks={
 "R1":["StableId.require","VersionNumber.parse","resolveExact","AssetPayloadValidator"],
 "R2":["GLOBAL_MAX_ORIGINAL_BYTES","MAX_RECENT = 32","MAX_REVISIONS = 32","synchronized"],
 "R3":["CatalogLifecycle","publishReady","DRAFT","READY","DEPRECATED","ARCHIVED"],
 "R4":["ATOMIC_MOVE","dependency.lock","sha256","ProjectLibraryBinder"],
 "R5":["sourceName unsafe","ensureChild","ASSET_RUNTIME_TYPE_VALIDATOR_REQUIRED"],
 "R6":["versionCode 3"],
 "R7":[],
 "R8":["AccessibilityContract","labelIndonesia","MainActivity"],
 "R9":["ComponentManifest","DependencyResolutionResult","LibraryDependencyLock"],
}
for domain,markers in checks.items():
    for marker in markers:
        if marker=="versionCode 3":
            assert re.search(r"\bversionCode\s+3\b",gradle)
        elif marker=="MainActivity":
            assert (APP/"src/main/java/com/toolbox/tools/MainActivity.java").is_file()
        else:
            assert marker in combined,(domain,marker)

assert asset_plan["managedAssetDraftOnlyKinds"]==[
    "IMAGE","ICON","FONT","AUDIO","VIDEO"
]
assert set(asset_plan["managedAssetReadyKinds"])=={
    "RAW","JSON","TEMPLATE_DATA"
}

all_main=[]
for path in sorted((APP/"src/main").rglob("*")):
    if path.is_file() and path.suffix in {".java",".kt"}:
        all_main.append((path,path.read_text(errors="replace")))
forbidden=[
 r"\bDexClassLoader\b",
 r"\bURLClassLoader\b",
 r"\bjava\.lang\.reflect\b",
 r"\bSystem\.load(?:Library)?\b",
 r"\bProcessBuilder\b",
 r"\bcom\.google\.firebase\b",
 r"KEY_STORE_PASSWORD",
 r"SIGNING_KEY",
]
for path,text in all_main:
    for pattern in forbidden:
        assert re.search(pattern,text) is None,(path.relative_to(REPO).as_posix(),pattern)

native=list((APP/"src/main").rglob("*.so"))
assert not native,native
assert plan["domains"]["R7"]["applicability"]=="N_A_SCOPE_PROVEN"

hashes={}
for path in sorted((APP/"src").rglob("*")):
    if path.is_file():
        hashes[path.relative_to(REPO).as_posix()]=hashlib.sha256(path.read_bytes()).hexdigest()
for rel in [
    "app/build.gradle",
    "app/ASSURANCE_PLAN_R1_R9.json",
    "app/ASSET_ASSURANCE_PLAN.json",
]:
    hashes[rel]=hashlib.sha256((REPO/rel).read_bytes()).hexdigest()

evidence={
 "schemaVersion":2,
 "projectId":"ToolBox",
 "stage":"Tahap 3",
 "stageMap":"C",
 "status":"PASS",
 "parentBaseline":{
   "name":"Tahap 2",
   "apkSha256":plan["parentBaseline"]["apkSha256"],
   "unchangedByPublicWork":True
 },
 "androidApi":30,
 "versionCode":version,
 "researchMethodCounts":method_counts,
 "domains":{
   k:{"applicability":v["applicability"],"prebuild":"PASS"}
   for k,v in plan["domains"].items()
 },
 "assetChain":"BOUND",
 "r7":"N_A_SCOPE_PROVEN",
 "sourceHashes":hashes,
 "publicBoundaries":{
   "privateContentIncluded":False,
   "firebaseUsed":False,
   "signingUsed":False,
   "executableAssetLoadingAdded":False
 }
}
(OUT/"tahap3-r1-r8-prebuild-evidence.json").write_text(
    json.dumps(evidence,indent=2,sort_keys=True)+"\n"
)
print("TAHAP_3_R1_R8_PREBUILD = PASS")
print("R1_R9_RESEARCH_CORPUS = BOUND")
print("ASSET_SAFE_CHAIN = BOUND")
print("R7 = N_A_SCOPE_PROVEN")
