# ToolBox Kernel Architecture

## Scope

This document describes only the kernel foundation implemented in this repository. UI, Android host code, concrete dynamic-code loaders, storage implementations, and application features are outside this module.

## Authority boundary

`ToolBoxKernel` is the lifecycle authority. Internal registries are not exposed for direct mutation. Consumers can query module state, read services/capabilities, execute commands, and subscribe to events only through controlled public methods.

## Module lifecycle

A registered module can move through:

`REGISTERED -> LOADING -> LOADED -> STARTING -> STARTED -> STOPPING -> STOPPED -> STARTING`

Uninstall adds `UNLOADING`. Any lifecycle callback failure moves the module to `FAILED`. Failed load/start attempts release the module scope so retry starts from a clean managed-resource state.

Module callbacks are always executed outside registry structural locks.

## Module identity

A module descriptor is validated and copied at installation. Its ID and dependency set therefore cannot change underneath the registry even if a module exposes a computed descriptor property.

## Dependencies

Dependencies are represented by `ModuleDependency` rather than raw strings. Required dependencies participate in graph resolution and startup ordering. Optional missing dependencies are ignored. Cycles and missing required dependencies fail resolution before callbacks run.

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

The kernel emits Java 11 bytecode and contains no Android framework dependency. `KernelPorts` provides state, logging, execution, clock, compatibility, and admission extension points. Concrete Android loaders must remain outside this module and should verify integrity before loading external code.
