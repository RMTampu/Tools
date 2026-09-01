package io.toolbox.kernel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Independent bounded reference model for the public kernel/module lifecycle.
 *
 * The model intentionally does not call implementation transition helpers. It defines the
 * expected stable-state semantics separately, then compares every prefix of every operation
 * sequence of length five (4^5 = 1,024 complete sequences) against ToolBoxKernel.
 */
class KernelReferenceModelTest {
    private enum class Op {
        INSTALL,
        UNINSTALL,
        START,
        STOP
    }

    private enum class RefKernelState {
        NEW,
        RUNNING,
        STOPPED
    }

    private enum class RefModuleState {
        ABSENT,
        REGISTERED,
        STARTED,
        STOPPED
    }

    private enum class Outcome {
        INSTALL_OK,
        INSTALL_REJECTED,
        UNINSTALL_TRUE,
        UNINSTALL_FALSE,
        START_OK,
        START_ILLEGAL,
        STOP_OK
    }

    private data class ReferenceState(
        var kernel: RefKernelState = RefKernelState.NEW,
        var module: RefModuleState = RefModuleState.ABSENT
    ) {
        fun apply(op: Op): Outcome = when (op) {
            Op.INSTALL -> {
                if (module != RefModuleState.ABSENT) {
                    Outcome.INSTALL_REJECTED
                } else {
                    module = if (kernel == RefKernelState.RUNNING) {
                        RefModuleState.STARTED
                    } else {
                        RefModuleState.REGISTERED
                    }
                    Outcome.INSTALL_OK
                }
            }

            Op.UNINSTALL -> {
                if (module == RefModuleState.ABSENT) {
                    Outcome.UNINSTALL_FALSE
                } else {
                    module = RefModuleState.ABSENT
                    Outcome.UNINSTALL_TRUE
                }
            }

            Op.START -> {
                if (kernel == RefKernelState.RUNNING) {
                    Outcome.START_ILLEGAL
                } else {
                    kernel = RefKernelState.RUNNING
                    module = when (module) {
                        RefModuleState.REGISTERED,
                        RefModuleState.STOPPED -> RefModuleState.STARTED
                        else -> module
                    }
                    Outcome.START_OK
                }
            }

            Op.STOP -> {
                kernel = RefKernelState.STOPPED
                if (module == RefModuleState.STARTED) {
                    module = RefModuleState.STOPPED
                }
                Outcome.STOP_OK
            }
        }
    }

    @Test
    fun `R1 R3 bounded public lifecycle reference model agrees with implementation`() {
        val operations = Op.entries
        val sequence = ArrayList<Op>(SEQUENCE_LENGTH)
        var completeSequences = 0
        var checkedPrefixes = 0

        fun enumerate(depth: Int) {
            if (depth == SEQUENCE_LENGTH) {
                verifySequence(sequence)
                completeSequences += 1
                checkedPrefixes += SEQUENCE_LENGTH
                return
            }
            operations.forEach { operation ->
                sequence += operation
                enumerate(depth + 1)
                sequence.removeAt(sequence.lastIndex)
            }
        }

        enumerate(0)

        assertEquals(1_024, completeSequences)
        assertEquals(5_120, checkedPrefixes)
    }

    @Test
    fun `R3 failure and recovery transition table agrees with kernel contract`() {
        val startFailKernel = ToolBoxKernel()
        val startFailModule = object : ToolBoxModule {
            override val descriptor = descriptor("engine.model-start-fail")

            override fun onStart() {
                error("modelled start failure")
            }
        }
        assertTrue(startFailKernel.install(startFailModule).isEmpty())
        val startFailures = startFailKernel.start()
        assertTrue(startFailures.any { it.phase == "start" })
        assertEquals(KernelState.DEGRADED, startFailKernel.state)
        assertEquals(ModuleState.FAILED, startFailKernel.modules.stateOf(startFailModule.descriptor.id))

        val stopFailKernel = ToolBoxKernel()
        val stopFailModule = object : ToolBoxModule {
            override val descriptor = descriptor("engine.model-stop-fail")

            override fun onStop() {
                error("modelled stop failure")
            }
        }
        assertTrue(stopFailKernel.install(stopFailModule).isEmpty())
        assertTrue(stopFailKernel.start().isEmpty())
        val stopFailures = stopFailKernel.stop()
        assertTrue(stopFailures.any { it.phase == "stop" })
        assertEquals(KernelState.FAILED, stopFailKernel.state)
        assertEquals(ModuleState.FAILED, stopFailKernel.modules.stateOf(stopFailModule.descriptor.id))

        val store = InMemoryKernelStateStore().also {
            it.put("kernel.state", KernelState.RUNNING.name)
        }
        val recoveredKernel = ToolBoxKernel(ports = KernelPorts(stateStore = store))
        assertEquals(KernelState.FAILED, recoveredKernel.state)
        assertTrue(recoveredKernel.install(object : ToolBoxModule {
            override val descriptor = descriptor("engine.model-recovery")
        }).isEmpty())
        assertTrue(recoveredKernel.start().isEmpty())
        assertEquals(KernelState.RUNNING, recoveredKernel.state)
    }

    private fun verifySequence(sequence: List<Op>) {
        val reference = ReferenceState()
        val kernel = ToolBoxKernel()
        val module = object : ToolBoxModule {
            override val descriptor = descriptor(MODULE_ID)
        }

        sequence.forEachIndexed { index, operation ->
            val expectedOutcome = reference.apply(operation)
            val actualOutcome = execute(kernel, module, operation)
            val prefix = sequence.take(index + 1).joinToString(" -> ")

            assertEquals(expectedOutcome, actualOutcome, "outcome mismatch after $prefix")
            assertEquals(
                reference.kernel.name,
                kernel.state.name,
                "kernel state mismatch after $prefix"
            )
            assertEquals(
                expectedModuleState(reference.module),
                kernel.modules.stateOf(MODULE_ID),
                "module state mismatch after $prefix"
            )
        }
    }

    private fun execute(kernel: ToolBoxKernel, module: ToolBoxModule, operation: Op): Outcome =
        when (operation) {
            Op.INSTALL -> {
                val failures = kernel.install(module)
                if (failures.isEmpty()) Outcome.INSTALL_OK else Outcome.INSTALL_REJECTED
            }

            Op.UNINSTALL -> {
                if (kernel.uninstall(MODULE_ID)) Outcome.UNINSTALL_TRUE else Outcome.UNINSTALL_FALSE
            }

            Op.START -> {
                val result = runCatching { kernel.start() }
                if (result.isFailure) {
                    Outcome.START_ILLEGAL
                } else {
                    assertTrue(result.getOrThrow().isEmpty())
                    Outcome.START_OK
                }
            }

            Op.STOP -> {
                val result = runCatching { kernel.stop() }
                assertTrue(result.isSuccess, "healthy reference sequence must allow stop")
                assertTrue(result.getOrThrow().isEmpty())
                Outcome.STOP_OK
            }
        }

    private fun expectedModuleState(state: RefModuleState): ModuleState? = when (state) {
        RefModuleState.ABSENT -> null
        RefModuleState.REGISTERED -> ModuleState.REGISTERED
        RefModuleState.STARTED -> ModuleState.STARTED
        RefModuleState.STOPPED -> ModuleState.STOPPED
    }

    private fun descriptor(id: String) = ModuleDescriptor(
        id = id,
        name = id,
        version = "1.0.0"
    )

    private companion object {
        const val MODULE_ID = "engine.reference-model"
        const val SEQUENCE_LENGTH = 5
    }
}
