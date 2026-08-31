# ToolBox — Locked Android 11 / API 30 / ARM64 Toolchain

## Status

This document is the human-readable contract for machine profile `toolbox-android11-api30-arm64-v1` in `verification/toolchains/android11-arm64.lock.json`.

The profile is **fail-closed**. A newer version is not automatically safer or compatible. Any version, host architecture, SDK package revision, dependency, or runtime target outside this profile is `NOT_PROVEN` until this contract and the machine lock are deliberately revised and revalidated.

## Device target

- Android release: **11**
- Android API: **30**
- Primary ABI: **arm64-v8a**
- `minSdk`: **30**
- `targetSdk`: **30**
- `compileSdk`: **35**
- Java bytecode target: **11**
- Kotlin JVM target: **11**

`compileSdk=35` is a build-time compiler input and does not change the runtime product target. The install/runtime contract remains Android 11/API30/ARM64.

## GitHub build host

Production build verification is locked to:

- runner: `ubuntu-24.04`
- host architecture: `x86_64`
- Temurin setup JDK: `17.0.20+1`
- observed JDK runtime: `17.0.20.1+1`
- Gradle: `8.7`
- Gradle distribution SHA-256: `544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d`
- Android command-line tools download build: `14742923`
- installed command-line tools package revision: `20.0`
- Android Platform Tools: `37.0.1`
- Android Platform 30 revision: `3`
- Android Platform 35 revision: `2`
- Android Build Tools: `34.0.0`
- Android Gradle Plugin: `8.6.1`
- Kotlin: `1.9.24`
- Kotlin binary compatibility validator: `0.18.1`

The command-line tools download build ID and installed package revision are different identities. Build `14742923` installs `cmdline-tools/20.0`; gates must validate both rather than incorrectly comparing the build ID with the filesystem package version.

## Direct dependency compatibility set

The current direct dependency set is locked to:

- `org.jetbrains.kotlin:kotlin-stdlib:1.9.24`
- JUnit BOM `5.10.2`
- JUnit Jupiter engine `5.10.2`
- JUnit Platform launcher `1.10.2`
- AndroidX Test Core `1.7.0`
- AndroidX Test Runner `1.7.0`
- AndroidX Test Ext JUnit `1.3.0`
- Espresso Core `3.7.0`
- UIAutomator `2.3.0`
- Test Orchestrator `1.6.1`

UIAutomator `2.4.0` is intentionally excluded from this profile because it publishes Kotlin 2.1 metadata that is incompatible with the repository-locked Kotlin 1.9.24 compiler. `2.3.0` has already been proven with the current instrumentation source and dependency trust chain.

All transitive artifacts remain subject to Gradle dependency locking and strict verification metadata/checksums.

## ARM64 runtime package host

For Android 11 ARM64 runtime-package qualification, GitHub's ARM64 macOS environment is locked to:

- runner: `macos-15` (observed `macos-15-arm64`)
- host architecture: `arm64`
- Temurin JDK: `17.0.20+8`
- Android command-line tools download build: `14742923`
- installed command-line tools revision: `20.0`
- Platform Tools: `37.0.1`
- Android Emulator: `37.1.11`
- Emulator Build ID: `15917651`
- system image: `system-images;android-30;google_apis;arm64-v8a`
- system image revision: `16`
- guest API: `30`
- guest ABI: `arm64-v8a`

Temurin `17.0.20+1` must not be assumed to exist on ARM64 simply because it exists on the x64 build host. GitHub/Temurin resolution proved that ARM64 provides `17.0.20+8`, so build-host and runtime-host JDK builds are intentionally host-specific.

## Runtime capability boundary

Correct package versions do not imply that a GitHub-hosted runner can boot the required guest.

The current hosted macOS ARM64 environment lacks the nested virtualization/HVF capability required for the Android Emulator ARM64 path. This is classified as:

`HOST_CAPABILITY_UNAVAILABLE`

It is **not** `ASSET_FAILURE`, `APPLICATION_FAILURE`, or `VERSION_DRIFT`.

No x86/x86_64 Android guest may be substituted as proof for the ARM64 product target. Final runtime proof requires a real API30 ARM64-capable execution environment whose device properties prove:

- `ro.build.version.release = 11`
- `ro.build.version.sdk = 30`
- `ro.product.cpu.abi = arm64-v8a`

## Mandatory gate order

Before application compilation/build:

1. load `verification/toolchains/android11-arm64.lock.json`;
2. run `verification/scripts/toolchain_gate.py --mode source` where applicable;
3. provision the exact host-specific JDK, Gradle and Android SDK inputs;
4. run `verification/scripts/toolchain_gate.py --mode build`;
5. run the normal A0 prebuild gate, which independently invokes the build toolchain gate;
6. only then may kernel/app compilation and APK build proceed.

For runtime-package qualification:

1. provision the exact ARM64 host JDK and Android SDK packages;
2. run `toolchain_gate.py --mode runtime-package`;
3. separately qualify host virtualization/device capability;
4. after a real target boots/connects, run `toolchain_gate.py --mode runtime-device` before runtime/R9 proof.

## Failure classification

Use these categories so environment defects are not misreported as application or asset defects:

- `VERSION_DRIFT` — installed/declared tool revision differs from the lock.
- `DEPENDENCY_DRIFT` — direct/transitive dependency identity is outside the locked/verified graph.
- `HOST_ARCH_MISMATCH` — workflow executes on a host architecture different from the declared profile.
- `HOST_CAPABILITY_UNAVAILABLE` — versions are correct but required virtualization/device capability is unavailable.
- `DEVICE_TARGET_MISMATCH` — connected guest/device is not Android 11/API30/ARM64.
- `APPLICATION_FAILURE` — target environment is proven correct and application behavior fails.
- `ASSET_FAILURE` — asset/resource evidence itself proves an asset defect.

## Change rule

Any change to JDK, Gradle, AGP, Kotlin, compile/min/target SDK, Build Tools, command-line tools, Platform Tools, emulator, system image, direct dependency versions, runner OS/architecture, or ABI invalidates the corresponding toolchain proof.

Do not upgrade merely because a newer version exists. Change the lock only when required, then rerun the dedicated `Android 11 ARM64 Toolchain Gate Validation` workflow and all downstream APPLICATION_SAFE_100 evidence affected by the change.
