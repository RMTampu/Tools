# Rancangan ToolBox
## Visual Declarative App Factory + Managed Repair & Evolution Platform
### Rancangan Keseluruhan Terpadu

> **MASTER LOCATION:** `RMTampu/ToolBox` (private). `RMTampu/Tools` adalah public build/test/CI engine.
>
> **STATUS DOKUMEN:** fondasi konseptual historis yang tetap dipertahankan untuk konteks arsitektur. Bila bertentangan dengan `Rancangan-Editor-Terpadu.md`, `Rancangan-UI-Shell.md`, `MASTER-RANCANGAN.md`, atau `REPOSITORY_INTEGRATION_POLICY.md`, keputusan terbaru tersebut **menggantikan** bagian lama yang bertentangan.
>
> Khususnya: `Deck Panel` sudah dihapus dari Shell terbaru; lima editor terpisah pada UX sudah diganti satu `Editor` dengan UI/Logic/Data/Binding/Asset internal; batas `ToolBox-aware only` sudah diganti Capability Scan + Edit Bridge berdasarkan capability yang tersedia; dan pusat product/source/rancangan kini private `RMTampu/ToolBox`.

---

# 1. Identitas Produk

ToolBox adalah **satu aplikasi Android** yang menjadi **rumah bagi banyak tool/engine**.

ToolBox mempunyai dua keluarga kemampuan besar:

1. **Visual Declarative App Factory** — membuat aplikasi secara visual/deklaratif sampai menghasilkan project yang siap divalidasi dan dibuild di GitHub.
2. **Managed Repair & Evolution Platform** — memperbaiki, mengubah, menguji, membekukan, memulihkan, dan mengembangkan aplikasi melalui capability/contract/editing door yang tersedia dan diizinkan.

ToolBox **bukan APK scanner umum** dan tidak dirancang untuk membypass sandbox/signature Android.

Target produk utama:

```text
Android 11
API 30
arm64-v8a
```

APK final dibangun melalui GitHub Actions. HP dipakai untuk merancang, mengedit, memvalidasi, mengelola project, membuat paket build, menjalankan preview, dan mengelola lifecycle ToolBox.

---

# 2. Prinsip Besar

Fondasi ToolBox mengikuti prinsip berikut:

- satu APK host;
- banyak tool/engine mandiri;
- hanya tool yang diperlukan yang aktif;
- hanya screen/working sector yang diperlukan yang aktif;
- Project Store adalah sumber kebenaran utama;
- RAM hanya working set;
- cache selalu dapat dibuang;
- data project milik pengguna tetap terlihat di penyimpanan;
- integrasi antar-tool melalui Stable ID dan contract;
- tidak ada dependency runtime langsung antar-tool;
- UI dibuat visual-first;
- code hanya fallback untuk kebutuhan khusus dan tetap menjadi input build, bukan kode yang dieksekusi dinamis di perangkat;
- perubahan project disimpan secara manual;
- Save selalu transactional;
- tidak ada cloning screen untuk masuk Edit Mode;
- tidak ada pemuatan seluruh project hanya untuk mengedit satu bagian;
- GitHub bukan tempat menemukan kesalahan dasar project;
- build hanya dibuka jika validator lokal menyatakan project siap;
- kegagalan satu tool tidak boleh menjatuhkan seluruh host;
- setiap resource harus dapat dilacak, divalidasi, dan direkonstruksi dari data project.

Ringkasnya:

```text
Storage-first
Contract-first
Stable-ID-first
Visual-first
Lazy-load
Bounded working set
Manual transactional save
Failure isolation
```

---

# 3. Arsitektur Rumah ToolBox — Fondasi dan Override Terkini

Fondasi internal tetap berupa host, registry, services, dan engine/module. Pada UX terkini, kemampuan UI/Logic/Data/Binding/Asset berada di bawah satu entry `Editor`.

```text
ToolBox Host
│
├─ Shell UI
│  ├─ Bubble
│  ├─ Floating Window
│  └─ Multi-Function Edge Panel
│
├─ Editor
│  ├─ UI
│  ├─ Logic / Flow
│  ├─ Data
│  ├─ Binding
│  └─ Asset
├─ Build Manager
├─ Recovery Manager
├─ Freeze / Evolution Manager
└─ engine/module lain
```

Tool tidak dianggap aplikasi terpisah secara instalasi. Semuanya berada dalam satu APK, tetapi dipisahkan melalui contract dan lifecycle.

Setiap tool hanya mengetahui tool lain melalui Tool Registry, Component Registry, Capability Registry, Action Registry, Event Contract, Property Contract, Data Contract, Navigation Contract, Permission Contract, Stable IDs, dan dependency metadata.

Tidak ada kebutuhan untuk menyimpan reference hidup dari satu tool ke tool lain.

---

# 4. Halaman sebagai Wujud Tool/Engine

Halaman adalah **bentuk visual aktif dari ToolBox atau engine yang sedang digunakan**. ToolBox sendiri tidak perlu memenuhi layar dengan chrome/editor permanen. Area utama layar adalah halaman aktif.

Contoh:

```text
Shell ToolBox
   ↓
Editor > UI
```

atau:

```text
Shell ToolBox
   ↓
Editor > Logic
```

atau:

```text
Shell ToolBox
   ↓
Halaman Recovery
```

Dengan demikian layar utama selalu dapat digunakan semaksimal mungkin oleh fungsi aktif.

---

# 5. Lifecycle Tool

Setiap tool mengikuti lifecycle konseptual:

```text
COLD
↓
LOAD
↓
ACTIVE
↓
SAVE bila pengguna meminta
↓
RELEASE
↓
COLD / INACTIVE
```

## 5.1 LOAD

Hanya memuat metadata yang diperlukan, screen/resource aktif, contract terkait, asset preview yang diperlukan, dan working state kecil.

## 5.2 ACTIVE

Tool mempunyai working set sendiri dan tidak membuat tool lain aktif tanpa kebutuhan nyata.

## 5.3 SAVE

