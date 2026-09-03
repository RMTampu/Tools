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

Batas akhir pekerjaan Public adalah kesiapan **satu tahap pembangunan utuh**: `STAGE_READY_PRIVATE`. Komponen dapat lebih dahulu mencapai `COMPONENT_READY_PRIVATE`, tetapi tetap ditahan di Public sampai seluruh tahap siap. Definisi dan syaratnya di §6; keduanya bukan integrasi sebenarnya atau aplikasi final PASS.

## 4. Pertumbuhan Bertahap

Project dapat berkembang bertahap:

`A -> A+B -> A+B+C -> A+B+C+D -> ...`

A/B/C/D pada pola tersebut adalah tahap pembangunan utuh sesuai peta pengguna. Seluruh komponen baru dalam satu tahap dimatangkan bersama di Public tanpa melihat isi Private; setelah `STAGE_READY_PRIVATE` dan otorisasi yang berlaku, tahap dipromosikan sebagai satu kesatuan untuk satu percobaan Private terencana. Sublangkah seperti A1/A2/A3/A4 bukan batas promosi. Baseline dan proof yang masih sah dipakai kembali; jangan membangun ulang bagian yang telah terbukti.

## 5. Contract sebagai Jembatan

Public dan Private dihubungkan oleh contract aman, bukan oleh isi Private.

Setiap contract harus memiliki minimal `CONTRACT_ID`, `VERSION`, `COMPATIBILITY`, lifecycle/input-output yang diperlukan, dependency requirement, dan error code aman.

Untuk pengujian penyambungan di Public, baseline APK/state final Private WAJIB digantikan oleh dummy/mock/simulator mandiri yang dibuat dari contract/interface yang sudah dinyatakan aman untuk Public.

Dummy tersebut bukan APK baseline, bukan salinan kernel, dan bukan hasil mengekstrak, menyamarkan, mengganti nama, atau menyunting source/asset/config/state/APK/artifact Private. Isi Private tidak boleh diambil ke Public terlebih dahulu untuk kemudian disanitasi.

Jika contract aman belum cukup untuk suatu pengujian, catat gap sebagai `NOT_PROVEN`; jangan mengambil isi Private atau mengklaim dummy sebagai bukti integrasi/runtime final.

## 6. Jalur Kematangan Komponen

Untuk mencapai kesiapan komponen di Public, setiap komponen wajib melewati:

`SPEC -> CONTRACT -> DEPENDENCY -> UNIT_TEST -> SIMULATOR -> FAILURE_TEST -> PACKAGE_VALIDATION -> COMPONENT_READY_PRIVATE`

Kesiapan komponen saja tidak pernah mengizinkan integrasi Private.

### 6.1 Kesiapan Komponen Tidak Sama dengan Kesiapan Tahap

| Status | Makna | Izin masuk Private |
| --- | --- | --- |
| `COMPONENT_READY_PRIVATE` | Seluruh proof wajib komponen pada scope Public selesai; komponen ditahan di Public menunggu tahap lengkap. | Tidak mengizinkan promosi, integrasi, build, atau test Private tersendiri. |
| `STAGE_READY_PRIVATE` | Seluruh cakupan tahap yang ditetapkan pengguna sudah ditutup menurut §6.2. | Syarat kesiapan satu tahap utuh; tetap memerlukan otorisasi dan gate yang berlaku. |

Status lama `READY_PRIVATE` pada output/manifest komponen hanya dibaca sebagai kesiapan komponen, bukan izin Private. Jangan mengganti nama field/status mesin tanpa memeriksa consumer; jangan menaikkan status lama menjadi kesiapan tahap secara otomatis. Alias seperti `STAGE_A_READY_PRIVATE` sah sebagai kesiapan tahap A hanya jika identitas, cakupan, dan bukti tahap memenuhi §6.2; nama artifact atau teks PASS saja tidak cukup.

### 6.2 Closure Tahap Utuh Sebelum Private

