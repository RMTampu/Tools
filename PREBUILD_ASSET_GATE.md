> Catatan aktif: aturan pola lama antar repository sudah dihapus. ToolBox dikerjakan di repo publik utama; repo private hanya untuk secret, signing, build final, Firebase/final runtime test, dan release sensitif.

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

text
ASSET_SAFE_S7 = PASS
```

---

# 12. Gate 8 — Public Runtime / Emulator / Integration Testing

Artifact Public yang sudah lolos verifikasi package kemudian diuji melalui runtime/emulator/integration sesuai kebutuhan dan aturan repository.

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

text
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

text
PREPARE
→ INVENTORY/CONTRACT
→ AUDIT
→ ROUTE PROOF
→ PREBUILD CLOSURE
→ PUBLIC BUILD / PACKAGE
→ PUBLIC PACKAGE VERIFY
→ PUBLIC RUNTIME TEST
→ PUBLIC ASSET ACCEPTANCE
→ PACKAGE_VALIDATION
→ READY_PRIVATE
```

Jika satu tahap tidak terbukti selesai, tahap berikutnya tetap tertutup.

Jika kapasitas agen tidak cukup untuk menjalankan satu tahap secara lengkap sekaligus:

```text
PECAH EKSEKUSI MENJADI UNIT LEBIH KECIL
TETAPI JANGAN KURANGI ATURAN, COVERAGE, PROOF, ATAU EVIDENCE
```
