package io.toolbox.kernel

interface ToolBoxModule {
    val descriptor: ModuleDescriptor

    fun onLoad(context: KernelContext) = Unit

    fun onStart() = Unit

    fun onStop() = Unit

    fun healthCheck(): HealthStatus = HealthStatus.ok()
}

data class KernelContext(
    val config: KernelConfig,
    val services: ServiceRegistry,
    val capabilities: CapabilityRegistry,
    val events: EventBus,
    val commands: CommandBus,
    val ports: KernelPorts
)
