# APP_SAFE_R7_NATIVE_PLUGIN_RUNTIME.md

## 1. Status

Korpus metode aktif untuk **R7 — Native, ABI, Plugin, Reflection & Third-Party Runtime Safety** dalam framework `APPLICATION_SAFE_100`.

Status riset: `PRACTICAL_SATURATION` terhadap ruang metode yang telah disapu.

Test environment dan Firebase authorization selalu mengikuti `TEST_ROUTING_POLICY.md`.

**Batas Firebase wajib:** seluruh akses/pengecekan/eksekusi Firebase/Test Lab hanya dari Private. Public tidak boleh memakainya, termasuk untuk dummy/prototype. Penyebutan Firebase dan approval di dokumen ini adalah requirement final Private, bukan izin Public; pengujian komponen Public memakai mock/simulator mandiri tanpa koneksi Firebase sampai `READY_PRIVATE`.

---

## 2. Scope

R7 menutup:

- NDK/native C/C++ runtime;
- JNI boundary;
- ABI and ELF/symbol compatibility;
- native undefined behavior and memory safety;
- plugin/engine contract and loading;
- class loader/dynamic code;
- reflection/dynamic entry points;
- third-party runtime behavior and compatibility;
- native/plugin failure isolation.

R6 owns package/build presence; R7 owns runtime semantic correctness after load.

Target release utama tetap `arm64-v8a` pada Android 11/API30. Target tersebut tidak menjadikan seluruh GitHub runtime test wajib ARM64/API30. Development test boleh menggunakan environment yang tersedia dan relevan; target-specific ARM64 runtime witness hanya melalui Final Gate sesuai `TEST_ROUTING_POLICY.md`.

---

## 3. Metode Aktif

### R7-M01 — Native / Dynamic Runtime Universe Closure
Inventaris seluruh `.so`, JNI method, native dependency, exported/imported symbol, plugin/engine, classloader, dynamically resolved class/member, reflection registry, generated adapter, third-party runtime library, and external code-loading path.

### R7-M02 — ABI Contract Closure
Untuk target release utama `arm64-v8a`, verifikasi instruction set/ABI, library location, ELF machine/class, min Android API/native symbol availability, alignment/calling convention assumptions, STL/runtime linkage, and no accidental dependency on unsupported ABI.

ABI contract closure dapat menggunakan structural/package/static evidence pada GitHub untuk membuktikan artifact memang ARM64. Hal tersebut berbeda dari runtime ARM64 witness.

### R7-M03 — Native Dependency / Symbol Graph Closure
Bangun graph `.so -> needed .so -> imported symbol -> provider`. Missing provider, wrong version, unresolved symbol, duplicate conflicting provider, or accidental system-private symbol = fail.

### R7-M04 — JNI Signature & Type Contract
Setiap Java/Kotlin<->native boundary wajib mempunyai exact method signature, type width, signedness, nullability, array/string encoding, ownership, exception propagation, thread attach/detach, and local/global reference lifetime.

### R7-M05 — CheckJNI / Runtime Boundary Validation
Aktifkan CheckJNI atau equivalent test configuration untuk menangkap invalid reference, wrong thread/JNIEnv, pending exception misuse, bad type, invalid array/string access, and JNI contract violation.

Development CheckJNI/runtime testing boleh menggunakan environment GitHub yang tersedia dan kompatibel. Jangan mengklaim environment non-ARM64 sebagai ARM64 runtime proof.

### R7-M06 — Native Static Analysis & Compiler Diagnostics
Gunakan compiler warnings-as-errors for selected classes, static analyzers, UB-focused checks, bounds/lifetime analysis where supported, and audit of manual assembly/intrinsics. Suppression requires proof.

### R7-M07 — Memory-Safety Sanitizers
Gunakan sanitizer yang kompatibel dengan test environment (ASan/HWASan or equivalent) untuk heap/stack use-after-free, overflow, invalid free, and related memory errors.

Sanitizer environment boleh berbeda dari target final bila tooling target tidak mendukung sanitizer secara praktis. Limitation wajib dicatat. Android 11/ARM64 target-specific sanitizer claim tidak boleh dibuat tanpa evidence pada environment yang sah untuk claim tersebut.

Unsupported sanitizer coverage cannot be silently marked PASS.

### R7-M08 — Undefined-Behavior Sanitization
UBSan or equivalent instrumentation untuk signed overflow where relevant, invalid shift, misalignment, invalid enum/vptr, divide-by-zero, and C/C++ UB classes permitted by selected sanitizer profile.

### R7-M09 — Native Fuzzing / Adversarial Input
Fuzz JNI parsers, codecs, native protocol, binary format, and high-risk C/C++ entry points using structure-aware or coverage-guided generation. Crash/hang/UB is failure evidence; no-crash fuzz run is supporting evidence only.

Fuzzing development dapat dilakukan pada ABI/test environment yang tersedia selama semantics yang diklaim tidak architecture-specific atau limitation dinyatakan eksplisit.

### R7-M10 — Native Resource / Thread / Signal Contract
Track malloc/native heap, threads, mutexes, file/socket handles, callbacks, signal handlers, dan lifecycle cleanup. Native resources participate in R2 budget/ownership proof.

