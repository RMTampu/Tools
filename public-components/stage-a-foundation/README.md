# ToolBox Public — Stage A Foundation Closure

## Status

`STAGE_A_PUBLIC_CLOSURE_CANDIDATE`

Status `STAGE_A_READY_PRIVATE` hanya diterbitkan oleh workflow Stage A setelah seluruh dependency test, integration simulator, mutation challenge, R1–R9 Public closure, dan package validation PASS pada exact revision.

## Tujuan

Menutup **Tahap A sebagai satu unit pembangunan**, bukan mempromosikan A1/A2/A3/A4 secara terpisah.

Tahap A Public menggabungkan:

- registry/runtime metadata contracts yang sudah matang;
- runtime safety primitives yang sudah matang;
- ExecutionGuard untuk admission fail-closed;
- recovery bootstrap + state-store contract;
- diagnostic mapping;
- Safe UI state contract;
- aggregated health;
- integration surrogate yang memakai `ProductRegistry` yang sama.

A1–A4 diperlakukan sebagai sublangkah internal. `COMPONENT_READY_PRIVATE` dependency tidak membuka integrasi Private. Handoff memerlukan artifact Stage A dengan `STAGE_A_READY_PRIVATE` **dan** manifest/evidence seluruh cakupan tahap yang disetujui menurut [aturan global §6](../../GLOBAL_PUBLIC_PRIVATE_DEVELOPMENT_RULES.md#6-jalur-kematangan-komponen). Alias ini berarti `STAGE_READY_PRIVATE` untuk A hanya bila syarat tersebut terbukti, bukan karena nama paket atau teks status. Izin, budget, prasyarat penerima, dan satu-attempt gate Private tetap wajib.

Baca [peta MD](../../PETA-PEMAKAIAN-MD.md), [APPLICATION](../../APPLICATION_SAFE_100_PROCESS.md), [R6](../../APP_SAFE_R6_BUILD_DEPENDENCY_INSTALL.md) dan [R9](../../APP_SAFE_R9_VERIFICATION_COMPLETENESS.md) untuk kontrak/dummy, seluruh input fase, fault/mutation, evidence binding dan closure tahap. Jangan memperkecil tahap menjadi daftar implementasi yang kebetulan sudah tersedia.

## Boundary

Public tidak memuat source/asset/state/APK Private, tidak membaca Private dari workflow, tidak menjalankan Firebase, dan tidak mengklaim final Android runtime PASS.

Pengujian penyambungan memakai dummy/simulator mandiri dari contract Public.

## Dependency

- `public.runtime-contracts` 0.1.0 / `toolbox.runtime.metadata` 1.0.0
- `public.runtime-safety-contracts` 0.1.0 / `toolbox.runtime.safety` 1.0.0
- Java release 11
- external runtime dependency 0

## Jalur closure

```text
DEPENDENCY REVALIDATION
-> STAGE A CONTRACT/ADAPTER COMPILE
-> SELF/BOUNDARY/PROPERTY TEST
-> REGISTRY INTEGRATION SIMULATOR
-> MUTATION CHALLENGE
-> R9 EVIDENCE CLOSURE
-> CONSOLIDATED PACKAGE VALIDATION
-> STAGE_A_READY_PRIVATE
```

Private tidak disentuh oleh workflow ini.
