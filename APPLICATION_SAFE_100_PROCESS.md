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
- preparation sampai `READY_PRIVATE`.

Dokumen ini **tidak** memberi kewenangan Public untuk menjalankan final application assurance terhadap integrated Private state.

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
READY_PRIVATE
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
-> READY_PRIVATE
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

Public hanya dapat menghasilkan `READY_PRIVATE` setelah seluruh proof yang diwajibkan scope Public PASS.

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

`READY_PRIVATE` berarti paket siap untuk integrasi ke baseline APK/state final yang sebenarnya di Private, bukan integrasi atau aplikasi final sudah PASS. Final integration berikutnya terjadi di Private.

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
