# GLOBAL PUBLIC–PRIVATE DEVELOPMENT RULES

## Status

Dokumen ini adalah aturan global lintas project/repository. Setiap agen WAJIB membacanya setelah membuka `AGENTS.md` dan sebelum aturan teknis lain. Jika aturan repo lama bertentangan dengan dokumen ini, aturan global ini menang kecuali instruksi pengguna terbaru secara eksplisit mengubahnya.

## 1. Berlaku Global

Aturan berlaku untuk semua project sekarang dan project baru. Aturan tidak bergantung pada nama aplikasi, nama repo, kernel, atau satu produk tertentu.

Setiap project memiliki identitas dan Private Master sendiri. Tidak boleh mencampur source, asset, konfigurasi, keputusan, masalah, state, atau pembahasan antar project tanpa instruksi eksplisit pengguna.

## 2. Private Master

Setiap project memiliki `PRIVATE MASTER` sebagai Single Source of Truth, Vault, dan Final Processing Environment.

Private Master menyimpan kernel/core final, source sensitif, seluruh asset asli, konfigurasi final, state integrasi, versioning resmi, build final, signing identity/reference, final-test evidence, dan release final.

**LARANGAN KERAS: isi Private tidak boleh keluar ke Public.**

Public dilarang menerima source/kernel Private, asset Private, konfigurasi internal, database/state, secret/token, dump internal, artifact yang mengandung isi Private, atau salinan/mirror terselubung.

## 3. Public Research

Public digunakan untuk riset, desain, prototype, pengembangan komponen, audit, debugging, mock/simulator, unit/contract/dependency/failure test, packaging, dan staging sebelum Private.

Public bukan master aplikasi final dan tidak boleh membutuhkan akses baca ke Private.

Public hanya boleh bekerja dengan contract/interface yang memang aman dipublikasikan, mock, simulator, test harness, fixture/dummy data, dan komponen Public yang sedang dikembangkan.

Batas akhir pekerjaan Public adalah `READY_PRIVATE`: komponen dan Promotion Package sudah matang untuk diintegrasikan ke baseline APK/state final di Private. Status ini bukan bukti bahwa integrasi sebenarnya atau aplikasi final sudah PASS.

## 4. Pertumbuhan Bertahap

Project dapat berkembang bertahap:

`A -> A+B -> A+B+C -> A+B+C+D -> ...`

Komponen baru B/C/D dikembangkan dan dimatangkan di Public tanpa melihat isi state final Private. Setelah `READY_PRIVATE`, komponen dipromosikan ke Private dan baru di sana disambungkan ke state final sebenarnya.

## 5. Contract sebagai Jembatan

Public dan Private dihubungkan oleh contract aman, bukan oleh isi Private.

Setiap contract harus memiliki minimal `CONTRACT_ID`, `VERSION`, `COMPATIBILITY`, lifecycle/input-output yang diperlukan, dependency requirement, dan error code aman.

Untuk pengujian penyambungan di Public, baseline APK/state final Private WAJIB digantikan oleh dummy/mock/simulator mandiri yang dibuat dari contract/interface yang sudah dinyatakan aman untuk Public.

Dummy tersebut bukan APK baseline, bukan salinan kernel, dan bukan hasil mengekstrak, menyamarkan, mengganti nama, atau menyunting source/asset/config/state/APK/artifact Private. Isi Private tidak boleh diambil ke Public terlebih dahulu untuk kemudian disanitasi.

Jika contract aman belum cukup untuk suatu pengujian, catat gap sebagai `NOT_PROVEN`; jangan mengambil isi Private atau mengklaim dummy sebagai bukti integrasi/runtime final.

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

## 9. Private Execution Machine

Mesin Private adalah satu-satunya jalur yang boleh mengeksekusi isi Private untuk final processing.

Jalur final resmi:

`PREFLIGHT -> SNAPSHOT -> INTEGRATE -> REGRESSION -> VERIFY -> COMMIT -> BUILD_APK -> SIGN_CANDIDATE -> VERIFY_SIGNATURE -> FIREBASE/FINAL_RUNTIME_TEST -> PASS -> RELEASE`

Aturan:

- build APK yang memakai source/asset Private wajib berjalan pada boundary Private;
- signing candidate wajib berjalan pada boundary Private;
- seluruh akses operasional Firebase/Test Lab dan final runtime execution terhadap APK kandidat wajib dimulai serta dijalankan dari boundary Private;
- APK yang diuji final harus merupakan candidate yang sudah ditandatangani dan diverifikasi signature-nya;
- release hanya boleh memakai artifact kandidat yang identitas/hash/signature-nya sama dengan artifact yang memperoleh final PASS;
- Public tidak boleh menjadi bridge, reusable runner, caller target, artifact relay, atau CI engine untuk mengeksekusi isi Private.

GitHub Actions boleh digunakan sebagai mesin Private jika workflow dan seluruh input/output Private tetap berada pada repository/jalur Private dan tidak disalurkan ke Public.

### 9.1 Firebase / Test Lab Hanya di Private

**Public DILARANG melakukan pengecekan, mengakses layanan, atau menjalankan pengujian Firebase/Test Lab dalam bentuk apa pun, termasuk dengan dummy, prototype, atau artifact yang sepenuhnya Public.**

Larangan mencakup connection check, autentikasi ke layanan, pembacaan catalog/model, candidate preflight yang mengakses Firebase, upload/download artifact atau hasil, submit test matrix, serta penggunaan Public sebagai caller, executor, atau relay Firebase. Tidak ada pengecualian karena alasan riset, smoke test, kesiapan komponen, atau single-use approval.

