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

### 5.1 Sasaran Wajib: Public Build-Ready, Private Wiring-Only

Untuk setiap tahap pembangunan, sasaran operasional wajib adalah:

```text
PUBLIC = IMPLEMENTASI + ITERASI + DUMMY INTEGRATION + FULL PUBLIC ASSEMBLY REHEARSAL + BUILD-READY PACKAGE
PRIVATE = VALIDATE + MECHANICAL WIRING + BUILD + SIGN + VERIFY + FINAL TEST
```

Private **bukan** tempat membuat algoritma, adapter produksi baru, kebijakan resource baru, dependency baru, lifecycle baru, recovery logic baru, Safe UI implementation baru, atau mencari cara menyambungkan komponen. Seluruh keputusan dan implementasi yang dapat dibuktikan tanpa isi Private wajib selesai di Public.

Sebelum `STAGE_READY_PRIVATE`, invariant berikut wajib benar untuk scope tahap:

```text
PRIVATE_IMPLEMENTATION_REQUIRED = FALSE
PRIVATE_LOGIC_CHANGE_REQUIRED = FALSE
PRIVATE_DESIGN_DECISION_REQUIRED = FALSE
PRIVATE_DEPENDENCY_DECISION_REQUIRED = FALSE
PRIVATE_MANUAL_PATCH_REQUIRED = FALSE
PRIVATE_WIRING_ONLY = TRUE
```

Jika salah satu tidak dapat dibuktikan, tahap tetap `NOT_PROVEN` dan dilarang membuka attempt Private.

### 5.2 Safe Private Receiver Contract

Setiap baseline Private yang akan menerima Promotion Package wajib mempunyai **receiver contract aman** yang cukup untuk Public mengembangkan dan menguji sambungan tanpa mengetahui isi Private.

Receiver contract hanya boleh memuat informasi minimum yang diperlukan, misalnya:

- stable receiver/slot ID;
- contract/interface version;
- required/optional status;
- input/output shape yang aman;
- lifecycle/startup ordering yang memang perlu diketahui consumer;
- compatibility range;
- dependency class/requirement yang aman;
- generic failure/error code;
- acceptance schema version yang diperlukan untuk handoff.

Receiver contract **dilarang** memuat source Private, path internal, nama class internal yang tidak perlu, algoritma, state, asset, secret, database, internal topology, internal dependency yang sensitif, atau detail lain yang tidak diperlukan untuk wiring.

Gunakan ID generik/opaque bila nama semantik dapat membuka rancangan internal. Prinsipnya:

```text
PUBLIC_KNOWS_SOCKET_SHAPE = MINIMUM_REQUIRED
PUBLIC_KNOWS_PRIVATE_IMPLEMENTATION = 0
```

Contract aman harus diperlakukan sebagai boundary tersendiri. Dilarang membuatnya dengan pola `copy Private -> sensor/redact -> publish`. Contract harus dirancang sebagai abstraksi boundary dari awal.

### 5.3 Private Receiver Adapter Tetap Private

Mapping dari receiver/slot ID aman ke path, class, object, registry, state, atau struktur internal yang sebenarnya tetap berada di Private sebagai **Private Receiver Adapter** atau mekanisme ekuivalen.

Public tidak boleh mengetahui mapping internal tersebut.

Private Receiver Adapter harus sudah tersedia dan compatible sebelum tahap Public dinyatakan siap. Jika Private masih harus menciptakan implementation receiver baru ketika Promotion Package tiba, maka `PRIVATE_IMPLEMENTATION_REQUIRED = TRUE` dan `STAGE_READY_PRIVATE` batal.

Perubahan receiver internal yang tidak mengubah public-safe contract boleh tetap Private. Perubahan yang mengubah shape/version contract wajib memperbarui contract aman sebelum Public closure tahap.

### 5.4 Dummy Private Host Wajib dan Harus Independen

Public wajib mempunyai `DUMMY_PRIVATE_HOST`/surrogate ekuivalen yang mengimplementasikan receiver contract aman secara independen.

Dummy harus:

