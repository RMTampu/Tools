# APP_SAFE_R1_LOGIC_INPUT.md

## 1. Status

Dokumen ini adalah korpus metode aktif untuk **R1 — Logic, Exception & Input Semantics Safety** dalam framework `APPLICATION_SAFE_100`.

Hanya metode yang menambah kelas jaminan nyata setelah penyaringan, penggabungan duplikasi, dominance filtering, dan audit kontra yang dipertahankan. Heuristik, tool tertentu, AI/LLM, coverage statistik, atau teknik yang hanya mengulang jaminan metode lain tidak menjadi dasar PASS sendiri.

Status riset: `PRACTICAL_SATURATION` terhadap ruang metode yang telah disapu. Ini bukan klaim bahwa metode baru tidak mungkin ditemukan di masa depan.

---

## 2. Scope

R1 menutup minimal:

- application/business logic;
- nullability dan invalid reference;
- exception flow dan error propagation;
- unexpected/external input semantics;
- parsing dan validation semantics;
- time/date/timezone/locale/Unicode semantics;
- numeric overflow/underflow/precision/NaN/Infinity;
- protocol/typestate misuse pada API/object;
- decision/condition logic;
- perubahan kode/requirement yang membatalkan proof logika.

Concurrency, lifecycle, persistence, network transport, build, native, UI/device, dan application-wide completeness memiliki owner R2–R9 kecuali suatu invariant R1 secara eksplisit melintasi batas tersebut.

---

## 3. Metode Aktif

### R1-M01 — Requirement Correctness, Completeness, Consistency & Testability
Requirement yang menjadi sumber perilaku harus dapat diperiksa kebenaran, kelengkapan, konsistensi, determinisme yang diperlukan, batas input, off-nominal behavior, dan acceptance oracle-nya.

### R1-M02 — End-to-End Semantic Traceability
Wajib tersedia jalur:

```text
REQUIREMENT -> MODEL/CONTRACT -> IMPLEMENTATION -> TEST/PROOF -> OBSERVABLE RESULT
```

Untraced required behavior = `NOT_PROVEN`.

### R1-M03 — Executable Contracts / Design by Contract
Tetapkan precondition, postcondition, invariant, illegal-state rule, error semantics, dan ownership terhadap nilai penting. Contract tidak boleh hanya berupa komentar yang tidak diverifikasi.

### R1-M04 — Type, Nullness, Range & Refinement Enforcement
Gunakan type system, nullness analysis, value/range constraints, sealed/closed domains, atau refinement/dependent-type style proof bila relevan untuk membuat invalid state tidak representable atau terdeteksi sebelum runtime.

### R1-M05 — Formal Specification & Reference Model
Untuk logic kritis, bentuk model formal atau executable reference model yang mendefinisikan behavior yang benar secara independen dari implementasi utama.

### R1-M06 — Deductive Verification / Verification Conditions
Gunakan theorem proving, Hoare-style reasoning, SMT-backed verification, atau proof obligation yang setara untuk property yang memerlukan bukti universal terhadap domain yang ditutup.

### R1-M07 — Model Checking
Model state/decision yang relevan dan periksa safety/liveness/temporal property pada seluruh reachable state dalam model. Counterexample harus diperlakukan sebagai evidence kegagalan model/implementation contract.

### R1-M08 — Abstract Interpretation & Sound Static Data-Flow Analysis
Gunakan analisis statis yang sesuai untuk nullness, unreachable/error path, range, exception flow, taint/value propagation, dan property lain yang dapat dibuktikan secara sound terhadap abstraksi yang digunakan.

### R1-M09 — Symbolic / Concolic Path Exploration
Eksplorasi branch/path dengan constraint solving untuk menemukan path langka yang sulit dicapai oleh test input biasa. Bound dan path yang tidak dieksplorasi harus dinyatakan eksplisit.

### R1-M10 — Bounded Exhaustive Enumeration
Jika domain finite atau dapat dibatasi secara sah, enumerasi seluruh nilai/state/sequence di dalam bound. Klaim tidak boleh diperluas di luar bound yang dibuktikan.

### R1-M11 — Input Partition, Boundary & Robustness Testing
Definisikan equivalence partition, min/max, just-inside, just-outside, empty, malformed, oversized, duplicate, missing, dan invalid combinations berdasarkan contract.

### R1-M12 — Decision/Condition Coverage
Untuk decision logic kritis gunakan branch/condition coverage, full condition combination bila feasible, dan MC/DC bila diperlukan untuk membuktikan pengaruh independen condition pada decision.

### R1-M13 — Combinatorial t-Way Interaction Testing
Model faktor input/config/state dan constraints-nya. Terapkan covering array sampai interaction strength yang ditentukan oleh fault model; jangan menyebut t-way sebagai exhaustive bila `t < N`.

### R1-M14 — Property-Based / Generative Testing
Turunkan generator dan property dari contract, bukan dari implementasi. Sertakan shrinking/minimization counterexample dan invalid-input generators.

### R1-M15 — Structured / Grammar-Aware / Stateful Fuzzing
Gunakan grammar/schema/state machine untuk menghasilkan input yang valid, semi-valid, dan adversarial sehingga parser/logic dalam dapat dijangkau; coverage-guided fuzzing hanya supporting kecuali fault domain dan oracle tertutup.

### R1-M16 — Metamorphic Testing
Jika oracle exact sulit tersedia, definisikan metamorphic relations yang benar secara requirement dan verifikasi hubungan output antar-transformasi input.

