package io.toolbox.kernel

/** Central lifecycle transition table. Callers cannot invent transitions by supplying expected states. */
internal object ModuleStateMachine {
    private val allowed: Map<ModuleState, Set<ModuleState>> = mapOf(
        ModuleState.REGISTERED to setOf(ModuleState.LOADING, ModuleState.FAILED, ModuleState.QUARANTINED),
        ModuleState.LOADING to setOf(ModuleState.LOADED, ModuleState.FAILED, ModuleState.QUARANTINED),
        ModuleState.LOADED to setOf(ModuleState.STARTING, ModuleState.FAILED, ModuleState.QUARANTINED),
        ModuleState.STARTING to setOf(ModuleState.STARTED, ModuleState.FAILED, ModuleState.QUARANTINED),
        ModuleState.STARTED to setOf(ModuleState.QUIESCING, ModuleState.FAILED, ModuleState.QUARANTINED),
        ModuleState.QUIESCING to setOf(ModuleState.STOPPING, ModuleState.FAILED, ModuleState.QUARANTINED),
        ModuleState.STOPPING to setOf(ModuleState.UNLOADING, ModuleState.FAILED, ModuleState.QUARANTINED),
        ModuleState.UNLOADING to setOf(ModuleState.STOPPED, ModuleState.FAILED, ModuleState.QUARANTINED),
        ModuleState.STOPPED to setOf(ModuleState.LOADING, ModuleState.FAILED, ModuleState.QUARANTINED),
        ModuleState.FAILED to setOf(ModuleState.REGISTERED, ModuleState.QUARANTINED),
        ModuleState.QUARANTINED to emptySet()
    )

    internal fun canTransition(from: ModuleState, to: ModuleState): Boolean = to in allowed.getValue(from)

    internal fun canRetry(failure: ModuleFailure?): Boolean = when (failure?.phase) {
        LifecyclePhase.LOAD,
        LifecyclePhase.START,
        LifecyclePhase.RESOLUTION -> true
        else -> false
    }
}
