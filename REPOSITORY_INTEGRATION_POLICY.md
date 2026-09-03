# GLOBAL REPOSITORY INTEGRATION POLICY

## 1. Status

Dokumen ini menetapkan aturan integrasi lintas repository untuk semua project.

Wajib dibaca setelah `AGENTS.md` dan `GLOBAL_PUBLIC_PRIVATE_DEVELOPMENT_RULES.md`.

Jika dokumen lama atau workflow lama bertentangan dengan aturan global, aturan global menang.

## 2. Model Repository

Setiap project memiliki:

```text
PRIVATE MASTER
= SINGLE SOURCE OF TRUTH + VAULT + FINAL PROCESSING + PRIVATE EXECUTION

PUBLIC RESEARCH/STAGING
= RESEARCH + ITERATION + MOCK/SIMULATOR + PUBLIC TEST + READY_PRIVATE PACKAGING
```

Repository backup/shared boleh ada, tetapi tidak otomatis menjadi master.

## 3. Batas Mutlak Private -> Public

**Isi Private dilarang keras keluar ke Public.**

Dilarang:

- checkout Private dari workflow/repo Public;
- memberikan credential Public untuk membaca Private;
- menyalin/mirror source, kernel, asset, config, state, database, dump, APK, atau artifact Private ke Public;
- membangun final product Private di Public;
- memakai Public sebagai bridge/reusable runner/CI engine untuk mengeksekusi isi Private;
- mengirim artifact kandidat Private ke Public untuk diteruskan ke Firebase atau final test.

Contract/interface yang memang diklasifikasikan aman untuk Public bukan isi Private yang diekspor; contract tersebut harus dikelola sebagai boundary publik tersendiri.

Pengujian penyambungan di Public WAJIB menggunakan dummy/mock/simulator mandiri dari contract aman, bukan APK baseline atau salinan/ekstraksi/penyamaran isi Private. Integrasi ke baseline/state final yang sebenarnya hanya dilakukan setelah Promotion Package masuk Private dan preflight PASS.

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
-> BUILD APK
-> SIGN CANDIDATE
-> VERIFY SIGNATURE
-> FIREBASE / FINAL RUNTIME TEST
-> PASS
-> RELEASE
```

Public tidak mengetahui atau mengambil state final Private.

## 5. Promotion Package

Perpindahan Public -> Private hanya melalui paket yang telah dinyatakan `READY_PRIVATE`. Status ini berarti paket matang untuk integrasi Private, bukan integrasi baseline atau aplikasi final sudah PASS.

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

## 8. Private Execution Boundary

Seluruh pekerjaan yang memerlukan isi Private berjalan di boundary Private.

Untuk aplikasi final, urutan resmi adalah:

```text
COMMITTED PRIVATE STATE
-> BUILD APK
-> SIGN CANDIDATE
-> VERIFY SIGNATURE
-> FIREBASE / FINAL RUNTIME TEST
-> PASS
-> RELEASE
```

APK yang dipakai untuk final test harus sudah ditandatangani dan diverifikasi. Artifact release harus identik secara identity/hash/signature dengan candidate yang memperoleh final PASS.

GitHub Actions diperbolehkan untuk build/test Private hanya jika workflow, source, asset, secret, artifact, log, dan seluruh jalur eksekusinya tetap berada pada boundary Private dan tidak menggunakan repository Public sebagai executor/relay.

## 9. Kegagalan di Private

**Dilarang keras trial-and-error berulang di Private.**

Jika gagal:

```text
STOP
-> ROLLBACK bila diperlukan
-> SANITIZED_FAILURE_REPORT
-> PUBLIC
-> FIX/RETEST
-> READY_PRIVATE
-> PRIVATE
```

Yang boleh keluar ke Public hanya error/compatibility information yang telah disanitasi.

## 10. Sanitized Failure Report

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
- APK/artifact Private;
- internal dump;
- konfigurasi internal;
- detail kernel yang membuka isi Private.

## 11. Shared Component

Komponen lintas project harus berstatus eksplisit `GLOBAL/SHARED_COMPONENT` dan memiliki:

- source resmi;
- version;
- contract;
- dependency;
- compatibility;
- test evidence.

Dilarang mengambil komponen repo lain secara acak.

## 12. Public Auto Cleanup

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

## 13. Build dan Test Boundary

Public boleh build/test hanya terhadap komponen/data Public, mock, simulator, fixture, atau prototype.

Public tidak boleh build/test aplikasi final yang membutuhkan source, asset, state, artifact, credential, atau internal contract Private.

Final application build, signing, signature verification, final runtime test, dan release dilakukan pada mesin/jalur Private.

Seluruh akses operasional Firebase/Test Lab hanya boleh dari Private. Public dilarang melakukan connection check, catalog/model lookup, preflight yang mengakses Firebase, upload/download, atau test matrix, termasuk terhadap dummy/prototype Public. Single-use approval hanya berlaku untuk final execution Private, bukan pengecualian Public. Riset dokumentasi API terbuka serta mock/fixture tanpa koneksi/panggilan Firebase tetap diperbolehkan sesuai aturan global §9.1.

Dependency/toolchain harus dikunci dan environment Public/Private dibuat sedekat mungkin untuk aspek yang mempengaruhi kompatibilitas.

## 14. Project Isolation

Setiap transfer wajib mempunyai sumber, tujuan, Project ID, Component ID/version, dan tujuan integrasi yang jelas.

Asset, source, config, state, keputusan, dan pembahasan project lain tidak boleh dicampurkan tanpa instruksi eksplisit pengguna.

## 15. Repository Role Registry

Peran repo harus dinyatakan di `AGENTS.md` repo masing-masing.

Role yang diperbolehkan antara lain:

- `PRIVATE_MASTER`
- `PUBLIC_RESEARCH_STAGING`
- `PRIVATE_BACKUP`
- `GLOBAL_SHARED_COMPONENT`

Nama aplikasi/repo tidak boleh menjadi syarat agar aturan global berlaku.

## 16. Invariant

```text
PRIVATE_CONTENT_TO_PUBLIC = 0
PUBLIC_PRIVATE_READ_ACCESS = 0
PRIVATE_EXECUTION_THROUGH_PUBLIC = 0
PRIVATE_FINAL_BUILD_IN_PUBLIC = 0
PRIVATE_ARTIFACT_RELAY_THROUGH_PUBLIC = 0
FIREBASE_EXECUTION_BOUNDARY = PRIVATE_ONLY
PUBLIC_FIREBASE_ACCESS = 0
PUBLIC_FIREBASE_EXECUTION = 0
PRIVATE_TRIAL_AND_ERROR = 0
UNSANITIZED_FAILURE_REPORT = 0
UNDECLARED_CROSS_PROJECT_TRANSFER = 0
PUBLIC_JOB_AUTO_CLEANUP = REQUIRED
PRIVATE_FINAL_ORDER = BUILD -> SIGN -> VERIFY_SIGNATURE -> FINAL_TEST -> PASS -> RELEASE
```
