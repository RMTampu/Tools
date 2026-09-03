# Public Component — ToolBox Runtime Contracts

## Status

`PUBLIC_DEVELOPMENT_IN_PROGRESS`

Komponen ini adalah material Public yang berdiri sendiri. Ia tidak berisi source, asset, state, credential, APK, atau artifact Private dan tidak membutuhkan akses ke `RMTampu/ToolBox`.

## Tujuan

Menyediakan contract metadata dan registry aman untuk tahap fondasi ToolBox tanpa memuat atau meniru kernel Private.

Kemampuan Public component:

- Tool Contract;
- Component Contract;
- Action Contract;
- Capability Contract;
- Event Contract;
- Permission Contract;
- Product Registry metadata;
- atomic bundle publication;
- exact Stable ID lookup;
- validation dan failure codes;
- simulator tanpa runtime engine.

## Batas

Komponen ini tidak:

- membangun APK;
- melakukan signing;
- memakai Firebase;
- membaca repository Private;
- memuat arbitrary DEX/JAR/native plugin;
- menjalankan engine sebenarnya;
- mengklaim final Android 11/ARM64 runtime PASS.

## Contract identity

```text
PROJECT_ID = ToolBox
COMPONENT_ID = public.runtime-contracts
COMPONENT_VERSION = 0.1.0
CONTRACT_ID = toolbox.runtime.metadata
CONTRACT_VERSION = 1.0.0
TARGET = Android 11 / API 30 compatible product contract
JVM_BYTECODE = Java 11
```

## Pipeline Public

```text
SPEC
-> CONTRACT
-> DEPENDENCY
-> UNIT_TEST
-> SIMULATOR
-> FAILURE_TEST
-> PACKAGE_VALIDATION
-> READY_PRIVATE
```

`READY_PRIVATE` pada output/manifest komponen adalah status legacy yang dibaca sebagai `COMPONENT_READY_PRIVATE`: komponen matang dan ditahan di Public. Ia tidak mengizinkan promosi/preflight/integrasi sendiri atau final PASS. Hanya closure tahap utuh `STAGE_READY_PRIVATE` dengan otorisasi yang berlaku membuka satu attempt Private menurut [aturan global](../../GLOBAL_PUBLIC_PRIVATE_DEVELOPMENT_RULES.md). Nama status mesin di pipeline ini dipertahankan untuk compatibility; bukan penaikan kesiapan tahap otomatis.

## Dependency

Runtime dependency eksternal: **0**.

Build/test memakai JDK 17 tetapi source dikompilasi dengan `javac --release 11`, sehingga artifact contract tidak bergantung pada Java 17 runtime API.

## Acceptance Public

Komponen dapat menjadi `READY_PRIVATE` hanya bila:

- seluruh source compile dengan `--release 11`;
- positive tests PASS;
- negative/failure tests PASS;
- concurrent publication test PASS;
- simulator PASS tanpa engine/runtime callback;
- JAR package validation PASS;
- Promotion Manifest mempunyai hash, version, compatibility, dan test status;
- workflow hanya berjalan pada `RMTampu/Tools`;
- workspace cleanup berjalan `if: always()`;
- tidak terdapat private token, signing, APK build, Firebase, atau Private checkout.

## Urutan Dokumen Kerja Komponen

Awali dengan [AGENTS repository](../../AGENTS.md), aturan global Rule 0, dan [peta pemakaian MD Public](../../PETA-PEMAKAIAN-MD.md). Setelah aturan awal:

1. [CONTRACT.md](CONTRACT.md) — sumber input/output, batas resource, publication, failure, dan integration handoff.
2. [ASSURANCE_R1_R9.md](ASSURANCE_R1_R9.md) bersama sumber R1–R9 yang dirujuk — applicability, metode, dan evidence sebelum pekerjaan bergantung padanya; matriks tidak menggantikan sumber metode.
3. [SANITIZED_PRIVATE_FAILURE_2026-09-03.md](SANITIZED_PRIVATE_FAILURE_2026-09-03.md), [PRIVATE_INTEGRATION_REQUIREMENTS.json](PRIVATE_INTEGRATION_REQUIREMENTS.json), dan [R6_PRIVATE_HANDOFF_ASSURANCE_ADDENDUM.md](R6_PRIVATE_HANDOFF_ASSURANCE_ADDENDUM.md) — saat memperbaiki/menguji handoff dependency trust terkait.
4. [README stage-a-foundation](../stage-a-foundation/README.md) — saat komponen dimasukkan ke closure tahap utuh; jangan mempromosikan komponen secara mandiri.

Catatan failure dipakai sebagai requirement/regresi yang aman, bukan izin membaca Private. Status dan evidence tetap harus cocok dengan exact revision; daftar dokumen ini tidak menyatakan test/package sudah PASS.
