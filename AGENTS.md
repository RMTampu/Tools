# AGENTS.md — ToolBox Public Build/Test Engine

## 1. Wajib Dibaca Sebelum Bekerja

Setiap agen yang membaca, mengubah, membangun, menguji, memvalidasi, atau mengaudit repository `RMTampu/Tools` WAJIB membaca file ini terlebih dahulu.

Untuk menjaga seluruh aturan lama tanpa kehilangan coverage, agen juga WAJIB membaca `AGENTS_LEGACY_RULES.md` untuk pekerjaan yang menyentuh gate, asset, application safety, Android target, build, test, Firebase, atau prosedur yang dirujuk di sana.

**Perubahan peran repository ini tidak membatalkan aturan safety/gate lama.** Hanya identitas dan pembagian tanggung jawab repository yang berubah.

## 2. Identitas Repository Terkini

```text
RMTampu/ToolBox (PRIVATE)
= MASTER SOURCE / PRODUCT / ASSET / RANCANGAN

RMTampu/Tools (PUBLIC)
= BUILD / TEST / CI EXECUTION ENGINE
```

`RMTampu/Tools` bukan lagi pusat source/rancangan produk ToolBox. Source/product copy lama yang masih ada di repository ini selama migrasi adalah `LEGACY_MIGRATION_COPY`.

Baca dan patuhi `REPOSITORY_INTEGRATION_POLICY.md` untuk seluruh pekerjaan lintas repository.

## 3. Peran yang Dipertahankan di Tools

Repository ini tetap menjadi tempat untuk:

- reusable GitHub Actions workflow;
- build orchestration;
- test orchestration;
- validator/verifier CI;
- CI helper scripts;
- Firebase/Test Lab bridge;
- development/basic/intermediate/regression test tooling;
- final-gate execution tooling setelah authorization yang sah.

Tools tetap dipertahankan karena sudah terhubung ke berbagai layanan build/test termasuk Firebase.

## 4. Alur Resmi Private → Public CI

```text
RMTampu/ToolBox private
→ caller workflow / explicit CI request
→ pinned reusable workflow di RMTampu/Tools
→ build/test/verification
→ result kembali ke caller/private project
```

Wajib:

- build source berasal dari exact private source commit/ref yang diminta;
- reusable workflow dipin ke ref/tag/commit tervalidasi;
- jangan diam-diam build source lama yang tersimpan di Tools;
- jangan menulis source/asset private ke log/artifact public;
- catat `SOURCE_REPOSITORY`, `SOURCE_COMMIT_SHA`, `SOURCE_REF`, `CI_REPOSITORY`, dan `CI_WORKFLOW_REF`;
- secret/credential least-privilege dan hanya diberikan ke job yang memerlukannya.

## 5. Aturan Build

APK Android hanya dibangun melalui GitHub Actions.

Termux bukan lingkungan build dan package/tool tambahan tidak boleh diinstal di Termux tanpa izin eksplisit pengguna.

Build tidak boleh digunakan untuk mencari tahu apakah prebuild gate seharusnya PASS.

Seluruh aturan prebuild/application/asset yang berlaku tetap mengikuti file sumber aktif, termasuk:

- `AGENT_PROCEDURE_EXECUTION_RULES.md`;
- `PREBUILD_ASSET_GATE.md`;
- `ASSET_SAFE_100_RULES.md`;
- `ASSET_SAFE_100_METHODS.md`;
- `ASSET_SAFE_100_PROCESS.md`;
- `ASSET_ROUTE_PROOF_METHODS.md`;
- `ASSET_ROUTE_PROOF_PROCESS.md`;
- `APPLICATION_SAFE_100_PROCESS.md`;
- `APP_SAFE_R1_LOGIC_INPUT.md` sampai `APP_SAFE_R9_VERIFICATION_COMPLETENESS.md`.

## 6. Android Target dan Test Routing

Target release ToolBox tetap:

```text
Android 11
API 30
arm64-v8a
```

GitHub development test environment boleh fleksibel sesuai `TEST_ROUTING_POLICY.md`.

Hasil non-API30/non-ARM64 tidak boleh diklaim sebagai final Android 11 ARM64 runtime proof.

## 7. Firebase Final Gate

Firebase tetap:

```text
FIREBASE DEFAULT STATE = LOCKED
1 EXPLICIT USER APPROVAL = 1 FIREBASE EXECUTION ATTEMPT
```

Dilarang auto-run, auto-retry, memakai approval lama, atau fallback final target ke API/ABI lain.

Setelah GitHub development evidence PASS, agen harus berhenti sebelum Firebase dan meminta approval eksplisit pengguna untuk satu execution attempt.

## 8. Asset Boundary

Master asset ToolBox berada di private `RMTampu/ToolBox`.

Public Tools tidak boleh menjadi penyimpanan master asset private.

Jika runtime/CI membutuhkan asset private, gunakan checkout/input dari caller private atau mekanisme scoped yang sesuai policy. Jangan menyalin asset private ke public hanya untuk mempermudah build.

Perubahan path/repository asset membatalkan proof yang bergantung pada route/path dan wajib diaudit ulang.

## 9. Dokumentasi

Dokumen rancangan master berada di private `RMTampu/ToolBox`.

Dokumen aturan/prosedur di Tools adalah **CI execution copies** bila diperlukan oleh build/test.

Bila ada wording lama di dokumen Tools yang seolah menempatkan Tools sebagai pusat produk, interpretasinya digantikan oleh:

```text
PRODUCT_MASTER = RMTampu/ToolBox
CI_ENGINE      = RMTampu/Tools
```

Untuk isi teknis/gate yang tidak terkait pembagian repository, aturan lama tetap berlaku penuh sampai secara eksplisit direvisi.

## 10. Migration Safety

Urutan wajib:

```text
COPY TO PRIVATE
→ VERIFY
→ UPDATE REFERENCES / ROUTING
→ AUDIT DELTA
→ DELETE SOURCE COPY hanya jika memang harus dipindahkan
```

Jangan menghapus asset/source lama sebelum salinan private dan reference-nya diverifikasi.

## 11. Urutan Otoritas

Jika ada konflik:

```text
Instruksi pengguna terbaru
→ AGENTS.md repository terkait
→ REPOSITORY_INTEGRATION_POLICY.md
→ TEST_ROUTING_POLICY.md untuk test/Firebase
→ AGENTS_LEGACY_RULES.md dan rule/procedure files aktif
→ dokumentasi lain
```

`AGENTS_LEGACY_RULES.md` mempertahankan detail aturan sebelumnya; ia bukan sumber untuk menentukan repository master setelah migrasi.

## 12. Invariant

```text
PRODUCT_MASTER = RMTampu/ToolBox
CI_ENGINE = RMTampu/Tools
PRIVATE_SOURCE_LEAK = 0
SILENT_STALE_PUBLIC_BUILD = 0
UNPINNED_FINAL_CI_WORKFLOW = 0
UNAUTHORIZED_FIREBASE_RUN = 0
```
