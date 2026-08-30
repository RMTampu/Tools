package io.toolbox.kernel

import java.util.concurrent.ConcurrentHashMap

internal class LifecycleCoordinator(
    private val config: KernelConfig,
    private val ports: KernelPorts,
    private val modules: ModuleRegistry,
    private val services: ServiceRegistry,
    private val capabilities: CapabilityRegistry,
    private val events: EventBus,
    private val commands: CommandBus
) {
    private val scopes = ConcurrentHashMap<String, ModuleScope>()

    internal fun startAll(): KernelResult<Unit> {
        val plan = modules.resolvePlan()
        if (!plan.isSuccess) return KernelResult(value = null, errors = plan.errors, failures = plan.failures)
        val failures = mutableListOf<ModuleFailure>()
        plan.value.orEmpty().forEach { moduleId -> loadIfNeeded(moduleId, failures) }
        plan.value.orEmpty().forEach { moduleId -> startIfReady(moduleId, failures) }
        return if (failures.isEmpty()) KernelResult.success(Unit) else KernelResult.lifecycleFailure(failures)
    }

    internal fun activate(moduleId: String): KernelResult<Unit> {
        val failures = mutableListOf<ModuleFailure>()
        loadIfNeeded(moduleId, failures)
        if (failures.isEmpty()) startIfReady(moduleId, failures)
        return if (failures.isEmpty()) KernelResult.success(Unit) else KernelResult.lifecycleFailure(failures)
    }

    private fun loadIfNeeded(moduleId: String, failures: MutableList<ModuleFailure>): Unit {
        val handle = modules.handle(moduleId) ?: return
        if (handle.state != ModuleState.REGISTERED) return
        val unavailableDependency = handle.descriptor.dependencies.firstOrNull { dependency ->
            if (dependency.optional && !modules.contains(dependency.id)) return@firstOrNull false
            modules.stateOf(dependency.id) !in setOf(ModuleState.LOADED, ModuleState.STARTED, ModuleState.STOPPED)
        }
        if (unavailableDependency != null) {
            modules.forceState(moduleId, ModuleState.FAILED)
            failures += ModuleFailure(moduleId, "load", IllegalStateException("Dependency not ready: ${unavailableDependency.id}"))
            return
        }
        val missingCapability = handle.descriptor.requiredCapabilities.firstOrNull { capabilities.get(it) == null }
        if (missingCapability != null) {
            modules.forceState(moduleId, ModuleState.FAILED)
            failures += ModuleFailure(moduleId, "capability-resolution", IllegalStateException("Required capability not available: $missingCapability"))
            return
        }
        if (!modules.transition(moduleId, setOf(ModuleState.REGISTERED), ModuleState.LOADING)) return

        val scope = ModuleScope(moduleId, services, capabilities, events, commands, ports.clock)
        scopes[moduleId] = scope
        val context = KernelContext(config, moduleId, scope.services, scope.capabilities, scope.events, scope.commands, ports.logger)
        val loadResult = runCatching {
            ports.executor.execute("module:$moduleId:load") { handle.module.onLoad(context) }
        }
        loadResult.onSuccess {
            modules.forceState(moduleId, ModuleState.LOADED)
            modules.setHealth(moduleId, HealthStatus.unknown("Loaded; not yet probed"))
        }.onFailure { error ->
            failures += ModuleFailure(moduleId, "load", error)
            cleanupAfterFailedLoad(moduleId, handle.module, failures)
            modules.forceState(moduleId, ModuleState.FAILED)
        }
    }

    private fun startIfReady(moduleId: String, failures: MutableList<ModuleFailure>): Unit {
        val handle = modules.handle(moduleId) ?: return
        if (handle.state !in setOf(ModuleState.LOADED, ModuleState.STOPPED)) return
        val inactiveDependency = handle.descriptor.dependencies.firstOrNull { dependency ->
            if (dependency.optional && !modules.contains(dependency.id)) return@firstOrNull false
            modules.stateOf(dependency.id) != ModuleState.STARTED
        }
        if (inactiveDependency != null) {
            modules.forceState(moduleId, ModuleState.FAILED)
            failures += ModuleFailure(moduleId, "start", IllegalStateException("Dependency not started: ${inactiveDependency.id}"))
            cleanupAfterFailedStart(moduleId, handle.module, failures)
            return
        }
        if (!modules.transition(moduleId, setOf(ModuleState.LOADED, ModuleState.STOPPED), ModuleState.STARTING)) return
        runCatching {
            ports.executor.execute("module:$moduleId:start") { handle.module.onStart() }
        }.onSuccess {
            modules.forceState(moduleId, ModuleState.STARTED)
            modules.setHealth(moduleId, HealthStatus.unknown("Started; not yet probed"))
        }.onFailure { error ->
            failures += ModuleFailure(moduleId, "start", error)
            cleanupAfterFailedStart(moduleId, handle.module, failures)
            modules.forceState(moduleId, ModuleState.FAILED)
        }
    }

    internal fun stopAll(): KernelResult<Unit> {
        val plan = modules.resolvePlan()
        val ids = if (plan.isSuccess) plan.value.orEmpty().asReversed() else modules.descriptors().map { it.id }.asReversed()
        val failures = mutableListOf<ModuleFailure>()
        ids.forEach { moduleId -> stopOne(moduleId, failures) }
        if (!plan.isSuccess && failures.isEmpty()) {
            return KernelResult(value = null, errors = plan.errors, failures = plan.failures)
        }
        return if (failures.isEmpty()) KernelResult.success(Unit) else KernelResult.lifecycleFailure(failures)
    }

    private fun stopOne(moduleId: String, failures: MutableList<ModuleFailure>): Unit {
        val handle = modules.handle(moduleId) ?: return
        val allowed = handle.state == ModuleState.STARTED || (handle.state == ModuleState.FAILED && scopes.containsKey(moduleId))
        if (!allowed) return
        modules.forceState(moduleId, ModuleState.STOPPING)
        runCatching {
            ports.executor.execute("module:$moduleId:stop") { handle.module.onStop() }
        }.onSuccess {
            modules.forceState(moduleId, ModuleState.STOPPED)
            modules.setHealth(moduleId, HealthStatus.unknown("Stopped"))
        }.onFailure { error ->
            modules.forceState(moduleId, ModuleState.FAILED)
            failures += ModuleFailure(moduleId, "stop", error)
        }
    }

    internal fun uninstall(moduleId: String): KernelResult<Boolean> {
        val handle = modules.handle(moduleId)
            ?: return KernelResult.failure(KernelError(KernelErrorCode.NOT_FOUND, "Unknown module: $moduleId"))
        val dependents = modules.requiredDependents(moduleId)
        if (dependents.isNotEmpty()) {
            return KernelResult.failure(KernelError(KernelErrorCode.CONFLICT, "Cannot uninstall $moduleId; required by ${dependents.joinToString()}"))
        }
        val failures = mutableListOf<ModuleFailure>()
        if (handle.state == ModuleState.STARTED || (handle.state == ModuleState.FAILED && scopes.containsKey(moduleId))) {
            stopOne(moduleId, failures)
            if (failures.isNotEmpty()) return KernelResult.lifecycleFailure(failures)
        }
        val current = modules.handle(moduleId) ?: return KernelResult.success(false)
        if (current.state in setOf(ModuleState.LOADED, ModuleState.STOPPED)) {
            modules.forceState(moduleId, ModuleState.UNLOADING)
            runCatching {
                ports.executor.execute("module:$moduleId:unload") { current.module.onUnload() }
            }.onFailure { error -> failures += ModuleFailure(moduleId, "unload", error) }
            if (failures.isNotEmpty()) {
                modules.forceState(moduleId, ModuleState.FAILED)
                return KernelResult.lifecycleFailure(failures)
            }
        }
        scopes.remove(moduleId)?.close()
        modules.remove(moduleId)
        return KernelResult.success(true)
    }

    internal fun retry(moduleId: String, activateNow: Boolean): KernelResult<Unit> {
        val handle = modules.handle(moduleId)
            ?: return KernelResult.failure(KernelError(KernelErrorCode.NOT_FOUND, "Unknown module: $moduleId"))
        if (handle.state != ModuleState.FAILED) {
            return KernelResult.failure(KernelError(KernelErrorCode.INVALID_STATE, "Module $moduleId is not FAILED"))
        }
        scopes.remove(moduleId)?.close()
        modules.forceState(moduleId, ModuleState.REGISTERED)
        modules.setHealth(moduleId, HealthStatus.unknown())
        return if (activateNow) activate(moduleId) else KernelResult.success(Unit)
    }

    internal fun discardFailedRegistration(moduleId: String): Unit {
        scopes.remove(moduleId)?.close()
        modules.remove(moduleId)
    }

    internal fun probeHealth(): List<ModuleHealth> {
        modules.descriptors().forEach { descriptor ->
            val handle = modules.handle(descriptor.id) ?: return@forEach
            if (handle.state != ModuleState.STARTED) {
                modules.setHealth(descriptor.id, HealthStatus.unknown("State: ${handle.state}"))
                return@forEach
            }
            val status = runCatching {
                var result: HealthStatus? = null
                ports.executor.execute("module:${descriptor.id}:health") { result = handle.module.healthCheck() }
                result ?: HealthStatus.failed("Health executor returned without result")
            }.getOrElse { HealthStatus.failed(it.message ?: it::class.java.simpleName) }
            modules.setHealth(descriptor.id, status)
        }
        return modules.healthSnapshot()
    }

    private fun cleanupAfterFailedLoad(moduleId: String, module: ToolBoxModule, failures: MutableList<ModuleFailure>): Unit {
        runCatching { ports.executor.execute("module:$moduleId:unload-after-load-failure") { module.onUnload() } }
            .onFailure { failures += ModuleFailure(moduleId, "cleanup-unload", it) }
        scopes.remove(moduleId)?.close()
    }

    private fun cleanupAfterFailedStart(moduleId: String, module: ToolBoxModule, failures: MutableList<ModuleFailure>): Unit {
        runCatching { ports.executor.execute("module:$moduleId:stop-after-start-failure") { module.onStop() } }
            .onFailure { failures += ModuleFailure(moduleId, "cleanup-stop", it) }
        runCatching { ports.executor.execute("module:$moduleId:unload-after-start-failure") { module.onUnload() } }
            .onFailure { failures += ModuleFailure(moduleId, "cleanup-unload", it) }
        scopes.remove(moduleId)?.close()
    }
}
