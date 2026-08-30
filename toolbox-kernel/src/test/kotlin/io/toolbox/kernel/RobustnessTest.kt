package io.toolbox.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RobustnessTest {
    @Test
    fun `descriptor getter failure becomes structured result`() {
        val module = object : ToolBoxModule {
            override val descriptor: ModuleDescriptor get() = error("descriptor getter")
        }
        val result = ToolBoxKernel().install(module)
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.INVALID_DESCRIPTOR, result.errors.first().code)
    }

    @Test
    fun `compatibility policy failure becomes structured result`() {
        val ports = KernelPorts(
            compatibilityPolicy = CompatibilityPolicy { _, _, _ -> error("policy") }
        )
        val result = ToolBoxKernel(ports = ports).install(module("policy"))
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.POLICY_FAILURE, result.errors.first().code)
    }

    @Test
    fun `admission policy failure becomes structured result`() {
        val ports = KernelPorts(
            admissionPolicy = ModuleAdmissionPolicy { _, _ -> error("policy") }
        )
        val result = ToolBoxKernel(ports = ports).install(module("admission"))
        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.POLICY_FAILURE, result.errors.first().code)
    }

    @Test
    fun `installed descriptor collections cannot be mutated through cast`() {
        val abis = mutableSetOf("arm64-v8a")
        val required = mutableSetOf("cap")
        val descriptor = ModuleDescriptor("immutable", "immutable", "1.0", supportedAbis = abis, requiredCapabilities = required)
        val kernel = ToolBoxKernel()
        assertTrue(kernel.install(module(descriptor)).isSuccess)
        abis += "x86_64"
        required.clear()
        val installed = kernel.moduleDescriptors().single()
        assertEquals(setOf("arm64-v8a"), installed.supportedAbis)
        assertEquals(setOf("cap"), installed.requiredCapabilities)
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (installed.supportedAbis as MutableSet<String>).add("x86_64")
        }
    }

    @Test
    fun `public module extensions are inactive after stop and active after restart`() {
        val kernel = ToolBoxKernel()
        val module = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("extensions", "extensions", "1.0")
            override fun onLoad(context: KernelContext) {
                context.services.register(String::class.java, "service")
                context.capabilities.register(object : Capability {
                    override val id = "cap"
                    override val version = 1
                    override val providerModuleId = "extensions"
                })
                context.commands.register("ping") { CommandResult.success("pong") }
            }
        }
        kernel.install(module)
        assertTrue(kernel.start().isSuccess)
        assertEquals("service", kernel.service(String::class.java))
        assertEquals(listOf("cap"), kernel.capabilities().map { it.id })
        assertTrue(kernel.execute(command("ping")).success)

        assertTrue(kernel.stop().isSuccess)
        assertNull(kernel.service(String::class.java))
        assertTrue(kernel.capabilities().isEmpty())
        assertFalse(kernel.execute(command("ping")).success)

        assertTrue(kernel.start().isSuccess)
        assertEquals("service", kernel.service(String::class.java))
        assertTrue(kernel.execute(command("ping")).success)
    }

    @Test
    fun `retry refuses failed module that still owns loaded scope`() {
        var failStop = true
        val kernel = ToolBoxKernel()
        val module = object : ToolBoxModule {
            override val descriptor = ModuleDescriptor("scope", "scope", "1.0")
            override fun onStop() {
                if (failStop) error("stop failed")
            }
        }
        kernel.install(module)
        kernel.start()
        assertFalse(kernel.uninstall("scope").isSuccess)
        val retry = kernel.retryModule("scope")
        assertFalse(retry.isSuccess)
        assertEquals(KernelErrorCode.INVALID_STATE, retry.errors.first().code)
        failStop = false
        assertTrue(kernel.uninstall("scope").isSuccess)
        assertNull(kernel.moduleState("scope"))
    }

    private fun module(id: String): ToolBoxModule = module(ModuleDescriptor(id, id, "1.0"))

    private fun module(descriptorValue: ModuleDescriptor): ToolBoxModule = object : ToolBoxModule {
        override val descriptor = descriptorValue
    }

    private fun command(nameValue: String): KernelCommand = object : KernelCommand {
        override val name = nameValue
    }
}
