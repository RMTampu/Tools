package io.toolbox.kernel

class ModuleRegistry {
    private data class Record(
        val module: ToolBoxModule,
        val descriptor: ModuleDescriptor,
        val lifecycleLock: Any = Any(),
        var state: ModuleState = ModuleState.REGISTERED,
        var loadAttempted: Boolean = false,
        var loadCompleted: Boolean = false,
        var startAttempted: Boolean = false,
        var registryJournal: KernelRegistryMutationJournal? = null,
        var activationPending: Boolean = false,
        var activationIncludesLoad: Boolean = false
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
        val registryFailures = if (cleanupFailures.isEmpty()) releaseRegistryOwnership(record) else emptyList()
        val failures = cleanupFailures + registryFailures
        if (failures.isNotEmpty()) {
            synchronized(lock) {
                if (records[moduleId] === record) record.state = ModuleState.FAILED
            }
            val primary = failures.first()
            failures.drop(1).forEach(primary::addSuppressed)
            throw IllegalStateException("Failed to cleanly uninstall module $moduleId", primary)
        }

        synchronized(lock) {
            if (records[moduleId] === record) {
                record.activationPending = false
                record.activationIncludesLoad = false
                records.remove(moduleId)
            }
        }
        return true
    }

    internal fun rollbackInstall(moduleId: String): List<ModuleFailure> {
        val record = synchronized(lock) { records[moduleId] } ?: return emptyList()
        val cleanupFailures = cleanupForRemoval(record)
        val registryFailures = if (cleanupFailures.isEmpty()) releaseRegistryOwnership(record) else emptyList()
        val failures = buildList {
            cleanupFailures.forEach { add(ModuleFailure(moduleId, "rollback", it)) }
            registryFailures.forEach { add(ModuleFailure(moduleId, "registry-rollback", it)) }
        }
        if (failures.isEmpty()) {
            synchronized(lock) {
                if (records[moduleId] === record) {
                    record.activationPending = false
                    record.activationIncludesLoad = false
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
        return failures
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

        val finalState = synchronized(lock) {
            if (records[moduleId] === record) record.state else null
        }
        if (finalState != ModuleState.STARTED) {
            return listOf(
                ModuleFailure(
                    moduleId,
                    "install",
                    IllegalStateException(
                        "Module did not reach STARTED state: ${finalState?.name ?: "REMOVED"}"
                    )
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
            val stopFailure = stopRecord(record)
            if (stopFailure != null) {
                failures += stopFailure
            } else {
                failures += retryPendingActivationCleanup(record)
            }
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
                    record.registryJournal = KernelRegistryMutationJournal()
                    record.activationPending = true
                    record.activationIncludesLoad = true
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
            val journal = synchronized(lock) { record.registryJournal }
                ?: return@synchronized LifecycleAttempt(
                    failure = ModuleFailure(
                        record.descriptor.id,
                        "load",
                        IllegalStateException("Missing registry journal for module load")
                    )
                )
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

            val activationContext = context.withRegistryJournal(journal, record.descriptor.id)
            val loadResult = runCatching { record.module.onLoad(activationContext) }
            if (loadResult.isSuccess) {
                val retained = synchronized(lock) {
                    if (records[record.descriptor.id] === record) {
                        record.loadAttempted = false
                        record.loadCompleted = true
                        record.state = ModuleState.LOADED
                        true
                    } else {
                        false
                    }
                }
                if (retained) return@synchronized LifecycleAttempt()

                val error = IllegalStateException("Module removed itself during load: ${record.descriptor.id}")
                runCatching { record.module.onUnload() }.exceptionOrNull()?.let(error::addSuppressed)
                rollbackActivationIfOpen(record).forEach(error::addSuppressed)
                return@synchronized LifecycleAttempt(ModuleFailure(record.descriptor.id, "load", error))
            }

            val error = loadResult.exceptionOrNull()!!
            val cleanup = runCatching { record.module.onUnload() }
            cleanup.exceptionOrNull()?.let(error::addSuppressed)
            val retained = synchronized(lock) {
                if (records[record.descriptor.id] === record) {
                    record.loadAttempted = false
                    record.loadCompleted = cleanup.isFailure
                    record.state = ModuleState.FAILED
                    true
                } else {
                    false
                }
            }
            if (cleanup.isSuccess || !retained) {
                val rollbackFailures = rollbackActivationIfOpen(record)
                rollbackFailures.forEach(error::addSuppressed)
                if (rollbackFailures.isEmpty()) {
                    synchronized(lock) {
                        if (records[record.descriptor.id] === record) {
                            record.activationPending = false
                            record.activationIncludesLoad = false
                        }
                    }
                }
            }
            LifecycleAttempt(ModuleFailure(record.descriptor.id, "load", error))
        }
    }

    private fun startRecord(record: Record): LifecycleAttempt {
        var inactiveDependency: String? = null
        var busy = false
        var startingFromStopped = false
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
                    startingFromStopped = record.state == ModuleState.STOPPED
                    record.startAttempted = true
                    true
                }
            }
        }

        if (inactiveDependency != null) {
            val error = IllegalStateException("Dependency not started: $inactiveDependency")
            return failLoadedActivation(record, error)
        }
        if (busy) return LifecycleAttempt(busy = true)
        if (!shouldStart) return LifecycleAttempt()

        return synchronized(record.lifecycleLock) {
            val journal = synchronized(lock) { record.registryJournal }
            if (startingFromStopped && journal != null) {
                val beginError = runCatching { journal.begin() }.exceptionOrNull()
                if (beginError != null) {
                    synchronized(lock) {
                        if (records[record.descriptor.id] === record) {
                            record.state = ModuleState.FAILED
                        }
                    }
                    return@synchronized LifecycleAttempt(ModuleFailure(record.descriptor.id, "start", beginError))
                }
                synchronized(lock) {
                    if (records[record.descriptor.id] === record) {
                        record.activationPending = true
                        record.activationIncludesLoad = false
                    }
                }
            }

            val stillClaimed = synchronized(lock) {
                records[record.descriptor.id] === record &&
                    record.state in setOf(ModuleState.LOADED, ModuleState.STOPPED) &&
                    record.startAttempted
            }
            if (!stillClaimed) return@synchronized LifecycleAttempt()

            val startResult = runCatching { record.module.onStart() }
            if (startResult.isSuccess) {
                val retained = synchronized(lock) { records[record.descriptor.id] === record }
                if (!retained) {
                    val error = IllegalStateException("Module removed itself during start: ${record.descriptor.id}")
                    journal?.let { rollbackActivationIfOpen(record) }?.forEach(error::addSuppressed)
                    return@synchronized LifecycleAttempt(ModuleFailure(record.descriptor.id, "start", error))
                }

                val commitError = journal?.let { runCatching { it.commit() }.exceptionOrNull() }
                if (commitError == null) {
                    synchronized(lock) {
                        if (records[record.descriptor.id] === record) {
                            record.activationPending = false
                            record.activationIncludesLoad = false
                            record.state = ModuleState.STARTED
                        }
                    }
                    return@synchronized LifecycleAttempt()
                }
                return@synchronized failStartedActivation(record, commitError, startingFromStopped)
            }

            failStartedActivation(record, startResult.exceptionOrNull()!!, startingFromStopped)
        }
    }

    private fun failLoadedActivation(record: Record, error: Throwable): LifecycleAttempt =
        synchronized(record.lifecycleLock) {
            val shouldUnload = synchronized(lock) {
                records[record.descriptor.id] === record && record.loadCompleted
            }
            val cleanup = if (shouldUnload) runCatching { record.module.onUnload() } else Result.success(Unit)
            cleanup.exceptionOrNull()?.let(error::addSuppressed)
            synchronized(lock) {
                if (records[record.descriptor.id] === record) {
                    if (cleanup.isSuccess) record.loadCompleted = false
                    record.state = ModuleState.FAILED
                }
            }
            if (cleanup.isSuccess) {
                val rollbackFailures = rollbackActivationIfOpen(record)
                rollbackFailures.forEach(error::addSuppressed)
                if (rollbackFailures.isEmpty()) {
                    synchronized(lock) {
                        if (records[record.descriptor.id] === record) {
                            record.activationPending = false
                            record.activationIncludesLoad = false
                        }
                    }
                }
            }
            LifecycleAttempt(ModuleFailure(record.descriptor.id, "start", error))
        }

    private fun failStartedActivation(
        record: Record,
        error: Throwable,
        startingFromStopped: Boolean
    ): LifecycleAttempt {
        val stopCleanup = runCatching { record.module.onStop() }
        stopCleanup.exceptionOrNull()?.let(error::addSuppressed)

        val shouldUnload = !startingFromStopped && stopCleanup.isSuccess && synchronized(lock) {
            records[record.descriptor.id] === record && record.loadCompleted
        }
        val unloadCleanup = if (shouldUnload) runCatching { record.module.onUnload() } else Result.success(Unit)
        unloadCleanup.exceptionOrNull()?.let(error::addSuppressed)

        synchronized(lock) {
            if (records[record.descriptor.id] === record) {
                if (stopCleanup.isSuccess) record.startAttempted = false
                if (shouldUnload && unloadCleanup.isSuccess) record.loadCompleted = false
                record.state = ModuleState.FAILED
            }
        }

        if (stopCleanup.isSuccess && unloadCleanup.isSuccess) {
            val rollbackFailures = rollbackActivationIfOpen(record)
            rollbackFailures.forEach(error::addSuppressed)
            if (rollbackFailures.isEmpty()) {
                synchronized(lock) {
                    if (records[record.descriptor.id] === record) {
                        record.activationPending = false
                        record.activationIncludesLoad = false
                    }
                }
            }
        }
        return LifecycleAttempt(ModuleFailure(record.descriptor.id, "start", error))
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

    private fun retryPendingActivationCleanup(record: Record): List<ModuleFailure> =
        synchronized(record.lifecycleLock) {
            val pending = synchronized(lock) {
                records[record.descriptor.id] === record && record.activationPending
            }
            if (!pending) return@synchronized emptyList()

            val stillStarting = synchronized(lock) { record.startAttempted }
            if (stillStarting) {
                return@synchronized listOf(
                    ModuleFailure(
                        record.descriptor.id,
                        "activation-cleanup",
                        IllegalStateException("Module activation stop cleanup is still unresolved")
                    )
                )
            }

            val includesLoad = synchronized(lock) { record.activationIncludesLoad }
            val shouldUnload = synchronized(lock) {
                records[record.descriptor.id] === record &&
                    includesLoad &&
                    record.loadCompleted
            }
            if (shouldUnload) {
                val unloadResult = runCatching { record.module.onUnload() }
                if (unloadResult.isFailure) {
                    synchronized(lock) {
                        if (records[record.descriptor.id] === record) record.state = ModuleState.FAILED
                    }
                    return@synchronized listOf(
                        ModuleFailure(
                            record.descriptor.id,
                            "activation-cleanup",
                            unloadResult.exceptionOrNull()!!
                        )
                    )
                }
                synchronized(lock) {
                    if (records[record.descriptor.id] === record) record.loadCompleted = false
                }
            }

            val rollbackFailures = if (includesLoad) {
                releaseRegistryOwnership(record)
            } else {
                rollbackActivationIfOpen(record)
            }
            if (rollbackFailures.isEmpty()) {
                synchronized(lock) {
                    if (records[record.descriptor.id] === record) {
                        record.activationPending = false
                        record.activationIncludesLoad = false
                        record.state = ModuleState.FAILED
                    }
                }
            } else {
                synchronized(lock) {
                    if (records[record.descriptor.id] === record) record.state = ModuleState.FAILED
                }
            }
            rollbackFailures.map { error ->
                ModuleFailure(record.descriptor.id, "registry-rollback", error)
            }
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

    private fun rollbackActivationIfOpen(record: Record): List<Throwable> =
        synchronized(lock) { record.registryJournal }?.rollbackIfOpen() ?: emptyList()

    private fun releaseRegistryOwnership(record: Record): List<Throwable> =
        synchronized(lock) { record.registryJournal }?.releaseAll() ?: emptyList()

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
