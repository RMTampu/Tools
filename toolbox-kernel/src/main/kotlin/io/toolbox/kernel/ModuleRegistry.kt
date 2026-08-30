package io.toolbox.kernel

class ModuleRegistry {
    private data class Record(
        val module: ToolBoxModule,
        var state: ModuleState = ModuleState.REGISTERED
    )

    private val records = linkedMapOf<String, Record>()

    @Synchronized
    fun install(module: ToolBoxModule) {
        val descriptor = module.descriptor
        require(descriptor.id.isNotBlank()) { "Module id cannot be blank" }
        require(descriptor.name.isNotBlank()) { "Module name cannot be blank" }
        check(descriptor.id !in records) { "Module already installed: ${descriptor.id}" }
        records[descriptor.id] = Record(module)
    }

    @Synchronized
    fun uninstall(moduleId: String): Boolean {
        val record = records[moduleId] ?: return false
        val dependents = records.values.filter { moduleId in it.module.descriptor.dependencies }
        check(dependents.isEmpty()) {
            "Cannot uninstall $moduleId; required by ${dependents.joinToString { it.module.descriptor.id }}"
        }
        if (record.state == ModuleState.STARTED) {
            runCatching { record.module.onStop() }
            record.state = ModuleState.STOPPED
        }
        records.remove(moduleId)
        return true
    }

    @Synchronized
    fun loadAll(context: KernelContext): List<ModuleFailure> {
        val order = runCatching { resolveOrder() }
            .getOrElse { return listOf(ModuleFailure("kernel", "dependency-resolution", it)) }
        val failures = mutableListOf<ModuleFailure>()

        order.forEach { record ->
            if (record.state != ModuleState.REGISTERED) return@forEach
            val missingReadyDependency = record.module.descriptor.dependencies.firstOrNull { dependencyId ->
                records[dependencyId]?.state !in setOf(ModuleState.LOADED, ModuleState.STARTED, ModuleState.STOPPED)
            }
            if (missingReadyDependency != null) {
                record.state = ModuleState.FAILED
                failures += ModuleFailure(
                    record.module.descriptor.id,
                    "load",
                    IllegalStateException("Dependency not ready: $missingReadyDependency")
                )
                return@forEach
            }

            runCatching { record.module.onLoad(context) }
                .onSuccess { record.state = ModuleState.LOADED }
                .onFailure {
                    record.state = ModuleState.FAILED
                    failures += ModuleFailure(record.module.descriptor.id, "load", it)
                }
        }
        return failures
    }

    @Synchronized
    fun startAll(): List<ModuleFailure> {
        val order = runCatching { resolveOrder() }
            .getOrElse { return listOf(ModuleFailure("kernel", "dependency-resolution", it)) }
        val failures = mutableListOf<ModuleFailure>()

        order.forEach { record ->
            if (record.state !in setOf(ModuleState.LOADED, ModuleState.STOPPED)) return@forEach
            val inactiveDependency = record.module.descriptor.dependencies.firstOrNull { dependencyId ->
                records[dependencyId]?.state != ModuleState.STARTED
            }
            if (inactiveDependency != null) {
                record.state = ModuleState.FAILED
                failures += ModuleFailure(
                    record.module.descriptor.id,
                    "start",
                    IllegalStateException("Dependency not started: $inactiveDependency")
                )
                return@forEach
            }

            runCatching { record.module.onStart() }
                .onSuccess { record.state = ModuleState.STARTED }
                .onFailure {
                    record.state = ModuleState.FAILED
                    failures += ModuleFailure(record.module.descriptor.id, "start", it)
                }
        }
        return failures
    }

    @Synchronized
    fun loadAndStart(moduleId: String, context: KernelContext): List<ModuleFailure> {
        val record = records[moduleId]
            ?: return listOf(ModuleFailure(moduleId, "install", IllegalArgumentException("Unknown module")))
        val failures = mutableListOf<ModuleFailure>()

        val missingDependency = record.module.descriptor.dependencies.firstOrNull { dependencyId ->
            records[dependencyId]?.state != ModuleState.STARTED
        }
        if (missingDependency != null) {
            record.state = ModuleState.FAILED
            return listOf(
                ModuleFailure(
                    moduleId,
                    "install",
                    IllegalStateException("Dependency not started: $missingDependency")
                )
            )
        }

        if (record.state == ModuleState.REGISTERED) {
            runCatching { record.module.onLoad(context) }
                .onSuccess { record.state = ModuleState.LOADED }
                .onFailure {
                    record.state = ModuleState.FAILED
                    failures += ModuleFailure(moduleId, "load", it)
                }
        }

        if (record.state in setOf(ModuleState.LOADED, ModuleState.STOPPED)) {
            runCatching { record.module.onStart() }
                .onSuccess { record.state = ModuleState.STARTED }
                .onFailure {
                    record.state = ModuleState.FAILED
                    failures += ModuleFailure(moduleId, "start", it)
                }
        }
        return failures
    }

    @Synchronized
    fun stopAll(): List<ModuleFailure> {
        val order = runCatching { resolveOrder().asReversed() }
            .getOrElse { records.values.toList().asReversed() }
        val failures = mutableListOf<ModuleFailure>()

        order.forEach { record ->
            if (record.state != ModuleState.STARTED) return@forEach
            runCatching { record.module.onStop() }
                .onSuccess { record.state = ModuleState.STOPPED }
                .onFailure {
                    record.state = ModuleState.FAILED
                    failures += ModuleFailure(record.module.descriptor.id, "stop", it)
                }
        }
        return failures
    }

    @Synchronized
    fun health(): List<ModuleHealth> = records.values.map { record ->
        val status = when (record.state) {
            ModuleState.STARTED -> runCatching { record.module.healthCheck() }
                .getOrElse { HealthStatus.failed(it.message ?: it::class.java.simpleName) }
            ModuleState.FAILED -> HealthStatus.failed("Module lifecycle failed")
            else -> HealthStatus.ok("State: ${record.state}")
        }
        ModuleHealth(record.module.descriptor, record.state, status)
    }

    @Synchronized
    fun stateOf(moduleId: String): ModuleState? = records[moduleId]?.state

    @Synchronized
    fun descriptors(): List<ModuleDescriptor> = records.values.map { it.module.descriptor }

    private fun resolveOrder(): List<Record> {
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        val result = mutableListOf<Record>()

        fun visit(moduleId: String) {
            if (moduleId in visited) return
            check(moduleId !in visiting) { "Module dependency cycle detected at $moduleId" }
            val record = records[moduleId] ?: error("Missing module dependency: $moduleId")
            visiting += moduleId
            record.module.descriptor.dependencies.forEach(::visit)
            visiting -= moduleId
            visited += moduleId
            result += record
        }

        records.keys.forEach(::visit)
        return result
    }
}
