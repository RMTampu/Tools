package io.toolbox.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegistryTest {
    @Test
    fun `wildcard topic is delivered once`() {
        var count = 0
        val kernel = ToolBoxKernel(ports = KernelPorts(clock = KernelClock { 42L }))
        val module = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("publisher", "publisher", "1.0")
            override fun onLoad(context: KernelContext) { context.events.publish("*") }
        }
        kernel.install(module)
        kernel.subscribe("*") { count++ }
        kernel.start()
        assertEquals(3, count)
    }

    @Test
    fun `event listener failure is isolated and logged`() {
        val logger = RecordingLogger()
        val kernel = ToolBoxKernel(ports = KernelPorts(logger = logger))
        kernel.subscribe("boom") { error("listener failure") }
        val module = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("publisher", "publisher", "1.0")
            override fun onLoad(context: KernelContext) { context.events.publish("boom") }
        }
        assertTrue(kernel.install(module).isSuccess)
        assertTrue(kernel.start().isSuccess)
        assertTrue(logger.warnings.any { it.contains("Event listener") })
    }

    @Test
    fun `missing command returns failure`() {
        val result = ToolBoxKernel().execute(object : KernelCommand { override val name = "missing" })
        assertFalse(result.success)
    }

    @Test
    fun `command handler exception is isolated`() {
        val kernel = ToolBoxKernel()
        val module = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("commands", "commands", "1.0")
            override fun onLoad(context: KernelContext) {
                context.commands.register("explode") { error("handler") }
            }
        }
        kernel.install(module)
        kernel.start()
        val result = kernel.execute(object : KernelCommand { override val name = "explode" })
        assertFalse(result.success)
    }

    private class RecordingLogger : KernelLogger {
        val warnings = mutableListOf<String>()
        override fun warn(message: String, error: Throwable?) { warnings += message }
    }
}
