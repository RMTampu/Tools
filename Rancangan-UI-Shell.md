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
