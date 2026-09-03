# GLOBAL REPOSITORY INTEGRATION POLICY

## 1. Status

Dokumen ini menetapkan aturan integrasi lintas repository untuk semua project.

Wajib dibaca setelah `AGENTS.md` dan `GLOBAL_PUBLIC_PRIVATE_DEVELOPMENT_RULES.md`.

Dokumen ini sekaligus menjadi standar normatif aktif untuk **deterministic wiring / sealed integration** yang menerapkan Rule 0 §5.1–§5.9. Tidak ada dokumen paralel yang boleh melemahkan aturan ini. Jika terjadi konflik, Rule 0 tetap menang.

Jika dokumen lama atau workflow lama bertentangan dengan aturan global, aturan global menang.

## 2. Model Repository

Setiap project memiliki:

```text
PRIVATE MASTER
= SINGLE SOURCE OF TRUTH + VAULT + FINAL PROCESSING + PRIVATE EXECUTION

PUBLIC RESEARCH/STAGING
= RESEARCH + ITERATION + MOCK/SIMULATOR + PUBLIC TEST + WHOLE-STAGE PACKAGING
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

Pengujian penyambungan di Public WAJIB menggunakan dummy/surrogate mandiri dari contract aman, bukan APK baseline atau salinan/ekstraksi/penyamaran isi Private. Integrasi ke baseline/state final yang sebenarnya hanya dilakukan untuk tahap utuh setelah `STAGE_READY_PRIVATE`, otorisasi eksekusi, dan preflight PASS sesuai aturan global §6.

Private receiver mapping, receiver conformance certificate/evidence, baseline identity internal, config, cache, log, dan detail penerima tetap Private. Public hanya mengetahui contract/socket minimum yang memang diperlukan.

## 4. Arah Integrasi Resmi

```text
PUBLIC
RESEARCH
-> BUILD COMPONENT
-> CONTRACT / R1-R9 / FAILURE TEST
-> REFERENCE DUMMY + ADVERSARIAL CONFORMANT DUMMY
-> DETERMINISTIC WIRING REHEARSAL
-> FULL PUBLIC DUMMY APPLICATION ASSEMBLY
-> PACKAGE / PROVENANCE / REPRODUCIBILITY VALIDATION
-> COMPONENT_READY_PRIVATE (DITAHAN DI PUBLIC)
-> CLOSURE SELURUH TAHAP
-> PROOF-CARRYING STAGE CAPSULE
-> STAGE_READY_PRIVATE
-> OTORISASI EKSEKUSI TAHAP

                stage capsule only
                         |
                         v
