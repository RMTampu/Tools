# ToolBox Legacy Technical Rules — Repository-Split Edition

## Status

Dokumen ini mempertahankan aturan teknis umum dari era sebelum repository dipisah. **Tidak ada aturan di sini yang boleh menempatkan `RMTampu/Tools` sebagai product master.**

```text
PRODUCT_MASTER = RMTampu/ToolBox (PRIVATE)
CI_ENGINE      = RMTampu/Tools (PUBLIC)
```

Aturan teknis yang lebih rinci tetap dimiliki file prosedur khusus dan tidak dikurangi oleh dokumen ini.

## Product Foundation

ToolBox tetap menargetkan Android 11 / API 30 / `arm64-v8a`, menggunakan extensible core/kernel, registry, contracts, failure isolation, dan extension point agar engine/tool dapat berkembang tanpa menjadikan UI atau modul tertentu sebagai dependency kernel yang tidak perlu.

Product source, kernel, UI, asset/resource, dependency state, verification state, dan master rancangan berada di private `RMTampu/ToolBox`. Public `RMTampu/Tools` hanya menyediakan CI execution tooling.

## Build

- APK hanya dibangun melalui GitHub Actions.
- Termux hanya relay bila diperlukan dan bukan lingkungan build.
- Jangan instal package/tool di Termux tanpa izin eksplisit pengguna.
- Target artifact/release tidak boleh fallback diam-diam dari Android 11/API30/ARM64.
- Dependency/toolchain final harus pinned dan tervalidasi.
- Build hanya boleh dimulai setelah seluruh prebuild gate wajib PASS.
- Build tidak boleh dipakai sebagai eksperimen untuk mengetahui apakah gate dasar seharusnya PASS.

## GitHub Test vs Final Target

GitHub development/basic/intermediate/regression test boleh memakai environment relevan yang tersedia, dengan environment aktual dicatat. Hasil non-API30/non-ARM64 tidak boleh diklaim sebagai final Android 11 ARM64 runtime proof.

Final target-specific witness hanya melalui Firebase Final Gate sesuai `TEST_ROUTING_POLICY.md`.

```text
FIREBASE DEFAULT STATE = LOCKED
1 EXPLICIT USER APPROVAL = 1 FIREBASE EXECUTION ATTEMPT
```

Tidak ada auto-run atau auto-retry Firebase.

## Repository Changes

Sebelum mengubah file:

1. baca file dan aturan terkait;
2. pahami dependency/contract;
3. batasi perubahan pada scope yang diperlukan;
4. jangan hapus komponen sebelum terbukti aman;
5. pertahankan compatibility dan proof yang masih valid;
6. perubahan input wajib menginvalidasi proof terdampak dan menjalankan ulang gate yang relevan.

## Asset Rules

Pekerjaan asset/resource wajib mengikuti seluruh sumber aktif berikut tanpa pengurangan coverage:

- `ASSET_SAFE_100_RULES.md`
- `ASSET_SAFE_100_METHODS.md`
- `ASSET_SAFE_100_PROCESS.md`
- `ASSET_ROUTE_PROOF_METHODS.md`
- `ASSET_ROUTE_PROOF_PROCESS.md`
- `PREBUILD_ASSET_GATE.md`

Jika route/reference/resolution termasuk scope, `ROUTE_PROOF_PASS` wajib sebelum build boundary dibuka.

## Application Safety

Pekerjaan aplikasi yang akan dibangun/diuji/audit wajib mengikuti `APPLICATION_SAFE_100_PROCESS.md` dan domain:

1. `APP_SAFE_R1_LOGIC_INPUT.md`
2. `APP_SAFE_R2_CONCURRENCY_RESOURCE.md`
3. `APP_SAFE_R3_LIFECYCLE_STATE_RECOVERY.md`
4. `APP_SAFE_R4_PERSISTENCE_STORAGE_VERSION.md`
5. `APP_SAFE_R5_SECURITY_NETWORK_BOUNDARY.md`
6. `APP_SAFE_R6_BUILD_DEPENDENCY_INSTALL.md`
7. `APP_SAFE_R7_NATIVE_PLUGIN_RUNTIME.md`
8. `APP_SAFE_R8_UI_DEVICE_POWER.md`
9. `APP_SAFE_R9_VERIFICATION_COMPLETENESS.md`

Urutan closure tetap `R1 → R2 → R3 → R4 → R5 → R6 → R7 → R8 → R9` sesuai proses aktif. APK/production build tidak boleh dimulai sebelum `APPLICATION_PREBUILD_PASS`; asset gate juga wajib bila asset/resource termasuk scope.

## Procedure Execution

`AGENT_PROCEDURE_EXECUTION_RULES.md` wajib untuk gate/audit/build/test. Context limit tidak boleh mengurangi rule, coverage, invariant, evidence, atau syarat PASS. Jika proses terlalu besar, pecah unit eksekusi; jangan mengurangi prosedur.

Status `PARTIAL`, `UNKNOWN`, `NOT_CHECKED`, `INCOMPLETE`, `INDETERMINATE`, atau `ASSUMED` bukan PASS. Default ketika proof tidak cukup adalah `NOT_PROVEN`.

## Final Authority

```text
Instruksi pengguna terbaru
→ AGENTS.md repository terkait
→ REPOSITORY_INTEGRATION_POLICY.md
→ TEST_ROUTING_POLICY.md
→ file procedure/gate khusus
→ dokumen lain
```

Repository split tidak melemahkan aturan safety; hanya memindahkan product master ke private `RMTampu/ToolBox` dan mempertahankan `RMTampu/Tools` sebagai CI engine.
