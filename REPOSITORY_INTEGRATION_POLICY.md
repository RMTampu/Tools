# GLOBAL REPOSITORY INTEGRATION POLICY

## 1. Status

Dokumen ini menetapkan aturan integrasi lintas repository untuk semua project.

Wajib dibaca setelah `AGENTS.md` dan `GLOBAL_PUBLIC_PRIVATE_DEVELOPMENT_RULES.md`.

Jika dokumen lama atau workflow lama bertentangan dengan aturan global, aturan global menang.

## 2. Model Repository

Setiap project memiliki:

```text
PRIVATE MASTER
= SINGLE SOURCE OF TRUTH + VAULT + FINAL PROCESSING

PUBLIC RESEARCH/STAGING
= RESEARCH + ITERATION + MOCK/SIMULATOR + TEST + READY_PRIVATE PACKAGING
```

Repository backup/shared boleh ada, tetapi tidak otomatis menjadi master.

## 3. Batas Mutlak Private -> Public

**Isi Private dilarang keras keluar ke Public.**

Dilarang:

- checkout Private dari workflow/repo Public;
- memberikan credential Public untuk membaca Private;
- menyalin/mirror source, kernel, asset, config, state, database, dump, atau artifact Private ke Public;
- membangun final product Private di Public;
- memakai Public sebagai bridge untuk mengeksekusi isi Private.

Contract/interface yang memang diklasifikasikan aman untuk Public bukan isi Private yang diekspor; contract tersebut harus dikelola sebagai boundary publik tersendiri.

## 4. Arah Integrasi Resmi

```text
PUBLIC
RESEARCH
-> BUILD COMPONENT
-> AUDIT/TEST/SIMULATOR
-> PACKAGE
-> READY_PRIVATE

                promotion package only
                         |
                         v
PRIVATE MASTER
PREFLIGHT
-> SNAPSHOT
-> INTEGRATE
-> REGRESSION
-> VERIFY
-> COMMIT
-> FINAL BUILD
-> RELEASE
```

Public tidak mengetahui atau mengambil state final Private.

## 5. Promotion Package

Perpindahan Public -> Private hanya melalui paket yang telah dinyatakan `READY_PRIVATE`.

Minimal metadata:

- Project ID;
- Component ID/version;
- Contract version;
- dependency/toolchain lock/digest;
- target platform;
- hash/checksum;
- compatibility;
- test status;
- promotion manifest.

Private wajib memverifikasi identitas dan hash sebelum integrasi.

## 6. Private Preflight

Sebelum proses berat, periksa:

`PACKAGE -> MANIFEST -> HASH -> CONTRACT -> DEPENDENCY -> COMPATIBILITY -> ENVIRONMENT`

Jika satu saja gagal: `STOP`.

Jangan menggunakan build/integrasi berat untuk mencari tahu kesalahan yang seharusnya dapat ditemukan oleh preflight.

## 7. Transaction dan Rollback

Sebelum integrasi:

```text
CURRENT_FINAL
-> SNAPSHOT
-> INTEGRATE
-> VERIFY
```

PASS -> `COMMIT_NEW_FINAL_STATE`.

FAIL -> `ROLLBACK`.

State final sebelumnya harus tetap dapat dipulihkan.

## 8. Kegagalan di Private

**Dilarang keras trial-and-error berulang di Private.**

Jika gagal:

```text
STOP
-> ROLLBACK
-> SANITIZED_FAILURE_REPORT
-> PUBLIC
-> FIX/RETEST
-> READY_PRIVATE
-> PRIVATE
```

Yang boleh keluar ke Public hanya error/compatibility information yang telah disanitasi.

## 9. Sanitized Failure Report

Boleh memuat:

- Error ID;
- contract mismatch;
- unsupported version;
- dependency mismatch;
- lifecycle/validation status generik.

Dilarang memuat:

- source/asset Private;
- secret/token;
- path sensitif;
- database/state;
- internal dump;
- konfigurasi internal;
- detail kernel yang membuka isi Private.

## 10. Shared Component

Komponen lintas project harus berstatus eksplisit `GLOBAL/SHARED_COMPONENT` dan memiliki:

- source resmi;
- version;
- contract;
- dependency;
- compatibility;
- test evidence.

Dilarang mengambil komponen repo lain secara acak.

## 11. Public Auto Cleanup

Setiap Public job wajib memiliki cleanup otomatis setelah selesai/gagal sejauh platform memungkinkan.

Target cleanup:

- workflow run/log;
- artifact sementara;
- cache;
- workspace;
- branch/ref sementara;
- debug output;
- temporary test data.

Cleanup tidak pernah dianggap pengganti larangan Private -> Public.

## 12. Build dan Test Boundary

Public boleh build/test hanya terhadap komponen/data Public, mock, simulator, fixture, atau prototype.

Final application build/test/release dilakukan di Private Master atau jalur final private yang tidak menyalurkan isi Private ke repository Public.

Dependency/toolchain harus dikunci dan environment Public/Private dibuat sedekat mungkin untuk aspek yang mempengaruhi kompatibilitas.

## 13. Project Isolation

Setiap transfer wajib mempunyai sumber, tujuan, Project ID, Component ID/version, dan tujuan integrasi yang jelas.

Asset, source, config, state, keputusan, dan pembahasan project lain tidak boleh dicampurkan tanpa instruksi eksplisit pengguna.

## 14. Repository Role Registry

Peran repo harus dinyatakan di `AGENTS.md` repo masing-masing.

Role yang diperbolehkan antara lain:

- `PRIVATE_MASTER`
- `PUBLIC_RESEARCH_STAGING`
- `PRIVATE_BACKUP`
- `GLOBAL_SHARED_COMPONENT`

Nama aplikasi/repo tidak boleh menjadi syarat agar aturan global berlaku.

## 15. Invariant

```text
PRIVATE_CONTENT_TO_PUBLIC = 0
PUBLIC_PRIVATE_READ_ACCESS = 0
PRIVATE_FINAL_BUILD_IN_PUBLIC = 0
PRIVATE_TRIAL_AND_ERROR = 0
UNSANITIZED_FAILURE_REPORT = 0
UNDECLARED_CROSS_PROJECT_TRANSFER = 0
PUBLIC_JOB_AUTO_CLEANUP = REQUIRED
```
