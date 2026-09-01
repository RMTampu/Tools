package io.toolbox.enginehost

import io.toolbox.kernel.Capability
import io.toolbox.kernel.KernelEvent
import io.toolbox.kernel.KernelState
import io.toolbox.kernel.ToolBoxKernel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngineHostTest {
    @Test
    fun `registration is metadata only and acquisition is lazy`() {
        val kernel = ToolBoxKernel()
        val host = EngineHost()
        val provider = RecordingProvider(descriptor("engine.lazy")) { RecordingEngine() }

        assertTrue(kernel.install(host).isEmpty())
        assertTrue(host.register(provider).registered)
        assertEquals(0, provider.createCount)

        assertTrue(kernel.start().isEmpty())
        assertEquals(0, provider.createCount)

        val acquired = host.acquire("engine.lazy") as EngineAcquireResult.Acquired
        assertEquals(1, provider.createCount)
        assertEquals(EngineState.RUNNING, host.status("engine.lazy")?.state)

        acquired.lease.close()
        assertEquals(EngineState.REGISTERED, host.status("engine.lazy")?.state)
    }

    @Test
    fun `last lease releases engine while shared lease keeps it alive`() {
        val kernel = ToolBoxKernel()
        val host = EngineHost()
        val engine = RecordingEngine()
        val provider = RecordingProvider(descriptor("engine.shared")) { engine }

        kernel.install(host)
        host.register(provider)
        kernel.start()

        val first = host.acquire("engine.shared") as EngineAcquireResult.Acquired
        val second = host.acquire("engine.shared") as EngineAcquireResult.Acquired

        assertEquals(1, provider.createCount)
        assertEquals(2, host.status("engine.shared")?.activeLeases)

        first.lease.close()
        assertEquals(EngineState.RUNNING, host.status("engine.shared")?.state)
        assertEquals(1, host.status("engine.shared")?.activeLeases)
        assertEquals(0, engine.stopCount)

        second.lease.close()
        assertEquals(EngineState.REGISTERED, host.status("engine.shared")?.state)
        assertEquals(1, engine.stopCount)
        assertEquals(1, engine.unloadCount)
    }

    @Test
    fun `provided capability is owned by engine lifetime`() {
        val kernel = ToolBoxKernel()
        val host = EngineHost()
        val provider = RecordingProvider(
            descriptor(
                engineId = "engine.capability",
                providedCapabilityIds = setOf("cap.search")
            )
        ) {
            object : RecordingEngine() {
                override fun onLoad(scope: EngineRuntimeScope) {
                    super.onLoad(scope)
                    scope.registerCapability(TestCapability("cap.search", 2, "engine.capability"))
                }
            }
        }

        kernel.install(host)
        host.register(provider)
        kernel.start()

        val acquired = host.acquire("engine.capability") as EngineAcquireResult.Acquired
        assertEquals(2, kernel.capabilities.get("cap.search")?.version)

        acquired.lease.close()
        assertNull(kernel.capabilities.get("cap.search"))
    }

    @Test
    fun `missing required capability blocks materialization`() {
        val kernel = ToolBoxKernel()
        val host = EngineHost()
        val provider = RecordingProvider(
            descriptor(
                engineId = "engine.needs-data",
                requiredCapabilities = setOf(CapabilityRequirement("cap.data", minVersion = 2))
            )
        ) { RecordingEngine() }

        kernel.install(host)
        host.register(provider)
        kernel.start()

        val result = host.acquire("engine.needs-data") as EngineAcquireResult.Rejected

        assertEquals("MISSING_REQUIRED_CAPABILITY", result.code)
        assertEquals(0, provider.createCount)
        assertEquals(EngineState.REGISTERED, host.status("engine.needs-data")?.state)
    }

    @Test
    fun `incompatible engine remains discoverable but cannot execute`() {
        val kernel = ToolBoxKernel()
        val host = EngineHost(EngineEnvironment(androidApi = 30, abi = "arm64-v8a"))
        val provider = RecordingProvider(
            descriptor(
                engineId = "engine.x86-only",
                supportedAbi = setOf("x86_64")
            )
        ) { RecordingEngine() }

        kernel.install(host)
        val registration = host.register(provider)
        kernel.start()

        assertTrue(registration.registered)
        assertFalse(registration.compatible)
        assertEquals(EngineState.INCOMPATIBLE, host.status("engine.x86-only")?.state)

        val result = host.acquire("engine.x86-only") as EngineAcquireResult.Rejected
        assertEquals("ENGINE_INCOMPATIBLE", result.code)
        assertEquals(0, provider.createCount)
    }

    @Test
    fun `one engine activation failure stays isolated from healthy engine and kernel`() {
        val kernel = ToolBoxKernel()
        val host = EngineHost()
        val failingProvider = RecordingProvider(descriptor("engine.failing")) {
            object : RecordingEngine() {
                override fun onStart() {
                    super.onStart()
                    error("intentional engine failure")
                }
            }
        }
        val healthyProvider = RecordingProvider(descriptor("engine.healthy")) { RecordingEngine() }

        kernel.install(host)
        host.register(failingProvider)
        host.register(healthyProvider)
        kernel.start()

        val failed = host.acquire("engine.failing") as EngineAcquireResult.Rejected
        val healthy = host.acquire("engine.healthy") as EngineAcquireResult.Acquired

        assertEquals("ENGINE_ACTIVATION_FAILED", failed.code)
        assertEquals(EngineState.FAILED, host.status("engine.failing")?.state)
        assertEquals(EngineState.RUNNING, host.status("engine.healthy")?.state)
        assertEquals(KernelState.RUNNING, kernel.state)
        assertTrue(host.healthCheck().healthy)

        healthy.lease.close()
    }

    @Test
    fun `declared capability must actually be registered by engine`() {
        val kernel = ToolBoxKernel()
        val host = EngineHost()
        val provider = RecordingProvider(
            descriptor(
                engineId = "engine.bad-contract",
                providedCapabilityIds = setOf("cap.promised")
            )
        ) { RecordingEngine() }

        kernel.install(host)
        host.register(provider)
        kernel.start()

        val result = host.acquire("engine.bad-contract") as EngineAcquireResult.Rejected

        assertEquals("ENGINE_ACTIVATION_FAILED", result.code)
        assertEquals(EngineState.FAILED, host.status("engine.bad-contract")?.state)
        assertNull(kernel.capabilities.get("cap.promised"))
    }

    @Test
    fun `runtime scope removes owned service and subscription on release`() {
        val kernel = ToolBoxKernel()
        val host = EngineHost()
        val observed = mutableListOf<String>()
        val service = TestService("ready")
        val provider = RecordingProvider(descriptor("engine.scope")) {
            object : RecordingEngine() {
                override fun onLoad(scope: EngineRuntimeScope) {
                    super.onLoad(scope)
                    scope.registerService(TestService::class.java, service)
                    scope.subscribe("probe") { event -> observed += event.payload.toString() }
                }
            }
        }

        kernel.install(host)
        host.register(provider)
        kernel.start()

        val acquired = host.acquire("engine.scope") as EngineAcquireResult.Acquired
        assertNotNull(kernel.services.get(TestService::class.java))
        kernel.events.publish(KernelEvent("probe", "test", "before"))
        assertEquals(listOf("before"), observed)

        acquired.lease.close()
        assertNull(kernel.services.get(TestService::class.java))
        kernel.events.publish(KernelEvent("probe", "test", "after"))
        assertEquals(listOf("before"), observed)
    }

    private fun descriptor(
        engineId: String,
        supportedAbi: Set<String> = setOf("arm64-v8a"),
        requiredCapabilities: Set<CapabilityRequirement> = emptySet(),
        providedCapabilityIds: Set<String> = emptySet()
    ) = EngineDescriptor(
        engineId = engineId,
        name = engineId,
        engineVersion = "1.0.0",
        supportedAbi = supportedAbi,
        requiredCapabilities = requiredCapabilities,
        providedCapabilityIds = providedCapabilityIds,
        entryPoint = "$engineId.EntryPoint"
    )

    private class RecordingProvider(
        override val descriptor: EngineDescriptor,
        private val factory: () -> ToolBoxEngine
    ) : EngineProvider {
        var createCount: Int = 0
            private set

        override fun create(): ToolBoxEngine {
            createCount += 1
            return factory()
        }
    }

    private open class RecordingEngine : ToolBoxEngine {
        var loadCount = 0
        var startCount = 0
        var stopCount = 0
        var unloadCount = 0

        override fun onLoad(scope: EngineRuntimeScope) {
            loadCount += 1
        }

        override fun onStart() {
            startCount += 1
        }

        override fun onStop() {
            stopCount += 1
        }

        override fun onUnload() {
            unloadCount += 1
        }
    }

    private data class TestCapability(
        override val id: String,
        override val version: Int,
        override val providerModuleId: String
    ) : Capability

    private data class TestService(val state: String)
}