Save bukan autosave. Save hanya berjalan ketika pengguna secara eksplisit memerintahkannya atau ketika operasi sistem yang memang didefinisikan sebagai commit eksplisit dilakukan.

## 5.4 RELEASE

Saat tool dilepas, resource runtime harus dilepas: Activity/View/editor surface yang tidak lagi dipakai, renderer, bitmap, preview high-resolution, render buffer, temporary graph, parser result, listener, observer, callback, coroutine/job, thread, timer, stream, file handle, RAM cache, temporary import/export buffer, reference Context/View lama, dan dependency runtime antartool.

Yang tetap ada adalah data persistent dan state kecil yang memang diperlukan untuk kembali ke pekerjaan tersebut.

---

# 6. Shell UI ToolBox — Override Terkini

Shell terkini menggunakan:

```text
Bubble
= pusat akses cepat / command trigger

Floating Window
= menu/command luas yang dipanggil Bubble

Multi-Function Edge Panel
= panel kontekstual untuk component/asset/property/fungsi Editor aktif
```

`Deck Panel` **tidak lagi digunakan** pada rancangan terkini. Rincian final Shell mengikuti `Rancangan-UI-Shell.md`.

---

# 7. Bubble — Draggable Priority Overlay + Floating Window Trigger

Bubble adalah **Top-Layer Draggable Floating Overlay** yang menjadi pusat akses cepat ToolBox.

```text
Bubble Quick Access
├─ Edit ON / OFF
├─ Tool
├─ Pengaturan
└─ Floating Window
```

Bubble dapat digeser bebas dalam safe bounds, menyimpan posisi per orientation bila diperlukan, tidak memakai touch-through sebagai default, dan mempunyai prioritas sentuhan terhadap UI project pada area Bubble.

Floating Window bersifat context-aware, aman terhadap bounds/IME, dapat ditutup dengan tap di luar yang dikonsumsi, dan tidak menjadi bagian dari project aplikasi hasil build.

---

# 8. Multi-Function Edge Panel

Edge tampil sebagai handle tipis saat tertutup, tetapi touch target transparannya cukup besar untuk digunakan.

Edge mendukung:

```text
Tap → buka / tutup
Drag biasa → buka / tutup progresif
Long Press → mode pindah posisi
→ anchor valid
→ short-drag/tap anchor
→ snap
```

Isi penuh dimuat lazy. Posisi mengikuti safe bounds dan dapat disimpan per orientation.

Pada UI Editor, Edge dapat berfungsi sebagai sumber component/asset, lalu berubah menjadi menu edit contextual saat object dipilih. Pada Logic/Data/Binding/Asset, isi Edge mengikuti capability aktif.

---

# 9. Live Interactive UI Workspace

UI Editor bukan canvas statis. Pengguna dapat menjalankan aplikasi yang sedang dirancang seperti aplikasi hidup, kemudian masuk Edit Mode saat ingin mengubahnya.

```text
Home
↓ tekan Settings
Settings terbuka
↓ buka Privacy
Privacy terbuka
↓ Bubble → Edit ON
↓
edit keadaan visual yang sedang terlihat
```

Navigation graph dihasilkan dari hubungan yang benar-benar dibuat, bukan harus digambar lebih dulu.

---

# 10. Edit OFF dan Edit ON

## 10.1 Edit OFF

```text
EDIT = OFF
```

Layar bekerja seperti aplikasi: button menjalankan event, navigation berjalan, drawer/dialog/input/animasi/state bekerja.

## 10.2 Edit ON

```text
Bubble
↓
Edit ON
↓
SELURUH SCREEN = AREA EDIT
```

Selama Edit ON, sentuhan tidak menjalankan action aplikasi; sentuhan memilih object; drag/gesture dialihkan ke editor; Edge membaca object yang dipilih; screen tetap screen yang sama.

---

# 11. No-Cloning Editing

Edit Mode **tidak boleh membuat clone screen**.

Dilarang menjadikan duplicate screen, duplicate View hierarchy, full-screen bitmap copy, atau graph kedua penuh sebagai mekanisme utama edit.

Yang digunakan:

```text
Screen yang sama
+
perubahan input behavior
+
working state editor
```

State visual cukup direpresentasikan sebagai metadata ringan seperti drawer open, dialog visible, selected tab, scroll, bukan screenshot sebagai source of truth.

---

# 12. Visual State Hold Saat Masuk Edit

Pengguna dapat membawa UI ke state tertentu saat Edit OFF lalu mengaktifkan Edit ON. Drawer, dialog, bottom sheet, selected tab, expanded panel, dropdown, error/loading/success state dapat tetap terlihat sebagai state deklaratif aktif, bukan clone layar.

---

# 13. Manual Save Murni

```text
Edit
↓
Working State RAM
↓
Dirty Flag
↓
Save
↓
Transactional Commit
```

Tidak ada autosave project persistent. Jika pengguna meninggalkan konteks dengan perubahan belum disimpan, UI menawarkan:

```text
Simpan
Buang
Batal
```

---

# 14. Undo / Redo

Undo/Redo memakai **Atomic Undo/Redo Transaction Group**. Satu aksi yang mengubah beberapa resource dikembalikan sebagai satu group. History dibatasi, tidak membuat snapshot penuh project, dan setelah Save revision tersimpan menjadi baseline baru.

---

# 15. Per-Screen Working Sector

Satu screen aktif = satu working sector aktif. Project besar tidak berarti semua screen aktif di RAM. Saat pindah screen, renderer/resource lama dilepas dan state ringan dipertahankan. Navigation history menyimpan ID/state kecil, bukan screen utuh.

---

# 16. Non-Linear Round-Trip Editing

Pengguna dapat berpindah:

```text
UI ↔ Logic ↔ Data ↔ Binding ↔ Asset
```

pada satu project aktif. Semua membaca source of truth yang sama; hanya satu fungsi heavy-active pada satu waktu sesuai `Rancangan-Editor-Terpadu.md`.

---

