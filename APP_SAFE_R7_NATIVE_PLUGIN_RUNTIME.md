# APP_SAFE_R7_NATIVE_PLUGIN_RUNTIME.md

> Aturan aktif: pengembangan dan pematangan dilakukan di repo publik utama. Secret, signing, credential, final signed build, Firebase/final runtime test, dan release sensitif tetap di jalur private.

## 1. Status
Korpus aktif R7 — Native, ABI, Plugin, Reflection & Third-Party Runtime Safety. Target produk Android 11/API30/arm64-v8a. Development evidence publik tidak boleh diklaim sebagai final target-specific witness bila environment berbeda.

## 2. Scope
R7 mencakup native/NDK, JNI, ABI, ELF/symbol, plugin/engine contract, class loading, reflection, dynamic code, third-party runtime, dan failure isolation.

## 3. Metode Aktif

### R7-M01 — Native / Dynamic Runtime Universe Closure
Inventaris seluruh native library, JNI entry, plugin, engine, reflection target, dynamic loader, dan third-party runtime path.

### R7-M02 — ABI Contract Closure
Verifikasi ABI target, ELF machine/class, library placement, API compatibility, linkage, dan tidak ada unsupported ABI dependency.

### R7-M03 — Native Dependency / Symbol Graph Closure
Buktikan graph library dan imported/exported symbols lengkap, konsisten, dan tidak memakai provider tak sah.

### R7-M04 — JNI Signature & Type Contract
Tutup signature, width, nullability, encoding, ownership, exception, thread attach/detach, dan reference lifetime.

### R7-M05 — CheckJNI / Runtime Boundary Validation
Gunakan CheckJNI atau equivalent untuk menangkap reference/thread/type/exception misuse pada environment yang mendukung.

### R7-M06 — Native Static Analysis & Compiler Diagnostics
Aktifkan warning/analyzer yang relevan dan fail pada finding critical yang belum diberi justification.

### R7-M07 — Memory-Safety Sanitizers
Gunakan ASan/HWASan/equivalent bila applicable dan catat limitation environment.

### R7-M08 — Undefined-Behavior Sanitization
Gunakan UBSan/equivalent untuk kelas UB yang relevan.

### R7-M09 — Native Fuzzing / Adversarial Input
Fuzz parser/protocol/native entry berisiko dengan corpus, budget, deduplication, dan regression seed.

### R7-M10 — Native Resource / Thread / Signal Contract
Tutup ownership native heap, thread, mutex, fd/socket, callback, signal, dan cleanup.

### R7-M11 — Native Crash Observability / Symbolization
Pastikan build ID/symbol mapping cukup untuk mengikat crash ke binary dan source revision.

### R7-M12 — Plugin / Engine Manifest Contract
Deklarasikan identity, version, API range, ABI, capability, dependency, entry point, lifecycle, dan resource requirement.

### R7-M13 — Compatibility Negotiation Before Load
Host harus menolak plugin incompatible sebelum executable code dimuat.

### R7-M14 — Plugin Interface Behavioral Contract
Tentukan pre/postcondition, callback ordering, threading, timeout, error, lifecycle, resource budget, dan reentrancy.

### R7-M15 — ClassLoader / Namespace Isolation
Definisikan parent/child policy, duplicate-class behavior, visibility, dan collision handling.

### R7-M16 — Dynamic Code Provenance / Integrity
Dynamic code, bila ada, wajib memiliki provenance, integrity, version, storage, authorization, dan rollback yang tervalidasi.

### R7-M17 — Reflection Registry / Closed Dynamic Reachability
Reflection target harus finite/contract-bounded dan terhubung ke keep/reachability proof.

### R7-M18 — Third-Party Runtime Behavioral Qualification
Uji used API, error behavior, threading, lifecycle, resource/performance assumption, dan platform compatibility.

### R7-M19 — Vulnerability / Maintenance Review as Security Evidence
Review vulnerability dan maintenance status sebagai supporting security evidence, bukan correctness proof tunggal.

### R7-M20 — Failure Isolation / Sandbox Boundary
Buktikan crash/failure plugin/native tidak merusak global state di luar boundary yang diizinkan.

### R7-M21 — Load/Unload/Reload/Upgrade Exercise
Uji load berulang, restart, crash, upgrade, downgrade rejection, cleanup, dan callback cancellation.

### R7-M22 — Differential / Cross-Build Native Validation
Bandingkan debug/sanitized/release semantics bila feasible untuk menemukan UB/optimization-dependent behavior.

### R7-M23 — Change-Impact & Mutation Adequacy
Perubahan ABI/JNI/plugin/loader/reflection/native dependency membatalkan evidence terkait; mutation challenge harus menangkap meaningful defect.

## 4. Development vs Final R7 Status
`R7_DEVELOPMENT_PASS` hanya mencakup proof publik yang benar-benar dijalankan. Final ARM64/API30 runtime witness yang memerlukan credential/lab private tetap terpisah.

## 5. Fault Model Minimum
```text
WRONG_NATIVE_ABI
UNRESOLVED_NATIVE_SYMBOL
PRIVATE_PLATFORM_SYMBOL_DEPENDENCY
JNI_SIGNATURE_MISMATCH
JNI_REFERENCE_LIFETIME_ERROR
JNI_WRONG_THREAD_ENV
NATIVE_BUFFER_OVERFLOW
USE_AFTER_FREE
DOUBLE_FREE
NATIVE_MEMORY_LEAK
C_CPP_UNDEFINED_BEHAVIOR
NATIVE_HANG_DEADLOCK
PLUGIN_METADATA_LIE
PLUGIN_API_VERSION_MISMATCH
PLUGIN_CAPABILITY_MISMATCH
PLUGIN_LIFECYCLE_ERROR
CLASSLOADER_COLLISION
REFLECTION_TARGET_MISSING
DYNAMIC_CODE_INTEGRITY_FAILURE
THIRD_PARTY_RUNTIME_INCOMPATIBILITY
PLUGIN_CRASH_PROPAGATION
UNSYMBOLIZABLE_NATIVE_FAILURE
```

## 6. PASS Formula
`APP_SAFE_R7_PASS` hanya jika:
```text
NATIVE_COMPONENT_UNKNOWN = 0
UNPROVEN_ARM64_ABI = 0
UNRESOLVED_NATIVE_DEPENDENCY = 0
JNI_CONTRACT_UNKNOWN = 0
UNRESOLVED_SANITIZER_REQUIRED_FINDING = 0
PLUGIN_CONTRACT_UNKNOWN = 0
UNBOUNDED_REQUIRED_REFLECTION = 0
UNVERIFIED_DYNAMIC_CODE = 0
THIRD_PARTY_USED_API_UNQUALIFIED = 0
NATIVE_PLUGIN_FAULT_ESCAPE = 0
STALE_EVIDENCE = 0
```
