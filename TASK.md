# TASK.md - Tugas Aktif Global

## Tugas Aktif

Kerjakan **Tahap 2 = B — Penyimpanan proyek, Simpan, revisi, dan pemulihan** pada repo publik utama `RMTampu/Tools`.

Baseline resmi adalah **Baseline Tahap 1**, exact APK Android 11/API30/arm64 yang sudah signed dan lulus Firebase final test pada jalur private. Tahap 2 wajib menambah kemampuan di atas baseline tersebut dan tidak boleh menafsirkannya sebagai Tahap 1 baru.

## Scope Tahap 2

### B1 — Struktur proyek dan identitas
- Project Store source of truth.
- Stable ID.
- `project.json` definition.
- `project.manifest` integrity record.
- lightweight `project.index`.
- resource package terpisah dari project definition.
- schema/build-model/revision/lifecycle/dependency/reference metadata.
- klasifikasi ACCESS_LOST/FOLDER_MISSING/RESOURCE_MISSING/PROJECT_CORRUPT.

### B2 — Manual transactional Save + Undo/Redo
- manual Save only.
- dirty state.
- Simpan / Keluar Tanpa Simpan / Batal.
- staging + journal + validate + atomic revision publish.
- optimistic revision check dan `STALE_WRITE`.
- single writer.
- multi-resource action = satu undo group.
- Undo/Redo chronological dan bounded.

### B3 — Integrity, migration, recovery
- checksum/hash.
- current/previous valid revision.
- interrupted transaction cleanup.
- corruption detection.
- explicit recovery preview + restore.
- recovered draft tidak dihitung sebagai Save.
- migration incremental deterministic.
- schema incompatible fail-closed.
- reference integrity.
- storage/access-loss classification.

### B4 — Recovery contract
- recovery candidates tidak dipilih diam-diam.
- preview sebelum restore.
- restore menerbitkan revision baru.
- required/current valid tidak dihapus sebagai disposable history.

## Exit Gate Tahap 2

```text
BASELINE_TAHAP_1_UNCHANGED = YES
VERSION_CODE_GT_TAHAP_1 = YES
PROJECT_STORE = PASS
STABLE_ID = PASS
PROJECT_DEFINITION = PASS
PROJECT_MANIFEST = PASS
PROJECT_INDEX = PASS
SEPARATE_RESOURCE_PACKAGE = PASS
MANUAL_SAVE_ONLY = PASS
TRANSACTION_JOURNAL = PASS
STALE_WRITE = PASS
UNDO_REDO_GROUP = PASS
UNDO_HISTORY_BOUNDED = PASS
REVISION_HISTORY = PASS
CORRUPTION_DETECTION = PASS
PREVIOUS_VALID_RECOVERY = PASS
EXPLICIT_RECOVERY_PREVIEW = PASS
DRAFT_NOT_SAVE = PASS
MIGRATION = PASS
REFERENCE_INTEGRITY = PASS
ACCESS_LOSS_CLASSIFICATION = PASS
ANDROID_11_API30_BUILD = PASS
PUBLIC_UNSIGNED_APK = PASS
UNKNOWN_REQUIRED_TAHAP_2_BEHAVIOR = 0
```

Private tetap hanya untuk signing, credential, Firebase final runtime test, dan release sensitif.
