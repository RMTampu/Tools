# Kernel Foundation Acceptance Tests

The kernel foundation is accepted when CI verifies all of the following:

1. Normal module load/start/stop lifecycle succeeds.
2. Dependencies load/start before dependents and stop in reverse order.
3. Missing required dependencies and dependency cycles fail before lifecycle callbacks run.
4. Missing optional dependencies do not block startup.
5. Invalid descriptors, incompatible API ranges, wrong ABI, duplicate IDs, and source/descriptor ID mismatches are rejected as structured results.
6. The actual runtime Android API/ABI supplied by the host must match the configured target before a module can be installed.
7. External source metadata is inspected and fully preflighted before `ModuleLoader.load()` is invoked.
8. A loader whose runtime module descriptor drifts from inspected metadata is rejected without registration.
9. Required capabilities must exist before the dependent module is loaded.
10. Runtime installation failure is atomic and does not leave a failed registration behind.
11. `onStop` failure prevents normal uninstall and remains observable.
12. `onUnload` is called on successful uninstall.
13. Managed services, capabilities, commands, and event subscriptions are removed when their owner module is unloaded.
14. One module cannot replace another module's owned registry entry.
15. Event listener exceptions are isolated and reported through the logger.
16. Wildcard event subscribers receive a wildcard-topic event once, not twice.
17. `snapshot()` never invokes module `healthCheck()`.
18. Explicit health probing updates cached health and can degrade kernel state.
19. Previous persisted kernel state is read before the new runtime writes `NEW`.
20. Failed modules can be retried from a clean managed-resource scope.
21. Lifecycle callbacks run without a kernel monitor/registry lock that can deadlock a callback-owned worker thread.
22. Competing lifecycle mutations fail deterministically with `OPERATION_IN_PROGRESS` rather than block indefinitely.
23. Kotlin explicit API mode and all unit tests pass under GitHub Actions.
24. CI actions are pinned to reviewed commit SHAs and checkout does not persist credentials.

## Application Safety Acceptance

`APPLICATION_SAFE_100` is accepted only when `.github/workflows/application-safe-100.yml` proves, for the same Git revision:

1. A0–A9 prebuild closure emits `APPLICATION_PREBUILD_PASS` before any APK assembly.
2. Dependency resolution converges from two independent clean Gradle homes under strict lock/checksum verification.
3. Release signing identity is pinned before build and matches the keystore supplied only through GitHub Actions secrets.
4. Two clean release builds are byte-identical.
5. Debug and release APK container/manifest/signature gates pass.
6. The exact verified APK digests are the APK digests exercised at runtime.
7. Android runtime is exactly API 30 with primary ABI `arm64-v8a`.
8. Lifecycle, process death/restart, external input, accessibility, orientation, font scale, UI mode, compact-screen, memory, startup, and rendering budgets pass.
9. Active application/asset mutations are detected with `FAULT_ESCAPE = 0`.
10. Cross-domain challenge passes and R1–R9 all close with UNKNOWN/MISSING/UNPROVEN/SKIPPED/INDETERMINATE/STALE_EVIDENCE/FAULT_ESCAPE equal to zero.

A build success alone never satisfies this acceptance section.
