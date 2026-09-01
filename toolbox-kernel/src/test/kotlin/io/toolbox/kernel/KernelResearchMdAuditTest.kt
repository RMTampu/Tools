package io.toolbox.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KernelResearchMdAuditTest {
    @Test
    fun `R2 closed subscriptions must not leave unbounded empty topic buckets`() {
        val bus = EventBus()
        repeat(2_000) { index ->
            bus.subscribe("audit.topic.$index") { }.close()
        }

        val field = EventBus::class.java.getDeclaredField("listeners")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val listeners = field.get(bus) as Map<String, *>

        assertTrue(
            listeners.isEmpty(),
            "closed subscriptions left ${listeners.size} empty topic buckets retained by EventBus"
        )
    }

    @Test
    fun `R2 R7 uninstall must release registry ownership even if module cleanup forgets it`() {
        val kernel = ToolBoxKernel()
        var eventDeliveries = 0
        val module = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor(
                id = "engine.ownership-audit",
                name = "ownership audit",
                version = "1.0.0"
            )

            override fun onLoad(context: KernelContext) {
                context.services.register(String::class.java, "owned-service")
                context.capabilities.register(object : Capability {
                    override val id = "audit.owned-capability"
                    override val version = 1
                    override val providerModuleId = descriptor.id
                })
                context.commands.register("audit.owned-command") {
                    CommandResult.success("owned")
                }
                context.events.subscribe("audit.owned-event") {
                    eventDeliveries += 1
                }
            }
        }

        assertTrue(kernel.install(module).isEmpty())
        assertTrue(kernel.start().isEmpty())
        assertTrue(kernel.uninstall(module.descriptor.id))

        assertNull(kernel.services.get(String::class.java), "uninstalled module service remained registered")
        assertNull(kernel.capabilities.get("audit.owned-capability"), "uninstalled module capability remained registered")
        assertFalse(
            kernel.commands.execute(object : KernelCommand {
                override val name = "audit.owned-command"
            }).success,
            "uninstalled module command remained executable"
        )
        kernel.events.publish(KernelEvent("audit.owned-event", "audit"))
        assertEquals(0, eventDeliveries, "uninstalled module event listener remained reachable")
    }

    @Test
    fun `R5 R7 default source boundary must reject before loader without explicit trust policy`() {
        var loadCount = 0
        val descriptor = ModuleDescriptor(
            id = "engine.untrusted-default",
            name = "untrusted default",
            version = "1.0.0"
        )
        val source = ModuleSource(
            descriptor = descriptor,
            location = "external://untrusted-default"
        )
        val kernel = ToolBoxKernel()

        val failures = kernel.install(source, ModuleLoader {
            loadCount += 1
            object : ToolBoxModule {
                override val descriptor = descriptor
            }
        })

        assertEquals(0, loadCount, "default kernel policy executed a source loader without explicit trust admission")
        assertTrue(failures.isNotEmpty(), "default kernel policy accepted a source without explicit trust admission")
        assertNull(kernel.modules.stateOf(descriptor.id))
    }
}
