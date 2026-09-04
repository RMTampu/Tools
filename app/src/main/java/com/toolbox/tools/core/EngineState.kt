package com.toolbox.tools.core

sealed class EngineState {
    object Created : EngineState()
    object Running : EngineState()
    object Stopped : EngineState()
    object Recovery : EngineState()
}
