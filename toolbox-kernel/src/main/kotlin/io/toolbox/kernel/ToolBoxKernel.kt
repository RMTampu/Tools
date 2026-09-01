package io.toolbox.kernel

import java.util.concurrent.atomic.AtomicReference

class ToolBoxKernel(
    val config: KernelConfig = KernelConfig(),
    val ports: KernelPorts = KernelPorts()
) {
    val services = ServiceRegistry()
    val capabilities = CapabilityRegistry()
    val events = EventBus(ports.logger)
    val commands = CommandBus()
    val modules = ModuleRegistry()

    private val recoveredInitialState = recoverPersistedState()
    private val stateRef = AtomicReference(recoveredInitialState.first)
    private var recoveryStartAllowed = recoveredInitialState.second

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
        persistState(state)
    }

    @Synchronized
    fun install(module: ToolBoxModule): List<ModuleFailure> {
        val preflightFailure = preflight(module.descriptor, null)
        if (preflightFailure != null) return listOf(preflightFailure)
        return installAdmitted(module)
    }

    @Synchronized
    fun install(source: ModuleSource, loader: ModuleLoader): List<ModuleFailure> {
        val expectedDescriptor = source.descriptor
        val preflightFailure = preflight(expectedDescriptor, source)
        if (preflightFailure != null) return listOf(preflightFailure)

        val module = runCatching { loader.load(source) }
            .getOrElse {
                ports.logger.error("Failed to load module source ${source.id}", it)
                return listOf(ModuleFailure(source.id, "source-load", it))
            }

        if (module.descriptor != expectedDescriptor) {
            val error = IllegalStateException(
                "Loaded module descriptor does not match admitted source descriptor: ${source.id}"
            )
            ports.logger.error("Rejected descriptor mismatch for source ${source.id}", error)
            return listOf(ModuleFailure(source.id, "source-descriptor-mismatch", error))
        }

        return installAdmitted(module)
    }

    private fun preflight(descriptor: ModuleDescriptor, source: ModuleSource?): ModuleFailure? {
        val recoveryRegistration = state == KernelState.FAILED && recoveryStartAllowed
        check(state !in setOf(KernelState.STARTING, KernelState.STOPPING) && (state != KernelState.FAILED || recoveryRegistration)) {
            "Cannot install module while kernel state is $state"
        }

        val compatibility = ports.compatibilityPolicy.check(config, descriptor)
        if (!compatibility.compatible) {
            val error = IllegalArgumentException(compatibility.reason)
            ports.logger.warn("Rejected incompatible module ${descriptor.id}", error)
            events.publish(KernelEvent("kernel.module.rejected", config.name, descriptor))
            return ModuleFailure(descriptor.id, "compatibility", error)
        }

        val admission = ports.admissionPolicy.evaluate(descriptor, source)
        if (!admission.allowed) {
            val error = IllegalStateException(admission.reason)
            ports.logger.warn("Module admission rejected ${descriptor.id}", error)
            events.publish(KernelEvent("kernel.module.rejected", config.name, descriptor))
            return ModuleFailure(descriptor.id, "admission", error)
        }

        return null
    }

    private fun installAdmitted(module: ToolBoxModule): List<ModuleFailure> {
        val descriptor = module.descriptor
        val registrationFailure = runCatching { modules.install(module) }.exceptionOrNull()
        if (registrationFailure != null) {
            ports.logger.error("Failed to register module ${descriptor.id}", registrationFailure)
            return listOf(ModuleFailure(descriptor.id, "registration", registrationFailure))
        }

        if (state in setOf(KernelState.RUNNING, KernelState.DEGRADED)) {
            val activationFailures = modules.loadAndStart(descriptor.id, context)
            if (activationFailures.isNotEmpty()) {
                val rollbackFailures = modules.rollbackInstall(descriptor.id)
                val failures = activationFailures + rollbackFailures
                failures.forEach {
                    ports.logger.error("Module ${it.moduleId} failed during ${it.phase}", it.cause)
                }
                refreshOperationalState()
                return failures
            }
        }

        events.publish(
            KernelEvent(
                topic = "kernel.module.installed",
                source = config.name,
                payload = descriptor
            )
        )
        ports.logger.info("Installed module ${descriptor.id} ${descriptor.version}")
        return emptyList()
    }

    @Synchronized
    fun uninstall(moduleId: String): Boolean {
        check(state !in setOf(KernelState.STARTING, KernelState.STOPPING)) {
            "Cannot uninstall module while kernel state is $state"
        }

        val removed = runCatching { modules.uninstall(moduleId) }
            .getOrElse {
                ports.logger.error("Failed to uninstall module $moduleId", it)
                refreshOperationalState()
                return false
            }

        if (removed) {
            events.publish(KernelEvent("kernel.module.uninstalled", config.name, moduleId))
            ports.logger.info("Uninstalled module $moduleId")
            refreshOperationalState()
        }
        return removed
    }

    @Synchronized
    fun start(): List<ModuleFailure> {
        val canRecover = state == KernelState.FAILED && recoveryStartAllowed
        check(state in setOf(KernelState.NEW, KernelState.STOPPED) || canRecover) {
            "Kernel cannot start from state $state"
        }
        recoveryStartAllowed = false

        setState(KernelState.STARTING)
        events.publish(KernelEvent("kernel.starting", config.name))
        ports.logger.info("Starting ${config.name} kernel ${config.version}")

        val failures = mutableListOf<ModuleFailure>()
        failures += modules.loadAll(context)
        failures += modules.startAll()

        val unhealthy = modules.health().any {
            it.state == ModuleState.FAILED || (it.state == ModuleState.STARTED && !it.health.healthy)
        }
        setState(if (failures.isEmpty() && !unhealthy) KernelState.RUNNING else KernelState.DEGRADED)
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

    private fun recoverPersistedState(): Pair<KernelState, Boolean> {
        val raw = runCatching { ports.stateStore.get(KERNEL_STATE_KEY) }
            .onFailure { ports.logger.warn("Unable to read persisted kernel state", it) }
            .getOrNull()
            ?: return KernelState.NEW to false

        val persisted = runCatching { KernelState.valueOf(raw) }
            .getOrElse {
                ports.logger.warn("Invalid persisted kernel state: $raw", it)
                return KernelState.FAILED to true
            }

        return when (persisted) {
            KernelState.NEW -> KernelState.NEW to false
            KernelState.STOPPED -> KernelState.STOPPED to false
            else -> {
                ports.logger.warn("Detected unclean previous kernel state: $persisted")
                KernelState.FAILED to true
            }
        }
    }

    private fun persistState(newState: KernelState) {
        runCatching { ports.stateStore.put(KERNEL_STATE_KEY, newState.name) }
            .onFailure { ports.logger.warn("Unable to persist kernel state", it) }
    }

    private companion object {
        const val KERNEL_STATE_KEY = "kernel.state"
    }
}
