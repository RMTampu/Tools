# TEST ROUTING POLICY

## Status

Aturan test routing lintas repo lama sudah dihapus.

## Jalur Test

- Test pengembangan berjalan di repo publik utama.
- Test boleh mencakup unit test, integration test, UI test, emulator test, dan build debug/release unsigned selama tidak memakai secret sensitif.
- Test final yang memakai credential asli, signing key, Firebase, atau artifact tertandatangan berjalan di repo private.

## Firebase

Firebase/Test Lab dengan credential asli hanya boleh berjalan dari jalur private.

Repo publik tidak boleh menyimpan credential Firebase. Repo publik boleh menyimpan mock, stub, dokumentasi, dan test tanpa secret.

## Build

Build pengembangan boleh berjalan di repo publik.

Build final tertandatangan dan verifikasi signature berjalan di repo private.
