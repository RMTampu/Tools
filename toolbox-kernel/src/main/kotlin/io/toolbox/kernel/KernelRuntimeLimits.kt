package io.toolbox.kernel

/**
 * One-time runtime resource budget for callback and event dispatch. It is intentionally separate
 * from [KernelConfig] so the established KernelConfig constructor/copy ABI remains intact.
 */
public class KernelRuntimeLimits(
    public val maxLifecycleCallbacks: Int = 8,
    public val maxExtensionCallbacks: Int = 24,
    public val maxEventSubscriptions: Int = 256,
    public val eventDispatchTimeoutMillis: Long = 10_000
) {
    init {
        require(maxLifecycleCallbacks > 0) { "Lifecycle callback limit must be positive" }
        require(maxExtensionCallbacks > 0) { "Extension callback limit must be positive" }
        require(maxEventSubscriptions > 0) { "Event subscription limit must be positive" }
        require(eventDispatchTimeoutMillis > 0) { "Event dispatch timeout must be positive" }
    }
}
