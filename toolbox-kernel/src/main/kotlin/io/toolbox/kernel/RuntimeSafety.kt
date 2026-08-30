package io.toolbox.kernel

import java.util.LinkedHashSet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Semaphore
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
    fun trackTimedOut(completion: CallbackCompletion): Unit
    fun hasOutstandingCallbacks(): Boolean
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

/** Tracks actual callback termination, not merely timeout/cancellation request state. */
internal class CallbackCompletion internal constructor(
    internal val taskName: String
) {
    private val done = CountDownLatch(1)

    internal val isComplete: Boolean get() = done.count == 0L

    internal fun await(timeoutMillis: Long): Boolean = try {
        done.await(timeoutMillis.coerceAtLeast(0), TimeUnit.MILLISECONDS)
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }

    internal fun complete(): Unit = done.countDown()
}

internal class ModuleLease(
    override val token: OwnerToken
) : ResourceOwner {
    private val lock = Object()
    private var contextOpen = true
    private var acceptingInvocations = false
    private var activeInvocations = 0
    private val outstandingCallbacks = LinkedHashSet<CallbackCompletion>()

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

    override fun trackTimedOut(completion: CallbackCompletion): Unit = synchronized(lock) {
        purgeCompletedCallbacks()
        if (!completion.isComplete) outstandingCallbacks += completion
    }

    override fun hasOutstandingCallbacks(): Boolean = synchronized(lock) {
        purgeCompletedCallbacks()
        outstandingCallbacks.isNotEmpty()
    }

    internal fun hasActiveInvocations(): Boolean = synchronized(lock) { activeInvocations > 0 }

    internal fun quiesce(timeoutMillis: Long): Boolean {
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis.coerceAtLeast(0))
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

    private fun purgeCompletedCallbacks(): Unit {
        outstandingCallbacks.removeIf { it.isComplete }
    }
}

internal object KernelResourceOwner : ResourceOwner {
    override val token: OwnerToken = OwnerToken("kernel", 0)
    override fun assertContextOpen(): Unit = Unit
    override fun isAcceptingInvocations(): Boolean = true
    override fun tryAcquireInvocation(): InvocationPermit = InvocationPermit.noOp()
    override fun trackTimedOut(completion: CallbackCompletion): Unit = Unit
    override fun hasOutstandingCallbacks(): Boolean = false
}

internal sealed class CallbackOutcome<out T> {
    data class Success<T>(val value: T) : CallbackOutcome<T>()
    data class Failure(val error: Throwable) : CallbackOutcome<Nothing>()
    data class TimedOut(
        val error: TimeoutException,
        val completion: CallbackCompletion
    ) : CallbackOutcome<Nothing>()
}

private enum class CallbackExecutionState {
    PENDING,
    RUNNING,
    COMPLETED,
    CANCELLED
}

/**
 * Guards the KernelExecutor SPI. A task can start exactly once, cannot start after cancellation,
 * and actual callback completion remains observable even if an executor violates the synchronous contract.
 */
