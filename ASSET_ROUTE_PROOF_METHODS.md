# ASSET_ROUTE_PROOF_METHODS.md

## 1. Status Dokumen

Dokumen ini adalah korpus metode aktif hasil riset untuk membuktikan kebenaran jalur komunikasi asset/resource sebelum build pada repository `RMTampu/Tools`.

Dokumen ini hanya memuat metode yang telah diterima setelah penyaringan, penggabungan metode yang tumpang tindih, audit kontra, dan penghapusan metode yang lebih lemah/redundan/tidak menambah jaminan.

Metode yang ditolak tidak menjadi bagian dari aturan aktif dan tidak boleh dipakai untuk menggantikan atau melemahkan metode di dokumen ini.

Dokumen ini melengkapi `ASSET_SAFE_100_RULES.md`. `ASSET_SAFE_100_RULES.md` tetap menjadi sumber aturan asset umum, sedangkan dokumen ini memperdalam proof khusus jalur consumer/resource/reference/resolution/authority/semantic route sebelum build.

Seluruh eksekusi wajib mengikuti `AGENT_PROCEDURE_EXECUTION_RULES.md`.

---

## 2. Batas Klaim

Klaim proof hanya sah terhadap domain tertutup yang didefinisikan secara eksplisit.

Domain minimal yang harus ditutup bila relevan:

- asset universe;
- consumer universe;
- route/reference universe;
- configuration universe;
- state universe;
- authority/capability universe;
- observer/observation universe;
- assumption universe;
- supported context/module universe;
- fault model;
- Android 11/API 30 resource-resolution semantics yang menjadi target;
- dynamic lookup registry;
- external/generated inputs yang diizinkan contract.

Tidak boleh mengubah domain diam-diam untuk memaksa proof menjadi PASS.

Jika domain yang diperlukan masih terbuka atau tidak diketahui:

```text
ROUTE_PROOF = NOT_PROVEN
```

---

## 3. Prinsip Monotonik

Metode dalam dokumen ini dipakai sebagai satu sistem terintegrasi.

Aturan:

- metode yang lebih kuat menyerap metode yang lebih lemah;
- komponen unik yang menambah jaminan dipertahankan;
- dua metode tidak boleh menghasilkan dua sumber kebenaran yang saling bersaing;
- penambahan metode baru hanya boleh memperkuat proof, bukan membatalkan invariant aktif;
- jika dua metode berlaku pada level berbeda, keduanya tetap dipertahankan sebagai lapisan berbeda;
- keberhasilan tool/solver/checker tidak menggantikan theorem, contract, atau evidence yang diwajibkan.

---

# 4. Closed Semantic Domain & Assumption Closure

Sebelum route dibuktikan, wajib membentuk domain semantik tertutup.

Harus tersedia minimal:

```text
CLOSED_ASSET_UNIVERSE
CLOSED_CONSUMER_UNIVERSE
CLOSED_ROUTE_UNIVERSE
CLOSED_CONTEXT_UNIVERSE
CLOSED_AUTHORITY_UNIVERSE
CLOSED_CONFIGURATION_UNIVERSE
CLOSED_STATE_UNIVERSE
CLOSED_OBSERVATION_UNIVERSE
CLOSED_ASSUMPTION_UNIVERSE
CLOSED_FAULT_MODEL
```

Setiap assumption wajib mempunyai:

- ID;
- provenance;
- scope;
- alasan diperlukan;
- claim yang bergantung padanya;
- cara validasi/falsifikasi;
- status evidence.

Dilarang menggunakan hidden assumption sebagai dasar PASS.

Wajib:

```text
HIDDEN_ASSUMPTION = 0
UNVALIDATED_ASSUMPTION = 0
OVERCONSTRAINING_ASSUMPTION = 0
VACUOUS_PROOF = 0
```

---

# 5. Independent Semantic Oracle Convergence

