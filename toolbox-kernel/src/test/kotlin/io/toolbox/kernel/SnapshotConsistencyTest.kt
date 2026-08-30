package io.toolbox.kernel

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnapshotConsistencyTest {
    @Test
    fun `snapshot read boundary cannot enter before structural mutation publishes revision`() {
        val mutationGuard = KernelMutationGuard()
        val mutationReachedRevision = CountDownLatch(1)
        val releaseMutation = CountDownLatch(1)
        val registry = ServiceRegistry(mutationGuard) {
            mutationReachedRevision.countDown()
            releaseMutation.await(1, TimeUnit.SECONDS)
        }

        val writer = Thread {
            registry.register(KernelResourceOwner, ServiceKey(String::class.java), "value", replace = false)
        }
        writer.start()
        assertTrue(mutationReachedRevision.await(1, TimeUnit.SECONDS))

        val observedSize = AtomicInteger(-1)
        val readerDone = CountDownLatch(1)
        val reader = Thread {
            mutationGuard.snapshot {
                observedSize.set(registry.size)
            }
            readerDone.countDown()
        }
        reader.start()

        assertFalse(readerDone.await(50, TimeUnit.MILLISECONDS))
        releaseMutation.countDown()
        writer.join(1_000)
        assertTrue(readerDone.await(1, TimeUnit.SECONDS))
        assertEquals(1, observedSize.get())
    }

    @Test
    fun `snapshot taken while lifecycle transaction is active is not marked consistent`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val kernel = ToolBoxKernel()
        assertTrue(
            kernel.install(
                module(
                    "snapshot-worker",
                    onLoadBlock = {
                        entered.countDown()
                        release.await(1, TimeUnit.SECONDS)
                    }
                )
            ).isSuccess
        )

        val starter = Thread { kernel.start() }
        starter.start()
        assertTrue(entered.await(1, TimeUnit.SECONDS))

        assertFalse(kernel.snapshot().consistent)

        release.countDown()
        starter.join(2_000)
        assertTrue(kernel.snapshot().consistent)
    }
}