private class GuardedCallback<T>(
    private val taskName: String,
    private val task: () -> T,
    private val completion: CallbackCompletion,
    private val releaseCapacity: () -> Unit
) {
    private val state = AtomicReference(CallbackExecutionState.PENDING)
    private val result = AtomicReference<CallbackOutcome<T>?>(null)
    private val protocolFailure = AtomicReference<Throwable?>(null)
    private val runningThread = AtomicReference<Thread?>(null)
    private val workerReturned = AtomicBoolean(false)
    private val capacityReleased = AtomicBoolean(false)

    internal fun run(): Unit {
        if (!state.compareAndSet(CallbackExecutionState.PENDING, CallbackExecutionState.RUNNING)) return
        runningThread.set(Thread.currentThread())
        try {
            result.compareAndSet(null, CallbackOutcome.Success(task()))
        } catch (error: Throwable) {
            result.compareAndSet(null, CallbackOutcome.Failure(error))
        } finally {
            runningThread.set(null)
            state.set(CallbackExecutionState.COMPLETED)
            completion.complete()
            releaseCapacityIfTerminal()
        }
    }

    internal fun executorReturned(): Unit {
        workerReturned.set(true)
        when (state.get()) {
            CallbackExecutionState.PENDING -> {
                if (state.compareAndSet(CallbackExecutionState.PENDING, CallbackExecutionState.CANCELLED)) {
                    result.compareAndSet(
                        null,
                        CallbackOutcome.Failure(
                            IllegalStateException("KernelExecutor returned without executing task $taskName")
                        )
                    )
                    completion.complete()
                }
            }
            CallbackExecutionState.RUNNING -> protocolFailure.compareAndSet(
                null,
                IllegalStateException("KernelExecutor returned before task $taskName completed")
            )
            CallbackExecutionState.COMPLETED,
            CallbackExecutionState.CANCELLED -> Unit
        }
        releaseCapacityIfTerminal()
    }

    internal fun executorFailed(error: Throwable): Unit {
        when (state.get()) {
            CallbackExecutionState.PENDING -> {
                if (state.compareAndSet(CallbackExecutionState.PENDING, CallbackExecutionState.CANCELLED)) {
                    result.compareAndSet(null, CallbackOutcome.Failure(error))
                    completion.complete()
                }
            }
            CallbackExecutionState.RUNNING,
            CallbackExecutionState.COMPLETED -> protocolFailure.compareAndSet(null, error)
            CallbackExecutionState.CANCELLED -> Unit
        }
    }

    internal fun cancelBeforeStart(): Unit {
        if (state.compareAndSet(CallbackExecutionState.PENDING, CallbackExecutionState.CANCELLED)) {
            completion.complete()
            releaseCapacityIfTerminal()
        }
    }

    internal fun interruptRunning(): Unit {
        runningThread.get()?.interrupt()
    }

    internal fun outcome(): CallbackOutcome<T> {
        protocolFailure.get()?.let { return CallbackOutcome.Failure(it) }
        return result.get() ?: CallbackOutcome.Failure(
            IllegalStateException("Kernel callback produced no outcome: $taskName")
        )
    }

    private fun releaseCapacityIfTerminal(): Unit {
        val terminal = state.get() in setOf(CallbackExecutionState.COMPLETED, CallbackExecutionState.CANCELLED)
        if (workerReturned.get() && terminal && capacityReleased.compareAndSet(false, true)) {
            releaseCapacity()
        }
    }
}

/**
 * Executes untrusted callbacks outside kernel monitors with bounded concurrency.
 * A timeout requests interruption but keeps an actual-completion token because Java interruption
 * does not prove that callback code has terminated.
 */
internal class CallbackSupervisor(
    private val executor: KernelExecutor,
    private val logger: KernelLogger,
    maxConcurrentCallbacks: Int = DEFAULT_MAX_CONCURRENT_CALLBACKS
) {
    private val capacity = Semaphore(maxConcurrentCallbacks, true)

    init {
        require(maxConcurrentCallbacks > 0) { "Callback concurrency limit must be positive" }
    }

    internal fun <T> execute(taskName: String, timeoutMillis: Long, task: () -> T): CallbackOutcome<T> {
        if (!capacity.tryAcquire()) {
            return CallbackOutcome.Failure(
                RejectedExecutionException("Kernel callback capacity exhausted while scheduling $taskName")
            )
        }

        val completion = CallbackCompletion(taskName)
        val guarded = GuardedCallback(taskName, task, completion, capacity::release)
        val worker = Thread(
            {
                try {
                    executor.execute(taskName, guarded::run)
                } catch (error: Throwable) {
                    guarded.executorFailed(error)
                } finally {
                    guarded.executorReturned()
                }
            },
            "toolbox-$taskName"
        ).apply { isDaemon = true }

        try {
            worker.start()
        } catch (error: Throwable) {
            guarded.executorFailed(error)
            guarded.executorReturned()
            return CallbackOutcome.Failure(error)
        }

        val completed = completion.await(timeoutMillis)
        if (!completed) {
            guarded.cancelBeforeStart()
            worker.interrupt()
            guarded.interruptRunning()
            val timeout = TimeoutException("Kernel callback timed out after ${timeoutMillis}ms: $taskName")
            logger.error(timeout.message ?: "Kernel callback timeout", timeout)
            return CallbackOutcome.TimedOut(timeout, completion)
        }
        return guarded.outcome()
    }

    private companion object {
        private const val DEFAULT_MAX_CONCURRENT_CALLBACKS: Int = 32
    }
}
