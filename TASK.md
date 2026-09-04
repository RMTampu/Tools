# TASK.md - Tugas Aktif Global

## Tugas Aktif

Kerjakan **Tahap 6 = F — UI/Logic/Data/Binding/Asset terpadu, authoring, search, dan template** pada repo publik utama `RMTampu/Tools`.

Baseline resmi aktif adalah **Baseline Tahap 5**, exact APK Android 11/API30/arm64 yang sudah signed, R1-R9 + ASSET_SAFE PASS, lulus Firebase final test, dan terkunci.

Tahap 6 wajib menambah kemampuan di atas Tahap 5. Baseline Tahap 5 tidak boleh dimodifikasi.

## Scope Tahap 6

### F1 — Unified Authoring Workspace
- satu workspace memakai shared model Tahap 4 + shell/editor Tahap 5.
- UI / Logic / Data / Binding / Asset adalah lima section authoring pada context yang sama.
- hanya satu section heavy-active.
- perpindahan section mempertahankan project identity dan working state.
- setiap section memakai Stable ID, typed contract, dan diagnostic yang sama.
- section tidak membuat clone model.

### F2 — Unified Search
- search lintas Component / Template / Asset / Screen / Flow / Data Source / Binding / Action / Event.
- query tervalidasi dan bounded.
- hasil bounded, deterministic, stable-keyed, dan dapat difilter berdasarkan section/kind.
- exact Stable ID dapat ditemukan tanpa bergantung label tampilan.
- search tidak mengaktifkan/mengeksekusi item.
- item unavailable/broken tidak dipromosikan sebagai valid result.

### F3 — Authoring Drafts
- draft memakai Stable Draft ID + target section + revision.
- draft lifecycle: DRAFT / VALIDATED / PUBLISHED / DISCARDED.
- edit draft meningkatkan revision monotonik.
- publish hanya dari VALIDATED.
- publish/discard terminal dan fail-closed.
- history draft bounded.
- draft tidak mengubah registry/runtime sampai publish berhasil.

### F4 — Template Authoring
- template draft mempunyai Stable Template ID, label Bahasa Indonesia, version, internal object IDs, component dependency, dan asset dependency.
- validate dependency sebelum publish.
- preview/instantiation plan menghasilkan identity map baru tanpa mengubah template master.
- publish atomik ke Template Registry dan exact version.
- duplicate version, missing dependency, invalid ID, atau archived dependency gagal tertutup.
- template search langsung tersedia setelah publish.

## Exit Gate Tahap 6

```text
BASELINE_TAHAP_5_UNCHANGED = YES
VERSION_CODE_GT_TAHAP_5 = YES
UNIFIED_AUTHORING_WORKSPACE = PASS
UI_SECTION = PASS
LOGIC_SECTION = PASS
DATA_SECTION = PASS
BINDING_SECTION = PASS
ASSET_SECTION = PASS
ONE_HEAVY_SECTION = PASS
SHARED_MODEL_NO_CLONE = PASS
UNIFIED_SEARCH = PASS
SEARCH_BOUNDED = PASS
SEARCH_DETERMINISTIC = PASS
SEARCH_STABLE_ID = PASS
SEARCH_NO_EXECUTION = PASS
AUTHORING_DRAFT_LIFECYCLE = PASS
DRAFT_REVISION_MONOTONIC = PASS
DRAFT_TERMINAL_STATE = PASS
DRAFT_HISTORY_BOUNDED = PASS
TEMPLATE_AUTHORING = PASS
TEMPLATE_DEPENDENCY_VALIDATION = PASS
TEMPLATE_PREVIEW_NO_MASTER_MUTATION = PASS
TEMPLATE_EXACT_VERSION_PUBLISH = PASS
TEMPLATE_SEARCH_AFTER_PUBLISH = PASS
R1_R9_AUTOMATIC = PASS
ASSET_SAFE_REGRESSION = PASS
ANDROID_11_API30_BUILD = PASS
PUBLIC_UNSIGNED_APK = PASS
UNKNOWN_REQUIRED_TAHAP_6_BEHAVIOR = 0
```

Private tetap hanya untuk signing, credential, Firebase final runtime test, baseline locking, dan release sensitif.
