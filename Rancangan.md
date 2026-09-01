# Rancangan ToolBox
## Visual Declarative App Factory + Managed Repair & Evolution Platform
### Rancangan Keseluruhan Terpadu

> **Status dokumen:** rancangan konseptual terperinci yang masih dapat dimatangkan.
>
> Dokumen ini **bukan aturan kerja**, bukan prosedur agen, dan bukan instruksi build. Dokumen ini berdiri sendiri sebagai gambaran arsitektur, perilaku sistem, kontrak data, model UI, model penyimpanan, model integrasi, model recovery, model build, dan arah teknis ToolBox.
>
> Tujuan dokumen ini adalah menyimpan rancangan ToolBox secara utuh agar fondasi tidak berubah-ubah ketika implementasi dimulai.

---

# 1. Identitas Produk

ToolBox adalah **satu aplikasi Android** yang menjadi **rumah bagi banyak tool/engine**.

ToolBox mempunyai dua keluarga kemampuan besar:

1. **Visual Declarative App Factory** — membuat aplikasi secara visual/deklaratif sampai menghasilkan project yang siap divalidasi dan dibuild di GitHub.
2. **Managed Repair & Evolution Platform** — memperbaiki, mengubah, menguji, membekukan, memulihkan, dan mengembangkan aplikasi yang memang ToolBox-aware melalui kontrak yang telah disepakati.

ToolBox **bukan APK scanner umum** dan tidak dirancang untuk membongkar atau memodifikasi aplikasi pihak lain secara sembarang.

Target produk utama:

```text
Android 11
API 30
arm64-v8a
```

APK final dibangun di GitHub. HP dipakai untuk merancang, mengedit, memvalidasi, mengelola project, membuat paket build, menjalankan preview, dan mengelola lifecycle ToolBox.

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

# 3. Arsitektur Rumah ToolBox

ToolBox dipandang sebagai rumah:

```text
ToolBox Host
│
├─ Shell UI
│  ├─ Bubble
│  ├─ Edge Panel
│  └─ Deck Panel
│
├─ UI Editor
├─ Logic / Flow Editor
├─ Data Tool
├─ Integration / Binding Tool
├─ Asset Tool
├─ Build Manager
├─ Recovery Manager
├─ Freeze / Evolution Manager
└─ tool/engine lain
```

Tool tidak dianggap aplikasi terpisah secara instalasi. Semuanya berada dalam satu APK, tetapi dipisahkan melalui contract dan lifecycle.

Setiap tool hanya mengetahui tool lain melalui:

- Tool Registry;
- Component Registry;
- Capability Registry;
- Action Registry;
- Event Contract;
- Property Contract;
- Data Contract;
- Navigation Contract;
- Permission Contract;
- Stable IDs;
- dependency metadata.

Tidak ada kebutuhan untuk menyimpan reference hidup dari satu tool ke tool lain.

---

# 4. Halaman sebagai Wujud Tool/Engine

Halaman adalah **bentuk visual aktif dari ToolBox atau engine yang sedang digunakan**.

ToolBox sendiri tidak perlu memenuhi layar dengan chrome/editor permanen. Area utama layar adalah halaman aktif.

Contoh:

```text
Shell ToolBox
   ↓
Halaman UI Editor
```

atau:

```text
Shell ToolBox
   ↓
Halaman Logic Editor
```

atau:

```text
Shell ToolBox
   ↓
Halaman Recovery
```

Dengan demikian layar utama selalu dapat digunakan semaksimal mungkin oleh tool aktif.

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

Hanya memuat:

- metadata yang diperlukan;
- screen/resource aktif;
- contract terkait;
- asset preview yang diperlukan;
- working state kecil.

## 5.2 ACTIVE

Tool mempunyai working set sendiri dan tidak membuat tool lain aktif tanpa kebutuhan nyata.

## 5.3 SAVE

Save bukan autosave. Save hanya berjalan ketika pengguna secara eksplisit memerintahkannya atau ketika operasi sistem yang memang didefinisikan sebagai commit eksplisit dilakukan.

## 5.4 RELEASE

Saat tool dilepas, resource runtime harus dilepas:

- Activity/View/editor surface yang tidak lagi dipakai;
- renderer;
- bitmap;
- preview high-resolution;
- render buffer;
- temporary graph;
- parser result;
- listener;
- observer;
- callback;
- coroutine/job;
- thread;
- timer;
- stream;
- file handle;
- RAM cache;
- temporary import/export buffer;
- reference Context/View lama;
- dependency runtime antartool.

Yang tetap ada adalah data persistent dan state kecil yang memang diperlukan untuk kembali ke pekerjaan tersebut.

---

# 6. Shell UI ToolBox

Shell ToolBox terdiri dari tiga kontrol utama:

```text
Bubble     = pusat perintah / mouse ToolBox
Edge Panel = sumber asset/component
Deck Panel = alat edit manual
```

Ketiganya berada di atas halaman aktif dan tidak menjadi bagian dari project aplikasi yang sedang dibuat.

Shell mempunyai coordinate space sendiri dan tidak ikut berubah ketika viewport editor di-zoom atau di-pan.

---

# 7. Bubble — Draggable Priority Overlay

Bubble adalah **Top-Layer Draggable Floating Overlay** dengan perilaku:

- selalu berada di lapisan interaksi tertinggi dalam ToolBox;
- dapat digeser bebas;
- tidak boleh keluar dari batas layar yang valid;
- posisi terakhir dapat disimpan;
- sentuhan pada area Bubble mutlak diterima Bubble terlebih dahulu;
- object UI di bawah Bubble tidak menerima sentuhan;
- untuk mengakses object yang tertutup, Bubble digeser;
- tidak menggunakan touch-through sebagai perilaku default;
- tidak memerlukan overlay lintas-aplikasi selama penggunaannya hanya di dalam ToolBox;
- dapat membuka pusat perintah, pengaturan, mode edit, binding, navigasi ke tool terkait, dan fungsi sistem ToolBox.

Nama konsep teknis:

> **Draggable Priority Overlay Bubble**

atau:

> **Draggable topmost overlay view with touch interception and bounded movement.**

Bubble sendiri dapat diedit melalui pengaturan khusus UI Editor untuk kontrol ToolBox.

Properti yang dapat dikustomisasi mencakup paling tidak:

- posisi;
- ukuran;
- bentuk;
- transparansi;
- pola buka menu;
- perilaku drag;
- posisi default.

Selalu tersedia safe constraints:

- ukuran tidak boleh menjadi nol;
- Bubble tidak boleh sepenuhnya keluar layar;
- konfigurasi yang membuat Bubble tidak dapat diakses harus dapat dipulihkan;
- tersedia mekanisme reset layout shell ke kondisi aman.

---

# 8. Edge Panel

Dalam kondisi tertutup, Edge Panel hanya tampil sebagai **garis/handle tipis**.

Fungsi utama Edge Panel adalah menyediakan asset/component untuk dimasukkan ke halaman dengan drag and drop.

Prinsip:

```text
Edge Handle
↓ tarik
Edge Panel terbuka
↓
pilih asset/component
↓
drag & drop
↓
object/instance dibuat
```

Isi penuh panel dimuat secara lazy. Handle tetap ringan ketika panel tertutup.

## 8.1 Portrait

Dalam portrait:

- Edge berada di kiri atau kanan;
- dapat dipindahkan kiri ↔ kanan;
- area gesture hanya sebesar handle yang diperlukan;
- jangan mengambil seluruh sisi layar untuk gesture exclusion.

## 8.2 Landscape

Dalam landscape:

- Edge berpindah menjadi panel horizontal;
- posisi default berada di bawah;
- dapat dipindahkan bawah ↔ atas;
- alasan utama adalah kebutuhan ruang horizontal yang lebih besar untuk menampilkan asset/component.

---

# 9. Deck Panel

Deck adalah tempat semua pengaturan manipulasi manual object.

Contoh fungsi:

- memperbesar;
- memperkecil;
- memperlebar;
- mempersempit;
- posisi;
- margin;
- padding;
- radius;
- opacity;
- rotation;
- alignment;
- style;
- property yang dideklarasikan Component Contract.

## 9.1 Portrait

- Deck berada di bawah;
- default tertutup;
- ada bulatan/kait kecil di sudut untuk menariknya terbuka;
- isi Deck baru dimaterialisasi penuh ketika diperlukan.

## 9.2 Landscape

Saat Edge pindah ke bawah/atas, Deck berpindah ke sisi agar area horizontal bawah dapat dipakai Edge.

Dengan demikian Edge dan Deck **bertukar sektor kerja** sesuai orientasi.

---

# 10. Edit Kontrol ToolBox

Bubble, Edge Handle, Edge Panel, Deck Handle, dan Deck Panel dapat diedit sebagai **kontrol ToolBox**, bukan sebagai object aplikasi.

Mode ini terpisah dari edit project:

```text
Edit Project UI
≠
Edit ToolBox Controls
```

Konfigurasi shell disimpan sebagai editor/tool settings, tidak ikut masuk ke definisi aplikasi yang dibuild.

---

# 11. Live Interactive UI Workspace

