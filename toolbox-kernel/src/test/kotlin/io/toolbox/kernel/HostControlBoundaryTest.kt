package io.toolbox.kernel

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.AbstractMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HostControlBoundaryTest {
    @Test
    fun `module descriptor timeout is structured and does not register late`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val candidate = object : ToolBoxModule {
            override val descriptor: ModuleDescriptor
                get() {
                    entered.countDown()
                    blockUntil(release)
                    return ModuleDescriptor("slowdescriptor", "slowdescriptor", "1.0.0")
                }
        }
        val kernel = ToolBoxKernel(KernelConfig(lifecycleTimeoutMillis = 50))

        val result = runBeforeRelease(entered, release) { kernel.install(candidate) }
        try {
            assertFalse(result.isSuccess)
            assertEquals(KernelErrorCode.INVALID_DESCRIPTOR, result.errors.single().code)
            assertNull(kernel.moduleState("slowdescriptor"))
            assertEquals(KernelState.NEW, kernel.state)
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `module source snapshot timeout is structured before staging`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var stagedCalled = false
        val metadata = object : AbstractMap<String, String>() {
            override val entries: Set<Map.Entry<String, String>>
                get() {
                    entered.countDown()
                    blockUntil(release)
                    return emptySet()
                }
        }
        val source = ModuleSource("external", "package.zip", metadata)
        val ports = authoritativePorts(
            stager = ModuleSourceStager { candidate ->
                stagedCalled = true
                staged(candidate)
            }
        )
        val kernel = ToolBoxKernel(KernelConfig(lifecycleTimeoutMillis = 50), ports)

        val result = runBeforeRelease(entered, release) { kernel.install(source, passiveLoader()) }
        try {
            assertFalse(result.isSuccess)
            assertEquals(KernelErrorCode.INVALID_DESCRIPTOR, result.errors.single().code)
            assertFalse(stagedCalled)
            assertNull(kernel.moduleState("external"))
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `compatibility policy timeout fails closed before registration`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val policy = CompatibilityPolicy { _, _, _ ->
            entered.countDown()
            blockUntil(release)
            CompatibilityResult(true)
        }
        val kernel = ToolBoxKernel(
            KernelConfig(lifecycleTimeoutMillis = 50),
            KernelPorts(compatibilityPolicy = policy)
        )

        val result = runBeforeRelease(entered, release) { kernel.install(module("policyslow")) }
        try {
            assertFalse(result.isSuccess)
            assertEquals(KernelErrorCode.POLICY_FAILURE, result.errors.single().code)
            assertNull(kernel.moduleState("policyslow"))
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `admission policy timeout fails closed before registration`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val policy = ModuleAdmissionPolicy { _, _, _ ->
            entered.countDown()
            blockUntil(release)
            AdmissionDecision(true)
        }
        val kernel = ToolBoxKernel(
            KernelConfig(lifecycleTimeoutMillis = 50),
            KernelPorts(admissionPolicy = policy)
        )

        val result = runBeforeRelease(entered, release) { kernel.install(module("admissionslow")) }
        try {
            assertFalse(result.isSuccess)
            assertEquals(KernelErrorCode.POLICY_FAILURE, result.errors.single().code)
            assertNull(kernel.moduleState("admissionslow"))
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `source stager timeout prevents inspection`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var inspectCalled = false
        val ports = authoritativePorts(
            stager = ModuleSourceStager { source ->
                entered.countDown()
                blockUntil(release)
                staged(source)
            }
        )
        val loader = object : ModuleLoader {
            override fun inspect(source: StagedModuleSource): ModuleDescriptor {
                inspectCalled = true
                return ModuleDescriptor("external", "external", "1.0.0")
            }

            override fun load(source: VerifiedModuleSource, descriptor: ModuleDescriptor): ToolBoxModule =
                module("external")
        }
        val kernel = ToolBoxKernel(KernelConfig(lifecycleTimeoutMillis = 50), ports)

        val result = runBeforeRelease(entered, release) {
            kernel.install(ModuleSource("external", "package.zip"), loader)
        }
        try {
            assertFalse(result.isSuccess)
            assertEquals(KernelErrorCode.SOURCE_STAGING, result.errors.single().code)
            assertFalse(inspectCalled)
            assertNull(kernel.moduleState("external"))
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `source inspection timeout prevents verification and loading`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var verifierCalled = false
        var loadCalled = false
        val ports = authoritativePorts(
            verifier = ModuleSourceVerifier { _, _ ->
                verifierCalled = true
                SourceVerificationResult(true, "hash", algorithm = "SHA-256", policyId = "test")
            }
        )
        val loader = object : ModuleLoader {
            override fun inspect(source: StagedModuleSource): ModuleDescriptor {
                entered.countDown()
                blockUntil(release)
                return ModuleDescriptor("external", "external", "1.0.0")
            }

            override fun load(source: VerifiedModuleSource, descriptor: ModuleDescriptor): ToolBoxModule {
                loadCalled = true
                return module("external")
            }
        }
        val kernel = ToolBoxKernel(KernelConfig(lifecycleTimeoutMillis = 50), ports)

        val result = runBeforeRelease(entered, release) {
            kernel.install(ModuleSource("external", "package.zip"), loader)
        }
        try {
            assertFalse(result.isSuccess)
            assertEquals(KernelErrorCode.SOURCE_INSPECTION, result.errors.single().code)
            assertFalse(verifierCalled)
            assertFalse(loadCalled)
            assertNull(kernel.moduleState("external"))
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `source verifier timeout prevents executable loading`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var loadCalled = false
        val ports = authoritativePorts(
            verifier = ModuleSourceVerifier { _, _ ->
                entered.countDown()
                blockUntil(release)
                SourceVerificationResult(true, "hash", algorithm = "SHA-256", policyId = "test")
            }
        )
        val loader = object : ModuleLoader {
            override fun inspect(source: StagedModuleSource): ModuleDescriptor =
                ModuleDescriptor("external", "external", "1.0.0")

            override fun load(source: VerifiedModuleSource, descriptor: ModuleDescriptor): ToolBoxModule {
                loadCalled = true
                return module("external")
            }
        }
        val kernel = ToolBoxKernel(KernelConfig(lifecycleTimeoutMillis = 50), ports)

        val result = runBeforeRelease(entered, release) {
            kernel.install(ModuleSource("external", "package.zip"), loader)
        }
        try {
            assertFalse(result.isSuccess)
            assertEquals(KernelErrorCode.SOURCE_VERIFICATION, result.errors.single().code)
            assertFalse(loadCalled)
            assertNull(kernel.moduleState("external"))
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `source loader timeout cannot register module after caller has failed`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val loader = object : ModuleLoader {
            override fun inspect(source: StagedModuleSource): ModuleDescriptor =
                ModuleDescriptor("external", "external", "1.0.0")

            override fun load(source: VerifiedModuleSource, descriptor: ModuleDescriptor): ToolBoxModule {
                entered.countDown()
                blockUntil(release)
                return module("external")
            }
        }
        val kernel = ToolBoxKernel(
            KernelConfig(lifecycleTimeoutMillis = 50),
            authoritativePorts()
        )

        val result = runBeforeRelease(entered, release) {
            kernel.install(ModuleSource("external", "package.zip"), loader)
        }
        try {
            assertFalse(result.isSuccess)
            assertEquals(KernelErrorCode.SOURCE_LOAD, result.errors.single().code)
            assertNull(kernel.moduleState("external"))
        } finally {
            release.countDown()
        }
        Thread.sleep(50)
        assertNull(kernel.moduleState("external"))
    }

    @Test
    fun `loaded descriptor timeout cannot register module`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val descriptor = ModuleDescriptor("external", "external", "1.0.0")
        val loaded = object : ToolBoxModule {
            override val descriptor: ModuleDescriptor
                get() {
                    entered.countDown()
                    blockUntil(release)
                    return descriptor
                }
        }
        val loader = object : ModuleLoader {
            override fun inspect(source: StagedModuleSource): ModuleDescriptor = descriptor
            override fun load(source: VerifiedModuleSource, descriptor: ModuleDescriptor): ToolBoxModule = loaded
        }
        val kernel = ToolBoxKernel(
            KernelConfig(lifecycleTimeoutMillis = 50),
            authoritativePorts()
        )

        val result = runBeforeRelease(entered, release) {
            kernel.install(ModuleSource("external", "package.zip"), loader)
        }
        try {
            assertFalse(result.isSuccess)
            assertEquals(KernelErrorCode.INVALID_DESCRIPTOR, result.errors.single().code)
            assertNull(kernel.moduleState("external"))
        } finally {
            release.countDown()
        }
    }

    private fun passiveLoader(): ModuleLoader = object : ModuleLoader {
        override fun inspect(source: StagedModuleSource): ModuleDescriptor =
            ModuleDescriptor("external", "external", "1.0.0")

        override fun load(source: VerifiedModuleSource, descriptor: ModuleDescriptor): ToolBoxModule =
            module("external")
    }

    private fun <T> runBeforeRelease(
        entered: CountDownLatch,
        release: CountDownLatch,
        action: () -> T
    ): T {
        val result = AtomicReference<T?>()
        val caller = Thread { result.set(action()) }
        caller.start()
        assertTrue(entered.await(1, TimeUnit.SECONDS), "Host call did not enter")
        caller.join(1_000)
        val completedBeforeRelease = !caller.isAlive
        if (!completedBeforeRelease) {
            release.countDown()
            caller.join(1_000)
        }
        assertTrue(completedBeforeRelease, "Kernel operation did not return before blocked host call was released")
        return requireNotNull(result.get())
    }

    private fun blockUntil(release: CountDownLatch): Unit {
        while (release.count > 0) {
            try {
                release.await(1, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                // Simulate a non-cooperative same-process host implementation.
            }
        }
    }
}
