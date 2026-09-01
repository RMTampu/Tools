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
) {
    init {
        require(name.isNotBlank()) { "Kernel name cannot be blank" }
        require(version.isNotBlank()) { "Kernel version cannot be blank" }
        require(moduleApiVersion > 0) { "Kernel module API version must be positive" }
        require(androidApiBaseline > 0) { "Kernel Android API baseline must be positive" }
        require(architectureBaseline.isNotBlank()) { "Kernel architecture baseline cannot be blank" }
    }
}

data class ModuleDescriptor(
    val id: String,
    val name: String,
    val version: String,
    val apiVersion: Int = 1,
    val minAndroidApi: Int = 30,
    val supportedArchitectures: Set<String> = setOf("arm64-v8a"),
    val dependencies: Set<String> = emptySet()
) {
    init {
        require(id.isNotBlank()) { "Module id cannot be blank" }
        require(id.none(Char::isWhitespace)) { "Module id cannot contain whitespace" }
        require(name.isNotBlank()) { "Module name cannot be blank" }
        require(version.isNotBlank()) { "Module version cannot be blank" }
        require(apiVersion > 0) { "Module API version must be positive" }
        require(minAndroidApi > 0) { "Module min Android API must be positive" }
        require(supportedArchitectures.none(String::isBlank)) { "Supported architecture cannot be blank" }
        require(dependencies.none(String::isBlank)) { "Module dependency id cannot be blank" }
        require(id !in dependencies) { "Module cannot depend on itself: $id" }
    }
}

data class CompatibilityResult(
    val compatible: Boolean,
    val reason: String = if (compatible) "Compatible" else "Incompatible"
)

data class AdmissionDecision(
    val allowed: Boolean,
    val reason: String = if (allowed) "Allowed" else "Rejected"
)

data class ModuleSource(
    val descriptor: ModuleDescriptor,
    val location: String,
    val metadata: Map<String, String> = emptyMap()
) {
    val id: String get() = descriptor.id

    init {
        require(location.isNotBlank()) { "Module source location cannot be blank" }
        require(metadata.keys.none(String::isBlank)) { "Module source metadata key cannot be blank" }
    }
}

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
) {
    init {
        require(topic.isNotBlank()) { "Event topic cannot be blank" }
        require(source.isNotBlank()) { "Event source cannot be blank" }
    }
}

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
