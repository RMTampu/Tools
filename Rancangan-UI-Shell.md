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

Contoh urutan menu untuk sebuah object:

```text
EDGE — Object Terpilih
├─ Pilih Tampilan / Style
├─ Ubah Ukuran
├─ Posisi
├─ Isi / Konten
├─ Warna
├─ Font
├─ Spacing
├─ Border
├─ State
├─ Binding
├─ Event
└─ Lainnya sesuai Property Contract
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
