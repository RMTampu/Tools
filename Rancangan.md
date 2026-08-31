# Rancangan ToolBox
## Visual Declarative App Factory
### Dokumen Rancangan / Referensi Pemantangan

> **Status dokumen:** Draf rancangan.
>
> Dokumen ini **bukan aturan kerja**, bukan instruksi wajib agen, dan bukan spesifikasi implementasi final.
> Fungsinya adalah menyimpan seluruh arah, keputusan, pertimbangan, dan struktur konsep ToolBox agar dapat dibaca ulang, diaudit, dibandingkan, dan dimatangkan sebelum dijadikan aturan atau spesifikasi pembangunan.

---

# 1. Tujuan Utama

ToolBox dirancang sebagai **Visual Declarative App Factory** untuk membangun aplikasi dalam bentuk **asset/project definition yang siap dibuild di GitHub**, bukan sebagai alat untuk melakukan proses build utama langsung di HP.

Pengguna bekerja dengan cara visual dan deklaratif:

- menentukan apa yang harus ada;
- menentukan bagaimana tampilan aplikasi;
- menentukan apa yang dilakukan tiap objek;
- menentukan hubungan antarbagian;
- menyambungkan jalur fungsi;
- menyimpan semuanya sebagai project/asset;
- melakukan validasi;
- mengirim hasil yang sudah siap ke GitHub untuk proses build APK.

Target pengalaman pengguna:

> Pengguna mengatur **apa yang dibuat dan jalurnya ke mana**, bukan menulis kode dasar.

---

# 2. Prinsip Besar Arsitektur

ToolBox adalah **satu aplikasi host** yang menjadi rumah bagi banyak tool.

ToolBox **bukan** satu engine besar yang seluruh komponennya selalu hidup bersamaan.

Konsep dasarnya:

```text
ToolBox Host
│
├─ UI Tool
├─ Logic Tool
├─ Data Tool
├─ Composer / Integration Tool
├─ Build Manager
└─ Tool lain yang ditambahkan kemudian
```

Setiap tool:

- bekerja mandiri;
- tidak memiliki dependency runtime langsung terhadap tool lain;
- hanya aktif ketika sedang digunakan;
- menyimpan hasil kerjanya ke Project Store;
- melepaskan resource ketika ditutup;
- hanya mengetahui tool lain melalui metadata/kontrak ringan bila memang perlu.

Tujuan utamanya adalah menjaga ToolBox tetap terasa sebagai satu aplikasi utuh, tetapi secara internal tidak membebani HP dengan semua engine aktif bersamaan.

---

# 3. Model Kerja Tool Mandiri

Setiap tool diperlakukan seolah-olah merupakan aplikasi tersendiri yang hidup di dalam rumah bernama ToolBox.

Contoh:

```text
Buka UI Tool
→ UI Tool aktif
→ tool lain tidak ikut bekerja

Pindah ke Logic Tool
→ UI Tool simpan pekerjaan
→ UI Tool release
→ Logic Tool dimuat
```

Tidak ada kebutuhan untuk mempertahankan seluruh tool dalam kondisi siap aktif di RAM.

Prinsip:

> Satu ToolBox, banyak tool, tetapi hanya tool yang diperlukan yang benar-benar hidup.

---

# 4. Tool Lifecycle

Setiap tool memiliki lifecycle yang jelas:

```text
COLD
↓
LOAD
↓
ACTIVE
↓
SAVE
↓
RELEASE
↓
COLD / INACTIVE
```

## 4.1 LOAD

Tool memuat hanya data dan resource yang diperlukan untuk pekerjaan saat ini.

## 4.2 ACTIVE

Tool aktif digunakan oleh pengguna.

## 4.3 SAVE

Perubahan penting disimpan ke Project Store dengan mekanisme transaksi yang aman.

## 4.4 RELEASE

Saat tool ditutup atau pengguna berpindah ke tool lain, resource yang hanya diperlukan ketika tool aktif harus dilepas.

