package io.toolbox.kernel

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KernelFourthAuditTest {
    @Test
    fun `module source metadata must be a defensive snapshot`() {
        val metadata = linkedMapOf("channel" to "stable")
        val source = ModuleSource(
            descriptor = ModuleDescriptor("engine.source-snapshot", "source snapshot", "1.0.0"),
            location = "memory://source-snapshot",
            metadata = metadata
        )

        metadata["channel"] = "mutated"
        metadata["unexpected"] = "value"

        assertEquals(mapOf("channel" to "stable"), source.metadata)
    }

    @Test
    fun `registry rollback must preserve concurrent replacement of the same command`() {
        val kernel = ToolBoxKernel()
        assertTrue(kernel.start().isEmpty())

        val startEntered = CountDownLatch(1)
        val releaseFailure = CountDownLatch(1)
        val failing = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("engine.same-key-rollback", "same key rollback", "1.0.0")

            override fun onLoad(context: KernelContext) {
                context.commands.register("audit.shared-command") {
                    CommandResult.success("module")
                }
            }

            override fun onStart() {
                startEntered.countDown()
                check(releaseFailure.await(2, TimeUnit.SECONDS)) { "rollback release timed out" }
                error("activation failed")
            }
        }

        val installer = thread(start = true, name = "kernel-same-key-rollback") {
            kernel.install(failing)
        }
        assertTrue(startEntered.await(2, TimeUnit.SECONDS))
        kernel.commands.register("audit.shared-command", replace = true) {
            CommandResult.success("external")
        }
        releaseFailure.countDown()
        installer.join(2_000)
        assertTrue(!installer.isAlive)

        val result = kernel.commands.execute(object : KernelCommand {
            override val name = "audit.shared-command"
        })
        assertTrue(result.success)
        assertEquals("external", result.value)
    }

    @Test
    fun `failed activation journal must remove side effects across all registries`() {
        val kernel = ToolBoxKernel()
        assertTrue(kernel.start().isEmpty())
        val deliveries = AtomicInteger(0)
        val failing = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("engine.cross-registry", "cross registry", "1.0.0")

            override fun onLoad(context: KernelContext) {
                context.services.register(String::class.java, "module-service")
                context.capabilities.register(object : Capability {
                    override val id = "audit.cross-capability"
                    override val version = 1
                    override val providerModuleId = descriptor.id
                })
                context.events.subscribe("audit.cross-event") {
                    deliveries.incrementAndGet()
                }
                context.commands.register("audit.cross-command") {
                    CommandResult.success("module")
                }
            }

            override fun onStart() {
                error("activation failed")
            }
        }

        assertTrue(kernel.install(failing).isNotEmpty())

        assertNull(kernel.services.get(String::class.java))
        assertNull(kernel.capabilities.get("audit.cross-capability"))
        assertTrue(
            !kernel.commands.execute(object : KernelCommand {
                override val name = "audit.cross-command"
            }).success
        )
        kernel.events.publish(KernelEvent("audit.cross-event", "audit"))
        assertEquals(0, deliveries.get())
    }

    @Test
    fun `committed activation context must remain usable after install`() {
        val kernel = ToolBoxKernel()
        assertTrue(kernel.start().isEmpty())
        var captured: KernelContext? = null
        val module = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("engine.committed-context", "committed context", "1.0.0")

            override fun onLoad(context: KernelContext) {
                captured = context
            }
        }

        assertTrue(kernel.install(module).isEmpty())
        captured!!.commands.register("audit.after-commit") {
            CommandResult.success("after-commit")
        }

        val result = kernel.commands.execute(object : KernelCommand {
            override val name = "audit.after-commit"
        })
        assertTrue(result.success)
        assertEquals("after-commit", result.value)
    }

    @Test
    fun `rolled back activation context must reject stale mutations`() {
        val kernel = ToolBoxKernel()
        assertTrue(kernel.start().isEmpty())
        var captured: KernelContext? = null
        val failing = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("engine.stale-context", "stale context", "1.0.0")

            override fun onLoad(context: KernelContext) {
                captured = context
            }

            override fun onStart() {
                error("activation failed")
            }
        }

        assertTrue(kernel.install(failing).isNotEmpty())
        assertFailsWith<IllegalStateException> {
            captured!!.commands.register("audit.stale-command") {
                CommandResult.success()
            }
        }
        assertTrue(
            !kernel.commands.execute(object : KernelCommand {
                override val name = "audit.stale-command"
            }).success
        )
    }

    @Test
    fun `dynamic install must not report success if module removes itself during load`() {
        val kernel = ToolBoxKernel()
        assertTrue(kernel.start().isEmpty())
        val module = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("engine.self-remove", "self remove", "1.0.0")

            override fun onLoad(context: KernelContext) {
                check(kernel.modules.uninstall(descriptor.id)) { "self removal failed" }
            }
        }

        val failures = kernel.install(module)

        assertTrue(failures.isNotEmpty(), "install reported success although the module record disappeared during activation")
        assertNull(kernel.modules.stateOf("engine.self-remove"))
    }
}
