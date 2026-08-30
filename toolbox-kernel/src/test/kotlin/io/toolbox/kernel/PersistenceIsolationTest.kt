package io.toolbox.kernel

import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistenceIsolationTest {
    @Test
    fun `unavailable diagnostic state store cannot corrupt kernel lifecycle`() {
        val brokenStore = object : KernelStateStore {
            override fun put(key: String, value: String): Unit = error("store unavailable")
            override fun get(key: String): String? = error("store unavailable")
            override fun remove(key: String): Unit = error("store unavailable")
            override fun keys(prefix: String): Set<String> = error("store unavailable")
        }

        val kernel = ToolBoxKernel(ports = KernelPorts(stateStore = brokenStore))
        assertNull(kernel.previousPersistedState)
        assertTrue(kernel.install(module("survives-store-failure")).isSuccess)
        assertTrue(kernel.start().isSuccess)
        assertEquals(KernelState.RUNNING, kernel.state)
    }

    @Test
    fun `canonical diagnostic record survives failure of legacy mirror writes`() {
        val data = ConcurrentHashMap<String, String>()
        val partialStore = object : KernelStateStore {
            override fun put(key: String, value: String): Unit {
                if (key.endsWith(".state")) error("legacy mirror unavailable")
                data[key] = value
            }

            override fun get(key: String): String? = data[key]
            override fun remove(key: String): Unit {
                data.remove(key)
            }

            override fun keys(prefix: String): Set<String> = data.keys.filterTo(linkedSetOf()) { it.startsWith(prefix) }
        }

        val first = ToolBoxKernel(KernelConfig(kernelId = "diagnostic"), KernelPorts(stateStore = partialStore))
        assertTrue(first.start().isSuccess)

        val recovered = ToolBoxKernel(KernelConfig(kernelId = "diagnostic"), KernelPorts(stateStore = partialStore))
        assertEquals(KernelState.RUNNING, recovered.previousPersistedState)
    }
}
