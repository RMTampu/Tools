# Rancangan UI Shell ToolBox
## Addendum Konseptual — Bubble + Multi-Function Edge Panel

> **Status:** rancangan konseptual UI Shell yang disepakati pengguna. Dokumen ini bukan aturan kerja dan bukan prosedur build.

## 1. Bentuk Shell Terkini

Shell UI disederhanakan menjadi dua kontrol utama:

```text
Bubble
= akses cepat / command trigger

Multi-Function Edge Panel
= satu panel kontekstual untuk menu, pilihan, komponen, properti, dan fungsi tool aktif
```

`Deck Panel` tidak lagi diperlukan pada rancangan shell terkini. Fungsi yang sebelumnya berada di Deck dipindahkan ke Multi-Function Edge Panel.

## 2. Bubble

Bubble tetap ringkas dan hanya mempunyai empat akses cepat utama:

```text
Bubble Quick Access
├─ Edit ON / OFF
├─ Tool
├─ Pengaturan
└─ Floating Window
```

Bubble tetap merupakan top-layer draggable overlay, mempunyai bounded movement, tidak menggunakan touch-through, dan menyimpan posisi aman per orientasi bila diperlukan.

Floating Window tetap dipanggil dari Bubble dan digunakan untuk menu/command yang lebih luas bila diperlukan.

## 3. Multi-Function Edge Panel

Edge tidak lagi hanya menjadi sumber komponen/asset. Edge menjadi **Contextual Multi-Function Panel** yang satu kerangkanya dipakai oleh semua tool.

Isi panel berubah sesuai:

- tool aktif;
- mode Edit ON/OFF;
- screen aktif;
- object yang dipilih;
- kategori/menu yang sedang dibuka;
- konteks operasi saat ini.

Panel tidak membuat UI terpisah untuk setiap tool. Yang tetap adalah shell dan pola interaksinya; yang berubah adalah isi dan pilihan.

## 4. Contoh Context Drill-Down

Contoh saat pengguna ingin menambahkan atau mengubah kolom input:

```text
EDGE — Komponen
├─ Tombol
├─ Teks
├─ Kolom Input
├─ Gambar
└─ List

pilih Kolom Input
↓

EDGE — Kolom Input
├─ Text
├─ Number
├─ Email
├─ Password
├─ Search
├─ Phone
├─ Date
└─ variant lain yang tersedia
```

Jika object Input sudah dipilih di layar:

```text
EDGE — Input_01
├─ Teks
├─ Hint
├─ Type
├─ Ukuran
├─ Posisi
├─ Padding
├─ Margin
├─ Radius
├─ Warna
├─ Font
├─ State
├─ Binding
└─ Event
```

Dengan demikian panel yang sama dapat bergerak secara kontekstual:

```text
Home
↓
Kategori
↓
Pilihan
↓
Object
↓
Properti / fungsi lanjutan
```

## 5. Navigasi Internal Panel

Agar pengguna tidak kehilangan konteks ketika isi Edge berubah, panel wajib mempunyai mekanisme:

- Back;
- breadcrumb/context title;
- context history ringan;
- restore posisi/level sebelumnya tanpa memuat seluruh tool kembali.

Contoh header:

```text
‹ Kembali    Input > Properties    ×
```

## 6. Satu Panel untuk Semua Tool

Contoh perubahan isi berdasarkan tool aktif:

```text
UI aktif
→ Component / Object / Property / State / Binding

Logic aktif
→ Event / Action / Condition / Node Property

Data aktif
→ Source / Field / Query / Data Property

Binding aktif
→ Source / Target / Mapping / Converter

Asset aktif
→ Asset / Category / Import / Asset Property
```

Satu shell tetap digunakan. Tidak dibuat Bubble/Edge berbeda untuk setiap tool.

## 7. Interaksi Handle Edge

Handle Edge mempertahankan pola interaksi yang sudah disepakati:

- Tap → buka/tutup panel;
- Drag biasa → buka/tutup dengan seret;
- Long-press → masuk mode pindah posisi;
- pemindahan memakai anchor snap sehingga pengguna tidak perlu menyeret jauh;
- posisi selalu tunduk pada safe bounds;
- touch target boleh lebih besar daripada visual handle;
- posisi dapat disimpan terpisah untuk portrait/landscape;
- rotasi/IME melakukan clamp ke posisi valid;
- emergency/reset shell recovery tetap tersedia.

## 8. Prinsip UX

Tujuan revisi ini adalah:

- mengurangi overlay yang menutup layar;
- mengurangi jumlah kontrol permanen;
- membuat satu pola UI yang dapat dipakai semua tool;
- mempertahankan visual-first workflow;
- menjaga shell ringan pada HP;
- memindahkan kompleksitas ke konten kontekstual, bukan menambah panel baru.

Invariant utama:

> **Satu panel kontekstual boleh mempunyai banyak fungsi, tetapi pengguna harus selalu dapat mengetahui konteks saat ini dan kembali ke level sebelumnya dengan jelas.**

## 9. Mode Awal Edge — Sumber yang Dapat Dimasukkan ke Layar

Saat UI Editor aktif dan belum ada object yang sedang diedit, isi awal Edge berfokus pada hal yang dapat dimasukkan ke layar.

Contoh:

```text
EDGE — Tambah ke Layar
├─ Komponen
├─ Template
├─ Kit
├─ Asset
├─ Recent
└─ Favorite
```

Item yang kompatibel dapat langsung digunakan melalui drag and drop ke layar. Pengguna tidak perlu masuk ke menu property hanya untuk menambahkan object.

Komponen bawaan ToolBox, hasil import, template, dan kit yang sudah lolos validasi dapat muncul dalam katalog yang sama dengan penanda sumber bila diperlukan.

## 10. Tap Object → Edge Menjadi Menu Edit Lanjutan

Saat `Edit ON` dan pengguna mengetuk object yang sudah berada di layar, Edge tidak tetap menampilkan katalog penambahan. Edge langsung berubah menjadi menu edit kontekstual untuk object tersebut.

Urutan menu edit object yang disepakati:

```text
EDGE — Object Terpilih
├─ Pilih Tampilan / Style
├─ Ubah Ukuran
├─ Posisi
├─ Isi / Konten
├─ Warna
├─ Jarak
├─ Bentuk
├─ Border / Garis
├─ Font & Teks
├─ Opacity
├─ Rotasi & Transform
├─ Alignment
├─ Layer
├─ State
├─ Animasi
├─ Auto Connect Binding
├─ Event / Aksi
├─ Accessibility
├─ Kunci
└─ Lainnya
```

Menu disusun vertikal. Fungsi visual dan manipulasi yang paling sering digunakan ditempatkan di bagian atas, sedangkan fungsi teknis dan yang lebih jarang digunakan berada lebih bawah.

Isi menu tidak dipaksakan sama untuk semua object. Edge hanya menampilkan kemampuan yang benar-benar didukung oleh Property/Event/Capability Contract object aktif.

## 11. Pilih Tampilan / Style sebagai Galeri Visual

`Pilih Tampilan / Style` membuka galeri visual dari tampilan yang kompatibel dengan jenis object terpilih.

Contoh untuk kolom status atau kolom input:

```text
Object > Pilih Tampilan

[ Preview Default ] [ Preview Neon ]
[ Preview Minimal ] [ Preview Rounded ]
[ Preview Kit A   ] [ Preview Kit B   ]
```

Pilihan dapat berasal dari:

- style bawaan ToolBox;
- component style project;
- template yang tersimpan;
- kit/import yang sudah dikonversi dan divalidasi.

Tap pada preview menerapkan perubahan ke Working State untuk preview langsung pada object yang sama. Perubahan tetap tunduk pada manual Save dan Undo/Redo.

Istilah dibedakan:

```text
Tema Aplikasi
= berlaku luas pada project

Tampilan / Style Object
= visual untuk object tertentu
```

## 12. Pola Interaksi Utama UI Editor

Pola penggunaan utama menjadi sangat sederhana:

```text
Drag dari Edge
→ menambah sesuatu ke layar

Tap object di layar
→ Edge berubah menjadi menu edit object
```

