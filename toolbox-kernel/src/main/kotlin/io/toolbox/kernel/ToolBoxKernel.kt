package io.toolbox.kernel

import java.util.concurrent.atomic.AtomicReference

public class ToolBoxKernel(
    public val config: KernelConfig = KernelConfig(),
    public val ports: KernelPorts = KernelPorts()
) {
    private val services = ServiceRegistry()
    private val capabilities = CapabilityRegistry()
    private val events = EventBus(ports.logger)
    private val commands = CommandBus()
    private val modules = ModuleRegistry()
    private val lifecycle = LifecycleCoordinator(config, ports, modules, services, capabilities, events, commands)
    private val stateRef = AtomicReference(KernelState.NEW)

    public val state: KernelState get() = stateRef.get()
    public val previousPersistedState: KernelState? = readPersistedState()

    init {
        if (previousPersistedState in setOf(KernelState.STARTING, KernelState.RUNNING, KernelState.DEGRADED, KernelState.STOPPING, KernelState.FAILED)) {
            ports.logger.warn("Previous kernel lifecycle ended in $previousPersistedState")
        }
        persistState(KernelState.NEW)
    }

    @Synchronized
    public fun install(module: ToolBoxModule): KernelResult<ModuleDescriptor> {
        invalidInstallState()?.let { return KernelResult.failure(it) }
        val descriptor = module.descriptor.snapshot()
        val preflight = preflightDescriptor(descriptor, null)
        if (!preflight.isSuccess) return preflight
        return registerPreflighted(module, descriptor)
    }

    @Synchronized
    public fun install(source: ModuleSource, loader: ModuleLoader): KernelResult<ModuleDescriptor> {
        invalidInstallState()?.let { return KernelResult.failure(it) }
        val stableSource = source.snapshot()
        stableSource.validationError()?.let { message ->
            return KernelResult.failure(KernelError(KernelErrorCode.INVALID_DESCRIPTOR, message))
        }

        val inspectedDescriptor = runCatching { loader.inspect(stableSource).snapshot() }.getOrElse { error ->
            ports.logger.error("Failed to inspect module source ${stableSource.id}", error)
            return KernelResult.failure(KernelError(KernelErrorCode.SOURCE_INSPECTION, "Failed to inspect module source ${stableSource.id}", error))
        }
        if (stableSource.id != inspectedDescriptor.id) {
            return KernelResult.failure(KernelError(KernelErrorCode.SOURCE_MISMATCH, "Source id ${stableSource.id} does not match inspected descriptor id ${inspectedDescriptor.id}"))
        }

        val preflight = preflightDescriptor(inspectedDescriptor, stableSource)
        if (!preflight.isSuccess) return preflight

        val loaded = runCatching { loader.load(stableSource, inspectedDescriptor) }.getOrElse { error ->
            ports.logger.error("Failed to load module source ${stableSource.id}", error)
            return KernelResult.failure(KernelError(KernelErrorCode.SOURCE_LOAD, "Failed to load module source ${stableSource.id}", error))
        }
        val loadedDescriptor = loaded.descriptor.snapshot()
        if (loadedDescriptor != inspectedDescriptor) {
            return KernelResult.failure(
                KernelError(
                    KernelErrorCode.SOURCE_MISMATCH,
                    "Loaded module descriptor does not match the descriptor that passed preflight for ${stableSource.id}"
                )
            )
        }
        return registerPreflighted(loaded, inspectedDescriptor)
    }

    private fun invalidInstallState(): KernelError? =
        if (state in setOf(KernelState.STARTING, KernelState.STOPPING, KernelState.FAILED)) {
            KernelError(KernelErrorCode.INVALID_STATE, "Cannot install module while kernel state is $state")
        } else {
            null
        }

    private fun preflightDescriptor(descriptor: ModuleDescriptor, source: ModuleSource?): KernelResult<ModuleDescriptor> {
        descriptor.validationError()?.let { message ->
            return KernelResult.failure(KernelError(KernelErrorCode.INVALID_DESCRIPTOR, message))
        }
        val compatibility = ports.compatibilityPolicy.check(config, ports.runtimeEnvironment, descriptor)
        if (!compatibility.compatible) {
            publish("kernel.module.rejected", descriptor)
            return KernelResult.failure(KernelError(KernelErrorCode.INCOMPATIBLE_MODULE, compatibility.reason))
        }
        val admission = ports.admissionPolicy.evaluate(descriptor, source)
        if (!admission.allowed) {
            publish("kernel.module.rejected", descriptor)
            return KernelResult.failure(KernelError(KernelErrorCode.ADMISSION_REJECTED, admission.reason))
        }
        return KernelResult.success(descriptor)
    }

    private fun registerPreflighted(module: ToolBoxModule, descriptor: ModuleDescriptor): KernelResult<ModuleDescriptor> {
        val registered = runCatching { modules.register(module, descriptor) }
        if (registered.isFailure) {
            val error = registered.exceptionOrNull()
            return KernelResult.failure(KernelError(KernelErrorCode.CONFLICT, error?.message ?: "Module registration conflict", error))
        }

        if (isOperational()) {
            val activation = lifecycle.activate(descriptor.id)
            if (!activation.isSuccess) {
                lifecycle.discardFailedRegistration(descriptor.id)
                refreshOperationalState()
                publish("kernel.module.activation_failed", descriptor)
                return KernelResult(value = null, errors = activation.errors, failures = activation.failures)
            }
            lifecycle.probeHealth()
            refreshOperationalState()
        }
        publish("kernel.module.installed", descriptor)
        ports.logger.info("Installed module ${descriptor.id} ${descriptor.version}")
        return KernelResult.success(descriptor)
    }

    @Synchronized
    public fun uninstall(moduleId: String): KernelResult<Boolean> {
        if (state in setOf(KernelState.STARTING, KernelState.STOPPING)) {
            return KernelResult.failure(KernelError(KernelErrorCode.INVALID_STATE, "Cannot uninstall module while kernel state is $state"))
        }
        val result = lifecycle.uninstall(moduleId)
        if (result.isSuccess && result.value == true) {
            publish("kernel.module.uninstalled", moduleId)
            ports.logger.info("Uninstalled module $moduleId")
            refreshOperationalState()
        }
        return result
    }

    @Synchronized
    public fun retryModule(moduleId: String): KernelResult<Unit> {
        val result = lifecycle.retry(moduleId, activateNow = isOperational())
        if (result.isSuccess && isOperational()) lifecycle.probeHealth()
        refreshOperationalState()
        return result
    }

    @Synchronized
    public fun start(): KernelResult<Unit> {
        if (state !in setOf(KernelState.NEW, KernelState.STOPPED)) {
            return KernelResult.failure(KernelError(KernelErrorCode.INVALID_STATE, "Kernel cannot start from state $state"))
        }
        setState(KernelState.STARTING)
        publish("kernel.starting")
        ports.logger.info("Starting ${config.name} kernel ${config.version}")

        val result = lifecycle.startAll()
        lifecycle.probeHealth()
        updateStateAfterStart(result)
        result.failures.forEach { ports.logger.error("Module ${it.moduleId} failed during ${it.phase}", it.cause) }
        publish("kernel.started", result.failures)
        return result
    }

    @Synchronized
    public fun stop(): KernelResult<Unit> {
        if (state in setOf(KernelState.NEW, KernelState.STOPPED)) {
            setState(KernelState.STOPPED)
            return KernelResult.success(Unit)
        }
        if (state !in setOf(KernelState.RUNNING, KernelState.DEGRADED, KernelState.FAILED)) {
            return KernelResult.failure(KernelError(KernelErrorCode.INVALID_STATE, "Kernel cannot stop from state $state"))
        }
        setState(KernelState.STOPPING)
        publish("kernel.stopping")
        val result = lifecycle.stopAll()
        setState(if (result.isSuccess) KernelState.STOPPED else KernelState.FAILED)
        result.failures.forEach { ports.logger.error("Module ${it.moduleId} failed during ${it.phase}", it.cause) }
        publish("kernel.stopped", result.failures)
        return result
    }

    @Synchronized
    public fun probeHealth(): List<ModuleHealth> {
        val health = lifecycle.probeHealth()
        refreshOperationalState()
        return health
    }

    public fun snapshot(): KernelSnapshot = KernelSnapshot(
        config = config,
        runtimeEnvironment = ports.runtimeEnvironment,
        state = state,
        previousPersistedState = previousPersistedState,
        modules = modules.healthSnapshot(),
        registeredServices = services.size,
        registeredCapabilities = capabilities.size,
        registeredCommands = commands.size,
        eventSubscriptions = events.size
    )

    public fun moduleDescriptors(): List<ModuleDescriptor> = modules.descriptors()
    public fun moduleState(moduleId: String): ModuleState? = modules.stateOf(moduleId)
    public fun <T : Any> service(type: Class<T>): T? = services.get(type)
    public fun capabilities(): List<Capability> = capabilities.all()
    public fun execute(command: KernelCommand): CommandResult = commands.execute(command)
    public fun subscribe(topic: String, listener: (KernelEvent) -> Unit): Subscription = events.subscribe(KERNEL_OWNER, topic, listener)
    public fun isOperational(): Boolean = state in setOf(KernelState.RUNNING, KernelState.DEGRADED)

    private fun updateStateAfterStart(result: KernelResult<Unit>): Unit {
        val health = modules.healthSnapshot()
        val startedCount = health.count { it.state == ModuleState.STARTED }
        val unhealthy = health.any { it.state == ModuleState.FAILED || (it.state == ModuleState.STARTED && it.health.state == HealthState.UNHEALTHY) }
        val next = when {
            result.isSuccess && !unhealthy -> KernelState.RUNNING
            startedCount > 0 -> KernelState.DEGRADED
            else -> KernelState.FAILED
        }
        setState(next)
    }

    private fun refreshOperationalState(): Unit {
        if (state !in setOf(KernelState.RUNNING, KernelState.DEGRADED)) return
        val unhealthy = modules.healthSnapshot().any {
            it.state == ModuleState.FAILED || (it.state == ModuleState.STARTED && it.health.state == HealthState.UNHEALTHY)
        }
        setState(if (unhealthy) KernelState.DEGRADED else KernelState.RUNNING)
    }

    private fun publish(topic: String, payload: Any? = null): Unit =
        events.publish(KernelEvent(topic, config.name, payload, ports.clock.nowMillis()))

    private fun setState(newState: KernelState): Unit {
        stateRef.set(newState)
        persistState(newState)
    }

    private fun readPersistedState(): KernelState? = runCatching {
        ports.stateStore.get(STATE_KEY)?.let(KernelState::valueOf)
    }.onFailure { ports.logger.warn("Unable to read previous kernel state", it) }.getOrNull()

    private fun persistState(newState: KernelState): Unit {
        runCatching { ports.stateStore.put(STATE_KEY, newState.name) }
            .onFailure { ports.logger.warn("Unable to persist kernel state", it) }
    }

    private companion object {
        const val STATE_KEY: String = "kernel.state"
        const val KERNEL_OWNER: String = "kernel"
    }
}
