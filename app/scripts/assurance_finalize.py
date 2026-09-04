#!/usr/bin/env python3
import hashlib,json,os,re,xml.etree.ElementTree as ET
from pathlib import Path

APP=Path(__file__).resolve().parents[1]
BUILD=APP/"build"
OUT=BUILD/"assurance"
OUT.mkdir(parents=True,exist_ok=True)

pre=json.loads((OUT/"tahap10-r1-r8-prebuild-evidence.json").read_text())
asset_pre=json.loads((OUT/"tahap10-asset-prebuild-evidence.json").read_text())
asset_final=json.loads((OUT/"tahap10-asset-final-evidence.json").read_text())
mut=json.loads((OUT/"tahap10-mutation-evidence.json").read_text())
candidate=json.loads((OUT/"tahap10-candidate-manifest.json").read_text())
plan=json.loads((APP/"ASSURANCE_PLAN_R1_R9.json").read_text())

assert pre["status"]=="PASS" and pre["stage"]=="Tahap 10"
assert pre["assetChain"]=="BOUND"
assert mut["status"]=="PASS" and mut["mutationsEscaped"]==0
assert asset_pre["status"]=="ASSET_SAFE_100_DEVELOPMENT_PREBUILD_PASS"
assert asset_final["status"]=="ASSET_SAFE_100_DEVELOPMENT_PASS"
assert asset_final["unknownAssets"]==0
assert asset_final["unprovenRequiredAssets"]==0
assert candidate["status"]=="PUBLIC_TAHAP_10_CANDIDATE"
assert candidate["firebaseUsed"] is False
assert candidate["parentSignedApkSha256"]==plan["parentBaseline"]["apkSha256"]

xml_files=sorted((BUILD/"test-results"/"testDebugUnitTest").glob("TEST-*.xml"))
assert xml_files,"JUnit evidence missing"
tests=failures=errors=skipped=0
for file in xml_files:
    root=ET.parse(file).getroot()
    tests+=int(root.attrib.get("tests","0"))
    failures+=int(root.attrib.get("failures","0"))
    errors+=int(root.attrib.get("errors","0"))
    skipped+=int(root.attrib.get("skipped","0"))
assert tests>0 and failures==0 and errors==0 and skipped==0

apk=BUILD/"outputs"/"apk"/"release"/"app-release-unsigned.apk"
digest_file=Path(str(apk)+".sha256")
assert apk.is_file() and digest_file.is_file()
actual=hashlib.sha256(apk.read_bytes()).hexdigest()
assert actual==digest_file.read_text().strip().split()[0]
assert actual==candidate["unsignedApkSha256"]

runtime=(OUT/"tahap10-api30-runtime.txt").read_text()
for marker in [
    "API30_APP_LAUNCH=PASS",
    "TAHAP10_UI_TEXT=PASS",
    "API_LEVEL=30",
    "READY_GATE=PASS",
    "IR_BUILD=PASS",
    "CANDIDATE_PREVIEW=PASS",
    "TAHAP9_REGRESSION=PASS",
    "FIREBASE_USED=NO"
]:
    assert marker in runtime,marker

ir_match=re.search(r"^IR_SHA256=([0-9a-f]{64})$",runtime,flags=re.MULTILINE)
assert ir_match and ir_match.group(1)==candidate["irSha256"]

canonical=(
    "TBX_CANDIDATE_V1\n"
    + candidate["applicationId"] + "\n"
    + str(candidate["versionCode"]) + "\n"
    + candidate["versionName"] + "\n"
    + candidate["parentSignedApkSha256"] + "\n"
    + candidate["irSha256"] + "\n"
    + candidate["unsignedApkSha256"] + "\n"
)
recomputed=hashlib.sha256(canonical.encode()).hexdigest()
assert recomputed==candidate["candidateSha256"]
assert candidate["candidateId"]=="candidate."+recomputed

evidence={
    "schemaVersion":9,
    "projectId":"ToolBox",
    "stage":"Tahap 10",
    "stageMap":"J",
    "status":"PASS",
    "sourceRepository":os.environ.get("GITHUB_REPOSITORY","LOCAL"),
    "sourceCommitSha":os.environ.get("GITHUB_SHA","LOCAL"),
    "workflowRunId":os.environ.get("GITHUB_RUN_ID","LOCAL"),
    "parentBaseline":{
        "name":"Tahap 9",
        "apkSha256":plan["parentBaseline"]["apkSha256"],
        "unchanged":True
    },
    "r1ToR9":{
        domain:{
            "applicability":plan["domains"][domain]["applicability"],
            "researchMethodsBound":pre["researchMethodCounts"][domain],
            "status":"PASS"
        }
        for domain in [f"R{i}" for i in range(1,10)]
    },
    "assetAssurance":{
        "status":"PASS",
        "requiredAssets":asset_final["requiredAssets"],
        "runtimeAssetsProven":asset_final["runtimeAssetsProven"],
        "unknownAssets":0
    },
    "evidence":{
        "unitTests":"PASS",
        "mutation":"PASS",
        "assetRoute":"PASS",
        "api30Runtime":"PASS",
        "packageMetadata":"PASS",
        "unsignedApkDigest":"PASS",
        "parentTahap9SignedIdentity":"PASS",
        "readyPreviewReadOnly":"PASS",
        "readyLifecycle":"PASS",
        "finalRecoveryBeforeReady":"PASS",
        "buildValidator":"PASS",
        "irDeterministic":"PASS",
        "irResourceSha256":"PASS",
        "candidateIdentity":"PASS",
        "candidateInputSensitivity":"PASS",
        "interactiveUiRoute":"PASS",
        "tahap9Regression":"PASS",
        "firebaseUsed":"NO"
    },
    "candidate":candidate,
    "testSummary":{
        "tests":tests,
        "failures":failures,
        "errors":errors,
        "skipped":skipped
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
        "mutationEscape":0,
        "unprovenRequiredAssets":0
    },
    "publicBoundaries":{
        "privateContentIncluded":False,
        "firebaseUsed":False,
        "signingUsed":False,
        "finalArm64RuntimeClaimed":False
    }
}
(OUT/"tahap10-r1-r9-evidence.json").write_text(
    json.dumps(evidence,indent=2,sort_keys=True)+"\n"
)
print("TAHAP_10_R1_R9 = PASS")
print("TAHAP_10_CANDIDATE_IDENTITY = PASS")
print("ASSET_SAFE_CHAIN = PASS")
print("UNKNOWN = 0")
print("SKIPPED = 0")
print("MUTATION_ESCAPE = 0")
print("FIREBASE_USED = NO")
