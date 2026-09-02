# AGENTS.md — Public Research / Test Staging

## RULE 0 — WAJIB DIBACA PERTAMA

Sebelum membaca aturan repository lain, sebelum membuka file pekerjaan, dan sebelum melakukan perubahan apa pun, agen WAJIB membaca:

`GLOBAL_PUBLIC_PRIVATE_DEVELOPMENT_RULES.md`

Aturan global tersebut adalah aturan induk lintas project. Jika aturan lama di repository ini bertentangan dengannya, **aturan global menang** kecuali instruksi pengguna terbaru secara eksplisit mengubahnya.

Invariant terpenting:

```text
PRIVATE_CONTENT_TO_PUBLIC = FORBIDDEN
PUBLIC = RESEARCH / ITERATION / STAGING
PRIVATE = MASTER / VAULT / FINAL PROCESSING / FINAL EXECUTION
PUBLIC_PRIVATE_READ_ACCESS = FORBIDDEN
PRIVATE_EXECUTION_THROUGH_PUBLIC = FORBIDDEN
PUBLIC_JOB_AUTO_CLEANUP = REQUIRED
```

## 1. Wajib Dibaca Sebelum Bekerja

Setiap agen yang membaca, mengubah, membangun, menguji, memvalidasi, mengaudit, atau membuat workflow di `RMTampu/Tools` WAJIB membaca `AGENTS.md` dan Rule 0 terlebih dahulu.

Aturan safety/gate lama tetap berlaku hanya jika tidak bertentangan dengan Rule 0.

## 2. Identitas Repository

`RMTampu/Tools` adalah **Public Research / Test / Staging Repository**.

Repository ini bukan Private Master project mana pun dan bukan tempat final processing aplikasi Private.

Untuk ToolBox saat ini:

```text
RMTampu/ToolBox (PRIVATE) = PRIVATE MASTER + FINAL EXECUTION
RMTampu/Tools (PUBLIC)    = RESEARCH / TEST / STAGING
RMTampu/Backup (PRIVATE)  = BACKUP ROLE bila digunakan
```

Aturan global harus tetap dapat dipakai untuk project lain tanpa bergantung pada nama ToolBox.

## 3. Larangan Akses Private

Repository Public dilarang:

- checkout repository Private;
- menerima token/credential untuk membaca Private;
- menerima source/kernel/asset/config/state Private;
- menerima APK/artifact yang mengandung isi Private;
- menjadi reusable CI yang mengeksekusi source Private;
- menjadi artifact relay/Firebase bridge untuk Private;
- menyimpan mirror/snapshot/legacy copy isi Private.

Semua workflow lama yang memerlukan source/artifact Private di Public **tidak boleh digunakan** dan harus dianggap legacy/incompatible dengan Rule 0.

## 4. Fungsi Public yang Diizinkan

Public digunakan untuk:

- riset dan desain;
- prototype;
- pengembangan komponen baru;
- unit/contract/dependency/failure test;
- mock/simulator/test harness;
- audit dan debugging komponen Public;
- packaging Promotion Package;
- staging hingga `READY_PRIVATE`.

Public hanya memakai contract/interface yang memang aman dipublikasikan, fixture/dummy data, mock/simulator, dan komponen yang sedang dikembangkan di Public.

## 5. Jalur Kematangan Komponen

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

Setelah `READY_PRIVATE`, paket dapat dipromosikan ke Private Master. Public tidak melakukan integrasi sebenarnya terhadap state final Private.

## 6. Promotion Package

Paket yang keluar dari Public menuju Private minimal memiliki metadata aman:

- Project ID;
- Component ID/version;
- Contract version;
- dependency/toolchain lock atau digest;
- target platform;
- hash/checksum;
- compatibility;
- test status;
- promotion manifest.

Tidak boleh memasukkan data Private ke Promotion Package.

## 7. Auto Cleanup Public

Setiap pekerjaan dengan mesin Public WAJIB memiliki cleanup otomatis setelah selesai, berhasil maupun gagal, tanpa menunggu perintah pengguna.

Bersihkan sejauh platform memungkinkan:

- workflow run/log;
- artifact sementara;
- cache pekerjaan;
- workspace sementara;
- branch/ref sementara yang dibuat untuk job;
- debug output sementara;
- temporary test data.

Pipeline baru tidak boleh dianggap matang jika lifecycle cleanup belum dirancang.

