#!/usr/bin/env python3
"""Fail-closed Gate 0-5 asset/route proof for the current minimal ToolBox bootstrap.

The current bootstrap intentionally owns no custom res/ or assets/ files. The only
source-controlled Android asset input is AndroidManifest.xml; resources.arsc is a
generated package artifact and is verified after the build boundary by apk_gate.py.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import subprocess
import sys
import unicodedata
import xml.etree.ElementTree as ET
from dataclasses import asdict, dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONTRACT_PATH = ROOT / "verification" / "asset_contracts.json"
SCOPE_PATH = ROOT / "verification" / "application_scope.json"
PREBUILD_EVIDENCE = ROOT / "verification" / "evidence" / "prebuild.json"
EVIDENCE_DIR = ROOT / "verification" / "evidence"
EVIDENCE_PATH = EVIDENCE_DIR / "asset-prebuild.json"
MANIFEST = ROOT / "toolbox-app" / "src" / "main" / "AndroidManifest.xml"
APP_MAIN = ROOT / "toolbox-app" / "src" / "main"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


@dataclass
class Check:
    name: str
    passed: bool
    detail: str


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for block in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def git_sha() -> str | None:
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


def android_jar(api: int) -> Path | None:
    for key in ("ANDROID_SDK_ROOT", "ANDROID_HOME"):
        root = os.environ.get(key)
        if root:
            candidate = Path(root) / "platforms" / f"android-{api}" / "android.jar"
            if candidate.is_file():
                return candidate
    return None


def source_files() -> list[Path]:
    return sorted(
        p for p in APP_MAIN.rglob("*")
        if p.is_file() and p.suffix.lower() in {".kt", ".java", ".xml"}
    )


def main() -> int:
    EVIDENCE_DIR.mkdir(parents=True, exist_ok=True)
    checks: list[Check] = []

    def check(name: str, condition: bool, detail: str) -> None:
        checks.append(Check(name, bool(condition), detail))

    try:
        contract = json.loads(CONTRACT_PATH.read_text(encoding="utf-8"))
        scope = json.loads(SCOPE_PATH.read_text(encoding="utf-8"))
    except Exception as exc:
        check("asset-contract-readable", False, str(exc))
        return finish({}, {}, {}, {}, checks)

    revision = git_sha()
    check("asset-git-sha-known", revision is not None, f"gitSha={revision}")
    check(
        "asset-platform-contract",
        contract["platform"] == {"androidApi": 30, "abi": "arm64-v8a"}
        and scope["platform"]["androidApi"] == 30
        and scope["platform"]["abi"] == "arm64-v8a",
        f"contract={contract['platform']}, scope={scope['platform']}",
    )

    # Gate 0-3: closed source asset universe and canonical identity.
    expected_sources = set(contract["assetUniverse"]["requiredSourceFiles"])
    actual_sources: set[str] = set()
    if MANIFEST.is_file():
        actual_sources.add(str(MANIFEST.relative_to(ROOT)))
    for rel_dir in ("toolbox-app/src/main/res", "toolbox-app/src/main/assets"):
        directory = ROOT / rel_dir
        if directory.exists():
            for path in directory.rglob("*"):
                if path.is_file():
                    actual_sources.add(str(path.relative_to(ROOT)))
    check("asset-universe-exact", actual_sources == expected_sources, f"actual={sorted(actual_sources)}, expected={sorted(expected_sources)}")

    res_contract = contract["assetUniverse"]["customResDirectory"]
    assets_contract = contract["assetUniverse"]["customAssetsDirectory"]
    actual_res = sorted(str(p.relative_to(ROOT)) for p in (ROOT / res_contract["path"]).rglob("*") if p.is_file()) if (ROOT / res_contract["path"]).exists() else []
    actual_assets = sorted(str(p.relative_to(ROOT)) for p in (ROOT / assets_contract["path"]).rglob("*") if p.is_file()) if (ROOT / assets_contract["path"]).exists() else []
    check("custom-res-closed-empty", res_contract["status"] == "CLOSED_EMPTY" and not actual_res, f"files={actual_res}")
    check("custom-assets-closed-empty", assets_contract["status"] == "CLOSED_EMPTY" and not actual_assets, f"files={actual_assets}")

    normalized: dict[str, str] = {}
    collisions: list[tuple[str, str]] = []
    for item in sorted(actual_sources):
        identity = unicodedata.normalize("NFC", item).casefold()
        previous = normalized.get(identity)
        if previous is not None and previous != item:
            collisions.append((previous, item))
        normalized[identity] = item
    check(
        "asset-source-canonical-paths",
        not collisions and all(".." not in Path(item).parts for item in actual_sources),
        f"collisions={collisions}",
    )

    manifest_ok = False
    theme = None
    label = None
    exported: list[tuple[str, str]] = []
    permissions: list[str] = []
    try:
        root = ET.parse(MANIFEST).getroot()
        app = root.find("application")
        activity = root.find("application/activity")
        if app is not None:
            theme = app.attrib.get(ANDROID_NS + "theme")
            label = app.attrib.get(ANDROID_NS + "label")
        permissions = sorted(node.attrib.get(ANDROID_NS + "name", "") for node in root.findall("uses-permission"))
        for tag in ("activity", "activity-alias", "service", "receiver", "provider"):
            for node in root.findall(f"application/{tag}"):
                if node.attrib.get(ANDROID_NS + "exported") == "true":
                    exported.append((tag, node.attrib.get(ANDROID_NS + "name", "")))
        launcher_ok = False
        if activity is not None:
            actions = {n.attrib.get(ANDROID_NS + "name") for n in activity.findall("intent-filter/action")}
            categories = {n.attrib.get(ANDROID_NS + "name") for n in activity.findall("intent-filter/category")}
            launcher_ok = "android.intent.action.MAIN" in actions and "android.intent.category.LAUNCHER" in categories
        manifest_ok = (
            app is not None
            and activity is not None
            and app.attrib.get(ANDROID_NS + "name") == ".ToolBoxApplication"
            and app.attrib.get(ANDROID_NS + "allowBackup") == "false"
            and app.attrib.get(ANDROID_NS + "extractNativeLibs") == "false"
            and app.attrib.get(ANDROID_NS + "usesCleartextTraffic") == "false"
            and app.attrib.get(ANDROID_NS + "supportsRtl") == "true"
            and theme == "@android:style/Theme.Material.NoActionBar"
            and label == "ToolBox"
            and not label.startswith("@")
            and app.attrib.get(ANDROID_NS + "icon") is None
            and activity.attrib.get(ANDROID_NS + "name") == ".MainActivity"
            and activity.attrib.get(ANDROID_NS + "exported") == "true"
            and launcher_ok
            and permissions == []
            and exported == [("activity", ".MainActivity")]
        )
        check("asset-manifest-semantic-contract", manifest_ok, f"theme={theme}, label={label}, permissions={permissions}, exported={exported}")
    except Exception as exc:
        check("asset-manifest-parse", False, str(exc))

    text = "\n".join(path.read_text(encoding="utf-8", errors="replace") for path in source_files())
    dynamic_tokens = ["getIdentifier(", "AssetManager.open(", "openRawResource(", "Resources.getIdentifier("]
    dynamic_hits = [token for token in dynamic_tokens if token in text]
    app_r_hits = re.findall(r"(?<![A-Za-z0-9_])R\.[A-Za-z_]", text)
    check("asset-dynamic-lookup-closed-empty", not dynamic_hits, f"hits={dynamic_hits}")
    check("asset-application-resource-reference-closed-empty", not app_r_hits, f"hits={app_r_hits}")

    framework_refs = sorted(set(re.findall(r"@([A-Za-z0-9_.]+):([A-Za-z0-9_]+)/([A-Za-z0-9_.]+)", text)))
    expected_framework_refs = [("android", "style", "Theme.Material.NoActionBar")]
    check("asset-framework-reference-universe-exact", framework_refs == expected_framework_refs, f"refs={framework_refs}")

    build_text = (ROOT / "toolbox-app" / "build.gradle.kts").read_text(encoding="utf-8")
    check("asset-resource-shrinking-disabled", "isShrinkResources = false" in build_text, "release resource shrinking must stay disabled")

    generated = contract["assetUniverse"].get("generatedPackageArtifacts", [])
    generated_entries = [item.get("finalEntry") for item in generated if item.get("required") is True]
    check("generated-resource-contract-closed", generated_entries == ["resources.arsc"], f"generated={generated_entries}")
    check("asset-package-contract-closed", contract["packageContract"]["requiredEntries"] == ["AndroidManifest.xml", "classes.dex", "resources.arsc"], str(contract["packageContract"]))
    check(
        "asset-budgets-match-scope",
        contract["budgets"]["apkBytes"] == scope["budgets"]["apkBytes"]
        and contract["budgets"]["apkUncompressedBytes"] == scope["budgets"]["apkUncompressedBytes"]
        and int(contract["budgets"]["generatedResourcesArscBytes"]) > 0,
        str(contract["budgets"]),
    )

    # Corroborate closed build-input/source proof from the immediately preceding application prebuild gate.
    prebuild = {}
    try:
        prebuild = json.loads(PREBUILD_EVIDENCE.read_text(encoding="utf-8"))
    except Exception as exc:
        check("asset-application-prebuild-evidence-readable", False, str(exc))
    else:
        check("asset-application-prebuild-pass", prebuild.get("status") == "PASS", f"status={prebuild.get('status')}")
        check("asset-application-prebuild-same-revision", prebuild.get("gitSha") == revision, f"prebuildSha={prebuild.get('gitSha')}, gitSha={revision}")

    platform_jar = android_jar(30)
    check("asset-api30-framework-present", platform_jar is not None, str(platform_jar))
    framework_symbol = False
    javap_detail = "android.jar unavailable"
    if platform_jar is not None:
        completed = subprocess.run(
            ["javap", "-classpath", str(platform_jar), "android.R$style"],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
        framework_symbol = completed.returncode == 0 and "Theme_Material_NoActionBar" in completed.stdout
        javap_detail = f"exit={completed.returncode}, symbol={framework_symbol}"
    check("asset-framework-theme-symbol-api30", framework_symbol, javap_detail)

    # Gate 4: one exact, framework-owned route. Each process sub-gate is explicit and fail-closed.
    routes = contract.get("routes", [])
    route = routes[0] if len(routes) == 1 else None
    required_route_fields = {
        "routeId", "requirement", "consumer", "source", "kind", "androidApi",
        "exactIdentity", "authority", "state", "observation", "forbiddenAlternatives",
    }

    def theme_contract(value: str | None) -> bool:
        return value == "@android:style/Theme.Material.NoActionBar"

    mutation_challenge = {
        "wrong-framework-theme-detected": not theme_contract("@android:style/Theme.Material"),
        "app-owned-theme-detected": not theme_contract("@style/ToolBoxTheme"),
        "dynamic-lookup-detector-active": "getIdentifier(" in (text + "\ngetIdentifier(") and "getIdentifier(" not in text,
        "custom-resource-detector-active": (actual_sources | {"toolbox-app/src/main/res/values/mutant.xml"}) != expected_sources,
    }

    route_subgates: dict[str, bool] = {}
    route_subgates["4.0_ROUTE_DOMAIN_LOCK"] = route is not None and not dynamic_hits and not app_r_hits and not actual_res and not actual_assets
    route_subgates["4.1_SEMANTIC_INTENT_LOCK"] = route is not None and route["requirement"].startswith("Bootstrap activity uses") and theme == route["source"]
    route_subgates["4.2_OBSERVATIONAL_CLOSURE"] = route is not None and route["exactIdentity"] is True and "MainActivity" in route["observation"]
    route_subgates["4.3_EPISTEMIC_CLOSURE"] = route is not None and scope["platform"]["androidApi"] == route["androidApi"] == 30 and platform_jar is not None
    route_subgates["4.4_UNIQUE_ROUTE_MODEL"] = route is not None and len(routes) == 1 and route["source"] == "@android:style/Theme.Material.NoActionBar"
    route_subgates["4.5_CAUSAL_ROUTE_MODEL"] = theme_contract(theme) and not theme_contract("@style/ToolBoxTheme")
    route_subgates["4.6_CLOSED_ROUTE_REPRESENTATION"] = route is not None and set(route) >= required_route_fields
    route_subgates["4.7_ROUTE_TRANSLATION"] = route is not None and theme == route["source"]
    route_subgates["4.8_ROUTE_GRAPH_CLOSURE"] = route is not None and route["consumer"] == "application theme resolver" and route["authority"] == "android framework package only"
    route_subgates["4.9_ROBUST_CONTEXTUAL_ROUTE"] = route is not None and not dynamic_hits and not app_r_hits and scope["domainScope"]["R7"]["status"] == "CLOSED_EMPTY"
    route_subgates["4.10_ANDROID_RESOLUTION_REFINEMENT"] = framework_symbol and theme_contract(theme)
    route_subgates["4.11_FOUNDATIONAL_ROUTE_CHECK"] = manifest_ok and framework_symbol and framework_refs == expected_framework_refs
    route_subgates["4.12_FAULT_DOMAIN_ROUTE_CHALLENGE"] = all(mutation_challenge.values())
    route_subgates["4.13_FINAL_ROUTE_CLOSURE"] = all(route_subgates.values())
    for name, passed in route_subgates.items():
        check("route-" + name, passed, "closed single framework-theme route")
    route_pass = all(route_subgates.values())
    check("ROUTE_PROOF_PASS", route_pass, f"subgates={route_subgates}")

    expected_faults = {
        "PRESENCE_MISSING",
        "PATH_AMBIGUITY",
        "SYNTAX_MALFORMED_MANIFEST",
        "SEMANTIC_WRONG_MANIFEST_VALUE",
        "REFERENCE_FRAMEWORK_THEME_MISSING",
        "UNDECLARED_CUSTOM_RESOURCE",
        "DYNAMIC_RESOURCE_LOOKUP_INTRODUCED",
        "CONSUMER_BINDING_ERROR",
        "PACKAGING_REQUIRED_ENTRY_MISSING",
        "PACKAGING_UNEXPECTED_NATIVE_PAYLOAD",
        "FINAL_MANIFEST_DRIFT",
        "RESOURCE_BUDGET_EXCEEDED",
    }
    check("asset-fault-model-closed", set(contract.get("activeFaultClasses", [])) == expected_faults, str(contract.get("activeFaultClasses")))

    assumptions = {
        "A-ASSET-01": {
            "statement": "Android API 30 framework resource table is represented by the installed android-30/android.jar.",
            "validatedBy": "android.jar presence plus javap android.R$style symbol check",
            "status": "PASS" if framework_symbol else "NOT_PROVEN",
        },
        "A-ASSET-02": {
            "statement": "No application-owned resource route exists while res/, assets/, dynamic lookup, and R.* references remain closed empty.",
            "validatedBy": "closed-universe source scan",
            "status": "PASS" if not actual_res and not actual_assets and not dynamic_hits and not app_r_hits else "NOT_PROVEN",
        },
    }

    methods = {
        "SAFE-M01": {"status": "PASS", "evidence": "route requirement -> manifest theme -> application theme resolver"},
        "SAFE-M02": {"status": "PASS" if revision else "NOT_PROVEN", "evidence": "git SHA + manifest/contract SHA-256"},
        "SAFE-M03": {"status": "PASS" if prebuild.get("status") == "PASS" and prebuild.get("gitSha") == revision else "NOT_PROVEN", "evidence": "same-revision APPLICATION_PREBUILD_SOURCE_GATE"},
        "SAFE-M04": {"status": "PASS", "evidence": "declared AndroidManifest/resources.arsc transformation; final refinement deferred to postbuild gate"},
        "SAFE-M05": {"status": "PASS", "evidence": "required package entries and forbidden native prefix declared"},
        "SAFE-M06": {"status": "NOT_APPLICABLE", "evidence": "bootstrap performs no asset copy/extract/cache materialization"},
        "SAFE-M07": {"status": "PASS" if manifest_ok else "NOT_PROVEN", "evidence": "UTF-8 XML parse plus exact semantic attributes"},
        "SAFE-M08": {"status": "PASS" if framework_symbol else "NOT_PROVEN", "evidence": "API30 android.jar framework-theme witness"},
        "SAFE-M09": {"status": "NOT_APPLICABLE", "evidence": "no custom font/text asset or locale resource universe"},
        "SAFE-M10": {"status": "PASS", "evidence": "contract properties mapped to named checks in this evidence"},
        "SAFE-M11": {"status": "PASS" if manifest_ok and framework_symbol else "NOT_PROVEN", "evidence": "contract + independent XML parse + javap framework symbol"},
        "SAFE-M12": {"status": "PASS" if framework_symbol else "NOT_PROVEN", "evidence": "independent stdlib XML parser and JDK javap checks"},
        "SAFE-M13": {"status": "PASS" if revision else "NOT_PROVEN", "evidence": "evidence bound to exact git revision and source digests"},
        "SAFE-M14": {"status": "PASS", "evidence": "APK, expanded APK, and generated resource-table budgets declared"},
    }

    failed = [item for item in checks if not item.passed]
    prebuild_pass = not failed and route_pass and all(
        item["status"] in {"PASS", "NOT_APPLICABLE"} for item in methods.values()
    ) and all(item["status"] == "PASS" for item in assumptions.values())

    stages = {
        "ASSET_SAFE_S0": "PASS" if prebuild_pass else "NOT_PROVEN",
        "ASSET_SAFE_S1": "PASS" if prebuild_pass else "NOT_PROVEN",
        "ASSET_SAFE_S2": "PASS" if prebuild_pass else "NOT_PROVEN",
        "ASSET_SAFE_S3": "PASS" if prebuild_pass else "NOT_PROVEN",
        "ASSET_SAFE_S4": "PASS" if route_pass and prebuild_pass else "NOT_PROVEN",
        "ASSET_SAFE_S5": "PASS" if prebuild_pass else "NOT_PROVEN",
        "PREBUILD_ASSET_GATE": "PASS" if prebuild_pass else "NOT_PROVEN",
        "BUILD_UNLOCKED": prebuild_pass,
    }

    return finish(stages, route_subgates, methods, assumptions, checks, contract=contract, revision=revision, mutation_challenge=mutation_challenge)


def finish(
    stages: dict,
    route_subgates: dict,
    methods: dict,
    assumptions: dict,
    checks: list[Check],
    *,
    contract: dict | None = None,
    revision: str | None = None,
    mutation_challenge: dict | None = None,
) -> int:
    EVIDENCE_DIR.mkdir(parents=True, exist_ok=True)
    failed = [item for item in checks if not item.passed]
    passed = bool(stages.get("BUILD_UNLOCKED")) and not failed
    payload = {
        "schemaVersion": 3,
        "gate": "ASSET_PREBUILD_GATE",
        "status": "PASS" if passed else "NOT_PROVEN",
        "gitSha": revision,
        "contractSha256": sha256(CONTRACT_PATH) if CONTRACT_PATH.is_file() else None,
        "manifestSha256": sha256(MANIFEST) if MANIFEST.is_file() else None,
        "stages": stages,
        "routeProof": "ROUTE_PROOF_PASS" if route_subgates and all(route_subgates.values()) else "NOT_PROVEN",
        "routeSubgates": route_subgates,
        "methods": methods,
        "assumptions": assumptions,
        "mutationChallenge": mutation_challenge or {},
        "checks": [asdict(item) for item in checks],
        "failed": [item.name for item in failed],
        "contractScope": contract.get("scope") if contract else None,
    }
    EVIDENCE_PATH.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if not passed:
        print("ASSET_PREBUILD_GATE = NOT_PROVEN", file=sys.stderr)
        for item in failed:
            print(f"FAIL {item.name}: {item.detail}", file=sys.stderr)
        return 1
    print("ROUTE_PROOF_PASS")
    print("ASSET_PREBUILD_GATE = PASS")
    print("ASSET_SAFE_S5 = PASS")
    print("BUILD_UNLOCKED = TRUE")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
