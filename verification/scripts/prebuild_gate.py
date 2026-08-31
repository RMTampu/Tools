#!/usr/bin/env python3
"""Fail-closed source/build-input gate for APPLICATION_SAFE_100.

This script intentionally performs no APK assembly. It validates the closed scope,
build policy, exact Android 11/ARM64 toolchain, source boundaries, manifest semantics,
dependency lock/verification state, and CI policy before the build boundary may open.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Iterable

ROOT = Path(__file__).resolve().parents[2]
SCOPE_PATH = ROOT / "verification" / "application_scope.json"
EVIDENCE_DIR = ROOT / "verification" / "evidence"
EVIDENCE_PATH = EVIDENCE_DIR / "prebuild.json"
TOOLCHAIN_GATE = ROOT / "verification" / "scripts" / "toolchain_gate.py"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


@dataclass
class Check:
    name: str
    passed: bool
    detail: str


checks: list[Check] = []


def check(name: str, condition: bool, detail: str) -> None:
    checks.append(Check(name=name, passed=bool(condition), detail=detail))


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def current_git_sha() -> str | None:
    env_sha = os.environ.get("GITHUB_SHA", "").strip()
    if re.fullmatch(r"[0-9a-fA-F]{40}", env_sha):
        return env_sha.lower()
    try:
        value = subprocess.check_output(
            ["git", "rev-parse", "HEAD"],
            cwd=ROOT,
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
        return value.lower() if re.fullmatch(r"[0-9a-fA-F]{40}", value) else None
    except Exception:
        return None


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def iter_source_files() -> Iterable[Path]:
    roots = [ROOT / "toolbox-app" / "src" / "main", ROOT / "toolbox-kernel" / "src" / "main"]
    allowed = {".kt", ".kts", ".java", ".xml", ".json", ".properties", ".pro"}
    for source_root in roots:
        if not source_root.exists():
            continue
        for path in source_root.rglob("*"):
            if path.is_file() and path.suffix.lower() in allowed:
                yield path


def main() -> int:
    EVIDENCE_DIR.mkdir(parents=True, exist_ok=True)

    try:
        scope = json.loads(read(SCOPE_PATH))
    except Exception as exc:  # fail closed
        check("scope-readable", False, f"Unable to read scope: {exc}")
        return finish({})

    source_sha = current_git_sha()
    check("source-git-sha-known", source_sha is not None, f"gitSha={source_sha}")

    # R6 fail-closed toolchain preflight. This runs after the workflow has
    # materialized its declared JDK/Gradle/Android SDK inputs and before any
    # kernel/app compilation. A version, package revision, host architecture,
    # direct dependency, or source contract mismatch therefore cannot escape
    # into a later build failure.
    try:
        toolchain = subprocess.run(
            [sys.executable, str(TOOLCHAIN_GATE), "--mode", "build"],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=False,
        )
        toolchain_output = toolchain.stdout.strip()
        check(
            "android11-arm64-toolchain-preflight",
            toolchain.returncode == 0,
            toolchain_output[-4000:] if toolchain_output else f"returncode={toolchain.returncode}",
        )
    except Exception as exc:
        check("android11-arm64-toolchain-preflight", False, f"toolchain gate failure: {exc}")

    required_documents = scope.get("requiredDocuments", [])
    missing_docs = [name for name in required_documents if not (ROOT / name).is_file()]
    check("required-rule-sources-present", not missing_docs, f"missing={missing_docs}")

    settings = read(ROOT / "settings.gradle.kts")
    check("module-kernel-declared", 'include(":toolbox-kernel")' in settings, "settings.gradle.kts")
    check("module-app-declared", 'include(":toolbox-app")' in settings, "settings.gradle.kts")
    check("repository-mode-fail-on-project-repos", "FAIL_ON_PROJECT_REPOS" in settings, "settings.gradle.kts")

    root_build = read(ROOT / "build.gradle.kts")
    app_build = read(ROOT / "toolbox-app" / "build.gradle.kts")
    props = read(ROOT / "gradle.properties")
    platform = scope["platform"]

    expected_fragments = {
        "agp-version": f'id("com.android.application") version "{platform["agp"]}"',
        "kotlin-version": f'id("org.jetbrains.kotlin.android") version "{platform["kotlin"]}"',
        "compile-sdk": f'compileSdk = {platform["compileSdk"]}',
        "min-sdk": f'minSdk = {platform["androidApi"]}',
        "target-sdk": f'targetSdk = {platform["androidApi"]}',
        "application-id": f'applicationId = "{scope["applicationId"]}"',
        "release-minify": "isMinifyEnabled = true",
        "dependency-locking": "lockAllConfigurations()",
        "version-conflict-fail": "failOnVersionConflict()",
    }
    combined_build = root_build + "\n" + app_build
    for name, fragment in expected_fragments.items():
        check(name, fragment in combined_build, fragment)

    check("deterministic-jvm-locale", "-Duser.language=en" in props and "-Duser.timezone=UTC" in props, "gradle.properties")
    check("lint-abort-on-error", "abortOnError = true" in app_build and "warningsAsErrors = true" in app_build, "toolbox-app/build.gradle.kts")
    check("orchestrator-enabled", 'execution = "ANDROIDX_TEST_ORCHESTRATOR"' in app_build, "toolbox-app/build.gradle.kts")

    # Lockfiles + artifact verification are the authoritative reproducibility layer.
    # Dynamic/changing selectors remain forbidden before resolution.
    dependency_texts = [root_build, app_build, settings]
    dynamic_patterns = [
        r'"[^"\n]*:\+"',
        r'"[^"\n]*:latest(?:\.[^"\n]+)?"',
        r'"[^"\n]*SNAPSHOT[^"\n]*"',
        r'"[^"\n]*:\[[^"\n]+"',
        r'"[^"\n]*:\([^"\n]+"',
    ]
    dynamic_hits = [pat for text in dependency_texts for pat in dynamic_patterns if re.search(pat, text, re.IGNORECASE)]
    check("no-dynamic-dependency-selector", not dynamic_hits, f"hits={dynamic_hits}")

    lockfiles = [ROOT / "toolbox-kernel" / "gradle.lockfile", ROOT / "toolbox-app" / "gradle.lockfile"]
    missing_locks = [str(path.relative_to(ROOT)) for path in lockfiles if not path.is_file()]
    check("dependency-lockfiles-present", not missing_locks, f"missing={missing_locks}")

    verification_metadata = ROOT / "gradle" / "verification-metadata.xml"
    check("dependency-verification-metadata-present", verification_metadata.is_file(), str(verification_metadata.relative_to(ROOT)))
    if verification_metadata.is_file():
        metadata_text = read(verification_metadata)
        check("dependency-verification-sha256-present", "<sha256" in metadata_text, "verification-metadata.xml contains SHA-256 entries")

    manifest_path = ROOT / "toolbox-app" / "src" / "main" / "AndroidManifest.xml"
    try:
        manifest_root = ET.parse(manifest_path).getroot()
        app = manifest_root.find("application")
        check("manifest-application-present", app is not None, str(manifest_path.relative_to(ROOT)))
        if app is not None:
            check("manifest-cleartext-disabled", app.attrib.get(ANDROID_NS + "usesCleartextTraffic") == "false", "usesCleartextTraffic=false")
            check("manifest-backup-disabled", app.attrib.get(ANDROID_NS + "allowBackup") == "false", "allowBackup=false")

        permissions = [node.attrib.get(ANDROID_NS + "name", "") for node in manifest_root.findall("uses-permission")]
        forbidden_permissions = [p for p in scope["forbiddenUntilScopeUpdated"] if p.startswith("android.permission.")]
        bad_permissions = sorted(set(permissions).intersection(forbidden_permissions))
        check("closed-empty-permission-universe", not bad_permissions, f"declared={permissions}, forbidden={bad_permissions}")

        exported = []
        for component_tag in ("activity", "activity-alias", "service", "receiver", "provider"):
            for node in manifest_root.findall(f"application/{component_tag}"):
                if node.attrib.get(ANDROID_NS + "exported") == "true":
                    exported.append((component_tag, node.attrib.get(ANDROID_NS + "name", "")))
        check("exported-component-universe", exported == [("activity", ".MainActivity")], f"exported={exported}")
    except Exception as exc:
        check("manifest-parse", False, f"Manifest parse failure: {exc}")

    forbidden_tokens = [token for token in scope["forbiddenUntilScopeUpdated"] if not token.startswith("android.permission.")]
    forbidden_hits: list[str] = []
    for path in iter_source_files():
        text = read(path)
        for token in forbidden_tokens:
            if token in text:
                forbidden_hits.append(f"{path.relative_to(ROOT)}::{token}")
    check("closed-empty-domain-source-scan", not forbidden_hits, f"hits={forbidden_hits}")

    proguard = read(ROOT / "toolbox-app" / "proguard-rules.pro")
    blanket_keep = bool(re.search(r"(?m)^\s*-keep(?:names)?\s+[^\n]*\*\*", proguard))
    check("no-blanket-shrinker-keep", not blanket_keep, "proguard-rules.pro")

    workflow_dir = ROOT / ".github" / "workflows"
    unpinned_actions: list[str] = []
    continue_on_error: list[str] = []
    if workflow_dir.is_dir():
        for workflow in workflow_dir.glob("*.y*ml"):
            text = read(workflow)
            for line in text.splitlines():
                match = re.search(r"\buses:\s*([^\s#]+)", line)
                if match:
                    ref = match.group(1)
                    if not ref.startswith("./"):
                        if "@" not in ref or not re.fullmatch(r"[0-9a-fA-F]{40}", ref.rsplit("@", 1)[1]):
                            unpinned_actions.append(f"{workflow.name}:{ref}")
                if re.search(r"\bcontinue-on-error:\s*true\b", line, re.IGNORECASE):
                    continue_on_error.append(f"{workflow.name}:{line.strip()}")
    check("github-actions-commit-pinned", not unpinned_actions, f"unpinned={unpinned_actions}")
    check("no-ci-continue-on-error-bypass", not continue_on_error, f"hits={continue_on_error}")

    return finish(scope)


def finish(scope: dict) -> int:
    failed = [item for item in checks if not item.passed]
    payload = {
        "schemaVersion": 2,
        "gate": "APPLICATION_PREBUILD_SOURCE_GATE",
        "status": "PASS" if not failed else "NOT_PROVEN",
        "gitSha": current_git_sha(),
        "scopeSha256": sha256(SCOPE_PATH) if SCOPE_PATH.is_file() else None,
        "toolchainProfile": "verification/toolchains/android11-arm64.lock.json",
        "source": {
            "repository": "RMTampu/Tools",
            "target": "Android 11 / API 30 / arm64-v8a",
        },
        "checks": [asdict(item) for item in checks],
        "failed": [item.name for item in failed],
    }
    EVIDENCE_PATH.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    if failed:
        print("APPLICATION_PREBUILD_SOURCE_GATE = NOT_PROVEN", file=sys.stderr)
        for item in failed:
            print(f"FAIL {item.name}: {item.detail}", file=sys.stderr)
        return 1

    print("APPLICATION_PREBUILD_SOURCE_GATE = PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
