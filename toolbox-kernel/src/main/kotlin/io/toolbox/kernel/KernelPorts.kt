package io.toolbox.kernel

import java.util.concurrent.ConcurrentHashMap

interface KernelStateStore {
    fun put(key: String, value: String)
    fun get(key: String): String?
    fun remove(key: String)
    fun keys(prefix: String = ""): Set<String>
}

class InMemoryKernelStateStore : KernelStateStore {
    private val data = ConcurrentHashMap<String, String>()

    override fun put(key: String, value: String) {
        data[key] = value
    }

    override fun get(key: String): String? = data[key]

    override fun remove(key: String) {
        data.remove(key)
    }

    override fun keys(prefix: String): Set<String> = data.keys.filterTo(linkedSetOf()) { it.startsWith(prefix) }
}

interface KernelLogger {
    fun debug(message: String) = Unit
    fun info(message: String) = Unit
    fun warn(message: String, error: Throwable? = null) = Unit
    fun error(message: String, error: Throwable? = null) = Unit
}

object NoopKernelLogger : KernelLogger

fun interface KernelExecutor {
    fun execute(taskName: String, task: () -> Unit)
}

object DirectKernelExecutor : KernelExecutor {
    override fun execute(taskName: String, task: () -> Unit) = task()
}

fun interface CompatibilityPolicy {
    fun check(config: KernelConfig, descriptor: ModuleDescriptor): CompatibilityResult
}

object DefaultCompatibilityPolicy : CompatibilityPolicy {
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

fun interface ModuleAdmissionPolicy {
    fun evaluate(descriptor: ModuleDescriptor, source: ModuleSource?): AdmissionDecision
}

object AllowAllModuleAdmissionPolicy : ModuleAdmissionPolicy {
    override fun evaluate(descriptor: ModuleDescriptor, source: ModuleSource?): AdmissionDecision = AdmissionDecision(true)
}

fun interface ModuleLoader {
    fun load(source: ModuleSource): ToolBoxModule
}

data class KernelPorts(
    val stateStore: KernelStateStore = InMemoryKernelStateStore(),
    val logger: KernelLogger = NoopKernelLogger,
    val executor: KernelExecutor = DirectKernelExecutor,
    val compatibilityPolicy: CompatibilityPolicy = DefaultCompatibilityPolicy,
    val admissionPolicy: ModuleAdmissionPolicy = AllowAllModuleAdmissionPolicy
)
