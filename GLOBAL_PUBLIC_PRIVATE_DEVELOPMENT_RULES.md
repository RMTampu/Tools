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

Batas akhir pekerjaan Public adalah kesiapan **satu tahap pembangunan utuh**: `STAGE_READY_PRIVATE`. Komponen dapat lebih dahulu mencapai `COMPONENT_READY_PRIVATE`, tetapi tetap ditahan di Public sampai seluruh tahap siap. Keduanya bukan integrasi sebenarnya atau aplikasi final PASS.

## 4. Pertumbuhan Bertahap

Project dapat berkembang bertahap:

`A -> A+B -> A+B+C -> A+B+C+D -> ...`

A/B/C/D pada pola tersebut adalah tahap pembangunan utuh sesuai peta pengguna. Seluruh komponen baru dalam satu tahap dimatangkan bersama di Public tanpa melihat isi Private; setelah `STAGE_READY_PRIVATE` dan otorisasi yang berlaku, tahap dipromosikan sebagai satu kesatuan untuk satu percobaan Private terencana. Sublangkah seperti A1/A2/A3/A4 bukan batas promosi. Baseline dan proof yang masih sah dipakai kembali; jangan membangun ulang bagian yang telah terbukti.

## 5. Contract sebagai Jembatan

Public dan Private dihubungkan oleh contract aman, bukan oleh isi Private.

Setiap contract harus memiliki minimal `CONTRACT_ID`, `VERSION`, `COMPATIBILITY`, lifecycle/input-output yang diperlukan, dependency requirement, dan error code aman.

Untuk pengujian penyambungan di Public, baseline APK/state final Private WAJIB digantikan oleh dummy/mock/simulator mandiri yang dibuat dari contract/interface yang sudah dinyatakan aman untuk Public.

Dummy tersebut bukan APK baseline, bukan salinan kernel, dan bukan hasil mengekstrak, menyamarkan, mengganti nama, menyunting, meredaksi, mendekompilasi, atau mengobservasi source/asset/config/state/APK/artifact Private. Isi Private tidak boleh diambil ke Public terlebih dahulu untuk kemudian disanitasi.

Jika contract aman belum cukup untuk suatu pengujian, catat gap sebagai `NOT_PROVEN`; jangan mengambil isi Private atau mengklaim dummy sebagai bukti integrasi/runtime final.

### 5.1 Sasaran Wajib: Public Build-Ready, Private Wiring-Only

Untuk setiap tahap pembangunan, sasaran operasional wajib adalah:

```text
PUBLIC = IMPLEMENTASI + ITERASI + DUAL-DUMMY INTEGRATION + FULL PUBLIC ASSEMBLY REHEARSAL + BUILD-READY STAGE CAPSULE
PRIVATE = VALIDATE + DETERMINISTIC MECHANICAL WIRING + BUILD + SIGN + VERIFY + FINAL TEST
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

Setiap baseline Private yang akan menerima Promotion Package wajib mempunyai receiver contract aman yang cukup untuk Public mengembangkan dan menguji sambungan tanpa mengetahui isi Private.

Receiver contract hanya boleh memuat informasi minimum yang diperlukan, misalnya stable/opaque slot ID, contract version/digest, required/optional status, input/output shape aman, lifecycle/startup ordering yang perlu diketahui consumer, compatibility range, dependency class/requirement aman, generic failure/error code, dan acceptance schema version/digest.

Receiver contract **dilarang** memuat source Private, path internal, nama class internal yang tidak perlu, algoritma, state, asset, secret, database, internal topology, internal dependency yang sensitif, atau detail lain yang tidak diperlukan untuk wiring.

Prinsipnya:

```text
PUBLIC_KNOWS_SOCKET_SHAPE = MINIMUM_REQUIRED
PUBLIC_KNOWS_PRIVATE_IMPLEMENTATION = 0
```

Contract aman harus dirancang sebagai abstraksi boundary dari awal. Dilarang pola `copy Private -> sensor/redact -> publish`.

### 5.3 Stable Integration Plane dan Private Receiver Adapter

Project yang mempunyai kernel/registry/extension-point harus menjaga **Stable Integration Plane** atau mekanisme ekuivalen yang sekecil dan sestabil mungkin.

Mapping dari receiver/slot ID aman ke path, class, object, registry, state, atau struktur internal yang sebenarnya tetap berada di Private sebagai Private Receiver Adapter/map atau mekanisme ekuivalen.

Public tidak boleh mengetahui mapping internal tersebut.

Private Receiver Adapter/Integration Plane harus sudah tersedia dan compatible sebelum tahap Public dinyatakan siap. Jika Private masih harus menciptakan implementation receiver baru ketika Stage Capsule tiba, maka `PRIVATE_IMPLEMENTATION_REQUIRED = TRUE` dan `STAGE_READY_PRIVATE` batal.

Untuk project ToolBox, Stage A adalah fondasi yang tepat untuk mengunci Integration Plane karena scope tahap A mencakup kernel, registry, lifecycle, contract, dan safety boundary.

### 5.4 Canonical Contract Artifact

Tidak boleh ada dua definisi contract yang hanya diasumsikan sama.

Satu canonical safe contract artifact/IDL/schema menjadi sumber bersama shape/version wiring dan mempunyai exact digest. Public SDK/interface, dummy interface, conformance suite, wiring schema, Private receiver interface binding, dan Private conformance verifier harus berasal dari atau diverifikasi terhadap canonical source tersebut.

Public dan Private wajib mengikat exact contract digest yang sama. Contract mismatch = STOP sebelum attempt/build.

### 5.5 Sealed Private Receiver Certificate

Receiver Private aktual harus mempunyai conformance evidence/certificate yang **tetap Private**, mengikat baseline, receiver contract digest, receiver adapter/map digest, conformance-suite digest, dan environment/toolchain binding bila material.

Certificate/evidence, log, config, receiver map, atau ekstraknya tidak boleh diekspor ke Public.

Untuk menghindari attempt tahap sebagai qualification loop, receiver certificate idealnya dibuat/seal ketika baseline sebelumnya dikunci dan direuse selama receiver/contract/input dasarnya tidak berubah.

Perubahan material receiver/contract menginvalidasi certificate. Qualification baru adalah pekerjaan Private nyata dengan budget/authorization; bukan preflight gratis.

### 5.6 Dual Independent Public Dummy

Public wajib mempunyai minimal:

```text
REFERENCE_DUMMY
ADVERSARIAL_CONFORMANT_DUMMY
```

Keduanya dibuat hanya dari canonical safe contract, independen dari Private.

Adversarial dummy tetap legal menurut contract tetapi menantang batas yang diperbolehkan, misalnya optional capability absent, minimum capacity, maximum legal latency, restricted/recovery state, process restart, failure surface, dan lifecycle timing edge sesuai scope.

Kedua dummy harus sebisa mungkin mengurangi common-mode oracle/implementation yang sama.

Jika package hanya bekerja pada satu dummy tetapi gagal pada implementasi lain yang tetap conform, `STAGE_READY_PRIVATE = FALSE`.

### 5.7 Machine-Readable Stage Wiring Manifest

Setiap Stage Capsule wajib membawa `STAGE_WIRING_MANIFEST` atau format mesin ekuivalen.

Manifest minimal mengikat project/stage ID, canonical contract ID/version/digest, exact package/member/source/asset/build-descriptor hashes, mapping public slot ID -> promoted provider ID, registry binding, module registration, dependency graph, startup/lifecycle ordering, state/recovery/Safe-UI/resource/diagnostic binding bila berlaku, manifest/resource/shrinker/build requirements, compatibility constraints, expected fail-closed behavior, dan handoff/acceptance schema version/digest.

Manifest harus dapat diterapkan secara deterministic. Instruksi naratif seperti `buat adapter`, `cari tempat bootstrap`, `sesuaikan sampai compile`, atau `perbaiki jika gagal` tidak cukup untuk `STAGE_READY_PRIVATE`.

### 5.8 Deterministic Wiring Compiler dan Independent Verifier

Private wiring harus berupa transformasi mesin deterministic:

```text
STAGE_WIRING_MANIFEST
+ PRIVATE_RECEIVER_MAP
+ CANONICAL_CONTRACT
-> GENERATED_BINDINGS
```

Untuk input yang sama, output harus sama secara semantic/digest sesuai contract.

Compiler/generator hanya boleh menghasilkan mechanical wiring yang dideklarasikan: placement, registration, slot/provider binding, startup binding, thin delegation tanpa semantic baru, build-graph connection, dan provenance/evidence.

Compiler **tidak boleh menjadi oracle tunggal** bagi outputnya sendiri. Critical wiring wajib diperiksa independent translation verifier yang memperoleh expected graph/constraints secara terpisah.

Minimal verifier menolak missing/duplicate/incompatible/undeclared binding, illegal dependency cycle/order, forbidden normal path dari restricted state, undeclared generated file, dan manual patch.

### 5.9 Formal Wiring Model

Property universal/critical pada ruang wiring finite harus dialokasikan ke exhaustive/model checking/formal method bila testing contoh tidak cukup, sesuai R9.

Formal model harus ditrace ke manifest, generated wiring, dan implementation verifier. `FORMAL_MODEL_PASS` sendiri tidak cukup bila translation ke implementation tidak dibuktikan.

### 5.10 Full Public Assembly Rehearsal

Sebelum `STAGE_READY_PRIVATE`, Public wajib menjalankan rehearsal dari workspace bersih:

```text
FRESH PUBLIC WORKSPACE
-> CANONICAL CONTRACT
-> REFERENCE + ADVERSARIAL CONFORMANT DUMMY
-> EXACT STAGE CAPSULE
-> APPLY STAGE_WIRING_MANIFEST DETERMINISTICALLY
-> INDEPENDENT VERIFY
-> FULL PUBLIC DUMMY APPLICATION/APK ASSEMBLY
-> INSTALL/RUN ON DECLARED DEVELOPMENT TARGET
-> VERIFY STARTUP + REGISTRY + ROUTES + STATE/LIFECYCLE + FAILURE/RECOVERY + SAFE MODE/UI AS APPLICABLE
-> RESTART/PROCESS-DEATH TEST AS APPLICABLE
-> NEGATIVE/MUTATION TEST AS APPLICABLE
-> PACKAGE/PROVENANCE VALIDATION
-> PASS
```

Full dummy application hanya terdiri dari material Public + dummy receiver. Ia bukan aplikasi final, tidak boleh memakai identitas/asset/state/secret Private, dan tidak boleh disebut final runtime proof.

Public Firebase/Test Lab tetap dilarang.

Invariant closure:

```text
PUBLIC_FULL_ASSEMBLY_REHEARSAL = PASS
PROMOTED_PRODUCTION_SOURCE_CHANGED_AFTER_REHEARSAL = 0
MANUAL_FIX_AFTER_PACKAGE_APPLY = 0
DUMMY_HOST_DERIVED_FROM_PRIVATE = 0
PRIVATE_CONTENT_USED_BY_REHEARSAL = 0
```

### 5.11 Reproducible / Hermetic Qualification

R6 berlaku penuh. Untuk claim deterministic/reproducible, Public melakukan clean independent assembly comparison sesuai R6. Bila format bit-reproducible, digest output harus identik; normalisasi hanya diizinkan untuk nondeterminism yang dibuktikan non-semantic.

Double-clean qualification Public bukan alasan melakukan double candidate build di Private. Private memakai satu candidate build bila qualification/reuse evidence masih sah.

### 5.12 Build-Ready Proof-Carrying Stage Capsule

Promotion Package tahap harus assembly-ready/build-ready untuk receiver contract, bukan sekadar source-ready, dan diperlakukan sebagai **proof-carrying Stage Capsule**.

Selain source/asset produksi, capsule harus membawa seluruh material Public yang diperlukan agar Private tidak membuat keputusan baru: module/build descriptors, dependency declarations/locks/digests, registry descriptors, canonical contract digest, lifecycle/startup binding, manifest/resource/shrinker requirements, compatibility matrix, wiring manifest, acceptance schema digest, dual-dummy/full-assembly evidence, R1–R9 closure, independent/formal verification evidence bila berlaku, provenance/attestation, dan capsule root digest.

Input Private-only seperti secret/signing material, internal state, Private receiver mapping/certificate, dan final baseline tidak boleh masuk Stage Capsule.

### 5.13 Public Handoff Acceptance Contract

Private wajib menyediakan acceptance contract aman untuk bagian handoff yang harus dibentuk Public. Contract hanya mendeskripsikan schema/field/type/version/status yang boleh dan wajib, bukan nilai atau struktur internal Private.

Public wajib memvalidasi Stage Capsule, wiring manifest, dependency handoff record, dan metadata lain yang akan dibaca Private terhadap acceptance contract sebelum `STAGE_READY_PRIVATE`.

Jika active Private verifier memerlukan field public-addressable yang tidak tercakup acceptance contract, contract stale dan closure tahap batal sampai diperbaiki.

### 5.14 No-Private-Implementation Gate

Sebelum promosi dan kembali saat Private preflight wajib ada pemeriksaan fail-closed:

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

Kategori perubahan Private yang diperbolehkan hanya mechanical zones yang sudah dideklarasikan. Jika diff/operasi keluar dari kategori tersebut: `PRIVATE_DEVELOPMENT_DETECTED -> STOP -> ROLLBACK_IF_NEEDED -> SANITIZED_REPORT -> PUBLIC`.

### 5.15 Detail Normatif dan Basis Riset

`REPOSITORY_INTEGRATION_POLICY.md` wajib dibaca setelah Rule 0 untuk detail Stable Integration Plane, sealed receiver lifecycle, proof-carrying capsule, cryptographic admission, atomic apply, tooling strategy, dan basis riset contract verification, reproducible/hermetic build, provenance/attestation, serta formal verification.

Tidak ada kewajiban mengganti toolchain yang sudah sesuai hanya karena suatu tool disebut sebagai referensi riset.

## 6. Jalur Kematangan Komponen

Untuk mencapai kesiapan komponen di Public, setiap komponen wajib melewati:

`SPEC -> CONTRACT -> DEPENDENCY -> UNIT_TEST -> SIMULATOR/DUMMY -> FAILURE_TEST -> PACKAGE_VALIDATION -> COMPONENT_READY_PRIVATE`

Kesiapan komponen saja tidak pernah mengizinkan integrasi Private.

### 6.1 Kesiapan Komponen Tidak Sama dengan Kesiapan Tahap

| Status | Makna | Izin masuk Private |
| --- | --- | --- |
| `COMPONENT_READY_PRIVATE` | Seluruh proof wajib komponen pada scope Public selesai; komponen ditahan di Public menunggu tahap lengkap. | Tidak mengizinkan promosi, integrasi, build, atau test Private tersendiri. |
| `STAGE_READY_PRIVATE` | Seluruh cakupan tahap yang ditetapkan pengguna sudah ditutup menurut §6.2. | Syarat kesiapan satu tahap utuh; tetap memerlukan otorisasi dan gate yang berlaku. |

Status lama `READY_PRIVATE` pada output/manifest komponen hanya dibaca sebagai kesiapan komponen, bukan izin Private. Jangan menaikkan status lama menjadi kesiapan tahap secara otomatis.

### 6.2 Closure Tahap Utuh Sebelum Private

Agen wajib memastikan:

1. Identitas tahap dan seluruh requirement/sublangkah/komponen sesuai peta yang disetujui.
2. Seluruh kontrak sambungan, versi, registry route, dependency, urutan/lifecycle, failure handling, acceptance, dan batas resource telah jelas.
3. Seluruh proof wajib Public, termasuk interaksi antar-komponen satu tahap, R1–R9 yang berlaku, asset/route proof, negative test, dan package validation lengkap.
4. Promotion manifest/Stage Capsule mengikat seluruh paket anggota dan evidence.
5. Penerima Private meninjau bukti prasyarat baseline/adapter/toolchain/receiver certificate yang masih sah tanpa mengekspor isinya.
6. Rencana satu attempt mencakup gate murah sebelum mahal, wiring, regression/build/signing/final test yang diperlukan, pemulihan, budget/durasi, dan otorisasi.
7. Canonical safe contract tersedia dan frozen.
8. Reference + adversarial conformant dummy PASS.
9. Machine-readable Stage Wiring Manifest lengkap/deterministic.
10. Deterministic wiring compiler + independent translation verifier terbukti.
11. Formal wiring counterexample = 0 bila applicable.
12. Full Public Assembly Rehearsal PASS tanpa manual fix.
13. Public Handoff Acceptance Contract/schema compatibility PASS.
14. Proof-carrying Stage Capsule valid/build-ready.
15. `NO_PRIVATE_IMPLEMENTATION` gate PASS.
16. `UNKNOWN = 0`, `NOT_PROVEN = 0` untuk seluruh Public prerequisite yang berlaku.

Pisahkan prerequisite masuk dari witness integrasi/runtime final yang baru dihasilkan pada attempt Private. Witness final tetap pending/NOT_PROVEN sampai dijalankan; bukan syarat PASS melingkar untuk memasuki Private. `STAGE_READY_PRIVATE` bukan final application PASS.

### 6.3 Satu Tahap, Satu Percobaan Private Terencana

Satu tahap utuh adalah satu batas promosi/integrasi. Komponen, sublangkah, gate, sub-gate, individual check, jumlah paket, pergantian agen, atau pergantian konteks tidak menambah jatah attempt.

Sebelum dispatch, agen wajib menunjukkan stage ID, scope/capsule binding, prasyarat, daftar operasi/workflow/job, evidence reuse, estimasi total menit/biaya/penyimpanan, batas durasi, titik STOP, serta izin eksekusi tahap yang sesuai instruksi pengguna. Izin menyunting MD tidak mengizinkan eksekusi Private.

Catat satu attempt ID beserta scope/input binding dan status. Cegah dispatch ganda dan pekerjaan bersamaan. Semua job, preflight, bootstrap, qualification, build, dan verification Private dihitung sebagai penggunaan nyata; memindahkannya ke workflow lain tidak membuatnya gratis atau membuka attempt baru.

Satu attempt adalah satu rencana eksekusi tahap, bukan janji satu command/workflow. Jangan mendispatch gate terpisah jika coverage yang sama sudah sah tercakup candidate path. Jangan mengurangi proof untuk menghemat kuota.

Checkpoint persetujuan Firebase terhadap signed candidate tetap wajib dan terpisah dari izin wiring/build tahap.

Kegagalan/timeout/cancellation setelah pekerjaan Private dimulai tidak mengembalikan jatah attempt. STOP, rollback bila perlu, sanitized report, Public reclosure, lalu keputusan/izin baru untuk attempt berikutnya.

Targetnya keberhasilan percobaan pertama. “100%” tidak boleh dimaknai probabilitas attempt pertama 100% atau bug-free absolut.

Claim wiring yang sah bila seluruh exact sealed scope terbukti adalah:

```text
DETERMINISTIC_WIRING_CLOSED = TRUE
WIRING_UNKNOWN = 0
WIRING_NOT_PROVEN = 0
```

## 7. Promotion Package / Stage Capsule

Promotion Package tahap memuat `PROJECT_ID`, `STAGE_ID`, versi scope, seluruh sublangkah/komponen wajib, exact member hashes, canonical contract binding, Stage Wiring Manifest, route/test-evidence binding, R1–R9 closure, acceptance schema binding, dual-dummy/full-assembly evidence, provenance/attestation, capsule root digest, dan batas claim.

Metadata Private seperti baseline internal, receiver map/certificate, dan bukti penerima disimpan pada catatan penerimaan Private, tidak disalin ke Public.

Private wajib menolak capsule yang identity/hash/contract/dependency/compatibility/wiring/acceptance/provenance binding-nya tidak valid.

## 8. Private Preflight dan Transaction

Hanya setelah closure tahap dan izin eksekusi pada §6, Private menjalankan preflight murah. Preflight termasuk biaya attempt.

Sebelum workload berat periksa fail-closed:

```text
CAPSULE ID/HASH/ROOT DIGEST
-> CANONICAL CONTRACT DIGEST
-> STAGE WIRING MANIFEST/SCHEMA DIGEST
-> ACCEPTANCE SCHEMA DIGEST
-> DEPENDENCY/TOOLCHAIN COMPATIBILITY
-> PRIVATE RECEIVER CERTIFICATE FRESHNESS
-> RECEIVER CONTRACT DIGEST MATCH
-> ENVIRONMENT/BASELINE PREREQUISITE
-> NO-PRIVATE-IMPLEMENTATION CONDITIONS
```

Jika satu saja mismatch/unknown/stale: **STOP**. Jangan menjalankan build/wiring berat untuk menemukan mismatch yang dapat diketahui di preflight.

Transaksi resmi:

```text
CURRENT_FINAL
-> FREEZE EXACT INPUTS
-> SNAPSHOT
-> GENERATE DECLARED WIRING
-> INDEPENDENT VERIFY
-> ATOMIC APPLY
-> VERIFY TREE/DIFF
-> REGRESSION
-> COMMIT_NEW_FINAL_STATE
```

Jika FAIL: `ROLLBACK` ke state sebelumnya.

## 9. Private Execution Machine

Mesin Private adalah satu-satunya jalur yang boleh mengeksekusi isi Private untuk final processing.

Jalur final resmi:

`PREFLIGHT -> SNAPSHOT -> GENERATE_DECLARED_WIRING -> INDEPENDENT_VERIFY -> ATOMIC_APPLY -> VERIFY_TREE_DIFF -> REGRESSION -> COMMIT -> BUILD_APK -> SIGN_CANDIDATE -> VERIFY_SIGNATURE_HASH_PROVENANCE -> FIREBASE/FINAL_RUNTIME_TEST -> PASS -> RELEASE`

Aturan:

- build APK yang memakai source/asset Private wajib berjalan pada boundary Private;
- signing candidate wajib berjalan pada boundary Private;
- final runtime/Firebase hanya dari Private;
- APK final test harus candidate yang sudah ditandatangani dan diverifikasi;
- release hanya exact candidate yang memperoleh final PASS;
- Public tidak boleh menjadi bridge/runner/relay untuk isi Private;
- Private wiring tidak boleh menjadi tempat menulis behavior produk yang seharusnya matang di Public;
- deterministic wiring tooling harus benar-benar implemented/tested; teks MD bukan enforcement proof.

GitHub Actions boleh digunakan sebagai mesin Private jika workflow/input/output tetap Private.

### 9.1 Firebase / Test Lab Hanya di Private

**Public DILARANG melakukan pengecekan, akses, atau pengujian Firebase/Test Lab dalam bentuk apa pun, termasuk dummy/prototype Public.**

Larangan mencakup connection check, autentikasi, catalog/model lookup, candidate preflight yang mengakses Firebase, upload/download, submit test matrix, serta caller/relay Firebase.

Public boleh mempelajari dokumentasi API terbuka dan menguji mock/fixture tanpa koneksi/panggilan Firebase.

Final Firebase test memakai exact signed candidate Private dan memerlukan persetujuan eksplisit satu attempt sesuai policy.

```text
FIREBASE_EXECUTION_BOUNDARY = PRIVATE_ONLY
PUBLIC_FIREBASE_ACCESS = 0
PUBLIC_FIREBASE_EXECUTION = 0
PUBLIC_FIREBASE_DUMMY_EXCEPTION = 0
```

## 10. Larangan Trial-and-Error di Private

**DILARANG KERAS melakukan trial-and-error berulang di Private.**

Jika kegagalan final/wiring terjadi:

`STOP -> ROLLBACK bila diperlukan -> SANITIZED_FAILURE_REPORT -> PUBLIC FIX/RETEST -> TUTUP ULANG TAHAP TERDAMPAK -> STAGE_READY_PRIVATE -> TUNGGU KEPUTUSAN/IZIN ATTEMPT BARU`

Dilarang pola `Private gagal -> edit behavior -> build lagi -> gagal -> edit lagi`.

## 11. Sanitized Failure Report

Informasi yang keluar dari Private ke Public hanya boleh error/compatibility information aman seperti error ID, contract mismatch, unsupported version, dependency mismatch, receiver-slot/wiring-manifest mismatch generik, acceptance-schema mismatch, lifecycle failure, atau generic validation result.

Dilarang membawa source/asset Private, secret/token, konfigurasi internal, path sensitif, dump, database/state, APK/artifact Private, receiver map/certificate content, atau detail kernel yang membuka isi Private.

## 12. Auto Cleanup Public

Setiap pekerjaan mesin Public WAJIB memiliki Auto Cleanup setelah sukses/gagal sejauh platform memungkinkan.

Bersihkan workflow run/log yang dapat dihapus, artifact sementara, cache, workspace, temporary branch/ref, debug output, dan temporary test data.

Sebelum menghapus output sementara, pastikan Stage Capsule/evidence wajib tersimpan secara tahan lama sesuai retention. Cleanup tidak menggantikan keamanan; data Private tidak boleh pernah masuk Public.

## 13. Isolasi Project dan Shared Component

Setiap pekerjaan harus memiliki `PROJECT_ID`, target master, component ID/version, contract ID/version/digest, target platform, dan compatibility.

Dilarang mengambil isi repo/project lain hanya karena terlihat cocok.

Komponen lintas project harus dinyatakan `GLOBAL/SHARED_COMPONENT` dan mempunyai source resmi, version, contract, dependency, compatibility, dan test.

## 14. Dependency dan Environment Lock

Public dan Private wajib mengunci seluruh input yang memengaruhi hasil serta membuktikan compatibility aspek bersama melalui contract. Perbedaan environment harus eksplisit; kemiripan environment bukan proof kesetaraan. Ikuti R6 dan R9.

Dependency/toolchain qualification untuk Stage Capsule harus selesai atau merupakan Private prerequisite yang sudah tersedia sebelum `STAGE_READY_PRIVATE`. Private tidak boleh digunakan memilih dependency baru secara trial-and-error.

## 15. Jalur Resmi

Public:

`RESEARCH -> DESIGN -> BUILD_COMPONENT -> CONTRACT/R1-R9 -> DUAL_DUMMY -> DETERMINISTIC_WIRING_REHEARSAL -> FULL_PUBLIC_ASSEMBLY -> REPRODUCIBILITY/PROVENANCE -> PACKAGE_VALIDATION -> COMPONENT_READY_PRIVATE -> TUTUP SELURUH TAHAP -> PROOF_CARRYING_STAGE_CAPSULE -> STAGE_READY_PRIVATE`

Private:

`PREFLIGHT -> SNAPSHOT -> GENERATE_DECLARED_WIRING -> INDEPENDENT_VERIFY -> ATOMIC_APPLY -> VERIFY_TREE_DIFF -> REGRESSION -> COMMIT -> BUILD_APK -> SIGN_CANDIDATE -> VERIFY_SIGNATURE/HASH/PROVENANCE -> FIREBASE/FINAL_RUNTIME_TEST -> PASS -> RELEASE`

Jika gagal di Private:

`STOP -> ROLLBACK bila diperlukan -> SANITIZED_FAILURE_REPORT -> PUBLIC`

## 16. Larangan Sistem

Dilarang menyediakan atau menggunakan:

- checkout Private dari Public;
- token/credential Public untuk membaca Private;
- mirror kernel/source/asset/artifact Private di Public;
- registry Public yang menyimpan isi Private;
- contract Public yang mengungkap internal receiver mapping/path/class/topology yang tidak diperlukan;
- dummy yang diturunkan dari salinan/redaksi/dekompilasi/observasi isi Private;
- ekspor Private receiver map/certificate/conformance evidence;
- Public runner/workflow sebagai mesin build/test isi Private;
- Public sebagai jalur Firebase;
- transfer bebas antar project;
- debug/trial-and-error berulang di Private;
- implementasi behavior produk baru di Private ketika `PRIVATE_WIRING_ONLY`;
- manual patch sesudah Stage Capsule diterapkan;
- wiring compiler sebagai satu-satunya oracle;
- log yang membocorkan data Private;
- ketergantungan Public pada isi Private.

## 17. Konteks Percakapan Project

Perintah `Buka [Nama Project]` mengaktifkan hanya konteks project tersebut. Asset, keputusan, masalah, dan pembahasan project lain tidak boleh ikut terbawa.

Pertanyaan umum wajib dijawab sebagai pertanyaan umum dan tidak otomatis dikaitkan dengan project/repo/aplikasi lama.

Prioritas konteks:

`Pesan pengguna saat ini -> Project yang secara eksplisit dibuka -> Konteks umum`.

## 18. Prinsip Final

- Private Master selalu sumber kebenaran final.
- Isi Private tidak pernah keluar ke Public.
- Public hanya mengetahui contract/socket minimum; internal receiver mapping/conformance evidence tetap Private.
- Public menghabiskan implementasi dan iterasi, termasuk dual dummy, deterministic wiring rehearsal, full dummy build, independent verification, dan reproducibility/provenance yang berlaku.
- `STAGE_READY_PRIVATE` hanya sah jika Stage Capsule build-ready dan tidak membutuhkan implementation/design/manual patch baru di Private.
- Satu tahap utuh adalah satu batas promosi dan satu percobaan Private terencana.
- Private melakukan validate + deterministic mechanical wiring + final build/sign/verify/final test.
- Kegagalan Private kembali ke Public melalui sanitized report.
- Asset/pembahasan antar project wajib terisolasi.
- Pekerjaan manual berulang yang dapat diotomatisasi wajib diotomatisasi.
- `DETERMINISTIC_WIRING_CLOSED = TRUE` adalah claim kondisional pada exact sealed scope/input, bukan probabilitas first attempt 100%.
