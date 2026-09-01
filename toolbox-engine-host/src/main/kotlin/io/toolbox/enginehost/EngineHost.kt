package io.toolbox.enginehost

import io.toolbox.kernel.HealthStatus
import io.toolbox.kernel.KernelContext
import io.toolbox.kernel.KernelEvent
import io.toolbox.kernel.ModuleDescriptor
import io.toolbox.kernel.ToolBoxModule

class EngineHost(
    private val environment: EngineEnvironment = EngineEnvironment(
        androidApi = 30,
        abi = "arm64-v8a"
    )
) : ToolBoxModule {
    override val descriptor = ModuleDescriptor(
        id = "toolbox.engine-host",
        name = "ToolBox Engine Host",
        version = "1.0.0",
        apiVersion = 1,
        minAndroidApi = 30,
        supportedArchitectures = setOf("arm64-v8a")
    )

    private data class Record(
        val provider: EngineProvider,
        val compatibility: EngineCompatibilityResult,
        var state: EngineState,
        var instance: ToolBoxEngine? = null,
        var scope: EngineRuntimeScope? = null,
        var started: Boolean = false,
        var activeLeases: Int = 0,
        var generation: Long = 0,
        var lastFailure: String? = null
    ) {
        val descriptor: EngineDescriptor get() = provider.descriptor
    }

    private val records = linkedMapOf<String, Record>()
    private var kernelContext: KernelContext? = null
    private var hostRunning = false

    override fun onLoad(context: KernelContext) {
        synchronized(this) {
            check(kernelContext == null) { "EngineHost already loaded" }
            kernelContext = context
            context.services.register(EngineHost::class.java, this)
        }
    }

    override fun onStart() {
        synchronized(this) {
            check(kernelContext != null) { "EngineHost cannot start before load" }
            hostRunning = true
            publish("engine.host.started", descriptor.id)
        }
    }

    override fun onStop() {
        synchronized(this) {
            hostRunning = false
            records.values
                .filter { it.state == EngineState.RUNNING }
                .asReversed()
                .forEach { releaseRecord(it, "host-stop") }
            publish("engine.host.stopped", descriptor.id)
        }
    }

    override fun healthCheck(): HealthStatus = synchronized(this) {
        if (kernelContext == null) {
            return@synchronized HealthStatus.failed("EngineHost has no kernel context")
        }
        if (!hostRunning) {
            return@synchronized HealthStatus.failed("EngineHost is not running")
        }
        val failed = records.values.count { it.state == EngineState.FAILED }
        val incompatible = records.values.count { it.state == EngineState.INCOMPATIBLE }
        HealthStatus.ok(
            "EngineHost operational; registered=${records.size}, failed-isolated=$failed, incompatible=$incompatible"
        )
    }

    @Synchronized
    fun register(provider: EngineProvider): EngineRegistrationResult {
        val validationError = runCatching { validateDescriptor(provider.descriptor) }.exceptionOrNull()
        if (validationError != null) {
            return EngineRegistrationResult(
                registered = false,
                compatible = false,
                reason = validationError.message ?: "Invalid engine descriptor"
            )
        }

        val engineId = provider.descriptor.engineId
        if (engineId in records) {
            return EngineRegistrationResult(
                registered = false,
                compatible = records.getValue(engineId).compatibility.compatible,
                reason = "Engine already registered: $engineId"
            )
        }

        val compatibility = checkCompatibility(provider.descriptor)
        val state = if (compatibility.compatible) EngineState.REGISTERED else EngineState.INCOMPATIBLE
        records[engineId] = Record(
            provider = provider,
            compatibility = compatibility,
            state = state
        )

        publish(
            topic = if (compatibility.compatible) "engine.registered" else "engine.incompatible",
            payload = provider.descriptor
        )

        return EngineRegistrationResult(
            registered = true,
            compatible = compatibility.compatible,
            reason = compatibility.reason
        )
    }

    @Synchronized
    fun unregister(engineId: String): Boolean {
        val record = records[engineId] ?: return false
        check(record.activeLeases == 0) { "Cannot unregister active engine: $engineId" }
        check(record.state !in setOf(EngineState.LOADING, EngineState.STARTING, EngineState.RUNNING, EngineState.RELEASING)) {
            "Cannot unregister engine while state is ${record.state}: $engineId"
        }
        records.remove(engineId)
        publish("engine.unregistered", record.descriptor)
        return true
    }

    @Synchronized
    fun acquire(engineId: String): EngineAcquireResult {
        val context = kernelContext
            ?: return rejected(engineId, "HOST_NOT_LOADED", "EngineHost is not loaded")
        if (!hostRunning) {
            return rejected(engineId, "HOST_NOT_RUNNING", "EngineHost is not running")
        }

        val record = records[engineId]
            ?: return rejected(engineId, "UNKNOWN_ENGINE", "Unknown engine: $engineId")

        when (record.state) {
            EngineState.INCOMPATIBLE -> {
                return rejected(engineId, "ENGINE_INCOMPATIBLE", record.compatibility.reason)
            }
            EngineState.FAILED -> {
                return rejected(
                    engineId,
                    "ENGINE_FAILED",
                    record.lastFailure ?: "Engine is in failed state; unregister/re-register after correction"
                )
            }
            EngineState.RUNNING -> {
                record.activeLeases += 1
                return acquired(record)
            }
            EngineState.REGISTERED -> Unit
            else -> {
                return rejected(engineId, "ENGINE_BUSY", "Engine is transitioning: ${record.state}")
            }
        }

        val missingRequirement = record.descriptor.requiredCapabilities.firstOrNull { requirement ->
            val capability = context.capabilities.get(requirement.id)
            capability == null || capability.version < requirement.minVersion
        }
        if (missingRequirement != null) {
            val reason = "Required capability unavailable: ${missingRequirement.id} >= ${missingRequirement.minVersion}"
            publish("engine.requirement.missing", mapOf("engineId" to engineId, "reason" to reason))
            return rejected(engineId, "MISSING_REQUIRED_CAPABILITY", reason)
        }

        record.state = EngineState.LOADING
        val engine = runCatching { record.provider.create() }
            .getOrElse { return failActivation(record, "create", it) }
        val scope = EngineRuntimeScope(engineId, context)
        record.instance = engine
        record.scope = scope

        runCatching { engine.onLoad(scope) }
            .onFailure { return failActivation(record, "load", it) }

        record.state = EngineState.STARTING
        runCatching { engine.onStart() }
            .onSuccess { record.started = true }
            .onFailure { return failActivation(record, "start", it) }

        runCatching { verifyDeclaredCapabilities(record) }
            .onFailure { return failActivation(record, "contract-verification", it) }

        record.generation += 1
        record.activeLeases = 1
        record.state = EngineState.RUNNING
        record.lastFailure = null
        publish("engine.started", record.descriptor)
        return acquired(record)
    }

    @Synchronized
    fun status(engineId: String): EngineStatus? = records[engineId]?.let(::toStatus)

    @Synchronized
    fun statuses(): List<EngineStatus> = records.values
        .map(::toStatus)
        .sortedBy { it.descriptor.engineId }

    @Synchronized
    fun isRunning(engineId: String): Boolean = records[engineId]?.state == EngineState.RUNNING

    private fun acquired(record: Record): EngineAcquireResult.Acquired = EngineAcquireResult.Acquired(
        EngineLease(
            engineId = record.descriptor.engineId,
            generation = record.generation,
            releaseAction = ::releaseLease
        )
    )

    @Synchronized
    private fun releaseLease(engineId: String, generation: Long) {
        val record = records[engineId] ?: return
        if (record.state != EngineState.RUNNING || record.generation != generation) return
        if (record.activeLeases <= 0) return

        record.activeLeases -= 1
        if (record.activeLeases == 0) {
            releaseRecord(record, "last-lease-closed")
        }
    }

    private fun releaseRecord(record: Record, reason: String) {
        if (record.state != EngineState.RUNNING) return
        record.state = EngineState.RELEASING
        val failures = cleanupRuntime(record)
        if (failures.isEmpty()) {
            record.state = EngineState.REGISTERED
            record.lastFailure = null
            publish("engine.released", mapOf("engineId" to record.descriptor.engineId, "reason" to reason))
        } else {
            val primary = failures.first()
            failures.drop(1).forEach(primary::addSuppressed)
            record.state = EngineState.FAILED
            record.lastFailure = "release: ${primary.message ?: primary::class.java.simpleName}"
            publish("engine.failed", mapOf("engineId" to record.descriptor.engineId, "phase" to "release"))
        }
    }

    private fun failActivation(
        record: Record,
        phase: String,
        error: Throwable
    ): EngineAcquireResult.Rejected {
        val cleanupFailures = cleanupRuntime(record)
        cleanupFailures.forEach(error::addSuppressed)
        record.state = EngineState.FAILED
        record.lastFailure = "$phase: ${error.message ?: error::class.java.simpleName}"
        publish("engine.failed", mapOf("engineId" to record.descriptor.engineId, "phase" to phase))
        return rejected(
            record.descriptor.engineId,
            "ENGINE_ACTIVATION_FAILED",
            record.lastFailure ?: "Engine activation failed"
        )
    }

    private fun cleanupRuntime(record: Record): List<Throwable> {
        val failures = mutableListOf<Throwable>()
        val instance = record.instance
        val scope = record.scope

        if (record.started && instance != null) {
            runCatching { instance.onStop() }.onFailure(failures::add)
        }
        record.started = false

        if (instance != null) {
            runCatching { instance.onUnload() }.onFailure(failures::add)
        }
        if (scope != null) {
            runCatching { scope.close() }.onFailure(failures::add)
        }

        record.instance = null
        record.scope = null
        record.activeLeases = 0
        return failures
    }

    private fun toStatus(record: Record): EngineStatus {
        val health = if (record.state == EngineState.RUNNING) {
            runCatching { record.instance?.healthCheck() ?: HealthStatus.failed("Missing engine instance") }
                .getOrElse { HealthStatus.failed(it.message ?: it::class.java.simpleName) }
        } else if (record.state == EngineState.FAILED) {
            HealthStatus.failed(record.lastFailure ?: "Engine failed")
        } else {
            HealthStatus.ok("State: ${record.state}")
        }

        return EngineStatus(
            descriptor = record.descriptor,
            state = record.state,
            activeLeases = record.activeLeases,
            compatibility = record.compatibility,
            health = health,
            lastFailure = record.lastFailure
        )
    }

    private fun checkCompatibility(descriptor: EngineDescriptor): EngineCompatibilityResult {
        if (descriptor.origin != EngineOrigin.COMPILED_APK) {
            return EngineCompatibilityResult(false, "Only compiled trusted APK engines are executable")
        }
        if (!descriptor.lifecycle.lazyLoad || !descriptor.lifecycle.releasable || !descriptor.lifecycle.failureIsolationRequired) {
            return EngineCompatibilityResult(false, "Engine lifecycle contract is below ToolBox baseline")
        }
        if (environment.androidApi < descriptor.minAndroidApi) {
            return EngineCompatibilityResult(false, "Engine requires Android API ${descriptor.minAndroidApi}")
        }
        if (descriptor.maxAndroidApi != null && environment.androidApi > descriptor.maxAndroidApi) {
            return EngineCompatibilityResult(false, "Engine supports Android API only through ${descriptor.maxAndroidApi}")
        }
        if (environment.abi !in descriptor.supportedAbi) {
            return EngineCompatibilityResult(false, "Engine does not support ABI ${environment.abi}")
        }
        return EngineCompatibilityResult(true)
    }

    private fun validateDescriptor(descriptor: EngineDescriptor) {
        require(descriptor.engineId.isNotBlank()) { "Engine id cannot be blank" }
        require(descriptor.engineId.none(Char::isWhitespace)) { "Engine id cannot contain whitespace" }
        require(descriptor.name.isNotBlank()) { "Engine name cannot be blank" }
        require(descriptor.engineVersion.isNotBlank()) { "Engine version cannot be blank" }
        require(descriptor.contractVersion > 0) { "Contract version must be positive" }
        require(descriptor.minAndroidApi > 0) { "minAndroidApi must be positive" }
        require(descriptor.maxAndroidApi == null || descriptor.maxAndroidApi >= descriptor.minAndroidApi) {
            "maxAndroidApi cannot be below minAndroidApi"
        }
        require(descriptor.supportedAbi.isNotEmpty()) { "supportedAbi cannot be empty" }
        require(descriptor.supportedAbi.none(String::isBlank)) { "supportedAbi cannot contain blank values" }
        require(descriptor.entryPoint.isNotBlank()) { "Entry point identity cannot be blank" }
        descriptor.requiredCapabilities.forEach { requirement ->
            require(requirement.id.isNotBlank()) { "Required capability id cannot be blank" }
            require(requirement.minVersion > 0) { "Required capability version must be positive" }
            require(requirement.id !in descriptor.providedCapabilityIds) {
                "Engine cannot require its own provided capability: ${requirement.id}"
            }
        }
        validateIds("provided capability", descriptor.providedCapabilityIds)
        validateIds("component", descriptor.componentIds)
        validateIds("action", descriptor.actionIds)
        validateIds("event", descriptor.eventIds)
        validateIds("data type", descriptor.dataTypeIds)
        validateIds("permission", descriptor.permissionNeeds)
    }

    private fun validateIds(label: String, ids: Set<String>) {
        require(ids.none(String::isBlank)) { "$label id cannot be blank" }
    }

    private fun verifyDeclaredCapabilities(record: Record) {
        val context = kernelContext ?: error("EngineHost lost kernel context")
        record.descriptor.providedCapabilityIds.forEach { capabilityId ->
            val capability = context.capabilities.get(capabilityId)
                ?: error("Declared capability was not registered: $capabilityId")
            check(capability.providerModuleId == record.descriptor.engineId) {
                "Declared capability $capabilityId is owned by ${capability.providerModuleId}, not ${record.descriptor.engineId}"
            }
        }
    }

    private fun rejected(engineId: String, code: String, reason: String): EngineAcquireResult.Rejected =
        EngineAcquireResult.Rejected(engineId = engineId, code = code, reason = reason)

    private fun publish(topic: String, payload: Any?) {
        kernelContext?.events?.publish(
            KernelEvent(
                topic = topic,
                source = descriptor.id,
                payload = payload
            )
        )
    }
}
