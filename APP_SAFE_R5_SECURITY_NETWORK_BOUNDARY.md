# APP_SAFE_R5_SECURITY_NETWORK_BOUNDARY.md

## 1. Status

Korpus metode aktif untuk **R5 — Security, Permission, Network & External-Boundary Safety** dalam framework `APPLICATION_SAFE_100`.

Status riset: `PRACTICAL_SATURATION` terhadap ruang metode yang telah disapu.

---

## 2. Scope

R5 menutup:

- permission and privilege usage;
- authentication/authorization boundary;
- untrusted input from UI/IPC/files/network/deep links;
- network confidentiality/integrity/endpoint identity;
- retry/timeout/offline/partition behavior;
- idempotency and duplicate effects;
- external-service schema/contract drift;
- secret/credential handling where application-owned;
- hostile/malformed external data;
- security testing and attack-surface closure.

WebView-specific runtime behavior dimiliki R8, tetapi trust boundary dan untrusted content rule tetap tunduk pada R5.

---

## 3. Metode Aktif

### R5-M01 — Trust-Boundary & Data-Flow Universe Closure
Inventaris seluruh entry/exit boundary: UI/user input, deep link, Intent/IPC, ContentProvider, clipboard/file import, network endpoint, callback/webhook-like channel, external storage, SDK/plugin input, and remote configuration. Setiap flow diberi trust level dan authority.

### R5-M02 — Threat Modeling & Attack-Surface Closure
Bentuk threat model terhadap assets, actors, trust boundaries, privilege transitions, spoofing/tampering/replay/injection/data exposure/DoS, dan abuse case. Threat baru yang material wajib masuk fault model.

### R5-M03 — Least Privilege & Permission Contract
Setiap permission/capability harus mempunyai requirement, scope, timing, denial behavior, revocation behavior, and fallback. Permission yang tidak diperlukan dilarang. Runtime permission denial/revocation harus menjadi tested state.

### R5-M04 — Authentication / Authorization Enforcement
Authentication membuktikan identity; authorization membuktikan operation/resource access. Sensitive operation tidak boleh bergantung hanya pada UI visibility atau client-side state bila authoritative enforcement berada di server/service.

### R5-M05 — Boundary Input Validation / Canonicalization
Semua untrusted input wajib schema/type/range/length/encoding/normalization validation sebelum digunakan. Validate after canonicalization bila representation ambiguity dapat mengubah identity/authorization.

### R5-M06 — Output / Interpreter Boundary Safety
Data yang menuju SQL, HTML/JS, shell-like interpreter, URI, path, log, serialization, or dynamic loader harus memakai safe API/escaping/parameterization sesuai sink. Input sanitization generik tidak menggantikan sink-specific encoding.

### R5-M07 — Secure Transport Policy
Network channel yang memerlukan integrity/confidentiality wajib memakai platform TLS validation yang benar, cleartext disabled kecuali contract eksplisit membolehkan local/non-sensitive exception, dan custom trust override tidak boleh bocor ke release.

### R5-M08 — Endpoint Identity / Trust-Anchor Closure
Tetapkan expected endpoint/domain/trust anchor. Certificate/CA policy, hostname verification, certificate transparency/pinning bila dipilih, backup/rotation key, expiration, and recovery from certificate rotation harus diuji. Pinning bukan default universal; jika digunakan wajib ada lifecycle contract.

### R5-M09 — Timeout / Retry / Backoff / Jitter Policy
Setiap remote operation mempunyai connect/read/write/overall deadline, retryable-vs-permanent error classification, bounded attempts, exponential/backoff strategy, and jitter where fan-out can synchronize clients.

### R5-M10 — Idempotency / Duplicate-Suppression Proof
Operation yang dapat di-retry setelah uncertain outcome wajib idempotent atau menggunakan operation ID/deduplication/transaction semantics sehingga duplicate delivery tidak menggandakan destructive side effect.

### R5-M11 — Circuit-Break / Fail-Fast / Bulkhead Policy
Untuk dependency yang dapat gagal lama, tetapkan fail-fast/circuit breaker and isolation boundary bila dibutuhkan. Dependency failure tidak boleh menghabiskan seluruh worker/thread/queue budget aplikasi.

