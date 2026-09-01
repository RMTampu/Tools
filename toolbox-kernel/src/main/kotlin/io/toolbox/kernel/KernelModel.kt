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

class ModuleDescriptor(
    val id: String,
    val name: String,
    val version: String,
    val apiVersion: Int = 1,
    val minAndroidApi: Int = 30,
    supportedArchitectures: Set<String> = setOf("arm64-v8a"),
    dependencies: Set<String> = emptySet()
) {
    val supportedArchitectures: Set<String> = supportedArchitectures.toSet()
    val dependencies: Set<String> = dependencies.toSet()

    init {
        require(id.isNotBlank()) { "Module id cannot be blank" }
        require(id.none(Char::isWhitespace)) { "Module id cannot contain whitespace" }
        require(name.isNotBlank()) { "Module name cannot be blank" }
        require(version.isNotBlank()) { "Module version cannot be blank" }
        require(apiVersion > 0) { "Module API version must be positive" }
        require(minAndroidApi > 0) { "Module min Android API must be positive" }
        require(this.supportedArchitectures.none(String::isBlank)) { "Supported architecture cannot be blank" }
        require(this.supportedArchitectures.none { it.any(Char::isWhitespace) }) {
            "Supported architecture cannot contain whitespace"
        }
        require(this.dependencies.none(String::isBlank)) { "Module dependency id cannot be blank" }
        require(this.dependencies.none { it.any(Char::isWhitespace) }) {
            "Module dependency id cannot contain whitespace"
        }
        require(id !in this.dependencies) { "Module cannot depend on itself: $id" }
    }

    fun copy(
        id: String = this.id,
        name: String = this.name,
        version: String = this.version,
        apiVersion: Int = this.apiVersion,
        minAndroidApi: Int = this.minAndroidApi,
        supportedArchitectures: Set<String> = this.supportedArchitectures,
        dependencies: Set<String> = this.dependencies
    ): ModuleDescriptor = ModuleDescriptor(
        id = id,
        name = name,
        version = version,
        apiVersion = apiVersion,
        minAndroidApi = minAndroidApi,
        supportedArchitectures = supportedArchitectures,
        dependencies = dependencies
    )

    operator fun component1(): String = id
    operator fun component2(): String = name
    operator fun component3(): String = version
    operator fun component4(): Int = apiVersion
    operator fun component5(): Int = minAndroidApi
    operator fun component6(): Set<String> = supportedArchitectures
    operator fun component7(): Set<String> = dependencies

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is ModuleDescriptor &&
                id == other.id &&
                name == other.name &&
                version == other.version &&
                apiVersion == other.apiVersion &&
                minAndroidApi == other.minAndroidApi &&
                supportedArchitectures == other.supportedArchitectures &&
                dependencies == other.dependencies)

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + version.hashCode()
        result = 31 * result + apiVersion
        result = 31 * result + minAndroidApi
        result = 31 * result + supportedArchitectures.hashCode()
        result = 31 * result + dependencies.hashCode()
        return result
    }

    override fun toString(): String =
        "ModuleDescriptor(id=$id, name=$name, version=$version, apiVersion=$apiVersion, " +
            "minAndroidApi=$minAndroidApi, supportedArchitectures=$supportedArchitectures, " +
            "dependencies=$dependencies)"
}

data class CompatibilityResult(
    val compatible: Boolean,
    val reason: String = if (compatible) "Compatible" else "Incompatible"
)

data class AdmissionDecision(
    val allowed: Boolean,
    val reason: String = if (allowed) "Allowed" else "Rejected"
)

class ModuleSource(
    val descriptor: ModuleDescriptor,
    val location: String,
    metadata: Map<String, String> = emptyMap()
) {
    val metadata: Map<String, String> = metadata.toMap()
    val id: String get() = descriptor.id

    init {
        require(location.isNotBlank()) { "Module source location cannot be blank" }
        require(this.metadata.keys.none(String::isBlank)) { "Module source metadata key cannot be blank" }
    }

    fun copy(
        descriptor: ModuleDescriptor = this.descriptor,
        location: String = this.location,
        metadata: Map<String, String> = this.metadata
    ): ModuleSource = ModuleSource(
        descriptor = descriptor,
        location = location,
        metadata = metadata
    )

    operator fun component1(): ModuleDescriptor = descriptor
    operator fun component2(): String = location
    operator fun component3(): Map<String, String> = metadata

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is ModuleSource &&
                descriptor == other.descriptor &&
                location == other.location &&
                metadata == other.metadata)

    override fun hashCode(): Int {
        var result = descriptor.hashCode()
        result = 31 * result + location.hashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }

    override fun toString(): String =
        "ModuleSource(descriptor=$descriptor, location=$location, metadata=$metadata)"
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
