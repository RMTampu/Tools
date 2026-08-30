package io.toolbox.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoutingRebindTest {
    @Test
    fun `stopped consumer can rebind after its previous provider fails`() {
        val selectedProviders = mutableListOf<String>()
        val kernel = ToolBoxKernel()
        assertTrue(
            kernel.install(
                module(
                    "provider-high",
                    providedCapabilities = setOf(
                        CapabilityDeclaration("storage", ModuleVersion.parse("2.0.0"))
                    ),
                    onLoadBlock = {
                        it.capabilities.register(capability("storage", "2.0.0", "provider-high"))
                    },
                    onStopBlock = { error("provider-high retirement failure") }
                )
            ).isSuccess
        )
        assertTrue(
            kernel.install(
                module(
                    "provider-low",
                    providedCapabilities = setOf(
                        CapabilityDeclaration("storage", ModuleVersion.parse("1.0.0"))
                    ),
                    onLoadBlock = {
                        it.capabilities.register(capability("storage", "1.0.0", "provider-low"))
                    }
                )
            ).isSuccess
        )
        assertTrue(
            kernel.install(
                module(
                    "consumer",
                    requiredCapabilities = setOf(
                        CapabilityRequirement.required(
                            "storage",
                            VersionRange.between(
                                ModuleVersion.parse("1.0.0"),
                                ModuleVersion.parse("3.0.0")
                            )
                        )
                    ),
                    onLoadBlock = { context ->
                        selectedProviders += context.capabilities.get("storage")?.providerModuleId
                            ?: error("storage route unavailable")
                    }
                )
            ).isSuccess
        )

        assertTrue(kernel.start().isSuccess)
        assertEquals(listOf("provider-high"), selectedProviders)
        assertTrue(kernel.stopModule("consumer").isSuccess)

        val providerStop = kernel.stopModule("provider-high")
        assertFalse(providerStop.isSuccess)
        assertEquals(ModuleState.FAILED, kernel.moduleState("provider-high"))
        assertEquals(KernelState.DEGRADED, kernel.state)

        val consumerRestart = kernel.startModule("consumer")
        assertTrue(consumerRestart.isSuccess)
        assertEquals(ModuleState.STARTED, kernel.moduleState("consumer"))
        assertEquals(listOf("provider-high", "provider-low"), selectedProviders)
        assertEquals(KernelState.DEGRADED, kernel.state)
    }

    @Test
    fun `start request for already started consumer preserves its current binding`() {
        val selectedProviders = mutableListOf<String>()
        val kernel = ToolBoxKernel()
        assertTrue(
            kernel.install(
                module(
                    "provider-low",
                    providedCapabilities = setOf(
                        CapabilityDeclaration("storage", ModuleVersion.parse("1.0.0"))
                    ),
                    onLoadBlock = {
                        it.capabilities.register(capability("storage", "1.0.0", "provider-low"))
                    }
                )
            ).isSuccess
        )
        assertTrue(
            kernel.install(
                module(
                    "consumer",
                    requiredCapabilities = setOf(CapabilityRequirement.required("storage")),
                    onLoadBlock = { context ->
                        selectedProviders += context.capabilities.get("storage")?.providerModuleId
                            ?: error("storage route unavailable")
                    }
                )
            ).isSuccess
        )
        assertTrue(kernel.start().isSuccess)
        assertEquals(listOf("provider-low"), selectedProviders)

        assertTrue(
            kernel.install(
                module(
                    "provider-high",
                    providedCapabilities = setOf(
                        CapabilityDeclaration("storage", ModuleVersion.parse("2.0.0"))
                    ),
                    onLoadBlock = {
                        it.capabilities.register(capability("storage", "2.0.0", "provider-high"))
                    }
                )
            ).isSuccess
        )

        assertTrue(kernel.startModule("consumer").isSuccess)
        assertEquals(ModuleState.STARTED, kernel.moduleState("consumer"))
        assertEquals(listOf("provider-low"), selectedProviders)
        assertEquals(KernelState.RUNNING, kernel.state)
    }
}