Yang wajib dilepas:

- UI/View aktif;
- screen yang sedang dirender;
- editor overlay;
- selection handle;
- panel sementara;
- bitmap di RAM;
- thumbnail RAM;
- preview resolusi tinggi;
- render buffer;
- object kerja sementara;
- node aktif;
- temporary object graph;
- parser result sementara;
- listener;
- observer;
- callback;
- subscription;
- coroutine;
- worker;
- thread;
- timer;
- cache RAM;
- stream;
- file handle;
- parser buffer;
- temporary import/export buffer;
- reference ke Activity/View/renderer lama;
- referensi tool yang tidak lagi diperlukan.

Yang tidak boleh hilang saat RELEASE:

- Project Data;
- Stable Object ID;
- Binding;
- Navigation;
- Asset asli;
- state penting;
- transaction/recovery state.

State ringan yang boleh disimpan untuk melanjutkan sesi:

- screen terakhir;
- posisi scroll;
- zoom;
- object terakhir yang dipilih;
- editor state kecil.

Prinsip:

> Yang diperlukan untuk melanjutkan project disimpan.  
> Yang hanya diperlukan agar tool sedang berjalan dilepaskan.

---

# 5. Project Store

Project Store adalah **sumber kebenaran utama project**.

Project tidak boleh bergantung pada keadaan RAM.

RAM hanya digunakan sebagai ruang kerja sementara.

Model:

```text
Storage
→ Project utama

RAM
→ working set yang sedang aktif

Cache
→ data sementara yang boleh dibuang
```

Dengan demikian:

- tool dapat ditutup tanpa kehilangan project;
- Android dapat membunuh process tanpa membuat project hilang;
- jumlah tool tidak harus menambah RAM secara linear;
- jumlah screen tidak harus menambah RAM secara linear.

---

# 6. Lokasi Penyimpanan

Data yang merupakan milik pengguna harus berada di folder yang terlihat di penyimpanan internal bersama.

Struktur utama yang disarankan:

```text
Documents/
└─ ToolBox/
   ├─ Projects/
   ├─ Assets/
   ├─ Templates/
   ├─ Exports/
   ├─ Snapshots/
   └─ Cache/
```

Project dan asset pengguna tidak disembunyikan di `Android/data`.

Pada Android 11, akses folder menggunakan **Storage Access Framework (SAF)**.

Alur yang diinginkan:

```text
Pilih / buat folder ToolBox sekali
↓
simpan persistent URI permission
↓
tidak perlu meminta izin berulang
```

Data yang memang lebih aman atau wajib berada di ruang privat aplikasi dapat tetap berada di app-private storage, misalnya:

- database/index internal;
- transaction staging;
- lock file;
- runtime metadata;
- crash recovery state;
- data sistem yang tidak perlu disentuh pengguna.

---

# 7. Format Project Store: Hybrid Per-Screen Store

Project Store menggunakan model **Hybrid Per-Screen Store**.

Bukan:

- satu file besar untuk seluruh project;
- satu file terpisah untuk setiap object kecil.

Struktur dasarnya:

```text
Documents/
└─ ToolBox/
   └─ Projects/
      └─ <ProjectName>/
         ├─ project.json
         ├─ screens/
         │  ├─ <screen-id>/
         │  │  └─ screen.json
         │  └─ ...
         ├─ logic/
         ├─ data/
         ├─ bindings/
         ├─ assets/
         └─ metadata/
```

## 7.1 Per-Screen Package

Setiap screen menjadi satu paket kerja.

Keuntungan:

- screen aktif dapat dimuat sendiri;
- screen lain tidak perlu masuk RAM;
- perpindahan screen cocok dengan konsep save → release → load;
- satu screen bermasalah tidak langsung berarti seluruh project rusak.

## 7.2 Lightweight Generated Index

ToolBox membuat index ringan untuk mempercepat pencarian.

Contoh isi index:

```text
screen_home
→ object yang ada di home

obj_search
→ berada di screen_home

binding_001
→ obj_search → Browser.Search
```

Index **bukan sumber kebenaran utama**.

Jika index rusak atau dihapus:

```text
hapus index
↓
ToolBox scan Project Store
↓
buat index baru
```

Project tetap aman.

## 7.3 Asset Terpisah

Asset besar tetap disimpan sebagai file sendiri.

Contoh:

- image;
- audio;
- video;
- font;
- icon;
- file data besar;
- template kompleks.

Asset tidak ditanam langsung ke file definisi screen jika dapat menyebabkan file membesar atau sulit dikelola.

---

# 8. Transactional Project Storage

Penyimpanan project harus transactional.

Masalah yang ingin dicegah:

```text
1. UI sudah berubah
2. Binding belum berubah
3. Navigation belum berubah
4. aplikasi crash

→ project menjadi setengah tersimpan
```

Alur yang diinginkan:

```text
Perubahan
↓
Staging / Journal
↓
Tulis semua perubahan terkait
↓
Validasi
↓
COMMIT
```

Jika gagal:

```text
ROLLBACK
↓
kembali ke kondisi valid sebelumnya
```

Screen atau tool lama tidak boleh direlease sebelum perubahan penting sudah aman.

Project utama tetap berada di:

```text
Documents/ToolBox/Projects/
```

Sedangkan app-private storage boleh dipakai untuk:

- transaction journal;
- staging;
- lock;
- recovery metadata;
- crash-safe state.

Target:

- tahan crash;
- tahan process death;
- tahan restart;
- tahan write terputus;
- tahan kondisi storage penuh sejauh mungkin;
- tidak menghasilkan project setengah valid.

---

# 9. Schema Version dan Migration

Setiap project memiliki `schemaVersion`.

Contoh:

```text
schemaVersion = 3
```

ToolBox harus dapat berkembang tanpa memaksa project lama dibuat ulang.

Alur:

```text
Buka project
↓
baca schemaVersion
↓
versi sama?
├─ Ya → buka
└─ Tidak
   ↓
   migration
   ↓
   validasi
   ↓
   commit
```

Migration dilakukan secara bertahap:

```text
v1 → v2 → v3 → v4
```

Migration wajib transactional:

```text
snapshot / staging
↓
migrate
↓
validate
↓
commit

gagal
↓
rollback
```

Prinsip:

> Versioned Schema + Automatic Transactional Migration + Rollback.

---

# 10. Object Identity

Setiap object memiliki ID internal unik dan stabil.

Contoh:

```text
Nama visual: Tombol Cari
ID internal: obj_001
```

Nama visual boleh berubah:

```text
Search
→ Cari
→ Temukan
```

Tetapi ID tetap:

```text
obj_001
```

Aturan lifecycle ID:

- rename → ID tetap;
- pindah screen → ID tetap;
- ubah warna/ukuran/properti → ID tetap;
- duplicate → ID baru;
- copy/paste → ID baru;
- instance dari template → ID baru;
- delete → ID tidak boleh dipakai ulang;
- import project lain → deteksi konflik dan remap otomatis;
- binding selalu menunjuk ID internal, bukan nama visual.

Prinsip:

> ID stabil sepanjang umur object.  
> Object baru selalu mendapat ID baru.  
> ID yang sudah dihapus tidak didaur ulang.

---

# 11. UI Pengguna untuk Object dan Action

ID teknis tidak perlu menjadi tampilan utama bagi pengguna.

Di sisi pengguna, object/action/binding ditampilkan sebagai:

```text
ikon + label bahasa Indonesia
```

Contoh:

```text
🔍 Cari
⚙️ Pengaturan
🏠 Beranda
💬 Pesan
```

Istilah teknis seperti:

```text
onClick
navigateTo
submitQuery
```

dapat diterjemahkan menjadi:

```text
👆 Saat Ditekan
➡️ Buka Halaman
🔍 Lakukan Pencarian
```

