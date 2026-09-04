package com.toolbox.tools.repair;

public enum RepairPhase {
    NEW,
    STAGED,
    ACTIVATED,
    VERIFIED,
    ROLLED_BACK,
    FAILED_SAFE;

    public boolean terminal() {
        return this == VERIFIED || this == ROLLED_BACK || this == FAILED_SAFE;
    }
}
