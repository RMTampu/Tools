package io.toolbox.enginehost

import io.toolbox.kernel.HealthStatus
import java.util.concurrent.atomic.AtomicBoolean

enum class EngineState {
    REGISTERED,
    INCOMPATIBLE,
    LOADING,
    STARTING,
    RUNNING,
    RELEASING,
    FAILED
}

enum class EngineOrigin {
    COMPILED_APK
}

data class CapabilityRequirement(
    val id: String,
    val minVersion: Int = 1
)

data class EngineLifecycleRequirements(
    val lazyLoad: Boolean = true,
    val releasable: Boolean = true,
    val failureIsolationRequired: Boolean = true
)

data class EngineDescriptor(
    val engineId: String,
    val name: String,
    val engineVersion: String,
    val contractVersion: Int = 1,
    val minAndroidApi: Int = 30,
    val maxAndroidApi: Int? = null,
    val supportedAbi: Set<String> = setOf("arm64-v8a"),
    val requiredCapabilities: Set<CapabilityRequirement> = emptySet(),
    val providedCapabilityIds: Set<String> = emptySet(),
    val componentIds: Set<String> = emptySet(),
    val actionIds: Set<String> = emptySet(),
    val eventIds: Set<String> = emptySet(),
    val dataTypeIds: Set<String> = emptySet(),
    val permissionNeeds: Set<String> = emptySet(),
    val entryPoint: String,
    val lifecycle: EngineLifecycleRequirements = EngineLifecycleRequirements(),
    val origin: EngineOrigin = EngineOrigin.COMPILED_APK
)

data class EngineEnvironment(
    val androidApi: Int,
    val abi: String
)

data class EngineCompatibilityResult(
    val compatible: Boolean,
    val reason: String = if (compatible) "Compatible" else "Incompatible"
)

data class EngineRegistrationResult(
    val registered: Boolean,
    val compatible: Boolean,
    val reason: String
)

data class EngineStatus(
    val descriptor: EngineDescriptor,
    val state: EngineState,
    val activeLeases: Int,
    val compatibility: EngineCompatibilityResult,
    val health: HealthStatus,
    val lastFailure: String? = null
)

sealed interface EngineAcquireResult {
    data class Acquired(val lease: EngineLease) : EngineAcquireResult

    data class Rejected(
        val engineId: String,
        val code: String,
        val reason: String
    ) : EngineAcquireResult
}

interface ToolBoxEngine {
    fun onLoad(scope: EngineRuntimeScope) = Unit
    fun onStart() = Unit
    fun onStop() = Unit
    fun onUnload() = Unit
    fun healthCheck(): HealthStatus = HealthStatus.ok()
}

interface EngineProvider {
    val descriptor: EngineDescriptor
    fun create(): ToolBoxEngine
}

class EngineLease internal constructor(
    val engineId: String,
    private val generation: Long,
    private val releaseAction: (String, Long) -> Unit
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            releaseAction(engineId, generation)
        }
    }
}
