# TEST_ROUTING_POLICY.md — ToolBox

## 1. Status dan Tujuan

Dokumen ini adalah aturan routing pengujian untuk repository `RMTampu/Tools`.

Tujuannya adalah:

- menjadikan GitHub Actions sebagai jalur default untuk pengujian development, basic, intermediate, dan regression;
- melindungi kuota Firebase Test Lab agar tidak terpakai untuk debugging atau pemeriksaan berulang yang dapat dilakukan di GitHub;
- menggunakan Firebase Test Lab terutama pada final qualification untuk kandidat fitur/perubahan/release yang sudah stabil;
- mempertahankan target distribusi resmi Android 11 / API 30 / ARM64 (`arm64-v8a`);
- mengizinkan environment GitHub yang paling sesuai untuk pengujian dasar bila Android 11 ARM64 tidak tersedia secara praktis di GitHub;
- mencegah environment non-target dianggap sebagai bukti final target;
- menggunakan kembali evidence yang masih valid sesuai change-impact analysis.

Prinsip utama:

```text
GITHUB = DEVELOPMENT / BASIC / INTERMEDIATE / REGRESSION VERIFICATION
FIREBASE TEST LAB = FINAL TARGET QUALIFICATION
```

Penghematan kuota hanya boleh mengubah **tempat, waktu, dan batching pengujian**. Penghematan kuota DILARANG mengurangi required test, coverage, invariant, proof, evidence, atau syarat PASS yang diwajibkan aturan repository lain.

---

## 2. Hubungan dengan Aturan Repository Lain

Dokumen ini mengatur **routing lingkungan pengujian**, bukan menggantikan requirement pengujian.

Urutan prioritas tetap:

1. instruksi pengguna terbaru;
2. `AGENTS.md`;
3. prosedur/gate wajib yang dirujuk oleh `AGENTS.md`;
4. `TEST_ROUTING_POLICY.md` untuk pemilihan lingkungan pengujian.

Jika suatu rule R1–R9, asset rule, prebuild gate, atau prosedur lain mewajibkan proof tertentu, proof tersebut tetap wajib. Policy ini hanya menentukan di mana proof dapat/harus dijalankan dan kapan Firebase boleh digunakan.

---

## 3. Target Resmi

Target distribusi final ToolBox tetap:

```text
Android 11
API 30
ABI arm64-v8a
```

Tidak tersedianya GitHub-hosted Android 11 ARM64 runtime yang identik dengan target final TIDAK mengubah target resmi.

Environment GitHub yang berbeda API/ABI hanya boleh menjadi development/basic evidence sesuai batas klaim pada dokumen ini.

---

## 4. Stage T0 — Source / Static / Unit Verification

Lokasi default:

```text
GitHub Actions
```

Digunakan untuk pekerjaan yang tidak membutuhkan target runtime final, termasuk bila relevan:

- compile checks;
- lint;
- static analysis;
- dependency verification;
- source invariant checks;
- pure JVM/unit tests;
- contract tests;
- deterministic model tests;
- asset/prebuild checks;
- kernel/core tests yang independen dari Android runtime.

Firebase Test Lab DILARANG digunakan untuk T0.

Output T0 tidak boleh diklaim sebagai runtime Android 11 ARM64 proof.

---

## 5. Stage T1 — Basic Android Runtime Verification

Lokasi default:

```text
GitHub Actions
```

Gunakan Android emulator/runtime yang paling sesuai dan tersedia secara praktis.

Urutan preferensi:

1. API 30 bila tersedia;
2. API Android yang perilakunya paling dekat dan masih relevan terhadap perubahan yang diuji;
3. ABI emulator yang tersedia di GitHub, termasuk `x86_64`, bila ARM64 tidak tersedia secara praktis.

T1 digunakan untuk menemukan failure dasar, misalnya:

- startup crash;
- lifecycle error dasar;
- navigation/interaction error;
- manifest/configuration problem;
- resource/runtime integration failure;
- UI behavior dasar;
- install/startup issue yang dapat direproduksi pada environment tersebut.

