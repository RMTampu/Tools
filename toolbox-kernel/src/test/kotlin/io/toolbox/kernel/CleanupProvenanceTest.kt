package io.toolbox.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CleanupProvenanceTest {
    @Test
    fun `startup load failure with unload failure is not retryable`() {
        val kernel = ToolBoxKernel()
        assertTrue(
            kernel.install(
                module(
                    "dirty-load-startup",
                    onLoadBlock = { error("load failed") },
                    onUnloadBlock = { error("cleanup failed") }
                )
            ).isSuccess
        )

        val result = kernel.start()

        assertFalse(result.isSuccess)
        assertEquals(ModuleState.FAILED, kernel.moduleState("dirty-load-startup"))
        assertEquals(
            LifecyclePhase.UNLOAD,
            kernel.snapshot().modules.single { it.descriptor.id == "dirty-load-startup" }.lastFailure?.phase
        )
        assertFalse(kernel.retryModule("dirty-load-startup").isSuccess)
    }

    @Test
    fun `startup start failure with unload failure is not retryable`() {
        val kernel = ToolBoxKernel()
        assertTrue(
            kernel.install(
                module(
                    "dirty-start-startup",
                    onStartBlock = { error("start failed") },
                    onUnloadBlock = { error("cleanup failed") }
                )
            ).isSuccess
        )

        val result = kernel.start()

        assertFalse(result.isSuccess)
        assertEquals(ModuleState.FAILED, kernel.moduleState("dirty-start-startup"))
        assertEquals(
            LifecyclePhase.UNLOAD,
            kernel.snapshot().modules.single { it.descriptor.id == "dirty-start-startup" }.lastFailure?.phase
        )
        assertFalse(kernel.retryModule("dirty-start-startup").isSuccess)
    }
}
