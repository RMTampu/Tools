# APP_SAFE_R6_BUILD_DEPENDENCY_INSTALL.md

## 1. Status

Korpus metode aktif untuk **R6 — Build, Dependency, Manifest, Shrinking & Installation Safety** dalam framework `APPLICATION_SAFE_100`.

Status riset: `PRACTICAL_SATURATION` terhadap ruang metode yang telah disapu.

Test environment dan Firebase authorization selalu mengikuti `TEST_ROUTING_POLICY.md`.

**Batas Firebase wajib:** seluruh akses/pengecekan/eksekusi Firebase/Test Lab hanya dari Private. Public tidak boleh memakainya, termasuk untuk dummy/prototype. Penyebutan Firebase dan approval di dokumen ini adalah requirement final Private, bukan izin Public; pengujian komponen Public memakai mock/simulator mandiri tanpa koneksi Firebase sampai `COMPONENT_READY_PRIVATE` dan ditahan sampai closure tahap utuh menurut aturan global §6. Kesiapan komponen tidak memberi izin promosi/eksekusi Private.

---

## 2. Scope

R6 menutup:

- build-input and toolchain integrity;
- dependency resolution and compatibility;
- manifest merging and final manifest semantics;
- code/resource shrinking interaction;
- reflection/dynamic reachability handoff to R7;
- signing identity and APK verification;
- package/variant/ABI/sdk contract;
- installation/update compatibility;
- reproducibility/provenance;
- CI artifact-to-source binding.

Asset package proof tetap mengikuti `ASSET_SAFE_100`; native runtime semantics dimiliki R7.

Target produk tetap Android 11 / API 30 / ARM64. Penyebutan target tersebut tidak berarti seluruh install/runtime test GitHub wajib berjalan pada environment identik. GitHub development testing bersifat fleksibel dalam batas Public/Private; target-specific Firebase test hanya boleh dijalankan dari Private setelah single-use approval pengguna.

---

## 3. Metode Aktif

### R6-M01 — Closed Build-Input Universe
Inventaris seluruh source, generated source, asset/resource, Gradle file, plugin, compiler, JDK, Android Gradle Plugin, SDK/Build Tools, NDK bila ada, environment variable yang memengaruhi output, dependency repository, signing config, keep rule, manifest fragment, and CI input.

### R6-M02 — Toolchain / Version Pinning
Build final harus memakai toolchain versions yang eksplisit dan kompatibel. Floating toolchain/dynamic version yang dapat mengubah artifact tanpa perubahan source dilarang untuk jalur final.

### R6-M03 — Dependency Locking & Transitive Closure
Seluruh resolved direct + transitive dependency harus mempunyai exact version/identity. Lock state mismatch, extra unexpected dependency, or missing locked dependency = fail-closed.

### R6-M04 — Dependency Integrity Verification
Gunakan checksum/signature/verification metadata atau mekanisme setara untuk membuktikan binary/source dependency yang diambil sama dengan yang diharapkan.

### R6-M05 — Dependency Compatibility Contract
Setiap dependency wajib kompatibel dengan target Android 11/API30, JVM/bytecode, min/target SDK, arm64/native needs, public API contract, R8 behavior, and required runtime. Upgrade dependency invalidates affected proof.

Compatibility contract boleh dibuktikan secara struktural/static pada GitHub bila property tersebut tidak membutuhkan target runtime. Runtime target-specific proof mengikuti `TEST_ROUTING_POLICY.md`.

### R6-M06 — Hermeticity / External Influence Closure
Final build harus mengurangi atau menutup undeclared network/time/user-home/global cache/input influence. Bila hermetic full tidak feasible, seluruh external influence yang dapat mengubah output harus dideklarasikan dan diverifikasi.

### R6-M07 — Reproducible / Deterministic Build Comparison
Untuk build yang diklaim reproducible, rebuild dari input identik dan bandingkan bitwise output. Jika format/signing memasukkan expected nondeterminism, normalize only fields yang contract membuktikan non-semantic lalu compare semantic artifact model.

