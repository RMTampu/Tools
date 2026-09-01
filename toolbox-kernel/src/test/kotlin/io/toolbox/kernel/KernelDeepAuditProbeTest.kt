package io.toolbox.kernel

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KernelDeepAuditProbeTest {
    @Test
    fun `state store read failure must fail closed instead of pretending fresh state`() {
        val store = object : KernelStateStore {
            override fun put(key: String, value: String) = Unit
            override fun get(key: String): String? = error("state read unavailable")
            override fun remove(key: String) = Unit
            override fun keys(prefix: String): Set<String> = emptySet()
        }

        val kernel = ToolBoxKernel(ports = KernelPorts(stateStore = store))

        assertEquals(KernelState.FAILED, kernel.state)
    }

    @Test
    fun `logger failure must not abort kernel start transition`() {
        val logger = object : KernelLogger {
            override fun info(message: String) {
                error("logger unavailable")
            }
        }
        val kernel = ToolBoxKernel(ports = KernelPorts(logger = logger))

        val result = runCatching { kernel.start() }

        assertTrue(result.isSuccess, "optional logger failure escaped kernel boundary: ${result.exceptionOrNull()}")
        assertEquals(KernelState.RUNNING, kernel.state)
    }

    @Test
    fun `dependency identifiers must obey the same identifier grammar as module ids`() {
        assertFailsWith<IllegalArgumentException> {
            ModuleDescriptor(
                id = "engine.child",
                name = "child",
                version = "1.0.0",
                dependencies = setOf("invalid dependency")
            )
        }
    }

    @Test
    fun `capability provider identifier must obey module id grammar`() {
        val registry = CapabilityRegistry()
        val invalid = object : Capability {
            override val id = "cap.sample"
            override val version = 1
            override val providerModuleId = "invalid provider"
        }

        assertFailsWith<IllegalArgumentException> { registry.register(invalid) }
    }

    @Test
    fun `module descriptor must not change identity or compatibility after preflight`() {
        val accepted = ModuleDescriptor(
            id = "engine.mutable",
            name = "mutable",
            version = "1.0.0",
            minAndroidApi = 30
        )
        val rejected = accepted.copy(minAndroidApi = 31)
        var reads = 0
        val module = object : ToolBoxModule {
            override val descriptor: ModuleDescriptor
                get() {
                    reads += 1
                    return if (reads == 1) accepted else rejected
                }
        }
        val kernel = ToolBoxKernel()

        val failures = kernel.install(module)

        assertTrue(failures.isNotEmpty(), "descriptor changed after preflight but install still succeeded")
        assertEquals("compatibility", failures.first().phase)
        assertNull(kernel.modules.stateOf("engine.mutable"))
    }

    @Test
    fun `module lifecycle callback must not execute while registry monitor blocks peer inspection`() {
        val kernel = ToolBoxKernel()
        val peerInspectionCompleted = AtomicBoolean(false)
        val module = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor(
                id = "engine.lock-probe",
                name = "lock probe",
                version = "1.0.0"
            )

            override fun onStart() {
                val worker = thread(start = true, name = "kernel-registry-probe") {
                    kernel.modules.stateOf(descriptor.id)
                    peerInspectionCompleted.set(true)
                }
                worker.join(500)
                check(peerInspectionCompleted.get()) {
                    "module callback is executing while ModuleRegistry monitor blocks peer access"
                }
            }
        }

        assertTrue(kernel.install(module).isEmpty())
        val failures = kernel.start()

        assertTrue(failures.isEmpty(), failures.joinToString { "${it.phase}: ${it.cause.message}" })
        assertTrue(peerInspectionCompleted.get())
    }

    @Test
    fun `event subscribe racing last unsubscribe must not lose the new listener`() {
        val bus = EventBus()
        val isEmptyEntered = CountDownLatch(1)
        val allowIsEmptyReturn = CountDownLatch(1)
        val bucket = PausingIsEmptyList<(KernelEvent) -> Unit>(isEmptyEntered, allowIsEmptyReturn)

        val field = EventBus::class.java.getDeclaredField("listeners")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val listenerMap = field.get(bus) as ConcurrentHashMap<String, CopyOnWriteArrayList<(KernelEvent) -> Unit>>
        listenerMap["race"] = bucket

        val first = bus.subscribe("race") { }
        val closeThread = thread(start = true, name = "event-close-probe") {
            first.close()
        }

        assertTrue(isEmptyEntered.await(2, TimeUnit.SECONDS), "unsubscribe did not reach empty-bucket check")

        val deliveries = AtomicInteger(0)
        bus.subscribe("race") { deliveries.incrementAndGet() }
        allowIsEmptyReturn.countDown()
        closeThread.join(2_000)
        assertTrue(!closeThread.isAlive, "unsubscribe probe did not finish")

        bus.publish(KernelEvent("race", "audit"))

        assertEquals(1, deliveries.get(), "listener registered during unsubscribe was detached from EventBus")
    }

    private class PausingIsEmptyList<E>(
        private val entered: CountDownLatch,
        private val release: CountDownLatch
    ) : CopyOnWriteArrayList<E>() {
        override fun isEmpty(): Boolean {
            val observedEmpty = super.isEmpty()
            entered.countDown()
            check(release.await(2, TimeUnit.SECONDS)) { "audit latch timed out" }
            return observedEmpty
        }
    }
}
