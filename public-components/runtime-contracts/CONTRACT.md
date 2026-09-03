> Catatan aktif: aturan lintas repo lama sudah dihapus. ToolBox dikerjakan di repo publik utama; repo private hanya untuk secret, signing, build final, Firebase/final runtime test, dan release sensitif.

## Private integration handoff

Sebelum regression atau build setelah integrasi, host Private wajib:

Rincian machine-readable ada di `PRIVATE_INTEGRATION_REQUIREMENTS.json`.

Public hanya mendefinisikan requirement handoff. Public dilarang mengetahui, mengambil, atau mengubah daftar build input/trust manifest internal Private. Jika host Private tidak dapat membuktikan trust state baru, integrasi harus fail-closed dan kembali melalui sanitized failure flow.
