# Firebase Test Lab Bridge — ToolBox

## 1. Status

Bridge ini khusus untuk **Firebase Final Gate** ToolBox.

Target Firebase dikunci keras ke:

```text
Android 11
API 30
ARM64 / arm64-v8a
```

Workflow:

```text
.github/workflows/firebase-test-lab.yml
```

Build dan development/regression testing tetap dilakukan melalui GitHub Actions. Firebase bukan development environment dan bukan fallback ketika GitHub test gagal.

Aturan authorization utama berada pada:

```text
TEST_ROUTING_POLICY.md
```

---

## 2. Prinsip Utama

```text
GITHUB ACTIONS
  -> development/basic/intermediate/regression tests
  -> environment fleksibel
  -> DEVELOPMENT_PASS
  -> STOP
  -> minta persetujuan pengguna
  -> 1 persetujuan = 1 Firebase execution attempt
  -> Firebase target WAJIB API30 + arm64-v8a
  -> setelah attempt izin habis
  -> untuk attempt berikutnya wajib minta izin lagi
```

Tidak ada jalur:

```text
GitHub PASS -> Firebase otomatis
```

Tidak ada auto-retry Firebase.

---

## 3. Konfigurasi Identitas

Bridge memakai:

```text
GitHub OIDC
-> Google Workload Identity Federation
-> dedicated service account
-> Firebase / Google Cloud project
```

Repository Variables:

```text
FIREBASE_PROJECT_ID
GCP_WIF_PROVIDER
GCP_SERVICE_ACCOUNT
```

Tidak ada service-account JSON/private key yang perlu disimpan di repository.

---

## 4. User Authorization Gate

Firebase test execution secara default:

```text
LOCKED
```

Setelah GitHub development verification menghasilkan `DEVELOPMENT_PASS`, agen wajib berhenti dan bertanya kepada pengguna apakah kandidat tersebut diizinkan masuk Final Gate.

Persetujuan yang sah harus eksplisit dan diberikan sebagai jawaban terhadap pertanyaan Final Gate.

Contoh makna pertanyaan:

```text
Apakah Anda mengizinkan 1x eksekusi Firebase Final Gate
untuk kandidat ini pada Android 11 / API 30 / ARM64?
```

Jika pengguna tidak menjawab secara eksplisit atau jawaban ambigu:

```text
FIREBASE_AUTHORIZATION = LOCKED
```

---

## 5. Single-Use Rule

Persetujuan pengguna menghasilkan:

```text
FIREBASE_AUTHORIZATION = AUTHORIZED_ONCE
```

Persetujuan tersebut hanya berlaku untuk:

- satu kandidat yang sedang ditanyakan;
- satu artifact/run yang diidentifikasi;
- satu Firebase test execution attempt.

Setelah satu execution attempt dimulai:

```text
FIREBASE_AUTHORIZATION = CONSUMED
FIREBASE_AUTHORIZATION = LOCKED
```

Ini tetap berlaku jika hasil attempt:

- PASS;
- FAIL;
- timeout;
- cancellation;
- test matrix error;
- infrastructure/execution error setelah attempt dimulai.

Run ke-2 selalu membutuhkan approval ke-2.
Run ke-3 selalu membutuhkan approval ke-3.
Dan seterusnya.

Dilarang reuse approval lama.

---

## 6. Kandidat yang Diizinkan

Sebelum meminta approval, agen wajib memastikan kandidat sudah mencapai `DEVELOPMENT_PASS` sesuai `TEST_ROUTING_POLICY.md`.

Identitas kandidat sejauh tersedia wajib dicatat:

```text
SOURCE_COMMIT_SHA
SOURCE_RUN_ID
ARTIFACT_NAME
APK_SHA256
TEST_SCOPE
```

Jika kandidat berubah material sebelum test attempt dimulai, approval sebelumnya batal dan Firebase kembali `LOCKED`.

---

## 7. Firebase Target Lock

Setiap final test wajib memakai:

```text
ANDROID_VERSION_ID = 30
REQUIRED_ABI = arm64-v8a
```

Model Firebase wajib dibuktikan secara live:

```text
model supports API 30
AND model supports arm64-v8a
AND model is available/live
```

Target Firebase tidak fleksibel.

Dilarang fallback ke:

- API selain 30;
- x86;
- x86_64;
- non-ARM model;
- model yang ABI-nya tidak dapat dibuktikan;
- device lain hanya agar workflow hijau.

Jika model API30 ARM64 tidak tersedia:

```text
FINAL_GATE_TARGET = NOT_AVAILABLE
FINAL_GATE_RESULT = NOT_PROVEN
```

Jangan menjalankan final test pada target alternatif.

---

## 8. Perbedaan GitHub dan Firebase

GitHub test environment bersifat fleksibel.

Contoh yang sah:

```text
GitHub API 35 / x86_64
-> runtime regression PASS
-> DEVELOPMENT_PASS
```

Tetapi hasil tersebut bukan:

