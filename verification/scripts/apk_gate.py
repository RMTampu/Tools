#!/usr/bin/env python3
"""Verify final APK container, merged manifest, ABI and package semantics."""

from __future__ import annotations

import argparse
import hashlib
import json
import posixpath
import sys
import unicodedata
import xml.etree.ElementTree as ET
import zipfile
from dataclasses import dataclass, asdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCOPE = json.loads((ROOT / "verification" / "application_scope.json").read_text(encoding="utf-8"))
EVIDENCE_DIR = ROOT / "verification" / "evidence"
ANDROID = "{http://schemas.android.com/apk/res/android}"


@dataclass
class Check:
    name: str
    passed: bool
    detail: str


def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--manifest-xml", required=True, type=Path)
    parser.add_argument("--variant", required=True, choices=("debug", "release"))
    args = parser.parse_args()

    checks: list[Check] = []

    def check(name: str, condition: bool, detail: str) -> None:
        checks.append(Check(name, bool(condition), detail))

    apk = args.apk.resolve()
    manifest_xml = args.manifest_xml.resolve()
    check("apk-present", apk.is_file() and apk.stat().st_size > 0, str(apk))
    check("merged-manifest-present", manifest_xml.is_file() and manifest_xml.stat().st_size > 0, str(manifest_xml))
    if not apk.is_file() or not manifest_xml.is_file():
        return finish(args.variant, apk, checks, {})

    names: list[str] = []
    total_uncompressed = 0
    total_compressed = 0
    pathological_entries: list[str] = []
    unsafe_paths: list[str] = []
    native_entries: list[str] = []
    normalized_seen: dict[str, str] = {}
    normalized_collisions: list[tuple[str, str]] = []

    try:
        with zipfile.ZipFile(apk, "r") as archive:
            for info in archive.infolist():
                name = info.filename
                names.append(name)
                total_uncompressed += info.file_size
                total_compressed += info.compress_size

                normalized = posixpath.normpath(name.replace("\\", "/"))
                if (
                    name.startswith("/")
                    or "\\" in name
                    or normalized == ".."
                    or normalized.startswith("../")
                    or "/../" in f"/{normalized}/"
                ):
                    unsafe_paths.append(name)

                identity = unicodedata.normalize("NFC", normalized).casefold()
                prior = normalized_seen.get(identity)
                if prior is not None and prior != name:
                    normalized_collisions.append((prior, name))
                else:
                    normalized_seen[identity] = name

                if name.endswith(".so") or name.startswith("lib/"):
                    native_entries.append(name)

                if info.file_size >= 1024 * 1024 and info.compress_size > 0:
                    ratio = info.file_size / info.compress_size
                    if ratio > 100:
                        pathological_entries.append(f"{name}:{ratio:.1f}x")
    except Exception as exc:
        check("apk-zip-readable", False, str(exc))
        return finish(args.variant, apk, checks, {})

    duplicates = sorted({name for name in names if names.count(name) > 1})
    check("apk-no-duplicate-entry", not duplicates, f"duplicates={duplicates}")
    check("apk-canonical-paths", not unsafe_paths, f"unsafe={unsafe_paths}")
    check("apk-normalized-path-unique", not normalized_collisions, f"collisions={normalized_collisions}")
    check("apk-no-pathological-compression", not pathological_entries, f"entries={pathological_entries}")
    check("apk-required-container-entries", "AndroidManifest.xml" in names and "classes.dex" in names and "resources.arsc" in names, "manifest/classes/resources")

    r7_closed_empty = SCOPE["domainScope"]["R7"]["status"] == "CLOSED_EMPTY"
    check("r7-native-archive-closure", (not r7_closed_empty) or not native_entries, f"native={native_entries}")

    # A bootstrap APK should remain comfortably bounded. This guards accidental payload growth.
    max_apk_bytes = int(SCOPE.get("budgets", {}).get("apkBytes", 64 * 1024 * 1024))
    max_uncompressed = int(SCOPE.get("budgets", {}).get("apkUncompressedBytes", 128 * 1024 * 1024))
    check("apk-size-budget", apk.stat().st_size <= max_apk_bytes, f"actual={apk.stat().st_size}, max={max_apk_bytes}")
    check("apk-expanded-size-budget", total_uncompressed <= max_uncompressed, f"actual={total_uncompressed}, max={max_uncompressed}")

    manifest_details: dict[str, object] = {}
    try:
        root = ET.parse(manifest_xml).getroot()
        expected_package = SCOPE["debugApplicationId"] if args.variant == "debug" else SCOPE["applicationId"]
        actual_package = root.attrib.get("package", "")
        check("final-package-id", actual_package == expected_package, f"actual={actual_package}, expected={expected_package}")

        uses_sdk = root.find("uses-sdk")
        min_sdk = uses_sdk.attrib.get(ANDROID + "minSdkVersion") if uses_sdk is not None else None
        target_sdk = uses_sdk.attrib.get(ANDROID + "targetSdkVersion") if uses_sdk is not None else None
        required_api = str(SCOPE["platform"]["androidApi"])
        check("final-min-sdk", min_sdk == required_api, f"actual={min_sdk}, expected={required_api}")
        check("final-target-sdk", target_sdk == required_api, f"actual={target_sdk}, expected={required_api}")

        app = root.find("application")
        check("final-application-present", app is not None, "merged manifest")
        if app is not None:
            cleartext = app.attrib.get(ANDROID + "usesCleartextTraffic")
            backup = app.attrib.get(ANDROID + "allowBackup")
            debuggable = app.attrib.get(ANDROID + "debuggable", "false")
            check("final-cleartext-disabled", cleartext == "false", f"actual={cleartext}")
            check("final-backup-disabled", backup == "false", f"actual={backup}")
            expected_debuggable = args.variant == "debug"
            actual_debuggable = debuggable == "true"
            check("final-debuggable-contract", actual_debuggable == expected_debuggable, f"actual={actual_debuggable}, expected={expected_debuggable}")
        else:
            cleartext = backup = debuggable = None

        permissions = sorted(node.attrib.get(ANDROID + "name", "") for node in root.findall("uses-permission"))
        check("final-permission-universe", not permissions, f"permissions={permissions}")

        exported: list[tuple[str, str]] = []
        for tag in ("activity", "activity-alias", "service", "receiver", "provider"):
            for node in root.findall(f"application/{tag}"):
                if node.attrib.get(ANDROID + "exported") == "true":
                    exported.append((tag, node.attrib.get(ANDROID + "name", "")))
        exported_ok = len(exported) == 1 and exported[0][0] == "activity" and exported[0][1].endswith(".MainActivity")
        check("final-exported-component-universe", exported_ok, f"exported={exported}")

        manifest_details = {
            "package": actual_package,
            "minSdk": min_sdk,
            "targetSdk": target_sdk,
            "permissions": permissions,
            "exported": exported,
            "cleartext": cleartext,
            "allowBackup": backup,
            "debuggable": debuggable,
        }
    except Exception as exc:
        check("merged-manifest-parse", False, str(exc))

    details = {
        "sha256": digest(apk),
        "sizeBytes": apk.stat().st_size,
        "entryCount": len(names),
        "uncompressedBytes": total_uncompressed,
        "compressedPayloadBytes": total_compressed,
        "nativeEntries": native_entries,
        "manifest": manifest_details,
    }
    return finish(args.variant, apk, checks, details)


def finish(variant: str, apk: Path, checks: list[Check], details: dict) -> int:
    EVIDENCE_DIR.mkdir(parents=True, exist_ok=True)
    failed = [item for item in checks if not item.passed]
    payload = {
        "schemaVersion": 1,
        "gate": "FINAL_APK_GATE",
        "variant": variant,
        "status": "PASS" if not failed else "NOT_PROVEN",
        "apk": str(apk),
        "checks": [asdict(item) for item in checks],
        "failed": [item.name for item in failed],
        "details": details,
    }
    out = EVIDENCE_DIR / f"apk-{variant}.json"
    out.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if failed:
        print(f"FINAL_APK_GATE[{variant}] = NOT_PROVEN", file=sys.stderr)
        for item in failed:
            print(f"FAIL {item.name}: {item.detail}", file=sys.stderr)
        return 1
    print(f"FINAL_APK_GATE[{variant}] = PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