Agen wajib memastikan:

1. Identitas tahap dan seluruh requirement/sublangkah/komponen sesuai peta yang disetujui. Tidak boleh memperkecil cakupan agar paket parsial disebut satu tahap.
2. Seluruh kontrak sambungan, versi, registry route, dependency, urutan/lifecycle, penanganan gagal, acceptance, dan batas resource yang diperlukan telah jelas. Keputusan teknis yang belum ditetapkan adalah blocker, bukan ruang untuk asumsi.
3. Seluruh proof wajib Public, termasuk interaksi antar-komponen satu tahap, R1–R9 yang berlaku, asset/route proof, negative test, dan validasi paket sudah lengkap pada input yang terikat. N/A memerlukan alasan berbasis scope, bukan penghematan kuota.
4. Promotion manifest tahap mengikat seluruh paket anggota dan evidence; paket parsial tidak membuka promosi.
5. Penerima Private meninjau bukti prasyarat baseline/adapter/toolchain/trust yang masih sah tanpa mengekspor isinya. Bila kualifikasi baru diperlukan, nyatakan kebutuhan serta biayanya sebelum eksekusi; jangan menyembunyikannya sebagai persiapan gratis.
6. Rencana satu attempt mencakup urutan integrasi, gate murah sebelum mahal, regression/build/signing/final test yang diperlukan, pemulihan, batas biaya/durasi, dan otorisasi yang berlaku.

Pisahkan **prasyarat masuk yang harus sudah terbukti** dari **witness integrasi/runtime final yang baru dihasilkan pada attempt Private**. Witness final tersebut tetap pending/NOT_PROVEN sampai dijalankan; bukan syarat PASS melingkar untuk memasuki Private, dan bukan alasan menunda test yang sebenarnya bisa ditutup di Public. `STAGE_READY_PRIVATE` bukan final application PASS.

### 6.3 Satu Tahap, Satu Percobaan Private Terencana

Satu tahap utuh adalah satu batas promosi/integrasi. Komponen, sublangkah, gate, sub-gate, individual check, jumlah paket, pergantian agen, atau pergantian konteks tidak menambah jatah attempt.

Sebelum dispatch, agen wajib menunjukkan stage ID, scope/package binding, prasyarat, daftar operasi/workflow/job, bukti yang dipakai ulang, estimasi total menit/biaya termasuk layanan dan penyimpanan, batas durasi, titik STOP, serta izin eksekusi tahap yang sesuai instruksi pengguna. Izin menyunting MD tidak mengizinkan eksekusi Private.

Catat satu attempt ID beserta scope/input binding dan status belum dimulai/berjalan/menunggu approval/selesai/gagal/tidak diketahui. Cegah dispatch ganda dan pekerjaan bersamaan untuk attempt yang sama; bila status dispatch tidak pasti, periksa run yang ada, jangan mengirim ulang. Semua job, preflight, bootstrap, kualifikasi, build, dan verifikasi Private dihitung sebagai penggunaan nyata; memindahkannya ke workflow lain tidak membuatnya gratis atau membuka attempt baru.

Satu attempt adalah satu rencana eksekusi tahap, bukan janji satu command/workflow. Jangan memanggil workflow gate terpisah jika gate yang sama sudah tercakup secara sah dalam jalur kandidat. Jangan mengurangi proof untuk menghemat kuota. Jika kuota atau prasyarat tidak cukup, STOP dan sampaikan pilihan sebelum memakai Private.

Checkpoint persetujuan Firebase terhadap signed candidate tetap wajib. Menunggu persetujuan tidak mengizinkan rebuild, mengganti kandidat, atau mengulang pekerjaan yang sudah sah. Approval Firebase satu kali tetap terpisah dari izin integrasi/build tahap.

