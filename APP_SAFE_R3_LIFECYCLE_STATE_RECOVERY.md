# APP_SAFE_R3_LIFECYCLE_STATE_RECOVERY.md

## 1. Status

Korpus metode aktif untuk **R3 — Android Lifecycle, Component, State & Recovery Safety** dalam framework `APPLICATION_SAFE_100`.

Status riset: `PRACTICAL_SATURATION` terhadap ruang metode yang telah disapu.

---

## 2. Scope

R3 menutup:

- Activity/Fragment/View/Compose host lifecycle;
- process death dan recreation;
- transient state save/restore;
- navigation/Intent/task semantics;
- Service/Receiver/background component lifecycle;
- Binder/IPC liveness dan remote death;
- startup/bootstrap/init ordering;
- component state machine;
- crash recovery, rollback, safe mode, replay/restart;
- repeated lifecycle transitions.

Persistent schema/data correctness dimiliki R4. Resource/asset correctness tetap mengikuti `ASSET_SAFE_100`.

---

## 3. Metode Aktif

### R3-M01 — Lifecycle & Component Universe Closure
Inventaris seluruh component, lifecycle owner, entry point, task/navigation destination, receiver, service, worker, provider, IPC endpoint, startup initializer, dan recovery entry.

### R3-M02 — Explicit State-Machine Model
Untuk setiap component/stateful subsystem bentuk state machine dengan legal transition, forbidden transition, entry/exit effect, reentrancy, terminal state, and recovery transition. Reachable state yang tidak mempunyai expected behavior = `NOT_PROVEN`.

### R3-M03 — Lifecycle Ownership & Resource Binding
Listener, observer, coroutine/job, registration, callback, stream, binder link, and UI reference harus terikat pada lifecycle owner yang benar dan tidak hidup melewati owner tanpa contract eksplisit.

### R3-M04 — State Classification
Pisahkan minimal:

```text
DERIVED_STATE
TRANSIENT_UI_STATE
SESSION_STATE
PERSISTENT_DURABLE_STATE
RECOVERABLE_OPERATION_STATE
EXTERNAL_SOURCE_OF_TRUTH
```

Setiap kategori wajib mempunyai source of truth dan restore strategy yang benar.

### R3-M05 — Save/Restore Round-Trip Proof
Untuk state yang harus survive recreation/process death, verifikasi encode/save -> destroy -> recreate -> restore -> semantic equivalence. Nilai yang tidak seharusnya dipersist tidak boleh ikut menghidupkan stale object/reference.

### R3-M06 — Configuration-Recreation Exhaustive Witnesses
Exercise lifecycle melalui orientation, locale, night/day, window/multi-window, size/configuration change yang didukung. Repeated recreation wajib tidak menduplikasi side effect atau kehilangan state.

### R3-M07 — Process-Death Injection
Kill process pada setiap critical resumable state, lalu restart melalui launcher, recent task, pending intent/deep link, atau entry yang relevan. Restore harus berasal dari durable/saved source, bukan accidental in-memory survival.

### R3-M08 — Navigation / Intent Contract Closure
Setiap route/deep link/Intent memiliki input schema, required extras, optional/default semantics, destination, back-stack expectation, idempotency, authorization boundary bila eksternal, dan invalid-input behavior.

### R3-M09 — IPC/Binder Contract & Remote-Death Handling
Untuk remote binder: schema/version, payload size budget, timeout/blocking expectation, `RemoteException`, liveness race, `DeathRecipient` bila diperlukan, reconnection/rebind semantics, duplicate request, dan partial response wajib ditutup.

### R3-M10 — Service / Receiver / Background Execution Model
Setiap background action harus mempunyai allowed platform state, start condition, deadline, reschedule/retry rule, foreground requirement bila berlaku, process-kill behavior, and completion persistence. Background restriction tidak boleh diasumsikan tidak terjadi.

### R3-M11 — Startup Dependency Graph
Bangun DAG initializer/bootstrap dengan prerequisites, side effects, retryability, critical/noncritical classification, timeout, and fallback. Hidden ordering melalui class initialization/global singleton dilarang bila menjadi correctness dependency.

