#!/usr/bin/env python3
import json,subprocess
from pathlib import Path

APP=Path(__file__).resolve().parents[1]
REPO=APP.parent
OUT=APP/"build"/"assurance"
OUT.mkdir(parents=True,exist_ok=True)

mutations=[
 {
  "name":"declarative_executable_escape",
  "path":APP/"src/main/java/com/toolbox/tools/product/DeclarativeProjectRuntime.java",
  "old":'throw new IllegalArgumentException(\n                "resource tidak didukung tanpa rebuild: " + id\n        );',
  "new":'return AuthoringSection.UI;',
  "test":"com.toolbox.tools.product.FullProductArchitectureTest.runtimeDeklaratifMenolakKapabilitasExecutableBaru"
 },
 {
  "name":"multi_heavy_tool_escape",
  "path":APP/"src/main/java/com/toolbox/tools/product/ToolLifecycleManager.java",
  "old":"performRelease(active);",
  "new":"states.put(active, State.ACTIVE);",
  "test":"com.toolbox.tools.product.FullProductArchitectureTest.limaToolMemakaiLifecycleSatuFungsiBeratAktif"
 },
 {
  "name":"incremental_validation_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/core/ProjectManager.java",
  "old":"if (!lastIncrementalValidation.isPass()) {",
  "new":"if (false) {",
  "test":"com.toolbox.tools.product.ProductProductionContractsTest.projectManagerRejectsInvalidIncrementalMutation"
 },
 {
  "name":"managed_protocol_version_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/protocol/ManagedAppProtocol.java",
  "old":"if (protocolVersion < 1\n                    || protocolVersion > CURRENT_VERSION) {",
  "new":"if (false) {",
  "test":"com.toolbox.tools.protocol.ManagedAppProtocolTest.rejectsUnsupportedProtocolAndEmptyNegotiation"
 },
 {
  "name":"full_product_verifier_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/product/FullProductVerifier.java",
  "old":"&& available.size() == ProductCapability.values().length;",
  "new":";",
  "test":"com.toolbox.tools.product.FullProductVerifierNegativeTest.kekuranganWajibHarusMemblokirProduk"
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
 "schemaVersion":12,
 "status":"PASS",
 "mutationsTotal":len(mutations),
 "mutationsKilled":len(killed),
 "mutationsEscaped":0,
 "killed":killed,
}
(OUT/"product-full-mutation-evidence.json").write_text(
 json.dumps(evidence,indent=2,sort_keys=True)+"\n"
)
print("PRODUCT_FULL_R9_MUTATION = PASS")
print("MUTATION_ESCAPE = 0")
