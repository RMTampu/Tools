# Firebase Test Lab — Public Repository Boundary

## 1. Status

`RMTampu/Tools` **dilarang melakukan seluruh akses, pengecekan, dan pengujian Firebase/Test Lab**, termasuk terhadap dummy/prototype/artifact Public. Repository ini bukan Firebase executor, caller, atau bridge.

Semua routing lama yang memakai pola berikut dinyatakan tidak berlaku:

```text
PRIVATE APK -> RMTampu/Tools PUBLIC -> FIREBASE
PRIVATE SOURCE -> PUBLIC BUILD -> FIREBASE
```

Seluruh operasi Firebase untuk ToolBox hanya berada pada boundary Private `RMTampu/ToolBox`. Larangan Public tidak bergantung pada asal artifact atau adanya single-use approval.

## 2. Yang Dilarang di Public

Repository ini tidak boleh:

- menerima APK/artifact Private;
- menerima source/asset/config/state Private;
- menerima credential untuk membaca Private;
- mengambil artifact dari run Private untuk diteruskan ke Firebase;
- melakukan connection check, autentikasi, catalog/model lookup, atau candidate preflight yang mengakses Firebase;
- mengunggah/mengunduh artifact atau hasil melalui Firebase/Test Lab;
- menjalankan Firebase test matrix, smoke test, atau test lain, termasuk untuk dummy/prototype Public;
- menjadi caller, relay, atau bridge Firebase/final-test.

## 3. Yang Masih Boleh Dilakukan

Public hanya boleh melakukan riset **tanpa akses operasional atau panggilan ke layanan Firebase/Test Lab**, misalnya:

- mempelajari dokumentasi contract/API yang sudah terbuka;
- membuat mock/simulator mandiri yang tidak terhubung ke Firebase;
- menyusun test strategy untuk pelaksanaan kelak di Private;
- memvalidasi logic komponen/tooling menggunakan fixture Public tanpa Firebase.

Pengujian penyambungan di Public menggunakan dummy mandiri dari contract aman sebagai pengganti baseline APK/state final, bukan salinan/ekstraksi/penyamaran isi Private. Dummy/prototype tersebut **tidak boleh diuji melalui Firebase**.

Hasil tersebut hanya evidence Public/research. Komponen mencapai `COMPONENT_READY_PRIVATE` dan ditahan sampai seluruh tahap `STAGE_READY_PRIVATE`; hanya tahap utuh dengan otorisasi yang berlaku dapat dipromosikan menurut aturan global §6. Tidak ada final ToolBox runtime proof dari dummy atau izin Private dari status komponen.

## 4. Authorization Principle

Public tidak mempunyai jalur authorization Firebase. Aturan berikut **hanya untuk final execution di Private**:

```text
PUBLIC_FIREBASE = FORBIDDEN
PRIVATE_FINAL_FIREBASE_DEFAULT = LOCKED
1 EXPLICIT USER APPROVAL = 1 PRIVATE FINAL FIREBASE EXECUTION ATTEMPT
NO AUTO RETRY
NO FALLBACK
NO APPROVAL REUSE
```

Mode `connection-only` dan `candidate-preflight`, bila dipakai, juga hanya di Private dan mengikuti policy Private; keduanya tidak mengizinkan Public melakukan pengecekan atau submit matrix. Persetujuan final Private tidak membuka pengecualian dummy/prototype Public.

## 5. Final Candidate Order

Jalur final Private yang menjadi referensi konseptual:

```text
BUILD APK
-> SIGN CANDIDATE
-> VERIFY SIGNATURE / HASH / PROVENANCE
-> FIREBASE / FINAL TEST
-> PASS
-> RELEASE EXACT SAME SIGNED CANDIDATE
```

Public tidak menjalankan tahapan tersebut terhadap isi Private.

## 6. Final Rule

```text
PUBLIC_FIREBASE_PRIVATE_ARTIFACT_BRIDGE = 0
PRIVATE_CONTENT_IN_PUBLIC = 0
PRIVATE_EXECUTION_THROUGH_PUBLIC = 0
FIREBASE_EXECUTION_BOUNDARY = PRIVATE_ONLY
PUBLIC_FIREBASE_ACCESS = 0
PUBLIC_FIREBASE_EXECUTION = 0
PUBLIC_FIREBASE_DUMMY_EXCEPTION = 0
```
