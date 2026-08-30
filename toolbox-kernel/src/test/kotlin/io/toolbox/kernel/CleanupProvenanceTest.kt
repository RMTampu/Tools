package io.toolbox.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
        assertFalse(kernel.uninstall("dirty-load-startup").isSuccess)
        assertEquals(ModuleState.FAILED, kernel.moduleState("dirty-load-startup"))
        assertTrue(kernel.forceUninstall("dirty-load-startup").isSuccess)
        assertNull(kernel.moduleState("dirty-load-startup"))
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
        assertFalse(kernel.uninstall("dirty-start-startup").isSuccess)
        assertEquals(ModuleState.FAILED, kernel.moduleState("dirty-start-startup"))
        assertTrue(kernel.forceUninstall("dirty-start-startup").isSuccess)
        assertNull(kernel.moduleState("dirty-start-startup"))
    }

    @Test
    fun `runtime rollback unload failure requires emergency purge`() {
        val kernel = ToolBoxKernel()
        assertTrue(kernel.start().isSuccess)

        val install = kernel.install(
            module(
                "dirty-runtime",
                onStartBlock = { error("start failed") },
                onUnloadBlock = { error("cleanup failed") }
            )
        )

        assertFalse(install.isSuccess)
        assertEquals(ModuleState.FAILED, kernel.moduleState("dirty-runtime"))
        assertEquals(
            LifecyclePhase.UNLOAD,
            kernel.snapshot().modules.single { it.descriptor.id == "dirty-runtime" }.lastFailure?.phase
        )

        val normalUninstall = kernel.uninstall("dirty-runtime")
        assertFalse(normalUninstall.isSuccess)
        assertEquals(KernelErrorCode.INVALID_STATE, normalUninstall.errors.single().code)
        assertEquals(ModuleState.FAILED, kernel.moduleState("dirty-runtime"))

        assertTrue(kernel.forceUninstall("dirty-runtime").isSuccess)
        assertNull(kernel.moduleState("dirty-runtime"))
    }

    @Test
    fun `completed unload failure cannot be erased by normal uninstall`() {
        val kernel = ToolBoxKernel()
        assertTrue(kernel.install(module("dirty-stop", onUnloadBlock = { error("unload failed") })).isSuccess)
        assertTrue(kernel.start().isSuccess)

        val stop = kernel.stopModule("dirty-stop")

        assertFalse(stop.isSuccess)
        assertEquals(ModuleState.FAILED, kernel.moduleState("dirty-stop"))
        assertEquals(
            LifecyclePhase.UNLOAD,
            kernel.snapshot().modules.single { it.descriptor.id == "dirty-stop" }.lastFailure?.phase
        )

        val normalUninstall = kernel.uninstall("dirty-stop")
        assertFalse(normalUninstall.isSuccess)
        assertEquals(KernelErrorCode.INVALID_STATE, normalUninstall.errors.single().code)
        assertEquals(ModuleState.FAILED, kernel.moduleState("dirty-stop"))

        assertTrue(kernel.forceUninstall("dirty-stop").isSuccess)
        assertNull(kernel.moduleState("dirty-stop"))
    }
}
