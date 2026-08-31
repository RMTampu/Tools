# APPLICATION_SAFE_100_PROCESS.md

## 1. Status Dokumen

Dokumen ini adalah **orkestrator wajib** untuk sembilan paket assurance aplikasi:

1. `APP_SAFE_R1_LOGIC_INPUT.md`
2. `APP_SAFE_R2_CONCURRENCY_RESOURCE.md`
3. `APP_SAFE_R3_LIFECYCLE_STATE_RECOVERY.md`
4. `APP_SAFE_R4_PERSISTENCE_STORAGE_VERSION.md`
5. `APP_SAFE_R5_SECURITY_NETWORK_BOUNDARY.md`
6. `APP_SAFE_R6_BUILD_DEPENDENCY_INSTALL.md`
7. `APP_SAFE_R7_NATIVE_PLUGIN_RUNTIME.md`
8. `APP_SAFE_R8_UI_DEVICE_POWER.md`
9. `APP_SAFE_R9_VERIFICATION_COMPLETENESS.md`

Tujuan akhir adalah status `APPLICATION_SAFE_100` terhadap **closed application domain** yang ditetapkan untuk ToolBox pada Android 11 / API 30 / ARM64.

Android 11 / API30 / ARM64 pada kalimat di atas adalah **target produk/final compatibility domain**, bukan hard lock untuk seluruh GitHub development test environment.

Dokumen ini tidak menggantikan `ASSET_SAFE_100_RULES.md`, `ASSET_SAFE_100_PROCESS.md`, `PREBUILD_ASSET_GATE.md`, `AGENT_PROCEDURE_EXECUTION_RULES.md`, atau `TEST_ROUTING_POLICY.md`.

Bila scope aplikasi menyentuh asset/resource, seluruh asset gate tetap wajib dan menjadi dependency dari application-wide acceptance.

Seluruh eksekusi wajib mengikuti `AGENT_PROCEDURE_EXECUTION_RULES.md`.

Seluruh keputusan test environment dan Firebase authorization WAJIB mengikuti `TEST_ROUTING_POLICY.md`.

---

## 2. Prinsip Domain Order

Urutan ownership/domain resmi:

```text
R1  LOGIC / EXCEPTION / INPUT
 ↓
R2  CONCURRENCY / RESPONSIVENESS / RESOURCE
 ↓
R3  LIFECYCLE / COMPONENT / STATE / RECOVERY
 ↓
R4  PERSISTENCE / DATABASE / STORAGE / VERSIONED STATE
 ↓
R5  SECURITY / PERMISSION / NETWORK / EXTERNAL BOUNDARY
 ↓
R6  BUILD / DEPENDENCY / MANIFEST / SHRINK / INSTALL
 ↓
R7  NATIVE / ABI / PLUGIN / REFLECTION / THIRD-PARTY RUNTIME
 ↓
R8  UI / RENDERING / WEBVIEW / HARDWARE / VENDOR / POWER
 ↓
R9  APPLICATION-WIDE VERIFICATION COMPLETENESS
```

Urutan ini tidak berarti seluruh runtime proof R1 harus selesai sebelum source analysis R2 boleh dimulai. Urutan ini menentukan **ownership, staged review, dan final closure order**. Build boundary diatur oleh rantai operasional pada bagian berikut.

---

## 3. Status Resmi

Status domain:

```text
APP_SAFE_R1_PREBUILD_PASS
APP_SAFE_R2_PREBUILD_PASS
APP_SAFE_R3_PREBUILD_PASS
APP_SAFE_R4_PREBUILD_PASS
APP_SAFE_R5_PREBUILD_PASS
APP_SAFE_R6_PREBUILD_PASS
APP_SAFE_R7_PREBUILD_PASS
APP_SAFE_R8_PREBUILD_PASS

APP_SAFE_R1_PASS
APP_SAFE_R2_PASS
APP_SAFE_R3_PASS
APP_SAFE_R4_PASS
APP_SAFE_R5_PASS
APP_SAFE_R6_PASS
APP_SAFE_R7_PASS
APP_SAFE_R8_PASS
APP_SAFE_R9_PASS

APPLICATION_PREBUILD_PASS
APPLICATION_SAFE_100
```

Status routing tambahan dari `TEST_ROUTING_POLICY.md` dapat hidup di samping status di atas:

```text
DEVELOPMENT_PASS
FINAL_GATE_WAITING_USER_APPROVAL
FIREBASE_AUTHORIZED_ONCE
FIREBASE_AUTHORIZATION_CONSUMED
FIREBASE_TARGET_PASS
FINAL_GATE_NOT_PROVEN
```

