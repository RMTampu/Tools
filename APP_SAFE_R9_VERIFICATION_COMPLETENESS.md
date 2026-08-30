# APP_SAFE_R9_VERIFICATION_COMPLETENESS.md

## 1. Status

Korpus metode aktif untuk **R9 — Application-Wide Verification Completeness** dalam framework `APPLICATION_SAFE_100`.

R9 tidak menggantikan R1–R8 dan tidak menjadi tempat memindahkan fault domain yang belum selesai. R9 membuktikan bahwa seluruh domain, claim, evidence, cross-domain interaction, fault challenge, dan verifier yang diwajibkan telah lengkap dan masih valid.

Status riset: `PRACTICAL_SATURATION` terhadap ruang metode assurance/V&V yang telah disapu.

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

### R9-M14 — Fuzzing Portfolio Coverage
Gunakan grammar-aware, stateful, coverage-guided, API/protocol, native, serialization, and external-boundary fuzzing sesuai target. Seed corpus, dictionary/schema, time/resource budget, crash deduplication, and regression corpus harus dipertahankan.

### R9-M15 — Test Oracle / Reference Corpus Qualification
Critical parser/serializer/format/protocol test harus mempunyai valid/invalid reference corpus yang provenance-nya diketahui. Corpus incompleteness tidak boleh disamakan dengan format proof universal.

### R9-M16 — Tool Qualification / Trusted-Base Closure
Daftar compiler, static analyzer, sanitizer, test runner, emulator/device, script, proof checker, parser, diff/oracle tool yang menjadi trusted base. Verifikasi version/configuration, sanity tests, known limitations, and self/mutation checks where feasible.

### R9-M17 — Evidence Provenance & Binding
Setiap evidence wajib mengikat minimal source revision/input set, tool/version/config, environment/device where material, build/artifact digest, test/proof ID, timestamp/run identity, and result. Evidence dari artifact/revision lain tidak dapat dipakai ulang tanpa equivalence proof.

### R9-M18 — Evidence Freshness / Change-Impact Closure
Setiap change menghasilkan impact set. Evidence affected wajib invalidated. Final acceptance requires `STALE_EVIDENCE = 0` and no unknown dependency between changed item and proof.

### R9-M19 — Fail-Closed Unknown / Skipped / Inconclusive Handling
Status `UNKNOWN`, `SKIPPED`, `NOT_RUN`, `NOT_LOADED`, `INCOMPLETE`, `INDETERMINATE_TOOL`, `ASSUMED`, and `NOT_PROVEN` tidak boleh dihitung PASS. Tool failure wajib diperbaiki/rerun atau claim tetap tertutup.

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

### R9-M22 — Residual-Assumption / Trusted-Base Register
Semua assumption yang tidak dibuktikan harus eksplisit, mempunyai owner, scope, justification, and monitoring/revalidation rule. `APPLICATION_SAFE_100` hanya boleh mengklaim 100% terhadap closed domain yang memasukkan assumption boundary tersebut.

### R9-M23 — Production Observability as Supporting Discovery
Crash/ANR/native tombstone/health telemetry boleh digunakan untuk menemukan unmodeled fault dan regression setelah release. Absence of field failures bukan proof `APPLICATION_SAFE_100`; temuan baru wajib memperluas fault model dan invalidasi claim terdampak.

### R9-M24 — Independent Final Acceptance Review
Final decision harus memeriksa seluruh matrices, evidence bindings, mutation escapes, unresolved findings, stale proof, tool failures, and scope boundary. Build success/test count/coverage percentage tunggal tidak boleh menjadi final oracle.

---

## 4. Metode yang Tidak Menjadi Proof Mandiri

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

## 5. Final Fault / Evidence Closure

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

---

## 6. PASS Formula

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
