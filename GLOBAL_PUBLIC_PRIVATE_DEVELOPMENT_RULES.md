# GLOBAL PUBLIC–PRIVATE DEVELOPMENT RULES

## Status

Dokumen ini adalah aturan global lintas project/repository. Setiap agen WAJIB membacanya setelah membuka `AGENTS.md` dan sebelum aturan teknis lain. Jika aturan repo lama bertentangan dengan dokumen ini, aturan global ini menang kecuali instruksi pengguna terbaru secara eksplisit mengubahnya.

## 1. Berlaku Global

Aturan berlaku untuk semua project sekarang dan project baru. Aturan tidak bergantung pada nama aplikasi, nama repo, kernel, atau satu produk tertentu.

Setiap project memiliki identitas dan Private Master sendiri. Tidak boleh mencampur source, asset, konfigurasi, keputusan, masalah, state, atau pembahasan antar project tanpa instruksi eksplisit pengguna.

## 2. Private Master

Setiap project memiliki `PRIVATE MASTER` sebagai Single Source of Truth, Vault, dan Final Processing Environment.

Private Master menyimpan kernel/core final, source sensitif, seluruh asset asli, konfigurasi final, state integrasi, versioning resmi, build final, dan release final.

**LARANGAN KERAS: isi Private tidak boleh keluar ke Public.**

Public dilarang menerima source/kernel Private, asset Private, konfigurasi internal, database/state, secret/token, dump internal, artifact yang mengandung isi Private, atau salinan/mirror terselubung.

## 3. Public Research

Public hanya untuk riset, desain, prototype, pengembangan komponen, audit, debugging, mock/simulator, unit/contract/dependency/failure test, packaging, dan staging sebelum Private.

Public bukan master aplikasi final dan tidak boleh membutuhkan akses baca ke Private.

Public hanya boleh bekerja dengan contract/interface yang memang aman dipublikasikan, mock, simulator, test harness, fixture/dummy data, dan komponen public yang sedang dikembangkan.

## 4. Pertumbuhan Bertahap

Project dapat berkembang bertahap:

`A -> A+B -> A+B+C -> A+B+C+D -> ...`

Komponen baru B/C/D dikembangkan dan dimatangkan di Public tanpa melihat isi state final Private. Setelah `READY_PRIVATE`, komponen dipromosikan ke Private dan baru di sana disambungkan ke state final sebenarnya.

## 5. Contract sebagai Jembatan

Public dan Private dihubungkan oleh contract aman, bukan oleh isi Private.

Setiap contract harus memiliki minimal `CONTRACT_ID`, `VERSION`, `COMPATIBILITY`, lifecycle/input-output yang diperlukan, dependency requirement, dan error code aman.

Mock/simulator Public tidak boleh menjadi salinan kernel Private terselubung.

## 6. Jalur Kematangan Komponen

Sebelum boleh masuk Private, komponen wajib melewati:

`SPEC -> CONTRACT -> DEPENDENCY -> UNIT_TEST -> SIMULATOR -> FAILURE_TEST -> PACKAGE_VALIDATION -> READY_PRIVATE`

Belum `READY_PRIVATE` berarti dilarang masuk final integration.

## 7. Promotion Package

Paket promosi minimal membawa metadata aman:

- `PROJECT_ID`
- `COMPONENT_ID`
- `VERSION`
- `CONTRACT_VERSION`
- dependency/toolchain lock atau digest
- target platform
- hash/checksum
- compatibility
- test status
- promotion manifest

Private wajib menolak paket yang identitas, hash, contract, dependency, atau compatibility-nya tidak valid.

## 8. Private Preflight dan Transaction

Sebelum proses berat, Private menjalankan preflight murah terhadap package/manifest/hash/contract/dependency/compatibility/environment.

Jika preflight gagal: **STOP**. Jangan menjalankan build/integrasi berat.

Sebelum integrasi:

`CURRENT_FINAL -> SNAPSHOT -> INTEGRATE -> VERIFY`

Jika PASS: `COMMIT_NEW_FINAL_STATE`.

Jika FAIL: `ROLLBACK` ke state sebelumnya.

