# APPLICATION_SAFE_100_PROCESS.md — Public Research Scope

## 1. Status

Dokumen ini adalah **adapter scope Public** untuk metode assurance R1–R9 pada `RMTampu/Tools`.

Metode teknis R1–R9 tetap boleh dipakai untuk:

- research;
- design review;
- prototype;
- component-level verification;
- mock/simulator/test harness;
- contract/fault/dependency testing;
- kesiapan komponen `COMPONENT_READY_PRIVATE` dan closure tahap `STAGE_READY_PRIVATE`.

Dokumen ini **tidak** memberi kewenangan Public untuk menjalankan final application assurance terhadap integrated Private state.

## Kesiapan Integrasi Satu Tahap

Bagian ini menerapkan hasil riset ke prosedur aktif, bukan mengubah fitur, susunan tahap, kontrak perilaku, atau jalur registry rancangan. Definisi tahap/status/attempt mengikuti `GLOBAL_PUBLIC_PRIVATE_DEVELOPMENT_RULES.md` §6. Untuk setiap tahap yang dikerjakan, agen wajib menghasilkan atau merujuk evidence berikut sebelum handoff.

| Metode wajib | Penerapan pada pekerjaan | Bukti dan batas klaim |
| --- | --- | --- |
| Contract-first dan traceability | Petakan setiap requirement tahap ke kontrak, produsen/penerima, registry route, versi, input/output, lifecycle/urutan, ownership, error/recovery, acceptance, dan budget yang sudah ditetapkan. | Requirement → implementasi → test/oracle → evidence. Kontrak atau ambang yang belum diputuskan tetap blocker; agen tidak mengarang nilai. |
| Dummy independen dan pembuktian dua sisi | Buat dummy hanya dari contract/material Public. Uji dummy dan komponen terhadap suite kontrak yang sama serta oracle perilaku yang tidak sekadar meniru implementasi. | Bukti Public menyatakan kemampuan dan keterbatasan dummy. Kesesuaian penerima nyata dibuktikan di Private; reuse bukti prasyarat yang sah dan rencanakan witness integrasi aktual dalam attempt tahap. |
| R6 seluruh fase | Inventaris dan ikat input/perintah seluruh fase yang relevan, termasuk dependency/plugin/toolchain/trust dan perbedaan environment. | Ikuti R6 bagian “Penutupan R6 Sebelum Handoff Tahap”; PASS compiler/packaging komponen tidak dianggap PASS build APK Private. |
| Risk/FMEA dan interaksi tahap | Petakan mode gagal, dampak, penyebab, kontrol, owner dan proof; uji antar-komponen, state/urutan normal-gagal-recovery, serta kombinasi konfigurasi berdasarkan risiko. | Coverage state/transition/sequence dan t-way mempunyai batas/model/strength yang jelas. Ketidakmampuan dummy merepresentasikan kondisi tertentu dicatat, bukan dihapus. |
| Mutation/fault seeding dan verifier | Tantang missing route, versi/dependency salah, stale binding, resource pressure, partial failure, serta verifier yang menerima flag/teks PASS palsu. | Mutation bermakna harus ditolak; survivor ditelusuri, equivalent mutation diberi alasan, keterbatasan membutuhkan metode alternatif. Jumlah test/file tidak cukup. |
| Stage closure dan pengendalian kuota | Tutup seluruh anggota serta interaksi tahap, lalu siapkan satu rencana Private dengan gate murah di depan, reuse bukti sah, no duplicate dispatch, budget dan STOP. | Stage manifest/closure, evidence index, daftar prasyarat dan witness final pending, serta rencana/otorisasi attempt mengikuti aturan global §6–§8. |

Evidence index minimal mencatat: Stage ID dan versi scope, requirement/contract/route ID, domain R1–R9 dan applicability, implementasi/input revision, test/oracle dan versi/configuration, environment, expected/actual result, artifact/package digest bila relevan, evidence/run reference, keterbatasan, owner, serta status. Tambahkan review baseline/adapter/toolchain pada catatan Private saja.

