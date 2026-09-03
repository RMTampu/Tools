package io.toolbox.stagea;

import io.toolbox.contracts.safety.SafetyContracts;

import java.util.Objects;

public final class SafeUiPolicy {
    private SafeUiPolicy() {}
    public static StageAContracts.SafeUiModel modelFor(SafetyContracts.RecoveryState state) {
        Objects.requireNonNull(state, "state");
        switch (state) {
            case NORMAL: return new StageAContracts.SafeUiModel(false, false, "safe.ui.hidden", state);
            case DEGRADED: return new StageAContracts.SafeUiModel(false, false, "safe.ui.degraded", state);
            case RECOVERY_REQUIRED: return new StageAContracts.SafeUiModel(true, true, "safe.ui.recovery.required", state);
            case SAFE_MODE: return new StageAContracts.SafeUiModel(true, true, "safe.ui.active", state);
            case QUARANTINED: return new StageAContracts.SafeUiModel(true, true, "safe.ui.quarantined", state);
            default: throw new StageAContracts.StageAException("safe.ui.state.unknown", "Unsupported recovery state");
        }
    }
}
