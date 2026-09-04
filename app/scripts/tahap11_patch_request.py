#!/usr/bin/env python3
import hashlib,json,os
from pathlib import Path

APP=Path(__file__).resolve().parents[1]
OUT=APP/"build"/"assurance"
OUT.mkdir(parents=True,exist_ok=True)
candidate=json.loads((OUT/"tahap11-candidate-manifest.json").read_text())

def sha(value):
    return hashlib.sha256(value.encode()).hexdigest()

upserts={
    "app.release.candidate":candidate["candidateSha256"],
    "app.release.ir":candidate["irSha256"],
}
payload_canonical="TBX_PATCH_PAYLOAD_V1\n"
for key in sorted(upserts):
    value=upserts[key]
    payload_canonical+=f"upsert|{key}|{len(value.encode())}|{sha(value)}\n"
payload_sha=sha(payload_canonical)

manifest={
    "schemaVersion":1,
    "patchId":"patch.tahap11.app",
    "projectId":"project.default",
    "baseRevision":10,
    "targetRevision":11,
    "parentSignedApkSha256":candidate["parentSignedApkSha256"],
    "targetCandidateSha256":candidate["candidateSha256"],
    "rollbackBaselineApkSha256":candidate["rollbackBaselineApkSha256"],
    "payloadSha256":payload_sha,
}
manifest_canonical=(
    "TBX_PATCH_V1\n"
    +manifest["patchId"]+"\n"
    +manifest["projectId"]+"\n"
    +str(manifest["baseRevision"])+"\n"
    +str(manifest["targetRevision"])+"\n"
    +manifest["parentSignedApkSha256"]+"\n"
    +manifest["targetCandidateSha256"]+"\n"
    +manifest["rollbackBaselineApkSha256"]+"\n"
    +manifest["payloadSha256"]+"\n"
)
manifest_sha=sha(manifest_canonical)
signed_message=(
    "TBX_REMOTE_PATCH_V1\n"
    +manifest_sha+"\n"
    +manifest["targetCandidateSha256"]+"\n"
    +manifest["rollbackBaselineApkSha256"]+"\n"
)
request={
    "schemaVersion":1,
    "status":"PUBLIC_TAHAP_11_PATCH_REQUEST",
    "manifest":manifest,
    "manifestCanonical":manifest_canonical,
    "manifestSha256":manifest_sha,
    "payload":{
        "upserts":upserts,
        "deletes":[],
        "canonical":payload_canonical,
        "sha256":payload_sha,
    },
    "remoteSignedMessage":signed_message,
    "requiredSignerCertificateSha256":
        "290fb37d527935766e327781833493400dd647cfc8bdbe433254a2df52e4b8e4",
    "publicSourceRepository":os.environ.get("GITHUB_REPOSITORY","LOCAL"),
    "publicSourceCommitSha":os.environ.get("GITHUB_SHA","LOCAL"),
    "publicWorkflowRunId":os.environ.get("GITHUB_RUN_ID","LOCAL"),
    "firebaseUsed":False,
}
(OUT/"tahap11-patch-request.json").write_text(
    json.dumps(request,indent=2,sort_keys=True)+"\n"
)
print("TAHAP_11_APP_PATCH_REQUEST = PASS")
print("PATCH_MANIFEST_SHA256="+manifest_sha)
print("PATCH_PAYLOAD_SHA256="+payload_sha)
print("REMOTE_VERIFICATION_REQUIRED = YES")
print("SAFE_RESTORE_REQUIRED = YES")
print("FIREBASE_USED = NO")
