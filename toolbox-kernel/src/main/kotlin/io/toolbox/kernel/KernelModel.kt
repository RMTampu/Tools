package io.toolbox.kernel

enum class KernelState {
    NEW,
    STARTING,
    RUNNING,
    DEGRADED,
    STOPPING,
    STOPPED,
    FAILED
}

enum class ModuleState {
    REGISTERED,
    LOADED,
    STARTED,
    STOPPED,
    FAILED
}

data class KernelConfig(
    val name: String = "ToolBox",
    val version: String = "0.1.0",
    val moduleApiVersion: Int = 1,
    val androidApiBaseline: Int = 30,
    val architectureBaseline: String = "arm64-v8a"
)

data class ModuleDescriptor(
    val id: String,
    val name: String,
    val version: String,
    val apiVersion: Int = 1,
    val minAndroidApi: Int = 30,
    val supportedArchitectures: Set<String> = setOf("arm64-v8a"),
    val dependencies: Set<String> = emptySet()
)

data class CompatibilityResult(
    val compatible: Boolean,
    val reason: String = if (compatible) "Compatible" else "Incompatible"
)

data class AdmissionDecision(
    val allowed: Boolean,
    val reason: String = if (allowed) "Allowed" else "Rejected"
)

data class ModuleSource(
    val id: String,
    val location: String,
    val metadata: Map<String, String> = emptyMap()
)

data class HealthStatus(
    val healthy: Boolean,
    val message: String = if (healthy) "OK" else "FAILED"
) {
    companion object {
        fun ok(message: String = "OK") = HealthStatus(true, message)
        fun failed(message: String) = HealthStatus(false, message)
    }
}

data class ModuleHealth(
    val descriptor: ModuleDescriptor,
    val state: ModuleState,
    val health: HealthStatus
)

data class KernelSnapshot(
    val config: KernelConfig,
    val state: KernelState,
    val modules: List<ModuleHealth>,
    val registeredServices: Int,
    val registeredCapabilities: Int,
    val registeredCommands: Int
)

data class ModuleFailure(
    val moduleId: String,
    val phase: String,
    val cause: Throwable
)

data class KernelEvent(
    val topic: String,
    val source: String,
    val payload: Any? = null,
    val timestampMillis: Long = System.currentTimeMillis()
)

interface Capability {
    val id: String
    val version: Int
    val providerModuleId: String
}

interface KernelCommand {
    val name: String
}

data class CommandResult(
    val success: Boolean,
    val value: Any? = null,
    val error: Throwable? = null
) {
    companion object {
        fun success(value: Any? = null) = CommandResult(true, value, null)
        fun failure(error: Throwable) = CommandResult(false, null, error)
    }
}
