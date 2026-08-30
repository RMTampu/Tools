package io.toolbox.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompatibilityTest {
    @Test
    fun `custom compatibility policy cannot bypass mandatory target ABI`() {
        val ports = KernelPorts(
            compatibilityPolicy = CompatibilityPolicy { _, _, _ -> CompatibilityResult(true) }
        )
        val descriptor = ModuleDescriptor("wrongabi", "wrongabi", "1.0.0", supportedAbis = setOf("x86_64"))
        val result = ToolBoxKernel(ports = ports).install(object : ToolBoxModule { override val descriptor = descriptor })
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.INCOMPATIBLE_MODULE, result.errors.first().code)
    }

    @Test
    fun `module API below supported floor is rejected`() {
        val config = KernelConfig(moduleApiVersion = 3, minimumSupportedModuleApiVersion = 2)
        val descriptor = ModuleDescriptor("oldapi", "oldapi", "1.0.0", apiVersion = 1)
        val result = ToolBoxKernel(config).install(object : ToolBoxModule { override val descriptor = descriptor })
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.INCOMPATIBLE_MODULE, result.errors.first().code)
    }

    @Test
    fun `external loading requires authoritative runtime environment`() {
        var loadCalled = false
        val descriptor = ModuleDescriptor("external", "external", "1.0.0")
        val loader = object : ModuleLoader {
            override fun inspect(source: ModuleSource): ModuleDescriptor = descriptor
            override fun load(source: VerifiedModuleSource, descriptor: ModuleDescriptor): ToolBoxModule {
                loadCalled = true
                return module("external")
            }
        }
        val result = ToolBoxKernel().install(ModuleSource("external", "package.zip"), loader)
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.RUNTIME_ENVIRONMENT_REQUIRED, result.errors.first().code)
        assertFalse(loadCalled)
    }

    @Test
    fun `external loading fails closed without verifier`() {
        var loadCalled = false
        val descriptor = ModuleDescriptor("external", "external", "1.0.0")
        val loader = object : ModuleLoader {
            override fun inspect(source: ModuleSource): ModuleDescriptor = descriptor
            override fun load(source: VerifiedModuleSource, descriptor: ModuleDescriptor): ToolBoxModule {
                loadCalled = true
                return module("external")
            }
        }
        val ports = KernelPorts(runtimeEnvironment = KernelRuntimeEnvironment.authoritative(30, "arm64-v8a"))
        val result = ToolBoxKernel(ports = ports).install(ModuleSource("external", "package.zip"), loader)
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.SOURCE_VERIFICATION, result.errors.first().code)
        assertFalse(loadCalled)
    }

    @Test
    fun `verified external source can load and descriptor drift is still rejected`() {
        val inspected = ModuleDescriptor("external", "external", "1.0.0")
        val drifted = inspected.copy(version = ModuleVersion.parse("2.0.0"))
        val loader = object : ModuleLoader {
            override fun inspect(source: ModuleSource): ModuleDescriptor = inspected
            override fun load(source: VerifiedModuleSource, descriptor: ModuleDescriptor): ToolBoxModule =
                object : ToolBoxModule { override val descriptor = drifted }
        }
        val kernel = ToolBoxKernel(ports = authoritativePorts())
        val result = kernel.install(ModuleSource("external", "package.zip"), loader)
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.SOURCE_MISMATCH, result.errors.first().code)
        assertNull(kernel.moduleState("external"))
    }

    @Test
    fun `verified external source installs when metadata remains stable`() {
        val descriptor = ModuleDescriptor("external", "external", "1.0.0")
        var fingerprintSeen: String? = null
        val loader = object : ModuleLoader {
            override fun inspect(source: ModuleSource): ModuleDescriptor = descriptor
            override fun load(source: VerifiedModuleSource, descriptor: ModuleDescriptor): ToolBoxModule {
                fingerprintSeen = source.fingerprint
                return object : ToolBoxModule { override val descriptor = descriptor }
            }
        }
        val kernel = ToolBoxKernel(ports = authoritativePorts())
        val result = kernel.install(ModuleSource("external", "package.zip"), loader)
        assertTrue(result.isSuccess)
        assertEquals("sha256:test", fingerprintSeen)
        assertEquals(ModuleState.REGISTERED, kernel.moduleState("external"))
    }

    @Test
    fun `additional compatibility policy can only tighten compatibility`() {
        val ports = KernelPorts(
            compatibilityPolicy = CompatibilityPolicy { _, _, descriptor ->
                CompatibilityResult(descriptor.id != "blocked", "custom block")
            }
        )
        val result = ToolBoxKernel(ports = ports).install(module("blocked"))
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.INCOMPATIBLE_MODULE, result.errors.first().code)
    }
}
