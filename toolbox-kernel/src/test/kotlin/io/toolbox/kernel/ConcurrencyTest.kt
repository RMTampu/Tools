package io.toolbox.kernel

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConcurrencyTest {
    @Test
    fun `callback worker calling lifecycle mutation is rejected without deadlock`() {
        val kernel = ToolBoxKernel()
        val nestedResult = AtomicReference<KernelResult<ModuleDescriptor>?>(null)
        var workerCompletedInsideCallback = false
        val parent = module(
            "parent",
            onStartBlock = {
                val worker = Thread { nestedResult.set(kernel.install(module("nested"))) }
                worker.start()
                worker.join(1_000)
                workerCompletedInsideCallback = !worker.isAlive
            }
        )
        assertTrue(kernel.install(parent).isSuccess)
        assertTrue(kernel.start().isSuccess)
        assertTrue(workerCompletedInsideCallback)
        val nested = assertNotNull(nestedResult.get())
        assertFalse(nested.isSuccess)
        assertEquals(KernelErrorCode.OPERATION_IN_PROGRESS, nested.errors.first().code)
    }

    @Test
    fun `timed out command keeps invocation lease until actual handler exits`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val config = KernelConfig(commandTimeoutMillis = 50, invocationDrainTimeoutMillis = 80)
        val kernel = ToolBoxKernel(config)
        kernel.install(
            module(
                "worker",
                onLoadBlock = { context ->
                    context.commands.register("worker.hang") {
                        entered.countDown()
                        while (release.count > 0) {
                            try {
                                release.await(1, TimeUnit.SECONDS)
                            } catch (_: InterruptedException) {
                                // Keep the handler alive beyond command timeout.
                            }
                        }
                        CommandResult.success()
                    }
                }
            )
        )
        assertTrue(kernel.start().isSuccess)
        val result = AtomicReference<CommandResult>()
        val caller = Thread { result.set(kernel.execute(command("worker.hang"))) }
        caller.start()
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        caller.join(1_000)
        assertFalse(result.get().success)

        val stop = kernel.stopModule("worker")
        assertFalse(stop.isSuccess)
        assertEquals(ModuleState.QUARANTINED, kernel.moduleState("worker"))
        assertEquals(LifecyclePhase.QUIESCE, stop.failures.first().phase)
        release.countDown()
    }

    @Test
    fun `executor remains synchronous at SPI boundary`() {
        val order = mutableListOf<String>()
        val executor = KernelExecutor { _, task ->
            order += "before"
            task()
            order += "after"
        }
        val kernel = ToolBoxKernel(ports = KernelPorts(executor = executor))
        kernel.install(
            module(
                "sync",
                onLoadBlock = { order += "load" },
                onStartBlock = { order += "start" },
                healthBlock = { order += "health"; HealthStatus.ok() }
            )
        )
        assertTrue(kernel.start().isSuccess)
        assertEquals(
            listOf("before", "load", "after", "before", "start", "after", "before", "health", "after"),
            order
        )
    }
}
