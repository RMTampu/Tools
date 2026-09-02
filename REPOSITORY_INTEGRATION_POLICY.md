# ToolBox Repository Integration Policy

## 1. Status dan Tujuan

Dokumen ini menetapkan pembagian tanggung jawab resmi antara repository private `RMTampu/ToolBox` dan repository public `RMTampu/Tools`.

Keputusan ini menggantikan konsep lama yang menempatkan `RMTampu/Tools` sebagai pusat source/product ToolBox.

```text
RMTampu/ToolBox (PRIVATE)
= MASTER SOURCE / PRODUCT TRUTH

RMTampu/Tools (PUBLIC)
= BUILD / TEST / CI EXECUTION TOOLING
```

Repository lain yang dipakai khusus backup final tetap terpisah dan tidak menjadi source of truth pengembangan.

## 2. Peran `RMTampu/ToolBox` — Private Master

`RMTampu/ToolBox` adalah pusat pengembangan ToolBox dan sumber kebenaran utama untuk source aplikasi/kernel aktif, asset/resource pengembangan, project configuration/dependency lock, manifest/contract/registry input produk, dokumen rancangan master, keputusan arsitektur, aturan pengembangan product source, dan metadata build candidate.

Perubahan produk harus dibuat dan disimpan di repository private ini terlebih dahulu. Source/asset private tidak boleh dipindahkan ke repository public hanya agar CI lebih mudah.

## 3. Peran `RMTampu/Tools` — Public Build/Test Engine

`RMTampu/Tools` dipertahankan karena sudah menjadi jalur build/test dan terhubung dengan layanan eksternal seperti Firebase.

Repository ini berfungsi sebagai reusable GitHub Actions workflow, build/test orchestration, validator/verifier CI, Firebase/Test Lab bridge sesuai authorization policy, CI helper scripts yang tidak mengandung source/asset private, dan public execution contract untuk project private yang memanggilnya.

`RMTampu/Tools` BUKAN lagi master source ToolBox dan BUKAN tempat menentukan rancangan produk terbaru. Source/product copy lama yang masih tertinggal selama migrasi adalah `LEGACY_MIGRATION_COPY`, bukan sumber kebenaran.

## 4. Arah Kerja Resmi

```text
RMTampu/ToolBox private
        │ caller workflow / explicit CI request
        ▼
RMTampu/Tools public
        │ reusable workflow + validator + test tooling
        ▼
Build / Test / Verification
        │
        ▼
Result kembali ke caller/private project
```

Aturan penting:

1. source yang dibuild adalah commit/ref dari caller private `RMTampu/ToolBox`;
2. workflow `RMTampu/Tools` harus dipin ke tag atau commit SHA yang tervalidasi;
3. build tidak boleh diam-diam memakai source lama yang tersimpan di repository public;
4. workflow public tidak boleh mengubah master source private tanpa aksi write terpisah yang memang diminta pengguna;
5. setiap hasil harus dapat ditelusuri ke `SOURCE_COMMIT_SHA` dan `CI_WORKFLOW_REF`;
6. source/asset private tidak boleh dicetak ke log atau dimasukkan ke artifact public.

## 5. Model Reusable Workflow

Default yang aman adalah private repository menjadi caller dan public `Tools` menyediakan reusable workflow.

```text
ToolBox/.github/workflows/build.yml
        │
        └─ uses: RMTampu/Tools/.github/workflows/<workflow>@<PINNED_REF>
```

Source private di-checkout dari caller repository. Secret private diberikan hanya jika workflow benar-benar memerlukannya; permission dibuat minimum.

Jika integrasi eksternal mengharuskan workflow dieksekusi langsung dari `RMTampu/Tools`, jalur tersebut harus menjadi bridge eksplisit, least-privilege, dan tidak boleh mengekspos source/asset private.

## 6. Asset dan Resource Private

Master asset/resource ToolBox berada di `RMTampu/ToolBox` atau repository private asset khusus yang kemudian ditetapkan.

