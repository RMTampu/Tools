# ASSET_SAFE_100_METHODS.md

## 1. Status Dokumen

Dokumen ini adalah korpus metode assurance aktif hasil riset lanjutan untuk memperkuat `ASSET_SAFE_100` pada repository `RMTampu/Tools`.

Dokumen ini hanya memuat metode yang diterima setelah:

- penyaringan terhadap scope asset;
- pemeriksaan apakah metode menambah kelas jaminan nyata;
- penggabungan metode yang tumpang tindih;
- dominance filtering terhadap metode yang lebih lemah;
- audit kontra terhadap `ASSET_SAFE_100_RULES.md`, `ASSET_ROUTE_PROOF_METHODS.md`, dan rantai gate aktif;
- penghapusan metode yang redundant, hanya berganti nama, tidak relevan, atau memperbesar trusted base tanpa jaminan tambahan yang sepadan.

Metode yang ditolak tidak menjadi aturan aktif dan tidak boleh digunakan untuk menggantikan atau melemahkan metode dalam dokumen ini.

Dokumen ini melengkapi, bukan menggantikan:

- `ASSET_SAFE_100_RULES.md`;
- `ASSET_ROUTE_PROOF_METHODS.md`;
- `ASSET_ROUTE_PROOF_PROCESS.md`;
- `PREBUILD_ASSET_GATE.md`;
- `AGENT_PROCEDURE_EXECUTION_RULES.md`.

Seluruh metode di dokumen ini wajib dieksekusi melalui `ASSET_SAFE_100_PROCESS.md` sesuai scope yang berlaku.

---

## 2. Prinsip Integrasi Monotonik

Metode baru hanya boleh diterima jika meningkatkan jaminan tanpa membatalkan invariant yang sudah berlaku.

Aturan:

```text
OLD_REQUIRED_GUARANTEES ⊆ NEW_REQUIRED_GUARANTEES
```

Dilarang:

- menghapus proof lama hanya karena metode baru terlihat lebih kuat;
- mengganti exact proof dengan heuristic;
- mengganti evidence dengan confidence score;
- mempersempit domain diam-diam agar PASS lebih mudah;
- membuat dua sumber kebenaran yang saling bersaing.

Jika metode baru mencakup metode lama sepenuhnya, metode lama boleh dianggap terserap hanya jika seluruh invariant-nya tetap dipertahankan secara eksplisit.

---

# 3. Requirement-to-Asset Semantic Traceability

## METHOD SAFE-M01 — Asset Harus Dapat Ditelusuri ke Requirement yang Membenarkan Keberadaannya

Asset Contract tidak cukup hanya menjelaskan format dan consumer. Untuk setiap required asset yang mempunyai arti/fungsi terhadap aplikasi, wajib tersedia hubungan eksplisit:

```text
REQUIREMENT / USER-INTENT / SYSTEM-CONTRACT
→ ASSET ROLE
→ ASSET CONTRACT
→ CONSUMER EXPECTATION
→ OBSERVABLE RESULT
```

Wajib:

- setiap required semantic asset mempunyai sumber requirement/intent;
- setiap requirement yang memerlukan asset mempunyai target asset/role yang diketahui;
- tidak ada asset kritis dengan semantic purpose yang hanya diasumsikan;
- perubahan requirement membatalkan proof asset yang bergantung padanya;
- perubahan asset yang mengubah semantic result wajib ditelusuri kembali ke requirement.

Untuk asset yang murni teknis dan tidak mempunyai semantic role independen, contract harus membuktikan mengapa traceability tersebut `NOT_APPLICABLE`.

Invariant:

```text
UNTRACED_REQUIRED_ASSET = 0
UNSATISFIED_ASSET_REQUIREMENT = 0
```

---

# 4. Asset Provenance dan Chain-of-Custody Closure

## METHOD SAFE-M02 — Identitas Asset Harus Terikat dari Source Sampai Consumer

Hash source saja tidak cukup jika asset melewati generator, optimizer, compiler, merge, packaging, extraction, copy, cache, atau materialization.

