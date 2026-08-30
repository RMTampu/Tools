package io.toolbox.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CapabilityTest {
    @Test
    fun `highest compatible capability provider is selected deterministically`() {
        var selected: Capability? = null
        val kernel = ToolBoxKernel()
        kernel.install(
            module(
                "provider-low",
                providedCapabilities = setOf(CapabilityDeclaration("storage", ModuleVersion.parse("1.0.0"))),
                onLoadBlock = { it.capabilities.register(capability("storage", "1.0.0", "provider-low")) }
            )
        )
        kernel.install(
            module(
                "provider-high",
                providedCapabilities = setOf(CapabilityDeclaration("storage", ModuleVersion.parse("2.0.0"))),
                onLoadBlock = { it.capabilities.register(capability("storage", "2.0.0", "provider-high")) }
            )
        )
        kernel.install(
            module(
                "consumer",
                requiredCapabilities = setOf(
                    CapabilityRequirement.required(
                        "storage",
                        VersionRange.between(ModuleVersion.parse("1.0.0"), ModuleVersion.parse("3.0.0"))
                    )
                ),
                onLoadBlock = { selected = it.capabilities.get("storage") }
            )
        )
        assertTrue(kernel.start().isSuccess)
        assertEquals("provider-high", selected?.providerModuleId)
        assertEquals(ModuleVersion.parse("2.0.0"), selected?.version)
    }

    @Test
    fun `capability provider cannot uninstall while required consumer exists`() {
        val kernel = ToolBoxKernel()
        kernel.install(
            module(
                "provider",
                providedCapabilities = setOf(CapabilityDeclaration("storage", ModuleVersion.parse("1.0.0"))),
                onLoadBlock = { it.capabilities.register(capability("storage", "1.0.0", "provider")) }
            )
        )
        kernel.install(
            module(
                "consumer",
                requiredCapabilities = setOf(CapabilityRequirement.required("storage"))
            )
        )
        assertTrue(kernel.start().isSuccess)
        val result = kernel.uninstall("provider")
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.CONFLICT, result.errors.first().code)
    }

    @Test
    fun `unsatisfied capability version fails resolution before callbacks`() {
        var consumerLoaded = false
        val kernel = ToolBoxKernel()
        kernel.install(
            module(
                "provider",
                providedCapabilities = setOf(CapabilityDeclaration("storage", ModuleVersion.parse("1.0.0"))),
                onLoadBlock = { it.capabilities.register(capability("storage", "1.0.0", "provider")) }
            )
        )
        kernel.install(
            module(
                "consumer",
                requiredCapabilities = setOf(
                    CapabilityRequirement.required(
                        "storage",
                        VersionRange.atLeast(ModuleVersion.parse("2.0.0"))
                    )
                ),
                onLoadBlock = { consumerLoaded = true }
            )
        )
        val result = kernel.start()
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.CAPABILITY_RESOLUTION, result.errors.first().code)
        assertFalse(consumerLoaded)
    }

    @Test
    fun `provider cannot register undeclared capability or wrong version`() {
        val kernel = ToolBoxKernel()
        kernel.install(
            module(
                "provider",
                providedCapabilities = setOf(CapabilityDeclaration("storage", ModuleVersion.parse("2.0.0"))),
                onLoadBlock = { it.capabilities.register(capability("storage", "1.0.0", "provider")) }
            )
        )
        val result = kernel.start()
        assertFalse(result.isSuccess)
        assertEquals(ModuleState.FAILED, kernel.moduleState("provider"))
        assertEquals(LifecyclePhase.LOAD, result.failures.first().phase)
    }

    @Test
    fun `missing optional capability does not block startup`() {
        val kernel = ToolBoxKernel()
        kernel.install(
            module(
                "consumer",
                requiredCapabilities = setOf(CapabilityRequirement.optional("optional.storage"))
            )
        )
        assertTrue(kernel.start().isSuccess)
        assertEquals(ModuleState.STARTED, kernel.moduleState("consumer"))
    }
}