UI Editor bukan canvas statis.

UI Editor adalah **Live Interactive UI Workspace**: pengguna dapat menjalankan aplikasi yang sedang dirancang seolah-olah aplikasi itu hidup, kemudian masuk ke Edit Mode ketika ingin mengubahnya.

Contoh:

```text
Home
↓ tekan Settings
Settings terbuka
↓ buka sub-menu Privacy
Privacy terbuka
↓ Bubble → Edit ON
↓
edit keadaan visual yang sedang terlihat
```

Navigation graph dihasilkan dari hubungan yang benar-benar dibuat, bukan harus digambar lebih dulu.

---

# 12. Edit OFF dan Edit ON

## 12.1 Edit OFF

```text
EDIT = OFF
```

Layar bekerja seperti aplikasi:

- button menjalankan event;
- navigation berjalan;
- drawer membuka;
- dialog muncul;
- input menerima teks;
- animasi berjalan;
- state dapat berubah.

## 12.2 Edit ON

Edit Mode dipicu dari Bubble.

```text
Bubble
↓
Edit ON
↓
SELURUH SCREEN = AREA EDIT
```

Selama Edit ON:

- sentuhan tidak menjalankan action aplikasi;
- sentuhan memilih object untuk diedit;
- drag/gesture dialihkan ke fungsi editor;
- Deck membaca object yang sedang dipilih;
- screen tetap merupakan screen yang sama.

Tidak ada pemilihan “Edit vs Live” per object. Mode berlaku untuk screen aktif secara keseluruhan.

---

# 13. No-Cloning Editing

Edit Mode **tidak boleh membuat clone screen**.

Dilarang menjadikan hal berikut sebagai mekanisme utama edit:

- duplicate screen;
- duplicate View hierarchy;
- full-screen bitmap copy;
- copy semua object ke graph kedua hanya untuk edit.

Yang digunakan:

```text
Screen yang sama
+
perubahan input behavior
+
working state editor
```

State visual yang sedang terbuka cukup direpresentasikan sebagai metadata ringan, misalnya:

```text
drawer = OPEN
dialog = VISIBLE
selectedTab = 3
scroll = 420
```

Bukan screenshot sebagai source of truth.

---

# 14. Visual State Hold Saat Masuk Edit

Pengguna dapat membawa UI ke kondisi tertentu ketika Edit OFF, lalu mengaktifkan Edit ON.

Contoh:

```text
Drawer OPEN
↓
Bubble → Edit ON
↓
Drawer tetap OPEN
↓
object di dalam drawer dapat diedit
```

Prinsip yang sama berlaku untuk:

- dialog;
- bottom sheet;
- selected tab;
- expanded panel;
- dropdown;
- error/loading/success state;
- komponen interaktif lain.

State tersebut bukan clone layar; hanya state deklaratif/working state yang sedang aktif.

---

# 15. Manual Save Murni

UI Editor menggunakan **manual save murni**.

Tidak ada autosave project yang menangkap setiap perubahan.

Alur:

```text
Edit
↓
Working State RAM
↓
Dirty Flag
↓
Bubble → Save
↓
Transactional Commit
```

Selama belum Save:

- perubahan dapat berada di working state;
- undo/redo aktif dibatasi di working memory;
- project persistent tidak ditulis terus-menerus.

Jika proses mati sebelum Save:

> perubahan sejak Save terakhir boleh hilang.

Risiko ini diterima sebagai konsekuensi desain manual-save.

Jika pengguna mencoba meninggalkan konteks dengan perubahan belum disimpan, UI dapat menawarkan:

```text
Simpan
Buang
Batal
```

Ini bukan autosave.

---

# 16. Undo / Redo

Undo/Redo menggunakan **Atomic Undo/Redo Transaction Group**.

Satu aksi pengguna dapat mengubah banyak resource sekaligus. Undo harus mengembalikan semua bagian terkait secara konsisten.

Contoh satu operasi:

```text
ubah object
+ ubah constraint
+ ubah binding
```

Undo dianggap satu group.

Aturan:

- riwayat dibatasi;
- tidak membuat snapshot penuh project;
- tidak menulis project setiap perubahan;
- setelah Save, revision tersimpan menjadi baseline baru;
- tombstone ID tetap tersedia selama masih diperlukan untuk Undo.

---

# 17. Per-Screen Working Sector

Satu screen aktif = satu working sector aktif.

Project dengan 10, 100, atau 500 screen tidak berarti semua screen aktif di RAM.

Alur normal:

```text
Screen A aktif
↓
Save jika diminta
↓
release renderer/view/resource A bila berpindah
↓
load Screen B
```

Kembali ke A:

```text
read definition A
↓
render A
↓
restore lightweight editor/session state
```

Navigation history menyimpan ID dan state kecil, bukan screen utuh.

Saat transition, dua screen boleh hidup singkat hanya selama transition diperlukan, lalu screen lama dilepas.

---

# 18. Non-Linear Round-Trip Editing

Pengguna tidak dipaksa mengikuti wizard linear.

Pengguna dapat berpindah:

```text
UI ↔ Logic ↔ Data ↔ Integration ↔ Asset
```

pada tahap mana pun.

Semua tool membaca source of truth yang sama dari Project Store dan contract registry.

Tool lain tidak harus tetap hidup untuk mempertahankan hubungan project.

---

# 19. Mode Visual / Properties / Code

ToolBox tetap mendukung tiga representasi kerja:

- **Visual** — jalur utama;
- **Properties** — pengaturan deklaratif terstruktur;
- **Code** — fallback untuk kebutuhan khusus.

Ketiganya merepresentasikan model yang sama dan tidak boleh menjadi tiga sumber kebenaran berbeda.

Code mode bukan jalur dynamic-code execution di HP. Jika menghasilkan source/build logic, source tersebut masuk proses validasi dan hanya dieksekusi/di-compile di lingkungan build yang sah.

---

# 20. Project Store

Project Store adalah **source of truth utama untuk editing**.

RAM hanya working set dan cache tidak pernah menjadi sumber kebenaran.

Struktur utama:

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

Data milik pengguna tetap terlihat dan dapat dipindahkan/dicadangkan pengguna.

Data sensitif atau runtime-only boleh berada di app-private storage.

---

# 21. Hybrid Per-Screen Store

Project menggunakan model:

> **Per-screen package sebagai source of truth + lightweight generated index + asset terpisah.**

Contoh:

```text
Projects/<ProjectName>/
├─ project.json
├─ project.manifest
├─ screens/
│  ├─ <screen-id>/screen.json
│  └─ ...
├─ logic/
├─ data/
├─ bindings/
├─ assets/
├─ styles/
├─ localization/
└─ metadata/
```

Tidak menggunakan satu file raksasa untuk seluruh project dan tidak menggunakan satu file untuk setiap object kecil.

---

# 22. project.json dan project.manifest

Keduanya mempunyai fungsi berbeda.

## 22.1 project.json

`project.json` adalah **Application Definition**.

Mencakup hal seperti:

- project/app identity;
- package identity;
- target platform;
- daftar screen;
- theme;
- navigation root;
- capability yang dipakai;
- konfigurasi aplikasi.

## 22.2 project.manifest

`project.manifest` adalah **Storage Integrity Record**.

Mencatat:

- projectId;
- schemaVersion;
- revision;
- daftar resource penting;
- revision per resource;
- hash/checksum bagian penting;
- asset references;
- status validitas terakhir.

Manifest bukan sumber definisi aplikasi kedua.

Untuk project besar, record integrity dapat dipecah/shard per resource agar satu manifest tidak menjadi bottleneck.

---

# 23. Transactional Save

Save menggunakan pola:

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

Jika gagal:

```text
Rollback / discard staging
↓
Revision valid sebelumnya tetap digunakan
```

Jangan berasumsi operasi rename provider selalu atomic. Keamanan commit ditentukan oleh journal, verification, revision marker, dan last-known-valid record.

Project tidak boleh berada pada kondisi setengah lama + setengah baru.

---

# 24. Revision & Single Writer per Resource

Setiap project/resource mempunyai revision.

Save menggunakan **Optimistic Revision Check + Single Writer per Resource**.

Commit hanya boleh terjadi jika revision yang diharapkan masih sama.

Jika editor lama mencoba menyimpan setelah resource sudah mempunyai revision baru:

```text
STALE_WRITE
```

write ditolak, tidak boleh menimpa data baru.

Untuk transaksi yang menyentuh beberapa resource, digunakan **short-lived Project Commit Coordinator** agar semua resource dipublikasikan sebagai revision project yang konsisten.

Tidak ada global lock panjang untuk seluruh sesi editing.

---

# 25. Schema & Versioning

Versi dipisahkan:

```text
schemaVersion
buildModelVersion
contractVersion
toolVersion
capabilityVersion
componentVersion
```

Prinsip:

> **Independent Versioning + Compatibility Range + Adapter/Migration Layer**

Compatibility tidak mensyaratkan semua versi harus sama.

Contoh:

```text
supportedContract = 2..4
requiredContract = 3
```

Jika kompatibel, langsung digunakan.

Jika dapat diadaptasi, gunakan compatibility adapter.

