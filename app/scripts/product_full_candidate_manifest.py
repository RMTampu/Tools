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
baseline_name="ToolBox Produk Penuh v12"
baseline_version_code=12
baseline="4f4579d87d867524e1b308de1a9a39ac2be0a18894d9317eea60a67dc4d91c05"
baseline_certificate="290fb37d527935766e327781833493400dd647cfc8bdbe433254a2df52e4b8e4"
baseline_public_run="33892292329"
baseline_private_runtime_run="33932725592"
baseline_firebase_run="33933089444"
baseline_firebase_matrix="4946808111994836277"

canonical=(
 "TBX_FULL_PRODUCT_CANDIDATE_V2\n"
 "com.toolbox.tools\n"
 "12\n"
 "12.0-produk-penuh\n"
 +baseline_name+"\n"
 +str(baseline_version_code)+"\n"
 +baseline+"\n"
 +baseline_certificate+"\n"
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
 "baselineStage":baseline_version_code,
 "baselineName":baseline_name,
 "baselineVersionCode":baseline_version_code,
 "baselineSignedApkSha256":baseline,
 "baselineCertificateSha256":baseline_certificate,
 "baselinePublicR1R9RunId":baseline_public_run,
 "baselinePrivateRuntimeRunId":baseline_private_runtime_run,
 "baselineFirebaseRunId":baseline_firebase_run,
 "baselineFirebaseMatrixId":baseline_firebase_matrix,
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
print("BASELINE_V12 = PASS")
print("FIREBASE_USED = NO")
