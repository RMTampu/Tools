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
    private val activeWiring = linkedMapOf<String, ResolutionPlan>()
    @Volatile private var activePlan: ResolutionPlan? = null

    internal fun startAll(): KernelResult<Unit> {
        activeWiring.clear()
        activePlan = ResolutionPlan.empty()
        val failures = mutableListOf<ModuleFailure>()
        val errors = mutableListOf<KernelError>()

        modules.descriptors().sortedBy { it.id }.forEach { descriptor ->
            val state = modules.stateOf(descriptor.id) ?: return@forEach
            if (state in setOf(ModuleState.FAILED, ModuleState.QUARANTINED)) return@forEach

            val resolved = modules.resolvePlanFor(descriptor.id)
            if (!resolved.isSuccess) {
                errors += resolved.errors
                val failure = ModuleFailure(
                    descriptor.id,
                    LifecyclePhase.RESOLUTION,
                    IllegalStateException(resolved.errors.joinToString("; ") { it.message }),
                    state
                )
                modules.recordFailure(descriptor.id, failure)
                failures += failure
                return@forEach
            }

            val plan = resolved.value ?: return@forEach
            plan.order.forEach { moduleId -> activateOne(moduleId, plan, failures) }
        }

        return if (errors.isEmpty() && failures.isEmpty()) {
            KernelResult.success(Unit)
        } else {
            KernelResult.failure(errors.distinctBy { it.code to it.message }, failures)
        }
    }

    internal fun startModule(moduleId: String): KernelResult<Unit> {
        val target = modules.handle(moduleId)
            ?: return KernelResult.failure(KernelError(KernelErrorCode.NOT_FOUND, "Unknown module: $moduleId"))
        if (target.state == ModuleState.STARTED) return KernelResult.success(Unit)

        val resolved = modules.resolvePlanFor(moduleId)
        if (!resolved.isSuccess) return KernelResult.failure(resolved.errors, resolved.failures)
        val plan = resolved.value ?: return KernelResult.failure(
            KernelError(KernelErrorCode.DEPENDENCY_RESOLUTION, "Resolution returned no plan")
        )
        val failures = mutableListOf<ModuleFailure>()
        plan.order.forEach { id -> activateOne(id, plan, failures) }
        return resultFromFailures(failures)
    }

    internal fun activate(moduleId: String): KernelResult<Unit> = startModule(moduleId)

    internal fun stopModule(moduleId: String): KernelResult<Unit> {
        val handle = modules.handle(moduleId)
            ?: return KernelResult.failure(KernelError(KernelErrorCode.NOT_FOUND, "Unknown module: $moduleId"))
        if (handle.state != ModuleState.STARTED) {
            return KernelResult.failure(KernelError(KernelErrorCode.INVALID_STATE, "Module $moduleId is not STARTED"))
        }
        val dependents = activeDependentsOf(moduleId)
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
        activeWiring.clear()
        activePlan = null
        if (!plan.isSuccess && failures.isEmpty()) return KernelResult.failure(plan.errors, plan.failures)
        return resultFromFailures(failures)
    }

    internal fun uninstall(moduleId: String): KernelResult<Boolean> {
        val handle = modules.handle(moduleId)
            ?: return KernelResult.failure(KernelError(KernelErrorCode.NOT_FOUND, "Unknown module: $moduleId"))
        if (handle.state == ModuleState.QUARANTINED) {
            return KernelResult.failure(
                KernelError(
                    KernelErrorCode.QUARANTINED,
                    "Module $moduleId is quarantined; it can be purged only after all timed-out work actually terminates"
                )
            )
        }
        if (handle.state == ModuleState.FAILED && handle.lastFailure?.phase == LifecyclePhase.UNLOAD) {
            return KernelResult.failure(
                KernelError(
                    KernelErrorCode.INVALID_STATE,
                    "Module $moduleId has an unresolved unload cleanup failure; use forceUninstall only after accepting emergency purge semantics"
                )
            )
        }
        val dependents = modules.removalBlockers(moduleId, activePlan)
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
        removeActiveWiring(moduleId)
        return KernelResult.success(true)
    }

    internal fun forceUninstall(moduleId: String): KernelResult<Boolean> {
        val handle = modules.handle(moduleId)
            ?: return KernelResult.failure(KernelError(KernelErrorCode.NOT_FOUND, "Unknown module: $moduleId"))
        if (handle.state !in setOf(ModuleState.FAILED, ModuleState.QUARANTINED)) {
            return KernelResult.failure(
                KernelError(
                    KernelErrorCode.INVALID_STATE,
                    "Emergency purge is allowed only for FAILED or QUARANTINED modules; $moduleId is ${handle.state}"
                )
            )
        }

        val dependents = activeDependentsOf(moduleId)
        if (dependents.isNotEmpty()) {
            return KernelResult.failure(
                KernelError(KernelErrorCode.CONFLICT, "Cannot purge $moduleId; required by ${dependents.joinToString()}")
            )
        }

        val scope = scopes[moduleId]
        if (scope != null) {
            scope.close()
            if (scope.lease.hasActiveInvocations() || scope.lease.hasOutstandingCallbacks()) {
                return KernelResult.failure(
                    KernelError(
                        KernelErrorCode.QUARANTINED,
                        "Cannot purge $moduleId while timed-out callbacks or active invocations are still running"
                    )
                )
            }
            scopes.remove(moduleId, scope)
        }
        modules.forceRemove(moduleId)
        removeActiveWiring(moduleId)
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
        removeActiveWiring(moduleId)
    }

    internal fun probeHealth(): List<ModuleHealth> {
        modules.descriptors().forEach { descriptor ->
            val handle = modules.handle(descriptor.id) ?: return@forEach
            if (handle.state != ModuleState.STARTED) {
                modules.setHealth(descriptor.id, HealthStatus.unknown("State: ${handle.state}"))
                return@forEach
            }
            val scope = scopes[descriptor.id]
            if (scope == null) {
                modules.setHealth(descriptor.id, HealthStatus.failed("Module scope is unavailable", clock.nowMillis()))
                return@forEach
            }
            if (scope.lease.hasOutstandingCallbacks()) {
                modules.setHealth(
                    descriptor.id,
                    HealthStatus.failed("Timed-out callback is still running", clock.nowMillis())
                )
                return@forEach
            }
            val permit = scope.lease.tryAcquireInvocation()
            if (permit == null) {
                modules.setHealth(descriptor.id, HealthStatus.failed("Module invocation lease is unavailable", clock.nowMillis()))
                return@forEach
            }
            val outcome = supervisor.executeExtension(
                owner = scope.lease.token,
                taskName = "module:${descriptor.id}:health",
                timeoutMillis = config.healthTimeoutMillis
            ) {
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
                    if (outcome.completion.isComplete) {
                        permit.close()
                    } else {
                        scope.lease.trackTimedOut(outcome.completion)
                    }
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
            start(moduleId, plan, failures)
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
                quarantine(moduleId, setOf(ModuleState.LOADING), failure, outcome.completion)
            }
        }
    }

    private fun start(moduleId: String, plan: ResolutionPlan, failures: MutableList<ModuleFailure>): Unit {
        val handle = modules.handle(moduleId) ?: return
        val scope = scopes[moduleId] ?: return
        if (!modules.transition(moduleId, setOf(ModuleState.LOADED), ModuleState.STARTING)) return
        when (val outcome = supervisor.execute("module:$moduleId:start", config.lifecycleTimeoutMillis) { handle.module.onStart() }) {
            is CallbackOutcome.Success -> {
                if (!modules.transition(moduleId, setOf(ModuleState.STARTING), ModuleState.STARTED)) return
                scope.lease.activateInvocations()
                recordActiveWiring(moduleId, plan)
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
                quarantine(moduleId, setOf(ModuleState.STARTING), failure, outcome.completion)
            }
        }
    }

    private fun stopOne(moduleId: String, failures: MutableList<ModuleFailure>): Unit {
        val handle = modules.handle(moduleId) ?: return
        val scope = scopes[moduleId] ?: return
        if (!modules.transition(moduleId, setOf(ModuleState.STARTED), ModuleState.QUIESCING)) return
        removeActiveWiring(moduleId)

        if (!scope.lease.quiesce(config.invocationDrainTimeoutMillis)) {
            val error = java.util.concurrent.TimeoutException("Active invocations did not drain for $moduleId")
            val failure = ModuleFailure(moduleId, LifecyclePhase.QUIESCE, error, ModuleState.QUIESCING)
            failures += failure
            scope.close()
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
                quarantine(moduleId, setOf(ModuleState.STOPPING), failure, outcome.completion)
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
                quarantine(moduleId, setOf(ModuleState.UNLOADING), failure, outcome.completion)
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
        var finalFailure = primary
        when (val cleanup = supervisor.execute("module:$moduleId:unload-after-load-failure", config.lifecycleTimeoutMillis) { module.onUnload() }) {
            is CallbackOutcome.Success -> Unit
            is CallbackOutcome.Failure -> {
                val cleanupFailure = ModuleFailure(moduleId, LifecyclePhase.UNLOAD, cleanup.error, ModuleState.LOADING)
                failures += cleanupFailure
                finalFailure = cleanupFailure
            }
            is CallbackOutcome.TimedOut -> {
                val timeoutFailure = ModuleFailure(moduleId, LifecyclePhase.UNLOAD, cleanup.error, ModuleState.LOADING)
                failures += timeoutFailure
                quarantine(moduleId, setOf(ModuleState.LOADING), timeoutFailure, cleanup.completion)
                return
            }
        }
        scopes.remove(moduleId)?.close()
        modules.markFailure(moduleId, setOf(ModuleState.LOADING), finalFailure)
    }

    private fun cleanupAfterCompletedStartFailure(
        moduleId: String,
        module: ToolBoxModule,
        primary: ModuleFailure,
        failures: MutableList<ModuleFailure>
    ): Unit {
        var finalFailure = primary
        when (val cleanup = supervisor.execute("module:$moduleId:unload-after-start-failure", config.lifecycleTimeoutMillis) { module.onUnload() }) {
            is CallbackOutcome.Success -> Unit
            is CallbackOutcome.Failure -> {
                val cleanupFailure = ModuleFailure(moduleId, LifecyclePhase.UNLOAD, cleanup.error, ModuleState.STARTING)
                failures += cleanupFailure
                finalFailure = cleanupFailure
            }
            is CallbackOutcome.TimedOut -> {
                val timeoutFailure = ModuleFailure(moduleId, LifecyclePhase.UNLOAD, cleanup.error, ModuleState.STARTING)
                failures += timeoutFailure
                quarantine(moduleId, setOf(ModuleState.STARTING), timeoutFailure, cleanup.completion)
                return
            }
        }
        scopes.remove(moduleId)?.close()
        modules.markFailure(moduleId, setOf(ModuleState.STARTING), finalFailure)
    }

    private fun quarantine(
        moduleId: String,
        expected: Set<ModuleState>,
        failure: ModuleFailure,
        completion: CallbackCompletion
    ): Unit {
        scopes[moduleId]?.let { scope ->
            scope.lease.trackTimedOut(completion)
            scope.close()
        }
        removeActiveWiring(moduleId)
        modules.markFailure(moduleId, expected, failure, quarantine = true)
    }

    private fun recordActiveWiring(moduleId: String, plan: ResolutionPlan): Unit {
        activeWiring[moduleId] = plan.wiringFor(moduleId)
        activePlan = mergeActiveWiring()
    }

    private fun removeActiveWiring(moduleId: String): Unit {
        if (activeWiring.remove(moduleId) != null) activePlan = mergeActiveWiring()
    }

    private fun mergeActiveWiring(): ResolutionPlan {
        var merged = ResolutionPlan.empty()
        activeWiring.values.forEach { wiring -> merged = merged.merge(wiring) }
        return merged
    }

    private fun activeDependentsOf(moduleId: String): List<String> {
        val plan = activePlan
        return if (plan != null) {
            plan.dependentsOf(moduleId).filter { modules.stateOf(it) == ModuleState.STARTED }
        } else {
            modules.potentialRequiredDependentsOf(moduleId, startedOnly = true)
        }
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

private fun ResolutionPlan.wiringFor(moduleId: String): ResolutionPlan {
    val directDependencies = dependenciesOf(moduleId)
    return ResolutionPlan(
        order = listOf(moduleId),
        hardDependencies = buildMap {
            put(moduleId, directDependencies)
            directDependencies.forEach { providerId -> putIfAbsent(providerId, emptySet()) }
        },
        capabilityBindings = capabilityBindings.filterKeys { it.consumerModuleId == moduleId }
    )
}
