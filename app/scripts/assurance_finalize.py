#!/usr/bin/env python3
import hashlib, json, os, xml.etree.ElementTree as ET
from pathlib import Path

APP=Path(__file__).resolve().parents[1]
REPO=APP.parent
BUILD=APP/"build"
OUT=BUILD/"assurance"
OUT.mkdir(parents=True,exist_ok=True)

pre=json.loads((OUT/"tahap2-r1-r8-prebuild-evidence.json").read_text())
mut=json.loads((OUT/"tahap2-mutation-evidence.json").read_text())
plan=json.loads((APP/"ASSURANCE_PLAN_R1_R9.json").read_text())

assert pre["status"]=="PASS"
assert mut["status"]=="PASS" and mut["mutationsEscaped"]==0
assert set(pre["domains"])=={f"R{i}" for i in range(1,10)}
assert all(v["prebuild"]=="PASS" for v in pre["domains"].values())
assert all(pre["researchMethodCounts"][f"R{i}"]>0 for i in range(1,10))

xml_dir=BUILD/"test-results"/"testDebugUnitTest"
xml_files=sorted(xml_dir.glob("TEST-*.xml"))
assert xml_files,"JUnit evidence missing"
tests=failures=errors=skipped=0
for file in xml_files:
    root=ET.parse(file).getroot()
    tests+=int(root.attrib.get("tests","0"))
    failures+=int(root.attrib.get("failures","0"))
    errors+=int(root.attrib.get("errors","0"))
    skipped+=int(root.attrib.get("skipped","0"))
assert tests>0
assert failures==0 and errors==0 and skipped==0,(tests,failures,errors,skipped)

apk=BUILD/"outputs"/"apk"/"release"/"app-release-unsigned.apk"
digest_file=Path(str(apk)+".sha256")
assert apk.is_file() and apk.stat().st_size>0
assert digest_file.is_file()
actual=hashlib.sha256(apk.read_bytes()).hexdigest()
recorded=digest_file.read_text().strip().split()[0]
assert actual==recorded

evidence={
 "schemaVersion":1,
 "projectId":"ToolBox",
 "stage":"Tahap 2",
 "stageMap":"B",
 "status":"PASS",
 "sourceRepository":os.environ.get("GITHUB_REPOSITORY","LOCAL_PUBLIC_VALIDATION"),
 "sourceCommitSha":os.environ.get("GITHUB_SHA","LOCAL_PUBLIC_VALIDATION"),
 "workflowRunId":os.environ.get("GITHUB_RUN_ID","LOCAL_PUBLIC_VALIDATION"),
 "r1ToR9":{
   domain:{
     "applicability":plan["domains"][domain]["applicability"],
     "researchMethodsBound":pre["researchMethodCounts"][domain],
     "status":"PASS"
   } for domain in [f"R{i}" for i in range(1,10)]
 },
 "evidence":{
   "unitTests":"PASS",
   "mutation":"PASS",
   "packageMetadata":"PASS",
   "unsignedApkDigest":"PASS",
   "baselineTahap1Compatibility":"PASS"
 },
 "testSummary":{
   "tests":tests,"failures":failures,"errors":errors,"skipped":skipped
 },
 "artifact":{
   "fileName":apk.name,
   "sha256":actual,
   "sizeBytes":apk.stat().st_size
 },
 "closure":{
   "unknown":0,
   "skipped":0,
   "staleEvidence":0,
   "mutationEscape":0
 },
 "publicBoundaries":{
   "privateContentIncluded":False,
   "firebaseUsed":False,
   "signingUsed":False,
   "finalApplicationSafe100Claimed":False
 }
}
(OUT/"tahap2-r1-r9-evidence.json").write_text(json.dumps(evidence,indent=2,sort_keys=True)+"\n")
print("TAHAP_2_R1_R9 = PASS")
print("UNKNOWN = 0")
print("SKIPPED = 0")
print("MUTATION_ESCAPE = 0")
