package io.toolbox.kernel

/**
 * Central kernel lifecycle transition table. Kernel state changes are rejected unless they are
 * explicitly part of the lifecycle contract. An unexpected internal exception always fails closed
 * because the operation may have partially mutated module/runtime state before escaping its boundary.
 */
internal object KernelStateMachine {
    private val allowed: Map<KernelState, Set<KernelState>> = mapOf(
        KernelState.NEW to setOf(KernelState.STARTING, KernelState.STOPPED, KernelState.FAILED),
        KernelState.STARTING to setOf(KernelState.RUNNING, KernelState.DEGRADED, KernelState.FAILED),
        KernelState.RUNNING to setOf(KernelState.DEGRADED, KernelState.STOPPING, KernelState.FAILED),
        KernelState.DEGRADED to setOf(KernelState.RUNNING, KernelState.STOPPING, KernelState.FAILED),
        KernelState.STOPPING to setOf(KernelState.STOPPED, KernelState.STOPPED_WITH_ERRORS, KernelState.FAILED),
        KernelState.STOPPED to setOf(KernelState.STARTING, KernelState.FAILED),
        KernelState.STOPPED_WITH_ERRORS to setOf(KernelState.STARTING, KernelState.FAILED),
        KernelState.FAILED to setOf(KernelState.STOPPING)
    )

    internal fun canTransition(from: KernelState, to: KernelState): Boolean =
        from == to || to in allowed.getValue(from)

    internal fun recoveryAfterUnexpected(from: KernelState): KernelState =
        if (from == KernelState.FAILED) from else KernelState.FAILED
}
