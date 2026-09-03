# GLOBAL REPOSITORY INTEGRATION POLICY

## 1. Status

Dokumen ini menetapkan aturan integrasi lintas repository untuk semua project.

Wajib dibaca setelah `AGENTS.md` dan `GLOBAL_PUBLIC_PRIVATE_DEVELOPMENT_RULES.md`.

Untuk pekerjaan promotion/handoff/wiring satu tahap, `DETERMINISTIC_WIRING_STANDARD.md` juga WAJIB dibaca dan diterapkan. Dokumen tersebut adalah implementasi normatif dari Rule 0 §5.1–§5.9; jika ada konflik, Rule 0 tetap menang.

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

## 5. Stable Integration Plane dan Canonical Contract

Project yang mempunyai kernel/registry/extension-point harus memelihara Stable Integration Plane atau mekanisme ekuivalen sesuai `DETERMINISTIC_WIRING_STANDARD.md`.

Canonical safe contract harus menjadi sumber tunggal shape/version wiring yang dibagikan secara aman. Public dan Private mengikat exact contract digest yang sama, tetapi mapping slot ke internal implementation tetap Private.

Untuk ToolBox, Stage A adalah tempat fondasi Integration Plane karena scope tahap A mencakup kernel, registry, lifecycle, contract, dan safety boundary. Tahap berikutnya memakai plane yang sudah dikunci kecuali ada perubahan contract yang sah dan change-impact closure.

## 6. Promotion Package / Proof-Carrying Stage Capsule

Perpindahan Public -> Private hanya melalui Promotion Package **tahap utuh** dengan `STAGE_READY_PRIVATE` dan otorisasi eksekusi yang berlaku. `COMPONENT_READY_PRIVATE` atau output lama `READY_PRIVATE` komponen tidak memberi izin promosi/integrasi. Kesiapan tahap bukan final PASS. Definisi dan batas satu attempt mengikuti aturan global §6.1–§6.3.

Promotion Package harus diperlakukan sebagai **proof-carrying stage capsule** dan mengikat minimal:

- Stage ID dan versi scope;
- seluruh sublangkah/komponen wajib;
- paket anggota/source/asset/build-descriptor hashes;
- canonical contract ID/version/digest;
- machine-readable Stage Wiring Manifest;
- registry/route/lifecycle/startup binding;
- dependency/toolchain lock/digest dan compatibility contract;
- acceptance/handoff schema version/digest;
- R1–R9 applicability/evidence;
- reference + adversarial dummy evidence;
- full Public assembly rehearsal evidence;
- reproducibility/hermeticity evidence bila berlaku;
- formal/independent wiring verification evidence bila berlaku;
- provenance/attestation;
- capsule root digest;
- batas final witness yang masih pending di Private.

Reference baseline/toolchain/adapter/receiver certificate dan evidence penerima Private disimpan pada catatan penerimaan Private; tidak diekspor ke Public.

Minimal metadata setiap paket anggota tetap mencakup:

- Project ID;
- Component ID/version;
- Contract version/digest;
- dependency/toolchain lock/digest;
- target platform;
- hash/checksum;
- compatibility;
- test status;
- promotion manifest.

Private wajib menolak capsule yang identity/hash/contract/dependency/compatibility/wiring/acceptance/provenance binding-nya tidak valid.

## 7. Public Closure Wajib Sebelum Handoff