### R5-M12 — Offline / Partition / Reconciliation Model
Tentukan source of truth, offline-readable state, queued write policy, conflict resolution, stale data indicator, retry after reconnect, duplicate merge, and ordering semantics. Network availability tidak boleh diasumsikan kontinu.

### R5-M13 — Remote Contract / Schema Version Compatibility
Response/request schema, status code, nullable/missing/unknown fields, pagination, partial response, version negotiation, rate limit, and deprecation behavior harus memiliki contract dan compatibility tests.

### R5-M14 — Network / External Fault Injection
Inject DNS failure, connection refusal, TLS failure, certificate rotation/expiry test cases, timeout, packet loss where feasible, offline/online transition, truncated response, malformed JSON/binary, duplicate response, out-of-order completion, HTTP error classes, and server overload/rate limit.

### R5-M15 — Replay / Freshness / Time-Bound Security Semantics
Untuk token/signature/nonce/time-sensitive operation, verify replay protection, expiry/skew handling, monotonic sequence where needed, clock failure strategy, and no silent acceptance of stale security assertion.

### R5-M16 — Secret / Credential Lifecycle
Application-owned secret must not be hardcoded unless explicitly public/non-secret; define generation/import, storage, access scope, rotation, invalidation, backup policy, logging redaction, and memory exposure constraints where relevant.

### R5-M17 — Static Security Analysis / Taint Analysis
Gunakan lint/static/taint analysis untuk menemukan unsafe sources-to-sinks, exported component misconfiguration, insecure crypto/network call, and missing validation. Finding disposition wajib evidence-based.

### R5-M18 — Dynamic Security Testing / Fuzzing / Penetration Testing
Exercise boundary dengan hostile inputs, permission manipulation, deep links, IPC, network proxy/fault lab, and abuse cases. Penetration testing adalah independent challenge, bukan pengganti contract/static proof.

### R5-M19 — Third-Party Endpoint / SDK Boundary Containment
External dependency harus mempunyai failure contract, data-sharing scope, timeout/resource cap, version expectation, and disable/fallback policy. SDK behavior tidak boleh diberi implicit unlimited trust.

### R5-M20 — Change-Impact & Mutation Adequacy
Perubahan permission, exported component, endpoint, auth flow, trust config, schema, retry, cryptographic policy, or external SDK invalidates affected proof. Mutations: permission bypass, cleartext enabled, hostname validation bypass, retry unbounded, duplicate side effect, malformed input accepted, revoked permission crash.

---

## 4. Fault Model Minimum

```text
EXCESS_PRIVILEGE
PERMISSION_DENIAL_CRASH
PERMISSION_REVOCATION_ERROR
AUTHENTICATION_BYPASS
AUTHORIZATION_BYPASS
UNTRUSTED_INPUT_ACCEPTED
INJECTION_SINK_ERROR
CLEARTEXT_TRAFFIC_UNEXPECTED
TLS_VALIDATION_BYPASS
WRONG_ENDPOINT_TRUST
CERT_ROTATION_FAILURE
UNBOUNDED_RETRY
RETRY_STORM
NON_IDEMPOTENT_RETRY_DUPLICATION
OFFLINE_STATE_ERROR
RECONCILIATION_CONFLICT_ERROR
REMOTE_SCHEMA_DRIFT
MALFORMED_REMOTE_RESPONSE
REPLAY_ACCEPTED
STALE_TOKEN_TIME_ERROR
SECRET_EXPOSURE
EXTERNAL_DEPENDENCY_RESOURCE_EXHAUSTION
```

---

## 5. PASS Formula

`APP_SAFE_R5_PASS` hanya jika:

```text
TRUST_BOUNDARY_UNKNOWN = 0
UNJUSTIFIED_PERMISSION = 0
UNVALIDATED_UNTRUSTED_INPUT = 0
AUTHORIZATION_PATH_UNKNOWN = 0
INSECURE_REQUIRED_TRANSPORT = 0
REMOTE_FAILURE_POLICY_UNKNOWN = 0
NON_IDEMPOTENT_UNCERTAIN_RETRY = 0
OFFLINE_RECONCILIATION_UNKNOWN = 0
EXTERNAL_CONTRACT_UNKNOWN = 0
SECURITY_NETWORK_FAULT_ESCAPE = 0
STALE_EVIDENCE = 0
```
