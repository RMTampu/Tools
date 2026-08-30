package io.toolbox.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DependencyProtectionTest {
    @Test
    fun `unrelated resolution failure cannot erase a direct required dependent`() {
        val kernel = ToolBoxKernel()
        assertTrue(kernel.install(module("provider")).isSuccess)
        assertTrue(
            kernel.install(
                module("consumer", dependencies = setOf(ModuleDependency.required("provider")))
            ).isSuccess
        )
        assertTrue(
            kernel.install(
                module("broken", dependencies = setOf(ModuleDependency.required("missing")))
            ).isSuccess
        )

        val result = kernel.uninstall("provider")

        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.CONFLICT, result.errors.first().code)
        assertEquals(ModuleState.REGISTERED, kernel.moduleState("provider"))
    }

    @Test
    fun `unrelated unresolved graph does not block independent uninstall`() {
        val kernel = ToolBoxKernel()
        assertTrue(kernel.install(module("independent")).isSuccess)
        assertTrue(
            kernel.install(
                module("broken", dependencies = setOf(ModuleDependency.required("missing")))
            ).isSuccess
        )

        assertTrue(kernel.uninstall("independent").isSuccess)
        assertNull(kernel.moduleState("independent"))
        assertEquals(ModuleState.REGISTERED, kernel.moduleState("broken"))
    }

    @Test
    fun `sole required capability provider cannot be removed from inactive graph`() {
        val storage = CapabilityDeclaration("storage", ModuleVersion.parse("1.0.0"))
        val kernel = ToolBoxKernel()
        assertTrue(kernel.install(module("provider", providedCapabilities = setOf(storage))).isSuccess)
        assertTrue(
            kernel.install(
                module(
                    "consumer",
                    requiredCapabilities = setOf(CapabilityRequirement.required("storage"))
                )
            ).isSuccess
        )

        val result = kernel.uninstall("provider")

        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.CONFLICT, result.errors.first().code)
        assertEquals(ModuleState.REGISTERED, kernel.moduleState("provider"))
    }

    @Test
    fun `redundant capability provider can be removed when alternative route is resolvable`() {
        val storage = CapabilityDeclaration("storage", ModuleVersion.parse("1.0.0"))
        val kernel = ToolBoxKernel()
        assertTrue(kernel.install(module("provider-a", providedCapabilities = setOf(storage))).isSuccess)
        assertTrue(kernel.install(module("provider-b", providedCapabilities = setOf(storage))).isSuccess)
        assertTrue(
            kernel.install(
                module(
                    "consumer",
                    requiredCapabilities = setOf(CapabilityRequirement.required("storage"))
                )
            ).isSuccess
        )

        assertTrue(kernel.uninstall("provider-a").isSuccess)
        assertNull(kernel.moduleState("provider-a"))
        assertEquals(ModuleState.REGISTERED, kernel.moduleState("provider-b"))
        assertEquals(ModuleState.REGISTERED, kernel.moduleState("consumer"))
    }
}