Untuk setiap required asset, bangun provenance chain yang relevan:

```text
SOURCE
→ DECLARED BUILD INPUT
→ TRANSFORMATION(S)
→ INTERMEDIATE REPRESENTATION
→ FINAL PACKAGE REPRESENTATION
→ RUNTIME MATERIALIZATION bila ada
→ CONSUMER INPUT
```

Setiap node/edge harus mempunyai identity/provenance evidence yang sesuai dengan jenis transformasi.

Wajib mendeteksi:

- undeclared asset-affecting input;
- source substitution;
- stale generated asset;
- unexpected transformation;
- asset dari cache yang tidak sesuai input aktif;
- runtime copy/extraction yang tidak sama secara semantik dengan artifact final.

Invariant:

```text
UNKNOWN_PROVENANCE_EDGE = 0
UNDECLARED_ASSET_INPUT = 0
STALE_ASSET_TRANSFORM = 0
```

---

# 5. Build-Input Closure dan Reproducibility Cross-Check

## METHOD SAFE-M03 — Semua Input yang Dapat Mengubah Asset Harus Diketahui

Closed Asset Universe diperluas menjadi closed asset-affecting build input universe.

Cakupan bila relevan:

- source asset;
- generated source input;
- Gradle/resource configuration;
- merge priority;
- resource shrink configuration;
- image/resource optimization setting;
- locale/configuration generation data;
- dependency resource version;
- plugin/tool version yang dapat mengubah representasi asset;
- environment variable atau external input yang dapat mengubah asset output.

Build kedua/reproducibility check dapat digunakan sebagai independent corroborative check untuk mendeteksi input tersembunyi atau nondeterministic transformation, tetapi byte-for-byte reproducibility hanya wajib bila contract/build pipeline memang menuntut determinism.

Jika output berbeda, perbedaan harus dapat dijelaskan dan dibuktikan tidak mengubah semantic asset model.

Invariant:

```text
UNKNOWN_ASSET_AFFECTING_BUILD_INPUT = 0
UNEXPLAINED_ASSET_OUTPUT_VARIANCE = 0
```

---

# 6. Transformation Refinement / Translation Validation

## METHOD SAFE-M04 — Setiap Transformasi Asset Harus Mempertahankan Contract

Build dapat mengubah asset secara legal. Karena itu checksum source vs final tidak selalu tepat.

Untuk setiap transformasi yang dapat mengubah representasi asset:

```text
INPUT CONTRACT
→ TRANSFORMATION
→ OUTPUT CONTRACT
→ SEMANTIC EQUIVALENCE / REFINEMENT PROOF
```

Contoh transformasi:

- XML/resource compilation;
- resource table linking;
- image optimization;
- density conversion;
- format conversion;
- compression;
- generated resource production;
- database/template generation;
- copy/extract/materialize ke runtime filesystem.

Wajib membuktikan bahwa output mempertahankan semua property contract yang diwajibkan consumer.

Invariant:

```text
UNVALIDATED_REQUIRED_TRANSFORMATION = 0
TRANSFORMATION_SEMANTIC_DRIFT = 0
```

---

# 7. Final Package Physical Representation Closure

## METHOD SAFE-M05 — Semantic Identity Saja Tidak Cukup Jika Loader Bergantung pada Representasi Fisik

Untuk asset tertentu, cara asset disimpan dalam APK/package memengaruhi apakah consumer dapat menggunakannya.

Contract wajib menyatakan bila relevan:

- compressed / uncompressed requirement;
- archive entry uniqueness;
- alignment requirement;
- seekability/file-descriptor requirement;
- storage location/module/split;
- expected package owner;
- delivery availability;
- size/offset constraint yang diperlukan loader.

Contoh: consumer yang membutuhkan file descriptor/seekable asset tidak boleh dianggap aman hanya karena entry tersedia jika packaging mode membuat jalur loader tersebut gagal.

Invariant:

```text
PACKAGE_PHYSICAL_CONTRACT_MISMATCH = 0
DUPLICATE_EFFECTIVE_PACKAGE_ENTRY = 0
UNAVAILABLE_REQUIRED_DELIVERY_ASSET = 0
```

---

# 8. Runtime Materialization Equivalence

## METHOD SAFE-M06 — Asset yang Disalin/Diekstrak/Di-cache Harus Dibuktikan Lagi pada Titik Pemakaian

Jika aplikasi tidak memakai asset langsung dari APK tetapi melakukan:

- copy ke internal storage;
- extraction;
- decompression;
- first-run initialization;
- cache population;
- database prepackaged copy;
- generated runtime derivative;

maka final-package proof belum cukup.

Wajib membangun:

```text
FINAL PACKAGE ASSET
→ MATERIALIZATION STEP
→ MATERIALIZED ASSET
→ REAL CONSUMER
```

dan membuktikan output materialization memenuhi contract yang sama atau contract refinement yang eksplisit.

Invariant:

```text
UNPROVEN_RUNTIME_MATERIALIZATION = 0
MATERIALIZATION_SEMANTIC_DRIFT = 0
```

---

# 9. Format, Encoding, dan Semantic-Metadata Conformance

## METHOD SAFE-M07 — Metadata yang Mengubah Makna Harus Menjadi Bagian Contract

Type-specific validation diperkuat agar tidak hanya memvalidasi payload utama.

Periksa metadata/representation yang dapat mengubah hasil consumer, misalnya bila relevan:

- text encoding/BOM;
- Unicode normalization;
- XML namespace/encoding;
- bitmap density metadata;
- EXIF orientation;
- color profile/color space;
- alpha/premultiplication semantics;
- animation timing/loop metadata;
- media rotation/timebase/track metadata;
- font variation axes/feature metadata;
- database encoding/page/schema metadata;
- archive/container metadata yang memengaruhi loader.

Metadata yang tidak berpengaruh terhadap contract dapat `NOT_APPLICABLE` dengan bukti.

Invariant:

```text
UNKNOWN_SEMANTIC_METADATA = 0
SEMANTIC_METADATA_MISMATCH = 0
```

---

# 10. Environment / Device Dependency Closure

## METHOD SAFE-M08 — Environment yang Dapat Mengubah Hasil Asset Harus Masuk Proof Universe

Target `Android 11 / API 30 / arm64-v8a` belum otomatis menutup seluruh dependency lingkungan.

Jika hasil asset bergantung pada environment, kumpulkan dependency yang relevan, misalnya:

- Android framework resource behavior;
- locale/ICU behavior;
- system font fallback;
- codec availability;
- graphics/rendering implementation;
- density/display characteristics;
- WebView implementation bila bundled web asset bergantung padanya;
- OEM/device behavior yang termasuk supported deployment domain.

Bangun equivalence classes hanya berdasarkan perbedaan environment yang benar-benar dapat mengubah observable contract.

Untuk setiap class wajib ada proof/witness atau batas support yang eksplisit.

Dilarang mengklaim 100 terhadap environment yang tidak dimasukkan ke supported universe.

Invariant:

```text
UNKNOWN_REQUIRED_ENVIRONMENT_CLASS = 0
UNPROVEN_ENVIRONMENT_DEPENDENCY = 0
```

---

# 11. Text Shaping, Grapheme, dan Fallback Closure

## METHOD SAFE-M09 — Codepoint Coverage Tidak Selalu Membuktikan Teks Dapat Dirender Benar

Untuk font/text asset yang mendukung script atau sequence kompleks, glyph-per-codepoint proof harus diperluas bila relevan terhadap contract.

Cakupan dapat mencakup:

- grapheme cluster;
- combining marks;
- ligature/GSUB/GPOS behavior;
- bidi interaction;
- complex-script shaping;
- emoji/variation selector/ZWJ sequence;
- approved system fallback chain.

Gunakan shipped strings dan required sequence set sebagai closed witness universe bila input tidak arbitrer.

