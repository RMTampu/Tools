# Rancangan Editor Terpadu ToolBox
## Konsolidasi Keputusan Final Setelah Audit Editor

> **Status:** `EDITOR DESIGN AUDIT = PASS`
>
> Dokumen ini mengkonsolidasikan keputusan rancangan Editor yang sudah disetujui sampai Audit Putaran 2. Dokumen ini menjadi sumber konsolidasi untuk pembuatan Master Rancangan berikutnya.
>
> Bila terdapat perbedaan dengan rancangan Editor lama, keputusan di dokumen ini **menggantikan** konsep lama yang bertentangan. Rancangan Shell visual yang tidak bertentangan tetap mengikuti `Rancangan-UI-Shell.md`.

---

# 1. Prinsip Utama Editor

ToolBox hanya menampilkan **satu entry utama bernama `Editor`**.

```text
ToolBox
└─ Editor
   ├─ UI
   ├─ Logic
   ├─ Data
   ├─ Binding
   └─ Asset
```

UI, Logic, Data, Binding, dan Asset tetap boleh memiliki engine/module internal yang terpisah, tetapi dari sudut pandang pengguna semuanya adalah bagian dari satu Editor.

Prinsip runtime:

- hanya satu fungsi Editor yang aktif penuh pada satu waktu;
- project aktif tetap sama ketika berpindah UI/Logic/Data/Binding/Asset;
- state ringan dipertahankan;
- resource berat fungsi sebelumnya dilepas;
- Edge mengikuti fungsi Editor aktif;
- selector fungsi Editor dapat diminimalkan/disembunyikan;
- satu project aktif penuh di RAM pada satu waktu;
- storage adalah source of truth persistent;
- RAM hanya working set;
- Save tetap manual dan transactional.

---

# 2. Pilihan Sumber Editor

Saat `Editor` dibuka, pengguna memilih target:

```text
EDITOR
├─ Proyek Tersimpan
├─ Aplikasi Terinstal
├─ Edit ToolBox
└─ Buat / Edit Komponen
```

## 2.1 Proyek Tersimpan

Daftar hanya menampilkan nama project. Setiap nama tetap terhubung ke folder/package project masing-masing.

## 2.2 Aplikasi Terinstal

ToolBox tidak bertanya apakah aplikasi dibuat oleh ToolBox. Prinsipnya adalah:

> **Apa yang tersedia dan diizinkan untuk diedit?**

Aplikasi dapat berasal dari Android Studio, Replit, AI builder, GitHub, ToolBox, project teman, atau sumber lain.

Sebelum editing, ToolBox menjalankan **Capability Scan** untuk menentukan kemampuan yang benar-benar tersedia:

```text
UI
Logic
Data
Binding
Asset
Runtime / Apply
```

Bagian yang tidak tersedia disembunyikan atau ditandai read-only. Tidak ada bypass sandbox/signature Android.

## 2.3 Edit ToolBox

ToolBox dapat mengedit dirinya sendiri hanya pada surface yang memang dinyatakan editable. Kernel, recovery core, dan safety-critical foundation tidak menjadi target edit visual biasa.

Alur aman self-edit:

```text
Working State
↓
Staging
↓
Validate
↓
Recovery Point
↓
Activate
↓
Verify
↓
PASS atau Rollback
```

## 2.4 Buat / Edit Komponen

Entry ini membuka Component Editor untuk membuat atau mengubah reusable component.

```text
Buat Baru
Komponen Tersimpan
Duplicate as Base
```

Component Editor menggunakan kemampuan UI/Logic/Data/Binding/Asset yang sama, tetapi targetnya satu reusable component.

---

# 3. Shared Floating Editor Framework

Semua fungsi object memakai satu kerangka Floating Editor kontekstual.

Aturan:

- draggable;
- auto-position agar tidak menutupi object terpilih;
- mengingat posisi relevan;
- hanya satu Floating Editor utama aktif;
- memilih fungsi lain mengganti isi Floating Editor, bukan membuka banyak window;
- header dapat menyediakan `Pin` dan `X`;
- `Pin` mempertahankan posisi pilihan pengguna;
- tanpa Pin, posisi dapat menyesuaikan otomatis;
- aman terhadap IME/keyboard dan safe bounds;
- perubahan langsung masuk Working State;
- menutup dengan `X` tidak berarti revert;
- Undo/Redo tetap berada pada history Editor utama.

---

# 4. Fungsi Edit Object

Object terpilih dapat mempunyai fungsi berikut bila contract/capability mendukung:

