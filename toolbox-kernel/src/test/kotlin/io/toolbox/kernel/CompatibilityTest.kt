package io.toolbox.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatibilityTest {
    @Test
    fun `module excluding Android 11 is rejected`() {
        val kernel = ToolBoxKernel()
        val module = moduleWith(ModuleDescriptor("api", "api", "1.0", minAndroidApi = 21, maxAndroidApi = 29))
        val result = kernel.install(module)
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.INCOMPATIBLE_MODULE, result.errors.first().code)
    }

    @Test
    fun `module without arm64 ABI is rejected`() {
        val kernel = ToolBoxKernel()
        val module = moduleWith(ModuleDescriptor("abi", "abi", "1.0", supportedAbis = setOf("x86_64")))
        val result = kernel.install(module)
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.INCOMPATIBLE_MODULE, result.errors.first().code)
    }

    @Test
    fun `missing required capability blocks module load`() {
        val kernel = ToolBoxKernel()
        val module = moduleWith(ModuleDescriptor("consumer", "consumer", "1.0", requiredCapabilities = setOf("storage")))
        kernel.install(module)
        val result = kernel.start()
        assertFalse(result.isSuccess)
        assertEquals(ModuleState.FAILED, kernel.moduleState("consumer"))
        assertTrue(result.failures.any { it.phase == "capability-resolution" })
    }

    @Test
    fun `capability provided by dependency satisfies consumer`() {
        val kernel = ToolBoxKernel()
        val provider = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("provider", "provider", "1.0")
            override fun onLoad(context: KernelContext) {
                context.capabilities.register(object : Capability {
                    override val id = "storage"
                    override val version = 1
                    override val providerModuleId = "provider"
                })
            }
        }
        val consumer = moduleWith(
            ModuleDescriptor(
                "consumer",
                "consumer",
                "1.0",
                requiredCapabilities = setOf("storage"),
                dependencies = setOf(ModuleDependency.required("provider"))
            )
        )
        kernel.install(consumer)
        kernel.install(provider)
        assertTrue(kernel.start().isSuccess)
        assertEquals(ModuleState.STARTED, kernel.moduleState("consumer"))
    }

    @Test
    fun `blank entry point is invalid metadata`() {
        val kernel = ToolBoxKernel()
        val result = kernel.install(moduleWith(ModuleDescriptor("entry", "entry", "1.0", entryPoint = "")))
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.INVALID_DESCRIPTOR, result.errors.first().code)
    }

    private fun moduleWith(descriptorValue: ModuleDescriptor): ToolBoxModule = object : ToolBoxModule {
        override val descriptor = descriptorValue
    }
}