Jika input eksternal arbitrer didukung, contract wajib menentukan fallback/support boundary.

Invariant:

```text
UNPROVEN_REQUIRED_TEXT_SEQUENCE = 0
BROKEN_REQUIRED_SHAPING_OR_FALLBACK = 0
```

---

# 12. Contract Property-to-Evidence Closure

## METHOD SAFE-M10 — “Asset Sudah Dieksekusi” Tidak Sama dengan “Seluruh Contract Sudah Dibuktikan”

Setiap clause/property pada Asset Contract dan Consumer Contract wajib dipetakan ke evidence.

Bangun minimal:

```text
CONTRACT_PROPERTY_ID
→ VALIDATION METHOD
→ WITNESS / TEST / PROOF
→ EVIDENCE REFERENCE
→ STATUS
```

Runtime exercise yang hanya membuktikan "tidak crash" tidak boleh dianggap membuktikan semantic, visual, budget, localization, version, atau property lain yang tidak diamati.

Wajib:

```text
Required contract properties = N
Proven contract properties   = N
Unobserved                    = 0
```

Invariant:

```text
UNOBSERVED_REQUIRED_CONTRACT_PROPERTY = 0
```

---

# 13. Independent Oracle dan Common-Mode Evidence Control

## METHOD SAFE-M11 — Bukti Kritis Tidak Boleh Bergantung pada Satu Sumber Kesalahan yang Sama

Untuk semantic/visual/requirement-critical asset, bila satu oracle dapat salah dengan cara yang sama seperti artifact yang diverifikasi, gunakan independent corroboration yang benar-benar berasal dari jalur berbeda.

Contoh pasangan bila relevan:

- source contract vs rendered semantic assertion;
- generated expected model vs independent package inspection;
- golden image vs structural/semantic property;
- primary parser vs independent format/conformance checker.

Dua checker yang memakai expected data/generated model yang sama tidak otomatis independen.

Wajib mencatat provenance evidence kritis dan common-mode dependency yang diketahui.

Invariant:

```text
UNRESOLVED_CRITICAL_COMMON_MODE_EVIDENCE = 0
```

---

# 14. Verifier Assurance dan Trusted-Base Minimization

## METHOD SAFE-M12 — Verifier Juga Harus Dibuktikan Layak Dipercaya untuk Klaimnya

Mutation detection memperkuat verifier, tetapi tidak selalu cukup untuk membuktikan seluruh implementasi verifier benar.

Untuk validator kritis gunakan kombinasi yang relevan:

- positive known-good corpus;
- negative/mutation corpus;
- boundary cases;
- differential validation dengan implementation independen bila tersedia;
- self-test;
- version/config pinning;
- validation terhadap format/standard reference bila tersedia;
- checker sederhana/independen untuk output proof yang memungkinkan.

Trusted base harus dibuat sekecil praktis mungkin.

Jika verifier kritis berubah versi/config, evidence yang bergantung padanya harus dievaluasi ulang.

Invariant:

```text
UNQUALIFIED_CRITICAL_VERIFIER = 0
UNEXPLAINED_VERIFIER_DISAGREEMENT = 0
```

---

# 15. Evidence Binding, Freshness, dan Automatic Invalidation

## METHOD SAFE-M13 — Evidence Harus Terikat pada Input yang Tepat

Setiap evidence penting wajib dapat ditelusuri ke input yang dibuktikannya.

Metadata evidence minimal bila relevan:

```text
Asset-ID / Property-ID
source identity/digest
contract version
dependency/configuration identity
tool/version/config
environment class
artifact identity
witness state/configuration
result
```

Evidence menjadi stale bila salah satu input yang menjadi premise berubah.

Agen tidak boleh menggunakan PASS lama tanpa membuktikan premise masih identik atau perubahan tidak memengaruhi proof.

Invariant:

```text
STALE_EVIDENCE_USED_FOR_PASS = 0
UNBOUND_REQUIRED_EVIDENCE = 0
```

