# TASK — Tools Public CI Engine

## Peran Repository

`RMTampu/Tools` dipertahankan sebagai **public Build/Test/CI/Firebase bridge** karena sudah terdaftar pada integrasi eksternal.

Master product ToolBox telah dipindahkan ke private `RMTampu/ToolBox`.

```text
RMTampu/ToolBox private
= source / product / asset / rancangan master

RMTampu/Tools public
= build / test / validator / Firebase bridge
```

## Tugas migrasi aktif

1. Pastikan semua source/product asset yang masih berada di `Tools` telah disalin dan diverifikasi di `ToolBox`.
2. Setelah verifikasi, hapus source/product asset tersebut dari `Tools`.
3. Jangan meninggalkan asset pengembangan ToolBox di repository public `Tools`.
4. Pertahankan workflow, build/test verifier, Firebase bridge, dan dokumen CI yang memang diperlukan.
5. Ubah workflow agar menggunakan source/ref caller private, bukan stale product source yang tersimpan lokal di `Tools`.
6. Pin reusable workflow/version yang digunakan project private.
7. Lakukan audit delta setelah migrasi dan routing selesai.

## Invariant

```text
PRODUCT_ASSET_LEFT_IN_TOOLS = 0
STALE_PUBLIC_PRODUCT_SOURCE_USED_FOR_BUILD = 0
PUBLIC_PRIVATE_SOURCE_LEAK = 0
UNKNOWN_BUILD_SOURCE = 0
```

## Firebase

Firebase tetap `LOCKED`.

```text
1 EXPLICIT USER APPROVAL = 1 FIREBASE EXECUTION ATTEMPT
```