# 17. Mode Visual / Properties / Code

Tiga representasi kerja:

- **Visual** — default;
- **Properties** — deklaratif terstruktur;
- **Code** — fallback/advanced representation.

Ketiganya merepresentasikan model yang sama. Two-way sync hanya dilakukan bila representasi aman/lossless. Code bukan arbitrary executable plugin di host.

---

# 18. Project Store

Project Store adalah **source of truth utama untuk editing**. RAM hanya working set dan cache tidak menjadi sumber kebenaran.

Struktur konseptual:

```text
Documents/ToolBox/
├─ Projects/
├─ Assets/
├─ Templates/
├─ Exports/
├─ Snapshots/
├─ Backups/
└─ Cache/
```

Data sensitif/runtime-only dapat berada di app-private storage.

---

# 19. Hybrid Per-Screen Store

Project memakai per-screen package sebagai source of truth + lightweight generated index + asset terpisah.

```text
Projects/<ProjectName>/
├─ project.json
├─ project.manifest
├─ screens/<screen-id>/screen.json
├─ logic/
├─ data/
├─ bindings/
├─ assets/
├─ styles/
├─ localization/
└─ metadata/
```

Tidak memakai satu file raksasa untuk seluruh project atau satu file per object kecil.

---

# 20. project.json dan project.manifest

`project.json` adalah Application Definition. `project.manifest` adalah Storage Integrity Record untuk projectId/schemaVersion/revision/resource/hash/asset references/status validitas. Manifest bukan source definition kedua dan dapat di-shard bila diperlukan.

---

# 21. Transactional Save

```text
Working Changes
↓
Staging / Journal
↓
Write Related Resources
↓
Validate
↓
Commit Record
↓
Publish Revision Baru
```

Jika gagal, staging di-discard/rollback dan revision valid sebelumnya tetap dipakai. Keamanan commit ditentukan journal, verification, revision marker, dan last-known-valid record.

---

# 22. Revision & Single Writer per Resource

Setiap project/resource mempunyai revision. Save memakai optimistic revision check + single writer per resource. Stale writer menghasilkan `STALE_WRITE`. Multi-resource transaction memakai short-lived Project Commit Coordinator.

---

# 23. Schema & Versioning

Versi dipisahkan:

```text
schemaVersion
buildModelVersion
contractVersion
toolVersion
capabilityVersion
componentVersion
```

Gunakan independent versioning + compatibility range + adapter/migration layer. Permanent migration selalu transactional dan eksplisit.

---

# 24. Stable Identity

Entity penting seperti Project, Screen, UI Object, Component Definition/Instance, Action, Event Binding, Data Source/Field, Logic Node, Navigation Route, Asset, Design Token, Background Task, Capability instance mempunyai Stable ID unik.

```text
rename/move/edit property → ID tetap
delete + undo → ID lama kembali
duplicate/copy/paste → ID baru
import merge → conflict remap otomatis
```

ID tidak didaur ulang.

---

# 25. Tombstone & Undo Restore

Saat object dihapus, identity dapat masuk tombstone selama masih dibutuhkan Undo/reference tracking. Undo Delete mengembalikan object, ID original, dan binding lama bila masih valid. Tombstone dipadatkan ketika history tidak lagi memerlukan, tetapi ID lama tidak dipakai ulang.

---

# 26. Generated Index & Dependency Graph

Index/dependency graph ringan adalah derived/rebuildable dari Project Store dan memakai Stable ID → Stable ID. Working changes dapat memakai in-memory dependency delta overlay; graph persistent diperbarui saat Save.

---

# 27. Impact Tracking

Dependency Graph digunakan untuk incremental validation/composition, targeted diagnostics, delete impact preview, dan audit. Hanya dependency terkait yang perlu divalidasi ulang bila perubahan lokal.

---

# 28. Component Registry

Component Registry adalah katalog metadata component; discovery tidak memuat runtime engine. Metadata minimal meliputi componentId, label, icon, category, version, property/event contract, capability requirements, compatibility, implementation reference.

Jika component tidak tersedia, instance project tidak dihapus; status `COMPONENT_UNAVAILABLE`.

---

# 29. Repository Component Registry Inventory

Repository/product master mempunyai inventory machine-readable component/capability/action/asset. Inventory membantu audit implementation/asset/contract/action/binding/permission/version. Koneksi hanya berdasarkan exact contract/Stable ID mapping, bukan nama mirip.

---

# 30. Property Contract

Setiap component mendeskripsikan property secara deklaratif: propertyId, type, nullability, default, range, unit, enum, editable/read-only, validation, state applicability, converter.

Tipe dapat berupa BOOLEAN, NUMBER, DIMENSION, TEXT, COLOR, ASSET, ENUM, URI, LIST, OBJECT, REFERENCE.

Multi-Function Edge/Floating Editor membaca contract untuk menghasilkan editor generik.

---

# 31. Event Contract

Event memakai Stable ID dan typed context, misalnya `ui.button.onClick`, `ui.input.onChange`. Contract mendeskripsikan eventId, payload/input context, output, propagation policy, dan compatible action types.

---

# 32. Action Registry

Action Registry berisi metadata action seperti `navigation.openScreen`, `browser.search`, `data.save`, `media.play`, `dialog.open` dengan typed input/output, parameters, permission requirements, execution mode, async behavior, timeout, cancellation, dan idempotency/execution ID bila diperlukan.

---

# 33. Compatibility Matching

Event/data/property/action dihubungkan berdasarkan typed contract. Safe converter harus eksplisit dan terdaftar. Input tidak lengkap berarti binding belum selesai. Tidak ada konversi diam-diam yang tidak dapat dijelaskan.

---

# 34. Composite Action

Satu command visual dapat merepresentasikan Ordered Steps + Success Condition + Failure Behavior + Optional Fallback + Optional Rollback/Compensation. Async/retry-sensitive action memiliki timeout/cancellation/execution ID/idempotency. Flow kompleks dipindahkan ke Logic.

---

# 35. Navigation Contract

