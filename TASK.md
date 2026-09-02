# TASK — ToolBox

## Status Audit Editor

- Audit Putaran 2: **PASS pada arsitektur Editor inti**.
- Blocker arsitektur Editor: **0**.
- GAP arsitektur Editor: **0**.
- Keputusan audit yang sudah diterima:
  - Edit Bridge berbasis adapter/capability untuk aplikasi terinstal.
  - Self-Edit Protection dengan staging, validation, recovery point, activation, dan rollback.
  - `dependency.lock` per project.
  - Delete project lokal tidak otomatis menghapus backup GitHub.
  - Round-trip tracking untuk project eksternal dan Sync ke Source.
  - Remote backup diverifikasi dengan checksum/manifest sebelum `BACKUP VERIFIED`.
  - Credential asset private dibatasi dan tidak boleh masuk source, log, artifact, APK, atau repo publik.

## Tugas Repository Berikutnya

1. **Repo backup private**
   - Biarkan kosong sampai ada project yang benar-benar `FINAL READY`.
   - Setelah ada final project, backup utama per app tetap `App.apk` + `App.patch` beserta metadata/checksum yang diperlukan untuk verifikasi.

2. **Repo private `RMTampu/ToolBox` sebagai master pengembangan pertama**
   - Pindahkan seluruh asset pengembangan ToolBox yang relevan dari repo lama ke repo private `RMTampu/ToolBox`.
   - Sebelum memindahkan asset, baca dan patuhi `ASSET_SAFE_100_RULES.md`, `PREBUILD_ASSET_GATE.md`, serta aturan repository yang berlaku.
   - Jangan menghapus asset sumber sebelum salinan tujuan diverifikasi lengkap dan valid.

3. **Dokumen rancangan master**
   - Salin dokumen rancangan `.md` ToolBox yang sudah dikonsolidasikan ke repo private `RMTampu/ToolBox`.
   - Jadikan salinan di repo private tersebut sebagai **Master Rancangan — Salinan Pertama**.
   - Konsolidasikan keputusan terbaru terlebih dahulu agar konsep lama yang sudah diganti tidak ikut menjadi sumber master yang ambigu.

4. **Repo publik Build/Test bersama**
   - Siapkan satu repo publik sebagai reusable build/test engine untuk project private.
   - Project private menjadi caller; source project tetap private.
   - Pin workflow ke tag/commit SHA yang tervalidasi.
   - Credential untuk asset/backup private harus least-privilege dan terpisah.

5. **Audit setelah perubahan repository**
   - Setelah pemindahan asset dan penetapan master selesai, lakukan audit delta untuk memastikan referensi asset, manifest, path, dependency lock, build input, dan backup routing tetap konsisten.

## Catatan

- Jangan mengisi repo backup sebelum ada project final.
- Jangan menyatakan repository migration selesai sebelum integritas asset dan referensi telah diverifikasi.
- Rancangan Editor inti sudah tidak memiliki GAP/BLOCKER yang diketahui; perubahan struktur repository hanya memerlukan audit delta pada storage/asset/build routing sebelum status keseluruhan disebut final.
