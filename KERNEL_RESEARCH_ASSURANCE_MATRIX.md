# ToolBox Kernel Research Assurance Matrix

## 1. Purpose

This file closes the research-to-proof traceability for the **basic `toolbox-kernel` JVM core only**.

It is derived from:

- `APP_SAFE_R1_LOGIC_INPUT.md`
- `APP_SAFE_R2_CONCURRENCY_RESOURCE.md`
- `APP_SAFE_R3_LIFECYCLE_STATE_RECOVERY.md`
- `APP_SAFE_R4_PERSISTENCE_STORAGE_VERSION.md`
- `APP_SAFE_R5_SECURITY_NETWORK_BOUNDARY.md`
- `APP_SAFE_R6_BUILD_DEPENDENCY_INSTALL.md`
- `APP_SAFE_R7_NATIVE_PLUGIN_RUNTIME.md`
- `APP_SAFE_R8_UI_DEVICE_POWER.md`
- `APP_SAFE_R9_VERIFICATION_COMPLETENESS.md`
- `AGENTS.md`
- `AGENT_PROCEDURE_EXECUTION_RULES.md`
- `TEST_ROUTING_POLICY.md`

This document does **not** declare `APPLICATION_SAFE_100` for the whole ToolBox application. It defines a closed development-assurance claim for the basic JVM kernel layer and assigns every non-kernel concern to an explicit boundary instead of silently counting it as PASS.

---

## 2. Closed Claim

The claim evaluated by this matrix is:

> The basic ToolBox JVM kernel enforces its declared configuration, module admission, dependency, lifecycle, registry ownership, rollback, failure-isolation, event/command routing, and kernel-state persistence-boundary semantics consistently for the tested kernel contract, without requiring UI, Android component, APK, native, network, database, or product-feature behavior.

The claim is valid only when the evidence-binding conditions in section 9 are satisfied for the exact candidate revision.

---

## 3. Explicit Scope Boundaries / Residual Assumptions

### A1 — Trusted in-APK callback execution

`ToolBoxModule` lifecycle callbacks, command handlers, and event listeners are in-process callbacks. The basic kernel provides exception isolation and lifecycle serialization but **does not claim preemptive timeout/sandbox containment for a callback that never returns**.

The synchronous callback contract is therefore part of the basic-kernel trust boundary. Hard timeout, process sandboxing, workload budgets, or engine-runtime scheduling belong to a higher runtime/Engine Host layer if required.

This prevents an unsupported R2/R7 hang-isolation claim.

### A2 — `KernelStateStore` durability belongs to the adapter

The kernel owns:

- state classification;
- read/write failure semantics;
- fail-closed recovery from invalid/unclean persisted kernel state;
- authoritative use of `kernel.state`.

Actual disk atomicity, fsync, corruption recovery, schema migration, backup/restore, or storage-full behavior belongs to the production `KernelStateStore` implementation. The included `InMemoryKernelStateStore` is a deterministic test adapter, not proof of physical durable storage.

### A3 — Android 11 / ARM64 is a compatibility contract at this layer

`toolbox-kernel` is a JVM module and contains no Activity, Service, View, WebView, JNI, `.so`, APK manifest, or device runtime code. API 30 / `arm64-v8a` are represented by kernel/module compatibility metadata and policy.

No Android UI/device/native runtime claim is made by this matrix.

### A4 — Explicit source admission policy owns provenance decisions

The safe default rejects `ModuleSource` before loader execution. A host that explicitly supplies another `ModuleAdmissionPolicy` owns the provenance/integrity decision for sources that it chooses to admit. The kernel still requires compatibility and admitted-descriptor equality before registration.

---

## 4. R1–R9 Domain Ownership

