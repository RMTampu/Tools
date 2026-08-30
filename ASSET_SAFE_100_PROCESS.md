# ASSET_SAFE_100_PROCESS.md

## 1. Status Dokumen

Dokumen ini menentukan urutan eksekusi operasional untuk menerapkan `ASSET_SAFE_100_RULES.md` bersama metode tambahan yang diterima dalam `ASSET_SAFE_100_METHODS.md`.

Dokumen ini TIDAK membuat rantai baru yang bersaing dengan `PREBUILD_ASSET_GATE.md`.

Sebaliknya, seluruh langkah dalam dokumen ini dipetakan ke Gate 0–9 yang sudah menjadi urutan resmi repository.

Seluruh eksekusi wajib mengikuti `AGENT_PROCEDURE_EXECUTION_RULES.md`.

---

## 2. Aturan Masuk

Sebelum proses ini dimulai, agen wajib membaca:

- `AGENTS.md`;
- `AGENT_PROCEDURE_EXECUTION_RULES.md`;
- `PREBUILD_ASSET_GATE.md`;
- `ASSET_SAFE_100_RULES.md`;
- `ASSET_SAFE_100_METHODS.md`;
- `ASSET_SAFE_100_PROCESS.md`.

Jika scope mempunyai asset route/reference/resolution, agen juga wajib membaca:

- `ASSET_ROUTE_PROOF_METHODS.md`;
- `ASSET_ROUTE_PROOF_PROCESS.md`.

Jika salah satu aturan yang dibutuhkan belum tersedia saat langkah aktif dijalankan:

```text
CURRENT_ASSET_SAFE_STEP = NOT_PROVEN
```

---

# 3. Pemetaan ke Rantai Gate Resmi

Urutan resmi tetap:

```text
GATE 0  — RULE ENTRY / SCOPE LOCK
GATE 1  — ASSET PREPARATION
GATE 2  — ASSET INVENTORY & CONTRACT CLOSURE
GATE 3  — ASSET AUDIT & SOURCE VALIDATION
GATE 4  — ASSET REFERENCE / ROUTE / COMMUNICATION PROOF
GATE 5  — PREBUILD ASSET CLOSURE
================ BUILD BOUNDARY ================
GATE 6  — BUILD
GATE 7  — FINAL ARTIFACT / PACKAGE VERIFICATION
GATE 8  — RUNTIME / EMULATOR / INTEGRATION TESTING
GATE 9  — FINAL ACCEPTANCE
```

Proses internal `ASSET_SAFE_100` yang dipetakan ke gate tersebut adalah:

```text
S0  RULE / SCOPE / SUPPORT-DOMAIN LOCK              -> GATE 0
S1  REQUIREMENT / INTENT / ASSET PREPARATION        -> GATE 1
S2  INVENTORY / CONTRACT / TRACEABILITY CLOSURE     -> GATE 2
S3  SOURCE / FORMAT / PROVENANCE / BUDGET AUDIT     -> GATE 3
S4  ROUTE / CONSUMER / RESOLUTION PROOF             -> GATE 4
S5  PREBUILD EVIDENCE / INPUT / VERIFIER CLOSURE    -> GATE 5
S6  CONTROLLED BUILD                                 -> GATE 6
S7  FINAL PACKAGE / TRANSFORMATION CLOSURE           -> GATE 7
S8  RUNTIME / MATERIALIZATION / ENVIRONMENT PROOF    -> GATE 8
S9  FAULT / ADVERSARIAL / ASSURANCE CLOSURE          -> GATE 9
```

Tidak boleh menukar urutan ini dengan cara yang melanggar `PREBUILD_ASSET_GATE.md`.

---

# 4. S0 — Rule / Scope / Support-Domain Lock

Dipetakan ke `GATE 0`.

Wajib:

- kunci asset scope;
- kunci supported platform/environment boundary;
- kunci configuration/state universe yang berlaku;
- kunci asset-affecting build-input categories;
- kunci required evidence types;
- tentukan apakah route proof berlaku;
- tentukan apakah runtime materialization berlaku;
- tentukan apakah complex text shaping berlaku;
- tentukan apakah external/generated asset input berlaku.

PASS hanya jika tidak ada required scope dimension yang masih ambigu.

Output:

```text
ASSET_SAFE_S0 = PASS
```

---

# 5. S1 — Requirement / Intent / Asset Preparation

Dipetakan ke `GATE 1`.

Wajib:

- siapkan seluruh required asset;
- identifikasi requirement/system-contract/user-intent yang menjadi dasar asset;
- identifikasi semantic role setiap asset yang memerlukan semantic proof;
- identifikasi source/provenance origin;
- identifikasi expected generation/transformation path bila ada;
- identifikasi environment dependency awal.

