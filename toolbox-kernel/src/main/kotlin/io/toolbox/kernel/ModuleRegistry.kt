package io.toolbox.kernel

class ModuleRegistry {
    private data class Record(
        val module: ToolBoxModule,
        val descriptor: ModuleDescriptor,
        var state: ModuleState = ModuleState.REGISTERED,
        var loadCompleted: Boolean = false,
        var startAttempted: Boolean = false
    )

    private val lock = Any()
    private val records = linkedMapOf<String, Record>()

    fun install(module: ToolBoxModule, descriptor: ModuleDescriptor = module.descriptor) {
        synchronized(lock) {
            check(descriptor.id !in records) { "Module already installed: ${descriptor.id}" }
            records[descriptor.id] = Record(module, descriptor)
        }
    }

    fun uninstall(moduleId: String): Boolean {
        val record = synchronized(lock) {
            val current = records[moduleId] ?: return false
            val dependents = records.values.filter { moduleId in it.descriptor.dependencies }
            check(dependents.isEmpty()) {
                "Cannot uninstall $moduleId; required by ${dependents.joinToString { it.descriptor.id }}"
            }
            current
        }

        val cleanupFailures = cleanupForRemoval(record)
        if (cleanupFailures.isNotEmpty()) {
            val primary = cleanupFailures.first()
            cleanupFailures.drop(1).forEach(primary::addSuppressed)
            throw IllegalStateException("Failed to cleanly uninstall module $moduleId", primary)
        }

        synchronized(lock) {
            if (records[moduleId] === record) {
                records.remove(moduleId)
            }
        }
        return true
    }

    internal fun rollbackInstall(moduleId: String): List<ModuleFailure> {
        val record = synchronized(lock) { records[moduleId] } ?: return emptyList()
        val cleanupFailures = cleanupForRemoval(record)
        if (cleanupFailures.isEmpty()) {
            synchronized(lock) {
                if (records[moduleId] === record) {
                    records.remove(moduleId)
                }
            }
            return emptyList()
        }
        synchronized(lock) {
            if (records[moduleId] === record) {
                record.state = ModuleState.FAILED
            }
        }
        return cleanupFailures.map { ModuleFailure(moduleId, "rollback", it) }
    }

    fun loadAll(context: KernelContext): List<ModuleFailure> {
        val order = runCatching { resolveOrder() }
            .getOrElse { return listOf(ModuleFailure("kernel", "dependency-resolution", it)) }
        val failures = mutableListOf<ModuleFailure>()

        for (record in order) {
            var missingReadyDependency: String? = null
            val shouldLoad = synchronized(lock) {
                if (records[record.descriptor.id] !== record || record.state != ModuleState.REGISTERED) {
                    false
                } else {
                    missingReadyDependency = record.descriptor.dependencies.firstOrNull { dependencyId ->
                        records[dependencyId]?.state !in setOf(ModuleState.LOADED, ModuleState.STARTED, ModuleState.STOPPED)
                    }
                    if (missingReadyDependency != null) {
                        record.state = ModuleState.FAILED
                        false
                    } else {
                        true
                    }
                }
            }

            if (missingReadyDependency != null) {
                failures += ModuleFailure(
                    record.descriptor.id,
                    "load",
                    IllegalStateException("Dependency not ready: $missingReadyDependency")
                )
                continue
            }
            if (!shouldLoad) continue

            runCatching { record.module.onLoad(context) }
                .onSuccess {
                    synchronized(lock) {
                        if (records[record.descriptor.id] === record) {
                            record.loadCompleted = true
                            record.state = ModuleState.LOADED
                        }
                    }
                }
                .onFailure { error ->
                    val cleanup = runCatching { record.module.onUnload() }
                    cleanup.exceptionOrNull()?.let(error::addSuppressed)
                    synchronized(lock) {
                        if (records[record.descriptor.id] === record) {
                            record.loadCompleted = cleanup.isFailure
                            record.state = ModuleState.FAILED
                        }
                    }
                    failures += ModuleFailure(record.descriptor.id, "load", error)
                }
        }
        return failures
    }