Environment non-ARM64 atau non-API30 pada T1 DILARANG dianggap sebagai bukti final kompatibilitas Android 11 ARM64.

Status yang boleh dihasilkan antara lain:

```text
GITHUB_BASIC_RUNTIME_PASS
```

Status tersebut bukan `ANDROID_11_ARM64_FINAL_PASS`.

---

## 6. Stage T2 — Development / Regression Verification

Lokasi default:

```text
GitHub Actions
```

T2 digunakan selama:

- implementasi fitur;
- penambahan engine/tool;
- debugging;
- refactor;
- bug fixing;
- perubahan UI;
- perubahan dependency;
- perubahan asset;
- perubahan manifest;
- perubahan persistence/lifecycle/runtime;
- perubahan workflow;
- perubahan internal lain yang belum menjadi final candidate.

Selama perubahan masih berada pada development loop:

```text
FIREBASE_TEST_LAB_ROUTE = FORBIDDEN
```

Agen wajib menggunakan pemeriksaan GitHub yang relevan terlebih dahulu dan memperbaiki semua failure yang dapat ditemukan tanpa Firebase.

---

## 7. FINAL_CANDIDATE_READY

Firebase final qualification hanya boleh dibuka setelah perubahan menjadi kandidat final.

Minimal:

```text
IMPLEMENTATION_COMPLETE
AND REQUIRED_PREBUILD_GATES = PASS
AND CONTROLLED_GITHUB_BUILD = PASS
AND REQUIRED_GITHUB_TESTS = PASS
AND KNOWN_CRITICAL_HIGH_FAILURE = 0
AND FINAL_CANDIDATE_ARTIFACT_EXISTS
AND SOURCE_REVISION_IS_LOCKED
AND NO_PLANNED_SOURCE_CHANGE_BEFORE_FINAL_QUALIFICATION
```

Jika salah satu kondisi belum terpenuhi:

```text
FINAL_CANDIDATE_READY = NO
FIREBASE_TEST_LAB_ROUTE = CLOSED
```

Jika seluruh kondisi terpenuhi:

```text
FINAL_CANDIDATE_READY = YES
```

---

## 8. Stage T3 — Firebase Final Target Qualification

Lokasi:

```text
Firebase Test Lab
```

Firebase digunakan sebagai **final target qualification**, bukan development/debugging environment.

Target final qualification harus sedekat mungkin dengan target distribusi resmi:

```text
Android 11 / API 30
ARM64 / arm64-v8a
```

Firebase boleh digunakan setelah `FINAL_CANDIDATE_READY = YES` ketika final Android 11 ARM64 runtime/device evidence memang diwajibkan atau evidence sebelumnya telah terinvalidasi.

Contoh scope:

- fitur baru yang telah selesai;
- engine/tool baru yang telah stabil;
- perubahan runtime/lifecycle/persistence yang telah selesai;
- perubahan native/plugin yang telah selesai;
- perubahan UI/device-sensitive yang telah selesai;
- bug fix runtime target yang telah lolos regression GitHub;
- release candidate;
- perubahan material yang menginvalidasi evidence Android 11 ARM64 sebelumnya.

Firebase final qualification yang PASS hanya membuktikan scope yang benar-benar diuji. Satu run Firebase tidak menggantikan seluruh R1–R9 atau seluruh `APPLICATION_SAFE_100`.

---

## 9. Firebase Forbidden Uses

Firebase Test Lab DILARANG digunakan hanya untuk:

- mencoba apakah source compile;
- mencoba apakah build berhasil;
- debugging awal;
- setiap commit;
- setiap perubahan kecil;
- perubahan dokumentasi/comment/formatting;
- unit-test-only changes;
- static-analysis-only changes;
- perubahan yang tidak mempengaruhi runtime target;
- mengulang evidence yang masih valid;
- menjalankan kandidat yang masih diketahui akan diubah;
- menggantikan pemeriksaan GitHub yang dapat dilakukan tanpa Firebase;
- loop `Firebase -> edit -> Firebase -> edit` selama debugging.

