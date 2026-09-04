#!/usr/bin/env python3
import json,subprocess
from pathlib import Path

APP=Path(__file__).resolve().parents[1]
REPO=APP.parent
OUT=APP/"build"/"assurance"
OUT.mkdir(parents=True,exist_ok=True)

mutations=[
 {
  "name":"stable_id_escape",
  "path":APP/"src/main/java/com/toolbox/tools/core/StableId.java",
  "old":'Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}")',
  "new":'Pattern.compile(".+")',
  "test":"com.toolbox.tools.core.ProjectDefinitionCodecTest.invalidStableIdIsRejected"
 },
 {
  "name":"patch_remote_verification_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/delivery/SafePatchManager.java",
  "old":"if (!remoteVerifier.verify(manifest, payload, proof)) {",
  "new":"if (false && !remoteVerifier.verify(manifest, payload, proof)) {",
  "test":"com.toolbox.tools.delivery.SafePatchManagerTest.invalidRemoteProofIsRejectedWithoutMutation"
 },
 {
  "name":"patch_recovery_snapshot_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/delivery/SafePatchManager.java",
  "old":"projectManager.captureFinalRecoverySnapshot();",
  "new":"/* mutation: recovery snapshot bypass */",
  "test":"com.toolbox.tools.delivery.SafePatchManagerTest.applyCapturesRecoveryPointBeforeMutation"
 },
 {
  "name":"patch_payload_hash_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/delivery/RemotePatchVerifier.java",
  "old":"if(!manifest.payloadSha256().equals(payload.sha256())) return false;",
  "new":"if(false && !manifest.payloadSha256().equals(payload.sha256())) return false;",
  "test":"com.toolbox.tools.delivery.RemotePatchVerifierTest.validRemoteSignaturePassesAndTamperingFails"
 }
]

killed=[]
for mutation in mutations:
    path=mutation["path"]
    original=path.read_text()
    assert mutation["old"] in original,(mutation["name"],"target missing")
    try:
        path.write_text(original.replace(mutation["old"],mutation["new"],1))
        result=subprocess.run(
            [
                "gradle","--no-daemon",
                ":app:testDebugUnitTest",
                "--tests",mutation["test"],
                "--rerun-tasks",
            ],
            cwd=REPO,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
        )
        if result.returncode==0:
            print(result.stdout)
            raise SystemExit("MUTATION_ESCAPED="+mutation["name"])
        killed.append(mutation["name"])
        print("MUTATION_KILLED="+mutation["name"])
    finally:
        path.write_text(original)

evidence={
    "schemaVersion":11,
    "stage":"Tahap 11",
    "status":"PASS",
    "mutationsTotal":len(mutations),
    "mutationsKilled":len(killed),
    "mutationsEscaped":0,
    "killed":killed,
}
(OUT/"tahap11-mutation-evidence.json").write_text(
    json.dumps(evidence,indent=2,sort_keys=True)+"\n"
)
print("TAHAP_11_R9_MUTATION = PASS")
print("MUTATION_ESCAPE = 0")