ID teknis tetap bekerja di belakang layar dan hanya perlu ditampilkan jika mode teknis/debug memang membutuhkannya.

Prinsip:

> Machine-facing identity tetap teknis dan stabil.  
> User-facing representation harus visual, sederhana, dan mudah dikenali.

---

# 12. Capability Registry + Binding Contract

Tool tidak saling bergantung secara runtime.

Namun setiap tool dapat mengetahui capability tool lain melalui metadata/contract ringan.

Registry dapat menyimpan:

- object type;
- event;
- action;
- input;
- output;
- binding point;
- capability;
- kategori;
- label;
- ikon;
- contract descriptor.

Yang dimuat bukan engine tool lain.

Contoh:

```text
UI Tool
→ tahu bahwa action Browser.Search tersedia

Logic Tool
→ tahu bahwa SearchButton.onClick tersedia
```

Tool lain tetap tidak harus aktif.

---

# 13. Pramuat Action Binding: List / Grid

Pramuat binding/capability menggunakan tampilan **List atau Grid ringan**.

Alur:

```text
List / Grid
↓
ikon + label Indonesia
↓
pilih
↓
cocokkan
↓
binding terbentuk
↓
detail/diagram dibuat jika diperlukan
```

Yang dipramuat hanya metadata ringan:

- ID;
- label;
- ikon;
- kategori;
- input/output;
- binding point.

Engine lain tidak ikut dimuat.

Jika item sangat banyak:

- gunakan kategori;
- search;
- filter;
- lazy-load;
- virtualization.

Prinsip:

> Browse ringan dulu, pilih, lalu materialisasi detail hanya sesuai kebutuhan.

---

# 14. Composite Action Binding

Jika satu interaksi pengguna sebenarnya membutuhkan dua atau lebih jalur teknis yang merupakan satu perilaku, ToolBox dapat menggabungkannya menjadi satu pilihan visual.

Contoh:

```text
💾➡️ Simpan & Lanjut
```

Secara internal:

```text
1. Simpan data
2. Buka halaman berikutnya
```

Di sisi pengguna tetap satu pilihan.

Namun internal harus tetap menyimpan:

- urutan sub-action;
- parameter;
- validasi;
- binding individual;
- kondisi eksekusi.

Jika beberapa jalur konflik atau tidak kompatibel, sistem tidak boleh menggabungkannya secara diam-diam.

---

# 15. Live Interactive UI Workspace

UI Editor tidak menggunakan konsep canvas statis seperti Figma secara penuh.

UI Editor bekerja sebagai **Live Interactive UI Workspace**.

Pengguna membangun aplikasi dengan masuk dan berjalan di dalam UI yang sedang dibuat.

Contoh:

```text
Beranda
→ tekan Pengaturan
→ masuk ke layar Pengaturan
→ edit layar Pengaturan

Pengaturan
→ tekan Privasi
→ masuk ke layar Privasi
→ edit layar Privasi
```

Interaksi dapat langsung diuji.

Contoh:

```text
☰
→ tekan
→ menu benar-benar terbuka
→ pengguna mengedit menu dalam kondisi terbuka
```

Pengguna dapat mengedit:

- isi;
- ukuran;
- posisi;
- animasi;
- tampilan;
- state;
- tujuan navigasi;
- interaction point.

Konsepnya:

> Anda tidak menggambar aplikasi dari luar.  
> Anda masuk ke aplikasi, menjalankannya, lalu mengedit bagian yang sedang dilihat.

---

# 16. Portrait dan Landscape

Workspace mendukung:

- layar penuh portrait;
- layar penuh landscape.

Setiap screen harus dapat memiliki definisi layout yang relevan terhadap orientasi.

Detail implementasi adaptasi layout masih dapat dimatangkan kemudian.

---

# 17. Per-Screen Working Sector

UI Editor memakai model:

> Satu screen aktif = satu working sector hidup.

Jika project memiliki:

```text
10 screen
100 screen
500 screen
```

