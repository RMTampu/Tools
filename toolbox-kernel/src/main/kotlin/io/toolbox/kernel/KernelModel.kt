package io.toolbox.kernel

import java.util.Collections
import java.util.LinkedHashMap
import java.util.LinkedHashSet

public enum class KernelState {
    NEW,
    STARTING,
    RUNNING,
    DEGRADED,
    STOPPING,
    STOPPED,
    STOPPED_WITH_ERRORS,
    FAILED
}

public enum class ModuleState {
    REGISTERED,
    LOADING,
    LOADED,
    STARTING,
    STARTED,
    QUIESCING,
    STOPPING,
    UNLOADING,
    STOPPED,
    FAILED,
    QUARANTINED
}

public enum class HealthState {
    UNKNOWN,
    HEALTHY,
    UNHEALTHY
}

public enum class LifecyclePhase {
    RESOLUTION,
    LOAD,
    START,
    QUIESCE,
    STOP,
    UNLOAD,
    HEALTH,
    SOURCE_STAGING,
    SOURCE_INSPECTION,
    SOURCE_VERIFICATION,
    SOURCE_LOAD
}

public enum class DependencyKind {
    REQUIRED,
    OPTIONAL
}

public data class KernelConfig(
    public val kernelId: String = "toolbox",
    public val name: String = "ToolBox",
    public val version: String = "0.3.0",
    public val moduleApiVersion: Int = 1,
    public val minimumSupportedModuleApiVersion: Int = 1,
    public val androidApiBaseline: Int = 30,
    public val architectureBaseline: String = "arm64-v8a",
    public val lifecycleTimeoutMillis: Long = 10_000,
    public val healthTimeoutMillis: Long = 5_000,
    public val commandTimeoutMillis: Long = 10_000,
    public val eventListenerTimeoutMillis: Long = 5_000,
    public val invocationDrainTimeoutMillis: Long = 5_000
) {
    init {
        require(KernelIdentifiers.isValid(kernelId)) { "Kernel id is invalid: $kernelId" }
        require(name.isNotBlank()) { "Kernel name cannot be blank" }
        require(version.isNotBlank()) { "Kernel version cannot be blank" }
        require(moduleApiVersion > 0) { "Kernel module API version must be positive" }
        require(minimumSupportedModuleApiVersion > 0) { "Minimum supported module API version must be positive" }
        require(minimumSupportedModuleApiVersion <= moduleApiVersion) { "Minimum supported module API version cannot exceed current module API version" }
        require(androidApiBaseline > 0) { "Kernel Android API baseline must be positive" }
        require(architectureBaseline.isNotBlank()) { "Kernel architecture baseline cannot be blank" }
        require(lifecycleTimeoutMillis > 0) { "Lifecycle timeout must be positive" }
        require(healthTimeoutMillis > 0) { "Health timeout must be positive" }
        require(commandTimeoutMillis > 0) { "Command timeout must be positive" }
        require(eventListenerTimeoutMillis > 0) { "Event listener timeout must be positive" }
        require(invocationDrainTimeoutMillis > 0) { "Invocation drain timeout must be positive" }
    }
}

public data class KernelRuntimeEnvironment(
    public val androidApi: Int,
    public val abi: String,
    public val authoritative: Boolean
) {
    init {
        require(androidApi > 0) { "Runtime Android API must be positive" }
        require(abi.isNotBlank()) { "Runtime ABI cannot be blank" }
    }

    public companion object {
        public fun unknownForTarget(config: KernelConfig): KernelRuntimeEnvironment =
            KernelRuntimeEnvironment(config.androidApiBaseline, config.architectureBaseline, authoritative = false)

        public fun authoritative(androidApi: Int, abi: String): KernelRuntimeEnvironment =
            KernelRuntimeEnvironment(androidApi, abi, authoritative = true)
    }
}

