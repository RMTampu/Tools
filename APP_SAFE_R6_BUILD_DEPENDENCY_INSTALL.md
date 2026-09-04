# APP_SAFE_R6_BUILD_DEPENDENCY_INSTALL.md

> Aturan aktif: seluruh pengembangan dan pematangan teknis dilakukan di repo publik utama. Jalur private hanya untuk secret, signing key, credential, build final tertandatangan, Firebase/final runtime test, dan release sensitif.

## 1. Status

Korpus metode aktif untuk **R6 — Build, Dependency, Manifest, Shrinking & Installation Safety** dalam framework `APPLICATION_SAFE_100`.

Status riset: `PRACTICAL_SATURATION` terhadap ruang metode yang telah disapu.

Target produk: Android 11 / API 30 / arm64-v8a. Pengujian publik boleh memakai environment CI yang relevan selama claim tidak dinaikkan menjadi bukti final target-specific. Bukti final yang membutuhkan signing/credential/Firebase tetap berada di jalur private.

---

## 2. Scope

R6 menutup:

- build-input and toolchain integrity;
- dependency resolution and compatibility;
- manifest merging and final manifest semantics;
- code/resource shrinking interaction;
- reflection/dynamic reachability handoff ke R7;
- signing identity dan APK verification;
- package/variant/ABI/sdk contract;
- installation/update compatibility;
- reproducibility/provenance;
- CI artifact-to-source binding.

Asset package proof tetap mengikuti `ASSET_SAFE_100`; native runtime semantics dimiliki R7.

---

## 3. Metode Aktif

### R6-M01 — Closed Build-Input Universe
Inventaris seluruh source, generated source, asset/resource, Gradle file, plugin, compiler, JDK, Android Gradle Plugin, SDK/Build Tools, NDK bila ada, environment variable yang memengaruhi output, dependency repository, signing config, keep rule, manifest fragment, dan CI input.

### R6-M02 — Toolchain / Version Pinning
Build wajib memakai toolchain versions yang eksplisit dan kompatibel. Floating toolchain/dynamic version yang dapat mengubah artifact tanpa perubahan source dilarang pada jalur yang menghasilkan artifact terverifikasi.

### R6-M03 — Dependency Locking & Transitive Closure
Seluruh resolved direct + transitive dependency harus mempunyai exact version/identity. Lock state mismatch, extra unexpected dependency, atau missing locked dependency = fail-closed.

### R6-M04 — Dependency Integrity Verification
Gunakan checksum/signature/verification metadata atau mekanisme setara untuk membuktikan binary/source dependency yang diambil sama dengan yang diharapkan.

### R6-M05 — Dependency Compatibility Contract
Setiap dependency wajib kompatibel dengan Android 11/API30, JVM/bytecode, min/target SDK, arm64/native needs, public API contract, R8 behavior, dan required runtime. Upgrade dependency membatalkan proof yang terdampak sampai tervalidasi ulang.

### R6-M06 — Hermeticity / External Influence Closure
Build harus mengurangi atau menutup undeclared network/time/user-home/global cache/input influence. Bila hermetic penuh tidak feasible, seluruh external influence yang dapat mengubah output harus dideklarasikan dan diverifikasi.

### R6-M07 — Reproducible / Deterministic Build Comparison
Untuk build yang diklaim reproducible, rebuild dari input identik dan bandingkan output. Jika format memasukkan expected nondeterminism, normalisasi hanya field yang contract membuktikan non-semantic lalu bandingkan semantic artifact model.

### R6-M08 — Build Provenance / Artifact Attestation
Simpan source revision, builder/workflow identity, resolved materials, toolchain, parameters, timestamps, dan artifact digests sehingga artifact dapat ditelusuri ke build invocation yang sah.

### R6-M09 — Clean-Build / Cache-Contamination Challenge
Bandingkan clean environment dengan cached/incremental build. Output semantic tidak boleh bergantung pada stale generated file, local untracked artifact, atau cache yang tidak menjadi declared input.

### R6-M10 — Variant / Flavor / Build-Type Matrix Closure
Inventaris seluruh variant yang didukung. Manifest, resources, dependencies, BuildConfig, proguard rules, native libs, signing, dan application ID untuk variant final harus sesuai contract.

### R6-M11 — Manifest Merge Semantic Oracle
Periksa **final merged manifest**, bukan hanya source fragments. Validate component/exported status, permission, uses-feature, provider authority, intent filter, application flags, min/target SDK, network config, metadata, service/receiver attributes, dan unintended override/merge marker.