## 8. Build dan Test

GitHub Actions di Public hanya boleh membangun/menguji **komponen Public, mock, simulator, fixture, atau prototype Public**.

Dilarang menggunakan Public untuk final build aplikasi yang source/asset finalnya berada di Private.

Final build, signing, signature verification, Firebase/final runtime test, dan release dilakukan di jalur Private project masing-masing.

Termux tidak digunakan sebagai lingkungan build aplikasi dan package/tool tambahan tidak boleh diinstal tanpa izin eksplisit pengguna.

## 9. Android / Target-Specific Testing

Untuk komponen yang menarget Android, environment test harus dicatat dan dependency/toolchain dikunci.

Hasil simulator/non-target tidak boleh diklaim sebagai final runtime proof. Final target-specific proof tetap tanggung jawab Private Master/final processing.

## 10. Firebase / External Test Bridge

Tidak boleh menggunakan Public sebagai jalur untuk mengirim source/asset/APK/artifact Private ke layanan eksternal.

Jika layanan eksternal digunakan untuk final verification, jalur harus dimulai dari Private/approved private processing dan tetap mengikuti authorization policy.

```text
FIREBASE DEFAULT = LOCKED
1 EXPLICIT USER APPROVAL = 1 FIREBASE EXECUTION ATTEMPT
```

Tidak boleh auto-run atau auto-retry Firebase.

## 11. Shared Component dan Isolasi Project

Dilarang mengambil file/asset/config dari project lain hanya karena terlihat cocok.

Komponen lintas project harus dinyatakan sebagai `GLOBAL/SHARED_COMPONENT` dan memiliki source resmi, version, contract, dependency, compatibility, serta test.

Pembahasan dan pekerjaan project juga harus terisolasi sesuai aturan global.

## 12. Safety / Procedure

Aturan teknis yang tersedia tetap berlaku bila relevan dan tidak bertentangan dengan Rule 0, termasuk:

- `AGENT_PROCEDURE_EXECUTION_RULES.md`;
- `PREBUILD_ASSET_GATE.md`;
- `ASSET_SAFE_100_RULES.md`;
- `ASSET_SAFE_100_METHODS.md`;
- `ASSET_SAFE_100_PROCESS.md`;
- `ASSET_ROUTE_PROOF_METHODS.md`;
- `ASSET_ROUTE_PROOF_PROCESS.md`;
- `APPLICATION_SAFE_100_PROCESS.md`;
- rule/procedure domain R1–R9 bila tersedia.

### 12.1 Scope Wajib untuk Dokumen Assurance Public

Jika dokumen R6–R9, Asset Safe, Prebuild Asset Gate, atau dokumen assurance legacy menyebut:

- final build;
- final APK/package;
- signing;
- install final;
- Firebase;
- final acceptance;
- release;

maka pada repository Public klausul tersebut hanya boleh dibaca sebagai **metode, contract, model, prototype, atau research requirement terhadap scope Public**.

Klausul tersebut **tidak pernah** memberi izin untuk membawa atau mengeksekusi isi Private di Public.

Final execution terhadap integrated Private state tetap dilakukan di Private.

Aturan legacy tidak boleh menghidupkan kembali alur Private -> Public.

## 13. Urutan Otoritas

```text
Instruksi pengguna terbaru
-> AGENTS.md RULE 0
-> GLOBAL_PUBLIC_PRIVATE_DEVELOPMENT_RULES.md
-> aturan khusus repository yang tidak bertentangan
-> REPOSITORY_INTEGRATION_POLICY.md
-> TEST_ROUTING_POLICY.md / safety procedure terkait
-> dokumentasi legacy lain
```

## 14. Invariant

```text
PUBLIC_ROLE = RESEARCH_TEST_STAGING
PRIVATE_CONTENT_IN_PUBLIC = 0
PUBLIC_PRIVATE_READ_ACCESS = 0
PRIVATE_SOURCE_BUILD_IN_PUBLIC = 0
PRIVATE_EXECUTION_THROUGH_PUBLIC = 0
PUBLIC_FINAL_PRODUCT_BUILD = 0
PUBLIC_FINAL_SIGNING = 0
PUBLIC_FIREBASE_PRIVATE_ARTIFACT = 0
PUBLIC_JOB_AUTO_CLEANUP = REQUIRED
UNAUTHORIZED_FIREBASE_RUN = 0
```
