# ToolBox — Public Research / Test / Staging

`RMTampu/Tools` adalah repository **Public Research / Test / Staging**.

```text
RMTampu/ToolBox (PRIVATE)
= product master + source + asset + final integration + build + signing + Firebase/final test + release

RMTampu/Tools (PUBLIC)
= research + prototype + component development + mock/simulator + public testing + READY_PRIVATE staging
```

## Batas Repository

- Product source aplikasi/kernel Private tidak disimpan atau dibaca di repository ini.
- Product asset/resource Private tidak disimpan atau dibaca di repository ini.
- APK/artifact Private tidak boleh direlay melalui repository ini.
- Public tidak boleh menerima credential untuk checkout/read Private.
- Workflow Public hanya boleh bekerja terhadap component/source/data yang memang Public, mock, simulator, fixture, atau prototype.
- Public tidak menjadi reusable CI untuk source Private.
- Public dilarang melakukan akses/pengecekan/pengujian Firebase/Test Lab, termasuk untuk dummy/prototype Public; tidak boleh menjadi caller/bridge/relay Firebase.
- Final build/signing/Firebase/release berada pada boundary Private.

## Jalur Resmi

```text
PUBLIC
RESEARCH
-> DESIGN
-> BUILD COMPONENT
-> AUDIT / TEST / SIMULATOR
-> PACKAGE
-> READY_PRIVATE
-> AUTO CLEANUP
```

Pengujian penyambungan Public memakai dummy/mock/simulator mandiri dari contract aman, bukan salinan/ekstraksi/penyamaran baseline APK atau isi Private, dan tanpa Firebase.

Setelah `READY_PRIVATE`, hanya Promotion Package aman yang masuk ke Private untuk integrasi baseline sebenarnya. Status tersebut bukan final application PASS. Public tidak mengetahui state final Private seperti `A`, `A+B`, atau `A+B+C`.

Setiap Public job wajib memiliki Auto Cleanup otomatis setelah sukses/gagal sejauh platform memungkinkan.

Baca `AGENTS.md`, `GLOBAL_PUBLIC_PRIVATE_DEVELOPMENT_RULES.md`, dan `REPOSITORY_INTEGRATION_POLICY.md` sebelum perubahan.
