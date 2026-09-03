# FIREBASE TEST LAB POLICY

## Status

Dokumen ini menggantikan aturan lama yang dibuat karena pemisahan pola lama antar repository.

## Aturan

Firebase/Test Lab yang memakai credential asli hanya dijalankan dari repo private atau jalur private.

Repo publik boleh memuat:

- kode aplikasi;
- mock Firebase;
- dokumentasi;
- test tanpa credential asli.

Repo publik tidak boleh memuat:

- credential Firebase;
- token;
- service account;
- file rahasia;
- log yang membuka secret.

Build final yang akan diuji dengan Firebase harus dibangun dan ditandatangani di jalur private.
