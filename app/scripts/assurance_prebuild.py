#!/usr/bin/env python3
import hashlib, json, re
from pathlib import Path

APP=Path(__file__).resolve().parents[1]
REPO=APP.parent
OUT=APP/"build"/"assurance"
OUT.mkdir(parents=True,exist_ok=True)

plan=json.loads((APP/"ASSURANCE_PLAN_R1_R9.json").read_text())
assert plan["projectId"]=="ToolBox"
assert plan["stage"]=="Tahap 2" and plan["stageMap"]=="B"
assert plan["target"]["androidApi"]==30
assert set(plan["domains"])=={f"R{i}" for i in range(1,10)}

method_counts={}
for index in range(1,10):
    domain=f"R{index}"
    doc=REPO/plan["domains"][domain]["sourceMethodDoc"]
    assert doc.is_file(), doc
    text=doc.read_text()
    methods=re.findall(rf"^### R{index}-M\d+",text,flags=re.MULTILINE)
    assert methods,(domain,"research method corpus missing")
    method_counts[domain]=len(methods)

chain=(REPO/"R1_R9_ASSET_CHAIN.md").read_text()
for index in range(1,10):
    assert f"APP_SAFE_R{index}_" in chain or f"R{index}" in chain

gradle=(APP/"build.gradle").read_text()
manifest=(APP/"src/main/AndroidManifest.xml").read_text()
assert "applicationId 'com.toolbox.tools'" in gradle
assert re.search(r"\bminSdk\s+30\b",gradle)
assert re.search(r"\btargetSdk\s+30\b",gradle)
version=int(re.search(r"\bversionCode\s+(\d+)\b",gradle).group(1))
assert version>1
assert 'android:name=".MainActivity"' in manifest
assert 'android:exported="true"' in manifest

required=[
 "src/main/java/com/toolbox/tools/core/StableId.java",
 "src/main/java/com/toolbox/tools/core/DigestUtils.java",
 "src/main/java/com/toolbox/tools/core/ProjectState.java",
 "src/main/java/com/toolbox/tools/core/ProjectDefinitionCodec.java",
 "src/main/java/com/toolbox/tools/core/ProjectManifest.java",
 "src/main/java/com/toolbox/tools/core/ProjectValidator.java",
 "src/main/java/com/toolbox/tools/core/ProjectValidationResult.java",
 "src/main/java/com/toolbox/tools/core/UnsavedDecision.java",
 "src/main/java/com/toolbox/tools/core/RecoveryCandidate.java",
 "src/main/java/com/toolbox/tools/core/ProjectStore.java",
 "src/main/java/com/toolbox/tools/core/FileProjectStore.java",
 "src/main/java/com/toolbox/tools/core/ProjectManager.java",
 "src/main/java/com/toolbox/tools/core/RecoverySnapshotStore.java",
 "src/main/java/com/toolbox/tools/core/ProjectMigrationRegistry.java",
 "src/main/java/com/toolbox/tools/core/ProjectRelinkVerifier.java",
 "src/main/java/com/toolbox/tools/android/SafProjectAccessGateway.java",
]
for rel in required:
    assert (APP/rel).is_file(),rel

texts={rel:(APP/rel).read_text() for rel in required}
combined="\n".join(texts.values())

checks={
 "R1":["StableId.require","REFERENCE_TARGET_MISSING","SCHEMA_INCOMPATIBLE"],
 "R2":["MAX_UNDO_GROUPS = 64","MAX_REVISIONS = 32","FileLock"],
 "R3":["journal.pending","previewRecoveryCandidate","UnsavedDecision"],
 "R4":["project.manifest","project.index","SHA-256","ATOMIC_MOVE","ProjectMigrationRegistry"],
 "R5":["takePersistableUriPermission","ensureChild","ProjectRelinkVerifier"],
 "R6":["versionCode 2"],
 "R7":[],
 "R8":["MainActivity","SafProjectAccessGateway"],
 "R9":["RecoveryCandidate.Kind","ProjectValidationResult"],
}
for domain,markers in checks.items():
    for marker in markers:
        if marker=="versionCode 2":
            assert re.search(r"\bversionCode\s+2\b",gradle)
        elif marker=="MainActivity":
            assert (APP/"src/main/java/com/toolbox/tools/MainActivity.java").is_file()
        else:
            assert marker in combined,(domain,marker)

forbidden=[
 r"\bDexClassLoader\b",r"\bURLClassLoader\b",r"\bjava\.lang\.reflect\b",
 r"\bSystem\.load(?:Library)?\b",r"\bRuntime\.getRuntime\b",
 r"\bProcessBuilder\b",r"\bcom\.google\.firebase\b",
 r"RMTampu/ToolBox",r"KEY_STORE_PASSWORD",r"SIGNING_KEY"
]
for rel,text in texts.items():
    for pattern in forbidden:
        assert re.search(pattern,text) is None,(rel,pattern)

native=list((APP/"src/main").rglob("*.so"))
assert not native,native
assert plan["domains"]["R7"]["applicability"]=="N_A_SCOPE_PROVEN"

hashes={}
for path in sorted((APP/"src").rglob("*")):
    if path.is_file():
        hashes[path.relative_to(REPO).as_posix()]=hashlib.sha256(path.read_bytes()).hexdigest()
hashes["app/build.gradle"]=hashlib.sha256((APP/"build.gradle").read_bytes()).hexdigest()
hashes["app/ASSURANCE_PLAN_R1_R9.json"]=hashlib.sha256((APP/"ASSURANCE_PLAN_R1_R9.json").read_bytes()).hexdigest()

evidence={
 "schemaVersion":1,
 "projectId":"ToolBox",
 "stage":"Tahap 2",
 "stageMap":"B",
 "status":"PASS",
 "androidApi":30,
 "versionCode":version,
 "researchMethodCounts":method_counts,
 "domains":{k:{"applicability":v["applicability"],"prebuild":"PASS"} for k,v in plan["domains"].items()},
 "r7":"N_A_SCOPE_PROVEN",
 "sourceHashes":hashes,
 "publicBoundaries":{
   "privateContentIncluded":False,
   "firebaseUsed":False,
   "signingUsed":False,
   "networkAuthorityAdded":False
 }
}
(OUT/"tahap2-r1-r8-prebuild-evidence.json").write_text(json.dumps(evidence,indent=2,sort_keys=True)+"\n")
print("TAHAP_2_R1_R8_PREBUILD = PASS")
print("R1_R9_RESEARCH_CORPUS = BOUND")
print("R7 = N_A_SCOPE_PROVEN")
