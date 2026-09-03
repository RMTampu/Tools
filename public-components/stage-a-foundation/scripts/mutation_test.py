#!/usr/bin/env python3
import json
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parents[1]
BUILD = ROOT / "build" / "assurance"
BUILD.mkdir(parents=True, exist_ok=True)
dependency_sources = [
    REPO / "public-components/runtime-contracts/src/main/java/io/toolbox/contracts/runtime/Contracts.java",
    REPO / "public-components/runtime-contracts/src/main/java/io/toolbox/contracts/runtime/ProductRegistry.java",
    REPO / "public-components/runtime-safety-contracts/src/main/java/io/toolbox/contracts/safety/SafetyContracts.java",
    REPO / "public-components/runtime-safety-contracts/src/main/java/io/toolbox/contracts/safety/DiagnosticBuffer.java",
    REPO / "public-components/runtime-safety-contracts/src/main/java/io/toolbox/contracts/safety/ResourceGuard.java",
    REPO / "public-components/runtime-safety-contracts/src/main/java/io/toolbox/contracts/safety/RecoveryMachine.java",
]
stage_sources = sorted((ROOT / "src/main/java/io/toolbox/stagea").glob("*.java"))
test_sources = sorted((ROOT / "src/test/java/io/toolbox/stagea").glob("*.java"))
mutations = [
    {"id":"capability_availability_bypass","file":"ExecutionGuard.java","old":"if (availability == null || availability == StageAContracts.Availability.UNAVAILABLE) return reject(\"admission.capability.unavailable\", stableActionId);","new":"if (false) return reject(\"admission.capability.unavailable\", stableActionId);","occurrence":1},
    {"id":"resource_reject_bypass","file":"ExecutionGuard.java","old":"if (resourceDecision.mode() == SafetyContracts.GuardMode.REJECT) return reject(\"admission.resource.rejected\", stableActionId);","new":"if (false) return reject(\"admission.resource.rejected\", stableActionId);","occurrence":1},
    {"id":"recovery_persist_failure_not_quarantined","file":"RecoveryCoordinator.java","old":"state = SafetyContracts.RecoveryState.QUARANTINED;\n            throw new StageAContracts.StageAException(\"recovery.state.persist.failed\"","new":"state = transition.previous();\n            throw new StageAContracts.StageAException(\"recovery.state.persist.failed\"","occurrence":1},
    {"id":"safe_ui_quarantine_not_restricted","file":"SafeUiPolicy.java","old":"return new StageAContracts.SafeUiModel(true, true, \"safe.ui.quarantined\", state);","new":"return new StageAContracts.SafeUiModel(true, false, \"safe.ui.quarantined\", state);","occurrence":1},
    {"id":"health_quarantine_not_blocked","file":"HealthAggregator.java","old":"health = StageAContracts.HealthState.BLOCKED;","new":"health = StageAContracts.HealthState.DEGRADED;","occurrence":1},
    {"id":"diagnostic_code_separator_changed","file":"DiagnosticMapper.java","old":".replace('_', '.');","new":".replace('_', '-');","occurrence":1},
    {"id":"safe_ui_restore_authority_falsely_enabled","file":"SafeUiActionPolicy.java","old":"public static boolean canRestoreKnownGood() {\n        return false;\n    }","new":"public static boolean canRestoreKnownGood() {\n        return true;\n    }","occurrence":1},
    {"id":"safe_ui_quarantine_terminal_bypass","file":"SafeUiActionPolicy.java","old":"return Objects.requireNonNull(state, \"state\") != SafetyContracts.RecoveryState.QUARANTINED;","new":"return true;","occurrence":1},
]
def replace_occurrence(text, old, new, occurrence):
    start = -1; pos = 0
    for _ in range(occurrence):
        start = text.find(old, pos)
        if start < 0: raise AssertionError(f"mutation target not found: {old}")
        pos = start + len(old)
    return text[:start] + new + text[start + len(old):]
def copy_source(src, dst_root):
    rel = src.relative_to(REPO); dst = dst_root / rel; dst.parent.mkdir(parents=True, exist_ok=True); shutil.copy2(src, dst); return dst
results = []
for mutation in mutations:
    with tempfile.TemporaryDirectory(prefix="stage-a-mutation-") as tmp:
        tmp_root = Path(tmp)
        copied_main = [copy_source(path, tmp_root) for path in dependency_sources + stage_sources]
        copied_tests = [copy_source(path, tmp_root) for path in test_sources]
        target = tmp_root / ROOT.relative_to(REPO) / "src/main/java/io/toolbox/stagea" / mutation["file"]
        target.write_text(replace_occurrence(target.read_text(), mutation["old"], mutation["new"], mutation["occurrence"]))
        classes = tmp_root / "classes"; tests = tmp_root / "test-classes"; classes.mkdir(); tests.mkdir()
        compile_main = subprocess.run(["javac","--release","11","-Xlint:all","-Werror","-d",str(classes)] + [str(path) for path in copied_main], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        if compile_main.returncode != 0: raise AssertionError(f"mutation {mutation['id']} did not compile:\n" + compile_main.stdout)
        compile_test = subprocess.run(["javac","--release","11","-Xlint:all","-Werror","-cp",str(classes),"-d",str(tests)] + [str(path) for path in copied_tests], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        if compile_test.returncode != 0: raise AssertionError(f"test compilation failed for {mutation['id']}:\n" + compile_test.stdout)
        runs = []
        for test_class in ("io.toolbox.stagea.StageAFoundationSelfTest", "io.toolbox.stagea.SafeUiActionPolicyTest"):
            run = subprocess.run(["java","-ea","-cp",f"{classes}:{tests}",test_class], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
            runs.append({"class":test_class,"returnCode":run.returncode,"output":run.stdout})
        killed = any(row["returnCode"] != 0 for row in runs)
        results.append({"id":mutation["id"],"killed":killed,"testReturnCodes":{row["class"]:row["returnCode"] for row in runs}})
        if not killed:
            outputs = "\n".join(row["class"] + ":\n" + row["output"] for row in runs)
            raise AssertionError(f"mutation escaped: {mutation['id']}\n{outputs}")
        print(f"MUTATION_KILLED={mutation['id']}")
summary = {"schemaVersion":1,"status":"PASS","mutationsTotal":len(results),"mutationsKilled":sum(1 for row in results if row["killed"]),"mutationsEscaped":sum(1 for row in results if not row["killed"]),"results":results}
(BUILD / "mutation-evidence.json").write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n")
print("STAGE_A_MUTATION_CHALLENGE = PASS")
print(f"MUTATIONS_KILLED={summary['mutationsKilled']}")
