# public.runtime-contracts — R1–R9 Public Assurance Matrix

## 1. Scope lock

This file applies `APPLICATION_SAFE_100_PROCESS.md` to the **Public component scope only**.

```text
PROJECT_ID = ToolBox
COMPONENT_ID = public.runtime-contracts
COMPONENT_VERSION = 0.1.0
CONTRACT_ID = toolbox.runtime.metadata
CONTRACT_VERSION = 1.0.0
PUBLIC_SCOPE = metadata contract + in-memory metadata registry + simulator/test/package
PRIVATE_CONTENT_INCLUDED = 0
PRIVATE_EXECUTION_THROUGH_PUBLIC = 0
PUBLIC_FINAL_APPLICATION_BUILD = 0
PUBLIC_FINAL_SIGNING = 0
PUBLIC_FIREBASE_PRIVATE_ARTIFACT = 0
```

This component is a pure Java 11 metadata library. It does not contain Android lifecycle code, persistent storage, network I/O, native/JNI code, dynamic code loading, UI, hardware control, signing authority, Firebase authority, or final application execution.

A method may be marked `N/A` only where the contract/source boundary proves that the fault domain does not exist in this Public component. `N/A` does **not** transfer a final Private claim to Public.

## 2. Status vocabulary

```text
APPLICABLE = method must have objective evidence before READY_PRIVATE
N/A_SCOPE_PROVEN = method does not apply to this component and the absence is contract/source-verifiable
NOT_PROVEN = gate closed
PASS = required Public-scope evidence exists and is bound to the current revision
```

No status in this file means `FINAL_APPLICATION_SAFE_100`.

---

## 3. R1 — Logic / Input

### Applicable methods

`R1-M01, M02, M03, M04, M11, M12, M14, M15, M16, M17, M18, M19, M21, M22, M25, M26`

Evidence obligations:

- requirement/contract is explicit in `CONTRACT.md`;
- requirement -> implementation -> test -> observable marker trace exists;
- Stable ID/version/reference/collection/bundle/resource bounds are executable contracts;
- invalid, duplicate, missing-reference, wrong-provider, declaration-mismatch and dependency failures fail closed;
- cross-domain duplicate IDs are rejected before commit;
- property/generative tests use an independently implemented Stable ID oracle;
- metamorphic relations challenge trimming, valid extension and invalid uppercase identity;
- exception/failure codes are asserted;
- publication is all-or-nothing for contract failures;
- Unicode/non-ASCII Stable IDs are explicitly rejected by the ASCII identity contract;
- meaningful R1 faults have negative tests;
- any source/contract/test change reruns the workflow and binds evidence to `GITHUB_SHA`.

### N/A scope proven

- `R1-M05, M06, M07, M08, M09`: no critical algorithm/property in this component requires formal specification, theorem proving, model checking, abstract interpretation or symbolic path solving for the Public claim; executable contract plus independent reference/generative challenge is the selected proof allocation.
- `R1-M10`: the complete input string universe is bounded but too large for meaningful exhaustive enumeration; boundary + independent generative/differential checking is used instead.
- `R1-M13`: there is no configuration-factor matrix whose correctness depends on t-way combinations.
- `R1-M20`: no business arithmetic/floating-point/date numeric semantics; bounded integer sizes are constrained far below overflow.
- `R1-M23`: no synthesis/refinement generator is used.
- `R1-M24`: invariant mining is discovery-only and is not required for the current closed contract.

R1 Public target:

```text
REQUIRED_BEHAVIOR_UNKNOWN = 0
UNTRACED_BEHAVIOR = 0
UNPROVEN_CRITICAL_DECISION = 0
UNHANDLED_REQUIRED_EXCEPTION_PATH = 0
UNCLOSED_REQUIRED_INPUT_DOMAIN = 0
NUMERIC_SEMANTIC_UNKNOWN = 0
TIME_LOCALE_UNICODE_UNKNOWN = 0
FAULT_ESCAPE = 0
STALE_EVIDENCE = 0
```

---

## 4. R2 — Concurrency / Resource

### Applicable methods

`R2-M01, M02, M03, M04, M05, M06, M07, M08, M13, M16, M18, M19, M20`

Evidence obligations:

