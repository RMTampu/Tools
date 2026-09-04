# R1-R9 Asset Chain

Dokumen ini adalah rantai wajib untuk setiap pekerjaan yang menyentuh asset, source asset, package, build artifact, registry binding, workflow build/test, atau file yang menentukan perilaku asset.

Aturan ini berlaku global di repo ini. Jika perintah user mengandung kata kerja seperti kerjakan, lanjut, lanjutkan, perbaiki, bangun, matangkan, uji, validasi, buat, atau sentuh asset, agen harus menjalankan rantai ini sebagai aksi sampai PASS, artifact siap, atau blocker nyata.

## Rantai Wajib

| Urutan | Area | MD / File Acuan | Kewajiban |
| --- | --- | --- | --- |
| 0 | Perintah kerja | `AGENTS.md`, `TASK.md`, `AGENT_PROCEDURE_EXECUTION_RULES.md` | Pastikan perintah diperlakukan sebagai pekerjaan penuh, bukan tanya jawab atau laporan progres. |
| 1 | Asset safe baseline | `ASSET_SAFE_100_RULES.md`, `ASSET_SAFE_100_PROCESS.md`, `ASSET_SAFE_100_METHODS.md` | Terapkan standar keamanan asset sebelum mengubah, membuat, atau mempromosikan asset. |
| 2 | Route proof | `ASSET_ROUTE_PROOF_PROCESS.md`, `ASSET_ROUTE_PROOF_METHODS.md` | Buktikan route asset dari source sampai konsumsi runtime/build. |
| 3 | R1-R9 app safety | `APP_SAFE_R1_LOGIC_INPUT.md` sampai `APP_SAFE_R9_VERIFICATION_COMPLETENESS.md` | Jalankan seluruh riset R1-R9 sesuai area risiko asset yang disentuh. Tidak boleh memilih sebagian tanpa alasan teknis yang tertulis di evidence. |
| 4 | Gate sebelum build | `PREBUILD_ASSET_GATE.md`, `APPLICATION_SAFE_100_PROCESS.md`, `TEST_ROUTING_POLICY.md` | Pastikan asset lulus gate sebelum package/build dianggap layak. |
| 5 | Boundary publik/private | `GLOBAL_PUBLIC_PRIVATE_DEVELOPMENT_RULES.md` | Pastikan pematangan dilakukan di repo publik yang diarahkan, sedangkan build signing/final private hanya memakai artifact siap. |
| 6 | Assurance lokal komponen | `public-components/**/ASSURANCE_R1_R9.md`, `public-components/**/ASSURANCE_PLAN_R1_R9.json` | Gunakan rencana R1-R9 milik komponen yang disentuh. Jika belum ada, buat minimum plan sebelum menyatakan selesai. |
| 7 | Validator dan run | `public-components/**/scripts/assurance_*`, `mutation_test.py`, `package_validate.py`, workflow CI terkait | Jalankan validator yang relevan, pantau run sampai selesai, perbaiki error otomatis, lalu ulangi sampai PASS atau blocker nyata. |

## Daftar MD R1-R9

- `APP_SAFE_R1_LOGIC_INPUT.md`
- `APP_SAFE_R2_CONCURRENCY_RESOURCE.md`
- `APP_SAFE_R3_LIFECYCLE_STATE_RECOVERY.md`
- `APP_SAFE_R4_PERSISTENCE_STORAGE_VERSION.md`
- `APP_SAFE_R5_SECURITY_NETWORK_BOUNDARY.md`
- `APP_SAFE_R6_BUILD_DEPENDENCY_INSTALL.md`
- `APP_SAFE_R7_NATIVE_PLUGIN_RUNTIME.md`
- `APP_SAFE_R8_UI_DEVICE_POWER.md`
- `APP_SAFE_R9_VERIFICATION_COMPLETENESS.md`

## File Terkait Yang Ditemukan

- `AGENT_PROCEDURE_EXECUTION_RULES.md`
- `APPLICATION_SAFE_100_PROCESS.md`
- `APP_SAFE_R1_LOGIC_INPUT.md`
- `APP_SAFE_R2_CONCURRENCY_RESOURCE.md`
- `APP_SAFE_R3_LIFECYCLE_STATE_RECOVERY.md`
- `APP_SAFE_R4_PERSISTENCE_STORAGE_VERSION.md`
- `APP_SAFE_R5_SECURITY_NETWORK_BOUNDARY.md`
- `APP_SAFE_R6_BUILD_DEPENDENCY_INSTALL.md`
- `APP_SAFE_R7_NATIVE_PLUGIN_RUNTIME.md`
- `APP_SAFE_R8_UI_DEVICE_POWER.md`
- `APP_SAFE_R9_VERIFICATION_COMPLETENESS.md`
- `ASSET_ROUTE_PROOF_METHODS.md`
- `ASSET_ROUTE_PROOF_PROCESS.md`
- `ASSET_SAFE_100_METHODS.md`
- `ASSET_SAFE_100_PROCESS.md`
- `ASSET_SAFE_100_RULES.md`
- `GLOBAL_PUBLIC_PRIVATE_DEVELOPMENT_RULES.md`
- `PREBUILD_ASSET_GATE.md`
- `R1_R9_ASSET_CHAIN.md`
- `TASK.md`
- `TEST_ROUTING_POLICY.md`
- `public-components/runtime-contracts/ASSURANCE_R1_R9.md`
- `public-components/runtime-contracts/R6_PRIVATE_HANDOFF_ASSURANCE_ADDENDUM.md`
- `public-components/runtime-safety-contracts/ASSURANCE_PLAN_R1_R9.json`
- `public-components/stage-a-android-host/ASSURANCE_PLAN_R1_R9.json`
- `public-components/stage-a-foundation/ASSURANCE_PLAN_R1_R9.json`

## Protokol Saat Menyentuh Asset

1. Baca `AGENTS.md`, lalu dokumen ini, lalu MD pada urutan rantai yang relevan.
2. Tentukan asset yang disentuh dan map ke R1-R9. Defaultnya semua R1-R9 dianggap berlaku sampai terbukti tidak relevan.
3. Kerjakan perubahan nyata pada source/asset/test/build/workflow yang diperlukan.
4. Jalankan validator komponen dan workflow terkait. Mengirim workflow tanpa memantau hasil bukan PASS.
5. Jika ada error, bug, compile failure, route failure, mutation failure, atau package validation failure, perbaiki otomatis dan ulangi run.
6. Berhenti hanya ketika PASS lengkap, artifact siap ke tahap private signing/final test, atau ada blocker nyata di luar akses agen.
7. Laporan akhir wajib membawa evidence: file berubah, test/run yang PASS, artifact/digest bila ada, atau blocker spesifik.

## Larangan

- Dilarang menyentuh asset hanya berdasarkan ingatan tanpa membaca rantai ini.
- Dilarang mengganti pekerjaan asset dengan checkpoint, status file, rencana, atau laporan parsial.
- Dilarang menyatakan siap private jika R1-R9, route proof, package validation, atau workflow terkait belum PASS.
- Dilarang berhenti setelah trigger workflow; agen wajib memantau log aktif sampai hasil final.

## Exit Gate

Asset dianggap selesai hanya jika:

- seluruh R1-R9 yang relevan sudah diterapkan dan dibuktikan;
- route source ke package/build/runtime terbukti;
- validator lokal dan workflow terkait PASS;
- package/artifact siap untuk build signing dan final private test; dan
- tidak ada manual fix tersisa.