Jika perlu migration permanen, migration dilakukan secara transactional dan eksplisit.

**Lazy migration tidak berarti menulis project diam-diam ketika resource dibaca.** Yang boleh lazy adalah compatibility/read adapter. Mutasi permanen tetap melalui transaction resmi.

---

# 26. Stable Identity

Setiap entity penting mempunyai Stable ID unik berentropi tinggi.

Minimal:

- Project;
- Screen;
- UI Object;
- Component Definition;
- Component Instance;
- Action;
- Event Binding;
- Data Source;
- Data Field;
- Logic Node;
- Navigation Route;
- Asset;
- Design Token;
- Background Task;
- Capability instance.

Aturan:

```text
rename        → ID tetap
move          → ID tetap
edit property → ID tetap
delete + undo → ID lama kembali
duplicate     → ID baru
copy/paste    → ID baru
import merge  → conflict remap otomatis
```

ID yang pernah dipakai tidak didaur ulang.

---

# 27. Tombstone & Undo Restore

Saat object dihapus, identitasnya dapat masuk tombstone selama masih diperlukan oleh Undo/reference tracking.

Jika Undo Delete:

```text
object kembali
+
ID original kembali
+
binding lama dapat tersambung kembali
```

Tombstone tidak menjadi database sejarah tanpa batas. Setelah history tidak lagi diperlukan, metadata dapat dipadatkan tetapi ID lama tetap tidak digunakan ulang.

---

# 28. Generated Index & Dependency Graph

Project mempunyai index ringan dan dependency graph generated.

Contoh:

```text
obj_search
→ screen_home
→ binding_021
→ browser.search
```

Source of truth tetap Project Store.

Jika index hilang:

```text
Project Store
↓
rebuild
```

Dependency Graph menggunakan Stable ID → Stable ID, bukan View/Context/engine instance.

Selama ada perubahan belum disimpan, editor dapat memakai **in-memory dependency delta overlay**. Graph persistent diperbarui saat Save.

---

# 29. Impact Tracking

Dependency Graph dipakai untuk mengetahui apa yang terpengaruh oleh perubahan.

Contoh:

```text
asset_logo berubah
↓
obj_header_logo
obj_splash_logo
↓
screen_home
screen_splash
```

Hanya dependency terkait yang divalidasi ulang.

Manfaat:

- incremental validation;
- incremental composition;
- targeted diagnostics;
- delete impact preview;
- audit lebih cepat;
- tidak perlu full-project scan terus-menerus.

---

# 30. Component Registry

Component Registry adalah katalog metadata komponen yang tersedia.

Registry tidak memuat engine/runtime hanya untuk discovery.

Metadata minimal:

- componentId;
- label;
- icon;
- category;
- version;
- property contract;
- event contract;
- capability requirements;
- compatibility;
- implementation reference.

Prinsip:

> **Metadata-only discovery, lazy materialization.**

Component Registry bukan source of truth project.

Jika component tidak tersedia, project tidak menghapus instance-nya. Status menjadi:

```text
COMPONENT_UNAVAILABLE
```

---

# 31. Repository Component Registry Inventory

Repository mempunyai inventory machine-readable yang mencatat component/capability/action/asset yang memang dimiliki ToolBox.

Tujuan utamanya adalah membantu audit:

```text
Component
├─ implementation ada?
├─ asset ada?
├─ property contract ada?
├─ event contract ada?
├─ action/binding cocok?
├─ permission contract cocok?
└─ version cocok?
```

Dokumentasi manusia dapat dihasilkan dari inventory, bukan menjadi sumber kebenaran kedua.

Agen audit boleh menghubungkan bagian yang memang terbukti sesuai contract ID.

Agen tidak boleh membuat koneksi hanya berdasarkan kemiripan nama.

Perbaikan otomatis hanya dilakukan bila deterministik, dapat divalidasi, dan tidak mengubah maksud project secara spekulatif.

---

# 32. Property Contract

Setiap component mendeskripsikan property-nya secara deklaratif.

Contract dapat mencakup:

- propertyId;
- type;
- nullability;
- default;
- min/max/range;
- unit;
- enum/choice;
- read-only/editable;
- validation;
- state applicability;
- converter yang sah.

Contoh tipe:

```text
BOOLEAN
NUMBER
DIMENSION
TEXT
COLOR
ASSET
ENUM
URI
LIST
OBJECT
REFERENCE
```

Deck membaca contract dan menghasilkan editor generik.

Tidak boleh ada ratusan `if component == ...` sebagai dasar Deck.

---

# 33. Event Contract

Event mempunyai Stable ID dan typed context.

Contoh:

```text
ui.button.onClick
ui.input.onChange
media.player.onCompleted
```

Contract mendeskripsikan:

- eventId;
- payload/input context;
- output bila ada;
- propagation policy;
- compatible action types.

---

# 34. Action Registry

Action Registry berisi metadata action yang disediakan ToolBox/engine.

Contoh:

```text
navigation.openScreen
browser.search
data.save
media.play
dialog.open
```

Action contract mencakup:

- actionId;
- version;
- category;
- typed input;
- typed output;
- parameters;
- permission requirements;
- execution mode;
- async behavior;
- timeout;
- cancellation;
- idempotency/execution ID bila diperlukan.

---

# 35. Compatibility Matching

Event, data, property, dan action dihubungkan berdasarkan typed contract.

Contoh:

```text
SearchField.text : TEXT
↓ compatible
Browser.Search.query : TEXT
```

Jika input tidak lengkap, binding tidak dianggap selesai.

Safe converter harus eksplisit dan terdaftar.

Tidak ada konversi diam-diam yang tidak dapat dijelaskan.

---

# 36. Composite Action

Satu perintah visual boleh merepresentasikan beberapa action teknis.

Model:

```text
Ordered Steps
+
Success Condition
+
Failure Behavior
+
Optional Fallback
+
Optional Rollback/Compensation
```

Contoh:

```text
Simpan & Lanjut
├─ Save
├─ Validate
└─ Open Next Screen
```

Jika langkah kedua gagal, langkah ketiga tidak berjalan kecuali contract menyatakan sebaliknya.

Untuk action asynchronous/retry-sensitive ditambahkan:

- timeout;
- cancellation;
- execution ID;
- idempotency rule.

Rollback tidak dipaksakan pada operasi yang secara alamiah tidak dapat dibatalkan seperti pesan/pembayaran yang sudah selesai.

Jika flow sudah terlalu kompleks, representasinya dipindahkan ke Logic/Flow Editor.

---

# 37. Navigation Contract

Navigation menggunakan Stable Screen ID.

```text
Button.onClick
↓
navigation.openScreen
↓
screen_settings
```

Nama screen boleh berubah tanpa memutus navigation.

Navigation contract dapat mempunyai typed parameters.

Contoh:

```text
screen_product_detail
requires productId : PRODUCT_ID
```

Build Validator memastikan parameter wajib lengkap.

Navigation Graph adalah visualisasi generated, bukan source of truth.

Broken target menjadi:

```text
BROKEN_NAVIGATION_REFERENCE
```

bukan langsung dihapus.

Portrait/landscape tetap satu Screen ID.

---

# 38. Back Stack

Back stack hanya menyimpan:

- Screen ID;
- parameter yang diperlukan;
- lightweight state.

Tidak menyimpan clone screen.

Back/forward history tidak menjadi alasan untuk mempertahankan seluruh renderer lama di RAM.

---

# 39. Data Source Contract

Data Source didefinisikan secara typed dan mempunyai Stable ID.

Contoh:

```text
data_users
├─ userId : ID
├─ name   : TEXT
├─ age    : NUMBER
└─ avatar : IMAGE
```

UI tidak perlu mengetahui apakah data berasal dari:

- database;
- API;
- file;
- form input;
- runtime state;
- hasil action.

---

# 40. Data Binding

Dukungan:

```text
ONE-WAY
Data → UI
```

atau:

```text
TWO-WAY
Data ↔ Input UI
```

Two-way binding tidak menjadi default untuk semua property.

Two-way binding menggunakan change-origin/version token dan cycle suppression agar tidak terjadi update loop seperti:

```text
A → B → A → B ...
```

Derived values harus pure/tanpa side effect.

---

# 41. Lazy/Paged Data Access

Data besar tidak dimuat seluruhnya ke RAM.

```text
Data Source
↓
query/filter/page
↓
working subset
↓
viewport
```

List/Grid menggunakan paging/chunking dan recyclable view binding.

100.000 record tidak berarti 100.000 object UI aktif.

---

# 42. Dynamic List Item Identity

Item dinamis memakai **Stable Data-Item Key**, bukan index posisi.

State selection/animation/binding mengikuti identitas data.

View dapat didaur ulang.

Jika item pindah posisi, identitasnya tidak berubah.

---

# 43. Broken Reference Model

Reference yang hilang tidak dihapus diam-diam.

Status dapat berupa:

- BROKEN_REFERENCE;
- BROKEN_DATA_REFERENCE;
- BROKEN_NAVIGATION_REFERENCE;
- BROKEN_STYLE_REFERENCE;
- MISSING_ASSET;
- COMPONENT_UNAVAILABLE;
- CAPABILITY_INCOMPATIBLE.

