# APP_SAFE_R9_VERIFICATION_COMPLETENESS.md

## 1. Status

Korpus metode aktif untuk **R9 — Application-Wide Verification Completeness** dalam framework `APPLICATION_SAFE_100`.

R9 tidak menggantikan R1–R8 dan tidak menjadi tempat memindahkan fault domain yang belum selesai. R9 membuktikan bahwa seluruh domain, claim, evidence, cross-domain interaction, fault challenge, dan verifier yang diwajibkan telah lengkap dan masih valid.

Status riset: `PRACTICAL_SATURATION` terhadap ruang metode assurance/V&V yang telah disapu.

Untuk seluruh keputusan test environment dan Firebase authorization, R9 WAJIB mengikuti `TEST_ROUTING_POLICY.md`. R9 tidak mempunyai kewenangan membuka Firebase sendiri.

**Batas Firebase wajib:** seluruh akses/pengecekan/eksekusi Firebase/Test Lab hanya dari Private. Public tidak boleh memakainya, termasuk untuk dummy/prototype. Penyebutan Firebase dan approval di dokumen ini adalah requirement final Private, bukan izin Public; pengujian komponen Public memakai mock/simulator mandiri tanpa koneksi Firebase sampai `READY_PRIVATE`.

---

## 2. Scope

R9 menutup:

- closed application failure universe;
- requirements/risk/fault-to-proof traceability;
- coverage completeness across R1–R8 and assets;
- cross-domain interaction failures;
- claim-evidence assurance argument;
- independent verification/oracle diversity;
- test/proof adequacy;
- mutation/fault injection adequacy;
- verifier/tool qualification;
- evidence provenance/freshness;
- change-impact invalidation;
- unknown/omitted/skipped closure;
- final `APPLICATION_SAFE_100` decision.

Target produk utama tetap Android 11/API30/ARM64. Environment GitHub untuk development/regression boleh fleksibel. Jika final target-specific witness masih diperlukan, R9 harus menandainya sebagai pending Final Gate dan tidak boleh menjalankan Firebase tanpa single-use approval pengguna.

---

## 3. Metode Aktif

### R9-M01 — Closed Application Failure Universe
Gabungkan fault models dari R1–R8, `ASSET_SAFE_100`, build/package/runtime platform, lalu deduplicate tanpa menghapus semantic distinctions. Fault class baru yang ditemukan saat implementation/test wajib masuk universe sebelum final PASS.

### R9-M02 — Requirement / Risk / Fault / Method / Evidence Traceability
Setiap required behavior dan fault class harus dapat ditelusuri:

```text
REQUIREMENT / RISK
-> FAULT CLASS
-> PREVENTION / PROOF METHOD
-> TEST / VERIFIER
-> EVIDENCE
-> CLAIM
```

Unmapped required row = `NOT_PROVEN`.

### R9-M03 — Domain Ownership Matrix
Setiap failure mechanism mempunyai owner utama R1–R8/ASSET. Cross-domain rule boleh memiliki lebih dari satu owner, tetapi tidak boleh ada gap karena dua domain saling menganggap domain lain yang bertanggung jawab.

### R9-M04 — Cross-Domain Interaction Model
Model interaksi yang dapat menghasilkan failure hanya saat beberapa domain bertemu, misalnya lifecycle+network+persistence, R8+R2, build+reflection+R7, asset+UI+locale, process-death+transaction+retry. Bentuk factors/constraints dan expected invariant.

### R9-M05 — Combinatorial Interaction Coverage
Gunakan t-way covering arrays untuk configuration/state/domain factors bila exhaustive Cartesian product tidak feasible. Interaction strength wajib ditetapkan dari fault/risk model dan coverage harus dihitung; t-way tidak boleh diklaim exhaustive di atas strength tersebut.

Development combinations boleh dijalankan pada GitHub environment yang tersedia dan relevan. Environment non-target tidak boleh diperluas menjadi final Android 11 ARM64 claim.

### R9-M06 — State / Transition / Sequence Coverage
Gabungkan state machines R1–R8 yang relevan dan ukur required state, transition, transition-pair/sequence, off-nominal transition, restart/recovery sequence, and forbidden-state challenge coverage.

### R9-M07 — Structural / Decision / Data-Flow Adequacy
Untuk code yang masuk critical scope, gunakan branch/condition/MC-DC/data-flow coverage sesuai rigor yang ditentukan. Coverage metric hanya adequacy indicator dan tidak menjadi correctness proof sendiri.

### R9-M08 — Formal Proof Allocation
Property yang universal/critical dan tidak dapat cukup dibuktikan dengan testing harus dialokasikan ke model checking, theorem proving, abstract interpretation, symbolic proof, or equivalent formal method. Bound/assumption harus eksplisit.

### R9-M09 — Independent Verification & Validation
Critical claims mendapat second independent assessment yang tidak sekadar menjalankan tool/config/oracle identik. Independence bisa berupa reviewer, independently derived model/oracle, alternate implementation, or separate verifier depending on claim.

