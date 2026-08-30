package io.toolbox.kernel

import java.util.concurrent.ConcurrentHashMap

public interface KernelStateStore {
    /**
     * Replaces one key atomically. Durable implementations must never expose a torn value for a
     * single key because kernel recovery commits its canonical state record through one [put].
     */
    public fun put(key: String, value: String): Unit
    public fun get(key: String): String?
    public fun remove(key: String): Unit
    public fun keys(prefix: String = ""): Set<String>
}

public class InMemoryKernelStateStore : KernelStateStore {
    private val data: ConcurrentHashMap<String, String> = ConcurrentHashMap()

    override fun put(key: String, value: String): Unit {
        data[key] = value
    }

    override fun get(key: String): String? = data[key]

    override fun remove(key: String): Unit {
        data.remove(key)
    }

    override fun keys(prefix: String): Set<String> = data.keys.filterTo(linkedSetOf()) { it.startsWith(prefix) }
}

/** Logging is observational. Kernel correctness never depends on logger implementations behaving correctly. */
public interface KernelLogger {
    public fun debug(message: String): Unit = Unit
    public fun info(message: String): Unit = Unit
    public fun warn(message: String, error: Throwable? = null): Unit = Unit
    public fun error(message: String, error: Throwable? = null): Unit = Unit
}

public object NoopKernelLogger : KernelLogger

public fun interface KernelExecutor {
    /** Executes [task] synchronously and returns only after it completed or threw. */
    public fun execute(taskName: String, task: () -> Unit): Unit
}

public object DirectKernelExecutor : KernelExecutor {
    override fun execute(taskName: String, task: () -> Unit): Unit = task()
}

public fun interface KernelClock {
    public fun nowMillis(): Long
}

public object SystemKernelClock : KernelClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

/** Additional compatibility policy. Mandatory kernel compatibility is always evaluated first and cannot be bypassed. */
public fun interface CompatibilityPolicy {
    public fun check(
        config: KernelConfig,
        runtimeEnvironment: KernelRuntimeEnvironment,
        descriptor: ModuleDescriptor
    ): CompatibilityResult
}

public object DefaultCompatibilityPolicy : CompatibilityPolicy {
    override fun check(
        config: KernelConfig,
        runtimeEnvironment: KernelRuntimeEnvironment,
        descriptor: ModuleDescriptor
    ): CompatibilityResult = CompatibilityResult(true)
}

/**
 * Admission runs after compatibility and, for external modules, after source verification so policy
 * can use verified signer/fingerprint identity rather than trusting unverified source metadata.
 */
public fun interface ModuleAdmissionPolicy {
    public fun evaluate(
        descriptor: ModuleDescriptor,
        source: ModuleSource?,
        verifiedSource: VerifiedModuleSource?
    ): AdmissionDecision
}

public object AllowAllModuleAdmissionPolicy : ModuleAdmissionPolicy {
    override fun evaluate(
        descriptor: ModuleDescriptor,
        source: ModuleSource?,
        verifiedSource: VerifiedModuleSource?
    ): AdmissionDecision = AdmissionDecision(true)
}

/**
 * Copies an external source into a stable host-controlled artifact before any executable inspection
 * or verification. Android hosts should stage into app-private storage and make the executable
 * read-only/immutable before returning.
 */
public fun interface ModuleSourceStager {
    public fun stage(source: ModuleSource): StagedModuleSource
}

/** External loading is fail-closed until the host provides a trusted stager. */
public object RejectUnstagedModuleSourceStager : ModuleSourceStager {
    override fun stage(source: ModuleSource): StagedModuleSource =
        throw IllegalStateException("No ModuleSourceStager configured")
}

public fun interface ModuleSourceVerifier {
    /** Verifies the exact staged artifact that will later be loaded. */
    public fun verify(source: StagedModuleSource, descriptor: ModuleDescriptor): SourceVerificationResult
}

/** External executable loading is fail-closed until the host supplies an integrity verifier. */
public object RejectUnverifiedModuleSourceVerifier : ModuleSourceVerifier {
    override fun verify(source: StagedModuleSource, descriptor: ModuleDescriptor): SourceVerificationResult =
        SourceVerificationResult(false, reason = "No ModuleSourceVerifier configured")
}

public interface ModuleLoader {
    /** Reads metadata from the staged immutable artifact without intentionally executing module code. */
    public fun inspect(source: StagedModuleSource): ModuleDescriptor

