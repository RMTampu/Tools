#!/usr/bin/env python3
import json,subprocess
from pathlib import Path
APP=Path(__file__).resolve().parents[1];REPO=APP.parent;OUT=APP/"build"/"assurance";OUT.mkdir(parents=True,exist_ok=True)
mutations=[
{"name":"stable_id_escape","path":APP/"src/main/java/com/toolbox/tools/core/StableId.java","old":'Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}")',"new":'Pattern.compile(".+")',"test":"com.toolbox.tools.core.ProjectDefinitionCodecTest.invalidStableIdIsRejected"},
{"name":"external_record_limit_bypass","path":APP/"src/main/java/com/toolbox/tools/integration/ExternalSnapshot.java","old":"if(records==null||records.size()>MAX_RECORDS)","new":"if(records==null)","test":"com.toolbox.tools.integration.ExternalNormalizationTest.externalBudgetsRejectOversizeInput"},
{"name":"duplicate_external_id_bypass","path":APP/"src/main/java/com/toolbox/tools/integration/ExternalNormalizer.java","old":"if(seen.put(stable,Boolean.TRUE)!=null)","new":"if(false)","test":"com.toolbox.tools.integration.ExternalNormalizationTest.duplicateExternalIdentityFailsClosed"},
{"name":"sync_conflict_bypass","path":APP/"src/main/java/com/toolbox/tools/integration/SyncEngine.java","old":"if(localDirty&&revision>=0&&remote.revision()!=revision)","new":"if(false)","test":"com.toolbox.tools.integration.ExportSyncTest.syncIsIdempotentAndConflictIsExplicit"},
{"name":"sync_history_bound_bypass","path":APP/"src/main/java/com/toolbox/tools/integration/SyncEngine.java","old":"while(history.size()>MAX_HISTORY)history.removeFirst();","new":"while(false)history.removeFirst();","test":"com.toolbox.tools.integration.ExportSyncTest.syncHistoryIsBounded"}
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
e={"schemaVersion":6,"stage":"Tahap 7","status":"PASS","mutationsTotal":len(mutations),"mutationsKilled":len(killed),"mutationsEscaped":0,"killed":killed}
(OUT/"tahap7-mutation-evidence.json").write_text(json.dumps(e,indent=2,sort_keys=True)+"\n")
print("TAHAP_7_R9_MUTATION = PASS");print("MUTATION_ESCAPE = 0")