### R7-M11 — Native Crash Observability / Symbolization
Final native artifact must have build IDs/symbol mapping required to associate tombstone/native crash with exact binary/source revision. Unsymbolizable critical crash evidence = incomplete diagnosis, not PASS.

### R7-M12 — Plugin / Engine Manifest Contract
Setiap plugin/engine declares identity, version, API range, Android API, supported ABI, capabilities, dependencies, entry point, data/schema contract, resource requirements, isolation policy, and unload/restart semantics.

### R7-M13 — Compatibility Negotiation Before Load
Host validates plugin metadata and capability requirements before class/native load. Incompatible ABI/API/interface/version must be rejected deterministically before executing plugin code.

### R7-M14 — Plugin Interface Behavioral Contract
Interface signature saja tidak cukup. Define pre/postcondition, callback ordering, threading, lifecycle, error semantics, timeout/resource budget, and reentrancy for host<->plugin calls.

### R7-M15 — ClassLoader / Namespace Isolation
Dynamic modules must have explicit parent/child loading policy, duplicate-class behavior, dependency namespace, visibility, and collision handling. Accidental reliance on host-private implementation classes dilarang.

### R7-M16 — Dynamic Code Provenance / Integrity
Prefer no code loading outside trusted APK/update domain. Jika dynamic code required, source, signature/hash, version, storage location, transport, rollback, and authorization must be closed and verified before execution.

### R7-M17 — Reflection Registry / Closed Dynamic Reachability
Reflection/class-name/member-name generation must be finite or contract-bounded. Map every reflected entry to known class/member and integrate with R8/R6 keep rules. Arbitrary unbounded reflection prevents closed proof.

### R7-M18 — Third-Party Runtime Behavioral Qualification
Library identity/version verification (R6) dilanjutkan dengan tests terhadap actual used API, error behavior, threading, lifecycle, performance/resource assumption, platform behavior, and known breaking-version constraints.

Routing:

```text
DEVELOPMENT QUALIFICATION
-> GitHub environment yang tersedia/relevan

ANDROID 11 / API30 / ARM64 TARGET-SPECIFIC RUNTIME QUALIFICATION
-> Firebase Final Gate di Private
-> hanya setelah explicit single-use user approval
```

Jika third-party behavior tidak architecture/API-specific, development evidence tidak perlu menunggu target-identical runtime.

### R7-M19 — Vulnerability / Maintenance Review as Security Evidence
Known-vulnerability and maintenance status must be checked for shipped third-party/native components. Absence of known CVE does not prove correctness; finding critical vulnerability blocks approval according to security policy.

### R7-M20 — Failure Isolation / Sandbox Boundary
Plugin/native failure that can be isolated should not corrupt kernel/global state. Where separate process/sandbox is used, verify crash containment, IPC recovery, resource cleanup, restart limit, and no privilege expansion.

### R7-M21 — Load/Unload/Reload/Upgrade Exercise
Exercise initial load, repeated load, unload where supported, host restart, plugin crash, version upgrade, incompatible downgrade/rejection, resource cleanup, and pending callback cancellation.

Development exercise boleh dilakukan pada GitHub runtime yang tersedia. Jika claim bergantung pada ARM64/API30 runtime, target-specific witness tetap dipisahkan dan hanya boleh dilakukan pada Firebase Final Gate di Private setelah user approval.

### R7-M22 — Differential / Cross-Build Native Validation
Where feasible compare debug/sanitized/release outputs for semantic equivalence and verify compiler/optimization does not reveal UB-dependent behavior.

Cross-build comparison boleh menggunakan beberapa test ABI/environment. Perbedaan test ABI harus dicatat dan tidak boleh dianggap ARM64 final witness.

### R7-M23 — Change-Impact & Mutation Adequacy
Changes to NDK/compiler flags, native dependency, ABI, JNI, plugin interface, loader, reflection name, keep rule, or third-party version invalidate affected evidence. Mutations: wrong ABI, missing symbol, JNI signature mismatch, UAF, plugin version mismatch, duplicate class, unsigned dynamic code, removed reflection keep entry.

Perubahan kandidat setelah Firebase approval tetapi sebelum execution membatalkan approval sesuai `TEST_ROUTING_POLICY.md`.

---

## 4. Development vs Final R7 Status

Untuk development:

```text
R7_DEVELOPMENT_PASS
```

boleh diberikan bila seluruh proof R7 yang relevan dan dapat dilakukan di GitHub telah PASS, structural ARM64 package/ABI contract sudah dibuktikan bila relevan, dan target-specific runtime gap dinyatakan eksplisit.

`R7_DEVELOPMENT_PASS` tidak sama dengan final `APP_SAFE_R7_PASS` jika active claim masih memerlukan ARM64/API30 runtime witness.

Jika Firebase diperlukan untuk menutup target runtime gap, eksekusinya hanya boleh di Private setelah kandidat/gate Private siap dan approval pengguna diperoleh. Public tetap dilarang menjalankan Firebase.

---

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

---

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

`UNPROVEN_ARM64_ABI = 0` minimal membutuhkan proof bahwa artifact/contract ARM64 valid. Jika final claim juga memerlukan actual ARM64 runtime behavior, structural ABI proof saja tidak cukup; target-specific runtime witness harus ditutup melalui user-authorized Firebase Final Gate di Private.
