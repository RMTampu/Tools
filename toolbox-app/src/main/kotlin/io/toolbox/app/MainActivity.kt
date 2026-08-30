package io.toolbox.app

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import io.toolbox.kernel.KernelState

class MainActivity : Activity() {
    companion object {
        const val STATUS_VIEW_ID: Int = 0x00F00001
        private const val STATE_GENERATION = "toolbox.screen.generation"
    }

    private var generation: Int = 0

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        generation = (savedInstanceState?.getInt(STATE_GENERATION) ?: -1) + 1

        val app = application as ToolBoxApplication
        val compatibility = app.compatibility
        val snapshot = KernelRuntime.snapshotOrNull()

        val status = when {
            !compatibility.supported -> "UNSUPPORTED: ${compatibility.reason}"
            snapshot == null -> "FAILED: kernel unavailable"
            snapshot.state != KernelState.RUNNING -> "FAILED: kernel state ${snapshot.state}"
            else -> "RUNNING: ${snapshot.config.name} ${snapshot.config.version}"
        }

        val statusView = TextView(this).apply {
            id = STATUS_VIEW_ID
            text = "ToolBox\n$status\nAndroid ${android.os.Build.VERSION.SDK_INT} / ${android.os.Build.SUPPORTED_ABIS.joinToString()}\nGeneration $generation"
            contentDescription = "ToolBox status $status generation $generation"
            gravity = Gravity.CENTER
            textSize = 18f
            setPadding(48, 48, 48, 48)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        setContentView(statusView)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_GENERATION, generation)
        super.onSaveInstanceState(outState)
    }
}
