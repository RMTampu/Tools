package io.toolbox.stagea.android;

import io.toolbox.contracts.safety.SafetyContracts;
import io.toolbox.stagea.StageAContracts;

public final class AndroidRecoveryStateStore implements StageAContracts.RecoveryStateStore {
    public static final String RECOVERY_KEY = "stage.a.recovery.state";
    private final AndroidAtomicStateStore store;

    public AndroidRecoveryStateStore(AndroidAtomicStateStore store) {
        if (store == null) throw new NullPointerException("store");
        this.store = store;
    }

    @Override
    public SafetyContracts.RecoveryState load() {
        String raw = store.get(RECOVERY_KEY);
        if (raw == null) return SafetyContracts.RecoveryState.NORMAL;
        try {
            return SafetyContracts.RecoveryState.valueOf(raw);
        } catch (IllegalArgumentException failure) {
            throw new StageAContracts.StageAException("recovery.state.corrupt", "Recovery state is invalid", failure);
        }
    }

    @Override
    public void save(SafetyContracts.RecoveryState state) {
        if (state == null) throw new NullPointerException("state");
        store.put(RECOVERY_KEY, state.name());
    }
}