### R6-M08 — Build Provenance / Artifact Attestation
Simpan source revision, builder/workflow identity, resolved materials, toolchain, parameters, timestamps, and artifact digests sehingga final APK dapat ditelusuri ke build invocation yang sah.

### R6-M09 — Clean-Build / Cache-Contamination Challenge
Bandingkan clean environment dengan cached/incremental build. Output semantic tidak boleh bergantung pada stale generated file, local untracked artifact, atau cache yang tidak menjadi declared input.

### R6-M10 — Variant / Flavor / Build-Type Matrix Closure
Inventaris seluruh variant yang didukung. Manifest, resources, dependencies, BuildConfig, proguard rules, native libs, signing, and application ID untuk variant final harus sesuai contract.

### R6-M11 — Manifest Merge Semantic Oracle
Periksa **final merged manifest**, bukan hanya source fragments. Validate component/exported status, permission, uses-feature, provider authority, intent filter, application flags, min/target SDK, network config, metadata, service/receiver attributes, and unintended override/merge marker.

### R6-M12 — Static Build/Manifest Lint & Policy Gate
Lint/build analysis wajib fail pada class finding yang ditetapkan critical oleh contract. Suppression harus spesifik, terdokumentasi, dan tidak boleh menjadi blanket bypass.

### R6-M13 — R8/Code-Shrinking Reachability Closure
Untuk release shrink/minify, buktikan code yang dipanggil secara direct, reflection, serialization, JNI, manifest, XML/onClick-like reference, dependency injection, plugin entry, and generated adapter tidak terhapus/diubah secara salah.

### R6-M14 — Keep-Rule Minimality & Mutation Verification
Keep rule harus cukup untuk preserve required behavior tetapi tidak sekadar `-keep **` untuk menyembunyikan reachability problem. Mutation remove/narrow keep rules and release runtime tests harus menangkap missing code.

Runtime mutation tests di GitHub boleh menggunakan environment yang sesuai/tersedia selama claim dibatasi pada environment tersebut. Target-specific final witness tidak boleh dipalsukan oleh environment non-target.

### R6-M15 — Obfuscation Mapping / Retrace Evidence
Simpan mapping/retrace metadata yang tepat untuk artifact final. Crash evidence final harus dapat dikembalikan ke source revision yang sesuai.

### R6-M16 — Final APK Structural / Semantic Verification
Periksa APK final: package/applicationId, manifest, sdk levels, classes/dex, native ABI set, resource/asset presence, compression/alignment where relevant, versionCode/versionName, signing certificates, and forbidden unexpected payload.

Verifikasi bahwa artifact release tetap menargetkan Android 11 / API30 / ARM64. Ini adalah package/target verification, bukan perintah agar semua runtime test GitHub memakai environment identik.

### R6-M17 — Signing Identity / Signature Verification
Final artifact harus diverifikasi dengan `apksigner` atau equivalent terhadap supported platform range. Certificate identity/lineage harus sesuai release contract. Modifikasi APK setelah signing dilarang.

### R6-M18 — Install / Upgrade / Reinstall Matrix
Uji install clean, upgrade dari seluruh supported installed versions, reinstall same version where relevant, data preservation, signature continuity, versionCode rules, failure behavior, and package manager acceptance.

Routing environment:

```text
DEVELOPMENT / REGRESSION INSTALL TESTS
-> GitHub Actions
-> environment Android yang tersedia dan relevan
-> API/ABI boleh fleksibel sesuai TEST_ROUTING_POLICY.md

FINAL TARGET-SPECIFIC INSTALL WITNESS
-> Firebase Final Gate di Private
-> Android 11 / API 30 / ARM64 only
-> hanya setelah explicit single-use user approval
```

Jika GitHub tidak menyediakan Android 11 ARM64, development install matrix tidak boleh dihentikan hanya karena environment identik tidak tersedia. Gunakan environment yang paling relevan dan catat limitation.

Environment GitHub non-target tidak boleh diklaim sebagai final Android 11 ARM64 install proof.

