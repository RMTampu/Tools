# APP_SAFE_R2_CONCURRENCY_RESOURCE.md

## 1. Status

Dokumen ini adalah korpus metode aktif untuk **R2 — Concurrency, Responsiveness & Resource Safety** dalam framework `APPLICATION_SAFE_100`.

Hanya metode yang menambah jaminan berbeda yang dipertahankan. Tool tertentu, benchmark tunggal, stress test acak, atau profiler tanpa invariant tidak menjadi PASS sendiri.

Status riset: `PRACTICAL_SATURATION` terhadap ruang metode yang telah disapu.

---

## 2. Scope

R2 menutup minimal:

- data race;
- race condition non-data;
- deadlock/livelock/starvation;
- incorrect synchronization/visibility;
- main-thread blocking dan ANR;
- unbounded queue/work accumulation;
- memory leak dan OOM;
- thread/file-descriptor/socket/handle exhaustion;
- CPU/I/O overload;
- cancellation/timeout leakage;
- resource ownership/release;
- responsiveness and latency contract.

---

## 3. Metode Aktif

### R2-M01 — Shared-State & Resource Universe Closure
Inventaris seluruh mutable shared state, thread/dispatcher, lock, queue, executor, coroutine/job, file descriptor, socket, database connection, stream, cursor, bitmap/buffer, native handle, dan resource lain yang dapat hidup bersamaan.

### R2-M02 — Ownership / Thread-Confinement / Immutability by Construction
Prioritaskan immutable data, single-owner state, actor/thread confinement, message passing, scoped resource ownership, dan structured lifetime agar shared mutable state dan leaked lifetime diperkecil sebelum testing.

### R2-M03 — Happens-Before / Memory-Visibility Proof
Untuk setiap cross-thread communication, buktikan synchronization edge yang memberi ordering/visibility yang diperlukan. Unsynchronized assumption atau visibility yang bergantung timing dilarang.

### R2-M04 — Race Detection: Static + Dynamic
Gunakan static race analysis bila feasible dan dynamic race detector/instrumented stress untuk melengkapi. Satu detector tidak dianggap lengkap bila scope shared state lebih luas dari kemampuan detector.

### R2-M05 — Systematic Schedule Exploration
Gunakan scheduler-controlled/systematic concurrency testing, bounded preemption exploration, partial-order reduction, atau model checking untuk mengeksplorasi interleaving penting secara deterministik, bukan hanya mengandalkan probabilistic stress.

### R2-M06 — Lock-Order & Wait-For Closure
Bangun lock-order graph dan wait-for dependency. Required lock ordering harus acyclic. Nested locking, callback-under-lock, reentrant path, condition wait, dan multi-resource acquisition wajib dianalisis.

### R2-M07 — Deadlock/Livelock/Starvation/Fairness Proof
Selain deadlock, buktikan progress: operation tidak dapat stuck karena retry loop, unfair scheduling, starvation, priority inversion yang dapat dikontrol aplikasi, atau mutual backoff yang tidak pernah selesai.

### R2-M08 — Linearizability / Atomicity / Consistency Proof
Untuk concurrent operation yang seharusnya tampak atomic, definisikan linearization point atau equivalent sequential specification dan verifikasi history terhadap spec tersebut.

### R2-M09 — Main-Thread Contract & ANR Budget
Daftarkan seluruh operation yang dapat mencapai main/UI thread. Blocking I/O, network, database, slow binder, long CPU work, lock wait, dan unbounded callback work wajib dilarang atau dibuktikan berada di bawah budget.

### R2-M10 — Strict Runtime Policy / Blocking Detection
Gunakan `StrictMode` atau mekanisme setara untuk mendeteksi accidental disk/network/blocking work pada thread sensitif. Tool detection adalah evidence, bukan pengganti architectural contract.

### R2-M11 — Latency / Response-Time Budget Proof
Tetapkan budget terhadap startup, input handling, IPC, queue delay, decode/parse, background-to-UI response, dan critical flow. Ukur distribution termasuk tail latency; rata-rata saja tidak cukup.

### R2-M12 — Bounded Queue / Backpressure / Load-Shedding
Semua producer-consumer path wajib mempunyai capacity, admission, backpressure, coalescing, cancellation, dropping, or overload policy yang eksplisit. Unbounded queue pada input tak-terbatas = `NOT_PROVEN`.

### R2-M13 — Resource Budget Closure
Tetapkan budget untuk heap, native heap, decoded objects, threads, FDs, sockets, buffers, caches, concurrent tasks, CPU, I/O, dan simultaneous working set berdasarkan target Android 11/ARM64 dan supported workload.

### R2-M14 — Leak-Free Lifetime Proof
Setiap acquired resource harus mempunyai owner, release point, exceptional-path cleanup, cancellation cleanup, lifecycle boundary, dan retained-reference analysis. Heap/reference graph dan repeated-cycle test digunakan untuk menguji pertumbuhan residual.

### R2-M15 — Memory Pressure / OOM Resilience Testing
Uji low-memory, repeated allocations, large-but-valid workloads, cache pressure, background/foreground transitions, and recovery. OOM yang diakibatkan workload di luar contract tidak boleh dicampur dengan OOM dalam supported domain.

### R2-M16 — Resource Exhaustion Fault Injection
Inject FD exhaustion, thread creation failure, allocation failure where testable, disk/I/O stalls, executor saturation, queue saturation, binder slowdown, and cancellation storms untuk membuktikan graceful failure dan cleanup.

### R2-M17 — Cancellation / Timeout / Structured-Concurrency Closure
Task tree wajib mempunyai cancellation ownership, timeout semantics, child cleanup, no orphan job, no duplicated completion, dan deterministic terminal state.

### R2-M18 — Stress / Soak / Repetition sebagai Supporting Search
Long-run, high-frequency, randomized scheduling, repeated open/close, rotate/restart, and workload spikes dipakai untuk menemukan leak/race yang belum dimodelkan. Hasil PASS tidak menggantikan systematic proof.

### R2-M19 — Performance Regression & Change-Impact Invalidation
Perubahan synchronization, dispatcher, executor, queue, cache, resource limit, blocking call, dependency, native behavior, atau workload contract harus membatalkan evidence R2 yang terdampak.

### R2-M20 — Mutation/Fault-Model Adequacy
Mutasi minimal harus mencakup removed synchronization, wrong lock order, leaked close/release, main-thread I/O, unbounded queue, lost cancellation, resource budget breach, dan artificial slow dependency. Verifier wajib menangkap seluruh mutation meaningful.

---

## 4. Fault Model Minimum

```text
DATA_RACE
VISIBILITY_ORDERING_ERROR
LOST_UPDATE
NON_ATOMIC_COMPOUND_OPERATION
DEADLOCK
LIVELOCK
STARVATION
PRIORITY_INVERSION_APP_CONTROLLED
MAIN_THREAD_BLOCK
ANR_TIMEOUT
SLOW_BINDER_CHAIN
UNBOUNDED_QUEUE
BACKPRESSURE_FAILURE
MEMORY_LEAK
NATIVE_HANDLE_LEAK
FD_SOCKET_LEAK
THREAD_LEAK
OOM_SUPPORTED_WORKLOAD
CPU_OVERLOAD
IO_OVERLOAD
CANCELLATION_LEAK
ORPHAN_TASK
DOUBLE_COMPLETION
RESOURCE_RELEASE_ERROR
TAIL_LATENCY_BUDGET_BREACH
```

---

## 5. PASS Formula

`APP_SAFE_R2_PASS` hanya jika:

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
