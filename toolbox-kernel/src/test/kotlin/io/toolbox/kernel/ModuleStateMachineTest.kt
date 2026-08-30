package io.toolbox.kernel

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModuleStateMachineTest {
    @Test
    fun `registry rejects caller invented lifecycle transition`() {
        val registry = ModuleRegistry { }
        registry.register(module("sample"), ModuleDescriptor("sample", "sample", "1.0.0"))

        assertFalse(registry.transition("sample", setOf(ModuleState.REGISTERED), ModuleState.STARTED))
        assertTrue(registry.transition("sample", setOf(ModuleState.REGISTERED), ModuleState.LOADING))
        assertFalse(registry.transition("sample", setOf(ModuleState.LOADING), ModuleState.STOPPED))
    }

    @Test
    fun `failed cleanup provenance is not retryable`() {
        val stopFailure = ModuleFailure(
            "sample",
            LifecyclePhase.STOP,
            IllegalStateException("dirty stop"),
            ModuleState.STOPPING
        )
        val loadFailure = ModuleFailure(
            "sample",
            LifecyclePhase.LOAD,
            IllegalStateException("load"),
            ModuleState.LOADING
        )

        assertFalse(ModuleStateMachine.canRetry(stopFailure))
        assertTrue(ModuleStateMachine.canRetry(loadFailure))
    }
}
