# public.runtime-contracts — R6/R9 Private Handoff Assurance Addendum

## 1. Scope

This addendum applies only to the safe Public-to-Private Promotion Package handoff for:

```text
PROJECT_ID = ToolBox
COMPONENT_ID = public.runtime-contracts
COMPONENT_VERSION = 0.1.0
CONTRACT_ID = toolbox.runtime.metadata
CONTRACT_VERSION = 1.0.0
PUBLIC_SCOPE = sanitized handoff contract only
PRIVATE_CONTENT_INCLUDED = 0
PRIVATE_EXECUTION_THROUGH_PUBLIC = 0
```

It does not inspect, model, reproduce, or execute Private source, workflows, build inputs, dependency coordinates, paths, artifacts, or trust manifests.

## 2. Sanitized fault expansion

Private integration feedback introduced two safe R6 handoff fault classes:

```text
DEPENDENCY_BUILD_INPUT_TRUST_STALE
TOOLCHAIN_ARTIFACT_SOURCE_PHASE_MISMATCH
```

The second class means that trust generation and regression may each be internally valid while still disagreeing about where a required build-tool artifact comes from. A green trust-generation phase therefore cannot, by itself, prove regression artifact-source closure.

## 3. R6 ownership

Applicable Public handoff methods:

```text
R6-M01 CLOSED BUILD-INPUT UNIVERSE
R6-M02 TOOLCHAIN / VERSION PINNING
R6-M04 DEPENDENCY INTEGRITY VERIFICATION
R6-M06 HERMETICITY / EXTERNAL INFLUENCE CLOSURE
R6-M21 CI WORKFLOW INTEGRITY & REQUIRED GATE BINDING
R6-M23 CHANGE-IMPACT INVALIDATION
```

Public handoff requirements:

1. The Private host owns its actual dependency/build-tool trust universe.
2. Adding this component invalidates affected stale host trust evidence.
3. The host must refresh accepted trust state before regression/build.
4. Trust generation and regression must use the same declared build-tool artifact source identity.
5. If regression intentionally uses an alternate build-tool artifact source, every alternate artifact identity must be incorporated into the accepted Private trust state before regression.
6. An unbound build-tool artifact request during regression must fail closed.
7. Public must not know the Private file list, coordinates, artifact names, internal workflow paths, or trust manifest.

## 4. R9 handoff traceability

Applicable handoff-completeness methods:

```text
R9-M01 CLOSED FAILURE UNIVERSE
R9-M02 REQUIREMENT / RISK / FAULT / METHOD / EVIDENCE TRACEABILITY
R9-M17 EVIDENCE PROVENANCE & BINDING
R9-M18 EVIDENCE FRESHNESS / CHANGE-IMPACT CLOSURE
R9-M19 FAIL-CLOSED UNKNOWN / SKIPPED / INCONCLUSIVE HANDLING
R9-M20 NEGATIVE ASSURANCE / DEFEATER ANALYSIS
R9-M21 ASSURANCE CASE / CLAIM-EVIDENCE GRAPH
```

Trace rows:

| Requirement / risk | Fault class | Prevention / proof | Public evidence | Claim |
|---|---|---|---|---|
| Host trust evidence can become stale after integration inputs change | `DEPENDENCY_BUILD_INPUT_TRUST_STALE` | invalidate + refresh before regression | `PRIVATE_INTEGRATION_REQUIREMENTS.json` + executable handoff validator | `PRIVATE_INTEGRATION_HANDOFF_CONTRACT` |
| Trust-generation and regression may resolve build tools through different artifact sources | `TOOLCHAIN_ARTIFACT_SOURCE_PHASE_MISMATCH` | phase source parity, or pre-bind every alternate artifact identity | sanitized failure report + executable handoff validator | `PRIVATE_INTEGRATION_HANDOFF_CONTRACT` |
| Public could accidentally encode Private implementation details while fixing the handoff | `PRIVATE_BOUNDARY_DISCLOSURE` | forbidden-detail scan + Public-only static requirements | executable handoff validator | `PRIVATE_CONTENT_INCLUDED=0` |

## 5. Defeaters

```text
D-H1: dependency trust PASS is mistaken for regression artifact-source closure
RESOLUTION: explicit phase-parity requirement.

D-H2: alternate regression artifact source is introduced after trust generation
RESOLUTION: alternate artifacts must be bound before regression or regression fails closed.

D-H3: Public fix attempts to encode Private coordinates/paths/configuration
RESOLUTION: Public requirements remain mechanism-level; validator rejects known Private-detail classes.
```

## 6. Handoff closure

This addendum is PASS only when the current Public workflow proves:

```text
SANITIZED_FAILURE_REPORT_PRESENT = PASS
PRIVATE_INTEGRATION_REQUIREMENTS_PRESENT = PASS
TOOLCHAIN_ARTIFACT_SOURCE_PARITY_REQUIRED = PASS
ALTERNATE_ARTIFACT_PRIVATE_TRUST_BINDING_REQUIRED = PASS
UNBOUND_REGRESSION_ARTIFACT_FAIL_CLOSED = PASS
R6_HANDOFF_ASSURANCE = PASS
R9_HANDOFF_COMPLETENESS = PASS
PRIVATE_CONTENT_INCLUDED = 0
```

This is a Public handoff assurance claim only. It is not a claim that any Private integration, build, APK, signing, Android runtime, Firebase test, or final application state has passed.
