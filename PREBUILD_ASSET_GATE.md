# PREBUILD_ASSET_GATE.md

## 1. Status Dokumen

Dokumen ini adalah rantai gate wajib untuk seluruh pekerjaan asset/resource sebelum build, saat build, sesudah build, dan pengujian pada repository `RMTampu/Tools`.

Dokumen ini tidak menggantikan `ASSET_SAFE_100_RULES.md`. Dokumen ini menentukan urutan wajib kapan setiap kelompok aturan harus dijalankan.

Seluruh gate dalam dokumen ini WAJIB dijalankan sesuai `AGENT_PROCEDURE_EXECUTION_RULES.md`. Keterbatasan context/memory agen hanya boleh menyebabkan eksekusi dipecah menjadi unit yang lebih kecil; tidak boleh menyebabkan aturan, langkah, coverage, proof, atau evidence dikurangi.

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

Jika asset, contract, route, configuration, dependency, atau input lain berubah setelah suatu gate dinyatakan PASS, seluruh gate sesudah titik perubahan tersebut dianggap tidak valid dan wajib dijalankan kembali dari gate paling awal yang terdampak.

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

---

# 4. Gate 0 — Rule Entry / Scope Lock

Sebelum asset disentuh:

- baca `AGENTS.md`;
- baca `AGENT_PROCEDURE_EXECUTION_RULES.md`;
- baca `PREBUILD_ASSET_GATE.md`;
- baca `ASSET_SAFE_100_RULES.md`;
- ikuti seluruh referensi aturan tambahan yang diwajibkan oleh scope aktif;
- tentukan scope asset dan perubahan yang sedang dikerjakan;
- jangan memulai gate berikutnya bila scope atau aturan aktif belum diketahui lengkap.

Jika semua aturan tidak dapat dipertahankan secara lengkap sekaligus, Gate 0 WAJIB menetapkan eksekusi bertahap sesuai `AGENT_PROCEDURE_EXECUTION_RULES.md` sebelum pekerjaan dilanjutkan.

---

# 5. Gate 1 — Asset Preparation

Tahap penyiapan seluruh asset yang dibutuhkan oleh scope kerja.

Gate ini belum boleh melakukan final build.

Detail aturan gate ini akan dikembangkan pada dokumen/rule khusus tanpa mengubah urutan rantai ini.

---

# 6. Gate 2 — Asset Inventory & Contract Closure

Seluruh asset required harus sudah diketahui dan mempunyai contract yang diperlukan sebelum audit mendalam dimulai.

Detail aturan mengikuti `ASSET_SAFE_100_RULES.md` dan dokumen turunan yang nantinya ditetapkan.

---

# 7. Gate 3 — Asset Audit & Source Validation

Audit asset dilakukan sebelum build untuk memastikan source asset, identitas, format, semantic expectation, dependency, configuration, dan requirement yang relevan sudah benar terhadap contract.

Gate ini harus PASS sebelum pembuktian jalur komunikasi dianggap final.

---

# 8. Gate 4 — Asset Reference / Route / Communication Proof

Seluruh jalur consumer → asset, asset → asset, alias, dependency, configuration → variant, fallback, dan jalur dinamis yang termasuk scope wajib dibuktikan sesuai metode aktif yang telah diterima.

Tidak boleh ada jalur putus, salah alamat, ambigu, tidak diketahui, atau bypass yang belum diselesaikan.

---

# 9. Gate 5 — Prebuild Asset Closure

Gate ini adalah keputusan terakhir sebelum build.

BUILD hanya boleh dibuka bila seluruh gate 0 sampai 4 telah PASS dan tidak ada proof wajib yang masih UNKNOWN, SKIPPED, INCOMPLETE, INDETERMINATE, NOT_LOADED, NOT_READ, atau NOT_PROVEN.

Status:

```text
PREBUILD_ASSET_GATE = PASS
```

adalah syarat wajib untuk memasuki Gate 6.

---

# 10. Gate 6 — Build

Build hanya dilakukan melalui GitHub Actions sesuai `AGENTS.md`.

Build yang dimulai tanpa `PREBUILD_ASSET_GATE = PASS` adalah pelanggaran aturan repository dan hasilnya tidak boleh digunakan sebagai bukti final.

---

# 11. Gate 7 — Final Artifact / Package Verification

Setelah build, artifact final wajib diverifikasi untuk memastikan hasil package mempertahankan asset yang sudah dibuktikan sebelum build.

Build sukses tidak otomatis berarti gate ini PASS.

---

# 12. Gate 8 — Runtime / Emulator / Integration Testing

Artifact yang sudah lolos verifikasi package kemudian diuji melalui runtime/emulator/integration sesuai kebutuhan dan aturan repository.

Kegagalan tool tidak otomatis menjadi `FAIL_ASSET`; klasifikasi tetap mengikuti `ASSET_SAFE_100_RULES.md`.

---

# 13. Gate 9 — Final Acceptance

Final acceptance hanya boleh diberikan bila seluruh gate yang diwajibkan oleh scope telah selesai dan seluruh bukti yang diperlukan tersedia.

Tidak boleh menyatakan asset/build selesai hanya karena APK berhasil dikompilasi.

---

# 14. Aturan Anti-Skip

Dilarang:

- melompati gate;
- menjalankan BUILD untuk mencari tahu apakah asset benar;
- mengubah UNKNOWN menjadi PASS;
- menjadikan keberhasilan compiler sebagai pengganti proof pre-build;
- mengabaikan gate karena perubahan dianggap kecil;
- menggunakan artifact dari build yang melanggar rantai sebagai final artifact;
- mengurangi prosedur karena context/memory agen tidak cukup;
- menggunakan ringkasan sebagai pengganti aturan sumber;
- melanjutkan dari state prosedur yang tidak diketahui;
- menganggap rule yang belum dibaca atau evidence yang hilang sebagai PASS.

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
