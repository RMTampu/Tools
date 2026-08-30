# AGENTS.md — ToolBox

## 1. Wajib Dibaca Sebelum Bekerja

Setiap agen yang akan membaca, membuat, mengubah, menghapus, membangun, menguji, atau mengaudit isi repository ini WAJIB membaca `AGENTS.md` terlebih dahulu.

Aturan di file ini berlaku untuk seluruh repository `RMTampu/Tools`, termasuk semua subfolder dan modul, kecuali ada `AGENTS.md` yang lebih spesifik di subfolder tersebut.

## 2. Identitas Proyek

- Nama proyek: **ToolBox**
- Repository: **RMTampu/Tools**
- Target utama: **Android 11 / API 30**
- Arsitektur target: **arm64**
- Fondasi utama: **extensible core/kernel**
- Modul inti saat ini: `toolbox-kernel`
- Prinsip utama: **kernel stabil, engine/tools berkembang melalui extension point tanpa membangun ulang fondasi inti**

## 3. Target Kematangan Rancangan

Rancangan harus matang terhadap kemampuan dasar yang diminta sebelum implementasi besar dimulai.

Target proyek adalah rancangan **100% matang** terhadap ruang lingkup yang telah ditetapkan. Jika ada keterbatasan nyata, rancangan harus tetap mendekati **99%** dan keterbatasannya harus diketahui sebelum implementasi, bukan ditemukan setelah fondasi selesai dibangun.

Tidak boleh menghilangkan komponen dasar hanya agar rancangan terlihat sederhana. Kesederhanaan hanya berlaku pada cara penggunaan dan penjelasan, bukan dengan mengurangi fondasi yang diperlukan.

## 4. Stabilitas Kernel

`toolbox-kernel` adalah fondasi inti dan harus dijaga tetap kecil, stabil, modular, dan independen dari UI.

Engine, tool, editor, adapter, registry, renderer, storage implementation, update handler, dan fitur lain harus ditambahkan melalui kontrak atau extension point yang jelas.

Penambahan engine/tool baru yang masih termasuk kemampuan dasar ToolBox tidak boleh memaksa pembangunan ulang kernel hanya karena extension point penting belum dirancang.

Jika perubahan membutuhkan modifikasi kontrak inti, agen harus terlebih dahulu memastikan perubahan tersebut benar-benar diperlukan dan tidak dapat diselesaikan melalui extension point yang sudah ada.

## 5. Prinsip Modularitas

Gunakan pemisahan yang jelas antara:

- kernel/core contracts
- runtime/lifecycle
- service registry
- capability registry
- engine registry
- tool registry
- command routing
- event routing
- state management
- health checking
- failure isolation
- persistence adapter
- update/evolution layer
- UI/workbench layer

Kernel tidak boleh bergantung pada implementasi UI tertentu.

## 6. Otomatisasi dan Pekerjaan Berulang

Jangan membebankan pekerjaan manual yang sama kepada pengguna secara berulang.

Hal yang dapat diotomatisasi harus ditangani melalui otomatisasi, konfigurasi tersimpan, cache, registry, state persistence, atau mekanisme sekali konfigurasi.

Konfigurasi satu kali diperbolehkan. Setelah itu sistem harus dapat mengingat atau menggunakannya kembali tanpa meminta pengguna mengulang proses yang sama.

## 7. Aturan Build

APK Android hanya dibangun melalui **GitHub Actions**.

Termux hanya berfungsi sebagai relay/perantara bila diperlukan.

Dilarang menggunakan Termux sebagai lingkungan build aplikasi.

Dilarang menginstal package/tool tambahan di Termux tanpa izin eksplisit pengguna.

Jika dibutuhkan build, test APK, emulator, artifact, atau proses CI, gunakan workflow GitHub Actions.

## 8. Kompatibilitas

Semua keputusan teknis untuk aplikasi Android harus mempertahankan kompatibilitas dengan target **Android 11 / API 30 / arm64**.

Gunakan versi dependency yang stabil dan kompatibel dengan target tersebut. Jangan menaikkan requirement platform tanpa kebutuhan yang jelas.

Perubahan dependency tidak boleh dilakukan hanya karena versi yang lebih baru tersedia.

