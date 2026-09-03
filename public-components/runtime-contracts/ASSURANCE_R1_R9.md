> Catatan aktif: aturan lintas repo lama sudah dihapus. ToolBox dikerjakan di repo publik utama; repo private hanya untuk secret, signing, build final, Firebase/final runtime test, dan release sensitif.

## 12. Active Public fault universe

Applicable fault classes for this component include:

text
PUBLIC_BOUNDARY = PASS
R1_R8_PREBUILD_SCOPE_CLASSIFICATION = PASS
PUBLIC_BUILD_TEST = PASS
FAILURE_BOUNDARY_PROPERTY_CONCURRENCY_TESTS = PASS
R6_PACKAGE_MUTATION = PASS
R9_PUBLIC_EVIDENCE_COMPLETENESS = PASS
PACKAGE_VALIDATION = PASS
READY_PRIVATE_BINDING = PASS
PUBLIC_JOB_AUTO_CLEANUP = REQUIRED
```

If any required status is absent, unknown, skipped, stale, or fails, `READY_PRIVATE` must not be emitted.