Public tetap boleh mempelajari dokumentasi API yang sudah terbuka, merancang test strategy, dan menguji mock/fixture Public yang tidak terhubung atau memanggil layanan Firebase. Pengujian penyambungan komponen memakai dummy mandiri sesuai §5, tanpa Firebase.

Mode `connection-only` dan `candidate-preflight`, bila tersedia, tetap hanya di Private dan mengikuti policy Private; keduanya tidak menjadi final test atau izin submit matrix. Final Firebase test memakai APK candidate Private yang sudah dibangun, ditandatangani, dan diverifikasi, serta memerlukan persetujuan eksplisit satu attempt sesuai policy. Persetujuan final Private tidak membuka jalur Public.

```text
FIREBASE_EXECUTION_BOUNDARY = PRIVATE_ONLY
PUBLIC_FIREBASE_ACCESS = 0
PUBLIC_FIREBASE_EXECUTION = 0
PUBLIC_FIREBASE_DUMMY_EXCEPTION = 0
```

## 10. Larangan Trial-and-Error di Private

**DILARANG KERAS melakukan trial-and-error berulang di Private.**

Jika kegagalan final/integrasi terjadi di Private:

`STOP -> ROLLBACK bila diperlukan -> SANITIZED_FAILURE_REPORT -> PUBLIC -> FIX -> RETEST -> READY_PRIVATE -> PRIVATE`

Dilarang pola:

`Private gagal -> edit berulang di Private -> build lagi -> gagal -> edit lagi`.

Private dipakai untuk final processing dengan sesedikit mungkin iterasi. Public menghabiskan iterasi pengembangan.

## 11. Sanitized Failure Report

Informasi yang keluar dari Private ke Public hanya boleh berupa laporan aman, misalnya error ID, contract mismatch, unsupported version, lifecycle failure, dependency mismatch, atau generic validation result.

Laporan dilarang membawa source/asset Private, secret, token, konfigurasi internal, path sensitif, dump internal, database/state, APK/artifact Private, atau detail kernel yang membuka isi Private.

## 12. Auto Cleanup Public

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

## 13. Isolasi Project dan Shared Component

Setiap pekerjaan harus memiliki minimal `PROJECT_ID`, target master, component ID/version, contract, target platform, dan compatibility.

Dilarang mengambil isi repo/project lain hanya karena terlihat cocok.

Komponen lintas project harus dinyatakan eksplisit sebagai `GLOBAL/SHARED_COMPONENT` dan tetap memiliki source resmi, version, contract, dependency, compatibility, dan test.

## 14. Dependency dan Environment Lock

Public dan Private harus memakai versi toolchain/dependency yang dikunci dan sedekat mungkin untuk aspek yang mempengaruhi hasil. Perbedaan environment harus terdeteksi sebelum final processing.

## 15. Jalur Resmi

Public:

`RESEARCH -> DESIGN -> BUILD_COMPONENT -> AUDIT -> TEST -> SIMULATOR -> PACKAGE -> READY_PRIVATE -> AUTO_CLEANUP`

Private:

`PREFLIGHT -> SNAPSHOT -> INTEGRATE -> REGRESSION -> VERIFY -> COMMIT -> BUILD_APK -> SIGN_CANDIDATE -> VERIFY_SIGNATURE -> FIREBASE/FINAL_RUNTIME_TEST -> PASS -> RELEASE`

Jika gagal di Private:

`STOP -> ROLLBACK bila diperlukan -> SANITIZED_FAILURE_REPORT -> PUBLIC`

## 16. Larangan Sistem

Dilarang menyediakan atau menggunakan:

- checkout Private dari Public
- token/credential Public untuk membaca Private
- mirror kernel/source/asset/artifact Private di Public
- registry Public yang menyimpan isi Private
- Public runner/workflow sebagai mesin build/test untuk isi Private
- Public sebagai jalur pengecekan/akses/eksekusi Firebase/Test Lab, termasuk untuk dummy/prototype Public
- transfer bebas antar project
- debug/trial-and-error berulang di Private
- log yang membocorkan data Private
- ketergantungan Public pada isi Private

## 17. Konteks Percakapan Project

Perintah `Buka [Nama Project]` mengaktifkan hanya konteks project tersebut. Asset, keputusan, masalah, dan pembahasan project lain tidak boleh ikut terbawa.

Pertanyaan umum wajib dijawab sebagai pertanyaan umum dan tidak otomatis dikaitkan dengan project/repo/aplikasi lama.

Prioritas konteks:

`Pesan pengguna saat ini -> Project yang secara eksplisit dibuka -> Konteks umum`.

## 18. Prinsip Final

- Private Master selalu menjadi sumber kebenaran final.
- Isi Private tidak pernah keluar ke Public.
- Public menghabiskan iterasi pengembangan; Private melakukan final processing dengan iterasi minimum.
- Integrasi baseline sebenarnya, build APK final, signing kandidat final, final runtime test, dan release berada pada mesin Private; seluruh akses/eksekusi Firebase/Test Lab juga hanya di Private.
- Kegagalan Private wajib kembali ke Public melalui laporan yang sudah disanitasi bila perbaikan komponen diperlukan.
- Asset dan pembahasan antar project wajib terisolasi.
- Pekerjaan manual berulang yang dapat diotomatisasi wajib diotomatisasi.