Edge tetap mempertahankan Back, breadcrumb/context title, dan context history agar pengguna dapat kembali ke katalog atau konteks sebelumnya tanpa memulai ulang navigasi panel.

## 13. Posisi — Dua Mode Layout

Fungsi `Posisi` mempunyai dua mode yang jelas agar pengguna dapat memilih antara layout yang mengikuti aturan layar dan peletakan bebas.

```text
POSISI
├─ Mode Terikat Layout
└─ Mode Posisi Bebas
```

### 13.1 Mode Terikat Layout

Mode ini menjadi mode default untuk UI normal dan responsive.

Object mengikuti aturan parent/container, anchor, alignment, margin, spacing, safe area, orientation/adaptive layout, serta constraint yang berlaku. Pengguna tetap dapat drag object secara visual, tetapi ToolBox sebisa mungkin menerjemahkan hasil drag menjadi hubungan layout yang benar daripada sekadar menyimpan koordinat absolut.

Kontrol yang dapat ditampilkan antara lain:

```text
Anchor Horizontal
[ Kiri ] [ Tengah ] [ Kanan ]

Anchor Vertikal
[ Atas ] [ Tengah ] [ Bawah ]

Jarak dari tepi
Snap / Guide
```

Istilah constraint teknis tidak perlu ditampilkan pada jalur visual biasa.

### 13.2 Mode Posisi Bebas

Mode ini digunakan ketika pengguna ingin meletakkan object di luar aturan layout normal.

Object dapat:

- dipindah bebas dengan drag;
- memakai nilai X/Y;
- menumpuk dengan object lain;
- melewati grid/anchor normal;
- berada di luar batas container normal bila kebutuhan desain memang demikian.

Floating Position Editor menampilkan kontrol yang relevan, misalnya:

```text
Mode
[ Terikat Layout ] [ Bebas ]

X
[──────●──────] 120 dp

Y
[────●────────] 240 dp
```

Pada mode bebas, kontrol anchor/constraint yang tidak relevan disembunyikan.

### 13.3 Batas Layout dan Batas Render Dibedakan

`Mode Posisi Bebas` boleh melanggar aturan layout, tetapi tidak berarti semua posisi otomatis valid untuk hasil aplikasi.

```text
Aturan Layout
≠
Batas Render / Interactive Screen
```

Jika object berada di luar area yang dapat dirender atau disentuh secara efektif, ToolBox memberi indikator/diagnostic seperti `Di luar area layar` dan tidak menyembunyikan risiko tersebut.

Perubahan posisi tetap berlangsung pada Working State, dapat di-Undo/Redo, dan baru menjadi project persistent setelah Save.

## 14. Ubah Ukuran — Floating Resize Editor

Saat pengguna memilih `Ubah Ukuran`, ToolBox membuka Floating Resize Editor dan mengaktifkan resize handle pada object terpilih.

```text
Tap Object
↓
Edge → Ubah Ukuran
↓
Floating Resize Editor
+
Resize Handle pada Object
```

Kontrol utama:

```text
Lebar
[────────●────────] 240 dp

Tinggi
[──────●──────────] 56 dp

Mode Lebar  [ Fixed / Content / Fill ]
Mode Tinggi [ Fixed / Content / Fill ]

Kunci Rasio [ ON/OFF ]
Snap        [ ON/OFF ]
Reset
```

Cara edit yang tersedia:

- drag sisi object untuk mengubah satu dimensi;
- drag sudut object untuk mengubah lebar dan tinggi bersamaan;
- slider untuk perubahan cepat;
- nilai angka untuk presisi;
- lock ratio untuk menjaga proporsi;
- snapping terhadap grid/guide/object bila aktif.

Perubahan selama satu gesture resize diperlakukan sebagai satu transaction Undo.

`Fixed`, `Content`, dan `Fill` adalah mode ukuran yang berbeda. ToolBox tidak boleh diam-diam merusak constraint responsive hanya karena pengguna menyeret object.

