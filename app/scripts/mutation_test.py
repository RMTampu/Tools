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
  "name":"ready_dirty_gate_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/build/BuildValidator.java",
  "old":"if (kernel.projectManager().hasUnsavedChanges()) {",
  "new":"if (false) {",
  "test":"com.toolbox.tools.build.ReadyCoordinatorTest.dirtyProjectBlocksReady"
 },
 {
  "name":"ready_live_gate_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/build/BuildValidator.java",
  "old":"if (live == LiveSessionState.DIRTY",
  "new":"if (false && live == LiveSessionState.DIRTY",
  "test":"com.toolbox.tools.build.ReadyCoordinatorTest.dirtyLiveSessionBlocksReady"
 },
 {
  "name":"ready_repair_gate_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/build/BuildValidator.java",
  "old":"if (repair == RepairPhase.STAGED",
  "new":"if (false && repair == RepairPhase.STAGED",
  "test":"com.toolbox.tools.build.ReadyCoordinatorTest.stagedRepairBlocksReady"
 },
 {
  "name":"ready_final_recovery_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/build/ReadyCoordinator.java",
  "old":"kernel.projectManager().captureFinalRecoverySnapshot();",
  "new":"/* mutation: recovery snapshot bypass */",
  "test":"com.toolbox.tools.build.ReadyCoordinatorTest.readyPreviewIsReadOnlyAndPublishIsRevisioned"
 },
 {
  "name":"ir_raw_payload_leak",
  "path":APP/"src/main/java/com/toolbox/tools/build/ApplicationIrBuilder.java",
  "old":"sha256(entry.getValue())",
  "new":"entry.getValue()",
  "test":"com.toolbox.tools.build.ApplicationIrTest.irIsDeterministicStableKeyedAndReadOnly"
 },
 {
  "name":"candidate_parent_identity_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/build/CandidateIdentityFactory.java",
  "old":'+ parentSignedApkSha256 + "\\n"',
  "new":'+ "" + "\\n"',
  "test":"com.toolbox.tools.build.CandidateIdentityTest.everyIdentityInputChangesCandidate"
 }
]

killed=[]
for mutation in mutations:
    path=mutation["path"]
    original=path.read_text()
    assert mutation["old"] in original,(mutation["name"],"target missing")
    try:
        path.write_text(original.replace(
            mutation["old"],mutation["new"],1
        ))
        result=subprocess.run(
            [
                "gradle","--no-daemon",
                ":app:testDebugUnitTest",
                "--tests",mutation["test"],
                "--rerun-tasks"
            ],
            cwd=REPO,text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT
        )
        if result.returncode==0:
            print(result.stdout)
            raise SystemExit("MUTATION_ESCAPED="+mutation["name"])
        killed.append(mutation["name"])
        print("MUTATION_KILLED="+mutation["name"])
    finally:
        path.write_text(original)

evidence={
    "schemaVersion":9,
    "stage":"Tahap 10",
    "status":"PASS",
    "mutationsTotal":len(mutations),
    "mutationsKilled":len(killed),
    "mutationsEscaped":0,
    "killed":killed
}
(OUT/"tahap10-mutation-evidence.json").write_text(
    json.dumps(evidence,indent=2,sort_keys=True)+"\n"
)
print("TAHAP_10_R9_MUTATION = PASS")
print("MUTATION_ESCAPE = 0")