### R3-M12 — Idempotent / Reentrant Initialization
Startup dan restore path wajib aman terhadap repeated call, duplicate callback, partial previous initialization, process restart, dan recovery continuation.

### R3-M13 — Atomic State Transition / Journaled Operation
Critical multi-step state transition harus atomic atau mempunyai journal/checkpoint sehingga crash pada setiap boundary dapat dibedakan antara not-started, committed, atau recoverable in-progress.

### R3-M14 — Crash-Point / Transition Fault Injection
Inject termination/exception pada setiap state transition dan side-effect boundary. Setelah restart, invariant harus tetap benar dan tidak terjadi duplicate destructive action.

### R3-M15 — Deterministic Recovery / Replay Semantics
Recovery wajib mempunyai source of truth, ordering, deduplication/idempotency key bila perlu, termination condition, and observable success/failure state.

### R3-M16 — Checkpoint / Rollback / Safe-Mode Verification
Jika sistem mempunyai snapshot/rollback/recovery mode, verifikasi creation, integrity, selection, atomic switch, interrupted recovery, previous-valid fallback, and failed-safe terminal state.

### R3-M17 — Lifecycle Model-Based Testing
Generate transition sequences dari model termasuk rapid transitions, background/foreground loops, rotation loops, permission/system interruption, process death, navigation back/forward, dan duplicate event.

### R3-M18 — Temporal Property / Model Checking
Untuk state machine kritis, buktikan safety dan liveness: forbidden state tidak reachable, required cleanup akhirnya terjadi, recovery tidak loop selamanya, dan operation tidak committed dua kali.

### R3-M19 — Change-Impact Invalidation
Perubahan lifecycle owner, navigation graph, manifest component, saved-state schema, IPC interface, initializer, startup ordering, recovery mechanism, atau platform behavior wajib invalidasi proof R3 yang terkait.

### R3-M20 — Mutation Adequacy
Verifier harus mendeteksi minimal mutation: state tidak disimpan, duplicate side effect, missing unregister, wrong navigation input, binder death tidak ditangani, initializer order dibalik, crash di tengah commit, recovery loop, rollback ke invalid base.

---

## 4. Fault Model Minimum

```text
ILLEGAL_LIFECYCLE_TRANSITION
STALE_LIFECYCLE_REFERENCE
MISSING_STATE_SAVE
INCORRECT_STATE_RESTORE
PROCESS_DEATH_LOSS
DUPLICATE_SIDE_EFFECT_AFTER_RECREATE
NAVIGATION_CONTRACT_ERROR
INTENT_INPUT_ERROR
BACK_STACK_ERROR
IPC_REMOTE_DEATH_ERROR
IPC_PAYLOAD_LIMIT_ERROR
SERVICE_RESTART_ERROR
BACKGROUND_RESTRICTION_ERROR
STARTUP_ORDER_ERROR
PARTIAL_INITIALIZATION
NON_IDEMPOTENT_BOOTSTRAP
PARTIAL_STATE_COMMIT
RECOVERY_LOOP
ROLLBACK_TO_INVALID_STATE
DUPLICATE_REPLAY
MISSING_CLEANUP
```

---

## 5. PASS Formula

`APP_SAFE_R3_PASS` hanya jika:

```text
COMPONENT_UNKNOWN = 0
REACHABLE_STATE_WITHOUT_CONTRACT = 0
ILLEGAL_TRANSITION_UNRESOLVED = 0
REQUIRED_STATE_RESTORE_UNPROVEN = 0
PROCESS_DEATH_WITNESS_MISSING = 0
IPC_DEATH_PATH_UNPROVEN = 0
STARTUP_DEPENDENCY_UNKNOWN = 0
RECOVERY_TERMINAL_STATE_UNKNOWN = 0
LIFECYCLE_FAULT_ESCAPE = 0
STALE_EVIDENCE = 0
```
