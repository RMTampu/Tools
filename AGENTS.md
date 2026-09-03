# AGENTS.md - Aturan Kerja ToolBox

## Aturan Utama

Instruksi pengguna terbaru menetapkan bahwa pola kerja pemisahan pengembangan antar repository sudah dihapus.

ToolBox dikerjakan pada repo utama publik yang diarahkan pengguna. Repo private hanya dipakai untuk build final, signing, verifikasi signature, Firebase/final runtime test, dan release bila diperlukan.

## Sebelum Bekerja

Agen wajib membaca file ini sebelum mengubah repository.

Jika ada file lama yang masih menyebut pola pemisahan pengembangan antar repository, bagian itu dianggap tidak berlaku dan harus dibersihkan saat file terkait disentuh.

## Peran Repository

- Repo publik utama: tempat rancangan, source aplikasi, asset yang boleh publik, implementasi, test biasa, dan pematangan fitur.
- Repo private build/signing: tempat credential, signing key, secret, Firebase credential, build final tertandatangan, final runtime test, dan release sensitif.
- Termux hanya relay/perantara bila diperlukan; bukan tempat build aplikasi.

## Yang Tidak Boleh Masuk Repo Publik

- secret, token, password, private signing key;
- credential Firebase atau credential layanan eksternal;
- data pribadi, database nyata, dump internal sensitif;
- asset yang memang ingin tetap rahasia.

## Build dan Signing

Build pengembangan boleh dibuktikan dengan workflow repo publik jika tidak memakai secret sensitif.

Build final tertandatangan dilakukan di repo private atau jalur private yang tidak mengekspos credential. Signing key dan credential tidak boleh disimpan di source publik.

## Aturan Eksekusi Penuh

Jika pengguna memberi perintah kerja seperti lanjutkan, kerjakan, perbaiki, buat, build, test, cek sampai selesai, atau instruksi sejenis, agen wajib mengeksekusi pekerjaan sampai satu unit kerja penuh tuntas.

Agen tidak boleh berhenti hanya dengan menjawab, menjelaskan rencana, membuat file status, membuat checkpoint, melakukan commit dokumentasi, atau mengirim workflow. Laporan akhir baru boleh diberikan setelah ada hasil nyata pada source/asset/test/build.

Agen wajib:

1. membaca MD dan file terkait sebelum mengubah repository;
2. menentukan pekerjaan aktif dan hasil akhir teknis yang harus dicapai;
3. melakukan perubahan implementasi yang diperlukan pada source, asset, test, workflow, atau konfigurasi nyata;
4. menjalankan test/build/check yang relevan;
5. jika memakai GitHub Actions, memantau run aktif sampai selesai;
6. membaca log run aktif, bukan hanya melihat status commit;
7. bila gagal karena bug, error, konfigurasi, test, dependency, atau workflow yang masih berada dalam wewenang kerja, agen wajib memperbaiki otomatis, push ulang, jalankan ulang, dan pantau lagi;
8. mengulang siklus perbaikan sampai PASS atau sampai menemukan blocker nyata;
9. baru memberikan laporan akhir setelah pekerjaan tuntas, PASS, artifact siap, atau blocker jelas.

Agen dilarang membuat pengguna harus mengetik cek, lanjutkan, ulang, atau perintah serupa hanya agar agen meneruskan pekerjaan yang sama.

Selama pekerjaan belum tuntas, agen wajib tetap memantau proses aktif dan melanjutkan tindakan yang diperlukan.

## Gap Bukan Blocker

Ketiadaan source, asset, test, workflow, konfigurasi build, struktur module, atau baseline implementasi bukan blocker jika pekerjaan yang diminta memang mencakup pembuatan atau pematangan bagian tersebut.

Jika agen menemukan "belum ada source", "belum ada build.gradle", "belum ada settings.gradle", "belum ada registry", "belum ada test", atau gap sejenis, agen wajib membuat atau memperbaiki bagian yang hilang selama masih berada dalam scope pekerjaan dan tidak membutuhkan secret/izin khusus.

Agen dilarang berhenti dengan alasan repository belum memiliki fondasi aplikasi jika pengguna meminta membangun atau mematangkan fondasi tersebut.

Jawaban "belum bisa dikerjakan karena belum ada baseline" hanya sah jika baseline wajib berasal dari pengguna, mengandung rahasia, atau tidak boleh dibuat aman oleh agen. Jika baseline dapat dibuat dari rancangan dan aturan publik, agen wajib membuatnya.

## Larangan Laporan Palsu

Checkpoint, file status, audit singkat, ringkasan MD, atau klaim PASS dokumen tidak boleh dianggap sebagai penyelesaian pekerjaan implementasi.

Jika pengguna meminta "lanjutkan pekerjaan", "kerjakan tahap", "pematangan publik", "build", "test", atau perintah kerja sejenis, hasil yang sah minimal harus berisi salah satu dari ini:

- perubahan source aplikasi;
- perubahan asset aplikasi;
- perubahan test yang memverifikasi fitur;
- perubahan workflow/build konfigurasi yang dibutuhkan;
- hasil run test/build yang sudah dipantau sampai selesai;
- artifact hasil build/test;
- blocker nyata yang mencegah semua aksi teknis di atas.

Agen tidak boleh membuat file seperti STATUS, CHECKPOINT, PROOF, atau laporan sejenis sebagai pengganti pekerjaan teknis.

Membuat atau memperbarui Markdown hanya boleh menjadi pekerjaan utama jika pengguna secara eksplisit meminta perubahan dokumen/aturan/MD. Jika pengguna meminta implementasi atau pematangan aplikasi, Markdown hanya boleh menjadi pendukung, bukan hasil utama.

## Batas Berhenti yang Sah

Agen hanya boleh berhenti sebelum pekerjaan tuntas jika ada blocker nyata, yaitu:

- membutuhkan secret, token, signing key, credential Firebase, atau akses yang belum tersedia;
- membutuhkan persetujuan eksplisit untuk tindakan berisiko, penghapusan, pembayaran, release, signing final, atau Firebase final test;
- akses GitHub/repository/workflow ditolak oleh izin;
- konflik instruksi yang tidak bisa diputuskan aman;
- pekerjaan meminta hal di luar batas teknis atau keamanan yang diizinkan.

Jika berhenti karena blocker, agen wajib menjelaskan blocker secara singkat, menyebutkan langkah terakhir yang sudah dilakukan, dan menyebutkan tepat apa yang dibutuhkan agar pekerjaan bisa lanjut.

## Batas Tahap Publik

Untuk pekerjaan di repo publik, pekerjaan dianggap tuntas hanya jika asset/source/test/dokumentasi pendukung yang diminta sudah matang, dicek, dan siap untuk tahap berikutnya.

Jika tujuan akhirnya adalah build final, signing, atau Firebase final test, pekerjaan publik berhenti pada kondisi siap masuk jalur private. Setelah itu agen wajib meminta izin atau credential yang diperlukan untuk jalur private.

## Aturan yang Dihapus

Semua pola kerja lama yang memisahkan pengembangan aplikasi ke beberapa repository sudah dihapus. Jangan membuat gate, paket, kontrak, dummy, atau mekanisme penyambungan antar repository untuk pekerjaan ToolBox.

## Prinsip Kerja

Kerja dibuat sederhana: satu repo publik utama untuk mematangkan aplikasi, satu jalur private untuk rahasia build/signing/final test.

Jika ada konflik antara dokumen lama dan instruksi ini, aturan ini menang.
