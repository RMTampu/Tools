#!/usr/bin/env python3
import hashlib,json,os,re
from pathlib import Path

APP=Path(__file__).resolve().parents[1]
OUT=APP/"build"/"assurance"
OUT.mkdir(parents=True,exist_ok=True)

runtime=(OUT/"tahap10-api30-runtime.txt").read_text()
match=re.search(r"^IR_SHA256=([0-9a-f]{64})$",runtime,flags=re.MULTILINE)
assert match,"IR_SHA256 missing"
ir_sha=match.group(1)

apk=APP/"build/outputs/apk/release/app-release-unsigned.apk"
digest_file=Path(str(apk)+".sha256")
assert apk.is_file() and digest_file.is_file()
unsigned_sha=digest_file.read_text().strip().split()[0].lower()
assert re.fullmatch(r"[0-9a-f]{64}",unsigned_sha)

application_id="com.toolbox.tools"
version_code=10
version_name="10.0-tahap10-dev"
parent="8f6f504c8f289926ad88550ab2686b801efc3ac12536c9e57f807b208461a116"

canonical=(
    "TBX_CANDIDATE_V1\n"
    + application_id + "\n"
    + str(version_code) + "\n"
    + version_name + "\n"
    + parent + "\n"
    + ir_sha + "\n"
    + unsigned_sha + "\n"
)
candidate_sha=hashlib.sha256(canonical.encode()).hexdigest()

payload={
    "schemaVersion":1,
    "status":"PUBLIC_TAHAP_10_CANDIDATE",
    "candidateId":"candidate."+candidate_sha,
    "candidateSha256":candidate_sha,
    "applicationId":application_id,
    "versionCode":version_code,
    "versionName":version_name,
    "parentSignedApkSha256":parent,
    "irSha256":ir_sha,
    "unsignedApkSha256":unsigned_sha,
    "publicSourceRepository":os.environ.get("GITHUB_REPOSITORY","LOCAL"),
    "publicSourceCommitSha":os.environ.get("GITHUB_SHA","LOCAL"),
    "publicWorkflowRunId":os.environ.get("GITHUB_RUN_ID","LOCAL"),
    "firebaseUsed":False
}
(OUT/"tahap10-candidate-manifest.json").write_text(
    json.dumps(payload,indent=2,sort_keys=True)+"\n"
)
print("TAHAP_10_CANDIDATE_IDENTITY = PASS")
print("CANDIDATE_SHA256="+candidate_sha)
print("FIREBASE_USED = NO")
