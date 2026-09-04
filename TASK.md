# TASK.md - Tugas Aktif Global

## Tugas Aktif

Kerjakan **Tahap 10 = J — READY, validator/IR, private build, signing, final candidate identity** pada repo publik utama `RMTampu/Tools`.

Baseline parent aktif adalah **Tahap 9** dengan signed APK SHA-256 `8f6f504c8f289926ad88550ab2686b801efc3ac12536c9e57f807b208461a116`.

Instruksi user untuk rangkaian ini:
- selesaikan Tahap 8, 9, dan 10 beruntun;
- APK tiap tahap ditandatangani;
- **jangan menjalankan Firebase**;
- laporan hanya setelah seluruh rangkaian selesai.

Tahap 10 menambah kemampuan di atas Tahap 9; APK Tahap 9 tidak boleh dimodifikasi.

### J1 — READY Gate
- READY adalah lifecycle eksplisit, bukan label UI.
- READY hanya boleh dibuat dari project identity/schema/reference/runtime yang valid.
- Working Project wajib bersih sebelum READY.
- recovery required, live DIRTY/CONFLICT/FAILED_SAFE, atau repair STAGED/ACTIVATED/FAILED_SAFE memblokir READY.
- final recovery point dibuat sebelum lifecycle READY dipublish.
- transition READY terverifikasi dan revisioned.

### J2 — Build Validator + Deterministic IR
- validator meliputi project, runtime, library, health, live, repair, Android target, dan public/private boundary.
- IR immutable, versioned, deterministic, canonical, stable-keyed, bounded.
- IR memuat project/revision/resources/references/dependencies serta IDs runtime/library.
- resource payload direpresentasikan dengan SHA-256, bukan dieksekusi oleh builder.
- IR build tidak memutasi project/runtime/library.

### J3 — Candidate Identity
- candidate identity mengikat applicationId, versionCode, versionName, parent signed APK, IR SHA-256, dan unsigned APK SHA-256.
- candidate ID deterministic dan SHA-256 based.
- exact public source commit + public R1-R9 run + unsigned APK digest wajib masuk provenance.
- candidate identity berubah bila salah satu input identitas berubah.

### J4 — Private Build + Signing Tanpa Firebase
- private hanya mengambil exact public Tahap 10 PASS candidate.
- rebuild unsigned exact harus cocok digest publik.
- signing memakai certificate yang sama dengan Tahap 8/9.
- verifikasi zipalign, package metadata, v2/v3 signature, certificate SHA-256, dan signed APK SHA-256.
- **Firebase / Test Lab dilarang untuk rangkaian ini.**
- hasil akhir Tahap 10 adalah signed APK + provenance + signature verification.

## Exit Gate Tahap 10

```text
PARENT_TAHAP_9_SIGNED_IDENTITY = PASS
VERSION_CODE_GT_TAHAP_9 = YES
READY_PREVIEW_READ_ONLY = PASS
READY_REQUIRES_CLEAN_PROJECT = PASS
READY_RECOVERY_POINT_BEFORE_PUBLISH = PASS
READY_REVISIONED = PASS
READY_BLOCKS_UNSAFE_LIVE_REPAIR = PASS
BUILD_VALIDATOR = PASS
IR_VERSIONED = PASS
IR_DETERMINISTIC = PASS
IR_STABLE_KEYED = PASS
IR_RESOURCE_SHA256 = PASS
IR_NO_MUTATION = PASS
CANDIDATE_IDENTITY = PASS
CANDIDATE_IDENTITY_DETERMINISTIC = PASS
CANDIDATE_IDENTITY_INPUT_SENSITIVE = PASS
R1_R9_AUTOMATIC = PASS
ASSET_SAFE_REGRESSION = PASS
ANDROID_11_API30_BUILD = PASS
PUBLIC_UNSIGNED_APK = PASS
PRIVATE_SIGNING = PASS
SIGNATURE_V3_API30 = PASS
CERTIFICATE_CONTINUITY = PASS
FIREBASE_USED = NO
UNKNOWN_REQUIRED_TAHAP_10_BEHAVIOR = 0
```
