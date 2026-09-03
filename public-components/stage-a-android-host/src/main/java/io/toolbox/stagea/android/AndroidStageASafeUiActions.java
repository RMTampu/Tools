package io.toolbox.stagea.android;

import io.toolbox.contracts.safety.SafetyContracts;
import io.toolbox.stagea.StageAContracts;

import java.util.Objects;

/**
 * Production Safe-UI actions for Stage A. The implementation intentionally uses
 * only Stage-A public runtime primitives and returns bounded, non-sensitive status
 * text. It does not expose raw diagnostics, private paths, secrets, or receiver
 * implementation details.
 */
public final class AndroidStageASafeUiActions implements AndroidSafeUi.Actions {
    private final AndroidStageAHost host;

    public AndroidStageASafeUiActions(AndroidStageAHost host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    @Override
    public String verifyIntegrity() {
        StageAContracts.HealthSnapshot health = host.health();
        return "Pemeriksaan selesai: status=" + health.state().name()
                + ", recovery=" + health.recoveryState().name()
                + ", registry=" + health.registryEntries()
                + ", diagnostics=" + health.diagnosticCount() + ".";
    }

    @Override
    public String retryBootstrap() {
        SafetyContracts.RecoveryState state = host.bootstrap();
        return "Bootstrap diperiksa ulang: " + state.name() + ".";
    }

    @Override
    public String enterReadOnly() {
        StageAContracts.SafeUiModel model = host.safeUiModel();
        if (!model.visible() || !model.restricted()) {
            return "Mode baca-saja tidak diperlukan pada state saat ini.";
        }
        return "Mode aman tetap membatasi jalur eksekusi normal; akses baca-saja dipertahankan.";
    }

    @Override
    public String exportSanitizedDiagnostics() {
        StageAContracts.HealthSnapshot health = host.health();
        return "Diagnostik aman: count=" + health.diagnosticCount()
                + ", dropped=" + health.droppedDiagnostics()
                + ", guard=" + health.guardMode().name() + ".";
    }

    @Override
    public boolean canRestoreKnownGood() {
        return false;
    }

    @Override
    public String restoreKnownGood() {
        return "Pemulihan known-good belum tersedia pada Stage A.";
    }

    @Override
    public boolean canQuarantine() {
        return host.recoveryCoordinator().state() != SafetyContracts.RecoveryState.QUARANTINED;
    }

    @Override
    public String quarantine() {
        SafetyContracts.RecoveryState current = host.recoveryCoordinator().state();
        if (current == SafetyContracts.RecoveryState.QUARANTINED) {
            return "Runtime sudah berada dalam karantina.";
        }
        SafetyContracts.Transition transition = host.recoveryCoordinator()
                .apply(SafetyContracts.RecoveryEvent.FATAL_FAILURE);
        if (transition.next() != SafetyContracts.RecoveryState.QUARANTINED) {
            throw new StageAContracts.StageAException(
                    "safe.ui.quarantine.failed",
                    "Quarantine action did not reach terminal quarantine state"
            );
        }
        return "Runtime dikarantina; jalur eksekusi normal tetap diblokir.";
    }
}
