package io.toolbox.kernel

/**
 * Internal safety ceilings for metadata retained by the pure kernel and for dependency resolution
 * work. These are intentionally not public configuration: callers cannot relax the fail-closed
 * limits for one kernel instance and accidentally invalidate the foundation's resource guarantees.
 */
internal object KernelResourceBounds {
    internal const val MAX_IDENTIFIER_LENGTH: Int = 128
    internal const val MAX_MODULE_NAME_LENGTH: Int = 256
    internal const val MAX_ENTRY_POINT_LENGTH: Int = 512
    internal const val MAX_ABI_NAME_LENGTH: Int = 64
    internal const val MAX_INSTALLED_MODULES: Int = 512
    internal const val MAX_DEPENDENCIES_PER_MODULE: Int = 128
    internal const val MAX_PROVIDED_CAPABILITIES_PER_MODULE: Int = 128
    internal const val MAX_REQUIRED_CAPABILITIES_PER_MODULE: Int = 128
    internal const val MAX_SUPPORTED_ABIS_PER_MODULE: Int = 16
    internal const val MAX_RESOLUTION_DEPTH: Int = 128
    internal const val MAX_RESOLUTION_STEPS: Int = 262_144
}

internal fun ModuleDescriptor.resourceLimitError(): String? = when {
    id.length > KernelResourceBounds.MAX_IDENTIFIER_LENGTH ->
        "Module id is too long: ${id.length} > ${KernelResourceBounds.MAX_IDENTIFIER_LENGTH}"
    name.length > KernelResourceBounds.MAX_MODULE_NAME_LENGTH ->
        "Module name is too long: ${name.length} > ${KernelResourceBounds.MAX_MODULE_NAME_LENGTH}"
    entryPoint.length > KernelResourceBounds.MAX_ENTRY_POINT_LENGTH ->
        "Module entry point is too long: ${entryPoint.length} > ${KernelResourceBounds.MAX_ENTRY_POINT_LENGTH}"
    supportedAbis.size > KernelResourceBounds.MAX_SUPPORTED_ABIS_PER_MODULE ->
        "Module declares too many ABIs: ${supportedAbis.size} > ${KernelResourceBounds.MAX_SUPPORTED_ABIS_PER_MODULE}"
    supportedAbis.any { it.length > KernelResourceBounds.MAX_ABI_NAME_LENGTH } ->
        "Module ABI name exceeds ${KernelResourceBounds.MAX_ABI_NAME_LENGTH} characters"
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
