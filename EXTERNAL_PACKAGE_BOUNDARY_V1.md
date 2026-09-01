# EXTERNAL_PACKAGE_BOUNDARY_V1.md

Status: **FROZEN v1**

This document defines what external/project/update packages may and may not do in ToolBox. The boundary is fail-closed.

## 1. Default rule

External content is treated as **data**, not trusted executable code.

A package may describe behavior using contracts already implemented by the trusted ToolBox APK, but it may not create new executable primitives by supplying arbitrary code.

## 2. Allowed package content

A validated package may contain declarative material such as:

- UI definitions;
- component instances/configuration;
- assets;
- templates;
- design tokens/styles;
- typed bindings;
- declarative logic/workflow graphs;
- rules;
- localization data;
- data definitions;
- declarative migrations supported by the installed runtime;
- repair/update instructions supported by installed capabilities;
- compatibility metadata;
- hashes/signature metadata;
- dependency metadata.

All referenced operations must resolve to capabilities/actions/contracts already trusted and installed in the ToolBox runtime.

## 3. Forbidden normal-package content

The normal external package path must not execute or dynamically load arbitrary:

- DEX;
- JAR/class bytecode;
- native `.so` libraries;
- shell commands/scripts;
- downloaded JavaScript or other general-purpose scripts used as a code-execution escape hatch;
- reflection-based arbitrary class entry points supplied by the package;
- process injection or executable payloads.

Renaming or wrapping executable content does not make it declarative.

## 4. Capabilities that require an APK/build update

The following are outside the normal declarative package lane and require a trusted APK/build update path:

- a new executable primitive;
- a new native engine;
- a new Android component that must exist in the APK/manifest;
- a new base permission or platform integration that requires APK changes;
- a new trust root;
- a new recovery root or privileged recovery mechanism;
- executable code that is not already part of the trusted installed runtime.

## 5. Mandatory package manifest

A package must have a machine-readable manifest containing enough information to validate exact content. At minimum, where applicable:

- package ID;
- package version;
- package type;
- target project/app identity;
- schema/contract version;
- compatible ToolBox/engine contract range;
- required capabilities;
- declared permissions/capability effects;
- dependency declarations;
- file inventory;
- file/content hashes;
- migration/repair intent;
- package content hash;
- signature/trust metadata when the package uses a trusted-signed lane.

Trust applies to exact content. Content changes invalidate previous hash/signature trust.

## 6. Intake pipeline

External package intake must follow:

```text
RECEIVE
-> STAGING
-> PATH/CANONICALIZATION CHECK
-> SIZE/ENTRY/NESTING BUDGET CHECK
-> MANIFEST/SCHEMA CHECK
-> FILE HASH/INTEGRITY CHECK
-> CONTENT-TYPE CHECK
-> ID/REFERENCE CHECK
-> CAPABILITY/CONTRACT CHECK
-> TARGET/COMPATIBILITY CHECK
-> TRUST/SIGNATURE CHECK WHEN REQUIRED
-> DRY-RUN/PREVIEW WHEN APPLICABLE
-> READY TO APPLY OR REJECT/QUARANTINE
```

A package may not write directly into the active committed project/app state before this pipeline succeeds.

## 7. Archive/input safety

Import/package readers must defend against at least:

- path traversal;
- absolute-path escape;
- canonical-path mismatch;
- excessive entry count;
- excessive uncompressed size;
- decompression bombs;
- excessive nesting/recursion;
- unsupported content types;
- malformed schemas;
- duplicate/conflicting stable IDs;
- invalid or unresolved mandatory references.

Validation is performed before committed-state mutation.

## 8. Apply transaction

A package that changes committed state must use a transaction/journal boundary.

Conceptual sequence:

```text
KNOWN GOOD
-> STAGE
-> VALIDATE
-> PREPARE TRANSACTION
-> APPLY TO WORKING/CANDIDATE STATE
-> VERIFY
-> HEALTH CHECK
-> ATOMIC COMMIT
```

On failure:

```text
FAIL
-> ABORT/ROLLBACK
-> RESTORE KNOWN GOOD WHEN NEEDED
-> QUARANTINE INVALID INPUT WHEN NEEDED
-> DIAGNOSTIC
```

Mixed old/new committed state is not acceptable.

## 9. Declarative migration rule

A package may request only migrations that the installed trusted runtime explicitly supports.

Permanent migrations must be:

- explicit;
- versioned;
- validated;
- transactional;
- recoverable/rollback-aware where applicable;
- bounded in resource use.

A package must not smuggle arbitrary execution through a "migration" field.

## 10. Project Store external-file integrity

For a user-visible ToolBox project, `project.manifest` is required.

At minimum it records:

- project ID;
- schema version;
- project revision/status;
- important file inventory;
- asset/reference inventory or reference to generated inventory;
- integrity hashes/checksums for protected/important content;
- manifest revision/fingerprint.

When a project is opened or an external change is detected, ToolBox validates incrementally:

- required files exist;
- manifest identity matches the project;
- changed-file hashes;
- schema and stable IDs;
- affected references/dependency graph;
- used assets;
- revision consistency.

Possible states must distinguish at least:

```text
PROJECT_OK
EXTERNAL_CHANGE_DETECTED
RESOURCE_MISSING
BROKEN_REFERENCE
REVISION_MISMATCH
PROJECT_CORRUPT
ACCESS_LOST
```

External changes are not silently trusted and are not silently discarded.

## 11. Integrity vs authenticity

A local manifest/hash detects accidental or external modification but is not sufficient authenticity proof against an actor able to modify both file and hash.

Therefore:

- project manifest/hash = integrity/change detection;
- signature/trust root = authenticity for packages that require authenticity.

These concepts must not be conflated.

## 12. Secret boundary

External/project packages must not contain ToolBox secrets such as:

- GitHub tokens;
- signing private keys;
- keystore passwords;
- production credentials;
- private trust material.

Packages may reference logical secret requirements only. Secret resolution occurs through the approved private/secure environment.

## 13. Permission boundary

A package cannot silently elevate permissions.

Permission needs must be derived from declared capabilities/actions and must remain compatible with the installed trusted APK/runtime. If a requested behavior needs a permission or Android component not supported by the installed base, the package is incompatible and must not bypass the boundary.

## 14. Engine boundary

The Engine Host may activate only engine implementations that are already part of the trusted installed runtime/build.

External packages may select/configure compatible installed engines through stable contract IDs. They may not provide a runtime factory/class/native binary for the host to execute.

## 15. Trust state

A trusted/tested package status is bound to:

- package ID;
- version;
- target compatibility;
- exact content hash;
- signer/trust identity when applicable.

Any content change creates a new unverified content identity.

Trusted status never bypasses mandatory minimum compatibility, manifest, and declared-resource validation.

## 16. Fail-closed rule

If package identity, integrity, compatibility, required capability, mandatory reference, trust requirement, transaction precondition, or target identity cannot be proven, the result is not PASS.

The package remains staged/rejected/quarantined and must not mutate the committed active baseline.
