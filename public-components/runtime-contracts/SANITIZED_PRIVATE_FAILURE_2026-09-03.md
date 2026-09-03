# Sanitized Private Integration Failure — 2026-09-03

## Safe identity

```text
PROJECT_ID = ToolBox
COMPONENT_ID = public.runtime-contracts
FAILURE_ID = PRIVATE_R6_DEPENDENCY_TRUST_HANDOFF_GAP
PRIVATE_CONTENT_INCLUDED = 0
PRIVATE_SOURCE_INCLUDED = 0
PRIVATE_PATH_INCLUDED = 0
PRIVATE_ARTIFACT_INCLUDED = 0
```

## Sanitized result

Private integration stopped fail-closed at the dependency/build-input trust gate before component regression execution.

The safe failure class is:

```text
DEPENDENCY_BUILD_INPUT_UNIVERSE_CHANGED
AND
DEPENDENCY_BUILD_INPUT_HASH_EVIDENCE_STALE
```

No Private source, asset, configuration, path, state, APK, secret, token, or internal dump is included in this report.

## Public corrective requirement

The Promotion Package must explicitly declare that a Private host integrating this component MUST:

1. treat introduction of the component/module and any host build-configuration change required to attach it as a build/dependency trust input change;
2. invalidate stale dependency/build-input hash evidence affected by that change;
3. refresh the host dependency/build-input universe and hashes using the host's own Private trust procedure before regression/build execution;
4. fail closed if the host cannot prove the refreshed trust state;
5. keep this component metadata-only with zero required external runtime dependencies;
6. preserve Java 11 bytecode compatibility and the component identity/contract version from the Promotion Package.

Public does not define, inspect, or mutate the Private host's actual trust manifest or internal file list. Those remain Private responsibilities.