Pengguna dapat:

- relink;
- replace;
- restore;
- delete reference.

Mandatory broken reference memblok `READY TO BUILD`.

---

# 44. Logic / Flow Editor

Logic disimpan sebagai **Declarative Flow Graph**.

Node mempunyai Stable ID.

Sumber kebenaran graph adalah:

- Node ID;
- connection;
- input/output;
- condition;
- execution order;
- failure path.

Koordinat diagram hanya metadata editor.

Diagram dimaterialisasi secara lokal berdasarkan jalur yang sedang dibuka.

Tidak perlu merender seluruh graph project.

---

# 45. Branch, Loop, Async

Branch harus eksplisit:

```text
Condition
├─ TRUE
└─ FALSE
```

Async action mempunyai jalur:

```text
START
SUCCESS
FAILURE
CANCELLED/TIMEOUT bila relevan
```

Loop harus mempunyai explicit exit condition dan dapat mempunyai iteration/time limit.

Validator tidak mengklaim dapat membuktikan semua program pasti selesai. Runtime watchdog dapat menghentikan flow yang melewati batas yang telah ditetapkan.

---

# 46. List-First → Auto Diagram Materialization

Pemilihan action/capability menggunakan metadata ringan terlebih dahulu.

```text
List/Grid
↓
pilih item
↓
materialize local diagram bila diperlukan
```

Jangan memilih satu object lalu memuat seluruh graph project ke RAM.

---

# 47. Component Definition, Instance, Template

Dibedakan tiga hal:

## Component Definition

Sumber reusable yang tetap mempunyai hubungan dengan instance.

## Component Instance

Instance ringan yang merujuk definition dan menyimpan override seperlunya.

Mutable runtime state tidak dibagi antar-instance.

## Template

Template adalah titik awal. Setelah digunakan untuk membuat screen/object, hasilnya menjadi mandiri dan tidak berubah otomatis ketika template sumber berubah.

Prinsip:

> **Component linked, Template creates independent result.**

Inheritance bertingkat dalam dihindari. Composition lebih diutamakan.

---

# 48. UI State & State Variant

Satu object dapat mempunyai state seperti:

```text
NORMAL
PRESSED
DISABLED
LOADING
ERROR
SELECTED
```

State tidak membuat clone object.

Variant hanya menyimpan property delta.

Contoh:

```text
NORMAL    : base
DISABLED  : opacity=0.4, enabled=false
```

Layer state dipisahkan agar tidak terjadi state explosion:

```text
UI State
Orientation
Theme
Data State
```

Renderer mengkomposisikan layer saat diperlukan.

---

# 49. Animation Model

Animasi menggunakan model deklaratif:

```text
Trigger
↓
Timeline / Transition
↓
Property Changes
```

Yang disimpan:

- start/end;
- duration;
- easing;
- sequence/parallel relation;
- trigger.

Tidak menyimpan setiap frame hasil render.

Animasi kompleks dibentuk sebagai timeline/sequence, bukan arbitrary runtime script.

---

# 50. Design Token & Theme

Gunakan Stable-ID Design Token.

Contoh:

```text
color.primary
color.background
text.title
radius.medium
spacing.large
```

Object dapat mereferensikan token daripada menggandakan nilai.

Urutan resolusi style dibuat eksplisit:

```text
Component Default
↓
Theme / Token
↓
Component Style
↓
Instance Base Override
↓
Orientation / Adaptive Override
↓
State Variant
↓
Instance State Override
↓
Editor Preview Overlay
```

Tidak boleh ada precedence ambigu.

Jika token hilang, reference ditandai rusak atau pengguna diminta memilih pengganti.

---

# 51. Responsive Layout

UI menggunakan **Responsive Constraint Layout + Container-Based Composition**.

Object dapat memakai:

- fixed;
- content;
- fill/relative.

Constraint dapat mengacu parent/object lain.

Container dasar:

```text
ROW
COLUMN
STACK
GRID
FREE
```

Drag/resize tetap visual, tetapi ToolBox menerjemahkan hasilnya ke constraint/relationship yang sesuai bila memungkinkan.

Tidak mengandalkan koordinat absolut untuk semua hal.

---

# 52. Adaptive Size & Orientation

Tidak dibuat breakpoint tak terbatas.

Gunakan adaptive class terbatas, misalnya:

```text
COMPACT
MEDIUM
EXPANDED
```

plus orientation.

Satu Screen ID memiliki:

```text
Base Layout
Portrait Overrides
Landscape Overrides
Adaptive Overrides
```

Tidak membuat screen clone untuk tiap orientation.

Constraint conflict dan circular dependency harus dapat dideteksi.

---

# 53. Grid, Guide, Snapping

Editor menyediakan optional:

- grid;
- guide;
- edge snapping;
- center snapping;
- object-to-object snapping;
- spacing hints.

Semua bantuan ini adalah metadata/editor behavior dan tidak mengubah aplikasi kecuali pengguna benar-benar melakukan transform.

Snapping dapat dimatikan.

---

# 54. Multi-Select & Group Editing

Multi-select menggunakan **Multi-Selection Contract + Group Transform + Compatible-Property Filtering**.

Hanya property yang kompatibel yang diedit bersama.

Group transform menjadi satu transaction untuk Undo/Redo.

Alignment/distribution mendukung:

- left/right/top/bottom/center;
- equal size;
- equal spacing;
- distribution.

Constraint tetap divalidasi setelah transform.

---

# 55. Parent/Child & Reparenting

Setiap object mempunyai parent ownership yang eksplisit.

Saat object dipindah ke container lain:

- Stable ID tetap;
- coordinate context dihitung ulang;
- constraint disesuaikan;
- z-order disesuaikan;
- dependency yang tidak lagi valid ditandai;
- reference tidak dihapus diam-diam.

---

# 56. Object Lock

Object/group dapat dikunci untuk mencegah perubahan tidak sengaja.

Lock dapat mencakup:

- position;
- size;
- transform;
- property tertentu;
- seluruh object.

Object terkunci tetap dapat diinspeksi dan dibuka kuncinya.

---

# 57. Layer, Z-Order, Hit Test

Layer aplikasi dipisahkan:

```text
BACKGROUND
CONTENT
OVERLAY
MODAL
```

Masing-masing dapat memiliki z-order.

Shell ToolBox tetap berada di atas layer project:

```text
Bubble / Edge / Deck
───────────────────
App Modal
App Overlay
App Content
App Background
```

Saat Edit OFF, topmost eligible object menerima input.

Saat Edit ON, topmost editable object dipilih dan action aplikasi tidak dijalankan.

Editor tetap menyediakan kemampuan memilih underlying object/layer yang tertutup object lain.

---

# 58. Pointer Behavior

Object project dapat mempunyai pointer behavior seperti:

```text
AUTO
NONE
```

`NONE` memungkinkan dekorasi tidak menangkap sentuhan.

Ini tidak berlaku untuk Bubble: Bubble selalu mempunyai prioritas sentuhan pada areanya.

Object yang invisible/not-rendered tidak boleh meninggalkan invisible touch blocker.

---

# 59. Event Propagation

Event propagation tidak meniru model web secara membabi buta.

Contract final:

```text
optional capture/preview
↓
target
↓
optional parent propagation
```

Satu gesture mempunyai satu owner.

Policy eksplisit:

```text
TARGET_ONLY   (default)
CONTINUE
CONSUME
STOP
```

Parent propagation hanya terjadi bila contract mengizinkan.

---

# 60. Input, Gesture, Focus

Input Contract dapat mencakup:

- tap;
- long press;
- double tap;
- drag;
- swipe;
- scroll;
- text input;
- keyboard;
- focus;
- optional multi-touch.

Gesture Resolver memastikan satu gesture tidak mengeksekusi dua action yang bertentangan.

Saat Edit ON, input aplikasi diblok dan dialihkan menjadi input editor.

Focus routing menggunakan Stable ID.

Multi-touch hanya aktif untuk component yang mendeklarasikannya.

---

# 61. Safe Area & Insets

Layout harus sadar terhadap:

- status bar;
- navigation bar;
- gesture area;
- cutout/notch;
- keyboard/IME;
- perubahan viewport.

Object dapat memilih safe-area-aware atau edge-to-edge sesuai contract.

Bubble/Edge/Deck tetap berada dalam interactive bounds yang valid.

Edge handle tidak mengambil exclusion area lebih besar dari yang diperlukan.

---

# 62. Zoom / Pan & Coordinate Space

Zoom/pan editor hanya mengubah viewport editor.

```text
Design Coordinate Space
≠
Editor Viewport Transform
≠
ToolBox Shell Coordinate Space
```

Zoom/pan tidak mengubah layout project.

Bubble/Edge/Deck tetap pada posisi shell, tidak ikut ter-zoom bersama design canvas.

---

# 63. Accessibility & Semantic Contract

Object tidak hanya mempunyai visual, tetapi semantic role.

Contract mencakup:

- role;
- accessible label;
- description;
- focusable;
- enabled;
- selected;
- checked;
- required;
- expanded/collapsed;
- error/loading semantics.