itu tidak berarti semuanya hidup di RAM.

Normalnya:

```text
1 screen hidup
+
state ringan
```

Saat berpindah:

```text
Screen A aktif
↓
simpan perubahan A
↓
simpan state kecil A
↓
release View/renderer/bitmap/editor object A
↓
load Screen B
↓
render B
```

Screen yang tidak aktif hanya berupa definisi project di storage.

Saat kembali ke screen lama:

```text
baca screen definition
↓
render ulang
↓
restore state ringan
```

State ringan dapat meliputi:

- screenId;
- scroll;
- zoom;
- selected object;
- posisi viewport;
- editor state kecil.

Untuk animasi transisi, dua screen boleh hidup sementara, tetapi screen lama harus direlease setelah transisi selesai.

Target:

> Jumlah screen project tidak boleh membuat RAM tumbuh secara linear.

---

# 18. Navigasi Non-Linear

Pengembangan project tidak dipaksa mengikuti urutan satu arah.

Pengguna bebas berpindah:

```text
UI ↔ Logic ↔ Data ↔ Integration
```

Pengguna dapat:

- membuat UI;
- pindah ke logic;
- kembali ke UI;
- menambah screen;
- mengubah tombol;
- kembali ke logic;
- melanjutkan binding.

Konsep ini disebut:

**Non-Linear Round-Trip Editing**

Namun tool tidak perlu aktif bersamaan.

Yang berpindah adalah data project, bukan runtime tool.

---

# 19. Diagram sebagai Visualisasi, Bukan Sumber Utama

Diagram tidak menjadi sumber data utama.

Sumber data utama cukup berupa definisi binding.

Contoh:

```text
source = SearchButton.onClick
target = Browser.Search
input  = SearchField.text
```

Diagram dibuat otomatis dari data itu.

Jika diagram ditutup:

- diagram boleh dibuang dari RAM.

Jika dibuka lagi:

- diagram dapat dibuat ulang.

Prinsip:

> Binding adalah data utama.  
> Diagram adalah visualisasi sementara.

---

# 20. Auto Diagram Materialization

Flow:

```text
List ringan
↓
pilih object/action
↓
baca contract
↓
buat diagram lokal otomatis
↓
pengguna menyambungkan
```

Diagram tidak boleh memuat seluruh project secara otomatis.

Yang benar:

```text
pilih 1 object
↓
load dependency relevan
↓
buat diagram lokal
↓
expand bila pengguna meminta
```

Bukan:

```text
pilih 1 object
↓
scan seluruh project
↓
render ribuan node
```

---

# 21. Broken Reference Handling

Jika object/action/screen/data source yang dipakai binding hilang, binding tidak boleh dihapus diam-diam.

Statusnya menjadi:

```text
BROKEN_REFERENCE
```

Di UI pengguna dapat ditampilkan:

```text
⚠️ Jalur Putus
```

Pilihan perbaikan:

- Ganti Object;
- Hubungkan Ulang;
- Hapus Binding.

Project tetap boleh dibuka.

Tool lain tidak perlu hidup untuk mendeteksi masalah.

Validasi dilakukan melalui:

- Project Store;
- Stable ID;
- Binding Contract;
- index ringan.

Build tidak boleh dinyatakan siap jika masih ada broken reference yang bersifat wajib.

Prinsip:

> Jangan menghapus masalah diam-diam.  
> Tandai, simpan, dan sediakan jalur perbaikan.

---

# 22. Asset Besar

Asset besar menggunakan prinsip:

> Storage-first, Preview-first, Lazy-load, Bounded Working Set, Release.

Asset yang termasuk:

- gambar resolusi tinggi;
- video;
- audio;
- font besar;
- data besar;
- template kompleks.

Jangan memuat asset asli penuh secara otomatis.

Contoh gambar:

```text
File PNG 3 MB
↓ decode
Bitmap RAM dapat menjadi puluhan MB
```

Saat browsing:

```text
load thumbnail
```

