package io.toolbox.kernel

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
}