Icon-only interactive control harus memiliki label aksesibilitas.

Interactive hit target default dirancang cukup besar untuk disentuh, sementara visual icon boleh lebih kecil.

Focus order tidak hanya mengandalkan posisi koordinat.

Validator memberi diagnostic untuk masalah accessibility.

---

# 64. Text & Localization

Teks mendukung dua mode:

```text
DIRECT TEXT
TEXT RESOURCE
```

Text Resource memakai Stable ID dan locale variants.

Dukungan minimal:

- fallback locale;
- parameterized text;
- plural/quantity;
- number formatting;
- date/time formatting;
- currency formatting;
- RTL-aware start/end layout.

Satu bahasa tidak membuat clone screen.

Preview/validator dapat mendeteksi overflow/clipping akibat perbedaan panjang bahasa.

---

# 65. Conditional Properties

Property seperti:

- visible;
- enabled;
- selected;
- opacity;
- style variant;

boleh terikat ke state/data melalui **pure declarative expression**.

Expression tidak boleh melakukan network call, database mutation, atau side effect.

Side effect tetap melalui Action/Logic Contract.

---

# 66. Asset Identity

Asset memakai Stable Asset ID.

Object tidak bergantung langsung pada nama/path file.

```text
asset_A72F
→ original file metadata
→ references
```

Nama/lokasi file dapat berubah dan direlink tanpa mengganti semua object jika identitas asset dipertahankan.

---

# 67. Original vs Preview

Original asset adalah data penting.

Preview/thumbnail/render derivative adalah cache.

```text
Original Asset  = persistent
Preview         = disposable
Thumbnail       = disposable
Decoded bitmap  = working memory
```

Clear Cache tidak boleh menghapus original asset.

Satu original dapat digunakan banyak object tanpa clone file.

---

# 68. Asset Loading

Asset large menggunakan:

- thumbnail-first;
- preview-sized decode;
- viewport-first;
- lazy-load;
- streaming/chunking untuk audio/video;
- release saat tidak dipakai.

Image 8000×6000 tidak didecode penuh jika hanya ditampilkan 400×300 kecuali operasi memang membutuhkan full-resolution.

Decoded working copies tidak diperlakukan sebagai project data.

---

# 69. Unused/Missing/Duplicate Asset

Reference count/index dipakai untuk mendeteksi:

- UNUSED_ASSET;
- MISSING_ASSET;
- BROKEN_ASSET_REFERENCE;
- DUPLICATE_CANDIDATE.

Unused asset tidak dihapus otomatis. Pengguna dapat membersihkannya manual.

Duplicate detection menggunakan content hash, bukan nama file saja.

Missing asset dapat direlink sambil mempertahankan Stable Asset ID bila file yang benar ditemukan.

---

# 70. Cache Manager

Semua tool menggunakan **Central Cache Manager**.

Cache mempunyai:

- global budget;
- per-category budget;
- disk budget;
- memory budget;
- priority class.

Priority:

```text
HOT
WARM
COLD
TEMP
```

Eviction dilakukan dari data paling disposable.

Project data, original asset, dan required recovery data tidak pernah masuk eviction cache.

Cache key revision-aware agar preview lama tidak digunakan setelah resource berubah.

---

# 71. Manual Cache Cleanup

Pengguna dapat melihat penggunaan cache per kategori dan menghapus:

- thumbnail;
- preview;
- render temporary;
- parser/index cache;
- disposable temporary data.

Dapat disediakan:

```text
Hapus Cache
Hapus per kategori
```

Cache dapat dibangun ulang.

---

# 72. Recovery

Recovery melindungi **data yang sudah di-Save**.

Recovery tidak mengubah keputusan manual-save.

Jika crash terjadi sebelum Save, working changes boleh hilang.

Jika crash terjadi ketika transaction Save sedang berjalan, sistem menggunakan journal/last-valid revision untuk kembali ke kondisi valid.

---

# 73. Incremental Snapshot & Previous Valid

Snapshot tidak dibuat sebagai clone penuh project setiap perubahan.

Gunakan incremental snapshot/versioned resource protection.

Model:

```text
Current Valid
Previous Valid
Optional Checkpoint
```

Jumlah recovery dibatasi agar storage tidak membengkak.

---

# 74. Recovery Storage List

Recovery Manager mempunyai daftar yang dapat dikelola manual.

Informasi minimal:

- nama;
- tanggal;
- ukuran;
- jenis;
- project;
- status.

Status:

```text
REQUIRED / Dilindungi
DELETABLE / Bisa Dihapus
IN_USE / Sedang Dipakai
```

Pengguna dapat:

- hapus satu item;
- pilih banyak item;
- urutkan berdasarkan ukuran/tanggal;
- melihat ruang yang akan dibebaskan;
- memakai **Hapus Semua yang Aman**.

Current-valid atau recovery yang sedang wajib tidak boleh dihapus.

---

# 75. Backup

Backup berbeda dengan Recovery.

Recovery adalah perlindungan teknis.

Backup adalah salinan project yang dibuat atas permintaan pengguna.

Backup tidak dibuat tanpa batas secara otomatis.

---

# 76. SAF & User-Owned Storage

Pada Android 11, akses folder user-visible memakai Storage Access Framework.

Alur:

```text
pilih/buat folder sekali
↓
persist URI permission
↓
gunakan kembali
```

Jangan meminta izin setiap membuka ToolBox bila izin masih valid.

---

# 77. Access-Loss & Re-linking

Bedakan:

```text
PROJECT_OK
ACCESS_LOST
FOLDER_MISSING
RESOURCE_MISSING
PROJECT_CORRUPT
```

Kehilangan akses bukan berarti project rusak.

Jika akses SAF hilang:

```text
Pilih Kembali Folder
↓
verify projectId / manifest identity
↓
update URI
```

Jika folder berpindah, data tidak perlu disalin jika dapat direlink.

Metadata pemulihan kecil boleh disimpan di private storage:

- projectId;
- projectName;
- lastKnownUri;
- lastKnownRevision;
- manifest fingerprint.

ToolBox tidak melakukan scan seluruh storage secara liar.

---

# 78. Security Boundary Project Store

File di folder user-visible tidak langsung dipercaya.

Setiap resource yang masuk engine melewati validation boundary:

- path normalization;
- schema validation;
- Stable ID validation;
- reference validation;
- capability validation;
- type/MIME/content validation;
- size/budget validation;
- compatibility validation.

Project declarative tidak boleh memanggil arbitrary shell command, memuat native library sembarang, atau mengeksekusi arbitrary downloaded code.

---

# 79. Secret Separation

Visible Project Store tidak menyimpan:

- GitHub token;
- signing private key;
- keystore password;
- API secret;
- production credentials.

Project hanya menyimpan logical requirement/reference seperti:

```text
requiresSecret = MAP_API_KEY
```

Secret sebenarnya berada di secure environment/private storage/build secret store sesuai konteksnya.

---

# 80. Import Security

Import selalu melalui staging.

Validasi meliputi:

- path traversal;
- canonical path;
- maximum entry count;
- maximum uncompressed size;
- recursion/nesting limit;
- decompression budget;
- schema;
- IDs;
- contract compatibility;
- content type;
- asset integrity;
- package hash/signature bila relevan.

Package tidak boleh langsung ditulis ke project aktif sebelum valid.

---

# 81. Import vs Merge

Dibedakan:

## Import Project Baru

Project tetap satu kesatuan dan ID internal dapat dipertahankan.

## Merge ke Project Lama

Conflict ID diremap otomatis dan reference internal ikut diperbarui.

Import/Merge tidak dianggap operasi yang sama.

---

# 82. Export Contract

Export menghasilkan portable project package yang konsisten pada satu revision.

Ikut export:

- project definition;
- screens;
- logic;
- data definitions;
- bindings;
- styles;
- localization;
- required assets;
- dependency/version metadata.

Tidak ikut:

- cache;
- transient undo;
- temporary preview;
- internal recovery journal;
- secret;
- signing key;
- runtime log.

Packaging menggunakan streaming, bukan memuat seluruh project ke RAM.

---

# 83. Permission Contract

Permission diturunkan dari capability yang dipakai.

```text
Capability
↓
Permission Contract
↓
Minimal Permission Set
```

Permission dibedakan:

- install-time;
- runtime;
- special access;
- optional.

Action tidak boleh menganggap runtime permission pasti granted.

Failure path seperti permission denied harus dapat direpresentasikan.

Generator menerjemahkan requirement deklaratif ke implementasi Android yang sesuai target versi.

---

# 84. App & Screen Lifecycle

Lifecycle event deklaratif dapat mencakup:

```text
APP_START
APP_FOREGROUND
APP_BACKGROUND
SCREEN_ENTER
SCREEN_VISIBLE
SCREEN_LEAVE
SCREEN_RETURN
```

Tidak bergantung pada “APP_CLOSE” sebagai tempat menyimpan data penting karena proses Android dapat mati tanpa callback penutup yang dapat diandalkan.

Lifecycle action dapat mempunyai policy seperti:

```text
EVERY_ENTER
FIRST_ENTER
WHEN_DATA_STALE
```