```text
1. Style
2. Size
3. Position
4. Content
5. Color
6. Spacing
7. Shape
8. Border
9. Font/Text
10. Opacity
11. Rotation/Transform
12. Alignment
13. Layer
14. State
15. Animation
16. Auto Connect Binding
17. Event/Action
18. Accessibility
19. Lock
20. Others
```

Prinsip penting:

- menu hanya menampilkan property/capability yang benar-benar didukung component;
- Working State langsung memperlihatkan perubahan;
- Save persistent tetap manual;
- satu gesture besar seperti resize/multi-align menjadi satu Undo transaction;
- `Resize` berbeda dengan `Scale`;
- `Opacity 0%` berbeda dengan `Hidden`;
- state memakai property delta, bukan clone object;
- animation complex diarahkan ke Logic;
- accessibility diisi otomatis sejauh deterministik;
- lock adalah editor protection, bukan security boundary.

## 4.1 Size

Floating Resize Editor mendukung handle, slider, angka presisi, `Content/Fixed/Fill`, ratio lock, snap, dan preset grid untuk struktur/kepadatan container. Preset grid bukan ukuran fisik icon tunggal.

## 4.2 Position

Dua mode:

```text
Mode Terikat Layout
Mode Posisi Bebas
```

Terikat Layout adalah default responsive. Posisi Bebas memakai X/Y dan dapat menumpuk object, tetapi tetap diberi diagnostic bila berada di luar render/interaction bounds.

## 4.3 Color

Floating Color Editor dapat menyediakan token tema, palette, custom, gradient, transparent, recent, favorite, HSB, opacity, HEX, RGB, dan target warna kontekstual.

## 4.4 Spacing

Padding/Margin/Spacing empat sisi dengan linked/unlinked values.

## 4.5 Shape/Border/Text/Transform

Shape memakai preset dan radius. Border mendukung thickness/color/style/sides bila renderer mendukung. Text menyediakan visual font preview, ukuran sp, weight, italic, alignment, line height, letter spacing, case, overflow, dan theme token. Transform dapat memuat rotate/flip/scale.

## 4.6 Alignment/Layer/State/Animation

Alignment mendukung single/multi select, equal spacing/size. Layer memakai kelompok seperti Background/Content/Overlay/Modal. State menyimpan delta. Animation memiliki preset dan imported animation assets, dengan `Animation Edit ON/OFF` serta `Preview Once`.

## 4.7 Event/Action

Event/action sederhana diedit melalui Floating Editor. Flow bercabang, async, retry, multi-step, success/failure diarahkan ke Logic.

## 4.8 Others

Dapat memuat Copy, Duplicate, Replace Component, Reset, Save as Component, Save as Template, dan Delete.

---

# 5. Logic Editor

Logic memakai visual node/flow.

Edge dapat menampilkan:

```text
Event
Action
Condition
Flow
Variable
Function
```

Aturan:

- drag/drop node;
- visual connection;
- hanya koneksi contract-compatible yang diterima;
- invalid connection ditolak;
- node property menggunakan Floating Editor;
- hanya flow aktif yang dirender penuh;
- flow kompleks dari Event/Action object diteruskan ke Logic.

---

# 6. Data Editor

Data Editor bersifat visual:

```text
Source
Collection
Table
Field
Relation
Query
Mock Data
```

Field dapat memiliki:

```text
Name
Type
Default
Required
Unique
Validation
```

Relation dapat dibuat melalui drag/connect dan divalidasi. Mock Data dapat dipakai untuk UI Preview/Test.

Jika field masih direferensikan oleh binding/logic/component lalu hendak dihapus, ToolBox memberi warning sebelum Save.

---

# 7. Binding Center

Binding Center bukan tempat manual wiring satu-per-satu. Fungsinya:

```text
Auto Connect
Audit
Visual Map
Issue Tracking
Usage
History
```

Tersedia:

```text
[ Auto Connect Semua ]
```

Perbedaan:

- `Auto Connect Binding` pada object = component aktif saja;
- `Auto Connect Semua` pada Editor Binding = seluruh screen/project yang relevan.

Binding memakai **Global Binding Registry** dan **Default Binding Profile** per component.

Auto-connect hanya dilakukan bila target deterministik dan contract-compatible. Ambiguitas tidak ditebak.

Masalah menghasilkan diagnostic dengan alasan, Stable Error Code, dan `⧉ Copy` untuk laporan penuh.

Jika imported asset/component meminta action/binding yang belum tersedia, ToolBox melaporkan issue dan tidak silently connect.

---

# 8. Asset Center

Kategori dasar:

```text
Image
Icon
Font
Animation
Template
Component
Theme
Token
Import
```

Asset terpilih dapat menampilkan:

```text
Preview
Name
Type
Size
Source
Usage
Compatibility
Binding
Action
Dependencies
```

