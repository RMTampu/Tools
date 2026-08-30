# ToolBox

ToolBox is the core extensible kernel for an Android 11 (API 30) arm64 tool workbench.

This repository starts with a small, UI-independent kernel. Engines and tools plug into the kernel through stable contracts instead of modifying the kernel foundation.

## Core goals

- Stable kernel lifecycle
- Dynamic module/engine registration
- Service and capability registries
- Command and event routing
- Health checks and failure isolation
- No Android permissions required by the kernel itself
- Android 11 / API 30 baseline

## Modules

- `toolbox-kernel`: pure Kotlin core contracts and runtime

UI, editors, engines, storage adapters, package/update handlers, and other tools are intended to be added as modules above this kernel.
