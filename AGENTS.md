# ATURAN UTAMA EKSEKUSI

Jika user memberi perintah kerja, agen wajib bekerja sampai PASS.

Kata seperti:
kerjakan, lanjut, lanjutkan, perbaiki, bangun, matangkan, uji, validasi, cek, sampai tuntas

berarti:
AKSI LANGSUNG, BUKAN JAWABAN.

Agen dilarang mengirim laporan, status, alasan, rencana, atau progres sebelum:

1. PASS lengkap;
2. artifact siap;
3. blocker nyata.

Jika belum PASS, lanjut kerja.
Jika workflow berjalan, pantau sampai selesai.
Jika gagal, baca log, perbaiki, jalankan ulang.

Untuk project ToolBox, konteks aktif mencakup dua repo:
- RMTampu/ToolBox
- RMTampu/Tools

Agen wajib membaca AGENTS.md sebelum pekerjaan repo.
Jika pekerjaan menyentuh asset, agen wajib membaca dan mengikuti R1_R9_ASSET_CHAIN.md.

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


## Aturan Global Kata Kerja Pengguna

Aturan ini berlaku untuk semua project, bukan hanya ToolBox.

Jika pengguna memakai kata kerja atau perintah kerja seperti kerjakan, lanjut, lanjutkan, buat, perbaiki, cek, audit, build, test, jalankan, ulangi, selesaikan, matangkan, rapikan, hapus, pindahkan, update, atau kata kerja sejenis, agen wajib mengubahnya menjadi aksi nyata.

Kata kerja pengguna bukan undangan untuk berdiskusi, menjelaskan rencana, bertanya ulang, membuat laporan progres, atau berhenti setelah menjawab. Kata kerja pengguna berarti agen harus langsung bekerja sampai satu unit pekerjaan tuntas.

Agen wajib:

1. membaca aturan repository yang relevan;
2. menentukan target dari konteks terbaru pengguna;
3. melakukan aksi pada file, source, asset, test, workflow, build, atau proses yang memang diminta;
4. memperbaiki error yang muncul selama masih dalam scope;
5. memantau proses aktif sampai selesai jika ada run/build/test;
6. memberi laporan hanya setelah PASS, selesai, artifact siap, atau blocker nyata.

Kata lanjut dan lanjutkan berarti teruskan pekerjaan aktif terakhir dari titik berhenti yang benar. Agen tidak boleh menjawab dengan rangkuman lalu berhenti.

Agen hanya boleh bertanya sebelum bekerja jika target benar-benar tidak bisa ditentukan dari pesan terbaru, repository aktif, TASK.md, AGENTS.md, atau konteks project yang sedang dibuka.

Jika masih ada aksi aman yang bisa dilakukan, agen wajib melakukan aksi itu dulu.



## TASK.md Mengikat Agen

Jika repository memiliki TASK.md, agen wajib membacanya setelah AGENTS.md dan menjadikannya target kerja aktif.

TASK.md menghapus kebutuhan bertanya ulang tentang repo, tahap, atau langkah berikutnya selama isinya cukup untuk menentukan aksi aman.

Jika TASK.md menyatakan source, baseline, module, build, test, atau registry harus dibuat, agen wajib membuatnya. Agen tidak boleh berhenti dengan alasan komponen itu belum ada.

Jika perintah pengguna pendek, agen wajib menggabungkan pesan terbaru pengguna dengan TASK.md lalu langsung bekerja.

## Aturan Global Perintah Pengguna

Aturan ini berlaku untuk semua project dan semua repository.

Jika pesan pengguna mengandung perintah kerja, agen wajib menghasilkan aksi nyata sampai satu unit pekerjaan tuntas. Perintah pengguna tidak boleh diganti menjadi jawaban, laporan, rangkuman, rencana, atau diskusi.

Contoh perintah kerja termasuk, tetapi tidak terbatas pada: kerjakan, lanjut, lanjutkan, buat, perbaiki, cek, audit, build, test, jalankan, ulangi, selesaikan, matangkan, rapikan, hapus, pindahkan, update, baca lalu kerjakan, dan bentuk perintah sejenis.

Jika pengguna meminta sesuatu dikerjakan, maka agen wajib mengerjakannya secara totalitas sampai tuntas.

Jika pengguna memakai kata lanjut atau lanjutkan, maka agen wajib meneruskan pekerjaan aktif terakhir dari titik yang benar sampai selesai, bukan memberi ringkasan lalu berhenti.

Agen wajib:

1. membaca aturan dan file terkait yang diperlukan;
2. menentukan target dari pesan terbaru, project aktif, repository aktif, TASK.md, AGENTS.md, atau konteks kerja yang tersedia;
3. melakukan perubahan atau aksi nyata pada file, source, asset, test, workflow, build, run, atau proses yang relevan;
4. memperbaiki error yang muncul selama masih dalam scope;
5. memantau proses aktif sampai selesai jika ada test/build/workflow/run;
6. mengulang perbaikan dan pengujian sampai PASS, selesai, artifact siap, atau blocker nyata ditemukan;
7. baru memberi laporan akhir setelah pekerjaan selesai atau blocker nyata jelas.

Agen hanya boleh bertanya sebelum bekerja jika target benar-benar tidak bisa ditentukan dari konteks yang tersedia dan tidak ada aksi aman yang bisa dilakukan.

Jika masih ada aksi aman yang bisa dilakukan, agen wajib melakukan aksi itu dulu.

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


## Repo dan Jalur Source yang Benar

Agen wajib mengerjakan repository yang secara eksplisit diarahkan pengguna sebagai tempat kerja aktif. Jika pengguna menyebut repo publik sebagai tempat pematangan, perubahan implementasi tidak boleh dialihkan ke repo private kecuali bagian itu memang signing, secret, Firebase final test, atau release sensitif.

Sebelum membuat module, app, package, registry, kernel, workflow, atau struktur build baru, agen wajib memeriksa apakah jalur canonical sudah ada. Jika module canonical sudah ada, agen wajib mematangkan module itu, bukan membuat module kecil duplikat atau jalur samping yang tidak dipakai build utama.

Pekerjaan dianggap tidak sah apabila:

- perubahan source terjadi di repo yang berbeda dari repo kerja aktif yang diminta pengguna;
- agen membuat module baru yang menduplikasi module canonical yang sudah ada;
- perubahan tidak masuk ke build graph utama atau workflow utama;
- laporan akhir tidak menyebut path file nyata yang berubah, command/test/build yang dijalankan, dan hasil run yang sudah dipantau;
- agen mengklaim tahap selesai berdasarkan commit yang tidak mengubah jalur implementasi utama.

Jika repo aktif belum jelas, agen wajib menentukan dari instruksi terbaru pengguna dan struktur repository. Jika masih ambigu setelah itu, agen boleh bertanya satu kali sebelum mengubah source.

## Validasi Bukti Perubahan

Sebelum melapor bahwa pekerjaan selesai, agen wajib membuktikan perubahan terlihat pada repository target dengan membaca kembali tree atau file yang diubah dari remote/local repository.

Laporan akhir untuk pekerjaan teknis wajib menyebutkan:

- repository target yang benar;
- path source/asset/test/workflow yang berubah;
- commit SHA atau status working tree;
- test/build/run yang dijalankan dan hasilnya;
- sisa blocker jika ada.

Klaim perubahan tanpa bukti path dan hasil validasi dianggap gagal.



## Mode Eksekusi Senyap Sampai PASS

- Jika user memberi perintah kerja langsung seperti kerjakan, lanjut, lanjutkan, perbaiki, bangun, matangkan, uji, validasi, atau sampai tuntas, agen harus masuk mode eksekusi senyap.
- Dalam mode ini agen tidak boleh mengirim chat progres, alasan, rencana, ringkasan sementara, status belum selesai, atau kalimat bahwa pekerjaan akan dilanjutkan.
- Satu-satunya chat yang boleh dikirim adalah laporan akhir setelah PASS lengkap, artifact siap, atau blocker nyata yang menghentikan pekerjaan.
- Jika pekerjaan memicu GitHub Actions atau run lain, agen wajib memantau run aktif sampai selesai, membaca log bila gagal, memperbaiki penyebabnya, menjalankan ulang, dan tetap senyap sampai exit gate tercapai.
- Blocker nyata hanya boleh dilaporkan jika tidak dapat diselesaikan dengan akses agen, misalnya secret private tidak tersedia, izin repo ditolak, layanan eksternal mati, atau keputusan keamanan wajib dari user.

## Rantai R1-R9 Wajib Saat Menyentuh Asset

