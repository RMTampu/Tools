# ASSET_SAFE_100_RULES.md

## 1. Status Dokumen

> **Scope Public wajib:** dokumen ini hanya berlaku untuk asset/resource milik component, prototype, mock, simulator, fixture, dan package yang memang Public di `RMTampu/Tools`.
>
> Dalam dokumen ini, istilah final artifact/package dan `ASSET_SAFE_100` adalah metode dan formula untuk scope Public. Claim yang boleh dikeluarkan oleh repository ini adalah `PUBLIC_ASSET_SAFE_100`, lalu `PACKAGE_VALIDATION` dan `READY_PRIVATE`; tidak pernah final build, signing, Firebase, atau release Private.

Dokumen ini adalah aturan operasional wajib untuk seluruh pekerjaan yang menyentuh asset/resource pada repository `RMTampu/Tools`.

Target dokumen ini adalah `ASSET_SAFE_100`: seluruh asset yang termasuk ruang lingkup harus terbukti benar terhadap spesifikasinya, berhasil masuk ke hasil build, dapat di-resolve dan digunakan pada seluruh konfigurasi/state yang didukung, serta tidak meninggalkan kelas kegagalan asset yang belum diperiksa.

Dokumen ini hanya menilai kegagalan yang berasal dari asset/resource.

Kegagalan alat seperti emulator, CI runner, compiler, build tool, toolchain, validator, filesystem eksternal, atau alat pengujian tidak boleh diklasifikasikan sebagai kegagalan asset kecuali bukti menunjukkan asset itu sendiri adalah penyebabnya.

---

## 2. Target Platform

Target utama:

- Android 11
- API 30
- ABI utama `arm64-v8a`
- Artifact Android Public (component/prototype/mock/fixture), bukan artifact final Private

Aturan asset harus mempertahankan kompatibilitas terhadap target tersebut.

---

## 3. Status Resmi

Gunakan status berikut:

| Status | Arti |
|---|---|
| `ASSET_SAFE_100` | Semua proof wajib lengkap dan seluruh invariant asset terpenuhi |
| `FAIL_ASSET` | Kesalahan asset terbukti |
| `FAIL_APPLICATION` | Kegagalan berasal dari kode/logic aplikasi, bukan dari asset |
| `INDETERMINATE_TOOL` | Alat gagal sehingga kondisi asset belum dapat dibuktikan |
| `INCOMPLETE_PROOF` | Ada proof asset wajib yang belum tersedia |

Aturan keras:

- `INDETERMINATE_TOOL` bukan `FAIL_ASSET`.
- `INDETERMINATE_TOOL` juga bukan `ASSET_SAFE_100`.
- `INCOMPLETE_PROOF` tidak boleh diubah menjadi PASS.
- Tidak boleh menyatakan `ASSET_SAFE_100` hanya karena build sukses.

---

# 4. Prinsip Closed Asset Universe

## RULE ASSET-001 — Seluruh Asset Harus Diketahui

Agen WAJIB membangun `EXPECTED_ASSET_SET` yang mencakup seluruh asset/resource yang dapat menjadi bagian dari aplikasi.

Cakupan minimal:

- `res/`
- `assets/`
- `res/raw/`
- drawable
- mipmap
- layout
- XML
- values
- strings
- plurals
- styles
- themes
- fonts
- JSON/config
- database/template
- media
- generated resources
- dependency resources
- variant resources
- localized resources
- dynamic resources
- native payload yang diperlakukan sebagai asset aplikasi
- file lain yang ditandai wajib oleh project

Wajib:

```text
Expected asset tetapi tidak ditemukan -> FAIL_ASSET
Required asset tanpa contract         -> FAIL_ASSET
Unknown controlled asset              -> FAIL_ASSET
```

Tidak boleh ada required asset dengan status tidak diketahui.

---

# 5. Asset Contract

## RULE ASSET-002 — Setiap Required Asset Wajib Memiliki Contract

Setiap required asset wajib mempunyai identitas dan kontrak eksplisit.

Contract minimal:

```text
Asset-ID
source
SHA-256
type
required/optional
expected resource name
expected final owner
qualifier
supported configurations
consumer
loader
schema/format
expected semantic result
allowed dimensions
allowed memory/resource cost
expected UI states
fallback rule
version compatibility
```

Contract harus menentukan bukan hanya apakah file valid, tetapi apa yang dianggap benar untuk asset tersebut.

