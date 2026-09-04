package com.toolbox.tools.core

class RecoveryManager {
    private var recoveryRequired = false

    fun markRequired() {
        recoveryRequired = true
    }

    fun clear() {
        recoveryRequired = false
    }

    fun isRequired(): Boolean = recoveryRequired
}