Semantic intent jalur tidak boleh berasal dari satu representasi tunggal yang dipercaya tanpa challenge.

Untuk route kritis, bentuk evidence/oracle yang benar-benar independen sejauh domain memungkinkan, misalnya:

- goal/requirement oracle;
- consumer-contract oracle;
- scenario oracle;
- negative-scenario oracle;
- viewpoint oracle;
- metamorphic oracle;
- obstacle/anti-goal oracle;
- observation oracle.

Independensi harus dinilai dari provenance. Dua oracle yang otomatis dihasilkan dari sumber sama tidak dianggap dua bukti independen.

Aturan:

```text
ORACLE_DISAGREEMENT > 0 -> NOT_PROVEN
COMMON_MODE_EVIDENCE_GAP > 0 -> NOT_PROVEN
```

Tidak menggunakan majority vote untuk menutup konflik semantik.

---

# 6. Observational Completeness

Kesetaraan semantic route tidak boleh ditentukan hanya karena dua endpoint terlihat sama pada abstraksi yang terlalu kasar.

Bangun observer language dari consumer contract dan supported context.

Dua endpoint/model hanya boleh dianggap ekuivalen bila tidak ada observer yang diizinkan yang dapat membedakannya dalam configuration/state yang termasuk scope.

Jika contract menuntut exact identity, observational similarity tidak boleh mengganti exact-address requirement.

Wajib membedakan:

- exact-address contract;
- semantic-address contract;
- stateful behavioral equivalence;
- contextual observational equivalence.

Jika ada distinguishing observer yang valid:

```text
DECLARED_EQUIVALENCE = INVALID
```

---

# 7. Unique Global Semantic Route Model

Local edge correctness tidak cukup. Seluruh specification route harus menentukan satu model global yang sah, modulo equivalence yang sudah dibuktikan.

Wajib mencari model alternatif kedua.

Konsep challenge:

```text
find M1 satisfying specification
then constrain M != M1
search M2
```

Jika model kedua yang observably berbeda masih memenuhi seluruh constraint:

```text
GLOBAL_UNDERSPECIFICATION
ROUTE_PROOF = NOT_PROVEN
```

Wajib ada keputusan eksplisit untuk:

- owner setiap semantic decision;
- endpoint final;
- alias/share relation;
- dependency relation;
- influence relation;
- authority relation;
- config/state partition;
- tie-break rule yang memang bagian contract.

Incidental ordering tidak boleh menjadi penentu semantic route.

---

# 8. Defeater-Driven Epistemic Closure

Positive evidence saja tidak cukup untuk claim route kritis.

Bangun claim/defeater graph yang mencakup minimal:

- rebutting defeater: claim dapat salah;
- undercutting defeater: evidence/inference dapat tidak dapat dipercaya;
- scope incompleteness;
- assumption invalidity;
- common-mode evidence;
- mapping/model-to-reality mismatch;
- overconstraint/vacuity;
- hidden context/authority;
- alternative domain model;
- unsupported equivalence;
- unresolved observer distinction.

Setiap defeater material wajib menghasilkan proof obligation, test, countermodel search, mutation, scenario, atau evidence tambahan.

Untuk claim 100 dalam closed domain:

```text
UNRESOLVED_DEFEATER = 0
MATERIAL_RESIDUAL_DOUBT = 0
SURVIVING_COUNTERMODEL = 0
DOMAIN_MODEL_CONFLICT = 0
DEFEATER_MUTATION_ESCAPE = 0
```

Jika ada residual doubt material yang tidak dapat dieliminasi, jangan memaksa PASS.

---

# 9. Causal Dependency & Intervention Check

Dependency graph tidak boleh hanya dibangun dari korelasi, naming similarity, atau static co-occurrence bila hubungan sebab-akibat penting terhadap semantic route.

Untuk dependency/influence kritis, gunakan intervention/counterfactual challenge bila dapat dimodelkan:

- ubah satu input/decision yang diklaim sebagai sebab;
- tahan faktor lain sesuai contract;
- amati apakah endpoint/resolution/behavior berubah sebagaimana model memprediksi;
- challenge spurious dependency dan hidden dependency.

Wajib:

```text
SPURIOUS_CRITICAL_DEPENDENCY = 0
HIDDEN_CRITICAL_DEPENDENCY = 0
```

Causal check tidak menggantikan graph proof; ia memperkuat validitas graph/influence model.

---

# 10. Closed Verified Route Language

Jalur kritis sebisa mungkin tidak boleh tersebar sebagai free-form string/manual routing dalam Kotlin/XML/config tanpa domain tertutup.

Gunakan representasi route tertutup/deklaratif yang memiliki:

- grammar formal/canonical representation;
- typed identities;
- semantic roles;
- explicit consumer/producer relation;
- explicit alias/dependency relation;
- explicit configuration/state condition;
- explicit authority/capability;
- finite dynamic registry;
- no arbitrary unresolved name generation.

Target desain:

```text
INVALID_ROUTE -> unrepresentable atau ditolak sebelum generation
```

Manual/direct bypass harus dideteksi dan menjadi violation kecuali secara eksplisit menjadi bagian contract dan proof universe.

---

# 11. Route Synthesis + Translation Validation

Jika memungkinkan, route implementation harus dihasilkan dari model yang sudah dibuktikan, bukan ditulis ulang manual.

Pipeline ideal:

```text
SEMANTIC SPEC
-> TYPED ROUTE MODEL
-> CANONICAL IR
-> GENERATED ROUTE/BINDINGS
-> TRANSLATION VALIDATION
```

Setelah generation, wajib membuktikan bahwa hasil konkret mempertahankan model asal.

Tidak boleh menganggap generator benar hanya karena generator adalah tool resmi/internal.

Wajib mendeteksi:

- dropped edge;
- added edge;
- remapped endpoint;
- semantic weakening;
- unauthorized strengthening;
- namespace/module shift;
- capability widening;
- config/state condition drift.

---

# 12. Proof-Carrying Route & Foundational Checking

Setiap route/model kritis harus mempunyai machine-checkable evidence sejauh tooling tersedia.

Solver, analyzer, linter, atau generator hanya boleh menghasilkan candidate result/certificate; PASS akhir harus bergantung pada checker/proof obligation yang eksplisit.

Target proof mencakup bila relevan:

- parser/elaboration correctness;
- type/role correctness;
- route existence;
- uniqueness;
- totality pada supported domain;
- no dangling edge;
- no unauthorized route;
- semantic preservation;
- authority confinement;
- observational correctness;
- config/state resolution correctness;
- negative-space theorem.

Negative-space theorem:

```text
seluruh route yang diizinkan = route yang dispesifikasikan
route tambahan yang tidak dispesifikasikan = 0
```

Trusted base harus dibuat sekecil dan sejelas mungkin.

---

# 13. Robust Contextual & Authority Preservation

Route yang benar pada modul sendiri belum cukup bila context lain dapat mem-bypass, memalsukan, atau memperluas authority.

Untuk consumer/module/context yang termasuk scope, wajib periksa:

- exact consumer authority;
- no ambient asset authority yang tidak diperlukan;
- capability confinement;
- no capability leak;
- controlled delegation;
- role preservation;
- cross-module route closure;
- reflection/JNI/serialization/plugin path bila digunakan;
- third-party code yang dapat memperoleh access ke resource/asset manager bila termasuk supported context.

Jika arbitrary code di luar proof universe masih mempunyai unrestricted authority yang dapat mengubah semantic route, claim robust route 100 tidak boleh diberikan.

---

# 14. Hyperproperty & Compositional Contract Proof

Sebagian correctness bersifat relational: membandingkan dua execution/context/state, bukan satu execution saja.

Gunakan assume/guarantee/hypercontract-style obligations bila diperlukan untuk membuktikan:

- non-interference;
- determinism;
- observational equivalence;
- confidentiality/authority separation yang relevan;
- route invariance terhadap perubahan yang seharusnya tidak berpengaruh;
- valid composition antar-module.

Setiap component contract harus menunjukkan assumption dan guarantee secara eksplisit.

Composition PASS hanya bila assumptions antar-component terpenuhi tanpa circular unsupported assumption.

---

# 15. Heterogeneous Semantic Translation Preservation

Jika requirement/oracle/model menggunakan formalisme berbeda, kesamaan hasil secara informal tidak cukup.

Setiap translation penting harus mempunyai mapping semantics yang menunjukkan bahwa makna yang relevan dipertahankan.

Wajib memeriksa:

- source concept tidak hilang;
- target concept tidak memperoleh authority/meaning tambahan tanpa contract;
- satisfiability/consistency tetap sesuai;
- observational obligations tidak berubah;
- config/state/role semantics tidak menyimpang.

Translation yang tidak dapat dibuktikan cukup kuat menjadi `NOT_PROVEN` untuk jalur yang bergantung padanya.

---

# 16. Android Resource Resolution Refinement

Model route abstrak harus dipetakan ke semantics nyata Android 11/API 30.

Wajib memeriksa sesuai scope:

- resource namespace/module;
- merge/overlay winner;
- qualifier selection;
- default/fallback;
- theme/style/attr resolution;
- alias/reference chain;
- dynamic lookup registry;
- generated resources;
- shrinking/keep behavior bila digunakan;
- `res/`, `assets/`, `raw/`, loader-specific path;
- package/final resource identity.

Gunakan model independent/static proof dan cross-check dengan behavior resolver/tooling Android bila memungkinkan.

Perbedaan antara expected model dan Android resolution:

```text
RESOLUTION_MODEL_MISMATCH -> NOT_PROVEN atau FAIL_ASSET sesuai bukti
```

---

# 17. Complete Fault-Domain Challenge

Mutation yang dipilih manual bukan satu-satunya dasar fault closure.

Untuk bagian route yang dapat dimodelkan sebagai finite-state/I/O system, gunakan complete conformance test generation bila fault domain yang dibatasi memungkinkan.

Tujuan:

```text
setiap implementation yang salah di dalam CLOSED_FAULT_MODEL
harus dibedakan oleh sekurang-kurangnya satu test/witness
```

Untuk bagian yang tidak memenuhi syarat complete finite conformance, gunakan kombinasi:

- systematic mutation;
- adversarial countermodel search;
- negative scenario;
- alternative-domain challenge;
- metamorphic relation;
- resolver differential check;
- fault injection yang masih berada dalam declared fault model.

Dilarang mengklaim complete fault coverage di luar fault model yang benar-benar ditutup.

---

# 18. Active Countermodel / Alternative-Model Elimination

Proof wajib aktif mencari model lain yang konsisten dengan evidence tetapi menghasilkan route berbeda.

Search dapat dilakukan terhadap:

- semantic intent;
- endpoint assignment;
- dependency/influence graph;
- configuration partition;
- authority distribution;
- observer equivalence;
- assumption set;
- Android mapping.

Jika alternative supported model bertahan setelah seluruh evidence diterapkan:

```text
ROUTE_MODEL_NOT_UNIQUE
ROUTE_PROOF = NOT_PROVEN
```

---

# 19. Consumer-to-Asset Exact Binding

Untuk setiap consumer yang termasuk scope, wajib diketahui:

```text
Consumer-ID
Semantic Role
Required Asset-ID
Allowed Endpoint Set
Exact/semantic address mode
Configuration/State Conditions
Required Authority
Loader/API
Expected Observation
Forbidden Alternatives
```

Wajib membangun `ASSET_CONSUMER_GRAPH` dan memastikan seluruh consumer required mempunyai binding yang benar.

Klasifikasi minimum:

- missing target -> `BROKEN_REFERENCE`;
- asset-to-asset wrong target -> `REFERENCE_PATH_ERROR`;
- Android wrong variant -> `RESOLUTION_PATH_ERROR`;
- consumer memilih asset valid tetapi salah -> `CONSUMER_BINDING_ERROR`;
- content valid tetapi semantic salah -> `ASSET_SEMANTIC_ERROR`.

---

# 20. Exact Graph Closure

Bangun graph terintegrasi yang minimal mencakup:

```text
consumer -> logical role
logical role -> Asset-ID
Asset-ID -> reference/dependency
Asset-ID -> variant/config condition
Asset-ID -> fallback
Asset-ID -> loader
Asset-ID -> final packaged identity
consumer/context -> authority/capability
observer -> allowed observation
```

Wajib:

```text
DANGLING_ROUTE_EDGE = 0
UNKNOWN_ROUTE_EDGE = 0
AMBIGUOUS_REQUIRED_ROUTE = 0
UNAUTHORIZED_ROUTE = 0
UNPROVEN_DYNAMIC_ROUTE = 0
UNEXPECTED_TRANSITIVE_ROUTE = 0
```

---

# 21. Final Accepted Invariants

Gate route tidak boleh PASS sebelum seluruh invariant yang relevan terhadap scope mencapai kondisi berikut:

```text
CLOSED_REQUIRED_DOMAIN = TRUE
HIDDEN_ASSUMPTION = 0
UNVALIDATED_ASSUMPTION = 0
ORACLE_DISAGREEMENT = 0
COMMON_MODE_EVIDENCE_GAP = 0
GLOBAL_UNDERSPECIFICATION = 0
UNRESOLVED_DEFEATER = 0
MATERIAL_RESIDUAL_DOUBT = 0
SURVIVING_COUNTERMODEL = 0
DOMAIN_MODEL_CONFLICT = 0
SPURIOUS_CRITICAL_DEPENDENCY = 0
HIDDEN_CRITICAL_DEPENDENCY = 0
DANGLING_ROUTE_EDGE = 0
UNKNOWN_ROUTE_EDGE = 0
AMBIGUOUS_REQUIRED_ROUTE = 0
UNAUTHORIZED_ROUTE = 0
UNPROVEN_DYNAMIC_ROUTE = 0
UNEXPECTED_TRANSITIVE_ROUTE = 0
TRANSLATION_SEMANTIC_DRIFT = 0
AUTHORITY_LEAK = 0
CONTEXTUAL_BYPASS = 0
RESOLUTION_MODEL_MISMATCH = 0
DEFEATER_MUTATION_ESCAPE = 0
REQUIRED_PROOF_MISSING = 0
```

---

# 22. Status

Status minimum untuk dokumen ini:

| Status | Arti |
|---|---|
| `ROUTE_PROOF_PASS` | Seluruh obligation yang berlaku terhadap scope telah dibuktikan |
| `FAIL_ROUTE` | Jalur/reference/resolution/binding terbukti salah |
| `NOT_PROVEN` | Proof belum lengkap atau domain/evidence belum tertutup |
| `INDETERMINATE_TOOL` | Tool failure menghalangi proof; bukan PASS dan bukan otomatis FAIL_ROUTE |

`ROUTE_PROOF_PASS` tidak boleh diberikan hanya karena:

- build berhasil;
- resource ditemukan;
- lint tidak menemukan error;
- solver SAT;
- test sample lulus;
- tidak ada crash;
- satu oracle menyatakan benar.

---

# 23. Hubungan dengan Build

Dokumen ini merupakan aturan wajib untuk Gate 4 pada `PREBUILD_ASSET_GATE.md`.

Build tetap tertutup sampai proses pada `ASSET_ROUTE_PROOF_PROCESS.md` selesai dan menghasilkan:

```text
ROUTE_PROOF_PASS
```

serta seluruh Gate 0–4 lainnya memenuhi syarat Gate 5.
