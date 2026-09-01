package io.toolbox.kernel

class ModuleRegistry {
    private data class Record(
        val module: ToolBoxModule,
        val descriptor: ModuleDescriptor,
        val lifecycleLock: Any = Any(),
        var state: ModuleState = ModuleState.REGISTERED,
        var loadAttempted: Boolean = false,
        var loadCompleted: Boolean = false,
        var startAttempted: Boolean = false
    )

    private data class LifecycleAttempt(
        val failure: ModuleFailure? = null,
        val busy: Boolean = false
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
        order.forEach { record ->
            loadRecord(record, context).failure?.let(failures::add)
        }
        return failures
    }

    fun startAll(): List<ModuleFailure> {
        val order = runCatching { resolveOrder() }
            .getOrElse { return listOf(ModuleFailure("kernel", "dependency-resolution", it)) }
        val failures = mutableListOf<ModuleFailure>()
        order.forEach { record ->
            startRecord(record).failure?.let(failures::add)
        }
        return failures
    }

    fun loadAndStart(moduleId: String, context: KernelContext): List<ModuleFailure> {
        val record = synchronized(lock) { records[moduleId] }
            ?: return listOf(ModuleFailure(moduleId, "install", IllegalArgumentException("Unknown module")))

        val missingDependency = synchronized(lock) {
            if (records[moduleId] !== record) {
                return@synchronized null
            }
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

        val loadAttempt = loadRecord(record, context)
        loadAttempt.failure?.let { return listOf(it) }
        if (loadAttempt.busy) {
            return listOf(
                ModuleFailure(
                    moduleId,
                    "install",
                    IllegalStateException("Module load is already in progress: $moduleId")
                )
            )
        }

        val startAttempt = startRecord(record)
        startAttempt.failure?.let { return listOf(it) }
        if (startAttempt.busy) {
            return listOf(
                ModuleFailure(
                    moduleId,
                    "install",
                    IllegalStateException("Module start is already in progress: $moduleId")
                )
            )
        }
        return emptyList()
    }

    fun stopAll(): List<ModuleFailure> {
        val order = runCatching { resolveOrder().asReversed() }
            .getOrElse { synchronized(lock) { records.values.toList().asReversed() } }
        val failures = mutableListOf<ModuleFailure>()
        order.forEach { record ->
            stopRecord(record)?.let(failures::add)
        }
        return failures
    }

    fun health(): List<ModuleHealth> {
        val snapshot = synchronized(lock) { records.values.toList() }
        return snapshot.mapNotNull(::healthRecord)
    }

    fun stateOf(moduleId: String): ModuleState? = synchronized(lock) { records[moduleId]?.state }

    fun descriptors(): List<ModuleDescriptor> = synchronized(lock) { records.values.map { it.descriptor } }

    private fun loadRecord(record: Record, context: KernelContext): LifecycleAttempt {
        var missingReadyDependency: String? = null
        var busy = false
        val shouldLoad = synchronized(lock) {
            if (records[record.descriptor.id] !== record || record.state != ModuleState.REGISTERED) {
                false
            } else if (record.loadAttempted) {
                busy = true
                false
            } else {
                missingReadyDependency = record.descriptor.dependencies.firstOrNull { dependencyId ->
                    records[dependencyId]?.state !in setOf(ModuleState.LOADED, ModuleState.STARTED, ModuleState.STOPPED)
                }
                if (missingReadyDependency != null) {
                    record.state = ModuleState.FAILED
                    false
                } else {
                    record.loadAttempted = true
                    true
                }
            }
        }

        if (missingReadyDependency != null) {
            return LifecycleAttempt(
                failure = ModuleFailure(
                    record.descriptor.id,
                    "load",
                    IllegalStateException("Dependency not ready: $missingReadyDependency")
                )
            )
        }
        if (busy) return LifecycleAttempt(busy = true)
        if (!shouldLoad) return LifecycleAttempt()

        return synchronized(record.lifecycleLock) {
            val stillClaimed = synchronized(lock) {
                records[record.descriptor.id] === record &&
                    record.state == ModuleState.REGISTERED &&
                    record.loadAttempted
            }
            if (!stillClaimed) {
                synchronized(lock) {
                    if (records[record.descriptor.id] === record) record.loadAttempted = false
                }
                return@synchronized LifecycleAttempt()
            }

            val loadResult = runCatching { record.module.onLoad(context) }
            if (loadResult.isSuccess) {
                synchronized(lock) {
                    if (records[record.descriptor.id] === record) {
                        record.loadAttempted = false
                        record.loadCompleted = true
                        record.state = ModuleState.LOADED
                    }
                }
                return@synchronized LifecycleAttempt()
            }

            val error = loadResult.exceptionOrNull()!!
            val cleanup = runCatching { record.module.onUnload() }
            cleanup.exceptionOrNull()?.let(error::addSuppressed)
            synchronized(lock) {
                if (records[record.descriptor.id] === record) {
                    record.loadAttempted = false
                    record.loadCompleted = cleanup.isFailure
                    record.state = ModuleState.FAILED
                }
            }
            LifecycleAttempt(ModuleFailure(record.descriptor.id, "load", error))
        }
    }

    private fun startRecord(record: Record): LifecycleAttempt {
        var inactiveDependency: String? = null
        var busy = false
        val shouldStart = synchronized(lock) {
            if (
                records[record.descriptor.id] !== record ||
                record.state !in setOf(ModuleState.LOADED, ModuleState.STOPPED)
            ) {
                false
            } else if (record.startAttempted) {
                busy = true
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
            return LifecycleAttempt(
                failure = ModuleFailure(
                    record.descriptor.id,
                    "start",
                    IllegalStateException("Dependency not started: $inactiveDependency")
                )
            )
        }
        if (busy) return LifecycleAttempt(busy = true)
        if (!shouldStart) return LifecycleAttempt()

        return synchronized(record.lifecycleLock) {
            val stillClaimed = synchronized(lock) {
                records[record.descriptor.id] === record &&
                    record.state in setOf(ModuleState.LOADED, ModuleState.STOPPED) &&
                    record.startAttempted
            }
            if (!stillClaimed) return@synchronized LifecycleAttempt()

            val startResult = runCatching { record.module.onStart() }
            if (startResult.isSuccess) {
                synchronized(lock) {
                    if (records[record.descriptor.id] === record) {
                        record.state = ModuleState.STARTED
                    }
                }
                return@synchronized LifecycleAttempt()
            }

            val error = startResult.exceptionOrNull()!!
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
            LifecycleAttempt(ModuleFailure(record.descriptor.id, "start", error))
        }
    }

    private fun stopRecord(record: Record): ModuleFailure? =
        synchronized(record.lifecycleLock) {
            val shouldStop = synchronized(lock) {
                records[record.descriptor.id] === record &&
                    (record.state == ModuleState.STARTED || record.startAttempted)
            }
            if (!shouldStop) return@synchronized null

            val stopResult = runCatching { record.module.onStop() }
            if (stopResult.isSuccess) {
                synchronized(lock) {
                    if (records[record.descriptor.id] === record) {
                        record.startAttempted = false
                        record.state = ModuleState.STOPPED
                    }
                }
                return@synchronized null
            }

            val error = stopResult.exceptionOrNull()!!
            synchronized(lock) {
                if (records[record.descriptor.id] === record) {
                    record.state = ModuleState.FAILED
                }
            }
            ModuleFailure(record.descriptor.id, "stop", error)
        }

    private fun healthRecord(record: Record): ModuleHealth? =
        synchronized(record.lifecycleLock) {
            val state = synchronized(lock) {
                if (records[record.descriptor.id] !== record) null else record.state
            } ?: return@synchronized null

            val status = when (state) {
                ModuleState.STARTED -> runCatching { record.module.healthCheck() }
                    .getOrElse { HealthStatus.failed(it.message ?: it::class.java.simpleName) }
                ModuleState.FAILED -> HealthStatus.failed("Module lifecycle failed")
                else -> HealthStatus.ok("State: $state")
            }
            ModuleHealth(record.descriptor, state, status)
        }

    private fun cleanupForRemoval(record: Record): List<Throwable> =
        synchronized(record.lifecycleLock) {
            val failures = mutableListOf<Throwable>()

            val needsStop = synchronized(lock) {
                records[record.descriptor.id] === record &&
                    (record.state == ModuleState.STARTED || record.startAttempted)
            }
            if (needsStop) {
                val stopResult = runCatching { record.module.onStop() }
                if (stopResult.isSuccess) {
                    synchronized(lock) {
                        if (records[record.descriptor.id] === record) {
                            record.startAttempted = false
                            record.state = ModuleState.STOPPED
                        }
                    }
                } else {
                    synchronized(lock) {
                        if (records[record.descriptor.id] === record) record.state = ModuleState.FAILED
                    }
                    failures += stopResult.exceptionOrNull()!!
                    return@synchronized failures
                }
            }

            val needsUnload = synchronized(lock) {
                records[record.descriptor.id] === record && record.loadCompleted
            }
            if (needsUnload) {
                val unloadResult = runCatching { record.module.onUnload() }
                if (unloadResult.isSuccess) {
                    synchronized(lock) {
                        if (records[record.descriptor.id] === record) {
                            record.loadCompleted = false
                            record.state = ModuleState.REGISTERED
                        }
                    }
                } else {
                    synchronized(lock) {
                        if (records[record.descriptor.id] === record) record.state = ModuleState.FAILED
                    }
                    failures += unloadResult.exceptionOrNull()!!
                }
            }

            failures
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
