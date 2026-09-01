package io.toolbox.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KernelSixthAuditTest {
    @Test
    fun `kernel stop must finish startup rollback after first stop cleanup failure`() {
        var stopCalls = 0
        var unloadCalls = 0
        val kernel = ToolBoxKernel()
        val failing = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor(
                "engine.startup-stop-retry",
                "startup stop retry",
                "1.0.0"
            )

            override fun onLoad(context: KernelContext) {
                context.services.register(String::class.java, "pending-startup-service")
            }

            override fun onStart() {
                error("start failed")
            }

            override fun onStop() {
                stopCalls += 1
                if (stopCalls == 1) error("first stop cleanup failed")
            }

            override fun onUnload() {
                unloadCalls += 1
            }
        }

        assertTrue(kernel.install(failing).isEmpty())
        val startFailures = kernel.start()

        assertTrue(startFailures.any { it.moduleId == failing.descriptor.id && it.phase == "start" })
        assertEquals(1, stopCalls)
        assertEquals(0, unloadCalls)
        assertEquals("pending-startup-service", kernel.services.get(String::class.java))

        val stopFailures = kernel.stop()

        assertTrue(stopFailures.isEmpty(), stopFailures.joinToString { "${it.phase}: ${it.cause.message}" })
        assertEquals(2, stopCalls)
        assertEquals(1, unloadCalls)
        assertNull(kernel.services.get(String::class.java))
        assertEquals(KernelState.STOPPED, kernel.state)
    }

    @Test
    fun `kernel stop must retry failed startup unload before claiming clean stop`() {
        var unloadCalls = 0
        val kernel = ToolBoxKernel()
        val failing = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor(
                "engine.startup-unload-retry",
                "startup unload retry",
                "1.0.0"
            )

            override fun onLoad(context: KernelContext) {
                context.commands.register("audit.startup-unload-retry") {
                    CommandResult.success("leaked")
                }
            }

            override fun onStart() {
                error("start failed")
            }

            override fun onUnload() {
                unloadCalls += 1
                if (unloadCalls == 1) error("first unload cleanup failed")
            }
        }

        assertTrue(kernel.install(failing).isEmpty())
        val startFailures = kernel.start()

        assertTrue(startFailures.any { it.moduleId == failing.descriptor.id && it.phase == "start" })
        assertEquals(1, unloadCalls)
        assertTrue(
            kernel.commands.execute(object : KernelCommand {
                override val name = "audit.startup-unload-retry"
            }).success
        )

        val stopFailures = kernel.stop()

        assertTrue(stopFailures.isEmpty(), stopFailures.joinToString { "${it.phase}: ${it.cause.message}" })
        assertEquals(2, unloadCalls)
        assertTrue(
            !kernel.commands.execute(object : KernelCommand {
                override val name = "audit.startup-unload-retry"
            }).success,
            "kernel claimed clean stop while failed startup registration remained visible"
        )
        assertEquals(KernelState.STOPPED, kernel.state)
    }
}