`DEVELOPMENT_PASS` bukan sinonim `APPLICATION_SAFE_100` dan tidak otomatis membuka Firebase.

Status berikut **bukan PASS final**:

```text
UNKNOWN
PARTIAL
SKIPPED
NOT_RUN
NOT_READ
NOT_LOADED
NOT_PROVEN
INCOMPLETE
INCOMPLETE_PROOF
INDETERMINATE
INDETERMINATE_TOOL
ASSUMED
STALE_EVIDENCE
```

---

# 4. Rantai Operasional Wajib

```text
A0  RULE ENTRY / APPLICATION SCOPE LOCK
 ↓
A1  R1 PREBUILD CLOSURE
 ↓
A2  R2 PREBUILD CLOSURE
 ↓
A3  R3 PREBUILD CLOSURE
 ↓
A4  R4 PREBUILD CLOSURE
 ↓
A5  R5 PREBUILD CLOSURE
 ↓
A6  R6 PREBUILD / BUILD-INPUT CLOSURE
 ↓
A7  R7 PREBUILD NATIVE / PLUGIN CLOSURE
 ↓
A8  R8 PREBUILD UI / DEVICE / POWER PLAN CLOSURE
 ↓
A9  ASSET + APPLICATION PREBUILD CLOSURE
 ↓
================ BUILD BOUNDARY ================
 ↓
A10 CONTROLLED GITHUB ACTIONS BUILD
 ↓
A11 R6 FINAL ARTIFACT / SIGN / INSTALL VERIFICATION
 ↓
A12 R1–R5 FINAL RUNTIME / INTEGRATION CLOSURE
 ↓
A13 R7 NATIVE / PLUGIN RUNTIME CLOSURE
 ↓
A14 R8 UI / DEVICE / WEBVIEW / POWER RUNTIME CLOSURE
 ↓
A15 CROSS-DOMAIN INTERACTION CHALLENGE
 ↓
A16 R1–R8 FINAL DOMAIN ACCEPTANCE
 ↓
A17 R9 APPLICATION-WIDE VERIFICATION COMPLETENESS
 ↓
A18 FINAL APPLICATION_SAFE_100 ACCEPTANCE
```

Setiap stage hanya dapat dibuka jika stage sebelumnya memenuhi prerequisite yang berlaku.

**Catatan routing wajib:** A11–A15 tidak berarti semua test harus berjalan pada Android 11 ARM64. Development/regression execution menggunakan GitHub environment yang fleksibel sesuai `TEST_ROUTING_POLICY.md`. Jika final target-specific Android 11 ARM64 witness dibutuhkan untuk menutup suatu stage/claim, agen harus berhenti dan meminta single-use user approval sebelum Firebase.

---

## 4A. Test-Environment dan Firebase Authority Boundary

Target produk:

```text
Android 11 / API30 / arm64-v8a
```

GitHub development test environment:

```text
FLEXIBLE
```

Firebase final test environment:

```text
HARD LOCK = Android 11 / API30 / arm64-v8a
```

Aturan wajib:

1. seluruh test yang dapat dijalankan secara sah di GitHub harus dijalankan di GitHub terlebih dahulu;
2. GitHub boleh memakai API/ABI/emulator yang tersedia dan relevan;
3. environment non-target tidak boleh diklaim sebagai final target proof;
4. setelah affected GitHub checks mencapai `DEVELOPMENT_PASS`, agen wajib berhenti sebelum Firebase;
5. agen wajib bertanya kepada pengguna apakah kandidat tersebut diizinkan masuk Firebase Final Gate;
6. tanpa jawaban eksplisit, Firebase tetap `LOCKED`;
7. satu jawaban approval hanya berlaku untuk satu Firebase execution attempt;
8. setelah attempt dimulai, approval habis walaupun hasil PASS/FAIL/error/timeout/cancel;
9. retry/second run selalu membutuhkan approval baru;
10. Firebase tidak boleh fallback dari API30/ARM64 ke target lain.

Tidak ada stage A11–A18 yang boleh ditafsirkan sebagai kewenangan agen untuk menjalankan Firebase tanpa approval pengguna.

---

# 5. A0 — Rule Entry / Application Scope Lock

Wajib baca:

- `AGENTS.md`;
- `AGENT_PROCEDURE_EXECUTION_RULES.md`;
- `TEST_ROUTING_POLICY.md`;
- `APPLICATION_SAFE_100_PROCESS.md`;
- seluruh `APP_SAFE_R1...R9` yang relevan dengan pekerjaan aktif;
- `PREBUILD_ASSET_GATE.md`, `ASSET_SAFE_100_RULES.md`, `ASSET_SAFE_100_METHODS.md`, `ASSET_SAFE_100_PROCESS.md` bila asset/resource termasuk scope;
- `ASSET_ROUTE_PROOF_METHODS.md` dan `ASSET_ROUTE_PROOF_PROCESS.md` bila asset route/reference/resolution termasuk scope.

Kunci minimal:

```text
APPLICATION_REQUIREMENT_UNIVERSE
SUPPORTED_PLATFORM = Android 11 / API 30
SUPPORTED_ABI = arm64-v8a
SUPPORTED_DEVICE_ENVIRONMENT_BOUNDARY
SUPPORTED_INPUT_DOMAIN
SUPPORTED_STATE_CONFIGURATION_DOMAIN
DEPENDENCY / PLUGIN / EXTERNAL SERVICE DOMAIN
PERSISTENT_VERSION DOMAIN
APPLICATION_FAULT_UNIVERSE
EVIDENCE / TOOL TRUST BOUNDARY
TEST_ROUTING_BOUNDARY
FIREBASE_AUTHORIZATION_STATE
```

`SUPPORTED_PLATFORM` dan `SUPPORTED_ABI` adalah target product/final claim boundary. Mereka tidak memaksa seluruh GitHub test environment identik.

Jika boundary belum diketahui, `A0 = NOT_PROVEN`.

---

# 6. A1 — R1 Prebuild Closure

Sebelum runtime/build, selesaikan bagian R1 yang dapat dan harus dibuktikan dari requirement/model/source:

- requirement quality and traceability;
- contracts/type/null/range rules;
- formal/static analysis yang diwajibkan;
- input partitions/decision model;
- numeric/time/Unicode semantics;
- exception-flow design;
- protocol/typestate model;
- R1 fault model and mutation plan;
- required runtime witness/test plan.

Output:

```text
APP_SAFE_R1_PREBUILD_PASS
```

---

# 7. A2 — R2 Prebuild Closure

Wajib menutup:

- shared-state/resource universe;
- ownership/thread confinement;
- happens-before and synchronization model;
- lock order/progress model;
- main-thread policy;
- queue/backpressure policy;
- resource budgets;
- cancellation/timeouts;
- systematic-schedule and stress/fault plan.

Output:

```text
APP_SAFE_R2_PREBUILD_PASS
```

---

# 8. A3 — R3 Prebuild Closure

Wajib menutup:

- component/lifecycle universe;
- state machines;
- state classification/source of truth;
- save/restore/process-death contract;
- navigation/Intent/IPC contract;
- startup dependency graph;
- recovery/rollback model;
- transition fault plan.

Output:

```text
APP_SAFE_R3_PREBUILD_PASS
```

---

# 9. A4 — R4 Prebuild Closure

Wajib menutup:

- persistent store universe;
- transaction/invariant contracts;
- schema history and migration graph;
- serialization compatibility model;
- atomic-file/journal protocol;
- crash-consistency test plan;
- corruption/recovery policy;
- backup/version compatibility matrix.

Output:

```text
APP_SAFE_R4_PREBUILD_PASS
```

---

# 10. A5 — R5 Prebuild Closure

Wajib menutup:

- trust-boundary/data-flow universe;
- threat model;
- permission/least privilege;
- authentication/authorization contract;
- untrusted-input validation;
- TLS/trust/network policy;
- timeout/retry/idempotency/offline model;
- external schema/service contracts;
- security/network fault plan.

Output:

```text
APP_SAFE_R5_PREBUILD_PASS
```

---

# 11. A6 — R6 Prebuild / Build-Input Closure

Wajib menutup sebelum build:

- closed build-input universe;
- toolchain pinning;
- dependency locking/integrity/compatibility;
- provenance expectations;
- clean-build/hermeticity policy;
- variant matrix;
- final-manifest expected model;
- R8/keep/reflection reachability model;
- expected signing identity;
- expected APK/package/install contract;
- CI workflow mandatory gates.

Output:

```text
APP_SAFE_R6_PREBUILD_PASS
```

---

# 12. A7 — R7 Prebuild Native / Plugin Closure

Wajib menutup:

- native/plugin/reflection universe;
- ABI/JNI/native symbol contracts;
- sanitizer/static/fuzz plan;
- plugin metadata/interface/capability contract;
- classloader/reflection registry;
- dynamic-code provenance policy;
- third-party runtime qualification plan;
- failure-isolation model.

Output:

```text
APP_SAFE_R7_PREBUILD_PASS
```

---

# 13. A8 — R8 Prebuild UI / Device / Power Plan Closure

Wajib menutup:

- UI surface/flow universe;
- screen/config/device equivalence classes;
- accessibility oracle;
- startup/frame budgets;
- WebView configuration contract;
- hardware capability/fallback contracts;
- vendor/real-device witness matrix;
- Doze/App Standby/background restriction plan;
- system-interruption scenarios.

Output:

```text
APP_SAFE_R8_PREBUILD_PASS
```

---

# 14. A9 — Asset + Application Prebuild Closure

Build hanya boleh dibuka jika:

```text
APP_SAFE_R1_PREBUILD_PASS
AND APP_SAFE_R2_PREBUILD_PASS
AND APP_SAFE_R3_PREBUILD_PASS
AND APP_SAFE_R4_PREBUILD_PASS
AND APP_SAFE_R5_PREBUILD_PASS
AND APP_SAFE_R6_PREBUILD_PASS
AND APP_SAFE_R7_PREBUILD_PASS
AND APP_SAFE_R8_PREBUILD_PASS
AND PREBUILD_ASSET_GATE = PASS  [bila asset/resource scope berlaku]
AND ROUTE_PROOF_PASS            [bila asset route scope berlaku]
AND UNKNOWN = 0
AND SKIPPED = 0
AND NOT_PROVEN = 0
AND STALE_EVIDENCE = 0
```

Output:

```text
APPLICATION_PREBUILD_PASS
```

Tanpa status tersebut, build APK / production build / final packaging **DILARANG**.

---

# 15. A10 — Controlled GitHub Actions Build

Build APK hanya melalui GitHub Actions sesuai `AGENTS.md`.

Build wajib menggunakan exact prebuild inputs/provenance yang telah dibuktikan. Perubahan input setelah `APPLICATION_PREBUILD_PASS` membatalkan status tersebut dan proses kembali ke stage paling awal yang terdampak.

Build artifact tetap harus memenuhi target product contract Android 11/API30/ARM64, tetapi runtime environment untuk development tests setelah build boleh fleksibel sesuai `TEST_ROUTING_POLICY.md`.

---

# 16. A11 — R6 Final Artifact / Sign / Install Verification

Setelah APK final ada:

- verify artifact provenance/digest;
- inspect final manifest/package/sdk/ABI;
- verify shrink/reachability result;
- verify signing certificate/lineage;
- run clean install and supported upgrade matrix;
- verify install/update failure cases;
- verify mapping/retrace evidence.

Development/regression install testing dijalankan di GitHub menggunakan environment yang tersedia dan relevan.

Jika final claim memerlukan actual Android 11/API30/ARM64 install witness, witness tersebut hanya boleh dijalankan pada Firebase Final Gate setelah explicit single-use user approval.

R6 baru dapat menjadi final:

```text
APP_SAFE_R6_PASS
```

setelah seluruh required postbuild proof selesai.

Jika target-specific witness masih menunggu user approval, pertahankan development status; jangan menjalankan Firebase otomatis.

---

# 17. A12 — R1–R5 Final Runtime / Integration Closure

Jalankan runtime evidence yang tidak mungkin diselesaikan sebelum APK final:

- R1 input/path/exception/numeric/time/logic runtime witnesses;
- R2 systematic/stress/resource/ANR/leak/runtime budget witnesses;
- R3 lifecycle/recreation/process death/IPC/startup/recovery witnesses;
- R4 migration/crash/corruption/storage/backup runtime witnesses;
- R5 permission/network/offline/TLS/external fault/security runtime witnesses.

GitHub environment untuk runtime/integration development evidence bersifat fleksibel. API/ABI aktual wajib dicatat.

Jika suatu claim benar-benar target-specific terhadap Android 11/API30/ARM64 dan belum terbukti di GitHub secara authoritative, tandai sebagai pending Final Gate. A12 tidak memberikan izin Firebase.

Output final wajib:

```text
APP_SAFE_R1_PASS
APP_SAFE_R2_PASS
APP_SAFE_R3_PASS
APP_SAFE_R4_PASS
APP_SAFE_R5_PASS
```

---

# 18. A13 — R7 Native / Plugin Runtime Closure

