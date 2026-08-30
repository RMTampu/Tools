package io.toolbox.kernel

import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConcurrencyTest {
    @Test
    fun `callback worker calling kernel is rejected without deadlock`() {
        val kernel = ToolBoxKernel()
        val nestedResult = AtomicReference<KernelResult<ModuleDescriptor>?>(null)
        var workerCompletedInsideCallback = false
        val module = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("parent", "parent", "1.0")
            override fun onStart() {
                val worker = Thread {
                    nestedResult.set(
                        kernel.install(object : ToolBoxModule {
                            override val descriptor = ModuleDescriptor("nested", "nested", "1.0")
                        })
                    )
                }
                worker.start()
                worker.join(1_000)
                workerCompletedInsideCallback = !worker.isAlive
            }
        }

        assertTrue(kernel.install(module).isSuccess)
        val start = kernel.start()
        assertTrue(start.isSuccess)
        assertTrue(workerCompletedInsideCallback)
        val nested = assertNotNull(nestedResult.get())
        assertFalse(nested.isSuccess)
        assertEquals(KernelErrorCode.OPERATION_IN_PROGRESS, nested.errors.first().code)
    }

    @Test
    fun `executor contract remains synchronous`() {
        val order = mutableListOf<String>()
        val executor = KernelExecutor { _, task ->
            order += "before"
            task()
            order += "after"
        }
        val kernel = ToolBoxKernel(ports = KernelPorts(executor = executor))
        kernel.install(object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("sync", "sync", "1.0")
            override fun onLoad(context: KernelContext) { order += "load" }
            override fun onStart() { order += "start" }
            override fun healthCheck(): HealthStatus { order += "health"; return HealthStatus.ok() }
        })
        assertTrue(kernel.start().isSuccess)
        assertEquals(
            listOf("before", "load", "after", "before", "start", "after", "before", "health", "after"),
            order
        )
    }
}