Untuk Grid/List/Icon Container, preset seperti `4×4`, `5×5`, `6×6`, dan `Custom` adalah **preset struktur/kepadatan grid**, bukan ukuran fixed ikon tunggal. Ukuran cell dihitung dari area yang tersedia. Ukuran icon dan spacing dapat tetap mempunyai slider sendiri.

## 15. Isi / Konten

`Isi / Konten` membuka floating editor yang isinya mengikuti jenis component.

Contoh Button:

```text
Teks
Ikon
Posisi Ikon
Jarak Ikon
```

Contoh Input:

```text
Hint
Nilai Awal
Prefix
Suffix
Ikon Awal
Ikon Akhir
```

Contoh Image:

```text
Ganti Gambar
Fit: Cover / Contain / Fill
Crop / Posisi Gambar
```

Asset seperti gambar atau ikon dapat diganti melalui pilihan visual dan, bila relevan, drag and drop langsung ke object atau target editor.

## 16. Warna — Floating Color Editor

Menu `Warna` membuka Floating Color Editor.

Pilihan utama:

```text
Tema / Design Token
Palet
Custom
Gradient
Transparan
Warna Terakhir
Favorit
```

Custom color mendukung kontrol visual dan presisi:

```text
Hue
Saturation
Brightness
Opacity
HEX
RGB
```

Slider dipakai untuk perubahan cepat dan nilai dapat diedit secara presisi.

Gradient dapat mendukung Linear/Radial, color stop, dan angle bila component mendukungnya.

Target warna mengikuti kemampuan object. Contoh Button dapat mempunyai Background/Text/Border/Icon/Pressed; Input dapat mempunyai Background/Text/Hint/Border/Cursor/Selection/Error.

State warna hanya menyimpan override ketika benar-benar diubah. Design Token tetap menjadi pilihan utama untuk menjaga konsistensi theme.

## 17. Jarak — Padding, Margin, Spacing

`Jarak` menangani Padding, Margin, dan Spacing dalam satu Floating Editor.

```text
Padding
Atas / Kanan / Bawah / Kiri
[ slider + angka ]

Margin
Atas / Kanan / Bawah / Kiri
[ slider + angka ]

Spacing
[────────●────────] 10 dp
```

Tersedia mode link semua sisi. Jika link aktif, semua sisi berubah bersama. Jika dilepas, tiap sisi dapat diedit terpisah.

`Spacing` terutama digunakan pada container seperti Row, Column, atau Grid untuk mengatur jarak antar-child.

## 18. Bentuk

`Bentuk` menggunakan Floating Editor visual.

Preset dasar:

```text
Persegi
Rounded
Pill / Capsule
Lingkaran
```

Radius menggunakan slider + angka. Sudut dapat diedit sebagai `Semua Sudut` atau `Terpisah` untuk masing-masing corner.

Untuk bentuk seperti lingkaran, ToolBox dapat menjaga rasio 1:1 bila diperlukan.

Path bebas/skew ekstrem tidak dimasukkan sebagai fungsi dasar; kebutuhan seperti itu dapat menjadi capability/vector tool khusus.

## 19. Border / Garis

Kontrol Border:

```text
Ketebalan
[ slider + angka ]

Warna
Gaya: Solid / Dashed / Dotted
Sisi: Semua / Atas / Kanan / Bawah / Kiri
Link Semua Sisi
```

Jika renderer/component mendukung, tersedia posisi border `Inside / Center / Outside`.

Pilihan yang tidak didukung component tidak ditampilkan.

## 20. Font & Teks

Floating Text Editor dapat memuat:

```text
Font + Preview
Ukuran (sp) + slider
Weight
Normal / Italic
Alignment
Line Height
Letter Spacing
Case
Overflow
Max Lines
```

Font dapat berasal dari bawaan, project, atau hasil import yang kompatibel.

Tersedia `Gunakan Style Tema` untuk memakai typography Design Token. Jika pengguna melakukan perubahan manual, ToolBox membuat override object yang jelas.

## 21. Opacity / Transparansi

Opacity memakai slider 0–100% dan preset umum seperti 100%, 75%, 50%, 25%, 0%.

Nilai dapat dimasukkan secara presisi.

Invariant:

```text
Opacity 0%
≠
Object Hidden
```

Opacity hanya memengaruhi transparansi visual. Visibility/Hidden adalah property terpisah.

## 22. Rotasi & Transform

Kontrol dasar:

```text
Rotasi [ slider 0–360° ]
Preset [ 0° / 90° / 180° / 270° ]
Flip Horizontal
Flip Vertical
Scale
Kunci Rasio
```

`Ubah Ukuran` dan `Scale` dibedakan:

```text
Ubah Ukuran
= mengubah dimensi layout

Scale
= transform visual
```

Free skew/distort tidak menjadi fungsi dasar.

## 23. Alignment / Perataan

Jika satu object dipilih, Alignment bekerja terhadap parent/container:

```text
Horizontal: Kiri / Tengah / Kanan
Vertikal: Atas / Tengah / Bawah
```

Jika multi-select:

```text
Align Kiri / Tengah / Kanan
Align Atas / Tengah / Bawah
Distribusi Horizontal / Vertikal
Jarak Sama
Ukuran Sama: Lebar / Tinggi / Keduanya
```

Dapat tersedia pilihan menggunakan object terakhir sebagai acuan.

Multi-select alignment menjadi satu transaction Undo. Pada Mode Terikat Layout, ToolBox menyesuaikan constraint/anchor; pada Mode Bebas, posisi object yang diubah.

## 24. Layer / Urutan Tumpukan

Floating Layer Editor menyediakan:

```text
Paling Depan
Maju 1
Mundur 1
Paling Belakang
```

Tersedia mini hierarchy/list object sekitar untuk memilih object yang tertutup dan drag item untuk reorder bila valid.

Kelompok dasar:

```text
BACKGROUND / Latar
CONTENT / Konten
OVERLAY
MODAL / Dialog
```

Perpindahan group divalidasi terhadap parent/component contract dan tidak boleh dilakukan sembarangan bila tidak kompatibel.

## 25. State / Keadaan Object

State yang ditampilkan hanya yang didukung component, misalnya:

```text
Normal
Pressed
Focused
Selected
Disabled
Error
Loading
```

Saat state dipilih, object pada layar masuk preview state tersebut dan editor mengubah property untuk state itu.

State tidak membuat clone object. State hanya menyimpan property delta dari base/Normal.

Tersedia `Ikuti Normal` dan `Reset State` agar override dapat dihapus dan kembali mengikuti base.

## 26. Animasi

Floating Animation Editor menyediakan preset dasar seperti:

```text
Fade
Slide
Scale
Rotate
```

Kontrol:

```text
Trigger
Duration
Delay
Easing
Preview
```

Jenis tertentu membuka property tambahan, misalnya arah/jarak untuk Slide atau nilai From/To untuk Scale.

Flow animasi kompleks dapat diteruskan ke editor lanjutan/Logic Tool. Animasi disimpan deklaratif, bukan frame-by-frame.

### 26.1 Asset Animasi Tambahan

Animasi dapat diperluas dengan asset yang tervalidasi, termasuk format/capability yang didukung ToolBox seperti:

- Lottie/declarative animation;
- Animated Vector;
- Animated WebP/GIF;
- motion preset dari kit;
- animasi buatan pengguna.

Asset eksternal tidak boleh membawa arbitrary executable code ke host.

### 26.2 Mode Animasi Saat Edit

Tersedia toggle khusus editor:

```text
Mode Animasi Saat Edit
[ ON ] [ OFF ]

[ Preview Sekali ]
```

`ON` berarti animasi berjalan pada screen yang sedang diedit sesuai trigger/loop yang didefinisikan.

`OFF` berarti animasi tidak dijalankan selama Edit ON agar object mudah dipilih dan diedit, tetapi definisi animasi tetap tersimpan dan tetap dipakai pada aplikasi/runtime.

`Preview Sekali` dapat menjalankan animasi yang dipilih walaupun mode edit animation sedang OFF, lalu kembali ke kondisi statis.

## 27. Auto Connect Binding

Binding dirancang sederhana bagi pengguna dan ketat di belakang layar.

