package io.toolbox.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolBoxKernelTest {
    @Test
    fun `kernel starts stops and reloads module with fresh activation`() {
        val calls = mutableListOf<String>()
        val kernel = ToolBoxKernel()
        kernel.install(
            module(
                "sample",
                onLoadBlock = { calls += "load" },
                onStartBlock = { calls += "start" },
                onStopBlock = { calls += "stop" },
                onUnloadBlock = { calls += "unload" },
                healthBlock = { calls += "health"; HealthStatus.ok() }
            )
        )
        assertTrue(kernel.start().isSuccess)
        assertTrue(kernel.stop().isSuccess)
        assertEquals(ModuleState.STOPPED, kernel.moduleState("sample"))
        assertTrue(kernel.start().isSuccess)
        assertEquals(
            listOf("load", "start", "health", "stop", "unload", "load", "start", "health"),
            calls
        )
    }

    @Test
    fun `required dependency is started before consumer load`() {
        val calls = mutableListOf<String>()
        val kernel = ToolBoxKernel()
        kernel.install(
            module(
                "consumer",
                dependencies = setOf(ModuleDependency.required("provider")),
                onLoadBlock = { calls += "consumer-load" },
                onStartBlock = { calls += "consumer-start" }
            )
        )
        kernel.install(
            module(
                "provider",
                onLoadBlock = { calls += "provider-load" },
                onStartBlock = { calls += "provider-start" }
            )
        )
        assertTrue(kernel.start().isSuccess)
        assertEquals(
            listOf("provider-load", "provider-start", "consumer-load", "consumer-start"),
            calls.filterNot { it.contains("health") }
        )
    }

    @Test
    fun `required dependency version range is enforced`() {
        val kernel = ToolBoxKernel()
        kernel.install(module("provider", version = "1.5.0"))
        kernel.install(
            module(
                "consumer",
                dependencies = setOf(
                    ModuleDependency.required(
                        "provider",
                        VersionRange.between(ModuleVersion.parse("2.0.0"), ModuleVersion.parse("3.0.0"))
                    )
                )
            )
        )
        val result = kernel.start()
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.DEPENDENCY_RESOLUTION, result.errors.first().code)
    }

    @Test
    fun `broken dependency closure does not block independent module startup`() {
        val kernel = ToolBoxKernel()
        kernel.install(module("broken", dependencies = setOf(ModuleDependency.required("missing"))))
        kernel.install(module("healthy"))

        val result = kernel.start()

        assertFalse(result.isSuccess)
        assertEquals(ModuleState.REGISTERED, kernel.moduleState("broken"))
        assertEquals(ModuleState.STARTED, kernel.moduleState("healthy"))
        assertEquals(KernelState.DEGRADED, kernel.state)
        assertEquals(LifecyclePhase.RESOLUTION, kernel.snapshot().modules.first { it.descriptor.id == "broken" }.lastFailure?.phase)
    }

    @Test
    fun `individual module restart ignores unrelated unresolved module`() {
        val kernel = ToolBoxKernel()
        kernel.install(module("broken", dependencies = setOf(ModuleDependency.required("missing"))))
        kernel.install(module("healthy"))
        assertFalse(kernel.start().isSuccess)
        assertEquals(KernelState.DEGRADED, kernel.state)

        assertTrue(kernel.stopModule("healthy").isSuccess)
        assertTrue(kernel.startModule("healthy").isSuccess)
        assertEquals(ModuleState.STARTED, kernel.moduleState("healthy"))
        assertEquals(ModuleState.REGISTERED, kernel.moduleState("broken"))
    }

    @Test
    fun `optional dependency failure does not fail consumer`() {
        val kernel = ToolBoxKernel()
        kernel.install(module("optional-provider", onStartBlock = { error("optional failed") }))
        kernel.install(
            module(
                "consumer",
                dependencies = setOf(ModuleDependency.optional("optional-provider"))
            )
        )
        val result = kernel.start()
        assertFalse(result.isSuccess)
        assertEquals(ModuleState.FAILED, kernel.moduleState("optional-provider"))
        assertEquals(ModuleState.STARTED, kernel.moduleState("consumer"))
        assertEquals(KernelState.DEGRADED, kernel.state)
    }

    @Test
    fun `required cycle fails before callbacks`() {
        var called = false
        val kernel = ToolBoxKernel()
        kernel.install(module("a", dependencies = setOf(ModuleDependency.required("b")), onLoadBlock = { called = true }))
        kernel.install(module("b", dependencies = setOf(ModuleDependency.required("a")), onLoadBlock = { called = true }))
        val result = kernel.start()
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.DEPENDENCY_RESOLUTION, result.errors.first().code)
        assertFalse(called)
    }

    @Test
    fun `runtime activation failure removes non quarantined registration`() {
        val kernel = ToolBoxKernel()
        assertTrue(kernel.start().isSuccess)
        val result = kernel.install(module("bad", onStartBlock = { error("boom") }))
        assertFalse(result.isSuccess)
        assertNull(kernel.moduleState("bad"))
        assertTrue(kernel.isOperational())
    }

    @Test
    fun `runtime load rollback cleanup failure remains failed and non retryable`() {
        val kernel = ToolBoxKernel()
        assertTrue(kernel.start().isSuccess)

        val result = kernel.install(
            module(
                "dirty-load",
                onLoadBlock = { error("load failed") },
                onUnloadBlock = { error("cleanup failed") }
            )
        )

        assertFalse(result.isSuccess)
        assertEquals(ModuleState.FAILED, kernel.moduleState("dirty-load"))
        assertEquals(KernelState.DEGRADED, kernel.state)
        assertEquals(
            LifecyclePhase.UNLOAD,
            kernel.snapshot().modules.single { it.descriptor.id == "dirty-load" }.lastFailure?.phase
        )
        assertFalse(kernel.retryModule("dirty-load").isSuccess)
        assertTrue(kernel.forceUninstall("dirty-load").isSuccess)
        assertNull(kernel.moduleState("dirty-load"))
    }

    @Test
    fun `runtime start rollback cleanup failure remains failed and non retryable`() {
        val kernel = ToolBoxKernel()
        assertTrue(kernel.start().isSuccess)

        val result = kernel.install(
            module(
                "dirty-start",
                onStartBlock = { error("start failed") },
                onUnloadBlock = { error("cleanup failed") }
            )
        )

        assertFalse(result.isSuccess)
        assertEquals(ModuleState.FAILED, kernel.moduleState("dirty-start"))
        assertEquals(KernelState.DEGRADED, kernel.state)
        assertEquals(
            LifecyclePhase.UNLOAD,
            kernel.snapshot().modules.single { it.descriptor.id == "dirty-start" }.lastFailure?.phase
        )
        assertFalse(kernel.retryModule("dirty-start").isSuccess)
        assertTrue(kernel.forceUninstall("dirty-start").isSuccess)
        assertNull(kernel.moduleState("dirty-start"))
    }

    @Test
    fun `individual stop refuses active required dependent`() {
        val kernel = ToolBoxKernel()
        kernel.install(module("base"))
        kernel.install(module("child", dependencies = setOf(ModuleDependency.required("base"))))
        assertTrue(kernel.start().isSuccess)
        val result = kernel.stopModule("base")
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.CONFLICT, result.errors.first().code)
        assertEquals(ModuleState.STARTED, kernel.moduleState("base"))
    }

    @Test
    fun `completed stop failure is failed and cannot masquerade as clean stop`() {
        val kernel = ToolBoxKernel()
        kernel.install(module("fragile", onStopBlock = { error("stop failed") }))
        assertTrue(kernel.start().isSuccess)
        val stop = kernel.stop()
        assertFalse(stop.isSuccess)
        assertEquals(KernelState.STOPPED_WITH_ERRORS, kernel.state)
        assertEquals(ModuleState.FAILED, kernel.moduleState("fragile"))
        assertEquals(LifecyclePhase.STOP, kernel.snapshot().modules.single().lastFailure?.phase)
        assertFalse(kernel.retryModule("fragile").isSuccess)
        assertTrue(kernel.uninstall("fragile").isSuccess)
    }

    @Test
    fun `duplicate id is structured conflict`() {
        val kernel = ToolBoxKernel()
        assertTrue(kernel.install(module("same")).isSuccess)
        val result = kernel.install(module("same"))
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.CONFLICT, result.errors.first().code)
    }

    @Test
    fun `descriptor identity is snapshotted`() {
        var descriptor = ModuleDescriptor("stable", "stable", "1.0.0")
        val dynamic = object : ToolBoxModule {
            override val descriptor: ModuleDescriptor get() = descriptor
        }
        val kernel = ToolBoxKernel()
        assertTrue(kernel.install(dynamic).isSuccess)
        descriptor = ModuleDescriptor("changed", "changed", "2.0.0")
        assertEquals("stable", kernel.moduleDescriptors().single().id)
        assertNull(kernel.moduleState("changed"))
    }
}