Navigation memakai Stable Screen ID dan typed parameter. Rename screen tidak memutus navigation. Broken target menjadi `BROKEN_NAVIGATION_REFERENCE`. Portrait/landscape tetap satu Screen ID.

---

# 36. Back Stack

Back stack hanya menyimpan Screen ID, required parameter, dan lightweight state. Tidak menyimpan clone screen atau mempertahankan seluruh renderer lama di RAM.

---

# 37. Data Source Contract

Data Source typed + Stable ID. UI tidak perlu mengetahui apakah data berasal dari database, API, file, form input, runtime state, atau hasil action.

---

# 38. Data Binding

Mendukung one-way dan two-way. Two-way bukan default semua property dan menggunakan change-origin/version token + cycle suppression. Derived values pure/tanpa side effect.

---

# 39. Lazy/Paged Data Access

Data besar menggunakan query/filter/page → working subset → viewport. List/Grid memakai paging/chunking/recyclable view binding; record besar tidak berarti object UI sebanyak itu aktif.

---

# 40. Dynamic List Item Identity

Item dinamis menggunakan Stable Data-Item Key, bukan index posisi. Selection/animation/binding mengikuti identity data dan view dapat didaur ulang.

---

# 41. Broken Reference Model

Reference hilang tidak dihapus diam-diam. Status dapat berupa BROKEN_REFERENCE, BROKEN_DATA_REFERENCE, BROKEN_NAVIGATION_REFERENCE, BROKEN_STYLE_REFERENCE, MISSING_ASSET, COMPONENT_UNAVAILABLE, CAPABILITY_INCOMPATIBLE. Mandatory broken reference memblok `READY TO BUILD`.

---

# 42. Logic / Flow Editor

Logic disimpan sebagai Declarative Flow Graph dengan Stable Node ID. Source of truth adalah node ID, connection, input/output, condition, execution order, failure path. Koordinat diagram hanya editor metadata dan graph dimaterialisasi lokal.

---

# 43. Branch, Loop, Async

Branch eksplisit TRUE/FALSE. Async mempunyai START/SUCCESS/FAILURE/CANCELLED/TIMEOUT bila relevan. Loop mempunyai explicit exit condition dan dapat diberi iteration/time limit. Runtime watchdog dapat menghentikan flow yang melebihi batas.

---

# 44. List-First → Auto Diagram Materialization

Pemilihan action/capability memakai metadata ringan terlebih dahulu; diagram lokal dimaterialisasi hanya bila diperlukan. Jangan memuat seluruh graph project hanya untuk satu pilihan.

---

# 45. Component Definition, Instance, Template

Component Definition adalah reusable linked source; Component Instance merujuk definition + override; Template adalah titik awal yang menghasilkan object/screen mandiri. Composition lebih diutamakan daripada inheritance bertingkat dalam.

---

# 46. UI State & State Variant

State seperti NORMAL/PRESSED/DISABLED/LOADING/ERROR/SELECTED tidak membuat clone object. Variant hanya property delta. Layer UI State, Orientation, Theme, Data State dipisahkan untuk mencegah state explosion.

---

# 47. Animation Model

Animation disimpan deklaratif sebagai Trigger → Timeline/Transition → Property Changes dengan start/end, duration, easing, sequence/parallel relation. Tidak menyimpan setiap frame dan tidak menjadi arbitrary runtime script.

---

# 48. Design Token & Theme

Design Token memakai Stable ID untuk color/text/radius/spacing/elevation/icon style. Urutan resolusi style harus eksplisit dan tidak ambigu. Token hilang menjadi broken reference atau membutuhkan relink.

---

# 49. Responsive Layout

Gunakan responsive constraint layout + container-based composition. Object dapat fixed/content/fill, constraint terhadap parent/object lain, container ROW/COLUMN/STACK/GRID/FREE. Drag/resize diterjemahkan ke relation/constraint bila memungkinkan.

---

# 50. Adaptive Size & Orientation

Gunakan adaptive class terbatas seperti COMPACT/MEDIUM/EXPANDED + orientation. Satu Screen ID mempunyai Base Layout + Portrait/Landscape/Adaptive Overrides, bukan screen clone.

---

# 51. Grid, Guide, Snapping

Editor dapat menyediakan grid, guide, edge/center/object snapping, spacing hints sebagai editor behavior yang dapat dimatikan.

---

# 52. Multi-Select & Group Editing

Multi-select memakai compatible-property filtering + group transform. Group transform menjadi satu Undo transaction. Alignment/distribution/equal size/spacing divalidasi terhadap constraint setelah transform.

---

# 53. Parent/Child & Reparenting

Parent ownership eksplisit. Reparent mempertahankan Stable ID, menghitung ulang coordinate context, constraint, z-order, dan menandai dependency invalid tanpa silent delete.

---

# 54. Object Lock

Object/group dapat mengunci position, size, transform, property tertentu, atau semua. Lock adalah editor protection dan object tetap dapat diinspeksi.

---

# 55. Layer, Z-Order, Hit Test

Project layer: BACKGROUND / CONTENT / OVERLAY / MODAL. Shell ToolBox berada di atas project, sedangkan System/Safety Dialog wajib dapat berada di atas Shell. Edit OFF memberikan input ke topmost eligible object; Edit ON memilih topmost editable object tanpa menjalankan app action.

---

# 56. Pointer Behavior & Event Propagation

Project object dapat AUTO/NONE. Event propagation memakai optional capture/preview → target → optional parent propagation. Satu gesture satu owner dengan policy TARGET_ONLY/CONTINUE/CONSUME/STOP.

---

# 57. Input, Gesture, Focus

Input Contract dapat meliputi tap, long press, double tap, drag, swipe, scroll, text, keyboard, focus, optional multi-touch. Gesture Resolver mencegah satu gesture mengeksekusi dua action bertentangan. Focus routing memakai Stable ID.

---

# 58. Safe Area & Insets

