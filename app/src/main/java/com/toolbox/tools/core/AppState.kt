package com.toolbox.tools.core

enum class AppState {
    CREATED,
    INITIALIZING,
    READY,
    ERROR
}

object StateController {
    var state: AppState = AppState.CREATED
        private set

    fun initialize() {
        state = AppState.INITIALIZING
        state = AppState.READY
    }

    fun fail() {
        state = AppState.ERROR
    }
}
