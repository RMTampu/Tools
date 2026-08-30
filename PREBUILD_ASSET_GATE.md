# PREBUILD_ASSET_GATE.md

## 1. Status Dokumen

Dokumen ini adalah rantai gate wajib untuk seluruh pekerjaan asset/resource sebelum build, saat build, sesudah build, dan pengujian pada repository `RMTampu/Tools`.

Dokumen ini tidak menggantikan `ASSET_SAFE_100_RULES.md`. Dokumen ini menentukan urutan wajib kapan setiap kelompok aturan harus dijalankan.

Seluruh gate dalam dokumen ini WAJIB menjalankan metode dan proses yang berlaku dari:

- `ASSET_SAFE_100_RULES.md`;
- `ASSET_SAFE_100_METHODS.md`;
- `ASSET_SAFE_100_PROCESS.md`;
- `ASSET_ROUTE_PROOF_METHODS.md` dan `ASSET_ROUTE_PROOF_PROCESS.md` bila route/reference/resolution termasuk scope.

Seluruh gate WAJIB dijalankan sesuai `AGENT_PROCEDURE_EXECUTION_RULES.md`. Keterbatasan context/memory agen hanya boleh menyebabkan eksekusi dipecah menjadi unit yang lebih kecil; tidak boleh menyebabkan aturan, langkah, coverage, proof, atau evidence dikurangi.

---

# 2. Larangan Build Sebelum Rangkaian Selesai

**DILARANG MEMULAI BUILD APK / PRODUCTION BUILD / FINAL RESOURCE PACKAGING sebelum seluruh gate yang berada sebelum tahap BUILD selesai dan berstatus PASS.**

Tidak ada agen, workflow, script, atau proses otomatis yang boleh melewati gate sebelumnya hanya agar build dapat dimulai.

Aturan keras:

```text
Gate N hanya boleh dimulai jika Gate N-1 = PASS.

SKIPPED            != PASS
UNKNOWN            != PASS
INCOMPLETE_PROOF   != PASS
INDETERMINATE_TOOL != PASS
FAIL_ASSET         != PASS
NOT_PROVEN         != PASS
```

Jika satu gate belum PASS, proses berhenti pada gate tersebut dan BUILD DILARANG DIMULAI.

Jika asset, requirement, contract, route, configuration, dependency, provenance input, build input, transformation configuration, verifier, oracle, environment boundary, atau input lain berubah setelah suatu gate dinyatakan PASS, seluruh gate sesudah titik perubahan yang terdampak dianggap tidak valid dan wajib dijalankan kembali dari gate paling awal yang terdampak.

---

# 3. Rantai Gate Wajib

Urutan resmi:

```text
GATE 0  — RULE ENTRY / SCOPE LOCK
   ↓
GATE 1  — ASSET PREPARATION
   ↓
GATE 2  — ASSET INVENTORY & CONTRACT CLOSURE
   ↓
GATE 3  — ASSET AUDIT & SOURCE VALIDATION
   ↓
GATE 4  — ASSET REFERENCE / ROUTE / COMMUNICATION PROOF
   ↓
GATE 5  — PREBUILD ASSET CLOSURE
   ↓
================ BUILD BOUNDARY ================
   ↓
GATE 6  — BUILD
   ↓
GATE 7  — FINAL ARTIFACT / PACKAGE VERIFICATION
   ↓
GATE 8  — RUNTIME / EMULATOR / INTEGRATION TESTING
   ↓
GATE 9  — FINAL ACCEPTANCE
```

Pemetaan internal `ASSET_SAFE_100_PROCESS.md` ke rantai tersebut:

```text
S0 -> GATE 0
S1 -> GATE 1
S2 -> GATE 2
S3 -> GATE 3
S4 -> GATE 4
S5 -> GATE 5
S6 -> GATE 6
S7 -> GATE 7
S8 -> GATE 8
S9 -> GATE 9
```

Pemetaan ini tidak boleh diubah menjadi rantai paralel atau urutan alternatif.

---

# 4. Gate 0 — Rule Entry / Scope Lock

Sebelum asset disentuh:

- baca `AGENTS.md`;
- baca `AGENT_PROCEDURE_EXECUTION_RULES.md`;
- baca `PREBUILD_ASSET_GATE.md`;
- baca `ASSET_SAFE_100_RULES.md`;
- baca `ASSET_SAFE_100_METHODS.md`;
- baca `ASSET_SAFE_100_PROCESS.md`;
- ikuti seluruh referensi aturan tambahan yang diwajibkan oleh scope aktif;
- tentukan scope asset dan perubahan yang sedang dikerjakan;
- kunci supported environment/domain yang dapat memengaruhi hasil asset;
- identifikasi asset-affecting build inputs dan transformation classes yang berlaku;
- jangan memulai gate berikutnya bila scope atau aturan aktif belum diketahui lengkap.