| Domain | Kernel applicability | Kernel-scope disposition |
|---|---|---|
| R1 Logic / Exception / Input | Applicable | Contracts, invalid input, descriptor stability, dependency decisions, exception mapping, and reference-model behavior are challenged by unit/negative/model tests. |
| R2 Concurrency / Resource | Applicable in part | Operation ownership, per-module lifecycle serialization, concurrent registry mutation, EventBus races, bounded registry ownership, cleanup/release are covered. UI-thread/ANR and preemptive callback timeout are outside the basic JVM claim under A1. |
| R3 Lifecycle / State / Recovery | Applicable in part | Kernel/module state machines, repeated start/stop, failure transitions, pending cleanup, and persisted-state restart recovery are covered. Android Activity/Service/IPC/navigation lifecycle is not present in this module. |
| R4 Persistence / Storage | Applicable only at the state-store boundary | Kernel authoritative-state and failure/recovery semantics are covered. Physical durable-store correctness is assigned to the production adapter under A2. |
| R5 Security / External Boundary | Applicable in part | `ModuleSource` trust/admission, loader-before-trust prevention, authority minimization, capability ownership, and foreign-registry takeover prevention are covered. Network/auth/permissions are not present. |
| R6 Build / Dependency | Applicable in part | Exact JDK/Gradle identity, dependency lock/trust, strict dependency verification, and source gate are GitHub-gated. APK/signing/install/manifest concerns belong to the Android application artifact. |
| R7 Plugin / Runtime | Applicable to kernel module contract | Compatibility-before-load, admitted descriptor matching, lifecycle failure isolation, unload/reload semantics, and default denial of source loading are covered. Native/JNI/classloader claims are not made. |
| R8 UI / Device / Power | Not applicable | `toolbox-kernel` has no UI, WebView, hardware, Android background component, renderer, or power-management implementation. |
| R9 Verification Completeness | Applicable | This matrix, retained adversarial tests, independent reference model, exact-revision CI evidence, defect ledger, and explicit assumptions provide the kernel-scope assurance graph. |

---

## 5. Requirement → Fault → Method → Test / Evidence → Claim

