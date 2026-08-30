package io.toolbox.kernel

/**
 * Runtime resource budgets for callback execution and event fan-out.
 *
 * The limits are supplied through [BudgetedKernelExecutor], which preserves the established
 * [KernelPorts] and [ToolBoxKernel] constructor ABI while giving hosts a one-time configuration
 * point before the kernel starts using callbacks.
 */
public class KernelRuntimeLimits(
    public val maxLifecycleCallbacks: Int = 8,
    public val maxExtensionCallbacks: Int = 24,
    public val maxExtensionCallbacksPerOwner: Int = 4,
    public val maxEventSubscriptions: Int = 256,
    public val maxEventSubscriptionsPerOwner: Int = 32,
    public val eventDispatchTimeoutMillis: Long = 10_000
) {
    init {
        require(maxLifecycleCallbacks > 0) { "Lifecycle callback limit must be positive" }
        require(maxExtensionCallbacks > 0) { "Extension callback limit must be positive" }
        require(maxExtensionCallbacksPerOwner > 0) { "Per-owner extension callback limit must be positive" }
        require(maxExtensionCallbacksPerOwner <= maxExtensionCallbacks) {
            "Per-owner extension callback limit cannot exceed the global extension callback limit"
        }
        require(maxEventSubscriptions > 0) { "Event subscription limit must be positive" }
        require(maxEventSubscriptionsPerOwner > 0) { "Per-owner event subscription limit must be positive" }
        require(maxEventSubscriptionsPerOwner <= maxEventSubscriptions) {
            "Per-owner event subscription limit cannot exceed the global event subscription limit"
        }
        require(eventDispatchTimeoutMillis > 0) { "Event dispatch timeout must be positive" }
    }
}

/** Optional executor extension that supplies the callback/resource budget used by the kernel. */
public interface KernelRuntimeLimitsProvider {
    public val runtimeLimits: KernelRuntimeLimits
}

/**
 * Synchronous executor wrapper that attaches one immutable runtime budget to an existing executor.
 * The delegate must still obey [KernelExecutor]'s synchronous execution contract.
 */
public class BudgetedKernelExecutor(
    private val delegate: KernelExecutor = DirectKernelExecutor,
    override val runtimeLimits: KernelRuntimeLimits = KernelRuntimeLimits()
) : KernelExecutor, KernelRuntimeLimitsProvider {
    override fun execute(taskName: String, task: () -> Unit): Unit = delegate.execute(taskName, task)
}
