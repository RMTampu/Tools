package io.toolbox.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KernelStateMachineTest {
    @Test
    fun `kernel state machine allows only declared lifecycle edges`() {
        assertTrue(KernelStateMachine.canTransition(KernelState.NEW, KernelState.STARTING))
        assertTrue(KernelStateMachine.canTransition(KernelState.NEW, KernelState.STOPPED))
        assertTrue(KernelStateMachine.canTransition(KernelState.STARTING, KernelState.RUNNING))
        assertTrue(KernelStateMachine.canTransition(KernelState.STARTING, KernelState.DEGRADED))
        assertTrue(KernelStateMachine.canTransition(KernelState.RUNNING, KernelState.STOPPING))
        assertTrue(KernelStateMachine.canTransition(KernelState.DEGRADED, KernelState.STOPPING))
        assertTrue(KernelStateMachine.canTransition(KernelState.STOPPING, KernelState.STOPPED))
        assertTrue(KernelStateMachine.canTransition(KernelState.STOPPING, KernelState.STOPPED_WITH_ERRORS))
        assertTrue(KernelStateMachine.canTransition(KernelState.STOPPED, KernelState.STARTING))
        assertTrue(KernelStateMachine.canTransition(KernelState.STOPPED_WITH_ERRORS, KernelState.STARTING))
        assertTrue(KernelStateMachine.canTransition(KernelState.FAILED, KernelState.STOPPING))

        assertFalse(KernelStateMachine.canTransition(KernelState.NEW, KernelState.RUNNING))
        assertFalse(KernelStateMachine.canTransition(KernelState.RUNNING, KernelState.STOPPED))
        assertFalse(KernelStateMachine.canTransition(KernelState.STOPPING, KernelState.RUNNING))
        assertFalse(KernelStateMachine.canTransition(KernelState.STOPPED, KernelState.RUNNING))
        assertFalse(KernelStateMachine.canTransition(KernelState.FAILED, KernelState.RUNNING))
    }

    @Test
    fun `unexpected internal failure has a fail closed recovery from every kernel state`() {
        KernelState.entries.forEach { state ->
            val recovery = KernelStateMachine.recoveryAfterUnexpected(state)
            assertEquals(KernelState.FAILED, recovery)
            if (state != KernelState.FAILED) {
                assertTrue(
                    KernelStateMachine.canTransition(state, recovery),
                    "Unexpected failure cannot recover safely from $state"
                )
            }
        }
    }

    @Test
    fun `same state assignment is treated as idempotent`() {
        KernelState.entries.forEach { state ->
            assertTrue(KernelStateMachine.canTransition(state, state))
        }
    }
}
