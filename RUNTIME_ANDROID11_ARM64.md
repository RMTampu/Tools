# Android 11 ARM64 Runtime Proof Route

## Canonical route
Runtime proof uses an actually attached ADB target and requires Android release 11, API 30, primary ABI `arm64-v8a`, boot completed, exact source revision, and exact APK digests from static GitHub Actions provenance. The runtime job never rebuilds production APKs.

The canonical workflow is `.github/workflows/application-runtime-r9.yml`. Its runtime job requires the self-hosted label `toolbox-android11-arm64-runtime`.

## Canonical artifact layout
Static CI stages exactly `verification/runtime-bundle/` with `candidate-apks/` and `evidence/`. After download to `candidate`, valid paths are `candidate/candidate-apks/` and `candidate/evidence/`. `candidate/verification/...` is invalid.

## Hosted routes closed by actual evidence
- Ubuntu x64 + ARM64 guest: Emulator 37.1.11 rejects ARM64 guest on x86_64 host. Run 33431096853.
- macOS ARM64 + ARM64 guest: startup fails at `HV_UNSUPPORTED`; nested virtualization is unavailable. Run 33431361441.
- Ubuntu ARM64 (`ubuntu-24.04-arm`): host is AArch64 but `sdkmanager` exposes no Linux ARM64 `emulator` package and `/dev/kvm` is absent. Run 33456579283.

These are infrastructure failures, not asset failures. Do not retry a closed route by changing AVD paths, graphics flags, or runner labels. Reopen it only after the missing infrastructure capability is independently proven.
