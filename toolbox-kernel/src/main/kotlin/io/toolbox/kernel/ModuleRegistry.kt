package io.toolbox.kernel

internal data class ModuleHandle(
    val module: ToolBoxModule,
    val descriptor: ModuleDescriptor,
    val state: ModuleState,
    val health: HealthStatus
)

internal class ModuleRegistry {
    private data class Record(
        val module: ToolBoxModule,
        val descriptor: ModuleDescriptor,
        var state: ModuleState = ModuleState.REGISTERED,
        var health: HealthStatus = HealthStatus.unknown()
    )

    private val lock = Any()
    private val records = linkedMapOf<String, Record>()

    internal fun register(module: ToolBoxModule, descriptor: ModuleDescriptor): Unit = synchronized(lock) {
        check(descriptor.id !in records) { "Module already installed: ${descriptor.id}" }
        records[descriptor.id] = Record(module, descriptor)
    }

    internal fun contains(moduleId: String): Boolean = synchronized(lock) { moduleId in records }

    internal fun handle(moduleId: String): ModuleHandle? = synchronized(lock) {
        records[moduleId]?.let { ModuleHandle(it.module, it.descriptor, it.state, it.health) }
    }

    internal fun descriptors(): List<ModuleDescriptor> = synchronized(lock) { records.values.map { it.descriptor } }

    internal fun stateOf(moduleId: String): ModuleState? = synchronized(lock) { records[moduleId]?.state }

    internal fun healthSnapshot(): List<ModuleHealth> = synchronized(lock) {
        records.values.map { ModuleHealth(it.descriptor, it.state, it.health) }
    }

    internal fun transition(moduleId: String, expected: Set<ModuleState>, next: ModuleState): Boolean = synchronized(lock) {
        val record = records[moduleId] ?: return@synchronized false
        if (record.state !in expected) return@synchronized false
        record.state = next
        true
    }

    internal fun forceState(moduleId: String, next: ModuleState): Unit = synchronized(lock) {
        records[moduleId]?.state = next
    }

    internal fun setHealth(moduleId: String, health: HealthStatus): Unit = synchronized(lock) {
        records[moduleId]?.health = health
    }

    internal fun remove(moduleId: String): ModuleHandle? = synchronized(lock) {
        records.remove(moduleId)?.let { ModuleHandle(it.module, it.descriptor, it.state, it.health) }
    }

    internal fun requiredDependents(moduleId: String): List<String> = synchronized(lock) {
        records.values.filter { record ->
            record.descriptor.dependencies.any { dependency -> dependency.id == moduleId && !dependency.optional }
        }.map { it.descriptor.id }
    }

    internal fun resolvePlan(): KernelResult<List<String>> = synchronized(lock) {
        val visiting = linkedSetOf<String>()
        val visited = linkedSetOf<String>()
        val result = mutableListOf<String>()

        fun visit(moduleId: String): KernelError? {
            if (moduleId in visited) return null
            if (moduleId in visiting) {
                return KernelError(KernelErrorCode.DEPENDENCY_RESOLUTION, "Module dependency cycle detected at $moduleId")
            }
            val record = records[moduleId]
                ?: return KernelError(KernelErrorCode.DEPENDENCY_RESOLUTION, "Missing module dependency: $moduleId")
            visiting += moduleId
            record.descriptor.dependencies.forEach { dependency ->
                if (dependency.id !in records && dependency.optional) return@forEach
                val error = visit(dependency.id)
                if (error != null) return error
            }
            visiting -= moduleId
            visited += moduleId
            result += moduleId
            return null
        }

        records.keys.forEach { moduleId ->
            val error = visit(moduleId)
            if (error != null) return@synchronized KernelResult.failure(error)
        }
        KernelResult.success(result.toList())
    }
}
