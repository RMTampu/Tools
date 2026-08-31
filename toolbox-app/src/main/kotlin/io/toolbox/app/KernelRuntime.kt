package io.toolbox.app

import io.toolbox.kernel.KernelSnapshot
import io.toolbox.kernel.KernelState
import io.toolbox.kernel.ToolBoxKernel

object KernelRuntime {
    private val lock = Any()

    @Volatile
    private var kernel: ToolBoxKernel? = null

    fun startIfNeeded(): KernelSnapshot = synchronized(lock) {
        val existing = kernel
        if (existing != null && existing.state in setOf(KernelState.RUNNING, KernelState.DEGRADED)) {
            return@synchronized existing.snapshot()
        }

        val created = ToolBoxKernel()
        val result = created.start()
        check(result.isSuccess) {
            val errors = result.errors.joinToString(separator = "; ") { "${it.code}:${it.message}" }
            val failures = result.failures.joinToString(separator = "; ") { "${it.moduleId}:${it.phase}" }
            "Kernel startup failed: errors=[$errors], failures=[$failures]"
        }
        check(created.state == KernelState.RUNNING) {
            "Kernel entered unexpected state ${created.state}"
        }
        kernel = created
        created.snapshot()
    }

    fun snapshotOrNull(): KernelSnapshot? = kernel?.snapshot()

    fun stopForTest(): KernelSnapshot? = synchronized(lock) {
        val current = kernel ?: return@synchronized null
        current.stop()
        val snapshot = current.snapshot()
        kernel = null
        snapshot
    }
}
