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
  "name":"search_limit_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/authoring/AuthoringSearchQuery.java",
  "old":"if (limit <= 0 || limit > MAX_RESULTS) {",
  "new":"if (limit <= 0) {",
  "test":"com.toolbox.tools.authoring.UnifiedAuthoringSearchTest.unifiedSearchIsStableBoundedFilteredAndDoesNotExecute"
 },
 {
  "name":"search_section_filter_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/authoring/AuthoringSearchQuery.java",
  "old":"if (section == null) return true;",
  "new":"if (true) return true;",
  "test":"com.toolbox.tools.authoring.UnifiedAuthoringSearchTest.unifiedSearchIsStableBoundedFilteredAndDoesNotExecute"
 },
 {
  "name":"draft_terminal_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/authoring/AuthoringDraftStore.java",
  "old":"if (draft.lifecycle().terminal()) {",
  "new":"if (false) {",
  "test":"com.toolbox.tools.authoring.AuthoringDraftStoreTest.draftRevisionIsMonotonicAndPublishRequiresValidation"
 },
 {
  "name":"draft_publish_validation_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/authoring/AuthoringDraftStore.java",
  "old":"if (previous.lifecycle() != DraftLifecycle.VALIDATED) {",
  "new":"if (false) {",
  "test":"com.toolbox.tools.authoring.AuthoringDraftStoreTest.draftRevisionIsMonotonicAndPublishRequiresValidation"
 },
 {
  "name":"template_dependency_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/authoring/TemplateAuthoringService.java",
  "old":"if (!dependencies.isPass()) {",
  "new":"if (false) {",
  "test":"com.toolbox.tools.authoring.TemplateAuthoringServiceTest.missingDependencyFailsClosedAndDoesNotPublish"
 }
]

killed=[]
for m in mutations:
    p=m["path"]; original=p.read_text()
    assert m["old"] in original,(m["name"],"mutation target missing")
    try:
        p.write_text(original.replace(m["old"],m["new"],1))
        result=subprocess.run(
          ["gradle","--no-daemon",":app:testDebugUnitTest","--tests",m["test"],"--rerun-tasks"],
          cwd=REPO,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT
        )
        if result.returncode==0:
            print(result.stdout)
            raise SystemExit("MUTATION_ESCAPED="+m["name"])
        killed.append(m["name"])
        print("MUTATION_KILLED="+m["name"])
    finally:
        p.write_text(original)

e={"schemaVersion":5,"stage":"Tahap 6","status":"PASS","mutationsTotal":len(mutations),"mutationsKilled":len(killed),"mutationsEscaped":0,"killed":killed}
(OUT/"tahap6-mutation-evidence.json").write_text(json.dumps(e,indent=2,sort_keys=True)+"\n")
print("TAHAP_6_R9_MUTATION = PASS")
print("MUTATION_ESCAPE = 0")
