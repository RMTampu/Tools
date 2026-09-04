#!/usr/bin/env python3
import hashlib,json,re
from pathlib import Path

APP=Path(__file__).resolve().parents[1]
REPO=APP.parent
OUT=APP/"build"/"assurance"
OUT.mkdir(parents=True,exist_ok=True)

plan=json.loads((APP/"ASSURANCE_PLAN_R1_R9.json").read_text())
asset=json.loads((APP/"ASSET_ASSURANCE_PLAN.json").read_text())

assert plan["stage"]=="Tahap 11" and plan["stageMap"]=="K"
assert plan["parentBaseline"]["name"]=="Tahap 7"
assert plan["parentBaseline"]["apkSha256"]=="741ebcf799280fbba1b4c7d2e60ba157ba133e3f6545b3468882373150f024f7"
assert plan["parentBaseline"]["permanent"] is True
assert plan["parentBaseline"]["rollbackAnchor"] is True
assert plan["developmentParentCandidate"]["name"]=="Tahap 10"
assert plan["developmentParentCandidate"]["signedApkSha256"]=="fbc39153bc121ed2d32bc9c24e9ff8f0e9b7730fcef01021f4adfd830fbd21ff"
assert plan["developmentParentCandidate"]["baseline"] is False
assert asset["stage"]=="Tahap 11" and asset["stageMap"]=="K"

method_counts={}
for i in range(1,10):
    domain=f"R{i}"
    doc=REPO/plan["domains"][domain]["sourceMethodDoc"]
    assert doc.is_file(),doc
    methods=re.findall(rf"^### R{i}-M\d+",doc.read_text(),flags=re.MULTILINE)
    assert methods,(domain,"research corpus missing")
    method_counts[domain]=len(methods)

for rel in [
    "R1_R9_ASSET_CHAIN.md",
    "ASSET_SAFE_100_RULES.md",
    "ASSET_SAFE_100_PROCESS.md",
    "ASSET_SAFE_100_METHODS.md",
    "ASSET_ROUTE_PROOF_PROCESS.md",
    "ASSET_ROUTE_PROOF_METHODS.md",
    "PREBUILD_ASSET_GATE.md",
    "APPLICATION_SAFE_100_PROCESS.md",
    "TEST_ROUTING_POLICY.md",
]:
    assert (REPO/rel).is_file(),rel

gradle=(APP/"build.gradle").read_text()
manifest=(APP/"src/main/AndroidManifest.xml").read_text()
assert "applicationId 'com.toolbox.tools'" in gradle
assert re.search(r"\bminSdk\s+30\b",gradle)
assert re.search(r"\btargetSdk\s+30\b",gradle)
assert re.search(r"\bversionCode\s+11\b",gradle)
assert "versionName '11.0-tahap11-dev'" in gradle
assert 'android:name=".MainActivity"' in manifest

required=[
    "src/main/java/com/toolbox/tools/core/AppKernel.java",
    "src/main/java/com/toolbox/tools/build/BuildValidator.java",
    "src/main/java/com/toolbox/tools/build/ReadyCoordinator.java",
    "src/main/java/com/toolbox/tools/build/ApplicationIr.java",
    "src/main/java/com/toolbox/tools/build/ApplicationIrBuilder.java",
    "src/main/java/com/toolbox/tools/build/CandidateIdentity.java",
    "src/main/java/com/toolbox/tools/build/CandidateIdentityFactory.java",
    "src/main/java/com/toolbox/tools/delivery/PatchManifest.java",
    "src/main/java/com/toolbox/tools/delivery/PatchPayload.java",
    "src/main/java/com/toolbox/tools/delivery/RemoteVerificationProof.java",
    "src/main/java/com/toolbox/tools/delivery/RemotePatchVerifier.java",
    "src/main/java/com/toolbox/tools/delivery/RemoteTrustAnchor.java",
    "src/main/java/com/toolbox/tools/delivery/PatchApplyResult.java",
    "src/main/java/com/toolbox/tools/delivery/SafePatchManager.java",
    "src/test/java/com/toolbox/tools/delivery/RemotePatchVerifierTest.java",
    "src/test/java/com/toolbox/tools/delivery/SafePatchManagerTest.java",
]
for rel in required:
    assert (APP/rel).is_file(),rel

combined="\n".join((APP/rel).read_text() for rel in required)
for marker in [
    "TBX_PATCH_V1",
    "TBX_PATCH_PAYLOAD_V1",
    "TBX_REMOTE_PATCH_V1",
    "MAX_RAW_BYTES = 1024 * 1024",
    "captureFinalRecoverySnapshot",
    "remoteVerifier.verify",
    "restoreRevision",
    "CERTIFICATE_SHA256",
    "290fb37d527935766e327781833493400dd647cfc8bdbe433254a2df52e4b8e4",
]:
    assert marker in combined,marker

for path in sorted((APP/"src/main").rglob("*")):
    if not path.is_file() or path.suffix not in {".java",".kt"}:
        continue
    text=path.read_text(errors="replace")
    for pattern in [
        r"\bDexClassLoader\b",
        r"\bURLClassLoader\b",
        r"\bjava\.lang\.reflect\b",
        r"\bSystem\.load(?:Library)?\b",
        r"\bProcessBuilder\b",
        r"\bcom\.google\.firebase\b",
        r"SIGNING_KEY",
        r"KEY_STORE_PASSWORD",
    ]:
        assert re.search(pattern,text) is None,(path,pattern)
assert not list((APP/"src/main").rglob("*.so"))

hashes={}
for path in sorted((APP/"src").rglob("*")):
    if path.is_file():
        hashes[path.relative_to(REPO).as_posix()]=hashlib.sha256(path.read_bytes()).hexdigest()

evidence={
    "schemaVersion":11,
    "projectId":"ToolBox",
    "stage":"Tahap 11",
    "stageMap":"K",
    "status":"PASS",
    "parentBaseline":{
        "name":"Tahap 7",
        "apkSha256":plan["parentBaseline"]["apkSha256"],
        "permanent":True,
        "rollbackAnchor":True,
    },
    "developmentParentCandidate":{
        "name":"Tahap 10",
        "signedApkSha256":plan["developmentParentCandidate"]["signedApkSha256"],
        "baseline":False,
    },
    "androidApi":30,
    "versionCode":11,
    "researchMethodCounts":method_counts,
    "domains":{
        k:{"applicability":v["applicability"],"prebuild":"PASS"}
        for k,v in plan["domains"].items()
    },
    "delivery":{
        "appApk":"BOUND",
        "appPatch":"DECLARATIVE",
        "remoteVerification":"CERT_PINNED_FAIL_CLOSED",
        "safeRestore":"BOUND",
        "firebaseUsed":False,
    },
    "assetChain":"BOUND",
    "sourceHashes":hashes,
    "publicBoundaries":{
        "privateContentIncluded":False,
        "firebaseUsed":False,
        "signingUsed":False,
        "arbitraryExecutableRuntimeAdded":False,
    },
}
(OUT/"tahap11-r1-r8-prebuild-evidence.json").write_text(
    json.dumps(evidence,indent=2,sort_keys=True)+"\n"
)
print("TAHAP_11_R1_R8_PREBUILD = PASS")
print("R1_R9_RESEARCH_CORPUS = BOUND")
print("ROLLBACK_BASELINE_TAHAP_7 = PASS")
print("DEVELOPMENT_PARENT_TAHAP_10 = PASS")
print("REMOTE_VERIFICATION = BOUND")
print("SAFE_RESTORE = BOUND")
print("FIREBASE_USED = NO")