Contoh: bitmap yang valid tetapi merupakan gambar yang salah tetap harus dapat dinyatakan `FAIL_ASSET` melalui semantic/visual oracle.

---

# 6. Canonical Path Verification

## RULE ASSET-003 — Path Tidak Boleh Ambigu

Setiap path asset wajib melalui pemeriksaan:

```text
normalize
-> canonicalize
-> case-collision check
-> Unicode-normalization check
-> duplicate check
```

Harus mendeteksi minimal:

- perbedaan case yang bertabrakan;
- path ekuivalen dengan bentuk berbeda;
- Unicode filename yang terlihat sama tetapi memiliki representasi berbeda;
- duplicate archive/package path;
- path traversal atau bentuk path yang tidak canonical.

Wajib:

```text
Path ambiguity = 0
```

Jika tidak:

`FAIL_ASSET`.

---

# 7. Type-Specific Validation

## RULE ASSET-004 — Dilarang Menggunakan Satu Validator Generik

Setiap tipe asset wajib diverifikasi menggunakan aturan yang sesuai dengan tipenya.

Minimum:

| Tipe | Validasi wajib |
|---|---|
| XML resource | parse + reference + semantic structure |
| Layout | parse + inflate + measure + layout + draw |
| Drawable XML | inflate + render |
| Bitmap | full decode + dimensions + density + memory |
| NinePatch | decode + stretch metadata |
| Font | parse + load + style/weight + glyph coverage |
| String | syntax + formatting signature + localization contract |
| Plural | quantity coverage + formatting signature |
| Color/selector | resolve + state coverage |
| Theme/style | full attribute/parent resolution |
| JSON/config | syntax + schema + semantic constraints |
| Database | open + integrity + schema + required invariants |
| Media | container + codec + full stream decode |
| raw/assets | digest + exact read melalui loader nyata |
| HTML/CSS/local web | parse + dependency closure + load/render |
| Binary lain | parser/validator khusus format |

`file exists` tidak pernah cukup untuk menyatakan asset aman.

---

# 8. XML Runtime Meaning

## RULE ASSET-005 — XML Wajib Diuji Sesuai Cara Android Menggunakannya

XML yang well-formed belum cukup.

Untuk setiap XML yang relevan:

```text
parse
-> resolve references
-> resolve attributes
-> inflate menggunakan API sebenarnya
-> exercise hasilnya
```

Contoh jalur:

- layout -> `LayoutInflater`
- menu -> `MenuInflater`
- drawable XML -> drawable inflation
- color selector -> `ColorStateList`
- generic XML -> `XmlResourceParser`

Jika XML hanya dapat diparse tetapi gagal ketika di-inflate atau digunakan:

`FAIL_ASSET`.

---

# 9. Layout Exhaustive Inflation

## RULE ASSET-006 — Semua Layout Variant Wajib Di-exercise

Jangan hanya menguji layout utama.

Semua layout yang dapat dipilih, termasuk variant qualifier, wajib mempunyai runtime witness.

Untuk setiap layout:

```text
inflate
-> measure
-> layout
-> draw
```

Wajib menguji layout pada theme dan konfigurasi yang memang dapat digunakan oleh layout tersebut.

Jika ada required layout variant yang tidak pernah di-exercise:

`INCOMPLETE_PROOF`.

---

# 10. Theme dan Style Closure

## RULE ASSET-007 — Seluruh Theme/Style Reference Harus Tertutup

Bangun graph untuk:

- `?attr/...`
- `@style/...`
- `@color/...`
- `@drawable/...`
- parent style
- style inheritance
- theme attribute dependencies

Wajib:

```text
unresolved attr = 0
missing parent  = 0
missing value   = 0
invalid cycle   = 0
```

Setiap layout wajib di-inflate dengan setiap theme yang secara nyata dapat diterapkan kepadanya.

---

# 11. Resource Dependency Graph Closure

## RULE ASSET-008 — Semua Referensi Harus Masuk Resource Graph

Bangun `RESOURCE_GRAPH` yang mencakup minimal:

```text
XML -> resource
style -> parent
style/theme -> attr
layout -> include
layout -> drawable/string/style
menu -> icon/string
selector -> child drawable/color
string-array -> string/value
resource -> fallback
resource -> qualifier variant
dynamic-name -> Asset-ID
```

Wajib:

```text
Required nodes      = N
Resolved nodes      = N
Dangling edges      = 0
Unknown references  = 0
Broken references   = 0
```

Setiap dangling/broken required reference:

`FAIL_ASSET`.

---

# 12. Dynamic Resource Closure

## RULE ASSET-009 — Dynamic Lookup Harus Memiliki Domain Tertutup

Semua pola seperti:

- `getIdentifier(...)`
- `AssetManager.open(fileName)` dengan nama dinamis
- resource name dari JSON/config/database
- theme/template berdasarkan string
- plugin/module runtime asset name

wajib dipetakan ke finite registry.

Struktur:

```text
DYNAMIC_LOOKUP
-> FINITE REGISTRY
-> KNOWN Asset-ID
```

Semua nilai yang mungkin harus diketahui.

Jika aplikasi dapat menghasilkan nama required asset arbitrer tanpa domain tertutup:

```text
ASSET_SAFE_100 = tidak dapat dibuktikan
```

Status:

`INCOMPLETE_PROOF` sampai domain ditutup.

---

# 13. Merge dan Overlay Correctness

## RULE ASSET-010 — Resource yang Menang Merge Harus Resource yang Diharapkan

Untuk setiap resource yang mempunyai lebih dari satu kandidat, catat:

```text
Resource-ID
candidate A
candidate B
candidate C
EXPECTED WINNER
ACTUAL WINNER
```

Wajib:

```text
EXPECTED WINNER == ACTUAL WINNER
```

Unexpected override:

`FAIL_ASSET`.

Jangan menganggap resource benar hanya karena nama resource tersedia pada hasil akhir.

---

# 14. Resource Shrinking Closure

## RULE ASSET-011 — Required Resource Tidak Boleh Hilang Karena Shrinking

Untuk proof maksimum, mode validasi asset lebih aman menggunakan resource shrinking OFF.

Jika shrinking digunakan, wajib membuat:

```text
BEFORE_SHRINK_SET
AFTER_SHRINK_SET
```

Aturan:

```text
Required removed = 0
Unknown removed  = 0
```

Dynamic resources wajib menghasilkan keep mapping/rules otomatis berdasarkan dynamic registry.

Required resource yang hilang:

`FAIL_ASSET`.

---

# 15. Configuration Closure

## RULE ASSET-012 — Seluruh Keputusan Resource Resolver Harus Terbukti

Agen tidak boleh hanya menguji beberapa konfigurasi secara acak.

Kumpulkan seluruh qualifier yang benar-benar digunakan project, termasuk bila relevan:

- locale
- layout direction
- orientation
- screen dimensions
- density
- night/day
- UI mode
- smallest width
- screen size
- qualifier Android lain yang digunakan project

Bangun:

```text
ALL QUALIFIERS IN PROJECT
-> RESOURCE RESOLUTION MODEL
-> EQUIVALENCE CLASSES
```

Setiap equivalence class wajib mempunyai `Configuration Witness` yang menyebabkan Android memilih resource yang diharapkan.

Wajib:

```text
Configuration classes = N
Proven                = N
Unknown               = 0
```

---

# 16. Default/Fallback Closure

## RULE ASSET-013 — Tidak Boleh Ada Lubang Fallback

Untuk setiap required resource:

```text
FOR ALL supported configurations:
resolve(resource) != MISSING
```

Resource alternatif yang membutuhkan default wajib memiliki fallback yang benar.

Wajib:

```text
Missing required default = 0
Broken fallback          = 0
```

Jika tidak:

`FAIL_ASSET`.

---

# 17. Stateful Resource Closure

## RULE ASSET-014 — Semua State yang Didefinisikan Wajib Diuji

Untuk selector/stateful resource, enumerasi seluruh state yang didefinisikan, misalnya:

- normal/default
- pressed
- focused
- selected
- checked
- enabled
- disabled
- activated
- window-focused
- level/state lain yang digunakan

Setiap state wajib:

```text
resolve
-> inflate/decode
-> render/use
```

Default state wajib tersedia bila semantik resource memerlukannya.

Missing/broken required state:

`FAIL_ASSET`.

---

# 18. Bitmap Integrity dan Memory Safety

## RULE ASSET-015 — Bitmap Valid Harus Juga Aman untuk Contract Penggunaan

Untuk setiap bitmap:

```text
full decode
-> dimensions
-> pixel format
-> density
-> decoded memory
```

Contract wajib mempunyai batas yang relevan.

Validasi:

```text
decoded-memory <= contract budget
```

Untuk kumpulan asset yang menurut contract memang dimuat bersamaan:

```text
sum(expected simultaneously-loaded assets) <= asset memory budget
```

Bitmap yang secara contract terlalu besar:

`FAIL_ASSET`.

Jika kode aplikasi memuat asset secara berlebihan di luar contract, klasifikasikan sebagai `FAIL_APPLICATION`, bukan `FAIL_ASSET`.

---

# 19. Visual Golden Proof

## RULE ASSET-016 — Asset Visual Penting Wajib Memiliki Oracle

File visual yang valid secara teknis belum membuktikan bahwa tampilannya benar.

Setiap visual asset penting wajib mempunyai salah satu oracle:

- exact golden
- perceptual golden
- structural constraint
- semantic assertion

Untuk kondisi yang relevan lakukan:

```text
render
-> capture/inspect output
-> compare dengan oracle
```

Cakupan sesuai penggunaan asset:

- theme
- state
- locale
- density class
- orientation
- screen configuration relevan

Asset visual yang secara teknis valid tetapi tidak sesuai oracle:

`FAIL_ASSET`.

---

# 20. String Formatting Signature

## RULE ASSET-017 — Semua Translation Harus Menjaga Signature Placeholder

Untuk setiap string berformat, bandingkan seluruh locale terhadap contract:

- jumlah placeholder
- index placeholder
- tipe placeholder
- escaped format semantics bila relevan

Wajib:

```text
Format signature mismatch = 0
```

Contoh mismatch tipe/index antar-locale harus dianggap:

`FAIL_ASSET`.

---

# 21. Plural Closure

## RULE ASSET-018 — Plural Harus Valid untuk Locale yang Didukung

Untuk setiap plural pada setiap supported locale:

```text
locale plural rules
-> required quantity cases
-> resource availability
-> formatting signature
```

Jangan mengasumsikan aturan plural satu bahasa berlaku pada bahasa lain.

Missing/broken required plural case:

`FAIL_ASSET`.

---

# 22. Font Glyph Closure

## RULE ASSET-019 — Font Harus Dapat Menampilkan Charset yang Menjadi Contract

Untuk setiap font:

```text
parse
-> Typeface load
-> weight/style validation
-> glyph coverage
```

Kumpulkan required character set dari:

- seluruh shipped strings
- seluruh supported locales
- declared icon glyphs
- symbols/numbers yang diwajibkan contract

Untuk setiap required codepoint:

```text
font glyph tersedia
OR
approved system fallback tersedia
```

Wajib:

```text
missing required glyph = 0
```

Untuk teks arbitrer eksternal/pengguna, contract harus menetapkan penggunaan system fallback atau charset yang didukung.

---

# 23. Localization Visual Closure

## RULE ASSET-020 — Localization Tidak Boleh Hanya Diverifikasi dari File String

Untuk setiap supported locale yang relevan, verifikasi tampilan hasil localization.

Cakupan minimal sesuai penggunaan:

- default locale
- seluruh supported locale
- RTL bila didukung
- expanded/pseudo-localized content untuk mendeteksi clipping/layout issue
- night/day bila memengaruhi asset visual

Bug visual yang muncul karena asset localization:

`FAIL_ASSET`.

---

# 24. Database Asset Proof

## RULE ASSET-021 — Database Prepackaged Wajib Diuji Sampai Schema dan Data Invariant

Database asset tidak boleh hanya diperiksa dengan hash.

Untuk setiap database prepackaged:

```text
open database
-> integrity check
-> foreign-key check bila digunakan
-> schema match
-> expected version
-> required table/index/view presence
-> required rows/invariants
```

Jika Room digunakan, schema asset wajib cocok dengan schema yang diharapkan oleh aplikasi.

Database yang dapat dibuka tetapi schema/data contract salah:

`FAIL_ASSET`.

---

# 25. JSON/Config/Template Semantic Proof

## RULE ASSET-022 — Parse Success Tidak Cukup

Untuk JSON/config/template:

```text
syntax
-> schema
-> required fields
-> types
-> ranges
-> cross-field constraints
-> references
-> version
-> consumer compatibility
```

Contoh nilai negatif pada field yang secara contract harus positif adalah `FAIL_ASSET` walaupun JSON valid.

---

# 26. Raw dan Asset Loader Proof

## RULE ASSET-023 — Validasi Harus Menggunakan Loader yang Sebenarnya

Untuk asset yang digunakan melalui Android API, lakukan verifikasi melalui jalur yang sama dengan aplikasi.

Contoh:

```text
assets/foo -> AssetManager.open()
raw/foo    -> openRawResource()
XML        -> getXml()/inflater yang sesuai
```

Membaca file langsung dari ZIP tidak cukup untuk runtime proof.

---

# 27. Media Full-Decode Proof

## RULE ASSET-024 — Header Valid Tidak Cukup untuk Media

Untuk audio/video/media:

```text
metadata parse
-> container parse
-> codec compatibility terhadap target
-> decode beginning
-> decode middle
-> decode end
```

Untuk proof maksimum terhadap corruption payload:

```text
FULL STREAM DECODE
```

Media yang header-nya valid tetapi corrupt di tengah:

`FAIL_ASSET`.

---

# 28. Local HTML/CSS/Web Asset Closure

## RULE ASSET-025 — Bundled Web Content Harus Memiliki Dependency Closure

Jika project memiliki local web assets, bangun graph:

```text
HTML
|- CSS
|- JS
|- image
|- font
|- local links
```

Wajib:

```text
broken local URL = 0
missing asset    = 0
parse error      = 0
```

Kemudian load melalui jalur WebView/local-content yang benar-benar digunakan aplikasi dan verifikasi hasil sesuai contract.

---

# 29. Consumer Contract

## RULE ASSET-026 — Setiap Consumer dan Asset Harus Cocok

Asset yang valid sendiri dapat tetap menyebabkan crash/bug bila consumer mengharapkan tipe atau schema berbeda.

Untuk setiap relasi consumer -> asset, contract minimal:

```text
Consumer-ID
Asset-ID
expected type
expected schema
expected dimensions bila relevan
expected format
expected optionality
expected version
```

Wajib:

```text
asset output contract == consumer expected contract
```

Mismatch:

`FAIL_ASSET` bila asset tidak memenuhi contract yang telah ditentukan.

Jika contract benar tetapi kode consumer melanggar contract, gunakan `FAIL_APPLICATION`.

---

# 30. Consumer Discovery

## RULE ASSET-027 — Semua Titik Penggunaan Asset Harus Diketahui

Cari seluruh consumer asset, termasuk pola seperti:

- `R.*`
- `Resources.get*`
- `Context.getDrawable`
- `AssetManager.open`
- `openRawResource`
- `getIdentifier`
- `LayoutInflater`
- `MenuInflater`
- database asset loader
- media loader
- font loader
- WebView local asset
- custom parser/loader

Bangun `ASSET_CONSUMER_GRAPH`.

Wajib:

```text
Consumers discovered = N
Contracted consumers = N
Unknown              = 0
```

Unknown required consumer:

`INCOMPLETE_PROOF`.

---

# 31. Runtime Exhaustive Exercise

## RULE ASSET-028 — Semua Required Asset Wajib Di-exercise

Setelah artifact final tersedia:

```text
FOR EVERY REQUIRED Asset-ID
    resolve
    open
    parse
    decode
    inflate
    render/load/use
    validate semantic contract
END
```

Gunakan hanya operasi yang relevan dengan tipe asset.

Wajib:

```text
Required assets = N
Runtime proven  = N
Unproven        = 0
```

Sampling tidak cukup untuk `ASSET_SAFE_100`.

---

# 32. Runtime State-Space Proof

## RULE ASSET-029 — Asset yang Hanya Muncul pada State Tertentu Harus Memiliki Witness

Identifikasi state aplikasi/UI yang menyebabkan asset digunakan, misalnya:

- button pressed
- menu opened
- dialog opened
- dark mode
- orientation changed
- locale changed
- error state
- disabled state
- selected state

Setiap required asset yang state-dependent wajib memiliki `Witness State`.

Contoh:

```text
Asset-ID: error_icon
screen   : editor
state    : validation_error
result   : resolved + rendered + semantic check PASS
```

Required asset yang terpaket tetapi tidak pernah exercised:

`INCOMPLETE_PROOF`.

---

# 33. Visual Semantic Contract

## RULE ASSET-030 — Valid secara Teknis Tidak Sama dengan Benar secara Fungsi

Untuk visual asset penting, contract harus menjelaskan arti yang diharapkan bila arti tersebut dibutuhkan untuk mendeteksi bug.

Contoh:

```text
Asset-ID            : save_icon
expected semantic   : SAVE
expected dimensions : 24x24 logical
expected tintable   : true
expected states     : enabled/disabled
```

Tanpa oracle/semantic contract, agen tidak boleh mengklaim bahwa makna visual asset telah dibuktikan.