Kegagalan/timeout/cancellation setelah pekerjaan Private dimulai tidak mengembalikan jatah attempt. Lakukan STOP dan pemulihan aman yang telah diotorisasi. Perbaikan Public harus menutup ulang cakupan tahap terdampak; attempt Private berikutnya memerlukan keputusan/izin baru, bukan auto-retry.

Targetnya keberhasilan percobaan pertama. “100%” hanya berarti kelengkapan terhadap scope dan batas bukti yang disepakati, bukan jaminan bebas bug.

## 7. Promotion Package

Promotion Package tahap memuat `PROJECT_ID`, `STAGE_ID`, versi cakupan tahap, daftar seluruh sublangkah/komponen wajib, daftar paket anggota beserta hash, contract/route/test-evidence binding, hasil closure Public, dan batas claim. Metadata Private seperti baseline internal dan bukti penerima disimpan pada catatan penerimaan Private, tidak disalin ke Public.

Setiap paket anggota tetap minimal membawa metadata aman:

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

Hanya setelah closure tahap dan izin eksekusi pada §6, Private menjalankan preflight murah terhadap kelengkapan tahap, package/manifest/hash/contract/dependency/compatibility/environment. Preflight termasuk biaya attempt, bukan alasan menjalankan satu attempt per komponen.

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

`STOP -> ROLLBACK bila diperlukan -> SANITIZED_FAILURE_REPORT -> PUBLIC FIX/RETEST -> TUTUP ULANG TAHAP TERDAMPAK -> STAGE_READY_PRIVATE -> TUNGGU KEPUTUSAN/IZIN ATTEMPT BARU`

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

Sebelum menghapus output sementara, pastikan paket tahap dan evidence Public yang diwajibkan telah tersimpan secara tahan lama dan dapat diverifikasi sesuai retention yang disetujui. Paket/evidence wajib bukan sampah job; jangan menghapus satu-satunya bukti atau memaksa rerun akibat cleanup.

Cleanup tidak menggantikan aturan keamanan. Data Private tidak boleh pernah masuk Public sejak awal.

## 13. Isolasi Project dan Shared Component

Setiap pekerjaan harus memiliki minimal `PROJECT_ID`, target master, component ID/version, contract, target platform, dan compatibility.

Dilarang mengambil isi repo/project lain hanya karena terlihat cocok.

Komponen lintas project harus dinyatakan eksplisit sebagai `GLOBAL/SHARED_COMPONENT` dan tetap memiliki source resmi, version, contract, dependency, compatibility, dan test.

## 14. Dependency dan Environment Lock

Public dan Private wajib mengunci seluruh input yang memengaruhi hasil serta membuktikan kompatibilitas aspek bersama melalui contract. Daftar perbedaan environment harus eksplisit; kemiripan environment bukan proof kesetaraan. Ikuti R6 untuk seluruh fase/perintah dan dependency, serta R9 untuk binding/freshness evidence. Bukti dan konfigurasi Private tetap di Private.

## 15. Jalur Resmi

Public:

`RESEARCH -> DESIGN -> BUILD_COMPONENT -> AUDIT/TEST/SIMULATOR -> PACKAGE -> COMPONENT_READY_PRIVATE -> TUTUP SELURUH TAHAP -> STAGE_READY_PRIVATE`

Auto Cleanup berlaku per job setelah paket/evidence wajib dipertahankan sesuai §12. Tidak membuka promosi komponen.

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
- Public menghabiskan iterasi pengembangan; satu tahap utuh menjadi satu batas promosi dan satu percobaan Private terencana sesuai §6.3.
- Integrasi baseline sebenarnya, build APK final, signing kandidat final, final runtime test, dan release berada pada mesin Private; seluruh akses/eksekusi Firebase/Test Lab juga hanya di Private.
- Kegagalan Private wajib kembali ke Public melalui laporan yang sudah disanitasi bila perbaikan komponen diperlukan.
- Asset dan pembahasan antar project wajib terisolasi.
- Pekerjaan manual berulang yang dapat diotomatisasi wajib diotomatisasi.
