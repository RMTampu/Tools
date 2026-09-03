> Catatan aktif: aturan lintas repo lama sudah dihapus. ToolBox dikerjakan di repo publik utama; repo private hanya untuk secret, signing, build final, Firebase/final runtime test, dan release sensitif.

## 6. PASS Formula

`APP_SAFE_R7_PASS` hanya jika:

```text
NATIVE_COMPONENT_UNKNOWN = 0
UNPROVEN_ARM64_ABI = 0
UNRESOLVED_NATIVE_DEPENDENCY = 0
JNI_CONTRACT_UNKNOWN = 0
UNRESOLVED_SANITIZER_REQUIRED_FINDING = 0
PLUGIN_CONTRACT_UNKNOWN = 0
UNBOUNDED_REQUIRED_REFLECTION = 0
UNVERIFIED_DYNAMIC_CODE = 0
THIRD_PARTY_USED_API_UNQUALIFIED = 0
NATIVE_PLUGIN_FAULT_ESCAPE = 0
STALE_EVIDENCE = 0
```

`UNPROVEN_ARM64_ABI = 0` minimal membutuhkan proof bahwa artifact/contract ARM64 valid. Jika final claim juga memerlukan actual ARM64 runtime behavior, structural ABI proof saja tidak cukup; target-specific runtime witness harus ditutup melalui user-authorized Firebase Final Gate di Private.
