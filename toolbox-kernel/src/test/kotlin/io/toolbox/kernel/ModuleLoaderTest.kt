package io.toolbox.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModuleLoaderTest {
    @Test
    fun `incompatible inspection prevents executable load`() {
        var loadCalled = false
        val descriptor = ModuleDescriptor("external", "external", "1.0", supportedAbis = setOf("x86_64"))
        val loader = loader(descriptor) {
            loadCalled = true
            moduleWith(descriptor)
        }
        val kernel = ToolBoxKernel()
        val result = kernel.install(ModuleSource("external", "package.zip"), loader)
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.INCOMPATIBLE_MODULE, result.errors.first().code)
        assertFalse(loadCalled)
        assertNull(kernel.moduleState("external"))
    }

    @Test
    fun `source identity mismatch is rejected before load`() {
        var loadCalled = false
        val descriptor = ModuleDescriptor("descriptor-id", "descriptor-id", "1.0")
        val loader = loader(descriptor) {
            loadCalled = true
            moduleWith(descriptor)
        }
        val result = ToolBoxKernel().install(ModuleSource("source-id", "package.zip"), loader)
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.SOURCE_MISMATCH, result.errors.first().code)
        assertFalse(loadCalled)
    }

    @Test
    fun `inspection failure is structured and load is not called`() {
        var loadCalled = false
        val loader = object : ModuleLoader {
            override fun inspect(source: ModuleSource): ModuleDescriptor = error("manifest broken")
            override fun load(source: ModuleSource, descriptor: ModuleDescriptor): ToolBoxModule {
                loadCalled = true
                return moduleWith(descriptor)
            }
        }
        val result = ToolBoxKernel().install(ModuleSource("external", "package.zip"), loader)
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.SOURCE_INSPECTION, result.errors.first().code)
        assertFalse(loadCalled)
    }

    @Test
    fun `descriptor drift after load is rejected without registration`() {
        val inspected = ModuleDescriptor("external", "external", "1.0")
        val drifted = inspected.copy(version = "2.0")
        val loader = loader(inspected) { moduleWith(drifted) }
        val kernel = ToolBoxKernel()
        val result = kernel.install(ModuleSource("external", "package.zip"), loader)
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.SOURCE_MISMATCH, result.errors.first().code)
        assertNull(kernel.moduleState("external"))
    }

    @Test
    fun `valid inspected source installs through loader`() {
        var loadCalled = false
        val descriptor = ModuleDescriptor("external", "external", "1.0")
        val loader = loader(descriptor) {
            loadCalled = true
            moduleWith(descriptor)
        }
        val kernel = ToolBoxKernel()
        val result = kernel.install(ModuleSource("external", "package.zip", mapOf("sha256" to "example")), loader)
        assertTrue(result.isSuccess)
        assertTrue(loadCalled)
        assertEquals(ModuleState.REGISTERED, kernel.moduleState("external"))
    }

    private fun loader(
        descriptor: ModuleDescriptor,
        loadBlock: () -> ToolBoxModule
    ): ModuleLoader = object : ModuleLoader {
        override fun inspect(source: ModuleSource): ModuleDescriptor = descriptor
        override fun load(source: ModuleSource, descriptor: ModuleDescriptor): ToolBoxModule = loadBlock()
    }

    private fun moduleWith(descriptor: ModuleDescriptor): ToolBoxModule = object : ToolBoxModule {
        override val descriptor = descriptor
    }
}