Kemampuan:

- visual catalog;
- drag/drop ke UI/component;
- import dari GitHub/storage;
- where-used;
- unused/broken reference detection;
- replace melalui Stable Asset ID;
- format/size/compatibility/contract validation.

Asset lengkap dimuat on-demand. Index ringan tetap tersedia di RAM, cache dibatasi dan dapat dibuang.

---

# 9. Asset Authoring Contract dan Import Pipeline

Setiap project/aplikasi mempunyai Asset Authoring Contract yang dapat memuat:

```text
Project ID
Schema Version
Supported Component
Supported Property
Supported State
Supported Event
Supported Action
Supported Binding
Supported Data Type
Supported Asset/Animation
Deprecated/Unsupported Capability
```

Alur pembuatan/import asset:

```text
Read Contract
↓
Create Asset/Component
↓
Validate Manifest + IDs + Version
↓
Check Binding/Action/Dependency
↓
Import Package
↓
Library
```

Asset yang belum kompatibel boleh disimpan sebagai `WAITING/INCOMPATIBLE`, tetapi belum dapat dipakai sampai requirement terpenuhi.

Jika capability/contract ToolBox berkembang, asset WAITING direvalidasi tanpa harus reimport. Aktivasi tetap tindakan eksplisit pengguna; tidak ada penyisipan diam-diam ke project.

---

# 10. Library dan Dependency

Library terpusat dapat memuat:

```text
Component
Asset
Template
Animation
Font
Icon
Theme
Token
```

Aturan identity/dedup:

- Stable ID + checksum sama = tidak membuat duplicate;
- Stable ID sama + checksum berbeda = update/version case;
- nama sama + Stable ID berbeda = resource berbeda;
- project melakukan version pinning;
- library version baru tidak otomatis mengganti project.

`Buat Salinan untuk Project` menghasilkan copy-on-edit dengan identity baru sesuai aturan resource.

Safe cleanup menggunakan reference graph dan memeriksa project, final backup/patch, dependency component, pinned version, serta last-known-good requirement sebelum permanent delete.

---

# 11. dependency.lock

Setiap project mempunyai dependency lock untuk merekam versi aktual dependency penting yang digunakan.

Contoh konseptual:

```text
component.statusCard = 1.6
asset.icon.save = 2.1
schema = 5
adapter.android = 3.2
```

Tujuan:

- reproducible project/build;
- library update tidak mengganti dependency diam-diam;
- perubahan dependency menjadi operasi eksplisit dan dapat diaudit.

---

# 12. Component Editor dan Component Contracts

Per component terdapat contract berikut:

```text
Property Contract
Event/Action Contract
State Contract
Binding Contract
Accessibility Contract
Asset/Dependency Contract
```

Component Manifest minimal mempunyai:

```text
Stable Component ID
Name
Version
Source
Schema Version
Contracts
Dependencies
Checksum
```

Lifecycle:

```text
DRAFT
READY
DEPRECATED
ARCHIVED
```

Component yang gagal Validation Gate dapat disimpan sebagai DRAFT, tetapi tidak dianggap production-ready.

Validation Gate mencakup manifest/contracts/dependencies/assets/version/schema. Component Test juga harus PASS sebelum READY.

---

# 13. Component Version, Master, Instance, Variant, Composite

## 13.1 Versioning

Mengedit component READY menghasilkan working version baru. Project yang memakai versi lama tidak otomatis berubah.

Update melakukan impact/compatibility check terhadap property, binding/action, state, event, asset dependency, dan reference.

Safe update all hanya tersedia bila perubahan kompatibel.

## 13.2 Variant

Variant adalah delta dari base component, bukan clone penuh. Functionality master diwarisi kecuali override yang valid.

## 13.3 Composite Component

Composite terdiri dari child component dengan Stable Internal ID, dependency manifest, dan outer exposed contract. Internal property/event dapat disembunyikan.

## 13.4 Master vs Instance

Library mempunyai Master. Project/screen memakai Instance. Perubahan Instance menjadi override/delta.

Pilihan edit:

```text
Edit Instance
Edit Master
```

Master update melakukan impact check sebelum memengaruhi instance.

## 13.5 Detach Instance

Detach menghasilkan component independent dengan Stable ID baru. Override saat ini menjadi baseline. Component masuk DRAFT sampai tervalidasi.

## 13.6 Rebase Instance

Setelah master update, rebase mempertahankan override bila kompatibel dan melakukan merge deterministik. Conflict meminta pengguna memilih Master atau Instance dengan diagnostic `⧉ Copy`.

## 13.7 Migration Map

Breaking rename/change dapat mempunyai migration map, misalnya:

```text
titleText → title
```

