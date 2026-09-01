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
        if (!persistState(state)) {
            stateRef.set(KernelState.FAILED)
            recoveryStartAllowed = true
        }
    }

    @Synchronized
    fun install(module: ToolBoxModule): List<ModuleFailure> {
        val admittedDescriptor = runCatching { module.descriptor }
            .getOrElse {
                safeError("Failed to read module descriptor", it)
                return listOf(ModuleFailure("unknown", "descriptor", it))
            }
        val preflightFailure = preflight(admittedDescriptor, null)
        if (preflightFailure != null) return listOf(preflightFailure)

        val confirmedDescriptor = runCatching { module.descriptor }
            .getOrElse {
                safeError("Failed to re-read module descriptor ${admittedDescriptor.id}", it)
                return listOf(ModuleFailure(admittedDescriptor.id, "compatibility", it))
            }
        if (confirmedDescriptor != admittedDescriptor) {
            val error = IllegalStateException(
                "Module descriptor changed during compatibility preflight: ${admittedDescriptor.id}"
            )
            safeWarn("Rejected unstable module descriptor ${admittedDescriptor.id}", error)
            events.publish(KernelEvent("kernel.module.rejected", config.name, admittedDescriptor))
            return listOf(ModuleFailure(admittedDescriptor.id, "compatibility", error))
        }

        return installAdmitted(module, admittedDescriptor)
    }

    @Synchronized
    fun install(source: ModuleSource, loader: ModuleLoader): List<ModuleFailure> {
        val expectedDescriptor = source.descriptor
        val preflightFailure = preflight(expectedDescriptor, source)
        if (preflightFailure != null) return listOf(preflightFailure)

        val module = runCatching { loader.load(source) }
            .getOrElse {
                safeError("Failed to load module source ${source.id}", it)
                return listOf(ModuleFailure(source.id, "source-load", it))
            }

        val loadedDescriptor = runCatching { module.descriptor }
            .getOrElse {
                safeError("Failed to read loaded module descriptor ${source.id}", it)
                return listOf(ModuleFailure(source.id, "source-descriptor", it))
            }
        if (loadedDescriptor != expectedDescriptor) {
            val error = IllegalStateException(
                "Loaded module descriptor does not match admitted source descriptor: ${source.id}"
            )
            safeError("Rejected descriptor mismatch for source ${source.id}", error)
            return listOf(ModuleFailure(source.id, "source-descriptor-mismatch", error))
        }

        return installAdmitted(module, expectedDescriptor)
    }

    private fun preflight(descriptor: ModuleDescriptor, source: ModuleSource?): ModuleFailure? {
        val recoveryRegistration = state == KernelState.FAILED && recoveryStartAllowed
        check(state !in setOf(KernelState.STARTING, KernelState.STOPPING) && (state != KernelState.FAILED || recoveryRegistration)) {
            "Cannot install module while kernel state is $state"
        }

        val compatibility = ports.compatibilityPolicy.check(config, descriptor)
        if (!compatibility.compatible) {
            val error = IllegalArgumentException(compatibility.reason)
            safeWarn("Rejected incompatible module ${descriptor.id}", error)
            events.publish(KernelEvent("kernel.module.rejected", config.name, descriptor))
            return ModuleFailure(descriptor.id, "compatibility", error)
        }

        val admission = ports.admissionPolicy.evaluate(descriptor, source)
        if (!admission.allowed) {
            val error = IllegalStateException(admission.reason)
            safeWarn("Module admission rejected ${descriptor.id}", error)
            events.publish(KernelEvent("kernel.module.rejected", config.name, descriptor))
            return ModuleFailure(descriptor.id, "admission", error)
        }

        return null
    }

    private fun installAdmitted(module: ToolBoxModule, descriptor: ModuleDescriptor): List<ModuleFailure> {
        val registrationFailure = runCatching { modules.install(module, descriptor) }.exceptionOrNull()
        if (registrationFailure != null) {
            safeError("Failed to register module ${descriptor.id}", registrationFailure)
            return listOf(ModuleFailure(descriptor.id, "registration", registrationFailure))
        }

        if (state in setOf(KernelState.RUNNING, KernelState.DEGRADED)) {
            val registryCheckpoint = context.captureRegistryCheckpoint()
            val activationFailures = modules.loadAndStart(descriptor.id, context)
            if (activationFailures.isNotEmpty()) {
                val rollbackFailures = modules.rollbackInstall(descriptor.id)
                val registryRollbackFailure = runCatching {
                    context.restoreRegistryCheckpoint(registryCheckpoint)
                }.exceptionOrNull()
                val failures = buildList {
                    addAll(activationFailures)
                    addAll(rollbackFailures)
                    if (registryRollbackFailure != null) {
                        add(ModuleFailure(descriptor.id, "registry-rollback", registryRollbackFailure))
                    }
                }
                failures.forEach {
                    safeError("Module ${it.moduleId} failed during ${it.phase}", it.cause)
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
        safeInfo("Installed module ${descriptor.id} ${descriptor.version}")
        return emptyList()
    }

    @Synchronized
    fun uninstall(moduleId: String): Boolean {
        check(state !in setOf(KernelState.STARTING, KernelState.STOPPING)) {
            "Cannot uninstall module while kernel state is $state"
        }

        val removed = runCatching { modules.uninstall(moduleId) }
            .getOrElse {
                safeError("Failed to uninstall module $moduleId", it)
                refreshOperationalState()
                return false
            }

        if (removed) {
            events.publish(KernelEvent("kernel.module.uninstalled", config.name, moduleId))
            safeInfo("Uninstalled module $moduleId")
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

        if (!setState(KernelState.STARTING)) {
            val failure = statePersistenceFailure(KernelState.STARTING)
            safeError("Kernel start blocked because STARTING state could not be persisted", failure.cause)
            return listOf(failure)
        }
        events.publish(KernelEvent("kernel.starting", config.name))
        safeInfo("Starting ${config.name} kernel ${config.version}")

        val failures = mutableListOf<ModuleFailure>()
        failures += modules.loadAll(context)
        failures += modules.startAll()

        val unhealthy = modules.health().any {
            it.state == ModuleState.FAILED || (it.state == ModuleState.STARTED && !it.health.healthy)
        }
        val targetState = if (failures.isEmpty() && !unhealthy) KernelState.RUNNING else KernelState.DEGRADED
        if (!setState(targetState)) {
            failures += statePersistenceFailure(targetState)
        }
        failures.forEach { safeError("Module ${it.moduleId} failed during ${it.phase}", it.cause) }
        events.publish(KernelEvent("kernel.started", config.name, failures.toList()))
        return failures
    }

    @Synchronized
    fun stop(): List<ModuleFailure> {
        if (state in setOf(KernelState.NEW, KernelState.STOPPED)) {
            if (setState(KernelState.STOPPED)) {
                recoveryStartAllowed = false
                return emptyList()
            }
            return listOf(statePersistenceFailure(KernelState.STOPPED))
        }
        check(state in setOf(KernelState.RUNNING, KernelState.DEGRADED, KernelState.FAILED)) {
            "Kernel cannot stop from state $state"
        }

        val failures = mutableListOf<ModuleFailure>()
        if (!setState(KernelState.STOPPING)) {
            failures += statePersistenceFailure(KernelState.STOPPING)
        }
        events.publish(KernelEvent("kernel.stopping", config.name))

        failures += modules.stopAll()
        val targetState = if (failures.none { it.phase == "stop" }) KernelState.STOPPED else KernelState.FAILED
        if (!setState(targetState)) {
            failures += statePersistenceFailure(targetState)
        } else if (targetState == KernelState.STOPPED) {
            recoveryStartAllowed = false
        }
        failures.forEach { safeError("Module ${it.moduleId} failed during ${it.phase}", it.cause) }
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
        val target = if (unhealthy) KernelState.DEGRADED else KernelState.RUNNING
        if (!setState(target)) {
            safeError("Unable to persist refreshed kernel state $target", statePersistenceFailure(target).cause)
        }
    }

    private fun setState(newState: KernelState): Boolean {
        stateRef.set(newState)
        if (persistState(newState)) return true
        stateRef.set(KernelState.FAILED)
        recoveryStartAllowed = true
        return false
    }

    private fun recoverPersistedState(): Pair<KernelState, Boolean> {
        val rawResult = runCatching { ports.stateStore.get(KERNEL_STATE_KEY) }
        if (rawResult.isFailure) {
            safeWarn("Unable to read persisted kernel state", rawResult.exceptionOrNull())
            return KernelState.FAILED to true
        }
        val raw = rawResult.getOrNull() ?: return KernelState.NEW to false

        val persisted = runCatching { KernelState.valueOf(raw) }
            .getOrElse {
                safeWarn("Invalid persisted kernel state: $raw", it)
                return KernelState.FAILED to true
            }

        return when (persisted) {
            KernelState.NEW -> KernelState.NEW to false
            KernelState.STOPPED -> KernelState.STOPPED to false
            else -> {
                safeWarn("Detected unclean previous kernel state: $persisted")
                KernelState.FAILED to true
            }
        }
    }

    private fun persistState(newState: KernelState): Boolean {
        val result = runCatching { ports.stateStore.put(KERNEL_STATE_KEY, newState.name) }
        result.exceptionOrNull()?.let { safeWarn("Unable to persist kernel state", it) }
        return result.isSuccess
    }

    private fun statePersistenceFailure(target: KernelState): ModuleFailure = ModuleFailure(
        moduleId = "kernel",
        phase = "state-persist",
        cause = IllegalStateException("Unable to persist kernel state $target")
    )

    private fun safeInfo(message: String) {
        runCatching { ports.logger.info(message) }
    }

    private fun safeWarn(message: String, error: Throwable? = null) {
        runCatching { ports.logger.warn(message, error) }
    }

    private fun safeError(message: String, error: Throwable? = null) {
        runCatching { ports.logger.error(message, error) }
    }

    private companion object {
        const val KERNEL_STATE_KEY = "kernel.state"
    }
}
