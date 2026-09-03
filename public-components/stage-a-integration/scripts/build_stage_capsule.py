#!/usr/bin/env python3
from __future__ import annotations
import hashlib
import json
import os
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parents[1]
HOST = REPO / "public-components/stage-a-android-host"
FOUNDATION = REPO / "public-components/stage-a-foundation"
BUILD = ROOT / "build"
CAP_ROOT = BUILD / "capsule-root"
PRIVATE_VERIFICATION = CAP_ROOT / "verification"
MEMBERS_DIR = PRIVATE_VERIFICATION / "stage-a-capsule"
CAPSULE_PATH = PRIVATE_VERIFICATION / "stage-capsule.json"
CAPSULE_ZIP = BUILD / "toolbox-stage-a-sealed-capsule.zip"


def sha_bytes(data):
    return hashlib.sha256(data).hexdigest()


def sha(path):
    return sha_bytes(path.read_bytes())


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode()


def load(path):
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise RuntimeError(f"object required: {path}")
    return value


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def member_copy(source, name):
    destination = MEMBERS_DIR / name
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, destination)
    return {
        "path": destination.relative_to(CAP_ROOT).as_posix(),
        "sha256": sha(destination),
    }


def write_evidence(name, value):
    path = MEMBERS_DIR / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return {
        "path": path.relative_to(CAP_ROOT).as_posix(),
        "sha256": sha(path),
    }


def runtime_map(path):
    result = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            result[key.strip()] = value.strip()
    return result


def deterministic_zip(root, output):
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(p for p in root.rglob("*") if p.is_file()):
            rel = path.relative_to(root).as_posix()
            info = zipfile.ZipInfo(rel)
            info.date_time = (1980, 1, 1, 0, 0, 0)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, path.read_bytes())


