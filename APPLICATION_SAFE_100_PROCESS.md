# APPLICATION SAFE 100 PROCESS

## Tujuan

Dokumen ini menjaga agar rancangan dan implementasi ToolBox matang sebelum fitur dinyatakan selesai.

## Prinsip

- Fungsi utama harus lengkap.
- Struktur harus modular.
- Pekerjaan manual berulang harus diotomatisasi.
- Target tetap Android 11 / API 30 / arm64-v8a.
- Secret, token, signing key, dan credential tidak boleh masuk repo publik.
- Build final dan signing dilakukan di jalur private.

## Proses Ringkas

1. Tetapkan kebutuhan fitur.
2. Tentukan kontrak dan dependency yang diperlukan.
3. Implementasikan di repo publik utama.
4. Jalankan test yang relevan.
5. Perbaiki sampai stabil.
6. Dokumentasikan status dan batasan.
7. Untuk final release, lanjutkan build/signing/final test di jalur private.

## Aturan yang Tidak Dipakai Lagi

Proses ini tidak memakai pola pemisahan pengembangan antar repository.