---

## 10. Fail → Fix → GitHub → New Final Candidate → Firebase

Jika final qualification Firebase FAIL:

```text
FIREBASE FAIL
      ↓
ANALYZE FAILURE
      ↓
FIX SOURCE / CONFIGURATION
      ↓
CHANGE-IMPACT ANALYSIS
      ↓
RERUN ALL AFFECTED GITHUB GATES / TESTS
      ↓
GITHUB PASS
      ↓
BUILD NEW FINAL CANDIDATE
      ↓
FINAL_CANDIDATE_READY
      ↓
FIREBASE MAY RUN AGAIN
```

DILARANG menggunakan Firebase sebagai iterative debugging loop bila failure dapat dianalisis dan diuji ulang melalui GitHub.

Jika failure bersifat target-specific dan tidak dapat direproduksi pada GitHub, Firebase rerun tetap hanya dilakukan setelah perubahan perbaikan telah melewati seluruh affected GitHub proof yang tersedia dan kandidat final baru telah dibentuk.

---

## 11. Change-Impact dan Evidence Reuse

Setiap perubahan wajib menentukan evidence apa yang terdampak.

Evidence Firebase yang tidak terdampak dan masih memenuhi provenance/freshness rule BOLEH digunakan kembali.

Perubahan tidak otomatis membatalkan seluruh evidence Firebase.

Evidence final minimal harus mengikat:

- source revision / commit SHA;
- APK/artifact digest, idealnya SHA-256;
- application/version identity;
- workflow/test run identity;
- Android API;
- model/device class;
- ABI bila tersedia sebagai property environment;
- test configuration/scope;
- timestamp;
- result;
- claim/proof yang didukung.

Evidence dari artifact/revision lain tidak boleh digunakan kembali tanpa change-impact/equivalence proof yang sah sesuai aturan repository.

---

## 12. Feature Development Example

Benar:

```text
Feature A
├─ commit 1 ─┐
├─ commit 2  │
├─ commit 3  ├─ GitHub verification/regression
├─ commit 4  │
└─ commit 5 ─┘
       ↓
FINAL_CANDIDATE_READY
       ↓
Firebase final qualification
```

Dilarang sebagai default:

```text
commit 1 -> Firebase
commit 2 -> Firebase
commit 3 -> Firebase
commit 4 -> Firebase
commit 5 -> Firebase
```

---

## 13. Routing Decision

Agen wajib mengikuti keputusan berikut:

```text
CHANGE
  ↓
CHANGE-IMPACT ANALYSIS
  ↓
IS DEVELOPMENT/INTERMEDIATE?
  ├─ YES
  │    → GITHUB ROUTE
  │
  └─ NO
       ↓
FINAL_CANDIDATE_READY?
  ├─ NO
  │    → GITHUB ROUTE
  │
  └─ YES
       ↓
IS FINAL ANDROID 11 ARM64 EVIDENCE REQUIRED OR INVALIDATED?
  ├─ NO
  │    → REUSE STILL-VALID EVIDENCE
  │
  └─ YES
       → FIREBASE FINAL ROUTE
```

Agen tidak boleh memilih Firebase hanya karena Firebase tersedia.

---

## 14. PASS Semantics

Status berikut tidak setara:

```text
GITHUB_BASIC_RUNTIME_PASS
ANDROID_11_ARM64_FINAL_PASS
APPLICATION_SAFE_100
```

`GITHUB_BASIC_RUNTIME_PASS` hanya membuktikan scope pada environment GitHub yang digunakan.

`ANDROID_11_ARM64_FINAL_PASS` hanya boleh diberikan jika required target-specific evidence telah tersedia untuk kandidat/scope tersebut.

`APPLICATION_SAFE_100` tetap tunduk pada seluruh requirement R1–R9, asset proof bila berlaku, cross-domain closure, evidence completeness, dan seluruh aturan lain yang diwajibkan repository.

