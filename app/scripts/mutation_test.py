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
  "name":"renderer_exact_component_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/library/ComponentRegistry.java",
  "old":"return versions.get(version);",
  "new":"return versions.lastEntry().getValue();",
  "test":"com.toolbox.tools.runtime.RendererSharedModelTest.missingExactComponentProducesDiagnosticWithoutDeletingInstance"
 },
 {
  "name":"navigation_target_validation_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/runtime/NavigationManager.java",
  "old":"if (model.screen(route.targetScreenId()) == null) {",
  "new":"if (false) {",
  "test":"com.toolbox.tools.runtime.NavigationActionTest.brokenNavigationReferenceIsExplicitDiagnostic"
 },
 {
  "name":"binding_cycle_guard_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/runtime/BindingCycleGuard.java",
  "old":"if (active.contains(key)) {",
  "new":"if (false) {",
  "test":"com.toolbox.tools.runtime.DataBindingTest.bindingCompatibilityIsExactAndTwoWayCycleIsSuppressed"
 },
 {
  "name":"flow_port_type_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/runtime/FlowValidator.java",
  "old":"if (fromPort.type() != toPort.type()) {",
  "new":"if (false) {",
  "test":"com.toolbox.tools.runtime.FlowGraphTest.incompatiblePortConnectionFailsValidation"
 },
 {
  "name":"watchdog_step_limit_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/runtime/FlowWatchdog.java",
  "old":"if (steps > MAX_STEPS) {",
  "new":"if (false) {",
  "test":"com.toolbox.tools.runtime.FlowGraphTest.watchdogStopsStepAndTimeRunaway"
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

e={"schemaVersion":3,"stage":"Tahap 4","status":"PASS","mutationsTotal":len(mutations),"mutationsKilled":len(killed),"mutationsEscaped":0,"killed":killed}
(OUT/"tahap4-mutation-evidence.json").write_text(json.dumps(e,indent=2,sort_keys=True)+"\n")
print("TAHAP_4_R9_MUTATION = PASS")
print("MUTATION_ESCAPE = 0")