- shared mutable universe is only the registry maps guarded by one monitor;
- published contract objects and snapshots are immutable/defensively copied;
- all reads/writes/snapshots use the same monitor, providing the required happens-before edge;
- no nested lock graph exists; lock order is therefore acyclic by construction;
- publication has one serialized linearization region;
- contention tests cover independent publications and a same-ID race where exactly one publication commits;
- `MAX_STABLE_ID_LENGTH`, `MAX_COLLECTION_SIZE`, `MAX_BUNDLE_ENTRIES`, and `MAX_REGISTRY_ENTRIES` bound supported memory growth from metadata inputs;
- registry-capacity fault injection must fail before mutation;
- repeated concurrent tests are supporting stress evidence;
- source changes invalidate/rerun evidence.

### N/A scope proven

- `R2-M09, M10, M11`: no Android main/UI thread, StrictMode boundary, startup or user-facing latency contract exists in this component.
- `R2-M12`: no queue/executor/producer-consumer path exists.
- `R2-M14`: no file/socket/native handle/cursor/stream or other explicit acquired resource exists; heap objects are ordinary JVM-owned immutable/registry objects.
- `R2-M15`: the component does not claim low-memory survival under arbitrary VM pressure; supported metadata growth is instead bounded and saturation-tested.
- `R2-M17`: no async task/cancellation/timeout tree exists.

R2 Public target:

```text
SHARED_STATE_UNKNOWN = 0
UNPROVEN_HAPPENS_BEFORE_EDGE = 0
UNRESOLVED_LOCK_CYCLE = 0
PROGRESS_UNKNOWN = 0
MAIN_THREAD_UNBOUNDED_WORK = 0
UNBOUNDED_REQUIRED_QUEUE = 0
RESOURCE_OWNER_UNKNOWN = 0
SUPPORTED_WORKLOAD_BUDGET_BREACH = 0
LEAK_ESCAPE = 0
CONCURRENCY_FAULT_ESCAPE = 0
STALE_EVIDENCE = 0
```

---

## 5. R3 — Lifecycle / State / Recovery

### N/A scope proven

`R3-M01..M20 = N/A_SCOPE_PROVEN`

Reason: this Public component has no Android Activity/Fragment/View/Compose host, Service, Receiver, Worker, Provider, Binder/IPC endpoint, navigation graph, process-death restore state, startup initializer graph, recovery journal, checkpoint, rollback, or Safe UI. The in-memory registry publication transaction is owned by R1/R2. Android lifecycle/recovery integration belongs to the Private host after promotion.

Public closure:

```text
COMPONENT_UNKNOWN = 0
REACHABLE_ANDROID_LIFECYCLE_STATE = 0
REQUIRED_STATE_RESTORE_IN_PUBLIC_COMPONENT = 0
IPC_ENDPOINT_IN_PUBLIC_COMPONENT = 0
RECOVERY_ENGINE_IN_PUBLIC_COMPONENT = 0
```

---

## 6. R4 — Persistence / Storage / Versioned State

### N/A scope proven

`R4-M01..M20 = N/A_SCOPE_PROVEN`

Reason: the component performs no database access, file persistence, DataStore/preferences, serialization to durable state, migration, WAL/journal handling, backup/restore, cache persistence, or storage recovery. Package/JAR creation belongs to R6, not runtime persistence.

Public closure:

```text
PERSISTENT_STORE_IN_PUBLIC_COMPONENT = 0
MIGRATION_PATH_IN_PUBLIC_COMPONENT = 0
DURABLE_SERIALIZATION_IN_PUBLIC_COMPONENT = 0
BACKUP_RESTORE_IN_PUBLIC_COMPONENT = 0
```

---

## 7. R5 — Security / Network / External Boundary

### Applicable methods

`R5-M01, M02, M03, M05, M17, M18, M20`

Evidence obligations:

- the only runtime trust boundary is caller-supplied metadata;
- malformed/oversized/duplicate/cross-domain-collision/reference-confusion inputs are hostile cases;
- permission records are metadata only and cannot grant Android permission;
- Stable IDs, versions, references, collection sizes and bundle sizes validate before publication;
- source/package policy scans forbid loaders, process execution, network/filesystem authority, Firebase token paths and signing authority;
- hostile input tests exercise fail-closed behavior;
- changes rerun the security boundary scan and negative tests.

### N/A scope proven

- `R5-M04`: no authentication/authorization service or protected remote resource.
- `R5-M06`: no SQL/HTML/JS/shell/path/dynamic-loader interpreter sink.
- `R5-M07..M15`: no network/TLS/endpoint/retry/offline/remote schema/token/replay boundary.
- `R5-M16`: no application-owned secret/credential.
- `R5-M19`: external runtime dependencies/SDKs = 0.

Public target:

