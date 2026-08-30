package io.toolbox.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModuleLoaderTest {
    @Test
    fun `inspection failure is structured and verifier and loader are not called`() {
        var verifierCalled = false
        var loadCalled = false
        val ports = authoritativePorts(
            verifier = ModuleSourceVerifier { _, _ ->
                verifierCalled = true
                SourceVerificationResult(true, "hash")
            }
        )
        val loader = object : ModuleLoader {
            override fun inspect(source: ModuleSource): ModuleDescriptor = error("manifest broken")
            override fun load(source: VerifiedModuleSource, descriptor: ModuleDescriptor): ToolBoxModule {
                loadCalled = true
                return module("external")
            }
        }
        val result = ToolBoxKernel(ports = ports).install(ModuleSource("external", "package.zip"), loader)
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.SOURCE_INSPECTION, result.errors.first().code)
        assertFalse(verifierCalled)
        assertFalse(loadCalled)
    }

    @Test
    fun `source identity mismatch fails before verification`() {
        var verifierCalled = false
        val ports = authoritativePorts(
            verifier = ModuleSourceVerifier { _, _ ->
                verifierCalled = true
                SourceVerificationResult(true, "hash")
            }
        )
        val loader = loader(ModuleDescriptor("different", "different", "1.0.0"))
        val result = ToolBoxKernel(ports = ports).install(ModuleSource("external", "package.zip"), loader)
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.SOURCE_MISMATCH, result.errors.first().code)
        assertFalse(verifierCalled)
    }

    @Test
    fun `verification executes after compatibility and before executable load`() {
        val order = mutableListOf<String>()
        val descriptor = ModuleDescriptor("external", "external", "1.0.0")
        val ports = authoritativePorts(
            verifier = ModuleSourceVerifier { _, _ ->
                order += "verify"
                SourceVerificationResult(true, "sha256:verified")
            }
        )
        val loader = object : ModuleLoader {
            override fun inspect(source: ModuleSource): ModuleDescriptor {
                order += "inspect"
                return descriptor
            }

            override fun load(source: VerifiedModuleSource, descriptor: ModuleDescriptor): ToolBoxModule {
                order += "load"
                assertEquals("sha256:verified", source.fingerprint)
                return object : ToolBoxModule { override val descriptor = descriptor }
            }
        }
        val kernel = ToolBoxKernel(ports = ports)
        assertTrue(kernel.install(ModuleSource("external", "package.zip"), loader).isSuccess)
        assertEquals(listOf("inspect", "verify", "load"), order)
    }

    @Test
    fun `incompatible source never reaches verifier or executable loader`() {
        var verifierCalled = false
        var loadCalled = false
        val descriptor = ModuleDescriptor("external", "external", "1.0.0", supportedAbis = setOf("x86_64"))
        val ports = authoritativePorts(
            verifier = ModuleSourceVerifier { _, _ ->
                verifierCalled = true
                SourceVerificationResult(true, "hash")
            }
        )
        val loader = object : ModuleLoader {
            override fun inspect(source: ModuleSource): ModuleDescriptor = descriptor
            override fun load(source: VerifiedModuleSource, descriptor: ModuleDescriptor): ToolBoxModule {
                loadCalled = true
                return module("external")
            }
        }
        val kernel = ToolBoxKernel(ports = ports)
        val result = kernel.install(ModuleSource("external", "package.zip"), loader)
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.INCOMPATIBLE_MODULE, result.errors.first().code)
        assertFalse(verifierCalled)
        assertFalse(loadCalled)
        assertNull(kernel.moduleState("external"))
    }

    private fun loader(descriptor: ModuleDescriptor): ModuleLoader = object : ModuleLoader {
        override fun inspect(source: ModuleSource): ModuleDescriptor = descriptor
        override fun load(source: VerifiedModuleSource, descriptor: ModuleDescriptor): ToolBoxModule =
            object : ToolBoxModule { override val descriptor = descriptor }
    }
}
