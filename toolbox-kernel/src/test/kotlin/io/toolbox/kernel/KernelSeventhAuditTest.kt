package io.toolbox.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KernelSeventhAuditTest {
    @Test
    fun `kernel stop must fail closed when pending startup cleanup still cannot finish`() {
        var unloadCalls = 0
        val kernel = ToolBoxKernel()
        val failing = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor(
                "engine.startup-cleanup-hard-fail",
                "startup cleanup hard fail",
                "1.0.0"
            )

            override fun onLoad(context: KernelContext) {
                context.commands.register("audit.startup-cleanup-hard-fail") {
                    CommandResult.success("still-visible")
                }
            }

            override fun onStart() {
                error("start failed")
            }

            override fun onUnload() {
                unloadCalls += 1
                error("cleanup still unavailable")
            }
        }

        assertTrue(kernel.install(failing).isEmpty())
        val startFailures = kernel.start()

        assertTrue(startFailures.any { it.moduleId == failing.descriptor.id && it.phase == "start" })
        assertEquals(1, unloadCalls)

        val stopFailures = kernel.stop()

        assertTrue(stopFailures.any {
            it.moduleId == failing.descriptor.id && it.phase == "activation-cleanup"
        })
        assertEquals(2, unloadCalls)
        assertEquals(
            KernelState.FAILED,
            kernel.state,
            "kernel reported a clean STOPPED state while failed activation cleanup remained unresolved"
        )
        assertTrue(
            kernel.commands.execute(object : KernelCommand {
                override val name = "audit.startup-cleanup-hard-fail"
            }).success,
            "unresolved cleanup must remain visible as unresolved state rather than being silently discarded"
        )
    }
}
