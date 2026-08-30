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
21. Kotlin explicit API mode and all unit tests pass under GitHub Actions.
22. CI actions are pinned to reviewed commit SHAs and checkout does not persist credentials.
