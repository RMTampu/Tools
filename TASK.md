# TASK.md - Tugas Aktif Global

## Tugas Aktif

Kerjakan Tahap 1 pematangan publik sampai tuntas.

Repo aktif: RMTampu/Tools.

## Arti Perintah Pengguna

Jika pengguna menulis perintah pendek seperti:

- kerjakan tahap 1
- lanjut
- lanjutkan
- cek
- build
- test
- matangkan publik

maka agen wajib langsung bekerja pada tugas aktif ini.

Perintah tersebut tidak boleh dijawab dengan laporan, rencana, atau alasan belum ada source.

## Target Tahap 1

Tahap 1 dianggap selesai hanya jika repo publik memiliki fondasi teknis nyata yang dapat diuji.

Minimal harus ada:

- source atau module publik yang sesuai tahap;
- konfigurasi build/test publik;
- test atau validator yang berjalan;
- hasil validasi PASS atau blocker nyata.

## Jika Source Belum Ada

Jika source aplikasi, module, build.gradle, settings.gradle, test, registry, atau fondasi teknis belum ada, itu bukan blocker.

Agen wajib membuat baseline aman yang sesuai Android 11 API 30 arm64 dan aturan publik, selama tidak membutuhkan secret, signing key, Firebase credential, atau asset private.

Agen dilarang berhenti dengan jawaban seperti:

- belum ada source;
- belum ada baseline;
- belum bisa dikerjakan;
- langkah berikutnya adalah membuat source.

Jika langkah berikutnya adalah membuat source, agen wajib membuat source itu.

## Jalur Kerja Wajib

1. Baca AGENTS.md.
2. Baca TASK.md.
3. Cek struktur repo.
4. Kerjakan gap teknis nyata.
5. Jalankan test/build/validator yang tersedia.
6. Jika gagal, perbaiki dan ulangi.
7. Berhenti hanya jika PASS, artifact siap, atau blocker nyata.

## Batas Private

Repo private hanya untuk secret, signing, Firebase final test, final runtime test, dan release sensitif.

Tahap 1 publik tidak boleh berhenti hanya karena final signing/Firebase belum bisa dilakukan.
