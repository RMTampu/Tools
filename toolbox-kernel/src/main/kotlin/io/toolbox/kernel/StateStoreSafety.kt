package io.toolbox.kernel

import java.util.concurrent.ConcurrentHashMap

/**
 * Bounded observational state-store boundary. Runtime correctness never depends on this store.
 *
 * Only one delegate call may remain active. If a timed-out put later completes, it reconciles the
 * same key to the newest value requested while it was blocked, preventing a stale late write from
 * becoming the final diagnostic value merely because newer callers failed fast on capacity.
 */
internal class SafeKernelStateStore(
    private val delegate: KernelStateStore,
    private val logger: KernelLogger,
    private val timeoutMillis: Long
) {
    private val supervisor = HostCallSupervisor(HostSafetyDefaults.MAX_STATE_STORE_CALLS)
    private val latestWrites = ConcurrentHashMap<String, String>()

    init {
        require(timeoutMillis > 0) { "State-store timeout must be positive" }
    }

    internal fun get(key: String): String? = when (
        val outcome = supervisor.execute("state-store:get", timeoutMillis) { delegate.get(key) }
    ) {
        is CallbackOutcome.Success -> outcome.value
        is CallbackOutcome.Failure -> {
            logger.warn("Kernel state-store read failed for $key", outcome.error)
            null
        }
        is CallbackOutcome.TimedOut -> {
            logger.warn("Kernel state-store read timed out for $key", outcome.error)
            null
        }
    }

    internal fun put(key: String, value: String): Unit {
        latestWrites[key] = value
        when (
            val outcome = supervisor.execute("state-store:put", timeoutMillis) {
                reconcileWrite(key, value)
            }
        ) {
            is CallbackOutcome.Success -> Unit
            is CallbackOutcome.Failure -> logger.warn("Kernel state-store write failed for $key", outcome.error)
            is CallbackOutcome.TimedOut -> logger.warn("Kernel state-store write timed out for $key", outcome.error)
        }
    }

    private fun reconcileWrite(key: String, initialValue: String): Unit {
        var value = initialValue
        while (true) {
            delegate.put(key, value)
            val latest = latestWrites[key] ?: return
            if (latest == value) return
            value = latest
        }
    }
}
