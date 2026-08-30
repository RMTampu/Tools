# ToolBox Kernel Architecture

## Scope

This document describes only the kernel foundation implemented in this repository. UI, Android host code, concrete dynamic-code loaders, storage implementations, native packages, and application features are outside this module.

## Authority boundary

`ToolBoxKernel` is the lifecycle authority. Internal registries are not exposed for direct mutation. Consumers can query module state, read services/capabilities, execute commands, and subscribe to events only through controlled public methods.

## Module lifecycle

A registered module can move through:

`REGISTERED -> LOADING -> LOADED -> STARTING -> STARTED -> STOPPING -> STOPPED -> STARTING`

Uninstall adds `UNLOADING`. Any lifecycle callback failure moves the module to `FAILED`. Failed load/start attempts release the module scope so retry starts from a clean managed-resource state.

Module callbacks are always executed outside registry structural locks.

## Module identity and compatibility metadata

A module descriptor is validated and copied at installation. Its identity and compatibility declaration therefore cannot change underneath the registry even if a module exposes a computed descriptor property.

The descriptor carries the kernel-level metadata needed to reject incompatible modules before activation:

- module ID and version
- module API version
- minimum Android API
- optional maximum Android API
- supported ABIs
- required capabilities
- entry point
- module dependencies

`KernelRuntimeEnvironment` carries the actual Android API and ABI reported by the host. The default compatibility policy requires that runtime environment to match the configured distribution target and verifies that the module supports both. The distribution baseline is Android 11 / API 30 / `arm64-v8a`.

## External module preflight

`ModuleLoader` has two distinct phases:

1. `inspect(source)` reads metadata only and must not execute module code.
2. `load(source, descriptor)` is called only after source identity, descriptor validity, compatibility, and admission checks succeed.

The kernel then verifies that the loaded module exposes the same descriptor that passed preflight. A platform loader remains responsible for checking package/content integrity before executing externally supplied code; the kernel guarantees the ordering boundary needed for that implementation.

## Dependencies and capabilities

Dependencies are represented by `ModuleDependency` rather than raw strings. Required dependencies participate in graph resolution and startup ordering. Optional missing dependencies are ignored. Cycles and missing required dependencies fail resolution before callbacks run.

Required capabilities are checked after required dependency modules have loaded and before the dependent module's `onLoad`. Providers should therefore register required capabilities during `onLoad` and be declared as dependencies when deterministic ordering is required.

## Module scope and ownership

Each loaded module receives a `KernelContext` containing owner-bound facades:

- `ModuleServices`
- `ModuleCapabilities`
- `ModuleCommands`
- `ModuleEvents`

Every registration is tagged with the owning module ID. Replacing another module's resource is rejected. Closing the scope removes all resources and subscriptions owned by that module.

## Failure model

Public lifecycle operations return `KernelResult<T>` with `KernelError` and `ModuleFailure` details rather than mixing expected operation failures with thrown exceptions. Programmer errors inside callbacks are isolated at lifecycle boundaries.

## Health model

`healthCheck()` is invoked only by explicit health probing. `snapshot()` returns cached health and does not execute module code. A failed/unhealthy started module can move an operational kernel from `RUNNING` to `DEGRADED`.

## State store semantics

The state store records the last observed kernel state. On construction, the previous persisted state is read before `NEW` is written. This preserves diagnostic information about an earlier lifecycle rather than overwriting it before inspection. Full process-resume semantics belong to a concrete host/runtime layer and are not implied by this store.

## Platform boundary

The kernel emits Java 11 bytecode and contains no Android framework dependency. `KernelPorts` provides state, logging, execution, clock, runtime-environment, compatibility, and admission extension points. A concrete Android host supplies the real API/ABI values. A concrete external-code loader must inspect metadata without executing module code and verify payload integrity before its `load` phase executes code.
