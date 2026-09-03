# DETERMINISTIC WIRING & SEALED INTEGRATION STANDARD

## 1. Status dan Otoritas

Dokumen ini adalah standar normatif aktif untuk mencapai sasaran **Public build-ready dan Private wiring-only** tanpa membuka isi Private.

Dokumen ini menerapkan dan memperinci `GLOBAL_PUBLIC_PRIVATE_DEVELOPMENT_RULES.md` §5.1–§5.9, `REPOSITORY_INTEGRATION_POLICY.md`, R6, R9, dan aturan satu tahap/satu attempt. Jika terjadi konflik, urutan otoritas tetap mengikuti `AGENTS.md` dan Rule 0.

Tujuan standar ini bukan membuat klaim probabilistik bahwa seluruh infrastruktur tidak mungkin gagal. Istilah yang sah adalah:

```text
DETERMINISTIC_WIRING_CLOSED = TRUE
```

Artinya: untuk exact sealed inputs, exact contract, exact compatible receiver certificate, dan closed wiring model yang telah diverifikasi, tidak ada unresolved wiring path yang dapat menghasilkan mismatch sambungan. Ini **bukan** klaim `FIRST_ATTEMPT_SUCCESS_PROBABILITY = 100%` dan bukan jaminan terhadap outage, hardware failure, layanan eksternal, atau fault di luar closed model.

## 2. Sasaran Arsitektur

Model wajib:

```text
PRIVATE INTERNAL IMPLEMENTATION
        |
        v
STABLE INTEGRATION PLANE
        |
        v
CANONICAL SAFE CONTRACT ARTIFACT
       / \
      /   \
     v     v
PUBLIC      PRIVATE
PACKAGE     RECEIVER
CONFORMANCE CONFORMANCE
     \       /
      \     /
       SAME CONTRACT DIGEST
             |
             v
DETERMINISTIC WIRING COMPILER
             |
             v
INDEPENDENT TRANSLATION VERIFIER
             |
             v
ATOMIC APPLY -> BUILD -> SIGN -> VERIFY
```

Public hanya mengetahui bentuk socket minimum yang memang diperlukan. Mapping slot ke class/object/path/state/topology internal tetap Private.

## 3. Stable Integration Plane

Untuk project yang mempunyai kernel/registry/extension-point, baseline harus menyediakan **Stable Integration Plane** atau mekanisme ekuivalen yang sekecil dan sestabil mungkin.

Integration Plane wajib:

- memiliki opaque/stable receiver slot IDs;
- memisahkan public-safe contract dari mapping internal;
- menyediakan extension point yang cukup agar tahap berikutnya tidak perlu menciptakan receiver behavior baru di Private;
- menghindari ketergantungan Public pada nama class/path/topology Private;
- mempunyai versioning dan compatibility rules fail-closed;
- dapat dibuktikan melalui receiver conformance tanpa mengekspor implementation.

Untuk ToolBox, Stage A adalah tahap fondasi yang tepat untuk membangun/mengunci Integration Plane karena scope A memang kernel, registry, lifecycle, contract, dan safety boundary. Stage berikutnya harus memakai plane yang sudah terkunci kecuali ada contract change yang sah dan direview.

## 4. Canonical Contract Artifact

Tidak boleh ada dua definisi contract yang hanya diasumsikan sama.

Satu canonical contract artifact/IDL/schema menjadi sumber bersama, misalnya:

```text
INTEGRATION_CONTRACT_ID
INTEGRATION_CONTRACT_VERSION
INTEGRATION_CONTRACT_SHA256
```

Dari canonical contract dapat dihasilkan atau divalidasi:

- Public SDK/interface;
- Dummy Receiver interface;
- contract conformance suite;
- wiring manifest schema;
- Private receiver interface binding;
- Private conformance verifier.

Public dan Private harus mengikat **exact contract digest yang sama**. Contract mismatch = STOP sebelum attempt/build.

Canonical contract tidak boleh memuat source, secret, state, internal path, internal topology, private dependency detail, atau nama implementation yang tidak diperlukan.

## 5. Sealed Private Receiver Certificate

Receiver Private aktual harus mempunyai conformance evidence/certificate yang **tetap Private**.

Minimal mengikat secara lokal:

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

Untuk menghindari penggunaan attempt Private sebagai qualification loop, receiver certificate idealnya dibentuk/seal ketika baseline sebelumnya dikunci dan direuse selama receiver/contract/input yang menjadi dasar certificate tidak berubah.

Jika receiver berubah secara material, certificate lama invalid. Jika qualification baru benar-benar diperlukan, ia harus diperlakukan sebagai pekerjaan Private nyata sesuai budget/authorization; tidak boleh disamarkan sebagai preflight gratis.

## 6. Dual Independent Public Dummy

Public wajib menguji exact promoted production package terhadap minimal dua implementasi receiver yang dibuat hanya dari safe contract:

1. `REFERENCE_DUMMY` — perilaku normal dan representatif.
2. `ADVERSARIAL_CONFORMANT_DUMMY` — tetap legal menurut contract tetapi menggunakan batas/edge yang diperbolehkan.

Adversarial dummy dapat mencakup bila relevan:

- optional capability absent;
- minimum legal capacity;
- maximum legal latency;
- restricted/safe state;
- recovery state;
- process restart;
- failure surface yang dideklarasikan;
- legal ordering/timing edge.

Kedua dummy harus independen dari isi Private dan sebisa mungkin menghindari common-mode oracle/implementation yang sama.

Jika package hanya bekerja pada satu dummy tetapi gagal pada implementasi lain yang tetap conform, berarti package bergantung pada asumsi yang tidak dinyatakan contract dan `STAGE_READY_PRIVATE = FALSE`.

## 7. Machine-Readable Wiring Manifest

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
- acceptance schema version;
- negative/fail-closed expectations.

Instruksi naratif seperti `buat adapter`, `cari tempat bootstrap`, `sesuaikan sampai compile`, atau `perbaiki jika gagal` dilarang sebagai syarat wiring final.

## 8. Deterministic Wiring Compiler

Wiring Private tidak boleh bergantung pada keputusan manusia saat attempt.

Gunakan deterministic wiring compiler/generator atau transformasi mesin ekuivalen:

```text
STAGE_WIRING_MANIFEST
+ PRIVATE_RECEIVER_MAP
+ CANONICAL_CONTRACT
-> GENERATED_BINDINGS
```

Untuk input yang sama, generated result harus sama secara semantic/digest sesuai contract.

Compiler hanya boleh menghasilkan kategori mekanis yang dideklarasikan, misalnya:

- package/module placement;
- registration;
- slot/provider binding;
- startup binding;
- thin delegation tanpa semantic baru;
- build-graph connection;
- generated provenance.

Compiler tidak boleh menciptakan algoritma produk, policy baru, recovery behavior baru, UI behavior baru, dependency decision baru, atau business logic baru.

## 9. Independent Translation Verifier

Wiring compiler tidak boleh menjadi oracle tunggal bagi outputnya sendiri.

Critical wiring wajib diverifikasi oleh verifier independen yang memperoleh expected graph/constraints secara terpisah dari implementasi compiler.

Minimal memeriksa:

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

Compiler dan verifier harus mengurangi common-mode failure: jangan sekadar memanggil fungsi parser/graph builder yang sama lalu membandingkan hasilnya sendiri.

## 10. Formal Wiring Model

Property universal/critical pada ruang wiring finite harus dialokasikan ke exhaustive/model checking atau formal method bila testing contoh tidak cukup.

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

## 11. Full Public Assembly Rehearsal

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

## 12. Reproducible / Hermetic Public Qualification

R6 berlaku penuh. Untuk material yang mengklaim deterministic/reproducible, lakukan minimal dua clean independent Public assembly runs dengan input yang sama dan bandingkan output sesuai R6:

```text
CLEAN_RUN_A_INPUT_DIGEST = CLEAN_RUN_B_INPUT_DIGEST
CLEAN_RUN_A_SEMANTIC_OUTPUT = CLEAN_RUN_B_SEMANTIC_OUTPUT
```

Jika format yang diuji memang bit-reproducible, digest output harus identik bit-for-bit. Jika ada nondeterminism yang dibuktikan non-semantic, normalisasi hanya field yang contract membuktikan non-semantic.

Double-build/reproducibility di Public tidak menjadi alasan melakukan double candidate build di Private. Private menggunakan satu candidate build bila qualification/reuse evidence sudah sah.

## 13. Proof-Carrying Stage Capsule

Promotion Package tahap ditingkatkan menjadi **proof-carrying stage capsule**. Minimal membawa material Public yang diperlukan:

- production source/assets;
- module/build descriptors;
- canonical contract ID/version/digest;
- stage wiring manifest;
- dependency/toolchain lock/digests;
- compatibility matrix;
- reference + adversarial dummy evidence;
- Public assembly/reproducibility evidence;
- formal/independent-verification evidence bila berlaku;
- R1–R9 closure/evidence index;
- provenance/attestation;
- capsule root digest.

Private-only receiver certificate, internal receiver map, secrets, signing material, final baseline, state, dan internal toolchain evidence tetap Private.

## 14. Cryptographic Admission Handshake

Sebelum workload mahal, Private admission wajib membandingkan exact bindings yang aman:

```text
CAPSULE.CONTRACT_DIGEST == PRIVATE_RECEIVER_CERT.CONTRACT_DIGEST
CAPSULE.WIRING_SCHEMA_DIGEST == PRIVATE_ALLOWED_WIRING_SCHEMA_DIGEST
CAPSULE.ACCEPTANCE_SCHEMA_DIGEST == PRIVATE_ACCEPTANCE_SCHEMA_DIGEST
CAPSULE.DEPENDENCY/TOOLCHAIN_CONTRACT == ACCEPTED_PRIVATE_COMPATIBILITY_BINDING
CAPSULE_ROOT_DIGEST == EXPECTED_PROMOTION_DIGEST
```

Mismatch/unknown/stale = STOP. Jangan mencoba build untuk menemukan mismatch yang dapat ditemukan di admission.

## 15. Atomic Private Apply

Setelah admission PASS:

```text
INPUTS FROZEN
-> SNAPSHOT
-> GENERATE DECLARED WIRING
-> INDEPENDENT VERIFY
-> ATOMIC APPLY
-> VERIFY TREE/DIFF
-> REGRESSION
-> COMMIT
-> ONE CANDIDATE BUILD
-> SIGN
-> VERIFY SIGNATURE/HASH/PROVENANCE
```

Private diff selama tahap wiring dibatasi ke declared mechanical zones. Perubahan behavior di luar zona tersebut menghasilkan:

```text
PRIVATE_DEVELOPMENT_DETECTED
-> STOP
-> ROLLBACK IF NEEDED
-> SANITIZED_FAILURE_REPORT
-> PUBLIC RECLOSURE
```

## 16. Baseline-Sealed Receiver Lifecycle

Untuk memaksimalkan keberhasilan first attempt tahap berikutnya:

```text
STAGE N PRIVATE PASS
-> LOCK BASELINE N
-> SEAL/REVALIDATE RECEIVER CERTIFICATE FOR NEXT SAFE CONTRACT
-> FREEZE RECEIVER/CONTRACT DIGESTS
-> STAGE N+1 DEVELOPED IN PUBLIC
```

Dengan pola ini receiver qualification tidak menunggu sampai stage N+1 hendak dipasang.

Perubahan receiver/contract setelah seal menginvalidasi certificate dan seluruh Public proof yang bergantung pada contract tersebut melalui change-impact analysis.

## 17. Definisi Closure

`STAGE_READY_PRIVATE` yang memakai standar ini hanya sah bila:

```text
CANONICAL_CONTRACT_FROZEN = TRUE
PUBLIC_PACKAGE_CONFORMANCE = PASS
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

Receiver conformance/certificate sendiri tetap Private dan harus tersedia/fresh sebagai `PRIVATE_PREREQUISITE` sebelum attempt, bukan diekspor ke Public.

## 18. Makna “100%” yang Diizinkan

Dilarang menyatakan:

```text
FIRST_ATTEMPT_SUCCESS_PROBABILITY = 100%
ALL_INFRASTRUCTURE_FAILURE_IMPOSSIBLE = TRUE
BUG_FREE_ABSOLUTE = TRUE
```

Yang dapat dinyatakan bila seluruh proof terpenuhi:

```text
DETERMINISTIC_WIRING_CLOSED = TRUE
WIRING_UNKNOWN = 0
WIRING_NOT_PROVEN = 0
```

Claim tersebut selalu terikat exact scope, contract digest, receiver certificate, capsule digest, tooling/verifier versions, dan assumptions yang tercatat. R9 residual-assumption register tetap berlaku.

## 19. Tooling Strategy

Jangan membuat banyak workflow baru untuk setiap verifier. Preferensi:

```text
private_admission_gate.py
  + capsule/contract/receiver binding checks

apk-candidate.yml
  + deterministic wiring apply
  + independent wiring verification
  + existing regression/build/sign/verify
```

Capability baru boleh berupa script/library/verifier yang dipanggil workflow existing. Workflow baru hanya bila benar-benar tidak dapat diakomodasi tanpa menduplikasi capability, sesuai `PRIVATE_EXECUTION_TOOLING.md`.

## 20. Research Basis

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

Referensi ini adalah dasar metode; implementasi ToolBox tetap mengikuti R1–R9, aturan boundary, dan toolchain yang sudah tersedia. Tidak ada kewajiban mengganti Gradle dengan Bazel atau menambah tool hanya karena disebut sebagai referensi.

## 21. Invariant Final

```text
PUBLIC_KNOWS_PRIVATE_IMPLEMENTATION = 0
PRIVATE_RECEIVER_MAP_EXPORTED = 0
PRIVATE_CONFORMANCE_EVIDENCE_EXPORTED = 0
DUMMY_DERIVED_FROM_PRIVATE = 0
PUBLIC_FIREBASE_ACCESS = 0
PRIVATE_IMPLEMENTATION_DURING_STAGE_APPLY = 0
MANUAL_PRIVATE_PATCH = 0
WIRING_COMPILER_IS_SOLE_ORACLE = FALSE
STAGE_CAPSULE_EXACTLY_BOUND = TRUE
PRIVATE_WIRING_ONLY = TRUE
```