Status menjadi `INCOMPLETE_PROOF` untuk bagian semantic tersebut.

---

# 34. Resource Budget Safety

## RULE ASSET-031 — Asset Berat Harus Memenuhi Budget yang Ditentukan Contract

Untuk asset berat seperti:

- bitmap
- animation
- font
- video
- database
- large JSON/config

catat bila relevan:

```text
compressed size
expanded size
decode size
parse/decode cost
maximum expected simultaneous set
```

Bandingkan dengan budget pada contract.

Tujuan: mencegah asset yang valid tetapi menyebabkan resource exhaustion karena ukuran/karakteristik asset itu sendiri.

Jika asset melebihi budget contract:

`FAIL_ASSET`.

Jika resource exhaustion disebabkan logic aplikasi yang menggunakan asset di luar contract:

`FAIL_APPLICATION`.

---

# 35. Version dan Update Compatibility

## RULE ASSET-032 — Asset Versioned Harus Sesuai Consumer dan Data yang Didukung

Untuk asset yang dapat berubah antarversi seperti:

- database
- JSON schema
- template
- configuration
- dynamic registry

validasi bila relevan:

```text
asset schema/version
consumer supported version
migration/fallback
old persistent data compatibility
```

Asset baru yang valid sendiri tetapi tidak kompatibel dengan contract versi consumer:

`FAIL_ASSET`.

---

# 36. Final Package Equivalence

## RULE ASSET-033 — Source Proof Harus Dilanjutkan Sampai Artifact Final

Setelah artifact final terbentuk, buat `FINAL_ASSET_MODEL` dan bandingkan dengan expected model.

Untuk file yang tidak ditransformasi:

```text
expected digest == final content digest
```

Untuk Android compiled resources, bandingkan semantic identity:

```text
package
type
name
configuration
value/reference
```

Jangan memaksa byte checksum source sama dengan compiled resource bila format memang ditransformasi.

Wajib:

```text
Expected required packaged = N
Actual required packaged   = N
Missing                    = 0
Wrong semantic identity    = 0
```

---

# 37. Closed Fault Model

## RULE ASSET-034 — Semua Kelas Kegagalan Asset Harus Didaftarkan

Fault universe minimal wajib mencakup:

| Kelas | Contoh |
|---|---|
| Presence | missing |
| Identity | asset tertukar |
| Integrity | corrupt/truncated |
| Path | collision/case/Unicode ambiguity |
| Syntax | malformed XML/JSON |
| Schema | field/type salah |
| Semantic | nilai valid tetapi salah |
| Reference | dangling reference |
| Merge | wrong winner |
| Shrink | required resource removed |
| Dynamic | unresolved generated name |
| Configuration | qualifier hole |
| Fallback | default missing |
| State | selector state missing |
| Localization | translation/plural mismatch |
| Formatting | placeholder mismatch |
| Font | missing glyph |
| Decode | image/media corruption |
| Inflate | layout/drawable/menu failure |
| Database | schema/data integrity |
| Resource budget | asset terlalu berat |
| Consumer interface | loader/schema/type mismatch |
| Version | incompatible asset version |
| Visual | wrong rendering/meaning |
| Packaging | expected asset absent/wrong pada artifact final |

Jika ditemukan kelas kegagalan asset baru yang benar-benar berbeda saat implementasi, jangan diam-diam mengabaikannya. Kelas tersebut harus masuk fault model sebelum `ASSET_SAFE_100` dapat dinyatakan.

---

# 38. Mutation Proof

## RULE ASSET-035 — Verifier Wajib Dibuktikan Mendeteksi Setiap Fault Class

Untuk setiap kelas fault yang didefinisikan, buat minimal satu mutation/negative test yang sengaja menghasilkan kegagalan tersebut.

Contoh:

- hapus satu asset;
- corrupt bitmap;
- rusak reference XML;
- hilangkan default resource;
- buat wrong selector state;
- buat bitmap melewati budget;
- rusak database/schema;
- buat JSON schema mismatch;
- buat format string mismatch;
- buat wrong locale/plural;
- tukar visual asset;
- buat consumer-contract mismatch.

Wajib:

```text
Defined fault classes = N
Detected              = N
Escaped               = 0
```

Jika satu fault class lolos tanpa terdeteksi:

`INCOMPLETE_PROOF` dan `ASSET_SAFE_100` dilarang.

---

# 39. Pemisahan Error Tool dari Error Asset

