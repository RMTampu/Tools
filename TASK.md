# TASK.md - Tugas Aktif Global

## Tugas Aktif

Kerjakan **Tahap 7 = G — Adapter sumber eksternal, normalisasi, export, dan sync** pada repo publik utama `RMTampu/Tools`.

Baseline resmi aktif adalah **Baseline Tahap 6** dengan APK SHA-256 `64f93a41bbf7623d5bfcc4a6a0bee69cc0ca613897f55c5fd004fcb7f335d878`.

Tahap 7 menambah kemampuan di atas Tahap 6; Baseline Tahap 6 tidak boleh dimodifikasi.

### G1 — External Adapter Contract
- adapter punya Stable ID, label Bahasa Indonesia, capability IMPORT/EXPORT/SYNC, dan schema version.
- external payload dibatasi ukuran/jumlah.
- raw external identity dipertahankan sebagai provenance, bukan dijadikan executable authority.
- adapter tidak boleh bypass Android sandbox/signature.

### G2 — Normalization
- data eksternal dinormalisasi ke canonical record dengan Stable ID.
- field names divalidasi, values bounded, duplicate ID fail-closed.
- canonical ordering deterministic.
- invalid/unknown input menghasilkan diagnostic, tidak masuk working model diam-diam.

### G3 — Deterministic Export
- export memakai snapshot immutable dari model canonical.
- output deterministic, versioned, checksum SHA-256.
- export tidak mengubah working state/registry.
- unsupported target/version fail-closed.

### G4 — Sync
- sync memakai explicit cursor/revision.
- remote/local change dibandingkan dengan last-known state.
- konflik tidak silent overwrite; status CONFLICT wajib eksplisit.
- apply hanya untuk plan CLEAN.
- sync history bounded dan idempotent untuk cursor yang sama.
- offline/no-source/invalid-source dibedakan dari corruption.

## Exit Gate Tahap 7

```text
BASELINE_TAHAP_6_UNCHANGED = YES
VERSION_CODE_GT_TAHAP_6 = YES
EXTERNAL_ADAPTER_CONTRACT = PASS
ADAPTER_CAPABILITY_GATE = PASS
NORMALIZATION = PASS
NORMALIZATION_BOUNDED = PASS
DUPLICATE_EXTERNAL_ID_FAIL_CLOSED = PASS
DETERMINISTIC_EXPORT = PASS
EXPORT_CHECKSUM = PASS
EXPORT_NO_WORKING_STATE_MUTATION = PASS
SYNC_CURSOR = PASS
SYNC_CONFLICT_EXPLICIT = PASS
SYNC_CLEAN_APPLY = PASS
SYNC_IDEMPOTENT = PASS
SYNC_HISTORY_BOUNDED = PASS
EXTERNAL_PRESENTATION_INDONESIA = PASS
R1_R9_AUTOMATIC = PASS
ASSET_SAFE_REGRESSION = PASS
ANDROID_11_API30_BUILD = PASS
PUBLIC_UNSIGNED_APK = PASS
UNKNOWN_REQUIRED_TAHAP_7_BEHAVIOR = 0
```

Private tetap hanya untuk signing, credential, Firebase final runtime test, baseline locking, dan release sensitif.