PRIVATE MASTER
PREFLIGHT / CRYPTOGRAPHIC BINDING
-> SNAPSHOT
-> GENERATE DECLARED WIRING
-> INDEPENDENT VERIFY WIRING
-> ATOMIC APPLY
-> VERIFY TREE / DIFF
-> REGRESSION
-> COMMIT
-> BUILD APK
-> SIGN CANDIDATE
-> VERIFY SIGNATURE/HASH/PROVENANCE
-> FIREBASE / FINAL RUNTIME TEST
-> PASS
-> RELEASE
```

Public tidak mengetahui atau mengambil state final Private.

Istilah lama `INTEGRATE` pada dokumen historis/subordinat **tidak boleh** ditafsirkan sebagai ruang untuk coding/adaptation trial-and-error di Private. Untuk tahap yang mengklaim `PRIVATE_WIRING_ONLY`, maknanya adalah rantai deterministic di atas.

## 5. Stable Integration Plane

Project yang mempunyai kernel/registry/extension-point harus memelihara **Stable Integration Plane** atau mekanisme ekuivalen yang sekecil dan sestabil mungkin.

Integration Plane wajib:

- memiliki opaque/stable receiver slot IDs;
- memisahkan public-safe contract dari mapping internal;
- menyediakan extension point yang cukup agar tahap berikutnya tidak perlu menciptakan receiver behavior baru di Private;
- menghindari ketergantungan Public pada nama class/path/topology Private;
- mempunyai versioning dan compatibility rules fail-closed;
- dapat dibuktikan melalui receiver conformance tanpa mengekspor implementation.

Untuk ToolBox, Stage A adalah tempat fondasi Integration Plane karena scope tahap A mencakup kernel, registry, lifecycle, contract, dan safety boundary. Tahap berikutnya memakai plane yang sudah dikunci kecuali ada perubahan contract yang sah dan change-impact closure.

## 6. Canonical Safe Contract Artifact

Tidak boleh ada dua definisi contract yang hanya diasumsikan sama.

Satu canonical contract artifact/IDL/schema menjadi sumber bersama untuk shape/version wiring dan mempunyai exact digest, minimal:

```text
INTEGRATION_CONTRACT_ID
INTEGRATION_CONTRACT_VERSION
INTEGRATION_CONTRACT_SHA256
```

Dari canonical contract dapat dihasilkan/divalidasi Public SDK/interface, dummy receiver interface, conformance suite, wiring schema, Private receiver interface binding, dan Private conformance verifier.

Public dan Private harus mengikat exact contract digest yang sama. Mapping slot ke internal implementation tetap Private.

Canonical contract dilarang memuat source, secret, state, internal path, internal topology, private dependency detail, atau nama implementation yang tidak diperlukan.

## 7. Sealed Private Receiver Certificate

Receiver Private aktual harus mempunyai conformance evidence/certificate yang tetap Private dan minimal mengikat secara lokal:

```text
BASELINE_DIGEST
RECEIVER_CONTRACT_DIGEST
RECEIVER_ADAPTER_DIGEST
CONFORMANCE_SUITE_DIGEST
RECEIVER_MAPPING_DIGEST
TOOLCHAIN/ENVIRONMENT_BINDING_IF_MATERIAL
STATUS = PASS
```

Certificate/evidence tersebut tidak diekspor ke Public. Public tidak menerima log, config, mapping, atau ringkasan yang diturunkan dari isi Private.

Untuk menghindari penggunaan attempt tahap sebagai qualification loop, receiver certificate idealnya dibentuk/seal ketika baseline sebelumnya dikunci dan direuse selama receiver/contract/input yang menjadi dasar certificate tidak berubah.

Jika receiver berubah material, certificate lama invalid. Qualification baru yang memang diperlukan adalah pekerjaan Private nyata sesuai budget/authorization; tidak boleh disamarkan sebagai preflight gratis.

## 8. Dual Independent Public Dummy

Public wajib menguji exact promoted production package terhadap minimal dua implementasi receiver yang dibuat hanya dari safe contract:

1. `REFERENCE_DUMMY` — perilaku normal/representatif.
2. `ADVERSARIAL_CONFORMANT_DUMMY` — tetap legal menurut contract tetapi menggunakan batas/edge yang diperbolehkan.

Adversarial dummy dapat mencakup optional capability absent, minimum legal capacity, maximum legal latency, restricted/safe state, recovery state, process restart, failure surface, dan legal ordering/timing edge sesuai scope.

Kedua dummy harus independen dari isi Private dan sebisa mungkin mengurangi common-mode oracle/implementation yang sama.

Jika package hanya bekerja pada satu dummy tetapi gagal pada implementasi lain yang tetap conform, package bergantung pada asumsi yang tidak dinyatakan contract dan `STAGE_READY_PRIVATE = FALSE`.

## 9. Machine-Readable Stage Wiring Manifest

Setiap stage capsule wajib membawa manifest deterministic yang mengikat:

- project/stage ID;
- contract ID/version/digest;
- provider IDs dan exact material hashes;
- public slot ID -> provider ID;
- module registration;
- registry route;
- startup/lifecycle ordering;
- state/recovery/Safe UI/resource/diagnostic binding bila berlaku;
- manifest/resource/shrinker/build requirements;
- dependency/toolchain contract digests;
- acceptance schema version/digest;
- negative/fail-closed expectations.

Instruksi naratif seperti `buat adapter`, `cari tempat bootstrap`, `sesuaikan sampai compile`, atau `perbaiki jika gagal` dilarang sebagai syarat wiring final.

## 10. Deterministic Wiring Compiler dan Independent Translation Verifier

Wiring Private tidak boleh bergantung pada keputusan manusia saat attempt.

Gunakan deterministic wiring compiler/generator atau transformasi mesin ekuivalen:

```text
STAGE_WIRING_MANIFEST
+ PRIVATE_RECEIVER_MAP
+ CANONICAL_CONTRACT
-> GENERATED_BINDINGS
```

Untuk input yang sama, generated result harus sama secara semantic/digest sesuai contract.

Compiler hanya boleh menghasilkan kategori mekanis yang dideklarasikan: package/module placement, registration, slot/provider binding, startup binding, thin delegation tanpa semantic baru, build-graph connection, dan provenance/evidence.

Compiler tidak boleh menciptakan algoritma produk, policy baru, recovery behavior baru, UI behavior baru, dependency decision baru, atau business logic baru.

Compiler tidak boleh menjadi oracle tunggal bagi outputnya sendiri. Critical wiring wajib diverifikasi oleh **independent translation verifier** yang memperoleh expected graph/constraints secara terpisah dari implementation compiler.

Minimal verifier memeriksa:

```text
REQUIRED_SLOT_MISSING = 0
DUPLICATE_BINDING = 0
INCOMPATIBLE_PROVIDER = 0
UNDECLARED_BINDING = 0
ILLEGAL_DEPENDENCY_CYCLE = 0
STARTUP_ORDER_VIOLATION = 0
FORBIDDEN_NORMAL_PATH_FROM_RESTRICTED_STATE = 0
UNDECLARED_GENERATED_FILE = 0
MANUAL_PATCH = 0
```

Compiler dan verifier harus mengurangi common-mode failure; jangan sekadar memanggil implementation/parser/graph builder yang sama lalu membandingkan hasilnya sendiri.

## 11. Formal Wiring Model

Property universal/critical pada ruang wiring finite harus dialokasikan ke exhaustive/model checking/formal method bila testing contoh tidak cukup, sesuai R9-M08.

Property minimum bila berlaku:

- setiap required slot memiliki tepat satu provider compatible;
- optional slot mengikuti contract;
- tidak ada duplicate binding;
- tidak ada illegal cycle;
- startup partial order valid;
- restricted/safe state tidak dapat mencapai forbidden normal execution path;
- recovery transition/binding memenuhi invariant contract;
- provider/version compatibility selalu fail-closed.

Formal model harus ditrace ke manifest, generated wiring, dan implementation verifier. `FORMAL_MODEL_PASS` sendiri tidak cukup bila translation ke implementation tidak dibuktikan.

## 12. Full Public Assembly Rehearsal

Public wajib melakukan rehearsal dari fresh workspace dengan exact promoted production materials:

```text
FRESH WORKSPACE
-> CANONICAL CONTRACT
-> REFERENCE DUMMY + ADVERSARIAL DUMMY
-> EXACT STAGE CAPSULE
-> DETERMINISTIC WIRING APPLY
-> INDEPENDENT VERIFY
-> FULL DUMMY APPLICATION ASSEMBLY
-> INSTALL/RUN DEVELOPMENT TARGET
-> STARTUP/REGISTRY/ROUTES
-> STATE/LIFECYCLE/RESTART
-> FAILURE/RECOVERY/SAFE UI AS APPLICABLE
-> NEGATIVE/MUTATION
-> PACKAGE/PROVENANCE VALIDATION
-> PASS
```

Public dummy build bukan final Private build dan tidak boleh memakai Firebase/Test Lab.

Invariant:

```text
MANUAL_FIX_AFTER_APPLY = 0
PROMOTED_SOURCE_CHANGE_AFTER_REHEARSAL = 0
PRIVATE_CONTENT_USED = 0
DUMMY_DERIVED_FROM_PRIVATE = 0
UNKNOWN = 0
NOT_PROVEN = 0
```

## 13. Reproducible / Hermetic Public Qualification

R6 berlaku penuh. Untuk material yang mengklaim deterministic/reproducible, lakukan minimal dua clean independent Public assembly runs dengan input yang sama dan bandingkan output sesuai R6.

Jika format yang diuji bit-reproducible, digest output harus identik bit-for-bit. Jika ada nondeterminism yang dibuktikan non-semantic, normalisasi hanya field yang contract membuktikan non-semantic.

Double-build/reproducibility di Public tidak menjadi alasan melakukan double candidate build di Private. Private menggunakan satu candidate build bila qualification/reuse evidence sudah sah.

## 14. Promotion Package / Proof-Carrying Stage Capsule

Perpindahan Public -> Private hanya melalui Promotion Package **tahap utuh** dengan `STAGE_READY_PRIVATE` dan otorisasi eksekusi yang berlaku. `COMPONENT_READY_PRIVATE` atau output lama `READY_PRIVATE` komponen tidak memberi izin promosi/integrasi. Kesiapan tahap bukan final PASS. Definisi dan batas satu attempt mengikuti aturan global §6.1–§6.3.

Promotion Package harus diperlakukan sebagai **proof-carrying stage capsule** dan mengikat minimal:

- Stage ID dan versi scope;
- seluruh sublangkah/komponen wajib;
- production source/assets;
- module/build descriptors;
- canonical contract ID/version/digest;
- machine-readable Stage Wiring Manifest;
- dependency/toolchain lock/digests;
- compatibility matrix;
- acceptance/handoff schema version/digest;
- R1–R9 applicability/evidence;
- reference + adversarial dummy evidence;
- full Public assembly/reproducibility evidence;
- formal/independent wiring verification evidence bila berlaku;
- provenance/attestation;
- capsule root digest;
- batas final witness yang masih pending di Private.

Private-only receiver certificate, internal receiver map, secrets, signing material, final baseline, state, dan internal toolchain evidence tetap Private.

Private wajib menolak capsule yang identity/hash/contract/dependency/compatibility/wiring/acceptance/provenance binding-nya tidak valid.

## 15. Public Closure Wajib Sebelum Handoff

Sebelum `STAGE_READY_PRIVATE`, Public wajib membuktikan:

```text
CANONICAL_CONTRACT_FROZEN = TRUE
REFERENCE_DUMMY = PASS
ADVERSARIAL_CONFORMANT_DUMMY = PASS
PUBLIC_FULL_ASSEMBLY_REHEARSAL = PASS
WIRING_MANIFEST_COMPLETE = TRUE
WIRING_COMPILER_DETERMINISM = PASS
INDEPENDENT_TRANSLATION_VERIFIER = PASS
FORMAL_WIRING_COUNTEREXAMPLE = 0 IF_APPLICABLE
R6_BUILD_INPUT_CLOSURE = PASS
REPRODUCIBILITY_CLOSURE = PASS IF_CLAIMED/REQUIRED
PROOF_CARRYING_CAPSULE = VALID
PRIVATE_IMPLEMENTATION_REQUIRED = FALSE
PRIVATE_MANUAL_PATCH_REQUIRED = FALSE
UNKNOWN = 0
NOT_PROVEN = 0
```

Public tidak boleh mengklaim final Private runtime PASS dari dummy.

## 16. Private Preflight / Cryptographic Admission

Sebelum dispatch, pastikan closure tahap lengkap, izin, budget, attempt ID, serta tidak ada attempt ganda. Status unknown/komponen parsial tidak boleh diteruskan.

Preflight murah wajib memeriksa sebelum workload berat:

```text
CAPSULE ID/HASH/ROOT DIGEST
-> CANONICAL CONTRACT DIGEST
-> STAGE WIRING MANIFEST / SCHEMA DIGEST
-> ACCEPTANCE SCHEMA DIGEST
-> DEPENDENCY / TOOLCHAIN COMPATIBILITY BINDING
-> PRIVATE RECEIVER CERTIFICATE FRESHNESS
-> RECEIVER CONTRACT DIGEST MATCH
-> ENVIRONMENT / BASELINE PREREQUISITE
-> NO-PRIVATE-IMPLEMENTATION CONDITIONS
```

Jika satu saja mismatch/unknown/stale: `STOP`.

Jangan menggunakan build/integrasi berat untuk menemukan mismatch yang dapat ditemukan di admission.

## 17. Transaction, Atomic Apply, dan Rollback

Sebelum apply:

```text
CURRENT_FINAL
-> FREEZE EXACT INPUTS
-> SNAPSHOT
-> GENERATE DECLARED WIRING
-> INDEPENDENT VERIFY
-> ATOMIC APPLY
-> VERIFY TREE / DIFF
```

Generated wiring hanya boleh mengubah declared mechanical zones: package/module placement, registration, slot/provider binding, startup binding, thin delegation tanpa semantic baru, build-graph connection, dan provenance/evidence.

Jika perlu algoritma, adapter semantic baru, dependency decision baru, resource policy baru, recovery/UI behavior baru, manual compile fix, atau desain baru:

```text
PRIVATE_DEVELOPMENT_DETECTED
-> STOP
-> ROLLBACK IF NEEDED
-> SANITIZED_FAILURE_REPORT
-> PUBLIC RECLOSURE
```

PASS -> regression -> commit state baru.

FAIL -> rollback ke state sebelumnya.

State final sebelumnya harus tetap dapat dipulihkan.

## 18. Private Execution Boundary

Seluruh pekerjaan yang memerlukan isi Private berjalan di boundary Private.

Untuk aplikasi final, urutan resmi adalah:

```text
COMMITTED PRIVATE STATE
-> ONE CANDIDATE BUILD
-> SIGN CANDIDATE
-> VERIFY SIGNATURE / HASH / PROVENANCE
-> FIREBASE / FINAL RUNTIME TEST
-> PASS
-> RELEASE
```

APK yang dipakai untuk final test harus sudah ditandatangani dan diverifikasi. Artifact release harus identik secara identity/hash/signature dengan candidate yang memperoleh final PASS.

GitHub Actions diperbolehkan untuk build/test Private hanya jika workflow, source, asset, secret, artifact, log, dan seluruh jalur eksekusinya tetap berada pada boundary Private dan tidak menggunakan repository Public sebagai executor/relay.

## 19. Receiver Lifecycle

Untuk memaksimalkan keberhasilan first attempt tahap berikutnya:

```text
STAGE N PRIVATE PASS
-> LOCK BASELINE N
-> SEAL/REVALIDATE PRIVATE RECEIVER CERTIFICATE
-> FREEZE RECEIVER/CONTRACT DIGESTS
-> STAGE N+1 DEVELOPED IN PUBLIC
```

Perubahan material receiver/contract setelah seal menginvalidasi certificate dan seluruh proof Public yang bergantung pada contract tersebut melalui change-impact analysis.

Receiver conformance evidence tidak diekspor ke Public.

## 20. Kegagalan di Private

**Dilarang keras trial-and-error berulang di Private.**

Jika gagal:

```text
STOP
-> ROLLBACK bila diperlukan
-> SANITIZED_FAILURE_REPORT
-> PUBLIC
-> FIX/RETEST
-> CLOSURE ULANG TAHAP TERDAMPAK
-> STAGE_READY_PRIVATE
-> STOP / KEPUTUSAN DAN IZIN ATTEMPT BARU
```

Yang boleh keluar ke Public hanya error/compatibility information yang telah disanitasi.

## 21. Sanitized Failure Report

Boleh memuat Error ID, contract mismatch, unsupported version, dependency mismatch, receiver-slot/manifest mismatch generik, acceptance-schema mismatch, atau lifecycle/validation status generik.

Dilarang memuat source/asset Private, secret/token, path sensitif, database/state, APK/artifact Private, internal receiver map/certificate content, internal dump, konfigurasi internal, atau detail kernel yang membuka isi Private.

## 22. Shared Component

Komponen lintas project harus berstatus eksplisit `GLOBAL/SHARED_COMPONENT` dan memiliki source resmi, version, contract, dependency, compatibility, serta test evidence. Dilarang mengambil komponen repo lain secara acak.

## 23. Public Auto Cleanup

Setiap Public job wajib memiliki cleanup otomatis setelah selesai/gagal sejauh platform memungkinkan. Stage capsule dan evidence wajib dipertahankan secara tahan lama sebelum output sementara dihapus sesuai aturan global §12. Cleanup tidak pernah menggantikan larangan Private -> Public.

## 24. Build dan Test Boundary

Public boleh build/test hanya terhadap komponen/data Public, canonical safe contract, dummy/simulator, fixture, atau prototype.

Public boleh membangun **full dummy application/APK** untuk rehearsal jika seluruh isinya Public + dummy receiver. Itu bukan final application build dan tidak boleh memakai Private source/asset/state/credential/artifact.

Final application build, signing, signature verification, final runtime test, dan release dilakukan pada mesin/jalur Private.

Seluruh akses operasional Firebase/Test Lab hanya boleh dari Private. Public dilarang melakukan connection check, catalog/model lookup, preflight yang mengakses Firebase, upload/download, atau test matrix, termasuk terhadap dummy/prototype Public.

Seluruh input dependency/toolchain/perintah per fase harus dikunci dan compatibility/perbedaan environment dibuktikan menurut R6.

## 25. Project Isolation

Setiap transfer wajib mempunyai sumber, tujuan, Project ID, Component ID/version, contract ID/version/digest, dan tujuan integrasi yang jelas. Asset, source, config, state, keputusan, dan pembahasan project lain tidak boleh dicampurkan tanpa instruksi eksplisit pengguna.

## 26. Repository Role Registry

Peran repo harus dinyatakan di `AGENTS.md` repo masing-masing. Role yang diperbolehkan antara lain `PRIVATE_MASTER`, `PUBLIC_RESEARCH_STAGING`, `PRIVATE_BACKUP`, dan `GLOBAL_SHARED_COMPONENT`.

## 27. Makna 100% dan Claim yang Sah

Dilarang menyatakan:

```text
FIRST_ATTEMPT_SUCCESS_PROBABILITY = 100%
ALL_INFRASTRUCTURE_FAILURE_IMPOSSIBLE = TRUE
BUG_FREE_ABSOLUTE = TRUE
```

Jika seluruh closure wiring terbukti, claim yang sah adalah:

```text
DETERMINISTIC_WIRING_CLOSED = TRUE
WIRING_UNKNOWN = 0
WIRING_NOT_PROVEN = 0
```

Claim tersebut selalu terikat exact scope, contract/capsule/receiver certificate digests, tooling/verifier versions, dan assumptions yang dicatat. R9 residual-assumption register tetap berlaku.

## 28. Tooling Strategy

Jangan membuat banyak workflow baru untuk setiap verifier. Preferensi:

```text
private_admission_gate.py
  + capsule/contract/receiver binding checks

