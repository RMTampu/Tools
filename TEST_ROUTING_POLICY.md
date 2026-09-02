# TEST_ROUTING_POLICY.md — Public Research / Test / Staging

## 1. Status

Dokumen ini mengatur pengujian pada `RMTampu/Tools` sebagai repository Public Research/Test/Staging.

```text
PUBLIC ROLE = RESEARCH / ITERATION / PUBLIC TEST / READY_PRIVATE STAGING
PRIVATE FINAL EXECUTION = RMTampu/ToolBox
```

Aturan global dan `AGENTS.md` selalu lebih tinggi.

## 2. Batas Mutlak

Public hanya boleh menguji:

- component/source yang memang Public;
- prototype Public;
- mock/simulator;
- fixture/dummy data;
- test harness Public;
- contract/interface yang aman dipublikasikan.

Public dilarang:

- checkout/read Private;
- menerima source/kernel/asset/config/state Private;
- menerima APK/artifact Private;
- membangun APK final dari source Private;
- menandatangani candidate Private;
- menjalankan Firebase terhadap artifact Private;
- menjadi reusable CI/relay untuk isi Private.

## 3. Public Test Environment

Environment test Public boleh fleksibel sesuai contract pekerjaan.

Untuk target Android:

- API/ABI/emulator aktual harus dicatat;
- hasil non-target tidak boleh diklaim sebagai final Android 11/API30/ARM64 proof;
- dependency/toolchain harus dikunci bila memengaruhi hasil;
- claim tidak boleh lebih luas dari evidence.

## 4. Public Development Status

Status Public yang diperbolehkan:

```text
PUBLIC_DEVELOPMENT_IN_PROGRESS
PUBLIC_DEVELOPMENT_PASS
READY_PRIVATE
```

`READY_PRIVATE` hanya berarti component/promotion package sudah matang untuk masuk Private. Ia bukan final application PASS.

Jalur kematangan:

```text
SPEC
-> CONTRACT
-> DEPENDENCY
-> UNIT_TEST
-> SIMULATOR
-> FAILURE_TEST
-> PACKAGE_VALIDATION
-> READY_PRIVATE
```

## 5. Firebase

Firebase Final Gate untuk artifact Private bukan fungsi repository ini.

Aturan generic tetap dicatat agar tidak disalahgunakan:

```text
FIREBASE DEFAULT = LOCKED
1 EXPLICIT USER APPROVAL = 1 FIREBASE EXECUTION ATTEMPT
NO AUTO RETRY
NO FALLBACK
```

Eksekusi final dilakukan dari boundary Private sesuai policy Private.

## 6. Kegagalan Private yang Kembali ke Public

Public hanya boleh menerima `SANITIZED_FAILURE_REPORT` yang aman, misalnya:

- error ID;
- contract mismatch;
- unsupported version;
- dependency mismatch;
- lifecycle/validation status generik.

Tidak boleh menerima source/asset/APK/artifact/log dump/path sensitif/state/secret Private.

## 7. Auto Cleanup Public

Setiap Public job wajib melakukan cleanup otomatis setelah sukses/gagal sejauh platform memungkinkan terhadap:

- workflow run/log yang dapat dihapus;
- artifact sementara;
- cache;
- workspace;
- temporary branch/ref;
- debug output;
- temporary test data.

## 8. Final Rule

```text
PUBLIC = RESEARCH / ITERATION / TEST / READY_PRIVATE
PRIVATE_CONTENT_IN_PUBLIC = 0
PRIVATE_EXECUTION_THROUGH_PUBLIC = 0
PUBLIC_FINAL_BUILD = 0
PUBLIC_SIGNING_PRIVATE_CANDIDATE = 0
PUBLIC_FIREBASE_PRIVATE_ARTIFACT = 0
PUBLIC_JOB_AUTO_CLEANUP = REQUIRED
```
