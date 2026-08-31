# Firebase Test Lab Bridge — ToolBox

## Status

Bridge CI untuk target resmi ToolBox:

- Android 11 / API 30
- ARM64 / `arm64-v8a`
- Firebase Test Lab
- GitHub Actions sebagai satu-satunya jalur build/test cloud

Workflow:

```text
.github/workflows/firebase-test-lab.yml
```

Workflow dibuat `workflow_dispatch` agar pemasangan bridge tidak otomatis memulai build atau melewati application prebuild boundary.

Pemakaian Firebase untuk eksekusi test WAJIB mengikuti `TEST_ROUTING_POLICY.md`. Firebase bukan route development/debugging; test APK melalui bridge hanya boleh dijalankan setelah kandidat memenuhi `FINAL_CANDIDATE_READY` dan final target evidence memang diwajibkan atau terinvalidasi.

## Prinsip

```text
GITHUB ACTIONS
  -> GitHub OIDC token
  -> Google Workload Identity Federation
  -> Firebase / Google Cloud project
  -> Firebase Test Lab device catalog
  -> pilih live .arm model yang mendukung API 30 + arm64-v8a
  -> connection check
  -> final-candidate only: ambil APK artifact dari prior gated GitHub Actions run
  -> Firebase Test Lab Robo smoke test
  -> result/log/evidence kembali ke GitHub Actions
```

Tidak ada service-account JSON/private key yang disimpan di repository.

## Konfigurasi satu kali

Tiga GitHub Repository Variables diperlukan:

```text
FIREBASE_PROJECT_ID
GCP_WIF_PROVIDER
GCP_SERVICE_ACCOUNT
```

Nilai tersebut bukan password. Authentication dilakukan melalui GitHub OIDC dan Google Workload Identity Federation.

Di sisi Google Cloud/Firebase, satu kali konfigurasi diperlukan:

1. Buat/pilih Firebase project khusus untuk Test Lab.
2. Pastikan Cloud Testing API dan Cloud Tool Results API tersedia untuk project.
3. Buat Workload Identity Pool + GitHub provider.
4. Batasi trust provider ke repository `RMTampu/Tools`.
5. Hubungkan provider ke service account yang dipakai workflow.
6. Berikan IAM yang diperlukan untuk menjalankan Test Lab melalui `gcloud` pada project test.

Untuk penggunaan bucket hasil default yang dibuat Firebase, dokumentasi Test Lab saat ini mensyaratkan principal `gcloud` mempunyai `roles/editor` pada Firebase project. Karena itu gunakan project Firebase khusus Test Lab, bukan project yang menyimpan data produksi.

## Mode Workflow

### `connection-only`

Tidak membutuhkan APK dan tidak menjalankan APK test.

Memverifikasi:

- OIDC -> Google Cloud berhasil;
- project identity benar;
- katalog Firebase Test Lab dapat dibaca;
- terdapat live virtual Arm model dengan Android API 30;
- model tersebut mengiklankan `arm64-v8a`.

Jika tidak dapat dibuktikan, workflow fail-closed.

`connection-only` adalah pemeriksaan bridge/configuration dan bukan pengganti runtime proof atau final qualification.

### `test-existing-artifact`

Membutuhkan:

```text
source_run_id
artifact_name
```

Mode ini hanya boleh digunakan ketika `TEST_ROUTING_POLICY.md` telah membuka `FIREBASE_FINAL_ROUTE`, yaitu kandidat telah memenuhi `FINAL_CANDIDATE_READY` dan final Android 11 ARM64 evidence memang diperlukan atau telah terinvalidasi.

Workflow hanya mengambil artifact dari GitHub Actions run yang sudah ada. Ia tidak membangun APK sendiri dan tidak membuka build boundary.

Artifact wajib:

- berasal dari controlled/gated GitHub Actions build yang sah;
- terikat ke source revision final candidate;
- telah melewati required GitHub development/regression verification;
- berisi tepat satu APK;
- diberi SHA-256 sebelum dikirim ke Firebase Test Lab.

Test default:

```text
Robo smoke test
Android 11 / API 30
ARM64 arm64-v8a
portrait
en
5 minutes maximum
```

Robo smoke test ini hanya merupakan satu witness. Ia tidak dengan sendirinya menutup seluruh runtime, install/upgrade, native/plugin, UI/device/power, cross-domain, atau R1–R9 proof.

## Fail-Closed Rules

Workflow gagal jika:

- konfigurasi WIF belum lengkap;
- authentication Google gagal;
- project berbeda dari konfigurasi;
- katalog Test Lab tidak dapat dibaca;
- tidak ada model `.arm` API 30 + `arm64-v8a` yang dapat dibuktikan;
- source run ID tidak diberikan ketika mode test dipilih;
- artifact tidak ditemukan;
- artifact berisi nol atau lebih dari satu APK;
- Firebase Test Lab mengembalikan kegagalan.

Tidak ada fallback diam-diam ke x86/x86_64, API lain, atau perangkat non-ARM untuk final target qualification.

Jika mode test dipakai pada artifact yang belum memenuhi `FINAL_CANDIDATE_READY`, hasil tersebut tidak boleh dipakai sebagai final evidence meskipun workflow secara teknis dapat dieksekusi.

## Hubungan dengan Application Safe Process

Bridge ini bukan pengganti `APPLICATION_SAFE_100_PROCESS.md`.

Build APK tetap hanya boleh dilakukan setelah seluruh prebuild gate yang berlaku PASS. GitHub Actions tetap menjadi route default untuk development/basic/intermediate/regression verification sesuai `TEST_ROUTING_POLICY.md`.

Bridge Firebase hanya dipakai untuk final target qualification setelah final candidate yang sah tersedia dan target-specific evidence diperlukan. Firebase Test Lab virtual Arm evidence tidak menggantikan physical-device witness untuk klaim yang memang membutuhkan hardware/vendor/power behavior nyata.

Evidence Firebase harus tetap terikat ke revision/artifact/test configuration yang aktif dan tunduk pada change-impact, freshness, dan evidence-reuse rules repository.