    /** Loads executable code only from the exact staged artifact represented by [source]. */
    public fun load(source: VerifiedModuleSource, descriptor: ModuleDescriptor): ToolBoxModule
}

public data class KernelPorts(
    public val stateStore: KernelStateStore = InMemoryKernelStateStore(),
    public val logger: KernelLogger = NoopKernelLogger,
    public val executor: KernelExecutor = DirectKernelExecutor,
    public val clock: KernelClock = SystemKernelClock,
    public val runtimeEnvironment: KernelRuntimeEnvironment = KernelRuntimeEnvironment(30, "arm64-v8a", authoritative = false),
    public val compatibilityPolicy: CompatibilityPolicy = DefaultCompatibilityPolicy,
    public val admissionPolicy: ModuleAdmissionPolicy = AllowAllModuleAdmissionPolicy,
    public val sourceStager: ModuleSourceStager = RejectUnstagedModuleSourceStager,
    public val sourceVerifier: ModuleSourceVerifier = RejectUnverifiedModuleSourceVerifier
)

internal object MandatoryCompatibilityPolicy {
    internal fun check(
        config: KernelConfig,
        runtimeEnvironment: KernelRuntimeEnvironment,
        descriptor: ModuleDescriptor,
        requireAuthoritativeRuntime: Boolean
    ): CompatibilityResult {
        if (descriptor.apiVersion < config.minimumSupportedModuleApiVersion || descriptor.apiVersion > config.moduleApiVersion) {
            return CompatibilityResult(
                false,
                "Module API ${descriptor.apiVersion} is outside supported range ${config.minimumSupportedModuleApiVersion}..${config.moduleApiVersion}"
            )
        }
        if (!descriptor.supportsAndroidApi(config.androidApiBaseline)) {
            return CompatibilityResult(false, "Module does not support kernel target Android API ${config.androidApiBaseline}")
        }
        if (config.architectureBaseline !in descriptor.supportedAbis) {
            return CompatibilityResult(false, "Module does not support kernel target ABI ${config.architectureBaseline}")
        }
        if (requireAuthoritativeRuntime && !runtimeEnvironment.authoritative) {
            return CompatibilityResult(false, "Authoritative runtime environment is required before external executable loading")
        }
        if (runtimeEnvironment.authoritative) {
            if (runtimeEnvironment.androidApi != config.androidApiBaseline) {
                return CompatibilityResult(
                    false,
                    "Runtime Android API ${runtimeEnvironment.androidApi} does not match kernel target API ${config.androidApiBaseline}"
                )
            }
            if (runtimeEnvironment.abi != config.architectureBaseline) {
                return CompatibilityResult(
                    false,
                    "Runtime ABI ${runtimeEnvironment.abi} does not match kernel target ABI ${config.architectureBaseline}"
                )
            }
            if (!descriptor.supportsAndroidApi(runtimeEnvironment.androidApi)) {
                return CompatibilityResult(false, "Module does not support runtime Android API ${runtimeEnvironment.androidApi}")
            }
            if (runtimeEnvironment.abi !in descriptor.supportedAbis) {
                return CompatibilityResult(false, "Module does not support runtime ABI ${runtimeEnvironment.abi}")
            }
        }
        return CompatibilityResult(true)
    }

    private fun ModuleDescriptor.supportsAndroidApi(api: Int): Boolean =
        api >= minAndroidApi && (maxAndroidApi == null || api <= maxAndroidApi)
}

internal class SafeKernelLogger(private val delegate: KernelLogger) : KernelLogger {
    override fun debug(message: String): Unit = safe { delegate.debug(message) }
    override fun info(message: String): Unit = safe { delegate.info(message) }
    override fun warn(message: String, error: Throwable?): Unit = safe { delegate.warn(message, error) }
    override fun error(message: String, error: Throwable?): Unit = safe { delegate.error(message, error) }

    private inline fun safe(block: () -> Unit): Unit {
        try {
            block()
        } catch (_: Throwable) {
            // Logging must never become a kernel transaction dependency.
        }
    }
}

internal class SafeKernelClock(private val delegate: KernelClock) : KernelClock {
    override fun nowMillis(): Long = try {
        delegate.nowMillis()
    } catch (_: Throwable) {
        System.currentTimeMillis()
    }
}