public data class ModuleVersion(
    public val major: Int,
    public val minor: Int = 0,
    public val patch: Int = 0,
    public val qualifier: String? = null
) : Comparable<ModuleVersion> {
    init {
        require(major >= 0 && minor >= 0 && patch >= 0) { "Version numbers cannot be negative" }
        require(qualifier == null || QUALIFIER.matches(qualifier)) { "Invalid version qualifier: $qualifier" }
    }

    override fun compareTo(other: ModuleVersion): Int {
        major.compareTo(other.major).takeIf { it != 0 }?.let { return it }
        minor.compareTo(other.minor).takeIf { it != 0 }?.let { return it }
        patch.compareTo(other.patch).takeIf { it != 0 }?.let { return it }
        return when {
            qualifier == other.qualifier -> 0
            qualifier == null -> 1
            other.qualifier == null -> -1
            else -> qualifier.compareTo(other.qualifier)
        }
    }

    override fun toString(): String = buildString {
        append(major).append('.').append(minor).append('.').append(patch)
        qualifier?.let { append('-').append(it) }
    }

    public companion object {
        private val VERSION = Regex("^(0|[1-9][0-9]*)(?:\\.(0|[1-9][0-9]*))?(?:\\.(0|[1-9][0-9]*))?(?:-([0-9A-Za-z][0-9A-Za-z.-]*))?$")
        private val QUALIFIER = Regex("^[0-9A-Za-z][0-9A-Za-z.-]*$")

        public fun parse(value: String): ModuleVersion {
            val match = VERSION.matchEntire(value.trim()) ?: throw IllegalArgumentException("Invalid module version: $value")
            return ModuleVersion(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 0,
                patch = match.groupValues[3].takeIf { it.isNotEmpty() }?.toInt() ?: 0,
                qualifier = match.groupValues[4].takeIf { it.isNotEmpty() }
            )
        }
    }
}

public data class VersionRange(
    public val minimum: ModuleVersion? = null,
    public val maximum: ModuleVersion? = null,
    public val includeMinimum: Boolean = true,
    public val includeMaximum: Boolean = false
) {
    init {
        if (minimum != null && maximum != null) {
            require(minimum <= maximum) { "Version range minimum cannot exceed maximum" }
            require(minimum != maximum || (includeMinimum && includeMaximum)) { "Empty exact version range is not allowed" }
        }
    }

    public fun contains(version: ModuleVersion): Boolean {
        if (minimum != null) {
            val comparison = version.compareTo(minimum)
            if (comparison < 0 || (comparison == 0 && !includeMinimum)) return false
        }
        if (maximum != null) {
            val comparison = version.compareTo(maximum)
            if (comparison > 0 || (comparison == 0 && !includeMaximum)) return false
        }
        return true
    }

    public companion object {
        public fun any(): VersionRange = VersionRange()
        public fun atLeast(minimum: ModuleVersion): VersionRange = VersionRange(minimum = minimum)
        public fun exact(version: ModuleVersion): VersionRange = VersionRange(version, version, includeMinimum = true, includeMaximum = true)
        public fun between(
            minimum: ModuleVersion,
            maximum: ModuleVersion,
            includeMinimum: Boolean = true,
            includeMaximum: Boolean = false
        ): VersionRange = VersionRange(minimum, maximum, includeMinimum, includeMaximum)
    }
}

public data class ModuleDependency(
    public val id: String,
    public val versionRange: VersionRange = VersionRange.any(),
    public val kind: DependencyKind = DependencyKind.REQUIRED
) {
    init {
        require(KernelIdentifiers.isValid(id)) { "Dependency id is invalid: $id" }
    }

    public companion object {
        public fun required(id: String, versionRange: VersionRange = VersionRange.any()): ModuleDependency =
            ModuleDependency(id, versionRange, DependencyKind.REQUIRED)

        public fun optional(id: String, versionRange: VersionRange = VersionRange.any()): ModuleDependency =
            ModuleDependency(id, versionRange, DependencyKind.OPTIONAL)
    }
}

