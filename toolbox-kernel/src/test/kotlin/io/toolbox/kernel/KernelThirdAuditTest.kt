package io.toolbox.kernel

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KernelThirdAuditTest {
    @Test
    fun `concurrent loadAll must not invoke onLoad twice for one module`() {
        val kernel = ToolBoxKernel()
        val loadCalls = AtomicInteger(0)
        val firstLoadEntered = CountDownLatch(1)
        val releaseLoad = CountDownLatch(1)
        val module = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("engine.concurrent-load", "concurrent load", "1.0.0")

            override fun onLoad(context: KernelContext) {
                loadCalls.incrementAndGet()
                firstLoadEntered.countDown()
                check(releaseLoad.await(2, TimeUnit.SECONDS)) { "load release timed out" }
            }
        }
        assertTrue(kernel.install(module).isEmpty())
        val context = KernelContext(
            config = kernel.config,
            services = kernel.services,
            capabilities = kernel.capabilities,
            events = kernel.events,
            commands = kernel.commands
        )

        val first = thread(start = true, name = "kernel-load-first") { kernel.modules.loadAll(context) }
        assertTrue(firstLoadEntered.await(2, TimeUnit.SECONDS))
        val secondCallStarted = CountDownLatch(1)
        val second = thread(start = true, name = "kernel-load-second") {
            secondCallStarted.countDown()
            kernel.modules.loadAll(context)
        }
        assertTrue(secondCallStarted.await(1, TimeUnit.SECONDS))
        second.join(400)
        val secondBlockedInDuplicateLoad = second.isAlive

        releaseLoad.countDown()
        first.join(2_000)
        second.join(2_000)

        assertFalse(secondBlockedInDuplicateLoad, "second loadAll entered the same module lifecycle concurrently")
        assertEquals(1, loadCalls.get())
        assertEquals(ModuleState.LOADED, kernel.modules.stateOf("engine.concurrent-load"))
    }

    @Test
    fun `stopAll must not invoke onStop while onStart is still executing`() {
        val kernel = ToolBoxKernel()
        val startEntered = CountDownLatch(1)
        val releaseStart = CountDownLatch(1)
        val stopEntered = CountDownLatch(1)
        val startCompleted = AtomicBoolean(false)
        val overlapObserved = AtomicBoolean(false)
        val module = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("engine.start-stop", "start stop", "1.0.0")

            override fun onStart() {
                startEntered.countDown()
                check(releaseStart.await(2, TimeUnit.SECONDS)) { "start release timed out" }
                startCompleted.set(true)
            }

            override fun onStop() {
                if (!startCompleted.get()) overlapObserved.set(true)
                stopEntered.countDown()
            }
        }
        assertTrue(kernel.install(module).isEmpty())
        val context = KernelContext(
            config = kernel.config,
            services = kernel.services,
            capabilities = kernel.capabilities,
            events = kernel.events,
            commands = kernel.commands
        )
        assertTrue(kernel.modules.loadAll(context).isEmpty())

        val starter = thread(start = true, name = "kernel-start-overlap") { kernel.modules.startAll() }
        assertTrue(startEntered.await(2, TimeUnit.SECONDS))
        val stopper = thread(start = true, name = "kernel-stop-overlap") { kernel.modules.stopAll() }

        val stopRanBeforeStartFinished = stopEntered.await(400, TimeUnit.MILLISECONDS)
        releaseStart.countDown()
        starter.join(2_000)
        stopper.join(2_000)

        assertFalse(stopRanBeforeStartFinished, "onStop ran before onStart completed")
        assertFalse(overlapObserved.get())
        assertEquals(ModuleState.STOPPED, kernel.modules.stateOf("engine.start-stop"))
    }

    @Test
    fun `failed activation rollback must preserve unrelated concurrent registry mutation`() {
        val kernel = ToolBoxKernel()
        assertTrue(kernel.start().isEmpty())
        val startEntered = CountDownLatch(1)
        val releaseFailure = CountDownLatch(1)
        val failing = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("engine.concurrent-rollback", "concurrent rollback", "1.0.0")

            override fun onLoad(context: KernelContext) {
                context.commands.register("audit.module-temporary") { CommandResult.success("module") }
            }

            override fun onStart() {
                startEntered.countDown()
                check(releaseFailure.await(2, TimeUnit.SECONDS)) { "rollback release timed out" }
                throw IllegalStateException("activation failed")
            }
        }

        val installer = thread(start = true, name = "kernel-install-rollback") {
            kernel.install(failing)
        }
        assertTrue(startEntered.await(2, TimeUnit.SECONDS))

        kernel.commands.register("audit.concurrent-external") { CommandResult.success("external") }
        releaseFailure.countDown()
        installer.join(2_000)
        assertFalse(installer.isAlive)

        assertNull(kernel.modules.stateOf("engine.concurrent-rollback"))
        assertFalse(kernel.commands.execute(object : KernelCommand {
            override val name = "audit.module-temporary"
        }).success)
        val external = kernel.commands.execute(object : KernelCommand {
            override val name = "audit.concurrent-external"
        })
        assertTrue(external.success, "rollback erased a registry mutation that did not belong to the failed module")
        assertEquals("external", external.value)
    }
}
