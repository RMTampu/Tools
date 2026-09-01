# BASELINE_CAPABILITY_CONTRACT_V1.md

Status: **FROZEN v1**

This document freezes the minimum capability foundation that ToolBox must preserve while the implementation grows. It is a contract for architecture and extension points, not a claim that every listed subsystem is already implemented.

## 1. Product target

- Primary product target: Android 11 / API 30.
- Primary release ABI: `arm64-v8a`.
- The kernel must remain small, UI-independent, and stable.
- New engines/tools must enter through explicit contracts and registries rather than direct coupling to kernel internals.

## 2. Non-negotiable kernel extension points

The foundation must preserve explicit extension points for:

1. module/runtime lifecycle;
2. service registry;
3. capability registry;
4. engine registry/host;
5. tool registry;
6. command routing;
7. event routing;
8. state/persistence adapter;
9. compatibility/admission policy;
10. health checking and failure isolation;
11. diagnostics;
12. update/evolution orchestration;
13. recovery/safe-mode orchestration;
14. workbench/UI integration without kernel-to-UI dependency.

Adding a capability that belongs to this baseline must not require rebuilding the kernel architecture because an expected extension point was omitted.

## 3. Engine/tool baseline contract

An engine/tool contract must be able to declare, at minimum:

- stable ID;
- version;
- contract version;
- Android API compatibility;
- ABI compatibility;
- required capabilities;
- provided capabilities;
- components/actions/events/data types it contributes;
- permission requirements;
- entry point identity;
- lifecycle requirements.

Discovery must be metadata-first. Runtime materialization must be lazy.

Direct tool-to-tool implementation dependency is not part of the baseline. Cross-tool cooperation must use contracts, services, commands, events, or capabilities.

## 4. Lifecycle baseline

Every executable engine/tool that is part of the trusted APK runtime must support a bounded lifecycle:

```text
REGISTER/DISCOVER
-> LOAD
-> START/ACTIVATE
-> USE
-> STOP/DEACTIVATE
-> RELEASE/UNLOAD
```

Required behavior:

- load lazily;
- release resources when no longer needed;
- no unbounded listener/thread/context retention;
- health reporting;
- explicit failure state;
- one engine failure must not take down unrelated engines when isolation is possible;
- dependencies are capability/contract driven, not hidden runtime assumptions.

## 5. Project-data baseline

Project Store remains the editing source of truth.

The foundation must support:

- stable identities;
- schema versioning;
- transactional manual Save;
- revision consistency;
- generated/rebuildable indexes;
- dependency graph;
- broken-reference preservation and diagnostics;
- manifest/integrity verification for user-visible project storage;
- recovery of the last valid committed state.

Cache, preview, generated indexes, and runtime materialization must never replace Project Store as source of truth.

## 6. Validation/build baseline

Before handoff to GitHub, ToolBox must have a local Build Contract Validator capable of blocking invalid projects.

At minimum the final local gate must cover:

- screens;
- required bindings;
- mandatory broken references;
- used assets;
- permission requirements;
- logic/actions;
- navigation;
- schema/version compatibility;
- implementation availability;
- build configuration;
- package identity;
- Android/ABI target;
- canonical build-model viability.

Only a project satisfying all mandatory requirements may receive:

```text
READY TO BUILD
```

GitHub is not the first-line detector for basic project-contract errors.

## 7. Change/apply baseline

Any operation that can alter a committed project/app baseline must have the architectural path for:

```text
INPUT
-> STAGING
-> INTEGRITY VALIDATION
-> COMPATIBILITY VALIDATION
-> DRY-RUN/PREVIEW WHEN APPLICABLE
-> TRANSACTION/JOURNAL
-> APPLY
-> HEALTH CHECK
-> COMMIT OR ROLLBACK
```

No partial/mixed committed revision is acceptable.

## 8. Recovery baseline

The foundation must preserve extension points for:

- known-good state;
- checkpoint/snapshot;
- A/B or previous-valid recovery where applicable;
- transaction journal;
- crash bootstrap/recovery;
- quarantine of invalid inputs;
- safe-mode inspection and restoration.

Safe/recovery behavior must not depend on the engine that is currently failing.

## 9. Security baseline

- User-visible project files are untrusted input until validated.
- Secrets are not stored in visible project data.
- Permission requirements are derived from declared capability use.
- External packages do not gain arbitrary code execution rights.
- Executable primitives, new Android components, new native engines, or trust-root changes require a trusted APK/build update path.

The exact external package rules are frozen separately in `EXTERNAL_PACKAGE_BOUNDARY_V1.md`.

## 10. Compatibility rule

The baseline contract is additive-first.

A future extension may add new optional metadata or contracts without breaking v1 consumers. Breaking changes require an explicit new contract version and compatibility/adaptation strategy.

## 11. Definition of architectural compliance

A change complies with Baseline Capability Contract v1 only if it:

1. does not introduce a direct dependency that bypasses the declared extension points;
2. does not require arbitrary external executable loading;
3. preserves Android 11 / API 30 / `arm64-v8a` product compatibility;
4. preserves failure isolation and lifecycle cleanup requirements;
5. preserves local validation before build handoff;
6. preserves transactional/recoverable mutation paths;
7. does not turn a derived subsystem into a new source of truth;
8. does not create repeated manual configuration where the system can safely persist it.