public data class CapabilityDeclaration(
    public val id: String,
    public val version: ModuleVersion
) {
    init {
        require(KernelIdentifiers.isValid(id)) { "Capability id is invalid: $id" }
    }
}

public data class CapabilityRequirement(
    public val id: String,
    public val versionRange: VersionRange = VersionRange.any(),
    public val kind: DependencyKind = DependencyKind.REQUIRED
) {
    init {
        require(KernelIdentifiers.isValid(id)) { "Capability requirement id is invalid: $id" }
    }

    public companion object {
        public fun required(id: String, versionRange: VersionRange = VersionRange.any()): CapabilityRequirement =
            CapabilityRequirement(id, versionRange, DependencyKind.REQUIRED)

        public fun optional(id: String, versionRange: VersionRange = VersionRange.any()): CapabilityRequirement =
            CapabilityRequirement(id, versionRange, DependencyKind.OPTIONAL)
    }
}

public data class ModuleDescriptor(
    public val id: String,
    public val name: String,
    public val version: ModuleVersion,
    public val apiVersion: Int = 1,
    public val minAndroidApi: Int = 30,
    public val maxAndroidApi: Int? = null,
    public val supportedAbis: Set<String> = setOf("arm64-v8a"),
    public val providedCapabilities: Set<CapabilityDeclaration> = emptySet(),
    public val requiredCapabilities: Set<CapabilityRequirement> = emptySet(),
    public val entryPoint: String = id,
    public val dependencies: Set<ModuleDependency> = emptySet()
) {
    public constructor(
        id: String,
        name: String,
        version: String,
        apiVersion: Int = 1,
        minAndroidApi: Int = 30,
        maxAndroidApi: Int? = null,
        supportedAbis: Set<String> = setOf("arm64-v8a"),
        providedCapabilities: Set<CapabilityDeclaration> = emptySet(),
        requiredCapabilities: Set<CapabilityRequirement> = emptySet(),
        entryPoint: String = id,
        dependencies: Set<ModuleDependency> = emptySet()
    ) : this(
        id,
        name,
        ModuleVersion.parse(version),
        apiVersion,
        minAndroidApi,
        maxAndroidApi,
        supportedAbis,
        providedCapabilities,
        requiredCapabilities,
        entryPoint,
        dependencies
    )
}

internal fun ModuleDescriptor.snapshot(): ModuleDescriptor = copy(
    supportedAbis = immutableSet(supportedAbis),
    providedCapabilities = immutableSet(providedCapabilities.map { it.copy() }.toSet()),
    requiredCapabilities = immutableSet(requiredCapabilities.map { it.copy() }.toSet()),
    dependencies = immutableSet(dependencies.map { it.copy() }.toSet())
)