- dibuat hanya dari contract/interface aman;
- tidak dibuat dari source, APK, state, asset, config, dump, reflection output, decompilation, atau observasi internal Private;
- tidak menyalin nama/path/topology internal yang tidak dibutuhkan contract;
- menyediakan seluruh slot yang diperlukan tahap;
- meniru lifecycle/startup/failure surface yang dideklarasikan contract;
- memungkinkan exact production stage package dipasang tanpa perubahan logic.

Dummy boleh berbeda implementasi internal dari Private. Yang wajib sama adalah **shape contract, version, ordering, failure surface, dan wiring semantics yang dipublikasikan**.

### 5.5 Machine-Readable Stage Wiring Manifest

Setiap Promotion Package tahap wajib membawa `STAGE_WIRING_MANIFEST` atau format mesin ekuivalen.

Manifest minimal mengikat:

- `PROJECT_ID` dan `STAGE_ID`;
- exact package/member/source/asset/build-descriptor hashes;
- receiver contract ID/version;
- mapping **public slot ID -> promoted provider ID**;
- registry binding yang diperlukan;
- module registration dan dependency graph yang diperlukan;
- startup/lifecycle ordering;
- state-store/recovery/Safe-UI/resource/diagnostic binding bila berlaku;
- manifest/resource/shrinker/build requirements yang aman dan diperlukan;
- compatibility constraints;
- expected negative/fail-closed behavior;
- handoff/acceptance schema version.

Manifest harus dapat diterapkan secara deterministic oleh tooling/dummy harness. Instruksi naratif seperti “buat adapter”, “cari tempat bootstrap”, atau “sesuaikan sampai compile” tidak cukup untuk `STAGE_READY_PRIVATE`.

Private boleh melakukan perubahan **mekanis** yang dihasilkan dari manifest terhadap receiver adapter/local wiring layer, tetapi dilarang menambahkan behavior produk baru. Jika wiring membutuhkan keputusan desain atau coding behavior baru, STOP dan kembali Public.

### 5.6 Full Public Assembly Rehearsal Wajib

Sebelum `STAGE_READY_PRIVATE`, Public wajib menjalankan rehearsal dari workspace bersih:

```text
FRESH PUBLIC WORKSPACE
-> LOAD DUMMY_PRIVATE_HOST
-> VERIFY SAFE RECEIVER CONTRACT
-> LOAD EXACT PROMOTION PACKAGE
-> APPLY STAGE_WIRING_MANIFEST AUTOMATICALLY/DETERMINISTICALLY
-> VERIFY NO MANUAL FIX
-> ASSEMBLE FULL PUBLIC DUMMY APPLICATION
-> INSTALL/RUN ON DECLARED DEVELOPMENT TARGET
-> VERIFY STARTUP + REGISTRY + ROUTES + STATE/LIFECYCLE + FAILURE/RECOVERY + SAFE MODE/UI AS APPLICABLE
-> RESTART/PROCESS-DEATH TEST AS APPLICABLE
-> NEGATIVE/MUTATION TEST AS APPLICABLE
-> PACKAGE/PROVENANCE VALIDATION
-> PASS
```

Full dummy application hanya terdiri dari material Public + dummy receiver. Ia **bukan aplikasi final**, tidak boleh memakai identitas/asset/state/secret Private, dan tidak boleh disebut final runtime proof.

Untuk Android, Public wajib membangun APK dummy/prototype lengkap dengan production stage sources yang exact terhadap Promotion Package dan mengujinya pada API/target development yang dideklarasikan. Jika environment Public tidak sama dengan final ABI/device, perbedaan itu harus eksplisit; misalnya x86_64 API30 witness tidak boleh diklaim sebagai final arm64 proof.

Public Firebase/Test Lab tetap dilarang menurut §9.1.

Rehearsal harus fail jika sesudah package diterapkan diperlukan edit manual, hotfix, implementasi baru, dependency decision baru, atau perubahan source produksi.

Invariant closure:

