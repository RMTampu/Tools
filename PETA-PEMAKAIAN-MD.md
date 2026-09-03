# PETA PEMAKAIAN MARKDOWN

## Status

Peta ini mengikuti aturan baru: ToolBox dikerjakan di repo publik utama, sedangkan repo private hanya untuk secret, signing, build final, Firebase/final runtime test, dan release sensitif.

## Urutan Baca Umum

1. AGENTS.md
2. GLOBAL_PUBLIC_PRIVATE_DEVELOPMENT_RULES.md
3. Rancangan utama yang sesuai pekerjaan
4. File aturan/test/safety yang langsung terkait
5. File implementasi yang akan diubah

## Aturan Pembersihan

Jika ada Markdown lama yang masih membawa pola pemisahan pengembangan antar repository, bagian itu tidak berlaku dan harus dibersihkan saat file tersebut disentuh.

## Batas Rahasia

Jangan memasukkan secret, signing key, token, credential Firebase, atau data sensitif ke repo publik.