Saat preview:

```text
load resolusi sesuai kebutuhan layar
```

Saat edit:

```text
load bagian/resolusi yang benar-benar diperlukan
```

Video/audio:

```text
stream/chunk
```

bukan seluruh file ke RAM.

Setelah tidak dipakai:

```text
release
```

Tujuan:

- mencegah lonjakan RAM;
- mencegah GC berat;
- mencegah UI lag;
- mengurangi zRAM pressure;
- mencegah OOM/crash.

---

# 23. Cache

Cache dibagi secara jelas.

```text
PROJECT DATA
→ permanen

SESSION STATE
→ ringan

DISPOSABLE CACHE
→ boleh dihapus
```

Disposable Cache dapat berisi:

- thumbnail;
- preview;
- render sementara;
- parser/index cache yang dapat dibuat ulang;
- temporary file;
- undo/redo sementara.

Project Data tidak boleh berada dalam Disposable Cache.

---

# 24. Undo / Redo

Undo/Redo tidak menggunakan snapshot penuh project tanpa batas.

Gunakan **bounded operation journal**.

Contoh:

```text
Move Button
Rename Text
Resize Card
Add Image
Delete Node
```

History memiliki batas:

- jumlah operasi;
- ukuran penyimpanan.

Jika batas tercapai:

```text
operasi paling lama
→ dibuang
```

Tujuannya agar history tidak membengkak tanpa batas.

---

# 25. Clear Cache Manual

ToolBox menyediakan fungsi Clear Cache manual.

Boleh menghapus:

- thumbnail;
- preview;
- temporary render;
- undo/redo sementara;
- parser/index cache yang dapat dibangun ulang;
- file temp.

Tidak boleh menghapus:

- Project;
- asset asli;
- template;
- export;
- snapshot penting;
- Stable Object ID;
- Binding penting;
- recovery state yang masih diperlukan.

Prinsip:

> Clear Cache membuat ToolBox lebih bersih, bukan merusak project.

---

# 26. Build Contract Validator

Sebelum project dikirim ke GitHub, ToolBox menjalankan validasi lokal.

Minimal memeriksa:

- semua screen valid;
- binding lengkap;
- tidak ada `BROKEN_REFERENCE` wajib;
- asset yang dipakai tersedia;
- permission yang dibutuhkan sudah tercatat;
- logic/action lengkap;
- navigation valid;
- schema project sesuai;
- build configuration lengkap.

Alur:

```text
Project
↓
Build Contract Validator
↓
valid?
├─ Tidak → tampilkan masalah
└─ Ya → READY TO BUILD
```

GitHub bukan tempat menemukan kesalahan dasar project.

Prinsip:

> Validasi lokal dulu → READY TO BUILD → baru GitHub.

Validator harus dapat berjalan per bagian/chunk tanpa memuat seluruh project ke RAM.

---

# 27. External-File Integrity

Karena project berada di folder yang terlihat pengguna, file dapat berubah dari luar ToolBox.

Contoh:

- asset dihapus;
- file dipindah;
- nama file berubah;
- file diedit manual;
- folder sebagian hilang.

Setiap project memiliki:

```text
project.manifest
```

Minimal berisi:

- project ID;
- schemaVersion;
- daftar file penting;
- referensi asset;
- checksum/hash bagian penting;
- status project terakhir.

Saat project dibuka:

```text
baca manifest
↓
cek metadata/perubahan
↓
validasi bagian yang berubah
```

Validasi harus **incremental**, bukan selalu scan/hash seluruh project.

Jika perubahan eksternal ditemukan:

```text
⚠️ Perubahan Eksternal Terdeteksi
```

Sistem dapat:

- rebuild index jika aman;
- menandai file hilang;
- menandai broken reference;
- menjalankan recovery;
- rollback jika diperlukan.

Prinsip:

> Visible Project Store + Manifest + Incremental Integrity Validation + Recovery.

---

# 28. RAM Strategy untuk Target 6 GB