Setiap component mempunyai **Default Binding Profile** yang memakai Binding ID/contract dari **Global Binding Registry**. Asset/component tidak membuat binding sendiri berdasarkan nama visual.

Di Edge hanya ada satu aksi utama:

```text
BINDING

[ AUTO CONNECT BINDING ]

Status: Belum Terkoneksi / Terkoneksi / Bermasalah
```

Saat ditekan:

```text
Auto Connect Binding
↓
baca Default Binding Profile component
↓
match dengan Global Binding Registry
↓
cari target/action/data/component kompatibel
↓
auto-connect semua koneksi yang deterministik
↓
validasi hasil
```

Tidak dilakukan penyambungan manual satu-per-satu sebagai alur utama.

Jika koneksi berhasil, isi Edge mengikuti fungsi/binding aktif dan menampilkan fungsi nyata yang tersedia, bukan daftar Binding ID teknis yang panjang.

Jika hanya satu target valid, koneksi dilakukan otomatis. Jika tidak ada target valid, dilaporkan. Jika lebih dari satu target sama-sama valid dan tidak ada aturan deterministik, ToolBox tidak menebak; status menjadi issue/ambigu.

### 27.1 Popup Masalah Binding

Jika sebagian binding gagal, ToolBox tetap mempertahankan bagian yang berhasil dan menampilkan popup alasan yang jelas.

Contoh:

```text
Auto Connect Binding                          ⧉ Copy

✓ 7 berhasil
⚠ 2 bermasalah

action.shareProject
Tidak ada action kompatibel

data.user.avatar
Tidak ditemukan data source bertipe IMAGE

[ Lihat Detail ] [ Tutup ]
```

Popup dapat melaporkan antara lain:

- Binding ID tidak dikenal;
- component target tidak ditemukan;
- action belum tersedia;
- data type mismatch;
- capability belum tersedia;
- permission/capability requirement belum didukung;
- contract version mismatch;
- ambiguous target;
- required binding tidak mempunyai target;
- binding deprecated;
- binding tidak terpakai/orphan.

### 27.2 Tombol Copy Report

Tombol `⧉ Copy` berada di sudut kanan atas popup error dan menyalin **laporan lengkap**, bukan hanya teks yang terlihat.

Format laporan minimal dapat memuat:

```text
AUTO CONNECT BINDING REPORT

Project
Screen
Component
Result Summary
Binding ID
Reason
Stable Error Code
Related Target/Capability
```

Kode error harus stabil, misalnya:

```text
BINDING_TARGET_NOT_FOUND
ACTION_NOT_AVAILABLE
DATA_TYPE_MISMATCH
CONTRACT_VERSION_MISMATCH
AMBIGUOUS_TARGET
```

Tujuannya agar laporan mudah ditempel ke chat, GitHub issue, audit, atau debugging agent.

## 28. Global Binding Registry, Asset Authoring Contract, dan Binding Usage Ledger

Tiga fondasi digunakan bersama:

```text
Global Binding Registry
= daftar Binding ID dan contract resmi

Asset Authoring Contract
= aturan/list pembuatan asset terhadap kemampuan aplikasi

Application Binding Usage Ledger
= rekap binding/action yang benar-benar digunakan aplikasi
```

Asset Authoring Contract minimal dapat memuat:

- supported components;
- supported events;
- supported actions;
- supported properties;
- supported data types;
- supported Binding IDs;
- supported capabilities;
- required compatibility versions;
- deprecated bindings;
- unsupported/forbidden bindings.

Contract ini digunakan saat asset dasar dibuat di GitHub agar asset tidak dibuat berdasarkan tebakan atau kemiripan nama.

Setiap aplikasi mempunyai Binding Usage Ledger sendiri. Contoh ToolBox dapat merekap Binding ID, versi, component/asset pemakai, screen/tool pemakai, status aktif/tidak terpakai, asset yang memperkenalkannya, dan status compatibility/deprecation.

Saat asset baru atau update asset di-import, ToolBox membandingkan manifest binding asset dengan Global Binding Registry dan Binding Usage Ledger aplikasi.

