package io.toolbox.stagea;

import io.toolbox.contracts.safety.DiagnosticBuffer;
import io.toolbox.contracts.safety.SafetyContracts;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic, fail-closed policy for Stage-A safe-UI actions.
 * This class owns no Android lifecycle, filesystem export, restore authority,
 * network access, Firebase access, or Private receiver knowledge.
 */
public final class SafeUiActionPolicy {
    private static final int MAX_SUMMARY_LENGTH = 512;

    private SafeUiActionPolicy() {}

    public static String inspection(StageAContracts.HealthSnapshot health) {
        Objects.requireNonNull(health, "health");
        return bounded("Pemeriksaan aman: health=" + health.state()
                + ", recovery=" + health.recoveryState()
                + ", guard=" + health.guardMode()
                + ", registry=" + health.registryEntries()
                + ", diagnostics=" + health.diagnosticCount()
                + ", dropped=" + health.droppedDiagnostics() + ".");
    }

    public static String bootstrapState(SafetyContracts.RecoveryState state) {
        Objects.requireNonNull(state, "state");
        return bounded("Bootstrap Stage A aktif dengan recovery=" + state + ".");
    }

    public static String restrictedModeStatus(SafetyContracts.RecoveryState state) {
        Objects.requireNonNull(state, "state");
        if (state == SafetyContracts.RecoveryState.QUARANTINED) {
            return "Runtime dikarantina; jalur normal tetap diblokir.";
        }
        if (state == SafetyContracts.RecoveryState.SAFE_MODE) {
            return "Mode aman terbatas sudah aktif.";
        }
        return "Stage A tidak mendefinisikan authority storage baca-saja; tidak ada klaim read-only dibuat.";
    }

    public static String sanitizedDiagnostics(DiagnosticBuffer diagnostics) {
        Objects.requireNonNull(diagnostics, "diagnostics");
        Set<String> codes = new LinkedHashSet<>();
        for (SafetyContracts.DiagnosticEvent event : diagnostics.snapshot()) {
            codes.add(event.code());
            if (codes.size() >= 8) break;
        }
        return bounded("Diagnostik aman: count=" + diagnostics.size()
                + ", dropped=" + diagnostics.droppedCount()
                + ", codes=" + codes + ".");
    }

    public static boolean canRestoreKnownGood() {
        return false;
    }

    public static String restoreUnavailable() {
        return "Restore known-good belum memiliki authority pada Stage A.";
    }

    public static boolean canQuarantine(SafetyContracts.RecoveryState state) {
        return Objects.requireNonNull(state, "state") != SafetyContracts.RecoveryState.QUARANTINED;
    }

    public static String quarantineResult(SafetyContracts.Transition transition) {
        Objects.requireNonNull(transition, "transition");
        return bounded("Karantina: " + transition.previous() + " -> " + transition.next() + ".");
    }

    private static String bounded(String value) {
        if (value.length() <= MAX_SUMMARY_LENGTH) return value;
        return value.substring(0, MAX_SUMMARY_LENGTH);
    }
}