Jika scope mencakup jalur/reference/resolution/consumer binding asset, agen juga WAJIB membaca `ASSET_ROUTE_PROOF_METHODS.md` dan `ASSET_ROUTE_PROOF_PROCESS.md` sebelum memasuki Gate 4.

Gate 0 juga wajib menyelesaikan `ASSET_SAFE_S0 = PASS` dari `ASSET_SAFE_100_PROCESS.md`.

Jika semua aturan tidak dapat dipertahankan secara lengkap sekaligus, Gate 0 WAJIB menetapkan eksekusi bertahap sesuai `AGENT_PROCEDURE_EXECUTION_RULES.md` sebelum pekerjaan dilanjutkan.

---

# 5. Gate 1 — Asset Preparation

Tahap penyiapan seluruh asset yang dibutuhkan oleh scope kerja.

Gate ini wajib menjalankan `ASSET_SAFE_S1` dari `ASSET_SAFE_100_PROCESS.md`, termasuk requirement/intent, semantic role, provenance origin, expected transformation path, dan environment dependency awal yang berlaku.

Gate ini belum boleh melakukan final build.

Gate 1 hanya PASS jika:

```text
ASSET_SAFE_S1 = PASS
```

---

# 6. Gate 2 — Asset Inventory & Contract Closure

Seluruh asset required harus sudah diketahui dan mempunyai contract yang diperlukan sebelum audit mendalam dimulai.

Gate ini wajib menjalankan aturan inventory/contract pada `ASSET_SAFE_100_RULES.md` dan `ASSET_SAFE_S2` pada `ASSET_SAFE_100_PROCESS.md`.

Cakupan tambahan yang wajib bila relevan:

- requirement-to-asset traceability;
- semantic metadata contract;
- package physical representation contract;
- environment dependency contract;
- expanded resource/complexity budget;
- daftar seluruh contract property yang wajib dibuktikan.

Gate 2 hanya PASS jika:

```text
ASSET_SAFE_S2 = PASS
```

---

# 7. Gate 3 — Asset Audit & Source Validation

Audit asset dilakukan sebelum build untuk memastikan source asset, identitas, format, semantic expectation, dependency, configuration, requirement, provenance, metadata, budget, dan input yang relevan sudah benar terhadap contract.

Gate ini wajib menjalankan seluruh source/type-specific validation pada `ASSET_SAFE_100_RULES.md` serta `ASSET_SAFE_S3` dari `ASSET_SAFE_100_PROCESS.md`.

Gate ini harus menutup bila relevan:

- source provenance;
- canonical identity/path;
- format/encoding conformance;
- semantic metadata;
- text shaping/sequence source requirements;
- expanded resource/complexity budget;
- generated asset freshness;
- known asset-affecting build-input universe;
- prebuild adversarial/boundary challenge yang diwajibkan.

Gate ini harus PASS sebelum pembuktian jalur komunikasi dianggap final.

Gate 3 hanya PASS jika:

```text
ASSET_SAFE_S3 = PASS
```

---

# 8. Gate 4 — Asset Reference / Route / Communication Proof

Gate 4 WAJIB menjalankan `ASSET_SAFE_S4` dari `ASSET_SAFE_100_PROCESS.md`.

Jika scope mempunyai route/reference/resolution, Gate 4 juga WAJIB menjalankan seluruh metode aktif yang diterima dalam `ASSET_ROUTE_PROOF_METHODS.md` melalui urutan operasional `ASSET_ROUTE_PROOF_PROCESS.md`.

Seluruh jalur consumer → asset, asset → asset, alias, dependency, configuration → variant, fallback, dynamic lookup, authority/capability, contextual route, dan jalur transitive yang termasuk scope wajib dibuktikan.

Tidak boleh ada jalur putus, salah alamat, ambigu, tidak diketahui, tidak terbukti, unauthorized, atau bypass yang belum diselesaikan.

Urutan internal route proof Gate 4 adalah:

```text
4.0  ROUTE SCOPE & DOMAIN LOCK
 ↓
4.1  SEMANTIC INTENT & ORACLE CONVERGENCE
 ↓
4.2  OBSERVATION & EQUIVALENCE CLOSURE
 ↓
4.3  ASSUMPTION / DEFEATER / EPISTEMIC CLOSURE
 ↓
4.4  UNIQUE GLOBAL ROUTE MODEL
 ↓
4.5  CAUSAL DEPENDENCY & INFLUENCE VALIDATION
 ↓
4.6  CLOSED ROUTE REPRESENTATION / DSL
 ↓
4.7  ROUTE SYNTHESIS & TRANSLATION VALIDATION
 ↓
4.8  CONSUMER BINDING / GRAPH / AUTHORITY CLOSURE
 ↓
4.9  CONTEXTUAL / COMPOSITIONAL / HYPERPROPERTY PROOF
 ↓
4.10 ANDROID 11 RESOLUTION REFINEMENT
 ↓
4.11 PROOF CERTIFICATE / FOUNDATIONAL CHECK
 ↓
4.12 COUNTERMODEL / MUTATION / COMPLETE FAULT-DOMAIN CHALLENGE
 ↓
4.13 FINAL ROUTE CLOSURE
```

Untuk scope yang mempunyai route, Gate 4 hanya PASS jika:

```text
ROUTE_PROOF_PASS
AND ASSET_SAFE_S4 = PASS
```

Jika route benar-benar `NOT_APPLICABLE`, contract wajib membuktikan ketidakberlakuannya dan Gate 4 tetap membutuhkan `ASSET_SAFE_S4 = PASS` untuk Consumer Contract yang berlaku.

---

# 9. Gate 5 — Prebuild Asset Closure

Gate ini adalah keputusan terakhir sebelum build.

Gate ini WAJIB menjalankan `ASSET_SAFE_S5` dari `ASSET_SAFE_100_PROCESS.md`.

BUILD hanya boleh dibuka bila seluruh gate 0 sampai 4 telah PASS dan tidak ada proof wajib yang masih UNKNOWN, SKIPPED, INCOMPLETE, INDETERMINATE, NOT_LOADED, NOT_READ, atau NOT_PROVEN.

Gate 5 wajib memastikan sebelum build:

- seluruh prebuild contract property mempunyai validation method;
- provenance source/input closure masih valid;
- tidak ada undeclared asset-affecting build input;
- transformation plan diketahui;
- critical verifier qualification yang diwajibkan tersedia;
- critical common-mode evidence risk telah ditangani;
- stale evidence tidak dipakai;
- proof invalidation state bersih;
- fault model prebuild mencakup semua kelas yang diketahui sampai titik ini.

Untuk scope yang mempunyai asset route/reference/resolution, `ROUTE_PROOF_PASS` wajib masih valid pada saat Gate 5 diperiksa.

Build hanya dibuka jika:

```text
ASSET_SAFE_S5 = PASS
AND PREBUILD_ASSET_GATE = PASS
AND BUILD_UNLOCKED = TRUE
```

Jika salah satu tidak terpenuhi:

```text
BUILD_UNLOCKED = FALSE
```

---

# 10. Gate 6 — Build

Build hanya dilakukan melalui GitHub Actions sesuai `AGENTS.md`.

Gate ini wajib menjalankan `ASSET_SAFE_S6` dari `ASSET_SAFE_100_PROCESS.md`.

Build harus menggunakan input yang sama dengan input yang telah dibuktikan pada Gate 0–5. Identity input/output yang diperlukan untuk provenance dan transformation proof harus dicatat.

Jika input build berbeda dari premise prebuild yang telah PASS, proof wajib diinvalidasi dari gate paling awal yang terdampak.

Build yang dimulai tanpa `PREBUILD_ASSET_GATE = PASS` atau `ASSET_SAFE_S5 = PASS` adalah pelanggaran aturan repository dan hasilnya tidak boleh digunakan sebagai bukti final.

---

# 11. Gate 7 — Final Artifact / Package Verification

Setelah build, artifact final wajib diverifikasi untuk memastikan hasil package mempertahankan asset yang sudah dibuktikan sebelum build.

Gate ini wajib menjalankan `ASSET_SAFE_S7` dari `ASSET_SAFE_100_PROCESS.md`.

Selain final package equivalence dari `ASSET_SAFE_100_RULES.md`, Gate 7 wajib menutup bila relevan:

- provenance continuity sampai artifact final;
- transformation refinement/translation validation;
- compiled semantic identity;
- physical package representation;
- compression/seekability/alignment/storage contract;
- module/split/delivery location;
- independent package inspection untuk evidence kritis;
- evidence binding ke artifact identity final.

Build sukses tidak otomatis berarti gate ini PASS.

Gate 7 hanya PASS jika:

```text
ASSET_SAFE_S7 = PASS
```

---

# 12. Gate 8 — Runtime / Emulator / Integration Testing

Artifact yang sudah lolos verifikasi package kemudian diuji melalui runtime/emulator/integration sesuai kebutuhan dan aturan repository.