Jika asset memperkenalkan action/binding yang belum tersedia di aplikasi, ToolBox wajib melaporkannya secara eksplisit dan tidak menyambungkan secara diam-diam.

Contoh:

```text
BINDING BARU DITEMUKAN

Asset: project_toolbar_v3
Binding: action.shareProject
Status: BELUM TERSEDIA DI TOOLBOX
```

Pengguna kemudian dapat melihat detail, menambahkan ke rancangan, mengganti binding bila valid, menonaktifkan fungsi terkait, atau membatalkan import sesuai konteks.

## 29. Event / Aksi

Event/Aksi tetap visual dan sederhana.

Floating Editor menampilkan event yang benar-benar dimiliki component, misalnya Button dapat mempunyai Tap/Long Press, Input dapat mempunyai Text Change/Focus/Enter/Valid/Error.

Saat `Pilih Aksi` ditekan, ToolBox membaca Action Registry dan hanya menampilkan action yang kompatibel.

Contoh kelompok:

```text
Navigasi
Data
UI
Logic
Tool
```

Koneksi sederhana dibuat otomatis di belakang layar dari pilihan pengguna. Pengguna tidak perlu mengedit Binding ID teknis.

Jika action membutuhkan input, hanya sumber data/input yang tipe contract-nya kompatibel yang ditampilkan.

Flow kompleks seperti branch, async, retry, success/failure, atau multi-step diarahkan ke Logic Tool.

Masalah action memakai popup diagnostic yang konsisten dan mempunyai `⧉ Copy` untuk menyalin laporan lengkap.

## 30. Accessibility

Sebagian besar metadata accessibility diisi otomatis dari component type, text, icon/asset metadata, dan action/binding yang digunakan.

Kontrol yang dapat ditampilkan:

```text
Label
Deskripsi
Role
Focusable
Urutan Fokus
Status
```

Icon-only interactive control wajib mempunyai accessible label yang sesuai.

Jika ditemukan masalah, ToolBox memberi diagnostic dengan Stable Error Code dan dukungan `⧉ Copy` report.

Accessibility warning ringan tidak harus menghentikan editing, tetapi masalah penting masuk Diagnostics dan Build Contract Validator sesuai severity.

## 31. Kunci / Lock

Lock adalah perlindungan editor, bukan security boundary.

Pilihan:

```text
Kunci Semua
Kunci Posisi
Kunci Ukuran
Kunci Style
Kunci Konten
Kunci Binding
Kunci Event/Aksi
```

Object yang terkunci tetap dapat dipilih dan diinspeksi. Saat Edit ON, indikator lock dapat tampil sebagai editor overlay dan tidak ikut ke aplikasi hasil build.

Jika pengguna mencoba mengubah bagian yang terkunci, ToolBox memberi pilihan `Buka Kunci` atau `Batal`.

Multi-select dapat mendukung `Kunci Semua Terpilih`.

## 32. Lainnya

Menu `Lainnya` berisi fungsi manajemen object yang lebih jarang digunakan:

```text
Copy
Duplicate
Replace Component
Reset ke Default
Simpan sebagai Component
Simpan sebagai Template
Hapus
```

Copy/Paste dan Duplicate menghasilkan Stable ID baru untuk object hasil duplikasi/paste sesuai aturan identity project.

Replace Component hanya mempertahankan property/binding yang benar-benar kompatibel.

Reset menghapus override dan kembali ke default/style contract yang sah.

Delete tetap dapat di-Undo selama history tersedia. Operasi berisiko seperti Delete/Reset dapat meminta konfirmasi singkat.

## 33. Prinsip Umum Floating Editor Fungsi Object

Fungsi object di atas mengikuti pola umum:

```text
Tap Object
↓
Edge menampilkan fungsi yang relevan
↓
Tap satu fungsi
↓
Floating Editor khusus fungsi terbuka
↓
edit dengan tap / slider / drag / angka / preview visual
↓
perubahan masuk Working State
↓
Undo/Redo tetap tersedia
↓
Manual Save untuk commit persistent
```

Floating Editor tidak menjadi source of truth kedua. Ia hanya menjadi surface editor terhadap model/property contract object aktif.
