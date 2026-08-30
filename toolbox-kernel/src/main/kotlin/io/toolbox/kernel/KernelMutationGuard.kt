package io.toolbox.kernel

import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Provides one kernel-wide structural mutation boundary for registries that otherwise use
 * concurrent collections. Snapshot readers hold the read side so they cannot observe a mutation
 * after the structure changed but before its revision was published.
 */
internal class KernelMutationGuard {
    private val lock = ReentrantReadWriteLock(true)

    internal fun <T> mutate(block: () -> T): T {
        val write = lock.writeLock()
        write.lock()
        return try {
            block()
        } finally {
            write.unlock()
        }
    }

    internal fun <T> snapshot(block: () -> T): T {
        val read = lock.readLock()
        read.lock()
        return try {
            block()
        } finally {
            read.unlock()
        }
    }
}
