# TASK.md - Tugas Aktif Global

## Tugas Aktif

Kerjakan **Tahap 3 = C — Komponen, asset, template, versi, dependency, dan Library** pada repo publik utama `RMTampu/Tools`.

Baseline resmi aktif adalah **Baseline Tahap 2**, exact APK Android 11/API30/arm64 yang sudah signed, R1-R9 PASS, dan lulus Firebase final test pada jalur private.

Tahap 3 wajib menambah kemampuan di atas Tahap 2. Baseline Tahap 2 tidak boleh dibongkar atau dimodifikasi.

## Scope Tahap 3

### C1 — Component Definition / Registry / Instance
- Stable Component ID.
- metadata: label Bahasa Indonesia, category, icon reference, version, lifecycle, implementation reference.
- typed Property Contract dan Event Contract.
- capability/asset/dependency requirements.
- lifecycle `DRAFT / READY / DEPRECATED / ARCHIVED`.
- READY hanya jika validation PASS.
- project instance pin ke exact component version; update tidak boleh silent.
- missing component = `COMPONENT_UNAVAILABLE`, instance tidak dihapus.

### C2 — Asset Identity / Integrity / Store
- Stable Asset ID; consumer tidak bergantung pada path/nama file.
- original persistent terpisah dari preview/cache.
- SHA-256, type/MIME, size budget, version dan source metadata.
- duplicate candidate berbasis content hash.
- missing/broken asset tidak dihapus diam-diam.
- relink mempertahankan Stable Asset ID hanya jika integrity/contract valid.
- clear cache tidak boleh menghapus original.
- path traversal/canonical path ambiguity fail-closed.

### C3 — Template
- Stable Template ID dan version.
- template = titik awal reusable, bukan linked component master.
- dependency closure ke component/asset.
- lifecycle + validation.
- inserted object tetap editable dan memperoleh identity baru.
- template incompatible/missing dependency fail-closed.

### C4 — Version / Dependency / Library
- semantic version parser + compatibility range.
- exact Stable ID mapping; nama mirip tidak boleh auto-connect.
- dependency resolver menghasilkan missing/incompatible diagnostics.
- Component Library + Asset Library + Template Library dalam satu Library Manager.
- search metadata ringan, favorite/recent, exact lookup.
- Library Master dipisahkan dari project instance.

## Exit Gate Tahap 3

```text
BASELINE_TAHAP_2_UNCHANGED = YES
VERSION_CODE_GT_TAHAP_2 = YES
COMPONENT_STABLE_ID = PASS
COMPONENT_VERSION_PINNING = PASS
COMPONENT_READY_GATE = PASS
PROPERTY_CONTRACT = PASS
EVENT_CONTRACT = PASS
ASSET_STABLE_ID = PASS
ASSET_SHA256 = PASS
ASSET_ORIGINAL_CACHE_SEPARATION = PASS
ASSET_DUPLICATE_DETECTION = PASS
ASSET_RELINK_INTEGRITY = PASS
ASSET_PATH_BOUNDARY = PASS
TEMPLATE_ID_VERSION = PASS
TEMPLATE_DEPENDENCY_CLOSURE = PASS
SEMVER_COMPATIBILITY = PASS
DEPENDENCY_RESOLUTION = PASS
LIBRARY_EXACT_LOOKUP = PASS
LIBRARY_SEARCH = PASS
R1_R9_AUTOMATIC = PASS
ASSET_SAFE_CHAIN = PASS
ANDROID_11_API30_BUILD = PASS
PUBLIC_UNSIGNED_APK = PASS
UNKNOWN_REQUIRED_TAHAP_3_BEHAVIOR = 0
```

Private tetap hanya untuk signing, credential, Firebase final runtime test, baseline locking, dan release sensitif.
