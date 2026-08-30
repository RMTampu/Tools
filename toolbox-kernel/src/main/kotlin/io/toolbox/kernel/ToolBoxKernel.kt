package io.toolbox.kernel

import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

public class ToolBoxKernel(
    public val config: KernelConfig = KernelConfig(),
    public val ports: KernelPorts = KernelPorts()
) {
    private val revision = AtomicLong(0)
    private val mutationGuard = KernelMutationGuard()
    private val safeLogger: KernelLogger = SafeKernelLogger(ports.logger)
    private val safeClock: KernelClock = SafeKernelClock(ports.clock)
    private val supervisor = CallbackSupervisor(ports.executor, safeLogger)
    private val services = ServiceRegistry(mutationGuard, ::mutated)
    private val capabilities = CapabilityRegistry(mutationGuard, ::mutated)
    private val events = EventBus(safeLogger, supervisor, config.eventListenerTimeoutMillis, mutationGuard, ::mutated)
    private val commands = CommandBus(supervisor, config.commandTimeoutMillis, mutationGuard, ::mutated)
    private val modules = ModuleRegistry(::mutated)
    private val lifecycle = LifecycleCoordinator(
        config,
        safeLogger,
        safeClock,
        supervisor,
        modules,
        services,
        capabilities,
        events,
        commands
    )
    private val stateRef = AtomicReference(KernelState.NEW)
    private val operationInProgress = AtomicBoolean(false)
    private val currentOperation = AtomicReference<String?>(null)
    private val statePrefix = "kernel.${config.kernelId}."

    public val sessionId: String = UUID.randomUUID().toString()
    public val state: KernelState get() = stateRef.get()
    public val previousPersistedState: KernelState? = readPersistedState()

    init {
        if (previousPersistedState in setOf(
                KernelState.STARTING,
                KernelState.RUNNING,
                KernelState.DEGRADED,
                KernelState.STOPPING,
                KernelState.FAILED
            )
        ) {
            safeLogger.warn("Previous kernel lifecycle ended in $previousPersistedState")
        }
        persistState(KernelState.NEW)
    }

    public fun install(module: ToolBoxModule): KernelResult<ModuleDescriptor> = runOperation("install") {
        invalidInstallState()?.let { return@runOperation KernelResult.failure(it) }
        val descriptor = try {
            module.descriptor.snapshot()
        } catch (error: Throwable) {
            return@runOperation KernelResult.failure(
                KernelError(KernelErrorCode.INVALID_DESCRIPTOR, "Failed to read module descriptor", error)
            )
        }
        val compatibility = preflightCompatibility(descriptor, requireAuthoritativeRuntime = false)
        if (!compatibility.isSuccess) return@runOperation compatibility
        val admission = evaluateAdmission(descriptor, source = null, verifiedSource = null)
        if (!admission.isSuccess) return@runOperation admission
        registerPreflighted(module, descriptor)
    }

    public fun install(source: ModuleSource, loader: ModuleLoader): KernelResult<ModuleDescriptor> = runOperation("source-install") {
        invalidInstallState()?.let { return@runOperation KernelResult.failure(it) }
        val stableSource = source.snapshot()
        stableSource.validationError()?.let { message ->
            return@runOperation KernelResult.failure(KernelError(KernelErrorCode.INVALID_DESCRIPTOR, message))
        }
        if (!ports.runtimeEnvironment.authoritative) {
            return@runOperation KernelResult.failure(
                KernelError(
                    KernelErrorCode.RUNTIME_ENVIRONMENT_REQUIRED,
                    "External executable loading requires an authoritative runtime API/ABI supplied by the host"
                )
            )
        }

        val staged = try {
            ports.sourceStager.stage(stableSource).snapshot()
        } catch (error: Throwable) {
            safeLogger.error("Failed to stage module source ${stableSource.id}", error)
            return@runOperation KernelResult.failure(
                KernelError(KernelErrorCode.SOURCE_STAGING, "Failed to stage module source ${stableSource.id}", error)
            )
        }
        staged.validationError()?.let { message ->
            return@runOperation KernelResult.failure(KernelError(KernelErrorCode.SOURCE_STAGING, message))
        }
        if (stableSource.id != staged.sourceId) {
            return@runOperation KernelResult.failure(
                KernelError(
                    KernelErrorCode.SOURCE_MISMATCH,
                    "Source id ${stableSource.id} does not match staged source id ${staged.sourceId}"
                )
            )
        }

        val inspectedDescriptor = try {
            loader.inspect(staged).snapshot()
        } catch (error: Throwable) {
            safeLogger.error("Failed to inspect staged module source ${stableSource.id}", error)
            return@runOperation KernelResult.failure(
                KernelError(KernelErrorCode.SOURCE_INSPECTION, "Failed to inspect staged module source ${stableSource.id}", error)
            )
        }
        if (staged.sourceId != inspectedDescriptor.id) {
            return@runOperation KernelResult.failure(
                KernelError(
                    KernelErrorCode.SOURCE_MISMATCH,
                    "Staged source id ${staged.sourceId} does not match inspected descriptor id ${inspectedDescriptor.id}"
                )
            )
        }

        val compatibility = preflightCompatibility(inspectedDescriptor, requireAuthoritativeRuntime = true)
        if (!compatibility.isSuccess) return@runOperation compatibility

        val verification = try {
            ports.sourceVerifier.verify(staged, inspectedDescriptor)
        } catch (error: Throwable) {
            safeLogger.error("Source verifier failed for ${stableSource.id}", error)
            return@runOperation KernelResult.failure(
                KernelError(KernelErrorCode.SOURCE_VERIFICATION, "Source verifier failed for ${stableSource.id}", error)
            )
        }
        if (
            !verification.verified ||
            verification.fingerprint.isBlank() ||
            verification.algorithm.isBlank() ||
            verification.policyId.isBlank()
        ) {
            return@runOperation KernelResult.failure(
                KernelError(
                    KernelErrorCode.SOURCE_VERIFICATION,
                    verification.reason.ifBlank { "Source verification rejected ${stableSource.id}" }
                )
            )
        }
        val verifiedSource = VerifiedModuleSource(
            stagedSource = staged,
            fingerprint = verification.fingerprint,
            verifiedAtMillis = safeClock.nowMillis(),
            algorithm = verification.algorithm,
            signerId = verification.signerId,
            policyId = verification.policyId
        )

        val admission = evaluateAdmission(inspectedDescriptor, stableSource, verifiedSource)
        if (!admission.isSuccess) return@runOperation admission

        val loaded = try {
            loader.load(verifiedSource, inspectedDescriptor)
        } catch (error: Throwable) {
            safeLogger.error("Failed to load verified module source ${stableSource.id}", error)
            return@runOperation KernelResult.failure(
                KernelError(KernelErrorCode.SOURCE_LOAD, "Failed to load verified module source ${stableSource.id}", error)
            )
        }
        val loadedDescriptor = try {
            loaded.descriptor.snapshot()
        } catch (error: Throwable) {
            return@runOperation KernelResult.failure(
                KernelError(KernelErrorCode.INVALID_DESCRIPTOR, "Failed to read loaded module descriptor", error)
            )
        }
        if (loadedDescriptor != inspectedDescriptor) {
            return@runOperation KernelResult.failure(
                KernelError(
                    KernelErrorCode.SOURCE_MISMATCH,
                    "Loaded module descriptor does not match inspected/verified metadata for ${stableSource.id}"
                )
            )
        }
        registerPreflighted(loaded, inspectedDescriptor)
    }

    public fun uninstall(moduleId: String): KernelResult<Boolean> = runOperation("uninstall") {
        if (state in setOf(KernelState.STARTING, KernelState.STOPPING)) {
            return@runOperation KernelResult.failure(
                KernelError(KernelErrorCode.INVALID_STATE, "Cannot uninstall module while kernel state is $state")
            )
        }
        val result = lifecycle.uninstall(moduleId)
        if (result.isSuccess && result.value == true) {
            publish(KernelTopics.MODULE_UNINSTALLED, moduleId)
            safeLogger.info("Uninstalled module $moduleId")
        }
        refreshOperationalState()
        result
    }

    public fun forceUninstall(moduleId: String): KernelResult<Boolean> = runOperation("force-uninstall") {
        val result = lifecycle.forceUninstall(moduleId)
        if (result.isSuccess && result.value == true) publish(KernelTopics.MODULE_UNINSTALLED, moduleId)
        refreshOperationalState()
        result
    }

    public fun retryModule(moduleId: String): KernelResult<Unit> = runOperation("retry") {
        val result = lifecycle.retry(moduleId, activateNow = isOperational())
        if (result.isSuccess && isOperational()) lifecycle.probeHealth()
        refreshOperationalState()
        result
    }

    public fun startModule(moduleId: String): KernelResult<Unit> = runOperation("start-module") {
        if (!isOperational()) {
            return@runOperation KernelResult.failure(
                KernelError(KernelErrorCode.INVALID_STATE, "Individual modules can start only while the kernel is operational")
            )
        }
        val result = lifecycle.startModule(moduleId)
        lifecycle.probeHealth()
        refreshOperationalState()
        result
    }

    public fun stopModule(moduleId: String): KernelResult<Unit> = runOperation("stop-module") {
        if (!isOperational()) {
            return@runOperation KernelResult.failure(
                KernelError(KernelErrorCode.INVALID_STATE, "Individual modules can stop only while the kernel is operational")
            )
        }
        val result = lifecycle.stopModule(moduleId)
        refreshOperationalState()
        result
    }

    public fun restartModule(moduleId: String): KernelResult<Unit> = runOperation("restart-module") {
        if (!isOperational()) {
            return@runOperation KernelResult.failure(
                KernelError(KernelErrorCode.INVALID_STATE, "Individual modules can restart only while the kernel is operational")
            )
        }
        val result = lifecycle.restartModule(moduleId)
        lifecycle.probeHealth()
        refreshOperationalState()
        result
    }

    public fun start(): KernelResult<Unit> = runOperation("start") {
        if (state !in setOf(KernelState.NEW, KernelState.STOPPED, KernelState.STOPPED_WITH_ERRORS)) {
            return@runOperation KernelResult.failure(
                KernelError(KernelErrorCode.INVALID_STATE, "Kernel cannot start from state $state")
            )
        }
        setState(KernelState.STARTING)
        publish(KernelTopics.STARTING)
        safeLogger.info("Starting ${config.name} kernel ${config.version}")

        val result = lifecycle.startAll()
        lifecycle.probeHealth()
        updateStateAfterStart(result)
        result.failures.forEach { safeLogger.error("Module ${it.moduleId} failed during ${it.phase}", it.cause) }
        publish(if (result.isSuccess) KernelTopics.START_COMPLETED else KernelTopics.START_FAILED, result.failures)
        result
    }

    public fun stop(): KernelResult<Unit> = runOperation("stop") {
        if (state in setOf(KernelState.NEW, KernelState.STOPPED)) {
            setState(KernelState.STOPPED)
            return@runOperation KernelResult.success(Unit)
        }
        if (state == KernelState.STOPPED_WITH_ERRORS) {
            return@runOperation KernelResult.failure(
                KernelError(
                    KernelErrorCode.INVALID_STATE,
                    "Kernel is already stopped with unresolved lifecycle errors; repeated stop cannot clear them"
                )
            )
        }
        if (state !in setOf(KernelState.RUNNING, KernelState.DEGRADED, KernelState.FAILED)) {
            return@runOperation KernelResult.failure(
                KernelError(KernelErrorCode.INVALID_STATE, "Kernel cannot stop from state $state")
            )
        }
        setState(KernelState.STOPPING)
        publish(KernelTopics.STOPPING)
        val result = lifecycle.stopAll()
        val quarantined = modules.healthSnapshot().any { it.state == ModuleState.QUARANTINED }
        setState(
            when {
                quarantined -> KernelState.FAILED
                result.isSuccess -> KernelState.STOPPED
                else -> KernelState.STOPPED_WITH_ERRORS
            }
        )
        result.failures.forEach { safeLogger.error("Module ${it.moduleId} failed during ${it.phase}", it.cause) }
        publish(if (result.isSuccess) KernelTopics.STOP_COMPLETED else KernelTopics.STOP_FAILED, result.failures)
        result
    }

    public fun probeHealth(): KernelResult<List<ModuleHealth>> = runOperation("health-probe") {
        val health = lifecycle.probeHealth()
        refreshOperationalState()
        KernelResult.success(health)
    }

    public fun snapshot(): KernelSnapshot = mutationGuard.snapshot {
        val before = revision.get()
        val operationBefore = operationInProgress.get()
        val candidate = snapshotAt(before, consistent = false)
        val after = revision.get()
        val operationAfter = operationInProgress.get()
        candidate.copy(
            revision = after,
            consistent = before == after && !operationBefore && !operationAfter
        )
    }

    public fun moduleDescriptors(): List<ModuleDescriptor> = modules.descriptors()
    public fun moduleState(moduleId: String): ModuleState? = modules.stateOf(moduleId)

    public fun <T : Any> service(type: Class<T>, qualifier: String = "default"): ServiceHandle<T>? {
        val registration = services.reference(ServiceKey(type, qualifier)) { true } ?: return null
        return ServiceHandle(registration.owner, registration.value)
    }

    public fun capabilities(): List<Capability> = capabilities.allActive()
    public fun execute(command: KernelCommand): CommandResult = commands.execute(command)
    public fun subscribe(topic: String, listener: (KernelEvent) -> Unit): Subscription =
        events.subscribe(KernelResourceOwner, topic, listener)

    public fun isOperational(): Boolean = state in setOf(KernelState.RUNNING, KernelState.DEGRADED)

    private fun invalidInstallState(): KernelError? =
        if (state in setOf(KernelState.STARTING, KernelState.STOPPING, KernelState.FAILED)) {
            KernelError(KernelErrorCode.INVALID_STATE, "Cannot install module while kernel state is $state")
        } else {
            null
        }

    private fun preflightCompatibility(
        descriptor: ModuleDescriptor,
        requireAuthoritativeRuntime: Boolean
    ): KernelResult<ModuleDescriptor> {
        descriptor.validationError()?.let { message ->
            return KernelResult.failure(KernelError(KernelErrorCode.INVALID_DESCRIPTOR, message))
        }

        val mandatory = MandatoryCompatibilityPolicy.check(
            config,
            ports.runtimeEnvironment,
            descriptor,
            requireAuthoritativeRuntime
        )
        if (!mandatory.compatible) {
            publish(KernelTopics.MODULE_REJECTED, descriptor)
            val code = if (requireAuthoritativeRuntime && !ports.runtimeEnvironment.authoritative) {
                KernelErrorCode.RUNTIME_ENVIRONMENT_REQUIRED
            } else {
                KernelErrorCode.INCOMPATIBLE_MODULE
            }
            return KernelResult.failure(KernelError(code, mandatory.reason))
        }

        val additional = try {
            ports.compatibilityPolicy.check(config, ports.runtimeEnvironment, descriptor)
        } catch (error: Throwable) {
            safeLogger.error("Compatibility policy failed for ${descriptor.id}", error)
            return KernelResult.failure(
                KernelError(KernelErrorCode.POLICY_FAILURE, "Compatibility policy failed for ${descriptor.id}", error)
            )
        }
        if (!additional.compatible) {
            publish(KernelTopics.MODULE_REJECTED, descriptor)
            return KernelResult.failure(KernelError(KernelErrorCode.INCOMPATIBLE_MODULE, additional.reason))
        }
        return KernelResult.success(descriptor)
    }

    private fun evaluateAdmission(
        descriptor: ModuleDescriptor,
        source: ModuleSource?,
        verifiedSource: VerifiedModuleSource?
    ): KernelResult<ModuleDescriptor> {
        val admission = try {
            ports.admissionPolicy.evaluate(descriptor, source, verifiedSource)
        } catch (error: Throwable) {
            safeLogger.error("Admission policy failed for ${descriptor.id}", error)
            return KernelResult.failure(
                KernelError(KernelErrorCode.POLICY_FAILURE, "Admission policy failed for ${descriptor.id}", error)
            )
        }
        if (!admission.allowed) {
            publish(KernelTopics.MODULE_REJECTED, descriptor)
            return KernelResult.failure(KernelError(KernelErrorCode.ADMISSION_REJECTED, admission.reason))
        }
        return KernelResult.success(descriptor)
    }

    private fun registerPreflighted(module: ToolBoxModule, descriptor: ModuleDescriptor): KernelResult<ModuleDescriptor> {
        try {
            modules.register(module, descriptor)
        } catch (error: Throwable) {
            return KernelResult.failure(
                KernelError(KernelErrorCode.CONFLICT, error.message ?: "Module registration conflict", error)
            )
        }

        if (isOperational()) {
            val activation = lifecycle.activate(descriptor.id)
            if (!activation.isSuccess) {
                val cleanupFailure = activation.failures.lastOrNull {
                    it.moduleId == descriptor.id && it.phase == LifecyclePhase.UNLOAD
                }
                when {
                    cleanupFailure != null -> modules.recordFailure(descriptor.id, cleanupFailure)
                    modules.stateOf(descriptor.id) == ModuleState.QUARANTINED -> Unit
                    else -> lifecycle.discardFailedRegistration(descriptor.id)
                }
                refreshOperationalState()
                publish(KernelTopics.MODULE_ACTIVATION_FAILED, descriptor)
                return KernelResult.failure(activation.errors, activation.failures)
            }
            lifecycle.probeHealth()
            refreshOperationalState()
        }
        publish(KernelTopics.MODULE_INSTALLED, descriptor)
        safeLogger.info("Installed module ${descriptor.id} ${descriptor.version}")
        return KernelResult.success(descriptor)
    }

    private fun updateStateAfterStart(result: KernelResult<Unit>): Unit {
        val health = modules.healthSnapshot()
        val startedCount = health.count { it.state == ModuleState.STARTED }
        val unhealthy = health.any { moduleHealth ->
            moduleHealth.state in setOf(ModuleState.FAILED, ModuleState.QUARANTINED) ||
                moduleHealth.lastFailure?.phase == LifecyclePhase.RESOLUTION ||
                (moduleHealth.state == ModuleState.STARTED && isBadHealth(moduleHealth.health))
        }
        val next = when {
            result.isSuccess && !unhealthy -> KernelState.RUNNING
            startedCount > 0 -> KernelState.DEGRADED
            else -> KernelState.FAILED
        }
        setState(next)
    }

    private fun refreshOperationalState(): Unit {
        if (state !in setOf(KernelState.RUNNING, KernelState.DEGRADED)) return
        val unhealthy = modules.healthSnapshot().any { moduleHealth ->
            moduleHealth.state in setOf(ModuleState.FAILED, ModuleState.QUARANTINED) ||
                moduleHealth.lastFailure?.phase == LifecyclePhase.RESOLUTION ||
                (moduleHealth.state == ModuleState.STARTED && isBadHealth(moduleHealth.health))
        }
        setState(if (unhealthy) KernelState.DEGRADED else KernelState.RUNNING)
    }

    private fun isBadHealth(health: HealthStatus): Boolean =
        health.state == HealthState.UNHEALTHY || (health.state == HealthState.UNKNOWN && health.checkedAtMillis != null)

    private fun publish(topic: String, payload: Any? = null): Unit {
        try {
            events.publish(KernelEvent(topic, config.name, payload, safeClock.nowMillis()))
        } catch (error: Throwable) {
            safeLogger.warn("Kernel event publication failed for $topic", error)
        }
    }

    private fun setState(newState: KernelState): Unit {
        stateRef.set(newState)
        mutated()
        persistState(newState)
    }

    private fun snapshotAt(snapshotRevision: Long, consistent: Boolean): KernelSnapshot = KernelSnapshot(
        config = config,
        runtimeEnvironment = ports.runtimeEnvironment,
        state = state,
        previousPersistedState = previousPersistedState,
        sessionId = sessionId,
        revision = snapshotRevision,
        consistent = consistent,
        modules = modules.healthSnapshot(),
        registeredServices = services.size,
        registeredCapabilities = capabilities.size,
        registeredCommands = commands.size,
        eventSubscriptions = events.size
    )

    private fun readPersistedState(): KernelState? = try {
        val canonical = ports.stateStore.get(statePrefix + "record")
        if (canonical != null) {
            val decoded = PersistedKernelStateCodec.decode(canonical)
            if (decoded == null) {
                safeLogger.warn("Canonical kernel state record is invalid for ${config.kernelId}")
                null
            } else {
                decoded.state
            }
        } else {
            ports.stateStore.get(statePrefix + "state")?.let(KernelState::valueOf)
        }
    } catch (error: Throwable) {
        safeLogger.warn("Unable to read previous kernel state", error)
        null
    }

    private fun persistState(newState: KernelState): Unit {
        try {
            val updatedAt = safeClock.nowMillis()
            val operation = currentOperation.get()
            val record = PersistedKernelStateRecord(
                state = newState,
                sessionId = sessionId,
                updatedAtMillis = updatedAt,
                operation = operation
            )
            ports.stateStore.put(statePrefix + "record", PersistedKernelStateCodec.encode(record))
            ports.stateStore.put(statePrefix + "state", newState.name)
            ports.stateStore.put(statePrefix + "session", sessionId)
            ports.stateStore.put(statePrefix + "updatedAt", updatedAt.toString())
            ports.stateStore.put(statePrefix + "operation", operation ?: "none")
        } catch (error: Throwable) {
            safeLogger.warn("Unable to persist kernel state", error)
        }
    }

    private fun mutated(): Unit {
        revision.incrementAndGet()
    }

    private fun <T> runOperation(name: String, block: () -> KernelResult<T>): KernelResult<T> {
        if (!operationInProgress.compareAndSet(false, true)) {
            return KernelResult.failure(
                KernelError(KernelErrorCode.OPERATION_IN_PROGRESS, "Kernel operation already in progress; rejected $name")
            )
        }
        currentOperation.set(name)
        persistState(state)
        return try {
            block()
        } catch (error: Throwable) {
            safeLogger.error("Unexpected kernel operation failure: $name", error)
            KernelResult.failure(
                KernelError(KernelErrorCode.LIFECYCLE, "Unexpected kernel operation failure: $name", error)
            )
        } finally {
            currentOperation.set(null)
            operationInProgress.set(false)
            persistState(state)
        }
    }
}
