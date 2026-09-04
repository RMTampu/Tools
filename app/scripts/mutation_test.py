#!/usr/bin/env python3
import json
import subprocess
from pathlib import Path

APP=Path(__file__).resolve().parents[1]
REPO=APP.parent
OUT=APP/"build"/"assurance"
OUT.mkdir(parents=True,exist_ok=True)

mutations=[
 {
  "name":"r1_stable_id_domain_escape",
  "path":APP/"src/main/java/com/toolbox/tools/core/StableId.java",
  "old":'Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}")',
  "new":'Pattern.compile(".+")',
  "test":"com.toolbox.tools.library.AssetLibraryTest.unsafeSourceNameFailsBeforeStoragePathExists"
 },
 {
  "name":"r1_component_exact_version_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/library/ComponentRegistry.java",
  "old":"return versions.get(version);",
  "new":"return versions.lastEntry().getValue();",
  "test":"com.toolbox.tools.library.ComponentRegistryTest.readyComponentHasCompleteManifestAndExactVersionPinning"
 },
 {
  "name":"r4_asset_hash_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/library/FileAssetStore.java",
  "old":"if (!DigestUtils.sha256(bytes).equals(descriptor.sha256())) {",
  "new":"if (false) {",
  "test":"com.toolbox.tools.library.AssetLibraryTest.fileStoreKeepsOriginalWhenCacheIsClearedAndRelinkIsHashBound"
 },
 {
  "name":"asset_runtime_validator_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/library/AssetPayloadValidator.java",
  "old":'errors.add("ASSET_RUNTIME_TYPE_VALIDATOR_REQUIRED");',
  "new":'/* mutation: runtime type accepted */',
  "test":"com.toolbox.tools.library.AssetLibraryTest.invalidJsonAndUnsupportedRuntimeTypeCannotBecomeReady"
 },
 {
  "name":"component_dependency_gate_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/library/ComponentValidator.java",
  "old":"if (dependency.required()\n                    && !componentRegistry.hasCompatible(",
  "new":"if (false && dependency.required()\n                    && !componentRegistry.hasCompatible(",
  "test":"com.toolbox.tools.library.ComponentRegistryTest.unresolvedRequiredDependencyBlocksReady"
 },
 {
  "name":"dependency_lock_integrity_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/library/LibraryDependencyLock.java",
  "old":"|| !MessageDigest.isEqual(",
  "new":"|| false && !MessageDigest.isEqual(",
  "test":"com.toolbox.tools.library.LibraryDependencyLockTest.dependencyLockChecksumMutationFailsClosed"
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
 "schemaVersion":2,
 "stage":"Tahap 3",
 "status":"PASS",
 "mutationsTotal":len(mutations),
 "mutationsKilled":len(killed),
 "mutationsEscaped":0,
 "killed":killed
}
(OUT/"tahap3-mutation-evidence.json").write_text(
    json.dumps(evidence,indent=2,sort_keys=True)+"\n"
)
print("TAHAP_3_R9_MUTATION = PASS")
print("MUTATION_ESCAPE = 0")
