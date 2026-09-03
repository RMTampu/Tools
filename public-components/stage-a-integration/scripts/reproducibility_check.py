#!/usr/bin/env python3
from __future__ import annotations
import hashlib
import json
import os
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parents[1]
HOST = REPO / "public-components/stage-a-android-host"
BUILD = HOST / "build/android"
EVIDENCE = ROOT / "build/evidence/reproducibility.json"


def sha(data):
    return hashlib.sha256(data).hexdigest()


def semantic_model(apk):
    with zipfile.ZipFile(apk, "r") as archive:
        rows = {}
        for name in sorted(archive.namelist()):
            if name.endswith("/") or name.startswith("META-INF/"):
                continue
            rows[name] = sha(archive.read(name))
    return rows


def snapshot(label):
    unsigned = BUILD / "test-unsigned.apk"
    badging = BUILD / "badging.txt"
    dex = BUILD / "dex/classes.dex"
    if not unsigned.is_file() or not badging.is_file() or not dex.is_file():
        raise RuntimeError(f"{label}: build outputs missing")
    return {
        "unsignedSemanticEntries": semantic_model(unsigned),
        "classesDexSha256": sha(dex.read_bytes()),
        "badgingSha256": sha(badging.read_bytes()),
        "androidManifestSourceSha256": sha((HOST / "AndroidManifest.xml").read_bytes()),
    }


def main():
    try:
        first = snapshot("first")
        subprocess.run(["bash", str(HOST / "scripts/build-test-apk.sh")], cwd=REPO, check=True)
        second = snapshot("second")
        if first != second:
            raise RuntimeError("clean rebuild semantic payload drift")
        evidence = {
            "schemaVersion": 1,
            "status": "PASS",
            "claim": "PUBLIC_STAGE",
            "method": "two-clean-build-semantic-pre-sign-payload-comparison",
            "first": first,
            "second": second,
            "semanticPayloadIdentical": True,
            "signedApkBitwiseReproducibilityClaimed": False,
            "signedApkReason": "Public host uses a newly generated disposable test key on each clean build; signature bytes are intentionally excluded from reproducibility claim.",
            "privateContentUsed": False,
            "firebaseUsed": False,
            "limitations": ["This proves semantic equality of unsigned APK entries, classes.dex, manifest source and badging for two clean Public builds; final Private signed APK remains a separate witness."],
        }
        EVIDENCE.parent.mkdir(parents=True, exist_ok=True)
        EVIDENCE.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    except (OSError, RuntimeError, subprocess.CalledProcessError, zipfile.BadZipFile) as exc:
        print("STAGE_A_PUBLIC_REPRODUCIBILITY = FAIL", file=sys.stderr)
        print(str(exc), file=sys.stderr)
        return 2
    print("STAGE_A_PUBLIC_REPRODUCIBILITY = PASS")
    print("PUBLIC_CLEAN_BUILD_REPLICAS = 2")
    print("SIGNED_APK_BITWISE_REPRODUCIBILITY_CLAIMED = 0")
    print("PRIVATE_CONTENT_USED = 0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
