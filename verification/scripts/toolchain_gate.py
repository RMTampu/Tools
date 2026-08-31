#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_LOCK = ROOT / "verification" / "toolchains" / "android11-arm64.lock.json"


def run(*args: str) -> str:
    proc = subprocess.run(args, cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False)
    if proc.returncode != 0:
        raise RuntimeError(f"command failed ({proc.returncode}): {' '.join(args)}\n{proc.stdout}")
    return proc.stdout


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def props(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        result[key.strip()] = value.strip()
    return result


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def normalize_arch(value: str) -> str:
    value = value.lower()
    return {"amd64": "x86_64", "aarch64": "arm64"}.get(value, value)


def main() -> int:
    parser = argparse.ArgumentParser(description="Fail-closed ToolBox Android 11/API30/ARM64 toolchain gate")
    parser.add_argument("--mode", choices=("source", "build", "runtime-package", "runtime-device"), required=True)
    parser.add_argument("--lock", default=str(DEFAULT_LOCK))
    parser.add_argument("--evidence")
    args = parser.parse_args()

    lock_path = Path(args.lock)
    if not lock_path.is_absolute():
        lock_path = ROOT / lock_path
    lock = json.loads(lock_path.read_text(encoding="utf-8"))
    checks: list[dict[str, object]] = []

    def check(name: str, passed: bool, detail: object) -> None:
        checks.append({"name": name, "passed": bool(passed), "detail": str(detail)})

    target = lock["deviceTarget"]
    build = lock["buildHost"]
    runtime = lock["runtimePackages"]
    deps = lock["directDependencies"]

    root_gradle = read("build.gradle.kts")
    app_gradle = read("toolbox-app/build.gradle.kts")
    settings = read("settings.gradle.kts")
    application_workflow = read(".github/workflows/application-safe-100.yml")
    validation_workflow = read(".github/workflows/android11-arm64-toolchain-gate-validation.yml")

    check("profile-id", lock.get("profileId") == "toolbox-android11-api30-arm64-v1", lock.get("profileId"))
    check("target-api30", target.get("apiLevel") == 30, target.get("apiLevel"))
    check("target-abi-arm64", target.get("abi") == "arm64-v8a", target.get("abi"))
    check("source-compile-sdk", f'compileSdk = {target["compileSdk"]}' in app_gradle, target["compileSdk"])
    check("source-min-sdk", f'minSdk = {target["minSdk"]}' in app_gradle, target["minSdk"])
    check("source-target-sdk", f'targetSdk = {target["targetSdk"]}' in app_gradle, target["targetSdk"])
    check("source-build-tools", f'buildToolsVersion = "{build["androidBuildTools"]}"' in app_gradle, build["androidBuildTools"])
    check("source-java-bytecode", f'JavaVersion.VERSION_{target["javaBytecodeTarget"]}' in app_gradle, target["javaBytecodeTarget"])
    check("source-kotlin-jvm", f'jvmTarget = "{target["kotlinJvmTarget"]}"' in app_gradle, target["kotlinJvmTarget"])
    check("source-agp", f'id("com.android.application") version "{build["agp"]}"' in root_gradle, build["agp"])
    check("source-kotlin-plugin", f'id("org.jetbrains.kotlin.android") version "{build["kotlin"]}"' in root_gradle, build["kotlin"])
    check("source-kotlin-jvm-plugin", f'kotlin("jvm") version "{build["kotlin"]}"' in root_gradle, build["kotlin"])
    check("source-binary-api-validator", f'id("org.jetbrains.kotlinx.binary-compatibility-validator") version "{build["binaryCompatibilityValidator"]}"' in root_gradle, build["binaryCompatibilityValidator"])
    check("source-dependency-locking", "lockAllConfigurations()" in root_gradle, "lockAllConfigurations")
    check("source-repositories-centralized", "RepositoriesMode.FAIL_ON_PROJECT_REPOS" in settings, "FAIL_ON_PROJECT_REPOS")

    # Production workflow declarations are checked directly. A correct version
    # appearing in some unrelated workflow cannot satisfy these checks.
    check("production-build-runner", f"runs-on: {build['runner']}" in application_workflow, build["runner"])
    check("production-build-jdk", f"java-version: '{build['jdk']['setupVersion']}'" in application_workflow, build["jdk"]["setupVersion"])
    check("production-build-jdk-arch", "architecture: x64" in application_workflow, "x64")
    check("production-build-gradle", f"gradle-version: '{build['gradle']['version']}'" in application_workflow, build["gradle"]["version"])
    check("production-cmdline-tools-download-build", f"cmdline-tools-version: '{build['androidCommandLineTools']['setupBuild']}'" in application_workflow, build["androidCommandLineTools"]["setupBuild"])
    for package in (
        "platforms;android-30",
        f"platforms;android-{target['compileSdk']}",
        f"build-tools;{build['androidBuildTools']}",
        "platform-tools",
    ):
        check(f"production-sdk-package-{package}", package in application_workflow, package)

    runtime_pref = runtime["preferredHost"]
    check("validation-runtime-runner", f"runs-on: {runtime_pref['runner']}" in validation_workflow, runtime_pref["runner"])
    check("validation-runtime-jdk", f"java-version: '{runtime_pref['jdk']['setupVersion']}'" in validation_workflow, runtime_pref["jdk"]["setupVersion"])
    check("validation-runtime-jdk-arch", "architecture: arm64" in validation_workflow, "arm64")
    check("validation-runtime-cmdline-tools", f"cmdline-tools-version: '{runtime['androidCommandLineTools']['setupBuild']}'" in validation_workflow, runtime["androidCommandLineTools"]["setupBuild"])
    check("validation-runtime-system-image", runtime["systemImage"]["package"] in validation_workflow, runtime["systemImage"]["package"])

    direct_needles = {
        "dependency-kotlin-stdlib": f'org.jetbrains.kotlin:kotlin-stdlib:{deps["kotlinStdlib"]}',
        "dependency-junit-bom": f'org.junit:junit-bom:{deps["junitBom"]}',
        "dependency-junit-engine": f'org.junit.jupiter:junit-jupiter-engine:{deps["junitJupiterEngine"]}',
        "dependency-junit-launcher": f'org.junit.platform:junit-platform-launcher:{deps["junitPlatformLauncher"]}',
        "dependency-androidx-test-core": f'androidx.test:core:{deps["androidxTestCore"]}',
        "dependency-androidx-test-runner": f'androidx.test:runner:{deps["androidxTestRunner"]}',
        "dependency-androidx-test-junit": f'androidx.test.ext:junit:{deps["androidxTestExtJunit"]}',
        "dependency-espresso": f'androidx.test.espresso:espresso-core:{deps["espressoCore"]}',
        "dependency-uiautomator": f'androidx.test.uiautomator:uiautomator:{deps["uiautomator"]}',
        "dependency-orchestrator": f'androidx.test:orchestrator:{deps["orchestrator"]}',
    }
    for name, needle in direct_needles.items():
        check(name, needle in app_gradle, needle)

    dynamic = re.findall(r'["\']([^"\']+:[^"\']+:(?:\+|latest\.[^"\']+|[^"\']*[+*][^"\']*))["\']', root_gradle + "\n" + app_gradle)
    check("source-no-dynamic-dependency-version", not dynamic, dynamic or "none")
    check("source-gradle-lockfile", (ROOT / "toolbox-app" / "gradle.lockfile").is_file(), "toolbox-app/gradle.lockfile")
    check("source-dependency-verification", (ROOT / "gradle" / "verification-metadata.xml").is_file(), "gradle/verification-metadata.xml")

    if args.mode == "build":
        check("build-host-os", platform.system().lower() == build["os"], platform.system())
        check("build-host-arch", normalize_arch(platform.machine()) == build["arch"], platform.machine())
        java = run("java", "-version")
        check("build-jdk-runtime", build["jdk"]["runtimeVersion"] in java, java.splitlines()[0] if java else "")
        check("build-jdk-temurin", "Temurin" in java, java.splitlines()[:2])
        gradle = run("gradle", "--version")
        check("build-gradle-version", re.search(rf"(?m)^Gradle {re.escape(build['gradle']['version'])}$", gradle) is not None, build["gradle"]["version"])
        sdkmanager_path = shutil.which("sdkmanager") or ""
        check("build-sdkmanager-present", bool(sdkmanager_path), sdkmanager_path)
        expected_dir = f"/cmdline-tools/{build['androidCommandLineTools']['sdkmanagerVersion']}/bin/sdkmanager"
        check("build-cmdline-tools-installed-version-path", sdkmanager_path.endswith(expected_dir), sdkmanager_path)
        sdkmanager_version = run("sdkmanager", "--version").strip().splitlines()[0]
        check("build-sdkmanager-version", sdkmanager_version == build["androidCommandLineTools"]["sdkmanagerVersion"], sdkmanager_version)
        cmd_props = props(Path(sdkmanager_path).parents[1] / "source.properties")
        check("build-cmdline-tools-pkg-revision", cmd_props.get("Pkg.Revision") == build["androidCommandLineTools"]["sdkmanagerVersion"], cmd_props.get("Pkg.Revision"))
        sdk_root = Path(os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME") or "")
        check("build-sdk-root", sdk_root.is_dir(), sdk_root)
        if sdk_root.is_dir():
            pt = props(sdk_root / "platform-tools" / "source.properties")
            p30 = props(sdk_root / "platforms" / "android-30" / "source.properties")
            p35 = props(sdk_root / "platforms" / "android-35" / "source.properties")
            bt = props(sdk_root / "build-tools" / build["androidBuildTools"] / "source.properties")
            check("build-platform-tools", pt.get("Pkg.Revision") == build["androidPlatformTools"], pt.get("Pkg.Revision"))
            check("build-platform-api30-revision", p30.get("Pkg.Revision") == build["androidPlatforms"]["30"], p30.get("Pkg.Revision"))
            check("build-platform-api30-release", p30.get("Platform.Version") == "11", p30.get("Platform.Version"))
            check("build-platform-api30-level", p30.get("AndroidVersion.ApiLevel") == "30", p30.get("AndroidVersion.ApiLevel"))
            check("build-platform-api35-revision", p35.get("Pkg.Revision") == build["androidPlatforms"]["35"], p35.get("Pkg.Revision"))
            check("build-build-tools", bt.get("Pkg.Revision") == build["androidBuildTools"], bt.get("Pkg.Revision"))
        runner_temp = Path(os.environ.get("RUNNER_TEMP", ""))
        gradle_zip = runner_temp / ".gradle-actions" / "gradle-installations" / "downloads" / f"gradle-{build['gradle']['version']}-bin.zip"
        check("build-gradle-distribution-present", gradle_zip.is_file(), gradle_zip)
        if gradle_zip.is_file():
            check("build-gradle-distribution-sha256", sha256(gradle_zip) == build["gradle"]["distributionSha256"], sha256(gradle_zip))

    elif args.mode == "runtime-package":
        pref = runtime["preferredHost"]
        check("runtime-host-os", platform.system().lower() == pref["os"], platform.system())
        check("runtime-host-arch", normalize_arch(platform.machine()) == pref["arch"], platform.machine())
        java = run("java", "-version")
        check("runtime-jdk", pref["jdk"]["runtimeVersion"] in java, java.splitlines()[:2])
        sdkmanager_path = shutil.which("sdkmanager") or ""
        expected_dir = f"/cmdline-tools/{runtime['androidCommandLineTools']['sdkmanagerVersion']}/bin/sdkmanager"
        check("runtime-cmdline-tools-installed-version-path", sdkmanager_path.endswith(expected_dir), sdkmanager_path)
        sdkmanager_version = run("sdkmanager", "--version").strip().splitlines()[0]
        check("runtime-sdkmanager-version", sdkmanager_version == runtime["androidCommandLineTools"]["sdkmanagerVersion"], sdkmanager_version)
        cmd_props = props(Path(sdkmanager_path).parents[1] / "source.properties")
        check("runtime-cmdline-tools-pkg-revision", cmd_props.get("Pkg.Revision") == runtime["androidCommandLineTools"]["sdkmanagerVersion"], cmd_props.get("Pkg.Revision"))
        sdk_root = Path(os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME") or "")
        pt = props(sdk_root / "platform-tools" / "source.properties")
        emu = props(sdk_root / "emulator" / "source.properties")
        image = props(sdk_root / "system-images" / "android-30" / "google_apis" / "arm64-v8a" / "source.properties")
        check("runtime-platform-tools", pt.get("Pkg.Revision") == runtime["androidPlatformTools"], pt.get("Pkg.Revision"))
        check("runtime-emulator-version", emu.get("Pkg.Revision") == runtime["emulator"]["version"], emu.get("Pkg.Revision"))
        check("runtime-emulator-build-id", emu.get("Pkg.BuildId") == runtime["emulator"]["buildId"], emu.get("Pkg.BuildId"))
        check("runtime-system-image-revision", image.get("Pkg.Revision") == runtime["systemImage"]["revision"], image.get("Pkg.Revision"))
        check("runtime-system-image-api", image.get("AndroidVersion.ApiLevel") == str(runtime["systemImage"]["apiLevel"]), image.get("AndroidVersion.ApiLevel"))
        check("runtime-system-image-abi", image.get("SystemImage.Abi") == runtime["systemImage"]["abi"], image.get("SystemImage.Abi"))
        check("runtime-system-image-tag", image.get("SystemImage.TagId") == runtime["systemImage"]["tag"], image.get("SystemImage.TagId"))

    elif args.mode == "runtime-device":
        adb = shutil.which("adb")
        check("runtime-adb-present", bool(adb), adb)
        if adb:
            for prop_name, expected in runtime["requiredDeviceProperties"].items():
                actual = run(adb, "shell", "getprop", prop_name).strip().replace("\r", "")
                check(f"device-{prop_name}", actual == expected, f"actual={actual} expected={expected}")

    failed = [item for item in checks if not item["passed"]]
    status = "PASS" if not failed else "NOT_PROVEN"
    payload = {
        "schemaVersion": 3,
        "gate": "ANDROID11_ARM64_TOOLCHAIN",
        "mode": args.mode,
        "status": status,
        "profileId": lock.get("profileId"),
        "gitSha": os.environ.get("GITHUB_SHA"),
        "checks": checks,
        "failed": [item["name"] for item in failed],
        "unknown": 0,
        "missing": len(failed),
        "unproven": len(failed),
        "skipped": 0,
        "indeterminate": 0,
        "staleEvidence": 0,
        "faultEscape": 0,
    }
    evidence = Path(args.evidence) if args.evidence else ROOT / "verification" / "evidence" / f"toolchain-{args.mode}.json"
    if not evidence.is_absolute():
        evidence = ROOT / evidence
    evidence.parent.mkdir(parents=True, exist_ok=True)
    evidence.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if failed:
        print(f"ANDROID11_ARM64_TOOLCHAIN[{args.mode}] = NOT_PROVEN")
        for item in failed:
            print(f"FAIL {item['name']}: {item['detail']}")
        return 1
    print(f"ANDROID11_ARM64_TOOLCHAIN[{args.mode}] = PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
