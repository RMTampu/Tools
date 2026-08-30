package io.toolbox.kernel

public enum class KernelState {
    NEW,
    STARTING,
    RUNNING,
    DEGRADED,
    STOPPING,
    STOPPED,
    FAILED
}

public enum class ModuleState {
    REGISTERED,
    LOADING,
    LOADED,
    STARTING,
    STARTED,
    STOPPING,
    STOPPED,
    UNLOADING,
    FAILED
}

public enum class HealthState {
    UNKNOWN,
    HEALTHY,
    UNHEALTHY
}

public data class KernelConfig(
    public val name: String = "ToolBox",
    public val version: String = "0.2.0",
    public val moduleApiVersion: Int = 1,
    public val androidApiBaseline: Int = 30,
    public val architectureBaseline: String = "arm64-v8a"
)

public data class ModuleDependency(
    public val id: String,
    public val optional: Boolean = false
) {
    init {
        require(id.isNotBlank()) { "Dependency id cannot be blank" }
    }

    public companion object {
        public fun required(id: String): ModuleDependency = ModuleDependency(id, optional = false)
        public fun optional(id: String): ModuleDependency = ModuleDependency(id, optional = true)
    }
}

public data class ModuleDescriptor(
    public val id: String,
    public val name: String,
    public val version: String,
    public val apiVersion: Int = 1,
    public val minAndroidApi: Int = 30,
    public val supportedArchitectures: Set<String> = setOf("arm64-v8a"),
    public val dependencies: Set<ModuleDependency> = emptySet()
)

internal fun ModuleDescriptor.snapshot(): ModuleDescriptor = copy(
    supportedArchitectures = supportedArchitectures.toSet(),
    dependencies = dependencies.map { it.copy() }.toSet()
)

internal fun ModuleDescriptor.validationError(): String? {
    if (id.isBlank()) return "Module id cannot be blank"
    if (name.isBlank()) return "Module name cannot be blank"
    if (version.isBlank()) return "Module version cannot be blank"
    if (apiVersion <= 0) return "Module API version must be positive"
    if (minAndroidApi <= 0) return "Minimum Android API must be positive"
    if (supportedArchitectures.any { it.isBlank() }) return "Architecture name cannot be blank"
    if (dependencies.any { it.id == id }) return "Module cannot depend on itself: $id"
    return null
}

public data class CompatibilityResult(
    public val compatible: Boolean,
    public val reason: String = if (compatible) "Compatible" else "Incompatible"
)

public data class AdmissionDecision(
    public val allowed: Boolean,
    public val reason: String = if (allowed) "Allowed" else "Rejected"
)

public data class ModuleSource(
    public val id: String,
    public val location: String,
    public val metadata: Map<String, String> = emptyMap()
)

public data class HealthStatus(
    public val state: HealthState,
    public val message: String
) {
    public val healthy: Boolean get() = state == HealthState.HEALTHY

    public companion object {
        public fun unknown(message: String = "UNKNOWN"): HealthStatus = HealthStatus(HealthState.UNKNOWN, message)
        public fun ok(message: String = "OK"): HealthStatus = HealthStatus(HealthState.HEALTHY, message)
        public fun failed(message: String): HealthStatus = HealthStatus(HealthState.UNHEALTHY, message)
    }
}

public data class ModuleHealth(
    public val descriptor: ModuleDescriptor,
    public val state: ModuleState,
    public val health: HealthStatus
)

public data class KernelSnapshot(
    public val config: KernelConfig,
    public val state: KernelState,
    public val previousPersistedState: KernelState?,
    public val modules: List<ModuleHealth>,
    public val registeredServices: Int,
    public val registeredCapabilities: Int,
    public val registeredCommands: Int,
    public val eventSubscriptions: Int
)

public data class ModuleFailure(
    public val moduleId: String,
    public val phase: String,
    public val cause: Throwable
)

public enum class KernelErrorCode {
    INVALID_STATE,
    INVALID_DESCRIPTOR,
    INCOMPATIBLE_MODULE,
    ADMISSION_REJECTED,
    CONFLICT,
    DEPENDENCY_RESOLUTION,
    LIFECYCLE,
    SOURCE_MISMATCH,
    NOT_FOUND,
    SOURCE_LOAD
}

public data class KernelError(
    public val code: KernelErrorCode,
    public val message: String,
    public val cause: Throwable? = null
)

public data class KernelResult<T>(
    public val value: T?,
    public val errors: List<KernelError> = emptyList(),
    public val failures: List<ModuleFailure> = emptyList()
) {
    public val isSuccess: Boolean get() = errors.isEmpty() && failures.isEmpty()

    public companion object {
        public fun <T> success(value: T): KernelResult<T> = KernelResult(value = value)

        public fun <T> failure(
            error: KernelError,
            failures: List<ModuleFailure> = emptyList()
        ): KernelResult<T> = KernelResult(value = null, errors = listOf(error), failures = failures)

        public fun <T> lifecycleFailure(failures: List<ModuleFailure>): KernelResult<T> = KernelResult(
            value = null,
            errors = listOf(KernelError(KernelErrorCode.LIFECYCLE, "Module lifecycle operation failed")),
            failures = failures
        )
    }
}

public data class KernelEvent(
    public val topic: String,
    public val source: String,
    public val payload: Any? = null,
    public val timestampMillis: Long
)

public interface Capability {
    public val id: String
    public val version: Int
    public val providerModuleId: String
}

public interface KernelCommand {
    public val name: String
}

public data class CommandResult(
    public val success: Boolean,
    public val value: Any? = null,
    public val error: Throwable? = null
) {
    public companion object {
        public fun success(value: Any? = null): CommandResult = CommandResult(true, value, null)
        public fun failure(error: Throwable): CommandResult = CommandResult(false, null, error)
    }
}