---

# 16. Expanded Resource / Complexity Budget Closure

## METHOD SAFE-M14 — Budget Harus Mencakup Semua Resource yang Dapat Dipicu oleh Asset

`Resource Budget Safety` diperluas dari ukuran/memori menjadi seluruh resource cost yang secara material dapat dipicu oleh karakteristik asset.

Contract harus menetapkan bila relevan:

- compressed size;
- expanded size;
- expansion ratio;
- decoded memory;
- peak simultaneous memory;
- parse/decode/render CPU time;
- wall-clock latency;
- I/O bytes/operations;
- nesting/depth/element count;
- animation/frame complexity;
- database/query initialization cost;
- pathological complexity limit.

Tujuannya menutup asset yang valid secara format tetapi secara intrinsik dapat menyebabkan OOM, ANR, freeze, extreme jank, atau resource exhaustion dalam penggunaan contract-normal.

Jika kegagalan hanya terjadi karena logic aplikasi melampaui contract penggunaan, klasifikasikan `FAIL_APPLICATION`.

Invariant:

```text
RESOURCE_BUDGET_EXCEEDED_BY_ASSET = 0
UNBOUNDED_REQUIRED_ASSET_COMPLEXITY = 0
```

---

# 17. Adversarial / Boundary / Metamorphic Challenge

## METHOD SAFE-M15 — Validator Harus Ditantang di Sekitar Batas Valid/Invalid

Mutation per fault class tetap wajib. Tambahkan challenge yang sesuai terhadap tipe asset/consumer bila dapat menemukan fault yang tidak tertangkap oleh contoh mutation tunggal:

- grammar-aware malformed variants;
- boundary-value generation;
- property-based validation;
- fuzzing terhadap parser/decoder/loader;
- metamorphic relation;
- truncation/bit corruption/container mutation;
- extreme but contract-bounded complexity.

Challenge tidak menggantikan exhaustive proof pada finite domain dan tidak boleh dipakai untuk mengklaim semua unknown-unknown tertutup.

Fungsinya adalah mencari counterexample terhadap verifier, contract, dan fault model aktif.

Jika ditemukan fault class baru yang material, fault model wajib dibuka kembali dan diperluas.

Invariant:

```text
KNOWN_ADVERSARIAL_ESCAPE = 0
UNRESOLVED_NEW_FAULT_CLASS = 0
```

---

# 18. Claim–Evidence–Defeater Assurance Closure

## METHOD SAFE-M16 — Final PASS Harus Dapat Diaudit sebagai Rantai Klaim dan Bukti

Untuk final `ASSET_SAFE_100`, bangun assurance structure minimal:

```text
TOP CLAIM: ASSET_SAFE_100 untuk scope yang dikunci
  ↓
SUB-CLAIMS / INVARIANTS
  ↓
EVIDENCE
  ↓
PREMISES / ASSUMPTIONS / ENVIRONMENT
  ↓
DEFEATERS / REASONS FOR DOUBT
```

Setiap invariant kritis harus mempunyai evidence dan premise yang dapat ditelusuri.

Alasan material yang dapat menggugurkan evidence/claim harus diselesaikan sebelum PASS, termasuk:

- evidence stale;
- tool tidak layak dipercaya;
- common-mode oracle error;
- environment belum ditutup;
- transformation tidak terbukti;
- property contract tidak diamati;
- provenance putus;
- scope diam-diam berubah.

Untuk klaim `ASSET_SAFE_100`, residual doubt material yang diketahui tidak boleh diterima sebagai PASS.

Invariant:

```text
UNRESOLVED_MATERIAL_DEFEATER = 0
MATERIAL_RESIDUAL_DOUBT = 0
```

---

# 19. Integrasi dengan Asset Route Proof

Route correctness tetap dijalankan melalui:

- `ASSET_ROUTE_PROOF_METHODS.md`;
- `ASSET_ROUTE_PROOF_PROCESS.md`.

Dokumen ini tidak mengulang detail route proof.

