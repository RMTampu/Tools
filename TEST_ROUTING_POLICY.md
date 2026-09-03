# TEST_ROUTING_POLICY.md — Public Research / Test / Staging

## 1. Status

Dokumen ini mengatur pengujian pada `RMTampu/Tools` sebagai repository Public Research/Test/Staging.

```text
PUBLIC ROLE = RESEARCH / ITERATION / PUBLIC TEST / WHOLE-STAGE STAGING
PRIVATE FINAL EXECUTION = RMTampu/ToolBox
```

Aturan global, `AGENTS.md`, dan `REPOSITORY_INTEGRATION_POLICY.md` selalu lebih tinggi.

Untuk promotion/wiring satu tahap, Public wajib menerapkan deterministic wiring / sealed integration pada `REPOSITORY_INTEGRATION_POLICY.md` §5–§18 dan §27–§29.

## 2. Batas Mutlak

Public hanya boleh menguji:

- component/source yang memang Public;
- prototype Public;
- canonical safe contract/interface;
- reference dummy;
- adversarial conformant dummy;
- mock/simulator;
- fixture/dummy data;
- test harness Public;
- full dummy application/APK yang seluruh isinya Public + dummy receiver.

Public dilarang:

- checkout/read Private;
- menerima source/kernel/asset/config/state Private;
- menerima APK/artifact Private;
- menerima Private receiver map/certificate/conformance evidence;
- membangun APK final dari source Private;
- menandatangani candidate Private;
- mengakses, mengecek, atau menjalankan Firebase/Test Lab, termasuk untuk dummy/prototype/artifact Public;
- menjadi reusable CI/relay untuk isi Private.

## 3. Public Test Environment dan Dummy Fidelity

Environment test Public boleh fleksibel sesuai contract pekerjaan, tetapi **tidak termasuk Firebase/Test Lab**.

Untuk menguji penyambungan, Public WAJIB menggunakan dummy mandiri dari canonical safe contract sebagai pengganti baseline APK/state final Private. Dummy tidak boleh merupakan salinan, ekstraksi, redaksi, dekompilasi, atau hasil observasi internal Private.

Minimal dua receiver Public digunakan bila scope wiring memerlukannya:

```text
REFERENCE_DUMMY
ADVERSARIAL_CONFORMANT_DUMMY
```

Adversarial dummy tetap conform terhadap contract tetapi menantang assumption legal seperti optional capability absent, minimum capacity, maximum legal latency, restricted/recovery state, restart, failure surface, atau lifecycle timing edge yang relevan.

Jika exact promoted package hanya bekerja pada satu dummy tetapi gagal pada dummy lain yang masih conform, `STAGE_READY_PRIVATE = FALSE`.

Integrasi sebenarnya hanya untuk tahap utuh setelah `STAGE_READY_PRIVATE`, otorisasi eksekusi, dan preflight Private PASS menurut aturan global §6.

Untuk target Android:

- API/ABI/emulator aktual harus dicatat;
- Public wajib membangun full dummy APK/application bila stage membutuhkan Android host integration proof;
- production stage materials yang digunakan rehearsal harus exact terhadap stage capsule;
- hasil non-target tidak boleh diklaim sebagai final Android 11/API30/ARM64 proof;
- dependency/toolchain harus dikunci bila memengaruhi hasil;
- claim tidak boleh lebih luas dari evidence.

## 4. Public Development Status

Status Public yang diperbolehkan:

```text
PUBLIC_DEVELOPMENT_IN_PROGRESS
PUBLIC_DEVELOPMENT_PASS
COMPONENT_READY_PRIVATE
STAGE_READY_PRIVATE
```

`COMPONENT_READY_PRIVATE` berarti komponen matang tetapi tetap ditahan di Public. Output lama `READY_PRIVATE` komponen bermakna sama dan bukan izin Private. Seluruh anggota/interaksi/proof tahap harus ditutup sebelum `STAGE_READY_PRIVATE`; hanya tahap utuh dapat dipromosikan dengan otorisasi yang berlaku. Ini bukan final application PASS.

Jalur kematangan komponen:

```text
SPEC
-> CONTRACT
-> DEPENDENCY
-> UNIT_TEST
-> SIMULATOR / DUMMY
-> FAILURE_TEST
-> PACKAGE_VALIDATION
-> COMPONENT_READY_PRIVATE
```

Closure tahap sebelum `STAGE_READY_PRIVATE` juga wajib mencakup:

```text
CANONICAL_CONTRACT_FROZEN = TRUE
REFERENCE_DUMMY = PASS
ADVERSARIAL_CONFORMANT_DUMMY = PASS
STAGE_WIRING_MANIFEST_COMPLETE = TRUE
DETERMINISTIC_WIRING_REHEARSAL = PASS
INDEPENDENT_TRANSLATION_VERIFIER = PASS
FORMAL_WIRING_COUNTEREXAMPLE = 0 IF_APPLICABLE
PUBLIC_FULL_ASSEMBLY_REHEARSAL = PASS
MANUAL_FIX_AFTER_APPLY = 0
PROMOTED_SOURCE_CHANGE_AFTER_REHEARSAL = 0
R6_BUILD_INPUT_CLOSURE = PASS
REPRODUCIBILITY_CLOSURE = PASS IF_CLAIMED/REQUIRED
PROOF_CARRYING_STAGE_CAPSULE = VALID
PRIVATE_IMPLEMENTATION_REQUIRED = FALSE
PRIVATE_MANUAL_PATCH_REQUIRED = FALSE
UNKNOWN = 0
NOT_PROVEN = 0
```

Public stage capsule hanya membawa public-safe material/evidence. Private receiver certificate, map, baseline internal, secret, state, dan conformance logs tidak boleh masuk capsule.

