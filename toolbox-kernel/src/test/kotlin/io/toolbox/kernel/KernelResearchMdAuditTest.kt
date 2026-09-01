package io.toolbox.kernel

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun `R2 R7 late registry mutations remain module owned until unload`() {
        lateinit var retainedContext: KernelContext
        val kernel = ToolBoxKernel()
        val module = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor(
                id = "engine.late-ownership",
                name = "late ownership",
                version = "1.0.0"
            )

            override fun onLoad(context: KernelContext) {
                retainedContext = context
            }
        }

        assertTrue(kernel.install(module).isEmpty())
        assertTrue(kernel.start().isEmpty())
        retainedContext.commands.register("audit.late-owned-command") {
            CommandResult.success("late")
        }
        assertTrue(kernel.uninstall(module.descriptor.id))

        assertFalse(
            kernel.commands.execute(object : KernelCommand {
                override val name = "audit.late-owned-command"
            }).success,
            "registry mutation made after activation escaped module ownership cleanup"
        )
    }

    @Test
    fun `R2 ownership tracking stays bounded for repeated same-key mutations`() {
        lateinit var retainedContext: KernelContext
        val kernel = ToolBoxKernel()
        val module = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor(
                id = "engine.bounded-ownership",
                name = "bounded ownership",
                version = "1.0.0"
            )

            override fun onLoad(context: KernelContext) {
                retainedContext = context
            }
        }
        assertTrue(kernel.install(module).isEmpty())
        assertTrue(kernel.start().isEmpty())

        repeat(2_000) {
            retainedContext.commands.register("audit.repeated-owned-command") {
                CommandResult.success("owned")
            }
            retainedContext.commands.unregister("audit.repeated-owned-command")
        }

        val recordsField = ModuleRegistry::class.java.getDeclaredField("records")
        recordsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val records = recordsField.get(kernel.modules) as Map<String, *>
        val record = checkNotNull(records[module.descriptor.id])
        val journalField = record.javaClass.getDeclaredField("registryJournal")
        journalField.isAccessible = true
        val journal = checkNotNull(journalField.get(record))
        val undoField = KernelRegistryMutationJournal::class.java.getDeclaredField("undoActions")
        undoField.isAccessible = true
        val retainedUndoActions = (undoField.get(journal) as Collection<*>).size

        assertTrue(
            retainedUndoActions <= 4,
            "same-key churn retained $retainedUndoActions ownership undo actions instead of bounded ownership state"
        )
    }

    @Test
    fun `R5 R7 module scoped command registry cannot replace foreign command`() {
        val kernel = ToolBoxKernel()
        kernel.commands.register("audit.foreign-command") { CommandResult.success("host") }
        lateinit var retainedContext: KernelContext
        val module = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("engine.scope-replace", "scope replace", "1.0.0")
            override fun onLoad(context: KernelContext) {
                retainedContext = context
            }
        }
        assertTrue(kernel.install(module).isEmpty())
        assertTrue(kernel.start().isEmpty())

        assertFailsWith<IllegalStateException> {
            retainedContext.commands.register("audit.foreign-command", replace = true) {
                CommandResult.success("module")
            }
        }
        assertEquals(
            "host",
            kernel.commands.execute(object : KernelCommand {
                override val name = "audit.foreign-command"
            }).value
        )
    }

    @Test
    fun `R5 R7 module scoped command registry cannot unregister foreign command`() {
        val kernel = ToolBoxKernel()
        kernel.commands.register("audit.foreign-remove") { CommandResult.success("host") }
        lateinit var retainedContext: KernelContext
        val module = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("engine.scope-remove", "scope remove", "1.0.0")
            override fun onLoad(context: KernelContext) {
                retainedContext = context
            }
        }
        assertTrue(kernel.install(module).isEmpty())
        assertTrue(kernel.start().isEmpty())

        assertFailsWith<IllegalStateException> {
            retainedContext.commands.unregister("audit.foreign-remove")
        }
        assertEquals(
            "host",
            kernel.commands.execute(object : KernelCommand {
                override val name = "audit.foreign-remove"
            }).value
        )
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

    @Test
    fun `R4 R5 R7 module context must not expose authoritative kernel ports`() {
        val exposed = KernelContext::class.java.declaredFields.any { field ->
            field.type == KernelPorts::class.java
        }

        assertFalse(
            exposed,
            "module context exposes KernelPorts including the authoritative KernelStateStore and admission policies"
        )
    }

    @Test
    fun `R2 lifecycle callback must not run while kernel global monitor blocks peer operation`() {
        lateinit var kernel: ToolBoxKernel
        val peerFinished = CountDownLatch(1)
        var finishedInsideCallback = false
        val module = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor(
                id = "engine.callback-lock-audit",
                name = "callback lock audit",
                version = "1.0.0"
            )

            override fun onStart() {
                val peer = Thread {
                    runCatching { kernel.uninstall("engine.not-installed") }
                    peerFinished.countDown()
                }
                peer.start()
                finishedInsideCallback = peerFinished.await(750, TimeUnit.MILLISECONDS)
            }
        }

        kernel = ToolBoxKernel()
        assertTrue(kernel.install(module).isEmpty())
        kernel.start()

        assertTrue(
            finishedInsideCallback,
            "kernel lifecycle callback executed while a global kernel monitor blocked peer mutation progress"
        )
    }

    @Test
    fun `R5 R7 module cannot spoof capability provider ownership`() {
        val kernel = ToolBoxKernel()
        val module = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor(
                id = "engine.capability-owner",
                name = "capability owner",
                version = "1.0.0"
            )

            override fun onLoad(context: KernelContext) {
                context.capabilities.register(object : Capability {
                    override val id = "audit.spoofed-provider"
                    override val version = 1
                    override val providerModuleId = "engine.someone-else"
                })
            }
        }

        assertTrue(kernel.install(module).isEmpty())
        val failures = kernel.start()

        assertTrue(failures.any { it.moduleId == module.descriptor.id })
        assertNull(kernel.capabilities.get("audit.spoofed-provider"))
        assertEquals(ModuleState.FAILED, kernel.modules.stateOf(module.descriptor.id))
    }
}