Layout sadar status bar, navigation bar, gesture area, cutout/notch, IME, viewport. Shell selalu dalam interactive bounds dan tidak mengambil gesture exclusion lebih besar dari kebutuhan.

---

# 59. Zoom / Pan & Coordinate Space

```text
Design Coordinate Space
≠ Editor Viewport Transform
≠ ToolBox Shell Coordinate Space
```

Zoom/pan tidak mengubah layout project dan Shell tidak ikut ter-zoom.

---

# 60. Accessibility & Semantic Contract

Object mempunyai role, accessible label/description, focusable/enabled/selected/checked/required/expanded/error/loading semantics. Icon-only interactive control wajib berlabel. Validator memberi diagnostic accessibility.

---

# 61. Text & Localization

Teks mendukung DIRECT TEXT dan TEXT RESOURCE dengan Stable ID + locale variants, fallback locale, parameterized/plural/number/date/time/currency formatting, RTL-aware layout. Bahasa tidak membuat clone screen.

---

# 62. Conditional Properties

visible/enabled/selected/opacity/style variant boleh terikat state/data melalui pure declarative expression tanpa side effect. Network/database mutation tetap melalui Action/Logic Contract.

---

# 63. Asset Identity

Asset memakai Stable Asset ID; object tidak bergantung langsung pada nama/path file. Nama/lokasi dapat berubah dan direlink bila logical identity dipertahankan.

---

# 64. Original vs Preview

Original Asset = persistent. Preview/thumbnail/decoded bitmap = disposable/cache/working memory. Clear Cache tidak boleh menghapus original. Satu original dapat dipakai banyak object tanpa clone file.

---

# 65. Asset Loading

Asset besar memakai thumbnail-first, preview-sized decode, viewport-first, lazy-load, streaming/chunking audio/video, release saat tidak dipakai. Full-resolution hanya ketika operasi benar-benar membutuhkan.

---

# 66. Unused/Missing/Duplicate Asset

Reference count/index mendeteksi UNUSED_ASSET, MISSING_ASSET, BROKEN_ASSET_REFERENCE, DUPLICATE_CANDIDATE. Unused tidak dihapus otomatis. Duplicate detection memakai content hash; missing dapat direlink dengan Stable ID yang sama bila file benar ditemukan.

---

# 67. Cache Manager

Central Cache Manager mempunyai global/per-category/disk/memory budget dan HOT/WARM/COLD/TEMP priority. Project data/original asset/required recovery tidak pernah menjadi eviction cache.

---

# 68. Manual Cache Cleanup

Pengguna dapat melihat dan menghapus thumbnail, preview, render temporary, parser/index cache, disposable temp per kategori. Cache selalu rebuildable.

---

# 69. Recovery

Recovery melindungi data yang sudah di-Save. Crash sebelum Save boleh kehilangan working changes. Crash saat transaction Save memakai journal/last-valid revision untuk kembali ke kondisi valid.

---

# 70. Incremental Snapshot & Previous Valid

Gunakan Current Valid / Previous Valid / Optional Checkpoint, bukan clone penuh setiap perubahan. Recovery dibatasi agar storage tidak membengkak.

---

# 71. Recovery Storage List

Recovery Manager menampilkan nama/tanggal/ukuran/jenis/project/status REQUIRED/DELETABLE/IN_USE. Pengguna dapat hapus item/multi-select/sort dan `Hapus Semua yang Aman`. Current-valid/required recovery tidak boleh dihapus.

---

# 72. Backup

Backup berbeda dari Recovery. Backup adalah salinan project atas permintaan pengguna; recovery adalah perlindungan teknis. Backup tidak dibuat tanpa batas otomatis.

---

# 73. SAF & User-Owned Storage

Pada Android 11, folder user-visible memakai Storage Access Framework. Pilih/buat folder sekali → persist URI permission → gunakan kembali. Jangan meminta permission berulang jika masih valid.

---

# 74. Access-Loss & Re-linking

Bedakan PROJECT_OK, ACCESS_LOST, FOLDER_MISSING, RESOURCE_MISSING, PROJECT_CORRUPT. Kehilangan SAF access bukan corruption. Relink memverifikasi projectId/manifest identity lalu update URI tanpa perlu copy jika folder hanya berpindah.

---

# 75. Security Boundary Project Store

User-visible files tidak langsung dipercaya. Semua resource melewati path normalization, schema/Stable ID/reference/capability/type/MIME/size/budget/compatibility validation. Project declarative tidak boleh memanggil arbitrary shell/native/downloaded code.

---

# 76. Secret Separation

Visible Project Store tidak menyimpan GitHub token, signing private key, keystore password, API secret, production credentials. Project hanya menyimpan logical requirement/reference; secret sebenarnya berada di secure environment/private storage/build secret store.

---

# 77. Import Security

Import selalu staging dan memvalidasi path traversal/canonical path, entry count, uncompressed size, nesting, decompression budget, schema, IDs, contract compatibility, content type, asset integrity, package hash/signature bila relevan. Tidak langsung menulis ke project aktif.

---

# 78. Import vs Merge

Import Project Baru mempertahankan kesatuan project/ID internal bila valid. Merge ke Project Lama meremap conflict ID dan references. Import dan Merge bukan operasi sama.

---

# 79. Export Contract

Export mengambil project definition, screens, logic, data definitions, bindings, styles, localization, required assets, dependency/version metadata pada satu revision. Cache/transient undo/preview/recovery journal/secret/signing key/runtime log tidak ikut.

---

# 80. Permission Contract

Permission diturunkan dari capability yang dipakai dan dibedakan install-time/runtime/special access/optional. Action tidak menganggap runtime permission pasti granted dan failure path harus dapat direpresentasikan.

---

# 81. App & Screen Lifecycle

Lifecycle event dapat mencakup APP_START/FOREGROUND/BACKGROUND, SCREEN_ENTER/VISIBLE/LEAVE/RETURN. Jangan bergantung pada APP_CLOSE untuk menyimpan data penting. Lifecycle action dapat EVERY_ENTER/FIRST_ENTER/WHEN_DATA_STALE.

