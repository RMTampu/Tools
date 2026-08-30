package io.toolbox.kernel

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal data class OwnerToken(
    val id: String,
    val generation: Long
)

internal interface ResourceOwner {
    val token: OwnerToken
    fun assertContextOpen(): Unit
    fun isAcceptingInvocations(): Boolean
    fun tryAcquireInvocation(): InvocationPermit?
}

internal class InvocationPermit(private val release: () -> Unit) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close(): Unit {
        if (closed.compareAndSet(false, true)) release()
    }

    internal companion object {
        internal fun noOp(): InvocationPermit = InvocationPermit { }
    }
}

internal class ModuleLease(
    override val token: OwnerToken
) : ResourceOwner {
    private val lock = Object()
    private var contextOpen = true
    private var acceptingInvocations = false
    private var activeInvocations = 0

    override fun assertContextOpen(): Unit = synchronized(lock) {
        check(contextOpen) { "Module context ${token.id}#${token.generation} is no longer valid" }
    }

    override fun isAcceptingInvocations(): Boolean = synchronized(lock) {
        contextOpen && acceptingInvocations
    }

    internal fun activateInvocations(): Unit = synchronized(lock) {
        check(contextOpen) { "Cannot activate a closed module context" }
        acceptingInvocations = true
    }

    override fun tryAcquireInvocation(): InvocationPermit? = synchronized(lock) {
        if (!contextOpen || !acceptingInvocations) return@synchronized null
        activeInvocations++
        InvocationPermit {
            synchronized(lock) {
                activeInvocations--
                lock.notifyAll()
            }
        }
    }

    internal fun quiesce(timeoutMillis: Long): Boolean {
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        synchronized(lock) {
            acceptingInvocations = false
            while (activeInvocations > 0) {
                val remainingNanos = deadlineNanos - System.nanoTime()
                if (remainingNanos <= 0) return false
                val millis = TimeUnit.NANOSECONDS.toMillis(remainingNanos).coerceAtLeast(1)
                try {
                    lock.wait(millis)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
            return true
        }
    }

    internal fun closeContext(): Unit = synchronized(lock) {
        contextOpen = false
        acceptingInvocations = false
        lock.notifyAll()
    }
}

internal object KernelResourceOwner : ResourceOwner {
    override val token: OwnerToken = OwnerToken("kernel", 0)
    override fun assertContextOpen(): Unit = Unit
    override fun isAcceptingInvocations(): Boolean = true
    override fun tryAcquireInvocation(): InvocationPermit = InvocationPermit.noOp()
}

internal sealed class CallbackOutcome<out T> {
    data class Success<T>(val value: T) : CallbackOutcome<T>()
    data class Failure(val error: Throwable) : CallbackOutcome<Nothing>()
    data class TimedOut(val error: TimeoutException) : CallbackOutcome<Nothing>()
}

internal class CallbackSupervisor(
    private val executor: KernelExecutor,
    private val logger: KernelLogger
) {
    internal fun <T> execute(taskName: String, timeoutMillis: Long, task: () -> T): CallbackOutcome<T> {
        val done = CountDownLatch(1)
        val outcome = AtomicReference<CallbackOutcome<T>?>(null)
        val worker = Thread(
            {
                try {
                    var called = false
                    executor.execute(taskName) {
                        called = true
                        try {
                            outcome.compareAndSet(null, CallbackOutcome.Success(task()))
                        } catch (error: Throwable) {
                            outcome.compareAndSet(null, CallbackOutcome.Failure(error))
                        }
                    }
                    if (!called) {
                        outcome.compareAndSet(
                            null,
                            CallbackOutcome.Failure(IllegalStateException("KernelExecutor returned without executing task $taskName"))
                        )
                    }
                } catch (error: Throwable) {
                    outcome.compareAndSet(null, CallbackOutcome.Failure(error))
                } finally {
                    done.countDown()
                }
            },
            "toolbox-$taskName"
        ).apply { isDaemon = true }

        try {
            worker.start()
        } catch (error: Throwable) {
            return CallbackOutcome.Failure(error)
        }

        val completed = try {
            done.await(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            return CallbackOutcome.Failure(interrupted)
        }
        if (!completed) {
            worker.interrupt()
            val timeout = TimeoutException("Kernel callback timed out after ${timeoutMillis}ms: $taskName")
            logger.error(timeout.message ?: "Kernel callback timeout", timeout)
            return CallbackOutcome.TimedOut(timeout)
        }
        return outcome.get() ?: CallbackOutcome.Failure(IllegalStateException("Kernel callback produced no outcome: $taskName"))
    }
}
