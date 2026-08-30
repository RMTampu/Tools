package io.toolbox.kernel

import java.util.concurrent.atomic.AtomicReference

class ToolBoxKernel(
    val config: KernelConfig = KernelConfig(),
    val ports: KernelPorts = KernelPorts()
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
        commands = commands,
        ports = ports
    )

    init {
        persistState(KernelState.NEW)
    }

    @Synchronized
    fun install(module: ToolBoxModule): List<ModuleFailure> = installInternal(module, null)

    @Synchronized
    fun install(source: ModuleSource, loader: ModuleLoader): List<ModuleFailure> {
        val module = runCatching { loader.load(source) }
            .getOrElse {
                ports.logger.error("Failed to load module source ${source.id}", it)
                return listOf(ModuleFailure(source.id, "source-load", it))
            }
        return installInternal(module, source)
    }

    private fun installInternal(module: ToolBoxModule, source: ModuleSource?): List<ModuleFailure> {
        check(state !in setOf(KernelState.STARTING, KernelState.STOPPING, KernelState.FAILED)) {
            "Cannot install module while kernel state is $state"
        }

        val descriptor = module.descriptor
        val compatibility = ports.compatibilityPolicy.check(config, descriptor)
        if (!compatibility.compatible) {
            val error = IllegalArgumentException(compatibility.reason)
            ports.logger.warn("Rejected incompatible module ${descriptor.id}", error)
            events.publish(KernelEvent("kernel.module.rejected", config.name, descriptor))
            return listOf(ModuleFailure(descriptor.id, "compatibility", error))
        }

        val admission = ports.admissionPolicy.evaluate(descriptor, source)
        if (!admission.allowed) {
            val error = IllegalStateException(admission.reason)
            ports.logger.warn("Module admission rejected ${descriptor.id}", error)
            events.publish(KernelEvent("kernel.module.rejected", config.name, descriptor))
            return listOf(ModuleFailure(descriptor.id, "admission", error))
        }

        modules.install(module)
        val failures = if (state in setOf(KernelState.RUNNING, KernelState.DEGRADED)) {
            modules.loadAndStart(descriptor.id, context)
        } else {
            emptyList()
        }

        if (failures.isNotEmpty() && state in setOf(KernelState.RUNNING, KernelState.DEGRADED)) {
            setState(KernelState.DEGRADED)
        }

        events.publish(
            KernelEvent(
                topic = "kernel.module.installed",
                source = config.name,
                payload = descriptor
            )
        )
        ports.logger.info("Installed module ${descriptor.id} ${descriptor.version}")
        return failures
    }

    @Synchronized
    fun uninstall(moduleId: String): Boolean {
        check(state !in setOf(KernelState.STARTING, KernelState.STOPPING)) {
            "Cannot uninstall module while kernel state is $state"
        }
        val removed = modules.uninstall(moduleId)
        if (removed) {
            events.publish(KernelEvent("kernel.module.uninstalled", config.name, moduleId))
            ports.logger.info("Uninstalled module $moduleId")
            refreshOperationalState()
        }
        return removed
    }

    @Synchronized
    fun start(): List<ModuleFailure> {
        check(state in setOf(KernelState.NEW, KernelState.STOPPED)) {
            "Kernel cannot start from state $state"
        }

        setState(KernelState.STARTING)
        events.publish(KernelEvent("kernel.starting", config.name))
        ports.logger.info("Starting ${config.name} kernel ${config.version}")

        val failures = mutableListOf<ModuleFailure>()
        failures += modules.loadAll(context)
        failures += modules.startAll()

        setState(if (failures.isEmpty()) KernelState.RUNNING else KernelState.DEGRADED)
        failures.forEach { ports.logger.error("Module ${it.moduleId} failed during ${it.phase}", it.cause) }
        events.publish(KernelEvent("kernel.started", config.name, failures.toList()))
        return failures
    }

    @Synchronized
    fun stop(): List<ModuleFailure> {
        if (state in setOf(KernelState.NEW, KernelState.STOPPED)) {
            setState(KernelState.STOPPED)
            return emptyList()
        }
        check(state in setOf(KernelState.RUNNING, KernelState.DEGRADED, KernelState.FAILED)) {
            "Kernel cannot stop from state $state"
        }

        setState(KernelState.STOPPING)
        events.publish(KernelEvent("kernel.stopping", config.name))

        val failures = modules.stopAll()
        setState(if (failures.isEmpty()) KernelState.STOPPED else KernelState.FAILED)
        failures.forEach { ports.logger.error("Module ${it.moduleId} failed during ${it.phase}", it.cause) }
        events.publish(KernelEvent("kernel.stopped", config.name, failures.toList()))
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
        setState(if (unhealthy) KernelState.DEGRADED else KernelState.RUNNING)
    }

    private fun setState(newState: KernelState) {
        stateRef.set(newState)
        persistState(newState)
    }

    private fun persistState(newState: KernelState) {
        runCatching { ports.stateStore.put("kernel.state", newState.name) }
            .onFailure { ports.logger.warn("Unable to persist kernel state", it) }
    }
}
