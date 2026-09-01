package io.toolbox.kernel

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KernelSecondAuditTest {
    @Test
    fun `failed activation rollback must preserve existing event subscription handle`() {
        val kernel = ToolBoxKernel()
        assertTrue(kernel.start().isEmpty())
        var deliveries = 0
        val subscription = kernel.events.subscribe("stable") { deliveries += 1 }
        val failing = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("engine.rollback-event", "rollback event", "1.0.0")

            override fun onLoad(context: KernelContext) {
                context.commands.register("audit.rollback-event") { CommandResult.success() }
            }

            override fun onStart() {
                throw IllegalStateException("activation failed")
            }
        }

        val failures = kernel.install(failing)
        assertTrue(failures.isNotEmpty())
        assertNull(kernel.modules.stateOf("engine.rollback-event"))

        subscription.close()
        kernel.events.publish(KernelEvent("stable", "audit"))

        assertEquals(0, deliveries, "rollback replaced the event bucket and invalidated the original subscription handle")
    }

    @Test
    fun `concurrent startAll must not invoke onStart twice for one module`() {
        val kernel = ToolBoxKernel()
        val startCalls = AtomicInteger(0)
        val firstStartEntered = CountDownLatch(1)
        val releaseFirstStart = CountDownLatch(1)
        val module = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("engine.concurrent-start", "concurrent start", "1.0.0")

            override fun onStart() {
                startCalls.incrementAndGet()
                firstStartEntered.countDown()
                check(releaseFirstStart.await(2, TimeUnit.SECONDS)) { "start release timed out" }
            }
        }
        assertTrue(kernel.install(module).isEmpty())
        val context = KernelContext(
            config = kernel.config,
            services = kernel.services,
            capabilities = kernel.capabilities,
            events = kernel.events,
            commands = kernel.commands,
            ports = kernel.ports
        )
        assertTrue(kernel.modules.loadAll(context).isEmpty())

        val first = thread(start = true, name = "kernel-start-first") { kernel.modules.startAll() }
        assertTrue(firstStartEntered.await(2, TimeUnit.SECONDS))
        val second = thread(start = true, name = "kernel-start-second") { kernel.modules.startAll() }
        second.join(300)

        val secondBlockedInDuplicateStart = second.isAlive
        releaseFirstStart.countDown()
        first.join(2_000)
        second.join(2_000)

        assertFalse(secondBlockedInDuplicateStart, "second startAll entered the same module lifecycle concurrently")
        assertEquals(1, startCalls.get())
    }

    @Test
    fun `compatibility policy exception must be isolated as install failure`() {
        val kernel = ToolBoxKernel(
            ports = KernelPorts(
                compatibilityPolicy = CompatibilityPolicy { _, _ ->
                    throw IllegalStateException("compatibility policy unavailable")
                }
            )
        )
        val module = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("engine.policy-compat", "policy compat", "1.0.0")
        }

        val result = runCatching { kernel.install(module) }

        assertTrue(result.isSuccess, "compatibility policy exception escaped kernel install boundary")
        assertEquals("compatibility-policy", result.getOrThrow().single().phase)
        assertNull(kernel.modules.stateOf("engine.policy-compat"))
    }

    @Test
    fun `admission policy exception must be isolated as install failure`() {
        val kernel = ToolBoxKernel(
            ports = KernelPorts(
                admissionPolicy = ModuleAdmissionPolicy { _, _ ->
                    throw IllegalStateException("admission policy unavailable")
                }
            )
        )
        val module = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("engine.policy-admission", "policy admission", "1.0.0")
        }

        val result = runCatching { kernel.install(module) }

        assertTrue(result.isSuccess, "admission policy exception escaped kernel install boundary")
        assertEquals("admission-policy", result.getOrThrow().single().phase)
        assertNull(kernel.modules.stateOf("engine.policy-admission"))
    }

    @Test
    fun `registered capability contract must be stable after validation`() {
        var version = 1
        var provider = "engine.owner"
        val capability = object : Capability {
            override val id = "cap.stable"
            override val version: Int get() = version
            override val providerModuleId: String get() = provider
        }
        val registry = CapabilityRegistry()

        registry.register(capability)
        version = 2
        provider = "engine.changed"

        val registered = registry.get("cap.stable")
        assertEquals(1, registered?.version)
        assertEquals("engine.owner", registered?.providerModuleId)
    }
}
