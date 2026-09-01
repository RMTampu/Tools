package io.toolbox.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    fun `stopped kernel restarts loaded module without loading it twice`() {
        val calls = mutableListOf<String>()
        val kernel = ToolBoxKernel()
        kernel.install(recordingModule("engine.restart", calls))

        kernel.start()
        kernel.stop()
        kernel.start()

        assertEquals(
            listOf(
                "load:engine.restart",
                "start:engine.restart",
                "stop:engine.restart",
                "start:engine.restart"
            ),
            calls
        )
        assertEquals(KernelState.RUNNING, kernel.state)
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
    fun `dependency cycle fails closed before module load`() {
        val calls = mutableListOf<String>()
        val kernel = ToolBoxKernel()
        kernel.install(recordingModule("engine.a", calls, setOf("engine.b")))
        kernel.install(recordingModule("engine.b", calls, setOf("engine.a")))

        val failures = kernel.start()

        assertTrue(failures.any { it.phase == "dependency-resolution" })
        assertTrue(calls.isEmpty())
        assertEquals(KernelState.DEGRADED, kernel.state)
    }

    @Test
    fun `missing dependency fails closed before module load`() {
        val calls = mutableListOf<String>()
        val kernel = ToolBoxKernel()
        kernel.install(recordingModule("engine.child", calls, setOf("engine.missing")))

        val failures = kernel.start()

        assertTrue(failures.any { it.phase == "dependency-resolution" })
        assertTrue(calls.isEmpty())
        assertEquals(KernelState.DEGRADED, kernel.state)
    }

    @Test
    fun `one failing module degrades kernel without stopping healthy modules`() {
        val kernel = ToolBoxKernel()
        val healthy = recordingModule("engine.healthy", mutableListOf())
        val failing = object : ToolBoxModule {
            override val descriptor = descriptor("engine.failing")

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

    @Test
    fun `unhealthy started module degrades kernel even when lifecycle succeeds`() {
        val kernel = ToolBoxKernel()
        val module = object : ToolBoxModule {
            override val descriptor = descriptor("engine.unhealthy")
            override fun healthCheck() = HealthStatus.failed("unhealthy")
        }

        kernel.install(module)
        val failures = kernel.start()

        assertTrue(failures.isEmpty())
        assertEquals(KernelState.DEGRADED, kernel.state)
    }

    @Test
    fun `source admission happens before loader execution`() {
        var loadCount = 0
        val policy = ModuleAdmissionPolicy { _, _ -> AdmissionDecision(false, "blocked") }
        val kernel = ToolBoxKernel(ports = KernelPorts(admissionPolicy = policy))
        val source = ModuleSource(descriptor("engine.blocked"), "compiled://engine.blocked")
        val loader = ModuleLoader {
            loadCount += 1
            recordingModule("engine.blocked", mutableListOf())
        }

        val failures = kernel.install(source, loader)

        assertEquals(1, failures.size)
        assertEquals("admission", failures.single().phase)
        assertEquals(0, loadCount)
        assertNull(kernel.modules.stateOf("engine.blocked"))
    }

    @Test
    fun `loaded source descriptor must equal admitted descriptor`() {
        val kernel = ToolBoxKernel()
        val source = ModuleSource(descriptor("engine.expected"), "compiled://engine.expected")
        val loader = ModuleLoader { recordingModule("engine.other", mutableListOf()) }

        val failures = kernel.install(source, loader)

        assertEquals("source-descriptor-mismatch", failures.single().phase)
        assertNull(kernel.modules.stateOf("engine.expected"))
        assertNull(kernel.modules.stateOf("engine.other"))
    }

    @Test
    fun `incompatible module is rejected without entering module registry`() {
        val kernel = ToolBoxKernel()
        val incompatible = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor(
                id = "engine.future",
                name = "engine.future",
                version = "1.0.0",
                minAndroidApi = 31
            )
        }

        val failures = kernel.install(incompatible)

        assertEquals("compatibility", failures.single().phase)
        assertNull(kernel.modules.stateOf("engine.future"))
    }

    @Test
    fun `failed dynamic install rolls back registry and loaded resources`() {
        var stopCount = 0
        var unloadCount = 0
        val kernel = ToolBoxKernel()
        kernel.start()
        val failing = object : ToolBoxModule {
            override val descriptor = descriptor("engine.dynamic-fail")

            override fun onStart() {
                error("start failed")
            }

            override fun onStop() {
                stopCount += 1
            }

            override fun onUnload() {
                unloadCount += 1
            }
        }

        val failures = kernel.install(failing)

        assertTrue(failures.any { it.phase == "start" })
        assertNull(kernel.modules.stateOf("engine.dynamic-fail"))
        assertEquals(1, stopCount)
        assertEquals(1, unloadCount)
        assertEquals(KernelState.RUNNING, kernel.state)
    }

    @Test
    fun `failed load cleanup remains retryable until rollback succeeds`() {
        var unloadCount = 0
        val kernel = ToolBoxKernel()
        kernel.start()
        val failing = object : ToolBoxModule {
            override val descriptor = descriptor("engine.load-cleanup")

            override fun onLoad(context: KernelContext) {
                error("load failed")
            }

            override fun onUnload() {
                unloadCount += 1
                if (unloadCount == 1) error("first cleanup failed")
            }
        }

        val failures = kernel.install(failing)

        assertTrue(failures.any { it.phase == "load" })
        assertEquals(2, unloadCount)
        assertNull(kernel.modules.stateOf("engine.load-cleanup"))
        assertEquals(KernelState.RUNNING, kernel.state)
    }

    @Test
    fun `failed uninstall keeps module registered and marks kernel degraded`() {
        val kernel = ToolBoxKernel()
        val module = object : ToolBoxModule {
            override val descriptor = descriptor("engine.stop-fail")

            override fun onStop() {
                error("stop failed")
            }
        }

        kernel.install(module)
        kernel.start()

        assertFalse(kernel.uninstall("engine.stop-fail"))
        assertEquals(ModuleState.FAILED, kernel.modules.stateOf("engine.stop-fail"))
        assertEquals(KernelState.DEGRADED, kernel.state)
    }

    @Test
    fun `kernel stop retries unresolved module stop and never reports false clean stop`() {
        var stopCalls = 0
        val kernel = ToolBoxKernel()
        val module = object : ToolBoxModule {
            override val descriptor = descriptor("engine.stop-retry")

            override fun onStop() {
                stopCalls += 1
                error("still cannot stop")
            }
        }

        kernel.install(module)
        kernel.start()
        assertFalse(kernel.uninstall("engine.stop-retry"))

        val failures = kernel.stop()

        assertTrue(failures.any { it.phase == "stop" })
        assertEquals(2, stopCalls)
        assertEquals(KernelState.FAILED, kernel.state)
        assertEquals(ModuleState.FAILED, kernel.modules.stateOf("engine.stop-retry"))
    }

    @Test
    fun `successful uninstall stops unloads then removes module`() {
        val calls = mutableListOf<String>()
        val kernel = ToolBoxKernel()
        val module = recordingModule("engine.remove", calls)
        kernel.install(module)
        kernel.start()

        assertTrue(kernel.uninstall("engine.remove"))

        assertNull(kernel.modules.stateOf("engine.remove"))
        assertEquals(
            listOf(
                "load:engine.remove",
                "start:engine.remove",
                "stop:engine.remove",
                "unload:engine.remove"
            ),
            calls
        )
    }

    @Test
    fun `unclean persisted state enters recoverable failed state instead of pretending to run`() {
        val store = InMemoryKernelStateStore()
        store.put("kernel.state", KernelState.RUNNING.name)
        val kernel = ToolBoxKernel(ports = KernelPorts(stateStore = store))

        assertEquals(KernelState.FAILED, kernel.state)
        assertEquals(KernelState.FAILED.name, store.get("kernel.state"))

        assertTrue(kernel.install(recordingModule("engine.recovered", mutableListOf())).isEmpty())
        assertTrue(kernel.start().isEmpty())
        assertEquals(KernelState.RUNNING, kernel.state)
    }

    @Test
    fun `clean stopped persisted state is restored safely`() {
        val store = InMemoryKernelStateStore()
        store.put("kernel.state", KernelState.STOPPED.name)
        val kernel = ToolBoxKernel(ports = KernelPorts(stateStore = store))

        assertEquals(KernelState.STOPPED, kernel.state)
        assertTrue(kernel.install(recordingModule("engine.clean-restart", mutableListOf())).isEmpty())
        assertTrue(kernel.start().isEmpty())
        assertEquals(KernelState.RUNNING, kernel.state)
    }

    @Test
    fun `wildcard event is delivered once when event topic itself is wildcard`() {
        val kernel = ToolBoxKernel()
        var deliveries = 0
        kernel.events.subscribe(EventBus.WILDCARD) { deliveries += 1 }

        kernel.events.publish(KernelEvent(EventBus.WILDCARD, "test"))

        assertEquals(1, deliveries)
    }

    @Test
    fun `event listener failure is logged and does not block remaining listeners`() {
        val logger = RecordingLogger()
        val kernel = ToolBoxKernel(ports = KernelPorts(logger = logger))
        var healthyListenerCalls = 0
        kernel.events.subscribe("probe") { error("listener failed") }
        kernel.events.subscribe("probe") { healthyListenerCalls += 1 }

        kernel.events.publish(KernelEvent("probe", "test"))

        assertEquals(1, healthyListenerCalls)
        assertTrue(logger.warnings.any { it.contains("Event listener failed") })
    }

    @Test
    fun `event listener isolation survives logger failure`() {
        val throwingLogger = object : KernelLogger {
            override fun warn(message: String, error: Throwable?) {
                error("logger failed")
            }
        }
        val kernel = ToolBoxKernel(ports = KernelPorts(logger = throwingLogger))
        var healthyListenerCalls = 0
        kernel.events.subscribe("probe") { error("listener failed") }
        kernel.events.subscribe("probe") { healthyListenerCalls += 1 }

        kernel.events.publish(KernelEvent("probe", "test"))

        assertEquals(1, healthyListenerCalls)
    }

    @Test
    fun `core model rejects invalid configuration and descriptors`() {
        assertFailsWith<IllegalArgumentException> { KernelConfig(name = "") }
        assertFailsWith<IllegalArgumentException> { KernelConfig(moduleApiVersion = 0) }
        assertFailsWith<IllegalArgumentException> {
            ModuleDescriptor(id = "bad id", name = "bad", version = "1")
        }
        assertFailsWith<IllegalArgumentException> {
            ModuleDescriptor(
                id = "engine.self",
                name = "engine.self",
                version = "1",
                dependencies = setOf("engine.self")
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ModuleSource(descriptor("engine.source"), "")
        }
    }

    @Test
    fun `capability registry rejects invalid capability contracts`() {
        val registry = CapabilityRegistry()
        val invalid = object : Capability {
            override val id = "cap.invalid"
            override val version = 0
            override val providerModuleId = "engine.owner"
        }

        assertFailsWith<IllegalArgumentException> { registry.register(invalid) }
        assertEquals(0, registry.size)
    }

    @Test
    fun `blank command names fail closed`() {
        val kernel = ToolBoxKernel()
        val result = kernel.commands.execute(object : KernelCommand {
            override val name = " "
        })

        assertFalse(result.success)
        assertTrue(result.error is IllegalArgumentException)
    }

    private fun descriptor(
        id: String,
        dependencies: Set<String> = emptySet()
    ) = ModuleDescriptor(
        id = id,
        name = id,
        version = "1.0.0",
        dependencies = dependencies
    )

    private fun recordingModule(
        id: String,
        calls: MutableList<String>,
        dependencies: Set<String> = emptySet()
    ): ToolBoxModule = object : ToolBoxModule {
        override val descriptor = descriptor(id, dependencies)

        override fun onLoad(context: KernelContext) {
            calls += "load:$id"
        }

        override fun onStart() {
            calls += "start:$id"
        }

        override fun onStop() {
            calls += "stop:$id"
        }

        override fun onUnload() {
            calls += "unload:$id"
        }
    }

    private class RecordingLogger : KernelLogger {
        val warnings = mutableListOf<String>()

        override fun warn(message: String, error: Throwable?) {
            warnings += message
        }
    }
}
