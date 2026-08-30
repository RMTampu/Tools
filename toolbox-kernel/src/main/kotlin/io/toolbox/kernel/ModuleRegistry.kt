package io.toolbox.kernel

internal data class ModuleHandle(
    val module: ToolBoxModule,
    val descriptor: ModuleDescriptor,
    val state: ModuleState,
    val health: HealthStatus,
    val lastFailure: ModuleFailure?
)

internal data class CapabilityBindingKey(
    val consumerModuleId: String,
    val capabilityId: String
)

internal data class ResolutionPlan(
    val order: List<String>,
    val hardDependencies: Map<String, Set<String>>,
    val capabilityBindings: Map<CapabilityBindingKey, String>
) {
    fun dependenciesOf(moduleId: String): Set<String> = hardDependencies[moduleId].orEmpty()
    fun capabilityBindingsFor(moduleId: String): Map<String, String> = capabilityBindings
        .filterKeys { it.consumerModuleId == moduleId }
        .mapKeys { it.key.capabilityId }

    fun dependentsOf(moduleId: String): List<String> = hardDependencies
        .filterValues { moduleId in it }
        .keys
        .sorted()
}

internal class ModuleRegistry(
    private val onMutation: () -> Unit
) {
    private data class Record(
        val module: ToolBoxModule,
        val descriptor: ModuleDescriptor,
        var state: ModuleState = ModuleState.REGISTERED,
        var health: HealthStatus = HealthStatus.unknown(),
        var lastFailure: ModuleFailure? = null
    )

    private val lock = Any()
    private val records = linkedMapOf<String, Record>()

    internal fun register(module: ToolBoxModule, descriptor: ModuleDescriptor): Unit = synchronized(lock) {
        check(descriptor.id !in records) { "Module already installed: ${descriptor.id}" }
        records[descriptor.id] = Record(module, descriptor)
        onMutation()
    }

    internal fun contains(moduleId: String): Boolean = synchronized(lock) { moduleId in records }

    internal fun handle(moduleId: String): ModuleHandle? = synchronized(lock) {
        records[moduleId]?.toHandle()
    }

    internal fun descriptors(): List<ModuleDescriptor> = synchronized(lock) {
        records.values.map { it.descriptor }
    }

    internal fun stateOf(moduleId: String): ModuleState? = synchronized(lock) { records[moduleId]?.state }

    internal fun healthSnapshot(): List<ModuleHealth> = synchronized(lock) {
        records.values.map { ModuleHealth(it.descriptor, it.state, it.health, it.lastFailure) }
    }

    internal fun transition(moduleId: String, expected: Set<ModuleState>, next: ModuleState): Boolean = synchronized(lock) {
        val record = records[moduleId] ?: return@synchronized false
        if (record.state !in expected) return@synchronized false
        record.state = next
        onMutation()
        true
    }

    internal fun markFailure(
        moduleId: String,
        expected: Set<ModuleState>,
        failure: ModuleFailure,
        quarantine: Boolean = false
    ): Boolean = synchronized(lock) {
        val record = records[moduleId] ?: return@synchronized false
        if (record.state !in expected) return@synchronized false
        record.lastFailure = failure
        record.state = if (quarantine) ModuleState.QUARANTINED else ModuleState.FAILED
        onMutation()
        true
    }

    internal fun recordFailure(moduleId: String, failure: ModuleFailure): Unit = synchronized(lock) {
        records[moduleId]?.let {
            it.lastFailure = failure
            onMutation()
        }
    }

    internal fun clearFailure(moduleId: String): Unit = synchronized(lock) {
        records[moduleId]?.let {
            if (it.lastFailure != null) {
                it.lastFailure = null
                onMutation()
            }
        }
    }

    internal fun setHealth(moduleId: String, health: HealthStatus): Unit = synchronized(lock) {
        records[moduleId]?.let {
            it.health = health
            onMutation()
        }
    }

    internal fun remove(moduleId: String, allowedStates: Set<ModuleState>): ModuleHandle? = synchronized(lock) {
        val record = records[moduleId] ?: return@synchronized null
        check(record.state in allowedStates) { "Cannot remove module $moduleId from state ${record.state}" }
        val removed = records.remove(moduleId)?.toHandle()
        if (removed != null) onMutation()
        removed
    }

    internal fun forceRemove(moduleId: String): ModuleHandle? = synchronized(lock) {
        val removed = records.remove(moduleId)?.toHandle()
        if (removed != null) onMutation()
        removed
    }

    internal fun resolvePlan(): KernelResult<ResolutionPlan> = synchronized(lock) {
        val hardDependencies = records.keys.associateWith { linkedSetOf<String>() }.toMutableMap()
        val capabilityBindings = linkedMapOf<CapabilityBindingKey, String>()

        records.values.sortedBy { it.descriptor.id }.forEach recordLoop@{ record ->
            val descriptor = record.descriptor
            descriptor.dependencies.sortedBy { it.id }.forEach dependencyLoop@{ dependency ->
                val provider = records[dependency.id]
                if (provider == null) {
                    if (dependency.kind == DependencyKind.REQUIRED) {
                        return@synchronized KernelResult.failure(
                            KernelError(KernelErrorCode.DEPENDENCY_RESOLUTION, "Missing required module dependency ${dependency.id} for ${descriptor.id}")
                        )
                    }
                    return@dependencyLoop
                }
                if (!dependency.versionRange.contains(provider.descriptor.version)) {
                    if (dependency.kind == DependencyKind.REQUIRED) {
                        return@synchronized KernelResult.failure(
                            KernelError(
                                KernelErrorCode.DEPENDENCY_RESOLUTION,
                                "Module ${descriptor.id} requires ${dependency.id} in ${dependency.versionRange}, found ${provider.descriptor.version}"
                            )
                        )
                    }
                    return@dependencyLoop
                }
                if (dependency.kind == DependencyKind.REQUIRED) hardDependencies.getValue(descriptor.id) += dependency.id
            }

            descriptor.requiredCapabilities.sortedBy { it.id }.forEach capabilityLoop@{ requirement ->
                val candidates = records.values
                    .asSequence()
                    .filter { it.descriptor.id != descriptor.id }
                    .filter { it.state !in setOf(ModuleState.FAILED, ModuleState.QUARANTINED) }
                    .flatMap { candidate ->
                        candidate.descriptor.providedCapabilities.asSequence()
                            .filter { it.id == requirement.id && requirement.versionRange.contains(it.version) }
                            .map { declaration -> candidate to declaration }
                    }
                    .sortedWith(
                        compareByDescending<Pair<Record, CapabilityDeclaration>> { it.second.version }
                            .thenBy { it.first.descriptor.id }
                    )
                    .toList()

                val selected = candidates.firstOrNull()
                if (selected == null) {
                    if (requirement.kind == DependencyKind.REQUIRED) {
                        return@synchronized KernelResult.failure(
                            KernelError(
                                KernelErrorCode.CAPABILITY_RESOLUTION,
                                "No provider satisfies required capability ${requirement.id} for ${descriptor.id}"
                            )
                        )
                    }
                    return@capabilityLoop
                }
                capabilityBindings[CapabilityBindingKey(descriptor.id, requirement.id)] = selected.first.descriptor.id
                if (requirement.kind == DependencyKind.REQUIRED) {
                    hardDependencies.getValue(descriptor.id) += selected.first.descriptor.id
                }
            }
        }

        val visiting = linkedSetOf<String>()
        val visited = linkedSetOf<String>()
        val order = mutableListOf<String>()

        fun visit(moduleId: String): KernelError? {
            if (moduleId in visited) return null
            if (moduleId in visiting) {
                return KernelError(KernelErrorCode.DEPENDENCY_RESOLUTION, "Required dependency cycle detected at $moduleId")
            }
            visiting += moduleId
            hardDependencies.getValue(moduleId).sorted().forEach dependencyLoop@{ dependencyId ->
                val error = visit(dependencyId)
                if (error != null) return error
            }
            visiting -= moduleId
            visited += moduleId
            order += moduleId
            return null
        }

        records.keys.sorted().forEach moduleLoop@{ moduleId ->
            val error = visit(moduleId)
            if (error != null) return@synchronized KernelResult.failure(error)
        }

        KernelResult.success(
            ResolutionPlan(
                order = order.toList(),
                hardDependencies = hardDependencies.mapValues { it.value.toSet() },
                capabilityBindings = capabilityBindings.toMap()
            )
        )
    }

    private fun Record.toHandle(): ModuleHandle = ModuleHandle(module, descriptor, state, health, lastFailure)
}