- Sebelum menyentuh asset, source asset, package, build artifact, registry binding, workflow build/test, atau file yang menentukan perilaku asset, agen wajib membaca dan mengikuti `R1_R9_ASSET_CHAIN.md`.
- `R1_R9_ASSET_CHAIN.md` adalah rantai otoritatif yang menghubungkan `ASSET_SAFE_100_*`, `ASSET_ROUTE_PROOF_*`, `APP_SAFE_R1_*` sampai `APP_SAFE_R9_*`, gate prebuild, assurance plan komponen, validator, dan workflow.
- Perintah user yang mengandung kata kerja seperti kerjakan, lanjut, lanjutkan, perbaiki, bangun, matangkan, uji, validasi, buat, atau sentuh asset harus menghasilkan aksi penuh mengikuti rantai tersebut sampai PASS, artifact siap, atau blocker nyata.
- Agen dilarang menjawab dengan rencana, laporan parsial, checkpoint, atau status tanpa menyelesaikan rantai R1-R9 yang berlaku.
- Jika chain file atau validator yang diperlukan belum ada, agen wajib membuat atau melengkapinya lebih dulu sebelum menyatakan pekerjaan asset selesai.

## R1-R9 Wajib Untuk Asset

Setiap kali agen menyentuh asset, membuat asset, mengubah asset, memindahkan asset, menghapus asset, mengemas asset, atau memakai asset sebagai bagian build/test, agen wajib menerapkan hasil riset R1-R9.

R1-R9 bukan saran dan bukan dokumentasi pasif. R1-R9 adalah gate wajib untuk pematangan asset.

Agen wajib membaca file R1-R9 yang relevan sebelum mengubah asset, termasuk file seperti APP_SAFE_R1 sampai APP_SAFE_R9, ASSET_SAFE_100_RULES, ASSET_SAFE_100_PROCESS, ASSET_SAFE_100_METHODS, ASSET_ROUTE_PROOF_PROCESS, dan ASSET_ROUTE_PROOF_METHODS jika tersedia di repository.

Setiap perubahan asset wajib dibuktikan melalui:

1. R1 logic/input;
2. R2 concurrency/resource;
3. R3 lifecycle/state/recovery;
4. R4 persistence/storage/version;
5. R5 security/network/boundary;
6. R6 build/dependency/install;
7. R7 native/plugin/runtime;
8. R8 UI/device/power;
9. R9 verification/completeness.

Jika asset belum bisa lolos R1-R9, agen wajib memperbaiki asset atau jalur validasinya selama masih dalam scope dan tidak membutuhkan secret/private credential.

Agen tidak boleh menyatakan asset matang, siap build, siap package, siap private signing, atau siap release sebelum R1-R9 yang relevan PASS atau blocker nyata tercatat.

Jika workflow atau script R1-R9 tersedia, agen wajib menjalankannya dan memantau hasilnya sampai selesai. Jika gagal, agen wajib membaca log, memperbaiki, dan mengulang sampai PASS atau blocker nyata.

Jika belum ada workflow/script R1-R9 untuk asset yang disentuh, agen wajib membuat validator minimal yang sesuai scope asset tersebut sebelum menyatakan pekerjaan selesai.

## Standar Kerja Sampai PASS

Aturan ini menjelaskan pola kerja wajib yang harus diikuti setiap agen baru.

Jika pengguna meminta pekerjaan dilakukan, agen wajib bekerja seperti ini:

1. baca AGENTS.md, TASK.md, dan file kerja terkait;
2. tentukan target akhir teknis dari konteks terbaru;
3. lakukan perubahan nyata pada source, asset, test, workflow, build, atau konfigurasi;
4. jalankan test/build/workflow yang relevan;
5. pantau run aktif sampai selesai;
6. jika run gagal, baca log job yang gagal;
7. perbaiki penyebab gagal;
8. push ulang atau jalankan ulang;
9. ulangi sampai PASS, artifact siap, atau blocker nyata;
10. baru beri laporan akhir.

Agen tidak boleh berhenti setelah:

- menemukan gap;
- membuat commit;
- membuat workflow;
- mengirim run;
- melihat run masih berjalan;
- melihat run gagal tanpa membaca log;
- memberi kalimat "saya lanjutkan";
- memberi ringkasan progres.

Laporan akhir yang sah hanya boleh diberikan jika salah satu kondisi ini terpenuhi:

- test/build/workflow sudah PASS;
- APK/package/capsule/artifact sudah dibuat dan siap tahap berikutnya;
- blocker nyata ditemukan dan semua aksi aman sudah dilakukan.

Jika target adalah paket siap private signing, agen wajib berhenti hanya setelah paket publik unsigned berhasil dibuat, artifact tersedia, digest tercatat, dan run validasi terkait PASS.

Jika pengguna berkata "lanjut", agen wajib melanjutkan dari run/commit/tahap aktif terakhir, bukan memulai debat atau memberi rangkuman.

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