Target desain ToolBox adalah menjaga RAM serendah mungkin.

Prinsip utama:

```text
1 APK
+
1 Host ringan
+
1 Tool berat aktif
+
1 Screen aktif
+
Working Set terbatas
+
Project disk-first
```

Jangan:

- memuat semua engine;
- memuat semua screen;
- memuat semua diagram;
- memuat semua asset;
- mempertahankan cache tanpa batas;
- menggunakan semua project graph sebagai object hidup.

Lakukan:

- lazy-load;
- load sesuai kebutuhan;
- release agresif;
- viewport-based render;
- bounded cache;
- bounded undo;
- disk-backed state;
- lightweight index;
- lightweight registry.

---

# 29. UI Editor Rendering

UI Editor harus menghindari canvas yang mempertahankan semua komponen project sebagai object UI hidup.

Screen aktif saja yang dirender.

Untuk design area besar:

- render yang terlihat;
- asset di luar viewport dapat tetap sebagai data ringan;
- hindari bitmap besar untuk seluruh canvas;
- hindari preview resolusi penuh jika tidak perlu.

---

# 30. App Process dan Isolasi

Default:

- gunakan satu process ringan dengan lifecycle tool yang disiplin.

Untuk pekerjaan sangat berat:

- boleh menggunakan disposable process terpisah;
- tetap dalam satu APK;
- hanya jika benar-benar memberikan keuntungan;
- process selesai → dilepas.

Jangan memisahkan semua tool menjadi process berbeda tanpa kebutuhan karena setiap process juga memiliki overhead.

---

# 31. Data Runtime vs User-Owned Data

User-owned data:

```text
Documents/ToolBox/
```

Runtime-private data:

```text
app-private storage
```

User-owned:

- Projects;
- Assets;
- Templates;
- Exports;
- Snapshots;
- cache yang memang ingin terlihat.

Runtime-private:

- transaction journal;
- recovery metadata;
- lock;
- temporary staging;
- internal database/index;
- runtime state.

---

# 32. Build di GitHub

HP tidak menjadi tempat build APK utama.

Alur:

```text
ToolBox
↓
Project Definition / Asset
↓
Local Validation
↓
READY TO BUILD
↓
GitHub
↓
Generator / Android Build
↓
APK
```

ToolBox berfungsi sebagai alat desain, pengembangan visual, integrasi, validasi, dan pengelolaan asset.

GitHub berfungsi sebagai lingkungan build.

---

# 33. Filosofi Penggunaan

Pengguna tidak seharusnya dipaksa memahami detail implementasi Android untuk pekerjaan normal.

Pengalaman yang diinginkan:

```text
Buat UI
↓
pilih interaction
↓
pilih action
↓
cocokkan jalur
↓
ToolBox menyimpan binding
↓
lanjut
```

Contoh browser:

```text
SearchButton
↓
pilih: 🔍 Cari

SearchField
↓
dipakai sebagai input

SettingsButton
↓
pilih: ⚙️ Buka Pengaturan
```

Pengguna hanya menentukan:

> apa yang harus ada dan ke mana jalurnya.

---

# 34. Risiko Utama yang Sudah Dikenali

## 34.1 RAM

Risiko:

- memory leak;
- bitmap besar;
- cache tidak dibatasi;
- tool gagal release;
- object graph tertahan;
- preview berat.

Mitigasi:

- satu tool aktif;
- satu screen aktif;
- storage-first;
- lazy-load;
- bounded cache;
- lifecycle release.

## 34.2 CPU

Risiko:

- scan seluruh project;
- rebuild semua dependency;
- render semua diagram;
- validasi global terlalu sering.

Mitigasi:

- incremental;
- per-screen;
- per-binding;
- per-object;
- generated index.

## 34.3 Storage

Risiko:

- snapshot;
- cache;
- temp;
- asset duplication;
- undo history.

Mitigasi:

- quota;
- dedup bila perlu;
- bounded history;
- Clear Cache;
- cleanup otomatis.