### R1-M17 — Differential / Equivalence Checking
Bandingkan implementasi dengan reference implementation/model atau implementasi independen. Common-mode error wajib dianalisis; agreement saja bukan proof jika sumber oracle tidak independen.

### R1-M18 — Exception-Flow Analysis & Fault Injection
Enumerasi throw/catch/propagation boundary, cancellation, timeout/error conversion, cleanup/finally semantics, dan inject kegagalan pada operasi yang dapat gagal. Silent swallowing dan ambiguous fallback dilarang.

### R1-M19 — Typestate / Protocol Compliance
Untuk API/object dengan urutan penggunaan valid, bentuk state machine protocol dan buktikan sequence legal, cleanup state, terminal state, repeated call semantics, dan forbidden transitions.

### R1-M20 — Numeric Semantics Proof
Untuk aritmetika relevan verifikasi integer overflow/underflow, narrowing/sign conversion, divide-by-zero, NaN/Infinity, rounding mode, floating-point precision/error bound, accumulation error, dan range contract.

### R1-M21 — Unicode / Locale / Time Semantic Closure
Tutup domain normalization, case mapping, grapheme/codepoint handling, confusable-sensitive identity bila relevan, locale-sensitive formatting/parsing, timezone/DST transition, calendar/date boundary, clock source, monotonic-vs-wall-clock semantics, dan versioned data rules.

### R1-M22 — Formal Inspection / Peer Review / Independent V&V
Logic kritis harus dapat diperiksa oleh reviewer/oracle independen dengan checklist berbasis contract dan evidence. Review tidak menggantikan proof otomatis/formal yang diwajibkan tetapi menutup defect class yang tidak terwakili oleh tool tunggal.

### R1-M23 — Correct-by-Construction / Refinement / Synthesis
Jika feasible, hasilkan implementasi dari specification/refinement steps atau verified generator sehingga sebagian correctness berasal dari construction, kemudian tetap verifikasi translation/output terhadap contract.

### R1-M24 — Specification / Invariant Mining sebagai Discovery
Inference dari trace/test/code boleh dipakai untuk menemukan invariant tersembunyi, tetapi inferred invariant wajib dikonfirmasi oleh requirement, formal reasoning, atau independent oracle sebelum menjadi dasar PASS.

### R1-M25 — Mutation / Fault Seeding Adequacy
Buat mutation untuk setiap fault class R1 yang didefinisikan. Verifier/test suite harus membunuh mutation yang semantically meaningful. Equivalent mutation wajib diklasifikasikan, bukan dihitung sebagai escape.

### R1-M26 — Change-Impact & Regression Proof Invalidation
Perubahan requirement, code, type, compiler semantics, library behavior, data model, locale/time rules, oracle, atau verifier harus menentukan earliest affected proof. Evidence downstream yang terdampak wajib invalidated dan dijalankan ulang.

---

## 4. Fault Model Minimum

R1 fault universe minimal mencakup:

```text
WRONG_BRANCH
MISSING_CASE
INVALID_DEFAULT
NULL_DEREFERENCE
ILLEGAL_STATE
UNHANDLED_EXCEPTION
WRONG_EXCEPTION_MAPPING
SILENT_ERROR_SWALLOW
INVALID_INPUT_ACCEPTED
VALID_INPUT_REJECTED
BOUNDARY_ERROR
COMBINATION_ERROR
PROTOCOL_SEQUENCE_ERROR
INTEGER_OVERFLOW_UNDERFLOW
FLOAT_PRECISION_ERROR
NAN_INFINITY_ERROR
UNICODE_NORMALIZATION_ERROR
LOCALE_SEMANTIC_ERROR
TIMEZONE_DST_CLOCK_ERROR
PARSER_SEMANTIC_ERROR
REQUIREMENT_IMPLEMENTATION_MISMATCH
ORACLE_ERROR
REGRESSION_AFTER_CHANGE
```

Fault class baru yang berbeda wajib ditambahkan sebelum final PASS.

---

## 5. Metode yang Diserap / Tidak Menjadi Proof Mandiri

Berikut tidak menjadi fondasi PASS sendiri:

- random testing tanpa contract/coverage model;
- AI/LLM-generated tests tanpa oracle independen;
- exploratory testing/error guessing;
- raw statement coverage;
- operational-profile/statistical reliability estimate;
- one-shot code review;
- logging/telemetry tanpa proof property;
- testing framework tertentu sebagai pengganti metode;
- specification mining yang belum dikonfirmasi.

Teknik tersebut boleh dipakai sebagai supporting evidence atau generator.

---

## 6. PASS Formula

`APP_SAFE_R1_PASS` hanya boleh diberikan jika seluruh method yang diwajibkan oleh scope mempunyai evidence yang valid dan:

```text
REQUIRED_BEHAVIOR_UNKNOWN = 0
UNTRACED_BEHAVIOR = 0
UNPROVEN_CRITICAL_DECISION = 0
UNHANDLED_REQUIRED_EXCEPTION_PATH = 0
UNCLOSED_REQUIRED_INPUT_DOMAIN = 0
NUMERIC_SEMANTIC_UNKNOWN = 0
TIME_LOCALE_UNICODE_UNKNOWN = 0
FAULT_ESCAPE = 0
STALE_EVIDENCE = 0
```

`NOT_APPLICABLE` hanya sah bila contract membuktikan metode/fault class tersebut benar-benar tidak berlaku.
