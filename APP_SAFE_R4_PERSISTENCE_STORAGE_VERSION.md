# APP_SAFE_R4_PERSISTENCE_STORAGE_VERSION.md

## 1. Status

Korpus metode aktif untuk **R4 — Persistence, Database, Storage & Versioned-State Safety** dalam framework `APPLICATION_SAFE_100`.

Status riset: `PRACTICAL_SATURATION` terhadap ruang metode yang telah disapu.

---

## 2. Scope

R4 menutup:

- runtime database correctness;
- transaction boundaries and isolation;
- schema migration;
- serialization/deserialization compatibility;
- file persistence and atomic replacement;
- cache/state persistence;
- corruption detection/recovery;
- storage-full/I/O failure;
- backup/restore;
- upgrade/downgrade and old persistent-state compatibility;
- crash consistency of durable state.

Asset database/config shipped inside APK tetap juga tunduk pada `ASSET_SAFE_100`.

---

## 3. Metode Aktif

### R4-M01 — Persistent-State Universe & Contract Closure
Inventaris database, table, index, view, DataStore/preferences, files, caches with correctness role, serialized blobs, journals/WAL, backup data, checkpoints, version metadata, temp files, and external durable stores used by application.

### R4-M02 — Single Source of Truth & Authority Model
Tentukan authoritative state untuk setiap datum dan relationship antara memory/cache/database/server copy. Ambiguous write authority dan dual-writer tanpa reconciliation contract dilarang.

### R4-M03 — Transaction Boundary / ACID Invariant Proof
Setiap multi-write operation yang membutuhkan atomicity wajib berada pada transaction/atomic protocol yang benar. Isolation, uniqueness, foreign key, check invariant, and commit/rollback semantics harus sesuai contract.

### R4-M04 — Concurrency & Locking Contract
Database/file multi-thread/process access wajib mempunyai locking/transaction strategy, busy/timeout behavior, retry policy, and no unsafe concurrent copy/replace. R2 tetap owner concurrency global.

### R4-M05 — Schema History & Migration Graph Closure
Simpan versioned schema history dan bentuk directed migration graph dari seluruh supported persisted versions menuju current version. Missing supported path = `NOT_PROVEN`.

### R4-M06 — Exhaustive Supported Migration Testing
Untuk setiap supported source version/path: create old schema -> seed representative + boundary data -> migrate -> validate final schema -> validate data semantics/invariants -> reopen using production stack.

### R4-M07 — Migration Semantic Preservation
Schema match saja tidak cukup. Buktikan preservation/transformation terhadap required rows, identity, ordering where contractual, relationships, defaults, nullability, units, enum mapping, and derived-state recomputation.

### R4-M08 — Serialization Round-Trip & Schema Evolution
Untuk every serialized durable format, verify encode/decode round-trip, unknown/new field behavior, missing field/default, version negotiation, enum evolution, field-number/key stability, canonicalization if identity/hash depends on representation, and invalid payload rejection.

### R4-M09 — Atomic File Replacement / Durability Protocol
File correctness-critical harus memakai write-temp/sync/atomic replace atau equivalent protocol. Verify interrupted write, first-create, rename failure, stale backup, and no partially-valid file publication.

### R4-M10 — Journal/WAL Cohesion
Database state yang bergantung journal/WAL/shm wajib diperlakukan sebagai satu persistence protocol. Copy/move/backup/restore tidak boleh memisahkan required recovery files dari database state.

### R4-M11 — Crash Consistency / Power-Loss Fault Injection
Inject crash/kill/I/O failure at every critical persistence boundary. Setelah reopen, state harus old-valid atau new-valid sesuai atomicity contract, bukan torn/ambiguous state.

### R4-M12 — Integrity / Corruption Detection
Jalankan database integrity, foreign-key/constraint checks, checksums/schema validation for other formats, and detect malformed/truncated data. Corrupt data tidak boleh silently dianggap valid.

