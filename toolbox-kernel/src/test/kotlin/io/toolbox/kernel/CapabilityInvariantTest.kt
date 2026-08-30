package io.toolbox.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CapabilityInvariantTest {
    @Test
    fun `descriptor capability is automatically published when provider starts`() {
        var observed: Capability? = null
        val kernel = ToolBoxKernel()
        kernel.install(
            module(
                "provider",
                providedCapabilities = setOf(CapabilityDeclaration("storage", ModuleVersion.parse("2.0.0")))
            )
        )
        kernel.install(
            module(
                "consumer",
                requiredCapabilities = setOf(CapabilityRequirement.required("storage")),
                onLoadBlock = { context -> observed = context.capabilities.get("storage") }
            )
        )

        assertTrue(kernel.start().isSuccess)
        val capability = assertNotNull(observed)
        assertEquals("storage", capability.id)
        assertEquals(ModuleVersion.parse("2.0.0"), capability.version)
        assertEquals("provider", capability.providerModuleId)
    }

    @Test
    fun `mutable capability object cannot drift registry metadata after registration`() {
        var currentVersion = ModuleVersion.parse("1.0.0")
        val kernel = ToolBoxKernel()
        kernel.install(
            module(
                "provider",
                providedCapabilities = setOf(CapabilityDeclaration("storage", ModuleVersion.parse("1.0.0"))),
                onLoadBlock = { context ->
                    val dynamic = object : Capability {
                        override val id: String get() = "storage"
                        override val version: ModuleVersion get() = currentVersion
                        override val providerModuleId: String get() = "provider"
                    }
                    context.capabilities.register(dynamic, replace = true)
                    currentVersion = ModuleVersion.parse("99.0.0")
                }
            )
        )

        assertTrue(kernel.start().isSuccess)
        val capability = kernel.capabilities().single { it.id == "storage" }
        assertEquals(ModuleVersion.parse("1.0.0"), capability.version)
    }

    @Test
    fun `descriptor capability cannot be removed from module scope`() {
        val kernel = ToolBoxKernel()
        kernel.install(
            module(
                "provider",
                providedCapabilities = setOf(CapabilityDeclaration("storage", ModuleVersion.parse("1.0.0"))),
                onLoadBlock = { context -> context.capabilities.unregister("storage") }
            )
        )

        val result = kernel.start()
        assertFalse(result.isSuccess)
        assertEquals(ModuleState.FAILED, kernel.moduleState("provider"))
        assertEquals(LifecyclePhase.LOAD, result.failures.first().phase)
    }
}