    fun startAll(): List<ModuleFailure> {
        val order = runCatching { resolveOrder() }
            .getOrElse { return listOf(ModuleFailure("kernel", "dependency-resolution", it)) }
        val failures = mutableListOf<ModuleFailure>()

        for (record in order) {
            var inactiveDependency: String? = null
            val shouldStart = synchronized(lock) {
                if (records[record.descriptor.id] !== record || record.state !in setOf(ModuleState.LOADED, ModuleState.STOPPED)) {
                    false
                } else {
                    inactiveDependency = record.descriptor.dependencies.firstOrNull { dependencyId ->
                        records[dependencyId]?.state != ModuleState.STARTED
                    }
                    if (inactiveDependency != null) {
                        record.state = ModuleState.FAILED
                        false
                    } else {
                        record.startAttempted = true
                        true
                    }
                }
            }

            if (inactiveDependency != null) {
                failures += ModuleFailure(
                    record.descriptor.id,
                    "start",
                    IllegalStateException("Dependency not started: $inactiveDependency")
                )
                continue
            }
            if (!shouldStart) continue

            runCatching { record.module.onStart() }
                .onSuccess {
                    synchronized(lock) {
                        if (records[record.descriptor.id] === record) {
                            record.state = ModuleState.STARTED
                        }
                    }
                }
                .onFailure { error ->
                    val cleanup = runCatching { record.module.onStop() }
                    synchronized(lock) {
                        if (records[record.descriptor.id] === record) {
                            if (cleanup.isSuccess) {
                                record.startAttempted = false
                            } else {
                                cleanup.exceptionOrNull()?.let(error::addSuppressed)
                            }
                            record.state = ModuleState.FAILED
                        }
                    }
                    failures += ModuleFailure(record.descriptor.id, "start", error)
                }
        }
        return failures
    }

    fun loadAndStart(moduleId: String, context: KernelContext): List<ModuleFailure> {
        val record = synchronized(lock) { records[moduleId] }
            ?: return listOf(ModuleFailure(moduleId, "install", IllegalArgumentException("Unknown module")))
        val failures = mutableListOf<ModuleFailure>()

        val missingDependency = synchronized(lock) {
            record.descriptor.dependencies.firstOrNull { dependencyId ->
                records[dependencyId]?.state != ModuleState.STARTED
            }
        }
        if (missingDependency != null) {
            synchronized(lock) {
                if (records[moduleId] === record) record.state = ModuleState.FAILED
            }
            return listOf(
                ModuleFailure(
                    moduleId,
                    "install",
                    IllegalStateException("Dependency not started: $missingDependency")
                )
            )
        }

        val shouldLoad = synchronized(lock) {
            records[moduleId] === record && record.state == ModuleState.REGISTERED
        }
        if (shouldLoad) {
            runCatching { record.module.onLoad(context) }
                .onSuccess {
                    synchronized(lock) {
                        if (records[moduleId] === record) {
                            record.loadCompleted = true
                            record.state = ModuleState.LOADED
                        }
                    }
                }
                .onFailure { error ->
                    val cleanup = runCatching { record.module.onUnload() }
                    cleanup.exceptionOrNull()?.let(error::addSuppressed)
                    synchronized(lock) {
                        if (records[moduleId] === record) {
                            record.loadCompleted = cleanup.isFailure
                            record.state = ModuleState.FAILED
                        }
                    }
                    failures += ModuleFailure(moduleId, "load", error)
                }
        }

        val shouldStart = synchronized(lock) {
            failures.isEmpty() && records[moduleId] === record && record.state in setOf(ModuleState.LOADED, ModuleState.STOPPED)
        }
        if (shouldStart) {
            synchronized(lock) {
                if (records[moduleId] === record) record.startAttempted = true
            }
            runCatching { record.module.onStart() }
                .onSuccess {
                    synchronized(lock) {
                        if (records[moduleId] === record) record.state = ModuleState.STARTED
                    }
                }
                .onFailure { error ->
                    val cleanup = runCatching { record.module.onStop() }
                    synchronized(lock) {
                        if (records[moduleId] === record) {
                            if (cleanup.isSuccess) {
                                record.startAttempted = false
                            } else {
                                cleanup.exceptionOrNull()?.let(error::addSuppressed)
                            }
                            record.state = ModuleState.FAILED
                        }
                    }
                    failures += ModuleFailure(moduleId, "start", error)
                }
        }
        return failures
    }

