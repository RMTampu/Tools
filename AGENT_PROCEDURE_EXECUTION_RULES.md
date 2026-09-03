> Catatan aktif: aturan lintas repo lama sudah dihapus. ToolBox dikerjakan di repo publik utama; repo private hanya untuk secret, signing, build final, Firebase/final runtime test, dan release sensitif.

## 8. Referensi Antar-File Wajib Diikuti

Jika file aturan A merujuk file aturan B sebagai requirement untuk pekerjaan aktif, agen WAJIB membaca B sebelum menjalankan langkah yang bergantung padanya.

Referensi yang belum dibaca diperlakukan sebagai:

```text
NOT_LOADED
```

dan bagian yang bergantung padanya tidak boleh dinyatakan PASS.

Agen tidak boleh menganggap isi file referensi sudah diketahui hanya karena pernah dibaca pada pekerjaan lain.

---

## 9. Evidence Wajib Dipertahankan

Setiap rule yang membutuhkan proof/evidence harus menghasilkan atau menunjuk evidence yang cukup sesuai aturan sumber.

Pernyataan seperti berikut tidak boleh menjadi dasar PASS:

```text
"sepertinya sudah"
"harusnya benar"
"kemungkinan aman"
"pernah dicek"
```

PASS harus dapat ditelusuri ke evidence yang relevan.

Jika evidence hilang, tidak dapat ditemukan, tidak lagi sesuai input saat ini, atau tidak dapat diverifikasi, status proof dianggap tidak lengkap sampai evidence diperoleh kembali.

---

## 10. Perubahan Membatalkan Proof yang Terdampak

Jika setelah suatu rule/gate PASS terjadi perubahan pada input yang menjadi dasar proof, agen wajib menentukan titik paling awal yang terdampak.

Contoh input yang dapat membatalkan proof:

- asset;
- contract;
- consumer;
- route/reference;
- configuration;
- dependency;
- source;
- generated output;
- build input;
- tool configuration;
- aturan/prosedur teknis yang mengubah requirement, acceptance criteria, input, atau metode pembuktian wajib;
- artifact antara;
- environment yang termasuk dalam contract proof.

### 10.1 Perubahan Dokumentasi Tidak Otomatis Membatalkan Proof

Perubahan dokumentasi/editorial **bukan** input proof dan tidak membatalkan status PASS, test, atau build yang sudah sah, selama tidak mengubah requirement teknis, contract, acceptance criteria, input build, konfigurasi, atau metode pembuktian wajib.

Contoh perubahan yang tidak membatalkan proof:

- ejaan, tata bahasa, format Markdown, atau struktur tampilan;
- tautan, navigasi, atau penjelasan yang tidak mengubah makna teknis;
- pencatatan status/evidence yang hanya merekam hasil yang telah ada.

Jika perubahan mengubah makna teknis, agen wajib melakukan impact analysis dan hanya menjalankan ulang rule/gate yang benar-benar terdampak. Build APK atau test runtime ulang hanya diperlukan bila perubahan tersebut memengaruhi input kandidat, contract build, artifact, atau claim yang sedang dibuktikan.

Seluruh status PASS setelah titik yang benar-benar terdampak dianggap tidak valid sampai rule/gate terkait dijalankan ulang.

---

## 11. Fail-Closed

Jika agen tidak dapat memastikan apakah suatu rule:

- telah dibaca;
- sedang menggunakan versi sumber yang berlaku;
- telah dijalankan lengkap;
- masih valid;
- memiliki evidence yang diperlukan;
- atau memenuhi syarat PASS;

status default adalah:

```text
NOT_PROVEN
```

bukan PASS.

Tahap berikutnya harus tetap tertutup sampai kondisi tersebut dibuktikan.

---

## 12. Optimasi yang Diizinkan dan Dilarang

Agen boleh mengoptimalkan:

- otomatisasi;
- pembagian sub-gate;
- batching pemeriksaan yang tetap mempertahankan seluruh requirement;
- parallel analysis jika dependency mengizinkan;
- caching evidence yang masih terbukti valid;
- penggunaan tool yang lebih efisien;
- pemisahan file aturan untuk mengurangi beban context.

Agen DILARANG mengoptimalkan dengan:

- menghapus pemeriksaan;
- mengurangi invariant;
- mengurangi coverage;
- mengganti proof dengan asumsi;
- melewati dependency;
- mengubah FAIL menjadi warning;
- mengubah UNKNOWN/NOT_PROVEN menjadi PASS;
- menghilangkan rule hanya karena sulit atau mahal dijalankan.

---

## 13. Prosedur Jika Kapasitas Sangat Terbatas

Jika agen hanya mampu menjalankan sebagian kecil prosedur dalam satu konteks, agen wajib menggunakan pola berikut:

```text
1. Tentukan unit aktif paling kecil yang masih lengkap.
2. Baca seluruh aturan sumber untuk unit aktif.
3. Jalankan seluruh langkah unit tersebut.
4. Kumpulkan dan simpan evidence/status.
5. Pastikan unit = PASS sebelum melanjutkan.
6. Baca aturan unit berikutnya.
7. Ulangi sampai gate selesai.
8. Baru lanjut ke gate berikutnya.
```

Tidak boleh mempercepat proses dengan menjalankan langkah berikutnya ketika langkah aktif belum terbukti selesai.

---

## 14. Aturan Khusus Build

Untuk setiap rantai yang memiliki build boundary, BUILD DILARANG DIMULAI jika masih ada satu saja rule pre-build wajib yang:

- belum dibaca;
- belum dimuat;
- belum dipahami;
- belum dijalankan;
- belum selesai;
- belum memiliki evidence;
- belum PASS;
- atau telah kehilangan validitas akibat perubahan.

Build tidak boleh digunakan sebagai cara untuk mencari tahu apakah prosedur pre-build seharusnya PASS.

Dilarang memulai build hanya untuk:

```text
"coba dulu"
"lihat apakah compile"
"cek nanti setelah build"
```

Build hanya dibuka oleh status PASS dari seluruh gate pre-build yang diwajibkan.

---

## 15. Kriteria Eksekusi Lengkap

Sebelum suatu gate/prosedur dinyatakan lengkap, kondisi berikut harus benar untuk scope yang diwajibkan:

```text
ALL_REQUIRED_RULES_AVAILABLE
AND
ALL_REQUIRED_RULES_READ
AND
ALL_REQUIRED_STEPS_EXECUTED
AND
ALL_REQUIRED_EVIDENCE_PRESENT
AND
NO_REQUIRED_STEP_OMITTED
AND
NO_PASS_BASED_ON_ASSUMPTION
AND
NO_REQUIRED_RULE_NOT_PROVEN
```

Jika salah satu kondisi tidak terpenuhi, gate/prosedur belum lengkap.

---

## 16. Prinsip Final

Aturan final untuk setiap agen:

```text
AGEN TIDAK WAJIB MENYIMPAN SELURUH ATURAN DI MEMORY SEKALIGUS.

AGEN WAJIB MEMASTIKAN BAHWA SELURUH ATURAN YANG BERLAKU UNTUK LANGKAH AKTIF
SEDANG TERSEDIA, DIBACA, DIPAHAMI, DAN DIJALANKAN LENGKAP.

JIKA TIDAK MUAT:
PECAH EKSEKUSI.

JANGAN PECAH ATAU KURANGI ATURAN.
```

Keterbatasan context/memory hanya boleh mengubah ukuran unit eksekusi, tidak boleh mengubah kelengkapan prosedur atau standar PASS.