## 9. Penguncian Pembangunan Android 11 ARM64

Target distribusi utama ToolBox dikunci ke **Android 11 / API 30 / ARM64 (`arm64-v8a`)**.

Penguncian ini berlaku pada hasil build, dependency, native library, engine, runtime compatibility, dan CI. Tujuannya adalah memastikan seluruh hasil pembangunan benar-benar berjalan pada target tersebut tanpa membuat desain kernel menjadi buntu untuk pengembangan di masa depan.

Aturan penguncian:

- ABI utama adalah `arm64-v8a` / AArch64 64-bit.
- Jangan membawa `armeabi-v7a`, `x86`, atau `x86_64` pada release utama kecuali ada kebutuhan yang secara eksplisit disetujui.
- Modul yang memakai NDK/native code harus membatasi ABI ke `arm64-v8a`.
- Dependency native wajib menyediakan binary `arm64-v8a` yang kompatibel dengan Android 11.
- Dependency yang hanya dapat berjalan pada API Android di atas target utama tidak boleh menjadi bagian wajib fungsi inti.
- Native library `.so` harus diperiksa agar ABI-nya benar dan tidak bergantung pada arsitektur lain.
- Engine baru wajib mendeklarasikan metadata kompatibilitas minimal: `engineId`, `engineVersion`, `minAndroidApi`, `maxAndroidApi` bila diperlukan, `supportedAbi`, `requiredCapabilities`, dan `entryPoint`.
- Engine yang tidak mendukung `arm64-v8a` atau tidak kompatibel dengan Android 11 harus ditolak sebelum dimuat.
- Runtime harus memeriksa Android API, ABI perangkat, dan kompatibilitas engine sebelum proses load.
- Tidak boleh ada fallback diam-diam ke ABI atau target platform lain.
- Paket engine native harus menempatkan binary ARM64 pada jalur yang jelas seperti `native/arm64-v8a/`.
- GitHub Actions harus melakukan verifikasi ABI, dependency, isi APK, kompatibilitas Android 11, kompatibilitas ARM64, serta pengujian pemuatan engine bila relevan.
- Build harus dinyatakan gagal jika dependency/native binary tidak menyediakan ARM64, engine mengklaim ARM64 tetapi binary tidak sesuai, requirement API inti melebihi target yang ditetapkan, atau hasil APK kehilangan kompatibilitas dengan target Android 11 ARM64.

Penguncian ini tidak berarti desain kernel hanya boleh mengenal satu CPU. **Kernel tetap platform-aware dan extensible**, sedangkan target distribusi utama saat ini dikunci ke **Android 11 / API 30 / ARM64**. Dengan demikian ToolBox fokus dan stabil pada target sekarang, tetapi fondasi tidak perlu dibangun ulang ketika engine baru ditambahkan atau target lain dipertimbangkan di masa depan.

## 10. Kualitas Implementasi

Sebelum menyatakan pekerjaan selesai, periksa minimal:

1. struktur dan dependency tetap konsisten;
2. kontrak publik tidak rusak tanpa alasan;
3. tidak ada duplikasi fungsi inti;
4. tidak ada pekerjaan manual berulang yang sebenarnya dapat diotomatisasi;
5. error dapat dilokalisasi dan tidak menjatuhkan seluruh kernel jika seharusnya bisa diisolasi;
6. perubahan dapat diuji;
7. target Android 11 / arm64 tetap terpenuhi;
8. fondasi tetap dapat menerima engine/tool baru tanpa perubahan besar yang tidak perlu.

## 11. Testing

Setiap fitur baru harus mempunyai cara verifikasi yang jelas.

Untuk perubahan pada core/kernel, prioritaskan unit test yang independen dari Android runtime jika memungkinkan.

Untuk bagian Android, integrasi, UI, atau emulator, jalankan pengujian melalui GitHub Actions.

Jangan menyatakan PASS hanya berdasarkan keberhasilan kompilasi jika fitur membutuhkan pengujian runtime.

## 12. Perubahan Repository

Sebelum mengubah file yang sudah ada:

1. baca file terkait;
2. pahami dependensi dan kontrak yang digunakan;
3. hindari perubahan di luar kebutuhan tugas;
4. jangan menghapus komponen yang belum terbukti tidak diperlukan;
5. pertahankan kompatibilitas dengan struktur yang sudah dipakai.

Jangan melakukan refactor besar yang tidak berhubungan langsung dengan tugas aktif.

## 13. Dokumentasi Keputusan

Keputusan arsitektur penting harus terdokumentasi agar agen berikutnya tidak mengulang analisis dari awal.

Jika repository kemudian memiliki `ARCHITECTURE.md`, `DECISIONS.md`, `PROJECT.md`, `TASK.md`, atau `ACCEPTANCE-TESTS.md`, agen wajib menggunakannya sebagai sumber konteks proyek bersama `AGENTS.md`.

Jika terdapat konflik, instruksi pengguna terbaru memiliki prioritas tertinggi, kemudian `AGENTS.md`, lalu dokumentasi proyek lainnya.

## 14. Larangan Asumsi

Jangan menganggap fitur sudah selesai hanya karena file, interface, atau class sudah ada.

Jangan menebak kondisi build, test, workflow, artifact, atau runtime. Periksa kondisi aktual sebelum membuat kesimpulan.

Jangan menyatakan implementasi matang jika masih ada komponen dasar yang diketahui belum tersedia.

## 15. Definisi Selesai

Sebuah tugas dapat dianggap selesai jika:

- fungsi utama yang diminta tersedia;
- integrasi dengan fondasi tidak merusak struktur inti;
- tidak meninggalkan pekerjaan manual berulang yang seharusnya otomatis;
- memiliki cara pengujian;
- lulus pengujian yang relevan;
- dokumentasi penting diperbarui jika keputusan arsitektur berubah;
- tidak menutup kemampuan ekspansi ToolBox yang sudah menjadi bagian dari tujuan dasarnya.

## 16. Aturan Asset — ASSET_SAFE_100

Untuk setiap pekerjaan yang membaca, menambah, mengubah, menghapus, memindahkan, menghasilkan, mengemas, memvalidasi, menguji, atau mengaudit asset/resource aplikasi, agen WAJIB membaca dan mematuhi `ASSET_SAFE_100_RULES.md` sebelum melakukan pekerjaan tersebut.

`ASSET_SAFE_100_RULES.md` adalah sumber aturan khusus asset untuk repository ini. Agen tidak boleh menyederhanakan, mengurangi, mengganti, atau melewati gate wajib di dalamnya hanya agar build/test menjadi PASS.

Kesalahan alat seperti emulator, CI runner, compiler, toolchain, atau validator tidak boleh diklasifikasikan sebagai kegagalan asset tanpa bukti bahwa asset itu sendiri adalah penyebabnya. Status asset hanya boleh dinyatakan `ASSET_SAFE_100` apabila seluruh invariant wajib dalam `ASSET_SAFE_100_RULES.md` telah terpenuhi.

## 17. Rantai Wajib Asset → Build → Test

Sebelum melakukan build yang melibatkan asset/resource aplikasi, agen WAJIB membaca dan menjalankan `PREBUILD_ASSET_GATE.md` secara berurutan.

**Build APK / production build / final resource packaging DILARANG DIMULAI sebelum seluruh gate pre-build yang diwajibkan dalam `PREBUILD_ASSET_GATE.md` selesai dan berstatus PASS.**

Gate tidak boleh dilompati, dianggap PASS tanpa bukti, atau dilewati hanya untuk melihat apakah build berhasil. Jika perubahan asset atau input terkait membatalkan proof yang sudah ada, agen wajib kembali ke gate paling awal yang terdampak sebelum build dapat dibuka kembali.

## 18. Aturan Kapasitas Agen dan Eksekusi Bertahap

Untuk setiap pekerjaan yang dikendalikan oleh prosedur, gate, audit, validasi, build, atau test, agen WAJIB membaca dan mematuhi `AGENT_PROCEDURE_EXECUTION_RULES.md`.

Agen tidak diwajibkan menyimpan seluruh aturan repository di memory sekaligus. Namun seluruh aturan yang berlaku untuk langkah aktif WAJIB tersedia, dibaca, dipahami, dan dijalankan lengkap pada saat langkah tersebut dieksekusi.