### R6-M12 — Static Build/Manifest Lint & Policy Gate
Lint/build analysis wajib fail pada class finding yang ditetapkan critical oleh contract. Suppression harus spesifik, terdokumentasi, dan tidak boleh menjadi blanket bypass.

### R6-M13 — R8/Code-Shrinking Reachability Closure
Untuk release shrink/minify, buktikan code yang dipanggil secara direct, reflection, serialization, JNI, manifest, XML/onClick-like reference, dependency injection, plugin entry, dan generated adapter tidak terhapus/diubah secara salah.

### R6-M14 — Keep-Rule Minimality & Mutation Verification
Keep rule harus cukup untuk preserve required behavior tetapi tidak sekadar `-keep **` untuk menyembunyikan reachability problem. Mutation remove/narrow keep rules dan release/runtime tests harus menangkap missing code.

### R6-M15 — Obfuscation Mapping / Retrace Evidence
Simpan mapping/retrace metadata yang tepat untuk artifact yang di-obfuscate. Crash evidence harus dapat dikembalikan ke source revision yang sesuai.

### R6-M16 — Final APK Structural / Semantic Verification
Periksa APK: package/applicationId, manifest, sdk levels, classes/dex, native ABI set, resource/asset presence, compression/alignment where relevant, versionCode/versionName, signing certificates pada jalur yang berwenang, dan forbidden unexpected payload.

### R6-M17 — Signing Identity / Signature Verification
Artifact final tertandatangan harus diverifikasi dengan `apksigner` atau equivalent terhadap supported platform range. Certificate identity/lineage harus sesuai release contract. Private key tidak boleh masuk repo publik dan APK tidak boleh dimodifikasi setelah signing.

### R6-M18 — Install / Upgrade / Reinstall Matrix
Uji install clean, upgrade dari supported installed versions, reinstall same version where relevant, data preservation, signature continuity, versionCode rules, failure behavior, dan package manager acceptance. CI publik menguji environment yang tersedia dan mencatat scope; witness final Android 11/API30/arm64 yang membutuhkan layanan/credential private dilakukan hanya di jalur private.

### R6-M19 — Failed / Interrupted Update Semantics
Jika aplikasi memiliki self-update/package update workflow, uji download/package corruption, insufficient storage, signature mismatch, interrupted staging, install rejection, dan rollback/fallback. R3/R4 memiliki internal state recovery; R6 memiliki package/install transaction.

### R6-M20 — Supply-Chain Inventory / SBOM
Buat machine-readable dependency/material inventory yang mengikat direct/transitive components ke build. Vulnerability status adalah security maintenance evidence, tetapi presence/identity closure tetap wajib walau tidak ada known CVE.

### R6-M21 — CI Workflow Integrity & Required Gate Binding
Artifact hanya sah jika berasal dari workflow/revision yang diwajibkan, seluruh mandatory gate dijalankan, tidak ada `continue-on-error` yang membypass gate, dan artifact upload terjadi hanya setelah validation.

### R6-M22 — Build Mutation / Defeater Testing
Inject wrong dependency, unlocked version, modified artifact checksum, manifest exported override, missing keep rule, wrong signing identity pada test fixture yang aman, extra ABI, wrong minSdk/target, stale generated file, dan post-build modification. Gate harus mendeteksi meaningful mutation.

### R6-M23 — Change-Impact Invalidation
Perubahan build script, dependency lock, repository, toolchain, plugin, manifest, keep rule, signing configuration, CI workflow, variant, atau package policy membatalkan corresponding evidence dan downstream artifact proof sampai validasi ulang.

---

## 4. Development vs Final R6 Status

`R6_DEVELOPMENT_PASS` boleh dicapai setelah seluruh proof R6 yang relevan dan dapat dilakukan di repo publik selesai pada environment development yang dicatat.

`R6_DEVELOPMENT_PASS` tidak sama dengan `APP_SAFE_R6_PASS` bila final target-specific install/runtime witness, signing identity, atau credential-backed validation masih diwajibkan. Bagian tersebut tetap dilakukan di jalur private tanpa mengekspos secret ke repo publik.

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

Jika final target-specific witness belum dapat dilakukan karena membutuhkan jalur private, status tetap development-only dan tidak boleh dinaikkan menjadi final claim.