internal fun ModuleDescriptor.validationError(): String? {
    if (!KernelIdentifiers.isValid(id)) return "Module id is invalid: $id"
    if (name.isBlank()) return "Module name cannot be blank"
    if (apiVersion <= 0) return "Module API version must be positive"
    if (minAndroidApi <= 0) return "Minimum Android API must be positive"
    if (maxAndroidApi != null && maxAndroidApi < minAndroidApi) return "Maximum Android API cannot be below minimum Android API"
    if (supportedAbis.isEmpty()) return "Module must declare at least one supported ABI"
    if (supportedAbis.any { it.isBlank() }) return "ABI name cannot be blank"
    if (entryPoint.isBlank()) return "Module entry point cannot be blank"
    if (dependencies.any { it.id == id }) return "Module cannot depend on itself: $id"
    if (dependencies.groupBy { it.id }.any { it.value.size > 1 }) return "Module dependency ids must be unique"
    if (providedCapabilities.groupBy { it.id }.any { it.value.size > 1 }) return "Provided capability ids must be unique"
    if (requiredCapabilities.groupBy { it.id }.any { it.value.size > 1 }) return "Required capability ids must be unique"
    if (providedCapabilities.map { it.id }.intersect(requiredCapabilities.map { it.id }.toSet()).isNotEmpty()) {
        return "A module cannot require a capability that it provides itself"
    }
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

internal fun ModuleSource.snapshot(): ModuleSource = copy(metadata = immutableMap(metadata))

internal fun ModuleSource.validationError(): String? {
    if (!KernelIdentifiers.isValid(id)) return "Module source id is invalid: $id"
    if (location.isBlank()) return "Module source location cannot be blank"
    return null
}

/**
 * Host-created stable artifact. For Android the host should stage into app-private storage and make
 * the executable artifact immutable/read-only before inspection, verification, or loading.
 */
public data class StagedModuleSource(
    public val sourceId: String,
    public val artifactId: String,
    public val location: String,
    public val metadata: Map<String, String> = emptyMap(),
    public val immutable: Boolean
)

internal fun StagedModuleSource.snapshot(): StagedModuleSource = copy(metadata = immutableMap(metadata))

internal fun StagedModuleSource.validationError(): String? {
    if (!KernelIdentifiers.isValid(sourceId)) return "Staged source id is invalid: $sourceId"
    if (artifactId.isBlank()) return "Staged artifact id cannot be blank"
    if (location.isBlank()) return "Staged artifact location cannot be blank"
    if (!immutable) return "Staged executable artifact must be immutable before verification/loading"
    return null
}

public data class VerifiedModuleSource internal constructor(
    public val stagedSource: StagedModuleSource,
    public val fingerprint: String,
    public val verifiedAtMillis: Long,
    public val algorithm: String,
    public val signerId: String?,
    public val policyId: String
)

public data class SourceVerificationResult(
    public val verified: Boolean,
    public val fingerprint: String = "",
    public val reason: String = if (verified) "Verified" else "Unverified",
    public val algorithm: String = "unspecified",
    public val signerId: String? = null,
    public val policyId: String = "default"
)

public data class HealthStatus(
    public val state: HealthState,
    public val message: String,
    public val checkedAtMillis: Long? = null,
    public val cause: Throwable? = null
) {
    public val healthy: Boolean get() = state == HealthState.HEALTHY

    public companion object {
        public fun unknown(message: String = "UNKNOWN"): HealthStatus = HealthStatus(HealthState.UNKNOWN, message)
        public fun ok(message: String = "OK", checkedAtMillis: Long? = null): HealthStatus =
            HealthStatus(HealthState.HEALTHY, message, checkedAtMillis)
        public fun failed(message: String, checkedAtMillis: Long? = null, cause: Throwable? = null): HealthStatus =
            HealthStatus(HealthState.UNHEALTHY, message, checkedAtMillis, cause)
    }
}

public data class ModuleFailure(
    public val moduleId: String,
    public val phase: LifecyclePhase,
    public val cause: Throwable,
    public val fromState: ModuleState? = null
)

public data class ModuleHealth(
    public val descriptor: ModuleDescriptor,
    public val state: ModuleState,
    public val health: HealthStatus,
    public val lastFailure: ModuleFailure? = null
)

public data class KernelSnapshot(
    public val config: KernelConfig,
    public val runtimeEnvironment: KernelRuntimeEnvironment,
    public val state: KernelState,
    public val previousPersistedState: KernelState?,
    public val sessionId: String,
    public val revision: Long,
    public val consistent: Boolean,
    public val modules: List<ModuleHealth>,
    public val registeredServices: Int,
    public val registeredCapabilities: Int,
    public val registeredCommands: Int,
    public val eventSubscriptions: Int
)

public enum class KernelErrorCode {
    INVALID_STATE,
    INVALID_DESCRIPTOR,
    INCOMPATIBLE_MODULE,
    RUNTIME_ENVIRONMENT_REQUIRED,
    ADMISSION_REJECTED,
    POLICY_FAILURE,
    CONFLICT,
    OPERATION_IN_PROGRESS,
    DEPENDENCY_RESOLUTION,
    CAPABILITY_RESOLUTION,
    LIFECYCLE,
    TIMEOUT,
    QUARANTINED,
    SOURCE_MISMATCH,
    SOURCE_STAGING,
    SOURCE_INSPECTION,
    SOURCE_VERIFICATION,
    SOURCE_LOAD,
    NOT_FOUND
}

public data class KernelError(
    public val code: KernelErrorCode,
    public val message: String,
    public val cause: Throwable? = null
)

public class KernelResult<T> private constructor(
    public val value: T?,
    public val errors: List<KernelError>,
    public val failures: List<ModuleFailure>
) {
    public val isSuccess: Boolean get() = errors.isEmpty() && failures.isEmpty()

    public companion object {
        public fun <T> success(value: T): KernelResult<T> = KernelResult(value, emptyList(), emptyList())

        public fun <T> failure(error: KernelError, failures: List<ModuleFailure> = emptyList()): KernelResult<T> =
            KernelResult(null, listOf(error), failures.toList())

        public fun <T> failure(errors: List<KernelError>, failures: List<ModuleFailure> = emptyList()): KernelResult<T> {
            require(errors.isNotEmpty() || failures.isNotEmpty()) { "Failure result must contain an error or module failure" }
            val normalizedErrors = if (errors.isEmpty()) listOf(KernelError(KernelErrorCode.LIFECYCLE, "Module lifecycle operation failed")) else errors.toList()
            return KernelResult(null, normalizedErrors, failures.toList())
        }

        public fun <T> lifecycleFailure(failures: List<ModuleFailure>): KernelResult<T> {
            require(failures.isNotEmpty()) { "Lifecycle failure must contain at least one module failure" }
            return KernelResult(
                null,
                listOf(KernelError(KernelErrorCode.LIFECYCLE, "Module lifecycle operation failed")),
                failures.toList()
            )
        }
    }
}

public data class KernelEvent(
    public val topic: String,
    public val source: String,
    public val payload: Any? = null,
    public val timestampMillis: Long
)

public object KernelTopics {
    public const val MODULE_INSTALLED: String = "kernel.module.installed"
    public const val MODULE_UNINSTALLED: String = "kernel.module.uninstalled"
    public const val MODULE_REJECTED: String = "kernel.module.rejected"
    public const val MODULE_ACTIVATION_FAILED: String = "kernel.module.activation.failed"
    public const val STARTING: String = "kernel.starting"
    public const val START_COMPLETED: String = "kernel.start.completed"
    public const val START_FAILED: String = "kernel.start.failed"
    public const val STOPPING: String = "kernel.stopping"
    public const val STOP_COMPLETED: String = "kernel.stop.completed"
    public const val STOP_FAILED: String = "kernel.stop.failed"
}

public interface Capability {
    public val id: String
    public val version: ModuleVersion
    public val providerModuleId: String
}

public data class ServiceKey<T : Any>(
    public val type: Class<T>,
    public val qualifier: String = "default"
) {
    init {
        require(KernelIdentifiers.isValid(qualifier)) { "Service qualifier is invalid: $qualifier" }
    }
}

public interface KernelCommand {
    public val name: String
}

public class CommandResult private constructor(
    public val success: Boolean,
    public val value: Any?,
    public val error: Throwable?
) {
    public companion object {
        public fun success(value: Any? = null): CommandResult = CommandResult(true, value, null)
        public fun failure(error: Throwable): CommandResult = CommandResult(false, null, error)
    }
}

internal object KernelIdentifiers {
    private val IDENTIFIER = Regex("^[a-z0-9]+(?:[._:-][a-z0-9]+)*$")
    internal fun isValid(value: String): Boolean = IDENTIFIER.matches(value)
    internal fun requireValid(value: String, label: String): Unit = require(isValid(value)) { "$label is invalid: $value" }
}

private fun <T> immutableSet(source: Set<T>): Set<T> = Collections.unmodifiableSet(LinkedHashSet(source))
private fun <K, V> immutableMap(source: Map<K, V>): Map<K, V> = Collections.unmodifiableMap(LinkedHashMap(source))