Migration deterministik dilakukan otomatis. Unresolved migration menghasilkan issue dan tidak dipaksakan.

---

# 14. Component Sandbox / Test

Sebelum READY, component diuji untuk:

```text
Visual
State
Binding
Event
Dependency
Resize
Variant
Animation
Accessibility
```

READY membutuhkan:

```text
Validation Gate PASS
+
Component Test PASS
```

---

# 15. Save, Working State, Undo/Redo

Satu tombol Save berlaku untuk seluruh project, bukan Save terpisah per UI/Logic/Data/Binding/Asset.

Alur:

```text
Working State
↓
Cross-Area Validation
↓
Transactional Save
↓
Manifest/Index Update
↓
Revision
```

Manual Save only. Tidak ada autosave project persistent.

Unsaved exit/project switch:

```text
Simpan
Keluar Tanpa Simpan
Batal
```

Undo/Redo adalah history terpadu lintas UI/Logic/Data/Binding/Asset secara kronologis. Operasi besar dapat dibungkus sebagai satu transaction.

---

# 16. Revision, Recovery, Backup

Tiga konsep dibedakan:

```text
Active Data/Asset
= kondisi project aktif

Revision
= history/version dari manual Save

Recovery/Backup
= salinan keselamatan
```

Revision tidak harus menduplikasi seluruh asset; dapat menggunakan delta + metadata selama restore tetap deterministik.

Recovery source priority:

```text
1. Final Recovery Snapshot
2. Last Valid Recovery
3. Last Valid Revision
4. Older Valid Revision
```

ToolBox menampilkan recommendation dan preview sebelum restore. Tidak memilih diam-diam.

Setelah restore, project divalidasi lagi. Gagal restore/validation tetap berada pada Repair Mode.

---

# 17. Project Lifecycle

Status project:

```text
ACTIVE
PAUSED
READY
ARCHIVED
TRASH
```

Aturan recovery:

- ACTIVE: recovery normal berjalan sesuai kebutuhan;
- PAUSED: tidak membuat backup baru terus-menerus; pertahankan recovery terakhir dan revision penting;
- READY: buat Final Recovery Snapshot dan bersihkan temporary backup yang tidak diperlukan;
- TRASH: project + backup lokal terkait masuk Trash;
- permanent delete menghapus project backup lokal terkait setelah confirmation.

Project Manager dapat menyediakan:

```text
Open
Pause/Activate
Ready
Archive
Duplicate
Export
Delete
```

Project READY dapat dibuka kembali menjadi ACTIVE.

---

# 18. Project Load dan Repair Mode

Saat project dibuka, lakukan quick integrity check terhadap:

```text
Manifest
Files
Assets
Schema
Binding
Actions
External Corruption Indicators
```

Issue ringan dapat membuka Repair Mode. Critical issue tidak boleh masuk normal load.

Repair Mode:

- inspection seluruh area;
- risky runtime dinonaktifkan;
- broken reference ditandai;
- Save hanya setelah minimum validation;
- validate ulang sebelum kembali ke normal.

Semua issue penting mendukung `⧉ Copy`.

---

# 19. Finalization dan Build Handoff

Alur project:

```text
ACTIVE
↓
Tandai READY
↓
Final Project Validation
↓
READY
↓
Kirim ke Build
↓
Local Build Contract Validator
↓
READY TO BUILD
↓
GitHub Build/Test
```

Final Project Validation meliputi UI/Logic/Data/Binding/Asset/Component/Manifest.

GitHub hanya menerima candidate `READY TO BUILD`.

Jika build gagal, project kembali/bertahan pada READY dan final APK lama yang valid tidak ditimpa.

Setelah build PASS:

```text
Verify APK
↓
Post-Build Test
↓
BUILD VERIFIED
↓
Final Qualification
↓
FINAL READY
```

Final Qualification memeriksa APK/revision/checksum/signature, patch/manifest, binding/action/assets ledger, dan Final Recovery Snapshot.

Development setelah final membuat Working Revision baru. Final baseline lama tetap terkunci sampai candidate baru menyelesaikan seluruh pipeline.

Baselines utama:

```text
Current Final
Last Known Good
Previous Final
```

Jika baseline keempat muncul, oldest cleanup candidate memerlukan confirmation pengguna.

---

# 20. Build Settings

`Project Settings > Build` dapat memuat:

```text
App Name
Package
Version Name
Version Code
Target SDK
Min SDK
ABI
Build Type
Signing Reference
Permissions
Variants
Output
```

ToolBox host sendiri ditargetkan Android 11/API 30/arm64-v8a. Project eksternal tidak dipaksa identik jika requirement project berbeda.

