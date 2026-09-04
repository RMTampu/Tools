# TASK.md - Tugas Aktif Global

## Tugas Aktif

Kerjakan **Tahap 9 = I — Capability Scan, Live Session, TERAPKAN, compare/history, dan self-edit terproteksi** pada repo publik utama `RMTampu/Tools`.

Baseline resmi aktif adalah **Baseline Tahap 8** dengan APK SHA-256 `1be38ee81c02ffc02882f883fdaa61caff6a9d462a5fcfdc6a8f520f06ee373a`.

Tahap 9 menambah kemampuan di atas Tahap 8; Baseline Tahap 8 tidak boleh dimodifikasi.

### I1 — Capability Scan
- target punya Stable Target ID dan status installed.
- area UI / Logic / Data / Binding / Asset / Runtime diklasifikasi AVAILABLE / READ_ONLY / UNAVAILABLE.
- scanner fail-closed saat target tidak installed.
- capability AVAILABLE tidak boleh diberikan bila tidak ada edit door yang sah.
- hasil scan deterministic dan read-only.

### I2 — Live Session
- LIVE hanya dapat dibuka bila Runtime capability AVAILABLE.
- session mempunyai Stable Session ID, target ID, base revision, dan explicit state.
- change set bounded dan stable-keyed.
- session tidak mengubah project aktif sebelum TERAPKAN.
- stale base revision menghasilkan CONFLICT, bukan overwrite.

### I3 — TERAPKAN + Compare/History
- compare menampilkan queued change tanpa mutasi.
- TERAPKAN menggunakan jalur aman Tahap 8: stage → recovery point → activate → verify/rollback.
- apply hanya untuk session DIRTY dan target/bridge yang sah.
- history bounded.
- repeated apply tanpa change baru tidak boleh melakukan mutasi kedua.
- failed apply masuk FAILED_SAFE atau CONFLICT dengan diagnostic eksplisit.

### I4 — Self Edit Protected
- ToolBox self-edit hanya pada declarative/editable surfaces.
- kernel / recovery / safety / security core selalu protected.
- self-edit tidak boleh bypass Android sandbox/signature.
- self-edit menggunakan project identity dan repair pipeline yang sama.
- LIVE/TERAPKAN untuk self target tidak mengubah baseline APK; hanya Working Project State.

## Exit Gate Tahap 9

```text
BASELINE_TAHAP_8_UNCHANGED = YES
VERSION_CODE_GT_TAHAP_8 = YES
CAPABILITY_SCAN = PASS
CAPABILITY_SCAN_FAIL_CLOSED = PASS
CAPABILITY_AREA_CLASSIFICATION = PASS
EDIT_DOOR_GATE = PASS
LIVE_SESSION = PASS
LIVE_RUNTIME_GATE = PASS
LIVE_CHANGE_BOUNDED = PASS
LIVE_NO_PREAPPLY_MUTATION = PASS
LIVE_STALE_REVISION_CONFLICT = PASS
COMPARE_READ_ONLY = PASS
TERAPKAN_REPAIR_PIPELINE = PASS
TERAPKAN_IDEMPOTENT = PASS
LIVE_HISTORY_BOUNDED = PASS
SELF_EDIT_DECLARATIVE_ONLY = PASS
SELF_EDIT_PROTECTED_CORE = PASS
SELF_EDIT_NO_BASELINE_MUTATION = PASS
R1_R9_AUTOMATIC = PASS
ASSET_SAFE_REGRESSION = PASS
ANDROID_11_API30_BUILD = PASS
PUBLIC_UNSIGNED_APK = PASS
UNKNOWN_REQUIRED_TAHAP_9_BEHAVIOR = 0
```

Private tetap hanya untuk signing, credential, Firebase final runtime test, baseline locking, dan release sensitif.
