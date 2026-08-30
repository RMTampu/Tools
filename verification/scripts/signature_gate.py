#!/usr/bin/env python3
"""Interpret apksigner verification output against the repository signing contract."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass, asdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONTRACT = json.loads((ROOT / "verification" / "signing_contract.json").read_text(encoding="utf-8"))
EVIDENCE_DIR = ROOT / "verification" / "evidence"


@dataclass
class Check:
    name: str
    passed: bool
    detail: str


def normalize_digest(value: str) -> str:
    return re.sub(r"[^0-9a-f]", "", value.lower())


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", required=True, type=Path, help="Output of apksigner verify --verbose --print-certs")
    parser.add_argument("--variant", required=True, choices=("debug", "release"))
    args = parser.parse_args()

    checks: list[Check] = []

    def check(name: str, condition: bool, detail: str) -> None:
        checks.append(Check(name, bool(condition), detail))

    if not args.report.is_file():
        check("apksigner-report-present", False, str(args.report))
        return finish(args.variant, checks, {})

    text = args.report.read_text(encoding="utf-8", errors="replace")
    policy = CONTRACT[args.variant]

    verified = bool(re.search(r"(?mi)^Verifies\s*$", text)) or "DOES NOT VERIFY" not in text.upper()
    v1 = bool(re.search(r"Verified using v1 scheme.*:\s*true", text, re.IGNORECASE))
    v2 = bool(re.search(r"Verified using v2 scheme.*:\s*true", text, re.IGNORECASE))
    v3 = bool(re.search(r"Verified using v3 scheme.*:\s*true", text, re.IGNORECASE))
    v4 = bool(re.search(r"Verified using v4 scheme.*:\s*true", text, re.IGNORECASE))

    cert_match = re.search(r"Signer #1 certificate SHA-256 digest:\s*([0-9A-Fa-f:]+)", text)
    cert = normalize_digest(cert_match.group(1)) if cert_match else None

    check("apk-signature-verifies", verified and "DOES NOT VERIFY" not in text.upper(), "apksigner verification result")
    if policy.get("requireV2"):
        check("signature-v2-required", v2, f"v2={v2}")
    if policy.get("requireV3"):
        check("signature-v3-required", v3, f"v3={v3}")
    check("signer-certificate-present", cert is not None and len(cert) == 64, f"certSha256={cert}")

    expected = policy.get("expectedCertificateSha256")
    if args.variant == "release":
        check("release-signing-identity-pinned", bool(expected), "verification/signing_contract.json expectedCertificateSha256")
        if expected:
            check("release-signing-identity-match", cert == normalize_digest(expected), f"actual={cert}, expected={normalize_digest(expected)}")

    details = {
        "v1": v1,
        "v2": v2,
        "v3": v3,
        "v4": v4,
        "certificateSha256": cert,
    }
    return finish(args.variant, checks, details)


def finish(variant: str, checks: list[Check], details: dict) -> int:
    failed = [c for c in checks if not c.passed]
    payload = {
        "schemaVersion": 1,
        "gate": "APK_SIGNATURE_GATE",
        "variant": variant,
        "status": "PASS" if not failed else "NOT_PROVEN",
        "checks": [asdict(c) for c in checks],
        "failed": [c.name for c in failed],
        "details": details,
    }
    EVIDENCE_DIR.mkdir(parents=True, exist_ok=True)
    (EVIDENCE_DIR / f"signature-{variant}.json").write_text(
        json.dumps(payload, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    if failed:
        print(f"APK_SIGNATURE_GATE[{variant}] = NOT_PROVEN", file=sys.stderr)
        for c in failed:
            print(f"FAIL {c.name}: {c.detail}", file=sys.stderr)
        return 1
    print(f"APK_SIGNATURE_GATE[{variant}] = PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
