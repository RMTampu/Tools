package io.toolbox.stagea.android;

import io.toolbox.contracts.safety.SafetyContracts;
import io.toolbox.stagea.StageAContracts;

import java.util.List;
import java.util.Objects;

/**
 * Production-safe actions for the Stage-A restricted UI.
 *
 * This implementation is deliberately bounded to Stage-A capabilities. It never
 * invents snapshot/restore semantics, never emits raw private state, and routes
 * quarantine through the promoted recovery state machine.
 */
public final class AndroidStageASafeUiActions implements AndroidSafeUi.Actions {
    private static final int MAX_DIAGNOSTIC_EVENTS_EXPORTED = 8;
    private static final int MAX_MESSAGE_LENGTH = 512;

    private final AndroidStageAHost host;

    public AndroidStageASafeUiActions(AndroidStageAHost host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    @Override
    public String verifyIntegrity() {
        StageAContracts.HealthSnapshot health = host.health();
        return bounded(
                "health=" + health.state().name()
                        + "; recovery=" + health.recoveryState().name()
                        + "; guard=" + health.guardMode().name()
                        + "; registryEntries=" + health.registryEntries()
                        + "; diagnostics=" + health.diagnosticCount()
                        + "; droppedDiagnostics=" + health.droppedDiagnostics()
        );
    }

    @Override
    public String retryBootstrap() {
        SafetyContracts.RecoveryState state = host.bootstrap();
        return bounded("bootstrap=" + state.name());
    }

    @Override
    public String enterReadOnly() {
        StageAContracts.SafeUiModel model = host.safeUiModel();
        if (!model.visible() || !model.restricted()) {
            throw new StageAContracts.StageAException(
                    "safe.ui.read.only.unavailable",
                    "Read-only restriction is available only while Safe UI is active"
            );
        }
        return "readOnly=ACTIVE; normalExecution=BLOCKED";
    }

    @Override
    public String exportSanitizedDiagnostics() {
        List<SafetyContracts.DiagnosticEvent> events = host.diagnostics().snapshot();
        StringBuilder out = new StringBuilder();
        out.append("diagnostics=").append(events.size());
        int start = Math.max(0, events.size() - MAX_DIAGNOSTIC_EVENTS_EXPORTED);
        for (int i = start; i < events.size(); i++) {
            SafetyContracts.DiagnosticEvent event = events.get(i);
            out.append(" | ")
                    .append(event.sequence()).append(':')
                    .append(event.severity().name()).append(':')
                    .append(event.sourceId()).append(':')
                    .append(event.code()).append(':')
                    .append(event.messageKey());
            if (out.length() >= MAX_MESSAGE_LENGTH) break;
        }
        return bounded(out.toString());
    }

    @Override
    public boolean canRestoreKnownGood() {
        // Stage A owns recovery state coordination, not snapshot/checkpoint restore.
        return false;
    }

    @Override
    public String restoreKnownGood() {
        throw new StageAContracts.StageAException(
                "safe.ui.restore.unavailable",
                "Known-good snapshot restore is not a Stage-A capability"
        );
    }

    @Override
    public boolean canQuarantine() {
        return host.recoveryCoordinator().state() != SafetyContracts.RecoveryState.QUARANTINED;
    }

    @Override
    public String quarantine() {
        SafetyContracts.RecoveryState state = host.recoveryCoordinator().state();
        if (state != SafetyContracts.RecoveryState.QUARANTINED) {
            state = host.recoveryCoordinator().apply(SafetyContracts.RecoveryEvent.FATAL_FAILURE).next();
        }
        return bounded("quarantine=" + state.name());
    }

    private static String bounded(String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.isEmpty()) return "status=UNAVAILABLE";
        return safe.length() <= MAX_MESSAGE_LENGTH ? safe : safe.substring(0, MAX_MESSAGE_LENGTH);
    }
}
