# Peta Pemakaian Markdown — Public Research / Test / Staging

## 1. Status dan batas

Peta ini AKTIF dan wajib digunakan melalui [AGENTS.md](AGENTS.md) untuk menentukan MD yang harus dibaca dan diterapkan pada pekerjaan `RMTampu/Tools`.

Terdaftar **29 MD**, termasuk peta ini, berdasarkan 28 MD pada snapshot penyusunan [eb5ec83](https://github.com/RMTampu/Tools/commit/eb5ec83d9590254b46bf49324259aa515cfaa6e3). Cocokkan kembali register dengan tree exact commit pekerjaan bila repository berubah.

Peta hanya memuat dokumen Public. Ia bukan salinan rancangan/state/baseline Private, bukan blueprint produk baru, dan bukan izin mengakses Private.

Boundary tetap mengikuti Rule 0: Public untuk research, komponen Public, dummy/mock/simulator mandiri, test dan package sampai `READY_PRIVATE`. Tidak boleh menerima salinan/ekstraksi/penyamaran isi Private. Semua akses, pengecekan, dan pengujian Firebase/Test Lab dilarang di Public, termasuk dummy/prototype.

Tidak semua MD wajib dibaca pada setiap tugas. Baca sumber awal selalu, lalu seluruh sumber domain yang berlaku sebelum pekerjaan bergantung padanya. Dokumen kompatibilitas/sejarah tetap punya kegunaan tanpa menjadi prosedur eksekusi lama.

## 2. Urutan baca awal

1. Baca [AGENTS.md](AGENTS.md), lalu [GLOBAL_PUBLIC_PRIVATE_DEVELOPMENT_RULES.md](GLOBAL_PUBLIC_PRIVATE_DEVELOPMENT_RULES.md) sesuai Rule 0. Baca AGENTS yang lebih spesifik bila ada pada jalur pekerjaan dan tidak bertentangan.
2. Baca [peta pemakaian MD ini](PETA-PEMAKAIAN-MD.md).
3. Baca [REPOSITORY_INTEGRATION_POLICY.md](REPOSITORY_INTEGRATION_POLICY.md), [TEST_ROUTING_POLICY.md](TEST_ROUTING_POLICY.md), dan [AGENT_PROCEDURE_EXECUTION_RULES.md](AGENT_PROCEDURE_EXECUTION_RULES.md). Tetapkan scope Public, izin tindakan, dependency prosedur, dan evidence.
4. Pilih jalur §3. Untuk komponen, baca README/contract milik komponen dan rujukan requirement yang berlaku pada exact commit pekerjaan.
5. Baca [APPLICATION_SAFE_100_PROCESS.md](APPLICATION_SAFE_100_PROCESS.md), tentukan domain R1–R9 yang berlaku, lalu baca **dan terapkan** metode relevan pada §4.2 sebelum desain/implementasi/test yang bergantung padanya. Jika menyentuh asset, tambahkan §4.3. Matriks komponen tidak menggantikan sumber metode.
6. Jalankan hanya unit yang diizinkan dengan seluruh sumber wajib tersedia. Verifikasi requirement terdampak dan catat penerapan/evidence/status sesuai §5.

Urutan baca tidak memerintahkan seluruh test/build dijalankan pada tugas dokumentasi. Sumber yang belum dibaca bukan `NOT_APPLICABLE`. Detail yang tidak tersedia harus dibaca kembali, bukan ditebak dari ringkasan.

## 3. Pemilihan dokumen menurut pekerjaan

Sumber tambahan berikut dibaca setelah §2; rujukan wajib dari sumber harus tetap diikuti.

| Pekerjaan | Urutan sumber tambahan | Pemakaian dan batas hasil |
|---|---|---|
| Orientasi/peran repository | README root → aturan awal → README komponen bila relevan | Bedakan Public component dari final product. Ringkasan tidak memberi izin tambahan. |
| Audit/perbaikan MD atau tautan | File terkait → dokumen pemakai/rujukan → R1/R5/R9 dan domain teknis terdampak | Periksa kejelasan requirement, cakupan, otoritas, tautan dan boundary. Editorial tidak otomatis membatalkan proof atau memicu test runtime. |
| Research/spec/contract komponen | README komponen → CONTRACT → APPLICATION → R1–R9 relevan → matriks assurance komponen | Turunkan metode menjadi requirement, invariant, failure case, oracle, dan acceptance sebelum implementasi. |
| Implementasi/perbaikan runtime-contracts | README komponen → CONTRACT → ASSURANCE_R1_R9 → sumber R1–R9 yang dialokasikan → source/test/script relevan | Gunakan scope metadata-only yang didefinisikan contract; klasifikasi domain diperiksa ulang jika scope berubah, bukan menyalin N/A untuk komponen lain. |
| Public test/simulator/packaging | Contract + matriks assurance → APPLICATION/metode terkait → workflow/script yang aktif | Ikuti gate Public dan evidence exact revision. `READY_PRIVATE` hanya bila syaratnya terbukti, bukan karena peta lengkap atau dokumen menyebut PASS. |
| Asset/resource serta reference/route | PREBUILD_ASSET_GATE → ASSET_SAFE rules → methods → process → route methods/process bila berlaku | Ikuti Gate 0–9/S0–S9 dan sub-gate 4.0–4.13 sesuai sumber. Klaim dibatasi scope Public; tidak ada Firebase atau final execution Private. |
| Failure yang disanitasi dan handoff | Laporan sanitized → CONTRACT dan requirement handoff → R1–R9 terdampak → test/package/evidence baru | Perbaiki requirement/komponen Public tanpa meminta input/state internal Private. Paket siap promosi tetap memerlukan Private preflight sendiri. |
| Audit routing lama atau usulan Firebase | AGENTS_LEGACY_RULES → TEST_ROUTING_POLICY → FIREBASE_TEST_LAB_BRIDGE → aturan global | Tentukan larangan, bukan jalur menjalankan Firebase. Riset API terbuka/mock tanpa koneksi boleh; approval Private bukan pengecualian Public. |

Untuk komponen `public.runtime-contracts`, jalur konkret adalah:

1. [README komponen](public-components/runtime-contracts/README.md): tujuan, scope, identity, acceptance.
2. [CONTRACT.md](public-components/runtime-contracts/CONTRACT.md): metadata, validation/publication, failure, batas eksekusi, dan handoff.
3. [ASSURANCE_R1_R9.md](public-components/runtime-contracts/ASSURANCE_R1_R9.md) **bersama** sumber metode §4.2: alokasi applicability dan evidence.
4. [Laporan sanitized 3 September 2026](public-components/runtime-contracts/SANITIZED_PRIVATE_FAILURE_2026-09-03.md) dan [PRIVATE_INTEGRATION_REQUIREMENTS.json](public-components/runtime-contracts/PRIVATE_INTEGRATION_REQUIREMENTS.json) bila pekerjaan terkait handoff/perbaikan failure tersebut.
5. [Workflow Public](.github/workflows/runtime-contracts-ci.yml) beserta script yang dirujuk, hanya ketika memilih/meninjau/menjalankan pemeriksaan Public yang masuk izin tugas.

Urutan kematangan tetap `SPEC → CONTRACT → DEPENDENCY → UNIT_TEST → SIMULATOR → FAILURE_TEST → PACKAGE_VALIDATION → READY_PRIVATE`. Urutan sumber di atas bukan gate baru dan tidak mengganti requirement proses.

## 4. Register seluruh MD

### 4.1 Aturan awal, navigasi, dan kompatibilitas

| Dokumen | Peran | Kapan dan bagaimana dipakai |
|---|---|---|
| [AGENTS.md](AGENTS.md) | Aturan masuk aktif | Setiap tugas: tetapkan kewajiban dan larangan Public sebelum membuka pekerjaan. |
| [GLOBAL_PUBLIC_PRIVATE_DEVELOPMENT_RULES.md](GLOBAL_PUBLIC_PRIVATE_DEVELOPMENT_RULES.md) | Rule 0 / aturan induk aktif | Setelah AGENTS: tentukan isolasi project, boundary data, promosi, failure, dan larangan Firebase Public. |
| [PETA-PEMAKAIAN-MD.md](PETA-PEMAKAIAN-MD.md) | Navigasi pemakaian aktif | Setiap tugas: pilih sumber wajib dan pemakai tiap MD; pertahankan inventaris saat dokumen berubah. |
| [README.md](README.md) | Orientasi repository | Onboarding atau perubahan role: cocokkan penjelasan Public dengan aturan aktif. |
| [AGENT_PROCEDURE_EXECUTION_RULES.md](AGENT_PROCEDURE_EXECUTION_RULES.md) | Prosedur/evidence aktif | Pekerjaan yang dikendalikan aturan: sumber lengkap, unit bertahap, anti-skip, status, dan impact analysis. |
| [REPOSITORY_INTEGRATION_POLICY.md](REPOSITORY_INTEGRATION_POLICY.md) | Integrasi dan isolasi aktif | Menentukan boundary/promosi: siapkan package aman dan metadata yang dibutuhkan, tanpa mengambil state Private. |
| [TEST_ROUTING_POLICY.md](TEST_ROUTING_POLICY.md) | Routing test Public aktif | Sebelum memilih test/environment/claim: pastikan scope Public, dummy mandiri, dan tidak memakai Firebase. |
| [AGENTS_LEGACY_RULES.md](AGENTS_LEGACY_RULES.md) | Indeks kompatibilitas legacy | Saat ada referensi lama atau perubahan routing: pertahankan metode yang sah, tolak routing lama yang `OBSOLETE/FORBIDDEN`; bukan gate tambahan setiap tugas. |

### 4.2 Research dan assurance aplikasi/komponen

R1–R9 bukan dokumen pasif. Pemilihan applicability harus didasarkan pada contract/source pekerjaan, bukan pada kenyamanan agen atau hasil kernel/komponen lain.

| Dokumen | Domain | Kapan dan bagaimana dipakai |
|---|---|---|
| [APPLICATION_SAFE_100_PROCESS.md](APPLICATION_SAFE_100_PROCESS.md) | Orkestrator assurance Public | Sebelum pekerjaan komponen: pilih domain/metode/evidence, jalur Public, dan batas klaim hingga `READY_PRIVATE`. |
| [APP_SAFE_R1_LOGIC_INPUT.md](APP_SAFE_R1_LOGIC_INPUT.md) | Logic/input/exception | Requirement, validation, parsing, typed contract, dan failure: turunkan semantik, batas, oracle, serta positive/negative case. |
| [APP_SAFE_R2_CONCURRENCY_RESOURCE.md](APP_SAFE_R2_CONCURRENCY_RESOURCE.md) | Concurrency/resource | Registry/shared state, capacity, queue atau resource: tutup ownership, interleaving, budget, dan failure sesuai scope. |
| [APP_SAFE_R3_LIFECYCLE_STATE_RECOVERY.md](APP_SAFE_R3_LIFECYCLE_STATE_RECOVERY.md) | Lifecycle/recovery | Komponen yang mempunyai lifecycle/state/recovery: model transisi dan fault; N/A hanya dengan bukti domain tidak ada. |
| [APP_SAFE_R4_PERSISTENCE_STORAGE_VERSION.md](APP_SAFE_R4_PERSISTENCE_STORAGE_VERSION.md) | Persistence/storage/version | Komponen dengan durable state/migration/backup: tetapkan atomicity dan compatibility; metadata in-memory tidak membuktikan storage Private. |
| [APP_SAFE_R5_SECURITY_NETWORK_BOUNDARY.md](APP_SAFE_R5_SECURITY_NETWORK_BOUNDARY.md) | Security/boundary | Input tidak tepercaya, permission, data/otorisasi/network/promosi: validasi dan batasi authority serta kebocoran. |
| [APP_SAFE_R6_BUILD_DEPENDENCY_INSTALL.md](APP_SAFE_R6_BUILD_DEPENDENCY_INSTALL.md) | Build/dependency/package | Package/toolchain/dependency/provenance Public: gunakan metode relevan; klausul APK/sign/install final tidak memberi izin final Private. |
| [APP_SAFE_R7_NATIVE_PLUGIN_RUNTIME.md](APP_SAFE_R7_NATIVE_PLUGIN_RUNTIME.md) | Native/plugin/loader/runtime | Bila domainnya ada pada komponen Public: tutup ABI/interface/load/failure; jangan mengimpor implementasi Private. |
| [APP_SAFE_R8_UI_DEVICE_POWER.md](APP_SAFE_R8_UI_DEVICE_POWER.md) | UI/device/power | Bila komponen Public punya UI/renderer/WebView/hardware/power: tentukan witness yang sah; simulator bukan bukti final target. |
| [APP_SAFE_R9_VERIFICATION_COMPLETENESS.md](APP_SAFE_R9_VERIFICATION_COMPLETENESS.md) | Kelengkapan verifikasi | Review/closure: ikat requirement–metode–test–evidence, periksa scope/N/A/gap/freshness dan batas klaim Public. |

### 4.3 Research dan assurance asset Public

| Dokumen | Peran | Kapan dan bagaimana dipakai |
|---|---|---|
| [PREBUILD_ASSET_GATE.md](PREBUILD_ASSET_GATE.md) | Pengatur Gate 0–9 Public | Sebelum menyentuh asset: pahami prasyarat serta urutan build/package/runtime/acceptance pada scope Public. |
| [ASSET_SAFE_100_RULES.md](ASSET_SAFE_100_RULES.md) | Requirement/invariant asset | Asset Public dibuat/diubah/divalidasi: gunakan universe, contract, type/semantic/consumer/budget dan proof yang relevan. |
| [ASSET_SAFE_100_METHODS.md](ASSET_SAFE_100_METHODS.md) | Metode riset asset | Terapkan metode traceability, provenance, transformasi, oracle, dan verifier melalui process yang berlaku. |
| [ASSET_SAFE_100_PROCESS.md](ASSET_SAFE_100_PROCESS.md) | Pelaksanaan S0–S9 | Laksanakan metode dalam Gate 0–9 yang sama; arti final/package selalu tunduk pada scope Public. |
| [ASSET_ROUTE_PROOF_METHODS.md](ASSET_ROUTE_PROOF_METHODS.md) | Metode route/reference | Bila ada route/binding/consumer/resolution: definisikan domain, graph, authority, oracle dan challenge. |
| [ASSET_ROUTE_PROOF_PROCESS.md](ASSET_ROUTE_PROOF_PROCESS.md) | Sub-gate route 4.0–4.13 | Sebelum Gate 4 yang berlaku: jalankan urutan proof lengkap; tidak mengganti sumber metode dengan ringkasan. |

### 4.4 Boundary Firebase dan dokumen komponen

| Dokumen | Peran | Kapan dan bagaimana dipakai |
|---|---|---|
| [FIREBASE_TEST_LAB_BRIDGE.md](FIREBASE_TEST_LAB_BRIDGE.md) | Dokumentasi larangan/boundary aktif | Saat ada usulan akses Firebase, workflow/bridge, atau rujukan legacy: menegaskan Public bukan executor/caller/relay; bukan petunjuk menjalankan Firebase di Public. |
| [public-components/runtime-contracts/README.md](public-components/runtime-contracts/README.md) | Orientasi komponen aktif | Sebelum bekerja pada runtime-contracts: baca tujuan, identity, scope dan acceptance; status tertulis bukan bukti run terbaru. |
| [public-components/runtime-contracts/CONTRACT.md](public-components/runtime-contracts/CONTRACT.md) | Kontrak komponen aktif | Desain/implementasi/test/handoff komponen: gunakan input, batas resource, atomic publication, lookup, failure, dan authority yang disepakati. |
| [public-components/runtime-contracts/ASSURANCE_R1_R9.md](public-components/runtime-contracts/ASSURANCE_R1_R9.md) | Alokasi metode/evidence aktif | Bersama sumber R1–R9 sebelum implementasi/test/closure: buktikan applicability/N/A dan traceability; jangan menyalin pengecualian scope ke komponen lain. |
| [public-components/runtime-contracts/SANITIZED_PRIVATE_FAILURE_2026-09-03.md](public-components/runtime-contracts/SANITIZED_PRIVATE_FAILURE_2026-09-03.md) | Catatan failure + requirement perbaikan aman | Saat memperbaiki/menguji handoff dependency trust terkait: telusuri sebab generik, contract dan evidence koreksi; tidak meminta/menyimpan input internal Private. Setelah ditutup, tetap menjadi provenance regresi, bukan izin baru. |

## 5. Bukti pembacaan dan penerapan

Gunakan catatan/laporan atau manifest/evidence yang sudah sesuai tugas. Catat minimal:

| Catatan | Isi yang diperlukan |
|---|---|
| Identitas | Project, repository Public, exact commit/ref, komponen/unit, scope, izin tindakan. |
| Sumber | Path, bagian, revision/blob yang dibaca, rujukan wajib, dan aturan otoritatif. |
| Applicability | Metode/requirement yang berlaku dan alasan contract/source untuk yang tidak berlaku. |
| Penerapan | Requirement/metode → keputusan/tindakan/perubahan → lokasi hasil. |
| Verifikasi | Pemeriksaan aktual, evidence/run/hash bila berlaku, hasil, batas environment/claim. |
| Status lanjut | Selesai/pending/gagal/invalidated dan dependency berikutnya; yang belum terbukti tidak diberi PASS. |

`READ` saja tidak sama dengan `APPLIED`/`VERIFIED`. Evidence test/run lama tidak dipromosikan ke revision/claim lain tanpa impact/equivalence yang sah. Catatan dokumen atau peta lengkap tidak menghasilkan `READY_PRIVATE`.

Tugas baca-saja tidak mengubah repo hanya untuk mencatat pembacaan. Dokumen yang tidak terkait tugas tidak dipaksa menjadi gate baru. Editorial/navigasi tanpa dampak teknis tidak otomatis membatalkan proof/test/build yang sah, sesuai [prosedur §10.1](AGENT_PROCEDURE_EXECUTION_RULES.md#101-perubahan-dokumentasi-tidak-otomatis-membatalkan-proof).

## 6. Pemeliharaan dan pemeriksaan cakupan

1. Ambil inventaris semua path `.md` secara case-insensitive dari tree exact commit, termasuk subfolder; jangan mengandalkan indeks pencarian saja.
2. Setiap MD baru/berpindah/berubah fungsi harus memperbarui register, jalur baca, dan rujukan pemakainya dalam perubahan yang sama.
3. Periksa tautan, filename, rujukan grup, dan consumer script/workflow. File tanpa incoming link MD belum tentu tidak digunakan oleh CI atau oleh aturan kelompok.
4. Bedakan aturan/metode aktif, contract, navigasi, kompatibilitas, dan evidence/history. Jangan menghapus/menggabungkan/reactivate dokumen lama hanya karena sulit ditemukan.
5. Bila sumber yang diwajibkan hilang atau belum dibaca, hentikan unit/claim yang bergantung padanya; jangan menebak isinya atau menghapus requirement.
6. Peta tidak mengalahkan aturan sumber. Kalau tidak selaras, ikuti otoritas dan koreksi navigasi dalam izin tugas tanpa mendesain ulang komponen.
7. Jangan menyalin dokumen, inventaris internal, source, state, artifact, atau detail sensitif Private ke peta Public. Dokumen dengan nama sama pada dua repo tetap tunduk pada role masing-masing.

Target pemeriksaan adalah seluruh path punya peran, kondisi penggunaan, dan pemakai yang jelas. Ini adalah kewajiban kerja agen yang diperiksa dari evidence, bukan klaim bahwa CI sudah otomatis memverifikasi penggunaan setiap MD.

## 7. Catatan audit penggunaan — 3 September 2026

- Seluruh 28 MD pada snapshot penyusunan dibaca; register menjadi 29 dengan peta ini. Revisi CONTRACT dan laporan sanitized yang masuk selama pemeriksaan turut dicakup.
- Sebelumnya AGENTS menyebut domain R1–R9 secara kelompok; tidak adanya tautan filename satu per satu bukan bukti korpus tidak dipakai. [assurance_prebuild.py](public-components/runtime-contracts/scripts/assurance_prebuild.py) sudah membaca dokumen R1–R9, CONTRACT dan matriks assurance; [package_validate.py](public-components/runtime-contracts/scripts/package_validate.py) menggunakan CONTRACT/matriks dalam paket sumber. Pemeriksaan ini baca-saja, bukan menjalankan script.
- Jalur manusia/agen dari AGENTS ke dokumen komponen, indeks legacy, dan boundary Firebase sekarang dituliskan eksplisit. README komponen juga merujuk contract, assurance, serta laporan handoff yang relevan.
- Korpus metode dengan nama mirip tidak digabung: rules, methods, process dan gate memiliki tanggung jawab berbeda. Dokumen boundary bernama bridge tetap dipakai untuk menolak Firebase Public, bukan dihapus atau dijadikan executor.
- Audit ini tidak menjalankan CI, build/test komponen, Firebase, promosi, atau cleanup; tidak menetapkan `READY_PRIVATE` atau final application PASS baru.
