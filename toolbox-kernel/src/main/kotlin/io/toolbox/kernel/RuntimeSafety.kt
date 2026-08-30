package io.toolbox.kernel

import java.util.LinkedHashSet
import java.util.concurrent.ConcurrentHashMap
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
    fun isContextUsable(): Boolean
    fun isAcceptingInvocations(): Boolean
    fun tryAcquireContextUse(): InvocationPermit?
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

internal inline fun <T> ResourceOwner.withContextUse(block: () -> T): T {
    val permit = tryAcquireContextUse()
        ?: throw IllegalStateException("Module context ${token.id}#${token.generation} is no longer usable")
    return try {
        block()
    } finally {
        permit.close()
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
    private var contextUsesOpen = true
    private var acceptingInvocations = false
    private var activeInvocations = 0
    private val outstandingCallbacks = LinkedHashSet<CallbackCompletion>()

    override fun assertContextOpen(): Unit = synchronized(lock) {
        check(contextOpen) { "Module context ${token.id}#${token.generation} is no longer valid" }
    }

    override fun isContextUsable(): Boolean = synchronized(lock) {
        contextOpen && contextUsesOpen
    }

    override fun isAcceptingInvocations(): Boolean = synchronized(lock) {
        contextOpen && acceptingInvocations
    }

    internal fun activateInvocations(): Unit = synchronized(lock) {
        check(contextOpen && contextUsesOpen) { "Cannot activate a closed or quiescing module context" }
        acceptingInvocations = true
    }

    override fun tryAcquireContextUse(): InvocationPermit? = synchronized(lock) {
        if (!contextOpen || !contextUsesOpen) return@synchronized null
        activeInvocations++
        permit()
    }

    override fun tryAcquireInvocation(): InvocationPermit? = synchronized(lock) {
        if (!contextOpen || !acceptingInvocations) return@synchronized null
        activeInvocations++
        permit()
    }

    private fun permit(): InvocationPermit = InvocationPermit {
        synchronized(lock) {
            activeInvocations--
            check(activeInvocations >= 0) { "Invocation permit underflow for ${token.id}#${token.generation}" }
            lock.notifyAll()
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
        val startedAtNanos = System.nanoTime()
        synchronized(lock) {
            acceptingInvocations = false
            contextUsesOpen = false
            while (activeInvocations > 0) {
                val remainingMillis = remainingMillis(startedAtNanos, timeoutMillis)
                if (remainingMillis <= 0) return false
                try {
                    lock.wait(remainingMillis)
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
        contextUsesOpen = false
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
    override fun isContextUsable(): Boolean = true
    override fun isAcceptingInvocations(): Boolean = true
    override fun tryAcquireContextUse(): InvocationPermit = InvocationPermit.noOp()
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

internal class CallbackCapacityException(message: String) : RejectedExecutionException(message)

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
    private val executorReturnedSignal = CountDownLatch(1)
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
        try {
            when (state.get() ?: CallbackExecutionState.CANCELLED) {
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
        } finally {
            executorReturnedSignal.countDown()
            releaseCapacityIfTerminal()
        }
    }

    internal fun executorFailed(error: Throwable): Unit {
        when (state.get() ?: CallbackExecutionState.CANCELLED) {
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

    internal fun awaitExecutorReturn(timeoutMillis: Long): Boolean = try {
        executorReturnedSignal.await(timeoutMillis.coerceAtLeast(0), TimeUnit.MILLISECONDS)
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }

    internal fun outcome(): CallbackOutcome<T> {
        protocolFailure.get()?.let { return CallbackOutcome.Failure(it) }
        return result.get() ?: CallbackOutcome.Failure(
            IllegalStateException("Kernel callback produced no outcome: $taskName")
        )
    }

    private fun releaseCapacityIfTerminal(): Unit {
        val current = state.get() ?: CallbackExecutionState.CANCELLED
        val terminal = current == CallbackExecutionState.COMPLETED || current == CallbackExecutionState.CANCELLED
        if (workerReturned.get() && terminal && capacityReleased.compareAndSet(false, true)) {
            releaseCapacity()
        }
    }
}

/**
 * Executes untrusted callbacks outside kernel monitors with bounded concurrency.
 *
 * Lifecycle/control callbacks and extension callbacks have independent pools, so extension work
 * cannot consume the capacity required by stop/unload/recovery. Extension callbacks additionally
 * use an owner quota, preventing one module generation from monopolizing the extension pool.
 * A timeout requests interruption but keeps an actual-completion token because Java interruption
 * does not prove that callback code has terminated.
 */
internal class CallbackSupervisor(
    private val executor: KernelExecutor,
    private val logger: KernelLogger
) {
    internal val limits: KernelRuntimeLimits =
        (executor as? KernelRuntimeLimitsProvider)?.runtimeLimits ?: KernelRuntimeLimits()

    private val lifecycleCapacity = Semaphore(limits.maxLifecycleCallbacks, true)
    private val extensionCapacity = Semaphore(limits.maxExtensionCallbacks, true)
    private val activeExtensionCallbacksByOwner = ConcurrentHashMap<OwnerToken, Int>()

    internal fun <T> execute(taskName: String, timeoutMillis: Long, task: () -> T): CallbackOutcome<T> =
        executeBounded(
            taskName = taskName,
            timeoutMillis = timeoutMillis,
            capacity = lifecycleCapacity,
            capacityName = "lifecycle",
            owner = null,
            task = task
        )

    internal fun <T> executeExtension(
        owner: OwnerToken,
        taskName: String,
        timeoutMillis: Long,
        task: () -> T
    ): CallbackOutcome<T> = executeBounded(
        taskName = taskName,
        timeoutMillis = timeoutMillis,
        capacity = extensionCapacity,
        capacityName = "extension",
        owner = owner,
        task = task
    )

    private fun <T> executeBounded(
        taskName: String,
        timeoutMillis: Long,
        capacity: Semaphore,
        capacityName: String,
        owner: OwnerToken?,
        task: () -> T
    ): CallbackOutcome<T> {
        require(timeoutMillis > 0) { "Callback timeout must be positive" }
        if (!capacity.tryAcquire()) {
            return CallbackOutcome.Failure(
                CallbackCapacityException("Kernel $capacityName callback capacity exhausted while scheduling $taskName")
            )
        }
        if (owner != null && !tryAcquireOwner(owner)) {
            capacity.release()
            return CallbackOutcome.Failure(
                CallbackCapacityException(
                    "Kernel extension callback quota exhausted for ${owner.id}#${owner.generation} while scheduling $taskName"
                )
            )
        }

        val startedAtNanos = System.nanoTime()
        val completion = CallbackCompletion(taskName)
        val guarded = GuardedCallback(
            taskName = taskName,
            task = task,
            completion = completion,
            releaseCapacity = {
                if (owner != null) releaseOwner(owner)
                capacity.release()
            }
        )
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

        if (!completion.await(remainingMillis(startedAtNanos, timeoutMillis))) {
            return timeout(taskName, timeoutMillis, completion, guarded, worker)
        }
        if (!guarded.awaitExecutorReturn(remainingMillis(startedAtNanos, timeoutMillis))) {
            return timeout(taskName, timeoutMillis, completion, guarded, worker)
        }
        return guarded.outcome()
    }

    private fun tryAcquireOwner(owner: OwnerToken): Boolean {
        var acquired = false
        activeExtensionCallbacksByOwner.compute(owner) { _, current ->
            val count = current ?: 0
            if (count >= limits.maxExtensionCallbacksPerOwner) {
                count
            } else {
                acquired = true
                count + 1
            }
        }
        return acquired
    }

    private fun releaseOwner(owner: OwnerToken): Unit {
        activeExtensionCallbacksByOwner.compute(owner) { _, current ->
            check(current != null && current > 0) {
                "Extension callback quota underflow for ${owner.id}#${owner.generation}"
            }
            if (current == 1) null else current - 1
        }
    }

    private fun <T> timeout(
        taskName: String,
        timeoutMillis: Long,
        completion: CallbackCompletion,
        guarded: GuardedCallback<T>,
        worker: Thread
    ): CallbackOutcome<T> {
        guarded.cancelBeforeStart()
        worker.interrupt()
        guarded.interruptRunning()
        val timeout = TimeoutException("Kernel callback timed out after ${timeoutMillis}ms: $taskName")
        logger.error(timeout.message ?: "Kernel callback timeout", timeout)
        return CallbackOutcome.TimedOut(timeout, completion)
    }
}

internal fun remainingMillis(startedAtNanos: Long, timeoutMillis: Long): Long {
    if (timeoutMillis <= 0) return 0
    val budgetNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
    val elapsedNanos = (System.nanoTime() - startedAtNanos).coerceAtLeast(0)
    val remainingNanos = budgetNanos - elapsedNanos
    if (remainingNanos <= 0) return 0
    return TimeUnit.NANOSECONDS.toMillis(remainingNanos).coerceAtLeast(1)
}
