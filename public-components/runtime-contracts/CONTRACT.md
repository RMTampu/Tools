# toolbox.runtime.metadata — Contract 1.0.0

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

Tidak ada executable callback pada contract Public ini.

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
2. tidak ada ID duplikat;
3. provider ID sesuai tool bundle;
4. daftar ID yang dideklarasikan Tool sama dengan isi bundle;
5. permission/capability reference tersedia pada bundle atau registry yang sudah committed;
6. dependency Tool tersedia bila diwajibkan;
7. tidak ada state parsial bila validasi gagal.

## Lookup

Lookup hanya exact Stable ID. Nama/label mirip tidak boleh digunakan untuk koneksi otomatis.

## Failure codes

```text
CONTRACT_INVALID
DUPLICATE_ID
PROVIDER_MISMATCH
DEPENDENCY_MISSING
CAPABILITY_UNAVAILABLE
PERMISSION_REFERENCE_MISSING
DECLARATION_MISMATCH
```

Failure publication tidak boleh menghapus entry registry yang sebelumnya valid.

## Thread safety

Publication dan snapshot wajib thread-safe. Concurrent bundle publication tidak boleh menghasilkan partial registry state.

## Executable boundary

Contract ini menyimpan metadata saja. Ia tidak mempunyai API untuk:

- class loading;
- DEX/JAR/native loading;
- arbitrary reflection execution;
- Android permission grant;
- filesystem/network authority;
- Firebase/build/signing.

Runtime Private menentukan adapter/execution policy setelah Promotion Package lulus preflight.