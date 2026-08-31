# TEST_ROUTING_POLICY.md — ToolBox Test Routing & Final-Gate Authorization

## 1. Status dan Tujuan

Dokumen ini adalah sumber aturan utama untuk **pemilihan lingkungan pengujian** dan **otorisasi Firebase Test Lab** pada repository `RMTampu/Tools`.

Tujuan aturan ini:

- menjadikan GitHub Actions sebagai jalur default untuk development, basic, intermediate, regression, debugging, dan pemeriksaan berulang;
- membuat environment pengujian GitHub **fleksibel** agar pekerjaan tidak terhambat hanya karena Android 11 ARM64 tidak tersedia secara praktis;
- mempertahankan target produk ToolBox tetap Android 11 / API 30 / ARM64 (`arm64-v8a`);
- mengunci **setiap Firebase Test Lab final execution** hanya pada Android 11 / API 30 / ARM64;
- memastikan Firebase **tidak pernah dijalankan otomatis hanya karena GitHub PASS**;
- memastikan setiap eksekusi Firebase membutuhkan persetujuan eksplisit pengguna;
- memastikan **1 persetujuan = tepat 1 percobaan eksekusi Firebase**;
- mencegah auto-retry, approval reuse, approval inheritance, atau penggunaan kuota tanpa keputusan pengguna;
- membedakan dengan tegas development evidence dari final target evidence.

Prinsip inti:

```text
GITHUB ACTIONS = FLEXIBLE DEVELOPMENT / REGRESSION TEST ENVIRONMENT
FIREBASE TEST LAB = USER-AUTHORIZED FINAL TARGET GATE
FIREBASE TARGET = ANDROID 11 / API 30 / ARM64 ONLY
1 USER APPROVAL = 1 FIREBASE EXECUTION ATTEMPT
```

---

## 2. Prioritas dan Hubungan dengan Aturan Lain

Dokumen ini mengatur **test execution routing**. Ia tidak menghapus requirement correctness, coverage, proof, invariant, gate, atau evidence dari R1–R9, `APPLICATION_SAFE_100`, `ASSET_SAFE_100`, dan aturan repository lainnya.

Urutan prioritas:

1. instruksi pengguna terbaru;
2. `AGENTS.md`;
3. `TEST_ROUTING_POLICY.md` untuk seluruh keputusan test environment dan Firebase authorization;
4. prosedur/metode domain lain.

Setiap kalimat pada dokumen lain yang menyebut Android 11 / API 30 / ARM64 harus dibaca sebagai **target produk / compatibility target**, kecuali kalimat tersebut secara eksplisit membahas Firebase Final Gate.

Tidak boleh menafsirkan penyebutan target produk tersebut sebagai perintah bahwa seluruh runtime test GitHub wajib berjalan pada Android 11 ARM64.

Jika ada wording lama yang dapat ditafsirkan sebagai:

```text
ALL GITHUB TESTS MUST RUN ON ANDROID 11 ARM64
```

interpretasi tersebut DILARANG. Aturan environment test yang sah adalah dokumen ini.

---

## 3. Target Produk Tidak Berubah

Target distribusi utama ToolBox tetap:

```text
ANDROID_VERSION = Android 11
ANDROID_API = 30
RELEASE_ABI = arm64-v8a
```

Fleksibilitas test GitHub **tidak** berarti:

- APK release boleh berubah menjadi x86/x86_64;
- dependency native boleh kehilangan `arm64-v8a`;
- min/target SDK boleh berubah tanpa keputusan desain;
- engine boleh mengklaim ARM64 tanpa compatibility contract;
- target produk boleh bergeser dari Android 11.

Yang dibuat fleksibel hanya **environment pengujian development di GitHub**.

---

## 4. State Resmi

State routing resmi:

```text
DEVELOPMENT_IN_PROGRESS
DEVELOPMENT_PASS
FINAL_GATE_WAITING_USER_APPROVAL
FIREBASE_AUTHORIZED_ONCE
FIREBASE_EXECUTION_IN_PROGRESS
FIREBASE_AUTHORIZATION_CONSUMED
FIREBASE_TARGET_PASS
FIREBASE_TARGET_FAIL
FINAL_GATE_NOT_PROVEN
```

State default Firebase selalu:

```text
FIREBASE_AUTHORIZATION = LOCKED
```

Tidak ada state `AUTO_APPROVED`.
Tidak ada state `PERMANENT_APPROVAL`.
Tidak ada state `RETRY_APPROVED`.

---

## 5. GitHub Actions Adalah Jalur Default

Selama pengembangan, agen wajib menggunakan GitHub Actions untuk seluruh pemeriksaan yang dapat dilakukan di sana.

Contoh:

- compile/build checks;
- lint/static analysis;
- unit tests;
- contract/model tests;
- integration tests;
- Android emulator tests;
- UI tests;
- lifecycle tests;
- regression tests;
- install/startup smoke tests;
- debugging;
- fault injection yang didukung;
- dependency/manifest/package checks;
- artifact inspection;
- native checks yang dapat dilakukan tanpa final target device.

Firebase tidak boleh dipakai hanya karena Firebase tersedia.

---

## 6. GitHub Test Environment Bersifat Fleksibel

Untuk development/basic/intermediate/regression testing di GitHub, agen boleh menggunakan Android runtime/emulator yang paling sesuai dan tersedia secara praktis.

Urutan preferensi yang dianjurkan, bukan hard lock:

```text
1. Android 11 / API 30 bila tersedia dan stabil
2. API terdekat yang relevan terhadap behavior yang diuji
3. emulator/ABI yang tersedia dan stabil pada GitHub
4. x86_64 diperbolehkan untuk behavior yang tidak bergantung pada ARM64
```

GitHub testing boleh menggunakan:

- API selain 30;
- ABI x86_64 atau ABI test lain yang tersedia;
- emulator/model yang berbeda dari target final;
- environment yang lebih baru/lama bila masih valid untuk menemukan defect pada scope tersebut.

Syaratnya:

- environment yang dipakai harus dicatat pada evidence;
- agen tidak boleh membuat claim lebih luas daripada environment yang diuji;
- environment non-target tidak boleh diklaim sebagai bukti final Android 11 ARM64;
- bila suatu behavior benar-benar architecture/API-specific, limitation harus dicatat sebagai target-specific evidence yang belum ditutup.

---

## 7. GitHub PASS Bukan Firebase PASS

Status berikut berbeda:

```text
DEVELOPMENT_PASS
FIREBASE_TARGET_PASS
APPLICATION_SAFE_100
```

`DEVELOPMENT_PASS` berarti seluruh pemeriksaan GitHub yang relevan terhadap pekerjaan aktif telah PASS pada environment development yang digunakan.

`DEVELOPMENT_PASS` tidak otomatis berarti:

```text
ANDROID_11_ARM64_RUNTIME_PROVEN
FIREBASE_TARGET_PASS
APPLICATION_SAFE_100
```

Agen dilarang menyamakan ketiganya.

---

## 8. Kapan DEVELOPMENT_PASS Boleh Diberikan

Agen hanya boleh menyatakan `DEVELOPMENT_PASS` jika:

```text
IMPLEMENTATION_SCOPE_COMPLETE
AND REQUIRED_PREBUILD_GATES = PASS
AND REQUIRED_GITHUB_BUILD_OR_CHECKS = PASS
AND REQUIRED_GITHUB_TESTS = PASS
AND KNOWN_BLOCKING_FAILURE = 0
AND TEST_ENVIRONMENT_RECORDED
AND UNRESOLVED_DEVELOPMENT_FAILURE = 0
```