### R4-M13 — Corruption Quarantine & Recovery
Recovery/salvage output tidak langsung dipercaya. Recovered data wajib revalidate against schema/business invariants; unrecoverable or uncertain records must be quarantined/dropped according to explicit policy.

### R4-M14 — Storage-Full / I/O Failure Semantics
Simulasikan no-space, permission/read-only transition where applicable, write error, fsync/rename failure, corrupted temp, slow I/O, and interrupted cleanup. State harus tetap recoverable dan user-visible error tidak menyebabkan further corruption.

### R4-M15 — Cache Coherence / Invalidation Proof
Cache dengan semantic role wajib memiliki key identity, invalidation trigger, version/generation, stale-data policy, max lifetime, process restart behavior, and rebuild path. Cache yang dapat dibuang tidak boleh menjadi hidden source of truth.

### R4-M16 — Backup / Restore Equivalence
Untuk data yang dibackup: backup closure, required companion files, encryption/key assumptions, restore to clean install, restore across supported app version, and semantic invariant validation wajib diuji.

### R4-M17 — Upgrade / Downgrade / Forward-Backward Compatibility Matrix
Tetapkan versi yang didukung. Uji old->new, supported new->newer, reinstall/restore, and downgrade hanya jika memang contract mendukung. Unsupported downgrade harus fail clearly tanpa corrupt state.

### R4-M18 — Golden Dataset + Property-Based Persistent Testing
Gunakan deterministic datasets untuk critical invariants serta generated boundary/adversarial datasets untuk migration/serialization/query behavior. Data generation tidak menggantikan schema and transaction proof.

### R4-M19 — Cleanup / Temp / Tombstone Closure
Temporary files, old database, backup file, pending journal, migration markers, and recovery snapshots harus mempunyai lifecycle/retention/cleanup contract yang tidak menghapus recovery evidence terlalu awal.

### R4-M20 — Change-Impact & Mutation Adequacy
Perubahan schema, serializer, transaction, DAO/query, file format, cache key, backup rule, migration, or storage API wajib invalidate evidence. Mutations minimal: missing migration, dropped data, torn write, wrong default, corrupt payload accepted, WAL separated, storage-full partial commit.

---

## 4. Fault Model Minimum

```text
TRANSACTION_ATOMICITY_ERROR
ISOLATION_ERROR
CONSTRAINT_VIOLATION
SCHEMA_MISMATCH
MISSING_MIGRATION_PATH
MIGRATION_DATA_LOSS
MIGRATION_SEMANTIC_CHANGE
SERIALIZATION_INCOMPATIBILITY
UNKNOWN_FIELD_ENUM_ERROR
TORN_FILE_WRITE
ATOMIC_REPLACE_FAILURE
WAL_JOURNAL_SEPARATION
PERSISTENCE_CRASH_INCONSISTENCY
DATABASE_CORRUPTION
CORRUPT_DATA_ACCEPTED
INVALID_RECOVERY_DATA
STORAGE_FULL_PARTIAL_STATE
CACHE_STALE_STATE
BACKUP_RESTORE_MISMATCH
VERSIONED_STATE_INCOMPATIBILITY
TEMP_RECOVERY_FILE_ERROR
```

---

## 5. PASS Formula

`APP_SAFE_R4_PASS` hanya jika:

```text
PERSISTENT_STORE_UNKNOWN = 0
SOURCE_OF_TRUTH_AMBIGUITY = 0
UNPROVEN_TRANSACTION_BOUNDARY = 0
SUPPORTED_MIGRATION_PATH_MISSING = 0
MIGRATION_DATA_INVARIANT_UNKNOWN = 0
SERIALIZATION_COMPATIBILITY_UNKNOWN = 0
CRASH_CONSISTENCY_UNKNOWN = 0
CORRUPTION_PATH_UNPROVEN = 0
BACKUP_RESTORE_REQUIRED_UNPROVEN = 0
PERSISTENCE_FAULT_ESCAPE = 0
STALE_EVIDENCE = 0
```
