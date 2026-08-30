# ToolBox

ToolBox is a small, UI-independent, extensible kernel foundation targeting Android 11 / API 30 / `arm64-v8a`-compatible JVM bytecode.

## Kernel guarantees

- The kernel is the only authority that mutates module lifecycle state.
- Module descriptors are validated and snapshotted at installation time.
- Android API range, ABI, required capabilities, module API, and entry-point metadata are explicit compatibility contracts.
- Required dependencies are resolved deterministically before lifecycle execution.
- Module callbacks run outside registry locks.
- Services, capabilities, commands, and subscriptions are owned by a module scope and are cleaned automatically.
- Runtime installation is atomic: activation failure removes the failed registration and owned resources.
- Lifecycle failures are isolated and returned as structured `KernelResult` values.
- Health probing is explicit; `snapshot()` is passive and never invokes module code.
- Public API exposure is checked with Kotlin explicit API mode.
- Platform-specific loaders and storage implementations remain outside the pure Kotlin kernel.

## Module

- `toolbox-kernel`: public API/SPI plus the internal runtime.

See `ARCHITECTURE.md` and `ACCEPTANCE-TESTS.md` for the current foundation contract.
