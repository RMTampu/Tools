package io.toolbox.stagea.android;

import io.toolbox.contracts.safety.SafetyContracts;
import io.toolbox.stagea.StageAContracts;

import java.util.Objects;

/**
 * Production Safe-UI actions for Stage A. The implementation is deliberately
 * fail-closed: it exposes only Stage-A behavior that already exists and never
 * invents restore/read-write capabilities that are outside the Stage-A scope.
 */
public final class AndroidStageASafeUiActions implements AndroidSafeUi.Actions {
    private final AndroidStageAHost host;

    public AndroidStageASafeUiActions(AndroidStageAHost host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    @Override
    public String verifyIntegrity() {
        StageAContracts.HealthSnapshot health = host.health();
        switch (health.state()) {
            case HEALTHY:
                return "Kondisi runtime aman.";
            case DEGRADED:
                return "Runtime berjalan terbatas; pemeriksaan lanjutan diperlukan.";
            case UNHEALTHY:
                return "Runtime tidak sehat dan tetap dibatasi.";
            case BLOCKED:
            default:
                return "Runtime diblokir oleh kebijakan keselamatan.";
        }
    }

    @Override
    public String retryBootstrap() {
        SafetyContracts.RecoveryState state = host.bootstrap();
        return "Status bootstrap: " + safeState(state) + ".";
    }

    @Override
    public String enterReadOnly() {
        return "Mode terbatas tetap aktif; Stage A belum mengaktifkan jalur baca-saja terpisah.";
    }

    @Override
    public String exportSanitizedDiagnostics() {
        StageAContracts.HealthSnapshot health = host.health();
        return "Diagnostik aman: status=" + health.state().name()
                + ", kejadian=" + health.diagnosticCount()
                + ", terbuang=" + health.droppedDiagnostics() + ".";
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
            return "Runtime sudah dikarantina.";
        }
        SafetyContracts.Transition transition = host.recoveryCoordinator()
                .apply(SafetyContracts.RecoveryEvent.FATAL_FAILURE);
        if (transition.next() != SafetyContracts.RecoveryState.QUARANTINED) {
            throw new StageAContracts.StageAException(
                    "safe.ui.quarantine.failed", "Quarantine transition did not reach terminal state");
        }
        return "Runtime dikarantina.";
    }

    private static String safeState(SafetyContracts.RecoveryState state) {
        switch (state) {
            case NORMAL: return "normal";
            case DEGRADED: return "terbatas";
            case RECOVERY_REQUIRED: return "pemulihan diperlukan";
            case SAFE_MODE: return "mode aman";
            case QUARANTINED: return "dikarantina";
            default: return "tidak dikenal";
        }
    }
}
