package io.toolbox.kernel

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StateStoreSafetyTest {
    @Test
    fun `hanging state store read cannot block kernel construction`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val store = object : KernelStateStore {
            override fun put(key: String, value: String): Unit = Unit

            override fun get(key: String): String? {
                if (key.endsWith("record")) {
                    entered.countDown()
                    blockUntil(release)
                }
                return null
            }

            override fun remove(key: String): Unit = Unit
            override fun keys(prefix: String): Set<String> = emptySet()
        }
        val constructed = AtomicReference<ToolBoxKernel?>()
        val caller = Thread {
            constructed.set(
                ToolBoxKernel(
                    KernelConfig(lifecycleTimeoutMillis = 50),
                    KernelPorts(stateStore = store)
                )
            )
        }
        caller.start()
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        caller.join(1_000)
        val completedBeforeRelease = !caller.isAlive
        if (!completedBeforeRelease) {
            release.countDown()
            caller.join(1_000)
        }
        try {
            assertTrue(completedBeforeRelease, "Kernel construction waited for a non-cooperative state-store read")
            val kernel = requireNotNull(constructed.get())
            assertNull(kernel.previousPersistedState)
            assertEquals(KernelState.NEW, kernel.state)
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `late timed out state write reconciles canonical record to latest kernel state`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val firstPut = AtomicBoolean(true)
        val data = ConcurrentHashMap<String, String>()
        val store = object : KernelStateStore {
            override fun put(key: String, value: String): Unit {
                if (firstPut.compareAndSet(true, false)) {
                    entered.countDown()
                    blockUntil(release)
                }
                data[key] = value
            }

            override fun get(key: String): String? = data[key]

            override fun remove(key: String): Unit {
                data.remove(key)
            }

            override fun keys(prefix: String): Set<String> = data.keys.filterTo(linkedSetOf()) {
                it.startsWith(prefix)
            }
        }
        val constructed = AtomicReference<ToolBoxKernel?>()
        val caller = Thread {
            constructed.set(
                ToolBoxKernel(
                    KernelConfig(lifecycleTimeoutMillis = 50),
                    KernelPorts(stateStore = store)
                )
            )
        }
        caller.start()
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        caller.join(1_000)
        val completedBeforeRelease = !caller.isAlive
        if (!completedBeforeRelease) {
            release.countDown()
            caller.join(1_000)
        }
        assertTrue(completedBeforeRelease, "Kernel construction waited for a non-cooperative state-store write")

        val kernel = requireNotNull(constructed.get())
        assertTrue(kernel.install(module("worker")).isSuccess)
        assertTrue(kernel.start().isSuccess)
        assertEquals(KernelState.RUNNING, kernel.state)

        release.countDown()
        val canonicalKey = "kernel.${kernel.config.kernelId}.record"
        assertTrue(
            waitUntil(1_000) {
                PersistedKernelStateCodec.decode(data[canonicalKey].orEmpty())?.let { record ->
                    record.state == KernelState.RUNNING && record.operation == null && record.sessionId == kernel.sessionId
                } == true
            },
            "Late state-store completion did not reconcile the canonical record"
        )
    }

    private fun waitUntil(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }

    private fun blockUntil(release: CountDownLatch): Unit {
        while (release.count > 0) {
            try {
                release.await(1, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                // Simulate a non-cooperative host persistence implementation.
            }
        }
    }
}