```text
MASTER TOOLBOX ASSET
→ RMTampu/ToolBox
```

Asset private tidak dimirror ke `RMTampu/Tools` kecuali diklasifikasikan public. CI memakai asset dari checkout caller/private workspace. Perubahan lokasi repository tidak mengubah logical identity asset. Source copy lama hanya boleh dihapus setelah salinan private diverifikasi.

## 7. Dokumen dan Aturan

Dokumen rancangan master berada di `RMTampu/ToolBox`.

`RMTampu/Tools` boleh menyimpan salinan aturan/prosedur yang diperlukan build/test, tetapi salinan tersebut adalah **CI execution copy**, bukan master arsitektur produk.

Jika dokumen lama menyebut `RMTampu/Tools` sebagai repository pusat ToolBox, interpretasi tersebut sudah tidak berlaku.

Urutan otoritas:

```text
Instruksi pengguna terbaru
→ AGENTS.md repository terkait
→ REPOSITORY_INTEGRATION_POLICY.md
→ policy test/build khusus
→ dokumen lain
```

## 8. Build dan Test

Build APK tetap hanya melalui GitHub Actions. `RMTampu/Tools` adalah definisi mesin CI bersama, sedangkan commit source berasal dari project private yang memanggilnya.

Setiap build wajib merekam minimal:

```text
SOURCE_REPOSITORY
SOURCE_COMMIT_SHA
SOURCE_REF
CI_REPOSITORY = RMTampu/Tools
CI_WORKFLOW_REF
DEPENDENCY_LOCK_ID / DIGEST bila berlaku
```

Satu hasil build tidak boleh dipromosikan jika identitas source atau workflow tidak diketahui.

## 9. Firebase Final Gate

Keberadaan integrasi Firebase pada `RMTampu/Tools` tidak memberikan izin otomatis menjalankan Firebase.

```text
FIREBASE DEFAULT = LOCKED
1 EXPLICIT USER APPROVAL = 1 FIREBASE EXECUTION ATTEMPT
```

Tidak boleh auto-run atau auto-retry. Firebase final target tetap Android 11 / API 30 / ARM64 sesuai policy aktif.

## 10. Secret dan Credential Boundary

Credential untuk private source/asset, signing, Firebase, atau backup wajib least-privilege, dipisahkan menurut fungsi, tidak disimpan di source, tidak dicetak ke log, tidak dimasukkan ke APK/patch/artifact/cache public, dan tidak diteruskan bila tidak dibutuhkan.

## 11. Backup Repository

Repository private backup aplikasi tetap kosong sampai ada project `FINAL READY`. Backup final bukan workspace build/development source.

Format utama per app final:

```text
App.apk
App.patch
```

beserta manifest/checksum untuk verification. Delete project lokal/private source tidak otomatis menghapus backup final remote.

## 12. Migration Safety

```text
COPY TO PRIVATE
→ VERIFY CONTENT / IDENTITY / REFERENCES
→ UPDATE MASTER/CI ROLE
→ UPDATE WORKFLOW ROUTING
→ AUDIT DELTA
→ BARU HAPUS COPY YANG MEMANG HARUS DIPINDAHKAN
```

Tidak boleh menghapus source/asset lama sebelum salinan tujuan terbukti tersedia dan dapat dirujuk dengan benar. Perubahan lokasi repository membatalkan proof yang bergantung pada path/repository identity dan bagian tersebut harus divalidasi ulang.

## 13. Invariant Akhir

```text
PRODUCT_MASTER = RMTampu/ToolBox
CI_ENGINE      = RMTampu/Tools
PUBLIC_PRIVATE_SOURCE_LEAK = 0
SILENT_BUILD_FROM_STALE_PUBLIC_SOURCE = 0
UNPINNED_SHARED_WORKFLOW = 0
UNKNOWN_BUILD_SOURCE = 0
UNAUTHORIZED_FIREBASE_RUN = 0
```

Setiap agen wajib mempertahankan invariant tersebut.