Tidak boleh memulai inventory/contract closure jika required asset purpose masih tidak diketahui.

Output:

```text
ASSET_SAFE_S1 = PASS
```

---

# 6. S2 — Inventory / Contract / Traceability Closure

Dipetakan ke `GATE 2`.

Wajib menjalankan seluruh aturan inventory/contract pada `ASSET_SAFE_100_RULES.md`, lalu memperluas contract dengan metode yang berlaku dari `ASSET_SAFE_100_METHODS.md`.

Cakupan minimal:

- `EXPECTED_ASSET_SET` closed;
- Asset Contract complete;
- Consumer expectation known;
- requirement → asset traceability complete;
- asset → observable result traceability complete;
- semantic metadata contract complete bila relevan;
- package physical representation contract complete bila loader membutuhkannya;
- environment dependency contract complete bila relevan;
- resource/complexity budget complete;
- property list yang wajib dibuktikan tersedia.

Output:

```text
ASSET_SAFE_S2 = PASS
```

---

# 7. S3 — Source / Format / Provenance / Budget Audit

Dipetakan ke `GATE 3`.

Jalankan seluruh source/type-specific validation pada `ASSET_SAFE_100_RULES.md` dan tambahkan:

- provenance chain source → declared build input;
- canonical path/identity proof;
- format conformance;
- semantic metadata validation;
- text shaping/sequence source proof bila relevan;
- resource budget dan pathological complexity analysis;
- generated-source freshness;
- known asset-affecting build-input closure;
- adversarial/boundary challenge yang dapat dijalankan sebelum build.

Jika source asset valid tetapi provenance, required property, atau metadata yang menentukan makna belum terbukti, S3 tidak PASS.

Output:

```text
ASSET_SAFE_S3 = PASS
```

---

# 8. S4 — Route / Consumer / Resolution Proof

Dipetakan ke `GATE 4`.

Jika scope mempunyai route/reference/resolution, jalankan penuh:

```text
ASSET_ROUTE_PROOF_PROCESS.md
```

hingga menghasilkan:

```text
ROUTE_PROOF_PASS
```

Selain itu pastikan:

- seluruh Consumer Contract tertutup;
- seluruh consumer discovered;
- setiap asset output contract cocok dengan consumer expectation;
- tidak ada unauthorized/bypass route;
- property route terikat ke evidence.

Jika route tidak berlaku pada asset tertentu, `NOT_APPLICABLE` hanya sah jika contract membuktikannya.

Output:

```text
ASSET_SAFE_S4 = PASS
```

---

# 9. S5 — Prebuild Evidence / Input / Verifier Closure

Dipetakan ke `GATE 5`.

Ini adalah lapisan terakhir sebelum build.

Wajib memastikan:

- Gate 0–4 PASS;
- seluruh prebuild contract property telah mempunyai validation method;
- tidak ada stale evidence;
- tidak ada undeclared asset-affecting input;
- transformation plan diketahui;
- required verifier kritis telah mempunyai qualification evidence yang diperlukan;
- common-mode evidence risk yang material sudah diselesaikan atau mempunyai independent corroboration;
- proof invalidation state bersih;
- prebuild fault model sudah mencakup kelas yang diketahui sampai titik ini.

S5 hanya PASS jika:

```text
PREBUILD_ASSET_GATE = PASS
```

dan seluruh method prebuild yang berlaku dari `ASSET_SAFE_100_METHODS.md` tidak mempunyai status `NOT_PROVEN`.

Output:

```text
ASSET_SAFE_S5 = PASS
BUILD_UNLOCKED = TRUE
```

Jika S5 bukan PASS:

```text
BUILD_UNLOCKED = FALSE
```

---

# 10. S6 — Controlled Build

Dipetakan ke `GATE 6`.

Build hanya dilakukan setelah `ASSET_SAFE_S5 = PASS` dan sesuai `AGENTS.md`.

Selama build, capture identity dari input/output yang diperlukan untuk provenance dan transformation proof.

Jika build menggunakan input berbeda dari yang dibuktikan pada prebuild:

```text
INVALIDATE_FROM_EARLIEST_AFFECTED_GATE
```

Artifact dari build yang melanggar kondisi tersebut tidak boleh menjadi final evidence.

Output:

```text
ASSET_SAFE_S6 = PASS
```

---

# 11. S7 — Final Package / Transformation Closure

Dipetakan ke `GATE 7`.

Jalankan seluruh final-package proof pada `ASSET_SAFE_100_RULES.md` dan tambahkan:

- source/intermediate/final provenance continuity;
- transformation refinement/translation validation;
- semantic identity after compilation/link/optimization;
- final package physical representation contract;
- compression/seekability/alignment/storage location bila relevan;
- package entry uniqueness;
- expected module/split/delivery availability bila relevan;
- independent package inspection untuk critical evidence bila diperlukan;
- evidence binding ke artifact identity final.

