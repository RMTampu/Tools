package io.toolbox.kernel

/**
 * Internal safety ceilings for metadata retained by the pure kernel and for dependency resolution
 * work. These are intentionally not public configuration: callers cannot relax the fail-closed
 * limits for one kernel instance and accidentally invalidate the foundation's resource guarantees.
 */
internal object KernelResourceBounds {
    internal const val MAX_INSTALLED_MODULES: Int = 512
    internal const val MAX_DEPENDENCIES_PER_MODULE: Int = 128
    internal const val MAX_PROVIDED_CAPABILITIES_PER_MODULE: Int = 128
    internal const val MAX_REQUIRED_CAPABILITIES_PER_MODULE: Int = 128
    internal const val MAX_SUPPORTED_ABIS_PER_MODULE: Int = 16
    internal const val MAX_RESOLUTION_DEPTH: Int = 128
    internal const val MAX_RESOLUTION_STEPS: Int = 262_144
}

internal fun ModuleDescriptor.resourceLimitError(): String? = when {
    supportedAbis.size > KernelResourceBounds.MAX_SUPPORTED_ABIS_PER_MODULE ->
        "Module declares too many ABIs: ${supportedAbis.size} > ${KernelResourceBounds.MAX_SUPPORTED_ABIS_PER_MODULE}"
    dependencies.size > KernelResourceBounds.MAX_DEPENDENCIES_PER_MODULE ->
        "Module declares too many dependencies: ${dependencies.size} > ${KernelResourceBounds.MAX_DEPENDENCIES_PER_MODULE}"
    providedCapabilities.size > KernelResourceBounds.MAX_PROVIDED_CAPABILITIES_PER_MODULE ->
        "Module declares too many provided capabilities: ${providedCapabilities.size} > ${KernelResourceBounds.MAX_PROVIDED_CAPABILITIES_PER_MODULE}"
    requiredCapabilities.size > KernelResourceBounds.MAX_REQUIRED_CAPABILITIES_PER_MODULE ->
        "Module declares too many required capabilities: ${requiredCapabilities.size} > ${KernelResourceBounds.MAX_REQUIRED_CAPABILITIES_PER_MODULE}"
    else -> null
}

internal class ResolutionBudget(
    private var remainingSteps: Int = KernelResourceBounds.MAX_RESOLUTION_STEPS
) {
    init {
        require(remainingSteps > 0) { "Resolution step budget must be positive" }
    }

    internal fun consume(moduleId: String): KernelError? {
        if (remainingSteps <= 0) {
            return KernelError(
                KernelErrorCode.DEPENDENCY_RESOLUTION,
                "Dependency resolution work budget exhausted while resolving $moduleId"
            )
        }
        remainingSteps--
        return null
    }
}
