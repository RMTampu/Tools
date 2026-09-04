# TASK.md - Tugas Aktif Global

## Tugas Aktif

Kerjakan **Tahap 2 — Pematangan Pembuatan/Implementasi** pada repo publik utama `RMTampu/Tools`.

Baseline Tahap 1 sudah dikunci pada jalur private sebagai exact APK Android 11 yang signed + Firebase-tested. Tahap 2 wajib menghasilkan kandidat baru dengan `versionCode` lebih tinggi dan tidak boleh memodifikasi baseline Tahap 1.

## Scope Tahap 2 Aktif

Urutan implementasi dimulai dari fondasi Stage B yang sudah ditetapkan:

1. identity dan schema versioned;
2. storage gateway dan implementation nyata;
3. Save;
4. Undo/Redo;
5. revision history yang bounded;
6. corruption detection dan recovery;
7. integrasi ke kernel/host Android 11;
8. unit/failure/persistence tests;
9. public build/package validation sampai PASS.

Tahap lanjutan setelah core ini tetap mencakup freeze/journal, import terbuka, repair/dynamic UI, managed target, lalu validasi/distribusi sesuai roadmap, tetapi tidak boleh melompati core Stage B.

## Exit Gate Unit Tahap 2 Ini

Unit dianggap PASS hanya bila:

- `applicationId=com.toolbox.tools` tetap;
- target/min SDK tetap API 30;
- `versionCode > 1`;
- workspace identity + schema tervalidasi fail-closed;
- save/load deterministic;
- checksum corruption ditolak;
- file replacement atomic atau fail-closed;
- backup valid dapat dipakai untuk recovery;
- Undo/Redo dan revision teruji;
- history bounded;
- recovery state terpicu pada storage failure;
- Android host memakai persistent storage gateway;
- unit tests PASS;
- unsigned release APK berhasil dibuild GitHub Actions dan artifact tersedia.

Private tetap hanya untuk signing, credential, Firebase final runtime test, dan release sensitif.
