# toolbox.runtime.metadata — Contract 1.0.0

> Aturan aktif: contract ini dikembangkan dan dimatangkan di repo publik utama. Secret, signing, credential, final signed build, Firebase/final runtime test, dan release sensitif berada di jalur private.

## Machine identity

- `CONTRACT_ID`: `toolbox.runtime.metadata`
- `VERSION`: `1.0.0`
- `COMPATIBILITY`: metadata-only, Java 11 bytecode, Android API 30 product target
- `COMPONENT_ID`: `public.runtime-contracts`

## Stable ID

Stable ID wajib lowercase dan berbentuk segment machine-readable seperti:

```text
tool.editor
component.button
action.data.save
capability.editor.visual
event.project.saved
permission.storage.read
```

Label pengguna tidak menjadi Stable ID.

Batas input wajib:

```text
MAX_STABLE_ID_LENGTH = 128
MAX_VERSION_LENGTH = 64
MAX_EXTERNAL_REF_LENGTH = 256
MAX_COLLECTION_SIZE = 256
MAX_BUNDLE_ENTRIES = 512
MAX_REGISTRY_ENTRIES = 4096
```

Input di luar batas tersebut harus fail-closed dengan `RESOURCE_LIMIT`. Batas ini adalah bagian dari contract R1/R2 dan tidak boleh dihapus tanpa impact analysis serta pengulangan evidence R1–R9 yang terdampak.

## Tool Contract

Input metadata:

```text
toolId
toolVersion
contractVersion
dependencies[]
componentIds[]
actionIds[]
capabilityIds[]
eventIds[]
permissionIds[]
entryPointId
```

Tidak ada executable callback pada contract publik ini.

## Component Contract

```text
componentId
componentVersion
contractVersion
providerToolId
propertyContractIds[]
eventIds[]
capabilityRequirements[]
permissionNeeds[]
implementationRef
```

## Action Contract

```text
actionId
actionVersion
contractVersion
providerToolId
inputSchemaRef
outputSchemaRef
capabilityRequirements[]
permissionNeeds[]
executionMode
asyncBehavior
timeoutPolicy
cancellationPolicy
idempotencyPolicy
```

## Capability Contract

```text
capabilityId
capabilityVersion
contractVersion
providerToolId
compatibilityRef
permissionNeeds[]
```

## Event Contract

```text
eventId
contractVersion
providerToolId
payloadSchemaRef
propagationPolicy
compatibleActionTypes[]
```

## Permission Contract

```text
permissionId
kind = INSTALL_TIME | RUNTIME | SPECIAL_ACCESS | OPTIONAL
platformPermissionRef
reasonKey
deniedBehavior
unsupportedBehavior
```

Runtime permission tidak boleh dianggap granted hanya karena metadata menyatakannya.

## Publication

Satu `ToolBundle` diterbitkan secara atomik ke `ProductRegistry`.

Sebelum commit registry wajib memeriksa:

1. seluruh Stable ID valid;
2. tidak ada ID duplikat, termasuk duplikat lintas domain dalam bundle yang sama;
3. provider ID sesuai tool bundle;
4. daftar ID yang dideklarasikan Tool sama dengan isi bundle;
5. permission/capability/event reference tersedia pada bundle atau registry yang sudah committed;
6. dependency Tool tersedia bila diwajibkan;
7. seluruh batas ukuran/resource terpenuhi;
8. tidak ada state parsial bila validasi gagal.

## Lookup

Lookup hanya exact Stable ID. Nama/label mirip tidak boleh digunakan untuk koneksi otomatis.

## Failure codes

```text
CONTRACT_INVALID
RESOURCE_LIMIT
DUPLICATE_ID
PROVIDER_MISMATCH
DEPENDENCY_MISSING
CAPABILITY_UNAVAILABLE
PERMISSION_REFERENCE_MISSING
DECLARATION_MISMATCH
```

Failure publication tidak boleh menghapus entry registry yang sebelumnya valid.

## Thread safety

Publication dan snapshot wajib thread-safe. Concurrent bundle publication tidak boleh menghasilkan partial registry state. Registry menggunakan satu synchronization boundary untuk publication/read/snapshot sehingga mutation publication terserialisasi.

## Persistence / lifecycle boundary

Registry publik ini bersifat in-memory dan tidak memiliki persistence, database, file state, Android lifecycle owner, process-death restore, recovery journal, atau rollback engine. Domain tersebut bukan bagian dari component contract ini dan hanya boleh ditambahkan melalui contract baru yang kembali menjalani assurance yang relevan.

## Executable boundary

Contract ini menyimpan metadata saja. Ia tidak mempunyai API untuk:

- class loading;
- DEX/JAR/native loading;
- arbitrary reflection execution;
- Android permission grant;
- filesystem/network authority;
- database/persistent storage authority;
- UI/hardware/power authority;
- Firebase/build/signing.

Implementasi runtime yang mengonsumsi contract ini wajib tetap melewati validation, capability, permission, lifecycle, dan safety boundary milik aplikasi utama.
