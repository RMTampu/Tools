# TASK.md - Tugas Aktif Global

## Tugas Aktif

Kerjakan **Tahap 5 = E — Bubble, Edge Panel, Floating Editor, dan pengeditan visual** pada repo publik utama `RMTampu/Tools`.

Baseline resmi aktif adalah **Baseline Tahap 4**, exact APK Android 11/API30/arm64 yang sudah signed, R1-R9 + ASSET_SAFE PASS, lulus Firebase final test, dan terkunci pada jalur private.

Tahap 5 wajib menambah kemampuan di atas Tahap 4. Baseline Tahap 4 tidak boleh dimodifikasi.

## Scope Tahap 5

### E1 — Bubble Quick Access
- draggable dan bounded.
- tidak touch-through.
- safe position portrait/landscape.
- tap membuka/menutup panel.
- Edit ON/OFF, Tool, Pengaturan, Floating Window.
- reset shell selalu tersedia.

### E2 — Contextual Multi-Function Edge Panel
- satu panel; isi berubah menurut Editor Function, Edit ON/OFF, selected object, dan operation context.
- header Back/breadcrumb/context title/close.
- handle tap/drag/long-press + safe clamp.
- UI / Logic / Data / Binding / Asset.
- satu heavy function aktif pada satu waktu.
- saat UI tanpa selection: Komponen / Template / Kit / Asset / Recent / Favorite.
- saat object dipilih: menu capability-aware Style sampai Others.

### E3 — Floating Editor Framework
- satu primary Floating Editor aktif.
- draggable, pinned, close, safe bounds, IME-safe.
- auto-placement menghindari selected object bila mungkin.
- X hanya menutup editor; tidak revert Working State.
- perubahan masuk Working State.
- gesture edit = satu undo transaction.

### E4 — Visual Editing Working State
- Stable Object ID.
- selection dan add-to-screen tidak mengubah master component.
- Visual/Properties/Code tetap memakai shared model Tahap 4.
- edit OFF = normal interaction; edit ON = selection/edit.
- EDIT/PREVIEW/TEST/LIVE state eksplisit.
- Preview menyembunyikan overlay editor.
- LIVE hanya jika capability tersedia.
- lock protection per area.
- move/resize/content/style/state/transform dan property editing memakai generic visual operation contract.
- broken/unsupported operation = diagnostic, bukan silent mutation.
- history bounded dan undo/redo kronologis.

## Exit Gate Tahap 5

```text
BASELINE_TAHAP_4_UNCHANGED = YES
VERSION_CODE_GT_TAHAP_4 = YES
BUBBLE_BOUNDED = PASS
BUBBLE_ORIENTATION_POSITION = PASS
BUBBLE_NO_TOUCH_THROUGH = PASS
EDGE_CONTEXTUAL = PASS
ONE_HEAVY_FUNCTION = PASS
EDGE_SAFE_CLAMP = PASS
FLOATING_EDITOR_SINGLE_PRIMARY = PASS
FLOATING_EDITOR_SAFE_PLACEMENT = PASS
FLOATING_CLOSE_NO_REVERT = PASS
VISUAL_WORKING_STATE = PASS
STABLE_OBJECT_ID = PASS
CAPABILITY_AWARE_MENU = PASS
EDIT_OFF_NORMAL_INTERACTION = PASS
EDIT_ON_SELECTION = PASS
EDIT_PREVIEW_TEST_LIVE = PASS
LIVE_CAPABILITY_GATE = PASS
LOCK_PROTECTION = PASS
GESTURE_ONE_UNDO_TRANSACTION = PASS
UNDO_REDO_BOUNDED = PASS
BROKEN_OPERATION_DIAGNOSTIC = PASS
R1_R9_AUTOMATIC = PASS
ASSET_SAFE_REGRESSION = PASS
ANDROID_11_API30_BUILD = PASS
PUBLIC_UNSIGNED_APK = PASS
UNKNOWN_REQUIRED_TAHAP_5_BEHAVIOR = 0
```

Private tetap hanya untuk signing, credential, Firebase final runtime test, baseline locking, dan release sensitif.
