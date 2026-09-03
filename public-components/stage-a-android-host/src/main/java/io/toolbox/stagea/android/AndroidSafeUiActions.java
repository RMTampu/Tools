package io.toolbox.stagea.android;

import io.toolbox.contracts.safety.SafetyContracts;
import io.toolbox.stagea.SafeUiActionPolicy;

import java.util.Objects;

/** Production Safe-UI action adapter. Private integration may wire this, not reimplement it. */
public final class AndroidSafeUiActions implements AndroidSafeUi.Actions {
    private final AndroidStageAHost host;

    public AndroidSafeUiActions(AndroidStageAHost host) {
        this.host = Objects.requireNonNull(host, "host");
        this.host.bootstrap();
    }

    @Override
    public String verifyIntegrity() {
        return SafeUiActionPolicy.inspection(host.health());
    }

    @Override
    public String retryBootstrap() {
        return SafeUiActionPolicy.bootstrapState(host.bootstrap());
    }

    @Override
    public String enterReadOnly() {
        SafetyContracts.RecoveryState state = host.recoveryCoordinator().state();
        return SafeUiActionPolicy.restrictedModeStatus(state);
    }

    @Override
    public String exportSanitizedDiagnostics() {
        return SafeUiActionPolicy.sanitizedDiagnostics(host.diagnostics());
    }

    @Override
    public boolean canRestoreKnownGood() {
        return SafeUiActionPolicy.canRestoreKnownGood();
    }

    @Override
    public String restoreKnownGood() {
        return SafeUiActionPolicy.restoreUnavailable();
    }

    @Override
    public boolean canQuarantine() {
        return SafeUiActionPolicy.canQuarantine(host.recoveryCoordinator().state());
    }

    @Override
    public String quarantine() {
        SafetyContracts.RecoveryState current = host.recoveryCoordinator().state();
        if (!SafeUiActionPolicy.canQuarantine(current)) {
            return SafeUiActionPolicy.restrictedModeStatus(current);
        }
        SafetyContracts.Transition transition = host.recoveryCoordinator().apply(SafetyContracts.RecoveryEvent.FATAL_FAILURE);
        return SafeUiActionPolicy.quarantineResult(transition);
    }
}