---

# 82. Background Task Contract

Background task dipisahkan dari screen, mempunyai Stable ID dan QUEUED/RUNNING/PAUSED/SUCCESS/FAILED/CANCELLED, typed input/progress/result/retry/timeout/cancellation/constraint/execution class. Retry dibatasi dan screen boleh RELEASE.

---

# 83. Safety Boundary Live Preview

Action dengan side effect nyata seperti delete data produksi, pembayaran, upload produksi, external destructive intent, atau credential sensitif masuk Safety Gate/simulation boundary saat desain. Built app dapat menjalankan action asli sesuai logic/permission.

---

# 84. Preview Data Sandbox

UI/Logic dapat memakai sample/loading/error/empty/list/simulated action result terpisah dari data produksi/runtime dan tidak otomatis menjadi production data.

---

# 85. Editor Context State

Simpan ringan screenId, selectedObjectId, active editor function, zoom/pan/scroll, Bubble/Floating/Edge state, panel state, editor mode. Tidak mempertahankan full View hierarchy. Invalid shell state di-clamp/snap ke safe configuration.

---

# 86. Editor Metadata vs Runtime Data

Selection rectangle, guide, grid, zoom, viewport, diagnostics overlay, shell layout, temporary preview state adalah editor-only dan tidak masuk runtime APK kecuali property memang bagian desain aplikasi.

---

# 87. Copy/Paste Clipboard

Contract-Aware Clipboard + Automatic ID Remapping. Paste membuat ID baru, memproses dependency minimum, mempertahankan reference valid, meremap conflict, menandai broken reference, dan tidak menghubungkan berdasarkan nama mirip.

---

# 88. Diagnostics

Unified Diagnostic Contract mempunyai diagnosticId/severity/code/source/resourceId/location/message/suggested fix/related diagnostics dengan INFO/WARNING/ERROR/BLOCKING. Diagnostic menunjuk Stable ID/resource tepat.

---

# 89. Detect → Suggest → Fix

Auto-fix hanya jika deterministik, contract-backed, dapat divalidasi, reversible/aman, dan tidak spekulatif. Ambigu tidak diperbaiki otomatis.

---

# 90. Incremental Validation

Gunakan dependency graph untuk memvalidasi resource terkait secara incremental. Full validation tetap pada gate penting seperti build/release.

---

# 91. Build Contract Validator

Sebelum project dikirim ke GitHub, ToolBox memeriksa screen, binding wajib, broken reference, used asset, permission, logic/action, navigation, schema/version compatibility, implementation availability, build/package/target Android/ABI, dan canonical model viability.

Hanya jika syarat wajib lolos:

```text
READY TO BUILD
```

---

# 92. Canonical Build Model / IR

```text
Project Store
↓
Composer
↓
Canonical Build Model / IR
↓
Generator
↓
Android Project
↓
APK
```

IR generated/versioned/rebuildable dan bukan source of truth editing.

---

# 93. Build Package

READY TO BUILD menghasilkan immutable build package terikat revision tertentu dengan buildId/projectId/projectRevision/schema/buildModel/target Android/ABI/package/dependency provenance/content hash. Satu Build ID = satu isi pasti.

---

# 94. Build Handoff — Repository Terkini

Product/build input berasal dari private `RMTampu/ToolBox`. Public `RMTampu/Tools` menyediakan reusable workflow/validator/test tooling.

Build hanya menerima material yang diperlukan: Canonical Build Model, required assets, generator inputs, build metadata. Tidak mengirim cache, undo history, preview, recovery tak terkait, secret.

Traceability wajib:

```text
SOURCE_REPOSITORY = RMTampu/ToolBox
SOURCE_COMMIT_SHA
SOURCE_REF
CI_REPOSITORY = RMTampu/Tools
CI_WORKFLOW_REF
```

Rincian mengikuti `REPOSITORY_INTEGRATION_POLICY.md`.

---

# 95. Signing

Signing secret tidak berada di Project Store. Project menyatakan logical signing profile/expected identity. APK diverifikasi terhadap expected signer fingerprint; mismatch menghasilkan `SIGNING_IDENTITY_MISMATCH`.

---

# 96. Build Artifact Traceability

Record build minimal memuat Build ID, Project Revision, source commit/repo, CI workflow ref/run, APK name/size/SHA-256, signing fingerprint, SDK/architecture, validator/build/test result.

```text
yang divalidasi = yang dikirim = yang dibuild = artifact yang diterima
```

---

# 97. Tool / Engine Extension Contract

Engine masuk melalui Tool Contract berisi toolId/toolVersion/contractVersion/compatibility/components/actions/events/capabilities/data types/permission needs/entry point/lifecycle. Host menemukan engine melalui registry, bukan direct dependency.

---

# 98. No Direct Inter-Tool Dependency

Gunakan Registry/Contract, bukan UI Editor → direct engine → direct engine. Metadata capability tidak membutuhkan engine runtime hidup.

---

# 99. Mandatory Lifecycle Compliance

Engine harus lazy-load, release resource, berhenti saat tidak diperlukan, tidak meninggalkan listener/thread/context reference, mengikuti budget, memberi diagnostic, dan gagal terisolasi.

---

# 100. Failure Isolation

Engine gagal menjadi FAILED/UNAVAILABLE; host/project/engine lain tetap berjalan sejauh dependency memungkinkan. Bagian dependent ditandai eksplisit.

---

# 101. Executable Runtime Boundary

External package/project jalur normal bersifat declarative: UI definitions/assets/templates/workflows/rules/declarative migrations/repair definitions/metadata. Arbitrary DEX/JAR/native downloaded code tidak bebas dieksekusi host. Primitive executable/Android component/permission/native engine/trust root baru masuk trusted APK update/build path.

---

# 102. Installed Target / Edit Bridge — Override Terkini

Konsep lama `ToolBox-aware only` **diganti** oleh prinsip:

