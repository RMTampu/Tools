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
  "name":"patch_host_compatibility_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/delivery/PatchManifest.java",
  "old":"if (versionCode < minHostVersionCode\n                || versionCode > maxHostVersionCode) {",
  "new":"if (false) {",
  "test":"com.toolbox.tools.product.MaximalProductionClosureTest.patchManifestV2ClosesCompatibilityCapabilitiesAndFiles"
 },
 {
  "name":"runtime_pressure_degradation_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/product/ResourceGuard.java",
  "old":"previewQuality = 0.5f;\n                preloadEnabled = false;\n                releaseGeneration++;",
  "new":"previewQuality = 1.0f;\n                preloadEnabled = true;\n                releaseGeneration++;",
  "test":"com.toolbox.tools.product.MaximalProductionClosureTest.memoryPressureActuallyReducesWorkingSetPolicy"
 },
 {
  "name":"patch_journal_phase_persistence_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/delivery/PatchTransactionJournal.java",
  "old":"state.put(KEY_PHASE, Objects.requireNonNull(phase, \"phase\").name());",
  "new":"state.remove(KEY_PHASE);",
  "test":"com.toolbox.tools.product.MaximalProductionClosureTest.interruptedPatchJournalRollsBackOnBootstrap"
 },
 {
  "name":"runtime_state_put_persistence_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/core/FileRuntimeStateStore.java",
  "old":"values.put(\n                StableId.require(key, \"runtimeStateKey\"),\n                java.util.Objects.requireNonNull(value, \"value\")\n        );\n        persist();",
  "new":"values.put(\n                StableId.require(key, \"runtimeStateKey\"),\n                java.util.Objects.requireNonNull(value, \"value\")\n        );",
  "test":"com.toolbox.tools.product.MaximalProductionClosureTest.runtimeStateSurvivesFreshStoreInstance"
 },
 {
  "name":"patch_runtime_apk_identity_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/delivery/SafePatchManager.java",
  "old":"if (!runtimeParentApkSha256.equals(\n                    manifest.parentSignedApkSha256()\n            )) {",
  "new":"if (false) {",
  "test":"com.toolbox.tools.delivery.SafePatchManagerTest.v2PatchRejectsRuntimeApkLineageMismatch"
 },
 {
  "name":"legacy_patch_schema_downgrade",
  "path":APP/"src/main/java/com/toolbox/tools/delivery/SafePatchManager.java",
  "old":"if (runtimeApkIdentityBound()\n                && manifest.schemaVersion() < 2) {",
  "new":"if (false) {",
  "test":"com.toolbox.tools.delivery.SafePatchManagerTest.boundRuntimeRejectsLegacyV1Patch"
 },
 {
  "name":"managed_target_host_rebind_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/delivery/SafePatchManager.java",
  "old":"this.hostPackageName = packageName;",
  "new":"this.hostPackageName = com.toolbox.tools.BuildConfig.APPLICATION_ID;",
  "test":"com.toolbox.tools.delivery.SafePatchManagerTest.managedTargetHostContextRejectsWrongTargetPackage"
 },
 {
  "name":"production_patch_schema_policy_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/delivery/EvolutionPackagePolicy.java",
  "old":"if (schemaVersion != PatchManifest.CURRENT_SCHEMA_VERSION) {",
  "new":"if (false) {",
  "test":"com.toolbox.tools.product.MaximalProductionClosureTest.productionEvolutionPackagePolicyRejectsLegacySchema"
 },
 {
  "name":"post_activation_health_rollback_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/delivery/SafePatchManager.java",
  "old":"if (!healthGate.isHealthy(committed)) {",
  "new":"if (false) {",
  "test":"com.toolbox.tools.delivery.SafePatchManagerTest.postActivationHealthFailureRollsBackAutomatically"
 },
 {
  "name":"import_decompression_ratio_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/product/ImportSecurityValidator.java",
  "old":"if (!Double.isFinite(ratio)\n                        || ratio > MAX_DECOMPRESSION_RATIO) {",
  "new":"if (false) {",
  "test":"com.toolbox.tools.product.MaximalProductionClosureTest.importSecurityRejectsTraversalBombExecutableAndUntrustedPackage"
 },
 {
  "name":"import_merge_reference_remap_bypass",
  "path":APP/"src/main/java/com/toolbox/tools/product/ImportMergeManager.java",
  "old":"mappedTargets.add(mapId(idMap, targetId));",
  "new":"mappedTargets.add(targetId);",
  "test":"com.toolbox.tools.product.MaximalProductionClosureTest.importAndMergePreserveNewProjectAndRemapConflictingReferences"
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
 "schemaVersion":13,
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
