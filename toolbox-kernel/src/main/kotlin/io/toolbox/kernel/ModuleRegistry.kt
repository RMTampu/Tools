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

    fun merge(other: ResolutionPlan): ResolutionPlan {
        val mergedDependencies = linkedMapOf<String, MutableSet<String>>()
        (hardDependencies.keys + other.hardDependencies.keys).sorted().forEach { id ->
            mergedDependencies[id] = linkedSetOf<String>().apply {
                addAll(hardDependencies[id].orEmpty())
                addAll(other.hardDependencies[id].orEmpty())
            }
        }
        val mergedBindings = linkedMapOf<CapabilityBindingKey, String>()
        capabilityBindings.forEach { (key, value) -> mergedBindings[key] = value }
        other.capabilityBindings.forEach { (key, value) ->
            val existing = mergedBindings[key]
            check(existing == null || existing == value) { "Conflicting capability binding for $key: $existing vs $value" }
            mergedBindings[key] = value
        }
        return ResolutionPlan(
            order = (order + other.order).distinct(),
            hardDependencies = mergedDependencies.mapValues { it.value.toSet() },
            capabilityBindings = mergedBindings.toMap()
        )
    }

    companion object {
        fun empty(): ResolutionPlan = ResolutionPlan(emptyList(), emptyMap(), emptyMap())
    }
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

    private sealed class ResolveAttempt {
        data class Success(val plan: ResolutionPlan) : ResolveAttempt()
        data class Failure(val error: KernelError) : ResolveAttempt()
    }

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

    /** Resolves only [moduleId] and the providers needed by its closure. Unrelated broken modules are ignored. */
    internal fun resolvePlanFor(moduleId: String): KernelResult<ResolutionPlan> = synchronized(lock) {
        if (moduleId !in records) {
            return@synchronized KernelResult.failure(KernelError(KernelErrorCode.NOT_FOUND, "Unknown module: $moduleId"))
        }
        when (val attempt = resolveModule(moduleId, linkedSetOf())) {
            is ResolveAttempt.Success -> KernelResult.success(attempt.plan)
            is ResolveAttempt.Failure -> KernelResult.failure(attempt.error)
        }
    }

    /** Strict all-module resolution retained for diagnostics; lifecycle startup uses per-root isolation. */
    internal fun resolvePlan(): KernelResult<ResolutionPlan> = synchronized(lock) {
        var merged = ResolutionPlan.empty()
        records.keys.sorted().forEach { moduleId ->
            when (val attempt = resolveModule(moduleId, linkedSetOf())) {
                is ResolveAttempt.Success -> merged = merged.merge(attempt.plan)
                is ResolveAttempt.Failure -> return@synchronized KernelResult.failure(attempt.error)
            }
        }
        KernelResult.success(merged)
    }

    private fun resolveModule(moduleId: String, path: LinkedHashSet<String>): ResolveAttempt {
        if (moduleId in path) {
            return ResolveAttempt.Failure(
                KernelError(
                    KernelErrorCode.DEPENDENCY_RESOLUTION,
                    "Required dependency cycle detected: ${(path + moduleId).joinToString(" -> ")}"
                )
            )
        }
        val record = records[moduleId] ?: return ResolveAttempt.Failure(
            KernelError(KernelErrorCode.DEPENDENCY_RESOLUTION, "Missing required module dependency $moduleId")
        )
        if (record.state in setOf(ModuleState.FAILED, ModuleState.QUARANTINED)) {
            return ResolveAttempt.Failure(
                KernelError(KernelErrorCode.DEPENDENCY_RESOLUTION, "Module $moduleId is not resolvable from state ${record.state}")
            )
        }

        val nextPath = LinkedHashSet(path).apply { add(moduleId) }
        var plan = ResolutionPlan(
            order = emptyList(),
            hardDependencies = mapOf(moduleId to emptySet()),
            capabilityBindings = emptyMap()
        )

        record.descriptor.dependencies.sortedBy { it.id }.forEach { dependency ->
            val provider = records[dependency.id]
            if (provider == null || !dependency.versionRange.contains(provider.descriptor.version)) {
                if (dependency.kind == DependencyKind.REQUIRED) {
                    val detail = if (provider == null) {
                        "Missing required module dependency ${dependency.id} for $moduleId"
                    } else {
                        "Module $moduleId requires ${dependency.id} in ${dependency.versionRange}, found ${provider.descriptor.version}"
                    }
                    return ResolveAttempt.Failure(KernelError(KernelErrorCode.DEPENDENCY_RESOLUTION, detail))
                }
                return@forEach
            }

            val dependencyAttempt = resolveModule(dependency.id, nextPath)
            if (dependencyAttempt is ResolveAttempt.Failure) {
                if (dependency.kind == DependencyKind.REQUIRED) return dependencyAttempt
                return@forEach
            }
            dependencyAttempt as ResolveAttempt.Success
            plan = plan.merge(dependencyAttempt.plan)
            if (dependency.kind == DependencyKind.REQUIRED) {
                plan = plan.withHardDependency(moduleId, dependency.id)
            }
        }

        record.descriptor.requiredCapabilities.sortedBy { it.id }.forEach { requirement ->
            val candidates = records.values
                .asSequence()
                .filter { it.descriptor.id != moduleId }
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

            var selected: Pair<Record, ResolutionPlan>? = null
            for (candidate in candidates) {
                when (val attempt = resolveModule(candidate.first.descriptor.id, nextPath)) {
                    is ResolveAttempt.Success -> {
                        selected = candidate.first to attempt.plan
                        break
                    }
                    is ResolveAttempt.Failure -> Unit
                }
            }

            if (selected == null) {
                if (requirement.kind == DependencyKind.REQUIRED) {
                    return ResolveAttempt.Failure(
                        KernelError(
                            KernelErrorCode.CAPABILITY_RESOLUTION,
                            "No resolvable provider satisfies required capability ${requirement.id} for $moduleId"
                        )
                    )
                }
                return@forEach
            }

            val provider = selected.first.descriptor.id
            plan = plan.merge(selected.second)
            plan = plan.withCapabilityBinding(moduleId, requirement.id, provider)
            if (requirement.kind == DependencyKind.REQUIRED) {
                plan = plan.withHardDependency(moduleId, provider)
            }
        }

        plan = plan.copy(order = (plan.order + moduleId).distinct())
        return ResolveAttempt.Success(plan)
    }

    private fun ResolutionPlan.withHardDependency(consumerId: String, providerId: String): ResolutionPlan {
        val dependencies = hardDependencies.mapValues { it.value.toMutableSet() }.toMutableMap()
        dependencies.getOrPut(consumerId) { linkedSetOf() }.add(providerId)
        dependencies.putIfAbsent(providerId, linkedSetOf())
        return copy(hardDependencies = dependencies.mapValues { it.value.toSet() })
    }

    private fun ResolutionPlan.withCapabilityBinding(
        consumerId: String,
        capabilityId: String,
        providerId: String
    ): ResolutionPlan {
        val bindings = capabilityBindings.toMutableMap()
        bindings[CapabilityBindingKey(consumerId, capabilityId)] = providerId
        return copy(capabilityBindings = bindings.toMap())
    }

    private fun Record.toHandle(): ModuleHandle = ModuleHandle(module, descriptor, state, health, lastFailure)
}