> ToolBox tidak menilai dari mana aplikasi dibuat; ToolBox menilai capability/editing door apa yang tersedia dan diizinkan.

```text
Installed App
↓
Edit Bridge / Adapter
↓
Capability Scan
↓
Editor
```

Tidak ada bypass sandbox/signature Android. Unsupported section disembunyikan/read-only.

---

# 103. Declarative Update Package

Update/evolution package memakai packageId/version/type/target/compatibility/dependencies/capabilities/file hashes/integrity/signature/migration-repair intent. Trust terikat exact content; perubahan content membatalkan trust lama.

---

# 104. Update Apply Pipeline

```text
Manual Select
↓ Staging
↓ Validate Package
↓ Known-Good Checkpoint
↓ Prepare / Journal
↓ Dry Run / Self-Test
↓ Preview
↓ Explicit Apply
↓ Activate
↓ Health Check
↓ Commit atau Rollback
```

Tidak ada auto-commit hanya karena package terdeteksi.

---

# 105. Freeze Engine

Freeze Engine menjaga baseline/data melalui LIVE/BASELINE, FROZEN_BASE, WORKING/OVERLAY, RECOVERY A/B. Eksperimen masuk overlay; user dapat checkpoint/discard/recover/commit baseline baru secara eksplisit dan all-or-nothing.

---

# 106. Freeze State Machine

NORMAL → CREATING_SNAPSHOT → FROZEN → COMMITTING/RESTORING/THAWING, dengan VERIFYING/RECOVERY_REQUIRED/RECOVERY_RUNNING/FAILED_SAFE. Startup bootstrap membaca journal dan memulihkan operasi terputus; incomplete temp state tidak merusak last-known-valid baseline.

---

# 107. Safe Mode / Safe UI

Safe UI menyediakan baseline/overlay status, integrity verify, discard/restore/quarantine, diagnostic export, read-only inspection dan tidak bergantung pada engine yang rusak.

---

# 108. Health Check

Setelah repair/update/restore: Apply → Health Check → PASS commit/continue atau FAIL rollback/safe mode. Check dapat mencakup schema/files/capability/navigation/database/startup/no critical diagnostic.

---

# 109. Memory Architecture

Target device 6 GB tidak berarti seluruh RAM tersedia satu process. Budget berdasarkan memory class/pressure/active editor function/screen/decoded asset/renderer/PSS/leak trend.

```text
1 APK
+ lightweight host
+ 1 heavy function aktif
+ 1 screen aktif
+ bounded working set
```

---

# 110. Per-Screen Memory Budget

Gunakan viewport-first rendering, off-screen release, sampled image decode, bounded preloading, adaptive preview quality, list virtualization, per-resource budget. Memory pressure memicu pengurangan preloading/cache/quality/off-screen resources sebelum diagnostic berat.

---

# 111. Overdraw & Rendering Cost

Transparent layer/shadow/blur/bitmap/video/simultaneous animation dapat menekan GPU walaupun RAM cukup. Diagnostic perlu mempertimbangkan visible node/render complexity, bukan byte memory saja.

---

# 112. Memory Leak Discipline

Cegah listener/coroutine/context/view/bitmap/cache/timer/engine leak. Soak test switching editor function/screen dipakai mendeteksi memory staircase.

---

# 113. Test & Benchmark Contract

Fitur tidak matang hanya karena build berhasil. Test meliputi functional/lifecycle/memory/storage/transaction/crash/recovery/compatibility/scale/process death/build contract/import/security/rendering/performance dengan hasil measurable.

---

# 114. Soak Test

Contoh UI → Logic → Data → UI ×50/×100, periksa start/end/peak PSS, thread count, release, crash, latency trend. RAM terus naik per cycle = indikasi failure/leak.

---

# 115. Crash/Transaction Test

Putus proses pada staging/write/validation/pre-commit/post-commit marker/migration/recovery. Setelah restart hanya revision lama valid atau revision baru valid yang diterima; mixed revision dilarang.

---

# 116. Scale Classes

SMALL/MEDIUM/LARGE/STRESS dengan variasi screen/object/binding/asset/asset size/logic graph/component/dependency graph. Tujuan mengetahui adaptive behavior, bukan hanya angka maksimum.

---

# 117. External File Integrity

Manifest/hash mendeteksi file berubah/hilang/corrupt/revision mismatch. Hash di folder sama hanya integrity aid; authenticity memerlukan signature/trust root terpisah.

---

# 118. Build-Time Dependency Determinism

Build package membawa dependency/toolchain lock/provenance agar input sama tidak resolve dependency berbeda diam-diam. Perubahan dependency penting menghasilkan build identity baru.

---

# 119. Audit Agent Integration

Audit agent memakai inventory/dependency graph/diagnostics/contract IDs/manifest/build validator/implementation presence. Auto-fix hanya deterministic. Jangan menghapus kemampuan dasar agar audit PASS.

---

# 120. Automatic Repair Policy

Repair otomatis cocok untuk rebuild derived index/graph/cache, remap exact ID conflict, relink exact Stable ID, regenerate derived manifest, remove stale disposable cache. Tidak boleh menebak navigation/business logic/asset berdasarkan nama, menghapus user data, atau mengubah side effect penting.

---

# 121. Diagnostic Codes Bersama

Gunakan keluarga code konsisten seperti BROKEN_REFERENCE, MISSING_ASSET, COMPONENT_UNAVAILABLE, CONTRACT_MISMATCH, ACTION_IMPLEMENTATION_MISSING, PERMISSION_CONTRACT_MISSING, LAYOUT_CONSTRAINT_CONFLICT, STALE_WRITE, CAPABILITY_INCOMPATIBLE, SIGNING_IDENTITY_MISMATCH.

---

# 122. Prioritas Source of Truth

```text
Project Store = source of truth editing
Generated Index / Dependency Graph = derived/rebuildable
Canonical Build Model = generated build representation
Cache / Preview = disposable
Runtime View = materialized working representation
```

---

# 123. Invariant Utama