```text
TRUST_BOUNDARY_UNKNOWN = 0
UNJUSTIFIED_PERMISSION = 0
UNVALIDATED_UNTRUSTED_INPUT = 0
AUTHORIZATION_PATH_UNKNOWN = 0
INSECURE_REQUIRED_TRANSPORT = 0
REMOTE_FAILURE_POLICY_UNKNOWN = 0
NON_IDEMPOTENT_UNCERTAIN_RETRY = 0
OFFLINE_RECONCILIATION_UNKNOWN = 0
EXTERNAL_CONTRACT_UNKNOWN = 0
SECURITY_NETWORK_FAULT_ESCAPE = 0
STALE_EVIDENCE = 0
```

---

## 8. R6 — Build / Dependency / Package

### Applicable methods for the Public component package

`R6-M01, M02, M03, M04, M05, M06, M07, M08, M09, M12, M16, M20, M21, M22, M23`

Evidence obligations:

- build inputs are source, contract, test, scripts and pinned CI/toolchain configuration;
- GitHub actions are SHA-pinned; JDK and Python versions are explicit;
- Java compilation targets `--release 11` and package validation rejects class major >55;
- external runtime dependency count is zero and dependency digest is recorded;
- clean build removes prior output;
- JAR is produced twice with deterministic ZIP timestamp and must compare byte-for-byte;
- source snapshot uses deterministic ZIP metadata and per-file SHA-256;
- provenance binds repository, commit SHA, workflow run, artifact/source hashes and toolchain;
- `javac -Xlint:all` is mandatory;
- JAR namespace/classes/bytecode and forbidden boundary tokens are structurally validated;
- package mutation tests must detect missing required class, foreign namespace and invalid bytecode level;
- workflow has no `continue-on-error` bypass on mandatory gates;
- changes to build/source/workflow rerun evidence.

### N/A scope proven

- `R6-M10`: no product flavor/variant matrix for this standalone component.
- `R6-M11`: no Android manifest/APK is built in Public.
- `R6-M13, M14`: no shrinker/obfuscation/reflection keep-rule stage.
- `R6-M15`: no obfuscation mapping/retrace output.
- `R6-M17`: Public component Promotion Package is not a final signed APK and has no signing authority.
- `R6-M18`: no APK install/upgrade path in Public component scope.
- `R6-M19`: no self-update/package install transaction in this component.

`R6_PUBLIC_COMPONENT_PASS` is a component/package development status only. It is not final APK/sign/install proof.

---

## 9. R7 — Native / Plugin / Runtime

### N/A scope proven

`R7-M01..M23 = N/A_SCOPE_PROVEN`

Reason: native libraries/JNI = 0, plugins/engines executable = 0, dynamic code loading = 0, reflection execution = 0, third-party runtime libraries = 0. `RuntimeContractsSimulator` executes metadata publication only and prints `ENGINE_CALLBACKS_EXECUTED=0`.

Public closure:

```text
NATIVE_COMPONENT_UNKNOWN = 0
NATIVE_COMPONENT_COUNT = 0
PLUGIN_EXECUTABLE_COUNT = 0
DYNAMIC_CODE_PATH_COUNT = 0
THIRD_PARTY_RUNTIME_DEPENDENCY_COUNT = 0
```

---

## 10. R8 — UI / Device / Power

### N/A scope proven

`R8-M01..M22 = N/A_SCOPE_PROVEN`

Reason: no Android UI surface, rendering, WebView, hardware API, vendor-specific behavior, background scheduling, wake lock, sensor, camera, Bluetooth/NFC, or power policy exists in this Java metadata component. Android/device proof is owned by the Private integrated product and cannot be claimed by Public.

Public closure:

```text
UI_SURFACE_COUNT = 0
WEBVIEW_COUNT = 0
HARDWARE_API_COUNT = 0
BACKGROUND_POWER_PRIMITIVE_COUNT = 0
```

---

## 11. R9 — Public verification completeness

### Applicable methods

`R9-M01, M02, M03, M04, M05, M06, M07, M08, M09, M10, M11, M12, M13, M14, M15, M16, M17, M18, M19, M20, M21, M22, M24`

### N/A scope proven

- `R9-M23`: production observability is not a Public pre-promotion proof for this standalone component; future integrated production telemetry belongs to the Private product.

### Required R9 closures

