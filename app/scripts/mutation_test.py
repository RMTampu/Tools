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
  "name":"capability_edit_door_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/live/CapabilityScanner.java",
  "old":"&& target.editDoor() == EditDoor.NONE",
  "new":"&& false",
  "test":"com.toolbox.tools.live.CapabilityScannerTest.noEditDoorCannotClaimWritableCapability"
 },
 {
  "name":"live_runtime_gate_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/live/LiveSessionManager.java",
  "old":"if (!target.installed() || !scan.liveAvailable()) {",
  "new":"if (false) {",
  "test":"com.toolbox.tools.live.LiveSessionManagerTest.liveRuntimeGateRejectsUnavailableTarget"
 },
 {
  "name":"self_edit_policy_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/live/SelfEditPolicy.java",
  "old":"if (!isDeclarativeEditable(resourceId)) {",
  "new":"if (false) {",
  "test":"com.toolbox.tools.live.LiveSessionManagerTest.selfEditRejectsProtectedAndNonDeclarativeSurfaces"
 },
 {
  "name":"live_stale_revision_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/live/LiveSessionManager.java",
  "old":"if (projectManager.savedRevision() != baseRevision",
  "new":"if (false && projectManager.savedRevision() != baseRevision",
  "test":"com.toolbox.tools.live.LiveSessionManagerTest.staleBaseRevisionBecomesConflictWithoutOverwrite"
 },
 {
  "name":"live_history_bound_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/live/LiveSessionManager.java",
  "old":"while (history.size() > MAX_HISTORY) {",
  "new":"while (false) {",
  "test":"com.toolbox.tools.live.LiveSessionManagerTest.liveChangeAndHistoryBudgetsAreBounded"
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
                "gradle","--no-daemon",":app:testDebugUnitTest",
                "--tests",mutation["test"],"--rerun-tasks"
            ],
            cwd=REPO,text=True,
            stdout=subprocess.PIPE,stderr=subprocess.STDOUT
        )
        if result.returncode==0:
            print(result.stdout)
            raise SystemExit("MUTATION_ESCAPED="+mutation["name"])
        killed.append(mutation["name"])
        print("MUTATION_KILLED="+mutation["name"])
    finally:
        path.write_text(original)

evidence={
    "schemaVersion":8,
    "stage":"Tahap 9",
    "status":"PASS",
    "mutationsTotal":len(mutations),
    "mutationsKilled":len(killed),
    "mutationsEscaped":0,
    "killed":killed
}
(OUT/"tahap9-mutation-evidence.json").write_text(
    json.dumps(evidence,indent=2,sort_keys=True)+"\n"
)
print("TAHAP_9_R9_MUTATION = PASS")
print("MUTATION_ESCAPE = 0")
