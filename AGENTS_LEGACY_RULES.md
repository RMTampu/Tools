# ToolBox Legacy Technical Rules — Public Research Edition

## Status

Dokumen ini mempertahankan **nilai teknis** aturan legacy, tetapi seluruh routing lama yang menempatkan `RMTampu/Tools` sebagai Build/Test/CI/Firebase engine untuk isi Private dinyatakan **OBSOLETE / FORBIDDEN**.

```text
PRIVATE_MASTER = RMTampu/ToolBox
PUBLIC_RESEARCH_STAGING = RMTampu/Tools
PRIVATE_EXECUTION_THROUGH_PUBLIC = FORBIDDEN
```

## Public Scope

`RMTampu/Tools` hanya boleh digunakan untuk:

- research/design;
- prototype;
- component development;
- mock/simulator/test harness;
- unit/contract/dependency/failure test terhadap data/component Public;
- audit/debugging Public;
- packaging dan staging sampai `READY_PRIVATE`.

Public dilarang membaca, checkout, menerima, membangun, menandatangani, menguji final, atau merelay source/asset/state/APK/artifact Private.

## Technical Methods

Metode teknis dalam file R1–R9, Asset Safe, route proof, dan procedure execution tetap boleh digunakan di Public **hanya terhadap scope Public**.

Metode tersebut tidak memberi kewenangan untuk:

```text
PRIVATE SOURCE -> PUBLIC BUILD
PRIVATE APK -> PUBLIC TEST/FIREBASE
PRIVATE CREDENTIAL -> PUBLIC
PUBLIC FINAL RELEASE
```

Final application assurance terhadap integrated Private state dilakukan pada boundary Private.

## Build/Test Public

GitHub Actions Public boleh membangun/menguji:

- component Public;
- prototype Public;
- mock;
- simulator;
- fixture/dummy data;
- test harness Public.

Ia tidak boleh membangun APK final dari source Private.

## Promotion

Komponen matang mengikuti:

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

Setelah `READY_PRIVATE`, hanya Promotion Package aman yang dipromosikan ke Private.

## Auto Cleanup

Setiap Public job wajib memiliki cleanup otomatis setelah berhasil maupun gagal sejauh platform memungkinkan.

## Firebase

Firebase Final Gate untuk artifact Private **bukan fungsi repository Public ini**.

Aturan generic tetap:

```text
FIREBASE DEFAULT = LOCKED
1 EXPLICIT USER APPROVAL = 1 FIREBASE EXECUTION ATTEMPT
NO AUTO RETRY
```

Eksekusi final dilakukan dari boundary Private.

## Final Authority

```text
Instruksi pengguna terbaru
-> AGENTS.md RULE 0
-> GLOBAL_PUBLIC_PRIVATE_DEVELOPMENT_RULES.md
-> REPOSITORY_INTEGRATION_POLICY.md
-> aturan Public yang tidak bertentangan
-> dokumen legacy
```

Jika teks legacy lain menyebut `RMTampu/Tools = CI engine` untuk isi Private, teks tersebut tidak berlaku.