```text
ANDROID_11_ARM64_FINAL_PASS
```

Firebase adalah witness target final dan hanya dijalankan jika pengguna memberikan single-use approval.

---

## 9. Mode Workflow

### `connection-only`

Tidak menjalankan APK test dan tidak membuat final test matrix.

Mode ini hanya untuk pemeriksaan bridge/configuration, misalnya:

- OIDC authentication;
- project identity;
- Firebase device catalog read;
- memastikan model API30 ARM64 tersedia.

`connection-only` bukan runtime proof dan bukan Final Gate execution.

Agen tetap tidak boleh menjalankannya berulang tanpa kebutuhan nyata. Namun mode ini tidak boleh diperlakukan sebagai pengganti final test.

### `test-existing-artifact`

Mode ini adalah Firebase Final Gate execution.

Membutuhkan minimal:

```text
source_run_id
artifact_name
explicit single-use user approval
```

Sebelum mode ini dijalankan:

```text
DEVELOPMENT_PASS = TRUE
USER_APPROVAL = EXPLICIT
FIREBASE_AUTHORIZATION = AUTHORIZED_ONCE
```

Workflow mengambil APK dari prior gated GitHub Actions run. Ia tidak membangun APK sendiri.

Artifact wajib:

- berasal dari build GitHub yang sah;
- terikat ke kandidat yang disetujui;
- berisi tepat satu APK;
- diberi SHA-256 sebelum Firebase execution.

---

## 10. Default Test

Default final witness saat ini:

```text
Robo smoke test
Android 11 / API 30
ARM64 / arm64-v8a
portrait
en
5 minutes maximum
```

Robo smoke test hanya membuktikan scope yang benar-benar dijalankan.

Ia tidak otomatis menutup seluruh R1–R9 atau `APPLICATION_SAFE_100`.

---

## 11. Preflight vs Execution Attempt

Workflow boleh melakukan preflight yang diperlukan untuk memastikan target valid sebelum final command dikirim, termasuk membaca katalog model.

Execution attempt dimulai ketika workflow mengirim final Firebase test command/matrix request untuk kandidat tersebut.

Begitu attempt dimulai, approval dianggap consumed.

Jika target validation gagal sebelum final execution dikirim, final target tetap `NOT_PROVEN`; workflow tidak boleh fallback ke target lain.

Jika diperlukan percobaan final test baru setelah execution attempt sudah dimulai, agen wajib meminta approval pengguna baru.

---

## 12. Fail-Closed Rules

Workflow/final gate harus fail-closed jika:

- WIF configuration tidak lengkap;
- authentication Google gagal;
- project identity tidak sesuai;
- katalog Test Lab tidak dapat dibaca;
- model API30 ARM64 tidak tersedia;
- source run ID tidak tersedia;
- artifact tidak ditemukan;
- artifact berisi nol atau lebih dari satu APK;
- approval pengguna tidak ada;
- approval tidak berlaku pada kandidat aktif;
- Firebase execution mengembalikan failure.

Tidak ada fallback diam-diam.

---

## 13. No Auto-Retry

Dilarang:

```text
Firebase FAIL -> Firebase retry otomatis
Firebase timeout -> Firebase retry otomatis
Firebase execution error -> Firebase retry otomatis
```

Alur wajib:

```text
attempt #1
-> approval consumed
-> Firebase LOCKED
-> jika attempt baru diperlukan
-> agen bertanya lagi
-> pengguna approve lagi
-> attempt #2
```

---

## 14. Hubungan dengan APPLICATION_SAFE_100

Bridge ini bukan pengganti `APPLICATION_SAFE_100_PROCESS.md`.

GitHub tetap route development/default sesuai `TEST_ROUTING_POLICY.md`.

Jika suatu final application claim membutuhkan target-specific Android 11 ARM64 runtime evidence, Firebase Final Gate dapat menyediakan evidence tersebut **hanya setelah approval eksplisit pengguna**.

Firebase virtual ARM64 tidak menggantikan physical-device witness untuk klaim hardware/vendor/power yang memang membutuhkan real-device evidence.

---

## 15. Evidence Minimum

Setiap Firebase execution harus mencatat sejauh tersedia:

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
TEST_MATRIX_OR_RUN_ID
TIMESTAMP
RESULT
```

Evidence hanya sah untuk kandidat dan scope tersebut.

---

## 16. Final Rule

```text
GITHUB = FLEXIBLE DEVELOPMENT TESTING

FIREBASE = FINAL TARGET ONLY
FIREBASE TARGET = ANDROID 11 / API 30 / ARM64

FIREBASE DEFAULT = LOCKED
USER MUST APPROVE EXPLICITLY
1 APPROVAL = 1 EXECUTION ATTEMPT
AFTER ATTEMPT = LOCKED AGAIN
EVERY RETRY/SECOND RUN = ASK USER AGAIN

NO AUTO FIREBASE
NO AUTO RETRY
NO FALLBACK TARGET
NO APPROVAL REUSE
```