Edit ON/OFF bukan lifecycle aplikasi.

---

# 85. Background Task Contract

Pekerjaan yang harus tetap hidup dipisahkan dari screen.

```text
Screen
↓
request capability
↓
Background Task
↓
Screen boleh RELEASE
```

Task mempunyai Stable ID dan state:

```text
QUEUED
RUNNING
PAUSED
SUCCESS
FAILED
CANCELLED
```

Contract mencakup:

- input;
- progress;
- result;
- retry policy;
- timeout;
- cancellation;
- constraint;
- execution class.

Retry dibatasi dan tidak berjalan selamanya.

Setelah selesai, runtime resource dilepas.

Generator memilih mekanisme platform yang sesuai. Project tidak memerintah “pertahankan thread selamanya”.

---

# 86. Safety Boundary Live Preview

Saat Edit OFF di lingkungan desain, internal UI behavior boleh live langsung.

Action dengan side effect nyata seperti:

- delete data produksi;
- pembayaran;
- upload produksi;
- external destructive intent;
- operasi credential sensitif;

masuk Safety Gate/simulation boundary.

Tujuannya menjaga Live UI tetap realistis tanpa menjadikan proses desain sebagai pemicu operasi berbahaya yang tidak disengaja.

Built application dapat menjalankan action asli sesuai logic dan permission-nya.

---

# 87. Preview Data Sandbox

UI/Logic dapat menggunakan **Preview Data Sandbox + Mock Data Contract**.

Dukungan:

- sample data;
- loading state;
- error state;
- empty state;
- list preview;
- simulated action result.

Preview data terpisah dari data produksi/runtime asli dan tidak otomatis ikut menjadi data produksi.

---

# 88. Editor Context State

Konteks editor disimpan ringan:

- screenId;
- selectedObjectId;
- activeTool;
- zoom;
- pan/scroll;
- shell position;
- panel state;
- editor mode.

Tidak mempertahankan full View hierarchy untuk sekadar mengingat posisi kerja.

---

# 89. Editor Metadata vs Runtime Data

Editor-only metadata dipisahkan dari data aplikasi.

Contoh editor-only:

- selection rectangle;
- guide;
- grid;
- zoom;
- viewport;
- diagnostic overlay;
- shell layout;
- temporary preview state.

Metadata ini tidak masuk runtime APK kecuali property tersebut memang bagian desain aplikasi.

---

# 90. Copy/Paste Clipboard

Clipboard memakai **Contract-Aware Clipboard + Automatic ID Remapping**.

Saat paste:

- object baru memperoleh Stable ID baru;
- dependency minimum ikut diproses;
- reference yang masih valid dipertahankan;
- conflict ID diremap;
- reference yang tidak tersedia ditandai broken;
- tidak menghubungkan ke object lain hanya karena nama mirip.

---

# 91. Diagnostics

Semua tool menggunakan **Unified Diagnostic Contract**.

Field konseptual:

- diagnosticId;
- severity;
- code;
- source tool;
- resourceId;
- location/path;
- message;
- suggested fix;
- related diagnostics.

Severity:

```text
INFO
WARNING
ERROR
BLOCKING
```

Diagnostic harus menunjuk Stable ID/resource yang tepat.

---

# 92. Detect → Suggest → Fix

Diagnostic mengikuti pemisahan:

```text
DETECT
↓
SUGGEST
↓
FIX
```

Auto-fix hanya untuk kasus yang:

- deterministik;
- contract-backed;
- dapat divalidasi;
- reversible atau aman;
- tidak mengubah maksud pengguna secara spekulatif.

Perbaikan yang ambigu tidak dilakukan otomatis.

---

# 93. Incremental Validation

Perubahan satu resource tidak memicu full-project validation setiap detik.

Gunakan dependency graph:

```text
resource berubah
↓
resource terkait
↓
validator lokal
```

Full validation tetap dilakukan pada gate penting seperti build/package release.

---

# 94. Build Contract Validator

Sebelum project dikirim ke GitHub, ToolBox menjalankan **Build Contract Validator** lokal.

Minimal memeriksa:

- validitas semua screen;
- binding wajib;
- mandatory broken reference;
- asset yang digunakan;
- permission requirement;
- logic/action structure;
- navigation;
- schema;
- version compatibility;
- component/action implementation availability;
- build configuration;
- package identity;
- target Android/ABI;
- canonical model generation viability.

Status:

```text
PASS
WARNING
FAIL
BLOCKING
```

Hanya jika semua syarat wajib lolos:

```text
READY TO BUILD
```

GitHub tidak digunakan untuk menemukan kesalahan dasar project yang dapat ditemukan lokal.

---

# 95. Canonical Build Model / IR

Project Store adalah **editing language**.

GitHub tidak perlu memahami internals setiap editor/tool.

Alur:

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

Canonical Build Model:

- generated;
- versioned;
- bukan source of truth editing;
- tidak diedit manual oleh pengguna sebagai sumber utama;
- dapat dibangun ulang dari Project Store;
- hanya berisi informasi yang diperlukan generator.

Composition dapat incremental/streaming per resource.

---

# 96. Build Package

Setelah READY TO BUILD, dibuat build package immutable yang terikat pada revision tertentu.

Metadata minimal:

- buildId;
- projectId;
- projectRevision;
- schemaVersion;
- buildModelVersion;
- target Android;
- ABI;
- packageName;
- dependency lock/provenance metadata;
- content hash.

Satu Build ID = satu isi pasti.

Jika isi berubah, Build ID lama tidak digunakan ulang.

---

# 97. Build Handoff

GitHub menerima hanya material yang diperlukan:

- Canonical Build Model;
- required assets;
- generator inputs;
- build metadata.

Tidak mengirim:

- cache;
- undo history;
- preview sementara;
- recovery yang tidak berkaitan;
- secret.

GitHub memverifikasi integrity, version, target, dependency, dan resource sebelum generator/build berjalan.

---

# 98. Signing

Signing secret tidak berada di Project Store.

Project hanya menyatakan logical signing profile/expected identity.

APK hasil build diverifikasi terhadap expected signer fingerprint.

Jika fingerprint berbeda:

```text
SIGNING_IDENTITY_MISMATCH
```

Artifact tidak dianggap update valid.

---

# 99. Build Artifact Traceability

Hasil build harus dapat dilacak ke source revision.

Record konseptual:

- Build ID;
- Project Revision;
- build run/commit identity;
- APK name;
- APK size;
- APK SHA-256;
- signing fingerprint;
- min/target SDK;
- architecture;
- validator result;
- build result;
- test result.

Prinsip:

```text
yang divalidasi
=
yang dikirim
=
yang dibuild
=
artifact yang diterima
```

---

# 100. Tool / Engine Extension Contract

Engine masuk melalui Tool Contract.

Contract minimal menjelaskan:

- toolId;
- toolVersion;
- contractVersion;
- compatibility;
- components;
- actions;
- events;
- capabilities;
- data types;
- permission needs;
- entry point;
- lifecycle requirements.

ToolBox Host menemukan engine melalui registry, bukan direct dependency.

---

# 101. No Direct Inter-Tool Dependency

Dihindari:

```text
UI Editor → langsung Browser Engine → langsung Data Engine
```

Digunakan:

```text
UI Editor
↓
Registry / Contract
↑
Browser Engine
```

Tool lain tidak perlu hidup hanya karena metadata capability-nya sedang ditampilkan.

---

# 102. Mandatory Lifecycle Compliance

Engine baru wajib dapat:

- load lazily;
- release resource;
- berhenti ketika tidak diperlukan;
- tidak meninggalkan listener/thread/context reference;
- mengikuti memory/cache budget;
- memberikan diagnostic;
- gagal secara terisolasi.

Engine yang hanya “bisa jalan” tetapi tidak bisa dilepas dengan bersih belum dianggap sehat.

---

# 103. Failure Isolation

Jika satu engine gagal:

```text
Engine X = FAILED / UNAVAILABLE
```

ToolBox Host, project, dan engine lain tetap berjalan sejauh dependency memungkinkan.

Bagian yang bergantung pada engine gagal ditandai secara eksplisit.

---

# 104. Executable Runtime Boundary

Paket/project eksternal pada jalur normal bersifat **declarative**.

Paket eksternal dapat membawa:

- UI definitions;
- assets;
- templates;
- workflows;
- rules;
- declarative migrations;
- repair definitions;
- metadata.

Paket eksternal **tidak** boleh bebas membawa arbitrary DEX/JAR/native code untuk dieksekusi oleh host.

Jika kemampuan membutuhkan:

- primitive executable baru;
- Android component baru;
- permission baru yang membutuhkan perubahan base;
- native engine baru;
- perubahan trust/recovery root;

maka kemampuan tersebut masuk jalur update APK/build yang dipercaya.

Process isolation dapat digunakan untuk workload executable berat yang memang merupakan bagian resmi APK, bukan sebagai pembenar menjalankan arbitrary downloaded code.

---

# 105. Managed App Protocol

Interaksi ToolBox dengan aplikasi lain menggunakan **Managed App Protocol** yang opt-in.

Prinsip:

- target app harus ToolBox-aware;
- user/target authorization;
- capability session terbatas;
- target identity validation;
- timeout/cancellation;
- replay protection untuk command sensitif;
- tidak ada vendor/account/device lock sebagai prinsip dasar;
- unsupported app tidak dipaksa menjadi managed target.

ToolBox bukan pemodifikasi universal terhadap app yang tidak berpartisipasi.

---

# 106. Declarative Update Package

Update/evolution package memakai manifest yang mendeskripsikan:

- packageId;
- packageVersion;
- packageType;
- target project/app;
- engine/contract compatibility;
- dependencies;
- capabilities;
- file hashes;
- package integrity/signature metadata;
- migration/repair intent.

Trust status terikat pada isi package yang tepat. Perubahan isi membatalkan trust terhadap hash/signature lama.

---

# 107. Update Apply Pipeline

Apply package mengikuti:

```text
Manual Select
↓
Staging
↓
Validate Package
↓
Known-Good Checkpoint
↓
Prepare / Journal
↓
Dry Run / Self-Test
↓
Preview
↓
Explicit Apply
↓
Activate
↓
Health Check
↓
Commit atau Rollback
```

Tidak ada auto-commit hanya karena package terdeteksi.

Failure setelah activation memicu rollback/known-good recovery.

---

# 108. Freeze Engine

Freeze Engine digunakan untuk menjaga baseline aplikasi/data yang dikelola.

Model konseptual:

```text
LIVE / BASELINE
FROZEN_BASE
WORKING / OVERLAY
RECOVERY A/B
```

Saat Freeze aktif, perubahan eksperimen diarahkan ke overlay/working layer, bukan merusak baseline langsung.

Pengguna dapat:

- membuat checkpoint;
- mencoba perubahan;
- membuang overlay;
- recover ke baseline;
- menjadikan kondisi valid sebagai baseline baru melalui explicit commit.

Commit baseline selalu all-or-nothing.

---

# 109. Freeze State Machine

State konseptual:

```text
NORMAL
CREATING_SNAPSHOT
FROZEN
COMMITTING
RESTORING
THAWING
VERIFYING
RECOVERY_REQUIRED
RECOVERY_RUNNING
FAILED_SAFE
```

Operasi database/file penting harus disinkronkan sebelum snapshot/restore.

Startup bootstrap membaca state journal dan meneruskan atau memulihkan operasi yang terputus.

Temporary/incomplete state dibersihkan tanpa merusak last-known-valid baseline.

---

# 110. Safe Mode / Safe UI

Recovery/Safe UI merupakan kemampuan dasar yang harus tetap dapat berjalan ketika tool/engine tertentu bermasalah.

Safe mode dapat menyediakan:

- status baseline;
- status overlay;
- integrity verification;
- discard overlay;
- restore known-good baseline;
- quarantine invalid package/resource;
- diagnostic/report export;
- read-only inspection.

Safe UI tidak bergantung pada engine yang sedang rusak.

---

# 111. Health Check

Setelah repair/update/restore penting:

```text
Apply
↓
Health Check
↓
PASS → commit/continue
FAIL → rollback/safe mode
```

Health check dapat mencakup:

- schema readable;
- required files valid;
- capability load;
- key navigation path;
- database open;
- startup path;
- no critical diagnostic.

---

# 112. Memory Architecture

Target device mempunyai RAM 6 GB, tetapi ToolBox tidak menganggap seluruh 6 GB tersedia untuk satu process.

Budget runtime berdasarkan:

- Android memory class;
- current memory pressure;
- active tool;
- active screen;
- decoded asset cost;
- renderer cost;
- observed PSS/peak;
- leak trend.

Prinsip:

```text
1 APK
+ 1 lightweight host
+ 1 heavy tool aktif
+ 1 screen aktif
+ bounded working set
```

Angka PSS tertentu hanya target eksperimen awal, bukan jaminan universal.

---

# 113. Per-Screen Memory Budget

Satu screen besar pun tidak boleh bebas menggunakan RAM tanpa batas.

Gunakan:

- viewport-first rendering;
- off-screen release;
- sampled image decode;
- bounded preloading;
- adaptive preview quality;
- list virtualization;
- per-resource working budget.

Jika pressure meningkat:

```text
kurangi preloading
↓
buang disposable cache
↓
turunkan preview quality
↓
release off-screen resources
```

Jika masih berat, tampilkan diagnostic sumber biaya terbesar.

Tidak menggunakan batas object-count kaku sebagai satu-satunya metric.

---

# 114. Overdraw & Rendering Cost

Layer transparan, shadow, blur, bitmap besar, video, dan animasi simultan dapat menekan GPU walaupun RAM masih cukup.

Screen diagnostic perlu mampu menandai excessive overdraw/render cost.

Per-Screen Budget mempertimbangkan bukan hanya byte memory tetapi juga visible node dan render complexity.

---

# 115. Memory Leak Discipline

Hal yang harus dicegah:

- listener tertinggal;
- coroutine/job tidak berhenti;
- Context/View lama direferensikan;
- bitmap tidak dilepas;
- unbounded cache;
- timer/polling lama;
- engine tetap hidup setelah tool release.

Soak test switching tool/screen digunakan untuk mendeteksi memory staircase.

---

# 116. Test & Benchmark Contract

Fitur tidak dianggap matang hanya karena build berhasil.

Kelompok test:

- functional;
- lifecycle;
- memory;
- storage/transaction;
- crash/recovery;
- compatibility;
- scale;
- process death;
- build contract;
- import/security;
- rendering/performance.

Hasil test harus measurable dan dapat dicatat.

---

# 117. Soak Test

Contoh:

```text
UI → Logic → Data → UI
× 50 / × 100
```

Periksa:

- start PSS;
- end PSS;
- peak PSS;
- thread count;
- resource release;
- crash;
- latency trend.

RAM yang terus naik setiap cycle dianggap indikasi failure/leak meskipun belum crash.

---

# 118. Crash/Transaction Test

Test harus sengaja memutus proses pada titik:

- staging;
- write;
- validation;
- pre-commit;
- post-commit marker;
- migration;
- recovery.

Setelah restart hanya dua hasil yang dapat diterima:

```text
revision lama valid
atau
revision baru valid
```

Tidak boleh mixed revision.

---

# 119. Scale Classes

Test project minimal dibagi:

```text
SMALL
MEDIUM
LARGE
STRESS
```

Dengan variasi:

- screen count;
- object count;
- binding count;
- asset count;
- asset size;
- logic graph size;
- component count;
- dependency graph size.

Tujuan bukan sekadar mencari angka maksimum, tetapi mengetahui kapan adaptive behavior harus aktif.

---

# 120. External File Integrity

Karena project terlihat pengguna, ToolBox memeriksa perubahan external secara incremental.

Manifest/hash digunakan untuk mendeteksi:

- file berubah;
- file hilang;
- corruption;
- revision mismatch.

Hash di folder yang sama adalah integrity aid, bukan bukti authenticity terhadap attacker yang dapat mengubah file dan hash sekaligus.

Authenticity package membutuhkan signature/trust root terpisah.

---

# 121. Build-Time Dependency Determinism

Build package membawa dependency/toolchain lock/provenance yang diperlukan agar input yang sama tidak diam-diam resolve dependency berbeda.

Perubahan dependency penting menghasilkan Build ID/revision build baru.

Build output harus dapat ditelusuri kembali ke exact inputs.

---

# 122. Audit Agent Integration

Audit agent menggunakan:

- registry inventory;
- dependency graph;
- diagnostics;
- contract IDs;
- manifest integrity;
- build validator;
- implementation presence.

Alur:

```text
Inventory
↓
Detect missing/mismatch
↓
Trace dependency
↓
Auto-fix bila deterministik
↓
Validate
↓
Report remaining issue
```

Agen tidak mengarang koneksi berdasarkan nama.

Agen tidak menyederhanakan dengan menghapus kemampuan dasar hanya agar audit PASS.

---

# 123. Automatic Repair Policy

Untuk masalah yang sudah mempunyai solusi deterministik terbaik, ToolBox/audit dapat melakukan repair otomatis setelah memenuhi seluruh precondition.

Contoh yang cocok:

- rebuild generated index;
- rebuild dependency graph;
- regenerate preview cache;
- remap import ID conflict;
- relink contract berdasarkan exact Stable ID mapping;
- regenerate derived manifest record;
- remove stale disposable cache.

Contoh yang tidak boleh ditebak otomatis:

- memilih target navigation baru ketika beberapa target sama-sama masuk akal;
- mengganti business logic;
- memilih asset berbeda hanya karena nama mirip;
- menghapus data user;
- mengubah side effect penting.

---

# 124. Diagnostic Codes Bersama

Runtime, validator, build composer, dan audit menggunakan keluarga kode masalah yang konsisten.

Contoh:

```text
BROKEN_REFERENCE
MISSING_ASSET
COMPONENT_UNAVAILABLE
CONTRACT_MISMATCH
ACTION_IMPLEMENTATION_MISSING
PERMISSION_CONTRACT_MISSING
LAYOUT_CONSTRAINT_CONFLICT
STALE_WRITE
CAPABILITY_INCOMPATIBLE
SIGNING_IDENTITY_MISMATCH
```