Gate ini wajib menjalankan `ASSET_SAFE_S8` dari `ASSET_SAFE_100_PROCESS.md`.

Selain exhaustive runtime exercise, Gate 8 wajib menutup bila relevan:

- runtime materialization equivalence;
- real consumer use setelah copy/extract/cache;
- supported environment equivalence-class witnesses;
- complete contract property-to-evidence observation;
- text shaping/grapheme/fallback witness;
- expanded CPU/time/I/O/memory/complexity budgets;
- visual/semantic oracle;
- state/configuration witnesses;
- independent critical oracle corroboration.

Kegagalan tool tidak otomatis menjadi `FAIL_ASSET`; klasifikasi tetap mengikuti `ASSET_SAFE_100_RULES.md`.

Gate 8 hanya PASS jika:

```text
ASSET_SAFE_S8 = PASS
```

---

# 13. Gate 9 — Final Acceptance

Final acceptance hanya boleh diberikan bila seluruh gate yang diwajibkan oleh scope telah selesai dan seluruh bukti yang diperlukan tersedia.

Gate ini wajib menjalankan `ASSET_SAFE_S9` dari `ASSET_SAFE_100_PROCESS.md`.

Sebelum final acceptance wajib dilakukan:

- mutation proof seluruh defined fault classes;
- adversarial/boundary/metamorphic challenge yang berlaku;
- audit new fault classes;
- audit verifier disagreement;
- audit stale/unbound evidence;
- audit critical common-mode evidence;
- audit material defeaters/residual doubt;
- audit claim → invariant → evidence → premise traceability;
- audit bahwa seluruh evidence terikat ke artifact/input aktif.

Tidak boleh menyatakan asset/build selesai hanya karena APK berhasil dikompilasi atau runtime test tidak crash.

Final acceptance hanya boleh menghasilkan `ASSET_SAFE_100` jika:

```text
ASSET_SAFE_S0 = PASS
AND ASSET_SAFE_S1 = PASS
AND ASSET_SAFE_S2 = PASS
AND ASSET_SAFE_S3 = PASS
AND ASSET_SAFE_S4 = PASS
AND ASSET_SAFE_S5 = PASS
AND ASSET_SAFE_S6 = PASS
AND ASSET_SAFE_S7 = PASS
AND ASSET_SAFE_S8 = PASS
AND ASSET_SAFE_S9 = PASS
AND ASSET_SAFE_100_PROCESS = PASS
AND ASSET_SAFE_100_RULES_FORMULA = PASS
AND ASSET_SAFE_100_METHODS_FORMULA = PASS
```

Jika route proof berlaku:

```text
AND ROUTE_PROOF_PASS
```

---

# 14. Aturan Anti-Skip

Dilarang:

- melompati gate;
- melompati S-step `ASSET_SAFE_100_PROCESS.md`;
- melompati sub-gate Gate 4;
- menjalankan BUILD untuk mencari tahu apakah asset benar;
- mengubah UNKNOWN menjadi PASS;
- menjadikan keberhasilan compiler sebagai pengganti proof pre-build;
- mengabaikan gate karena perubahan dianggap kecil;
- menggunakan artifact dari build yang melanggar rantai sebagai final artifact;
- mengurangi prosedur karena context/memory agen tidak cukup;
- menggunakan ringkasan sebagai pengganti aturan sumber;
- melanjutkan dari state prosedur yang tidak diketahui;
- menganggap rule yang belum dibaca atau evidence yang hilang sebagai PASS;
- mengganti metode hasil riset aktif dengan metode yang lebih lemah/redundan/ditolak;
- menggunakan stale evidence;
- mengabaikan provenance/transformation/materialization proof yang berlaku;
- menganggap asset sudah dieksekusi berarti semua property contract telah dibuktikan;
- mengabaikan environment dependency yang termasuk supported domain;
- menerima unresolved material defeater untuk final `ASSET_SAFE_100`.

---

# 15. Prinsip Rantai

Rantai ini bersifat berurutan dan fail-closed:

```text
PREPARE
→ INVENTORY/CONTRACT
→ AUDIT
→ ROUTE PROOF
→ PREBUILD CLOSURE
→ BUILD
→ PACKAGE VERIFY
→ RUNTIME TEST
→ FINAL ACCEPTANCE
```

Jika satu tahap tidak terbukti selesai, tahap berikutnya tetap tertutup.

Jika kapasitas agen tidak cukup untuk menjalankan satu tahap secara lengkap sekaligus:

```text
PECAH EKSEKUSI MENJADI UNIT LEBIH KECIL
TETAPI JANGAN KURANGI ATURAN, COVERAGE, PROOF, ATAU EVIDENCE
```