1. Project valid mempunyai revision konsisten.
2. Manual Save bukan autosave tersembunyi.
3. Edit Mode tidak membuat clone screen.
4. Shell controls tidak menjadi bagian runtime app hasil build.
5. Stable ID tidak bergantung label visual.
6. Tool tidak direct-depend implementasi tool lain.
7. Registry memuat metadata, bukan semua runtime engine.
8. External project tidak mengeksekusi arbitrary code di host.
9. Cache tidak berwenang menghapus source data.
10. Recovery menjamin state yang sudah committed.
11. Broken reference tidak disembunyikan/silent-delete.
12. GitHub build terikat exact source/build identity.
13. Secret tidak disimpan di visible project.
14. Satu screen berat tetap tunduk budget.
15. Generated index rebuildable.
16. Permanent migration transactional.
17. Engine gagal tidak menjatuhkan host bila isolasi memungkinkan.
18. Permission berasal dari capability dipakai.
19. Background task tidak mempertahankan screen hanya agar hidup.
20. Audit auto-fix tidak menebak maksud pengguna.
21. Product master = private `RMTampu/ToolBox`.
22. CI engine = public `RMTampu/Tools`.
23. Build tidak boleh silently memakai stale public source.

---

# 124. Alur Kerja Project dari Awal sampai APK

```text
Buat / Buka Project
↓ Project Store
↓ Editor UI/Logic/Data/Binding/Asset
↓ Working State
↓ Manual Save
↓ Transactional Commit
↓ Valid Revision
↓ Diagnostics
↓ Build Contract Validator
↓ READY TO BUILD
↓ Composer / Canonical Build Model
↓ Immutable Build Package
↓ RMTampu/ToolBox exact source revision
↓ RMTampu/Tools pinned GitHub CI
↓ Signing / Tests
↓ Artifact + Build Report
```

---

# 125. Alur UI Editor — Terkini

```text
Screen aktif LIVE
↓ Bubble + Edge
↓ Bubble → Edit ON
↓ Screen yang sama menjadi area edit
↓ tap object → Edge/contextual Floating Editor
↓ Working State + Undo/Redo
↓ Save transactional
↓ Edit OFF
↓ UI kembali menjalankan action normal
```

Tidak ada Deck, cloning, atau autosave.

---

# 126. Alur Asset ke Object

```text
Edge
↓ Component/Asset Registry metadata
↓ drag component/asset
↓ create Stable Instance ID
↓ resolve property/event contract
↓ render active screen
↓ manual save
```

Original asset storage-first; preview on-demand.

---

# 127. Alur Binding

```text
Object / Default Binding Profile
↓ Global Binding Registry
↓ Compatibility Filter
↓ Auto Connect deterministic targets
↓ Validate
```

Ambiguity/error tidak ditebak dan masuk Diagnostics + Copy report.

---

# 128. Alur Logic

Event → Action → Condition/Branch → Async Success/Failure → Navigation/Data/Action berikutnya. Graph dimaterialisasi lokal dan release ketika tidak diperlukan.

---

# 129. Alur Repair / Evolution

Select target/package → Capability/Authorization → Staging → Integrity/Compatibility → Known-Good Protection → Dry Run/Preview → Explicit Apply → Health Check → Commit atau Rollback/Safe Mode.

---

# 130. Alur Freeze

NORMAL → Create Known-Good Baseline → FROZEN → perubahan masuk Working Overlay → Discard/Recovery/Commit validated baseline baru.

---

# 131. Arsitektur RAM Ringkas

```text
Host ringan
+ Shell kecil
+ 1 fungsi Editor/tool heavy aktif
+ 1 screen/sector aktif
+ Working State terbatas
+ viewport assets terbatas
```

---

# 132. Arsitektur Penyimpanan Ringkas

```text
VISIBLE USER STORAGE
├─ Projects
├─ Assets
├─ Templates
├─ Exports
├─ Snapshots
└─ Backups

PRIVATE APP STORAGE
├─ transaction journal
├─ staging
├─ lock/revision metadata
├─ secure runtime metadata
├─ recovery bootstrap state
└─ sensitive local configuration

DISPOSABLE
└─ Cache
```

---

# 133. Batas Antara Rancangan dan Implementasi

Dokumen ini menetapkan apa yang harus dijamin sistem dan hubungan komponen besar. Nama class/framework UI/library/ikon/warna/package internal boleh berubah selama invariant dan contract terpenuhi.

---

# 134. Bentuk Teknis ToolBox Saat Matang

ToolBox adalah satu host ringan bagi engine/module independen; Shell memakai Bubble + Floating Window + Multi-Function Edge; satu Editor menaungi UI/Logic/Data/Binding/Asset; editing screen yang sama tanpa cloning; project storage-first; Save manual transactional; hubungan Stable ID + typed contracts; engine/materialization lazy; asset adaptif; diagnostics/dependency incremental; recovery menjaga committed revision; external import berada pada validation boundary; repair/update memakai staging/health/rollback; build memakai Canonical Model exact revision; product master private `RMTampu/ToolBox`; build/test engine public `RMTampu/Tools`.

---

# 135. Kesimpulan Arsitektur

```text
RMTampu/ToolBox PRIVATE
        │
        ├─ Product Source / Project Store
        ├─ Master Asset
        ├─ Master Rancangan
        └─ Exact Revision
                │
                ▼
       Build Contract Validator
                │
        READY TO BUILD
                │
                ▼
RMTampu/Tools PUBLIC (PINNED CI WORKFLOW)
        │
        ├─ Build
        ├─ Test
        ├─ Validation
        └─ Firebase Bridge (LOCKED until approval)
                │
                ▼
          APK + Report
```

Fondasi akhirnya:

> **ToolBox menyimpan seluruh kemampuan sebagai definisi, contract, registry, project data, dan master asset di private product repository; runtime hanya mematerialisasikan bagian yang diperlukan; build/test dilaksanakan oleh public CI engine yang dipin dan tidak pernah mengambil alih fungsi source of truth produk.**