### R9-M10 — Oracle Diversity / Common-Mode Failure Analysis
Bila beberapa oracle agree, analisis apakah semuanya berasal dari source/model/library yang sama. Common-mode dependency wajib dicatat; agreement dependent tidak dihitung sebagai independent confirmation.

### R9-M11 — Metamorphic / Differential / Reference Challenge
Gunakan metamorphic relation, differential implementation, reference model, or golden corpus untuk menantang exact oracle dan menemukan systematic implementation/oracle error yang dapat lolos self-consistency tests.

### R9-M12 — Mutation / Fault-Seeding Completeness
Untuk setiap fault class yang dapat dimutasi secara meaningful, buat mutation/negative test. Verifier harus mendeteksi seluruh mutation yang non-equivalent. Fault class yang tidak bisa dimutasi perlu alternate evidence/challenge, bukan dihapus.

### R9-M13 — Fault Injection / Chaos at Controlled Boundaries
Inject failures pada storage, network, process, dependency, thread/resource, hardware, IPC, startup, update, and recovery boundary sesuai contract. Chaos tanpa invariant/oracle hanya supporting, bukan proof.

GitHub fault-injection environment boleh fleksibel. Jika fault mechanism membutuhkan target-specific Android 11 ARM64 behavior, final witness dipisahkan dan tunduk pada Final Gate approval.

### R9-M14 — Fuzzing Portfolio Coverage
Gunakan grammar-aware, stateful, coverage-guided, API/protocol, native, serialization, and external-boundary fuzzing sesuai target. Seed corpus, dictionary/schema, time/resource budget, crash deduplication, and regression corpus harus dipertahankan.

Fuzzing development tidak wajib menunggu Android 11 ARM64 environment jika fault class dapat ditutup secara sah pada environment lain. Scope claim harus sesuai evidence.

### R9-M15 — Test Oracle / Reference Corpus Qualification
Critical parser/serializer/format/protocol test harus mempunyai valid/invalid reference corpus yang provenance-nya diketahui. Corpus incompleteness tidak boleh disamakan dengan format proof universal.

### R9-M16 — Tool Qualification / Trusted-Base Closure
Daftar compiler, static analyzer, sanitizer, test runner, emulator/device, script, proof checker, parser, diff/oracle tool yang menjadi trusted base. Verifikasi version/configuration, sanity tests, known limitations, and self/mutation checks where feasible.

Environment GitHub dan Firebase harus dicatat terpisah. GitHub non-target bukan Firebase/target witness.

### R9-M17 — Evidence Provenance & Binding
Setiap evidence wajib mengikat minimal source revision/input set, tool/version/config, environment/device where material, build/artifact digest, test/proof ID, timestamp/run identity, and result. Evidence dari artifact/revision lain tidak dapat dipakai ulang tanpa equivalence proof.

Untuk Firebase evidence, binding juga wajib mencatat execution repository/boundary Private serta approval pengguna yang berlaku untuk attempt itu sesuai `TEST_ROUTING_POLICY.md`. Evidence dari eksekusi Public tidak sah untuk menutup claim Firebase.

### R9-M18 — Evidence Freshness / Change-Impact Closure
Setiap change menghasilkan impact set. Evidence affected wajib invalidated. Final acceptance requires `STALE_EVIDENCE = 0` and no unknown dependency between changed item and proof.

Perubahan kandidat juga membatalkan unused Firebase approval yang terikat ke kandidat sebelumnya.

### R9-M19 — Fail-Closed Unknown / Skipped / Inconclusive Handling
Status `UNKNOWN`, `SKIPPED`, `NOT_RUN`, `NOT_LOADED`, `INCOMPLETE`, `INDETERMINATE_TOOL`, `ASSUMED`, and `NOT_PROVEN` tidak boleh dihitung PASS.

Tool failure wajib diperbaiki/rerun atau claim tetap tertutup, **tetapi aturan ini tidak memberi izin auto-retry Firebase**. Setiap Firebase execution attempt baru membutuhkan explicit user approval baru.

### R9-M20 — Negative Assurance / Defeater Analysis
Untuk setiap final claim, cari alasan yang dapat membuat claim salah: missing requirement, wrong oracle, common-mode tool bug, unsupported device class, hidden dynamic path, stale evidence, incorrect model abstraction, unexpected environment, unmodeled cross-domain interaction. Semua material defeater wajib resolved atau claim dibatasi.

### R9-M21 — Assurance Case / Claim-Evidence Graph
Bangun graph argument:

```text
TOP CLAIM
-> SUBCLAIMS R1..R8 + ASSET + CROSS-DOMAIN
-> ASSUMPTIONS
-> EVIDENCE
-> DEFEATERS / RESOLUTION
```

No dangling required claim, no evidence without claim, no claim with hidden assumption.

Jika target-specific Android 11 ARM64 witness belum diotorisasi pengguna, graph harus menunjukkan node tersebut sebagai pending/unproven; tidak boleh menyelesaikannya dengan menjalankan Firebase otomatis.

