# TOOLBOX PUBLIC REPOSITORY WORK RULES

## Status

Dokumen ini menggantikan aturan lama Public-Private lintas repo.

Mulai sekarang ToolBox memakai pola sederhana: repo utama publik untuk pengembangan dan pematangan aplikasi, repo private hanya untuk build final, signing, credential, Firebase/final runtime test, dan release sensitif.

## 1. Repo Utama Publik

Repo publik utama adalah tempat kerja normal untuk:

- rancangan;
- source aplikasi;
- asset yang memang boleh terlihat publik;
- implementasi fitur;
- test;
- workflow pengembangan;
- dokumentasi;
- pematangan sampai aplikasi siap dibuild.

Semua yang masuk repo publik dianggap boleh dilihat orang lain.

## 2. Repo Private Build/Signing

Repo private hanya dipakai untuk hal yang tidak boleh terlihat publik:

- signing key;
- token dan secret;
- credential Firebase;
- credential layanan eksternal;
- build final tertandatangan;
- verifikasi signature;
- Firebase/final runtime test;
- release yang membutuhkan data rahasia.

Repo private bukan tempat memecah tahap pengembangan atau mengulang trial-and-error fitur.

## 3. Aturan Lintas Repo Lama Dihapus

Aturan berikut tidak berlaku lagi:

- Public Research / Test / Staging sebagai repo terpisah;
- Private Master sebagai source of truth pengembangan;
- Promotion Package;
- Stage Capsule;
- STAGE_READY_PRIVATE;
- COMPONENT_READY_PRIVATE;
- Private Receiver Contract;
- Dummy Private Host;
- Receiver Adapter;
- Public Handoff Acceptance Contract;
- No-Private-Implementation Gate lintas repo;
- wiring manifest antar repo;
- rehearsal khusus untuk mencocokkan receiver private.

Jika istilah lama itu muncul di dokumen lain sebagai aturan kerja, bagian tersebut harus dihapus atau diganti dengan aturan ini.

## 4. Build

Build pengembangan boleh berjalan di repo publik selama tidak memakai secret sensitif.

Build final tertandatangan berjalan di repo private atau jalur private yang aman. Signing key tidak boleh dimasukkan ke source publik.

## 5. Firebase

Firebase/final runtime test yang memakai credential asli hanya berjalan di jalur private.

Repo publik boleh berisi mock, test lokal, dokumentasi, atau kode aplikasi yang tidak membuka credential.

## 6. Target

Target utama tetap Android 11 / API 30 / arm64-v8a.

## 7. Prinsip

Sederhana: pengembangan di repo publik, rahasia build/sign/final test di private.

Tidak ada lagi mekanisme promosi tahap lintas repo.
