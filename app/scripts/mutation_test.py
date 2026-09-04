#!/usr/bin/env python3
import json, subprocess
from pathlib import Path

APP=Path(__file__).resolve().parents[1]
REPO=APP.parent
OUT=APP/"build"/"assurance"
OUT.mkdir(parents=True,exist_ok=True)

mutations=[
 {
  "name":"r1_stable_id_path_escape",
  "path":APP/"src/main/java/com/toolbox/tools/core/StableId.java",
  "old":'Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}")',
  "new":'Pattern.compile(".+")',
  "test":"com.toolbox.tools.core.ProjectDefinitionCodecTest.invalidStableIdIsRejected"
 },
 {
  "name":"r1_reference_integrity_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/core/ProjectValidator.java",
  "old":"if (!state.resources().containsKey(target)) {",
  "new":"if (false) {",
  "test":"com.toolbox.tools.core.ProjectDefinitionCodecTest.missingReferenceFailsClosed"
 },
 {
  "name":"r3_recovery_dirty_guard_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/core/ProjectManager.java",
  "old":"if (dirty || savedRevision <= 0 || current.revision() != savedRevision) {",
  "new":"if (false) {",
  "test":"com.toolbox.tools.core.RecoverySnapshotStoreTest.finalSnapshotCannotCaptureDirtyWorkingState"
 }
]

killed=[]
for mutation in mutations:
    path=mutation["path"]
    original=path.read_text()
    assert mutation["old"] in original,(mutation["name"],"mutation target missing")
    try:
        path.write_text(original.replace(mutation["old"],mutation["new"],1))
        result=subprocess.run(
            [
              "gradle","--no-daemon",
              ":app:testDebugUnitTest",
              "--tests",mutation["test"],
              "--rerun-tasks"
            ],
            cwd=REPO,
            text=True,
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
 "schemaVersion":1,
 "status":"PASS",
 "mutationsTotal":len(mutations),
 "mutationsKilled":len(killed),
 "mutationsEscaped":0,
 "killed":killed
}
(OUT/"tahap2-mutation-evidence.json").write_text(json.dumps(evidence,indent=2,sort_keys=True)+"\n")
print("TAHAP_2_R9_MUTATION = PASS")
print("MUTATION_ESCAPE = 0")
