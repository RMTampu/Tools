# ToolBox

ToolBox is an extensible Android 11 / API 30 / `arm64-v8a` workbench built around a small UI-independent kernel and an Android host application.

## Foundation guarantees

- The kernel is the authority for module lifecycle state.
- Module descriptors are validated and snapshotted at admission time.
- Required dependencies and capability routes are resolved before activation.
- Module callbacks execute outside registry locks with timeout and ownership tracking.
- Services, capabilities, commands, and subscriptions are owned by module scope and cleaned automatically.
- Failed runtime activation is removed only when rollback cleanup is proven complete; dirty cleanup remains observable as failure/quarantine.
- Health probing is explicit; passive snapshots never invoke module code.
- Public kernel ABI is checked against a committed baseline.
- Kernel JVM output is verified for Android API 30 DEX compatibility in CI.
- Platform-specific storage, loaders, UI, and application behavior remain above the pure Kotlin kernel boundary.

## Modules

- `toolbox-kernel`: pure Kotlin core contracts and runtime.
- `toolbox-app`: Android 11 host/application integration.

See `ARCHITECTURE.md`, `ACCEPTANCE-TESTS.md`, `AGENTS.md`, and the active safety-process documents for the current contracts and verification rules.