| ID | Requirement / risk | Fault classes challenged | Method / prevention | Primary verifier/evidence | Kernel claim |
|---|---|---|---|---|---|
| KREQ-01 | Kernel config and module contracts reject invalid values | INVALID_INPUT_ACCEPTED, ILLEGAL_STATE | Executable constructor contracts | `ToolBoxKernelTest.core model rejects invalid configuration and descriptors`; deep-audit identifier tests | Invalid basic configuration/descriptor contracts fail closed. |
| KREQ-02 | Validated descriptor/source data cannot mutate after validation | REQUIREMENT_IMPLEMENTATION_MISMATCH, REGRESSION_AFTER_CHANGE | Defensive snapshots + descriptor re-read/equality | `KernelDeepAuditProbeTest.validated descriptor collections...`; `KernelFourthAuditTest.module source metadata...`; descriptor TOCTOU test | Admission/compatibility decisions cannot be silently invalidated by mutable caller collections or descriptor drift. |
| KREQ-03 | Dependencies form a valid load/start order | MISSING_CASE, PROTOCOL_SEQUENCE_ERROR, STARTUP_ORDER_ERROR | Dependency DAG resolution | dependency-order, missing-dependency, cycle tests | Missing/cyclic dependencies fail before lifecycle activation; valid dependencies activate first. |
| KREQ-04 | Public kernel/module lifecycle follows a closed state model | ILLEGAL_STATE, PROTOCOL_SEQUENCE_ERROR, ILLEGAL_LIFECYCLE_TRANSITION | Independent executable reference model + bounded exhaustive enumeration | `KernelReferenceModelTest` enumerates 4^5 = 1,024 complete sequences and checks all 5,120 prefixes | Healthy public lifecycle semantics agree with an independently defined model within the stated bound. |
| KREQ-05 | Failure states and recovery transitions are deterministic | UNHANDLED_EXCEPTION, PARTIAL_INITIALIZATION, RECOVERY_LOOP | Failure transition table + fault injection | `KernelReferenceModelTest` failure/recovery table; startup failure tests | Start failure degrades, stop failure fails closed, and unclean persisted state enters explicit recoverable failure. |
| KREQ-06 | Kernel-wide mutating operations cannot overlap ambiguously | NON_ATOMIC_COMPOUND_OPERATION, DEADLOCK, LOST_UPDATE | Atomic operation claim / fail-fast serialization | research callback-under-global-monitor probe plus operation-gate regression suite | Kernel operation ownership is explicit without executing module callbacks under a global kernel monitor. |
| KREQ-07 | One module lifecycle callback phase cannot execute concurrently twice | DATA_RACE, DOUBLE_COMPLETION, PROTOCOL_SEQUENCE_ERROR | Per-record lifecycle claim + lifecycle lock | concurrent load/start and start-vs-stop audit tests | Duplicate lifecycle entry is rejected/serialized and destructive cleanup does not overlap active lifecycle callbacks. |
| KREQ-08 | Event subscribe/unsubscribe is race-safe and bounded | DATA_RACE, LOST_UPDATE, MEMORY_LEAK | Concurrent map compute + empty-bucket cleanup | EventBus race probe; research 2,000-topic close test | A racing subscription is not detached and closed one-shot topics do not leave unbounded empty buckets. |
| KREQ-09 | Registry resources have explicit module ownership and bounded tracking | RESOURCE_RELEASE_ERROR, MEMORY_LEAK, AUTHORIZATION_BYPASS | Owner-scoped Service/Capability/Command/Event registries; short-lived activation journal | uninstall ownership test; late-mutation ownership test; repeated same-key mutation boundedness test | Live registry entries are owned directly; rollback history is not used as an unbounded lifetime ownership log. |
| KREQ-10 | A module cannot replace or unregister another owner’s registry authority | AUTHORIZATION_BYPASS, EXCESS_PRIVILEGE | Owner check on scoped registry mutations | foreign command replace/unregister research tests; capability-provider spoof test | Module-scoped contexts cannot take over host/peer-owned command authority or spoof capability ownership. |
| KREQ-11 | Failed activation cannot publish partial registry state | PARTIAL_STATE_COMMIT, PARTIAL_INITIALIZATION | Activation mutation journal + compare-and-restore rollback | dynamic-install rollback; cross-registry rollback; startup load/start rollback tests | Failed activation removes only its still-owned side effects and preserves unrelated/concurrent replacements. |
| KREQ-12 | Successful module unload releases all owned registry state | RESOURCE_RELEASE_ERROR, STALE_LIFECYCLE_REFERENCE | Owner release on unload/uninstall | uninstall ownership, late registry mutation, successful uninstall tests | Module-owned services/capabilities/commands/listeners are not reachable after clean unload. |
| KREQ-13 | Failed cleanup remains visible and retryable | SILENT_ERROR_SWALLOW, MISSING_CLEANUP, PARTIAL_STATE_COMMIT | Fail-closed cleanup state + retry | fifth/sixth/seventh audit suites | Kernel does not report clean STOPPED/removal while required cleanup remains unresolved. |
| KREQ-14 | Healthy peer modules survive another module’s failure | PLUGIN_CRASH_PROPAGATION, PARTIAL_STATE_COMMIT | Per-module activation isolation | healthy-peer registration tests; failing-module isolation tests | One module failure does not erase healthy peer state or force healthy module failure when dependencies permit operation. |
| KREQ-15 | Persisted kernel state is authoritative and failure is fail-closed | SOURCE_OF_TRUTH_AMBIGUITY, PERSISTENCE_CRASH_INCONSISTENCY, CORRUPT_DATA_ACCEPTED | Single `kernel.state` authority + explicit recovery mapping | state-store read/write failure probes; clean/unclean persisted-state tests | Read/write failure or invalid/unclean state cannot be converted into a false clean RUNNING claim. |
| KREQ-16 | Module compatibility is checked before activation | PLUGIN_API_VERSION_MISMATCH, PLUGIN_METADATA_LIE | Compatibility policy before registry/lifecycle | incompatible module tests; descriptor stability test | API/Android/architecture incompatibility is rejected before activation. |
| KREQ-17 | `ModuleSource` does not execute before explicit trust admission | UNTRUSTED_INPUT_ACCEPTED, DYNAMIC_CODE_INTEGRITY_FAILURE | Default-deny source admission before loader | source-admission-before-loader; default source boundary research test | The default kernel does not execute a source loader without explicit host admission policy. |
| KREQ-18 | Loaded source identity must equal admitted identity | PLUGIN_METADATA_LIE, REQUIREMENT_IMPLEMENTATION_MISMATCH | Exact descriptor equality after loader | loaded-source descriptor mismatch test | A loader cannot substitute a different module contract after admission. |
| KREQ-19 | Module context does not expose kernel authority ports | AUTHORIZATION_BYPASS, SOURCE_OF_TRUTH_AMBIGUITY | Least-authority `KernelContext` | research reflection test for `KernelPorts` exposure | Modules receive scoped registries/config, not the authoritative state store or admission/compatibility policies. |
| KREQ-20 | Optional observability failure does not break core transitions | UNHANDLED_EXCEPTION, SILENT_ERROR_SWALLOW | Safe logger boundary; listener error isolation | logger-failure and event-listener-failure tests | Logger/listener exceptions are contained according to their contracts. |
| KREQ-21 | Stale failed-activation context cannot mutate kernel later | ILLEGAL_STATE, STALE_LIFECYCLE_REFERENCE | Journal terminal state | `KernelFourthAuditTest.rolled back activation context...` | A context belonging to rolled-back activation is terminal and rejects future registry mutation. |
| KREQ-22 | Successful activation context remains valid for module lifetime | PROTOCOL_SEQUENCE_ERROR | Committed scoped context + direct ownership | committed-context and late-ownership tests | A valid module may register later resources; those resources remain module-owned until unload. |
| KREQ-23 | Build/test dependency identities are fixed and checked | TOOLCHAIN_DRIFT, DEPENDENCY_VERSION_DRIFT, DEPENDENCY_INTEGRITY_MISMATCH | Pinned workflow SHAs/JDK/Gradle hash + lockfile + strict verification | `.github/workflows/kernel-ci.yml`; dependency trust gate; strict Gradle verification | Kernel development evidence uses an explicitly identified build/test toolchain and verified dependencies. |
| KREQ-24 | Evidence must bind to exact candidate revision | STALE_EVIDENCE, ARTIFACT_SOURCE_PROVENANCE_MISMATCH | PR-head run binding | section 9 evidence manifest rule | No earlier passing run is accepted after an affected source/test/document change. |