### R9-M22 — Residual-Assumption / Trusted-Base Register
Semua assumption yang tidak dibuktikan harus eksplisit, mempunyai owner, scope, justification, and monitoring/revalidation rule. `APPLICATION_SAFE_100` hanya boleh mengklaim 100% terhadap closed domain yang memasukkan assumption boundary tersebut.

### R9-M23 — Production Observability as Supporting Discovery
Crash/ANR/native tombstone/health telemetry boleh digunakan untuk menemukan unmodeled fault dan regression setelah release. Absence of field failures bukan proof `APPLICATION_SAFE_100`; temuan baru wajib memperluas fault model dan invalidasi claim terdampak.

### R9-M24 — Independent Final Acceptance Review
Final decision harus memeriksa seluruh matrices, evidence bindings, mutation escapes, unresolved findings, stale proof, tool failures, scope boundary, dan bila Firebase dipakai, validitas single-use user approval untuk execution yang menghasilkan evidence tersebut. Build success/test count/coverage percentage tunggal tidak boleh menjadi final oracle.

---

## 4. Firebase Authority Boundary

R9 boleh menyimpulkan:

```text
FINAL_TARGET_WITNESS_REQUIRED = YES
```

Tetapi R9 DILARANG mengubah kesimpulan tersebut menjadi permission Firebase.

Pada scope Public, tutup proof komponen sampai `PACKAGE_VALIDATION -> READY_PRIVATE`, lalu kirim Promotion Package ke Private. Target-specific final witness tetap tanggung jawab Private; Public tidak menjalankan Firebase.

Alur Firebase hanya di Private, setelah seluruh prerequisite kandidat menurut `TEST_ROUTING_POLICY.md` terpenuhi:

```text
PRIVATE REQUIRED VERIFICATION + SIGNED CANDIDATE + SIGNATURE/HASH/PROVENANCE = PASS
-> STOP
-> ask user
-> explicit approval for this Private candidate?
   NO  -> Firebase LOCKED
   YES -> one Firebase execution attempt from Private only
```

Jika satu attempt selesai atau gagal:

```text
approval consumed
-> Firebase LOCKED again
```

Retry di Private memerlukan approval baru. Approval tidak memberi izin untuk menjalankan Firebase dari Public.

---

## 5. Metode yang Tidak Menjadi Proof Mandiri

Tidak cukup sendiri:

- statement/line coverage tinggi;
- jumlah test besar;
- “semua unit test hijau”;
- statistical reliability / operational profile;
- exploratory testing;
- AI/LLM review/generation;
- bug-free history;
- zero crash telemetry;
- one device/emulator;
- one static analyzer;
- one fuzz campaign;
- one formal model yang tidak ditrace ke implementation;
- assurance case tanpa objective evidence.

Teknik tersebut boleh menjadi supporting evidence sesuai metode aktif.

---

## 6. Final Fault / Evidence Closure

R9 wajib menghasilkan:

```text
TOTAL_REQUIRED_CLAIMS = N
PROVEN_CLAIMS = N
UNPROVEN_CLAIMS = 0
TOTAL_FAULT_CLASSES = F
FAULT_CLASSES_CHALLENGED = F
FAULT_ESCAPE = 0
UNOWNED_FAULT_CLASS = 0
UNKNOWN = 0
SKIPPED = 0
INDETERMINATE = 0
STALE_EVIDENCE = 0
UNRESOLVED_DEFEATER = 0
UNDECLARED_MATERIAL_ASSUMPTION = 0
```

Jika target-specific evidence wajib belum tersedia karena user belum memberi Final Gate approval, maka final R9 closure belum dapat diberikan; development status tetap boleh dipertahankan sesuai `TEST_ROUTING_POLICY.md`.

---

## 7. PASS Formula

`APP_SAFE_R9_PASS` hanya jika:

```text
APP_SAFE_R1_PASS
AND APP_SAFE_R2_PASS
AND APP_SAFE_R3_PASS
AND APP_SAFE_R4_PASS
AND APP_SAFE_R5_PASS
AND APP_SAFE_R6_PASS
AND APP_SAFE_R7_PASS
AND APP_SAFE_R8_PASS
AND REQUIRED_ASSET_PROOF_PASS_IF_APPLICABLE
AND CrossDomainInteractionClosure
AND ClaimEvidenceClosure
AND FaultModelClosure
AND MutationFaultChallengeClosure
AND ToolQualificationClosure
AND EvidenceFreshnessClosure
AND DefeaterClosure
AND UNKNOWN = 0
AND SKIPPED = 0
AND UNPROVEN = 0
AND FAULT_ESCAPE = 0
```

R9 PASS adalah prasyarat final `APPLICATION_SAFE_100`, bukan sinonim otomatis bila scope/platform boundary belum dikunci.

R9 PASS juga bukan alasan untuk menjalankan Firebase tambahan. Setiap Firebase final execution hanya di Private dan tetap memerlukan approval pengguna baru untuk setiap attempt.
