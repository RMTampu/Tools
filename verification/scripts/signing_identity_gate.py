#!/usr/bin/env python3
"""Fail-closed prebuild verification of the release signing certificate identity."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
from dataclasses import asdict, dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONTRACT_PATH = ROOT / "verification" / "signing_contract.json"
EVIDENCE_DIR = ROOT / "verification" / "evidence"


@dataclass
class Check:
    name: str
    passed: bool
    detail: str


def current_git_sha() -> str | None:
    value = os.environ.get("GITHUB_SHA", "").strip()
    if re.fullmatch(r"[0-9a-fA-F]{40}", value):
        return value.lower()
    try:
        value = subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True, stderr=subprocess.DEVNULL
        ).strip()
        return value.lower() if re.fullmatch(r"[0-9a-fA-F]{40}", value) else None
    except Exception:
        return None


def normalize(value: str) -> str:
    return re.sub(r"[^0-9a-f]", "", value.lower())


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--keystore", required=True, type=Path)
    parser.add_argument("--store-password", required=True)
    parser.add_argument("--alias", required=True)
    args = parser.parse_args()

    checks: list[Check] = []

    def check(name: str, condition: bool, detail: str) -> None:
        checks.append(Check(name, bool(condition), detail))

    contract = json.loads(CONTRACT_PATH.read_text(encoding="utf-8"))
    policy = contract["release"]
    keystore = args.keystore.resolve()
    check("release-keystore-present", keystore.is_file() and keystore.stat().st_size > 0, str(keystore))
    check("release-alias-present", bool(args.alias.strip()), "alias supplied")
    actual: str | None = None

    if keystore.is_file():
        completed = subprocess.run(
            [
                "keytool", "-list", "-v",
                "-keystore", str(keystore),
                "-storepass", args.store_password,
                "-alias", args.alias,
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            check=False,
        )
        safe_detail = f"keytoolExit={completed.returncode}"
        check("release-keystore-readable", completed.returncode == 0, safe_detail)
        if completed.returncode == 0:
            match = re.search(r"SHA256:\s*([0-9A-Fa-f:]{64,95})", completed.stdout)
            actual = normalize(match.group(1)) if match else None
            check("release-certificate-sha256-observed", actual is not None and len(actual) == 64, f"certSha256={actual}")

    expected = policy.get("expectedCertificateSha256")
    check("release-signing-identity-pinned", bool(expected), "verification/signing_contract.json")
    if expected and actual:
        check(
            "release-signing-identity-match",
            normalize(expected) == actual,
            f"actual={actual}, expected={normalize(expected)}",
        )

    required_schemes = {"requireV2": True, "requireV3": True}
    for key, required in required_schemes.items():
        check(f"release-{key.lower()}-contract", policy.get(key) is required, f"{key}={policy.get(key)}")

    failed = [c for c in checks if not c.passed]
    EVIDENCE_DIR.mkdir(parents=True, exist_ok=True)
    payload = {
        "schemaVersion": 1,
        "gate": "SIGNING_IDENTITY_GATE",
        "status": "PASS" if not failed else "NOT_PROVEN",
        "gitSha": current_git_sha(),
        "keystoreSha256": sha256(keystore) if keystore.is_file() else None,
        "certificateSha256": actual,
        "checks": [asdict(c) for c in checks],
        "failed": [c.name for c in failed],
    }
    (EVIDENCE_DIR / "signing-identity.json").write_text(
        json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )

    if actual:
        print(f"RELEASE_CERT_SHA256={actual}")

    if failed:
        print("SIGNING_IDENTITY_GATE = NOT_PROVEN", file=sys.stderr)
        for c in failed:
            print(f"FAIL {c.name}: {c.detail}", file=sys.stderr)
        return 1

    print("SIGNING_IDENTITY_GATE = PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
