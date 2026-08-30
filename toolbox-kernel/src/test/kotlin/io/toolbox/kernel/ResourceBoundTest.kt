package io.toolbox.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResourceBoundTest {
    @Test
    fun `registry rejects dependency fanout beyond kernel bound`() {
        val dependencies = (0..KernelResourceBounds.MAX_DEPENDENCIES_PER_MODULE)
            .mapTo(linkedSetOf()) { index -> ModuleDependency.required("dependency$index") }
        val candidate = module("oversized", dependencies = dependencies)
        val registry = ModuleRegistry { }

        val error = assertFailsWith<IllegalArgumentException> {
            registry.register(candidate, candidate.descriptor)
        }

        assertTrue(error.message?.contains("too many dependencies") == true)
        assertFalse(registry.contains("oversized"))
    }

    @Test
    fun `oversized descriptor is rejected before kernel snapshot retention`() {
        val dependencies = (0..KernelResourceBounds.MAX_DEPENDENCIES_PER_MODULE)
            .mapTo(linkedSetOf()) { index -> ModuleDependency.required("precopy$index") }
        val candidate = module("precopy", dependencies = dependencies)
        val kernel = ToolBoxKernel()

        val result = kernel.install(candidate)

        assertFalse(result.isSuccess)
        val error = result.errors.single()
        assertEquals(KernelErrorCode.INVALID_DESCRIPTOR, error.code)
        assertNull(kernel.moduleState("precopy"))
        assertTrue(error.cause?.message?.contains("too many dependencies") == true)
    }

    @Test
    fun `oversized descriptor strings are rejected before snapshot retention`() {
        val descriptor = ModuleDescriptor(
            id = "bounded",
            name = "n".repeat(KernelResourceBounds.MAX_MODULE_NAME_LENGTH + 1),
            version = "1.0.0"
        )
        val candidate = object : ToolBoxModule {
            override val descriptor: ModuleDescriptor = descriptor
        }
        val kernel = ToolBoxKernel()

        val result = kernel.install(candidate)

        assertFalse(result.isSuccess)
        val error = result.errors.single()
        assertEquals(KernelErrorCode.INVALID_DESCRIPTOR, error.code)
        assertNull(kernel.moduleState("bounded"))
        assertTrue(error.cause?.message?.contains("name is too long") == true)
    }

    @Test
    fun `identifier length is bounded before identifier regex`() {
        val oversized = "a".repeat(KernelResourceBounds.MAX_IDENTIFIER_LENGTH + 1)

        val error = assertFailsWith<IllegalArgumentException> {
            ModuleDependency.required(oversized)
        }

        assertTrue(error.message?.contains("Dependency id is invalid") == true)
    }

    @Test
    fun `registry installed module capacity is finite`() {
        val registry = ModuleRegistry { }
        repeat(KernelResourceBounds.MAX_INSTALLED_MODULES) { index ->
            val candidate = module("module$index")
            registry.register(candidate, candidate.descriptor)
        }

        val overflow = module("moduleoverflow")
        val error = assertFailsWith<IllegalStateException> {
            registry.register(overflow, overflow.descriptor)
        }

        assertTrue(error.message?.contains("capacity exhausted") == true)
        assertEquals(KernelResourceBounds.MAX_INSTALLED_MODULES, registry.descriptors().size)
    }

    @Test
    fun `dependency chain at depth bound remains resolvable`() {
        val registry = ModuleRegistry { }
        repeat(KernelResourceBounds.MAX_RESOLUTION_DEPTH) { index ->
            val dependencies = if (index + 1 < KernelResourceBounds.MAX_RESOLUTION_DEPTH) {
                setOf(ModuleDependency.required("depth${index + 1}"))
            } else {
                emptySet()
            }
            val candidate = module("depth$index", dependencies = dependencies)
            registry.register(candidate, candidate.descriptor)
        }

        val result = registry.resolvePlanFor("depth0")

        assertTrue(result.isSuccess)
        assertEquals(KernelResourceBounds.MAX_RESOLUTION_DEPTH, result.value?.order?.size)
    }

    @Test
    fun `dependency chain beyond depth bound fails structurally`() {
        val registry = ModuleRegistry { }
        repeat(KernelResourceBounds.MAX_RESOLUTION_DEPTH + 1) { index ->
            val dependencies = if (index < KernelResourceBounds.MAX_RESOLUTION_DEPTH) {
                setOf(ModuleDependency.required("too-deep${index + 1}"))
            } else {
                emptySet()
            }
            val candidate = module("too-deep$index", dependencies = dependencies)
            registry.register(candidate, candidate.descriptor)
        }

        val result = registry.resolvePlanFor("too-deep0")

        assertFalse(result.isSuccess)
        assertEquals(KernelErrorCode.DEPENDENCY_RESOLUTION, result.errors.single().code)
        assertTrue(result.errors.single().message.contains("depth limit"))
    }

    @Test
    fun `resolution work budget fails closed when exhausted`() {
        val budget = ResolutionBudget(2)

        assertNull(budget.consume("first"))
        assertNull(budget.consume("second"))
        val error = assertNotNull(budget.consume("third"))

        assertEquals(KernelErrorCode.DEPENDENCY_RESOLUTION, error.code)
        assertTrue(error.message.contains("work budget exhausted"))
    }
}
