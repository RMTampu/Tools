package io.toolbox.kernel

import java.util.concurrent.Semaphore
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

internal object HostSafetyDefaults {
    internal const val MAX_CONTROL_CALLS: Int = 8
    internal const val MAX_LOG_CALLS: Int = 2
    internal const val MAX_CLOCK_CALLS: Int = 1
    internal const val OBSERVABILITY_TIMEOUT_MILLIS: Long = 100
}

private enum class HostCallState {
    PENDING,
    RUNNING,
    COMPLETED,
    CANCELLED
}

/**
 * Bounds synchronous host-owned calls without pretending that Java interruption proves termination.
 * A timed-out running call keeps its capacity permit until the worker actually exits. Pending work is
 * cancelled before start. This makes repeated non-cooperative host failures degrade to finite
 * capacity exhaustion rather than unbounded thread creation or a permanently blocked caller.
 */
internal class HostCallSupervisor(
    maxConcurrentCalls: Int = HostSafetyDefaults.MAX_CONTROL_CALLS
) {
    private val capacity = Semaphore(maxConcurrentCalls, true)

    init {
        require(maxConcurrentCalls > 0) { "Host-call capacity must be positive" }
    }

    internal fun <T> execute(
        taskName: String,
        timeoutMillis: Long,
        task: () -> T
    ): CallbackOutcome<T> {
        require(timeoutMillis > 0) { "Host-call timeout must be positive" }
        if (!capacity.tryAcquire()) {
            return CallbackOutcome.Failure(
                CallbackCapacityException("Kernel host-call capacity exhausted while scheduling $taskName")
            )
        }

        val completion = CallbackCompletion(taskName)
        val state = AtomicReference(HostCallState.PENDING)
        val result = AtomicReference<CallbackOutcome<T>?>(null)
        val worker = try {
            Thread(
                {
                    if (!state.compareAndSet(HostCallState.PENDING, HostCallState.RUNNING)) return@Thread
                    try {
                        result.compareAndSet(null, CallbackOutcome.Success(task()))
                    } catch (error: Throwable) {
                        result.compareAndSet(null, CallbackOutcome.Failure(error))
                    } finally {
                        state.set(HostCallState.COMPLETED)
                        completion.complete()
                        capacity.release()
                    }
                },
                "toolbox-host-$taskName"
            ).apply { isDaemon = true }
        } catch (error: Throwable) {
            completion.complete()
            capacity.release()
            return CallbackOutcome.Failure(error)
        }

        try {
            worker.start()
        } catch (error: Throwable) {
            state.set(HostCallState.CANCELLED)
            completion.complete()
            capacity.release()
            return CallbackOutcome.Failure(error)
        }

        if (!completion.await(timeoutMillis)) {
            if (state.compareAndSet(HostCallState.PENDING, HostCallState.CANCELLED)) {
                completion.complete()
                capacity.release()
            } else {
                worker.interrupt()
            }
            return CallbackOutcome.TimedOut(
                TimeoutException("Kernel host call timed out after ${timeoutMillis}ms: $taskName"),
                completion
            )
        }

        return result.get() ?: CallbackOutcome.Failure(
            IllegalStateException("Kernel host call produced no outcome: $taskName")
        )
    }
}