```text
PUBLIC_FULL_ASSEMBLY_REHEARSAL = PASS
PROMOTED_PRODUCTION_SOURCE_CHANGED_AFTER_REHEARSAL = 0
MANUAL_FIX_AFTER_PACKAGE_APPLY = 0
DUMMY_HOST_DERIVED_FROM_PRIVATE = 0
PRIVATE_CONTENT_USED_BY_REHEARSAL = 0
```

### 5.7 Build-Ready Promotion Package

Promotion Package tahap harus **assembly-ready/build-ready untuk receiver contract**, bukan sekadar source-ready.

Selain source/asset produksi yang memang dipromosikan, paket harus membawa seluruh material Public yang diperlukan agar integrasi tidak membutuhkan keputusan baru di Private, termasuk bila berlaku:

- module/build descriptors;
- dependency declarations/locks/digests;
- registry descriptors;
- schema/contract versions;
- lifecycle/startup binding declarations;
- manifest requirements;
- resource/shrinker/ProGuard/R8 requirements;
- compatibility matrix;
- wiring manifest;
- acceptance/handoff schema version;
- test/provenance bindings.

Input yang memang Private-only seperti secret/signing material, internal state, private receiver mapping, dan final baseline tidak boleh masuk Promotion Package.

### 5.8 Public Handoff Acceptance Contract

Private wajib menyediakan **acceptance contract aman** untuk bagian handoff yang harus dibentuk oleh Public. Contract ini hanya mendeskripsikan schema/field/type/version/status yang boleh dan wajib, bukan nilai atau struktur internal Private.

Public wajib memvalidasi Promotion Package, wiring manifest, dependency handoff record, dan metadata lain yang akan dibaca Private terhadap acceptance contract tersebut sebelum `STAGE_READY_PRIVATE`.

Jika active Private verifier memerlukan field public-addressable yang tidak tercakup acceptance contract, contract dianggap stale dan closure tahap batal sampai diperbaiki. Ini mencegah kegagalan Private hanya karena schema/record incompatibility yang seharusnya dapat diketahui di Public.

### 5.9 No-Private-Implementation Gate

Sebelum promosi dan kembali saat Private preflight, wajib ada pemeriksaan fail-closed yang membuktikan:

```text
NEW_PRODUCT_ALGORITHM_IN_PRIVATE = 0
NEW_PRODUCTION_ADAPTER_IN_PRIVATE = 0
NEW_PRODUCT_DEPENDENCY_DECISION_IN_PRIVATE = 0
NEW_RESOURCE_POLICY_IN_PRIVATE = 0
NEW_RECOVERY_BEHAVIOR_IN_PRIVATE = 0
NEW_UI_BEHAVIOR_IN_PRIVATE = 0
UNDECLARED_STARTUP_DECISION_IN_PRIVATE = 0
UNDECLARED_REGISTRY_DECISION_IN_PRIVATE = 0
PRIVATE_WIRING_ONLY = TRUE
```

Kategori perubahan Private yang diperbolehkan selama integrasi tahap hanya perubahan mekanis yang sudah dideklarasikan, misalnya package placement, module registration, slot/provider binding, startup binding, thin delegation tanpa semantic baru, build-graph connection, dan provenance/evidence.

Jika diff/operasi keluar dari kategori yang dideklarasikan, status menjadi `PRIVATE_DEVELOPMENT_DETECTED` -> STOP -> rollback bila perlu -> sanitized report -> Public.

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
7. Safe Private Receiver Contract tersedia dan cukup tanpa membocorkan implementation Private.
8. Dummy Private Host conform terhadap receiver contract dan dibangun independen dari Private.
9. Machine-readable Stage Wiring Manifest lengkap dan deterministic.
10. Full Public Assembly Rehearsal dari workspace bersih PASS tanpa manual fix setelah package apply.
11. Public Handoff Acceptance Contract/schema compatibility PASS untuk seluruh record yang harus dibaca Private.
12. Promotion Package build-ready sesuai §5.7 dan `NO_PRIVATE_IMPLEMENTATION` gate PASS.

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