1. Closed fault universe includes applicable R1/R2/R5/R6 faults.
2. Every applicable method maps to at least one objective evidence ID.
3. Domains R3/R4/R7/R8 are explicitly N/A by source/contract absence, not skipped.
4. Cross-domain interaction challenges include:
   - R1 + R2: duplicate/atomic publication under concurrency;
   - R1 + R5: malformed/Unicode/oversized/collision metadata;
   - R2 + R5: hostile resource-volume input versus registry/bundle limits;
   - R5 + R6: forbidden executable/network/Firebase/signing tokens in promotable source;
   - R1/R2/R5 + R6: current-revision evidence bound to the exact Promotion Package.
5. Oracle diversity includes Java runtime assertions, independent Java reference parser, Python static/package validation, shell CI assertions and cryptographic digests.
6. Mutation/fault challenge covers relevant contract and package failure classes.
7. Evidence is current and bound to `GITHUB_SHA`/workflow run.
8. `UNKNOWN`, `SKIPPED`, `NOT_RUN`, `ASSUMED`, `NOT_PROVEN`, stale evidence and unresolved defeaters are zero before `READY_PRIVATE`.

R9 Public target:

```text
UNOWNED_APPLICABLE_METHOD = 0
UNMAPPED_APPLICABLE_METHOD = 0
UNKNOWN = 0
SKIPPED = 0
NOT_PROVEN = 0
STALE_EVIDENCE = 0
FAULT_ESCAPE = 0
UNRESOLVED_DEFEATER = 0
UNDECLARED_MATERIAL_ASSUMPTION = 0
```

---

## 12. Active Public fault universe

Applicable fault classes for this component include:

```text
INVALID_INPUT_ACCEPTED
VALID_INPUT_REJECTED
BOUNDARY_ERROR
PARSER_SEMANTIC_ERROR
UNHANDLED_EXCEPTION
ILLEGAL_STATE
REQUIREMENT_IMPLEMENTATION_MISMATCH
REGRESSION_AFTER_CHANGE
DATA_RACE
VISIBILITY_ORDERING_ERROR
LOST_UPDATE
NON_ATOMIC_COMPOUND_OPERATION
RESOURCE_LIMIT_BYPASS
RESOURCE_EXHAUSTION_UNBOUNDED_METADATA
UNTRUSTED_INPUT_ACCEPTED
EXCESS_PRIVILEGE_METADATA_LIE
EXECUTABLE_BOUNDARY_ESCAPE
DEPENDENCY_VERSION_DRIFT
DEPENDENCY_INTEGRITY_MISMATCH
TOOLCHAIN_DRIFT
NON_HERMETIC_OUTPUT_DRIFT
STALE_CACHE_GENERATED_OUTPUT
PACKAGE_NAMESPACE_ESCAPE
BYTECODE_TARGET_MISMATCH
CI_GATE_BYPASS
ARTIFACT_SOURCE_PROVENANCE_MISMATCH
```

Faults owned entirely by absent domains (database migration, Android lifecycle, JNI, WebView, hardware, APK signing/install, etc.) are not silently removed; their domain is explicitly `N/A_SCOPE_PROVEN` for this Public component and remains the responsibility of the integrated Private product if/when introduced.

---

## 13. Residual assumptions / trusted base

Declared assumptions:

- Java language/JVM monitor semantics behave according to the selected Temurin JDK implementation/specification.
- Fatal VM/process failures such as host OOM during a map `put` are outside the atomic-publication guarantee of this small in-process metadata registry; supported metadata volume is bounded before publication.
- GitHub-hosted runner and pinned GitHub Action implementations are trusted CI infrastructure for Public development evidence.
- `RMTampu/Tools` contains no Private input; Public does not fetch Private state.
- Java 11 bytecode compatibility is structural Public evidence; Android 11/API30 integrated runtime behavior remains a Private responsibility.

These assumptions limit the Public claim and are not hidden as final product proof.

---

## 14. Promotion invariant

The Promotion Package may declare `READY_PRIVATE` only when the current workflow proves:

```text
PUBLIC_BOUNDARY = PASS
R1_R8_PREBUILD_SCOPE_CLASSIFICATION = PASS
PUBLIC_BUILD_TEST = PASS
FAILURE_BOUNDARY_PROPERTY_CONCURRENCY_TESTS = PASS
R6_PACKAGE_MUTATION = PASS
R9_PUBLIC_EVIDENCE_COMPLETENESS = PASS
PACKAGE_VALIDATION = PASS
READY_PRIVATE_BINDING = PASS
PUBLIC_JOB_AUTO_CLEANUP = REQUIRED
```

If any required status is absent, unknown, skipped, stale, or fails, `READY_PRIVATE` must not be emitted.