Build success tidak otomatis PASS.

Output:

```text
ASSET_SAFE_S7 = PASS
```

---

# 12. S8 — Runtime / Materialization / Environment Proof

Dipetakan ke `GATE 8`.

Jalankan exhaustive runtime exercise dari `ASSET_SAFE_100_RULES.md`, lalu tambahkan:

- runtime materialization equivalence bila asset disalin/diekstrak/di-cache;
- real consumer use setelah materialization;
- required environment equivalence-class witnesses;
- complete property-to-evidence observation;
- text shaping/grapheme/fallback witness bila relevan;
- expanded CPU/time/I/O/memory/complexity budget measurement;
- visual/semantic oracle checks;
- state/configuration witnesses;
- independent oracle corroboration untuk critical semantic asset bila diwajibkan.

Tidak cukup hanya membuktikan asset dapat dibuka atau aplikasi tidak crash.

Wajib:

```text
Required contract properties = N
Runtime/other evidence proven = N
Unobserved                    = 0
```

Output:

```text
ASSET_SAFE_S8 = PASS
```

---

# 13. S9 — Fault / Adversarial / Assurance Closure

Dipetakan ke `GATE 9` dan dilakukan sebelum final acceptance.

Wajib:

- jalankan mutation proof seluruh defined fault classes;
- jalankan adversarial/boundary/metamorphic challenge yang berlaku;
- jika muncul fault class baru, buka kembali fault model dan proof terkait;
- audit verifier disagreement;
- audit stale/unbound evidence;
- audit common-mode critical evidence;
- audit unresolved material defeaters;
- audit claim → invariant → evidence → premise traceability;
- pastikan route proof masih valid bila berlaku;
- pastikan seluruh evidence terikat ke artifact/input aktif.

Final closure membutuhkan:

```text
FAULT_ESCAPE = 0
UNRESOLVED_NEW_FAULT_CLASS = 0
UNRESOLVED_MATERIAL_DEFEATER = 0
MATERIAL_RESIDUAL_DOUBT = 0
UNKNOWN = 0
MISSING = 0
UNPROVEN = 0
SKIPPED = 0
```

Output akhir:

```text
ASSET_SAFE_100_PROCESS = PASS
```

---

# 14. Formula Final

Final `ASSET_SAFE_100` hanya boleh dinyatakan jika:

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
AND ASSET_SAFE_100_RULES_FORMULA = PASS
AND ASSET_SAFE_100_METHODS_FORMULA = PASS
```

Jika route proof berlaku:

```text
AND ROUTE_PROOF_PASS
```

Final output:

```text
ASSET_SAFE_100_PROCESS = PASS
FINAL STATUS = ASSET_SAFE_100
```

---

# 15. Proof Invalidation

Jika terjadi perubahan pada:

- requirement;
- asset;
- contract;
- source;
- generator;
- consumer;
- route;
- dependency;
- configuration;
- build input;
- build tool/config yang dapat mengubah asset;
- package transform;
- runtime materialization;
- environment/support boundary;
- verifier;
- oracle;
- evidence premise;

maka agen wajib menentukan S-step/Gate paling awal yang terdampak dan menjalankan ulang seluruh downstream proof.

Tidak boleh mempertahankan PASS lama hanya karena perubahan dianggap kecil.

---

# 16. Aturan Kapasitas Agen

Jika keseluruhan S-step terlalu besar untuk diproses sekaligus, pecah menjadi:

```text
S-STEP
→ METHOD GROUP
→ RULE
→ PROPERTY
→ INDIVIDUAL EVIDENCE CHECK
```

Namun aturan, coverage, dan syarat PASS tidak boleh dikurangi.

Ikuti `AGENT_PROCEDURE_EXECUTION_RULES.md` secara penuh.

---

# 17. Larangan

Agen dilarang:

- melompati S-step yang berlaku;
- menjalankan build sebelum S5 PASS;
- menganggap runtime exercise membuktikan property yang tidak diamati;
- menerima provenance chain yang putus;
- menggunakan stale evidence;
- mengabaikan transformation proof;
- mengabaikan physical package contract yang diperlukan loader;
- mengabaikan runtime materialization;
- mengabaikan environment dependency yang memengaruhi contract;
- menganggap glyph codepoint coverage cukup bila contract membutuhkan shaping/sequence proof;
- menggunakan satu oracle kritis tanpa menilai common-mode risk;
- menganggap verifier benar hanya karena verifier menghasilkan PASS;
- menghapus fault class baru agar final status tetap hijau;
- menerima residual doubt material untuk klaim `ASSET_SAFE_100`.