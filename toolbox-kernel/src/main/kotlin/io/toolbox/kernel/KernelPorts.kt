package io.toolbox.kernel

import java.util.concurrent.ConcurrentHashMap

public interface KernelStateStore {
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

public interface KernelLogger {
    public fun debug(message: String): Unit = Unit
    public fun info(message: String): Unit = Unit
    public fun warn(message: String, error: Throwable? = null): Unit = Unit
    public fun error(message: String, error: Throwable? = null): Unit = Unit
}

public object NoopKernelLogger : KernelLogger

public fun interface KernelExecutor {
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

public fun interface CompatibilityPolicy {
    public fun check(config: KernelConfig, descriptor: ModuleDescriptor): CompatibilityResult
}

public object DefaultCompatibilityPolicy : CompatibilityPolicy {
    override fun check(config: KernelConfig, descriptor: ModuleDescriptor): CompatibilityResult {
        if (descriptor.apiVersion > config.moduleApiVersion) {
            return CompatibilityResult(false, "Module API ${descriptor.apiVersion} exceeds kernel API ${config.moduleApiVersion}")
        }
        if (descriptor.minAndroidApi > config.androidApiBaseline) {
            return CompatibilityResult(false, "Module requires Android API ${descriptor.minAndroidApi}")
        }
        if (descriptor.supportedArchitectures.isNotEmpty() && config.architectureBaseline !in descriptor.supportedArchitectures) {
            return CompatibilityResult(false, "Module does not support ${config.architectureBaseline}")
        }
        return CompatibilityResult(true)
    }
}

public fun interface ModuleAdmissionPolicy {
    public fun evaluate(descriptor: ModuleDescriptor, source: ModuleSource?): AdmissionDecision
}

public object AllowAllModuleAdmissionPolicy : ModuleAdmissionPolicy {
    override fun evaluate(descriptor: ModuleDescriptor, source: ModuleSource?): AdmissionDecision = AdmissionDecision(true)
}

public fun interface ModuleLoader {
    public fun load(source: ModuleSource): ToolBoxModule
}

public data class KernelPorts(
    public val stateStore: KernelStateStore = InMemoryKernelStateStore(),
    public val logger: KernelLogger = NoopKernelLogger,
    public val executor: KernelExecutor = DirectKernelExecutor,
    public val clock: KernelClock = SystemKernelClock,
    public val compatibilityPolicy: CompatibilityPolicy = DefaultCompatibilityPolicy,
    public val admissionPolicy: ModuleAdmissionPolicy = AllowAllModuleAdmissionPolicy
)
