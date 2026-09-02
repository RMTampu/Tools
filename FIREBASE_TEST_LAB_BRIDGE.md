# Firebase Test Lab — Public Repository Boundary

## 1. Status

`RMTampu/Tools` **bukan Firebase Final Gate bridge untuk artifact Private**.

Semua routing lama yang memakai pola berikut dinyatakan tidak berlaku:

```text
PRIVATE APK -> RMTampu/Tools PUBLIC -> FIREBASE
PRIVATE SOURCE -> PUBLIC BUILD -> FIREBASE
```

Final Firebase execution untuk ToolBox berada pada boundary Private `RMTampu/ToolBox`.

## 2. Yang Dilarang di Public

Repository ini tidak boleh:

- menerima APK/artifact Private;
- menerima source/asset/config/state Private;
- menerima credential untuk membaca Private;
- mengambil artifact dari run Private untuk diteruskan ke Firebase;
- menjalankan final Firebase matrix untuk candidate Private;
- menjadi relay/bridge final-test Private.

## 3. Yang Masih Boleh Dilakukan

Public boleh melakukan riset generik terhadap Firebase/Test Lab tanpa memakai isi Private, misalnya:

- mempelajari contract/API;
- membuat mock/simulator;
- menyusun test strategy;
- menguji prototype/dummy artifact yang memang Public;
- memvalidasi logic tooling terhadap fixture Public.

Hasil tersebut hanya evidence Public/research, bukan final ToolBox runtime proof.

## 4. Authorization Principle

Aturan generic tetap:

```text
FIREBASE DEFAULT = LOCKED
1 EXPLICIT USER APPROVAL = 1 FINAL FIREBASE EXECUTION ATTEMPT
NO AUTO RETRY
NO FALLBACK
NO APPROVAL REUSE
```

Tetapi authorization final candidate Private dieksekusi pada boundary Private, bukan dari repository ini.

## 5. Final Candidate Order

Jalur final Private yang menjadi referensi konseptual:

```text
BUILD APK
-> SIGN CANDIDATE
-> VERIFY SIGNATURE / HASH / PROVENANCE
-> FIREBASE / FINAL TEST
-> PASS
-> RELEASE EXACT SAME SIGNED CANDIDATE
```

Public tidak menjalankan tahapan tersebut terhadap isi Private.

## 6. Final Rule

```text
PUBLIC_FIREBASE_PRIVATE_ARTIFACT_BRIDGE = 0
PRIVATE_CONTENT_IN_PUBLIC = 0
PRIVATE_EXECUTION_THROUGH_PUBLIC = 0
PUBLIC_FINAL_FIREBASE = 0
```
