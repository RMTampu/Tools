# Kernel Foundation Acceptance Tests

The kernel foundation is accepted when CI verifies all of the following:

1. Normal module load/start/stop lifecycle succeeds.
2. Dependencies load/start before dependents and stop in reverse order.
3. Missing required dependencies and dependency cycles fail before lifecycle callbacks run.
4. Missing optional dependencies do not block startup.
5. Invalid descriptors, incompatible API ranges, wrong ABI, duplicate IDs, and source/descriptor ID mismatches are rejected as structured results.
6. Required capabilities must exist before the dependent module is loaded.
7. Runtime installation failure is atomic and does not leave a failed registration behind.
8. `onStop` failure prevents normal uninstall and remains observable.
9. `onUnload` is called on successful uninstall.
10. Managed services, capabilities, commands, and event subscriptions are removed when their owner module is unloaded.
11. One module cannot replace another module's owned registry entry.
12. Event listener exceptions are isolated and reported through the logger.
13. Wildcard event subscribers receive a wildcard-topic event once, not twice.
14. `snapshot()` never invokes module `healthCheck()`.
15. Explicit health probing updates cached health and can degrade kernel state.
16. Previous persisted kernel state is read before the new runtime writes `NEW`.
17. Failed modules can be retried from a clean managed-resource scope.
18. Kotlin explicit API mode and all unit tests pass under GitHub Actions.