Jalankan:

- real load of every required native library/plugin;
- JNI exercise;
- sanitizer/fuzz/static finding disposition;
- plugin compatibility/rejection;
- reflection/classloader exercise;
- crash isolation/reload/restart;
- native symbolization evidence.

Development tests boleh menggunakan GitHub ABI/runtime yang tersedia sesuai `APP_SAFE_R7_NATIVE_PLUGIN_RUNTIME.md`.

Structural proof bahwa artifact release berisi ARM64 berbeda dari actual ARM64 runtime witness. Jika actual ARM64/API30 witness diperlukan, Firebase hanya boleh dijalankan setelah user approval satu kali.

Output final:

```text
APP_SAFE_R7_PASS
```

---

# 19. A14 — R8 UI / Device / WebView / Power Runtime Closure

Jalankan:

- full required UI-flow automation;
- screenshots/semantic visual assertions;
- accessibility checks;
- screen/window/config matrix;
- startup/frame/jank budgets;
- WebView scenarios;
- hardware unavailable/interruption scenarios;
- representative real-device witnesses;
- Doze/App Standby/background restrictions;
- graceful-degradation verification.

GitHub UI/device regression matrix bersifat fleksibel dan tidak dikunci ke API30/ARM64.

Jika target-specific Android 11/API30/ARM64 virtual-device witness dibutuhkan, Firebase Final Gate tetap memerlukan explicit single-use user approval.

Jika claim memerlukan physical/vendor hardware evidence, Firebase virtual device tidak menggantikan real/authoritative device witness.

Output final:

```text
APP_SAFE_R8_PASS
```

---

# 20. A15 — Cross-Domain Interaction Challenge

Sebelum final domain acceptance, gabungkan faktor dari domain berbeda.

Minimum candidate interactions bila relevan:

```text
R1 input × R5 remote data
R2 resource pressure × R8 UI rendering
R2 concurrency × R4 transaction
R3 process death × R4 persistence
R3 recovery × R5 retry/idempotency
R3 lifecycle × R7 plugin/native callbacks
R5 network failure × R4 offline persistence
R6 shrinking × R7 reflection/JNI/plugin
R6 update × R4 versioned state
R8 hardware loss × R3 lifecycle
ASSET configuration × R8 screen/locale/state
```

Gunakan exhaustive combinations bila finite/feasible; bila tidak, gunakan model/constraints + combinatorial strength yang eksplisit serta targeted high-risk sequences.

Development cross-domain tests boleh dijalankan pada GitHub environment yang sesuai. Target-specific gap tetap tunduk pada Final Gate authority boundary.

Output:

```text
CROSS_DOMAIN_CLOSURE = PASS
```

---

## 20A. Development PASS → Mandatory User Decision

Jika implementation dan seluruh affected GitHub checks yang dapat dijalankan telah PASS, agen dapat mencapai:

```text
DEVELOPMENT_PASS
```

Pada titik ini agen WAJIB berhenti sebelum Firebase dan bertanya:

```text
Apakah Anda mengizinkan 1x eksekusi Firebase Final Gate
untuk kandidat ini pada Android 11 / API30 / ARM64?
```

Jika pengguna tidak menyetujui:

```text
FIREBASE = LOCKED
```

Jika pengguna menyetujui:

```text
FIREBASE = AUTHORIZED_ONCE
```

Setelah satu execution attempt dimulai:

```text
AUTHORIZATION = CONSUMED
FIREBASE = LOCKED AGAIN
```

Run/retry berikutnya wajib meminta approval baru.

---

# 21. A16 — R1–R8 Final Domain Acceptance

Pastikan:

```text
APP_SAFE_R1_PASS
AND APP_SAFE_R2_PASS
AND APP_SAFE_R3_PASS
AND APP_SAFE_R4_PASS
AND APP_SAFE_R5_PASS
AND APP_SAFE_R6_PASS
AND APP_SAFE_R7_PASS
AND APP_SAFE_R8_PASS
AND CROSS_DOMAIN_CLOSURE = PASS
```

Tidak ada finding critical/high yang unresolved sesuai severity model aktif, dan tidak ada required proof berstatus non-PASS.

Jika target-specific Final Gate evidence diwajibkan oleh active claims, evidence tersebut harus berasal dari execution yang sah: Android 11/API30/ARM64 dan mempunyai single-use user approval untuk attempt tersebut.

---

# 22. A17 — R9 Application-Wide Verification Completeness

