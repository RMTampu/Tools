# TASK.md - Tugas Aktif Global

## Tugas Aktif

Kerjakan **Tahap 11 = K — App.apk + App.patch, remote verification, safe restore** pada repo publik utama `RMTampu/Tools`.

Baseline/rollback anchor permanen tetap **Tahap 7** dengan signed APK SHA-256 `741ebcf799280fbba1b4c7d2e60ba157ba133e3f6545b3468882373150f024f7`.

Tahap 10 signed APK SHA-256 `fbc39153bc121ed2d32bc9c24e9ff8f0e9b7730fcef01021f4adfd830fbd21ff` adalah development predecessor candidate, bukan baseline.

Tahap 11 wajib menghasilkan:
- App.apk;
- App.patch deklaratif dan SHA-256 bound;
- remote verification dengan release certificate trust anchor;
- fail-closed sebelum mutasi bila proof tidak valid;
- final recovery point sebelum patch mutasi;
- safe restore eksplisit;
- R1-R9 dan ASSET_SAFE PASS;
- Android 11/API30 PASS;
- private signing dengan certificate continuity;
- Firebase/Test Lab tidak dijalankan.

## Exit Gate

```text
ROLLBACK_BASELINE_TAHAP_7 = PASS
DEVELOPMENT_PARENT_TAHAP_10_SIGNED_IDENTITY = PASS
VERSION_CODE_11 = PASS
PATCH_MANIFEST_DETERMINISTIC = PASS
PATCH_PAYLOAD_SHA256 = PASS
PATCH_PAYLOAD_BOUNDED = PASS
REMOTE_CERT_TRUST_ANCHOR = PASS
REMOTE_SIGNATURE_REQUIRED = PASS
REMOTE_SIGNATURE_INVALID_FAIL_CLOSED = PASS
SAFE_RESTORE_POINT_BEFORE_MUTATION = PASS
SAFE_RESTORE_EXPLICIT = PASS
APP_PATCH_REQUEST = PASS
R1_R9_AUTOMATIC = PASS
ASSET_SAFE_REGRESSION = PASS
ANDROID_11_API30_BUILD = PASS
PUBLIC_UNSIGNED_APK = PASS
PRIVATE_SIGNING = PASS
APP_APK = PASS
APP_PATCH = PASS
SIGNATURE_V3_API30 = PASS
CERTIFICATE_CONTINUITY = PASS
FIREBASE_USED = NO
UNKNOWN_REQUIRED_TAHAP_11_BEHAVIOR = 0
```