Target-specific proof yang memang hanya dapat diperoleh di Final Gate boleh tetap belum tersedia dan tidak menghalangi `DEVELOPMENT_PASS`, selama:

- ketiadaan proof tersebut dinyatakan eksplisit;
- tidak diklaim sebagai target PASS;
- seluruh proof development yang dapat dilakukan di GitHub sudah diselesaikan.

---

## 9. Mandatory Stop Setelah DEVELOPMENT_PASS

Setelah pekerjaan executable/runtime mencapai `DEVELOPMENT_PASS`, agen **WAJIB BERHENTI sebelum Firebase**.

Tidak boleh ada transisi otomatis:

```text
DEVELOPMENT_PASS -> FIREBASE
```

Transisi yang sah:

```text
DEVELOPMENT_PASS
        ↓
FINAL_GATE_WAITING_USER_APPROVAL
        ↓
AGEN BERTANYA KEPADA PENGGUNA
```

Agen wajib menampilkan identitas kandidat yang akan diuji sejauh tersedia, minimal:

- pekerjaan/fitur yang selesai;
- source revision / commit SHA bila tersedia;
- artifact/run identity bila tersedia;
- jenis final test yang akan dijalankan;
- target final: Android 11 / API 30 / ARM64;
- penegasan bahwa izin hanya berlaku untuk satu eksekusi.

Pertanyaan harus memiliki makna eksplisit seperti:

```text
Pengujian GitHub sudah DEVELOPMENT_PASS.
Apakah Anda mengizinkan 1x eksekusi Firebase Final Gate untuk kandidat ini pada Android 11 / API 30 / ARM64?
```

---

## 10. Apa yang Dianggap Persetujuan Sah

Persetujuan hanya sah jika pengguna menjawab secara eksplisit terhadap pertanyaan Final Gate tersebut.

Contoh jawaban yang sah dalam konteks pertanyaan Final Gate:

```text
ya
yes
setuju
izinkan
lanjut final gate
jalankan firebase 1x
```

Persetujuan TIDAK boleh disimpulkan dari:

- persetujuan yang diberikan untuk pertanyaan lain;
- perintah lama pada percakapan sebelumnya;
- kalimat umum seperti `lanjutkan pekerjaan` bila tidak menjawab pertanyaan Final Gate;
- fakta bahwa pengguna sebelumnya pernah mengizinkan Firebase;
- fakta bahwa Firebase sudah terkonfigurasi;
- fakta bahwa GitHub PASS;
- asumsi agen bahwa final test sebaiknya dijalankan;
- approval milik kandidat/run sebelumnya.

Jika jawaban ambigu:

```text
FIREBASE_AUTHORIZATION = LOCKED
```

Agen wajib meminta kejelasan dan tidak boleh menjalankan Firebase.

---

## 11. Single-Use Authorization

Setelah persetujuan eksplisit:

```text
FIREBASE_AUTHORIZATION = AUTHORIZED_ONCE
```

Otorisasi tersebut:

- berlaku untuk **1 execution attempt saja**;
- berlaku hanya untuk kandidat yang ditanyakan;
- tidak berlaku permanen;
- tidak dapat diwariskan ke agen lain;
- tidak dapat dipakai untuk kandidat/artifact lain;
- tidak dapat dipakai untuk retry;
- tidak dapat dipakai untuk run kedua;
- tidak dapat disimpan sebagai blanket permission;
- tidak boleh diinterpretasikan sebagai `gunakan Firebase kapan pun diperlukan`.

Formula:

```text
1 EXPLICIT USER APPROVAL = 1 FIREBASE TEST EXECUTION ATTEMPT
```

---

## 12. Kapan Otorisasi Dianggap Terpakai

Otorisasi dianggap terpakai saat satu final-test execution attempt dimulai.

Begitu execution attempt dimulai:

```text
FIREBASE_AUTHORIZATION = CONSUMED
```

Otorisasi tetap dianggap habis jika attempt menghasilkan:

- PASS;
- FAIL;
- test matrix error;
- timeout;
- cancellation setelah attempt dimulai;
- infrastructure error saat execution attempt;
- immediate execution rejection setelah command final test disubmit.

Setelah itu:

```text
FIREBASE_AUTHORIZATION = LOCKED
```

Tidak ada auto-retry.

Untuk percobaan berikutnya, agen wajib bertanya lagi kepada pengguna.

---

## 13. Perubahan Kandidat Membatalkan Otorisasi

Sebelum execution attempt dimulai, authorization juga batal jika kandidat berubah material.

Contoh perubahan yang membatalkan authorization:

- source commit berubah;
- APK dibangun ulang dari input berbeda;
- artifact diganti;
- dependency berubah;
- manifest berubah;
- signing identity berubah;
- test scope berubah secara material;
- target final test berubah.

Setelah perubahan tersebut:

```text
FIREBASE_AUTHORIZATION = LOCKED
```

Agen harus menyelesaikan kembali affected GitHub verification dan meminta approval baru.

---

## 14. User Rejection / No Response

Jika pengguna menjawab tidak, menolak, belum siap, atau tidak memberikan persetujuan:

```text
FINAL_GATE = CLOSED
FIREBASE_AUTHORIZATION = LOCKED
```

Agen tidak boleh menjalankan Firebase.

`DEVELOPMENT_PASS` tetap boleh dipertahankan selama evidence-nya masih valid.

Agen tidak boleh mengubah penolakan menjadi kegagalan aplikasi.

---

## 15. Firebase Final Gate Dikunci Keras ke Android 11 ARM64

Berbeda dengan GitHub yang fleksibel, **Firebase Final Gate tidak fleksibel**.

Setiap Firebase final test WAJIB menggunakan:

```text
ANDROID_VERSION = Android 11
ANDROID_API = 30
ABI = arm64-v8a
DEVICE/MODEL = Firebase model yang secara live membuktikan dukungan API 30 + ARM64
```

Firebase Final Gate DILARANG menggunakan fallback ke:

- API 29;
- API 31+;
- x86;
- x86_64;
- non-ARM model;
- device yang tidak membuktikan `arm64-v8a`;
- target lain hanya karena target wajib sedang tidak tersedia.

Jika target wajib tidak tersedia:

```text
FINAL_GATE_TARGET_AVAILABLE = NO
FINAL_GATE_RESULT = NOT_PROVEN
```

Jangan mengganti target diam-diam.

---

## 16. Preflight Target Check

Sebelum mengirim APK untuk final execution, workflow/agen wajib memeriksa katalog Firebase dan membuktikan:

```text
MODEL_SUPPORTS_API_30 = TRUE
MODEL_SUPPORTS_ARM64_V8A = TRUE
MODEL_IS_LIVE/AVAILABLE = TRUE
```

Jika salah satu false/unknown:

- APK tidak boleh dikirim ke target alternatif;
- target final tidak boleh diklaim PASS;
- agen melaporkan bahwa Final Gate belum dapat dieksekusi pada target yang dikunci.

---

## 17. Tidak Ada Auto-Fallback

Aturan hard fail:

```text
FIREBASE_API != 30        -> DO NOT RUN FINAL TEST
FIREBASE_ABI != arm64-v8a -> DO NOT RUN FINAL TEST
TARGET_NOT_PROVEN         -> DO NOT CLAIM PASS
```

Tidak ada `best available Firebase device` untuk Final Gate.
Yang diperbolehkan hanya `required target available` atau `final target unavailable`.

---

## 18. Tidak Ada Auto-Retry Firebase

Dilarang membuat mekanisme:

```text
Firebase FAIL -> automatic Firebase retry
Firebase timeout -> automatic Firebase retry
Firebase infra error -> automatic Firebase retry
Firebase flaky -> automatic Firebase retry
```