Satu masalah tidak diberi istilah berbeda di setiap subsystem.

---

# 125. Prioritas Source of Truth

Sumber kebenaran dibedakan jelas:

```text
Project Store
= source of truth editing

Generated Index / Dependency Graph
= derived/rebuildable

Canonical Build Model
= generated build representation

Cache / Preview
= disposable

Runtime View
= materialized working representation
```

Tidak ada subsystem derived yang boleh mengambil alih fungsi source of truth utama.

---

# 126. Invariant Utama

Invariant yang tidak boleh dilanggar oleh desain:

1. **Satu project valid selalu mempunyai revision konsisten.**
2. **Manual Save tidak berubah menjadi autosave tersembunyi.**
3. **Edit Mode tidak membuat clone screen.**
4. **Bubble/Edge/Deck tidak menjadi bagian runtime aplikasi hasil build.**
5. **Stable ID tidak bergantung pada label visual.**
6. **Tool tidak bergantung langsung pada implementasi tool lain.**
7. **Registry memuat metadata, bukan semua runtime engine.**
8. **Project eksternal tidak dapat mengeksekusi arbitrary code di host.**
9. **Cache tidak pernah memiliki kewenangan menghapus source data.**
10. **Recovery hanya menjamin state yang sudah di-commit.**
11. **Broken reference tidak disembunyikan atau dihapus diam-diam.**
12. **GitHub build terikat exact Build ID/revision.**
13. **Secret tidak disimpan di project visible.**
14. **Satu screen berat tetap tunduk pada budget.**
15. **Generated index selalu dapat dibangun ulang.**
16. **Migration permanen selalu transactional.**
17. **Engine gagal tidak menjatuhkan host bila dependency memungkinkan isolasi.**
18. **Permission berasal dari capability yang benar-benar digunakan.**
19. **Background task tidak mempertahankan screen hanya untuk tetap hidup.**
20. **Audit auto-fix tidak boleh menebak maksud pengguna.**

---

# 127. Alur Kerja Project dari Awal sampai APK

```text
Buat / Buka Project
↓
Project Store
↓
UI Editor / Logic / Data / Asset
↓
Working State
↓
Manual Save
↓
Transactional Commit
↓
Project Revision Valid
↓
Incremental Diagnostics
↓
Build Contract Validator
↓
READY TO BUILD
↓
Composer
↓
Canonical Build Model
↓
Immutable Build Package
↓
GitHub Build
↓
Signing
↓
Tests
↓
Artifact + Build Report
```

---

# 128. Alur UI Editor

```text
Screen aktif LIVE
↓
Bubble tersedia di top layer
↓
Edge menyediakan asset/component
↓
Deck default tertutup

Bubble → Edit ON
↓
Screen yang sama menjadi area edit
↓
sentuhan memilih object, bukan menjalankan action
↓
Deck membaca Property Contract
↓
ubah object
↓
Working State + Undo/Redo RAM
↓
Bubble → Save
↓
Transactional Commit
↓
Edit OFF
↓
UI kembali menjalankan action normal
```

Tidak ada cloning dan tidak ada autosave.

---

# 129. Alur Asset ke Object

```text
Edge Panel
↓
Component Registry metadata
↓
drag component/asset
↓
create Stable Instance ID
↓
resolve property/event contract
↓
render di screen aktif
↓
manual save
```

Original asset tetap storage-first dan preview dibuat seperlunya.

---

# 130. Alur Binding

```text
Object Event
↓
Action Registry
↓
Compatibility Filter
↓
Typed Action
↓
Input Mapping
↓
Binding Stable ID
↓
Validator
```

Jika input belum lengkap, binding ditandai incomplete, bukan dibuat seolah valid.

---

# 131. Alur Logic

```text
Event
↓
Action
↓
Condition / Branch
↓
Async Success/Failure
↓
Navigation/Data/Action berikutnya
```

Graph dimaterialisasi lokal dan ditutup/release ketika tidak diperlukan.

---

# 132. Alur Repair / Evolution

```text
Select Managed Target / Package
↓
Target Authorization
↓
Staging
↓
Integrity + Compatibility Validation
↓
Known-Good Protection
↓
Dry Run / Preview
↓
Explicit Apply
↓
Health Check
├─ PASS → Commit
└─ FAIL → Rollback / Safe Mode
```

Tidak ada arbitrary code execution dari package.

---

# 133. Alur Freeze

```text
NORMAL
↓
Create Known-Good Baseline
↓
FROZEN
↓
perubahan masuk Working Overlay
├─ Discard → kembali baseline
├─ Recovery → restore baseline/known-good
└─ Commit → validate → Jadikan Baseline Baru
```

Baseline tidak berubah hanya karena eksperimen berjalan.

---

# 134. Arsitektur RAM Ringkas

```text
Host ringan
+
Shell kecil
+
1 Tool aktif
+
1 Screen/sector aktif
+
Working State terbatas
+
Viewport assets terbatas
```

Yang tidak aktif tetap berada sebagai definisi di storage.

---

# 135. Arsitektur Penyimpanan Ringkas

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

# 136. Batas Antara Rancangan dan Implementasi

Dokumen ini menetapkan **apa yang harus dijamin oleh sistem dan bagaimana komponen besar saling berhubungan**.

Detail implementasi seperti:

- nama class;
- framework UI tertentu;
- library tertentu;
- bentuk final ikon;
- urutan visual final menu Bubble;
- warna akhir tiap control;
- nama package internal;

boleh berubah selama invariant dan contract rancangan ini tetap terpenuhi.

Dengan demikian rancangan tetap stabil tanpa mengunci implementasi pada satu teknik yang belum terbukti terbaik.

---

# 137. Bentuk Teknis ToolBox Saat Rancangan Ini Dipenuhi

ToolBox pada kondisi matang mempunyai karakter berikut:

> Satu aplikasi host ringan yang menjadi rumah bagi tool-tool independen; halaman adalah wujud tool aktif; Bubble, Edge, dan Deck menjadi shell kontrol; UI aplikasi selalu live kecuali Bubble mengaktifkan Edit Mode; editing dilakukan pada screen yang sama tanpa cloning; project berada di storage milik pengguna; Save dilakukan manual dan transactional; semua hubungan memakai Stable ID dan typed contracts; registries hanya memuat metadata; tool/engine hidup hanya ketika diperlukan; asset dimuat secara adaptif; dependency dan diagnostics bekerja incremental; recovery menjaga revision yang sudah tersimpan; project eksternal berada di trust boundary deklaratif; repair/update memakai staging, validation, health check, rollback, dan safe mode; build menggunakan Canonical Build Model yang terikat exact revision dan hanya dikirim ke GitHub setelah Build Contract Validator menyatakan READY TO BUILD.

---

# 138. Kesimpulan Arsitektur

Bentuk keseluruhan ToolBox dapat diringkas:

```text
                           ┌──────────────────────────┐
                           │        ToolBox Host      │
                           └────────────┬─────────────┘
                                        │
                ┌───────────────────────┼───────────────────────┐
                │                       │                       │
        ┌───────▼────────┐      ┌──────▼───────┐      ┌───────▼────────┐
        │   Shell UI     │      │ Tool Registry │      │ Core Services   │
        │ Bubble/Edge/   │      │ + Contracts   │      │ Storage/Diag/   │
        │ Deck           │      │               │      │ Recovery        │
        └───────┬────────┘      └──────┬────────┘      └───────┬────────┘
                │                      │                        │
                └──────────────┬───────┴───────────────┬────────┘
                               │                       │
                       ┌───────▼────────┐      ┌──────▼─────────┐
                       │ Active Tool    │      │ Project Store   │
                       │ + Active Screen│      │ Source of Truth │
                       └───────┬────────┘      └──────┬─────────┘
                               │                       │
                               └───────────┬───────────┘
                                           │
                                  ┌────────▼────────┐
                                  │ Manual Save     │
                                  │ Transaction     │
                                  └────────┬────────┘
                                           │
                                  ┌────────▼────────┐
                                  │ Valid Revision  │
                                  └────────┬────────┘
                                           │
                           ┌───────────────▼───────────────┐
                           │ Build Validator / Composer   │
                           └───────────────┬───────────────┘
                                           │
                                  ┌────────▼────────┐
                                  │ Canonical IR    │
                                  └────────┬────────┘
                                           │
                                  ┌────────▼────────┐
                                  │ GitHub Build    │
                                  └────────┬────────┘
                                           │
                                  ┌────────▼────────┐
                                  │ APK + Report    │
                                  └─────────────────┘
```

Fondasinya adalah:

> **ToolBox tidak mencoba membuat seluruh sistem selalu hidup. ToolBox menyimpan seluruh kemampuan sebagai definisi, contract, registry, dan project data; kemudian hanya mematerialisasikan bagian yang benar-benar dibutuhkan pada saat itu.**

Itulah dasar agar ToolBox tetap dapat berkembang menjadi rumah bagi banyak engine tanpa menjadikan HP terbebani oleh seluruh kemampuan secara bersamaan.