apk-candidate.yml
  + deterministic wiring apply
  + independent wiring verification
  + existing regression/build/sign/verify
```

Capability baru boleh berupa script/library/verifier yang dipanggil workflow existing. Workflow baru hanya bila benar-benar tidak dapat diakomodasi tanpa menduplikasi capability, sesuai registry tooling Private.

## 29. Research Basis

Metode ini menggabungkan prinsip dari:

- consumer/provider contract verification — Pact provider verification: https://docs.pact.io/provider
- reproducible builds definition: https://reproducible-builds.org/docs/definition/
- Gradle dependency locking: https://docs.gradle.org/current/userguide/dependency_locking.html
- Gradle dependency verification: https://docs.gradle.org/current/userguide/dependency_verification.html
- hermetic build principles: https://bazel.build/basics/hermeticity
- SLSA provenance: https://slsa.dev/spec/v1.2/provenance
- in-toto supply-chain layout/link model: https://in-toto.io/docs/getting-started/
- Alloy model analysis/counterexample search: https://alloytools.org/faq/what_kind_of_analysis_does_the_alloy_analyzer_do.html
- TLA+/TLC state-machine/model checking: https://lamport.azurewebsites.net/tla/tla.html

Referensi adalah dasar metode. Implementasi project tetap mengikuti R1–R9, boundary, dan toolchain yang sudah tersedia. Tidak ada kewajiban mengganti Gradle dengan Bazel atau menambah tool hanya karena disebut sebagai referensi.

## 30. Invariant

```text
PRIVATE_CONTENT_TO_PUBLIC = 0
PUBLIC_PRIVATE_READ_ACCESS = 0
PRIVATE_EXECUTION_THROUGH_PUBLIC = 0
PRIVATE_FINAL_BUILD_IN_PUBLIC = 0
PRIVATE_ARTIFACT_RELAY_THROUGH_PUBLIC = 0
PRIVATE_RECEIVER_MAP_EXPORTED = 0
PRIVATE_CONFORMANCE_EVIDENCE_EXPORTED = 0
DUMMY_DERIVED_FROM_PRIVATE = 0
FIREBASE_EXECUTION_BOUNDARY = PRIVATE_ONLY
PUBLIC_FIREBASE_ACCESS = 0
PUBLIC_FIREBASE_EXECUTION = 0
PRIVATE_TRIAL_AND_ERROR = 0
COMPONENT_READY_PRIVATE_AUTHORIZES_PRIVATE = FALSE
SUBSTEP_IS_PRIVATE_INTEGRATION_BOUNDARY = FALSE
PRIVATE_INTEGRATION_BOUNDARY = WHOLE_APPROVED_STAGE
STAGE_READY_PRIVATE_AND_AUTHORIZATION = REQUIRED
DETERMINISTIC_WIRING_CLOSED_REQUIRED_FOR_WIRING_CLAIM = TRUE
WIRING_COMPILER_IS_SOLE_ORACLE = FALSE
PRIVATE_IMPLEMENTATION_DURING_STAGE_APPLY = 0
MANUAL_PRIVATE_PATCH = 0
AUTO_RETRY_PRIVATE = FORBIDDEN
UNSANITIZED_FAILURE_REPORT = 0
UNDECLARED_CROSS_PROJECT_TRANSFER = 0
PUBLIC_JOB_AUTO_CLEANUP = REQUIRED
PRIVATE_FINAL_ORDER = BUILD -> SIGN -> VERIFY_SIGNATURE -> FINAL_TEST -> PASS -> RELEASE
```
