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
  "name":"bubble_safe_clamp_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/editor/BubblePositionStore.java",
  "old":"EditorPoint clamped = safeBounds.clampTopLeft(",
  "new":"EditorPoint clamped = requested == null ? null : requested; /* mutation */ /*",
  "test":"com.toolbox.tools.editor.BubbleShellTest.bubbleIsBoundedAndStoresPerOrientation",
  "close_comment":true
 },
 {
  "name":"live_capability_gate_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/editor/EditorShellController.java",
  "old":"if (next == EditorMode.LIVE && !liveCapability) {",
  "new":"if (false) {",
  "test":"com.toolbox.tools.editor.BubbleShellTest.previewHidesOverlayAndLiveRequiresCapability"
 },
 {
  "name":"edge_capability_gate_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/editor/EdgePanelFactory.java",
  "old":"if (capabilities.supports(capability)) {",
  "new":"if (true) {",
  "test":"com.toolbox.tools.editor.EdgeFloatingTest.edgeChangesContextAndOnlyShowsSupportedCapabilities"
 },
 {
  "name":"visual_lock_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/editor/VisualEditorSession.java",
  "old":"if (lockSet(operation.objectId()).isLocked(operation.capability())) {",
  "new":"if (false) {",
  "test":"com.toolbox.tools.editor.VisualEditorSessionTest.lockPreventsMutationAndRecordsDiagnostic"
 },
 {
  "name":"history_bound_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/editor/VisualHistory.java",
  "old":"while (undo.size() > MAX_HISTORY) {",
  "new":"while (false) {",
  "test":"com.toolbox.tools.editor.VisualEditorSessionTest.historyIsBounded"
 }
]

killed=[]
for m in mutations:
    p=m["path"]; original=p.read_text()
    old=m["old"]
    assert old in original,(m["name"],"mutation target missing")
    mutated=original.replace(old,m["new"],1)
    if m.get("close_comment"):
        marker="                bubbleSize,\n                bubbleSize\n        );"
        assert marker in mutated,(m["name"],"close marker missing")
        mutated=mutated.replace(marker,marker+" */",1)
    try:
        p.write_text(mutated)
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

e={"schemaVersion":4,"stage":"Tahap 5","status":"PASS","mutationsTotal":len(mutations),"mutationsKilled":len(killed),"mutationsEscaped":0,"killed":killed}
(OUT/"tahap5-mutation-evidence.json").write_text(json.dumps(e,indent=2,sort_keys=True)+"\n")
print("TAHAP_5_R9_MUTATION = PASS")
print("MUTATION_ESCAPE = 0")