Sebelum `STAGE_READY_PRIVATE`, Public wajib membuktikan menurut Rule 0 dan `DETERMINISTIC_WIRING_STANDARD.md`:

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
PROOF_CARRYING_CAPSULE = VALID
PRIVATE_IMPLEMENTATION_REQUIRED = FALSE
PRIVATE_MANUAL_PATCH_REQUIRED = FALSE
UNKNOWN = 0
NOT_PROVEN = 0
```

Public tidak boleh mengklaim final Private runtime PASS dari dummy.

## 8. Private Preflight / Cryptographic Admission

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

Receiver certificate/evidence tetap Private dan idealnya sudah diseal saat baseline sebelumnya dikunci. Qualification baru yang diperlukan adalah operasi Private nyata, bukan preflight gratis.

Jika satu saja mismatch/unknown/stale: `STOP`.

Jangan menggunakan build/integrasi berat untuk mencari tahu kesalahan yang seharusnya dapat ditemukan oleh admission/preflight.

## 9. Transaction, Deterministic Wiring, dan Rollback

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

## 10. Private Execution Boundary

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

Double clean/reproducible qualification sebaiknya dihabiskan di Public bila dapat dibuktikan tanpa isi Private. Jangan menjadikan double Private candidate build sebagai rutinitas; reuse qualification yang masih sah sesuai R6.

## 11. Receiver Lifecycle

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

## 12. Kegagalan di Private

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

## 13. Sanitized Failure Report

Boleh memuat:

- Error ID;
- contract mismatch;
- unsupported version;
- dependency mismatch;
- receiver-slot/manifest mismatch generik;
- acceptance-schema mismatch;
- lifecycle/validation status generik.

Dilarang memuat:

- source/asset Private;
- secret/token;
- path sensitif;
- database/state;
- APK/artifact Private;
- internal receiver map/certificate content;
- internal dump;
- konfigurasi internal;
- detail kernel yang membuka isi Private.

## 14. Shared Component

Komponen lintas project harus berstatus eksplisit `GLOBAL/SHARED_COMPONENT` dan memiliki:

- source resmi;
- version;
- contract;
- dependency;
- compatibility;
- test evidence.

Dilarang mengambil komponen repo lain secara acak.

## 15. Public Auto Cleanup

Setiap Public job wajib memiliki cleanup otomatis setelah selesai/gagal sejauh platform memungkinkan.

Target cleanup:

- workflow run/log;
- artifact sementara;
- cache;
- workspace;
- branch/ref sementara;
- debug output sementara;
- temporary test data.

Stage capsule dan evidence wajib dipertahankan secara tahan lama sebelum output sementara dihapus sesuai aturan global §12. Cleanup tidak pernah dianggap pengganti larangan Private -> Public.

## 16. Build dan Test Boundary

Public boleh build/test hanya terhadap komponen/data Public, canonical safe contract, dummy/simulator, fixture, atau prototype.

Public boleh membangun **full dummy application/APK** untuk rehearsal jika seluruh isinya Public + dummy receiver. Itu bukan final application build dan tidak boleh memakai Private source/asset/state/credential/artifact.

Final application build, signing, signature verification, final runtime test, dan release dilakukan pada mesin/jalur Private.

Seluruh akses operasional Firebase/Test Lab hanya boleh dari Private. Public dilarang melakukan connection check, catalog/model lookup, preflight yang mengakses Firebase, upload/download, atau test matrix, termasuk terhadap dummy/prototype Public. Single-use approval hanya berlaku untuk final execution Private, bukan pengecualian Public. Riset dokumentasi API terbuka serta mock/fixture tanpa koneksi/panggilan Firebase tetap diperbolehkan sesuai aturan global §9.1.

Seluruh input dependency/toolchain/perintah per fase harus dikunci dan compatibility/perbedaan environment dibuktikan menurut R6. Kesamaan nama versi atau keberhasilan satu dummy tidak membuktikan seluruh input/receiver; karena itu dual dummy, exact digest binding, dan receiver prerequisite tetap wajib sesuai scope.

## 17. Project Isolation

Setiap transfer wajib mempunyai sumber, tujuan, Project ID, Component ID/version, contract ID/version/digest, dan tujuan integrasi yang jelas.

Asset, source, config, state, keputusan, dan pembahasan project lain tidak boleh dicampurkan tanpa instruksi eksplisit pengguna.

## 18. Repository Role Registry

Peran repo harus dinyatakan di `AGENTS.md` repo masing-masing.

Role yang diperbolehkan antara lain:

- `PRIVATE_MASTER`
- `PUBLIC_RESEARCH_STAGING`
- `PRIVATE_BACKUP`
- `GLOBAL_SHARED_COMPONENT`

Nama aplikasi/repo tidak boleh menjadi syarat agar aturan global berlaku.

## 19. Makna 100% dan Claim yang Sah

Dilarang menyatakan probabilitas first attempt 100%, bug-free absolut, atau semua infrastructure failure mustahil.

Jika seluruh closure wiring terbukti, claim yang sah adalah:

```text
DETERMINISTIC_WIRING_CLOSED = TRUE
WIRING_UNKNOWN = 0
WIRING_NOT_PROVEN = 0
```

Claim tersebut selalu terikat exact scope, contract/capsule/receiver certificate digests, tooling/verifier versions, dan assumptions yang dicatat. R9 residual-assumption register tetap berlaku.

## 20. Invariant

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
DETERMINISTIC_WIRING_STANDARD_REQUIRED = TRUE
WIRING_COMPILER_IS_SOLE_ORACLE = FALSE
PRIVATE_IMPLEMENTATION_DURING_STAGE_APPLY = 0
MANUAL_PRIVATE_PATCH = 0
AUTO_RETRY_PRIVATE = FORBIDDEN
UNSANITIZED_FAILURE_REPORT = 0
UNDECLARED_CROSS_PROJECT_TRANSFER = 0
PUBLIC_JOB_AUTO_CLEANUP = REQUIRED
PRIVATE_FINAL_ORDER = BUILD -> SIGN -> VERIFY_SIGNATURE -> FINAL_TEST -> PASS -> RELEASE
```