Project Configuration dibedakan dari ToolBox Device/Host Target.

---

# 21. Export / Import Project

Format project ToolBox dapat berupa `.toolboxproject` yang berisi:

```text
Manifest
UI
Logic
Data
Binding
Asset References
Component References
Important Revision Metadata
```

Cache/temp/backups tidak ikut kecuali dipilih eksplisit.

Import melakukan integrity/compatibility check. Jika Project ID sama:

```text
Open as New
Replace
Cancel
```

Tidak ada silent overwrite.

---

# 22. Import Project Eksternal

ToolBox dapat menerima project dari Android Studio, Replit, AI builder, ZIP/folder, teman, atau tool lain.

Pipeline:

```text
Detect Source
↓
Choose Adapter
↓
Capability Scan
↓
Normalize ke Internal Model
↓
Dependency/Reference Check
↓
Editor
```

Source asli tidak dimodifikasi saat import. Bagian readable/editable dinormalisasi; bagian unresolved dipertahankan. Partial import diperbolehkan jika aman.

Adapter dapat berkembang tanpa mengubah kernel.

---

# 23. Round-Trip Tracking dan Sync ke Source

Untuk project eksternal, ToolBox mencatat pemetaan:

```text
Source Asli
↕
ToolBox Internal Model
```

Setiap bagian diberi status seperti:

```text
Round-Trip Supported
Partial
Import Only
Read Only
Unsupported
```

Dua aksi utama:

```text
EXPORT PROJECT
SYNC KE SOURCE
```

`EXPORT PROJECT` adalah jalur default yang aman.

`SYNC KE SOURCE` hanya tersedia bila adapter mendukung. Alur:

```text
Impact Check
↓
Backup Source Changes
↓
Change Set
↓
Apply ke Source
↓
Validate
```

Jika bagian tidak round-trip safe, ToolBox tidak memaksakan overwrite dan memberikan `SYNC LIMITATION` + `⧉ Copy`.

---

# 24. Edit Bridge untuk Aplikasi Terinstal

Bridge standar:

```text
Installed App
↓
Edit Bridge
↓
Adapter yang sesuai
↓
Capability Scan
↓
Editor
```

Adapter tidak menilai asal aplikasi. Adapter hanya menerjemahkan editing door/capability yang memang tersedia menjadi capability ToolBox.

Adapter baru harus dapat ditambahkan melalui extension point tanpa perubahan kernel besar.

---

# 25. Live Edit Session

Jika aplikasi terinstal menyediakan capability yang sah:

```text
Select Target
↓
Capability Scan
↓
Live Session
↓
Edit Working State
↓
Preview/Test
↓
TERAPKAN
```

Label UI dibedakan:

```text
SIMPAN
= menyimpan project ToolBox

TERAPKAN
= menerapkan perubahan ke target live
```

`TERAPKAN` disembunyikan jika tidak ada live target/capability Apply.

---

# 26. Apply Transaction dan Rollback

Apply harus transactional sejauh capability target memungkinkan.

```text
Quick Validate
↓
Temporary Apply Snapshot
↓
Apply Change Set
↓
Verify
↓
PASS atau Rollback
```

Jika Apply gagal, Project Save tetap aman. Jika rollback gagal:

```text
APPLY_RECOVERY_REQUIRED
```

Apply progress menampilkan stage aktual. Tidak boleh ada Apply kedua selama transaction aktif.

Crash-safe transaction state harus disimpan cukup untuk recovery.

---

# 27. Selective Apply dan Dependency Ordering

ToolBox dapat menyediakan:

```text
Terapkan Perubahan
Terapkan Semua
```

Selective Apply memakai Change Set. Dependency order dihitung otomatis. Bagian yang saling tergantung dikelompokkan sebagai satu transaction bila diperlukan.

Jika satu stage gagal, stage berikutnya berhenti dan perubahan yang sudah diterapkan di-rollback sesuai capability.

Schema/data change besar dapat meminta `Terapkan Semua` bila selective apply tidak aman.

---

# 28. External Target Change Conflict

Jika target berubah selama Live Session:

```text
Reload Target
Compare
Keep Working State
```

Apply diblokir sampai conflict diselesaikan.

Resolution dilakukan per section. Pengguna dapat memilih Target atau Working State. Auto-merge hanya dilakukan bila deterministik.

---

# 29. Live Session Close, Resume, Journal

Close session:

Jika unsaved:

```text
Simpan
Buang
Batal
```

Jika saved tetapi belum applied:

```text
Terapkan
Keluar Tanpa Terapkan
Batal
```

Session cleanup melepaskan target/listener/renderer/cache/job. Apply/Rollback tidak boleh diputus paksa kecuali state recovery sudah aman tercatat.

