# AGENTS.md — ToolBox Public Build/Test Engine

## 1. Wajib Dibaca Sebelum Bekerja

Setiap agen yang membaca, mengubah, membangun, menguji, memvalidasi, atau mengaudit repository `RMTampu/Tools` WAJIB membaca file ini terlebih dahulu.

Untuk pekerjaan gate, asset, application safety, Android target, build, test, Firebase, atau prosedur, baca juga `AGENTS_LEGACY_RULES.md` dan file aturan khusus yang dirujuk di sana.

Perubahan peran repository tidak membatalkan aturan safety/gate. Hanya lokasi product master dan pembagian tanggung jawab repository yang berubah.

## 2. Identitas Repository Terkini

```text
RMTampu/ToolBox (PRIVATE)
= MASTER SOURCE / PRODUCT / ASSET / RANCANGAN / PRODUCT VERIFICATION STATE

RMTampu/Tools (PUBLIC)
= BUILD / TEST / CI EXECUTION ENGINE / FIREBASE BRIDGE
```

Migrasi product source/asset dari public `Tools` telah selesai. Repository ini **tidak boleh** kembali menjadi penyimpanan product source, product asset/resource, product Gradle workspace, master rancangan, atau product verification state.

Baca dan patuhi `REPOSITORY_INTEGRATION_POLICY.md` untuk pekerjaan lintas repository.

## 3. Peran yang Dipertahankan di Tools

Repository ini hanya dipakai untuk:

- reusable GitHub Actions workflow;
- build/test orchestration;
- validator/verifier CI;
- CI helper/tooling yang tidak berisi product source/asset private;
- Firebase/Test Lab bridge;
- development/basic/intermediate/regression test tooling;
- final-gate execution tooling setelah authorization yang sah;
- CI execution copies dari rule/procedure yang memang dibutuhkan.

Tools tetap dipertahankan karena sudah terhubung ke jalur build/test dan layanan eksternal seperti Firebase.

## 4. Alur Resmi Private → Public CI

```text
RMTampu/ToolBox private
→ caller workflow / explicit CI request
→ pinned reusable workflow di RMTampu/Tools
→ build/test/verification terhadap caller source
→ result kembali ke caller/private project
```

Wajib:

- build source berasal dari exact private caller commit/ref;
- reusable workflow dipin ke commit SHA tervalidasi;
- checkout harus mengambil caller source, bukan source lokal public;
- jangan menulis source/asset private ke log/artifact public;
- provenance minimal mencatat `SOURCE_REPOSITORY`, `SOURCE_COMMIT_SHA`, `SOURCE_REF`, `CI_REPOSITORY`, `CI_WORKFLOW_REF`;
- secret/credential least-privilege dan hanya diberikan ke job yang memerlukannya.

## 5. Aturan Build dan Gate

APK Android hanya dibangun melalui GitHub Actions. Termux bukan lingkungan build dan package/tool tambahan tidak boleh diinstal di Termux tanpa izin eksplisit pengguna.

Build tidak boleh dipakai untuk mencari tahu apakah prebuild gate seharusnya PASS.

Sumber prosedur aktif mencakup:

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

Target release ToolBox tetap Android 11 / API 30 / `arm64-v8a`. GitHub development test environment boleh fleksibel sesuai `TEST_ROUTING_POLICY.md`. Hasil non-API30/non-ARM64 tidak boleh diklaim sebagai final Android 11 ARM64 runtime proof.

## 7. Firebase Final Gate

```text
FIREBASE DEFAULT STATE = LOCKED
1 EXPLICIT USER APPROVAL = 1 FIREBASE EXECUTION ATTEMPT
```

Dilarang auto-run, auto-retry, memakai approval lama, atau fallback final target ke API/ABI lain. Setelah GitHub development evidence PASS, berhenti sebelum Firebase dan minta approval eksplisit pengguna untuk satu execution attempt.

## 8. Asset Boundary

Master asset ToolBox berada di private `RMTampu/ToolBox`. Public Tools tidak boleh menyimpan master maupun salinan product asset private. CI mengambil asset dari caller/private workspace atau mekanisme scoped yang disetujui. Perubahan path/repository asset membatalkan proof route/path yang terdampak.

## 9. Dokumentasi

Master rancangan berada di private `RMTampu/ToolBox`. Dokumen di Tools hanya boleh menjadi aturan/prosedur/kontrak eksekusi CI yang memang diperlukan. Tidak boleh ada master rancangan produk di repository public ini.

## 10. Repository Change Safety

Perubahan workflow shared, toolchain, dependency handling, source routing, secret routing, artifact routing, atau Firebase bridge membatalkan proof terkait dan wajib diaudit ulang. Workflow shared final harus tetap pinned dan fail-closed.

## 11. Urutan Otoritas

```text
Instruksi pengguna terbaru
→ AGENTS.md repository terkait
→ REPOSITORY_INTEGRATION_POLICY.md
→ TEST_ROUTING_POLICY.md untuk test/Firebase
→ AGENTS_LEGACY_RULES.md dan rule/procedure files aktif
→ dokumentasi lain
```

`AGENTS_LEGACY_RULES.md` mempertahankan aturan teknis lintas generasi tetapi tidak boleh mengubah pembagian repository terkini.

## 12. Invariant

```text
PRODUCT_MASTER = RMTampu/ToolBox
CI_ENGINE = RMTampu/Tools
PRODUCT_SOURCE_IN_PUBLIC = 0
PRODUCT_ASSET_IN_PUBLIC = 0
PRIVATE_SOURCE_LEAK = 0
SILENT_STALE_PUBLIC_BUILD = 0
UNPINNED_FINAL_CI_WORKFLOW = 0
UNAUTHORIZED_FIREBASE_RUN = 0
```