Setiap retry adalah execution attempt baru dan membutuhkan persetujuan pengguna baru.

Alur yang benar:

```text
FIREBASE ATTEMPT #1
        ↓
AUTHORIZATION CONSUMED
        ↓
PASS / FAIL / ERROR / TIMEOUT
        ↓
FIREBASE LOCKED
        ↓
Jika run lain diperlukan:
AGEN BERTANYA LAGI
        ↓
USER APPROVAL #2
        ↓
FIREBASE ATTEMPT #2
```

---

## 19. Jika Firebase FAIL

Jika final test menemukan defect aplikasi:

```text
FIREBASE FAIL
   ↓
FIREBASE LOCKED
   ↓
ANALYZE RESULT
   ↓
FIX DI SOURCE
   ↓
RETURN TO GITHUB
   ↓
RERUN AFFECTED DEVELOPMENT/REGRESSION TESTS
   ↓
DEVELOPMENT_PASS BARU
   ↓
ASK USER AGAIN
```

Firebase tidak boleh menjadi iterative debugging loop.

Jika defect hanya dapat direproduksi pada Android 11 ARM64, analisis boleh menggunakan evidence Firebase yang sudah diperoleh, tetapi execution baru tetap membutuhkan approval baru.

---

## 20. Jika Firebase PASS

Jika Firebase test PASS pada target terkunci:

```text
FIREBASE_TARGET_PASS
```

hanya berlaku untuk:

- kandidat/artifact yang diuji;
- test scope yang benar-benar dijalankan;
- Android 11 / API 30 / ARM64 environment tersebut;
- evidence/run identity tersebut.

Satu Firebase PASS tidak otomatis berarti seluruh `APPLICATION_SAFE_100` selesai.

Domain R1–R9, asset proof, cross-domain proof, physical-device claim, atau proof lain yang masih diperlukan tetap harus ditutup sesuai scope.

---

## 21. Hardware / Vendor / Physical Device Exception

Firebase virtual ARM64 tidak boleh dipakai untuk mengklaim hal yang memang memerlukan physical-device evidence.

Contoh:

- sensor fisik;
- Bluetooth/NFC/camera quirk;
- OEM/vendor-specific behavior;
- thermal behavior;
- hardware-specific GPU timing;
- physical power behavior.

Untuk claim tersebut gunakan evidence fisik/authoritative yang diwajibkan R8.

Aturan ini tidak membuka permission Firebase tambahan dan tidak mengubah single-use authorization.

---

## 22. Evidence Binding

Setiap Firebase final execution wajib mencatat sejauh tersedia:

```text
USER_APPROVAL_CONTEXT
SOURCE_COMMIT_SHA
SOURCE_RUN_ID
ARTIFACT_NAME
APK_SHA256
FIREBASE_PROJECT
DEVICE_MODEL
ANDROID_API = 30
ABI = arm64-v8a
TEST_TYPE
TEST_MATRIX/RUN_ID
TIMESTAMP
RESULT
```

Approval tidak boleh dipindahkan ke evidence lain.

---

## 23. Evidence Reuse

Evidence Firebase lama boleh digunakan kembali untuk claim yang masih identik dan tidak terinvalidasi oleh change-impact analysis.

Evidence reuse berarti **tidak menjalankan Firebase baru**.

Evidence reuse tidak menghasilkan authorization baru dan tidak boleh digunakan sebagai alasan untuk menjalankan test tambahan tanpa approval.

---

## 24. Development Environment vs Target-Specific Claim

Contoh sah:

```text
GitHub API 35 x86_64
-> UI navigation regression PASS
-> DEVELOPMENT_PASS
```

Itu tidak membuktikan:

```text
Android 11 ARM64 final runtime PASS
```

Contoh native:

```text
GitHub x86_64
-> logic around plugin loader PASS
-> APK inspection proves arm64-v8a .so packaged correctly
-> target-specific ARM64 runtime load remains unproven
```