Session Resume menyimpan state ringan:

```text
Project
Target
Editor Function
Screen
Selected Object
Working State Journal
Apply/Rollback Status
```

Setelah crash:

```text
Lanjutkan Sesi
Buka dari Awal
```

Session Journal adalah delta temporary, bukan Project Save. Big asset direferensikan, bukan disalin. Save/Discard membersihkan journal bila aman. Unresolved Apply journal tidak boleh dihapus.

---

# 30. Resource Guard dan State Handoff

Resource Guard menjaga editor sesuai target perangkat terbatas.

Aturan:

- satu project aktif penuh;
- satu fungsi Editor heavy-active;
- release screen/flow yang tidak aktif;
- asset full-resolution on-demand;
- bounded cache;
- memory pressure cleanup tidak boleh menghilangkan Working State + Journal;
- tidak perlu popup untuk cleanup normal;
- popup hanya bila operation tidak dapat dilanjutkan.

Saat pindah UI/Logic/Data/Binding/Asset, state ringan disimpan:

```text
Screen/Flow
Object Selection
Scroll
Zoom
Edge Context
Working State
```

Heavy renderer/resources dilepas, lalu dipulihkan bila pengguna kembali.

Prefetch hanya metadata ringan dan dapat dibatalkan Resource Guard.

---

# 31. Diagnostics Center

Diagnostics terpadu untuk:

```text
Error
Warning
Info
```

Issue minimal mempunyai:

```text
Project
Function
Screen/Object
Reason
Stable Error Code
Status
```

Tap issue membuka lokasi terkait. Setiap issue dapat memiliki `⧉ Copy`; tersedia pula `Copy Semua`.

Popup immediate dapat muncul saat operasi, sedangkan Diagnostics Center mempertahankan unresolved issues lintas fungsi.

---

# 32. Editor Modes

Mode utama:

```text
EDIT
PREVIEW
TEST
LIVE
```

## EDIT
Mengubah Working State.

## PREVIEW
Menjalankan tampilan seperti app tanpa overlay editor. Dapat mempreview orientation, size class/custom size, theme light/dark bila didukung, state, animation, layout, dan asset.

## TEST
Menjalankan interaction terhadap Working State Editor: navigation, button, binding, state, logic, data, asset, animation. Hasil PASS/FAIL. Failure dapat menyimpan screenshot ringan session-only dan issue `⧉ Copy`.

## LIVE
Hanya tersedia bila target live aktif. Menampilkan status target, Apply, Compare, Apply History, external-change notices.

Capability yang tidak tersedia membuat mode terkait disembunyikan.

---

# 33. Apply History

Apply History menyimpan metadata ringan:

```text
Timestamp
Target
Revision
Change Summary
Verify Result
Rollback Result
```

Entry normal/success dapat dihapus otomatis setelah sekitar satu jam. FAIL, rollback failure, atau `APPLY_RECOVERY_REQUIRED` dipertahankan sampai resolved.

---

# 34. Permission Manager

Permission Manager bersama melakukan:

- derive permission requirement dari UI/Logic/Asset/Action;
- mendeteksi missing/unused permission;
- menghindari overpermission;
- memberi diagnostic + `⧉ Copy`;
- menjadi bagian Build Contract Validator.

---

# 35. Navigation Manager

Navigation Graph dibuat dari screen/actions memakai Stable IDs.

Aturan:

- target harus valid;
- rename screen tidak merusak reference;
- deleted target memberi warning;
- circular navigation boleh bila valid;
- dead-end/unreachable screen dapat menjadi diagnostic;
- validator dijalankan sebelum build.

---

# 36. Theme / Design Token Manager

Token bersama dapat mencakup:

```text
Color
Typography
Radius
Spacing
Elevation
Icon Style
```

Component default mengikuti token. Perubahan token merambat ke object yang masih mengikuti token. Manual override tetap dipertahankan.

Menghapus token yang masih dipakai menghasilkan issue.

---

# 37. Screen Manager

Screen Manager menyediakan:

```text
Add
Duplicate
Rename
Set as Start
Reorder
Delete
```

Setiap screen mempunyai Stable ID. Rename tidak merusak navigation. Duplicate mendapat identity baru. Exactly one start screen berlaku bila project runtime membutuhkannya.

Delete terhadap referenced screen memberi warning + `⧉ Copy`.

---

# 38. Responsive Preview

Responsive Preview memakai screen yang sama, bukan clone screen.

Target preview dapat memuat:

```text
Portrait
Landscape
Phone Small
Phone Normal
Phone Large
Custom
```

Responsive issue masuk Diagnostics.

---

# 39. Visual / Properties / Code