## 9. Larangan Trial-and-Error di Private

**DILARANG KERAS melakukan trial-and-error berulang di Private.**

Jika kegagalan terjadi di Private:

`STOP -> ROLLBACK -> SANITIZED_FAILURE_REPORT -> PUBLIC -> FIX -> RETEST -> READY_PRIVATE -> PRIVATE`

Dilarang pola:

`Private gagal -> edit di Private -> build lagi -> gagal -> edit lagi`.

Private dipakai sesedikit mungkin. Public menghabiskan iterasi.

## 10. Sanitized Failure Report

Informasi yang keluar dari Private ke Public hanya boleh berupa laporan aman, misalnya error ID, contract mismatch, unsupported version, lifecycle failure, dependency mismatch, atau generic validation result.

Laporan dilarang membawa source/asset Private, secret, token, konfigurasi internal, path sensitif, dump internal, database/state, atau detail kernel yang membuka isi Private.

## 11. Auto Cleanup Public

Setiap pekerjaan yang memakai mesin Public WAJIB memiliki Auto Cleanup otomatis, berjalan setelah job berhasil maupun gagal tanpa menunggu perintah pengguna.

Bersihkan sejauh platform memungkinkan:

- workflow run/log
- artifact sementara
- cache pekerjaan
- workspace sementara
- branch/ref sementara yang memang dibuat untuk job
- debug output sementara
- temporary test data

Cleanup tidak menggantikan aturan keamanan. Data Private tidak boleh pernah masuk Public sejak awal.

## 12. Isolasi Project dan Shared Component

Setiap pekerjaan harus memiliki minimal `PROJECT_ID`, target master, component ID/version, contract, target platform, dan compatibility.

Dilarang mengambil isi repo/project lain hanya karena terlihat cocok.

Komponen lintas project harus dinyatakan eksplisit sebagai `GLOBAL/SHARED_COMPONENT` dan tetap memiliki source resmi, version, contract, dependency, compatibility, dan test.

## 13. Dependency dan Environment Lock

Public dan Private harus memakai versi toolchain/dependency yang dikunci dan sedekat mungkin untuk aspek yang mempengaruhi hasil. Perbedaan environment harus terdeteksi sebelum final processing.

## 14. Jalur Resmi

Public:

`RESEARCH -> DESIGN -> BUILD_COMPONENT -> AUDIT -> TEST -> SIMULATOR -> PACKAGE -> READY_PRIVATE -> AUTO_CLEANUP`

Private:

`PREFLIGHT -> SNAPSHOT -> INTEGRATE -> REGRESSION -> VERIFY -> COMMIT -> FINAL_BUILD -> RELEASE`

Jika gagal di Private:

`ROLLBACK -> SANITIZED_FAILURE_REPORT -> PUBLIC`

## 15. Larangan Sistem

Dilarang menyediakan atau menggunakan:

- checkout Private dari Public
- token/credential Public untuk membaca Private
- mirror kernel/source/asset Private di Public
- registry Public yang menyimpan isi Private
- transfer bebas antar project
- debug/trial-and-error berulang di Private
- log yang membocorkan data Private
- ketergantungan Public pada isi Private

## 16. Konteks Percakapan Project

Perintah `Buka [Nama Project]` mengaktifkan hanya konteks project tersebut. Asset, keputusan, masalah, dan pembahasan project lain tidak boleh ikut terbawa.

Pertanyaan umum wajib dijawab sebagai pertanyaan umum dan tidak otomatis dikaitkan dengan project/repo/aplikasi lama.

Prioritas konteks:

`Pesan pengguna saat ini -> Project yang secara eksplisit dibuka -> Konteks umum`.

## 17. Prinsip Final

- Private Master selalu menjadi sumber kebenaran final.
- Isi Private tidak pernah keluar ke Public.
- Public menghabiskan iterasi; Private menghabiskan sesedikit mungkin kuota.
- Kegagalan Private wajib kembali ke Public setelah rollback dan sanitasi laporan.
- Asset dan pembahasan antar project wajib terisolasi.
- Pekerjaan manual berulang yang dapat diotomatisasi wajib diotomatisasi.