Seluruh prerequisite Public yang berlaku harus terbukti sebelum `STAGE_READY_PRIVATE`; `UNKNOWN`, missing evidence, atau kegagalan yang bisa diselesaikan di Public memblokir handoff. Witness Private-only yang direncanakan bukan PASS Public dan bukan prasyarat melingkar sebelum integrasi. Jika penerima/lingkungan membutuhkan kualifikasi baru yang belum teranggarkan, hentikan dispatch dan laporkan keputusan yang dibutuhkan.

R1–R9 adalah domain assurance, bukan sembilan izin masuk Private. PASS komponen hanya mencakup scope/input/environment yang dibuktikan. Tahap belum siap hanya karena semua komponen masing-masing hijau: proof sambungan/interaksi tahap tetap wajib.

Gunakan kembali bukti baseline dan test yang masih sah dengan impact/equivalence yang dapat ditelusuri. Perubahan editorial tidak otomatis memaksa seluruh test/build diulang; perubahan teknis menginvalidasi proof terdampak. Jangan memindahkan pekerjaan yang mampu ditutup di Public ke Private demi kenyamanan agen.

Bukti kualifikasi host Private tidak boleh diekspor, termasuk konfigurasi/cache/log atau ekstraknya. Interface Public harus memang diklasifikasikan aman, bukan hasil membuka material Private di Public. Firebase tetap hanya Private; final approval terhadap signed candidate tetap wajib.

Target “sekali berhasil” adalah sasaran perencanaan, bukan jaminan. Jika data coverage/biaya belum tersedia, tulis belum terbukti; jangan memasang klaim 100%, STAGE_READY_PRIVATE, runtime PASS, atau guard otomatis hanya karena MD selesai.