Build sukses, GitHub emulator hijau, atau satu Firebase run hijau tidak boleh sendiri menjadi final oracle untuk `APPLICATION_SAFE_100`.

---

## 15. Native / ABI Rule

GitHub x86/x86_64 runtime dapat digunakan untuk behavior development yang architecture-independent.

Namun untuk claim yang material terhadap native/ABI, termasuk:

- JNI;
- `.so` loading;
- native symbol/linkage;
- plugin native;
- ARM64-only dependency;
- alignment/ABI-specific behavior;
- architecture-specific crash;

non-ARM64 runtime DILARANG menjadi pengganti ARM64 proof.

Required ARM64 evidence harus tetap ditutup pada final qualification atau environment authoritative lain yang sah sesuai aturan R7/R9.

---

## 16. Hardware / Vendor / Physical Device Exception

Firebase virtual device tidak otomatis cukup untuk semua claim R8.

Jika claim membutuhkan physical/representative device evidence, misalnya:

- camera/sensor behavior;
- Bluetooth/NFC;
- vendor/OEM behavior;
- GPU/device-specific timing;
- thermal/power behavior;
- hardware interruption yang tidak dapat direproduksi secara sah oleh virtual device;

maka gunakan physical-device atau authoritative lab evidence sebagaimana diwajibkan R8.

Policy `FIREBASE_FINAL_ONLY` tidak boleh digunakan untuk menghapus physical-device requirement yang memang berlaku.

---

## 17. Quota Protection

Default routing:

```text
GITHUB_BASIC_ROUTE = DEFAULT
GITHUB_REGRESSION_ROUTE = DEFAULT
FIREBASE_DEBUG_ROUTE = DISABLED
FIREBASE_INTERMEDIATE_ROUTE = DISABLED
FIREBASE_FINAL_ROUTE = CONDITIONAL
```

Jika beberapa required final scenarios dapat dibatch secara teknis tanpa mengurangi coverage, oracle, isolation, atau diagnosability yang diwajibkan, agen boleh melakukan batching untuk menghemat penggunaan quota.

Agen DILARANG menghapus required scenario hanya agar jumlah Firebase run berkurang.

---

## 18. Tool / Environment Failure

Jika GitHub runner, emulator, Firebase, CLI, network, atau tool gagal:

```text
TOOL_FAILURE != APPLICATION_PASS
TOOL_FAILURE != APPLICATION_FAIL
```

Klasifikasikan penyebab berdasarkan evidence.

Jika proof wajib tidak dapat diselesaikan karena tool/environment failure, status proof tetap `NOT_PROVEN`/`INDETERMINATE_TOOL` sesuai aturan yang berlaku sampai evidence sah tersedia.

Jangan memindahkan test ke Firebase hanya karena GitHub mengalami transient infrastructure failure. Perbaiki/rerun GitHub route terlebih dahulu kecuali final target qualification memang sudah dibuka.

---

## 19. Mandatory Agent Behavior

Ketika pekerjaan melibatkan test, emulator, runtime verification, final candidate, atau Firebase Test Lab, agen wajib:

1. menentukan stage T0/T1/T2/T3;
2. melakukan change-impact analysis;
3. menentukan apakah existing evidence masih valid;
4. menggunakan GitHub sebagai default route selama development;
5. memastikan `FINAL_CANDIDATE_READY` sebelum Firebase test execution;
6. membatasi Firebase pada required final target qualification;
7. menyimpan provenance/evidence final;
8. tidak mengurangi requirement dari aturan repository lain.

---

## 20. Final Rule

```text
DEVELOP OFTEN ON GITHUB.
QUALIFY FINAL CANDIDATES ON FIREBASE.
REUSE VALID EVIDENCE.
DO NOT SPEND FIREBASE QUOTA ON DEBUGGING.
DO NOT CLAIM NON-TARGET TESTS AS TARGET PROOF.
DO NOT REDUCE REQUIRED VERIFICATION TO SAVE QUOTA.
```
