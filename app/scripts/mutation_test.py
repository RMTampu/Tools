#!/usr/bin/env python3
import json,subprocess
from pathlib import Path
APP=Path(__file__).resolve().parents[1];REPO=APP.parent;OUT=APP/"build"/"assurance";OUT.mkdir(parents=True,exist_ok=True)
mutations=[
{"name":"stable_id_escape","path":APP/"src/main/java/com/toolbox/tools/core/StableId.java","old":'Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}")',"new":'Pattern.compile(".+")',"test":"com.toolbox.tools.core.ProjectDefinitionCodecTest.invalidStableIdIsRejected"},
{"name":"repair_revision_gate_bypass","path":APP/"src/main/java/com/toolbox/tools/repair/RepairPlanValidator.java","old":"if (projectManager.savedRevision() != plan.baseRevision()) {","new":"if (false) {","test":"com.toolbox.tools.repair.RepairSessionManagerTest.staleRevisionAndProtectedCoreFailClosed"},
{"name":"protected_core_gate_bypass","path":APP/"src/main/java/com/toolbox/tools/repair/RepairPlanValidator.java","old":"if (isProtected(id)) {","new":"if (false) {","test":"com.toolbox.tools.repair.RepairSessionManagerTest.staleRevisionAndProtectedCoreFailClosed"},
{"name":"verify_expected_change_bypass","path":APP/"src/main/java/com/toolbox/tools/repair/RepairSessionManager.java","old":"if (!entry.getValue().equals(","new":"if (false && !entry.getValue().equals(","test":"com.toolbox.tools.repair.RepairSessionManagerTest.verificationFailureRollsBackAndRollbackIsIdempotent"},
{"name":"repair_history_bound_bypass","path":APP/"src/main/java/com/toolbox/tools/repair/RepairSessionManager.java","old":"while (history.size() > MAX_HISTORY) {","new":"while (false) {","test":"com.toolbox.tools.repair.RepairSessionManagerTest.repairHistoryIsBounded"}
]
killed=[]
for m in mutations:
 p=m["path"];original=p.read_text();assert m["old"] in original,(m["name"],"target missing")
 try:
  p.write_text(original.replace(m["old"],m["new"],1))
  r=subprocess.run(["gradle","--no-daemon",":app:testDebugUnitTest","--tests",m["test"],"--rerun-tasks"],cwd=REPO,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
  if r.returncode==0: print(r.stdout);raise SystemExit("MUTATION_ESCAPED="+m["name"])
  killed.append(m["name"]);print("MUTATION_KILLED="+m["name"])
 finally:p.write_text(original)
e={"schemaVersion":7,"stage":"Tahap 8","status":"PASS","mutationsTotal":len(mutations),"mutationsKilled":len(killed),"mutationsEscaped":0,"killed":killed}
(OUT/"tahap8-mutation-evidence.json").write_text(json.dumps(e,indent=2,sort_keys=True)+"\n")
print("TAHAP_8_R9_MUTATION = PASS");print("MUTATION_ESCAPE = 0")
