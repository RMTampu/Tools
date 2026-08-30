package io.toolbox.kernel

/**
 * Kernel module SPI.
 *
 * Lifecycle contract:
 * - onLoad receives a generation-scoped context and may allocate/register load resources.
 * - onStart is called only after all hard dependencies/providers are STARTED.
 * - if onStart fails, onStop is not called; the module must clean partial start-only work before throwing.
 * - onStop must stop module-owned work and return in a timely manner.
 * - onUnload releases resources created by onLoad.
 * - a KernelContext becomes invalid after its activation is stopped, fails, times out, or is unloaded.
 * - callbacks may be interrupted when their configured deadline expires.
 */
public interface ToolBoxModule {
    public val descriptor: ModuleDescriptor

    public fun onLoad(context: KernelContext): Unit = Unit
    public fun onStart(): Unit = Unit
    public fun onStop(): Unit = Unit
    public fun onUnload(): Unit = Unit
    public fun healthCheck(): HealthStatus = HealthStatus.ok()
}

public class KernelContext internal constructor(
    public val config: KernelConfig,
    public val moduleId: String,
    public val services: ModuleServices,
    public val capabilities: ModuleCapabilities,
    public val events: ModuleEvents,
    public val commands: ModuleCommands,
    public val logger: KernelLogger
)