Jalankan seluruh `APP_SAFE_R9_VERIFICATION_COMPLETENESS.md`:

- merge/deduplicate fault universe;
- requirement/risk/fault/method/evidence traceability;
- ownership gap audit;
- cross-domain coverage audit;
- structural/combinatorial/state coverage;
- mutation/fault injection closure;
- independent V&V/oracle diversity;
- tool qualification/trusted-base audit;
- evidence provenance/freshness;
- defeater analysis;
- assurance case graph;
- unknown/skipped/inconclusive closure.

R9 boleh menentukan bahwa Final Gate evidence diperlukan, tetapi R9 tidak boleh mengotorisasi atau menjalankan Firebase sendiri.

Output:

```text
APP_SAFE_R9_PASS
```

---

# 23. A18 — Final APPLICATION_SAFE_100 Acceptance

Final status hanya boleh diberikan jika:

```text
APP_SAFE_R1_PASS
AND APP_SAFE_R2_PASS
AND APP_SAFE_R3_PASS
AND APP_SAFE_R4_PASS
AND APP_SAFE_R5_PASS
AND APP_SAFE_R6_PASS
AND APP_SAFE_R7_PASS
AND APP_SAFE_R8_PASS
AND APP_SAFE_R9_PASS
AND REQUIRED_ASSET_SAFE_100_IF_APPLICABLE
AND CROSS_DOMAIN_CLOSURE
AND APPLICATION_REQUIREMENT_COVERAGE = 100%
AND APPLICATION_FAULT_MODEL_COVERAGE = 100%
AND REQUIRED_EVIDENCE_COVERAGE = 100%
AND UNKNOWN = 0
AND MISSING = 0
AND UNPROVEN = 0
AND SKIPPED = 0
AND INDETERMINATE = 0
AND STALE_EVIDENCE = 0
AND FAULT_ESCAPE = 0
AND UNRESOLVED_DEFEATER = 0
```

Jika Firebase Final Gate evidence termasuk required evidence, evidence hanya sah bila:

```text
TARGET = Android 11 / API30 / arm64-v8a
AND USER_APPROVAL_FOR_THAT_ATTEMPT = VALID
AND APPROVAL_REUSE = 0
```

Status akhir:

```text
APPLICATION_SAFE_100
```

---

# 24. Batas Klaim

`APPLICATION_SAFE_100` berarti seluruh requirement, domain, state/configuration, dependency/environment boundary, fault classes, and evidence yang **didefinisikan dalam closed application scope** telah dibuktikan sesuai framework ini.

Status ini tidak berarti hukum fisika, OS/hardware di luar supported environment, external service di luar contract, atau unknown future fault yang tidak termasuk closed domain secara metafisik dijamin tidak pernah gagal.

Jika scope/boundary diperluas atau fault class baru ditemukan, final claim yang terdampak wajib dibuka kembali.

GitHub development environment yang fleksibel tidak memperluas claim ke target yang tidak diuji. Firebase target evidence hanya berlaku pada scope execution yang benar-benar dijalankan.

---

# 25. Anti-Skip dan Anti-Auto-Firebase

Dilarang:

- melompati A-stage;
- build sebelum `APPLICATION_PREBUILD_PASS`;
- memakai build sukses sebagai pengganti prebuild proof;
- memakai test hijau sebagai pengganti fault-model closure;
- menganggap satu emulator/device sebagai seluruh device domain;
- menganggap satu analyzer/fuzzer/oracle cukup untuk semua claim;
- mengubah tool failure menjadi PASS;
- mempertahankan PASS setelah input/evidence berubah tanpa change-impact proof;
- menghapus fault class hanya agar final status hijau;
- mengurangi prosedur karena context/memory agen tidak cukup;
- menganggap target Android 11 ARM64 berarti semua GitHub tests harus Android 11 ARM64;
- menganggap GitHub PASS otomatis membuka Firebase;
- menjalankan Firebase tanpa explicit user approval;
- memakai satu user approval untuk lebih dari satu execution attempt;
- auto-retry Firebase setelah PASS/FAIL/error/timeout/cancel;
- fallback Firebase ke API/ABI selain Android 11/API30/ARM64;
- menggunakan approval lama untuk kandidat baru atau retry.

Final principle:

```text
GITHUB DEVELOPMENT TESTING = FLEXIBLE
FIREBASE FINAL TARGET = ANDROID 11 / API30 / ARM64 ONLY
1 USER APPROVAL = 1 FIREBASE EXECUTION ATTEMPT
```