Promotion Package juga wajib membawa receiver-contract binding, machine-readable Stage Wiring Manifest, acceptance/handoff schema version, build descriptors/dependency declarations yang diperlukan, serta evidence Full Public Assembly Rehearsal dan `NO_PRIVATE_IMPLEMENTATION` gate.

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

Private wajib menolak paket yang identitas, hash, contract, dependency, compatibility, receiver-contract version, wiring manifest, atau acceptance schema-nya tidak valid.

## 8. Private Preflight dan Transaction

Hanya setelah closure tahap dan izin eksekusi pada §6, Private menjalankan preflight murah terhadap kelengkapan tahap, package/manifest/hash/contract/dependency/compatibility/environment. Preflight termasuk biaya attempt, bukan alasan menjalankan satu attempt per komponen.

Private preflight wajib memverifikasi bahwa seluruh wiring dapat dilakukan melalui receiver adapter/slot yang sudah ada dan Stage Wiring Manifest yang exact. Jika diperlukan implementation, design decision, dependency decision, manual patch, atau behavior baru, **STOP sebelum integrasi/build**.

Jika preflight gagal: **STOP**. Jangan menjalankan build/integrasi berat.

Sebelum integrasi:

`CURRENT_FINAL -> SNAPSHOT -> INTEGRATE -> VERIFY`

Jika PASS: `COMMIT_NEW_FINAL_STATE`.

Jika FAIL: `ROLLBACK` ke state sebelumnya.

## 9. Private Execution Machine

Mesin Private adalah satu-satunya jalur yang boleh mengeksekusi isi Private untuk final processing.

Jalur final resmi:

`PREFLIGHT -> SNAPSHOT -> APPLY_DECLARED_WIRING -> VERIFY_WIRING -> REGRESSION -> COMMIT -> BUILD_APK -> SIGN_CANDIDATE -> VERIFY_SIGNATURE -> FIREBASE/FINAL_RUNTIME_TEST -> PASS -> RELEASE`

Aturan:

- build APK yang memakai source/asset Private wajib berjalan pada boundary Private;
- signing candidate wajib berjalan pada boundary Private;
- seluruh akses operasional Firebase/Test Lab dan final runtime execution terhadap APK kandidat wajib dimulai serta dijalankan dari boundary Private;
- APK yang diuji final harus merupakan candidate yang sudah ditandatangani dan diverifikasi signature-nya;
- release hanya boleh memakai artifact kandidat yang identitas/hash/signature-nya sama dengan artifact yang memperoleh final PASS;
- Public tidak boleh menjadi bridge, reusable runner, caller target, artifact relay, atau CI engine untuk mengeksekusi isi Private;
- Private integration tidak boleh menjadi tempat menulis behavior produk yang seharusnya sudah matang di Public.

GitHub Actions boleh digunakan sebagai mesin Private jika workflow dan seluruh input/output Private tetap berada pada repository/jalur Private dan tidak disalurkan ke Public.

### 9.1 Firebase / Test Lab Hanya di Private

**Public DILARANG melakukan pengecekan, mengakses layanan, atau menjalankan pengujian Firebase/Test Lab dalam bentuk apa pun, termasuk dengan dummy, prototype, atau artifact yang sepenuhnya Public.**

Larangan mencakup connection check, autentikasi ke layanan, pembacaan catalog/model, candidate preflight yang mengakses Firebase, upload/download artifact atau hasil, submit test matrix, serta penggunaan Public sebagai caller, executor, atau relay Firebase. Tidak ada pengecualian karena alasan riset, smoke test, kesiapan komponen, atau single-use approval.

Public tetap boleh mempelajari dokumentasi API yang sudah terbuka, merancang test strategy, dan menguji mock/fixture Public yang tidak terhubung atau memanggil layanan Firebase. Pengujian penyambungan komponen memakai dummy mandiri sesuai §5, tanpa Firebase.

Mode `connection-only` dan `candidate-preflight`, bila tersedia, tetap hanya di Private dan mengikuti policy Private; keduanya tidak menjadi final test atau izin submit matrix. Final Firebase test memakai APK candidate Private yang sudah dibangun, ditandatangani, dan diverifikasi signature-nya, serta memerlukan persetujuan eksplisit satu attempt sesuai policy. Persetujuan final Private tidak membuka jalur Public.

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