Tiga view memakai **model data yang sama**:

```text
Visual
Properties
Code
```

Visual adalah default. Properties menampilkan struktur property. Code menjadi fallback/advanced representation.

Two-way synchronization hanya dilakukan bila perubahan dapat direpresentasikan secara aman oleh internal model.

Jika source/code tertentu tidak dapat direpresentasikan visual secara lossless, ToolBox memberi warning dan tidak memaksa konversi yang merusak model.

Code bukan arbitrary executable plugin yang dijalankan di host ToolBox.

---

# 40. Text / Localization Manager

Semua text dapat dikelola melalui Stable String ID.

Contoh:

```text
login_title
├─ id-ID: Masuk
├─ en-US: Login
└─ ja-JP: ログイン
```

Kemampuan:

- default language;
- translations;
- literal atau resource reference;
- usage tracking;
- safe rename via ID;
- referenced deletion warning;
- fallback language;
- missing translation diagnostic;
- import/export translation;
- live language preview.

Multi-language tidak dipaksa jika project tidak memerlukannya.

---

# 41. Global Search

Satu pencarian lintas:

```text
Screen
Component
Asset
Text
Logic
Action
Binding
Data Field
Template
Diagnostic
Token
Stable ID
```

Search memakai index metadata ringan dan tidak memuat seluruh heavy project content.

Tap hasil membuka lokasi terkait.

Global Replace, bila disediakan, harus menjadi operasi terproteksi dengan impact preview dan satu Undo transaction.

---

# 42. Cross-Project Copy / Import

Object/screen/component/template/asset dapat diambil dari project lain.

Sistem menghitung dependency closure yang diperlukan sebelum import.

Aturan:

- preview import plan;
- Stable ID/version conflict handling;
- instance/object hasil copy mendapat identity baru bila semestinya;
- project-specific secrets/signing credential tidak ikut disalin;
- dependency compatible ikut dibawa;
- partial import hanya bila dependency minimum tetap valid.

---

# 43. Template Editor

Template adalah reusable arrangement/screen structure, berbeda dari satu Component.

Template dapat dibuat dari:

```text
Whole Screen
Selected Area
Selected Component Group
```

Template dapat memuat layout, component references, style/token, optional placeholder binding, asset requirement, dan variant.

Saat template dimasukkan ke screen, object instance tetap dapat diedit normal dan tidak terkunci secara permanen pada template.

Template harus mempunyai identity/version/validation yang jelas sebelum masuk Library.

---

# 44. Schema dan Project Migration

Schema adalah kontrak struktur data project/component.

Setiap project mempunyai schema version. Migration bersifat incremental:

```text
Schema 3
→ Schema 4
→ Schema 5
```

Aturan migration:

- pre-migration safety point/snapshot;
- transform deterministik dapat otomatis;
- unknown/unresolved content dipertahankan jika mungkin;
- original tidak ditimpa sebelum hasil valid;
- migration validation wajib;
- gagal → rollback/Repair Mode;
- issue mendukung `⧉ Copy`.

---

# 45. External Import Safety

Safety ditentukan dari **content dan capability**, bukan asal project.

Import memeriksa antara lain:

```text
Format
Manifest
Path Traversal
Checksum
Dependency
Malformed Data
Size Bomb
Duplicate/Conflicting ID
Asset Integrity
Native Binary Metadata
Script/Executable Content
```

Arbitrary executable content tidak dijalankan hanya karena ikut berada dalam import package.

Legitimate source code tetap dapat disimpan/dibaca sebagai source, tetapi host ToolBox tidak menjalankan plugin/script asing secara otomatis.

Bagian unsupported/risky dapat dikarantina atau dinonaktifkan tanpa harus menolak seluruh project bila partial import tetap aman.

---

# 46. Backup Final APK + PATCH

Untuk final app, backup utama di repository backup private adalah:

```text
App.apk
App.patch
```

`App.patch` adalah **satu cumulative package**, bukan kumpulan fragment version yang terus bertambah. Patch dapat memuat component/asset yang dipilih pengguna beserta manifest, Stable IDs, versions, checksums, dan dependency metadata.

Restore dapat memilih component/asset tertentu dari satu patch.

Atomic patch update:

```text
Build Temporary Patch
↓
Validate
↓
Replace Previous Patch
```

Jika validation gagal, patch lama tetap tersedia.

APK restore memverifikasi checksum/signature sebelum install. Patch restore melakukan compatibility check dan tidak silently overwrite versi lebih baru.

Final APK/patch tidak dimuat ke RAM kecuali restore/import benar-benar diminta.

---

# 47. Remote Backup Integrity

Backup remote baru dianggap valid setelah:

```text
FINAL READY
↓
Checksum + Manifest
↓
Upload
↓
Remote Verification
↓
BACKUP VERIFIED
```

Backup lama tidak otomatis dihapus hanya karena upload baru berhasil.

Delete project lokal **tidak** menghapus backup GitHub. Penghapusan backup remote harus menjadi aksi terpisah dan eksplisit.

---

# 48. Repository Build/Test dan Private Asset Model

Arsitektur repository yang disetujui:

```text
PUBLIC
└─ Build/Test Engine Repository

PRIVATE
├─ Project-A
├─ Project-B
├─ Project-C
├─ Asset Repository terkait
└─ App-Backup
```

Repo public Build/Test berisi reusable workflow/validator/test tools. Source project tetap private.

Project private menjadi caller workflow. Workflow public sebaiknya dipin ke tag/commit SHA yang tervalidasi agar perubahan build engine tidak memengaruhi semua project secara diam-diam.

Private asset access memakai credential least-privilege yang hanya dapat membaca resource diperlukan. Credential tidak boleh ikut source, logs, artifact, APK, patch, atau repo publik.

Credential backup dipisahkan dari credential asset dan hanya mempunyai hak yang diperlukan untuk repository backup.

---

# 49. Backup Lifecycle terhadap Project Status

```text
ACTIVE
→ recovery normal

PAUSED
→ hentikan backup baru rutin; pertahankan recovery terakhir + revision penting

READY
→ Final Recovery Snapshot + cleanup temporary backup

TRASH/DELETE LOCAL
→ tidak menghapus remote backup
```

Repo backup private dibiarkan kosong sampai benar-benar ada project `FINAL READY`.

---

# 50. Audit Editor — Hasil Final Putaran 2

Audit mencakup:

```text
Editor Architecture
UI / Logic / Data
Binding / Asset
Component System
Project Lifecycle
Save / Revision / Recovery
Live Edit / Apply
External Project Import
Memory / Resource Guard
Build Handoff
Backup Model
Migration / Compatibility
Diagnostics
Edit Bridge
Self-Edit Protection
Dependency Lock
Round-Trip Tracking
Remote Backup Integrity
Private Asset Access
```

Status:

```text
BLOCKER : 0
GAP     : 0
EDITOR DESIGN AUDIT : PASS
```

Lima gap Audit Putaran 1 sudah ditutup:

1. Edit Bridge berbasis adapter/capability;
2. Self-Edit Protection melalui staging/validation/recovery/rollback;
3. `dependency.lock` per project;
4. delete lokal tidak menghapus remote backup;
5. Round-Trip Tracking untuk external project/source sync.

Detail repository baru tidak mengubah status PASS arsitektur Editor, tetapi setelah asset/master dipindahkan tetap diperlukan **audit delta repository** untuk memeriksa path, manifest, references, dependency lock, build input, credential routing, dan backup routing.

---

# 51. Keputusan yang Menggantikan Konsep Lama

Keputusan berikut harus dipakai bila terdapat dokumentasi lama yang bertentangan:

```text
Deck Panel
→ DIHAPUS dari shell terbaru

5 Editor/Tool terpisah di level UX
→ DIGANTI oleh 1 Editor dengan 5 fungsi internal

ToolBox-aware sebagai syarat asal aplikasi
→ DIGANTI oleh Capability Scan + Edit Bridge berdasarkan capability yang tersedia

Manual Binding satu-per-satu sebagai jalur utama
→ DIGANTI oleh Auto Connect + Global Binding Registry

Semua fungsi aktif bersamaan di RAM
→ TIDAK DIPAKAI; hanya satu fungsi heavy-active

Autosave project
→ TIDAK DIPAKAI; Manual Transactional Save

Delete local project menghapus remote backup
→ DILARANG
```

---

# 52. Status Dokumen dan Tahap Berikutnya

Dokumen ini menyimpan rancangan Editor yang telah melewati Audit Putaran 2 dan berstatus PASS.

Tahap repository berikutnya dilakukan satu per satu untuk keselamatan:

```text
1. Konsolidasi rancangan ke MD        ← selesai oleh dokumen ini
2. Salin/konsolidasikan Master MD ke repo private ToolBox
3. Pindahkan asset pengembangan yang relevan ke repo private ToolBox
4. Verifikasi salinan sebelum menghapus sumber apa pun
5. Siapkan repo public Build/Test reusable
6. Siapkan akses private asset/backup least-privilege
7. Lakukan Audit Delta Repository
```

Tidak ada asset atau backup final yang boleh dianggap selesai hanya karena file telah dipindahkan. Semua perpindahan harus diverifikasi sebelum sumber lama dibersihkan.
