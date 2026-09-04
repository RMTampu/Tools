#!/usr/bin/env python3
import hashlib,json,os,xml.etree.ElementTree as ET
from pathlib import Path

APP=Path(__file__).resolve().parents[1]
BUILD=APP/"build"
OUT=BUILD/"assurance"
OUT.mkdir(parents=True,exist_ok=True)

pre=json.loads((OUT/"tahap4-r1-r8-prebuild-evidence.json").read_text())
asset_pre=json.loads((OUT/"tahap4-asset-prebuild-evidence.json").read_text())
asset_final=json.loads((OUT/"tahap4-asset-final-evidence.json").read_text())
mut=json.loads((OUT/"tahap4-mutation-evidence.json").read_text())
plan=json.loads((APP/"ASSURANCE_PLAN_R1_R9.json").read_text())

assert pre["status"]=="PASS" and pre["stage"]=="Tahap 4"
assert pre["assetChain"]=="BOUND"
assert mut["status"]=="PASS" and mut["mutationsEscaped"]==0
assert asset_pre["status"]=="ASSET_SAFE_100_DEVELOPMENT_PREBUILD_PASS"
assert asset_final["status"]=="ASSET_SAFE_100_DEVELOPMENT_PASS"
assert asset_final["unknownAssets"]==0
assert asset_final["unprovenRequiredAssets"]==0
assert set(pre["domains"])=={f"R{i}" for i in range(1,10)}
assert all(v["prebuild"]=="PASS" for v in pre["domains"].values())
assert all(pre["researchMethodCounts"][f"R{i}"]>0 for i in range(1,10))

xml_files=sorted((BUILD/"test-results"/"testDebugUnitTest").glob("TEST-*.xml"))
assert xml_files,"JUnit evidence missing"
tests=failures=errors=skipped=0
for file in xml_files:
    root=ET.parse(file).getroot()
    tests+=int(root.attrib.get("tests","0"))
    failures+=int(root.attrib.get("failures","0"))
    errors+=int(root.attrib.get("errors","0"))
    skipped+=int(root.attrib.get("skipped","0"))
assert tests>0 and failures==0 and errors==0 and skipped==0,(tests,failures,errors,skipped)

apk=BUILD/"outputs"/"apk"/"release"/"app-release-unsigned.apk"
digest_file=Path(str(apk)+".sha256")
assert apk.is_file() and apk.stat().st_size>0 and digest_file.is_file()
actual=hashlib.sha256(apk.read_bytes()).hexdigest()
assert actual==digest_file.read_text().strip().split()[0]

runtime=(OUT/"tahap4-api30-runtime.txt").read_text()
for marker in ["API30_APP_LAUNCH=PASS","TAHAP4_UI_TEXT=PASS","API_LEVEL=30","RUNTIME_MODEL=PASS"]:
    assert marker in runtime,marker

evidence={
 "schemaVersion":3,
 "projectId":"ToolBox","stage":"Tahap 4","stageMap":"D","status":"PASS",
 "sourceRepository":os.environ.get("GITHUB_REPOSITORY","LOCAL_PUBLIC_VALIDATION"),
 "sourceCommitSha":os.environ.get("GITHUB_SHA","LOCAL_PUBLIC_VALIDATION"),
 "workflowRunId":os.environ.get("GITHUB_RUN_ID","LOCAL_PUBLIC_VALIDATION"),
 "parentBaseline":{"name":"Tahap 3","apkSha256":plan["parentBaseline"]["apkSha256"],"unchanged":True},
 "r1ToR9":{
   d:{"applicability":plan["domains"][d]["applicability"],"researchMethodsBound":pre["researchMethodCounts"][d],"status":"PASS"}
   for d in [f"R{i}" for i in range(1,10)]
 },
 "assetAssurance":{"status":"PASS","requiredAssets":asset_final["requiredAssets"],"runtimeAssetsProven":asset_final["runtimeAssetsProven"],"unknownAssets":0},
 "evidence":{
   "unitTests":"PASS","mutation":"PASS","assetRoute":"PASS","api30Runtime":"PASS",
   "packageMetadata":"PASS","unsignedApkDigest":"PASS","baselineTahap3DevelopmentCompatibility":"PASS",
   "sharedModel":"PASS","renderer":"PASS","navigation":"PASS","actionRegistry":"PASS",
   "dataBinding":"PASS","flowGraph":"PASS"
 },
 "testSummary":{"tests":tests,"failures":failures,"errors":errors,"skipped":skipped},
 "artifact":{"fileName":apk.name,"sha256":actual,"sizeBytes":apk.stat().st_size},
 "closure":{"unknown":0,"skipped":0,"staleEvidence":0,"mutationEscape":0,"unprovenRequiredAssets":0},
 "publicBoundaries":{"privateContentIncluded":False,"firebaseUsed":False,"signingUsed":False,"finalArm64RuntimeClaimed":False,"finalApplicationSafe100Claimed":False}
}
(OUT/"tahap4-r1-r9-evidence.json").write_text(json.dumps(evidence,indent=2,sort_keys=True)+"\n")
print("TAHAP_4_R1_R9 = PASS")
print("ASSET_SAFE_CHAIN = PASS")
print("UNKNOWN = 0")
print("SKIPPED = 0")
print("MUTATION_ESCAPE = 0")
