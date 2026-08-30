package io.toolbox.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolBoxKernelTest {
    @Test
    fun `kernel starts and stops installed module`() {
        val calls = mutableListOf<String>()
        val kernel = ToolBoxKernel()
        assertTrue(kernel.install(recordingModule("engine.sample", calls)).isSuccess)
        assertTrue(kernel.start().isSuccess)
        assertEquals(KernelState.RUNNING, kernel.state)
        assertEquals(ModuleState.STARTED, kernel.moduleState("engine.sample"))
        assertTrue(kernel.stop().isSuccess)
        assertEquals(ModuleState.STOPPED, kernel.moduleState("engine.sample"))
        assertEquals(listOf("load:engine.sample", "start:engine.sample", "health:engine.sample", "stop:engine.sample"), calls)
    }

    @Test
    fun `dependencies start before dependent and stop in reverse order`() {
        val calls = mutableListOf<String>()
        val kernel = ToolBoxKernel()
        kernel.install(recordingModule("child", calls, setOf(ModuleDependency.required("base"))))
        kernel.install(recordingModule("base", calls))
        assertTrue(kernel.start().isSuccess)
        assertTrue(kernel.stop().isSuccess)
        val lifecycle = calls.filterNot { it.startsWith("health:") }
        assertEquals(listOf("load:base", "load:child", "start:base", "start:child", "stop:child", "stop:base"), lifecycle)
    }

    @Test
    fun `missing required dependency fails before callbacks`() {
        val calls = mutableListOf<String>()
        val kernel = ToolBoxKernel()
        kernel.install(recordingModule("child", calls, setOf(ModuleDependency.required("missing"))))
        val result = kernel.start()
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.DEPENDENCY_RESOLUTION, result.errors.first().code)
        assertTrue(calls.isEmpty())
        assertEquals(KernelState.FAILED, kernel.state)
    }

    @Test
    fun `missing optional dependency does not block startup`() {
        val calls = mutableListOf<String>()
        val kernel = ToolBoxKernel()
        kernel.install(recordingModule("child", calls, setOf(ModuleDependency.optional("missing"))))
        assertTrue(kernel.start().isSuccess)
        assertEquals(ModuleState.STARTED, kernel.moduleState("child"))
    }

    @Test
    fun `dependency cycle is rejected before callbacks`() {
        val calls = mutableListOf<String>()
        val kernel = ToolBoxKernel()
        kernel.install(recordingModule("a", calls, setOf(ModuleDependency.required("b"))))
        kernel.install(recordingModule("b", calls, setOf(ModuleDependency.required("a"))))
        val result = kernel.start()
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.DEPENDENCY_RESOLUTION, result.errors.first().code)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `invalid descriptor and self dependency are rejected during install`() {
        val blank = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("", "x", "1.0")
        }
        val self = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("self", "self", "1.0", dependencies = setOf(ModuleDependency.required("self")))
        }
        val kernel = ToolBoxKernel()
        assertEquals(KernelErrorCode.INVALID_DESCRIPTOR, kernel.install(blank).errors.first().code)
        assertEquals(KernelErrorCode.INVALID_DESCRIPTOR, kernel.install(self).errors.first().code)
    }

    @Test
    fun `duplicate module id returns conflict instead of throwing`() {
        val kernel = ToolBoxKernel()
        assertTrue(kernel.install(recordingModule("same", mutableListOf())).isSuccess)
        val second = kernel.install(recordingModule("same", mutableListOf()))
        assertFalse(second.isSuccess)
        assertEquals(KernelErrorCode.CONFLICT, second.errors.first().code)
    }

    @Test
    fun `module descriptor is snapshotted at installation`() {
        var current = ModuleDescriptor("stable", "stable", "1.0")
        val module = object : ToolBoxModule { override val descriptor: ModuleDescriptor get() = current }
        val kernel = ToolBoxKernel()
        assertTrue(kernel.install(module).isSuccess)
        current = ModuleDescriptor("changed", "changed", "9.0")
        assertEquals("stable", kernel.moduleDescriptors().single().id)
        assertNull(kernel.moduleState("changed"))
    }

    @Test
    fun `future module api is rejected`() {
        val kernel = ToolBoxKernel(KernelConfig(moduleApiVersion = 1))
        val module = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("future", "future", "1.0", apiVersion = 2)
        }
        assertEquals(KernelErrorCode.INCOMPATIBLE_MODULE, kernel.install(module).errors.first().code)
    }

    @Test
    fun `runtime activation failure is atomic`() {
        val kernel = ToolBoxKernel()
        assertTrue(kernel.start().isSuccess)
        val failing = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("bad", "bad", "1.0")
            override fun onStart() = error("boom")
        }
        val result = kernel.install(failing)
        assertFalse(result.isSuccess)
        assertNull(kernel.moduleState("bad"))
        assertTrue(kernel.isOperational())
    }

    @Test
    fun `stop failure prevents uninstall`() {
        val kernel = ToolBoxKernel()
        val module = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("sticky", "sticky", "1.0")
            override fun onStop() = error("cannot stop")
        }
        kernel.install(module)
        kernel.start()
        val result = kernel.uninstall("sticky")
        assertFalse(result.isSuccess)
        assertEquals(ModuleState.FAILED, kernel.moduleState("sticky"))
        assertNotNull(kernel.moduleDescriptors().singleOrNull { it.id == "sticky" })
    }

    @Test
    fun `successful uninstall calls unload and cleans owned resources`() {
        val calls = mutableListOf<String>()
        val kernel = ToolBoxKernel()
        val module = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("owner", "owner", "1.0")
            override fun onLoad(context: KernelContext) {
                calls += "load"
                context.services.register(String::class.java, "owned")
                context.commands.register("owned.command") { CommandResult.success() }
                context.events.subscribe("owned.topic") { }
            }
            override fun onUnload() { calls += "unload" }
        }
        kernel.install(module)
        kernel.start()
        assertEquals("owned", kernel.service(String::class.java))
        assertEquals(1, kernel.snapshot().registeredCommands)
        assertEquals(1, kernel.snapshot().eventSubscriptions)
        assertTrue(kernel.uninstall("owner").isSuccess)
        assertNull(kernel.service(String::class.java))
        assertEquals(0, kernel.snapshot().registeredCommands)
        assertEquals(0, kernel.snapshot().eventSubscriptions)
        assertTrue("unload" in calls)
    }

    @Test
    fun `module cannot replace another module owned service`() {
        val kernel = ToolBoxKernel()
        val first = serviceModule("first", "one", replace = false)
        val second = serviceModule("second", "two", replace = true)
        kernel.install(first)
        kernel.install(second)
        val result = kernel.start()
        assertFalse(result.isSuccess)
        assertEquals("one", kernel.service(String::class.java))
        assertEquals(ModuleState.FAILED, kernel.moduleState("second"))
    }

    @Test
    fun `snapshot is passive and health probe can degrade kernel`() {
        var checks = 0
        val kernel = ToolBoxKernel()
        val module = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("health", "health", "1.0")
            override fun healthCheck(): HealthStatus {
                checks++
                return if (checks == 1) HealthStatus.ok() else HealthStatus.failed("down")
            }
        }
        kernel.install(module)
        kernel.start()
        assertEquals(1, checks)
        kernel.snapshot()
        assertEquals(1, checks)
        kernel.probeHealth()
        assertEquals(2, checks)
        assertEquals(KernelState.DEGRADED, kernel.state)
    }

    @Test
    fun `previous state is read before NEW is written`() {
        val store = InMemoryKernelStateStore()
        store.put("kernel.state", KernelState.RUNNING.name)
        val kernel = ToolBoxKernel(ports = KernelPorts(stateStore = store))
        assertEquals(KernelState.RUNNING, kernel.previousPersistedState)
        assertEquals(KernelState.NEW.name, store.get("kernel.state"))
    }

    @Test
    fun `failed module can be retried with a fresh managed scope`() {
        var fail = true
        val kernel = ToolBoxKernel()
        val module = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("retry", "retry", "1.0")
            override fun onStart() {
                if (fail) error("first start fails")
            }
        }
        kernel.install(module)
        assertFalse(kernel.start().isSuccess)
        assertEquals(ModuleState.FAILED, kernel.moduleState("retry"))
        fail = false
        val retry = kernel.retryModule("retry")
        assertTrue(retry.isSuccess)
        assertEquals(ModuleState.REGISTERED, kernel.moduleState("retry"))
        assertTrue(kernel.stop().isSuccess)
        assertTrue(kernel.start().isSuccess)
        assertEquals(ModuleState.STARTED, kernel.moduleState("retry"))
    }

    private fun recordingModule(
        id: String,
        calls: MutableList<String>,
        dependencies: Set<ModuleDependency> = emptySet()
    ): ToolBoxModule = object : ToolBoxModule {
        override val descriptor = ModuleDescriptor(id, id, "1.0.0", dependencies = dependencies)
        override fun onLoad(context: KernelContext) { calls += "load:$id" }
        override fun onStart() { calls += "start:$id" }
        override fun onStop() { calls += "stop:$id" }
        override fun onUnload() { calls += "unload:$id" }
        override fun healthCheck(): HealthStatus { calls += "health:$id"; return HealthStatus.ok() }
    }

    private fun serviceModule(id: String, value: String, replace: Boolean): ToolBoxModule = object : ToolBoxModule {
        override val descriptor = ModuleDescriptor(id, id, "1.0")
        override fun onLoad(context: KernelContext) {
            context.services.register(String::class.java, value, replace)
        }
    }
}
