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
    fun `incompatible direct provider can be removed so a compatible version can replace it`() {
        val kernel = ToolBoxKernel()
        assertTrue(kernel.install(module("provider", version = "1.0.0")).isSuccess)
        assertTrue(
            kernel.install(
                module(
                    "consumer",
                    dependencies = setOf(
                        ModuleDependency.required(
                            "provider",
                            VersionRange.atLeast(ModuleVersion.parse("2.0.0"))
                        )
                    )
                )
            ).isSuccess
        )

        assertTrue(kernel.uninstall("provider").isSuccess)
        assertNull(kernel.moduleState("provider"))
        assertEquals(ModuleState.REGISTERED, kernel.moduleState("consumer"))
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

    @Test
    fun `inactive capability consumer blocks removal when only alternative provider is unresolved`() {
        val storage = CapabilityDeclaration("storage", ModuleVersion.parse("1.0.0"))
        val kernel = ToolBoxKernel()
        assertTrue(kernel.install(module("provider-good", providedCapabilities = setOf(storage))).isSuccess)
        assertTrue(
            kernel.install(
                module(
                    "provider-broken",
                    dependencies = setOf(ModuleDependency.required("missing")),
                    providedCapabilities = setOf(storage)
                )
            ).isSuccess
        )
        assertTrue(
            kernel.install(
                module(
                    "consumer",
                    requiredCapabilities = setOf(CapabilityRequirement.required("storage"))
                )
            ).isSuccess
        )

        val result = kernel.uninstall("provider-good")

        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.CONFLICT, result.errors.first().code)
        assertEquals(ModuleState.REGISTERED, kernel.moduleState("provider-good"))
    }

    @Test
    fun `active capability consumer keeps its bound provider even when alternative exists`() {
        val storage = CapabilityDeclaration("storage", ModuleVersion.parse("1.0.0"))
        val kernel = ToolBoxKernel()
        assertTrue(
            kernel.install(
                module(
                    "provider-a",
                    providedCapabilities = setOf(storage),
                    onLoadBlock = { it.capabilities.register(capability("storage", "1.0.0", "provider-a")) }
                )
            ).isSuccess
        )
        assertTrue(
            kernel.install(
                module(
                    "provider-b",
                    providedCapabilities = setOf(storage),
                    onLoadBlock = { it.capabilities.register(capability("storage", "1.0.0", "provider-b")) }
                )
            ).isSuccess
        )
        assertTrue(
            kernel.install(
                module(
                    "consumer",
                    requiredCapabilities = setOf(CapabilityRequirement.required("storage"))
                )
            ).isSuccess
        )
        assertTrue(kernel.start().isSuccess)

        val result = kernel.uninstall("provider-a")

        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.CONFLICT, result.errors.first().code)
        assertEquals(ModuleState.STARTED, kernel.moduleState("provider-a"))
        assertEquals(ModuleState.STARTED, kernel.moduleState("consumer"))
    }
}
