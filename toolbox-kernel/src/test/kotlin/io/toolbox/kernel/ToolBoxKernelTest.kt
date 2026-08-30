package io.toolbox.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToolBoxKernelTest {
    @Test
    fun `kernel starts and stops installed module`() {
        val calls = mutableListOf<String>()
        val kernel = ToolBoxKernel()
        val module = recordingModule("engine.sample", calls)

        assertTrue(kernel.install(module).isEmpty())
        assertTrue(kernel.start().isEmpty())
        assertEquals(KernelState.RUNNING, kernel.state)
        assertEquals(ModuleState.STARTED, kernel.modules.stateOf("engine.sample"))
        assertEquals(listOf("load:engine.sample", "start:engine.sample"), calls)

        assertTrue(kernel.stop().isEmpty())
        assertEquals(KernelState.STOPPED, kernel.state)
        assertEquals(ModuleState.STOPPED, kernel.modules.stateOf("engine.sample"))
        assertEquals(
            listOf("load:engine.sample", "start:engine.sample", "stop:engine.sample"),
            calls
        )
    }

    @Test
    fun `dependencies load and start before dependent modules`() {
        val calls = mutableListOf<String>()
        val kernel = ToolBoxKernel()

        kernel.install(recordingModule("engine.child", calls, setOf("engine.base")))
        kernel.install(recordingModule("engine.base", calls))

        val failures = kernel.start()

        assertTrue(failures.isEmpty())
        assertEquals(
            listOf(
                "load:engine.base",
                "load:engine.child",
                "start:engine.base",
                "start:engine.child"
            ),
            calls
        )
    }

    @Test
    fun `one failing module degrades kernel without stopping healthy modules`() {
        val kernel = ToolBoxKernel()
        val healthy = recordingModule("engine.healthy", mutableListOf())
        val failing = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor(
                id = "engine.failing",
                name = "Failing engine",
                version = "1.0.0"
            )

            override fun onStart() {
                error("intentional failure")
            }
        }

        kernel.install(healthy)
        kernel.install(failing)
        val failures = kernel.start()

        assertEquals(1, failures.size)
        assertEquals(KernelState.DEGRADED, kernel.state)
        assertEquals(ModuleState.STARTED, kernel.modules.stateOf("engine.healthy"))
        assertEquals(ModuleState.FAILED, kernel.modules.stateOf("engine.failing"))
        assertTrue(kernel.isOperational())
    }

    private fun recordingModule(
        id: String,
        calls: MutableList<String>,
        dependencies: Set<String> = emptySet()
    ): ToolBoxModule = object : ToolBoxModule {
        override val descriptor = ModuleDescriptor(
            id = id,
            name = id,
            version = "1.0.0",
            dependencies = dependencies
        )

        override fun onLoad(context: KernelContext) {
            calls += "load:$id"
        }

        override fun onStart() {
            calls += "start:$id"
        }

        override fun onStop() {
            calls += "stop:$id"
        }
    }
}