Jika final target-specific install witness diperlukan untuk final claim dan pengguna belum memberi approval Firebase, status target-specific witness tetap `NOT_PROVEN`; agen tidak boleh menjalankan Firebase sendiri.

### R6-M19 — Failed / Interrupted Update Semantics
Jika aplikasi memiliki self-update/package update workflow, uji download/package corruption, insufficient storage, signature mismatch, interrupted staging, install rejection, and rollback/fallback. R3/R4 own internal state recovery; R6 owns package/install transaction.

Development fault testing dilakukan di GitHub sejauh feasible. Target-specific final execution hanya di Private dan mengikuti single-use Firebase approval rule.

### R6-M20 — Supply-Chain Inventory / SBOM
Buat machine-readable dependency/material inventory yang mengikat direct/transitive components ke final build. Vulnerability status adalah security maintenance evidence, tetapi presence/identity closure tetap wajib walau tidak ada known CVE.

### R6-M21 — CI Workflow Integrity & Required Gate Binding
Final artifact hanya sah jika berasal dari workflow/revision yang diwajibkan, seluruh mandatory gate dijalankan, no continue-on-error bypass, and artifact upload occurs only after validation.

Workflow CI dilarang mengubah GitHub PASS menjadi Firebase execution otomatis. Firebase authorization tetap mengikuti `TEST_ROUTING_POLICY.md`.

### R6-M22 — Build Mutation / Defeater Testing
Inject wrong dependency, unlocked version, modified artifact checksum, manifest exported override, missing keep rule, wrong signing cert, extra ABI, wrong minSdk/target, stale generated file, and post-sign modification. Gate harus mendeteksi semua meaningful mutation.

### R6-M23 — Change-Impact Invalidation
Perubahan build script, dependency lock, repository, toolchain, plugin, manifest, keep rule, signing, CI workflow, variant, or package policy invalidates corresponding evidence and downstream artifact proof.

Perubahan kandidat setelah Firebase approval tetapi sebelum execution juga membatalkan approval sesuai `TEST_ROUTING_POLICY.md`.

---

## Penutupan R6 Sebelum Handoff Tahap

R6-M01–M09 dan M21–M23 wajib diterapkan sebagai satu inventaris lintas fase, bukan berhenti pada compile/unit test komponen.

- Catat setiap fase/perintah yang benar-benar akan dipakai: resolve, compile, test, package, lint/shrink bila berlaku, candidate/sign/verify dan install. Hubungkan setiap fase ke source/generated input, konfigurasi/variant, JDK/compiler, Gradle/AGP, SDK/Build Tools/NDK, plugins, buildscript/classpath, dependency langsung/transitif, repository asal, lock, verification metadata, parameter/environment dan cache yang material.
- Enumerasi konfigurasi/dependency yang baru dimuat pada fase tertentu atau execution-time. Satu task dependency report, satu configuration yang di-resolve, atau dry-run saja tidak membuktikan seluruh input fase berikutnya.
- Bedakan tiga bukti: **version lock**, **integritas/trust byte**, dan **kompatibilitas/perilaku**. Checksum metadata yang baru dibangkitkan adalah kandidat untuk direview, bukan sumber kebenaran yang otomatis dipercaya. Jangan menambah checksum/version yang gagal hanya agar gate hijau; selidiki asal/perbedaan dan review melalui prosedur trust yang berlaku.
- Bukti R6 Public hanya mencakup input Public dan contract bersama. Identitas baseline, build input, konfigurasi, cache, dan bukti toolchain Private tetap di Private. Penerima memeriksa compatibility serta freshness bukti sendiri; jangan mengasumsikan toolchain mirip berarti setara.
- Uji negatif dependency/plugin baru yang tidak terinventaris, task-time resolution yang belum ditutup, versi/repository berubah, stale evidence, checksum salah, missing lock, dan verifier yang hanya menerima marker PASS.
- Catat prasyarat yang sudah terbukti dan witness final yang baru tersedia setelah build. Reproducibility/clean-build challenge yang memerlukan eksekusi tambahan adalah biaya nyata: reuse qualification sah bila berlaku; bila belum cukup, nyatakan kebutuhan/kuota sebelum dispatch, bukan diam-diam menjalankan bootstrap/rebuild.
- Evidence wajib mengikat fase/perintah/input/configuration/environment/result. R6 komponen PASS tidak boleh diwariskan menjadi R6 integrasi/Android final PASS tanpa bukti scope tambahan.

