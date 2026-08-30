package io.toolbox.kernel

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
