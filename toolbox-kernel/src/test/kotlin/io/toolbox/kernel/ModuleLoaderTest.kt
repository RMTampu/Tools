package io.toolbox.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModuleLoaderTest {
    @Test
    fun `staging failure is structured and later phases are not called`() {
        var inspectCalled = false
        val ports = authoritativePorts(
            stager = ModuleSourceStager { error("staging failed") }
        )
        val loader = object : ModuleLoader {
            override fun inspect(source: StagedModuleSource): ModuleDescriptor {
                inspectCalled = true
                return ModuleDescriptor("external", "external", "1.0.0")
            }

            override fun load(source: VerifiedModuleSource, descriptor: ModuleDescriptor): ToolBoxModule = module("external")
        }
        val result = ToolBoxKernel(ports = ports).install(ModuleSource("external", "package.zip"), loader)
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.SOURCE_STAGING, result.errors.first().code)
        assertFalse(inspectCalled)
    }

    @Test
    fun `inspection failure is structured and verifier and loader are not called`() {
        var verifierCalled = false
        var loadCalled = false
        val ports = authoritativePorts(
            verifier = ModuleSourceVerifier { _, _ ->
                verifierCalled = true
                SourceVerificationResult(true, "hash", algorithm = "SHA-256", policyId = "test")
            }
        )
        val loader = object : ModuleLoader {
            override fun inspect(source: StagedModuleSource): ModuleDescriptor = error("manifest broken")
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
            stager = ModuleSourceStager { source -> staged(source).copy(sourceId = "different") },
            verifier = ModuleSourceVerifier { _, _ ->
                verifierCalled = true
                SourceVerificationResult(true, "hash", algorithm = "SHA-256", policyId = "test")
            }
        )
        val loader = loader(ModuleDescriptor("external", "external", "1.0.0"))
        val result = ToolBoxKernel(ports = ports).install(ModuleSource("external", "package.zip"), loader)
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.SOURCE_MISMATCH, result.errors.first().code)
        assertFalse(verifierCalled)
    }

    @Test
    fun `staged artifact must be immutable`() {
        var inspectCalled = false
        val ports = authoritativePorts(
            stager = ModuleSourceStager { source -> staged(source).copy(immutable = false) }
        )
        val loader = object : ModuleLoader {
            override fun inspect(source: StagedModuleSource): ModuleDescriptor {
                inspectCalled = true
                return ModuleDescriptor("external", "external", "1.0.0")
            }
            override fun load(source: VerifiedModuleSource, descriptor: ModuleDescriptor): ToolBoxModule = module("external")
        }
        val result = ToolBoxKernel(ports = ports).install(ModuleSource("external", "package.zip"), loader)
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.SOURCE_STAGING, result.errors.first().code)
        assertFalse(inspectCalled)
    }

    @Test
    fun `staging inspection verification admission and loading happen in trust order`() {
        val order = mutableListOf<String>()
        val descriptor = ModuleDescriptor("external", "external", "1.0.0")
        val admission = ModuleAdmissionPolicy { _, _, verified ->
            order += "admit"
            assertEquals("sha256:verified", verified?.fingerprint)
            assertEquals("trusted-signer", verified?.signerId)
            AdmissionDecision(true)
        }
        val ports = authoritativePorts(
            stager = ModuleSourceStager { source -> order += "stage"; staged(source) },
            verifier = ModuleSourceVerifier { source, _ ->
                order += "verify"
                assertTrue(source.immutable)
                SourceVerificationResult(
                    verified = true,
                    fingerprint = "sha256:verified",
                    algorithm = "SHA-256",
                    signerId = "trusted-signer",
                    policyId = "signed-module-v1"
                )
            },
            admissionPolicy = admission
        )
        val loader = object : ModuleLoader {
            override fun inspect(source: StagedModuleSource): ModuleDescriptor {
                order += "inspect"
                assertTrue(source.location.startsWith("internal:"))
                return descriptor
            }

            override fun load(source: VerifiedModuleSource, descriptor: ModuleDescriptor): ToolBoxModule {
                order += "load"
                assertEquals("sha256:verified", source.fingerprint)
                assertEquals("internal:package.zip", source.stagedSource.location)
                return object : ToolBoxModule { override val descriptor = descriptor }
            }
        }
        val kernel = ToolBoxKernel(ports = ports)
        assertTrue(kernel.install(ModuleSource("external", "package.zip"), loader).isSuccess)
        assertEquals(listOf("stage", "inspect", "verify", "admit", "load"), order)
    }

    @Test
    fun `incompatible staged source never reaches verifier admission or executable loader`() {
        var verifierCalled = false
        var admissionCalled = false
        var loadCalled = false
        val descriptor = ModuleDescriptor("external", "external", "1.0.0", supportedAbis = setOf("x86_64"))
        val ports = authoritativePorts(
            verifier = ModuleSourceVerifier { _, _ ->
                verifierCalled = true
                SourceVerificationResult(true, "hash", algorithm = "SHA-256", policyId = "test")
            },
            admissionPolicy = ModuleAdmissionPolicy { _, _, _ ->
                admissionCalled = true
                AdmissionDecision(true)
            }
        )
        val loader = object : ModuleLoader {
            override fun inspect(source: StagedModuleSource): ModuleDescriptor = descriptor
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
        assertFalse(admissionCalled)
        assertFalse(loadCalled)
        assertNull(kernel.moduleState("external"))
    }

    private fun loader(descriptor: ModuleDescriptor): ModuleLoader = object : ModuleLoader {
        override fun inspect(source: StagedModuleSource): ModuleDescriptor = descriptor
        override fun load(source: VerifiedModuleSource, descriptor: ModuleDescriptor): ToolBoxModule =
            object : ToolBoxModule { override val descriptor = descriptor }
    }
}
