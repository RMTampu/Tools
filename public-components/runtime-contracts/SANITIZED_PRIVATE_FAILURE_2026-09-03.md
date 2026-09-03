# Sanitized Private Integration Failure — 2026-09-03

## Safe identity

```text
PROJECT_ID = ToolBox
COMPONENT_ID = public.runtime-contracts
FAILURE_ID_1 = PRIVATE_R6_DEPENDENCY_TRUST_HANDOFF_GAP
FAILURE_ID_2 = PRIVATE_R6_TOOLCHAIN_ARTIFACT_SOURCE_MISMATCH
PRIVATE_CONTENT_INCLUDED = 0
PRIVATE_SOURCE_INCLUDED = 0
PRIVATE_PATH_INCLUDED = 0
PRIVATE_ARTIFACT_INCLUDED = 0
```

## Sanitized result 1 — dependency trust handoff

A Private integration attempt stopped fail-closed at the dependency/build-input trust gate before component regression execution.

Safe failure class:

```text
DEPENDENCY_BUILD_INPUT_UNIVERSE_CHANGED
AND
DEPENDENCY_BUILD_INPUT_HASH_EVIDENCE_STALE
```

## Sanitized result 2 — toolchain artifact source parity

A later Private integration attempt proved its refreshed dependency trust state, then stopped fail-closed during regression because the build-tool artifact source used by regression was not equivalent to the artifact source covered by trust generation.

Safe failure class:

```text
TRUST_GENERATION_ARTIFACT_SOURCE != REGRESSION_ARTIFACT_SOURCE
AND
UNBOUND_BUILD_TOOL_ARTIFACT_REQUESTED_DURING_REGRESSION
```

This report intentionally does not identify Private filenames, paths, dependency coordinates, artifact names, internal workflow details, source, assets, state, APKs, secrets, tokens, or dumps.

## Public corrective requirement

The Promotion Package must explicitly declare that a Private host integrating this component MUST:

1. treat introduction of the component/module and any host build-configuration change required to attach it as a build/dependency trust input change;
2. invalidate stale dependency/build-input hash evidence affected by that change;
3. refresh the host dependency/build-input universe and hashes using the host's own Private trust procedure before regression/build execution;
4. fail closed if the host cannot prove the refreshed trust state;
5. preserve one declared build-tool artifact source identity across trust generation and regression; or, if regression intentionally uses an alternate source, bind every alternate artifact identity into the Private trust state before regression;
6. fail closed if regression attempts to resolve a build-tool artifact that is not represented by the accepted Private trust state;
7. keep this component metadata-only with zero required external runtime dependencies;
8. preserve Java 11 bytecode compatibility and the component identity/contract version from the Promotion Package.

Public does not define, inspect, or mutate the Private host's actual trust manifest, internal file list, workflow configuration, repository paths, or concrete build-tool artifact identities. Those remain Private responsibilities.
