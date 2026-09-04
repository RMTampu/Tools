package com.toolbox.tools.core;

public final class RecoveryManager {
    private boolean recoveryRequired;

    public synchronized void markRecoveryRequired() {
        recoveryRequired = true;
    }

    public synchronized void clearRecoveryRequired() {
        recoveryRequired = false;
    }

    public synchronized boolean isRecoveryRequired() {
        return recoveryRequired;
    }
}