def main():
    try:
        contract = ROOT / "CANONICAL_RECEIVER_CONTRACT.json"
        wiring = ROOT / "STAGE_WIRING_MANIFEST.json"
        acceptance = ROOT / "PUBLIC_HANDOFF_ACCEPTANCE_SCHEMA.json"
        independent = BUILD / "evidence/independent-wiring-verifier.json"
        reproducibility = BUILD / "evidence/reproducibility.json"
        runtime_summary = HOST / "build/host-runtime-summary.txt"
        apk = HOST / "build/android/stage-a-host-test.apk"
        promotion_manifest = FOUNDATION / "build/promotion/stage-a-promotion-manifest.json"
        production_zip = FOUNDATION / "build/promotion/toolbox-stage-a-1.1.0-production-sources.zip"
        for path in [contract, wiring, acceptance, independent, reproducibility, runtime_summary, apk, promotion_manifest, production_zip]:
            require(path.is_file(), f"missing capsule prerequisite: {path}")

        independent_value = load(independent)
        reproducibility_value = load(reproducibility)
        promotion = load(promotion_manifest)
        runtime = runtime_map(runtime_summary)

        require(independent_value.get("status") == "PASS" and independent_value.get("mutationEscape") == 0, "independent verifier not closed")
        require(reproducibility_value.get("status") == "PASS" and reproducibility_value.get("semanticPayloadIdentical") is True, "reproducibility not closed")
        require(runtime.get("ANDROID_API") == "30" and runtime.get("ABI") == "x86_64", "wrong Public runtime environment")
        require(runtime.get("REFERENCE_DUMMY") == "PASS", "reference dummy not proven")
        require(runtime.get("ADVERSARIAL_CONFORMANT_DUMMY") == "PASS", "adversarial dummy not proven")
        require(runtime.get("HOST_PROCESS_RESTART_READ") == "PASS" and runtime.get("HOST_CORRUPTION_REJECTION") == "PASS", "host lifecycle/failure proof incomplete")
        require(runtime.get("HOST_SAFE_UI_RENDER") == "PASS", "safe UI proof incomplete")
        require(runtime.get("NETWORK_CALLS") == "0" and runtime.get("FIREBASE_USED") == "0", "Public boundary violation")
        require(runtime.get("FINAL_ARM64_RUNTIME_CLAIMED") == "0", "Public cannot claim final arm64")

        require(promotion.get("status") == "STAGE_A_READY_PRIVATE", "promotion status")
        require(promotion.get("privateImplementationRequired") is False and promotion.get("privateWiringOnly") is True, "promotion wiring-only invariant")
        source = promotion.get("productionSourceArchive", {})
        require(source.get("sourceCount") == 20, "production source count")
        require(source.get("sha256") == sha(production_zip), "production archive digest mismatch")
        require(source.get("simulatorSourceIncluded") is False and source.get("testSourceIncluded") is False and source.get("androidTestHarnessIncluded") is False, "production archive contamination")
        require(promotion.get("publicBoundaries", {}).get("privateContentIncluded") is False, "private content boundary")
        require(promotion.get("publicBoundaries", {}).get("firebaseUsed") is False, "firebase boundary")

        if CAP_ROOT.exists():
            shutil.rmtree(CAP_ROOT)
        MEMBERS_DIR.mkdir(parents=True, exist_ok=True)

        members = {
            "canonicalContract": member_copy(contract, "canonical-contract.json"),
            "stageWiringManifest": member_copy(wiring, "stage-wiring-manifest.json"),
            "acceptanceSchema": member_copy(acceptance, "acceptance-schema.json"),
            "independentVerifierEvidence": member_copy(independent, "independent-verifier-evidence.json"),
            "reproducibilityEvidence": member_copy(reproducibility, "reproducibility-evidence.json"),
            "promotionManifest": member_copy(promotion_manifest, "promotion-manifest.json"),
            "productionSourceArchive": member_copy(production_zip, "production-sources.zip"),
        }

        runtime_digest = sha(runtime_summary)
        members["referenceDummyEvidence"] = write_evidence("reference-dummy-evidence.json", {
            "schemaVersion": 1,
            "status": "PASS",
            "receiver": "REFERENCE_DUMMY",
            "androidApi": 30,
            "abi": "x86_64",
            "runtimeSummarySha256": runtime_digest,
            "productionHostUsed": True,
            "privateContentUsed": False,
            "firebaseUsed": False,
        })
        members["adversarialDummyEvidence"] = write_evidence("adversarial-dummy-evidence.json", {
            "schemaVersion": 1,
            "status": "PASS",
            "receiver": "ADVERSARIAL_CONFORMANT_DUMMY",
            "androidApi": 30,
            "abi": "x86_64",
            "runtimeSummarySha256": runtime_digest,
            "restrictedSafeModeExercised": True,
            "productionHostUsed": True,
            "privateContentUsed": False,
            "firebaseUsed": False,
        })
        members["fullAssemblyEvidence"] = write_evidence("full-assembly-evidence.json", {
            "schemaVersion": 1,
            "status": "PASS",
            "claim": "PUBLIC_STAGE",
            "apkSha256": sha(apk),
            "runtimeSummarySha256": runtime_digest,
            "productionSourceArchiveSha256": sha(production_zip),
            "productionSourceCount": 20,
            "referenceDummy": "PASS",
            "adversarialConformantDummy": "PASS",
            "processRestart": "PASS",
            "corruptionRejection": "PASS",
            "safeUi": "PASS",
            "manualFixAfterPackageApply": 0,
            "privateContentUsed": False,
            "firebaseUsed": False,
            "finalAndroid11Arm64RuntimeClaimed": False,
        })

        capsule = {
            "schemaVersion": 1,
            "projectId": "ToolBox",
            "stageId": "A",
            "status": "STAGE_READY_PRIVATE",
            "members": members,
            "claims": {
                "privateImplementationRequired": False,
                "privateManualPatchRequired": False,
                "privateWiringOnly": True,
                "publicFullAssemblyRehearsal": "PASS",
                "referenceDummy": "PASS",
                "adversarialConformantDummy": "PASS",
                "reproducibility": "PASS",
                "independentWiringVerification": "PASS",
            },
        }
        capsule["capsuleRootSha256"] = sha_bytes(canonical(capsule))
        CAPSULE_PATH.parent.mkdir(parents=True, exist_ok=True)
        CAPSULE_PATH.write_text(json.dumps(capsule, indent=2, sort_keys=True) + "\n", encoding="utf-8")

        verify = load(CAPSULE_PATH)
        claimed = verify.pop("capsuleRootSha256")
        require(sha_bytes(canonical(verify)) == claimed, "capsule root self-check failed")
        forbidden = {"privateReceiverMap", "privateReceiverCertificateFullContent", "privateReceiverConformanceLogs", "privateInternalSlotMapping", "privateBaselineInternalIdentity"}
        require(not (forbidden & set(members)), "private-only material in capsule")

        deterministic_zip(CAP_ROOT, CAPSULE_ZIP)
        summary = {
            "schemaVersion": 1,
            "status": "PASS",
            "capsuleRootSha256": capsule["capsuleRootSha256"],
            "capsuleZipSha256": sha(CAPSULE_ZIP),
            "capsuleZipSize": CAPSULE_ZIP.stat().st_size,
            "productionSourceArchiveSha256": sha(production_zip),
            "memberCount": len(members),
            "privateContentIncluded": False,
            "firebaseUsed": False,
        }
        (BUILD / "stage-capsule-summary.json").write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    except (OSError, ValueError, RuntimeError, zipfile.BadZipFile) as exc:
        print("STAGE_A_SEALED_CAPSULE = FAIL", file=sys.stderr)
        print(str(exc), file=sys.stderr)
        return 2
    print("STAGE_A_SEALED_CAPSULE = PASS")
    print("STAGE_A_STATUS=STAGE_READY_PRIVATE")
    print("PRIVATE_IMPLEMENTATION_REQUIRED=0")
    print("PRIVATE_MANUAL_PATCH_REQUIRED=0")
    print("PRIVATE_CONTENT_INCLUDED=0")
    print("PUBLIC_FIREBASE_ACCESS=0")
    print("CAPSULE_ROOT_SHA256=" + capsule["capsuleRootSha256"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