## 34.4 I/O dan Lag

Risiko:

```text
drag 1 px
→ write project
drag 1 px
→ write project
```

Mitigasi:

```text
perubahan kecil
↓
working transaction kecil
↓
debounce/batch
↓
commit
```

## 34.5 Integritas

Risiko:

- object dihapus;
- binding putus;
- file diubah dari luar;
- migration gagal.

Mitigasi:

- Stable ID;
- BROKEN_REFERENCE;
- manifest;
- transactional storage;
- migration rollback;
- build validator.

---

# 35. Identitas Teknis Rancangan Saat Ini

Konsep ToolBox dapat diringkas sebagai:

> **Independent Tools + Disk-First Project Store + Per-Screen Working Sector + Lightweight Contract Integration + Transactional Safety + Local Validation + GitHub Build.**

Untuk UI Editor:

> **Live Interactive UI Workspace + Per-Screen Working Sector + List/Grid Binding + Auto Diagram Materialization.**

Untuk integrasi:

> **Capability Registry + Binding Contract + Stable Object ID.**

Untuk RAM:

> **Load Only What Is Needed, Release Everything Else.**

---

# 36. Bagian yang Masih Perlu Dimatangkan

Dokumen ini belum menetapkan detail final untuk:

- struktur lengkap `project.json`;
- struktur lengkap `screen.json`;
- format binding descriptor;
- format capability contract;
- format action descriptor;
- format navigation definition;
- format data definition;
- format logic definition;
- aturan permission mapping;
- struktur Composer Tool;
- generator contract ke GitHub;
- mode portrait/landscape detail;
- state UI kompleks;
- animation representation;
- template inheritance;
- component registry lengkap;
- plugin/tool extension contract;
- versi project compatibility jangka panjang;
- mekanisme recovery lengkap;
- backup/snapshot policy;
- quota cache final;
- budget RAM final berdasarkan device class;
- format export/import;
- keamanan package/project;
- signing/build handoff;
- strategi test dan benchmark.

Bagian-bagian tersebut sengaja belum dianggap final agar dapat dibahas dan dimatangkan satu per satu.

---

# 37. Status Kematangan Konsep

Arah arsitektur utama sudah jelas.

Bagian yang sudah memiliki keputusan konsep:

- ToolBox sebagai host;
- tool mandiri;
- lifecycle tool;
- Project Store;
- folder user-visible;
- SAF;
- Hybrid Per-Screen Store;
- transactional save;
- schema/migration;
- Stable Object ID;
- Capability Registry;
- Binding Contract;
- List/Grid action binding;
- Composite Action;
- Live Interactive UI Workspace;
- Per-Screen Working Sector;
- Non-Linear Round-Trip Editing;
- diagram sebagai visualisasi;
- asset besar storage-first;
- bounded cache;
- bounded undo/redo;
- Clear Cache;
- broken reference;
- local build validation;
- external file integrity;
- GitHub build.

Bagian yang belum selesai adalah detail spesifikasi implementasi dan kontrak format antarbagian.

---

# 38. Catatan Penggunaan Dokumen

Dokumen ini dibuat untuk:

- dibaca ulang;
- mengingat keputusan desain;
- membandingkan ide baru;
- mencari kelemahan;
- mengaudit risiko;
- mematangkan rancangan;
- menjadi bahan sebelum spesifikasi final dibuat.

Dokumen ini **bukan AGENTS.md**, bukan aturan agen, dan bukan instruksi kerja final.

Perubahan pada dokumen ini boleh terjadi selama fase pematangan rancangan.

---

# 39. Ringkasan Satu Kalimat

> ToolBox adalah satu rumah bagi tool-tool pengembangan aplikasi yang mandiri, ringan, visual, storage-first, saling mengenal melalui kontrak ringan, bekerja hanya saat dibutuhkan, dan menghasilkan project deklaratif siap-divalidasi serta siap-build di GitHub.
