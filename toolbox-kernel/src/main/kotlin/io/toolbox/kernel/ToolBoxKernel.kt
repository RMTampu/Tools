package io.toolbox.kernel

import java.util.concurrent.atomic.AtomicReference

class ToolBoxKernel(
    val config: KernelConfig = KernelConfig()
) {
    val services = ServiceRegistry()
    val capabilities = CapabilityRegistry()
    val events = EventBus()
    val commands = CommandBus()
    val modules = ModuleRegistry()

    private val stateRef = AtomicReference(KernelState.NEW)

    val state: KernelState get() = stateRef.get()

    private val context = KernelContext(
        config = config,
        services = services,
        capabilities = capabilities,
        events = events,
        commands = commands
    )

    @Synchronized
    fun install(module: ToolBoxModule): List<ModuleFailure> {
        check(state !in setOf(KernelState.STARTING, KernelState.STOPPING, KernelState.FAILED)) {
            "Cannot install module while kernel state is $state"
        }

        modules.install(module)
        val failures = if (state in setOf(KernelState.RUNNING, KernelState.DEGRADED)) {
            modules.loadAndStart(module.descriptor.id, context)
        } else {
            emptyList()
        }

        if (failures.isNotEmpty() && state in setOf(KernelState.RUNNING, KernelState.DEGRADED)) {
            stateRef.set(KernelState.DEGRADED)
        }

        events.publish(
            KernelEvent(
                topic = "kernel.module.installed",
                source = config.name,
                payload = module.descriptor
            )
        )
        return failures
    }

    @Synchronized
    fun uninstall(moduleId: String): Boolean {
        check(state !in setOf(KernelState.STARTING, KernelState.STOPPING)) {
            "Cannot uninstall module while kernel state is $state"
        }
        val removed = modules.uninstall(moduleId)
        if (removed) {
            events.publish(
                KernelEvent(
                    topic = "kernel.module.uninstalled",
                    source = config.name,
                    payload = moduleId
                )
            )
            refreshOperationalState()
        }
        return removed
    }

    @Synchronized
    fun start(): List<ModuleFailure> {
        check(state in setOf(KernelState.NEW, KernelState.STOPPED)) {
            "Kernel cannot start from state $state"
        }

        stateRef.set(KernelState.STARTING)
        events.publish(KernelEvent("kernel.starting", config.name))

        val failures = mutableListOf<ModuleFailure>()
        failures += modules.loadAll(context)
        failures += modules.startAll()

        stateRef.set(if (failures.isEmpty()) KernelState.RUNNING else KernelState.DEGRADED)
        events.publish(
            KernelEvent(
                topic = "kernel.started",
                source = config.name,
                payload = failures.toList()
            )
        )
        return failures
    }

    @Synchronized
    fun stop(): List<ModuleFailure> {
        if (state in setOf(KernelState.NEW, KernelState.STOPPED)) {
            stateRef.set(KernelState.STOPPED)
            return emptyList()
        }
        check(state in setOf(KernelState.RUNNING, KernelState.DEGRADED, KernelState.FAILED)) {
            "Kernel cannot stop from state $state"
        }

        stateRef.set(KernelState.STOPPING)
        events.publish(KernelEvent("kernel.stopping", config.name))

        val failures = modules.stopAll()
        stateRef.set(if (failures.isEmpty()) KernelState.STOPPED else KernelState.FAILED)
        events.publish(
            KernelEvent(
                topic = "kernel.stopped",
                source = config.name,
                payload = failures.toList()
            )
        )
        return failures
    }

    fun snapshot(): KernelSnapshot = KernelSnapshot(
        config = config,
        state = state,
        modules = modules.health(),
        registeredServices = services.size,
        registeredCapabilities = capabilities.size,
        registeredCommands = commands.size
    )

    fun isOperational(): Boolean = state in setOf(KernelState.RUNNING, KernelState.DEGRADED)

    @Synchronized
    private fun refreshOperationalState() {
        if (state !in setOf(KernelState.RUNNING, KernelState.DEGRADED)) return
        val unhealthy = modules.health().any {
            it.state == ModuleState.FAILED || (it.state == ModuleState.STARTED && !it.health.healthy)
        }
        stateRef.set(if (unhealthy) KernelState.DEGRADED else KernelState.RUNNING)
    }
}