Ikuti batas tahap dan satu attempt pada aturan global §6; tidak ada trigger Private per komponen atau per fase R6. Kebijakan ini tidak memilih versi toolchain baru atau mengubah kontrak produk.

Rujukan: [Gradle dependency locking](https://docs.gradle.org/current/userguide/dependency_locking.html), [Gradle dependency verification](https://docs.gradle.org/current/userguide/dependency_verification.html), dan [Google — building secure systems](https://google.github.io/building-secure-and-reliable-systems/raw/ch14.html).

## 4. Development vs Final R6 Status

Untuk mencegah salah tafsir:

```text
R6_DEVELOPMENT_PASS
```

boleh dicapai setelah seluruh R6 proof yang relevan dan dapat dilakukan di GitHub selesai pada environment development yang dicatat.

`R6_DEVELOPMENT_PASS` tidak sama dengan `APP_SAFE_R6_PASS` bila final target-specific install/runtime witness masih diwajibkan oleh active claim.

Jika Firebase diperlukan untuk menutup gap tersebut, eksekusinya hanya boleh di Private setelah pengguna memberikan single-use approval; Public tetap dilarang menjalankan Firebase.

---

## 5. Fault Model Minimum

```text
UNDECLARED_BUILD_INPUT
TOOLCHAIN_DRIFT
DEPENDENCY_VERSION_DRIFT
DEPENDENCY_INTEGRITY_MISMATCH
TRANSITIVE_DEPENDENCY_UNKNOWN
DEPENDENCY_PLATFORM_INCOMPATIBILITY
NON_HERMETIC_OUTPUT_DRIFT
STALE_CACHE_GENERATED_OUTPUT
WRONG_VARIANT_CONFIGURATION
MANIFEST_MERGE_OVERRIDE_ERROR
UNINTENDED_EXPORTED_COMPONENT
R8_REQUIRED_CODE_REMOVED
R8_REFLECTION_BREAKAGE
MISSING_MAPPING_RETRACE
WRONG_APK_PACKAGE_METADATA
WRONG_ABI_PACKAGING
SIGNATURE_INVALID
SIGNING_IDENTITY_MISMATCH
UPGRADE_INSTALL_REJECTED
UPDATE_DATA_COMPATIBILITY_BREAK
INTERRUPTED_UPDATE_INVALID_STATE
CI_GATE_BYPASS
ARTIFACT_SOURCE_PROVENANCE_MISMATCH
```

---

## 6. PASS Formula

`APP_SAFE_R6_PASS` hanya jika:

```text
UNDECLARED_BUILD_INPUT = 0
UNPINNED_REQUIRED_TOOLCHAIN = 0
UNLOCKED_REQUIRED_DEPENDENCY = 0
UNVERIFIED_DEPENDENCY_IDENTITY = 0
FINAL_MANIFEST_UNKNOWN = 0
UNPROVEN_SHRINK_REACHABILITY = 0
SIGNING_IDENTITY_UNKNOWN = 0
SUPPORTED_INSTALL_PATH_UNPROVEN = 0
ARTIFACT_PROVENANCE_UNKNOWN = 0
BUILD_FAULT_ESCAPE = 0
STALE_EVIDENCE = 0
```

Jika `SUPPORTED_INSTALL_PATH_UNPROVEN` hanya tersisa karena target-specific Android 11 ARM64 runtime witness belum diotorisasi, agen wajib mempertahankan development status. Final Gate approval hanya diminta pada jalur Private setelah kandidat/gate Private siap; tidak boleh menjalankan Firebase dari Public atau secara otomatis.
