# REPOSITORY WORK POLICY

## Status

Kebijakan lintas repo lama sudah dihapus.

ToolBox sekarang memakai pola kerja:

- repo publik utama untuk rancangan, source, asset publik, implementasi, test, dan pematangan aplikasi;
- repo private untuk secret, signing, credential, build final tertandatangan, Firebase/final runtime test, dan release sensitif.

## Aturan

1. Jangan masukkan secret, token, private signing key, credential Firebase, atau data sensitif ke repo publik.
2. Semua source dan asset yang masuk repo publik dianggap boleh dilihat publik.
3. Jangan memakai repo private sebagai tempat trial-and-error fitur.
4. Jangan membuat Promotion Package, Stage Capsule, receiver contract, dummy private host, atau wiring manifest lintas repo.
5. Build final dan signing dilakukan di jalur private.
6. Termux hanya relay bila diperlukan dan bukan tempat build aplikasi.

## Jika Ada Dokumen Lama

Jika dokumen lama menyebut Public Research, Private Master, STAGE_READY_PRIVATE, COMPONENT_READY_PRIVATE, Promotion Package, Stage Capsule, receiver private, atau wiring lintas repo, bagian itu tidak berlaku dan harus dibersihkan saat disentuh.
