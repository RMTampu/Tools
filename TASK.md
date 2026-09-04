# TASK.md - Tugas Aktif Global

## Tugas Aktif

Kerjakan **Tahap 8 = H — Repair, staging/activate/verify, rollback, health, dan recovery** pada repo publik utama `RMTampu/Tools`.

Baseline resmi aktif adalah **Baseline Tahap 7** dengan APK SHA-256 `741ebcf799280fbba1b4c7d2e60ba157ba133e3f6545b3468882373150f024f7`.

Tahap 8 menambah kemampuan di atas Tahap 7; Baseline Tahap 7 tidak boleh dimodifikasi.

### H1 — Repair Plan + Staging
- repair mempunyai Stable ID, base revision, bounded upsert/delete set, dan deterministic checksum.
- staging hanya menerima plan yang cocok dengan project identity/revision.
- invalid reference/schema/resource budget gagal tertutup.
- staging tidak mengubah project aktif.

### H2 — Activate + Verify
- alur wajib: Working State → Staging → Validate → Recovery Point → Activate → Verify.
- activate hanya dari STAGED.
- recovery point dibuat sebelum mutasi.
- verification memeriksa project integrity + expected changes + health.
- verified repair menjadi terminal VERIFIED.

### H3 — Rollback + Failure Isolation
- verification gagal tidak boleh dibiarkan sebagai state aktif diam-diam.
- rollback kembali ke pre-activation revision/recovery point.
- rollback explicit dan idempotent.
- failed/invalid transition menghasilkan diagnostic.
- repair history bounded.

### H4 — Health + Recovery
- health membedakan HEALTHY / DEGRADED / RECOVERY_REQUIRED.
- health mencakup kernel/project/access/recovery/runtime/editor/external integration.
- recovery candidate dapat dipreview sebelum restore.
- tidak ada auto-pilih recovery candidate.
- protected safety/recovery core tidak boleh diedit melalui repair plan.

## Exit Gate Tahap 8

```text
BASELINE_TAHAP_7_UNCHANGED = YES
VERSION_CODE_GT_TAHAP_7 = YES
REPAIR_PLAN = PASS
REPAIR_PLAN_BOUNDED = PASS
REPAIR_CHECKSUM_DETERMINISTIC = PASS
STAGING_NO_ACTIVE_MUTATION = PASS
BASE_REVISION_GATE = PASS
ACTIVATE_ONLY_FROM_STAGED = PASS
RECOVERY_POINT_BEFORE_ACTIVATE = PASS
VERIFY_AFTER_ACTIVATE = PASS
VERIFY_EXPECTED_CHANGES = PASS
ROLLBACK_ON_FAILURE = PASS
ROLLBACK_IDEMPOTENT = PASS
REPAIR_HISTORY_BOUNDED = PASS
HEALTH_CLASSIFICATION = PASS
RECOVERY_PREVIEW_EXPLICIT = PASS
PROTECTED_CORE_GATE = PASS
R1_R9_AUTOMATIC = PASS
ASSET_SAFE_REGRESSION = PASS
ANDROID_11_API30_BUILD = PASS
PUBLIC_UNSIGNED_APK = PASS
UNKNOWN_REQUIRED_TAHAP_8_BEHAVIOR = 0
```

Private tetap hanya untuk signing, credential, Firebase final runtime test, baseline locking, dan release sensitif.
