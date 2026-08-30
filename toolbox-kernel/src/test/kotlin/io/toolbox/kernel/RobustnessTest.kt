package io.toolbox.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RobustnessTest {
    @Test
    fun `throwing logger cannot corrupt kernel transaction`() {
        val logger = object : KernelLogger {
            override fun debug(message: String): Unit = error("logger")
            override fun info(message: String): Unit = error("logger")
            override fun warn(message: String, error: Throwable?): Unit = kotlin.error("logger")
            override fun error(message: String, error: Throwable?): Unit = kotlin.error("logger")
        }
        val kernel = ToolBoxKernel(ports = KernelPorts(logger = logger))
        assertTrue(kernel.install(module("safe")).isSuccess)
        assertTrue(kernel.start().isSuccess)
        assertEquals(KernelState.RUNNING, kernel.state)
    }

    @Test
    fun `throwing clock falls back without stranding lifecycle`() {
        val kernel = ToolBoxKernel(ports = KernelPorts(clock = KernelClock { error("clock") }))
        assertTrue(kernel.install(module("safe")).isSuccess)
        assertTrue(kernel.start().isSuccess)
        assertEquals(KernelState.RUNNING, kernel.state)
    }

    @Test
    fun `unknown health after explicit probe degrades kernel`() {
        val kernel = ToolBoxKernel()
        kernel.install(module("unknown", healthBlock = { HealthStatus.unknown("cannot determine") }))
        assertTrue(kernel.start().isSuccess)
        assertEquals(KernelState.DEGRADED, kernel.state)
        val health = kernel.snapshot().modules.single().health
        assertEquals(HealthState.UNKNOWN, health.state)
        assertNotNull(health.checkedAtMillis)
    }

    @Test
    fun `health exception retains cause for diagnostics`() {
        val kernel = ToolBoxKernel()
        kernel.install(module("health", healthBlock = { error("probe failed") }))
        assertTrue(kernel.start().isSuccess)
        val status = kernel.snapshot().modules.single().health
        assertEquals(HealthState.UNHEALTHY, status.state)
        assertEquals("probe failed", status.cause?.message)
        assertEquals(LifecyclePhase.HEALTH, kernel.snapshot().modules.single().lastFailure?.phase)
    }

    @Test
    fun `kernel state store is namespaced by kernel id`() {
        val store = InMemoryKernelStateStore()
        val first = ToolBoxKernel(KernelConfig(kernelId = "first"), KernelPorts(stateStore = store))
        val second = ToolBoxKernel(KernelConfig(kernelId = "second"), KernelPorts(stateStore = store))
        assertTrue(first.start().isSuccess)
        assertTrue(second.start().isSuccess)
        assertEquals(KernelState.RUNNING.name, store.get("kernel.first.state"))
        assertEquals(KernelState.RUNNING.name, store.get("kernel.second.state"))
        assertTrue(store.get("kernel.first.session") != store.get("kernel.second.session"))
    }

    @Test
    fun `lifecycle timeout quarantines module and force uninstall can remove it`() {
        val config = KernelConfig(lifecycleTimeoutMillis = 50)
        val kernel = ToolBoxKernel(config)
        kernel.install(
            module(
                "hung",
                onLoadBlock = {
                    while (true) {
                        try {
                            Thread.sleep(10_000)
                        } catch (_: InterruptedException) {
                            // Deliberately ignore interruption to simulate hostile/stuck module code.
                        }
                    }
                }
            )
        )
        val result = kernel.start()
        assertFalse(result.isSuccess)
        assertEquals(ModuleState.QUARANTINED, kernel.moduleState("hung"))
        assertEquals(LifecyclePhase.LOAD, result.failures.first().phase)
        assertTrue(kernel.forceUninstall("hung").isSuccess)
        assertEquals(null, kernel.moduleState("hung"))
    }

    @Test
    fun `executor returning without running task becomes lifecycle failure`() {
        val executor = KernelExecutor { _, _ -> Unit }
        val kernel = ToolBoxKernel(ports = KernelPorts(executor = executor))
        kernel.install(module("brokenexecutor"))
        val result = kernel.start()
        assertFalse(result.isSuccess)
        assertEquals(ModuleState.FAILED, kernel.moduleState("brokenexecutor"))
    }

    @Test
    fun `snapshot carries coherent revision when kernel is idle`() {
        val kernel = ToolBoxKernel()
        kernel.install(module("snapshot"))
        val snapshot = kernel.snapshot()
        assertTrue(snapshot.consistent)
        assertTrue(snapshot.revision > 0)
        assertTrue(snapshot.sessionId.isNotBlank())
    }

    @Test
    fun `identifier grammar rejects unstable identifiers`() {
        assertFailsWith<IllegalArgumentException> { ModuleDependency.required("Bad Id") }
        val kernel = ToolBoxKernel()
        val bad = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("Bad", "Bad", "1.0.0")
        }
        assertEquals(KernelErrorCode.INVALID_DESCRIPTOR, kernel.install(bad).errors.first().code)
    }
}
