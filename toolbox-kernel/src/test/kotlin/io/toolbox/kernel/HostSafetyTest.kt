package io.toolbox.kernel

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HostSafetyTest {
    @Test
    fun `timed out host call retains capacity until it actually exits`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val exited = CountDownLatch(1)
        val supervisor = HostCallSupervisor(maxConcurrentCalls = 1)

        val first = supervisor.execute("host-hang", 50) {
            entered.countDown()
            try {
                while (release.count > 0) {
                    try {
                        release.await(1, TimeUnit.SECONDS)
                    } catch (_: InterruptedException) {
                        // Deliberately ignore interruption to prove actual completion is tracked.
                    }
                }
            } finally {
                exited.countDown()
            }
            "done"
        }

        assertTrue(entered.await(1, TimeUnit.SECONDS))
        assertIs<CallbackOutcome.TimedOut>(first)
        val saturated = supervisor.execute("second", 50) { "should-not-run" }
        assertIs<CallbackOutcome.Failure>(saturated)
        assertIs<CallbackCapacityException>(saturated.error)

        release.countDown()
        assertTrue(exited.await(1, TimeUnit.SECONDS))
        val recovered = supervisor.execute("third", 200) { "ok" }
        assertIs<CallbackOutcome.Success<String>>(recovered)
        assertTrue(recovered.value == "ok")
    }

    @Test
    fun `hanging logger cannot block kernel transaction`() {
        val release = CountDownLatch(1)
        val logger = object : KernelLogger {
            override fun info(message: String): Unit = blockUntil(release)
        }
        val kernel = ToolBoxKernel(ports = KernelPorts(logger = logger))

        val result = kernel.install(module("safe"))

        assertTrue(result.isSuccess)
        assertTrue(kernel.moduleState("safe") == ModuleState.REGISTERED)
        release.countDown()
    }

    @Test
    fun `hanging clock falls back without blocking kernel`() {
        val release = CountDownLatch(1)
        val clock = KernelClock {
            blockUntil(release)
            7L
        }
        val kernel = ToolBoxKernel(ports = KernelPorts(clock = clock))

        val installed = kernel.install(module("safe"))
        val started = kernel.start()

        assertTrue(installed.isSuccess)
        assertTrue(started.isSuccess)
        assertTrue(kernel.state == KernelState.RUNNING)
        release.countDown()
    }

    private fun blockUntil(release: CountDownLatch): Unit {
        while (release.count > 0) {
            try {
                release.await(1, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                // Simulate a non-cooperative host implementation.
            }
        }
    }
}
