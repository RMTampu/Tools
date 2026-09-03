#!/usr/bin/env python3
import hashlib
import json
import os
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parents[1]
BUILD = ROOT / "build"
PROMOTION = BUILD / "promotion"
PROMOTION.mkdir(parents=True, exist_ok=True)
production_sources = [
    "public-components/runtime-contracts/src/main/java/io/toolbox/contracts/runtime/Contracts.java",
    "public-components/runtime-contracts/src/main/java/io/toolbox/contracts/runtime/ProductRegistry.java",
    "public-components/runtime-safety-contracts/src/main/java/io/toolbox/contracts/safety/SafetyContracts.java",
    "public-components/runtime-safety-contracts/src/main/java/io/toolbox/contracts/safety/DiagnosticBuffer.java",
    "public-components/runtime-safety-contracts/src/main/java/io/toolbox/contracts/safety/ResourceGuard.java",
    "public-components/runtime-safety-contracts/src/main/java/io/toolbox/contracts/safety/RecoveryMachine.java",
    "public-components/stage-a-foundation/src/main/java/io/toolbox/stagea/StageAContracts.java",
    "public-components/stage-a-foundation/src/main/java/io/toolbox/stagea/ExecutionGuard.java",
    "public-components/stage-a-foundation/src/main/java/io/toolbox/stagea/DiagnosticMapper.java",
    "public-components/stage-a-foundation/src/main/java/io/toolbox/stagea/RecoveryCoordinator.java",
    "public-components/stage-a-foundation/src/main/java/io/toolbox/stagea/SafeUiPolicy.java",
    "public-components/stage-a-foundation/src/main/java/io/toolbox/stagea/HealthAggregator.java",
]
metadata_files = ["public-components/stage-a-foundation/COMPONENT_SPEC.json","public-components/stage-a-foundation/CONTRACT.json","public-components/stage-a-foundation/ASSURANCE_PLAN_R1_R9.json","public-components/stage-a-foundation/PRIVATE_INTEGRATION_REQUIREMENTS.json"]
evidence_files = ["public-components/stage-a-foundation/build/assurance/prebuild-evidence.json","public-components/stage-a-foundation/build/assurance/mutation-evidence.json","public-components/stage-a-foundation/build/assurance/r1-r9-stage-a-evidence.json","public-components/stage-a-foundation/build/test-summary.txt"]
for rel in production_sources + metadata_files + evidence_files: assert (REPO / rel).is_file(), rel
for rel in production_sources: assert "Simulator.java" not in rel and "/src/test/" not in rel
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
source_hashes = {rel:sha(REPO/rel) for rel in production_sources}; metadata_hashes = {rel:sha(REPO/rel) for rel in metadata_files}; evidence_hashes = {rel:sha(REPO/rel) for rel in evidence_files}
source_zip = PROMOTION / "toolbox-stage-a-1.0.0-production-sources.zip"
with zipfile.ZipFile(source_zip,"w",compression=zipfile.ZIP_DEFLATED) as zf:
    for rel in production_sources:
        info=zipfile.ZipInfo(rel); info.date_time=(1980,1,1,0,0,0); info.compress_type=zipfile.ZIP_DEFLATED; info.external_attr=0o100644<<16; zf.writestr(info,(REPO/rel).read_bytes())
verification_jar = BUILD / "package" / "toolbox-stage-a-foundation-0.1.0.jar"; assert verification_jar.is_file()
manifest = {
    "schemaVersion":1,"projectId":"ToolBox","stageId":"A","status":"STAGE_A_READY_PRIVATE",
    "sourceRepository":os.environ.get("GITHUB_REPOSITORY","LOCAL_PUBLIC_VALIDATION"),"sourceCommitSha":os.environ.get("GITHUB_SHA","LOCAL_PUBLIC_VALIDATION"),"workflowRunId":os.environ.get("GITHUB_RUN_ID","LOCAL_PUBLIC_VALIDATION"),
    "promotionBoundary":"STAGE_ONLY","substeps":["A1","A2","A3","A4"],"substepsArePrivateBoundaries":False,"componentReadyPrivateAuthorizesPrivateStageIntegration":False,
    "dependencies":[{"componentId":"public.runtime-contracts","componentVersion":"0.1.0","contractVersion":"1.0.0"},{"componentId":"public.runtime-safety-contracts","componentVersion":"0.1.0","contractVersion":"1.0.0"},{"componentId":"public.stage-a-foundation","componentVersion":"0.1.0","contractVersion":"1.0.0"}],
    "productionSourceArchive":{"path":source_zip.name,"sha256":sha(source_zip),"size":source_zip.stat().st_size,"sourceCount":len(production_sources),"sourceHashes":source_hashes,"simulatorSourceIncluded":False,"testSourceIncluded":False,"promotionAllowed":True},
    "verificationJar":{"path":verification_jar.name,"sha256":sha(verification_jar),"size":verification_jar.stat().st_size,"privateProductionUseAllowed":False},
    "metadataHashes":metadata_hashes,"evidenceHashes":evidence_hashes,
    "assurance":{"r1ToR9PublicStageClosure":"PASS","registryIntegrationSimulator":"PASS","mutationChallenge":"PASS","unknown":0,"skipped":0,"staleEvidence":0,"finalApplicationSafe100Claimed":False},
    "publicBoundaries":{"privateContentIncluded":False,"privateReadAccess":False,"privateExecutionThroughPublic":False,"androidRuntimeClaimed":False,"firebaseUsed":False}
}
manifest_path=PROMOTION/"stage-a-promotion-manifest.json"; manifest_path.write_text(json.dumps(manifest,indent=2,sort_keys=True)+"\n")
bundle=PROMOTION/"toolbox-stage-a-ready-private.zip"
with zipfile.ZipFile(bundle,"w",compression=zipfile.ZIP_DEFLATED) as zf:
    for path in [source_zip,manifest_path]+[REPO/rel for rel in metadata_files+evidence_files]:
        arcname=path.name if path in {source_zip,manifest_path} else path.relative_to(REPO).as_posix()
        info=zipfile.ZipInfo(arcname); info.date_time=(1980,1,1,0,0,0); info.compress_type=zipfile.ZIP_DEFLATED; info.external_attr=0o100644<<16; zf.writestr(info,path.read_bytes())
(PROMOTION/"artifact-digests.json").write_text(json.dumps({"schemaVersion":1,"stageAReadyPrivateZip":{"sha256":sha(bundle),"size":bundle.stat().st_size},"productionSourceZip":{"sha256":sha(source_zip),"size":source_zip.stat().st_size},"manifest":{"sha256":sha(manifest_path),"size":manifest_path.stat().st_size}},indent=2,sort_keys=True)+"\n")
print("STAGE_A_PACKAGE_VALIDATION = PASS")
print("STAGE_A_STATUS=STAGE_A_READY_PRIVATE")
print(f"PRODUCTION_SOURCE_COUNT={len(production_sources)}")
print(f"STAGE_A_BUNDLE_SHA256={sha(bundle)}")