## 5. Deterministic Wiring Rehearsal

Sebelum handoff, Public menjalankan dari workspace bersih:

```text
FRESH PUBLIC WORKSPACE
-> CANONICAL SAFE CONTRACT
-> REFERENCE + ADVERSARIAL CONFORMANT DUMMY
-> EXACT PRODUCTION STAGE MATERIAL
-> APPLY MACHINE-READABLE STAGE WIRING MANIFEST
-> INDEPENDENT VERIFY GENERATED WIRING
-> FULL DUMMY APPLICATION/APK ASSEMBLY
-> INSTALL/RUN DEVELOPMENT TARGET
-> STARTUP / REGISTRY / ROUTES
-> STATE / LIFECYCLE / RESTART
-> FAILURE / RECOVERY / SAFE UI AS APPLICABLE
-> NEGATIVE / MUTATION
-> PACKAGE / PROVENANCE VALIDATION
-> PASS
```

Wiring compiler/generator tidak boleh menjadi satu-satunya oracle. Verifier independen harus menolak missing/duplicate/incompatible/undeclared binding, illegal dependency cycle/order, undeclared generated file, dan manual patch.

Property universal/critical yang tidak cukup diuji dengan contoh dialokasikan ke formal/exhaustive proof sesuai R9.

## 6. Reproducibility dan Provenance Public

R6 tetap berlaku penuh. Untuk claim deterministic/reproducible, lakukan clean independent assembly comparison sesuai R6. Bila format memang bit-reproducible, output digest harus identik; nondeterminism hanya boleh dinormalisasi bila telah dibuktikan non-semantic.

Public rehearsal/provenance wajib mengikat exact contract, stage wiring manifest, production materials, dependency/toolchain inputs, verifier/tool versions, environment dan output evidence.

Double-clean Public qualification tidak memberi izin melakukan double candidate build di Private.

## 7. Firebase

**Seluruh akses operasional Firebase/Test Lab hanya boleh dari Private. Public dilarang tanpa pengecualian dummy/prototype Public.**

Larangan mencakup connection check, autentikasi, catalog/model lookup, candidate preflight yang mengakses Firebase, upload/download artifact atau hasil, submit test matrix, dan caller/relay Firebase. Single-use approval tidak membuka jalur Public.

Public boleh mempelajari dokumentasi API terbuka dan menguji mock/fixture tanpa koneksi atau panggilan ke Firebase. Mode `connection-only`, `candidate-preflight`, serta final test adalah milik jalur Private dan tunduk pada policy Private. Final test memakai APK candidate Private yang sudah dibangun, ditandatangani, dan diverifikasi.

```text
PUBLIC_FIREBASE = FORBIDDEN
FIREBASE_EXECUTION_BOUNDARY = PRIVATE_ONLY
PRIVATE_FINAL_FIREBASE_DEFAULT = LOCKED
1 EXPLICIT USER APPROVAL = 1 PRIVATE FINAL FIREBASE EXECUTION ATTEMPT
NO AUTO RETRY
NO FALLBACK
```

## 8. Kegagalan Private yang Kembali ke Public

Public hanya boleh menerima `SANITIZED_FAILURE_REPORT` yang aman, misalnya:

- error ID;
- contract mismatch;
- unsupported version;
- dependency mismatch;
- receiver-slot/wiring-manifest mismatch generik;
- acceptance-schema mismatch;
- lifecycle/validation status generik.

Tidak boleh menerima source/asset/APK/artifact/log dump/path sensitif/state/secret/receiver map/certificate content Private.

## 9. Auto Cleanup Public

Setiap Public job wajib melakukan cleanup otomatis setelah sukses/gagal sejauh platform memungkinkan terhadap:

- workflow run/log yang dapat dihapus;
- artifact sementara;
- cache;
- workspace;
- temporary branch/ref;
- debug output;
- temporary test data.

Stage capsule dan evidence wajib yang menjadi satu-satunya proof harus dipertahankan sesuai retention sebelum cleanup.

## 10. Makna “100%”

Public tidak boleh mengklaim probabilitas first attempt 100%, final Private runtime PASS, atau bug-free absolut.

Claim yang boleh dihasilkan bila seluruh wiring closure terbukti adalah:

```text
DETERMINISTIC_WIRING_CLOSED = TRUE
WIRING_UNKNOWN = 0
WIRING_NOT_PROVEN = 0
```

Claim tersebut terikat exact contract/capsule/tool/verifier/environment assumptions dan tidak menggantikan receiver prerequisite/final witness Private.

## 11. Final Rule

```text
PUBLIC = RESEARCH / IMPLEMENTATION / ITERATION / DUAL-DUMMY / FULL-ASSEMBLY TEST / WHOLE-STAGE CLOSURE
PRIVATE_CONTENT_IN_PUBLIC = 0
PRIVATE_EXECUTION_THROUGH_PUBLIC = 0
PRIVATE_RECEIVER_MAP_IN_PUBLIC = 0
PRIVATE_CONFORMANCE_EVIDENCE_IN_PUBLIC = 0
DUMMY_DERIVED_FROM_PRIVATE = 0
PUBLIC_FINAL_BUILD = 0
PUBLIC_SIGNING_PRIVATE_CANDIDATE = 0
PUBLIC_FIREBASE_ACCESS = 0
PUBLIC_FIREBASE_EXECUTION = 0
PUBLIC_FIREBASE_DUMMY_EXCEPTION = 0
PUBLIC_JOB_AUTO_CLEANUP = REQUIRED
WIRING_COMPILER_IS_SOLE_ORACLE = FALSE
MANUAL_FIX_AFTER_PACKAGE_APPLY = 0
```