Rujukan metode: [NASA — product integration](https://www.nasa.gov/reference/5-0-product-realization/), [Google — fidelity test doubles](https://abseil.io/resources/swe-book/html/ch13.html), [NIST — state-based integration testing](https://www.nist.gov/publications/integration-testing-object-oriented-components-using-finite-state-machines), [NIST — combinatorial testing](https://www.nist.gov/publications/practical-combinatorial-testing), [PIT — mutation testing](https://pitest.org/quickstart/basic_concepts/), dan [GitHub — biaya Actions](https://docs.github.com/en/billing/concepts/product-billing/github-actions). Metode diterapkan sesuai domain; tidak mewajibkan penggantian tool/rancangan yang sudah sesuai.

## 2. Domain Methods Tetap Berlaku

Urutan domain tetap:

```text
R1 LOGIC / INPUT
-> R2 CONCURRENCY / RESOURCE
-> R3 LIFECYCLE / STATE / RECOVERY
-> R4 PERSISTENCE / STORAGE / VERSION
-> R5 SECURITY / NETWORK / EXTERNAL BOUNDARY
-> R6 BUILD / DEPENDENCY / INSTALL
-> R7 NATIVE / PLUGIN / RUNTIME
-> R8 UI / DEVICE / POWER
-> R9 VERIFICATION COMPLETENESS
```

Metode tersebut boleh digunakan di Public **hanya terhadap scope Public**.

## 3. Batas Claim Public

Status Public yang sah:

```text
PUBLIC_RESEARCH_PASS
PUBLIC_COMPONENT_PASS
PUBLIC_DEVELOPMENT_PASS
COMPONENT_READY_PRIVATE
STAGE_READY_PRIVATE
```

Public tidak boleh mengklaim:

```text
FINAL_APPLICATION_SAFE_100
FINAL_PRIVATE_BUILD_PASS
FINAL_SIGNING_PASS
FIREBASE_TARGET_PASS
FINAL_RELEASE_READY
```

Claim final tersebut hanya dapat ditutup pada boundary Private.

## 4. Public Prebuild/Research Chain

Untuk component Public:

```text
RULE/SCOPE LOCK
-> REQUIREMENT / CONTRACT REVIEW
-> R1-R8 APPLICABLE PREBUILD ANALYSIS
-> ASSET/ROUTE PROOF bila relevan
-> PUBLIC BUILD/TEST terhadap component/prototype Public
-> RUNTIME/SIMULATOR TEST
-> FAILURE / MUTATION / ADVERSARIAL TEST
-> R9 PUBLIC EVIDENCE COMPLETENESS
-> PACKAGE_VALIDATION
-> COMPONENT_READY_PRIVATE
```

Tidak ada final application build boundary pada Public untuk isi Private. Penyambungan komponen diuji menggunakan dummy/mock/simulator mandiri dari contract aman, bukan baseline APK atau salinan/ekstraksi/penyamaran isi Private; pengujian Public tidak menggunakan Firebase.

## 5. R6 di Public

R6 boleh digunakan untuk:

- dependency lock research;
- toolchain compatibility;
- manifest/package model pada prototype Public;
- reproducibility tests Public;
- component packaging tests;
- signing workflow research menggunakan dummy/prototype Public bila aman.

R6 tidak boleh digunakan untuk membangun/menandatangani APK final dari source Private.

## 6. R7/R8 di Public

R7/R8 boleh menjalankan native/plugin/UI/device tests terhadap component/prototype Public.

Evidence non-target tidak boleh diklaim sebagai final Android 11/API30/ARM64 proof untuk integrated Private product.

## 7. Firebase

Seluruh akses operasional, pengecekan, dan pengujian Firebase/Test Lab hanya boleh di Private. Public dilarang memakainya, termasuk untuk dummy/prototype/artifact Public dan sekalipun ada single-use approval.

Public tidak boleh:

- menerima APK atau mengunduh artifact Private;
- melakukan Firebase connection check, autentikasi, catalog/model lookup, atau preflight yang mengakses Firebase;
- melakukan upload/download atau submit Firebase test matrix;
- menjadi executor, caller, atau relay Firebase.

Riset dokumentasi API terbuka dan mock/fixture tanpa koneksi/panggilan Firebase tetap boleh. Final target-specific witness dipenuhi dari jalur Private dengan signed candidate dan approval sesuai policy Private, bukan dari Public.

## 8. Private Failure Feedback

Jika final Private menemukan kegagalan, Public hanya menerima `SANITIZED_FAILURE_REPORT`.

Dilarang menerima:

- source/asset Private;
- APK/artifact Private;
- secret/token;
- database/state;
- internal dump;
- path sensitif;
- detail kernel yang membuka isi Private.

## 9. Promotion Gate

Public hanya dapat menghasilkan `COMPONENT_READY_PRIVATE` setelah seluruh proof wajib komponen pada scope Public PASS. Komponen menunggu di Public; kesiapan tahap utuh memerlukan closure tambahan menurut aturan global §6.

```text
SPEC
-> CONTRACT
-> DEPENDENCY
-> UNIT_TEST
-> SIMULATOR
-> FAILURE_TEST
-> PACKAGE_VALIDATION
-> COMPONENT_READY_PRIVATE
```

`STAGE_READY_PRIVATE` memerlukan seluruh anggota/interaksi/proof tahap, bukan satu paket komponen. Hanya tahap utuh menjadi batas promosi/attempt Private dengan otorisasi yang berlaku. Output lama `READY_PRIVATE` komponen tidak membuka Private. Integrasi final hanya Private dan belum PASS sebelum bukti aktual.

## 10. Auto Cleanup

Setiap Public job wajib Auto Cleanup setelah sukses/gagal sejauh platform memungkinkan.

## 11. Final Invariant

```text
PUBLIC_SCOPE = RESEARCH / COMPONENT / PROTOTYPE / STAGING
PRIVATE_CONTENT_IN_PUBLIC = 0
PRIVATE_EXECUTION_THROUGH_PUBLIC = 0
PUBLIC_FINAL_APPLICATION_BUILD = 0
PUBLIC_FINAL_SIGNING = 0
PUBLIC_FIREBASE_ACCESS = 0
PUBLIC_FIREBASE_EXECUTION = 0
PUBLIC_FIREBASE_DUMMY_EXCEPTION = 0
PUBLIC_FINAL_APPLICATION_SAFE_100 = 0
PUBLIC_JOB_AUTO_CLEANUP = REQUIRED
```