Jika context, memory, tool output, atau kapasitas pemrosesan tidak memadai untuk menjalankan seluruh prosedur secara lengkap sekaligus, agen WAJIB memecah eksekusi menjadi gate, sub-gate, rule group, individual rule, atau individual check yang lebih kecil.

**Keterbatasan kapasitas hanya boleh mengubah ukuran unit eksekusi. Keterbatasan tersebut DILARANG digunakan untuk mengurangi aturan, coverage, invariant, proof, evidence, atau syarat PASS.**

Jika detail aturan aktif tidak tersedia, state prosedur tidak diketahui, evidence hilang, atau agen tidak dapat memastikan bahwa langkah telah dijalankan lengkap, status default adalah `NOT_PROVEN`, bukan PASS. Agen wajib membaca kembali sumber dan merekonstruksi state sebelum melanjutkan.

## 19. Aturan Hasil Riset Asset Route Proof

Untuk setiap pekerjaan yang menyentuh jalur consumer → asset, asset → asset, reference, dependency, alias, dynamic lookup, resource resolution, configuration → variant, fallback, authority/capability, contextual route, atau transitive asset route, agen WAJIB membaca dan mematuhi:

- `ASSET_ROUTE_PROOF_METHODS.md`;
- `ASSET_ROUTE_PROOF_PROCESS.md`.

`ASSET_ROUTE_PROOF_METHODS.md` adalah korpus aktif metode hasil riset yang sudah diterima, disaring, digabungkan, dan diaudit agar tidak kontra. Metode yang ditolak, lebih lemah, redundant, atau tidak menambah jaminan tidak boleh dipakai untuk menggantikan metode aktif tersebut.

`ASSET_ROUTE_PROOF_PROCESS.md` menentukan urutan eksekusi wajib untuk Gate 4 pada `PREBUILD_ASSET_GATE.md`.

Jika scope build mempunyai asset route/reference/resolution, Gate 4 DILARANG dinyatakan PASS sebelum proses tersebut menghasilkan:

```text
ROUTE_PROOF_PASS
```

Build tetap tertutup sampai `ROUTE_PROOF_PASS` terintegrasi dengan seluruh syarat Gate 5 dan `PREBUILD_ASSET_GATE = PASS`.

## 20. Aturan Hasil Riset ASSET_SAFE_100 Terintegrasi

Untuk setiap pekerjaan asset/resource yang akan masuk ke rantai build/test atau akan dinilai dengan status `ASSET_SAFE_100`, agen WAJIB membaca dan mematuhi:

- `ASSET_SAFE_100_METHODS.md`;
- `ASSET_SAFE_100_PROCESS.md`.

`ASSET_SAFE_100_METHODS.md` hanya memuat metode hasil riset yang sudah diterima setelah penyaringan, penggabungan metode yang tumpang tindih, dominance filtering, dan audit kontra terhadap aturan aktif. Metode yang ditolak, lebih lemah, redundant, atau tidak menambah jaminan tidak boleh dipakai untuk menggantikan metode aktif.

`ASSET_SAFE_100_PROCESS.md` memetakan seluruh metode tersebut ke Gate 0–9 pada `PREBUILD_ASSET_GATE.md`. Dokumen tersebut tidak boleh digunakan untuk membuat urutan paralel atau melewati build boundary yang sudah ditetapkan.

Final status `ASSET_SAFE_100` hanya boleh diberikan jika seluruh syarat dari:

```text
ASSET_SAFE_100_RULES.md
AND ASSET_SAFE_100_METHODS.md
AND ASSET_SAFE_100_PROCESS.md
```

terpenuhi untuk scope yang berlaku.

Jika route/reference/resolution termasuk scope, final status juga memerlukan `ROUTE_PROOF_PASS` sesuai `ASSET_ROUTE_PROOF_PROCESS.md`.

Build tetap dilarang sampai seluruh bagian prebuild pada `ASSET_SAFE_100_PROCESS.md` dan seluruh Gate 0–5 pada `PREBUILD_ASSET_GATE.md` selesai serta PASS.