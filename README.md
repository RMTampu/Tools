# ToolBox — Public Build/Test Engine

`RMTampu/Tools` adalah repository **public Build/Test/CI/Firebase engine**.

```text
RMTampu/ToolBox (PRIVATE)
= product master + source + asset + rancangan + product verification state

RMTampu/Tools (PUBLIC)
= reusable workflow + validator/orchestration + test tooling + Firebase bridge
```

## State setelah migrasi

- Product source aplikasi/kernel tidak disimpan di repository ini.
- Product asset/resource tidak disimpan di repository ini.
- Product Gradle workspace dan product verification state tidak disimpan di repository ini.
- Master rancangan berada di private `RMTampu/ToolBox`.
- Workflow reusable selalu bekerja terhadap **caller source**, bukan stale public product copy.
- Private caller harus mem-pin workflow public ke commit SHA tervalidasi.
- Source/asset private tidak boleh dipublikasikan melalui log atau artifact public.
- Firebase Final Gate tetap LOCKED dan memerlukan approval eksplisit pengguna untuk setiap execution attempt.

Repository ini dipertahankan sebagai CI engine karena jalur build/test dan integrasi eksternal seperti Firebase terhubung ke sini.

Baca `AGENTS.md` dan `REPOSITORY_INTEGRATION_POLICY.md` sebelum perubahan.
