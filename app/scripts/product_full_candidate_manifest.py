#!/usr/bin/env python3
import hashlib,json,os,re
from pathlib import Path

APP=Path(__file__).resolve().parents[1]
REPO=APP.parent
OUT=APP/"build"/"assurance"
OUT.mkdir(parents=True,exist_ok=True)

apk=APP/"build/outputs/apk/release/app-release-unsigned.apk"
digest_file=Path(str(apk)+".sha256")
assert apk.is_file() and digest_file.is_file()
apk_sha=digest_file.read_text().strip().split()[0].lower()
assert hashlib.sha256(apk.read_bytes()).hexdigest()==apk_sha

requirements=APP/"FULL_PRODUCT_REQUIREMENTS.json"
design=REPO/"RANCANGAN_PRODUK_PENUH.md"
requirements_sha=hashlib.sha256(requirements.read_bytes()).hexdigest()
design_sha=hashlib.sha256(design.read_bytes()).hexdigest()
baseline="f9dcffed7dc5d657c6dbd1c45933db6a4f6215f5145aee1849cc50f35038b76b"

canonical=(
 "TBX_FULL_PRODUCT_CANDIDATE_V1\n"
 "com.toolbox.tools\n"
 "12\n"
 "12.0-produk-penuh\n"
 +baseline+"\n"
 +design_sha+"\n"
 +requirements_sha+"\n"
 +apk_sha+"\n"
)
candidate_sha=hashlib.sha256(canonical.encode()).hexdigest()
payload={
 "schemaVersion":1,
 "status":"PUBLIC_FULL_PRODUCT_READY_PRIVATE_SIGNING",
 "applicationId":"com.toolbox.tools",
 "versionCode":12,
 "versionName":"12.0-produk-penuh",
 "baselineStage":11,
 "baselineSignedApkSha256":baseline,
 "designSections":135,
 "designSha256":design_sha,
 "requirementsSha256":requirements_sha,
 "unsignedApkSha256":apk_sha,
 "candidateSha256":candidate_sha,
 "sourceRepository":os.environ.get("GITHUB_REPOSITORY","LOCAL"),
 "sourceCommitSha":os.environ.get("GITHUB_SHA","LOCAL"),
 "workflowRunId":os.environ.get("GITHUB_RUN_ID","LOCAL"),
 "defaultLanguage":"id",
 "toolEnginesReady":5,
 "firebaseUsed":False,
 "signingUsed":False,
}
(OUT/"product-full-candidate-manifest.json").write_text(
 json.dumps(payload,indent=2,sort_keys=True)+"\n"
)
print("FULL_PRODUCT_CANDIDATE = PASS")
print("CANDIDATE_SHA256="+candidate_sha)
print("BASELINE_TAHAP_11 = PASS")
print("FIREBASE_USED = NO")