## RULE ASSET-036 — Jangan Salah Mengklasifikasikan Error

Jika asset sendiri terbukti salah:

`FAIL_ASSET`.

Jika validator/emulator/CI/tool gagal sebelum keadaan asset dapat dibuktikan:

`INDETERMINATE_TOOL`.

Jika code/logic aplikasi melanggar contract asset yang benar:

`FAIL_APPLICATION`.

Agen dilarang:

- mengubah tool error menjadi `FAIL_ASSET` tanpa bukti;
- mengubah tool error menjadi PASS;
- menyimpulkan asset aman hanya karena tool berhenti tanpa menemukan error.

---

# 40. Formula Final ASSET_SAFE_100

`ASSET_SAFE_100` hanya boleh diberikan jika seluruh kondisi berikut benar:

```text
AssetUniverseClosed
AND AssetContractCoverage
AND PathUniqueness
AND IdentityIntegrity
AND TypeValidation
AND SchemaValidation
AND SemanticValidation
AND ReferenceGraphClosure
AND MergeCorrectness
AND ShrinkCorrectness
AND DynamicLookupClosure
AND ConfigurationClosure
AND DefaultFallbackClosure
AND StatefulResourceClosure
AND LocalizationClosure
AND FormatSignatureClosure
AND FontGlyphClosure
AND DecodeIntegrity
AND InflateIntegrity
AND DatabaseIntegrity
AND MediaIntegrity
AND ConsumerContractClosure
AND RuntimeLoadCoverage
AND RuntimeStateCoverage
AND VisualOracleCoverage
AND ResourceBudgetSafety
AND VersionCompatibility
AND FinalPackageEquivalence
AND FaultModelCoverage
AND MutationDetection
```

Dan seluruh nilai berikut harus nol:

```text
UNKNOWN      = 0
MISSING      = 0
UNPROVEN     = 0
SKIPPED      = 0
FAULT_ESCAPE = 0
```

Jika satu invariant wajib belum terbukti:

`ASSET_SAFE_100` dilarang.

---

# 41. Prosedur Eksekusi Wajib untuk Agen

Untuk setiap pekerjaan validasi asset, agen harus mengikuti urutan berikut tanpa melompati gate yang relevan:

```text
1. Baca AGENTS.md dan ASSET_SAFE_100_RULES.md
2. Tentukan ruang lingkup asset yang berubah/terkait
3. Bangun atau perbarui EXPECTED_ASSET_SET
4. Pastikan seluruh required asset memiliki Asset Contract
5. Jalankan canonical path verification
6. Jalankan type-specific source validation
7. Bangun dan validasi resource dependency graph
8. Tutup seluruh dynamic lookup domain
9. Validasi merge/overlay outcome
10. Validasi shrink outcome bila shrinking digunakan
11. Bangun configuration equivalence classes dan witnesses
12. Validasi default/fallback closure
13. Validasi stateful resource closure
14. Jalankan localization/string/plural/font closure
15. Jalankan database/JSON/media/web/raw validation sesuai tipe
16. Bangun Consumer Contract dan ASSET_CONSUMER_GRAPH
17. Validasi setiap consumer-asset interface
18. Verifikasi resource budget untuk asset berat
19. Bangun artifact final asset model
20. Bandingkan expected model dengan artifact final
21. Exercise setiap required asset melalui runtime consumer/loader yang sebenarnya
22. Exercise seluruh witness state/configuration yang diwajibkan
23. Jalankan visual/semantic oracle untuk asset yang memerlukan oracle
24. Jalankan version/update compatibility proof bila relevan
25. Jalankan mutation tests untuk seluruh fault classes
26. Hitung seluruh invariant ASSET_SAFE_100
27. Nyatakan status hanya berdasarkan aturan dokumen ini
```

Jika suatu langkah tidak relevan terhadap tipe asset tertentu, tandai sebagai `NOT_APPLICABLE` hanya bila contract membuktikan langkah tersebut memang tidak berlaku. `NOT_APPLICABLE` tidak boleh dipakai untuk menghindari pemeriksaan yang sebenarnya diperlukan.

---

# 42. Larangan untuk Agen

Agen dilarang:

1. Menyatakan asset aman hanya karena build berhasil.
2. Menyatakan asset aman hanya karena jumlah file cocok.
3. Menyatakan asset aman hanya karena checksum cocok.
4. Menggunakan parser generik untuk menggantikan type-specific validation.
5. Mengabaikan runtime inflation/decode/load yang diwajibkan.
6. Mengabaikan qualifier/configuration yang benar-benar tersedia di project.
7. Mengabaikan default/fallback.
8. Membiarkan dynamic resource berada di luar finite registry.
9. Mengabaikan stateful selector/resource.
10. Mengabaikan formatting signature pada localization.
11. Menganggap font load PASS berarti glyph coverage otomatis PASS.
12. Menganggap JSON parse PASS berarti semantic contract PASS.
13. Menganggap database open PASS berarti schema/data integrity PASS.
14. Menganggap visual asset benar tanpa oracle bila makna/tampilan merupakan bagian contract.
15. Mengabaikan resource budget untuk asset yang dapat menyebabkan resource exhaustion.
16. Mengabaikan consumer contract.
17. Menguji sampling required asset lalu mengklaim coverage 100%.
18. Menganggap artifact source sama dengan artifact final tanpa final package proof.
19. Menghapus fault class hanya agar mutation test hijau.
20. Mengklasifikasikan tool failure sebagai asset failure tanpa bukti.
21. Mengubah `UNKNOWN`, `UNPROVEN`, `SKIPPED`, atau `INCOMPLETE_PROOF` menjadi `ASSET_SAFE_100`.

---

# 43. Laporan Final Wajib

Laporan final minimal harus memuat:

```text
ASSET_SAFE_100 REPORT
=====================

Target platform          : Android 11 / API 30 / arm64-v8a
Required assets          : N
Contracted assets        : N/N
Canonical paths          : PASS
Type validation          : N/N
Schema validation        : N/N
Semantic validation      : N/N
Resource graph closure   : PASS
Dynamic lookup closure   : PASS
Merge correctness        : PASS
Shrink correctness       : PASS / NOT_APPLICABLE
Configuration classes    : N/N
Fallback closure         : PASS
State closure            : PASS
Localization closure     : PASS
Formatting signatures    : PASS
Font glyph closure       : PASS
Decode/inflate integrity : PASS
Database integrity       : PASS / NOT_APPLICABLE
Media integrity          : PASS / NOT_APPLICABLE
Consumer contracts       : N/N
Runtime assets exercised : N/N
Runtime witnesses        : N/N
Visual oracle coverage   : N/N
Resource budget safety   : PASS
Version compatibility    : PASS / NOT_APPLICABLE
Final package equivalence: PASS
Fault classes            : N/N detected
Fault escapes            : 0
Unknown                  : 0
Missing                  : 0
Unproven                 : 0
Skipped                  : 0

FINAL STATUS:
ASSET_SAFE_100
```

Jika hasil belum memenuhi formula final, laporan wajib menggunakan status non-PASS yang sesuai dan menyebut invariant yang belum terpenuhi.

---

# 44. Definition of Done

Pekerjaan asset baru dianggap selesai pada tingkat maksimum jika:

```text
Asset universe closure       = 100%
Asset contract coverage      = 100%
Path uniqueness              = 100%
Type validation              = 100%
Schema validation            = 100%
Semantic validation          = 100%
Resource graph closure       = 100%
Dynamic lookup closure       = 100%
Configuration closure        = 100%
State closure                = 100%
Localization closure         = 100%
Consumer contract closure    = 100%
Runtime exercise             = 100%
Visual oracle coverage       = 100% untuk asset yang memerlukan oracle
Resource budget proof        = 100%
Final package equivalence    = 100%
Fault model detection        = 100%

UNKNOWN                      = 0
MISSING                      = 0
UNPROVEN                     = 0
SKIPPED                      = 0
FAULT_ESCAPE                 = 0
```

Status akhir:

`ASSET_SAFE_100`

---

# 45. Batas Klaim

`ASSET_SAFE_100` berarti:

> 100% seluruh asset yang didefinisikan terbukti benar terhadap contract-nya, tersedia pada seluruh configuration/state yang didukung, dapat digunakan melalui consumer sebenarnya, memenuhi semantic/resource-budget requirements, dan tidak memiliki kelas kegagalan asset yang didefinisikan tetapi tidak diperiksa.

`ASSET_SAFE_100` tidak berarti alat pembuatan, emulator, compiler, CI, toolchain, kernel, hardware, atau kode aplikasi secara umum dijamin bebas bug. Hal-hal tersebut berada di luar ruang lingkup penilaian asset dokumen ini kecuali terbukti langsung menyebabkan perubahan/kerusakan pada asset yang sedang dinilai.