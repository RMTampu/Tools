package io.toolbox.stagea;

import io.toolbox.contracts.safety.RecoveryMachine;
import io.toolbox.contracts.safety.SafetyContracts;

import java.util.Objects;

public final class RecoveryCoordinator {
    private final StageAContracts.RecoveryStateStore store;
    private SafetyContracts.RecoveryState state;
    private boolean bootstrapped;
    public RecoveryCoordinator(StageAContracts.RecoveryStateStore store) { this.store = Objects.requireNonNull(store, "store"); }
    public synchronized SafetyContracts.RecoveryState bootstrap() {
        if (bootstrapped) return state;
        try {
            SafetyContracts.RecoveryState loaded = store.load();
            if (loaded == null) throw new StageAContracts.StageAException("recovery.state.invalid", "Recovery state store returned null");
            state = loaded;
        } catch (RuntimeException failure) {
            state = SafetyContracts.RecoveryState.QUARANTINED;
        }
        bootstrapped = true;
        return state;
    }
    public synchronized SafetyContracts.RecoveryState state() { requireBootstrapped(); return state; }
    public synchronized SafetyContracts.Transition apply(SafetyContracts.RecoveryEvent event) {
        requireBootstrapped();
        Objects.requireNonNull(event, "event");
        RecoveryMachine candidate = new RecoveryMachine(state);
        SafetyContracts.Transition transition = candidate.apply(event);
        try {
            store.save(transition.next());
        } catch (RuntimeException failure) {
            state = SafetyContracts.RecoveryState.QUARANTINED;
            throw new StageAContracts.StageAException("recovery.state.persist.failed", "Recovery state persistence failed", failure);
        }
        state = transition.next();
        return transition;
    }
    private void requireBootstrapped() {
        if (!bootstrapped) throw new StageAContracts.StageAException("recovery.bootstrap.required", "Recovery coordinator must bootstrap before use");
    }
}