`ROUTE_PROOF_PASS` menjadi evidence wajib untuk property route/reference/resolution pada asset yang mempunyai route dalam scope.

Tidak boleh ada dua route model independen yang menghasilkan keputusan berbeda.

---

# 20. Formula Tambahan Wajib

Formula lama di `ASSET_SAFE_100_RULES.md` tetap wajib.

Selain itu, jika metode pada dokumen ini berlaku terhadap scope, final `ASSET_SAFE_100` juga membutuhkan:

```text
RequirementAssetTraceability
AND AssetProvenanceClosure
AND AssetAffectingBuildInputClosure
AND TransformationRefinement
AND PackagePhysicalClosure
AND RuntimeMaterializationEquivalence
AND SemanticMetadataConformance
AND EnvironmentDependencyClosure
AND RequiredTextSequenceClosure
AND ContractPropertyEvidenceClosure
AND CriticalOracleIndependence
AND CriticalVerifierAssurance
AND EvidenceBindingAndFreshness
AND ExpandedResourceBudgetClosure
AND AdversarialChallengeClosure
AND ClaimEvidenceDefeaterClosure
```

Dan seluruh nilai berikut harus nol untuk scope yang berlaku:

```text
UNTRACED_REQUIRED_ASSET = 0
UNSATISFIED_ASSET_REQUIREMENT = 0
UNKNOWN_PROVENANCE_EDGE = 0
UNDECLARED_ASSET_INPUT = 0
STALE_ASSET_TRANSFORM = 0
UNKNOWN_ASSET_AFFECTING_BUILD_INPUT = 0
UNEXPLAINED_ASSET_OUTPUT_VARIANCE = 0
UNVALIDATED_REQUIRED_TRANSFORMATION = 0
TRANSFORMATION_SEMANTIC_DRIFT = 0
PACKAGE_PHYSICAL_CONTRACT_MISMATCH = 0
UNPROVEN_RUNTIME_MATERIALIZATION = 0
MATERIALIZATION_SEMANTIC_DRIFT = 0
UNKNOWN_SEMANTIC_METADATA = 0
SEMANTIC_METADATA_MISMATCH = 0
UNKNOWN_REQUIRED_ENVIRONMENT_CLASS = 0
UNPROVEN_ENVIRONMENT_DEPENDENCY = 0
UNPROVEN_REQUIRED_TEXT_SEQUENCE = 0
BROKEN_REQUIRED_SHAPING_OR_FALLBACK = 0
UNOBSERVED_REQUIRED_CONTRACT_PROPERTY = 0
UNRESOLVED_CRITICAL_COMMON_MODE_EVIDENCE = 0
UNQUALIFIED_CRITICAL_VERIFIER = 0
UNEXPLAINED_VERIFIER_DISAGREEMENT = 0
STALE_EVIDENCE_USED_FOR_PASS = 0
UNBOUND_REQUIRED_EVIDENCE = 0
RESOURCE_BUDGET_EXCEEDED_BY_ASSET = 0
UNBOUNDED_REQUIRED_ASSET_COMPLEXITY = 0
KNOWN_ADVERSARIAL_ESCAPE = 0
UNRESOLVED_NEW_FAULT_CLASS = 0
UNRESOLVED_MATERIAL_DEFEATER = 0
MATERIAL_RESIDUAL_DOUBT = 0
```

---

# 21. Batas Klaim

Metode ini meningkatkan jaminan terhadap asset di dalam domain yang didefinisikan dan didukung.

Metode ini tidak mengubah `ASSET_SAFE_100` menjadi jaminan universal terhadap seluruh kemungkinan hardware, OS, toolchain, kode aplikasi, atau fakta dunia nyata yang berada di luar scope.

Jika suatu dependency lingkungan atau transformasi dapat memengaruhi asset tetapi sengaja dikeluarkan dari supported domain, batas tersebut harus dinyatakan eksplisit dan tidak boleh disembunyikan oleh label `ASSET_SAFE_100`.