Informasi yang keluar dari Private ke Public hanya boleh berupa laporan aman, misalnya error ID, contract mismatch, unsupported version, lifecycle failure, dependency mismatch, receiver-slot mismatch, acceptance-schema mismatch, atau generic validation result.

Laporan dilarang membawa source/asset Private, secret, token, konfigurasi internal, path sensitif, dump internal, database/state, APK/artifact Private, internal receiver mapping, atau detail kernel yang membuka isi Private.

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

Dependency/toolchain qualification yang diperlukan untuk Promotion Package harus diselesaikan atau dinyatakan sebagai prasyarat Private yang sudah tersedia sebelum `STAGE_READY_PRIVATE`. Private tidak boleh digunakan untuk memilih dependency baru secara trial-and-error.

## 15. Jalur Resmi

Public:

`RESEARCH -> DESIGN -> BUILD_COMPONENT -> AUDIT/TEST -> DUMMY_PRIVATE_HOST -> APPLY_WIRING_MANIFEST -> FULL_PUBLIC_ASSEMBLY_REHEARSAL -> PACKAGE_VALIDATION -> COMPONENT_READY_PRIVATE -> TUTUP SELURUH TAHAP -> STAGE_READY_PRIVATE`

Auto Cleanup berlaku per job setelah paket/evidence wajib dipertahankan sesuai §12. Tidak membuka promosi komponen.

Private:

`PREFLIGHT -> SNAPSHOT -> APPLY_DECLARED_WIRING -> VERIFY_WIRING -> REGRESSION -> COMMIT -> BUILD_APK -> SIGN_CANDIDATE -> VERIFY_SIGNATURE -> FIREBASE/FINAL_RUNTIME_TEST -> PASS -> RELEASE`

Jika gagal di Private:

`STOP -> ROLLBACK bila diperlukan -> SANITIZED_FAILURE_REPORT -> PUBLIC`

## 16. Larangan Sistem

Dilarang menyediakan atau menggunakan:

- checkout Private dari Public
- token/credential Public untuk membaca Private
- mirror kernel/source/asset/artifact Private di Public
- registry Public yang menyimpan isi Private
- contract Public yang mengungkap internal receiver mapping/path/class/topology yang tidak diperlukan
- dummy yang diturunkan dari salinan/redaksi/dekompilasi/observasi isi Private
- Public runner/workflow sebagai mesin build/test untuk isi Private
- Public sebagai jalur pengecekan/akses/eksekusi Firebase/Test Lab, termasuk untuk dummy/prototype Public
- transfer bebas antar project
- debug/trial-and-error berulang di Private
- implementasi behavior produk baru di Private ketika tahap mengklaim `PRIVATE_WIRING_ONLY`
- manual patch sesudah Promotion Package diterapkan untuk membuatnya compile/build
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
- Public hanya mengetahui contract/socket minimum yang diperlukan untuk membuat komponen dapat disambungkan; internal receiver mapping tetap Private.
- Public menghabiskan seluruh implementasi dan iterasi yang dapat dilakukan tanpa isi Private, termasuk dummy host, deterministic wiring rehearsal, dan full public dummy build.
- `STAGE_READY_PRIVATE` hanya sah bila package build-ready terhadap receiver contract dan tidak memerlukan implementation/design/manual patch baru di Private.
- Satu tahap utuh menjadi satu batas promosi dan satu percobaan Private terencana sesuai §6.3.
- Integrasi baseline sebenarnya, build APK final, signing kandidat final, final runtime test, dan release berada pada mesin Private; seluruh akses/eksekusi Firebase/Test Lab juga hanya di Private.
- Kegagalan Private wajib kembali ke Public melalui laporan yang sudah disanitasi bila perbaikan komponen diperlukan.
- Asset dan pembahasan antar project wajib terisolasi.
- Pekerjaan manual berulang yang dapat diotomatisasi wajib diotomatisasi.
