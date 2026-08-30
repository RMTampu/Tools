package io.toolbox.kernel

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal class LifecycleCoordinator(
    private val config: KernelConfig,
    private val logger: KernelLogger,
    private val clock: KernelClock,
    private val supervisor: CallbackSupervisor,
    private val modules: ModuleRegistry,
    private val services: ServiceRegistry,
    private val capabilities: CapabilityRegistry,
    private val events: EventBus,
    private val commands: CommandBus
) {
    private val scopes = ConcurrentHashMap<String, ModuleScope>()
    private val generation = AtomicLong(0)
    @Volatile private var activePlan: ResolutionPlan? = null

    internal fun startAll(): KernelResult<Unit> {
        val resolved = modules.resolvePlan()
        if (!resolved.isSuccess) return KernelResult.failure(resolved.errors, resolved.failures)
        val plan = resolved.value ?: return KernelResult.failure(
            KernelError(KernelErrorCode.DEPENDENCY_RESOLUTION, "Resolution returned no plan")
        )
        activePlan = plan
        val failures = mutableListOf<ModuleFailure>()
        plan.order.forEach { moduleId -> activateOne(moduleId, plan, failures) }
        return resultFromFailures(failures)
    }

    internal fun startModule(moduleId: String): KernelResult<Unit> {
        if (!modules.contains(moduleId)) {
            return KernelResult.failure(KernelError(KernelErrorCode.NOT_FOUND, "Unknown module: $moduleId"))
        }
        val resolved = modules.resolvePlan()
        if (!resolved.isSuccess) return KernelResult.failure(resolved.errors, resolved.failures)
        val plan = resolved.value ?: return KernelResult.failure(
            KernelError(KernelErrorCode.DEPENDENCY_RESOLUTION, "Resolution returned no plan")
        )
        activePlan = plan
        val required = dependencyClosure(moduleId, plan)
        val failures = mutableListOf<ModuleFailure>()
        plan.order.filter { it in required }.forEach { id -> activateOne(id, plan, failures) }
        return resultFromFailures(failures)
    }

    internal fun activate(moduleId: String): KernelResult<Unit> = startModule(moduleId)

    internal fun stopModule(moduleId: String): KernelResult<Unit> {
        val handle = modules.handle(moduleId)
            ?: return KernelResult.failure(KernelError(KernelErrorCode.NOT_FOUND, "Unknown module: $moduleId"))
        if (handle.state != ModuleState.STARTED) {
            return KernelResult.failure(KernelError(KernelErrorCode.INVALID_STATE, "Module $moduleId is not STARTED"))
        }
        val dependents = currentPlan().value?.dependentsOf(moduleId).orEmpty()
            .filter { modules.stateOf(it) == ModuleState.STARTED }
        if (dependents.isNotEmpty()) {
            return KernelResult.failure(
                KernelError(KernelErrorCode.CONFLICT, "Cannot stop $moduleId; active required dependents: ${dependents.joinToString()}")
            )
        }
        val failures = mutableListOf<ModuleFailure>()
        stopOne(moduleId, failures)
        return resultFromFailures(failures)
    }

    internal fun restartModule(moduleId: String): KernelResult<Unit> {
        val stop = stopModule(moduleId)
        if (!stop.isSuccess) return stop
        return startModule(moduleId)
    }

    internal fun stopAll(): KernelResult<Unit> {
        val plan = currentPlan()
        val ids = if (plan.isSuccess) {
            plan.value?.order.orEmpty().asReversed()
        } else {
            modules.descriptors().map { it.id }.sortedDescending()
        }
        val failures = mutableListOf<ModuleFailure>()
        ids.forEach { moduleId ->
            if (modules.stateOf(moduleId) == ModuleState.STARTED) stopOne(moduleId, failures)
        }
        activePlan = null
        if (!plan.isSuccess && failures.isEmpty()) return KernelResult.failure(plan.errors, plan.failures)
        return resultFromFailures(failures)
    }

    internal fun uninstall(moduleId: String): KernelResult<Boolean> {
        val handle = modules.handle(moduleId)
            ?: return KernelResult.failure(KernelError(KernelErrorCode.NOT_FOUND, "Unknown module: $moduleId"))
        if (handle.state == ModuleState.QUARANTINED) {
            return KernelResult.failure(
                KernelError(KernelErrorCode.QUARANTINED, "Module $moduleId is quarantined; use forceUninstall to remove it without callbacks")
            )
        }
        val plan = currentPlan()
        val dependents = plan.value?.dependentsOf(moduleId).orEmpty()
        if (dependents.isNotEmpty()) {
            return KernelResult.failure(
                KernelError(KernelErrorCode.CONFLICT, "Cannot uninstall $moduleId; required by ${dependents.joinToString()}")
            )
        }

        if (handle.state == ModuleState.STARTED) {
            val failures = mutableListOf<ModuleFailure>()
            stopOne(moduleId, failures)
            if (failures.isNotEmpty()) return KernelResult.lifecycleFailure(failures)
        }

        scopes.remove(moduleId)?.close()
        val current = modules.handle(moduleId) ?: return KernelResult.success(false)
        if (current.state !in setOf(ModuleState.REGISTERED, ModuleState.STOPPED, ModuleState.FAILED)) {
            return KernelResult.failure(
                KernelError(KernelErrorCode.INVALID_STATE, "Cannot uninstall $moduleId from state ${current.state}")
            )
        }
        modules.remove(moduleId, setOf(ModuleState.REGISTERED, ModuleState.STOPPED, ModuleState.FAILED))
        activePlan = null
        return KernelResult.success(true)
    }

    internal fun forceUninstall(moduleId: String): KernelResult<Boolean> {
        if (!modules.contains(moduleId)) {
            return KernelResult.failure(KernelError(KernelErrorCode.NOT_FOUND, "Unknown module: $moduleId"))
        }
        scopes.remove(moduleId)?.close()
        modules.forceRemove(moduleId)
        activePlan = null
        return KernelResult.success(true)
    }

    internal fun retry(moduleId: String, activateNow: Boolean): KernelResult<Unit> {
        val handle = modules.handle(moduleId)
            ?: return KernelResult.failure(KernelError(KernelErrorCode.NOT_FOUND, "Unknown module: $moduleId"))
        if (handle.state == ModuleState.QUARANTINED) {
            return KernelResult.failure(
                KernelError(KernelErrorCode.QUARANTINED, "Module $moduleId is quarantined and cannot be retried safely")
            )
        }
        if (handle.state != ModuleState.FAILED || scopes.containsKey(moduleId)) {
            return KernelResult.failure(
                KernelError(KernelErrorCode.INVALID_STATE, "Module $moduleId is not retryable from state ${handle.state}")
            )
        }
        if (!modules.transition(moduleId, setOf(ModuleState.FAILED), ModuleState.REGISTERED)) {
            return KernelResult.failure(KernelError(KernelErrorCode.INVALID_STATE, "Retry transition failed for $moduleId"))
        }
        modules.clearFailure(moduleId)
        modules.setHealth(moduleId, HealthStatus.unknown())
        return if (activateNow) startModule(moduleId) else KernelResult.success(Unit)
    }

    internal fun discardFailedRegistration(moduleId: String): Unit {
        if (modules.stateOf(moduleId) == ModuleState.QUARANTINED) return
        scopes.remove(moduleId)?.close()
        modules.forceRemove(moduleId)
        activePlan = null
    }

    internal fun probeHealth(): List<ModuleHealth> {
        modules.descriptors().forEach { descriptor ->
            val handle = modules.handle(descriptor.id) ?: return@forEach
            if (handle.state != ModuleState.STARTED) {
                modules.setHealth(descriptor.id, HealthStatus.unknown("State: ${handle.state}"))
                return@forEach
            }
            val scope = scopes[descriptor.id]
            val permit = scope?.lease?.tryAcquireInvocation()
            if (permit == null) {
                modules.setHealth(descriptor.id, HealthStatus.failed("Module invocation lease is unavailable", clock.nowMillis()))
                return@forEach
            }
            val outcome = supervisor.execute("module:${descriptor.id}:health", config.healthTimeoutMillis) {
                try {
                    handle.module.healthCheck()
                } finally {
                    permit.close()
                }
            }
            val status = when (outcome) {
                is CallbackOutcome.Success -> outcome.value.copy(checkedAtMillis = clock.nowMillis())
                is CallbackOutcome.Failure -> {
                    permit.close()
                    val failure = ModuleFailure(descriptor.id, LifecyclePhase.HEALTH, outcome.error, ModuleState.STARTED)
                    modules.recordFailure(descriptor.id, failure)
                    HealthStatus.failed(outcome.error.message ?: outcome.error::class.java.simpleName, clock.nowMillis(), outcome.error)
                }
                is CallbackOutcome.TimedOut -> {
                    val failure = ModuleFailure(descriptor.id, LifecyclePhase.HEALTH, outcome.error, ModuleState.STARTED)
                    modules.recordFailure(descriptor.id, failure)
                    HealthStatus.failed(outcome.error.message ?: "Health probe timed out", clock.nowMillis(), outcome.error)
                }
            }
            modules.setHealth(descriptor.id, status)
        }
        return modules.healthSnapshot()
    }

    internal fun plan(): KernelResult<ResolutionPlan> = currentPlan()

    private fun activateOne(moduleId: String, plan: ResolutionPlan, failures: MutableList<ModuleFailure>): Unit {
        val state = modules.stateOf(moduleId) ?: return
        if (state == ModuleState.STARTED) return
        if (state in setOf(ModuleState.FAILED, ModuleState.QUARANTINED)) return

        val inactiveDependency = plan.dependenciesOf(moduleId).firstOrNull { modules.stateOf(it) != ModuleState.STARTED }
        if (inactiveDependency != null) {
            val error = IllegalStateException("Required dependency $inactiveDependency is not STARTED")
            val failure = ModuleFailure(moduleId, LifecyclePhase.RESOLUTION, error, state)
            modules.markFailure(moduleId, setOf(state), failure)
            failures += failure
            return
        }

        if (state in setOf(ModuleState.REGISTERED, ModuleState.STOPPED)) {
            load(moduleId, plan, failures)
        }
        if (modules.stateOf(moduleId) == ModuleState.LOADED) {
            start(moduleId, failures)
        }
    }

    private fun load(moduleId: String, plan: ResolutionPlan, failures: MutableList<ModuleFailure>): Unit {
        val handle = modules.handle(moduleId) ?: return
        if (!modules.transition(moduleId, setOf(ModuleState.REGISTERED, ModuleState.STOPPED), ModuleState.LOADING)) return

        val requiredCapabilityFailure = handle.descriptor.requiredCapabilities
            .filter { it.kind == DependencyKind.REQUIRED }
            .firstOrNull { requirement ->
                val providerId = plan.capabilityBindings[CapabilityBindingKey(moduleId, requirement.id)]
                providerId == null || capabilities.findActive(requirement, providerId) == null
            }
        if (requiredCapabilityFailure != null) {
            val error = IllegalStateException("Required capability is not active: ${requiredCapabilityFailure.id}")
            val failure = ModuleFailure(moduleId, LifecyclePhase.RESOLUTION, error, ModuleState.LOADING)
            modules.markFailure(moduleId, setOf(ModuleState.LOADING), failure)
            failures += failure
            return
        }

        val allowedProviders = buildSet {
            add(moduleId)
            handle.descriptor.dependencies.forEach { dependency ->
                val provider = modules.handle(dependency.id)
                if (provider != null && dependency.versionRange.contains(provider.descriptor.version)) add(dependency.id)
            }
            addAll(plan.capabilityBindingsFor(moduleId).values)
        }
        val lease = ModuleLease(OwnerToken(moduleId, generation.incrementAndGet()))
        val scope = ModuleScope(
            lease = lease,
            descriptor = handle.descriptor,
            allowedProviderIds = allowedProviders,
            capabilityBindings = plan.capabilityBindingsFor(moduleId),
            servicesRegistry = services,
            capabilityRegistry = capabilities,
            eventBus = events,
            commandBus = commands,
            clock = clock
        )
        scopes[moduleId] = scope
        val context = KernelContext(config, moduleId, scope.services, scope.capabilities, scope.events, scope.commands, logger)
        when (val outcome = supervisor.execute("module:$moduleId:load", config.lifecycleTimeoutMillis) { handle.module.onLoad(context) }) {
            is CallbackOutcome.Success -> {
                if (!modules.transition(moduleId, setOf(ModuleState.LOADING), ModuleState.LOADED)) return
                modules.clearFailure(moduleId)
                modules.setHealth(moduleId, HealthStatus.unknown("Loaded; not yet started"))
            }
            is CallbackOutcome.Failure -> {
                val failure = ModuleFailure(moduleId, LifecyclePhase.LOAD, outcome.error, ModuleState.LOADING)
                failures += failure
                cleanupAfterCompletedLoadFailure(moduleId, handle.module, failure, failures)
            }
            is CallbackOutcome.TimedOut -> {
                val failure = ModuleFailure(moduleId, LifecyclePhase.LOAD, outcome.error, ModuleState.LOADING)
                failures += failure
                scopes.remove(moduleId)?.close()
                modules.markFailure(moduleId, setOf(ModuleState.LOADING), failure, quarantine = true)
            }
        }
    }

    private fun start(moduleId: String, failures: MutableList<ModuleFailure>): Unit {
        val handle = modules.handle(moduleId) ?: return
        val scope = scopes[moduleId] ?: return
        if (!modules.transition(moduleId, setOf(ModuleState.LOADED), ModuleState.STARTING)) return
        when (val outcome = supervisor.execute("module:$moduleId:start", config.lifecycleTimeoutMillis) { handle.module.onStart() }) {
            is CallbackOutcome.Success -> {
                if (!modules.transition(moduleId, setOf(ModuleState.STARTING), ModuleState.STARTED)) return
                scope.lease.activateInvocations()
                modules.clearFailure(moduleId)
                modules.setHealth(moduleId, HealthStatus.unknown("Started; not yet probed"))
            }
            is CallbackOutcome.Failure -> {
                val failure = ModuleFailure(moduleId, LifecyclePhase.START, outcome.error, ModuleState.STARTING)
                failures += failure
                cleanupAfterCompletedStartFailure(moduleId, handle.module, failure, failures)
            }
            is CallbackOutcome.TimedOut -> {
                val failure = ModuleFailure(moduleId, LifecyclePhase.START, outcome.error, ModuleState.STARTING)
                failures += failure
                scopes.remove(moduleId)?.close()
                modules.markFailure(moduleId, setOf(ModuleState.STARTING), failure, quarantine = true)
            }
        }
    }

    private fun stopOne(moduleId: String, failures: MutableList<ModuleFailure>): Unit {
        val handle = modules.handle(moduleId) ?: return
        val scope = scopes[moduleId] ?: return
        if (!modules.transition(moduleId, setOf(ModuleState.STARTED), ModuleState.QUIESCING)) return

        if (!scope.lease.quiesce(config.invocationDrainTimeoutMillis)) {
            val error = java.util.concurrent.TimeoutException("Active invocations did not drain for $moduleId")
            val failure = ModuleFailure(moduleId, LifecyclePhase.QUIESCE, error, ModuleState.QUIESCING)
            failures += failure
            scopes.remove(moduleId)?.close()
            modules.markFailure(moduleId, setOf(ModuleState.QUIESCING), failure, quarantine = true)
            return
        }

        if (!modules.transition(moduleId, setOf(ModuleState.QUIESCING), ModuleState.STOPPING)) return
        var callbackFailure: ModuleFailure? = null
        when (val outcome = supervisor.execute("module:$moduleId:stop", config.lifecycleTimeoutMillis) { handle.module.onStop() }) {
            is CallbackOutcome.Success -> Unit
            is CallbackOutcome.Failure -> {
                val failure = ModuleFailure(moduleId, LifecyclePhase.STOP, outcome.error, ModuleState.STOPPING)
                callbackFailure = failure
                failures += failure
            }
            is CallbackOutcome.TimedOut -> {
                val failure = ModuleFailure(moduleId, LifecyclePhase.STOP, outcome.error, ModuleState.STOPPING)
                failures += failure
                scopes.remove(moduleId)?.close()
                modules.markFailure(moduleId, setOf(ModuleState.STOPPING), failure, quarantine = true)
                return
            }
        }

        if (!modules.transition(moduleId, setOf(ModuleState.STOPPING), ModuleState.UNLOADING)) return
        when (val outcome = supervisor.execute("module:$moduleId:unload", config.lifecycleTimeoutMillis) { handle.module.onUnload() }) {
            is CallbackOutcome.Success -> Unit
            is CallbackOutcome.Failure -> {
                val failure = ModuleFailure(moduleId, LifecyclePhase.UNLOAD, outcome.error, ModuleState.UNLOADING)
                callbackFailure = failure
                failures += failure
            }
            is CallbackOutcome.TimedOut -> {
                val failure = ModuleFailure(moduleId, LifecyclePhase.UNLOAD, outcome.error, ModuleState.UNLOADING)
                failures += failure
                scopes.remove(moduleId)?.close()
                modules.markFailure(moduleId, setOf(ModuleState.UNLOADING), failure, quarantine = true)
                return
            }
        }

        scopes.remove(moduleId)?.close()
        if (modules.transition(moduleId, setOf(ModuleState.UNLOADING), ModuleState.STOPPED)) {
            callbackFailure?.let { modules.recordFailure(moduleId, it) } ?: modules.clearFailure(moduleId)
            modules.setHealth(moduleId, HealthStatus.unknown("Stopped"))
        }
    }

    private fun cleanupAfterCompletedLoadFailure(
        moduleId: String,
        module: ToolBoxModule,
        primary: ModuleFailure,
        failures: MutableList<ModuleFailure>
    ): Unit {
        when (val cleanup = supervisor.execute("module:$moduleId:unload-after-load-failure", config.lifecycleTimeoutMillis) { module.onUnload() }) {
            is CallbackOutcome.Success -> Unit
            is CallbackOutcome.Failure -> failures += ModuleFailure(moduleId, LifecyclePhase.UNLOAD, cleanup.error, ModuleState.LOADING)
            is CallbackOutcome.TimedOut -> {
                val timeoutFailure = ModuleFailure(moduleId, LifecyclePhase.UNLOAD, cleanup.error, ModuleState.LOADING)
                failures += timeoutFailure
                scopes.remove(moduleId)?.close()
                modules.markFailure(moduleId, setOf(ModuleState.LOADING), timeoutFailure, quarantine = true)
                return
            }
        }
        scopes.remove(moduleId)?.close()
        modules.markFailure(moduleId, setOf(ModuleState.LOADING), primary)
    }

    private fun cleanupAfterCompletedStartFailure(
        moduleId: String,
        module: ToolBoxModule,
        primary: ModuleFailure,
        failures: MutableList<ModuleFailure>
    ): Unit {
        when (val cleanup = supervisor.execute("module:$moduleId:unload-after-start-failure", config.lifecycleTimeoutMillis) { module.onUnload() }) {
            is CallbackOutcome.Success -> Unit
            is CallbackOutcome.Failure -> failures += ModuleFailure(moduleId, LifecyclePhase.UNLOAD, cleanup.error, ModuleState.STARTING)
            is CallbackOutcome.TimedOut -> {
                val timeoutFailure = ModuleFailure(moduleId, LifecyclePhase.UNLOAD, cleanup.error, ModuleState.STARTING)
                failures += timeoutFailure
                scopes.remove(moduleId)?.close()
                modules.markFailure(moduleId, setOf(ModuleState.STARTING), timeoutFailure, quarantine = true)
                return
            }
        }
        scopes.remove(moduleId)?.close()
        modules.markFailure(moduleId, setOf(ModuleState.STARTING), primary)
    }

    private fun dependencyClosure(moduleId: String, plan: ResolutionPlan): Set<String> {
        val result = linkedSetOf<String>()
        fun visit(id: String) {
            if (!result.add(id)) return
            plan.dependenciesOf(id).forEach(::visit)
        }
        visit(moduleId)
        return result
    }

    private fun currentPlan(): KernelResult<ResolutionPlan> {
        activePlan?.let { return KernelResult.success(it) }
        val resolved = modules.resolvePlan()
        if (resolved.isSuccess) activePlan = resolved.value
        return resolved
    }

    private fun resultFromFailures(failures: List<ModuleFailure>): KernelResult<Unit> =
        if (failures.isEmpty()) KernelResult.success(Unit) else KernelResult.lifecycleFailure(failures)
}