Jika pengguna mengizinkan Final Gate:

```text
Firebase Android 11 API30 ARM64
-> target-specific runtime witness
```

---

## 25. Routing Decision Resmi

```text
START WORK
   ↓
IMPLEMENT / FIX / CHANGE
   ↓
GITHUB FLEXIBLE VERIFICATION
   ↓
PASS?
   ├─ NO -> FIX -> GITHUB AGAIN
   │
   └─ YES
        ↓
DEVELOPMENT_PASS
        ↓
STOP BEFORE FIREBASE
        ↓
ASK USER FOR 1x FINAL-GATE APPROVAL
        ↓
USER APPROVES?
   ├─ NO / AMBIGUOUS
   │      -> FIREBASE LOCKED
   │
   └─ YES
          ↓
FIREBASE_AUTHORIZED_ONCE
          ↓
VERIFY FIREBASE TARGET = API30 + ARM64
          ↓
TARGET VALID?
   ├─ NO -> FINAL_GATE_NOT_PROVEN / NO FALLBACK
   │
   └─ YES
          ↓
ONE FIREBASE EXECUTION ATTEMPT
          ↓
AUTHORIZATION CONSUMED
          ↓
FIREBASE LOCKED AGAIN
```

---

## 26. Mandatory Agent Behavior

Setiap agen yang terlibat dalam test wajib:

1. membaca `AGENTS.md` dan dokumen ini;
2. membedakan target produk dari test environment;
3. menggunakan GitHub sebagai default route;
4. memilih environment GitHub secara fleksibel berdasarkan availability dan relevansi;
5. mencatat environment aktual;
6. tidak menyebut environment GitHub non-target sebagai Android 11 ARM64 final proof;
7. menyelesaikan seluruh affected GitHub checks sebelum `DEVELOPMENT_PASS`;
8. setelah `DEVELOPMENT_PASS`, berhenti sebelum Firebase;
9. meminta approval Final Gate secara eksplisit;
10. tidak menjalankan Firebase bila approval belum ada;
11. memperlakukan approval sebagai single-use;
12. mengikat approval ke kandidat yang ditanyakan;
13. mengunci Firebase target ke Android 11/API30/ARM64;
14. menolak fallback;
15. menghabiskan approval setelah satu execution attempt;
16. tidak auto-retry;
17. meminta approval baru untuk setiap attempt berikutnya;
18. tidak mengurangi verification requirement lain hanya untuk menghemat quota.

---

## 27. Anti-Misinterpretation Rules

Interpretasi berikut DILARANG:

```text
GitHub PASS means Firebase may run automatically.
Previous user approval means future Firebase runs are approved.
One approval allows retries.
One approval allows multiple test matrices.
One approval allows another artifact.
Android 11 ARM64 target means every GitHub emulator must be Android 11 ARM64.
GitHub x86_64 PASS means ARM64 runtime is proven.
Firebase can fall back to x86_64 if ARM64 unavailable.
Firebase can fall back to another API if API30 unavailable.
Firebase error allows automatic rerun.
Agent judgment can replace user approval.
```

Semua interpretasi di atas salah.

---

## 28. Final Rule

```text
TARGET PRODUCT = ANDROID 11 / API 30 / ARM64

GITHUB TESTING = FLEXIBLE
GITHUB = DEFAULT DEVELOPMENT ROUTE

AFTER DEVELOPMENT_PASS:
STOP AND ASK USER

USER APPROVAL = SINGLE USE
1 APPROVAL = 1 FIREBASE EXECUTION ATTEMPT
AFTER ATTEMPT = LOCKED AGAIN
EVERY RETRY = ASK AGAIN

FIREBASE FINAL TARGET = ANDROID 11 / API 30 / ARM64 ONLY
NO FIREBASE FALLBACK
NO AUTO FIREBASE
NO AUTO RETRY
NO APPROVAL REUSE
```
