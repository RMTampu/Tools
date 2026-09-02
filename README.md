# ToolBox — Public Build/Test Engine

`RMTampu/Tools` sekarang adalah repository **public build/test/CI engine** untuk ToolBox dan project private terkait.

```text
RMTampu/ToolBox (PRIVATE)
= master source + asset + rancangan + product state

RMTampu/Tools (PUBLIC)
= reusable workflow + validator + build/test tooling + Firebase bridge
```

Repository ini tetap dipakai karena sudah terhubung dengan jalur build/test dan layanan eksternal seperti Firebase.

Source/product copy lama yang masih ada di sini selama migrasi adalah `LEGACY_MIGRATION_COPY`, bukan source of truth baru.

## Aturan utama

- APK tetap dibangun hanya melalui GitHub Actions.
- Private project menjadi caller untuk reusable workflow public bila jalur tersebut tersedia.
- Workflow shared harus dipin ke tag/commit SHA yang tervalidasi.
- Build candidate harus dapat ditelusuri ke source commit private dan CI workflow ref.
- Source/asset private tidak boleh dicetak ke log atau dipublikasikan sebagai artifact public.
- Firebase Final Gate tetap membutuhkan approval eksplisit pengguna per execution attempt.

Baca `AGENTS.md` dan `REPOSITORY_INTEGRATION_POLICY.md` sebelum bekerja.

Master rancangan dan pengembangan ToolBox berada di private `RMTampu/ToolBox`.