# AGENTS.md - Aturan Kerja ToolBox

## Aturan Utama

Instruksi pengguna terbaru menetapkan bahwa pola kerja pola lama antar repository dihapus.

ToolBox dikerjakan pada repo utama publik yang diarahkan pengguna. Repo private hanya dipakai untuk build final, signing, verifikasi signature, Firebase/final runtime test, dan release bila diperlukan.

## Sebelum Bekerja

Agen wajib membaca file ini sebelum mengubah repository.

Jika ada file lama yang masih menyebut pola pola lama antar repository, pola lama antar repository, pola lama antar repository, pola lama antar repository, dummy receiver, atau pola lama antar repository, bagian itu dianggap tidak berlaku dan harus dibersihkan saat file terkait disentuh.

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

## Aturan yang Dihapus

Semua pola kerja lama yang memisahkan pengembangan aplikasi ke beberapa repository sudah dihapus. Jangan membuat gate, paket, kontrak, dummy, atau mekanisme penyambungan antar repository untuk pekerjaan ToolBox.

## Prinsip Kerja

Kerja dibuat sederhana: satu repo publik utama untuk mematangkan aplikasi, satu jalur private untuk rahasia build/signing/final test.

Jika ada konflik antara dokumen lama dan instruksi ini, aturan ini menang.