---

## 6. Retained Negative / Fault-Seeding Coverage

The adversarial audit tests are permanent regression tests rather than one-time probes.

Research failures that demonstrated real verifier sensitivity include:

- GitHub Kernel CI run `33547715256`: 57 tests / 4 research failures exposing EventBus bucket retention, uninstall ownership leakage, permissive default source admission, and `KernelPorts` authority exposure.
- GitHub Kernel CI run `33547991456`: 59 tests / 6 research failures, additionally proving global callback-under-monitor and capability-provider spoofing defects.
- GitHub Kernel CI run `33549511169`: 63 tests / 3 research failures proving lifetime ownership-history growth and foreign command replace/unregister defects.

A later verifier conflict in run `33549831296` was classified separately: the strengthened owner boundary correctly prohibited a legacy test from making a module replace a host-owned command. The test was corrected to preserve its intended concurrent rollback oracle without weakening the security boundary.

No failure above is converted to PASS by deletion of its adversarial test.

---

## 7. Independent Reference Challenge

`KernelReferenceModelTest` is intentionally separate from the implementation transition logic.

For the healthy public state machine it defines its own abstract states and operation semantics, then enumerates every operation sequence of length five:

```text
operations = { INSTALL, UNINSTALL, START, STOP }
complete sequences = 4^5 = 1,024
checked prefixes = 5,120
```

Each prefix compares:

- operation outcome;
- stable kernel state;
- stable module state.

A separate transition table challenges start failure, stop failure, and unclean persisted-state recovery.

The bound is explicit; this is not represented as an unbounded formal proof.

---

## 8. Tool / Environment Qualification for Kernel Development Evidence

The mandatory `ToolBox Kernel CI` workflow identifies and verifies:

- runner: `ubuntu-24.04`;
- exact Temurin JDK: `17.0.20+1`;
- Gradle: `8.7`;
- Gradle distribution SHA-256: `544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d`;
- pinned SHA for checkout/setup-java/setup-gradle actions;
- accepted dependency-trust state;
- Gradle `--dependency-verification strict`;
- `:toolbox-kernel:test`.

`ToolBox Application Prebuild Source Gate` is also required for the exact final PR HEAD.

These are JVM development proofs. They are not Android 11/ARM64 runtime evidence.

---

## 9. Evidence Provenance / Exact-Revision Binding

To avoid a self-referential document SHA, this file does not hard-code the SHA of the commit that contains itself.

The authoritative evidence manifest for acceptance is the GitHub pull-request metadata/body plus GitHub Actions run records. Final kernel research-development closure requires all of the following to refer to the **same exact PR HEAD**:

```text
PR_HEAD_SHA = candidate source revision
APPLICATION_PREBUILD_SOURCE_GATE(PR_HEAD_SHA) = PASS
KERNEL_CI(PR_HEAD_SHA) = PASS
DEPENDENCY_TRUST_GATE(PR_HEAD_SHA) = PASS
STRICT_DEPENDENCY_VERIFICATION(PR_HEAD_SHA) = PASS
KERNEL_TEST_TASK(PR_HEAD_SHA) = PASS
```

After any source, contract, test, workflow, dependency, or this assurance document changes, earlier affected PASS evidence is stale until the exact new HEAD is rerun.

The PR body must record the final candidate SHA and run IDs before merge.

---

## 10. Defeater / Common-Mode Review

Material defeaters explicitly handled:

1. **“Many green unit tests prove everything.”** Rejected. The claim also requires the reference model, negative probes, toolchain trust, scope ownership, and this traceability graph.
2. **“Module callbacks are sandboxed/time-bounded.”** Not claimed; A1 records the synchronous trusted-callback boundary.
3. **“In-memory state proves disk durability.”** Rejected; A2 assigns physical persistence proof to the production adapter.
4. **“JVM CI proves Android 11/ARM64 runtime.”** Rejected; A3 limits the claim.
5. **“Explicit admission policy automatically proves source integrity.”** Rejected; A4 assigns the host’s explicit provenance decision while keeping the kernel default fail-closed.
6. **“A stronger security boundary may be weakened to keep an old test green.”** Rejected; the stale legacy test was corrected instead.
7. **“Previous CI remains valid after new assurance/model changes.”** Rejected by the exact-HEAD evidence rule.

---

## 11. Kernel-Scope Acceptance Formula

The basic kernel research-development claim may be marked `PASS` only when:

```text
ALL_KERNEL_APPLICABLE_REQUIREMENTS_MAPPED = TRUE
KNOWN_KERNEL_RESEARCH_DEFECT_OPEN = 0
REFERENCE_MODEL_TEST = PASS
ADVERSARIAL_REGRESSION_TESTS = PASS
KERNEL_PREBUILD_SOURCE_GATE_EXACT_HEAD = PASS
KERNEL_CI_EXACT_HEAD = PASS
DEPENDENCY_TRUST_EXACT_HEAD = PASS
STRICT_DEPENDENCY_VERIFICATION_EXACT_HEAD = PASS
UNRESOLVED_KERNEL_SCOPE_DEFEATER = 0
UNDECLARED_KERNEL_SCOPE_ASSUMPTION = 0
STALE_KERNEL_EVIDENCE = 0
```

This result is named:

```text
KERNEL_RESEARCH_DEVELOPMENT_PASS
```

It is **not** `APP_SAFE_R1_PASS` through `APP_SAFE_R9_PASS` for the whole Android application, not `APPLICATION_SAFE_100`, and not `FIREBASE_TARGET_PASS`.

---

## 12. Firebase / Final Android Gate

This kernel-only JVM assurance activity does not authorize Firebase.

```text
FIREBASE_AUTHORIZATION = LOCKED
```

If a later Android application/runtime claim requires Android 11 / API 30 / ARM64 final evidence, that execution remains governed by `TEST_ROUTING_POLICY.md` and requires explicit single-use user approval for the exact candidate.
