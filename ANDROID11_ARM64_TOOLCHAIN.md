# ToolBox Android 11 / API 30 / ARM64 Toolchain Contract

## Purpose

This file defines the single supported GitHub toolchain profile for the current ToolBox distribution target. It exists to prevent failures caused by accidental version drift, host-architecture drift, or confusing a GitHub runner limitation with an Android/application fault.

Canonical machine-readable source: `verification/toolchains/android11-arm64.lock.json`.

Every production build must pass `verification/scripts/toolchain_gate.py --mode build` before any application build task is allowed to run.

## Device target

- Android release: 11
- API level: 30
- ABI: `arm64-v8a`
- `minSdk`: 30
- `targetSdk`: 30
- `compileSdk`: 35
- Java bytecode target: 11
- Kotlin JVM target: 11

`compileSdk=35` does **not** change the runtime target to Android 15. The runtime contract remains API 30. The compile SDK is intentionally paired with Android Gradle Plugin 8.6.1 because AGP 8.6 supports API 35 and requires Gradle 8.7, JDK 17, and Build Tools 34.0.0.

## Locked build host profile

| Component | Locked value |
|---|---|
| GitHub runner family | `ubuntu-24.04` |
| Host architecture | `x86_64` |
| Temurin JDK setup | `17.0.20+1` |
| Actual Java runtime | `17.0.20.1+1` |
| Gradle | `8.7` |
| Gradle ZIP SHA-256 | `544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d` |
| Android command-line tools build | `14742923` |
| `sdkmanager` | `20.0` |
| Platform Tools | `37.0.1` |
| Android SDK Platform 30 | revision `3` |
| Android SDK Platform 35 | revision `2` |
| Build Tools | `34.0.0` |
| Android Gradle Plugin | `8.6.1` |
| Kotlin | `1.9.24` |
| Binary compatibility validator | `0.18.1` |

The build profile was independently installed and checked on GitHub Actions. Preinstalled runner tools are not trusted as the project toolchain. For example, the Ubuntu runner had command-line tools 12.0 preinstalled; CI replaced them with the exact requested command-line-tools build.

## Locked direct test/development dependency profile

- Kotlin stdlib `1.9.24`
- JUnit BOM / Jupiter engine `5.10.2`
- JUnit Platform launcher `1.10.2`
- AndroidX Test Core `1.7.0`
- AndroidX Test Runner `1.7.0`
- AndroidX Test Ext JUnit `1.3.0`
- Espresso Core `3.7.0`
- UIAutomator `2.3.0`
- Test Orchestrator `1.6.1`

UIAutomator `2.3.0` is deliberate. `2.4.0` publishes Kotlin metadata outside the repository-locked Kotlin 1.9.24 compiler boundary. The project must not upgrade it merely because a newer version exists.

All Gradle configurations are dependency-locked and dependency verification uses checked-in SHA-256 metadata. Dynamic dependency versions are prohibited.

## Runtime package profile

The exact Android 11 ARM64 package tuple has been proven installable on a macOS ARM64 GitHub runner:

| Component | Locked value |
|---|---|
| Host architecture | `arm64` |
| Temurin JDK | `17.0.20+8` |
| Android command-line tools build | `14742923` |
| Platform Tools | `37.0.1` |
| Android Emulator | `37.1.11` |
| Emulator build ID | `15917651` |
| System image | `system-images;android-30;google_apis;arm64-v8a` |
| System image revision | `16` |
| System image API | `30` |
| System image ABI | `arm64-v8a` |

The JDK build is intentionally host-specific: the exact x64 build JDK `17.0.20+1` is not published for the ARM64 macOS host used by this probe; `17.0.20+8` is the proven compatible ARM64 build. A single JDK build number must therefore never be assumed across host architectures.

## Runtime host capability boundary

GitHub-hosted macOS ARM64 runners do not provide nested virtualization. The Android ARM64 emulator package installs correctly, but emulator boot fails with `HV_UNSUPPORTED` because HVF cannot be initialized. Passing `-accel off` does not bypass this requirement for the current ARM64 emulator path.

This status means:

- Android/API/ABI package versions: **PROVEN**
- package compatibility: **PROVEN**
- hosted macOS ARM64 emulator boot: **NOT_PROVEN / host capability unavailable**
- application or asset failure: **NOT implied**

A runtime route is acceptable only when an actual booted device proves all of:

- `ro.build.version.release = 11`
- `ro.build.version.sdk = 30`
- `ro.product.cpu.abi = arm64-v8a`

An x86 or x86_64 Android guest is never an acceptable substitute for ARM64 proof.

## Mandatory gate order

1. Install only the declared toolchain profile.
2. Run `toolchain_gate.py --mode source` when auditing configuration without installed tools.
3. Run `toolchain_gate.py --mode build` before any Gradle build or production packaging.
4. For runtime hosts, run `toolchain_gate.py --mode runtime-package` before creating the AVD/device session.
5. After boot/connect, run `toolchain_gate.py --mode runtime-device` before instrumentation/runtime proof.
6. If any version or required property differs, fail immediately with `ANDROID11_ARM64_TOOLCHAIN[...] = NOT_PROVEN`.

## Error classification

Failures must be classified before changing code or assets:

- **VERSION_DRIFT** — installed component does not match the lock.
- **HOST_ARCH_MISMATCH** — tool binary/package is valid but for a different host architecture.
- **HOST_CAPABILITY_UNAVAILABLE** — versions are correct but runner cannot provide required virtualization/hardware feature.
- **DEPENDENCY_DRIFT** — Gradle dependency lock or verification metadata does not match.
- **DEVICE_TARGET_MISMATCH** — booted/test device is not Android 11/API30/ARM64.
- **APPLICATION_FAILURE** — only after the complete environment gate passes.
- **ASSET_FAILURE** — only after evidence proves the asset itself is the fault source.

Do not weaken or silently update the lock to make CI green. Any intentional toolchain update requires a dedicated probe, compatibility review, updated evidence, and then an explicit lock revision.
