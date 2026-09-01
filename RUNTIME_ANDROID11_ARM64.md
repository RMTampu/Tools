# Android 11 ARM64 Runtime Proof Route

## Canonical route
Runtime proof uses an actually attached ADB target and requires Android release 11, API 30, primary ABI `arm64-v8a`, boot completed, exact source revision, and exact APK digests from static GitHub Actions provenance. The runtime job never rebuilds production APKs.

The canonical workflow is `.github/workflows/application-runtime-r9.yml`. Its runtime job requires the self-hosted labels `self-hosted`, `linux`, and `toolbox-android11-arm64-runtime`.

The host running the GitHub self-hosted runner is only the controller. The Android proof is taken from the attached ADB target, so the controller CPU architecture is not used as a substitute for the device ABI.

## Canonical artifact layout
Static CI stages exactly `verification/runtime-bundle/` with `candidate-apks/` and `evidence/`. After download to `candidate`, valid paths are:

- `candidate/candidate-apks/`
- `candidate/evidence/`

`candidate/verification/...` is invalid. `verification/scripts/runtime_ci_guard.py` fails the static pipeline if the wrong nested layout is reintroduced.

## Runtime target safety contract
Before any APK is installed or any runtime configuration is mutated, `verification/scripts/runtime_environment_gate.py` must prove all of the following:

- exactly one ADB target is in `device` state;
- that target is Android 11 / API 30;
- its primary ABI is exactly `arm64-v8a`;
- boot is complete;
- the unique serial is bound through `ANDROID_SERIAL` for all later commands;
- `io.toolbox.app`, `io.toolbox.app.debug`, and `io.toolbox.app.debug.test` are not already installed.

The package-absence rule is deliberate. Runtime proof is destructive to the candidate package because clean-install semantics must be tested. A device that already contains a ToolBox installation is rejected instead of silently uninstalling or overwriting user state.

`verification/scripts/runtime_gate.py` must snapshot the device configuration before changing it and restore the exact original state afterward, including:

- font scale;
- accelerometer rotation mode;
- user rotation;
- display size override;
- display density override;
- night-mode state.

A runtime result is not PASS if exact state restoration cannot be proven. The workflow also uninstalls instrumentation/debug candidates between phases so debug and release evidence cannot contaminate each other.

## Closed hosted routes
The following hosted-emulator routes are closed by actual evidence and must not be retried unless the missing infrastructure capability is independently proven to have changed:

- Ubuntu x64 + ARM64 guest: Emulator 37.1.11 rejects an ARM64 guest on an x86_64 host. Run 33431096853.
- macOS ARM64 + ARM64 guest: startup fails at `HV_UNSUPPORTED`; nested virtualization is unavailable. Run 33431361441.
- Ubuntu ARM64 (`ubuntu-24.04-arm`): host is AArch64 but `sdkmanager` exposes no Linux ARM64 `emulator` package and `/dev/kvm` is absent. Run 33456579283.

These are infrastructure failures, not asset failures. Do not retry a closed route by changing AVD paths, graphics flags, package versions, or runner labels. Reopen a route only after the previously missing capability is proven independently.

## One-time runner requirement
The remaining external prerequisite is a Linux self-hosted GitHub runner carrying the label `toolbox-android11-arm64-runtime` with `adb` available and exactly one approved Android 11 ARM64 target attached. This is infrastructure setup, not an APK build step. The APK still comes only from the successful static GitHub Actions run and is never rebuilt on the runtime host.

Termux must not be turned into an application build environment. No Termux package installation is authorized by this runtime route.