    fun stopAll(): List<ModuleFailure> {
        val order = runCatching { resolveOrder().asReversed() }
            .getOrElse { synchronized(lock) { records.values.toList().asReversed() } }
        val failures = mutableListOf<ModuleFailure>()

        for (record in order) {
            val shouldStop = synchronized(lock) {
                records[record.descriptor.id] === record &&
                    (record.state == ModuleState.STARTED || record.startAttempted)
            }
            if (!shouldStop) continue

            runCatching { record.module.onStop() }
                .onSuccess {
                    synchronized(lock) {
                        if (records[record.descriptor.id] === record) {
                            record.startAttempted = false
                            record.state = ModuleState.STOPPED
                        }
                    }
                }
                .onFailure { error ->
                    synchronized(lock) {
                        if (records[record.descriptor.id] === record) {
                            record.state = ModuleState.FAILED
                        }
                    }
                    failures += ModuleFailure(record.descriptor.id, "stop", error)
                }
        }
        return failures
    }

    fun health(): List<ModuleHealth> {
        val snapshot = synchronized(lock) {
            records.values.map { Triple(it.module, it.descriptor, it.state) }
        }
        return snapshot.map { (module, descriptor, state) ->
            val status = when (state) {
                ModuleState.STARTED -> runCatching { module.healthCheck() }
                    .getOrElse { HealthStatus.failed(it.message ?: it::class.java.simpleName) }
                ModuleState.FAILED -> HealthStatus.failed("Module lifecycle failed")
                else -> HealthStatus.ok("State: $state")
            }
            ModuleHealth(descriptor, state, status)
        }
    }

    fun stateOf(moduleId: String): ModuleState? = synchronized(lock) { records[moduleId]?.state }

    fun descriptors(): List<ModuleDescriptor> = synchronized(lock) { records.values.map { it.descriptor } }

    private fun cleanupForRemoval(record: Record): List<Throwable> {
        val failures = mutableListOf<Throwable>()

        val needsStop = synchronized(lock) {
            records[record.descriptor.id] === record &&
                (record.state == ModuleState.STARTED || record.startAttempted)
        }
        if (needsStop) {
            runCatching { record.module.onStop() }
                .onSuccess {
                    synchronized(lock) {
                        if (records[record.descriptor.id] === record) {
                            record.startAttempted = false
                            record.state = ModuleState.STOPPED
                        }
                    }
                }
                .onFailure {
                    synchronized(lock) {
                        if (records[record.descriptor.id] === record) record.state = ModuleState.FAILED
                    }
                    failures += it
                }
            if (failures.isNotEmpty()) return failures
        }

        val needsUnload = synchronized(lock) {
            records[record.descriptor.id] === record && record.loadCompleted
        }
        if (needsUnload) {
            runCatching { record.module.onUnload() }
                .onSuccess {
                    synchronized(lock) {
                        if (records[record.descriptor.id] === record) {
                            record.loadCompleted = false
                            record.state = ModuleState.REGISTERED
                        }
                    }
                }
                .onFailure {
                    synchronized(lock) {
                        if (records[record.descriptor.id] === record) record.state = ModuleState.FAILED
                    }
                    failures += it
                }
        }

        return failures
    }

    private fun resolveOrder(): List<Record> = synchronized(lock) {
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        val result = mutableListOf<Record>()

        fun visit(moduleId: String) {
            if (moduleId in visited) return
            check(moduleId !in visiting) { "Module dependency cycle detected at $moduleId" }
            val record = records[moduleId] ?: error("Missing module dependency: $moduleId")
            visiting += moduleId
            record.descriptor.dependencies.forEach(::visit)
            visiting -= moduleId
            visited += moduleId
            result += record
        }

        records.keys.forEach(::visit)
        result
    }
}
