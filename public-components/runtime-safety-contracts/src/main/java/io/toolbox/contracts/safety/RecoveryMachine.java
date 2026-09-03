package io.toolbox.contracts.safety;

import java.util.Objects;

/**
 * Small fail-closed recovery state machine. It models recovery intent only and
 * owns no durable checkpoint, file restore, process restart, or Android lifecycle action.
 */
public final class RecoveryMachine {
    private SafetyContracts.RecoveryState state;

    public RecoveryMachine() {
        this(SafetyContracts.RecoveryState.NORMAL);
    }

    public RecoveryMachine(SafetyContracts.RecoveryState initialState) {
        this.state = Objects.requireNonNull(initialState, "initialState");
    }

    public synchronized SafetyContracts.RecoveryState state() {
        return state;
    }

    public synchronized SafetyContracts.Transition apply(SafetyContracts.RecoveryEvent event) {
        Objects.requireNonNull(event, "event");
        SafetyContracts.RecoveryState previous = state;
        SafetyContracts.RecoveryState next = transition(previous, event);
        state = next;
        return new SafetyContracts.Transition(previous, event, next);
    }

    static SafetyContracts.RecoveryState transition(
            SafetyContracts.RecoveryState state,
            SafetyContracts.RecoveryEvent event
    ) {
        if (state == SafetyContracts.RecoveryState.QUARANTINED) {
            return SafetyContracts.RecoveryState.QUARANTINED;
        }

        switch (state) {
            case NORMAL:
                switch (event) {
                    case RESOURCE_PRESSURE: return SafetyContracts.RecoveryState.DEGRADED;
                    case RESOURCE_NORMAL: return SafetyContracts.RecoveryState.NORMAL;
                    case FAILURE_REQUIRES_RECOVERY: return SafetyContracts.RecoveryState.RECOVERY_REQUIRED;
                    case FATAL_FAILURE: return SafetyContracts.RecoveryState.QUARANTINED;
                    default: return illegal(state, event);
                }
            case DEGRADED:
                switch (event) {
                    case RESOURCE_PRESSURE: return SafetyContracts.RecoveryState.DEGRADED;
                    case RESOURCE_NORMAL: return SafetyContracts.RecoveryState.NORMAL;
                    case FAILURE_REQUIRES_RECOVERY: return SafetyContracts.RecoveryState.RECOVERY_REQUIRED;
                    case ENTER_SAFE_MODE: return SafetyContracts.RecoveryState.SAFE_MODE;
                    case FATAL_FAILURE: return SafetyContracts.RecoveryState.QUARANTINED;
                    default: return illegal(state, event);
                }
            case RECOVERY_REQUIRED:
                switch (event) {
                    case RESOURCE_PRESSURE:
                    case RESOURCE_NORMAL:
                    case FAILURE_REQUIRES_RECOVERY:
                        return SafetyContracts.RecoveryState.RECOVERY_REQUIRED;
                    case ENTER_SAFE_MODE: return SafetyContracts.RecoveryState.SAFE_MODE;
                    case RECOVERY_SUCCEEDED: return SafetyContracts.RecoveryState.NORMAL;
                    case RECOVERY_FAILED:
                    case FATAL_FAILURE:
                        return SafetyContracts.RecoveryState.QUARANTINED;
                    default: return illegal(state, event);
                }
            case SAFE_MODE:
                switch (event) {
                    case RESOURCE_PRESSURE:
                    case RESOURCE_NORMAL:
                    case FAILURE_REQUIRES_RECOVERY:
                    case ENTER_SAFE_MODE:
                        return SafetyContracts.RecoveryState.SAFE_MODE;
                    case RECOVERY_SUCCEEDED: return SafetyContracts.RecoveryState.NORMAL;
                    case RECOVERY_FAILED:
                    case FATAL_FAILURE:
                        return SafetyContracts.RecoveryState.QUARANTINED;
                    default: return illegal(state, event);
                }
            default:
                return illegal(state, event);
        }
    }

    private static SafetyContracts.RecoveryState illegal(
            SafetyContracts.RecoveryState state,
            SafetyContracts.RecoveryEvent event
    ) {
        throw new SafetyContracts.ContractException(
                "ILLEGAL_TRANSITION",
                "illegal recovery transition state=" + state + " event=" + event
        );
    }
}
