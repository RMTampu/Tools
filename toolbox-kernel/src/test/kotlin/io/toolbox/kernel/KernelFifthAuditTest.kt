package io.toolbox.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KernelFifthAuditTest {
    @Test
    fun `initial load failure must rollback registry side effects`() {
        val kernel = ToolBoxKernel()
        val failing = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor(
                "engine.startup-load-fail",
                "startup load fail",
                "1.0.0"
            )

            override fun onLoad(context: KernelContext) {
                context.commands.register("audit.startup-load-leak") {
                    CommandResult.success("leaked")
                }
                error("load failed")
            }
        }

        assertTrue(kernel.install(failing).isEmpty())
        val failures = kernel.start()

        assertTrue(failures.any { it.moduleId == failing.descriptor.id && it.phase == "load" })
        assertEquals(ModuleState.FAILED, kernel.modules.stateOf(failing.descriptor.id))
        assertTrue(
            !kernel.commands.execute(object : KernelCommand {
                override val name = "audit.startup-load-leak"
            }).success,
            "failed initial load left a command visible in the kernel registry"
        )
    }

    @Test
    fun `initial start failure must not expose loaded registry side effects`() {
        val kernel = ToolBoxKernel()
        val failing = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor(
                "engine.startup-start-fail",
                "startup start fail",
                "1.0.0"
            )

            override fun onLoad(context: KernelContext) {
                context.services.register(String::class.java, "failed-module-service")
                context.capabilities.register(object : Capability {
                    override val id = "audit.startup-start-capability"
                    override val version = 1
                    override val providerModuleId = descriptor.id
                })
            }

            override fun onStart() {
                error("start failed")
            }
        }

        assertTrue(kernel.install(failing).isEmpty())
        val failures = kernel.start()

        assertTrue(failures.any { it.moduleId == failing.descriptor.id && it.phase == "start" })
        assertEquals(ModuleState.FAILED, kernel.modules.stateOf(failing.descriptor.id))
        assertNull(kernel.services.get(String::class.java))
        assertNull(kernel.capabilities.get("audit.startup-start-capability"))
    }

    @Test
    fun `startup rollback of failed module must preserve healthy module registrations`() {
        val kernel = ToolBoxKernel()
        val healthy = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor(
                "engine.startup-healthy",
                "startup healthy",
                "1.0.0"
            )

            override fun onLoad(context: KernelContext) {
                context.commands.register("audit.startup-healthy-command") {
                    CommandResult.success("healthy")
                }
            }
        }
        val failing = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor(
                "engine.startup-failed-peer",
                "startup failed peer",
                "1.0.0"
            )

            override fun onLoad(context: KernelContext) {
                context.commands.register("audit.startup-failed-command") {
                    CommandResult.success("failed")
                }
            }

            override fun onStart() {
                error("peer start failed")
            }
        }

        assertTrue(kernel.install(healthy).isEmpty())
        assertTrue(kernel.install(failing).isEmpty())
        assertTrue(kernel.start().isNotEmpty())

        val healthyResult = kernel.commands.execute(object : KernelCommand {
            override val name = "audit.startup-healthy-command"
        })
        val failedResult = kernel.commands.execute(object : KernelCommand {
            override val name = "audit.startup-failed-command"
        })

        assertTrue(healthyResult.success)
        assertEquals("healthy", healthyResult.value)
        assertTrue(!failedResult.success, "failed module registration remained visible after startup rollback")
    }
}
