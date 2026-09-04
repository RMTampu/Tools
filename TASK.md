# TASK.md - Tugas Aktif Global

## Tugas Aktif

Kerjakan **Tahap 4 = D — Renderer, model bersama, navigation, event/action, data, binding, dan flow** pada repo publik utama `RMTampu/Tools`.

Baseline resmi aktif adalah **Baseline Tahap 3**, exact APK Android 11/API30/arm64 yang sudah signed, R1-R9 + ASSET_SAFE PASS, lulus Firebase final test, dan terkunci pada jalur private.

Tahap 4 wajib menambah kemampuan di atas Tahap 3. Baseline Tahap 3 tidak boleh dimodifikasi.

## Scope Tahap 4

### D1 — Shared model + renderer
- Visual / Properties / Code membaca model runtime/declarative yang sama.
- Screen memakai Stable Screen ID.
- Render tree derived/rebuildable, bukan source of truth.
- Renderer tidak menyimpan clone screen.
- broken component/reference tetap menjadi diagnostic, tidak silent-delete.

### D2 — Navigation + Event/Action
- Navigation berdasarkan Stable Screen ID + typed parameters.
- Back stack hanya Screen ID + lightweight parameters/state.
- Action Registry memakai typed input/output, permission requirement, execution mode, timeout/cancellation/idempotency metadata.
- Event Binding hanya menghubungkan compatible event/action contracts.
- Composite action memiliki ordered steps + success/failure/fallback/compensation metadata.
- broken navigation = `BROKEN_NAVIGATION_REFERENCE`.

### D3 — Data + Binding
- Data Source / Field memakai Stable ID + typed contract.
- query/page menghasilkan working subset.
- dynamic list identity memakai stable data-item key, bukan index.
- one-way dan two-way binding.
- two-way memakai origin/version token + cycle suppression.
- derived value pure; side effect tetap melalui Action/Logic.
- ambiguous/incompatible binding tidak auto-connect.

### D4 — Declarative Flow Graph
- Stable Flow/Node ID.
- explicit connection/port compatibility.
- branch TRUE/FALSE.
- async START/SUCCESS/FAILURE/CANCELLED/TIMEOUT.
- loop explicit exit + iteration/time limit.
- watchdog limit.
- diagram coordinate hanya editor metadata; graph logic tidak bergantung posisi.
- hanya active flow yang perlu dimaterialisasi penuh.

## Exit Gate Tahap 4

```text
BASELINE_TAHAP_3_UNCHANGED = YES
VERSION_CODE_GT_TAHAP_3 = YES
SHARED_MODEL = PASS
RENDER_TREE_DERIVED = PASS
STABLE_SCREEN_ID = PASS
NAVIGATION_REFERENCE_VALIDATION = PASS
LIGHTWEIGHT_BACK_STACK = PASS
ACTION_REGISTRY = PASS
EVENT_ACTION_COMPATIBILITY = PASS
COMPOSITE_ACTION = PASS
DATA_SOURCE_CONTRACT = PASS
PAGED_QUERY = PASS
STABLE_DATA_ITEM_KEY = PASS
ONE_WAY_BINDING = PASS
TWO_WAY_CYCLE_SUPPRESSION = PASS
FLOW_GRAPH = PASS
BRANCH_ASYNC_LOOP = PASS
FLOW_WATCHDOG = PASS
BROKEN_REFERENCE_DIAGNOSTICS = PASS
R1_R9_AUTOMATIC = PASS
ANDROID_11_API30_BUILD = PASS
PUBLIC_UNSIGNED_APK = PASS
UNKNOWN_REQUIRED_TAHAP_4_BEHAVIOR = 0
```

Private tetap hanya untuk signing, credential, Firebase final runtime test, baseline locking, dan release sensitif.
